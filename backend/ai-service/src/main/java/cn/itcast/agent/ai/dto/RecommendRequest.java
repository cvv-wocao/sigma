package cn.itcast.agent.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 推荐请求：用户用自己的话描述想要的项目，由后端完成
 * 「需求解析 -> 多关键词召回 -> 硬过滤 -> 模型排序 -> 结构化推荐」。
 *
 * @param message        用户需求描述，例如「我想找一个能快速搭后台管理系统的 Java 项目，
 *                       要有权限管理和代码生成，最好活跃维护」
 * @param userId         用户标识，用于会话历史落库；网关接入 JWT 后以网关注入的为准
 * @param conversationId 会话标识，不传则自动生成并在响应中返回
 * @param topN           期望推荐条数，不传用服务端默认值
 */
public record RecommendRequest(
        @NotBlank(message = "需求描述不能为空")
        @Size(max = 2000, message = "需求描述不能超过 2000 个字符")
        String message,

        @Size(max = 64, message = "userId 长度不能超过 64 个字符")
        String userId,

        @Size(max = 64, message = "conversationId 长度不能超过 64 个字符")
        String conversationId,

        @Min(value = 1, message = "推荐条数至少为 1")
        @Max(value = 10, message = "推荐条数最多为 10")
        Integer topN
) {
}