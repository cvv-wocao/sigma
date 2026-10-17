# 监控与可观测性

## 当前已实现

各后端服务（gateway / user / ai / search / project）都引入了：

- `spring-boot-starter-actuator`：提供健康检查与指标端点。
- `io.micrometer:micrometer-registry-prometheus`：把指标以 Prometheus 抓取格式输出。

暴露的端点（各服务 `application.yml` 的 `management.endpoints.web.exposure.include`）：

| 端点 | 用途 |
| --- | --- |
| `/actuator/health` | 健康检查，`show-details=always` 会列出数据库、Redis 等组件状态（仅开发期） |
| `/actuator/info` | 服务信息 |
| `/actuator/metrics` | 指标浏览（JSON） |
| `/actuator/prometheus` | Prometheus 抓取入口 |

未暴露的端点（`env`、`beans`、`configprops` 等）会泄露配置与内部结构，保持关闭，访问时返回 `code=404`。

所有指标都带 `application` 标签（值取自 `spring.application.name`），Prometheus 里可按服务维度筛选。

> `/actuator/**` 不对公网暴露：网关只声明了 `/api/**` 路由，且关闭了自动路由，因此从 8080 访问 `/api/actuator/**` 或 `/actuator/**` 都不会转发到后端服务。
> 各服务的 `management.server.port` 未单独配置，指标端口与业务端口相同（8080~8083），仅限内网/本机访问。

## 启动 Prometheus 与 Grafana

```bash
# 前提：后端服务已在本机启动（8080 / 8081 / 8082 / 8083 / 8084）
docker compose --profile monitoring up -d
```

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| Prometheus | http://localhost:9090 | 查看 Targets（`Status > Targets`）确认五个服务都是 `UP` |
| Grafana | http://localhost:3000 | 默认账号 `admin` / `admin`（可用 `GRAFANA_USER`、`GRAFANA_PASSWORD` 覆盖） |

抓取目标配置见本目录的 `prometheus.yml`：

- 后端跑在本机（IDE / jar）时用 `host.docker.internal:808x`；
- 后端跑在容器里（`docker compose --profile app`）时改成 `服务名:容器端口`，例如 `user-service:8081`。

> 修改 `prometheus.yml` 后需要让 Prometheus 重新加载配置：`docker restart jap-prometheus`（容器未开启 `--web.enable-lifecycle`，不能用 `/-/reload`）。

Grafana 首次使用需要手动添加 Prometheus 数据源（`http://prometheus:9090`，容器内地址）并导入 JVM / HTTP 面板。

## 待建设

- 链路：接入 OpenTelemetry / Micrometer Tracing，串联 gateway-service 与后端服务的调用链。
- 日志：统一日志格式与 traceId 透传，便于按会话维度排查问题。
- 告警：Prometheus Alertmanager + 规则（接口错误率、模型调用失败率等）。