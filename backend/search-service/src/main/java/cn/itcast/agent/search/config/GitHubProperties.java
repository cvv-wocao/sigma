package cn.itcast.agent.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub 外部数据源配置，对应 application.yml 中的 github.* 。
 *
 * @param baseUrl    API 地址，默认 https://api.github.com
 * @param webBaseUrl 网页地址，用于直接下载仓库归档，默认 https://github.com
 * @param token      访问令牌。未认证时搜索接口限流为 10 次/分钟，配置后为 30 次/分钟；
 *                   仓库检索本身不强制要求令牌，因此允许为空。
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String baseUrl, String webBaseUrl, String token) {

    public GitHubProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.github.com";
        }
        if (webBaseUrl == null || webBaseUrl.isBlank()) {
            webBaseUrl = "https://github.com";
        }
        if (token == null) {
            token = "";
        }
    }
}
