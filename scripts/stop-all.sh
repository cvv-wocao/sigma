#!/usr/bin/env bash
# =====================================================================
# 停止项目
#
#   ./scripts/stop-all.sh          停止全部容器（基础设施 + 5 个后端 + 监控），保留数据卷
#   ./scripts/stop-all.sh -v       连数据卷一起删（MySQL / Redis / Nacos 数据会被清空）
#   ./scripts/stop-all.sh --infra  只停基础设施（后端在 IDE 里跑的场合用）
#
# 为什么必须带 profile：docker compose down 只处理「当前 profile 选中」的服务，
# 不带 --profile app 时 5 个后端容器不在项目模型里，会被漏下拉。
# =====================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REMOVE_VOLUMES=0
INFRA_ONLY=0
for arg in "$@"; do
  case "$arg" in
    -v|--volumes) REMOVE_VOLUMES=1 ;;
    --infra)      INFRA_ONLY=1 ;;
    -h|--help)    sed -n '2,11p' "$0"; exit 0 ;;
    *)            echo "未知参数：$arg（用 -h 查看用法）"; exit 1 ;;
  esac
done

PROFILE_TEXT=""
if [ "$INFRA_ONLY" = "0" ]; then
  PROFILE_ARGS=(--profile app --profile monitoring)
  PROFILE_TEXT="--profile app --profile monitoring "
else
  PROFILE_ARGS=()
fi

DOWN_ARGS=(down --remove-orphans)
if [ "$REMOVE_VOLUMES" = "1" ]; then DOWN_ARGS+=(--volumes); fi

echo "==> 停止：docker compose ${PROFILE_TEXT}${DOWN_ARGS[*]}"
docker compose ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} "${DOWN_ARGS[@]}"

echo ""
echo "==> 剩余容器（正常应为空）"
docker compose ps || true

echo ""
if [ "$REMOVE_VOLUMES" = "1" ]; then
  echo "    数据卷已删除：下次启动会重新执行 infrastructure/mysql/init 下的初始化 SQL。"
else
  echo "    数据卷保留。要连数据一起清空：./scripts/stop-all.sh -v"
fi
echo "    重新启动：./scripts/up.sh --no-build（不重建镜像，最快）"
echo ""