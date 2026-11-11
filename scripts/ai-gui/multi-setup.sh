#!/bin/bash
# 联机验证用：建一个服务端容器与两个客户端容器（同一个镜像、同一个 Docker 网络），各有自己的 run 卷。
# Gradle 缓存卷不能三个容器共用（缓存锁靠本机进程通信，跨容器会 "Timeout waiting to lock journal cache"）：
# 服务端容器沿用 mcfb-gradle，两个客户端容器各用一份从它复制出来的卷（约 1.5 GB，只在不存在时复制）。
# 用法：scripts/ai-gui/multi-setup.sh        重建三个容器（run 卷保留，删卷才会丢世界）
# MULTI_ROLES="c1 c2" 只重建指定的容器（服务端在跑时不想动它）。
# 之后用环境变量选容器：AIGUI_CONTAINER=mcfb-c1 ./gui.sh shot a；服务端容器名 mcfb-srv，两个客户端 mcfb-c1、mcfb-c2。
set -euo pipefail
. "$(dirname "$0")/common.sh"
docker image inspect "$AIGUI_IMAGE" >/dev/null 2>&1 || { echo "先跑一次 setup.sh 建镜像与原生库"; exit 1; }
[ -f "$AIGUI_CACHE/lwjgl-natives/liblwjgl.so" ] || { echo "先跑一次 setup.sh 下载原生库"; exit 1; }
if pgrep -f "fml.modFolders=mcfloatingbackpack" >/dev/null; then echo "宿主机上有游戏进程在运行（pgrep），先退出"; exit 1; fi
docker network inspect mcfb-net >/dev/null 2>&1 || docker network create mcfb-net >/dev/null
JTO=""; [ "$(docker run --rm "$AIGUI_IMAGE" uname -m)" = "aarch64" ] && JTO="-Dorg.lwjgl.librarypath=/natives"
for n in ${MULTI_ROLES:-srv c1 c2}; do
  c=mcfb-$n
  G=mcfb-gradle; [ "$n" = srv ] || G=mcfb-gradle-$n
  if [ "$G" != mcfb-gradle ] && ! docker volume inspect "$G" >/dev/null 2>&1; then
    docker run --rm -v mcfb-gradle:/from:ro -v "$G":/to "$AIGUI_IMAGE" sh -c 'cd /from && tar --exclude=./daemon --exclude="*.lock" -cf - . | tar -xf - -C /to'
  fi
  docker rm -f "$c" >/dev/null 2>&1 || true
  docker run -d --name "$c" --hostname "$c" --network mcfb-net --shm-size=1g \
    -v "$G":/root/.gradle -v "mcfb-run-$n":/work/run \
    -e JAVA_TOOL_OPTIONS="$JTO" "$AIGUI_IMAGE" sleep infinity >/dev/null
  [ -n "$JTO" ] && docker cp "$AIGUI_CACHE/lwjgl-natives/." "$c:/natives"
  case $n in c*) docker exec -d "$c" Xvfb :99 -screen 0 1280x720x24;; esac
  AIGUI_CONTAINER=$c "$AIGUI_DIR/sync.sh"
done
echo "容器 mcfb-srv / mcfb-c1 / mcfb-c2 就绪（网络 mcfb-net，客户端用主机名 mcfb-srv:25565 连服务端）"
