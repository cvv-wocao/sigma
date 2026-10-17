#!/usr/bin/env bash
# =====================================================================
# java-agent-platform 一键启动
#
#   ./scripts/up.sh                    基础设施 + 5 个后端服务 + 前端（首次会构建镜像）
#   ./scripts/up.sh --with-monitoring  额外启动 Prometheus + Grafana
#   ./scripts/up.sh --infra-only       只启动 mysql / redis / nacos / nginx，后端在 IDE 里跑
#   ./scripts/up.sh --no-build         跳过前端构建与镜像重建，快速重启已有容器
#   ./scripts/up.sh --logs             启动后跟随日志
#
# 说明：Docker Desktop 未运行时脚本会尝试自动拉起并等待就绪；
#       容器不存在则创建、存在但已停止则启动、配置变更则重建（docker compose 幂等）。
#
# Windows 上用 Git Bash 执行：
#   "D:\Git\bin\bash.exe" scripts/up.sh
# =====================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

WITH_MONITORING=0
INFRA_ONLY=0
DO_BUILD=1
TAIL_LOGS=0

while [ $# -gt 0 ]; do
  case "$1" in
    --with-monitoring) WITH_MONITORING=1 ;;
    --infra-only)      INFRA_ONLY=1 ;;
    --no-build)        DO_BUILD=0 ;;
    --logs)            TAIL_LOGS=1 ;;
    -h|--help)         sed -n '2,16p' "$0"; exit 0 ;;
    *)                 echo "未知参数：$1（用 -h 查看用法）"; exit 1 ;;
  esac
  shift
done

c_step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }
c_ok()   { printf '    \033[32m[OK]\033[0m %s\n' "$1"; }
c_warn() { printf '    \033[33m[!]\033[0m %s\n' "$1"; }
c_fail() { printf '\n\033[31m[x] %s\033[0m\n' "$1" >&2; exit 1; }

env_get()    { grep -E "^$1=" .env 2>/dev/null | tail -n1 | cut -d= -f2- | tr -d '\r'; }
env_get_or() { local v; v="$(env_get "$1")"; printf '%s' "${v:-$2}"; }

# 尝试启动 Docker Desktop（Windows / macOS / Linux）
start_docker_desktop() {
  local kernel; kernel="$(uname -s)"
  case "$kernel" in
    MINGW*|MSYS*|CYGWIN*)
      local exe
      for exe in \
        "/d/Docker/DockerDesktop/Docker Desktop.exe" \
        "/c/Program Files/Docker/Docker/Docker Desktop.exe" \
        "/c/Program Files/Docker/Docker/Docker Desktop/Docker Desktop.exe"
      do
        if [ -f "$exe" ]; then
          if command -v powershell.exe >/dev/null 2>&1; then
            powershell.exe -NoProfile -Command "Start-Process -FilePath '$(cygpath -w "$exe")'" >/dev/null 2>&1 || true
          else
            ( "$exe" >/dev/null 2>&1 & ) || true
          fi
          echo "    已拉起 Docker Desktop：$(cygpath -w "$exe")"
          return 0
        fi
      done
      ;;
    Darwin)
      if [ -d "/Applications/Docker.app" ]; then
        open -a Docker && echo "    已拉起 Docker Desktop" && return 0
      fi
      ;;
    Linux)
      if command -v systemctl >/dev/null 2>&1 && sudo -n systemctl start docker >/dev/null 2>&1; then
        echo "    已启动 docker 服务"
        return 0
      fi
      ;;
  esac
  return 1
}

wait_for_docker() {
  local timeout="${1:-300}" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    if docker info >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
    waited=$((waited + 3))
    printf '.'
  done
  return 1
}

# ------------------------------ 0. 环境检查 ------------------------------
c_step "[0/5] 检查 Docker 环境"
command -v docker >/dev/null 2>&1        || c_fail "未找到 docker 命令，请先安装 Docker Desktop"
docker compose version >/dev/null 2>&1   || c_fail "未找到 docker compose（需要 Docker Compose v2 插件）"

if ! docker info >/dev/null 2>&1; then
  c_warn "Docker 守护进程未运行，尝试自动启动 Docker Desktop…"
  if start_docker_desktop; then
    printf '    等待 Docker 就绪'
    if wait_for_docker 300; then
      printf ' \033[32m就绪\033[0m\n'
    else
      printf ' \033[33m超时\033[0m\n'
      c_fail "Docker 在 5 分钟内未就绪，请手动打开 Docker Desktop 后重试"
    fi
  else
    c_fail "未能自动启动 Docker Desktop，请手动启动后重试"
  fi
fi
c_ok "Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null)"

# ------------------------------ 1. 环境变量 ------------------------------
c_step "[1/5] 准备环境变量"
if [ -f .env ]; then
  c_ok ".env 已存在，直接复用"
else
  cp .env.example .env
  c_warn "已从 .env.example 生成 .env，请补全 DEEPSEEK_API_KEY / GITEE_ACCESS_TOKEN 后重新执行"
fi

GATEWAY_PORT="$(env_get_or GATEWAY_HTTP_PORT 8080)"
NGINX_PORT="$(env_get_or NGINX_HTTP_PORT 80)"
NACOS_PORT="$(env_get_or NACOS_HTTP_PORT 8848)"
MYSQL_PORT="$(env_get_or MYSQL_PORT 3307)"
REDIS_PORT="$(env_get_or REDIS_PORT 6380)"

if [ "$INFRA_ONLY" = "0" ]; then
  [ -n "$(env_get DEEPSEEK_API_KEY)" ]   || c_warn "DEEPSEEK_API_KEY 为空 → AI 对话会报错"
  [ -n "$(env_get GITEE_ACCESS_TOKEN)" ] || c_warn "GITEE_ACCESS_TOKEN 为空 → Gitee 关键词搜索会返回空"
fi

# ------------------------- 2. 前端产物（Nginx 挂载） -------------------------
if [ "$DO_BUILD" = "1" ] && [ "$INFRA_ONLY" = "0" ]; then
  c_step "[2/5] 构建前端产物（Nginx 直接挂载 frontend/dist）"
  if [ ! -d frontend/node_modules ]; then
    c_warn "未发现 frontend/node_modules，先执行 npm install"
    (cd frontend && npm install)
  fi
  (cd frontend && npm run build)
  c_ok "frontend/dist 已更新"
else
  c_step "[2/5] 跳过前端构建"
  [ -d frontend/dist ] || c_warn "frontend/dist 不存在，Nginx 会返回 404（去掉 --no-build 重跑）"
fi

# ------------------------------ 3. 启动容器 ------------------------------
PROFILE_ARGS=()
UP_ARGS=(-d)
if [ "$INFRA_ONLY" = "0" ]; then
  PROFILE_ARGS+=(--profile app)
  if [ "$WITH_MONITORING" = "1" ]; then
    PROFILE_ARGS+=(--profile monitoring)
  fi
  if [ "$DO_BUILD" = "1" ]; then
    UP_ARGS+=(--build)
  fi
fi

c_step "[3/5] 启动容器：docker compose${PROFILE_ARGS[*]:+ ${PROFILE_ARGS[*]}} up ${UP_ARGS[*]}"
docker compose ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} up "${UP_ARGS[@]}"
c_ok "容器已提交启动（不存在会创建，已停止会启动，配置变更会重建）"

# ------------------------------ 4. 等待就绪 ------------------------------
wait_tcp() {
  local name="$1" port="$2" timeout="${3:-90}" waited=0
  printf '    %-16s' "$name"
  while [ "$waited" -lt "$timeout" ]; do
    if (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null; then
      exec 3<&- 3>&- 2>/dev/null || true
      printf '\033[32m就绪\033[0m (%ss)\n' "$waited"
      return 0
    fi
    sleep 2; waited=$((waited + 2)); printf '.'
  done
  printf '\033[33m超时\033[0m（容器可能仍在启动，用 ./scripts/logs.sh 看日志）\n'
  return 1
}

wait_http() {
  local name="$1" url="$2" timeout="${3:-120}" waited=0 code
  printf '    %-16s' "$name"
  while [ "$waited" -lt "$timeout" ]; do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$url" || true)"
    if [ -n "$code" ] && [ "$code" != "000" ]; then
      printf '\033[32m就绪\033[0m (%ss, http %s)\n' "$waited" "$code"
      return 0
    fi
    sleep 3; waited=$((waited + 3)); printf '.'
  done
  printf '\033[33m超时\033[0m\n'
  return 1
}

c_step "[4/5] 等待服务就绪"
wait_tcp  "MySQL"        "$MYSQL_PORT"  120 || true
wait_tcp  "Redis"        "$REDIS_PORT"   60 || true
wait_http "Nacos"        "http://127.0.0.1:${NACOS_PORT}/nacos" 180 || true

if [ "$INFRA_ONLY" = "0" ]; then
  wait_http "gateway-service"  "http://127.0.0.1:${GATEWAY_PORT}/actuator/health" 300 || true
  wait_http "user-service"     "http://127.0.0.1:8081/actuator/health" 150 || true
  wait_http "ai-service"       "http://127.0.0.1:8082/actuator/health" 150 || true
  wait_http "search-service"   "http://127.0.0.1:8083/actuator/health" 150 || true
  wait_http "project-service"  "http://127.0.0.1:8084/actuator/health" 150 || true
  wait_http "前端(Nginx)"      "http://127.0.0.1:${NGINX_PORT}/" 60 || true
fi

# ------------------------------ 5. 结果汇总 ------------------------------
c_step "[5/5] 当前容器状态"
docker compose ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} ps

echo ""
echo "  访问入口"
echo "    前端（Nginx）   http://localhost:${NGINX_PORT}"
echo "    网关 API        http://localhost:${GATEWAY_PORT}/api"
echo "    Nacos 控制台    http://localhost:${NACOS_PORT}/nacos"
echo "    MySQL           127.0.0.1:${MYSQL_PORT}   root / $(env_get_or MYSQL_ROOT_PASSWORD root)"
echo "    Redis           127.0.0.1:${REDIS_PORT}   密码 $(env_get_or REDIS_PASSWORD redis123)"
if [ "$WITH_MONITORING" = "1" ]; then
  echo "    Prometheus      http://localhost:$(env_get_or PROMETHEUS_PORT 9090)"
  echo "    Grafana         http://localhost:$(env_get_or GRAFANA_PORT 3000)   admin / admin"
fi
echo ""
echo "  常用操作"
echo "    跟随日志        ./scripts/logs.sh  [服务名]"
echo "    健康检查        ./scripts/health-check.sh"
echo "    停止全部        ./scripts/stop-all.sh"
echo ""

if [ "$TAIL_LOGS" = "1" ]; then
  c_step "跟随日志（Ctrl+C 退出，不会停止容器）"
  docker compose ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} logs -f --tail=50
fi