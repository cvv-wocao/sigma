package cn.itcast.agent.common.api.dto;

/**
 * 仓库 README 摘要，服务间共享模型。
 *
 * <p>「AI 推荐」流水线用它做「项目深读」：只靠简介（description）判断项目太浅，
 * 简介写得差的优质项目会被低估，因此对初筛候选再取一次 README 正文交给模型分析。
 * README 只用于提示词，不做持久化。</p>
 *
 * @param fullName  仓库全名 owner/repo，用于把结果对应回候选
 * @param content   README 正文（Markdown 片段，服务端已按上限截断）；无 README 或来源不支持时为 null
 * @param truncated 是否因为超长被截断
 */
public record RepositoryReadme(String fullName, String content, boolean truncated) {
}