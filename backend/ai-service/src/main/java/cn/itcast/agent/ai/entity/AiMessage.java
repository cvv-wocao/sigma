package cn.itcast.agent.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话消息实体，对应 infrastructure/mysql/init/03-ai.sql 的 ai_message。
 *
 * <p>只落库「用户消息」与「助手回复」两类，是对话历史的持久化副本，用于前端历史记录页展示。</p>
 */
@TableName("ai_message")
public class AiMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息角色：用户 */
    public static final String ROLE_USER = "USER";
    /** 消息角色：助手 */
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 所属会话标识 */
    private String conversationId;

    /** 消息角色：USER / ASSISTANT / SYSTEM */
    private String role;

    /** 消息文本 */
    private String content;

    /** 生成该消息的模型名，仅助手消息有值 */
    private String model;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}