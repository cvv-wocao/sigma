#!/usr/bin/env bash
# =====================================================================
# 查看容器日志
#   ./scripts/logs.sh                    跟踪全部服务日志（最近 100 行）
#   ./scripts/logs.sh gateway-service    只看某个服务
#   ./scripts/logs.sh mysql --tail=500   自定义行数
# =====================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_ARGS=(--profile app --profile monitoring)

if [ $# -gt 0 ] && [ "${1#-}" = "$1" ]; then
  SERVICE="$1"; shift
  docker compose "${COMPOSE_ARGS[@]}" logs -f "$@" "$SERVICE"
else
  docker compose "${COMPOSE_ARGS[@]}" logs -f --tail=100 "$@"
fi