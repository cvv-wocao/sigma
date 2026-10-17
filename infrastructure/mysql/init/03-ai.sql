-- =====================================================================
-- AI 会话与消息表（Agent 对话历史落库）
-- 挂载到 /docker-entrypoint-initdb.d，容器首次初始化时自动执行；
-- 数据卷已存在时不会重复执行，手动导入方式：
--   docker exec -i jap-mysql mysql -uroot -p<密码> < infrastructure/mysql/init/03-ai.sql
--   本机 MySQL：mysql -uroot -p<密码> < infrastructure/mysql/init/03-ai.sql
-- 说明：本表是「对话历史的持久化副本」，只用于展示与追溯；
--       Agent 的运行时上下文仍由 Redis 短期记忆提供，两者互不影响。
-- =====================================================================

USE `java_agent_platform`;

CREATE TABLE IF NOT EXISTS `ai_conversation`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NOT NULL COMMENT '所属用户 ID（网关注入的 X-User-Id）',
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话标识（UUID），与 Redis 记忆 key 中的会话段一致',
    `title`           VARCHAR(100) NOT NULL DEFAULT '' COMMENT '会话标题，取首条用户消息的前若干字符',
    `message_count`   INT          NOT NULL DEFAULT 0 COMMENT '消息条数（用户消息 + 助手回复）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_conversation_user` (`user_id`, `conversation_id`),
    KEY `idx_ai_conversation_recent` (`user_id`, `update_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 会话表';

CREATE TABLE IF NOT EXISTS `ai_message`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT      NOT NULL COMMENT '所属用户 ID',
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '所属会话标识',
    `role`            VARCHAR(16) NOT NULL COMMENT '消息角色：USER / ASSISTANT / SYSTEM',
    `content`         TEXT        NOT NULL COMMENT '消息文本',
    `model`           VARCHAR(64)          DEFAULT NULL COMMENT '生成该消息的模型名，仅助手消息有值',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_message_conversation` (`user_id`, `conversation_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 会话消息表';