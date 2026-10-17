# 数据流

本文档描述目标架构下的典型链路。骨架阶段尚未实现业务逻辑，链路以规划为准。

## 1. 对话链路（核心链路）

```
浏览器 / 前端页面
   │  POST /api/ai/chat  (SSE / 流式返回)
   ▼
gateway-service :8080
   │  鉴权校验（预留）→ 路由匹配 Path=/api/ai/**
   │  lb://ai-service
   ▼
ai-service :8082
   │  1) 读取会话短期记忆（Redis，按「用户 + 会话」隔离、可裁剪、可过期）
   │  2) 召回用户长期记忆（MySQL ai_user_memory，按相关度打分取前若干条）
   │  3) 加载提示词模板（resources/prompts，可被 Nacos 覆盖）
   │  4) 组装智能体：模型 + 工具列表 + 页面参考数据 + 长期记忆
   │  5) 调用模型（Spring AI，OpenAI 兼容接口）
   │  6) 如需外部数据 → 调用 search-service（Feign）
   │  7) 写回会话短期记忆（Redis）
   │  8) 回答返回后异步抽取长期记忆并落库（MySQL ai_user_memory）
   ▼
返回流式响应 → 网关 → 前端逐字渲染
```

要点：

- 会话上下文与会话标识由前端传入；短期记忆按「用户 + 会话」维度存储，长期记忆按「用户」维度存储。
- 模型调用超时时间明显长于普通接口，网关与 Nginx 的 `proxy_read_timeout` 已放宽到 300s。
- 流式响应需要网关透传 SSE，不走响应体聚合。

## 2. 检索链路

```
ai-service（工具调用 / 检索请求）
   │  GET /api/search/query
   ▼
gateway-service → search-service :8083
   │  适配外部数据源（HTTP API / 向量检索，后续阶段确定）
   ▼
结果归一化（统一 DTO）→ 返回调用方，由智能体决定如何使用
```

## 3. 鉴权链路（规划）

```
前端登录 → /api/user/login → user-service 校验账号密码
   │  签发令牌（后续阶段确定实现：JWT + Redis 缓存）
   ▼
后续请求携带令牌 → gateway-service 全局过滤器校验
   │  校验通过：透传用户标识到下游请求头
   ▼
下游服务从请求头读取用户上下文（common-security 提供解析工具）
```

## 4. 注册与配置流

```
服务启动
   │  1) 读取本地 application.yml（模板 + 环境变量）
   │  2) spring.config.import: optional:nacos:<service>.yaml 拉取远程配置（Nacos 不可用则跳过）
   │  3) 向 Nacos 注册自身（spring.application.name + ip:port）
   ▼
网关通过 Nacos 服务发现 + LoadBalancer 完成 lb://<service-name> 负载均衡
```