# java-agent-platform

一个基于 Spring Cloud 微服务与 Spring AI 的开发者智能体平台，将开源项目检索、AI 推荐、技术问答和个人项目管理整合到同一界面。

## 核心能力

- **AI 对话与 Agent**：由 DeepSeek 驱动，支持工具调用，并可结合当前页面上下文回答。
- **双层记忆**：Redis 保存会话短期记忆；MySQL 保存用户画像、偏好等长期事实，并按相关度、重要度和时效性召回。
- **项目检索**：通过 GitHub / Gitee OpenAPI 搜索开源项目，查看详情与分支信息。
- **智能推荐**：根据技术方向、团队规模等需求召回候选项目，再由模型筛选并生成推荐理由。
- **项目收藏**：支持收藏、备注、归档和状态管理。
- **账号与鉴权**：注册登录、JWT 鉴权，以及在网关统一透传用户身份。

## 技术栈

- 后端：Java 17、Spring Boot 4、Spring Cloud / Alibaba、Spring AI、Nacos、MyBatis-Plus
- 前端：Vue 3、TypeScript、Vite
- 数据与运维：MySQL、Redis、Docker Compose、Nginx、Prometheus / Grafana

## 架构概览

浏览器 -> Nginx / Gateway（JWT 鉴权）-> 用户、AI、检索、项目微服务 -> MySQL / Redis / Nacos / GitHub、Gitee。

## 快速开始

```bash
cp .env.example .env
# 在 .env 中填写 DEEPSEEK_API_KEY
docker compose --profile app up -d --build
```

详细配置与接口说明见 [README.md](README.md)。