package cn.itcast.agent.common.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Gitee 分支信息，服务间共享模型。
 * 当前阶段只取分支名，Gitee 返回的 commit / protected 等字段直接忽略。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GiteeBranch(String name) {
}