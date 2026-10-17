package cn.itcast.agent.common.security;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;

/**
 * 当前登录用户的解析工具。
 *
 * <p>用户身份由网关校验 JWT 后写入 X-User-Id 请求头（见 {@link AuthHeaders}），
 * 网关会先删除客户端伪造的同名头再重新写入，因此通过网关进来的该头是可信的。</p>
 *
 * <p>放在 common-security 是为了让各业务服务共用同一套解析规则，
 * 避免每个服务各写一份、对「缺失」与「非法」的处理不一致。</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 严格解析：用于「必须知道是谁」的接口，缺失或格式不正确直接按未登录处理。
     *
     * @param headerValue 请求头 X-User-Id 的值，可为 null
     * @return 用户 ID
     * @throws BusinessException 缺失或不是合法数字时抛出未登录错误
     */
    public static Long requireId(String headerValue) {
        Long userId = parseIdOrNull(headerValue);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少登录用户信息，请登录后通过网关访问");
        }
        return userId;
    }

    /**
     * 宽松解析：用于「能记录就记录」的场景，解析不出返回 null，由调用方决定是否跳过。
     * 例如绕过网关直接访问后端服务时该头为空，此时跳过与用户相关的写入，业务本身仍然可用。
     *
     * @param headerValue 请求头 X-User-Id 的值，可为 null
     * @return 用户 ID，无法解析时返回 null
     */
    public static Long parseIdOrNull(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(headerValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}