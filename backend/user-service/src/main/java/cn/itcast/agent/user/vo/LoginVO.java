package cn.itcast.agent.user.vo;

import java.io.Serializable;

/**
 * 登录出参：令牌 + 用户信息。
 *
 * @param token     访问令牌
 * @param tokenType 令牌类型，前端放入 Authorization 头
 * @param expiresIn 有效期（秒）
 * @param user      用户信息
 */
public record LoginVO(
        String token,
        String tokenType,
        long expiresIn,
        UserInfoVO user
) implements Serializable {
}