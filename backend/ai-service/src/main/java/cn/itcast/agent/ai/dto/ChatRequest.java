package cn.itcast.agent.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 普通对话请求体。
 *
 * @param message 用户输入的问题
 */
public record ChatRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 4000, message = "消息内容过长，最多 4000 个字符")
        String message
) {
}