package cn.itcast.agent.common.security.jwt;

import java.io.Serializable;

/**
 * 登录用户（令牌载荷）：下游服务从令牌解析后放入安全上下文。
 *
 * @param id       用户 ID
 * @param username 登录名
 * @param nickname 昵称
 * @param role     角色：ADMIN / USER
 */
public record LoginUser(Long id, String username, String nickname, String role) implements Serializable {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    public LoginUser(Long id, String username, String nickname) {
        this(id, username, nickname, ROLE_USER);
    }

    public LoginUser {
        role = role == null || role.isBlank() ? ROLE_USER : role.trim().toUpperCase();
    }

    public boolean admin() {
        return ROLE_ADMIN.equals(role);
    }
}