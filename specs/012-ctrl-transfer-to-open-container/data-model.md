# Data Model: 012-ctrl-transfer-to-open-container

不新增持久化数据，也不改网络包字段。这里只定义服务端"转出到打开的容器"这一步用到的抽象。

## SlotView（对菜单里一个槽位的只读描述，纯逻辑用）

| 字段 | 含义 |
|---|---|
| menuIndex | 该槽位在菜单 `slots` 里的下标（`quickMoveStack` 要用的就是它） |
| belongsToPlayerInventory | `slot.container == 玩家 Inventory`（包括盔甲、副手，它们和玩家物品栏共用同一个容器） |
| containerSlot | `belongsToPlayerInventory` 时是玩家 `Inventory` 里的下标（0–8 快捷栏，9–35 主物品栏，36–39 盔甲，40 副手）；其余槽位为 -1。只有 0–35 才能做临时槽位 |
| hasItem | 槽位当前是否有物品 |
| mayPickup | `slot.mayPickup(player)` |
| mayPlaceSource | `slot.mayPlace(要转的栈)` |
| isSelectedHotbar | 是 `Inventory.selected` 对应的那一格 |

## "有打开的容器"的判定

`player.containerMenu != player.inventoryMenu` 且 `menu.slots` 里存在 `container != 玩家 Inventory` 的槽位（`belongsToPlayerInventory == false`）。

## 临时槽位选择（纯逻辑，可单测）

输入：`List<SlotView>`；输出：被选中的 `menuIndex`、是否需要换出，或 "无可用槽位"。

| 优先级 | 条件 | 需要换出 |
|---|---|---|
| 1 | 玩家槽位、`!hasItem`、`mayPlaceSource` | 否 |
| 2 | 玩家槽位、`hasItem`、`containerSlot ∈ [9,35]`、`mayPickup`、`mayPlaceSource`、非 `isSelectedHotbar` | 是 |
| 3 | 以上都没有 | — 本次不转移，物品留在背包 |

同一优先级内取 `menuIndex` 最小的，保证结果确定、可测试。

## DeliveryResult

| 字段 | 说明 |
|---|---|
| leftover | 没有被容器接收、要放回背包的那部分（可能为空） |
| movedIntoContainer | 是否有物品被容器接收（仅用于日志） |
| conserved | 守恒检查结果：转移前后该种物品总数（菜单所有槽位 + 光标 + 背包）相等 |
| failure | 调用中是否捕获了异常（有则记日志） |

## 状态转移（一次转出一个栈）

```text
有打开的容器？ ──否──► 011 的路径（直接放进玩家物品栏 0–35）
        │是
        ▼
选临时槽位 ──无可用──► leftover = 原栈（留在背包）
        │有
        ▼
[换出 X（若需要）] → 把栈放进临时槽位
        │
        ▼
循环 quickMoveStack（上限保护；异常被捕获）
        │
        ▼
取回临时槽位里剩下的 → leftover；[把 X 放回]
        │
        ▼
守恒检查 → 记日志（不回滚）；leftover 写回背包槽位
```

## 与 011 的关系

- `QuickTransferRequest`、`QuickTransferLogic`、网络包、客户端手势：不改。
- 改动只在 `BackpackQuickTransferPayload` 处理 `TO_INVENTORY` 的那个分支：先判断有没有打开的容器，有则走上面的流程，没有则走 011 原路径。

---

# 补充（2026-11-05）：反向——容器 → 浮动背包

## Side 与组

| Side | 含义 | 所属组 | 请求里用什么下标 |
|---|---|---|---|
| `INVENTORY` | 玩家物品栏槽位（0–35） | 转入背包 | 物品栏下标（011 的包） |
| `CONTAINER` | 打开着的容器自己的槽位 | 转入背包 | **菜单槽位下标** + `containerId`（新包） |
| `PANEL` | 浮动面板槽位 | 转出 | 背包下标（011 的包） |

一次拖动的组由起点决定；组内的两种槽位混着滑过都处理。

## TakeFromContainerRequest（客户端 → 服务端）

| 字段 | 规则 |
|---|---|
| containerId | 必须等于服务端 `player.containerMenu.containerId`，否则整个请求作废 |
| menuSlots | 菜单槽位下标，无重复；每项 ∈ [0, menu.slots.size())；不是玩家物品栏槽位（`slot.container != 玩家 Inventory`）；数量 ≤ menu.slots.size() |

校验失败（id 不匹配、越界、重复、超限、光标非空、没有打开的容器）→ **整包拒绝，不改任何物品**。

## 来源槽位资格（逐个槽位，服务端再判一次）

`slot.mayPickup(player)` 为真，且 `slot.mayPlace(slot.getItem())` 为真（"能把这件物品放回去"，排除输出类槽位）；不满足的槽位跳过，不报错。

## 状态转移（一个容器槽位 → 背包）

```text
槽位有资格且有物品？ ──否──► 跳过
        │是
        ▼
取出：removed = slot.safeTake(count, count, player)  （调用 onTake；为空则跳过）
        │
        ▼
放进背包：rest = moveInto(removed, 背包 0..size-1)
        │
        ▼
rest 非空：放回原槽位  safeInsert(rest) → 仍有剩余 → slot.set(剩余) → 玩家物品栏 → player.drop
        │
        ▼
守恒检查（来源槽位 + 背包 + 回退去处）→ 记日志（不回滚）
```

不变量：物品总数守恒；容器槽位里堆叠 >64 时只转走背包放得下的数量，其余留在原槽位。
