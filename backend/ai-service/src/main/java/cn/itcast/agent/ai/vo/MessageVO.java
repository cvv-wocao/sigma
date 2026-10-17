package cn.itcast.agent.ai.vo;

/**
 * 会话消息（历史记录页右侧消息列表）。
 *
 * @param role       角色：USER / ASSISTANT / SYSTEM
 * @param content    消息文本
 * @param model      生成该消息的模型名，仅助手消息有值
 * @param createTime 创建时间
 */
public record MessageVO(String role, String content, String model, String createTime) {
}