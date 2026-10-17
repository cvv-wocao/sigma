# MySQL

## 启动

```bash
docker compose up -d mysql
```

- 端口：3306（可通过 `.env` 中 `MYSQL_PORT` 修改）
- 账号：`root` / 密码见 `.env` 中 `MYSQL_ROOT_PASSWORD`
- 数据库：`java_agent_platform`（见 `.env` 中 `MYSQL_DATABASE`）
- 字符集：`utf8mb4` / `utf8mb4_general_ci`
- 数据卷：`mysql-data`（持久化在 Docker 卷中）

## 初始化脚本

`init/` 目录被挂载到容器 `/docker-entrypoint-initdb.d`，
**仅在数据卷首次初始化时执行**。骨架阶段只有 `01-schema.sql`（创建数据库与字符集约定），
后续阶段在此追加各服务的表结构脚本。

> 需要重新执行初始化脚本时，先执行 `docker compose down -v` 删除数据卷（会清空数据）。