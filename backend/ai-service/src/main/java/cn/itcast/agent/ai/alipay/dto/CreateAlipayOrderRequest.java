package cn.itcast.agent.ai.alipay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 创建支付宝当面付充值订单。 */
public record CreateAlipayOrderRequest(
        @NotNull(message = "请填写充值金额")
        @DecimalMin(value = "0.01", message = "充值金额至少 0.01 元")
        @Digits(integer = 5, fraction = 2, message = "充值金额最多两位小数，且不能超过 99999 元")
        BigDecimal amountYuan
) {
}
