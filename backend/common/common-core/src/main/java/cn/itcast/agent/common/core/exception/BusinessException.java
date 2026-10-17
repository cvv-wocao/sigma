package cn.itcast.agent.common.core.exception;

import cn.itcast.agent.common.core.result.ErrorCode;

/**
 * 业务异常：由业务代码主动抛出，统一由全局异常处理器转换为 Result。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}