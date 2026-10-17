#!/usr/bin/env bash
# =====================================================================
# 运行状态总览 + 健康检查
#
#   ./scripts/health-check.sh           容器状态 + 端口/HTTP 健康检查
#   ./scripts/health-check.sh --quiet   只输出异常项（适合放进 CI，有异常则退出码为 1）
#
# 端口一律从项目根目录 .env 读取，改端口不用改这个脚本。
# Windows 上用 Git Bash 执行："D:\Git\bin\bash.exe" scripts/health-check.sh
# =====================================================================
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

QUIET=0
if [ "${1:-}" = "--quiet" ]; then QUIET=1; fi

env_get()    { grep -E "^$1=" .env 2>/dev/null | tail -n1 | cut -d= -f2- | tr -d '\r'; }
env_get_or() { local v; v="$(env_get "$1")"; printf '%s' "${v:-$2}"; }

MYSQL_PORT="$(env_get_or MYSQL_PORT 3307)"
REDIS_PORT="$(env_get_or REDIS_PORT 6380)"
NACOS_PORT="$(env_get_or NACOS_HTTP_PORT 8848)"
NACOS_GRPC_PORT="$(env_get_or NACOS_GRPC_PORT 9848)"
GATEWAY_PORT="$(env_get_or GATEWAY_HTTP_PORT 8080)"
NGINX_PORT="$(env_get_or NGINX_HTTP_PORT 80)"
PROMETHEUS_PORT="$(env_get_or PROMETHEUS_PORT 9090)"
GRAFANA_PORT="$(env_get_or GRAFANA_PORT 3000)"

FAILED=0
ok()   { if [ "$QUIET" = "0" ]; then printf '  \033[32m[OK]\033[0m   %-22s %s\n' "$1" "$2"; fi; }
skip() { if [ "$QUIET" = "0" ]; then printf '  \033[33m[--]\033[0m   %-22s %s\n' "$1" "$2"; fi; }
fail() { FAILED=$((FAILED + 1)); printf '  \033[31m[FAIL]\033[0m %-22s %s\n' "$1" "$2"; }

running() { docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$1"; }

check_tcp() {
  local name="$1" host="$2" port="$3"
  if (exec 3<>"/dev/tcp/${host}/${port}") 2>/dev/null; then
    exec 3<&- 3>&- 2>/dev/null || true
    ok "$name" "${host}:${port}"
  else
    fail "$name" "${host}:${port} 未监听"
  fi
}

check_http() {
  local name="$1" url="$2" code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" || true)"
  case "$code" in
    # 401/403 也算通过：说明请求已经到达后端，只是鉴权拦下了，链路是通的
    200|301|302|401|403) ok "$name" "$url (http ${code})" ;;
    *) fail "$name" "$url (${code:-无响应})" ;;
  esac
}

if [ "$QUIET" = "0" ]; then
  printf '\n\033[36m==> 容器状态\033[0m\n'
  docker compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null \
    || printf '  (docker 未运行或未启动任何容器)\n'
fi

printf '\n\033[36m==> 基础设施\033[0m\n'
check_tcp  "MySQL"          127.0.0.1 "$MYSQL_PORT"
check_tcp  "Redis"          127.0.0.1 "$REDIS_PORT"
check_http "Nacos 控制台"    "http://127.0.0.1:${NACOS_PORT}/nacos"
check_tcp  "Nacos gRPC"     127.0.0.1 "$NACOS_GRPC_PORT"
check_http "Nginx 静态页"    "http://127.0.0.1:${NGINX_PORT}/"
check_http "Nginx->网关->用户"  "http://127.0.0.1:${NGINX_PORT}/api/user/me"

printf '\n\033[36m==> 后端服务\033[0m\n'
if running jap-gateway-service; then
  check_http "gateway-service"  "http://127.0.0.1:${GATEWAY_PORT}/actuator/health"
  check_http "user-service"     "http://127.0.0.1:8081/actuator/health"
  check_http "ai-service"       "http://127.0.0.1:8082/actuator/health"
  check_http "search-service"   "http://127.0.0.1:8083/actuator/health"
  check_http "project-service"  "http://127.0.0.1:8084/actuator/health"
else
  skip "后端 5 个服务" "未启动（./scripts/up.sh 启动；--infra-only 则在 IDE 里跑）"
fi

printf '\n\033[36m==> 监控（默认不启动）\033[0m\n'
if running jap-prometheus; then
  check_http "Prometheus" "http://127.0.0.1:${PROMETHEUS_PORT}/-/healthy"
else
  skip "Prometheus" "未启动（./scripts/up.sh --with-monitoring 可开启）"
fi
if running jap-grafana; then
  check_http "Grafana" "http://127.0.0.1:${GRAFANA_PORT}/api/health"
else
  skip "Grafana" "未启动（./scripts/up.sh --with-monitoring 可开启）"
fi

printf '\n'
if [ "$FAILED" = "0" ]; then
  printf '\033[32m全部通过\033[0m\n\n'
else
  printf '\033[31m%s 项异常\033[0m —— 未启动的组件可用 ./scripts/up.sh 拉起，看日志用 ./scripts/logs.sh\n\n' "$FAILED"
fi

if [ "$QUIET" = "1" ] && [ "$FAILED" != "0" ]; then exit 1; fi
exit 0