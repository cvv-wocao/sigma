package cn.itcast.agent.user.service;

import cn.itcast.agent.user.dto.LoginRequest;
import cn.itcast.agent.user.dto.RegisterRequest;
import cn.itcast.agent.user.dto.UpdateProfileRequest;
import cn.itcast.agent.user.vo.LoginVO;
import cn.itcast.agent.user.vo.UserInfoVO;

/**
 * 用户业务接口。
 */
public interface UserService {

    /**
     * 注册账号。
     */
    UserInfoVO register(RegisterRequest request);

    /**
     * 登录并签发令牌。
     */
    LoginVO login(LoginRequest request);

    /**
     * 查询当前登录用户信息。
     */
    UserInfoVO getCurrentUser(Long userId);

    /**
     * 更新当前登录用户的昵称、邮箱、手机号与头像地址。
     */
    UserInfoVO updateCurrentUser(Long userId, UpdateProfileRequest request);
}
