package cn.itcast.agent.ai.vo;

/**
 * 「逐项对照用户需求」的核对结果。
 *
 * <p>有了它，用户能直接看到模型是按哪几个维度判断的、哪一项没命中，
 * 而不是只拿到一个分数和一句笼统的夸奖。</p>
 *
 * @param dimension 需求维度名，取自用户需求（技术栈 / 功能 / 规模 等）
 * @param verdict   结论：命中 / 部分命中 / 未命中
 * @param detail    判断依据（来自简介还是 README 摘要）
 */
public record MatchPointVO(String dimension, String verdict, String detail) {
}