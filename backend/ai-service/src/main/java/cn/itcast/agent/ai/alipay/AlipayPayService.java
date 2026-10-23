package cn.itcast.agent.ai.alipay;

import cn.itcast.agent.ai.alipay.dto.CreateAlipayOrderRequest;
import cn.itcast.agent.ai.alipay.vo.AlipayOrderVO;

/** 支付宝当面付充值与主动查单。 */
public interface AlipayPayService {

    AlipayOrderVO createOrder(Long userId, CreateAlipayOrderRequest request);

    AlipayOrderVO queryOrder(Long userId, String outTradeNo);

    /** 后台补偿查单：处理用户支付后关闭页面的情况。 */
    void reconcilePendingOrders();
}
