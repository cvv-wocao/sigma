package cn.itcast.agent.user.service.impl;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.security.jwt.JwtTokenProvider;
import cn.itcast.agent.common.security.jwt.LoginUser;
import cn.itcast.agent.user.dto.LoginRequest;
import cn.itcast.agent.user.dto.RegisterRequest;
import cn.itcast.agent.user.entity.User;
import cn.itcast.agent.user.mapper.UserMapper;
import cn.itcast.agent.user.service.UserService;
import cn.itcast.agent.user.vo.LoginVO;
import cn.itcast.agent.user.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 用户业务实现。
 * 密码使用 BCrypt 加密存储，登录成功后签发 JWT 令牌，服务端不保存会话。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final String TOKEN_TYPE = "Bearer";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public UserInfoVO register(RegisterRequest request) {
        if (!Objects.equals(request.password(), request.confirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        String username = request.username().trim();
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : username);
        user.setEmail(StringUtils.hasText(request.email()) ? request.email().trim() : null);
        user.setStatus(User.STATUS_ENABLED);

        userMapper.insert(user);
        log.info("注册成功：userId={}, username={}", user.getId(), user.getUsername());
        return UserInfoVO.from(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        String username = request.username().trim();
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!Objects.equals(User.STATUS_ENABLED, user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname());
        String token = jwtTokenProvider.createToken(loginUser);
        log.info("登录成功：userId={}, username={}", user.getId(), user.getUsername());
        return new LoginVO(token, TOKEN_TYPE, jwtTokenProvider.getExpireSeconds(), UserInfoVO.from(user));
    }

    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserInfoVO.from(user);
    }
}