package cn.itcast.agent.ai.alipay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 支付宝当面付配置。
 *
 * <p>采用“预下单 + 主动查单”，因此不需要 notify_url、备案域名或 HTTPS 回调入口。
 * 应用私钥和支付宝公钥通过只读文件挂载，不写入仓库。</p>
 */
@ConfigurationProperties(prefix = "ai.alipay-pay")
public record AlipayPayProperties(
        boolean enabled,
        String appId,
        String privateKeyPath,
        String alipayPublicKeyPath,
        String gatewayUrl,
        String subject,
        Integer expireMinutes,
        Long reconcileIntervalMs
) {

    private static final String PRODUCTION_GATEWAY = "https://openapi.alipay.com/gateway.do";
    private static final String DEFAULT_SUBJECT = "AI 平台 Token 额度充值";

    public AlipayPayProperties {
        appId = trim(appId);
        privateKeyPath = trim(privateKeyPath);
        alipayPublicKeyPath = trim(alipayPublicKeyPath);
        gatewayUrl = StringUtils.hasText(gatewayUrl) ? gatewayUrl.trim() : PRODUCTION_GATEWAY;
        subject = StringUtils.hasText(subject) ? subject.trim() : DEFAULT_SUBJECT;
        expireMinutes = expireMinutes == null ? 15 : Math.max(5, Math.min(expireMinutes, 120));
        reconcileIntervalMs = reconcileIntervalMs == null ? 60_000L : Math.max(10_000L, reconcileIntervalMs);
    }

    public boolean usable() {
        return enabled
                && StringUtils.hasText(appId)
                && StringUtils.hasText(privateKeyPath)
                && StringUtils.hasText(alipayPublicKeyPath)
                && StringUtils.hasText(gatewayUrl);
    }

    public String unavailableMessage() {
        if (!enabled) {
            return "支付宝支付尚未启用，请联系管理员";
        }
        return "支付宝支付配置不完整，请联系管理员检查 APPID、应用私钥和支付宝公钥";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
