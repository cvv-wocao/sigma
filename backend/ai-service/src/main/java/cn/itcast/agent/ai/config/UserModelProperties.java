package cn.itcast.agent.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户自带模型配置。
 *
 * <p>数据库只保存 API Key 的密文，加密密钥由 AI_MODEL_ENCRYPTION_KEY 注入。
 * 生产环境必须替换默认值，否则更换部署环境后无法解密旧配置，也会降低密钥保护强度。</p>
 *
 * @param encryptionKey AES-GCM 加密口令，服务端通过 SHA-256 派生 256 位密钥
 */
@ConfigurationProperties(prefix = "ai.user-model")
public record UserModelProperties(String encryptionKey) {

    private static final String DEFAULT_KEY = "local-dev-only-change-me";

    public UserModelProperties {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            encryptionKey = DEFAULT_KEY;
        }
    }
}