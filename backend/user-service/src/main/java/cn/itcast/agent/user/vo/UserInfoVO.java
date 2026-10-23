package cn.itcast.agent.user.vo;

import cn.itcast.agent.user.entity.User;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息出参（不含密码）。
 *
 * @param role ADMIN / USER，前端据此显示管理员入口
 */
public record UserInfoVO(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        String avatar,
        String role,
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
                user.getRole() == null || user.getRole().isBlank() ? User.ROLE_USER : user.getRole(),
                user.getCreateTime());
    }
}