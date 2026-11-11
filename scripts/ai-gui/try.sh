#!/bin/bash
# launch + enter + 打印聊天行与版本相关日志 + 截一张聊天图。用法：try.sh <标签> [Gradle 参数…]（039 一类"启动参数 → 聊天提示"的验证）
. "$(dirname "$0")/common.sh"
TAG=$1
"$AIGUI_DIR/launch.sh" "$@" >/dev/null
"$AIGUI_DIR/enter.sh" 38 >/dev/null
"$AIGUI_DIR/gui.sh" key t 1.5 >/dev/null
cp "$AIGUI_SHOTS/last.png" "$AIGUI_SHOTS/$TAG-chat.png"
"$AIGUI_DIR/gui.sh" key Escape 1 >/dev/null
echo "== $TAG"
docker exec "$AIGUI_CONTAINER" sh -c 'grep -a "\[CHAT\]\|Version mismatch\|diag server" /work/run/logs/latest.log | cut -c1-420'
echo "-- (以上为全部相关行)"
