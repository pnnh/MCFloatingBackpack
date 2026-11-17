# Data Model: 背包快照保留策略调整

## SnapshotKind（枚举，纯）

| 值 | 字节码 | 文件名后缀 | 每只背包保留 | 受保护 |
|---|---|---|---|---|
| `TIMED` | 1 | `-t` | 60 | 否 |
| `CHANGE` | 2 | （无） | 20 | 否 |
| `UPGRADE` | 3 | `-u` | 5 | 是（不被其它类的清理与容量淘汰删除） |

- 字节码 0 在协议里表示"全部"（只用于类别筛选，不是一个类别）。
- `fromByte`、`fromSuffix` 对不认识的值返回 `null`。

## SnapshotMeta（记录，纯）

`(UUID uuid, UUID owner, String ownerName, int capacity, long time, int slots, SnapshotKind kind, String fromVersion, String toVersion)`

- 旧的 6 字段构造保留一个便利的 `of(...)`（类别 `CHANGE`、版本为空），给现有调用与测试用。
- `fromVersion`、`toVersion` 只有 `UPGRADE` 非空，其它为 `""`。

## 快照文件（磁盘）

见 [contracts/storage.md](./contracts/storage.md)。头部字段：`version`（2）、`uuid`、`owner`、`ownerName`、`capacity`、`time`、`slots`、`kind`（字符串 `TIMED`/`CHANGE`/`UPGRADE`）、（升级前）`from_mod_version`、`to_mod_version`；`contents` 是背包内容的 NBT（原样搬运）。

## SnapshotState（记录，纯）→ `state.properties`

| 字段 | 含义 |
|---|---|
| `lastModVersion` | 上次运行记下的模组版本（字符串，可为空 = 没有记录） |
| `changeId` | 版本变化编号（整数，≥ 0；每次版本变化 +1） |
| `fromVersion` | 最近一次版本变化的旧版本（给提示用） |
| `pinned` | 最近一次版本变化钉住了几只背包的快照（给提示用） |
| `notified` | `changeId` 这一次已提示过的玩家 UUID 集合（有序，上限 10000，超出丢最早的） |

- 文本往返：`java.util.Properties` 风格的 `key=value`；`notified` 是逗号分隔的 UUID。
- 解析容错：缺字段、非数字、非法 UUID → 当"没有记录"（整个文件）或忽略那一项（名单里的坏 UUID）；永不抛。

## SnapshotSettings（记录，纯）

`(boolean enabled, int maxTotalMb, long timedIntervalMs, boolean clamped)`

- `from(boolean enabled, int configuredMb, Integer debugSeconds)`：`maxTotalMb = clamp(configuredMb, 128, 4096)`，`clamped` 记是否被夹；`timedIntervalMs = (debugSeconds ?? 900) * 1000`，调试秒数小于 1 当 900。
- `maxTotalBytes() = maxTotalMb * 1024 * 1024`（long）。

## TimedWindow<T>（纯）

状态：`pending: Map<UUID, T>`（脏的背包与最新内容）、`nextAt: long`。
- `mark(uuid, value, now)`：`pending.put`；首次调用时若 `nextAt == 0` 则 `nextAt = now + interval`。
- `due(now)`：若 `now >= nextAt`：取出全部 `pending` 并清空，`nextAt = now + interval`；否则空。
- `drainAll()`：不用（定时快照不在退出时额外写；退出写的是变更类）。

## UpgradePlan（纯）

输入：每只背包的快照元数据列表（认得的、新到旧）、当前版本、旧版本。输出：每只背包的动作 `Pin(time)`（最新一份不是升级前 → 钉住）、`UpdateTo(time)`（最新一份已是升级前 → 只更新新版本）、`Drop(times)`（升级前超过 5 份，要挤掉的最旧的）、`Skip`（没有快照）。

## SnapshotCapacity（纯）

输入：`List<File>`，`File(uuid, time, kind, bytes, newestOfItsBackpack)` 与 `maxBytes`；输出：要删的文件列表（按删除顺序），以及是否"只剩受保护的仍超限"。排序：先定时、后变更；同类里 `time` 小的先。受保护 = `kind == UPGRADE || newestOfItsBackpack`。

## LostCells（线程本地计数）

`add(int)`、`take()`（读出并清零，返回 int）、`reset()`。

## 版本变化提示（运行时，内存）

`SnapshotNotices.Pending(changeId, fromVersion, toVersion, pinned, direction (UPGRADE|DOWNGRADE|DISABLED))`：本次运行里检测到版本变化时创建，没有变化则为空。`direction` 由版本字符串比较决定：能解析成数字点分格式时按数值比，否则按"变化"算升级文字（降级判断只用于选文案，判断不了就用中性的"版本变化"文案——见 contracts/config-and-texts.md）。
