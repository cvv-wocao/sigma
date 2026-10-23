package cn.itcast.agent.ai.payment.vo;

import java.math.BigDecimal;

/** 前端生成二维码与轮询支付状态所需的订单信息。 */
public record WechatOrderVO(
        String outTradeNo,
        BigDecimal amountYuan,
        String status,
        String codeUrl,
        String expireTime,
        String paidTime
) {
}
