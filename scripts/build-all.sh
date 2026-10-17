#!/usr/bin/env bash
# 根目录统一构建：先后端（Maven 多模块），再前端（Vite）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> [1/2] 构建后端"
cd "$ROOT_DIR"
mvn -DskipTests clean package

echo "==> [2/2] 构建前端"
cd "$ROOT_DIR/frontend"
if [ ! -d node_modules ]; then
  echo "    未发现 node_modules，执行 npm install"
  npm install
fi
npm run build

echo "==> 构建完成"
echo "    后端产物：backend/**/target/*.jar"
echo "    前端产物：frontend/dist"