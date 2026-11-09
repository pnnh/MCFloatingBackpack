# Data Model: 背包编号与登记表（精简版）

## 1. 内部标识 `BackpackIdentity`（纯）

```text
BackpackIdentity { UUID uuid }          // 数据组件 backpack_identity，只有这一个字段
  String id()                           // = BackpackIds.of(uuid)
```

- 持久化编解码：`uuid`（字段名 `uuid`）。读取时忽略其它字段（最初版本还存过 `id` 与 `revision`）。
- 网络编解码：16 字节的 UUID。
- 复制的栈相等、编号相同。

## 2. 编号 `BackpackIds`（纯）

```text
FIRST = "ABCDEFGHJKLMNPQRSTUVWXYZ"        (24)
REST  = FIRST + "23456789"                 (32)
TOTAL = 24 * 32 * 32 = 24576
isValid(id) / index(id) / fromIndex(i)
of(UUID) -> String                         // mix(mix(lsb) ^ msb) 对 TOTAL 取模；同一 UUID 永远同一个编号
```

没有 `random`：编号不查登记表、不避重。

## 3. 登记记录 `RegistryRecord` 与索引 `RegistryIndex`（纯）

```text
RegistryRecord { UUID uuid, UUID owner, int capacity, State state, long createdAt, long touchedAt }
State { NORMAL, DELETED }
RegistryIndex: Map<UUID, RegistryRecord>
  register(record)        // 内部标识已存在则抛 IllegalStateException
  update(uuid, change)    // 不得改内部标识；不存在返回 false
  get / contains / size / records()
```

没有按编号的索引、没有所有者计数、没有"剩余编号"。`owner` = 最后使用者。

## 4. 登记表 `BackpackRegistry`（Minecraft，`SavedData`）

名字 `mcfloatingbackpack_registry`；`RegistryIndex` + 影子副本 `Map<UUID, BackpackContents>` + 每条记录的编码缓存。保存只重编码改动过的条目；加载时单条解码失败、重复的内部标识跳过，整份损坏按空表。存档里每条记录的字段：`uuid owner capacity state created touched` 与 `contents`（影子）；读取时忽略旧版本的 `id`、`revision`。

## 5. 可用性检查 `BackpackGate`（Minecraft）

```text
acquire(player, ref):
  栈不是背包                                  -> 拒绝（空）
  栈有标识且登记表里是 DELETED                -> 提示 id.tombstone（限频），拒绝
  栈没有标识                                  -> 新 UUID，写回栈
  登记表没有这条记录                          -> 登记（所有者 = 当前玩家，影子 = 栈当前内容）
  否则                                        -> 所有者/档位变了、或超过 60 秒没更新：更新记录；档位变了：影子换成栈当前内容
  返回 BackpackInventory.fromItem(栈)

afterSave(player, stack):                     // BackpackRef.saveInventory 里，写回栈之前
  栈没有标识                                  -> 什么都不做
  登记表没有这条记录                          -> 登记
  记录不是 DELETED                            -> 所有者 = 当前玩家，档位、最后使用时间更新，影子 = 栈当前内容
```

唯一的拒绝是墓碑。没有数量检查、没有修订号、没有复制检测。

## 6. 提示限频 `NoticeLimiter`（纯）

每个（玩家，原因）在 30 秒窗口内只放行第一次；现在只有 `TOMBSTONE` 一种原因。

## 7. 标题栏几何 `TitleIdLayout`（纯）

起点 = `panelX + 4 + 16 + 3`；可用宽度 = `max(0, closeLeft − x − 2)`；`truncated` 当且仅当文字宽度大于可用宽度。（未变。）
