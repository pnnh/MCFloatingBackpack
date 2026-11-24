# Data Model: 064

无持久化数据（`DataRevision` 6，协议号 22，不新增网络包）。运行时类型：

## `NestingRules.Verdict`

`OK` · `IS_BACKPACK`（物品本身是浮动背包）· `CONTAINS_BACKPACK`（里面含有浮动背包，结构化递归或通用兜底命中，或超过深度上限）。

## `NestingRules.Probe<T>`（接口，纯逻辑与 Minecraft 的接缝）

`isBackpack(T)` · `needsScan(T)`（是否带容器类数据，否则只看自身）· `children(T)`（原版容器、收纳袋里的物品）· `mentionsBackpack(T)`（通用兜底：编码后含五个浮动背包的物品 id 之一）。

`NestingRules.check(probe, stack)`：`isBackpack` → `IS_BACKPACK`；`!needsScan` → `OK`；否则递归 `children`（深度上限 8，超过 → `CONTAINS_BACKPACK`），再 `mentionsBackpack` → `CONTAINS_BACKPACK`；否则 `OK`。

## `NestedOrigins<K, S>`（每个玩家至多一条）

`record Origin(K backpack, S stack)`；`remember(player, backpack, stack)` 覆盖旧的；`allowsReturn(player, backpack, stack, sameStack)`：同一背包且 `sameStack` 为真才返回真，**返回真即清除**；`clear(player)`。不存盘。

## `BackpackInventory`（改）

`mainTargetOrder(stack)`：`NestingRules.check(...) != OK` 时返回三段全空；新增 `mainTargetOrderInternal(stack)` 即原来的行为。
