package cn.itcast.agent.ai.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 微信支付 API v3（Native 扫码支付）配置。
 *
 * <p>商户私钥与微信支付平台证书不放进仓库，生产环境通过只读目录挂载到容器。
 * 未配置时支付功能保持关闭，管理员手工充值不受影响。</p>
 */
@ConfigurationProperties(prefix = "ai.wechat-pay")
public record WechatPayProperties(
        boolean enabled,
        String appId,
        String mchId,
        String apiV3Key,
        String merchantSerialNumber,
        String privateKeyPath,
        String platformCertPath,
        String notifyUrl,
        String description,
        String baseUrl,
        Integer expireMinutes
) {

    private static final String DEFAULT_BASE_URL = "https://api.mch.weixin.qq.com";
    private static final String DEFAULT_DESCRIPTION = "AI 平台 Token 额度充值";

    public WechatPayProperties {
        appId = trim(appId);
        mchId = trim(mchId);
        apiV3Key = trim(apiV3Key);
        merchantSerialNumber = trim(merchantSerialNumber);
        privateKeyPath = trim(privateKeyPath);
        platformCertPath = trim(platformCertPath);
        notifyUrl = trim(notifyUrl);
        description = StringUtils.hasText(description) ? description.trim() : DEFAULT_DESCRIPTION;
        baseUrl = StringUtils.hasText(baseUrl) ? trimTrailingSlash(baseUrl.trim()) : DEFAULT_BASE_URL;
        expireMinutes = expireMinutes == null ? 15 : Math.max(5, Math.min(expireMinutes, 120));
    }

    /** 配置完整且已启用时才允许创建支付订单。 */
    public boolean usable() {
        return enabled
                && StringUtils.hasText(appId)
                && StringUtils.hasText(mchId)
                && StringUtils.hasText(apiV3Key)
                && StringUtils.hasText(merchantSerialNumber)
                && StringUtils.hasText(privateKeyPath)
                && StringUtils.hasText(platformCertPath)
                && StringUtils.hasText(notifyUrl);
    }

    /** 给接口返回的友好提示，不暴露任何密钥内容。 */
    public String unavailableMessage() {
        if (!enabled) {
            return "微信支付尚未启用，请联系管理员";
        }
        return "微信支付配置不完整，请联系管理员检查商户号、证书和回调地址";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
