package cn.itcast.agent.ai.service;

import cn.itcast.agent.ai.vo.ChatVO;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 普通对话：一问一答，不带工具调用与会话记忆。
 * 注入时用 chatClient 限定符，避免与带工具的 agentChatClient 冲突。
 */
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(@Qualifier("chatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatVO chat(String message) {
        ChatResponse response = chatClient.prompt()
                .user(message)
                .call()
                .chatResponse();

        if (response == null || response.getResult() == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "模型未返回有效结果");
        }

        String answer = response.getResult().getOutput().getText();
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new ChatVO(answer, model);
    }
}