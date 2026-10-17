package cn.itcast.agent.common.security.jwt;

import java.io.Serializable;

/**
 * 登录用户（令牌载荷）：下游服务从令牌解析后放入安全上下文。
 */
public record LoginUser(Long id, String username, String nickname) implements Serializable {
}