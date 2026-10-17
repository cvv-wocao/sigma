package cn.itcast.agent.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索策略配置，对应 application.yml 中的 search.* 。
 *
 * @param provider              主检索来源：github / gitee，默认 github
 *                              （Gitee 官方 search/repositories 当前恒返回空数组，不适合做主力来源）
 * @param fallbackEnabled       主来源无结果时是否依次尝试其余来源
 * @param maxResults            批量检索去重后返回的候选上限
 * @param maxDescriptionLength  简介清洗后的最大字符数，超出截断（前端卡片与提示词都按它控制体积）
 * @param spamDescriptionLength 简介原始长度超过该值即判定为关键词堆砌的垃圾仓库
 * @param spamFilterEnabled     是否在检索结果里剔除关键词堆砌的垃圾仓库
 * @param maxReadmeLength       README 清洗后的最大字符数
 */
@ConfigurationProperties(prefix = "search")
public record RepositorySearchProperties(String provider, Boolean fallbackEnabled, Integer maxResults,
                                         Integer maxDescriptionLength, Integer spamDescriptionLength,
                                         Boolean spamFilterEnabled, Integer maxReadmeLength) {

    public static final String PROVIDER_GITHUB = "github";
    public static final String PROVIDER_GITEE = "gitee";

    public RepositorySearchProperties {
        if (provider == null || provider.isBlank()) {
            provider = PROVIDER_GITHUB;
        }
        provider = provider.trim().toLowerCase();
        if (fallbackEnabled == null) {
            fallbackEnabled = Boolean.TRUE;
        }
        if (maxResults == null || maxResults < 1) {
            maxResults = 30;
        }
        if (maxDescriptionLength == null || maxDescriptionLength < 20) {
            maxDescriptionLength = 200;
        }
        if (spamDescriptionLength == null || spamDescriptionLength < 1) {
            spamDescriptionLength = 1000;
        }
        if (spamFilterEnabled == null) {
            spamFilterEnabled = Boolean.TRUE;
        }
        if (maxReadmeLength == null || maxReadmeLength < 100) {
            maxReadmeLength = 4000;
        }
    }
}