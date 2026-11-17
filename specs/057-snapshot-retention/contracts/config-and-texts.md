# Contract: 配置、调试开关、语言键

## 服务端配置（`mcfloatingbackpack-server.toml`，在已有的 `[logging]` 旁加 `[snapshots]`）

```toml
[snapshots]
	# Whether this mod keeps snapshots of backpacks (copies of what a backpack holds, written outside the world).
	# WARNING: with this off no snapshot is written, nothing is protected when a new version of the mod is
	# installed, and a mistake, a bug or damaged data can no longer be undone: the snapshots are the only backup of
	# the backpacks that is not inside the world. Existing snapshots are not deleted and can still be looked at and restored.
	# Read when the server starts; a change needs the world to be reopened.
	enabled = true
	# The most disk space, in megabytes, all the snapshots of this world may use together. Allowed 128 - 4096;
	# a value outside is changed to the nearest (and the log says so). When the limit is passed the oldest timed snapshots,
	# then the oldest change snapshots, are deleted; the newest snapshot of each backpack and the "before an update"
	# snapshots are never deleted for this, so the folder can stay above the limit.
	# A small limit means older snapshots go sooner. Read when the server starts.
	maxTotalMb = 768
```

- `SnapshotConfig.define(builder)`：`builder.push("snapshots")` … `pop()`；`ENABLED = builder.comment(...).define("enabled", true)`；`MAX_TOTAL_MB = builder.comment(...).defineInRange("maxTotalMb", 768, Integer.MIN_VALUE, Integer.MAX_VALUE)`（见 research R6：不用范围校验，自己夹取）。
- `DiagConfig` 的静态块：`BUILDER.pop()` 之后 `SnapshotConfig.define(BUILDER)`。
- `SnapshotSettings.from(...)` 夹取越界值并在 `SnapshotService.start` 里记一条 WARN："snapshots.maxTotalMb is X, outside 128–4096; using Y"。

## 调试开关（`VersionDebug`）

- `timedSnapshotSeconds()`：`Integer`，读 `System.getProperty("mcfloatingbackpack.debug.timedSnapshotSeconds")`，不是数字或 < 1 → `null`（用默认 900）。开发运行里 `-Pmcfb.timedSnapshotSeconds=5`（`build.gradle` 现有的转发把 `-Pmcfb.<name>` 变成 `-Dmcfloatingbackpack.debug.<name>`，不用改）。
- 文档（`VersionDebug` 的类注释）加一条。

## 语言键（中英都要；服务器控制台一侧沿用命令的英文兜底）

**聊天提示**（`SnapshotNotices`，前缀同 `message.mcfloatingbackpack.prefix`）：

| 键 | 中文 | 英文 | 参数 |
|---|---|---|---|
| `message.mcfloatingbackpack.snapshot.upgraded` | 模组已从 %s 更新到 %s；%s 只背包升级前的快照已保护。若发现东西不见了，可在快照窗口里选标着"升级前"的快照恢复；如果这次更新删除了某些物品，在新版本里恢复也拿不回它们，需要回到旧版本再恢复。 | The mod was updated from %s to %s; the snapshots from before the update of %s backpack(s) are protected. If something is missing, restore one marked "before update" in the snapshot window; if this update removed some items, restoring in the new version cannot bring them back: go back to the old version and restore there. | from, to, pinned |
| `message.mcfloatingbackpack.snapshot.downgraded` | 模组已从 %s 降到 %s；%s 只背包降级前的快照已保护。降级可能让新版本才有的物品丢失，可以用快照窗口里标着"升级前"的快照恢复。 | The mod was downgraded from %s to %s; the snapshots from before the change of %s backpack(s) are protected. A downgrade can lose items only the newer version has; restore one marked "before update" in the snapshot window. | from, to, pinned |
| `message.mcfloatingbackpack.snapshot.changed` | 模组版本从 %s 变为 %s；%s 只背包变化前的快照已保护，可在快照窗口里标着"升级前"的快照恢复。 | The mod version changed from %s to %s; the snapshots from before the change of %s backpack(s) are protected; restore one marked "before update" in the snapshot window. | from, to, pinned |
| `message.mcfloatingbackpack.snapshot.disabled` | 模组版本从 %s 变为 %s，但备份功能已关闭，没有任何快照被保护。 | The mod version changed from %s to %s, but snapshots are turned off, so nothing was protected. | from, to |
| `message.mcfloatingbackpack.snapshot.lost` | 读取你的背包时有 %s 格无法识别（对应物品已在新版本中移除），已留空。升级前的快照还在，见快照窗口。 | %s cell(s) of your backpacks could not be read (the items were removed in this version) and were left empty. Snapshots from before the update are kept: see the snapshot window. | cells |

**窗口**（`gui.mcfloatingbackpack.snapshot.*`）：

| 键 | 中文 | 英文 |
|---|---|---|
| `button.kind` | 类别：%s | Kind: %s |
| `kind.all` / `kind.timed` / `kind.change` / `kind.upgrade` | 全部 / 定时 / 变更 / 升级前 | All / Timed / Change / Before update |
| `kind.mark.timed` / `.change` / `.upgrade` | 定 / 变 / 升 | T / C / U |
| `version.to` | → %s | → %s |
| `result.restored_lost` | 给了 %s 一只新背包（%s）。其中 %s 格因物品已不存在无法恢复；原始快照文件仍保留，回到旧版本可完整恢复。 | Gave %s a new backpack of %s. %s cell(s) could not be restored because the items no longer exist; the snapshot file is kept, restore it in the old version to get everything back. |

（`result.restored_lost` 的参数顺序同 `RESTORED`：目标、背包 id，再加格数；`SnapshotResultText.placeholders` 与 `args` 同改。）

**命令**（`command.mcfloatingbackpack.*`）：`restore.lost`（英文兜底里同 `restore.ok` 再加一句）、`info` 里每份的类别标记（`info.version` 的文字加一个 `[%s]`）。

## 测试对照

资源测试检查：以上键在 zh_cn 与 en_us 里都有、两份语言文件键集合相同、占位符个数与 `SnapshotResultText.placeholders` 一致；配置注释里含 "WARNING" 与 "only backup"（保证风险说明没被删）。
