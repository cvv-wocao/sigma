package cn.itcast.agent.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 一次模型调用的 Token 使用与扣费流水，对应 ai_token_usage。 */
@TableName("ai_token_usage")
public class TokenUsage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String scene;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long inputCostMicros;
    private Long outputCostMicros;
    private Long costMicros;
    private Long durationMs;
    private String chargeMode;
    private Boolean platformToken;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Long getInputCostMicros() { return inputCostMicros; }
    public void setInputCostMicros(Long inputCostMicros) { this.inputCostMicros = inputCostMicros; }
    public Long getOutputCostMicros() { return outputCostMicros; }
    public void setOutputCostMicros(Long outputCostMicros) { this.outputCostMicros = outputCostMicros; }
    public Long getCostMicros() { return costMicros; }
    public void setCostMicros(Long costMicros) { this.costMicros = costMicros; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getChargeMode() { return chargeMode; }
    public void setChargeMode(String chargeMode) { this.chargeMode = chargeMode; }
    public Boolean getPlatformToken() { return platformToken; }
    public void setPlatformToken(Boolean platformToken) { this.platformToken = platformToken; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
