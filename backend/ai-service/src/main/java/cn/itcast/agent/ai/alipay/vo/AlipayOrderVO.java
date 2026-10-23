package cn.itcast.agent.ai.alipay.vo;

import java.math.BigDecimal;

/** 前端生成支付宝二维码与轮询支付状态所需的订单信息。 */
public record AlipayOrderVO(
        String outTradeNo,
        BigDecimal amountYuan,
        String status,
        String qrCode,
        String expireTime,
        String paidTime
) {
}
