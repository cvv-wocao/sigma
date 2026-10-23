package cn.itcast.agent.ai.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Token 流水聚合查询结果。 */
public class TokenUsageSummaryRow {

    private Long requestCount;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Long inputCostMicros;
    private Long outputCostMicros;
    private Long usageCostMicros;
    private Long platformUsageCostMicros;
    private Long selfKeyUsageCostMicros;
    private Long costMicros;
    private Long chargedRequestCount;
    private Long platformRequestCount;
    private Long selfKeyRequestCount;
    private BigDecimal avgDurationMs;
    private Long maxDurationMs;
    private LocalDateTime firstTime;
    private LocalDateTime lastTime;

    public Long getRequestCount() { return requestCount; }
    public void setRequestCount(Long requestCount) { this.requestCount = requestCount; }
    public Long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Long promptTokens) { this.promptTokens = promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Long completionTokens) { this.completionTokens = completionTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    public Long getInputCostMicros() { return inputCostMicros; }
    public void setInputCostMicros(Long inputCostMicros) { this.inputCostMicros = inputCostMicros; }
    public Long getOutputCostMicros() { return outputCostMicros; }
    public void setOutputCostMicros(Long outputCostMicros) { this.outputCostMicros = outputCostMicros; }
    public Long getUsageCostMicros() { return usageCostMicros; }
    public void setUsageCostMicros(Long usageCostMicros) { this.usageCostMicros = usageCostMicros; }
    public Long getPlatformUsageCostMicros() { return platformUsageCostMicros; }
    public void setPlatformUsageCostMicros(Long platformUsageCostMicros) { this.platformUsageCostMicros = platformUsageCostMicros; }
    public Long getSelfKeyUsageCostMicros() { return selfKeyUsageCostMicros; }
    public void setSelfKeyUsageCostMicros(Long selfKeyUsageCostMicros) { this.selfKeyUsageCostMicros = selfKeyUsageCostMicros; }
    public Long getCostMicros() { return costMicros; }
    public void setCostMicros(Long costMicros) { this.costMicros = costMicros; }
    public Long getChargedRequestCount() { return chargedRequestCount; }
    public void setChargedRequestCount(Long chargedRequestCount) { this.chargedRequestCount = chargedRequestCount; }
    public Long getPlatformRequestCount() { return platformRequestCount; }
    public void setPlatformRequestCount(Long platformRequestCount) { this.platformRequestCount = platformRequestCount; }
    public Long getSelfKeyRequestCount() { return selfKeyRequestCount; }
    public void setSelfKeyRequestCount(Long selfKeyRequestCount) { this.selfKeyRequestCount = selfKeyRequestCount; }
    public BigDecimal getAvgDurationMs() { return avgDurationMs; }
    public void setAvgDurationMs(BigDecimal avgDurationMs) { this.avgDurationMs = avgDurationMs; }
    public Long getMaxDurationMs() { return maxDurationMs; }
    public void setMaxDurationMs(Long maxDurationMs) { this.maxDurationMs = maxDurationMs; }
    public LocalDateTime getFirstTime() { return firstTime; }
    public void setFirstTime(LocalDateTime firstTime) { this.firstTime = firstTime; }
    public LocalDateTime getLastTime() { return lastTime; }
    public void setLastTime(LocalDateTime lastTime) { this.lastTime = lastTime; }
}
