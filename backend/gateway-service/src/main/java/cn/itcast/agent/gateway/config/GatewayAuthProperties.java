package cn.itcast.agent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 网关鉴权配置，对应 application.yml 中的 agent.auth.* 。
 *
 * @param whitelist 免鉴权路径，支持 Ant 风格通配（如 /api/user/login、/api/public/**）
 */
@ConfigurationProperties(prefix = "agent.auth")
public record GatewayAuthProperties(List<String> whitelist) {

    public GatewayAuthProperties {
        if (whitelist == null) {
            whitelist = List.of();
        }
    }
}