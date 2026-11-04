# Data Model: 浮动面板内的选区

## PanelSelection（客户端，纯逻辑）

选中格子的集合，元素是背包格子下标（`0..slotCount-1`）。

| 操作 | 语义 |
|---|---|
| `toggle(i)` | 在则移除，不在则加入 |
| `addAll(Collection<Integer>)` | 加入，不移除任何已有格子 |
| `clear()` | 清空 |
| `contains(i)` | 是否选中 |
| `isEmpty()` | 是否没有选区 |
| `snapshot()` | 升序的 `int[]`，供后续玩法与批量转出读取；返回的是拷贝 |
| `syncContext(visible, refKey, slotCount)` | 面板不可见，或 `refKey`、`slotCount` 与上次不同则清空；然后记住当前值 |

不变量：
- 元素始终是整数下标，不含重复；加入时必须 `0 <= i < slotCount`，超出实际格数的虚拟格子不会被加入。
- 选区与格子里有没有物品无关（空格子可以选中，也保持选中）。
- 选区与屏幕位置、滚动、缩放无关。

## SelectionGesture（客户端，纯逻辑状态机）

| 状态 | 含义 |
|---|---|
| `IDLE` | 没有进行中的选区手势 |
| `SELECTING` | Shift+左键按下后，尚未松开；`dragged` 标记是否已滑到别的格子 |
| `BATCH_PENDING` | Ctrl+左键按在已选格子上，等松开时批量转出 |

数据：`startSlot`、`dragged`、`visited`（本次手势已处理的格子）、`lastX/lastY`（用于线段采样）。

转移：

| 事件 | 条件 | 结果 |
|---|---|---|
| `beginSelect(slot)` | Shift、无 Ctrl、左键 | → `SELECTING`，`startSlot = slot`，`visited = {slot}`，选区**不变** |
| `advance(...)` | `SELECTING` | 线段采样，新格子（`visited` 去重）若 ≠ 起点：首次时把起点加入选区并置 `dragged`；每个新格子加入选区（空格子也加入） |
| `release(slotUnderCursor)` | `SELECTING` | 若 `dragged`：无动作；否则 `toggle(startSlot)`；→ `IDLE` |
| `beginBatch()` | Ctrl、无 Shift、左键、光标空、起点格已选 | → `BATCH_PENDING` |
| `releaseBatch(ctrlHeld, shiftHeld, visible, sameScreen, cursorEmpty)` | `BATCH_PENDING` | 全部满足 → 返回"升序、有物品的选中格子"列表（为空则不发）；否则返回空；→ `IDLE` |
| `cancel()` | 面板隐藏、界面切换、`SELECTING` 中按下 Ctrl、登出 | → `IDLE`；`SELECTING` 已加入的格子保留，未结算的点击切换作废 |

`advance` 不改变 `BATCH_PENDING`（移动不产生任何请求）。

## 批量转出请求（沿用 011/012，不新增）

`BackpackQuickTransferPayload(direction = TO_INVENTORY (1, 转出背包), slots = 升序、当前有物品的选中格子)`。服务端校验与行为见 011/012 的 contracts，本 feature 不改。

## 渲染数据

`FloatingPanelRenderer` 在每个可见格子绘制时查询 `PanelSelection.contains(i)`，为真则画金色内边框。选区本身不包含任何渲染信息。

## 与现有实体的关系

- `ClientBackpackView`：提供 `slotCount()`、`getStackInSlot(i)`（判断"有物品"）、`getActiveRef()`（`refKey` 来源）。
- `PanelSlots`：命中测试 `indexAt`、`isRowVisible`（渲染时只画可见格子，选区不受影响）。
- `FloatingPanelState`：面板可见性。
- 013 的 `PanelDragInput`：Alt（无界面时）让路；Shift 本来就让路。
