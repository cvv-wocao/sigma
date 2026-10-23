package cn.itcast.agent.ai.payment.dto;

/**
 * 微信支付交易的关键字段，用于统一解析查询响应与回调解密后的报文。
 */
public record WechatTransaction(
        String appId,
        String mchId,
        String outTradeNo,
        String transactionId,
        String tradeState,
        long amountFen,
        String currency,
        String successTime
) {
}
