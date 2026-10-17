package cn.itcast.agent.user.vo;

import cn.itcast.agent.user.entity.User;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息出参（不含密码）。
 */
public record UserInfoVO(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        String avatar,
        LocalDateTime createTime
) implements Serializable {

    public static UserInfoVO from(User user) {
        return new UserInfoVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getCreateTime());
    }
}