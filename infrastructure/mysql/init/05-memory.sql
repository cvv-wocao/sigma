-- =====================================================================
-- AI 用户长期记忆表（跨会话的用户事实库）
-- 挂载到 /docker-entrypoint-initdb.d，容器首次初始化时自动执行；
-- 数据卷已存在时不会重复执行，手动导入方式：
--   docker exec -i jap-mysql mysql -uroot -p<密码> < infrastructure/mysql/init/05-memory.sql
--   本机 MySQL：mysql -uroot -p<密码> < infrastructure/mysql/init/05-memory.sql
-- 说明：与 ai_conversation / ai_message 的分工
--       · ai_message          是「给人看」的完整对话历史，不参与模型上下文
--       · ai_user_memory（本表）是「给模型看」的长期记忆：从历史对话里抽取出来的
--         稳定事实（用户是谁、偏好什么、在做什么），跨会话保留，按相关度注入提示词
--       与 Redis 短期记忆（ai:chat:memory:*）的分工：短期记忆是「本次会话说了什么」，
--       会自动裁剪、会过期；长期记忆是「这个用户是谁」，长期保留、按用户隔离。
-- =====================================================================

USE `java_agent_platform`;

CREATE TABLE IF NOT EXISTS `ai_user_memory`
(
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`                BIGINT       NOT NULL COMMENT '所属用户 ID（网关注入的 X-User-Id）',
    `content`                VARCHAR(500) NOT NULL COMMENT '记忆正文：一句话描述用户身上稳定成立的事实',
    `content_hash`           CHAR(64)     NOT NULL COMMENT '记忆正文归一化后的 SHA-256，用于同一用户下去重',
    `keywords`               VARCHAR(255) NOT NULL DEFAULT '' COMMENT '召回用关键词，逗号分隔，由模型抽取时一并给出',
    `category`               VARCHAR(32)  NOT NULL DEFAULT 'FACT' COMMENT '记忆类别：PROFILE / PREFERENCE / GOAL / CONSTRAINT / FACT',
    `importance`             TINYINT      NOT NULL DEFAULT 3 COMMENT '重要度 1-5，越大越优先注入',
    `source_conversation_id` VARCHAR(64)           DEFAULT NULL COMMENT '首次抽取到该记忆的会话标识',
    `hit_count`              INT          NOT NULL DEFAULT 0 COMMENT '被召回并注入提示词的次数',
    `last_hit_time`          DATETIME              DEFAULT NULL COMMENT '最近一次被召回的时间',
    `embedding`              TEXT                  DEFAULT NULL COMMENT '记忆正文的向量（逗号分隔的浮点数），未启用向量召回时为空',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_user_memory_content` (`user_id`, `content_hash`),
    KEY `idx_ai_user_memory_user` (`user_id`, `importance`, `update_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 用户长期记忆表';
