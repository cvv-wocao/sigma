package cn.itcast.agent.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 推荐流水线配置，对应 application.yml 中的 ai.recommend.* 。
 *
 * <p>这里的取值都会在紧凑构造器里兜底与夹紧，避免配置写错导致「关键词过多把
 * 外部接口打爆」「候选池过大把提示词撑爆」或「深读条数过多触发平台限流」。</p>
 *
 * @param maxKeywords      单次把需求拆成多少个检索关键词（上限 8）
 * @param perKeyword       每个关键词向检索服务要多少条（上限 30）
 * @param candidatePoolMax 送入模型排序的候选上限（上限 50）
 * @param topN             默认推荐条数（上限 10）
 * @param deepReadMax      最多对多少条候选取 README 正文做「项目深读」（上限 30）；
 *                         每深读一条就是一次外部接口调用，取值直接决定耗时与限流风险
 * @param readmeMaxLength  每条 README 截断到多少字符后进入提示词（上限 8000）
 */
@ConfigurationProperties(prefix = "ai.recommend")
public record RecommendProperties(Integer maxKeywords, Integer perKeyword, Integer candidatePoolMax, Integer topN,
                                  Integer deepReadMax, Integer readmeMaxLength) {

    private static final int KEYWORDS_LIMIT = 8;
    private static final int PER_KEYWORD_LIMIT = 30;
    private static final int POOL_LIMIT = 50;
    private static final int TOP_N_LIMIT = 10;
    private static final int DEEP_READ_LIMIT = 30;
    private static final int README_LENGTH_LIMIT = 8000;

    public RecommendProperties {
        maxKeywords = clamp(maxKeywords, 6, 1, KEYWORDS_LIMIT);
        perKeyword = clamp(perKeyword, 15, 1, PER_KEYWORD_LIMIT);
        candidatePoolMax = clamp(candidatePoolMax, 30, 1, POOL_LIMIT);
        topN = clamp(topN, 5, 1, TOP_N_LIMIT);
        deepReadMax = clamp(deepReadMax, 12, 0, DEEP_READ_LIMIT);
        readmeMaxLength = clamp(readmeMaxLength, 1500, 200, README_LENGTH_LIMIT);
    }

    private static int clamp(Integer value, int defaultValue, int min, int max) {
        if (value == null || value < min) {
            return defaultValue;
        }
        return Math.min(value, max);
    }
}