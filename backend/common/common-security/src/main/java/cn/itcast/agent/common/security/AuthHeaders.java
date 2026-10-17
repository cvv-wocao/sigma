package cn.itcast.agent.common.security;

/**
 * 服务间传递登录身份用的请求头名称。
 *
 * <p>网关校验 JWT 通过后，会把身份写入这些请求头再转发给下游服务；
 * 下游服务读取它们即可拿到当前登录用户，无需再次解析令牌。
 * 集中放在这里，避免网关与业务服务各写一份字符串而写错。</p>
 *
 * <p>安全约定：网关会无条件删除客户端传入的这些头再重新写入，
 * 因此下游可以认为「通过网关进来的请求」这些头是可信的。</p>
 */
public final class AuthHeaders {

    /** 用户 ID */
    public static final String USER_ID = "X-User-Id";
    /** 登录名 */
    public static final String USERNAME = "X-Username";
    /** 昵称 */
    public static final String NICKNAME = "X-User-Nickname";

    private AuthHeaders() {
    }
}