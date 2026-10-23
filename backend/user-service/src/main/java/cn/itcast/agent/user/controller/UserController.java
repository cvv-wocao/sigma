package cn.itcast.agent.user.controller;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.jwt.LoginUser;
import cn.itcast.agent.user.dto.LoginRequest;
import cn.itcast.agent.user.dto.RegisterRequest;
import cn.itcast.agent.user.dto.UpdateProfileRequest;
import cn.itcast.agent.user.service.UserService;
import cn.itcast.agent.user.vo.LoginVO;
import cn.itcast.agent.user.vo.UserInfoVO;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。
 * 网关以 /api/user/** 对外暴露，通过 StripPrefix 去掉 /api 后转发到本服务的 /user/**。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 注册：POST /api/user/register
     */
    @PostMapping("/register")
    public Result<UserInfoVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    /**
     * 登录：POST /api/user/login
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    /**
     * 当前登录用户：GET /api/user/me（需要携带 Authorization: Bearer <token>）
     */
    @GetMapping("/me")
    public Result<UserInfoVO> currentUser() {
        return Result.success(userService.getCurrentUser(currentUserId()));
    }

    /**
     * 更新当前登录用户资料：PUT /api/user/me
     */
    @PutMapping("/me")
    public Result<UserInfoVO> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateCurrentUser(currentUserId(), request));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser.id();
    }
}
