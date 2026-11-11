#!/bin/bash
# 从标题界面进入第一个世界。用法：enter.sh [等待秒，默认 40]
# 第一次（没有世界）要手工创建：点 Singleplayer，改成创造模式，点 Create New World（坐标见 README）。
. "$(dirname "$0")/common.sh"
G="$AIGUI_DIR/gui.sh"
$G click 638 323 3 >/dev/null
$G click 600 255 1 >/dev/null
$G click 480 515 "${1:-40}"
