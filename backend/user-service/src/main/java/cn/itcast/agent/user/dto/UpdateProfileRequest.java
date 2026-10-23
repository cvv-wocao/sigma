package cn.itcast.agent.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 当前用户资料更新请求。
 *
 * <p>用户名是登录标识，不允许在这里修改；密码也不通过资料接口更新。</p>
 */
public record UpdateProfileRequest(

        @NotBlank(message = "昵称不能为空")
        @Size(max = 20, message = "昵称最长 20 个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱最长 100 个字符")
        String email,

        @Size(max = 20, message = "手机号最长 20 个字符")
        @Pattern(regexp = "^$|^[0-9+\\-\\s]{6,20}$", message = "手机号格式不正确")
        String phone,

        @Size(max = 255, message = "头像地址最长 255 个字符")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "头像地址必须是 http/https URL")
        String avatar
) {
}
