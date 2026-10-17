package cn.itcast.agent.ai.service;

import cn.itcast.agent.ai.config.RecommendProperties;
import cn.itcast.agent.ai.dto.RecommendRequest;
import cn.itcast.agent.ai.vo.MatchPointVO;
import cn.itcast.agent.ai.vo.RecommendVO;
import cn.itcast.agent.ai.vo.RecommendationVO;
import cn.itcast.agent.ai.vo.RequirementVO;
import cn.itcast.agent.common.api.client.SearchServiceClient;
import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.common.api.dto.RepositoryBatchQuery;
import cn.itcast.agent.common.api.dto.RepositoryReadme;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.CurrentUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * AI 推荐流水线：把「用户用自己的话描述需求」变成「挑好的几个开源项目」。
 *
 * <p>与 /ai/agent 的区别：Agent 是让模型自由决定要不要调工具，召回宽度与排序都不可控；
 * 这里是一条确定性流水线，每一步的输入输出都明确，便于定位「是没搜到，还是排错了」：</p>
 * <ol>
 *   <li>需求解析：模型把一整段需求拆成结构化维度（项目形态、技术栈、功能点、规模、其他约束、检索条件）</li>
 *   <li>多关键词召回：交给 search-service 批量检索并去重；语言 / Star / 活跃度作为限定符一起下推</li>
 *   <li>硬过滤：语言与「最近 N 天活跃」在本地二次过滤；语言过滤若把候选清空会被忽略并记日志</li>
 *   <li>项目深读：对初筛候选补 README 正文，让模型看到技术栈与功能清单，而不是只有一句简介</li>
 *   <li>模型排序：把需求维度与候选（含 README 摘要）一起给模型，输出分数、理由与逐维核对结论</li>
 *   <li>组装与落库：映射回仓库真实字段，写一份可读的推荐摘要到对话历史</li>
 * </ol>
 *
 * <p>降级策略：模型排序失败（输出不是合法 JSON / 全是候选之外的仓库）时，
 * 直接按检索服务给出的 Star 降序返回候补结果，而不是给用户一个报错。</p>
 */
@Service
public class RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendService.class);

    /** 候选简介送入模型时的截断长度，避免提示词被长描述撑爆 */
    private static final int DESCRIPTION_MAX_LENGTH = 200;
    /** 需求摘要的兜底长度（模型没给 summary 时截取用户原话） */
    private static final int SUMMARY_MAX_LENGTH = 100;
    /** 关键词兜底长度（模型没给关键词时截取用户原话） */
    private static final int KEYWORD_FALLBACK_LENGTH = 60;
    /** 语言约束最多保留几个 */
    private static final int LANGUAGE_LIMIT = 3;
    /** 「最近 N 天活跃」的上限，防止模型给出离谱数值 */
    private static final int MAX_UPDATED_WITHIN_DAYS = 3650;
    /** 落库摘要里每条推荐理由的截断长度 */
    private static final int HISTORY_REASON_MAX_LENGTH = 120;
    /** 技术栈 / 功能点 / 其他约束三种维度各自最多保留几项 */
    private static final int TECH_STACK_LIMIT = 6;
    private static final int FEATURES_LIMIT = 6;
    private static final int CONSTRAINTS_LIMIT = 5;
    /** 逐项核对最多保留几个维度（提示词已要求 5 个，这里再兜一层） */
    private static final int MATCH_MAX_ITEMS = 5;
    /** 项目深读的并发线程数：既缩短总耗时，又限制同时打向外部接口的请求数 */
    private static final int DEEP_READ_THREADS = 4;
    /** 单条 README 读取的超时时间（秒），超时即退化为只用元数据 */
    private static final long DEEP_READ_TIMEOUT_SECONDS = 10L;

    private final ChatClient recommendAnalysisChatClient;
    private final ChatClient recommendRankChatClient;
    private final SearchServiceClient searchServiceClient;
    private final ConversationHistoryService conversationHistoryService;
    private final RecommendProperties properties;
    private final JsonMapper jsonMapper;
    /**
     * 项目深读用的线程池：每个候选的 README 都是一次独立的 HTTP 调用，串行读取最慢会到十几秒；
     * 固定小并发既缩短耗时，又避免把外部接口打爆，@PreDestroy 时统一关闭。
     */
    private final ExecutorService deepReadExecutor = Executors.newFixedThreadPool(DEEP_READ_THREADS,
            runnable -> {
                Thread thread = new Thread(runnable, "recommend-deep-read");
                thread.setDaemon(true);
                return thread;
            });

    public RecommendService(@Qualifier("recommendAnalysisChatClient") ChatClient recommendAnalysisChatClient,
                            @Qualifier("recommendRankChatClient") ChatClient recommendRankChatClient,
                            SearchServiceClient searchServiceClient,
                            ConversationHistoryService conversationHistoryService,
                            RecommendProperties properties,
                            JsonMapper jsonMapper) {
        this.recommendAnalysisChatClient = recommendAnalysisChatClient;
        this.recommendRankChatClient = recommendRankChatClient;
        this.searchServiceClient = searchServiceClient;
        this.conversationHistoryService = conversationHistoryService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param authenticatedUserId 网关校验 JWT 后写入的用户 ID；
     *                            非空时优先于请求体里的 userId，避免客户端伪造身份串用会话
     */
    public RecommendVO recommend(RecommendRequest request, String authenticatedUserId) {
        String conversationId = StringUtils.hasText(request.conversationId())
                ? request.conversationId().trim()
                : UUID.randomUUID().toString();
        String userId = StringUtils.hasText(authenticatedUserId) ? authenticatedUserId : request.userId();
        int topN = request.topN() == null ? properties.topN() : request.topN();

        // 1) 需求解析
        ChatResult analysis = chat(recommendAnalysisChatClient, request.message());
        RequirementPlan plan = normalizePlan(parsePlan(analysis.text()), request.message());

        // 2) 多关键词召回
        List<GiteeRepository> candidates = recall(plan);

        // 3) 硬过滤（语言 / 活跃度）
        candidates = filterByLanguage(candidates, plan.languages());
        candidates = filterByRecency(candidates, plan.updatedWithinDays());

        // 4) 项目深读：给初筛候选补 README 正文，让模型基于内容而不是一句话简介判断

        Map<String, String> readmes = deepRead(candidates);

        // 5) 模型排序
        String model = analysis.model();
        List<RecommendationVO> recommendations = List.of();
        if (candidates.isEmpty()) {
            log.info("候选池为空，跳过模型排序：keywords={}", plan.keywords());
        } else {
            ChatResult ranking = chat(recommendRankChatClient, buildRankPrompt(plan, candidates, topN, readmes));
            if (StringUtils.hasText(ranking.model())) {
                model = ranking.model();
            }
            recommendations = parseRecommendations(ranking.text(), candidates, topN);
        }

        // 6) 组装与落库
        RequirementVO requirement = new RequirementVO(plan.summary(), plan.projectType(), plan.techStack(),
                plan.features(), plan.scale(), plan.constraints(), plan.keywords(), plan.languages(),
                plan.minStars(), plan.updatedWithinDays(), plan.license(), plan.notes());
        RecommendVO vo = new RecommendVO(requirement, recommendations, candidates.size(), model, conversationId);
        recordHistory(userId, conversationId, request.message(), summaryOf(vo), model);
        return vo;
    }

    /**
     * 多关键词召回。语言过滤先按「唯一语言」下推给检索服务；
     * 如果因此一条都没召回到，就去掉语言约束重试一次——宁可放宽，也不要直接给用户空结果。
     */
    private List<GiteeRepository> recall(RequirementPlan plan) {
        String languageFilter = plan.languages().size() == 1 ? plan.languages().get(0) : null;
        String pushedAfter = pushedAfterOf(plan.updatedWithinDays());
        List<GiteeRepository> candidates = queryBatch(plan.keywords(), languageFilter, plan.minStars(), pushedAfter);
        if (candidates.isEmpty() && languageFilter != null) {
            log.warn("带语言约束的召回为空，去掉语言约束重试：language={}, keywords={}", languageFilter, plan.keywords());
            candidates = queryBatch(plan.keywords(), null, plan.minStars(), pushedAfter);
        }
        log.info("推荐召回完成：keywords={}, 候选={}", plan.keywords(), candidates.size());
        return candidates;
    }

    /** 把「最近 N 天活跃」换算成日期下推给检索服务，而不是拿回来再本地筛掉 */
    private String pushedAfterOf(Integer updatedWithinDays) {
        return updatedWithinDays == null ? null : LocalDate.now().minusDays(updatedWithinDays).toString();
    }

    private List<GiteeRepository> queryBatch(List<String> keywords, String language, Integer minStars,
                                             String pushedAfter) {
        RepositoryBatchQuery query = new RepositoryBatchQuery(
                keywords, properties.perKeyword(), language, minStars, pushedAfter, properties.candidatePoolMax());
        Result<List<GiteeRepository>> result;
        try {
            result = searchServiceClient.searchRepositoriesBatch(query);
        } catch (Exception e) {
            log.error("调用检索服务召回候选失败：keywords={}", keywords, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "检索服务暂不可用，请稍后重试");
        }
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "检索失败：" + (result == null ? "检索服务无响应" : result.getMessage()));
        }
        return result.getData() == null ? List.of() : result.getData();
    }

    /**
     * 语言二次过滤：模型可能把前端框架（如 Vue）当成「语言」，此时按语言筛会全军覆没，
     * 所以筛空时忽略该约束并记日志，保证用户至少拿得到结果。
     */
    private List<GiteeRepository> filterByLanguage(List<GiteeRepository> candidates, List<String> languages) {
        if (languages.isEmpty() || candidates.isEmpty()) {
            return candidates;
        }
        Set<String> wanted = new LinkedHashSet<>();
        languages.forEach(language -> wanted.add(language.toLowerCase(Locale.ROOT)));
        List<GiteeRepository> matched = candidates.stream()
                .filter(repository -> repository.language() != null
                        && wanted.contains(repository.language().toLowerCase(Locale.ROOT)))
                .toList();
        if (matched.isEmpty()) {
            log.warn("按语言过滤后候选为空，已忽略语言约束：languages={}, 候选={}", languages, candidates.size());
            return candidates;
        }
        return matched;
    }

    /** 活跃度过滤：时间字段缺失或格式异常时不过滤，避免误杀 */
    private List<GiteeRepository> filterByRecency(List<GiteeRepository> candidates, Integer updatedWithinDays) {
        if (updatedWithinDays == null || candidates.isEmpty()) {
            return candidates;
        }
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(updatedWithinDays);
        return candidates.stream()
                .filter(repository -> {
                    OffsetDateTime pushedAt = parseIso(repository.updatedAt());
                    return pushedAt == null || !pushedAt.isBefore(threshold);
                })
                .toList();
    }

    /**
     * 组装排序提示词：需求维度 + 候选元数据（含深读到的 README 摘要），全部使用真实字段。
     * README 是「项目分析」的主要依据：有摘要的候选按内容判断，没有的只用元数据判断。
     */
    private String buildRankPrompt(RequirementPlan plan, List<GiteeRepository> candidates, int topN,
                                   Map<String, String> readmes) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("用户需求：").append(plan.summary()).append('\n');
        if (StringUtils.hasText(plan.projectType())) {
            builder.append("项目形态：").append(plan.projectType().trim()).append('\n');
        }
        if (!plan.techStack().isEmpty()) {
            builder.append("技术栈：").append(String.join(" / ", plan.techStack())).append('\n');
        }
        if (!plan.features().isEmpty()) {
            builder.append("功能点：").append(String.join(" / ", plan.features())).append('\n');
        }
        if (StringUtils.hasText(plan.scale())) {
            builder.append("规模/场景：").append(plan.scale().trim()).append('\n');
        }
        if (!plan.constraints().isEmpty()) {
            builder.append("其他约束：").append(String.join(" / ", plan.constraints())).append('\n');
        }
        if (!plan.languages().isEmpty()) {
            builder.append("语言约束：").append(String.join(" / ", plan.languages())).append('\n');
        }
        if (plan.minStars() != null) {
            builder.append("Star 下限：").append(plan.minStars()).append('\n');
        }
        if (plan.updatedWithinDays() != null) {
            builder.append("活跃度要求：最近 ").append(plan.updatedWithinDays()).append(" 天内有推送\n");
        }
        if (plan.license() != null) {
            builder.append("协议要求：").append(plan.license()).append('\n');
        }
        if (plan.notes() != null) {
            builder.append("补充说明：").append(plan.notes()).append('\n');
        }
        builder.append('\n').append("候选仓库（").append(candidates.size()).append(" 条）：").append('\n');
        int index = 1;
        for (GiteeRepository repository : candidates) {
            builder.append(index++).append(". ").append(repository.fullName())
                    .append(" | 语言：").append(textOrUnknown(repository.language()))
                    .append(" | Star：").append(repository.stars() == null ? "未知" : repository.stars())
                    .append(" | 协议：").append(textOrUnknown(repository.license()))
                    .append(" | 最近推送：").append(textOrUnknown(repository.updatedAt()));
            if (repository.topics() != null && !repository.topics().isEmpty()) {
                builder.append(" | 主题：").append(String.join(",", repository.topics()));
            }
            builder.append(" | 简介：").append(abbreviate(repository.description(), DESCRIPTION_MAX_LENGTH))
                    .append('\n');
            String readme = readmes.get(key(repository.fullName()));
            if (StringUtils.hasText(readme)) {
                builder.append("   README 摘要：").append(readme).append('\n');
            }
        }
        builder.append('\n').append("请按规则输出 JSON 数组，最多 ").append(topN).append(" 条。");
        return builder.toString();
    }

    /** 解析模型排序结果；解析失败或结果全不可用时退回 Star 降序候选 */
    private List<RecommendationVO> parseRecommendations(String raw, List<GiteeRepository> candidates, int topN) {
        Map<String, GiteeRepository> byFullName = new HashMap<>();
        candidates.forEach(repository -> byFullName.put(key(repository.fullName()), repository));

        List<RankItem> items;
        try {
            items = readJson(raw, '[', ']', new TypeReference<List<RankItem>>() {
            });
        } catch (IOException e) {
            log.warn("模型排序结果解析失败，改用 Star 兜底排序：error={}", e.getMessage());
            return fallbackRecommendations(candidates, topN);
        }

        List<RecommendationVO> ranked = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (RankItem item : items) {
            if (item == null || !StringUtils.hasText(item.fullName())) {
                continue;
            }
            String itemKey = key(item.fullName());
            GiteeRepository repository = byFullName.get(itemKey);
            if (repository == null) {
                log.warn("模型返回了候选列表以外的仓库，已忽略：{}", item.fullName());
                continue;
            }
            if (!used.add(itemKey)) {
                continue;
            }
            ranked.add(new RecommendationVO(repository, normalizeScore(item.score()),
                    reasonOrFallback(item),
                    safeMatches(item.matches()),
                    safeList(item.highlights()), safeList(item.risks())));
        }
        if (ranked.isEmpty()) {
            log.warn("模型排序结果中没有任何有效候选，改用 Star 兜底排序");
            return fallbackRecommendations(candidates, topN);
        }
        return ranked.stream()
                .sorted(Comparator.comparingInt((RecommendationVO item) ->
                        item.score() == null ? 0 : item.score()).reversed())
                .limit(topN)
                .toList();
    }

    private List<RecommendationVO> fallbackRecommendations(List<GiteeRepository> candidates, int topN) {
        return candidates.stream()
                .limit(topN)
                .map(repository -> new RecommendationVO(repository, null,
                        "模型排序不可用，按 Star 数与召回顺序给出的候补结果",
                        List.of(), List.of(), List.of()))
                .toList();
    }

    private RequirementPlan parsePlan(String raw) {
        try {
            RequirementPlan plan = readJson(raw, '{', '}', new TypeReference<RequirementPlan>() {
            });
            if (plan != null) {
                return plan;
            }
        } catch (IOException e) {
            log.warn("需求解析结果不是合法 JSON，改用用户原话兜底：error={}", e.getMessage());
        }
        return new RequirementPlan(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** 规整模型输出：去空、去重、限长、夹紧数值，缺关键词时退化为用户原话 */
    private RequirementPlan normalizePlan(RequirementPlan plan, String userMessage) {
        List<String> keywords = distinct(plan.keywords(), properties.maxKeywords());
        if (keywords.isEmpty()) {
            log.warn("需求解析未给出关键词，退化为使用用户原话检索");
            keywords = List.of(abbreviate(userMessage, KEYWORD_FALLBACK_LENGTH));
        }
        String summary = StringUtils.hasText(plan.summary())
                ? plan.summary().trim()
                : abbreviate(userMessage, SUMMARY_MAX_LENGTH);
        Integer minStars = plan.minStars() != null && plan.minStars() >= 0 ? plan.minStars() : null;
        Integer updatedWithinDays = plan.updatedWithinDays() != null && plan.updatedWithinDays() > 0
                ? Math.min(plan.updatedWithinDays(), MAX_UPDATED_WITHIN_DAYS)
                : null;
        return new RequirementPlan(summary, trimToNull(plan.projectType()),
                distinct(plan.techStack(), TECH_STACK_LIMIT), distinct(plan.features(), FEATURES_LIMIT),
                trimToNull(plan.scale()), distinct(plan.constraints(), CONSTRAINTS_LIMIT), keywords,
                distinct(plan.languages(), LANGUAGE_LIMIT), minStars, updatedWithinDays,
                trimToNull(plan.license()), trimToNull(plan.notes()));
    }

    private ChatResult chat(ChatClient client, String userPrompt) {
        ChatResponse response = client.prompt().user(userPrompt).call().chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "模型未返回有效结果，请稍后重试");
        }
        String text = response.getResult().getOutput().getText();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "模型返回内容为空，请稍后重试");
        }
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new ChatResult(text, model);
    }

    private <T> T readJson(String raw, char open, char close, TypeReference<T> type) throws IOException {
        String json = extractJson(raw, open, close);
        if (json == null) {
            throw new IOException("模型输出中没有找到 JSON 片段");
        }
        try {
            return jsonMapper.readerFor(type)
                    .with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                    .readValue(json);
        } catch (JacksonException e) {
            throw new IOException("模型输出 JSON 解析失败：" + e.getMessage(), e);
        }
    }

    /** 模型常把 JSON 包在 ```json 代码块或解释文字里，这里按首尾括号截取 */
    private String extractJson(String raw, char open, char close) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int start = raw.indexOf(open);
        int end = raw.lastIndexOf(close);
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private void recordHistory(String userId, String conversationId, String question, String answer, String model) {
        Long historyUserId = CurrentUser.parseIdOrNull(userId);
        if (historyUserId == null) {
            log.debug("未取得登录用户 ID，跳过对话历史落库：conversationId={}", conversationId);
            return;
        }
        try {
            conversationHistoryService.recordTurn(historyUserId, conversationId, question, answer, model);
        } catch (Exception e) {
            log.error("推荐结果落库失败：userId={}, conversationId={}", historyUserId, conversationId, e);
        }
    }

    /** 落库用的可读摘要：让「历史记录」页看到推荐了哪些项目、为什么推荐 */
    private String summaryOf(RecommendVO vo) {
        StringBuilder builder = new StringBuilder("【AI 推荐】").append(vo.requirement().summary()).append('\n');
        builder.append("检索关键词：").append(String.join(" / ", vo.requirement().keywords())).append('\n');
        builder.append("候选数：").append(vo.candidateCount()).append('\n');
        if (vo.recommendations().isEmpty()) {
            builder.append("本次没有匹配到合适的仓库，建议补充技术栈或功能点后重试。\n");
            return builder.toString();
        }
        int index = 1;
        for (RecommendationVO item : vo.recommendations()) {
            builder.append(index++).append(". ").append(item.repository().fullName());
            if (item.score() != null) {
                builder.append("（匹配度 ").append(item.score()).append("）");
            }
            builder.append("：").append(abbreviate(item.reason(), HISTORY_REASON_MAX_LENGTH)).append('\n');
            builder.append("   ").append(textOrEmpty(item.repository().htmlUrl())).append('\n');
        }
        return builder.toString();
    }

    private List<String> distinct(List<String> values, int limit) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                distinct.add(value.trim());
            }
            if (distinct.size() >= limit) {
                break;
            }
        }
        return List.copyOf(distinct);
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    private Integer normalizeScore(Integer score) {
        if (score == null) {
            return null;
        }
        return Math.max(0, Math.min(score, 100));
    }

    private OffsetDateTime parseIso(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            log.debug("无法解析更新时间字段，跳过时间过滤：value={}", value);
            return null;
        }
    }

    private String key(String fullName) {
        return fullName == null ? "" : fullName.trim().toLowerCase(Locale.ROOT);
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "…";
    }

    private String textOrUnknown(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未知";
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String textOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 项目深读：对初筛候选（最多 deepReadMax 条）并发读取 README 正文。
     *
     * <p>为什么要多做这一步：简介只有一句话，模型最多判断「像不像」；README 才写得清
     * 技术栈、功能清单和维护状态。读不到的（仓库没有 README、来源不支持、超时）
     * 直接跳过，让该候选退回「只用元数据」参与排序，绝不因为读不到内容而中断推荐。</p>
     *
     * @return key 为 fullName（小写）的 README 摘要，可能为空 Map
     */
    private Map<String, String> deepRead(List<GiteeRepository> candidates) {
        int limit = properties.deepReadMax();
        if (candidates.isEmpty() || limit <= 0) {
            return Map.of();
        }
        List<GiteeRepository> targets = candidates.stream().limit(limit).toList();
        List<CompletableFuture<RepositoryReadme>> futures = targets.stream()
                .map(repository -> CompletableFuture.supplyAsync(() -> fetchReadme(repository), deepReadExecutor))
                .toList();

        Map<String, String> readmes = new LinkedHashMap<>();
        for (int index = 0; index < futures.size(); index++) {
            GiteeRepository repository = targets.get(index);
            try {
                RepositoryReadme readme = futures.get(index).get(DEEP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (readme != null && StringUtils.hasText(readme.content())) {
                    readmes.put(key(repository.fullName()),
                            abbreviate(readme.content(), properties.readmeMaxLength()));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.debug("读取 README 失败，该候选退回只用元数据：repo={}, error={}",
                        repository.fullName(), e.getMessage());
            }
        }
        log.info("项目深读完成：候选={}, 深读上限={}, 取到 README={}", candidates.size(), limit, readmes.size());
        return readmes;
    }

    /** 调检索服务读 README；任何异常都视为「读不到」，返回 null 由调用方降级 */
    private RepositoryReadme fetchReadme(GiteeRepository repository) {
        String fullName = repository.fullName();
        if (!StringUtils.hasText(fullName)) {
            return null;
        }
        int separator = fullName.indexOf('/');
        if (separator <= 0 || separator == fullName.length() - 1) {
            return null;
        }
        String owner = fullName.substring(0, separator);
        String repo = fullName.substring(separator + 1);
        try {
            Result<RepositoryReadme> result = searchServiceClient.getRepositoryReadme(owner, repo);
            return result == null || !result.isSuccess() ? null : result.getData();
        } catch (Exception e) {
            log.debug("读取 README 失败：repo={}, error={}", fullName, e.getMessage());
            return null;
        }
    }

    /**
     * 推荐理由：模型偶尔漏掉 reason（把依据都写进了 matches），此时用命中维度拼一句，
     * 保证卡片上不会出现「模型未给出推荐理由」这种对用户没有信息量的文案。
     */
    private String reasonOrFallback(RankItem item) {
        if (StringUtils.hasText(item.reason())) {
            return item.reason().trim();
        }
        List<String> points = new ArrayList<>();
        for (MatchPointVO point : safeMatches(item.matches())) {
            if (!point.verdict().contains("未")) {
                points.add(point.dimension() + "：" + point.detail());
            }
            if (points.size() >= 2) {
                break;
            }
        }
        return points.isEmpty() ? "模型未给出推荐理由" : String.join("；", points);
    }

    /** 维度核对结果：丢掉空维度、限制条数，避免模型输出过长把提示词与前端撑爆 */
    private List<MatchPointVO> safeMatches(List<MatchItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null && StringUtils.hasText(item.dimension()))
                .limit(MATCH_MAX_ITEMS)
                .map(item -> new MatchPointVO(item.dimension().trim(),
                        textOrDefault(item.verdict(), "未知"),
                        textOrEmpty(item.detail())))
                .toList();
    }

    @PreDestroy
    void shutdownDeepReadExecutor() {
        deepReadExecutor.shutdownNow();
    }

    /** 一次模型调用的结果：正文 + 实际模型名 */
    private record ChatResult(String text, String model) {
    }

    /** 需求解析的结构化结果，字段与 prompts/recommend-analysis.txt 约定一致 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RequirementPlan(String summary, String projectType, List<String> techStack, List<String> features,
                                   String scale, List<String> constraints, List<String> keywords,
                                   List<String> languages, Integer minStars, Integer updatedWithinDays,
                                   String license, String notes) {

        /** 模型漏字段时不能直接 NPE：这里把 null 列表统一成空列表 */
        RequirementPlan {
            techStack = techStack == null ? List.of() : techStack;
            features = features == null ? List.of() : features;
            constraints = constraints == null ? List.of() : constraints;
            keywords = keywords == null ? List.of() : keywords;
            languages = languages == null ? List.of() : languages;
        }
    }

    /** 模型排序的单项结果，字段与 prompts/recommend-rank.txt 约定一致 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RankItem(String fullName, Integer score, String reason, List<MatchItem> matches,
                            List<String> highlights, List<String> risks) {
    }

    /** 逐项核对里的单项结果 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MatchItem(String dimension, String verdict, String detail) {
    }
}