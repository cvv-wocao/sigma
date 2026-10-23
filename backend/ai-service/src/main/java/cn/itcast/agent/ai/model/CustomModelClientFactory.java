package cn.itcast.agent.ai.model;

import cn.itcast.agent.ai.config.AiPromptPaths;
import cn.itcast.agent.ai.tool.SearchTools;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 用用户自己的 API Key 动态创建 ChatClient。
 *
 * <p>DeepSeek 的客户端协议与 OpenAI Chat Completions 兼容，因此用户只要填写兼容接口的
 * Base URL、模型名和 Key，就能接入 DeepSeek、OpenAI、硅基流动、本地 Ollama 等服务。
 * Base URL 不包含 /chat/completions，例如：</p>
 * <ul>
 *   <li>DeepSeek：https://api.deepseek.com</li>
 *   <li>OpenAI：https://api.openai.com/v1</li>
 *   <li>硅基流动：https://api.siliconflow.cn/v1</li>
 * </ul>
 */
@Component
public class CustomModelClientFactory {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int TEST_MAX_TOKENS = 64;

    private final SearchTools searchTools;
    private final ChatMemory chatMemory;

    public CustomModelClientFactory(SearchTools searchTools, ChatMemory chatMemory) {
        this.searchTools = searchTools;
        this.chatMemory = chatMemory;
    }

    public ChatClient chat(CustomModelConfig config) {
        return client(config, AiPromptPaths.CHAT, DEFAULT_TEMPERATURE, null);
    }

    public ChatClient agent(CustomModelConfig config) {
        return ChatClient.builder(chatModel(config, DEFAULT_TEMPERATURE, null))
                .defaultSystem(new ClassPathResource(AiPromptPaths.AGENT), StandardCharsets.UTF_8)
                .defaultTools(searchTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public ChatClient recommendAnalysis(CustomModelConfig config) {
        return client(config, AiPromptPaths.RECOMMEND_ANALYSIS, DEFAULT_TEMPERATURE, null);
    }

    public ChatClient recommendRank(CustomModelConfig config) {
        return client(config, AiPromptPaths.RECOMMEND_RANK, DEFAULT_TEMPERATURE, null);
    }

    public ChatClient memoryExtraction(CustomModelConfig config) {
        return client(config, AiPromptPaths.MEMORY_EXTRACT, DEFAULT_TEMPERATURE, null);
    }

    /** 连接测试只发一条极短请求，限制输出长度，避免消耗用户过多 token。 */
    public ChatClient test(CustomModelConfig config) {
        return client(config, null, 0.0, TEST_MAX_TOKENS);
    }

    private ChatClient client(CustomModelConfig config, String promptPath, double temperature, Integer maxTokens) {
        ChatClient.Builder builder = ChatClient.builder(chatModel(config, temperature, maxTokens));
        if (StringUtils.hasText(promptPath)) {
            builder.defaultSystem(new ClassPathResource(promptPath), StandardCharsets.UTF_8);
        }
        return builder.build();
    }

    private DeepSeekChatModel chatModel(CustomModelConfig config, double temperature, Integer maxTokens) {
        DeepSeekApi api = DeepSeekApi.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .build();

        DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder()
                .model(config.model())
                .temperature(temperature);
        if (maxTokens != null) {
            options.maxTokens(maxTokens);
        }

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(options.build())
                .retryTemplate(new RetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }
}