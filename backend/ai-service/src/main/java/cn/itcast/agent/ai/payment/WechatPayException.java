package cn.itcast.agent.ai.payment;

/** 微信支付接口、签名或报文异常；异常信息只进入日志，不直接返回给前端。 */
public class WechatPayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WechatPayException(String message) {
        super(message);
    }

    public WechatPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
