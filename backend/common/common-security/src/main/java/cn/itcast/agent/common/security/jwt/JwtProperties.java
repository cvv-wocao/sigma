package cn.itcast.agent.common.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置，对应 application.yml 中的 agent.auth.jwt.*。
 * 使用方通过 @EnableConfigurationProperties(JwtProperties.class) 注册。
 */
@ConfigurationProperties(prefix = "agent.auth.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求至少 32 字节，生产环境务必通过环境变量覆盖 */
    private String secret = "java-agent-platform-dev-secret-key-change-me-please";

    /** 令牌有效期（分钟） */
    private long expireMinutes = 120;

    /** 签发者 */
    private String issuer = "java-agent-platform";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(long expireMinutes) {
        this.expireMinutes = expireMinutes;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}