-- =====================================================================
-- 用户模块表结构（注册 / 登录）
-- 挂载到 /docker-entrypoint-initdb.d，容器首次初始化时自动执行；
-- 数据卷已存在时不会重复执行，手动导入方式：
--   docker exec -i jap-mysql mysql -uroot -p<密码> < infrastructure/mysql/init/02-user.sql
-- =====================================================================

USE `java_agent_platform`;

CREATE TABLE IF NOT EXISTS `user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录用户名',
    `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`    VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    `email`       VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `phone`       VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `avatar`      VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常，0 禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_email` (`email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户表';