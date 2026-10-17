package cn.itcast.agent.ai.vo;

import cn.itcast.agent.common.api.dto.GiteeRepository;

import java.util.List;

/**
 * 单条推荐结果：仓库原始信息 + 模型给出的匹配度与理由。
 *
 * <p>仓库信息直接复用共享 DTO，前端展示与「收藏到项目」都能沿用现有字段，
 * 不必为推荐再复制一份仓库结构。</p>
 *
 * @param repository 仓库信息（来自 GitHub / Gitee，source 字段标明来源）
 * @param score      匹配度 0-100；模型排序不可用时的兜底结果为 null
 * @param reason     推荐理由
 * @param matches    逐项对照用户需求的核对结果（技术栈 / 功能 / 规模 等）
 * @param highlights 与需求直接对应的优势
 * @param risks      需要注意的风险点
 */
public record RecommendationVO(
        GiteeRepository repository,
        Integer score,
        String reason,
        List<MatchPointVO> matches,
        List<String> highlights,
        List<String> risks
) {
}