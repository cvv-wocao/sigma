# Nacos（注册中心 / 配置中心）

## 启动

由根目录 `docker-compose.yml` 提供，单机模式（standalone），使用内置存储：

```bash
docker compose up -d nacos
```

- 控制台：http://127.0.0.1:8848/nacos
- 端口：8848（HTTP）、9848（gRPC，客户端 2.x/3.x 通信必需）
- 镜像：`nacos/nacos-server:v2.4.3`；如需 Nacos 3.x 服务端，修改 compose 中的 tag 即可
  （Spring Cloud Alibaba 2025.1.0.0 使用 nacos-client 3.x，服务端 2.2+ 均可兼容）

## 配置约定

各服务通过 `spring.config.import: optional:nacos:${spring.application.name}.yaml` 拉取自身配置：

| Data ID | Group | 对应服务 |
| --- | --- | --- |
| `gateway-service.yaml` | DEFAULT_GROUP | 网关服务 |
| `user-service.yaml` | DEFAULT_GROUP | 用户服务 |
| `ai-service.yaml` | DEFAULT_GROUP | AI 智能体服务 |
| `search-service.yaml` | DEFAULT_GROUP | 检索服务 |

- 命名空间：默认使用 `public`，多环境时建议按 `dev` / `test` / `prod` 创建独立命名空间，
  通过环境变量 `NACOS_NAMESPACE` 注入。
- 使用 `optional:` 前缀，Nacos 不可用时不会阻塞服务本地启动。

## application 目录

`infrastructure/nacos/application/` 用于存放各服务的配置样例与导出文件，
便于版本管理与批量导入（骨架阶段仅保留目录占位）。