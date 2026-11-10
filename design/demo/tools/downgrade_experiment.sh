#!/bin/bash
# 降级实验的准备工作（specs/040-downgrade-protection，quickstart 第三部分）。
#
# 目的：用旧版本的构建打开新版本保存过的世界，看旧版本对背包数据做了什么（字段丢失？嵌套物品丢失？整只背包消失？）。
#
# 这个脚本只做准备，**不启动游戏，不改你的原世界**：
#   1. 检查没有客户端在运行；
#   2. 把世界完整复制一份到 ~/Desktop/<世界名>-backup-<时间>（备份，已存在则拒绝覆盖）；
#   3. 用 git worktree 在 ../MCFloatingBackpack-old 检出旧提交（默认 e1207ea，030，早于 035 的扩展行；不要用 031～034：它们引用的 snapshot/net 包当年被 .gitignore 误忽略、从没进过 git，检出后编译不过），
#      并把世界再复制一份进那个工作树的 run/saves/ 里（实验在这份副本上做）；
#   4. 打印后续要你手动做的步骤。
#
# 用法：
#   design/demo/tools/downgrade_experiment.sh <世界目录> [旧提交]          # 例：run/saves/"New World"
#   design/demo/tools/downgrade_experiment.sh --dry-run <世界目录> [旧提交] # 只打印会做什么，什么都不做
#
# 退出码：0 完成（或 dry-run）；1 有客户端在运行、目录已存在或参数不对。

set -u
DRY=0
if [ "${1:-}" = "--dry-run" ]; then
  DRY=1
  shift
fi
WORLD="${1:-}"
COMMIT="${2:-e1207ea}"
if [ -z "$WORLD" ] || [ ! -f "$WORLD/level.dat" ]; then
  echo "用法：$0 [--dry-run] <世界目录（里面要有 level.dat）> [旧提交，默认 e1207ea]" >&2
  exit 1
fi
REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
NAME="$(basename "$WORLD")"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$HOME/Desktop/${NAME}-backup-${STAMP}"
OLD="$REPO/../MCFloatingBackpack-old"

if pgrep -f "fml.modFolders=mcfloatingbackpack" >/dev/null; then
  echo "有 MCFloatingBackpack 客户端在运行，先关掉它再做实验（同一时刻只开一个客户端）。" >&2
  exit 1
fi
if [ -e "$BACKUP" ]; then
  echo "备份目录已存在，拒绝覆盖：$BACKUP" >&2
  exit 1
fi
if [ -e "$OLD/run/saves/$NAME" ]; then
  echo "旧工作树里已经有同名世界，拒绝覆盖：$OLD/run/saves/$NAME" >&2
  exit 1
fi

run() {
  echo "+ $*"
  if [ "$DRY" = "0" ]; then
    "$@" || { echo "失败：$*" >&2; exit 1; }
  fi
}

echo "世界：${WORLD}"
echo "旧提交：${COMMIT}（$(git -C "$REPO" log -1 --format=%s "$COMMIT" 2>/dev/null || echo '找不到这个提交')）"
[ "$DRY" = "1" ] && echo "（dry-run：只打印，不执行）"
echo

run cp -R "$WORLD" "$BACKUP"
if [ -d "$OLD" ]; then
  echo "旧工作树已存在，沿用：${OLD}（请确认它检出的是 ${COMMIT}）"
else
  run git -C "$REPO" worktree add "$OLD" "$COMMIT"
fi
run mkdir -p "$OLD/run/saves"
run cp -R "$WORLD" "$OLD/run/saves/$NAME"

cat <<STEPS

准备好了。接下来由你手动做（我不会替你启动游戏或改世界）：

 1. 备份在：$BACKUP
    实验用的世界副本在：$OLD/run/saves/$NAME
 2. cd "$OLD" && ./gradlew runClient      （先确认没有别的客户端在运行）
 3. 进入世界 "$NAME"，观察并记下：
      a. 玩家物品栏里那只背包还在吗？（还在 / 消失 / 变成空背包）
      b. 日志（run/logs/latest.log）里有没有 "Tried to load invalid item"？有几条？
      c. 打开面板：背包里的内容少了什么？（工具区里的扩展件？嵌套的物品？隐藏行里的物品？）
      d. 随便点一个格子，触发一次保存，然后退出世界。
 4. 对比退出后的存档（用 NBT 工具看 $OLD/run/saves/$NAME/playerdata/<uuid>.dat 与备份里同名文件）：
      隐藏行物品、extension_rows 还在吗？data_revision（旧版本没有这个字段）呢？
 5. 把观察逐条写进 specs/040-downgrade-protection/research.md 的"实验结果"一节；
    如果背包整只消失（H3），建议另开 041。

清理（做完实验后）：git worktree remove "$OLD"
STEPS
