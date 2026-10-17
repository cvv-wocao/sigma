# 服务架构

## 1. 服务职责

| 服务 | 端口 | 职责 | 预留包结构 |
| --- | --- | --- | --- |
| `gateway-service` | 8080 | 统一入口、路由转发、跨域、鉴权拦截 | `config` `filter` `handler` |
| `user-service` | 8081 | 用户注册登录、账号信息、权限数据 | `controller` `service` `mapper` `entity` `dto` `vo` `config` `security` |
| `ai-service` | 8082 | 智能体编排、工具调用、提示词管理、会话短期记忆与用户长期记忆 | `controller` `agent` `tool` `prompt` `memory` `service` `client` `dto` `vo` `config` |
| `search-service` | 8083 | 统一检索入口、外部数据源适配 | `controller` `service` `client` `tool` `dto` `vo` `config` |

包根统一为 `cn.itcast.agent.<domain>`。

## 2. 模块依赖关系

```
java-agent-platform (root, pom)
└── backend (pom)
    ├── common/common-core        无依赖，基础工具与常量
    ├── common/common-model       -> common-core
    ├── common/common-api         -> common-core, common-model
    ├── common/common-security    -> common-core, spring-boot-starter-security
    ├── gateway-service           -> common-core
    ├── user-service              -> common-core, common-model, common-api, common-security
    ├── ai-service                -> common-core, common-api
    └── search-service            -> common-core, common-api
```

依赖原则：

1. 业务服务之间**不直接依赖**，跨服务调用一律走 `common-api` 中的 Feign 接口定义。
2. 公共模块只允许向下依赖（`core` 是依赖树的根）。
3. `gateway-service` 为响应式（WebFlux）栈，不引入 Servlet 相关的公共模块（`common-security`）。

## 3. 包结构约定

| 包 | 用途 |
| --- | --- |
| `controller` | 对外 HTTP 接口，只做参数校验与结果封装 |
| `service` | 业务编排 |
| `mapper` / `entity` | 持久层与数据库实体（`user-service`，XML 放 `resources/mapper`） |
| `agent` / `tool` / `prompt` / `memory` | 智能体定义、工具实现、提示词模板、会话短期记忆与用户长期记忆（`ai-service`） |
| `client` | 调用其他服务的 Feign 客户端、外部 HTTP 客户端 |
| `dto` / `vo` | 入参模型 / 出参模型 |
| `config` | 配置类（线程池、序列化、拦截器等） |
| `security` | 认证鉴权相关组件 |

## 4. 命名与约定

- 服务名即注册名（`spring.application.name`）：`gateway-service`、`user-service`、`ai-service`、`search-service`。
- 对外统一前缀 `/api/<domain>/**` 由网关路由到对应服务；服务内部接口路径不重复带 `/api` 前缀前需与团队统一（当前约定：网关保留前缀转发，服务内 Controller 以业务路径为准，后续在实现阶段确定是否 StripPrefix）。
- 日志级别：业务包 `cn.itcast.agent` 默认 `debug`，生产环境由 Nacos 配置覆盖。