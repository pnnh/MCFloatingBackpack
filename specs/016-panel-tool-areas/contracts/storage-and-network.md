# Contract: 存储与网络

## 1. 数据组件

`BackpackContents` 的编码（`CODEC`，存盘）：

```text
{ "items": [...], "cursor": {...}?, "tools": { "left": [...]?, "right": [...]?, "bottom": [...]? }? }
```

- `tools` 及其三个字段均为可选，缺省为空列表；016 之前保存的背包读出来 `tools` 为空，主格子不变（FR-009）。
- `fit(capacity)` 同时补齐三个工具区：长度 = `max(ToolAreaCapacity.of(area, capacity), 最后一个非空下标 + 1)`，前缀按顺序保留，不丢物品（FR-010）。
- 升级配方 `BackpackUpgradeRecipe` 调用 `fit`，所以升级后工具区内容保留、多出的格子为空。

`BackpackContents` 的网络编码（`STREAM_CODEC`，物品栈同步）增加 `ToolAreaContents`；每个列表上限 64，超限解码抛 `DecoderException`。

## 2. 协议版本

`BackpackNetwork` 的 `versioned("6")` → `versioned("7")`。变化的包：

| 包 | 变化 |
|---|---|
| `BackpackFullSyncPayload` | 增加 `ToolAreaContents` 字段（服务端 → 客户端） |
| `BackpackSessionPayload` | 增加 `ToolAreaContents` 字段（服务端 → 客户端） |
| `BackpackSlotUpdatePayload` | 增加 `area` 字节（服务端 → 客户端） |
| `BackpackClickPayload` | 增加 `area` 字节（客户端 → 服务端） |
| 物品组件同步 | `BackpackContents.STREAM_CODEC` 增加 `ToolAreaContents` |

`StreamCodec.composite` 最多 6 个字段：会话包原有 5 个字段 + 1 个 `ToolAreaContents` = 6，全量同步包 5 个，物品组件 3 个，都不超限；点击包原来就用手写 `StreamCodec.of`，直接多写一个字节。

不变的包：`BackpackQuickTransferPayload`、`BackpackTakeFromContainerPayload`、`BackpackCursorGesturePayload`、`MergePanelCursorPayload`、`BackpackOpenRequestPayload`。

## 3. 点击请求 `BackpackClickPayload`

字段：`area`（`PanelArea`，一个字节）、`slotIndex`、`swapHand`、`rightClick`、`screenOpen`、`panelOpen`、`sourceOrdinal`、`invIndex`。

服务端（`handleServer`）：

1. 校验 `panelOpen`、`PanelArea.fromByte(area) != null`、`slotIndex` 在该区域当前列表长度内（主区域沿用 `< 背包容量`）；非法整包忽略。
2. 重新解析背包（`BackpackRef.resolve` + `stillValid`），无效则 `sendSession(false)`。
3. 按 `area` 取访问器：`MAIN` 用主格子，工具区用对应 `ItemStackHandler`。
4. 若光标（`screenOpen` 时是菜单携带物，否则是面板光标）非空，或 `swapHand` 时主手物品非空：先问 `ToolAreaRules.canPlace(area, stack)`；本阶段恒为真，将来为假则整次点击忽略（FR-011）。
5. 走原有三条路径之一（菜单携带物 / `swapHand` 主手交换 / 面板光标），只是 `getSlot/setSlot` 来自上面的访问器；主手是浮动背包本身时仍不交换。
6. `ref.saveInventory`；`sendSlotUpdate(player, ref, area, slotIndex)`。

## 4. 槽位更新 `BackpackSlotUpdatePayload`

`(area, slotIndex, stack, cursor)`；客户端 `ClientBackpackView.applySlotUpdate` 按 `area` 更新主格子或对应工具区的镜像，并更新光标。

## 5. 全量同步与会话

- 服务端 `from(ref, inventory)` 把三个工具区读出放进 `ToolAreaContents`；客户端 `applyFull` 保存三个镜像，长度即容量（可能大于 `ToolAreaCapacity.of` 的值，见缩容不丢物品）。
- 工具区某区长度变化时，该区的滚动偏移复位为 0 再夹取；主格子长度变化时仍复位主区域的偏移（不变）。

## 6. 不变的服务端逻辑（必须保持）

011/012 的转入/转出、012 反向的 `BackpackTakeFromContainerPayload`、013 的分配/收集/收集全部、014 的批量转出都只读写主格子（`backpack.size()`/`getStackInSlot(int)`），本 feature 不修改这些文件；`git diff` 对这些文件（`QuickTransfer*`、`PanelDragInput`、`PanelDragGesture`、`PanelSelectionInput`、`SelectionGesture`、`BackpackQuickTransferPayload`、`BackpackTakeFromContainerPayload`、`BackpackCursorGesturePayload`、`PanelCursor*`）应为空。
