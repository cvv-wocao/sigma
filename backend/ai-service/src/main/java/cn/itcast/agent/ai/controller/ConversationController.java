package cn.itcast.agent.ai.controller;

import cn.itcast.agent.ai.service.ConversationHistoryService;

import cn.itcast.agent.ai.vo.ConversationVO;
import cn.itcast.agent.ai.vo.MessageVO;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.AuthHeaders;
import cn.itcast.agent.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对话历史查询接口。
 * 对外路径由网关统一加 /api 前缀，网关 StripPrefix 后到达本服务的是 /ai/conversations/**。
 *
 * <p>当前登录用户从网关写入的请求头读取，只能查询自己的会话。</p>
 */
@RestController
@RequestMapping("/ai/conversations")
public class ConversationController {

    private final ConversationHistoryService conversationHistoryService;

    public ConversationController(ConversationHistoryService conversationHistoryService) {
        this.conversationHistoryService = conversationHistoryService;
    }

    /** 当前用户的会话列表。对外：GET /api/ai/conversations */
    @GetMapping
    public Result<List<ConversationVO>> listConversations(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(conversationHistoryService.listConversations(CurrentUser.requireId(authenticatedUserId)));
    }

    /** 某个会话的消息列表。对外：GET /api/ai/conversations/{conversationId}/messages */
    @GetMapping("/{conversationId}/messages")
    public Result<List<MessageVO>> listMessages(
            @PathVariable("conversationId") String conversationId,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(conversationHistoryService.listMessages(CurrentUser.requireId(authenticatedUserId), conversationId));
    }
}