# Data Model: 快照管理界面

没有新增任何持久化数据。下面是**传输与界面状态**里用到的结构（全部在内存里）。

## 复用（030 已有）

- `SnapshotMeta(uuid, owner, ownerName, capacity, time, slots)`：一份快照的元数据。
- `SnapshotListing.Group(newest, versions)`、`Filter(owner, inactiveDays)`、`Page`。
- `BackpackSelector`、`AgeText`（客户端也会用 `AgeText` 显示"距今"，它是纯逻辑）。

## 传输结构（`snapshot/net/` 或 `network/`，纯数据 + 编解码）

| 结构 | 字段 | 说明 |
|---|---|---|
| `Row` | `uuid`, `owner(UUID)`, `ownerName`, `capacity`, `newestTime`, `slots`, `versions` | 列表一行（= `Group` 的传输形式） |
| `VersionRow` | `time`, `slots`, `ownerName`, `capacity` | 版本列表一行；"第几份"= 在列表中的序号（从新到旧，1 起） |
| `Scope`（枚举） | `MINE`, `ALL` | `ALL` 仅 op 有效 |
| `ActionKind`（枚举） | `RESTORE`, `DELETE_ONE`, `DELETE_ALL`, `TRIM_PREVIEW`, `TRIM_CONFIRM` | 仅 op 有效 |
| `ResultCode`（枚举） | 成功：`RESTORED`, `DELETED_ONE`, `DELETED_ALL`, `TRIM_PREVIEWED`, `TRIM_DONE`；失败：`NOT_FOUND`, `NO_PERMISSION`, `BAD_VERSION`, `TARGET_OFFLINE`, `NO_TIER`, `UNREADABLE`, `TOO_LARGE`, `FAILED`, `UNAVAILABLE` | 客户端把它映射成状态文字的语言键 |

## 界面状态（`SnapshotScreenState`，纯逻辑）

| 字段 | 含义 |
|---|---|
| `isOp` | 最近一次服务端响应里的权限标志；默认 false |
| `scope` | `MINE` / `ALL`；非 op 时强制 `MINE` |
| `ownerFilter`, `inactiveDays` | 过滤输入（仅 op；空 / −1 表示不过滤） |
| `page`, `pages`, `total` | 列表分页（页码从 1 起） |
| `rows` | 当前页的 `Row` 列表 |
| `selected` | 选中的背包 UUID（可空） |
| `versions` | 选中背包的 `VersionRow` 列表 |
| `selectedVersion` | 选中的版本序号（1 起，可空） |
| `preview` | 当前预览（容量、非空格数、物品栈列表）或失败原因 |
| `target` | 恢复目标玩家名（空 = 自己） |
| `pending` | 待二次确认的动作：`NONE` / `DELETE_ALL` / `TRIM`（带天数与预览得到的数量） |
| `status` | 底部状态文字的码与参数（`ResultCode` 或客户端自己的"加载中"等） |

**不变式**：`!isOp` ⇒ `scope == MINE`、没有过滤、没有 `target`、`pending == NONE`；`selected == null` ⇒ `selectedVersion == null`、`preview == null`、所有 op 动作不可用；`pending != NONE` 时再点别的动作先取消待确认。

## 按钮可用性（纯函数，可单测）

`恢复`、`删除这一份`：op 且选中了版本；`删除全部`：op 且选中了背包；`清理预览`：op 且天数输入合法；`确认`按钮只在 `pending != NONE` 时出现。
