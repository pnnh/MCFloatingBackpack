# Quickstart: 验证快照保留策略调整

## 自动测试

```bash
./gradlew compileJava test
```

预期：全部通过，包含：
- `SnapshotKindTest`、`SnapshotRetentionTest`（60/20/5、每类独立、只对该类动手）、`SnapshotCapacityTest`（顺序、受保护、只剩受保护）、`TimedWindowTest`（到点、脏标记、替换式、间隔）、`SnapshotStateTest`（往返、缺失/损坏/坏 UUID、名单上限）、`UpgradePlanTest`（钉住/更新/挤掉/没有快照）、`SnapshotSettingsTest`（夹取 128～4096、`clamped`、调试秒数）、`ThrottleTest`；
- 改过的 `SnapshotFilesTest`（后缀往返、不认识的名字返回 -1）、`SnapshotRequestRulesTest`（MAX_VERSIONS=100、页大小、类别）、`SnapshotNetTypesTest`（行、版本页的编解码与越界）、`SnapshotPolicyTest`（被 `SnapshotRetentionTest` 取代或改写）；
- 资源与接线检查：语言键（中英同集合、占位符个数）、配置注释含风险说明、协议号 `"21"`（八处旧断言同改）、`LoadFromFile` 与登录与 `ServerTickEvent.Pre` 监听、钉住"先写后校验再删旧"的顺序、`DataRevision.CURRENT == 6`。

## 游戏里（容器 `mcfb-c1`，见 docs/plan/ai-gui-verification.md）

准备：`export AIGUI_CONTAINER=mcfb-c1`；备份测试世界；`pgrep -f "[f]ml.modFolders=mcfloatingbackpack"` 确认宿主上没有游戏在跑（有则等用户退出）；`scripts/ai-gui/sync.sh`、`launch.sh <tag> -PnoJei -Pmcfb.timedSnapshotSeconds=5`。

| # | 场景 | 预期 |
|---|---|---|
| 1 | 改一只背包的内容，等 5 秒的窗口 | `…/snapshots/<世界>/<uuid-id>/` 里出现 `<time>-t.dat`（定时）；之后再等一个窗口且没改 → 不新增 |
| 2 | 改内容再停 10 秒 | 出现 `<time>.dat`（变更）；与上一份变更内容相同则不新增 |
| 3 | 连续改 70 次、每次等够一个窗口 | 定时类停在 60 份；变更类停在 20 份；用 `ls` 数 |
| 4 | 把 `state.properties` 的 `last_mod_version` 改成 `0.0.1`，重启 | 每只有快照的背包的最新一份变成 `-u`；`bp_dump.py` 或 NBT 转储对比钉住前后的 `contents` **完全相同**；`state.properties` 的 `change_id` +1、`notified` 为空 |
| 5 | 钉住之后再改背包、写 25 份变更 | `-u` 文件仍在 |
| 6 | 钉住后登录 | 聊天里一条"更新"提示（参数正确）；退出再登录 → 没有；`notified` 里有该玩家 |
| 7 | 第二个玩家（`--username P2`）在重启后才登录 | 补发一次 |
| 8 | 把 `last_mod_version` 改成一个更高的版本 | 提示是"降级"的文案；同样钉住 |
| 9 | 删除 `state.properties` 再启动 | 不钉住、不提示；文件被重新写出 |
| 10 | 在快照里造含不存在物品 id 的背包（改快照文件里的物品 id）→ 用窗口恢复 | 结果提示"其中 N 格因物品已不存在无法恢复……"，N 正确；新背包里那些格是空的，其余在原位 |
| 11 | 玩家数据里的背包含不存在的物品 id（056 的 `LenientStacks`）→ 登录 | 聊天里"读取你的背包时有 N 格无法识别"；日志有 WARN |
| 12 | `maxTotalMb = 1`（越界）；造够多快照超过 128 MB（用大背包） | 启动日志 WARN，改用 128；先删最旧的定时，再删最旧的变更；每只最新一份与 `-u` 不删；只剩受保护时日志 WARN，且每小时最多一条 |
| 13 | `enabled = false` | 改背包、等窗口 → 不再有新文件；已有文件数不变；`/mcfb snapshot list`、窗口、恢复照常；版本变化时不钉住，提示是"备份已关闭" |
| 14 | 快照窗口：造一只 85 份快照的背包（定时 60、变更 20、升级前 5） | 版本区分页正确（页数 = ceil(85/页大小)）；类别按钮循环；每行有类别标记；升级前类带 `→ 版本`；筛选后总数正确 |
| 15 | 在筛选与翻页之后点预览、恢复、删除 | 作用于被点的那一份（对照时间）；已被清理的那份 → 提示不存在，不动别的 |
| 16 | `/mcfb snapshot restore 85`、`restore 101`、`delete 100` | 85 可用；101 被命令解析拒绝；100 超出该背包份数时提示"没有这一份" |
| 17 | 放一份 `version=99` 的文件和一份后缀 `-x` 的文件到某背包文件夹 | 列表看不见；写满 85 份并触发容量淘汰后它们仍在磁盘上；日志对 `version=99` 那份有一条 INFO |
| 18 | 放一份 `version=1` 的旧文件（无 `kind`、无后缀） | 按变更类列出，参与变更类的保留 |
| 19 | 第一次列出：20 只背包各 85 份，量 `/mcfb snapshot list all` 的耗时 | < 约 2 秒；否则按 research R15 优化 |

记录：每项的实际结果；没验证的写明原因；主观项（窗口的手感与排版）等用户确认再勾。

## 日志优先

少用 OCR：`latest.log` 里的 `[CHAT]` 行判断提示；`ls`/`stat` 判断文件；`bp_dump.py` 与 NBT 转储判断内容；截图前把鼠标移开。
