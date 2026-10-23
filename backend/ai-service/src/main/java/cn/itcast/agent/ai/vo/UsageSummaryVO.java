package cn.itcast.agent.ai.vo;

import java.math.BigDecimal;

/** 指定时间窗口内的 Token 用量与扣费汇总。 */
public record UsageSummaryVO(
        long requestCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal inputCost,
        BigDecimal outputCost,
        BigDecimal usageCost,
        BigDecimal platformUsageCost,
        BigDecimal selfKeyUsageCost,
        BigDecimal totalCost,
        long chargedRequestCount,
        long platformRequestCount,
        long selfKeyRequestCount,
        long avgDurationMs,
        long maxDurationMs,
        String firstTime,
        String lastTime
) {
}
