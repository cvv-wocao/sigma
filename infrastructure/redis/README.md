# Redis

## 启动

```bash
docker compose up -d redis
```

- 端口：6379（可通过 `.env` 中 `REDIS_PORT` 修改）
- 密码：见 `.env` 中 `REDIS_PASSWORD`，由 compose 命令行参数 `--requirepass` 注入
  （Redis 配置文件不支持读取环境变量，因此密码不写在 `conf/redis.conf` 中）
- 数据卷：`redis-data`，配置文件 `conf/redis.conf` 只读挂载

## 用途

- `ai-service`：会话记忆（对话上下文）持久化
- 后续可扩展：登录令牌缓存、限流计数、检索结果缓存

## 配置说明

`conf/redis.conf` 为本地开发模板：开启 AOF、设置 `maxmemory` 与 `allkeys-lru` 淘汰策略、
关闭 protected-mode（容器内网环境）。生产环境请按实际情况调整。