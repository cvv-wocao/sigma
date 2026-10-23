package cn.itcast.agent.ai.vo;

import java.math.BigDecimal;

/** 当前用户的额度信息；金额均为元，保留 6 位小数。 */
public record WalletVO(
        Long userId,
        BigDecimal balance,
        BigDecimal totalRecharged,
        BigDecimal totalConsumed,
        BigDecimal inputPricePerMillionYuan,
        BigDecimal outputPricePerMillionYuan,
        String updateTime
) {
}