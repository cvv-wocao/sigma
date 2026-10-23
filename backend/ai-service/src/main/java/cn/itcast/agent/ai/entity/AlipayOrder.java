package cn.itcast.agent.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 支付宝当面付订单，对应 ai_alipay_order。 */
@TableName("ai_alipay_order")
public class AlipayOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String outTradeNo;
    private Long userId;
    private Integer amountFen;
    private Long amountMicros;
    private String status;
    private String qrCode;
    private String tradeNo;
    private LocalDateTime paidTime;
    private LocalDateTime expireTime;
    private LocalDateTime lastCheckedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOutTradeNo() { return outTradeNo; }
    public void setOutTradeNo(String outTradeNo) { this.outTradeNo = outTradeNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getAmountFen() { return amountFen; }
    public void setAmountFen(Integer amountFen) { this.amountFen = amountFen; }
    public Long getAmountMicros() { return amountMicros; }
    public void setAmountMicros(Long amountMicros) { this.amountMicros = amountMicros; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public LocalDateTime getPaidTime() { return paidTime; }
    public void setPaidTime(LocalDateTime paidTime) { this.paidTime = paidTime; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public LocalDateTime getLastCheckedTime() { return lastCheckedTime; }
    public void setLastCheckedTime(LocalDateTime lastCheckedTime) { this.lastCheckedTime = lastCheckedTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
