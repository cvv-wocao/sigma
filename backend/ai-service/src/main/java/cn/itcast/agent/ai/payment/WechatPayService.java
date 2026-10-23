package cn.itcast.agent.ai.payment;

import cn.itcast.agent.ai.payment.dto.CreateWechatOrderRequest;
import cn.itcast.agent.ai.payment.vo.WechatOrderVO;

/** 用户微信扫码充值与微信支付回调处理。 */
public interface WechatPayService {

    WechatOrderVO createOrder(Long userId, CreateWechatOrderRequest request);

    WechatOrderVO queryOrder(Long userId, String outTradeNo);

    void handleNotification(String body, String timestamp, String nonce,
                            String signature, String serial);
}
