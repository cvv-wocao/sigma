package cn.itcast.agent.ai.vo;

import java.math.BigDecimal;

/** 管理员的用户额度总览。 */
public record WalletOverviewVO(
        Long userId,
        String username,
        String nickname,
        String role,
        BigDecimal balance,
        BigDecimal totalConsumed,
        boolean admin
) {
}