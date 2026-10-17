# Nginx

## 职责

1. 托管前端静态资源（`frontend/dist` 只读挂载到 `/usr/share/nginx/html`）。
2. 将 `/api/**` 反向代理到网关服务（`gateway-service:8080`）。

## 启动

```bash
cd frontend && npm run build      # 先构建前端产物
docker compose up -d nginx
```

- 访问：http://127.0.0.1（端口由 `.env` 中 `NGINX_HTTP_PORT` 控制）
- 健康检查：http://127.0.0.1/health 返回 `ok`

## 说明

- 后端服务当前在宿主机（IDE）启动，因此 `nginx.conf` 中的 upstream 指向
  `host.docker.internal:8080`（compose 已配置 `extra_hosts`）。
  后续后端也容器化时，把 upstream 改为 `gateway-service:8080` 即可。
- 前端开发调试建议直接用 Vite（5173），此时不经过 Nginx；
  Vite 已配置 `/api` 代理到 8080。
- 缓存策略刻意分成三段，避免「重新构建后页面打不开」：
  - `/assets/**`（文件名带哈希）：`try_files $uri =404` + 一年强缓存。**不能**回退到
    `index.html`——否则浏览器会拿 `text/html` 当 JS 模块加载，直接报 MIME 类型错误。
  - `/index.html` 与 SPA 路由：`no-cache, no-store`，保证每次拿到最新入口页。
  - 其余路径：`try_files $uri $uri/ /index.html`，交给前端路由处理。