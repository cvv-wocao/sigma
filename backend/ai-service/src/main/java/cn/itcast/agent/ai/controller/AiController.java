package cn.itcast.agent.ai.controller;

import cn.itcast.agent.ai.dto.AgentRequest;
import cn.itcast.agent.ai.dto.ChatRequest;
import cn.itcast.agent.ai.dto.RecommendRequest;
import cn.itcast.agent.ai.service.AgentService;
import cn.itcast.agent.ai.service.ChatService;
import cn.itcast.agent.ai.service.RecommendService;
import cn.itcast.agent.ai.vo.AgentVO;
import cn.itcast.agent.ai.vo.ChatVO;
import cn.itcast.agent.ai.vo.RecommendVO;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.AuthHeaders;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对外接口。
 * 对外路径由网关统一加 /api 前缀，网关 StripPrefix 后到达本服务的是 /ai/**。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    private final ChatService chatService;
    private final AgentService agentService;
    private final RecommendService recommendService;

    public AiController(ChatService chatService, AgentService agentService, RecommendService recommendService) {
        this.chatService = chatService;
        this.agentService = agentService;
        this.recommendService = recommendService;
    }

    /**
     * 普通对话，不带工具与记忆。
     * 对外：POST /api/ai/chat
     */
    @PostMapping("/chat")
    public Result<ChatVO> chat(@Valid @RequestBody ChatRequest request,
                               @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId,
                               @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String authenticatedRole) {
        return Result.success(chatService.chat(request.message(), authenticatedUserId, authenticatedRole));
    }

    /**
     * Agent 对话，带工具调用（当前工具：仓库检索）与会话记忆。
     * 对外：POST /api/ai/agent
     *
     * <p>当前登录用户从网关写入的请求头读取（见 common-security 的 AuthHeaders）；
     * 网关会先删除客户端伪造的同名头，因此该值是可信任的。直接访问本服务（绕过网关）时
     * 才回退到请求体里的 userId。</p>
     */
    @PostMapping("/agent")
    public Result<AgentVO> agent(@Valid @RequestBody AgentRequest request,
                                 @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId,
                               @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String authenticatedRole) {
        return Result.success(agentService.agent(request, authenticatedUserId, authenticatedRole));
    }

    /**
     * AI 项目推荐：需求解析 -> 多关键词召回 -> 硬过滤 -> 模型排序 -> 结构化推荐。
     * 对外：POST /api/ai/recommend
     *
     * <p>与 /ai/agent 的差别：这里是一条确定性流水线，召回与排序都由后端控制，
     * 返回的是带匹配度与理由的结构化列表，而不是一段自由文本。</p>
     */
    @PostMapping("/recommend")
    public Result<RecommendVO> recommend(@Valid @RequestBody RecommendRequest request,
                                        @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId,
                               @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String authenticatedRole) {
        return Result.success(recommendService.recommend(request, authenticatedUserId, authenticatedRole));
    }
}
