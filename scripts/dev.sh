#!/usr/bin/env bash
# 开发模式：后端 8080 + 前端 5173（Vite 代理 /api）。不使用容器。
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# spring-boot:run 会把 JVM 工作目录切到 server/，相对路径 SQLITE_PATH 会落到 server/data
# 另起一个库：表现是「删了 data 里的库，页面还是旧数据」。这里统一按仓库根解析。
case "${SQLITE_PATH:-}" in
  /*) ;;
  *) SQLITE_PATH="$PWD/data/panel-studio.db" ;;
esac
export SQLITE_PATH

export JAVA_HOME="${JAVA_HOME:-}"
mvn -f server/pom.xml spring-boot:run &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT

pnpm -C web install
pnpm -C web dev
