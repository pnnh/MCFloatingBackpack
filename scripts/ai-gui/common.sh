# 公共变量，由其它脚本 source。可以用环境变量覆盖。
AIGUI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AIGUI_REPO="$(cd "$AIGUI_DIR/../.." && pwd)"
AIGUI_CONTAINER="${AIGUI_CONTAINER:-mcfb-gui}"
AIGUI_IMAGE="${AIGUI_IMAGE:-mcfb-gui-test}"
# 截图放这里（不进仓库）
AIGUI_SHOTS="${AIGUI_SHOTS:-${TMPDIR:-/tmp}/mcfb-ai-gui/shots}"
# 下载的 arm64 原生库缓存
AIGUI_CACHE="${AIGUI_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/mcfb-ai-gui}"
mkdir -p "$AIGUI_SHOTS"
# 在容器里执行，带虚拟屏幕
aigui_x() { docker exec -e DISPLAY=:99 "$AIGUI_CONTAINER" "$@"; }
aigui_sh() { docker exec "$AIGUI_CONTAINER" sh -c "$1"; }
