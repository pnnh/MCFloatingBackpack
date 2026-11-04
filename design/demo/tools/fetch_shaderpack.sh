#!/bin/bash
# 下载开发用光影包到 run/shaderpacks/（specs/010-backpack-texture-budget，T013）。
#
# 默认 Complementary Reimagined（支持 LabPBR 法线）。run/ 不进 git，所以光影包不随仓库分发，
# 新机器上跑一次本脚本即可。需要 curl 和 python3。
#
#   design/demo/tools/fetch_shaderpack.sh                 # 默认
#   PROJECT=bsl-shaders design/demo/tools/fetch_shaderpack.sh
#
# 之后：./gradlew runClientShader，游戏里 选项 → 视频设置 → 光影包 选择它。

set -eu
PROJECT="${PROJECT:-complementary-reimagined}"
REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
DEST="$REPO/run/shaderpacks"
UA="MCFloatingBackpack-dev"
mkdir -p "$DEST"

INFO="$(curl -sf -H "User-Agent: $UA" "https://api.modrinth.com/v2/project/$PROJECT/version?loaders=%5B%22iris%22%5D" \
  || curl -sf -H "User-Agent: $UA" "https://api.modrinth.com/v2/project/$PROJECT/version")"
URL_NAME="$(printf '%s' "$INFO" | python3 -c "
import sys, json
v = [x for x in json.load(sys.stdin) if x['version_type'] == 'release'][0]
f = [f for f in v['files'] if f['primary']][0]
print(f['url'], f['filename'])")"
URL="${URL_NAME% *}"; NAME="${URL_NAME#* }"

if [ -f "$DEST/$NAME" ]; then echo "已存在：$DEST/$NAME"; else
  curl -sfL -H "User-Agent: $UA" -o "$DEST/$NAME" "$URL"
  echo "已下载：$DEST/$NAME"
fi

# 让 Iris 启动即选中这个光影包（run/config/iris.properties 不存在时才写；已存在就不覆盖用户的设置）
CFG="$REPO/run/config/iris.properties"
mkdir -p "$REPO/run/config"
if [ ! -f "$CFG" ]; then
  printf 'shaderPack=%s\nenableShaders=true\n' "$NAME" > "$CFG"
  echo "已写入 $CFG"
else
  echo "iris.properties 已存在，未改动；如需选用请在游戏里 选项 → 视频设置 → 光影包 选择 $NAME"
fi
