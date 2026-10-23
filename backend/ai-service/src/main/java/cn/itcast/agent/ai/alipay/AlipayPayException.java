package cn.itcast.agent.ai.alipay;

/** 支付宝接口、签名或报文异常；详细信息只进入日志。 */
public class AlipayPayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AlipayPayException(String message) {
        super(message);
    }

    public AlipayPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
