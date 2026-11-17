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

export JAVA_HOME="${JAVA_HOME:-}"
mvn -f server/pom.xml spring-boot:run &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT

pnpm -C web install
pnpm -C web dev
