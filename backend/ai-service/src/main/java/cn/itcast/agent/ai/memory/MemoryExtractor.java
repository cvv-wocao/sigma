package cn.itcast.agent.ai.memory;

import cn.itcast.agent.ai.config.MemoryProperties;
import cn.itcast.agent.ai.entity.UserMemory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 长期记忆抽取器：从一轮问答里挑出「以后还用得上」的稳定信息。
 *
 * <p>这一步决定了长期记忆的质量：抽得太松，会把一次性的检索结果、仓库名、天气这种
 * 时间敏感内容也记下来，越攒越脏；抽得太紧，用户说过的重要偏好又留不下。
 * 所以判定规则全部写进 prompts/memory-extract.txt，这里只负责拼接输入、解析输出、
 * 把模型给的结果归一化成可以直接落库的形状。</p>
 *
 * <p>归一化（长度、类别、重要度、关键词条数）放在这里而不是落库处，是为了让
 * 「模型输出不可信」这件事实只在一个地方被处理，持久层可以假定拿到的一定合法。</p>
 */
@Component
public class MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractor.class);

    /** 提示词里用户问题的截断长度 */
    private static final int QUESTION_MAX_LENGTH = 1000;
    /** 提示词里助手回答的截断长度：抽取只需要大致语境，不必喂整篇长回答 */
    private static final int ANSWER_MAX_LENGTH = 1500;
    /** 单条记忆正文的最短长度，太短的多半是残句 */
    private static final int CONTENT_MIN_LENGTH = 4;
    /** 单条记忆正文的最大长度（数据库列为 500） */
    private static final int CONTENT_MAX_LENGTH = 300;
    /** 单条记忆最多保留几个关键词 */
    private static final int KEYWORDS_MAX_COUNT = 6;
    /** 关键词串的最大长度（数据库列为 255） */
    private static final int KEYWORDS_MAX_LENGTH = 200;
    /** 类别取值：不在这个集合里的一律按 FACT 处理，避免模型自由发挥写脏数据 */
    private static final Set<String> CATEGORIES = Set.of(
            UserMemory.CATEGORY_PROFILE,
            UserMemory.CATEGORY_PREFERENCE,
            UserMemory.CATEGORY_GOAL,
            UserMemory.CATEGORY_CONSTRAINT,
            UserMemory.CATEGORY_FACT);
    private static final int MIN_IMPORTANCE = 1;
    private static final int MAX_IMPORTANCE = 5;
    private static final int DEFAULT_IMPORTANCE = 3;

    private final ChatClient memoryExtractionChatClient;
    private final JsonMapper jsonMapper;
    private final MemoryProperties properties;

    public MemoryExtractor(@Qualifier("memoryExtractionChatClient") ChatClient memoryExtractionChatClient,
                           JsonMapper jsonMapper,
                           MemoryProperties properties) {
        this.memoryExtractionChatClient = memoryExtractionChatClient;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    /**
     * 抽取本轮问答里值得长期记住的信息。
     *
     * @param existingContents 该用户已有的记忆正文，交给模型避免重复抽取
     * @param question         本轮用户问题
     * @param answer           本轮助手回答
     * @return 归一化后的新记忆；没有可记的内容、模型输出不合法或调用失败时返回空列表
     */
    public List<MemoryDraft> extract(List<String> existingContents, String question, String answer) {
        if (!properties.active()) {
            return List.of();
        }
        List<String> existing = existingContents == null ? List.of() : existingContents;
        String raw;
        try {
            ChatResponse response = memoryExtractionChatClient.prompt()
                    .user(buildPrompt(existing, question, answer))
                    .call()
                    .chatResponse();
            raw = textOf(response);
        } catch (Exception e) {
            log.warn("长期记忆抽取调用失败，本轮跳过：{}", e.getMessage());
            return List.of();
        }
        if (!StringUtils.hasText(raw)) {
            log.debug("长期记忆抽取返回内容为空，本轮跳过");
            return List.of();
        }

        List<MemoryDraft> drafts = parse(raw);
        int limit = properties.extractMaxItems();
        if (drafts.size() > limit) {
            log.debug("长期记忆抽取条数超限，只保留前 {} 条：共 {} 条", limit, drafts.size());
            drafts = drafts.subList(0, limit);
        }
        return drafts;
    }

    /** 拼接抽取提示词：已有记忆在前，本轮对话在后，让模型只补充「新的」信息 */
    private String buildPrompt(List<String> existingContents, String question, String answer) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("【已有长期记忆】\n");
        if (existingContents.isEmpty()) {
            builder.append("（暂无）\n");
        } else {
            for (int i = 0; i < existingContents.size(); i++) {
                builder.append(i + 1).append(". ").append(existingContents.get(i)).append('\n');
            }
        }
        builder.append("\n【本轮对话】\n");
        builder.append("用户：").append(abbreviate(question, QUESTION_MAX_LENGTH)).append('\n');
        builder.append("助手：").append(abbreviate(answer, ANSWER_MAX_LENGTH)).append('\n');
        builder.append("\n请按系统提示词的要求输出 JSON 数组。");
        return builder.toString();
    }

    private String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private List<MemoryDraft> parse(String raw) {
        String json = extractJson(raw);
        if (json == null) {
            log.warn("长期记忆抽取结果里没有 JSON 数组，本轮跳过：{}", abbreviate(raw, 120));
            return List.of();
        }
        List<Draft> drafts;
        try {
            drafts = jsonMapper.readerFor(new TypeReference<List<Draft>>() { })
                    .with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                    .readValue(json);
        } catch (Exception e) {
            log.warn("长期记忆抽取结果解析失败，本轮跳过：{}", e.getMessage());
            return List.of();
        }
        if (drafts == null) {
            return List.of();
        }
        List<MemoryDraft> normalized = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            MemoryDraft memory = normalize(draft);
            if (memory != null) {
                normalized.add(memory);
            }
        }
        return normalized;
    }

    /** 模型常把 JSON 包在代码块或解释文字里，这里按首尾方括号截取 */
    private String extractJson(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    /** 归一化单条抽取结果；明显不合法的返回 null，由调用方丢弃 */
    private MemoryDraft normalize(Draft draft) {
        if (draft == null || !StringUtils.hasText(draft.content())) {
            return null;
        }
        String content = draft.content().trim();
        if (content.length() < CONTENT_MIN_LENGTH) {
            return null;
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            content = content.substring(0, CONTENT_MAX_LENGTH);
        }
        return new MemoryDraft(content, normalizeKeywords(draft.keywords()),
                normalizeCategory(draft.category()), normalizeImportance(draft.importance()));
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return UserMemory.CATEGORY_FACT;
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(normalized) ? normalized : UserMemory.CATEGORY_FACT;
    }

    private int normalizeImportance(Integer importance) {
        if (importance == null) {
            return DEFAULT_IMPORTANCE;
        }
        return Math.min(Math.max(importance, MIN_IMPORTANCE), MAX_IMPORTANCE);
    }

    /** 关键词用于召回打分：去重、去分隔符、限条数、限总长度 */
    private String normalizeKeywords(List<String> rawKeywords) {
        if (rawKeywords == null || rawKeywords.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        StringBuilder builder = new StringBuilder(64);
        for (String keyword : rawKeywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            String cleaned = keyword.trim().replaceAll("[,，;；]", "");
            if (cleaned.isEmpty() || unique.contains(cleaned)) {
                continue;
            }
            int extra = builder.length() == 0 ? cleaned.length() : cleaned.length() + 1;
            if (builder.length() + extra > KEYWORDS_MAX_LENGTH || unique.size() >= KEYWORDS_MAX_COUNT) {
                break;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(cleaned);
            unique.add(cleaned);
        }
        return builder.toString();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /**
     * 抽取结果：正文 + 逗号分隔关键词 + 类别 + 重要度，已归一化，调用方可直接落库。
     */
    public record MemoryDraft(String content, String keywords, String category, Integer importance) {

        public MemoryDraft {
            Objects.requireNonNull(content, "content");
        }
    }

    /** 模型返回的单条原始结果，字段与 prompts/memory-extract.txt 约定一致 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Draft(String content, List<String> keywords, String category, Integer importance) {
    }
}
