-- =====================================================================
-- 支付宝当面付（预下单 + 主动查单）订单
-- 已有 Docker 数据卷不会自动执行本脚本，升级时手动执行：
--   docker exec -i jap-mysql mysql -uroot -proot --default-character-set=utf8mb4 < infrastructure/mysql/init/09-alipay-pay.sql
--
-- 与微信订单相同的金额口径：
--   · amount_fen 是对账金额（分）；
--   · amount_micros 是平台钱包入账金额（微元，1 分 = 10,000 微元）；
--   · 入账只使用下单时冻结的 amount_micros，不直接使用支付宝返回金额。
-- 本方案不接收异步回调，前台轮询和后台定时任务都会调用支付宝查单。
-- =====================================================================

USE `java_agent_platform`;

CREATE TABLE IF NOT EXISTS `ai_alipay_order`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `out_trade_no`      VARCHAR(64)  NOT NULL COMMENT '平台商户订单号',
    `user_id`           BIGINT       NOT NULL COMMENT '充值用户 ID',
    `amount_fen`        INT          NOT NULL COMMENT '订单金额（分）',
    `amount_micros`     BIGINT       NOT NULL COMMENT '入账金额（微元）',
    `status`            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PAID / CLOSED / FAILED',
    `qr_code`           VARCHAR(512)          DEFAULT NULL COMMENT '支付宝预下单二维码内容',
    `trade_no`          VARCHAR(64)           DEFAULT NULL COMMENT '支付宝交易号',
    `paid_time`         DATETIME              DEFAULT NULL COMMENT '支付成功时间',
    `expire_time`       DATETIME     NOT NULL COMMENT '二维码过期时间',
    `last_checked_time` DATETIME              DEFAULT NULL COMMENT '最近一次主动查单时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alipay_out_trade_no` (`out_trade_no`),
    KEY `idx_alipay_user_status` (`user_id`, `status`, `create_time`),
    KEY `idx_alipay_status_create` (`status`, `create_time`),
    KEY `idx_alipay_trade_no` (`trade_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '支付宝当面付订单';
