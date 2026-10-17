package cn.itcast.agent.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户长期记忆实体，对应 infrastructure/mysql/init/05-memory.sql 的 ai_user_memory。
 *
 * <p>一条记忆就是一句「关于这个用户、长期成立的事实」，例如「用户主要用 Java + Spring Boot 做后端」。
 * 它不是某句话的副本，而是从对话里抽取并归并出来的结论，因此同一个用户下用 contentHash 去重。</p>
 */
@TableName("ai_user_memory")
public class UserMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记忆类别：用户画像（身份、岗位、所在地等） */
    public static final String CATEGORY_PROFILE = "PROFILE";
    /** 记忆类别：偏好（技术栈、风格、口味等） */
    public static final String CATEGORY_PREFERENCE = "PREFERENCE";
    /** 记忆类别：目标（正在做、想做的事） */
    public static final String CATEGORY_GOAL = "GOAL";
    /** 记忆类别：约束（必须满足或必须避免的条件） */
    public static final String CATEGORY_CONSTRAINT = "CONSTRAINT";
    /** 记忆类别：其他客观事实 */
    public static final String CATEGORY_FACT = "FACT";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 记忆正文 */
    private String content;

    /** 记忆正文归一化后的 SHA-256，用于同一用户下去重 */
    private String contentHash;

    /** 召回用关键词，逗号分隔 */
    private String keywords;

    /** 记忆类别：PROFILE / PREFERENCE / GOAL / CONSTRAINT / FACT */
    private String category;

    /** 重要度 1-5，越大越优先注入 */
    private Integer importance;

    /** 首次抽取到该记忆的会话标识 */
    private String sourceConversationId;

    /** 被召回并注入提示词的次数 */
    private Integer hitCount;

    /** 最近一次被召回的时间 */
    private LocalDateTime lastHitTime;

    /** 记忆正文的向量（逗号分隔的浮点数），未启用向量召回时为空 */
    private String embedding;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getImportance() {
        return importance;
    }

    public void setImportance(Integer importance) {
        this.importance = importance;
    }

    public String getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(String sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public LocalDateTime getLastHitTime() {
        return lastHitTime;
    }

    public void setLastHitTime(LocalDateTime lastHitTime) {
        this.lastHitTime = lastHitTime;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
