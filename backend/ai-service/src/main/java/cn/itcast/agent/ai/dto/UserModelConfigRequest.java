package cn.itcast.agent.ai.dto;

import jakarta.validation.constraints.Size;

/**
 * 保存或测试用户模型配置。
 *
 * <p>更新时 apiKey 可以留空，表示继续使用已保存的 Key；首次保存必须提供。</p>
 */
public record UserModelConfigRequest(
        @Size(max = 500, message = "Base URL 不能超过 500 个字符")
        String baseUrl,

        @Size(max = 512, message = "API Key 不能超过 512 个字符")
        String apiKey,

        @Size(max = 128, message = "模型名不能超过 128 个字符")
        String model,

        Boolean enabled
) {
}