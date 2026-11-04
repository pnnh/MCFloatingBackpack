# Data Model: 011-ctrl-quick-transfer

本功能不新增任何持久化数据：背包内容仍保存在物品实例的数据组件上（005/006），玩家物品栏仍是原版 `Inventory`。这里只定义**请求**与**客户端手势状态**。

## 槽位集合

| 名称 | 范围 | 说明 |
|---|---|---|
| 物品栏下标（InventoryIndex） | 0–35 | 玩家 `Inventory` 的 `items`：0–8 快捷栏，9–35 主物品栏。不含盔甲（36–39）、副手（40）。 |
| 背包下标（BackpackIndex） | 0 – (容量−1) | 当前打开的那只背包的格子，容量 36/54/72/90/108。 |

## Direction（`QuickTransferRequest.Direction`）

| 值 | 含义 |
|---|---|
| `TO_BACKPACK` | 来源是物品栏下标，目标是背包 |
| `TO_INVENTORY` | 来源是背包下标，目标是物品栏（0–35） |

## TransferRequest（客户端 → 服务端）

| 字段 | 规则 |
|---|---|
| direction | `QuickTransferRequest.Direction` |
| slots | 来源一侧的下标列表，**无重复**；`TO_BACKPACK` 时每项 ∈ [0,35]，至多 36 项；`TO_INVENTORY` 时每项 ∈ [0, 背包容量−1]，至多 108 项 |

校验失败（越界、重复、超限、背包不可用、光标非空）→ **整包拒绝，不改任何物品**（FR-012）。

## TransferResult（服务端内部，用于测试）

| 字段 | 说明 |
|---|---|
| 每个来源下标的剩余数量 | 放不下的部分留在来源槽位 |
| 目标侧被修改的下标集合 | 用于同步 |
| **不变量** | 所有涉及槽位的物品总数（按物品种类汇总）转移前后相等；没有任何槽位超过其堆叠上限 |

## 搬运算法（见 research Decision 6）

对一个来源栈，按目标下标升序：
1. 第一遍：对每个目标槽位，若同种（物品与组件相同）且未满 → 并入，直到来源用完或目标满。
2. 第二遍：对每个空目标槽位 → 放入不超过堆叠上限的数量。
3. 返回剩余；剩余写回来源槽位。

跳过规则：`TO_BACKPACK` 时跳过"当前背包本身所在的物品栏下标"（FR-010）。

## GestureState（客户端，不持久化）

```text
IDLE
  └─ 按下 Ctrl+左键，命中有效槽位，面板可见，光标为空
        → ACTIVE(direction, visited = {该槽位}, lastX, lastY)，并立即处理该槽位
ACTIVE
  ├─ 每帧：线段 (lastX,lastY)→(x,y) 采样命中；新命中、方向一致且不在 visited 的槽位加入本帧待发并记入 visited
  ├─ 每帧至多发一个 BackpackQuickTransferPayload
  └─ 任一条件成立 → IDLE：Ctrl 松开 / 左键松开 / 面板隐藏 / 打开或关闭了界面 / 光标上拿起了物品
```

| 字段 | 说明 |
|---|---|
| direction | 由起点所在一侧决定，整次拖动不变（FR-004） |
| visited | 本次拖动已处理过的"来源下标"集合，保证同一槽位只处理一次（FR-003） |
| lastX / lastY | 上一帧的鼠标 GUI 坐标，用于线段采样 |
