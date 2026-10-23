package cn.itcast.agent.ai.service.impl;

import cn.itcast.agent.ai.dto.UserModelConfigRequest;
import cn.itcast.agent.ai.entity.UserModelConfig;
import cn.itcast.agent.ai.mapper.UserModelConfigMapper;
import cn.itcast.agent.ai.model.CustomModelClientFactory;
import cn.itcast.agent.ai.model.CustomModelConfig;
import cn.itcast.agent.ai.security.ApiKeyCipher;
import cn.itcast.agent.ai.service.BillingService;
import cn.itcast.agent.ai.service.UserModelConfigService;
import cn.itcast.agent.ai.vo.UserModelConfigVO;
import cn.itcast.agent.ai.vo.UserModelTestVO;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户模型配置实现。
 *
 * <p>API Key 使用 AES-GCM 加密后落库；响应只返回末四位。配置启用后，该用户的普通对话、
 * Agent、推荐排序和长期记忆抽取都会走用户自己的模型。</p>
 */
@Service
public class UserModelConfigServiceImpl implements UserModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserModelConfigServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_TEST_PROMPT = "请只回复：OK";

    private final UserModelConfigMapper mapper;
    private final ApiKeyCipher cipher;
    private final CustomModelClientFactory clientFactory;
    private final BillingService billingService;

    public UserModelConfigServiceImpl(UserModelConfigMapper mapper,
                                      ApiKeyCipher cipher,
                                      CustomModelClientFactory clientFactory,
                                      BillingService billingService) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.clientFactory = clientFactory;
        this.billingService = billingService;
    }

    @Override
    public UserModelConfigVO get(Long userId) {
        UserModelConfig config = findByUserId(userId);
        if (config == null) {
            return new UserModelConfigVO(false, false, "", "", "", null, null);
        }
        return toVO(config);
    }

    @Override
    public UserModelConfigVO save(Long userId, UserModelConfigRequest request) {
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String model = requireText(request.model(), "请填写模型名", 128);
        String apiKey = trimToNull(request.apiKey());
        UserModelConfig existing = findByUserId(userId);
        if (existing == null && apiKey == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "首次保存时必须填写 API Key");
        }
        if (apiKey != null && apiKey.length() > 512) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "API Key 不能超过 512 个字符");
        }

        UserModelConfig config = existing == null ? new UserModelConfig() : existing;
        boolean changed = existing != null
                && (!Objects.equals(existing.getBaseUrl(), baseUrl)
                || !Objects.equals(existing.getModel(), model)
                || apiKey != null);
        config.setUserId(userId);
        config.setBaseUrl(baseUrl);
        config.setModel(model);
        config.setEnabled(request.enabled() == null || request.enabled());
        if (apiKey != null) {
            config.setApiKeyCipher(cipher.encrypt(apiKey));
            config.setApiKeyHint("••••" + suffix(apiKey));
        }
        if (existing == null) {
            mapper.insert(config);
        } else {
            mapper.updateById(config);
            if (changed) {
                mapper.update(null, Wrappers.<UserModelConfig>lambdaUpdate()
                        .eq(UserModelConfig::getUserId, userId)
                        .set(UserModelConfig::getLastTestTime, null));
            }
        }
        UserModelConfig saved = findByUserId(userId);
        log.info("用户模型配置已保存：userId={}, enabled={}, model={}", userId, config.getEnabled(), model);
        return toVO(saved == null ? config : saved);
    }

    @Override
    public int delete(Long userId) {
        int removed = mapper.delete(Wrappers.<UserModelConfig>lambdaQuery()
                .eq(UserModelConfig::getUserId, userId));
        log.info("用户模型配置已删除：userId={}, removed={}", userId, removed);
        return removed;
    }

    @Override
    public Optional<CustomModelConfig> resolve(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        UserModelConfig config = findByUserId(userId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CustomModelConfig(
                    config.getBaseUrl(), cipher.decrypt(config.getApiKeyCipher()), config.getModel()));
        } catch (Exception e) {
            log.error("用户模型 API Key 解密失败：userId={}", userId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "自定义模型 API Key 无法解密，请在模型设置中重新填写并保存");
        }
    }

    @Override
    public UserModelTestVO test(Long userId, UserModelConfigRequest request) {
        UserModelConfig existing = findByUserId(userId);
        String baseUrl = normalizeBaseUrl(firstText(request.baseUrl(), existing == null ? null : existing.getBaseUrl()));
        String model = requireText(firstText(request.model(), existing == null ? null : existing.getModel()),
                "请填写模型名", 128);
        String apiKey = trimToNull(request.apiKey());
        if (apiKey == null && existing != null) {
            try {
                apiKey = cipher.decrypt(existing.getApiKeyCipher());
            } catch (Exception e) {
                log.error("用户模型 API Key 解密失败：userId={}", userId, e);
                return new UserModelTestVO(false, "已保存的 API Key 无法解密，请重新填写", model, 0);
            }
        }
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写 API Key");
        }
        boolean testingSavedConfig = existing != null
                && Objects.equals(existing.getBaseUrl(), baseUrl)
                && Objects.equals(existing.getModel(), model)
                && trimToNull(request.apiKey()) == null;

        long start = System.nanoTime();
        try {
            ChatClient client = clientFactory.test(new CustomModelConfig(baseUrl, apiKey, model));
            ChatResponse response = client.prompt().user(DEFAULT_TEST_PROMPT).call().chatResponse();
            long latency = elapsed(start);
            billingService.recordUsage(userId, false, false, "MODEL_TEST", response, latency);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return new UserModelTestVO(false, "模型没有返回有效结果", model, latency);
            }
            String actualModel = response.getMetadata() == null || response.getMetadata().getModel() == null
                    ? model : response.getMetadata().getModel();
            if (testingSavedConfig) {
                mapper.update(null, Wrappers.<UserModelConfig>lambdaUpdate()
                        .eq(UserModelConfig::getUserId, userId)
                        .set(UserModelConfig::getLastTestTime, LocalDateTime.now()));
            }
            log.info("用户模型连接测试成功：userId={}, model={}, latencyMs={}", userId, actualModel, latency);
            return new UserModelTestVO(true, "连接成功", actualModel, latency);
        } catch (Exception e) {
            log.warn("用户模型连接测试失败：userId={}, baseUrl={}, model={}, error={}",
                    userId, baseUrl, model, e.getMessage());
            return new UserModelTestVO(false,
                    "连接失败，请检查 Base URL、API Key、模型名和账户余额", model, elapsed(start));
        }
    }

    private UserModelConfig findByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return mapper.selectOne(Wrappers.<UserModelConfig>lambdaQuery()
                .eq(UserModelConfig::getUserId, userId)
                .last("LIMIT 1"));
    }

    private UserModelConfigVO toVO(UserModelConfig config) {
        return new UserModelConfigVO(
                true,
                Boolean.TRUE.equals(config.getEnabled()),
                config.getBaseUrl(),
                config.getModel(),
                config.getApiKeyHint() == null ? "" : config.getApiKeyHint(),
                format(config.getLastTestTime()),
                format(config.getUpdateTime()));
    }

    private String normalizeBaseUrl(String raw) {
        String value = requireText(raw, "请填写 Base URL", 500);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("scheme or host invalid");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("userinfo/query/fragment not allowed");
            }
            return value;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Base URL 格式不正确，应类似 https://api.deepseek.com 或 https://api.openai.com/v1");
        }
    }

    private String requireText(String raw, String message, int maxLength) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        if (value.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message + "（最多 " + maxLength + " 个字符）");
        }
        return value;
    }

    private String firstText(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : second;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String suffix(String apiKey) {
        return apiKey.length() <= 4 ? "" : apiKey.substring(apiKey.length() - 4);
    }

    private long elapsed(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }

    private String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
