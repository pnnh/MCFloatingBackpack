#!/bin/bash
# 把工作区里的文件（已跟踪的加未被忽略的新文件）复制进容器的 /work，不挂载工作区，容器里的改动不会写回。
# 改了代码后重新运行一次，再 launch.sh。
set -euo pipefail
. "$(dirname "$0")/common.sh"
cd "$AIGUI_REPO"
# 工作区里已删除但还在索引里的文件会让 tar 报错，忽略这类错误
git ls-files -z --cached --others --exclude-standard | tar --null -T - -cf - 2>/dev/null | docker exec -i "$AIGUI_CONTAINER" tar --warning=no-unknown-keyword -xf - -C /work || true
echo "已同步到 $AIGUI_CONTAINER:/work"
