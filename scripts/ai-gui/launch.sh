#!/bin/bash
# 杀掉容器里旧的客户端，启动新的 runClient，等到标题界面，截一张图。
# 用法：launch.sh <标签> [Gradle 参数…]，例如 launch.sh A -Pmcfb.checkOwner=true -Pmcfb.serverVersion=0.2.0
# 联机/换账号：环境变量 AIGUI_GAME_ARGS 里的游戏参数会追加给客户端，如 AIGUI_GAME_ARGS="--username P1 --quickPlayMultiplayer mcfb-srv:25565"。
# 注意：只用来"换一次启动"。要保存世界请先在游戏里 Save and Quit（可能要按两次 Escape），不要依赖这里的 pkill。
. "$(dirname "$0")/common.sh"
TAG=$1; shift
if pgrep -f "fml.modFolders=mcfloatingbackpack" >/dev/null; then
  echo "宿主机上有游戏进程在运行（pgrep），先退出"; exit 1
fi
# 模式写成 [f]ml…，免得匹配到执行 pkill 的这条 shell 命令自己
docker exec "$AIGUI_CONTAINER" sh -c 'pkill -f "[f]ml.modFolders=mcfloatingbackpack"; true' >/dev/null 2>&1
sleep 3
docker exec "$AIGUI_CONTAINER" sh -c 'rm -f /work/run/logs/latest.log'
docker exec -d -e AIGUI_GAME_ARGS="${AIGUI_GAME_ARGS:-}" "$AIGUI_CONTAINER" sh -c "cd /work && ./gradlew -I scripts/ai-gui/game-args.init.gradle runClient --console=plain $* > /work/gradle-$TAG.log 2>&1"
for _ in $(seq 1 120); do
  docker exec "$AIGUI_CONTAINER" sh -c 'grep -q "atlas/gui.png-atlas" /work/run/logs/latest.log 2>/dev/null' && break
  docker exec "$AIGUI_CONTAINER" sh -c "grep -q 'BUILD FAILED' /work/gradle-$TAG.log" && { echo "BUILD FAILED，见容器里 /work/gradle-$TAG.log"; exit 1; }
  sleep 5
done
sleep 12
"$AIGUI_DIR/gui.sh" shot "launched-$TAG"
