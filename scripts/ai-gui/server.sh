#!/bin/bash
# 在容器里启动专用服务器（./gradlew runServer），等到 Done 或崩溃。世界在 /work/run/world。
# 用法：server.sh <标签> ["额外 JVM 参数"]，例如 server.sh P3 "-Dmcfloatingbackpack.acceptDowngrade=true"
# 停服用 stop-server.sh（Gradle 不把标准输入转给服务端，`stop` 命令打不进去，只能发 SIGTERM 触发原版的关停钩子）。
. "$(dirname "$0")/common.sh"
TAG=$1; EXTRA=${2:-}
docker exec "$AIGUI_CONTAINER" sh -c "rm -f /work/server-$TAG.log"
docker exec -d -e JAVA_TOOL_OPTIONS="$(docker exec "$AIGUI_CONTAINER" printenv JAVA_TOOL_OPTIONS) $EXTRA" "$AIGUI_CONTAINER" sh -c "cd /work && ./gradlew runServer --console=plain > /work/server-$TAG.log 2>&1"
for _ in $(seq 1 70); do
  docker exec "$AIGUI_CONTAINER" sh -c "grep -qE 'Done \(|Encountered an unexpected exception|BUILD (FAILED|SUCCESSFUL)' /work/server-$TAG.log" && break
  sleep 5
done
echo "日志：容器里 /work/server-$TAG.log"
