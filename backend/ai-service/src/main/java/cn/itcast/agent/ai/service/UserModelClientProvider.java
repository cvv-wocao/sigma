package cn.itcast.agent.ai.service;

import cn.itcast.agent.ai.model.CustomModelClientFactory;
import cn.itcast.agent.ai.model.CustomModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * 按用户选择模型客户端：启用自定义配置就使用用户自己的 Key，否则回退平台默认 DeepSeek。
 *
 * <p>返回的 ModelRoute 同时告诉调用方本次是否消耗平台 Token，计费服务据此决定是否扣余额。</p>
 */
@Component
public class UserModelClientProvider {

    private static final Logger log = LoggerFactory.getLogger(UserModelClientProvider.class);

    private final UserModelConfigService configService;
    private final CustomModelClientFactory customFactory;

    private final ChatClient platformChatClient;
    private final ChatClient platformAgentChatClient;
    private final ChatClient platformRecommendAnalysisChatClient;
    private final ChatClient platformRecommendRankChatClient;
    private final ChatClient platformMemoryExtractionChatClient;

    public UserModelClientProvider(
            UserModelConfigService configService,
            CustomModelClientFactory customFactory,
            @Qualifier("chatClient") ChatClient platformChatClient,
            @Qualifier("agentChatClient") ChatClient platformAgentChatClient,
            @Qualifier("recommendAnalysisChatClient") ChatClient platformRecommendAnalysisChatClient,
            @Qualifier("recommendRankChatClient") ChatClient platformRecommendRankChatClient,
            @Qualifier("memoryExtractionChatClient") ChatClient platformMemoryExtractionChatClient) {
        this.configService = configService;
        this.customFactory = customFactory;
        this.platformChatClient = platformChatClient;
        this.platformAgentChatClient = platformAgentChatClient;
        this.platformRecommendAnalysisChatClient = platformRecommendAnalysisChatClient;
        this.platformRecommendRankChatClient = platformRecommendRankChatClient;
        this.platformMemoryExtractionChatClient = platformMemoryExtractionChatClient;
    }

    public ModelRoute chat(Long userId) {
        return choose(userId, customFactory::chat, platformChatClient);
    }

    public ModelRoute agent(Long userId) {
        return choose(userId, customFactory::agent, platformAgentChatClient);
    }

    public ModelRoute recommendAnalysis(Long userId) {
        return choose(userId, customFactory::recommendAnalysis, platformRecommendAnalysisChatClient);
    }

    public ModelRoute recommendRank(Long userId) {
        return choose(userId, customFactory::recommendRank, platformRecommendRankChatClient);
    }

    public ModelRoute memoryExtraction(Long userId) {
        return choose(userId, customFactory::memoryExtraction, platformMemoryExtractionChatClient);
    }

    private ModelRoute choose(Long userId, Function<CustomModelConfig, ChatClient> customClient, ChatClient fallback) {
        Optional<CustomModelConfig> config = configService.resolve(userId);
        if (config.isEmpty()) {
            return new ModelRoute(fallback, true);
        }
        log.debug("使用用户自定义模型：userId={}, model={}", userId, config.get().model());
        return new ModelRoute(customClient.apply(config.get()), false);
    }

    /**
     * @param client        本次调用使用的 ChatClient
     * @param platformToken true 表示消耗平台 Token，普通用户需要余额
     */
    public record ModelRoute(ChatClient client, boolean platformToken) {
    }
}