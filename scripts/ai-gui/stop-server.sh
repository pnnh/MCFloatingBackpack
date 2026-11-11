#!/bin/bash
# 让容器里的专用服务器正常关停（SIGTERM，原版关停钩子会保存世界），并等它退出。
. "$(dirname "$0")/common.sh"
docker exec "$AIGUI_CONTAINER" sh -c 'pkill -TERM -f "[f]ml.modFolders=mcfloatingbackpack"; true'
for _ in $(seq 1 30); do
  N=$(docker exec "$AIGUI_CONTAINER" sh -c 'ps -eo args | grep -c "[f]ml.modFolders"')
  [ "$N" = "0" ] && { echo "服务器已退出"; exit 0; }
  sleep 2
done
echo "服务器没有在 60 秒内退出"; exit 1
