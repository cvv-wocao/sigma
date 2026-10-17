package cn.itcast.agent.ai.vo;

/**
 * 会话列表项（历史记录页左侧列表）。
 *
 * <p>时间统一格式化为字符串（yyyy-MM-dd HH:mm:ss），避免前后端对日期序列化格式产生隐式约定。</p>
 *
 * @param conversationId 会话标识，前端拉取消息时回传
 * @param title          会话标题（首条用户消息摘要）
 * @param messageCount   消息条数
 * @param createTime     创建时间
 * @param updateTime     最后更新时间
 */
public record ConversationVO(
        String conversationId,
        String title,
        Integer messageCount,
        String createTime,
        String updateTime
) {
}