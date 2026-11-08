#!/usr/bin/env bash
# 知音听书 单模块部署重启脚本（服务器端）
# 用法: bash /opt/tingshu/restart.sh <模块目录名，如 server-gateway>
# 约定: jar 由 CI 上传到 /opt/tingshu/，日志写 /opt/tingshu/logs/
set -euo pipefail

MODULE="$1"
DIR=/opt/tingshu
NAME="tingshu-${MODULE}"

JAR=$(find "$DIR" -maxdepth 1 -name "${MODULE}*.jar" ! -name "*sources*" | head -1)
if [ -z "${JAR}" ]; then
  echo "[restart] 未找到 ${MODULE} 的 jar"
  exit 1
fi

# 停旧进程：先按 pidfile 优雅停，再按命令行兜底
if [ -f "$DIR/${NAME}.pid" ]; then
  OLD=$(cat "$DIR/${NAME}.pid" 2>/dev/null || true)
  if [ -n "${OLD}" ] && kill -0 "$OLD" 2>/dev/null; then
    echo "[restart] 停止旧进程 $OLD"
    kill "$OLD" 2>/dev/null || true
    sleep 5
  fi
fi
pkill -f "java .*${JAR}" 2>/dev/null || true

mkdir -p "$DIR/logs"
echo "[restart] 启动 $JAR"
nohup java -Dfile.encoding=UTF-8 -jar "$JAR" \
  > "$DIR/logs/${NAME}.log" 2>&1 &
echo $! > "$DIR/${NAME}.pid"
echo "[restart] 已启动 pid=$(cat "$DIR/${NAME}.pid") 日志: $DIR/logs/${NAME}.log"
