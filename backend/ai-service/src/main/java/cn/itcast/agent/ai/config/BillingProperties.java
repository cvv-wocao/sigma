package cn.itcast.agent.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 平台模型 Token 计费配置。
 *
 * <p>价格单位是「元 / 百万 Token」，与 DeepSeek 等厂商的报价口径一致。
 * 余额与单次扣费在数据库中统一使用微元（1 元 = 1,000,000 微元），避免浮点误差。</p>
 */
@ConfigurationProperties(prefix = "ai.billing")
public record BillingProperties(BigDecimal inputPricePerMillionYuan,
                                BigDecimal outputPricePerMillionYuan) {

    public BillingProperties {
        if (inputPricePerMillionYuan == null || inputPricePerMillionYuan.signum() < 0) {
            inputPricePerMillionYuan = new BigDecimal("2.0");
        }
        if (outputPricePerMillionYuan == null || outputPricePerMillionYuan.signum() < 0) {
            outputPricePerMillionYuan = new BigDecimal("8.0");
        }
    }
}