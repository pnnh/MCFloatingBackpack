# Contract: 面板选区手势

## 1. 修饰键矩阵（面板格子上，左键/右键按下）

"Ctrl" 指 `Screen.hasControlDown()`（macOS 为 Command）；"Alt" 指 `Screen.hasAltDown()`（macOS 为 Option）。

| 修饰键 | 光标 | 起点格子 | 谁处理 | 结果 |
|---|---|---|---|---|
| 无 | 任意 | 任意 | 先清空选区，再交给 013/普通点击 | 点击/拖动均分/拖动收集/双击收集照旧（左右键都清空） |
| Shift（无 Ctrl） | 任意 | 任意 | **014 `SelectionGesture`**（仅左键） | 点击切换起点；拖动加入起点与滑过的格子；不动任何物品 |
| Shift，右键 | 任意 | 任意 | 吞掉，不做任何事 | 右键 + Shift 不属于任何手势；不清空选区 |
| Ctrl（无 Shift） | 空 | **已选** | **014 `BATCH_PENDING`** | 松开时批量转出 |
| Ctrl（无 Shift） | 空 | 未选 | 011/012 `QuickTransferInput`（未改） | 现有的 Ctrl 单格/拖动转出；不改选区 |
| Ctrl（无 Shift） | 非空 | 任意 | 013/普通点击 | 与改动前一致（Ctrl 手势要求光标为空） |
| Ctrl+Shift | 任意 | 任意 | 选区、011、013 都让路；按下被**吞掉** | 什么都不做（不拿起、不放下、不转移）；不清空选区 |
| Alt（无 Shift），无界面 | 任意 | 任意 | 先清空选区；013 让路；`sendClick` 带 `swapHand` | 主手与该格交换（含"主手是背包本身不交换"） |
| Alt（无 Shift），打开着界面 | 任意 | 任意 | 先清空选区；走 013/普通点击 | 与无修饰键一致（服务端忽略交换标志） |
| Shift+Alt | 任意 | 任意 | 014 选区（Shift 优先） | Alt 被忽略 |

**原则：Shift 在面板里只属于选区。** 按着 Shift 的按下如果不是上面的选择手势（右键、Ctrl+Shift），一律被消费并忽略，不会落到普通点击，避免误拿起物品。

点击面板的标题栏、滚动条、缩放手柄、关闭按钮、面板外：**不清空选区**。

## 2. 选择手势（SELECTING）

- 触发：Shift（无 Ctrl）+ 左键按在面板内一个真实格子（`0 <= 下标 < slotCount`）上。按下被消费（松开由 `ConsumedPressTracker` 吞掉）。
- 每帧驱动：`ScreenEvent.Render.Post`（界面打开时）或 `RenderGuiEvent.Post`（无界面时），与 011/013 相同；线段采样步长 4 像素。
- 点击：停在起点格子内松开 → `toggle(起点)`。
- 拖动：滑到不同于起点的格子 → 起点和所有滑过的格子 `addAll`；松开时不再切换。
- 取消：面板隐藏、界面切换、手势期间按下 Ctrl。已加入的格子保留。
- 不发送任何网络包，不改变任何物品或光标。

## 3. 批量转出（BATCH_PENDING）

- 触发：Ctrl（无 Shift）+ 左键按在**已选**格子上，且光标为空。按下被消费。
- 松开时，同时满足才发送：Ctrl 仍按着、没有 Shift、面板可见、界面没变、光标为空。
- 发送：`BackpackQuickTransferPayload.of(Direction.TO_INVENTORY, slots)`；`slots` = 选区中**当前有物品**的格子，**升序**。为空则不发。
- 松开前的鼠标移动不产生任何请求。
- 之后选区**不变**，无论服务端放下多少。

## 4. 主手交换请求（Alt）

`BackpackClickPayload(slotIndex, swapHand, rightClick, screenOpen, panelOpen, sourceOrdinal, invIndex)`：

- `swapHand`（原 `shiftDown`，同一个位）：客户端在点击时读 `Screen.hasAltDown()`。
- 服务端：`screenOpen && menu != null` 时走菜单逻辑（忽略 `swapHand`）；否则 `swapHand` 为真时与主手交换（主手是浮动背包时不交换）；否则普通点击。**逻辑不变，只是字段改名。**
- 线上格式不变，协议版本仍为 `"6"`。

## 5. 渲染

- 已选且可见的格子：格子内侧 1 像素金色（`0xFFFFD34D`）边框，画在物品与数量之后、悬停层之前。
- 悬停层、013 拖动预览（半透明白填充、黄色数量）保持原样，与选区边框可区分。
- 滚出视野的格子不绘制（`PanelSlots.isRowVisible`），但仍在选区里。

## 6. 选区生命周期

| 事件 | 选区 |
|---|---|
| 面板不可见（关闭、界面不允许等） | 清空（每 tick 同步） |
| `ClientBackpackView.getActiveRef()` 的 `(source, invIndex)` 变化 | 清空 |
| `slotCount()` 变化 | 清空 |
| 登出/重新进入世界 | 清空 |
| 滚动、缩放面板 | 不变 |
| 物品被放入/取出被选格子 | 不变 |
| HUD ↔ 打开着界面 | 不变 |
| 不按 Shift/Ctrl 的格子点击 | 清空 |

## 7. 读取接口（给后续玩法）

`PanelSelection.snapshot()` 返回升序下标数组的拷贝，`contains(i)` 与 `isEmpty()` 可直接查询；选择规则与批量转出无关，后续玩法只读取，不需要改动选择规则。
