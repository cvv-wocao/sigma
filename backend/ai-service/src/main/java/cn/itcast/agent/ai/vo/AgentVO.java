package cn.itcast.agent.ai.vo;

/**
 * Agent 对话响应。
 *
 * @param answer         模型最终回答
 * @param model          实际使用的模型名
 * @param conversationId 本次会话标识，客户端需要保存并在后续请求中回传
 */
public record AgentVO(String answer, String model, String conversationId) {
}