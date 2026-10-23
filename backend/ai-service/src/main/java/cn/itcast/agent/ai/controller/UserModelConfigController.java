package cn.itcast.agent.ai.controller;

import cn.itcast.agent.ai.dto.UserModelConfigRequest;
import cn.itcast.agent.ai.service.UserModelConfigService;
import cn.itcast.agent.ai.vo.UserModelConfigVO;
import cn.itcast.agent.ai.vo.UserModelTestVO;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.AuthHeaders;
import cn.itcast.agent.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自带模型配置接口。
 *
 * <p>对外路径由网关加 /api 前缀：/api/ai/model-config。用户只能读写自己的配置，
 * 用户 ID 取自网关校验证书后写入的 X-User-Id。</p>
 */
@RestController
@RequestMapping("/ai/model-config")
public class UserModelConfigController {

    private final UserModelConfigService configService;

    public UserModelConfigController(UserModelConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public Result<UserModelConfigVO> get(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(configService.get(CurrentUser.requireId(authenticatedUserId)));
    }

    @PutMapping
    public Result<UserModelConfigVO> save(
            @Valid @RequestBody UserModelConfigRequest request,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(configService.save(CurrentUser.requireId(authenticatedUserId), request));
    }

    @PostMapping("/test")
    public Result<UserModelTestVO> test(
            @Valid @RequestBody UserModelConfigRequest request,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(configService.test(CurrentUser.requireId(authenticatedUserId), request));
    }

    @DeleteMapping
    public Result<Integer> delete(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(configService.delete(CurrentUser.requireId(authenticatedUserId)));
    }
}