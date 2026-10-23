package cn.itcast.agent.ai.vo;

/**
 * 用户模型配置视图。API Key 只返回末四位提示，绝不返回明文或密文。
 *
 * @param configured   是否已保存过配置
 * @param enabled      是否启用；未启用时所有请求回退平台默认模型
 * @param baseUrl      OpenAI 兼容接口地址
 * @param model        模型名
 * @param apiKeyMasked 脱敏后的 Key，例如 ••••abcd
 * @param lastTestTime 最近一次连接测试成功时间
 * @param updateTime   配置最后更新时间
 */
public record UserModelConfigVO(boolean configured, boolean enabled, String baseUrl, String model,
                                String apiKeyMasked, String lastTestTime, String updateTime) {
}