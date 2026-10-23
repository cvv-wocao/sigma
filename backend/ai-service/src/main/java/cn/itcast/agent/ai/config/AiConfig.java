package cn.itcast.agent.ai.config;

import cn.itcast.agent.ai.tool.SearchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

/**
 * AI 能力装配。
 *
 * <p>ChatClient.Builder 由 Spring AI 的 deepseek starter 自动配置，这里装配五个 ChatClient：</p>
 * <ul>
 *   <li>chatClient：普通对话，不带工具、不带记忆，用于 /ai/chat</li>
 *   <li>agentChatClient：带系统提示词、SearchTools 与会话记忆，用于 /ai/agent</li>
 *   <li>recommendAnalysisChatClient：只做需求解析，输出结构化 JSON，用于推荐流水线第一步</li>
 *   <li>recommendRankChatClient：只做候选排序打分，输出结构化 JSON，用于推荐流水线第四步</li>
 *   <li>memoryExtractionChatClient：只做长期记忆抽取，输出结构化 JSON，用于每轮回答后的记忆沉淀</li>
 * </ul>
 *
 * <p>职责分开的原因是职责不同：普通对话不该看见工具，避免模型为了「找项目」以外的问题
 * 也去调用检索；推荐流水线的两步都要稳定的 JSON 输出，各自用独立系统提示词比在同一
 * 提示词里切换任务更可控。Builder 使用 clone() 派生，互不影响。</p>
 */
@Configuration
@EnableConfigurationProperties({UserModelProperties.class, BillingProperties.class})
public class AiConfig {

    /** 普通对话的系统提示词 */
    private static final String CHAT_SYSTEM_PROMPT = "prompts/chat-system.txt";
    /** Agent 的系统提示词 */
    private static final String AGENT_SYSTEM_PROMPT = "prompts/agent-system.txt";
    /** 推荐流水线：需求解析提示词 */
    private static final String RECOMMEND_ANALYSIS_PROMPT = "prompts/recommend-analysis.txt";
    /** 推荐流水线：候选排序提示词 */
    private static final String RECOMMEND_RANK_PROMPT = "prompts/recommend-rank.txt";
    /** 长期记忆：记忆抽取提示词 */
    private static final String MEMORY_EXTRACT_PROMPT = "prompts/memory-extract.txt";

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.clone()
                .defaultSystem(new ClassPathResource(CHAT_SYSTEM_PROMPT), StandardCharsets.UTF_8)
                .build();
    }

    @Bean
    public ChatClient agentChatClient(ChatClient.Builder builder, SearchTools searchTools, ChatMemory chatMemory) {
        return builder.clone()
                .defaultSystem(new ClassPathResource(AGENT_SYSTEM_PROMPT), StandardCharsets.UTF_8)
                .defaultTools(searchTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /** 需求解析：只负责把自然语言需求转成结构化检索条件 */
    @Bean
    public ChatClient recommendAnalysisChatClient(ChatClient.Builder builder) {
        return builder.clone()
                .defaultSystem(new ClassPathResource(RECOMMEND_ANALYSIS_PROMPT), StandardCharsets.UTF_8)
                .build();
    }

    /**
     * 长期记忆抽取：只负责把一轮问答里「长期成立」的用户信息抽成 JSON。
     *
     * <p>刻意不挂会话记忆 Advisor 与工具：抽取的输入是本轮问答加上已有记忆，由提示词完全决定，
     * 让历史对话或工具调用进来只会干扰判断、放大开销。</p>
     */
    @Bean
    public ChatClient memoryExtractionChatClient(ChatClient.Builder builder) {
        return builder.clone()
                .defaultSystem(new ClassPathResource(MEMORY_EXTRACT_PROMPT), StandardCharsets.UTF_8)
                .build();
    }

    /** 候选排序：只负责从候选列表里打分挑选，不检索、不扩写 */
    @Bean
    public ChatClient recommendRankChatClient(ChatClient.Builder builder) {
        return builder.clone()
                .defaultSystem(new ClassPathResource(RECOMMEND_RANK_PROMPT), StandardCharsets.UTF_8)
                .build();
    }
}