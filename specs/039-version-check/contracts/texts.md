# Contract: 提示文字与日志

## 客户端聊天（语言键，中英文，两个文件键集合保持一致）

经 `FloatingPanelEvents.chat` 输出（带 `[模组名]` 前缀、只给本人）。参数 `%1$s` = 客户端版本，`%2$s` = 服务器版本。

| 键 | zh_cn | en_us |
|---|---|---|
| `message.mcfloatingbackpack.version.older` | 你的浮动背包版本（%1$s）比服务器（%2$s）旧：请升级到与服务器相同的版本。版本差异不宜过大，否则可能出现未知问题；升级前请先备份存档。 | Your MCFloatingBackpack (%1$s) is older than the server's (%2$s): please update to the server's version. A big gap between versions may cause unknown problems; back up your world before updating. |
| `message.mcfloatingbackpack.version.newer` | 你的浮动背包版本（%1$s）比服务器（%2$s）新：请降级到与服务器相同的版本，或联系服主把服务器升级。版本差异不宜过大，否则可能出现未知问题；降级前请先备份存档。 | Your MCFloatingBackpack (%1$s) is newer than the server's (%2$s): please downgrade to the server's version, or ask the server owner to update the server. A big gap between versions may cause unknown problems; back up your world before downgrading. |
| `message.mcfloatingbackpack.version.unknown` | 无法比较浮动背包的版本（你的：%1$s，服务器：%2$s）。请确认两端使用同一个版本；版本不一致可能出现未知问题，改版本前请先备份存档。 | Cannot compare the MCFloatingBackpack versions (yours: %1$s, server: %2$s). Make sure both use the same version; a mismatch may cause unknown problems, so back up your world before changing versions. |

## 老客户端（服务器发的纯文本，不用语言键）

`VersionNotices.legacyClientText(serverVersion)`：

```text
[MCFloatingBackpack] 你的浮动背包版本过旧（它没有版本检测功能），服务器版本是 <serverVersion>。请升级到与服务器相同的版本；升级前请先备份存档。
[MCFloatingBackpack] Your MCFloatingBackpack is too old (it has no version check); the server runs <serverVersion>. Please update to the server's version, and back up your world before updating.
```

（两行作为一条聊天消息，换行分隔。）

## 服务器日志

经 `DiagLog.log(Side.SERVER, level, …)`，前缀 `[diag server]`，遵守 `logging.level`：

| 情形 | 级别 | 内容 |
|---|---|---|
| report 到达且不一致 | WARN | `Version mismatch: <玩家> runs MCFloatingBackpack <客户端> (<CLIENT_OLDER/CLIENT_NEWER/UNCOMPARABLE>), the server runs <服务器>` |
| report 到达且一致 | DEBUG | `<玩家> runs MCFloatingBackpack <客户端> (matches the server)` |
| 老客户端 | WARN | `<玩家> has no MCFloatingBackpack version check (a version older than this feature); the server runs <服务器>; told it to update` |
| 诊断 WARN 头部（背包"不见了"） | 沿用原级别 | 原文末尾追加 `Client mod version: <PlayerVersions.describe>` |
