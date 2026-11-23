# 数据模型：被 Accessories 丢掉的背包

本 feature 不改背包存储数据（`DataRevision` 仍 6），不新增存储；下面是运行时的概念。

## `Entry`（读文件得到）

| 字段 | 含义 |
|---|---|
| `slotName` | 所在的 Accessories 槽位类型名（`accessories_containers` 的键，如 `floating_backpack`） |
| `cosmetic` | 在 `cosmetics` 列表里（true）还是 `items`（false） |
| `itemId` | 物品 id，五个浮动背包之一 |
| `identity` | `backpack_identity.uuid`（四个 int）或 `null`（没有编号） |
| `stackTag` | 完整的物品 NBT（用于还原成物品栈） |

## `Pending`（按玩家 UUID，登录读文件时建立，核对后删除）

- `entries`：文件里**所有**槽位类型下的浮动背包（有效与否都包含，核对时再按现在的槽位类型分出孤立的）；
- `fileUnidentified`：`物品 id → 个数`，文件里的无编号背包总数（物品栏 `Inventory` + 全部 Accessories 容器，真实与装饰）。

## `Live`（核对时从玩家身上收集）

- `identities`：在场的编号集合（物品栏、光标、Accessories 现有容器、地上 16 格内的掉落物）；
- `unidentified`：`物品 id → 个数`，现在的无编号背包总数（同一个域）；
- `validSlotTypes`：Accessories 现在给这个玩家的槽位类型名集合。

## 规则（`OrphanRules.decide`，纯）

```
orphans = entries 中 slotName ∉ validSlotTypes 的
identified = orphans 中 identity ≠ null 且 identity ∉ live.identities
unidentifiedByItem = orphans 中 identity = null，按 itemId 分组
for 每个 itemId: missing = max(0, fileUnidentified[itemId] − live.unidentified[itemId])
                 取 unidentifiedByItem[itemId] 的前 min(missing, 个数) 只
返回 identified ∪ 上面选出的
```

性质（测试断言）：返回的都是孤立的；带编号且在场的一只都不返回；无编号的数量永不超过缺的数量，也不超过孤立的数量；没有孤立的就返回空；`validSlotTypes` 包含条目的槽位名时不返回；同一只不出现两次。
