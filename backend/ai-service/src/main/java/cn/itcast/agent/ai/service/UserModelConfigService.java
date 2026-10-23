package cn.itcast.agent.ai.service;

import cn.itcast.agent.ai.dto.UserModelConfigRequest;
import cn.itcast.agent.ai.model.CustomModelConfig;
import cn.itcast.agent.ai.vo.UserModelConfigVO;
import cn.itcast.agent.ai.vo.UserModelTestVO;

import java.util.Optional;

/**
 * 用户自带模型配置服务。
 *
 * <p>所有读写都以 userId 为隔离边界；配置只影响该用户发起的新请求。</p>
 */
public interface UserModelConfigService {

    /** 查询配置展示信息，不会返回 API Key 明文。 */
    UserModelConfigVO get(Long userId);

    /** 保存或更新配置；apiKey 为空时保留旧 Key。 */
    UserModelConfigVO save(Long userId, UserModelConfigRequest request);

    /** 删除配置，删除后该用户回退到平台默认模型。 */
    int delete(Long userId);

    /**
     * 获取可用于本次模型调用的配置。
     * 未配置或未启用时返回 empty，由调用方回退平台默认模型。
     */
    Optional<CustomModelConfig> resolve(Long userId);

    /** 使用请求中的配置或已保存配置测试连通性，不会修改已保存的 Key。 */
    UserModelTestVO test(Long userId, UserModelConfigRequest request);
}