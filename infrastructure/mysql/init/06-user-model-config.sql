-- =====================================================================
-- 用户自带模型配置表
-- 挂载到 /docker-entrypoint-initdb.d，容器首次初始化时自动执行；
-- 数据卷已存在时不会重复执行，手动导入方式：
--   docker exec -i jap-mysql mysql -uroot -p<密码> < infrastructure/mysql/init/06-user-model-config.sql
--   本机 MySQL：mysql -uroot -p<密码> < infrastructure/mysql/init/06-user-model-config.sql
-- 安全说明：api_key_cipher 保存 AES-GCM 密文，api_key_hint 只保留末四位用于界面展示。
-- =====================================================================

USE `java_agent_platform`;

CREATE TABLE IF NOT EXISTS `ai_user_model_config`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT       NOT NULL COMMENT '所属用户 ID（网关注入的 X-User-Id）',
    `base_url`       VARCHAR(500) NOT NULL COMMENT 'OpenAI 兼容接口 Base URL',
    `api_key_cipher` TEXT         NOT NULL COMMENT 'API Key 的 AES-GCM 密文（Base64）',
    `api_key_hint`   VARCHAR(16)  NOT NULL DEFAULT '' COMMENT 'API Key 末四位提示，如 ••••abcd',
    `model`          VARCHAR(128) NOT NULL COMMENT '模型名，如 deepseek-chat',
    `enabled`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1 使用用户模型，0 回退平台默认模型',
    `last_test_time` DATETIME              DEFAULT NULL COMMENT '最近一次连接测试成功时间',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_user_model_config_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户自带大模型接入配置';