#!/usr/bin/env bash
# 演示构建：web/dist → server 静态目录 → 单个可执行 jar（java -jar 一条命令跑完整应用）
set -euo pipefail
cd "$(dirname "$0")/.."

pnpm -C web install
pnpm -C web build

STATIC_DIR="server/src/main/resources/static"
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -R web/dist/. "$STATIC_DIR/"

mvn -f server/pom.xml clean package -DskipTests=false

# 密钥边界：构建产物里不允许出现疑似模型 Key
if grep -rInE "sk-[A-Za-z0-9]{20,}" "$STATIC_DIR" > /dev/null 2>&1; then
  echo "构建失败：前端产物中发现疑似密钥" >&2
  exit 1
fi

echo "构建完成：java -jar server/target/panel-studio.jar"
