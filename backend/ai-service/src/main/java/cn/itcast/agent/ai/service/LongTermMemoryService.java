package cn.itcast.agent.ai.service;

import cn.itcast.agent.ai.entity.UserMemory;
import cn.itcast.agent.ai.vo.MemoryVO;

import java.util.List;

/**
 * 长期记忆业务接口。
 *
 * <p>与另外两种「记忆」的分工，三者互不替代：</p>
 * <ul>
 *   <li>Redis 短期记忆（ai.chat.memory.*）：本次会话的滑动窗口，会自动裁剪、会过期，只为多轮上下文服务；</li>
 *   <li>MySQL 对话历史（ConversationHistoryService）：完整原文，给人看，不参与模型上下文；</li>
 *   <li>本接口：跨会话的用户事实库，模型从中抽取稳定结论并按相关度回灌提示词。</li>
 * </ul>
 *
 * <p>所有方法都以 userId 为隔离边界，只读写该用户自己的记忆；userId 为空时一律空操作。</p>
 */
public interface LongTermMemoryService {

    /**
     * 召回与当前问题相关的长期记忆。
     *
     * <p>这是对话热路径上的调用：必须足够快，且失败不能影响回答，因此内部不允许抛异常，
     * 任何异常都退化为「没有召回」。</p>
     *
     * @param userId 用户 ID，为空时返回空列表
     * @param query  本轮用户问题，用于计算相关度
     * @return 按相关度排序的记忆，最多 recallTopK 条
     */
    List<UserMemory> recall(Long userId, String query);

    /**
     * 把本轮问答里值得长期记住的信息抽取并写入。
     *
     * <p>这是回答之后的附带动作：异步执行、失败只记日志，不影响已经返回给用户的结果。</p>
     *
     * @param userId         用户 ID，为空时直接跳过
     * @param conversationId 会话标识，记录在记忆的来源字段上便于追溯
     * @param question       本轮用户问题
     * @param answer         本轮助手回答
     */
    void remember(Long userId, String conversationId, String question, String answer);

    /** 查询当前用户的长期记忆，按重要度与更新时间排序 */
    List<MemoryVO> list(Long userId);

    /**
     * 清空当前用户的长期记忆。
     *
     * @return 删除条数
     */
    int clear(Long userId);
}
