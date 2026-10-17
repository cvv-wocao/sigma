package cn.itcast.agent.ai.controller;

import cn.itcast.agent.ai.service.LongTermMemoryService;
import cn.itcast.agent.ai.vo.MemoryVO;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.AuthHeaders;
import cn.itcast.agent.common.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 长期记忆管理接口，让用户能看到并清空「模型记住了我什么」。
 *
 * <p>对外路径由网关统一加 /api 前缀，网关 StripPrefix 后到达本服务的是 /ai/memories 。
 * 当前登录用户从网关写入的请求头读取，只能操作自己的记忆。</p>
 */
@RestController
@RequestMapping("/ai/memories")
public class MemoryController {

    private final LongTermMemoryService longTermMemoryService;

    public MemoryController(LongTermMemoryService longTermMemoryService) {
        this.longTermMemoryService = longTermMemoryService;
    }

    /** 当前用户的长期记忆列表。对外：GET /api/ai/memories */
    @GetMapping
    public Result<List<MemoryVO>> list(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(longTermMemoryService.list(CurrentUser.requireId(authenticatedUserId)));
    }

    /** 清空当前用户的长期记忆。对外：DELETE /api/ai/memories */
    @DeleteMapping
    public Result<Integer> clear(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(longTermMemoryService.clear(CurrentUser.requireId(authenticatedUserId)));
    }
}
