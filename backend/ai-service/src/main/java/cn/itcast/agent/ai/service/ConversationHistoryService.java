package cn.itcast.agent.ai.service;

import cn.itcast.agent.ai.vo.ConversationVO;
import cn.itcast.agent.ai.vo.MessageVO;

import java.util.List;

/**
 * 对话历史（落库部分）业务接口。
 *
 * <p>与 Redis 短期记忆的分工：Redis 提供模型多轮对话所需的运行时上下文，会自动裁剪与过期；
 * 本服务提供「用户能看到的历史记录」，按用户与会话持久化在 MySQL 中，不参与模型上下文。</p>
 */
public interface ConversationHistoryService {

    /**
     * 记录一轮问答：写入用户消息与助手回复，并更新会话概要。
     *
     * @param userId         所属用户 ID（来自网关身份头）
     * @param conversationId 会话标识
     * @param userMessage    用户问题
     * @param assistantAnswer 助手回答
     * @param model          生成回答的模型名，可为空
     */
    void recordTurn(Long userId, String conversationId, String userMessage, String assistantAnswer, String model);

    /** 查询某用户的会话列表，按最后更新时间倒序 */
    List<ConversationVO> listConversations(Long userId);

    /** 查询某会话的消息列表，按时间正序；会话不属于该用户时抛业务异常 */
    List<MessageVO> listMessages(Long userId, String conversationId);
}