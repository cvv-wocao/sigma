package cn.itcast.agent.ai.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 创建微信扫码充值订单。
 *
 * @param amountYuan 充值金额（元），最多两位小数
 */
public record CreateWechatOrderRequest(
        @NotNull(message = "请填写充值金额")
        @DecimalMin(value = "0.01", message = "充值金额至少 0.01 元")
        @Digits(integer = 5, fraction = 2, message = "充值金额最多两位小数，且不能超过 99999 元")
        BigDecimal amountYuan
) {
}
