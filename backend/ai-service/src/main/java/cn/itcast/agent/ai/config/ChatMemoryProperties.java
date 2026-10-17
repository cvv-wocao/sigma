package cn.itcast.agent.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Agent 会话记忆配置，对应 application.yml 中的 ai.chat.memory.* 。
 *
 * @param maxMessages 单个会话最多保留的消息条数（用户消息与助手回复合计），超出后只保留最近的若干条
 * @param ttl         会话记忆在 Redis 中的存活时间，滑动过期；设为 0 表示不设置过期
 * @param keyPrefix   Redis key 前缀，最终 key 形如 ai:chat:memory:{userId}:{conversationId}
 */
@ConfigurationProperties(prefix = "ai.chat.memory")
public record ChatMemoryProperties(Integer maxMessages, Duration ttl, String keyPrefix) {

    public ChatMemoryProperties {
        if (maxMessages == null || maxMessages < 2) {
            maxMessages = 20;
        }
        if (ttl == null) {
            ttl = Duration.ofHours(24);
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "ai:chat:memory:";
        }
    }
}