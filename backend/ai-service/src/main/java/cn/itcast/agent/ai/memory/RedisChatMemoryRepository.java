package cn.itcast.agent.ai.memory;

import cn.itcast.agent.ai.config.ChatMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis 的会话记忆存储。
 *
 * <p>存储结构：每个会话对应一个 Redis List，key 为 {prefix}{conversationId}，
 * List 中的每个元素是「一条消息」的 JSON（只保存角色与文本）。</p>
 *
 * <p>只保存 角色+文本 而不是完整 Message 对象，是为了避开多态序列化的复杂度：
 * 短期记忆需要的是「之前聊了什么」，不需要保留工具调用、媒体等结构。
 * 等以后确实需要长期记忆或完整结构时再扩展本类即可。</p>
 *
 * <p>写入采用「先删后整段写入」：与 MessageWindowChatMemory 的语义一致
 * （它每次都会把裁剪后的完整窗口交回来），同时保证 Redis 中的顺序与内存一致。</p>
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryRepository.class);

    /** JSON 中存放消息角色的字段名 */
    private static final String FIELD_TYPE = "type";
    /** JSON 中存放消息文本的字段名 */
    private static final String FIELD_TEXT = "text";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final ChatMemoryProperties properties;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate,
                                     JsonMapper jsonMapper,
                                     ChatMemoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    public List<String> findConversationIds() {
        String prefix = properties.keyPrefix();
        List<String> conversationIds = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(200).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                conversationIds.add(cursor.next().substring(prefix.length()));
            }
        }
        return conversationIds;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<String> rawMessages = redisTemplate.opsForList().range(key(conversationId), 0, -1);
        if (rawMessages == null || rawMessages.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>(rawMessages.size());
        for (String rawMessage : rawMessages) {
            Message message = read(rawMessage);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = key(conversationId);
        redisTemplate.delete(key);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<String> values = new ArrayList<>(messages.size());
        for (Message message : messages) {
            String value = write(message);
            if (value != null) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return;
        }
        redisTemplate.opsForList().rightPushAll(key, values);
        Duration ttl = properties.ttl();
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            redisTemplate.expire(key, ttl);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return properties.keyPrefix() + conversationId;
    }

    /** 只持久化 用户 / 助手 / 系统 三类消息，其余（如工具消息）直接跳过 */
    private String write(Message message) {
        MessageType type = message.getMessageType();
        if (type != MessageType.USER && type != MessageType.ASSISTANT && type != MessageType.SYSTEM) {
            return null;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put(FIELD_TYPE, type.name());
        payload.put(FIELD_TEXT, message.getText() == null ? "" : message.getText());
        return jsonMapper.writeValueAsString(payload);
    }

    private Message read(String rawMessage) {
        try {
            Map<?, ?> payload = jsonMapper.readValue(rawMessage, Map.class);
            MessageType type = MessageType.valueOf(String.valueOf(payload.get(FIELD_TYPE)));
            String text = payload.get(FIELD_TEXT) == null ? "" : String.valueOf(payload.get(FIELD_TEXT));
            return switch (type) {
                case USER -> new UserMessage(text);
                case ASSISTANT -> new AssistantMessage(text);
                case SYSTEM -> new SystemMessage(text);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("会话记忆反序列化失败，已跳过该条消息：{}", rawMessage, e);
            return null;
        }
    }
}