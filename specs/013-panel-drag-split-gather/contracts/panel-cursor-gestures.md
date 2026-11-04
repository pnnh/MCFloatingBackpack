# Contract: 面板内的拖动分配与收集（网络包 + 客户端手势）

## 1. 网络包 `BackpackCursorGesturePayload`（客户端 → 服务端）

注册在 `BackpackNetwork`，协议版本 `"5"` → `"6"`（新增包必须升版本）。

| 字段 | 类型 | 说明 |
|---|---|---|
| mode | byte | 0 = `DISTRIBUTE_LEFT`，1 = `DISTRIBUTE_RIGHT`，2 = `GATHER_DRAG`，3 = `GATHER_ALL`；其他值整包拒绝 |
| slots | VarInt 列表 | 面板格子下标，规则见 data-model；读取时数量超过 `QuickTransferRequest.MAX_BACKPACK_SLOTS`（108）直接断开解码 |
| screenOpen | boolean | 同 `BackpackClickPayload`（客户端自报） |

### 服务端处理（`handleServer`，必须在服务端线程）

1. 玩家必须是 `ServerPlayer`；`BackpackRef.resolve(player)` 得到仍然有效的背包，否则 `BackpackNetwork.sendSession(player, false, null)` 并返回，不改任何物品。
2. 按 `screenOpen` 选光标来源（`PanelCursor`，data-model）；**光标必须非空**，否则忽略（四种模式都要求）。
3. 校验 `slots`：无重复、每项在 `[0, backpack.size())`、数量不超过背包大小；`GATHER_ALL` 恰好 1 项；任一不合法 → 整包忽略。
4. 按模式执行（`PanelCursorLogic`，与客户端预览同一份规则）：
   - `DISTRIBUTE_LEFT/RIGHT`：只对**有效格子**分配——按包内顺序逐个检查 `isValidDragSlot`（`selectedCount` 为已接受的格子数），无效的跳过；有效格子 ≥ 2 → 按 `placeCount` 结算；恰好 1 个 → 等同对该格做一次普通点击（左键 `BackpackClickLogic.click(…, false)`，右键 `click(…, true)`）；0 个 → 忽略。
   - `GATHER_DRAG`：按包内顺序，对每个与光标同种、光标未满的格子取 `min(格子数量, 最大堆叠 − 光标数量)`；其余忽略。
   - `GATHER_ALL`：对整个背包做两遍（先未满的同种堆叠，再满的），直到光标满；要求被双击的那个格子为空（与原版 `PICKUP_ALL` 的触发条件一致），否则忽略。
5. 写回光标（`PanelCursor`），`ref.saveInventory`，`player.getInventory().setChanged()`，菜单模式下 `menu.broadcastChanges()`，`BackpackNetwork.sendFullSync(player, ref)`（含光标）。

### 不变量（必须有单元测试，用假栈）

- 物品总数守恒：光标 + 所有背包格子里该种物品的总数，转移前后相等。
- 每格不超过 `min(最大堆叠, 格子上限)`；光标不超过最大堆叠。
- `10 个 ÷ 3 格 → 每格 3，光标剩 1`；`64 ÷ 4 → 16`；右键拖动每格 1；已有 60 的格子只再放 4；**已满的同种堆叠算有效格子但放不进东西**（与原版一致）。
- 光标数量不足以让新格子至少得 1 个时，该格不加入；结算时 `光标数量 < 格子数` 整个拒绝（与原版一致）。
- 单个有效格子 → 与 `BackpackClickLogic.click` 的结果相同。
- 收集：不同种、空格、已处理过的格子被忽略；光标满后不再收；格子里剩下的留在原格。
- 校验失败（模式未知、越界、重复、超限、光标为空）整包不改任何物品。

## 2. 客户端手势契约

### 2.1 触发条件（全部满足才开始新手势，否则走现有路径）

| 条件 | 说明 |
|---|---|
| 浮动面板可见 | 隐藏时不触发 |
| 按下的是面板里的一个格子 | 命中沿用 `PanelSlots.indexAt` 并检查 `< ClientBackpackView.slotCount()` |
| `Screen.hasControlDown()` 为假 | 按着 Ctrl（macOS 为 Command）走 011/012 |
| `Screen.hasShiftDown()` 为假 | 按着 Shift 走现有路径；**这里读取 Shift 只为让路** |
| 左键或右键 | 其他键忽略 |

### 2.2 结算时机（与现有行为的差异）

- **光标为空**：按下立即发"拿起"点击（现有行为，不变）；左键此后进入 `GATHERING`。
- **光标非空**：按下**不发包**，进入 `DISTRIBUTING`；松开时结算（data-model 的状态转移）。这是对现有"按下即放下"的行为变化，与原版一致。
- 双击：250ms 内同一格、同一按键的第二次点击，松开时发 `GATHER_ALL`，代替普通点击与分配。

### 2.3 每帧驱动与事件消费

- 沿用 011：`ScreenEvent.Render.Post`（开着界面）与 `RenderGuiEvent.Post`（HUD，界面打开时提前返回）每帧调用 `PanelDragInput.tick`，每帧每种请求至多一个包；按下事件被面板消费并登记 `ConsumedPressTracker`，所以松开事件也被吞；**结束与取消靠每帧轮询按键状态**，不依赖松开事件。
- 取消条件：面板隐藏、界面切换、光标变空（分配时）、按下了另一个鼠标键、Ctrl 或 Shift 变为按下。取消分配 = 丢弃，不发包；取消收集 = 保留已收的。

### 2.4 预览

- `DISTRIBUTING` 时选中格子数 ≥ 2：`FloatingPanelRenderer` 对每个选中格子渲染"放入后的数量"（超过上限时用黄色显示上限），光标上显示"将剩余的数量"；选中格子数为 1 时不画预览（与原版一致）。预览数据来自 `PanelDragGesture`，不改变任何真实物品。

## 3. 不在契约内（本阶段不做）

- 菜单自己的格子（玩家物品栏、箱子、精妙背包、旅行者背包）；跨面板与菜单格子的混合拖动；创造模式中键；触摸屏；Shift+双击。
