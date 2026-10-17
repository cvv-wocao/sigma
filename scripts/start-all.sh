#!/usr/bin/env bash
# 启动基础设施，并提示后端各服务的启动方式
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f .env ]; then
  echo "==> 未发现 .env，从 .env.example 复制"
  cp .env.example .env
fi

echo "==> 启动基础设施（mysql / redis / nacos / nginx）"
docker compose up -d

echo "==> 基础设施状态"
docker compose ps

cat <<'TIP'

==> 后端服务启动方式（任选其一）
    IDE：分别运行 GatewayApplication / UserApplication / AiApplication / SearchApplication
    命令行：
      mvn -pl backend/gateway-service spring-boot:run
      mvn -pl backend/user-service    spring-boot:run
      mvn -pl backend/ai-service      spring-boot:run
      mvn -pl backend/search-service  spring-boot:run

==> 前端：cd frontend && npm run dev
==> Nacos 控制台：http://127.0.0.1:8848/nacos
TIP