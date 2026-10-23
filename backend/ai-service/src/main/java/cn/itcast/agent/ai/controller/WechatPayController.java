package cn.itcast.agent.ai.controller;

import cn.itcast.agent.ai.payment.WechatPayService;
import cn.itcast.agent.ai.payment.dto.CreateWechatOrderRequest;
import cn.itcast.agent.ai.payment.vo.WechatOrderVO;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.AuthHeaders;
import cn.itcast.agent.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 用户微信扫码充值接口；notify 由微信服务器调用，不能返回项目统一 Result 结构。 */
@RestController
@RequestMapping("/ai/wallet/wechat")
public class WechatPayController {

    private static final Logger log = LoggerFactory.getLogger(WechatPayController.class);

    private final WechatPayService wechatPayService;

    public WechatPayController(WechatPayService wechatPayService) {
        this.wechatPayService = wechatPayService;
    }

    @PostMapping("/orders")
    public Result<WechatOrderVO> createOrder(
            @Valid @RequestBody CreateWechatOrderRequest request,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(wechatPayService.createOrder(
                CurrentUser.requireId(authenticatedUserId), request));
    }

    @GetMapping("/orders/{outTradeNo}")
    public Result<WechatOrderVO> queryOrder(
            @PathVariable("outTradeNo") String outTradeNo,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(wechatPayService.queryOrder(
                CurrentUser.requireId(authenticatedUserId), outTradeNo));
    }

    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> notify(
            @RequestBody(required = false) String body,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        try {
            wechatPayService.handleNotification(body, timestamp, nonce, signature, serial);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            // 返回非 2xx 让微信按官方策略重试；详细原因只写日志，避免泄露验签与订单信息。
            log.error("微信支付回调处理失败：{}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "FAIL", "message", "支付通知处理失败"));
        }
    }
}
