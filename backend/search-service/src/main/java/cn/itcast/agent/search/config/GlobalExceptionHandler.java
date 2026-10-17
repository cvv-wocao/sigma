package cn.itcast.agent.search.config;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一转换为 Result，避免堆栈直接暴露给前端。
 * 这里的提示语保持「来源无关」：检索来源可能是 GitHub 或 Gitee，
 * 写死平台名会把排查方向带偏。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    /** 必填查询参数缺失，例如没有传 keyword */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数：{}", e.getParameterName());
        return Result.error(ErrorCode.PARAM_ERROR, "缺少必要参数：" + e.getParameterName());
    }

    /** 外部数据源返回 4xx：区分 404 / 鉴权失败 / 限流，便于定位问题 */
    @ExceptionHandler(HttpClientErrorException.class)
    public Result<Void> handleHttpClientErrorException(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        log.warn("调用外部数据源返回客户端错误：status={}, body={}", status, e.getResponseBodyAsString());
        if (status == 404) {
            return Result.error(ErrorCode.NOT_FOUND, "该仓库在数据源上不存在");
        }
        if (status == 401 || status == 403) {
            return Result.error(ErrorCode.SERVER_ERROR, "外部数据源令牌无效或权限不足（也可能是触发限流）");
        }
        if (status == 429) {
            return Result.error(ErrorCode.SERVER_ERROR, "外部数据源调用频率超限，请稍后重试");
        }
        return Result.error(ErrorCode.SERVER_ERROR, "调用外部数据源失败，状态码：" + status);
    }

    /** 其余外部调用失败：网络不通、DNS 解析失败、数据源返回 5xx 等 */
    @ExceptionHandler(RestClientException.class)
    public Result<Void> handleRestClientException(RestClientException e) {
        log.error("调用外部数据源失败", e);
        return Result.error(ErrorCode.SERVER_ERROR, "调用外部数据源失败，请稍后重试");
    }

    /**
     * 访问不存在的路径（含未暴露的 actuator 端点）：按 404 返回，不要笼统报成 500。
     * 覆盖 Exception 的兜底处理会把 404 变成「服务器内部错误」，对排查问题不友好。
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