package cn.itcast.agent.ai.vo;

/**
 * 普通对话响应。
 *
 * @param answer 模型回答
 * @param model  实际使用的模型名，便于排查问题
 */
public record ChatVO(String answer, String model) {
}