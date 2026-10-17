package cn.itcast.agent.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agent 对话请求。
 *
 * @param message        用户问题
 * @param userId         用户标识，用于隔离不同用户的会话记忆。
 *                       当前由调用方传入；网关接入 JWT 鉴权后，这里应改为由网关按令牌注入，不再信任客户端传值。
 * @param conversationId 会话标识（同一用户下的多轮对话）。不传则自动生成一个并在响应中返回，
 *                       前端拿到后应保存起来，后续同一会话的请求都带上它，才能获得连续上下文。
 * @param context        当前页面上的参考数据（例如「AI 推荐」页刚算出的推荐结果），
 *                       由页面序列化成一段纯文本随本轮提问下发，让模型能针对用户眼前的项目回答。
 *                       它是「一次性背景资料」：只拼进本轮提示词，不写入会话记忆，逐轮携带也不会让记忆膨胀。
 */
public record AgentRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 4000, message = "消息内容不能超过 4000 个字符")
        String message,

        @Size(max = 64, message = "userId 长度不能超过 64 个字符")
        String userId,

        @Size(max = 64, message = "conversationId 长度不能超过 64 个字符")
        String conversationId,

        @Size(max = 6000, message = "context 长度不能超过 6000 个字符")
        String context
) {
}