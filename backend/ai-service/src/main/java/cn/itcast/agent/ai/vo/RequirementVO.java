package cn.itcast.agent.ai.vo;

import java.util.List;

/**
 * 需求解析结果：把用户的一整段自然语言需求显式化为可核对的维度与检索条件，
 * 前端展示出来便于用户确认「AI 是怎么理解我的需求的」。
 *
 * <p>它同时是后续「逐项核对」的维度来源：matches 里的 dimension 就取自这里的
 * projectType / techStack / features / scale / constraints。</p>
 *
 * @param summary           一到两句话复述用户需求
 * @param projectType       项目形态，例如「单体后台管理系统」；判断不出为空
 * @param techStack         识别出的技术栈（语言 / 框架 / 中间件）
 * @param features          识别出的功能点
 * @param scale             项目规模或使用场景；判断不出为空
 * @param constraints       其他硬性要求
 * @param keywords          实际用于检索的关键词
 * @param languages         作为检索条件的语言约束
 * @param minStars          Star 数下限
 * @param updatedWithinDays 只关注最近多少天内活跃的项目，null 表示不限
 * @param license           协议要求，null 表示不限
 * @param notes             模型的补充说明
 */
public record RequirementVO(
        String summary,
        String projectType,
        List<String> techStack,
        List<String> features,
        String scale,
        List<String> constraints,
        List<String> keywords,
        List<String> languages,
        Integer minStars,
        Integer updatedWithinDays,
        String license,
        String notes
) {
}