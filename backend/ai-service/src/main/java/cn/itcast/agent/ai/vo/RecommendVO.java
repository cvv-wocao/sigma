package cn.itcast.agent.ai.vo;

import java.util.List;

/**
 * AI 推荐响应。
 *
 * @param requirement     需求解析结果，便于用户核对检索条件
 * @param recommendations 按匹配度排序的推荐列表
 * @param candidateCount  本次实际参与排序的候选仓库数，用于判断召回是否过窄
 * @param model           生成结果所用模型名
 * @param conversationId  本次会话标识，客户端保存后可在历史记录中回看
 */
public record RecommendVO(
        RequirementVO requirement,
        List<RecommendationVO> recommendations,
        Integer candidateCount,
        String model,
        String conversationId
) {
}