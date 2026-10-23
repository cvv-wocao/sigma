package cn.itcast.agent.ai.service;

import cn.itcast.agent.ai.vo.ChatVO;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.security.CurrentUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * 普通对话：一问一答，不带工具调用与会话记忆。
 *
 * <p>登录用户如果启用了自己的模型，使用用户的 Base URL、API Key 和模型名；
 * 未配置时回退平台默认 DeepSeek。平台 Token 对普通用户按实际用量扣余额，管理员免扣费。</p>
 */
@Service
public class ChatService {

    private final UserModelClientProvider userModelClientProvider;
    private final BillingService billingService;

    public ChatService(UserModelClientProvider userModelClientProvider, BillingService billingService) {
        this.userModelClientProvider = userModelClientProvider;
        this.billingService = billingService;
    }

    public ChatVO chat(String message, String authenticatedUserId, String authenticatedRole) {
        Long userId = CurrentUser.parseIdOrNull(authenticatedUserId);
        boolean admin = billingService.isAdmin(userId, authenticatedRole);
        UserModelClientProvider.ModelRoute route = userModelClientProvider.chat(userId);
        billingService.requirePlatformAccess(userId, admin, route.platformToken());

        ChatClient chatClient = route.client();
        long startedAt = System.nanoTime();
        ChatResponse response = chatClient.prompt()
                .user(message)
                .call()
                .chatResponse();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        billingService.recordUsage(userId, admin, route.platformToken(), "CHAT", response, durationMs);

        if (response == null || response.getResult() == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "模型未返回有效结果");
        }

        String answer = response.getResult().getOutput().getText();
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new ChatVO(answer, model);
    }
}
