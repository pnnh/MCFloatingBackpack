# Tasks: 浮动背包与玩家物品栏之间的 Ctrl 快速转移

**Input**: Design documents from `/specs/011-ctrl-quick-transfer/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/quick-transfer.md, quickstart.md

**Tests**: 搬运算法、请求校验、界面白名单、手势状态机都是不依赖 Minecraft 的纯逻辑，plan 明确要求用单元测试覆盖（宪法 V 条允许纯逻辑用自动化测试，但玩家可见行为必须在客户端会话里验证）。因此测试任务在对应实现之前列出。

**Organization**: 按用户故事分组。基础设施（算法、请求校验、网络包骨架）阻塞所有故事，放在 Phase 2。US1、US2 是两个方向的 Ctrl+点击（都是 P1，共用基础设施，互相独立）；US3 的拖动建立在单槽位转移之上；US4 是"不影响原有手势"的回归验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1~US4）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（当前 11 个测试类），作为之后回归的对照

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 搬运算法、请求校验、网络包骨架与注册。没有它们，两个方向都无法实现。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/QuickTransferLogicTest.java`（**先写，先失败**）：用测试内的 `FakeStack` 实现 `QuickTransferLogic.StackOps<FakeStack>`，覆盖：整堆并入已有同种堆叠；并入后超过堆叠上限时多出部分放进空格；没有空格时剩余留在来源；不可堆叠物品（上限 1）只进空格；不同物品不合并；目标顺序按下标升序；被跳过的目标下标不会被写入；**性质检查**：随机构造 100 组来源/目标布局，转移前后同种物品总数相等、任何槽位不超过上限（data-model 的不变量）
- [X] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/QuickTransferRequestTest.java`（**先写，先失败**）：覆盖 `QuickTransferRequest.validate(direction, slots, backpackSize)`：`TO_BACKPACK` 时下标必须在 [0,35] 且至多 36 项；`TO_INVENTORY` 时在 [0, backpackSize-1] 且至多 `backpackSize` 项（上限 108）；含负数、越界、重复、空列表任一项 → 不合法；以及 `protectedInventoryIndex(source, invIndex, selectedSlot)`：主手 → 选中的快捷栏下标；来源是物品栏 → 该 `invIndex`；副手/胸甲 → -1
- [X] T004 [P] 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/QuickTransferLogic.java`：泛型纯逻辑，**不引用任何 Minecraft 类型**（让测试能直接加载，同 `BackpackTier` 的做法）。定义内部接口 `StackOps<T>`（`isEmpty`、`count`、`maxStack`、`sameKind`、`withCount`），以及 `static <T> T moveInto(StackOps<T> ops, T source, int[] targetOrder, IntFunction<T> getTarget, ObjIntConsumer<T> setTarget)`：第一遍并入同种且未满的目标，第二遍放入空目标，返回剩余（research Decision 6）。使 T002 通过
- [X] T005 [P] 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/QuickTransferRequest.java`：纯逻辑（无 Minecraft 类型）。`enum Direction { TO_BACKPACK, TO_INVENTORY }`，`validate(...)` 与 `protectedInventoryIndex(...)` 按 data-model.md 与 contracts/quick-transfer.md 实现。使 T003 通过。**偏离 plan 的说明**：plan 把请求校验测试放在 `network/`，这里放 `inventory/`，因为被测类必须不依赖 Minecraft 类型，而 `network/` 下的类都带 `StreamCodec`
- [X] T006 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/ItemStackTransferOps.java`：`QuickTransferLogic.StackOps<ItemStack>` 的生产实现（`ItemStack.isSameItemSameComponents`、`getMaxStackSize()`、`copyWithCount`）。依赖 T004
- [X] T007 新建 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackQuickTransferPayload.java`：客户端→服务端包，字段 direction（byte）、slots（VarInt 列表）（contracts/quick-transfer.md §1；实现时去掉了 sourceOrdinal/invIndex，服务端以实时解析为准）；`STREAM_CODEC`；`handleServer` 的**公共部分**按 contract 步骤 1–4 实现：`ServerPlayer` 检查、`BackpackRef.resolve` 实时解析（沿用 `BackpackClickPayload` 的"实时解析优先，声明来源一致才采用"）、光标非空则忽略、`QuickTransferRequest.validate` 失败整包忽略、`ref.loadInventory`（**注意**：服务端没有"面板是否打开"的状态，本包不带 `panelOpen` 字段，也不检查它；能校验的是背包仍然可用，见 contract §1）；然后按方向分派到两个**留空的**方法（US1、US2 分别实现），最后 `ref.saveInventory` + `player.getInventory().setChanged()` + `containerMenu.broadcastChanges()` + `BackpackNetwork.sendFullSync`（步骤 6–7）。依赖 T005
- [X] T008 修改 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackNetwork.java`：把 `versioned("3")` 改成 `versioned("4")`，注册 `BackpackQuickTransferPayload`（`playToServer`）。依赖 T007

**Checkpoint**: `./gradlew compileJava test` 通过；T002、T003 绿；游戏能启动并进入世界（协议版本 4）。

---

## Phase 3: User Story 1 - Ctrl+点击把物品栏里的物品存进浮动背包 (Priority: P1) 🎯 MVP

**Goal**: 面板可见时，Ctrl+点击玩家物品栏/快捷栏的槽位，整堆进入背包。

**Independent Test**: quickstart 一.1、2、5、7 与三.3～三.6：打开面板与物品栏，Command+点击物品栏槽位，物品进入背包，数量总和不变；箱子自己的格子、创造模式界面、盔甲/副手/合成槽、第三方界面都不触发。

- [X] T009 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferRulesTest.java`（**先写，先失败**）：覆盖 `QuickTransferRules.isSupportedScreenClass(String className)`：`net.minecraft.client.gui.screens.inventory.InventoryScreen`、`ContainerScreen`、`CraftingScreen` 等 → true；`CreativeModeInventoryScreen` → false；`net.p3pp3rf1y...StorageScreenBase`、`com.tiviacz...` 等任何非 `net.minecraft.` 开头的类名 → false；以及 `QuickTransferRules.isTransferableInventorySlot(boolean sameInventory, int containerSlot, boolean active)`：`sameInventory && 0<=containerSlot<36 && active` 才为 true（盔甲 36–39、副手 40、箱子格子 → false）
- [X] T010 [US1] 服务端：在 `BackpackQuickTransferPayload` 里实现 `TO_BACKPACK` 分支：对每个来源下标（包内顺序），跳过 `QuickTransferRequest.protectedInventoryIndex(...)`（FR-010）；用 `QuickTransferLogic.moveInto` + `ItemStackTransferOps` 把 `player.getInventory().getItem(i)` 转入背包 `BackpackInventory`（目标下标 0..size-1 升序）；剩余写回该物品栏槽位。依赖 T006、T007
- [X] T011 [US1] 先新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferRules.java`（**不导入任何 Minecraft 类型**，只含 `isSupportedScreenClass`、`isTransferableInventorySlot` 两个静态纯函数，使 T009 通过；单独成类是为了测试能直接加载）；再新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferInput.java`（与游戏相连的部分，plan 的结构），其中调用 `QuickTransferRules`；`findInventorySlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY)`：遍历 `screen.getMenu().slots`，用 `getGuiLeft()+slot.x`、`getGuiTop()+slot.y` 做 16×16 命中（research Decision 2），只返回 `container == 玩家 Inventory` 的槽位；`sendInventoryToBackpack(int inventoryIndex)`：用 `ClientBackpackView` 的当前来源构造并发送单槽位的 `BackpackQuickTransferPayload`（direction=TO_BACKPACK）。依赖 T008、T009
- [X] T012 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` 的 `onScreenMousePressed`：在现有"面板消费"判定**之后**加分支——条件全满足（`Screen.hasControlDown()`、左键、面板可见、`menu.getCarried().isEmpty()`、界面类型通过 `isSupportedScreenClass`、鼠标不在面板范围内、`findInventorySlot` 命中）时：发送请求、`event.setCanceled(true)`、并 `FloatingPanelInput` 里登记 `ConsumedPressTracker.press(button)`（需在 `FloatingPanelInput` 暴露一个 `markPressConsumed(int button)`）。**不得读取或判断 Shift**。依赖 T011
- [X] T013 [US1] 运行 `./gradlew compileJava test`，确认 T002、T003、T009 全绿、无回归（11 个旧测试类仍通过）
- [X] T014 [US1]（2026-11-04 用户验证 quickstart 一.1、2、5、7 通过；**三.3～三.6（箱子格子/创造模式/盔甲副手合成槽不触发）用户没有记录，按未验证记**，见 DELIVERY.md） 按 `quickstart.md` 一.1、2、5、7 与三.3～三.6 在 `./gradlew runClient`（macOS 用 Command 键）逐项验证，把结果记到 `specs/011-ctrl-quick-transfer/DELIVERY.md`（没做的项记"未验证"）

**Checkpoint**: US1 达成 FR-001、FR-006（物品栏→背包方向）、FR-007、FR-010、FR-011、FR-016、FR-017。

---

## Phase 4: User Story 2 - Ctrl+点击把浮动背包里的物品取回物品栏 (Priority: P1)

**Goal**: 面板可见时，Ctrl+点击面板里的槽位，整堆进入玩家物品栏/快捷栏；没开任何界面也可用。

**Independent Test**: quickstart 一.3、4、6：Command+点击面板槽位，物品进入玩家 36 格，总数不变；HUD 下也可用。

- [X] T015 [US2] 服务端：在 `BackpackQuickTransferPayload` 里实现 `TO_INVENTORY` 分支：对每个背包下标，用 `QuickTransferLogic.moveInto` 把 `BackpackInventory` 槽位的栈转入 `player.getInventory()`（目标下标 0..35 升序，直接 `getItem`/`setItem`，**不使用 `Inventory.add`**——创造模式下它会丢弃剩余，research Decision 6）；剩余写回背包槽位。依赖 T006、T007
- [X] T016 [US2] 客户端：在 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java` 的 `dispatchMouseButton` 面板槽位分支里，`Screen.hasControlDown()` 且左键且光标为空时，**不检查界面类型**（FR-002：没有界面、原版界面、第三方界面打开时，只要面板可见都可用）；改为调用 `QuickTransferInput.sendBackpackToInventory(int backpackIndex)`（TO_INVENTORY，单槽位；**在 `QuickTransferInput.java` 里新增这个方法**）而不是 `sendClick`；`slot >= ClientBackpackView.get().slotCount()` 的边界检查保持不变。光标不为空时保持现有 `sendClick` 路径（FR-007）。**不得读取或判断 Shift**。依赖 T015
- [X] T017 [US2] 在 HUD（没有界面）路径下，`FloatingPanelEvents.onWorldMouseButton` 已经把鼠标事件交给 `FloatingPanelInput.onMouseButton`，确认 T016 的分支在 `minecraft.screen == null` 时同样生效。依赖 T016
- [X] T018 [US2] 运行 `./gradlew compileJava test`，确认全绿
- [X] T019 [US2]（2026-11-04 用户验证 quickstart 一.3、4、6 通过） 按 `quickstart.md` 一.3、4、6 在 `./gradlew runClient` 逐项验证，结果记到 `DELIVERY.md`

**Checkpoint**: US2 达成 FR-002（含无界面）、FR-006（背包→物品栏方向）、FR-018。

---

## Phase 5: User Story 3 - Ctrl+拖动批量转移 (Priority: P2)

**Goal**: 按住 Ctrl 和左键滑过多个槽位，沿途槽位依次转移；方向由起点决定；松开即停。

**Independent Test**: quickstart 二：沿快捷栏一行滑过 9 个堆叠，9 个都进入背包；松开 Ctrl 或鼠标立即停止；从物品栏起点滑过面板不会反向取出。

- [X] T020 [P] [US3] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferGestureTest.java`（**先写，先失败**）：覆盖 `QuickTransferGesture`：按下时方向由起点所在一侧决定且之后不变；同一来源下标一次拖动只出现一次；`advance(x, y)` 在上一位置到当前位置的线段上每 ≤4 像素采样，快速掠过多个槽位时不漏格（用注入的槽位解析函数模拟 18 像素间距的一行槽位）；滑过另一侧的槽位被忽略；`stop()` 后不再产生槽位；空起点（起点槽位没有物品）也进入拖动
- [X] T021 [US3] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferGesture.java`：按 data-model.md 的 `GestureState` 实现状态机（IDLE/ACTIVE、direction、visited、lastX/lastY、线段采样），槽位解析通过注入的函数完成（`(x,y) → 槽位引用或 null`，槽位引用含"所在侧"与"来源下标"），**不依赖任何 Minecraft 类型**；`advance` 返回本帧新命中的来源下标列表。使 T020 通过
- [X] T022 [US3] 把 US1/US2 的单击也改为经由 `QuickTransferGesture`（**这会改动 US1/US2 已验收的路径，所以 T025 必须重跑 quickstart 一**）（按下 = 起点，立即处理起点槽位；放开前继续拖动），避免两套路径：修改 `FloatingPanelEvents.java` 与 `FloatingPanelInput.java` 的按下分支，让它们创建/启动手势而不是直接发单槽位包。依赖 T021
- [X] T023 [US3] 在 `FloatingPanelEvents.java` 的 `onScreenRender`（`ScreenEvent.Render.Post`）和 `onRenderGui`（`RenderGuiEvent.Post`）里每帧调用 `QuickTransferInput.tick(...)`；**与现有 `onRenderGui` 的做法一致：界面打开时 `onRenderGui` 提前返回，只由 `onScreenRender` 驱动，否则一帧会触发两次、违反"每帧至多一包"**：读鼠标 GUI 坐标，调用 `gesture.advance`，**每帧至多发一个** `BackpackQuickTransferPayload`（本帧新命中的下标列表）；用 `glfwGetMouseButton` 与 `Screen.hasControlDown()` 判断是否仍按着，任一松开、面板隐藏、界面打开/关闭、光标上拿起物品 → `gesture.stop()`（research Decision 3：松开判定靠轮询，不依赖松开事件）。依赖 T022
- [X] T024 [US3] 运行 `./gradlew compileJava test`，确认 T020 绿、全部测试通过
- [X] T025 [US3]（2026-11-04 用户验证 quickstart 二通过，含重跑一） 按 `quickstart.md` 二（1～6）在 `./gradlew runClient` 逐项验证，包括 36 格来回快速滑动无断线（SC-007）；**并因 T022 改动了单击路径，重跑 quickstart 一（Ctrl+点击两个方向）**；结果记到 `DELIVERY.md`

**Checkpoint**: US3 达成 FR-003、FR-004、FR-005、FR-013、SC-002、SC-007。

---

## Phase 6: User Story 4 - 原版和第三方的 Shift 手势保持原样 (Priority: P2)

**Goal**: 不抢占任何 Shift 手势；面板隐藏时 Ctrl 手势不生效；第三方界面不被改动。

**Independent Test**: quickstart 三.1、2、4、7：面板可见/隐藏两种状态下，Shift+点击的原版行为不变；`runClientCompat` 下精妙背包、旅行者背包界面里的玩家槽位不被我们拦截。

- [X] T026 [US4] 静态检查：在 `src/main/java/pnnh/mcfloatingbackpack/` 下对本 feature 新增和修改的代码 `grep -n "hasShiftDown\|KEY_LEFT_SHIFT\|KEY_RIGHT_SHIFT"`，确认**没有新增**对 Shift 的读取或判断（`FloatingPanelInput.sendClick` 里**原有**的 Shift 检测不动）；把检查命令与结果记到 `DELIVERY.md`
- [X] T027 [US4] 面板隐藏时 Ctrl 手势不生效：确认 `QuickTransferInput` 的所有入口都先检查 `FloatingPanelState.get().isVisible()`，并在 `FloatingPanelEvents.onWorldMouseButton` 中"面板不可见 → `resetPressTracking()` 并返回"的原路径没有被新增分支绕过。必要时补一个小的纯逻辑测试
- [X] T028 [US4]（2026-11-04 用户验证 quickstart 三.1、2、4、7、7a、8 通过） 按 `quickstart.md` 三.1、2、4、7、8 验证：原版 Shift+点击（物品栏↔快捷栏、箱子↔物品栏）不变；面板隐藏时 Ctrl 手势与原版一致；`./gradlew runClientCompat` 下精妙背包、旅行者背包界面里的任何槽位 Command+点击**不被拦截**、它们自己的 Shift 手势正常，同时面板自己的槽位 Command+点击仍可转出到玩家物品栏（quickstart 三.7、三.7a）；面板与物品栏界面重叠时命中规则正确。结果记到 `DELIVERY.md`

**Checkpoint**: US4 达成 FR-008、FR-009、FR-015、FR-016、SC-004、SC-005。

---

## Phase 7: Polish & 文档收尾

- [X] T029 [P] 更新 `specs/007-floating-window-chrome/spec.md` 的 Out of Scope："Shift+点击双向快速转移"一条后面加【011 修订】：已由 011 以 **Ctrl 手势**实现（范围：玩家 36 格 ↔ 浮动背包），Shift 手势保持原样
- [X] T030 [P] 更新 `docs/MCFloatingBackpack.md` 的 M5 段落：补一句 Ctrl+点击/拖动快速转移（macOS 为 Command 键），并指向 `specs/011-ctrl-quick-transfer/`
- [X] T031 [P] 更新 `specs/006-tiered-backpacks/DELIVERY.md` 里"Network | Payload version `3`"的说明：加【011 修订】协议版本已升到 4
- [X] T032（**部分**：用户做了多次转移，数量没有问题；未做 50 次的完整计数、重进存档、Windows/Linux，按未验证记，见 DELIVERY.md）按 `quickstart.md` 四与五补做：50 次以上转移（含满载的部分转移）前后相关物品总数相等；转移后重进存档内容一致；macOS Command 键两种手势均可用；Windows/Linux **无法验证**，在 `DELIVERY.md` 里如实写"未验证"
- [X] T033 创建 `specs/011-ctrl-quick-transfer/DELIVERY.md`：记录 T014、T019、T025、T028、T032 的结果，**包括没做到的和结论不确定的**；列出已知限制（第三方界面、外部容器格子、Shift+双击、Windows/Linux 未验证）
- [X] T034 运行最终回归 `./gradlew compileJava test`，并确认游戏能启动（`./gradlew runClient` 进入世界无报错）；把 spec 的 Status 改为实现完成，并如实写明验证未完成的部分

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（T001）：无依赖
- Phase 2（T002～T008）：T002、T003、T004、T005 互相独立可并行（T002 与 T004、T003 与 T005 是"测试先于实现"的配对）；T006 依赖 T004；T007 依赖 T005；T008 依赖 T007。**阻塞所有用户故事**
- Phase 3 US1（T009～T014）：依赖 Phase 2；T009 可与 Phase 2 并行先写
- Phase 4 US2（T015～T019）：依赖 Phase 2；与 US1 **互相独立**，改动的是 `BackpackQuickTransferPayload` 的不同分支和 `FloatingPanelInput`，可并行但要注意两者都会改同一个 payload 文件，实际建议先 US1 后 US2
- Phase 5 US3（T020～T025）：依赖 US1、US2（要把单击改为经由手势）
- Phase 6 US4（T026～T028）：依赖 US1～US3 的代码都写完才能做完整验证；T026 可在每个故事后增量做
- Phase 7：依赖前面的结果

### User Story Dependencies

- **US1**：依赖基础设施，是 MVP 的第一个方向
- **US2**：依赖基础设施，与 US1 独立
- **US3**：依赖 US1 与 US2 的单槽位路径
- **US4**：纯回归验证，依赖前三者代码完成

### Parallel Opportunities

- T002、T003、T004、T005 全部可并行（四个不同文件）
- T009（US1 的测试）可在 Phase 2 期间提前写
- T020（US3 的测试）可在 US1/US2 实现期间提前写
- T029、T030、T031 三个文档任务可并行

---

## Parallel Example: Phase 2

```text
并行启动：
  T002 QuickTransferLogicTest.java        （测试，先写）
  T003 QuickTransferRequestTest.java      （测试，先写）
  T004 QuickTransferLogic.java            （实现，使 T002 通过）
  T005 QuickTransferRequest.java          （实现，使 T003 通过）
随后顺序：T006 → T007 → T008
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1、2（基础设施）。
2. 完成 US1（物品栏 → 背包的 Ctrl+点击）：这是最小的可演示切片，**停下来在游戏里验证**（T014）。
3. 接着 US2（取回方向）：两个方向通了，Ctrl+点击这个手势就完整了。**建议把 US1+US2 作为第一个交付点**。
4. 再做 US3（拖动），它建立在前两者之上。
5. 最后 US4 的回归验证与文档收尾。

### 与宪法的关系

- 每个会改变玩家所见/所操作的故事，都有"在客户端会话里验证"的任务（T014、T019、T025、T028、T032）；验证没做的项在 DELIVERY.md 里记"未验证"，不能因为编译通过就勾选（宪法 V）。
- 不新增资源、不改共用图集（宪法 VI 不适用）。

### 风险提醒（实现时盯住）

- **第三方界面的玩家槽位也绑定真实 `Inventory`**：只靠"槽位的 container 是不是玩家 Inventory"排除不了它们，必须用界面类名白名单（T009、T011），并在 T028 用 `runClientCompat` 实测；白名单只约束**物品栏一侧**，面板一侧不限界面类型。
- **松开事件被吞会让按键状态卡住**（BUG-005-1）：本 feature 取消了按下事件，必须同时登记 `ConsumedPressTracker`（T012），拖动的结束判定靠轮询（T023）。
