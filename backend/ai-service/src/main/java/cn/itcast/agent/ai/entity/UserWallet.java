package cn.itcast.agent.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 用户平台 Token 钱包，对应 ai_user_wallet。金额单位均为微元。 */
@TableName("ai_user_wallet")
public class UserWallet implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private Long balanceMicros;
    private Long totalRechargedMicros;
    private Long totalConsumedMicros;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBalanceMicros() { return balanceMicros; }
    public void setBalanceMicros(Long balanceMicros) { this.balanceMicros = balanceMicros; }
    public Long getTotalRechargedMicros() { return totalRechargedMicros; }
    public void setTotalRechargedMicros(Long totalRechargedMicros) { this.totalRechargedMicros = totalRechargedMicros; }
    public Long getTotalConsumedMicros() { return totalConsumedMicros; }
    public void setTotalConsumedMicros(Long totalConsumedMicros) { this.totalConsumedMicros = totalConsumedMicros; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}