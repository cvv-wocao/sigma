package cn.itcast.agent.ai.config;

import cn.itcast.agent.ai.memory.RedisChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Agent 会话记忆装配。
 *
 * <p>Spring AI 自带 ChatMemoryAutoConfiguration，会提供内存版 ChatMemoryRepository 与 ChatMemory；
 * 那两个 Bean 都带 @ConditionalOnMissingBean，所以这里声明自己的实现即可覆盖它，
 * 无需 exclude，也不会产生重复 Bean。</p>
 */
@Configuration
@EnableConfigurationProperties(ChatMemoryProperties.class)
public class ChatMemoryConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository(StringRedisTemplate redisTemplate,
                                                     JsonMapper jsonMapper,
                                                     ChatMemoryProperties properties) {
        return new RedisChatMemoryRepository(redisTemplate, jsonMapper, properties);
    }

    /**
     * 滑动窗口记忆：只把最近 maxMessages 条消息回填给模型。
     * 不做摘要或向量化的长期记忆，当前阶段保持简单。
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository, ChatMemoryProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(properties.maxMessages())
                .build();
    }
}