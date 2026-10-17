package cn.itcast.agent.ai.service.impl;

import cn.itcast.agent.ai.config.MemoryProperties;
import cn.itcast.agent.ai.entity.UserMemory;
import cn.itcast.agent.ai.mapper.UserMemoryMapper;
import cn.itcast.agent.ai.memory.MemoryEmbeddingClient;
import cn.itcast.agent.ai.memory.MemoryExtractor;
import cn.itcast.agent.ai.service.LongTermMemoryService;
import cn.itcast.agent.ai.vo.MemoryVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 长期记忆实现：抽取写入 + 打分召回 + 自助管理。
 *
 * <p>整体节奏是「写入在后台、召回在热路径」：</p>
 * <ol>
 *   <li>召回：每轮对话前取候选池并打分，把最相关的若干条注入提示词。候选池按「重要度 + 更新时间」
 *       预筛，打分再叠加文本相关度、重要度与时效；配了向量接口时文本相关度用余弦相似度，
 *       否则退化成分词重合度（Dice 系数）。</li>
 *   <li>写入：回答返回之后再异步抽取本轮值得长期记住的信息，按正文哈希去重，同一条只提升重要度，
 *       不重复落库。</li>
 *   <li>治理：单用户记忆总量超出上限时，淘汰重要度最低、最久未更新的条目，避免无限增长。</li>
 * </ol>
 *
 * <p>所有失败都不会向上抛：召回失败退化为「没有记忆」，写入失败只记日志。记忆是增强项，
 * 不应该成为对话可用性的单点。</p>
 */
@Service
public class LongTermMemoryServiceImpl implements LongTermMemoryService {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryServiceImpl.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int MIN_IMPORTANCE = 1;
    private static final int MAX_IMPORTANCE = 5;
    private static final int DEFAULT_IMPORTANCE = 3;

    /** 打分权重：文本相关度是主项，重要度与时效做修正，三项相加为 1 */
    private static final double WEIGHT_TEXT = 0.45;
    private static final double WEIGHT_IMPORTANCE = 0.35;
    private static final double WEIGHT_RECENCY = 0.20;
    /** 时效半衰期：14 天没更新的记忆，时间项权重减半 */
    private static final double RECENCY_HALF_LIFE_DAYS = 14.0;
    /** 交付给抽取模型的已有记忆条数上限，避免提示词随记忆增长而膨胀 */
    private static final int EXISTING_PROMPT_LIMIT = 40;
    /** 英文单词分词的最短长度，太短的词没有区分度 */
    private static final int MIN_WORD_LENGTH = 2;

    private final UserMemoryMapper userMemoryMapper;
    private final MemoryExtractor memoryExtractor;
    private final MemoryEmbeddingClient embeddingClient;
    private final MemoryProperties properties;

    /**
     * 抽取与统计的后台线程：单线程即可——抽取是低频动作，串行还能天然避免同一用户并发写入撞唯一键。
     * 用守护线程 + @PreDestroy 关闭，保证服务退出时不会挂住。
     */
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "user-memory-writer");
        thread.setDaemon(true);
        return thread;
    });

    public LongTermMemoryServiceImpl(UserMemoryMapper userMemoryMapper,
                                     MemoryExtractor memoryExtractor,
                                     MemoryEmbeddingClient embeddingClient,
                                     MemoryProperties properties) {
        this.userMemoryMapper = userMemoryMapper;
        this.memoryExtractor = memoryExtractor;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    @Override
    public List<UserMemory> recall(Long userId, String query) {
        if (userId == null || !properties.active()) {
            return List.of();
        }
        List<UserMemory> candidates;
        try {
            candidates = userMemoryMapper.selectList(
                    Wrappers.<UserMemory>lambdaQuery()
                            .eq(UserMemory::getUserId, userId)
                            .orderByDesc(UserMemory::getImportance)
                            .orderByDesc(UserMemory::getUpdateTime)
                            .orderByDesc(UserMemory::getId)
                            .last("LIMIT " + properties.candidateLimit()));
        } catch (Exception e) {
            log.warn("长期记忆候选查询失败，本轮不注入记忆：userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        float[] queryVector = embeddingClient.embed(query).orElse(null);
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (UserMemory candidate : candidates) {
            double score = score(candidate, query, queryVector);
            if (score >= properties.minScore()) {
                scored.add(new Scored(candidate, score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<UserMemory> recalled = scored.stream()
                .limit(properties.recallTopK())
                .map(Scored::memory)
                .toList();

        if (!recalled.isEmpty()) {
            markHit(recalled);
            log.info("长期记忆召回：userId={}, 候选={}, 注入={}", userId, candidates.size(), recalled.size());
        }
        return recalled;
    }

    @Override
    public void remember(Long userId, String conversationId, String question, String answer) {
        if (userId == null || !properties.active() || backgroundExecutor.isShutdown()) {
            return;
        }
        try {
            backgroundExecutor.submit(() -> {
                try {
                    doRemember(userId, conversationId, question, answer);
                } catch (Exception e) {
                    log.error("长期记忆写入失败：userId={}, conversationId={}", userId, conversationId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.debug("长期记忆线程池已关闭，丢弃本轮抽取：userId={}", userId);
        }
    }

    @Override
    public List<MemoryVO> list(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<UserMemory> memories = userMemoryMapper.selectList(
                Wrappers.<UserMemory>lambdaQuery()
                        .eq(UserMemory::getUserId, userId)
                        .orderByDesc(UserMemory::getImportance)
                        .orderByDesc(UserMemory::getUpdateTime)
                        .orderByDesc(UserMemory::getId)
                        .last("LIMIT " + properties.maxPerUser()));
        return memories.stream().map(this::toVO).toList();
    }

    @Override
    public int clear(Long userId) {
        if (userId == null) {
            return 0;
        }
        int removed = userMemoryMapper.delete(Wrappers.<UserMemory>lambdaQuery().eq(UserMemory::getUserId, userId));
        log.info("长期记忆已清空：userId={}, 删除={}", userId, removed);
        return removed;
    }

    @PreDestroy
    void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    /** 抽取并落库：去重、合并、限量淘汰，整段都在后台线程里跑 */
    private void doRemember(Long userId, String conversationId, String question, String answer) {
        List<UserMemory> existing = userMemoryMapper.selectList(
                Wrappers.<UserMemory>lambdaQuery()
                        .eq(UserMemory::getUserId, userId)
                        .orderByDesc(UserMemory::getImportance)
                        .orderByDesc(UserMemory::getUpdateTime)
                        .orderByDesc(UserMemory::getId)
                        .last("LIMIT " + properties.maxPerUser()));
        Map<String, UserMemory> byHash = new HashMap<>(existing.size() * 2);
        for (UserMemory memory : existing) {
            byHash.put(memory.getContentHash(), memory);
        }
        List<String> promptContents = existing.stream()
                .limit(EXISTING_PROMPT_LIMIT)
                .map(UserMemory::getContent)
                .toList();

        List<MemoryExtractor.MemoryDraft> drafts = memoryExtractor.extract(promptContents, question, answer);
        if (drafts.isEmpty()) {
            log.debug("本轮没有抽到新的长期记忆：userId={}", userId);
            return;
        }

        int inserted = 0;
        int updated = 0;
        for (MemoryExtractor.MemoryDraft draft : drafts) {
            String hash = sha256(draft.content());
            UserMemory exists = byHash.get(hash);
            if (exists != null) {
                if (merge(exists, draft)) {
                    updated++;
                }
                continue;
            }
            if (insert(userId, conversationId, draft, hash)) {
                inserted++;
            }
        }
        int removed = enforceLimit(userId);
        log.info("长期记忆落库完成：userId={}, 新增={}, 更新={}, 淘汰={}", userId, inserted, updated, removed);
    }

    /**
     * 已存在同一条记忆时的合并策略：只提升重要度、补全缺失的关键词，不覆盖正文。
     * 正文是模型上一轮写的，这一轮未必更好；而重要度取最大值能体现「用户多次提到」。
     */
    private boolean merge(UserMemory exists, MemoryExtractor.MemoryDraft draft) {
        boolean changed = false;
        if (draft.importance() != null && draft.importance() > importanceOf(exists)) {
            exists.setImportance(draft.importance());
            changed = true;
        }
        if (!StringUtils.hasText(exists.getKeywords()) && StringUtils.hasText(draft.keywords())) {
            exists.setKeywords(draft.keywords());
            changed = true;
        }
        if (!changed) {
            return false;
        }
        UserMemory update = new UserMemory();
        update.setId(exists.getId());
        update.setImportance(exists.getImportance());
        update.setKeywords(exists.getKeywords());
        userMemoryMapper.updateById(update);
        return true;
    }

    private boolean insert(Long userId, String conversationId, MemoryExtractor.MemoryDraft draft, String hash) {
        UserMemory memory = new UserMemory();
        memory.setUserId(userId);
        memory.setContent(draft.content());
        memory.setContentHash(hash);
        memory.setKeywords(draft.keywords());
        memory.setCategory(draft.category());
        memory.setImportance(draft.importance());
        memory.setSourceConversationId(conversationId);
        memory.setHitCount(0);
        if (embeddingClient.enabled()) {
            embeddingClient.embed(draft.content())
                    .ifPresent(vector -> memory.setEmbedding(serializeVector(vector)));
        }
        try {
            userMemoryMapper.insert(memory);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("长期记忆已存在，跳过写入：userId={}, content={}", userId, draft.content());
            return false;
        }
    }

    /** 超出单用户上限时，淘汰重要度最低、最久未更新的若干条 */
    private int enforceLimit(Long userId) {
        long total = userMemoryMapper.selectCount(
                Wrappers.<UserMemory>lambdaQuery().eq(UserMemory::getUserId, userId));
        int overflow = (int) (total - properties.maxPerUser());
        if (overflow <= 0) {
            return 0;
        }
        List<UserMemory> stale = userMemoryMapper.selectList(
                Wrappers.<UserMemory>lambdaQuery()
                        .eq(UserMemory::getUserId, userId)
                        .orderByAsc(UserMemory::getImportance)
                        .orderByAsc(UserMemory::getUpdateTime)
                        .orderByAsc(UserMemory::getId)
                        .last("LIMIT " + overflow));
        List<Long> ids = stale.stream().map(UserMemory::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return userMemoryMapper.delete(Wrappers.<UserMemory>lambdaQuery()
                .eq(UserMemory::getUserId, userId)
                .in(UserMemory::getId, ids));
    }

    /** 命中统计：只更新计数与时间，失败不影响本轮回答 */
    private void markHit(List<UserMemory> memories) {
        List<Long> ids = memories.stream().map(UserMemory::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty() || backgroundExecutor.isShutdown()) {
            return;
        }
        try {
            backgroundExecutor.submit(() -> {
                try {
                    userMemoryMapper.update(null, Wrappers.<UserMemory>lambdaUpdate()
                            .in(UserMemory::getId, ids)
                            .setSql("hit_count = hit_count + 1")
                            .set(UserMemory::getLastHitTime, LocalDateTime.now()));
                } catch (Exception e) {
                    log.debug("长期记忆命中统计更新失败：{}", e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            log.debug("长期记忆线程池已关闭，跳过命中统计");
        }
    }

    private double score(UserMemory memory, String query, float[] queryVector) {
        double text = textScore(memory, query, queryVector);
        double importance = (importanceOf(memory) - MIN_IMPORTANCE) / (double) (MAX_IMPORTANCE - MIN_IMPORTANCE);
        return WEIGHT_TEXT * text + WEIGHT_IMPORTANCE * importance + WEIGHT_RECENCY * recency(memory.getUpdateTime());
    }

    /**
     * 文本相关度：配了向量接口且该条记忆有向量时用余弦相似度；
     * 否则用「中文二元组 + 英文单词」的重合度（Dice 系数）兜底。
     */
    private double textScore(UserMemory memory, String query, float[] queryVector) {
        if (queryVector != null) {
            float[] memoryVector = parseVector(memory.getEmbedding());
            if (memoryVector != null) {
                return cosine(queryVector, memoryVector);
            }
        }
        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) {
            return 0;
        }
        Set<String> memoryTokens = tokens(memory.getContent());
        if (StringUtils.hasText(memory.getKeywords())) {
            memoryTokens.addAll(tokens(memory.getKeywords()));
        }
        if (memoryTokens.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String token : queryTokens) {
            if (memoryTokens.contains(token)) {
                hit++;
            }
        }
        return 2.0 * hit / (queryTokens.size() + memoryTokens.size());
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            return 0;
        }
        double dot = 0;
        double normLeft = 0;
        double normRight = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            normLeft += left[i] * left[i];
            normRight += right[i] * right[i];
        }
        if (normLeft == 0 || normRight == 0) {
            return 0;
        }
        // 余弦值域是 [-1,1]，负相关按 0 处理，避免「越不相关排得越前」
        return Math.max(dot / (Math.sqrt(normLeft) * Math.sqrt(normRight)), 0);
    }

    /**
     * 简易分词：中文取相邻两字组成二元组，英文与数字按单词，统一转小写。
     *
     * <p>不引分词库是为了不增加依赖，也为了保持召回过程完全确定、可离线测试。
     * 中文二元组的召回率足够支撑「先粗筛、再由重要度与时效加权」的策略；
     * 需要真正语义级召回时，打开 ai.memory.embedding.* 即可切到向量。</p>
     */
    private Set<String> tokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        StringBuilder word = new StringBuilder();
        char previousCjk = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (isCjk(current)) {
                if (previousCjk != 0) {
                    tokens.add(new String(new char[]{previousCjk, current}));
                }
                previousCjk = current;
                flushWord(word, tokens);
            } else if (Character.isLetterOrDigit(current)) {
                word.append(current);
                previousCjk = 0;
            } else {
                flushWord(word, tokens);
                previousCjk = 0;
            }
        }
        flushWord(word, tokens);
        return tokens;
    }

    private void flushWord(StringBuilder word, Set<String> tokens) {
        if (word.length() >= MIN_WORD_LENGTH) {
            tokens.add(word.toString());
        }
        word.setLength(0);
    }

    private boolean isCjk(char value) {
        return value >= '\u4e00' && value <= '\u9fff';
    }

    /** 时效项：越久没更新越低，半衰期 14 天 */
    private double recency(LocalDateTime updateTime) {
        if (updateTime == null) {
            return 1;
        }
        double days = Duration.between(updateTime, LocalDateTime.now()).toMinutes() / 1440.0;
        if (days <= 0) {
            return 1;
        }
        return Math.pow(0.5, days / RECENCY_HALF_LIFE_DAYS);
    }

    private int importanceOf(UserMemory memory) {
        Integer importance = memory.getImportance();
        if (importance == null) {
            return DEFAULT_IMPORTANCE;
        }
        return Math.min(Math.max(importance, MIN_IMPORTANCE), MAX_IMPORTANCE);
    }

    /**
     * 去重用的内容哈希：先去掉所有空白再转小写，让「同一句话多一个空格」不会被当成两条。
     * 用哈希而不是正文做唯一键，是为了避开 utf8mb4 下的索引长度限制。
     */
    private String sha256(String content) {
        String normalized = content.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private String serializeVector(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 8);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.toString();
    }

    private float[] parseVector(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String[] parts = text.split(",");
        float[] vector = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            log.debug("长期记忆向量解析失败，该条退回关键词打分：{}", e.getMessage());
            return null;
        }
        return vector;
    }

    private MemoryVO toVO(UserMemory memory) {
        return new MemoryVO(memory.getId(), memory.getContent(), memory.getCategory(), importanceOf(memory),
                splitKeywords(memory.getKeywords()), memory.getHitCount() == null ? 0 : memory.getHitCount(),
                format(memory.getCreateTime()), format(memory.getUpdateTime()));
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return List.of();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME_FORMATTER.format(dateTime);
    }

    /** 打分中间结果 */
    private record Scored(UserMemory memory, double score) {
    }
}
