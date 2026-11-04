#!/bin/bash
# 度量共用方块图集的尺寸，并与预算比较（specs/010-backpack-texture-budget）。
#
# 做法：起一次 ./gradlew runClient，等游戏创建图集后读日志里的
#   Created: <W>x<H>x<N> minecraft:textures/atlas/blocks.png-atlas
# 一行。图集的宽或高超过预算则非零退出；日志里找不到这一行时也非零退出（避免"没测到"被当成"通过"）。
#
# 用法：
#   design/demo/tools/check_atlas_budget.sh            # 预算默认 2048
#   BUDGET=4096 design/demo/tools/check_atlas_budget.sh
#
# 退出码：0 通过；1 超预算；2 没有测到（启动失败或超时）。
#
# 注意：图集尺寸由所有进入图集的精灵共同决定，要在"干净客户端"（原版 + 本模组，没有其它模组与资源包）
# 下度量才有可比性。图集尺寸是实测值，显存是按尺寸推算的（W×H×4 字节 × 1.332，5 级 mipmap，1MB=10^6 字节，与 research.md 同口径），
# 不是 GPU 直接读数。

set -u
BUDGET="${BUDGET:-2048}"
WAIT_SECONDS="${WAIT_SECONDS:-60}"
REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
LOG="$REPO/run/logs/latest.log"
cd "$REPO" || exit 2

rm -f "$LOG"
echo "启动客户端度量图集（最多 ${WAIT_SECONDS} 秒）……"

# 没有 timeout 命令（macOS 默认没有）时用 gtimeout；都没有就自己后台等待再杀
if command -v timeout >/dev/null 2>&1; then
  timeout "$WAIT_SECONDS" ./gradlew runClient -q >/dev/null 2>&1
elif command -v gtimeout >/dev/null 2>&1; then
  gtimeout "$WAIT_SECONDS" ./gradlew runClient -q >/dev/null 2>&1
else
  ./gradlew runClient -q >/dev/null 2>&1 &
  PID=$!
  sleep "$WAIT_SECONDS"
  kill "$PID" 2>/dev/null
  pkill -P "$PID" 2>/dev/null
fi

# 超时只会杀掉 gradle 外壳，真正的 Minecraft 客户端 JVM 会留着（实测）。按它特有的启动参数清理，
# 不依赖 pgrep 的复杂正则；这个参数只出现在本模组的开发客户端里。
CLIENT_PATTERN="fml.modFolders=mcfloatingbackpack"
pkill -f "$CLIENT_PATTERN" 2>/dev/null
sleep 3

LINE="$(grep "blocks.png-atlas" "$LOG" 2>/dev/null | grep "Created:" | tail -1)"
if [ -z "$LINE" ]; then
  echo "没有在 $LOG 里找到 blocks.png-atlas 的 Created 行：客户端可能没有启动到创建图集，或超时太短。"
  echo "（可以调大 WAIT_SECONDS，或手动运行 ./gradlew runClient 查看报错。）"
  exit 2
fi

SIZE="$(echo "$LINE" | sed -E 's/.*Created: ([0-9]+)x([0-9]+)x([0-9]+).*/\1 \2 \3/')"
set -- $SIZE
W="$1"; H="$2"; MIP="$3"
VRAM="$(python3 -c "print(round($W*$H*4*1.332/1000000,1))")"

echo "共用方块图集：${W}x${H}（mip 级别 ${MIP}），估算显存约 ${VRAM}MB（由尺寸推算，非 GPU 实测）"
echo "预算：宽、高都不超过 ${BUDGET}"

if pgrep -f "$CLIENT_PATTERN" >/dev/null 2>&1; then
  echo "警告：度量结束后仍有客户端进程在运行（已尝试清理），请手动关闭。"
fi

if [ "$W" -gt "$BUDGET" ] || [ "$H" -gt "$BUDGET" ]; then
  echo "结果：超预算（${W}x${H} > ${BUDGET}x${BUDGET}）"
  exit 1
fi
echo "结果：通过"
exit 0
