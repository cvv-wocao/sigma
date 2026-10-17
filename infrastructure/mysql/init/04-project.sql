-- =====================================================================
-- 项目收藏表（project-service）
-- 挂载到 /docker-entrypoint-initdb.d，容器首次初始化时自动执行；
-- 数据卷已存在时不会重复执行，手动导入方式：
--   docker exec -i jap-mysql mysql -uroot -p<密码> < infrastructure/mysql/init/04-project.sql
--   本机 MySQL：mysql -uroot -p<密码> < infrastructure/mysql/init/04-project.sql
--
-- 说明：本表保存「用户收藏的项目」。
--       仓库的客观信息（语言、Star、Fork、默认分支）是收藏时刻的快照，
--       不随 Gitee 变化自动刷新，避免把外部数据源的可用性强加给本服务。
-- =====================================================================

USE `java_agent_platform`;

CREATE TABLE IF NOT EXISTS `project`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT       NOT NULL COMMENT '所属用户 ID（网关注入的 X-User-Id）',
    `full_name`      VARCHAR(200) NOT NULL COMMENT '仓库全名 owner/repo，同一用户下唯一',
    `repo_owner`     VARCHAR(100) NOT NULL COMMENT '仓库所属命名空间',
    `repo_name`      VARCHAR(100) NOT NULL COMMENT '仓库名',
    `description`    VARCHAR(500)          DEFAULT NULL COMMENT '仓库简介，来自检索结果，可人工修改',
    `language`       VARCHAR(50)           DEFAULT NULL COMMENT '主要语言',
    `stars`          INT                   DEFAULT NULL COMMENT 'Star 数快照',
    `forks`          INT                   DEFAULT NULL COMMENT 'Fork 数快照',
    `default_branch` VARCHAR(100)          DEFAULT NULL COMMENT '默认分支',
    `html_url`       VARCHAR(255)          DEFAULT NULL COMMENT '仓库主页地址',
    `note`           VARCHAR(500)          DEFAULT NULL COMMENT '用户备注：用途、接入计划等',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 关注中，0 已归档',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_user_full_name` (`user_id`, `full_name`),
    KEY `idx_project_user_update` (`user_id`, `update_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '项目收藏表';