package cn.itcast.agent.ai.vo;

import java.math.BigDecimal;

/** 一次模型调用的 Token 与扣费记录。 */
public record UsageRecordVO(
        Long id,
        String scene,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal inputCost,
        BigDecimal outputCost,
        BigDecimal cost,
        long durationMs,
        String chargeMode,
        boolean platformToken,
        String createTime
) {
}
