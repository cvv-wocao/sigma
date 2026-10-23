package cn.itcast.agent.ai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理员给用户充值平台额度。
 *
 * @param userId    被充值用户 ID
 * @param amountYuan 充值金额（元），最多两位小数
 * @param remark    备注，可为空
 */
public record RechargeRequest(
        @NotNull(message = "请选择要充值的用户") Long userId,
        @NotNull(message = "请填写充值金额")
        @DecimalMin(value = "0.01", message = "充值金额至少 0.01 元")
        @Digits(integer = 8, fraction = 2, message = "充值金额最多两位小数")
        BigDecimal amountYuan,
        @Size(max = 255, message = "备注不能超过 255 个字符") String remark
) {
}