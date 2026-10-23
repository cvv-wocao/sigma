package cn.itcast.agent.common.core.result;

/**
 * 平台统一错误码。
 * 2xx：成功；4xx：参数与认证问题；5xx：服务端问题；1xxx：业务错误。
 */
public enum ErrorCode {

    SUCCESS(200, "操作成功"),

    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "资源不存在"),

    USERNAME_EXISTS(1001, "用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    USER_DISABLED(1003, "账号已被禁用"),
    PASSWORD_NOT_MATCH(1004, "两次输入的密码不一致"),

    TOKEN_INVALID(1010, "登录凭证无效，请重新登录"),
    TOKEN_EXPIRED(1011, "登录已过期，请重新登录"),

    PROJECT_ALREADY_SAVED(1101, "该项目已在收藏列表中"),

    AI_BALANCE_INSUFFICIENT(1201, "平台额度不足，请充值或配置自己的模型 API"),

    SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}