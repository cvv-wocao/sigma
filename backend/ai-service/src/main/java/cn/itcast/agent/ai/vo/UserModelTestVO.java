package cn.itcast.agent.ai.vo;

/**
 * 自定义模型连接测试结果。
 *
 * @param success   是否连通
 * @param message   可展示的结果说明
 * @param model     响应中返回的模型名
 * @param latencyMs 请求耗时（毫秒）
 */
public record UserModelTestVO(boolean success, String message, String model, long latencyMs) {
}