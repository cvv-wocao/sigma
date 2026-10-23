-- =====================================================================
-- 微信 Native 扫码充值订单
-- 已有 Docker 数据卷不会自动执行本脚本，升级时手动执行：
--   docker exec -i jap-mysql mysql -uroot -proot --default-character-set=utf8mb4 < infrastructure/mysql/init/08-wechat-pay.sql
--
-- 金额口径：
--   · amount_fen 是微信支付使用的分（1 元 = 100 分）；
--   · amount_micros 是平台钱包使用的微元（1 分 = 10,000 微元）；
--   · 入账金额只使用下单时冻结的 amount_micros，不依赖回调里的任何金额。
-- =====================================================================

USE `java_agent_platform`;

CREATE TABLE IF NOT EXISTS `ai_wechat_order`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `out_trade_no`   VARCHAR(32)  NOT NULL COMMENT '平台商户订单号',
    `user_id`        BIGINT       NOT NULL COMMENT '充值用户 ID',
    `amount_fen`     INT          NOT NULL COMMENT '订单金额（分）',
    `amount_micros`  BIGINT       NOT NULL COMMENT '入账金额（微元）',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PAID / CLOSED / FAILED',
    `code_url`       VARCHAR(512)          DEFAULT NULL COMMENT '微信 Native 支付二维码内容',
    `transaction_id` VARCHAR(64)           DEFAULT NULL COMMENT '微信支付订单号',
    `paid_time`      DATETIME              DEFAULT NULL COMMENT '支付成功时间',
    `expire_time`    DATETIME     NOT NULL COMMENT '订单过期时间',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wechat_out_trade_no` (`out_trade_no`),
    KEY `idx_wechat_user_status` (`user_id`, `status`, `create_time`),
    KEY `idx_wechat_transaction_id` (`transaction_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '微信 Native 扫码充值订单';
