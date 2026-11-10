# Contract: 提示与日志

## 聊天（语言键，中英文，两个文件键集合保持一致）

**服务器发给本人**：`player.sendSystemMessage(Component.translatable(…))`（客户端有同一个版本的语言键，所以可以用语言键；带模组名前缀；只有这位玩家看得到）。参数 `%1$s` 背包的修订，`%2$s` 当前版本的修订。

| 键 | zh_cn | en_us |
|---|---|---|
| `message.mcfloatingbackpack.revision.protected` | 这只背包是用更新版本的浮动背包保存的（数据修订 %1$s，当前版本是 %2$s）。继续使用可能丢失数据：建议升级模组，或先备份存档。如果确实要继续，请在 60 秒内再次打开它，这表示你接受风险。 | This backpack was saved by a newer version of MCFloatingBackpack (data revision %1$s; this version is %2$s). Using it may lose data: please update the mod, or back up your world first. If you really want to go on, open it again within 60 seconds, which means you accept the risk. |
| `message.mcfloatingbackpack.revision.accepted` | 已接受风险：这只背包现在按当前版本（数据修订 %2$s）使用。 | Risk accepted: this backpack is now used as this version (data revision %2$s) sees it. |

（提示在**第一次拒绝**时发一条；确认放行时发 `accepted` 一条；其它路径静默。）

## 服务器日志（经 `DiagLog`，前缀 `[diag server]`，遵守 `logging.level`）

| 情形 | 级别 | 内容 |
|---|---|---|
| 被保护（第一次打开被拒） | WARN | `Backpack <编号或"无编号"> of <玩家> was saved by a newer version (revision <栈上修订>, this version <当前>): refused to open it; asked the player to confirm` |
| 确认放行 | WARN | `<玩家> accepted the risk for backpack <编号> (revision <栈上> -> <当前>): the data this version does not know is dropped` |
| 自动拾取跳过 | WARN（每位玩家每分钟至多一条） | `Auto pickup skipped for <玩家>: the backpack <编号> was saved by a newer version (revision <栈上>, this version <当前>)` |
