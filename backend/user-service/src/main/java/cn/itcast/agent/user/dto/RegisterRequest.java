package cn.itcast.agent.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 20, message = "用户名长度需为 4-20 个字符")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需为 6-32 个字符")
        String password,

        @NotBlank(message = "确认密码不能为空")
        String confirmPassword,

        @Size(max = 20, message = "昵称最长 20 个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱最长 100 个字符")
        String email
) {
}