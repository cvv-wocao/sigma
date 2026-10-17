-- =====================================================================
-- java-agent-platform 数据库初始化脚本（骨架阶段）
-- 由 docker-compose 挂载到 /docker-entrypoint-initdb.d，容器首次初始化时执行
-- 后续阶段的表结构脚本请按序号新增，例如：02-user.sql、03-ai.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `java_agent_platform`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

-- 后续阶段在此补充：
--   用户 / 权限相关表
--   会话与消息相关表
--   检索记录相关表