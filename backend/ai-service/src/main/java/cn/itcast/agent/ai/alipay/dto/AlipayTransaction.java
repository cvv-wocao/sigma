package cn.itcast.agent.ai.alipay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付宝查单结果的关键字段。 */
public record AlipayTransaction(
        String outTradeNo,
        String tradeNo,
        String tradeStatus,
        BigDecimal totalAmount,
        LocalDateTime paidTime
) {
}
