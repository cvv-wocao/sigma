package cn.itcast.agent.ai.config;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

/**
 * 全局异常处理：统一转换为 Result，避免堆栈直接暴露给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败：{}", message);
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR, "请求体格式不正确");
    }

    /** 模型调用不可重试失败：密钥错误、余额不足、模型名不存在等 */
    @ExceptionHandler(NonTransientAiException.class)
    public Result<Void> handleNonTransientAiException(NonTransientAiException e) {
        log.error("模型调用失败（不可重试）：{}", e.getMessage());
        return Result.error(ErrorCode.SERVER_ERROR, "模型调用失败，请检查 API Key、余额与模型名");
    }

    /** 模型调用可重试失败：网络抖动、限流等 */
    @ExceptionHandler(TransientAiException.class)
    public Result<Void> handleTransientAiException(TransientAiException e) {
        log.error("模型调用失败（可重试）：{}", e.getMessage());
        return Result.error(ErrorCode.SERVER_ERROR, "模型服务暂时不可用，请稍后重试");
    }

    /**
     * 访问不存在的路径（含未暴露的 actuator 端点）：按 404 返回，不要笼统报成 500。
     * 覆盖 Exception 的兜底处理会把 404 变成“服务器内部错误”，对排查问题不友好。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("请求路径不存在：{}", e.getResourcePath());
        return Result.error(ErrorCode.NOT_FOUND, "请求路径不存在");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.SERVER_ERROR);
    }
}