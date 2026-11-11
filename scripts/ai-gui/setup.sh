#!/bin/bash
# 建好（或重建）自动验证用的 Linux 容器：镜像、LWJGL arm64 原生库、容器、虚拟屏幕、仓库副本。
# 用法：scripts/ai-gui/setup.sh        （第一次约几分钟，下载镜像与原生库；Gradle 缓存和 run 目录放在命名卷里，重建不丢）
set -euo pipefail
. "$(dirname "$0")/common.sh"

docker info >/dev/null 2>&1 || { echo "Docker 没有运行"; exit 1; }
if pgrep -f "fml.modFolders=mcfloatingbackpack" >/dev/null; then
  echo "宿主机上有游戏进程在运行（pgrep），先退出再继续；容器里的客户端不受影响，但项目约定一次只开一个客户端"; exit 1
fi

docker image inspect "$AIGUI_IMAGE" >/dev/null 2>&1 || docker build -t "$AIGUI_IMAGE" "$AIGUI_DIR"

ARCH="$(docker run --rm "$AIGUI_IMAGE" uname -m)"
JTO=""
if [ "$ARCH" = "aarch64" ]; then
  # Gradle 在 arm64 容器里会解析出 x64 的 LWJGL 原生库（natives-linux），要手工换成 arm64 的。版本跟 Minecraft 1.21.1 一致（3.3.3）。
  LW=3.3.3
  if [ ! -f "$AIGUI_CACHE/lwjgl-natives/liblwjgl.so" ]; then
    mkdir -p "$AIGUI_CACHE/jars" "$AIGUI_CACHE/lwjgl-natives"
    for m in lwjgl lwjgl-glfw lwjgl-jemalloc lwjgl-openal lwjgl-opengl lwjgl-stb lwjgl-tinyfd; do
      curl -fsSL -o "$AIGUI_CACHE/jars/$m.jar" "https://repo1.maven.org/maven2/org/lwjgl/$m/$LW/$m-$LW-natives-linux-arm64.jar"
      unzip -oqj "$AIGUI_CACHE/jars/$m.jar" '*.so' -d "$AIGUI_CACHE/lwjgl-natives"
    done
  fi
  JTO="-Dorg.lwjgl.librarypath=/natives"
fi

docker rm -f "$AIGUI_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$AIGUI_CONTAINER" --shm-size=1g \
  -v mcfb-gradle:/root/.gradle -v mcfb-run:/work/run \
  -e JAVA_TOOL_OPTIONS="$JTO" "$AIGUI_IMAGE" sleep infinity >/dev/null
[ -n "$JTO" ] && docker cp "$AIGUI_CACHE/lwjgl-natives/." "$AIGUI_CONTAINER:/natives"
docker exec -d "$AIGUI_CONTAINER" Xvfb :99 -screen 0 1280x720x24
"$AIGUI_DIR/sync.sh"
echo "容器 $AIGUI_CONTAINER 就绪。下一步：scripts/ai-gui/launch.sh <标签> [-Pmcfb.… 参数]"
