#!/bin/bash
# 操作容器里的游戏窗口并截图。坐标是 1280×720 虚拟屏幕上的坐标（游戏窗口 854×480，左上角约在 213,120）。
# 用法：
#   gui.sh click X Y [等待秒]    移动、左键点击（"点一下拿起、再点一下放下"，拖拽手势不灵）
#   gui.sh rclick [等待秒]       右键
#   gui.sh sclick X Y [等待秒]   Shift+左键
#   gui.sh key 按键 [等待秒]     xdotool 的键名，如 b、e、Escape、slash、Return、1
#   gui.sh type 文本             输入文字（先 key slash 打开聊天）
#   gui.sh drag X1 Y1 X2 Y2      按下—移动—松开（在本项目的界面里不可靠）
#   gui.sh shot 名字             截图，输出文件路径（放在 $AIGUI_SHOTS）
# 除 shot 外，每个动作结束后都会再截一张 last.png。
. "$(dirname "$0")/common.sh"
D="docker exec -e DISPLAY=:99 $AIGUI_CONTAINER"
case "$1" in
  click) $D sh -c "xdotool mousemove $(( $2 - 6 )) $(( $3 - 6 )); sleep 0.3; xdotool mousemove $2 $3; sleep 0.3; xdotool click 1"; sleep "${4:-1.5}"; "$0" shot last;;
  rclick) $D sh -c "xdotool click 3"; sleep "${2:-1.5}"; "$0" shot last;;
  sclick) $D sh -c "xdotool mousemove $(( $2 - 6 )) $(( $3 - 6 )); sleep 0.3; xdotool mousemove $2 $3; sleep 0.3; xdotool keydown shift click 1 keyup shift"; sleep "${4:-1.5}"; "$0" shot last;;
  key)   $D xdotool key --delay 80 "$2"; sleep "${3:-1.5}"; "$0" shot last;;
  type)  $D xdotool type --delay 80 "$2"; sleep 1; "$0" shot last;;
  drag)  $D sh -c "xdotool mousemove $2 $3; sleep 0.3; xdotool mousedown 1; sleep 0.3; xdotool mousemove $(( ($2+$4)/2 )) $(( ($3+$5)/2 )); sleep 0.3; xdotool mousemove $4 $5; sleep 0.3; xdotool mouseup 1"; sleep 1; "$0" shot last;;
  shot)  $D import -window root "/tmp/$2.png" && docker cp "$AIGUI_CONTAINER:/tmp/$2.png" "$AIGUI_SHOTS/$2.png" >/dev/null; echo "$AIGUI_SHOTS/$2.png";;
  *) sed -n 2,14p "$0"; exit 1;;
esac
