package cn.itcast.agent.ai.service.impl;

import cn.itcast.agent.ai.entity.AiConversation;
import cn.itcast.agent.ai.entity.AiMessage;
import cn.itcast.agent.ai.mapper.AiConversationMapper;
import cn.itcast.agent.ai.mapper.AiMessageMapper;
import cn.itcast.agent.ai.service.ConversationHistoryService;
import cn.itcast.agent.ai.vo.ConversationVO;
import cn.itcast.agent.ai.vo.MessageVO;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 对话历史落库实现。
 *
 * <p>会话概要采用「先更新计数、更新不到再插入」的写法，
 * 配合 (user_id, conversation_id) 唯一键，可避免同一会话并发首轮请求时插入重复会话。</p>
 */
@Service
public class ConversationHistoryServiceImpl implements ConversationHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationHistoryServiceImpl.class);

    /** 时间对外统一格式，避免前后端各自约定日期序列化方式 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 会话标题最大长度，超出截断 */
    private static final int TITLE_MAX_LENGTH = 30;

    /** 一轮问答固定产生 2 条消息（用户 + 助手） */
    private static final int MESSAGES_PER_TURN = 2;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;

    public ConversationHistoryServiceImpl(AiConversationMapper conversationMapper, AiMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    @Transactional
    public void recordTurn(Long userId, String conversationId, String userMessage, String assistantAnswer, String model) {
        boolean matched = increaseMessageCount(userId, conversationId) > 0;
        if (!matched && !insertConversation(userId, conversationId, userMessage)) {
            // 并发情况下另一个请求刚插入了会话，这里补一次计数更新
            increaseMessageCount(userId, conversationId);
        }

        insertMessage(userId, conversationId, AiMessage.ROLE_USER, userMessage, null);
        insertMessage(userId, conversationId, AiMessage.ROLE_ASSISTANT, assistantAnswer, model);

        log.debug("对话历史已落库：userId={}, conversationId={}", userId, conversationId);
    }

    @Override
    public List<ConversationVO> listConversations(Long userId) {
        List<AiConversation> conversations = conversationMapper.selectList(
                Wrappers.<AiConversation>lambdaQuery()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getUpdateTime)
                        .orderByDesc(AiConversation::getId));

        return conversations.stream()
                .map(conversation -> new ConversationVO(
                        conversation.getConversationId(),
                        conversation.getTitle(),
                        conversation.getMessageCount(),
                        format(conversation.getCreateTime()),
                        format(conversation.getUpdateTime())))
                .toList();
    }

    @Override
    public List<MessageVO> listMessages(Long userId, String conversationId) {
        Long exists = conversationMapper.selectCount(
                Wrappers.<AiConversation>lambdaQuery()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getConversationId, conversationId));
        if (exists == null || exists == 0) {
            // 只允许查看自己的会话：会话不存在与不属于当前用户，对外表现一致
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }

        List<AiMessage> messages = messageMapper.selectList(
                Wrappers.<AiMessage>lambdaQuery()
                        .eq(AiMessage::getUserId, userId)
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByAsc(AiMessage::getId));

        return messages.stream()
                .map(message -> new MessageVO(
                        message.getRole(),
                        message.getContent(),
                        message.getModel(),
                        format(message.getCreateTime())))
                .toList();
    }

    /** 会话已存在时把消息条数加 2，返回受影响行数；返回 0 表示该会话还不存在 */
    private int increaseMessageCount(Long userId, String conversationId) {
        return conversationMapper.update(null,
                Wrappers.<AiConversation>lambdaUpdate()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getConversationId, conversationId)
                        .setSql("message_count = message_count + " + MESSAGES_PER_TURN));
    }

    /** @return 是否真的插入了会话（false 表示并发下被别人抢先插入） */
    private boolean insertConversation(Long userId, String conversationId, String userMessage) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setConversationId(conversationId);
        conversation.setTitle(titleOf(userMessage));
        conversation.setMessageCount(MESSAGES_PER_TURN);
        try {
            conversationMapper.insert(conversation);
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("会话已存在，跳过创建：userId={}, conversationId={}", userId, conversationId);
            return false;
        }
    }

    private void insertMessage(Long userId, String conversationId, String role, String content, String model) {
        AiMessage message = new AiMessage();
        message.setUserId(userId);
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setModel(model);
        messageMapper.insert(message);
    }

    /** 用首条用户消息生成标题：去掉换行、压缩长度 */
    private String titleOf(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return "新会话";
        }
        String title = userMessage.replaceAll("\\s+", " ").trim();
        if (title.length() <= TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, TITLE_MAX_LENGTH) + "…";
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME_FORMATTER.format(dateTime);
    }
}