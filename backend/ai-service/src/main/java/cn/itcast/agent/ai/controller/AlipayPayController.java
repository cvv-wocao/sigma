package cn.itcast.agent.ai.controller;

import cn.itcast.agent.ai.alipay.AlipayPayService;
import cn.itcast.agent.ai.alipay.dto.CreateAlipayOrderRequest;
import cn.itcast.agent.ai.alipay.vo.AlipayOrderVO;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.AuthHeaders;
import cn.itcast.agent.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝当面付充值接口。
 *
 * <p>本方案不接收异步回调，创建订单后由前端轮询，后台定时任务负责兜底查单。</p>
 */
@RestController
@RequestMapping("/ai/wallet/alipay")
public class AlipayPayController {

    private final AlipayPayService alipayPayService;

    public AlipayPayController(AlipayPayService alipayPayService) {
        this.alipayPayService = alipayPayService;
    }

    @PostMapping("/orders")
    public Result<AlipayOrderVO> createOrder(
            @Valid @RequestBody CreateAlipayOrderRequest request,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(alipayPayService.createOrder(
                CurrentUser.requireId(authenticatedUserId), request));
    }

    @GetMapping("/orders/{outTradeNo}")
    public Result<AlipayOrderVO> queryOrder(
            @PathVariable("outTradeNo") String outTradeNo,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(alipayPayService.queryOrder(
                CurrentUser.requireId(authenticatedUserId), outTradeNo));
    }
}
