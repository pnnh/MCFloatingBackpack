# Data Model: 服务端库存与面板内取放

## BackpackInventory（服务端，持久化）

挂在玩家 Attachment 上。

| 字段 | 规则 |
| --- | --- |
| slots | 长度 27 的物品栈数组。空槽为 empty。 |
| panelCursor | 面板光标上的物品栈（可 empty）。不持久化也可；若持久化则重进世界时仍拿在「面板光标」上，默认**持久化**以免掉线丢物。 |

校验：

- 单槽数量不超过该物品 `maxStackSize`。
- 序列化使用原版 `ItemStack` codec / NBT 友好 API。

## ClientBackpackView（客户端镜像）

| 字段 | 规则 |
| --- | --- |
| slots[27] | 最近一次同步结果。 |
| panelCursor | 同步的光标栈。 |
| dirty | 可选；收到全量后清除。 |

关系：只读于渲染与命中；所有变更请求发往服务端。

## ClickRequest（网络，客户端 → 服务端）

| 字段 | 规则 |
| --- | --- |
| slotIndex | `0..26`，或特殊值表示「非槽」（本阶段不用）。 |
| button | 左键 / 右键（M2 以左键为主；右键可留扩展）。 |
| shift | 是否 Shift（主手存取路径）。 |
| hasContainerScreen | 客户端报告当前是否有容器 Screen（服务端可再校验玩家 `containerMenu`）。 |

## SlotDelta（网络，服务端 → 客户端）

| 字段 | 规则 |
| --- | --- |
| slotIndex | 变更槽；或 `-1` 表示只更新光标。 |
| stack | 新物品栈。 |
| cursor | 若同时更新光标则带上。 |

全量同步可用重复 SlotDelta 或单独 `FullSync` 消息（含 27 槽 + 光标）。

## 状态转换（服务端）

```text
click(slot, shift=false):
  apply BackpackClickLogic(slots, panelCursor, slot) → save → send deltas

click(slot, shift=true, noContainerScreen):
  swap/move between slots[slot] and player main hand → save → send deltas
  (+ update player inventory)

click(..., hasContainerScreen && shift):
  ignore shift main-hand path; treat as non-shift panel click OR ignore
```
