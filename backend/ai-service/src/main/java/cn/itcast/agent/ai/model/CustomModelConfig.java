package cn.itcast.agent.ai.model;

/**
 * 解密后的用户模型配置，只存在于运行时内存，不直接返回给前端。
 *
 * @param baseUrl OpenAI 兼容接口的 Base URL，例如 https://api.deepseek.com 或 https://api.openai.com/v1
 * @param apiKey  用户自己的 API Key
 * @param model   模型名，例如 deepseek-chat
 */
public record CustomModelConfig(String baseUrl, String apiKey, String model) {
}