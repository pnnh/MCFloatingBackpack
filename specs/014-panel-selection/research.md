# Research: 浮动面板内的选区

所有结论来自读本仓库源码（011/012/013 与 `FloatingPanelInput` 等）和 1.21.1 的 `Screen` 源码；没有读第三方模组。**没有在游戏里验证**的地方在"仍不确定"里列出。

## Decision 1: 选区只放客户端，服务端不读

**Decision**: 选区 = 客户端的"背包格子下标集合"，不发包、不存盘。批量转出时客户端把当前选区的下标放进现有转出请求。

**Rationale**: 选区不是物品，改变的只是 UI 状态，不触碰宪法 II；011/012 的请求已经带格子列表且服务端逐项校验（范围、重复、数量上限 108），客户端传错只会让自己的转移失败。让服务端存选区会引入同步、失效、多端一致等问题，而本阶段没有任何需要服务端知道选区的用途。

**Alternatives considered**: 服务端存选区（后续玩法若需要再加，请求里带格子列表已经够用）；存在背包物品的数据组件里（会随物品持久化，违背"临时状态"，且每次选择都要落盘）。

## Decision 2: 批量转出复用 `BackpackQuickTransferPayload`，不升协议

**Decision**: 发送 `BackpackQuickTransferPayload.of(Direction.TO_INVENTORY, slots)`（常量名保留，012 起含义是"转出背包"，目的地由服务端按打开的菜单决定）。`slots` 由客户端取选区、去掉当前为空的格子、升序排列。

**Rationale**: 服务端 `handleServer` 要求光标为空、校验 `QuickTransferRequest.validate`（范围、重复、`≤ 背包容量`），然后按列表顺序逐格转出（`backpackOut`），放不下的留在原格并 `sendFullSync`——正好满足"从左到右、从上到下、放不下保留"。面板格子按行排布，下标顺序就是该顺序（`PanelSlots`）。客户端排序而不是服务端排序，因为顺序只影响调用者自己的物品；服务端依然校验合法性。

**Alternatives considered**: 新增批量转出包（没有任何新信息要传，只会升协议）；在服务端对列表排序（可以，但没有必要，且会改 011/012 的服务端文件）。

## Decision 3: 选择手势的"点击 vs 拖动"在松开时判定

**Decision**: Shift（且无 Ctrl）在面板格子上按下左键 → 状态机进入 `SELECTING`，记录起点，**此刻不改选区**。之后：
- 每帧按线段采样（`SAMPLE_STEP = 4`，与 011/013 相同）找新滑到的格子；一旦滑到**不同于起点的格子**，把起点和该格子都加入选区（此后路过的格子都加入），手势变成"拖动"；
- 松开时如果始终没离开起点格子 → 点击 → **切换起点**；如果已经成为拖动 → 什么都不再做。

**Rationale**: 这是满足"点击切换、拖动只加"又不出现"拖动起点被切换掉"的最简规则：切换只发生在"确定是点击"的松开时刻。只加不减由"拖动阶段只 `addAll`"保证。命中判定沿用 `PanelSlots.indexAt` 并检查 `< ClientBackpackView.slotCount()`，与 013 一致。

**Alternatives considered**: 按下就切换（拖动起点会被切掉，要再补一次）；起点决定"加还是减"的涂抹模式（用户选了"点击切换、拖动只加"）。

## Decision 4: Ctrl 点击已选格子 = 松开时批量转出，在 Shift 之后、`QuickTransferInput` 之前分派

**Decision**: `FloatingPanelInput.dispatchMouseButton` 的面板格子分支顺序改为：
1. `PanelSelectionInput.beginFromPanel(slot, isRight, x, y)`：左键 + Shift（无 Ctrl）→ 选择手势；左键 + Ctrl（无 Shift）+ 光标为空 + 该格已选 → `BATCH_PENDING`；两种情况都返回 true（按下被消费）。
2. `QuickTransferInput.beginFromPanel`（011，未改）；
3. 013 的 `PanelDragInput`；
4. `sendClick`。

在第 2～4 步之前，`PanelSelectionInput` 还负责**清空选区**：左键或右键按下面板格子，且不按 Shift、不按 Ctrl 时先清空。`BATCH_PENDING` 在松开（`onRelease`，在 013 的 `PanelDragInput.onRelease` 之前）时提交：要求 Ctrl 仍按着（`Screen.hasControlDown()`）、没有 Shift、面板可见、界面没变、光标为空；满足则发批量转出（若选区里当前没有有物品的格子则不发），否则取消。松开前移动鼠标不产生任何请求。

**Rationale**: 011 的 `beginFromPanel` 在 Ctrl 按下时就发请求并开始拖动，所以"已选格子"这个分支必须在它之前拦截；未选格子的 Ctrl 手势原样落到 011，满足 FR-010 且不改 011 的文件。按下被消费后，松开由 `ConsumedPressTracker`（BUG-005-1 的教训）保证只吞对应的松开。

**Alternatives considered**: 在 `QuickTransferInput.beginFromPanel` 里加分支（要改 011 的文件，违背"不改 011/012 客户端手势"的约束）。

## Decision 5: 修饰键矩阵（Ctrl+Shift、Alt）

**Decision**: 完整矩阵见 [contracts/panel-selection-gestures.md](./contracts/panel-selection-gestures.md)。要点：
- **原则：Shift 在面板里只属于选区。** 按着 Shift 的按下，只要不是选择手势（左键、无 Ctrl），就被消费并忽略：包括 Shift+右键、Ctrl+Shift+左/右键。它们不拿起/放下/转移任何物品，也不清空选区。
- Shift+Alt：Shift 优先，按选区处理。
- 选择拖动进行中按下 Ctrl：手势取消，已加入的格子保留；Shift 在按下之后松开不影响这次手势（只在按下时判定 Shift）。

**Rationale**: 保持各修饰键判定"各管各的、只在入口判定"，避免状态机里出现更多组合分支。

**Alternatives considered**: 让 Ctrl+Shift、Shift+右键落到普通点击（Shift 不再触发主手交换后，它们会变成拿起物品，容易误触；吞掉更安全，代价是这两个组合现在没有用途，将来要用再分配）。

## Decision 6: 主手交换从 Shift 改到 Alt

**Decision**:
- 客户端：`FloatingPanelInput.sendClick` 里原来读 `Screen.hasShiftDown()` / GLFW Shift 键的标志位，改为读 `Screen.hasAltDown()`（原版实现就是 GLFW 的左右 Alt）。字段在 `BackpackClickPayload` 里改名 `shiftDown` → `swapHand`，写入/读取的位不变，所以**线上格式不变，协议版本不变**。
- 服务端：`BackpackClickPayload.handleServer` 的交换分支逻辑（`screenOpen` 时走菜单、否则 `swapHand` 才交换、主手是背包时保护）**原样保留**，只是引用新字段名。
- 013：`PanelDragInput.beginFromPanel` 增加"没有打开容器界面且 Alt 按着时不接管（返回 false）"。原因：013 对光标非空的按下会延后到松开，若不让路，HUD 下 Alt+点击（光标里有物品时）会变成"松开才交换"，且按下时读不到。打开着界面时 Alt+点击是普通点击，服务端忽略该标志，013 不需要让路。
- Shift 不再读入 `sendClick`：Shift 按下的点击在面板里全部先被选区截走（Decision 3/4），不会走到 `sendClick`，所以不会误触发交换。

**Rationale**: 只换"标志位的来源"，不动服务端语义，回归面最小。用户裁决是"改成别的按键"，具体键由我取默认 Alt，登记在 spec 的 Assumptions。

**仍不确定**: macOS Option+左键在 GLFW 里是否稳定报告 Alt 键状态（`Screen.hasAltDown()` 读的是 GLFW 键状态，理论上没问题）；Windows/Linux 窗口管理器可能占用 Alt+点击。均未验证，见 quickstart。

## Decision 7: 渲染：金色内边框，画在物品之后

**Decision**: 对每个可见的已选格子，在格子内侧画 1 像素宽的金色边框（`0xFFFFD34D`，4 条 `fill`），在物品和数量之后、悬停高亮之前绘制；同一格同时被悬停时，半透明白色悬停层叠在边框上，两者都看得见。013 拖动预览使用半透明白色填充，与金色边框可区分。

**Rationale**: 现有悬停是半透明白填充（`COLOR_SLOT_HOVER`）、013 预览也是白填充，金色细边框与二者在颜色和形状上都不同；不用贴图所以不涉及宪法 VI。

**仍不确定**: 金色边框在 18 像素格子里是否足够醒目、是否与"满堆叠黄色数量"混淆，需要在游戏里看；颜色是一个常量，改起来很容易。

## Decision 8: 选区的生命周期用"每 tick 同步上下文"一处处理

**Decision**: `FloatingPanelEvents.onClientTick` 每 tick 调用 `PanelSelection.syncContext(visible, refKey, slotCount)`：面板不可见、`ClientBackpackView.getActiveRef()` 变化（来源或背包栏位）、或 `slotCount()` 变化时清空；`onLoggingIn` 里也清空。滚动、缩放不调用任何清空。`refKey` 是 `(source, invIndex)` 的组合。

**Rationale**: `closePanel`、`stateClose`、`hideIfDisallowed`、界面切换等多处都会让面板不可见；逐处加清空容易漏，tick 同步一处覆盖全部。选区按下标存，所以滚动/缩放天然不受影响（FR-012、FR-013）。同一个 tick 内的短暂不一致不影响（面板不可见时没有输入）。

**Alternatives considered**: 在每个关闭面板的调用点清空（漏一处就留下旧选区）。

**仍不确定**: "换用另一只背包"但 `(source, invIndex)` 恰好相同（比如在同一个栏位里把背包换成另一只同容量的）无法被识别；这种情况下选区会沿用下标。可以接受，登记为已知限制。

## Decision 9: 光标拿着物品时的行为

**Decision**: Shift 选择手势在光标非空时仍然可用，不触碰光标或任何物品；Ctrl 批量转出要求光标为空（与 011 一致，服务端也会拒绝）。

**Rationale**: 选区只是标记，没有理由因为光标上有东西而禁用；批量转出的"光标必须为空"是服务端既有前提。

## Decision 10: 测试策略

**Decision**: `PanelSelection`、`SelectionGesture` 是不依赖 Minecraft 的纯类，用单元测试覆盖；`PanelSelectionInput`、渲染、事件钩子依赖游戏状态，只能在客户端会话里验证（宪法 V），列入 quickstart。

**Rationale**: 011/013 同样的做法，规则可测、胶水靠手测。

## 仍不确定 / 需要游戏内确认

- Alt（macOS Option）+点击在 macOS 上能稳定触发主手交换；Windows/Linux 窗口管理器冲突无法验证。
- 金色内边框的辨识度。
- Shift+右键、Ctrl+Shift 被吞掉是否让人困惑（Decision 5）。
- Ctrl 批量转出"按下记录、松开发送"的手感，尤其是松开时 Ctrl 已放开的取消条件是否容易误触。
- 同栏位换成另一只同容量背包时选区不会被清空（Decision 8）。
