-- =====================================================================
-- 角色与平台 Token 计费
-- 已有 Docker 数据卷不会自动执行本脚本，升级时手动执行：
--   docker exec -i jap-mysql mysql -uroot -proot --default-character-set=utf8mb4 < infrastructure/mysql/init/07-billing.sql
--
-- 计费口径：
--   · 余额单位是「微元」，1 元 = 1,000,000 微元，避免浮点误差；
--   · 普通用户未配置自己的模型时使用平台 DeepSeek，按输入/输出 Token 扣费；
--   · 管理员使用平台模型不扣费，配置了自己的模型时同样优先使用自己的模型。
-- =====================================================================

USE `java_agent_platform`;

-- 兼容已经初始化过的 user 表：只有缺少 role 列时才执行 ALTER
SET @role_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND column_name = 'role'
);
SET @role_ddl = IF(
    @role_column_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT ''USER'' COMMENT ''角色：ADMIN / USER'' AFTER `status`',
    'SELECT 1'
);
PREPARE role_stmt FROM @role_ddl;
EXECUTE role_stmt;
DEALLOCATE PREPARE role_stmt;

-- 当前项目已有账号 demo 作为管理员；如果账号名不同，请改成自己的用户名后执行
UPDATE `user` SET `role` = 'ADMIN' WHERE `username` = 'demo';

CREATE TABLE IF NOT EXISTS `ai_user_wallet`
(
    `user_id`                BIGINT   NOT NULL COMMENT '用户 ID',
    `balance_micros`         BIGINT   NOT NULL DEFAULT 0 COMMENT '可用余额（微元，1 元=1000000 微元）',
    `total_recharged_micros` BIGINT   NOT NULL DEFAULT 0 COMMENT '累计充值（微元）',
    `total_consumed_micros`  BIGINT   NOT NULL DEFAULT 0 COMMENT '累计消耗（微元）',
    `create_time`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户平台 Token 钱包';

CREATE TABLE IF NOT EXISTS `ai_token_usage`
(
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT      NOT NULL COMMENT '用户 ID',
    `scene`             VARCHAR(32) NOT NULL COMMENT '调用场景：CHAT / AGENT / RECOMMEND_ANALYSIS / RECOMMEND_RANK / MEMORY_EXTRACT',
    `model`             VARCHAR(128)         DEFAULT NULL COMMENT '实际模型名',
    `prompt_tokens`     INT         NOT NULL DEFAULT 0 COMMENT '输入 Token',
    `completion_tokens` INT         NOT NULL DEFAULT 0 COMMENT '输出 Token',
    `total_tokens`      INT         NOT NULL DEFAULT 0 COMMENT '总 Token',
    `cost_micros`       BIGINT      NOT NULL DEFAULT 0 COMMENT '本次扣费（微元）',
    `platform_token`    TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '1 使用平台 Token，0 使用用户自己的 API',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_token_usage_user_time` (`user_id`, `create_time`),
    KEY `idx_ai_token_usage_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '模型 Token 使用与扣费流水';

CREATE TABLE IF NOT EXISTS `ai_recharge_record`
(
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT   NOT NULL COMMENT '被充值用户 ID',
    `operator_id`   BIGINT   NOT NULL COMMENT '操作管理员 ID',
    `amount_micros` BIGINT   NOT NULL COMMENT '充值金额（微元）',
    `remark`        VARCHAR(255)      DEFAULT NULL COMMENT '备注',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_recharge_user_time` (`user_id`, `create_time`),
    KEY `idx_ai_recharge_operator_time` (`operator_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '管理员充值记录';