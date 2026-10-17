package cn.itcast.agent.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gitee 外部数据源配置，对应 application.yml 中的 gitee.* 。
 *
 * @param baseUrl     Gitee 站点地址，默认 https://gitee.com
 * @param accessToken 个人访问令牌。仓库详情与分支接口免鉴权即可用；
 *                    关键词搜索接口必须携带令牌，否则 Gitee 只返回空数组。
 */
@ConfigurationProperties(prefix = "gitee")
public record GiteeProperties(String baseUrl, String accessToken) {

    public GiteeProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://gitee.com";
        }
        if (accessToken == null) {
            accessToken = "";
        }
    }
}