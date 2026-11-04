# Tasks: 浮动面板内的选区（Shift 选择，Ctrl 批量转移）

**Input**: Design documents from `/specs/014-panel-selection/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/panel-selection-gestures.md, quickstart.md

**Tests**: 选区集合、手势状态机都是不依赖 Minecraft 的纯逻辑，plan 要求用单元测试覆盖（与 011/013 同一做法）；玩家可见行为必须在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础（`PanelSelection` 与它的测试）；US1（Shift 选择 + 高亮）是 MVP，它同时建立手势状态机、`PanelSelectionInput` 与输入/事件/渲染接线；US2 在其上增加 Ctrl 批量转出；US3 是生命周期（清空条件）；US4 是 Shift 主手交换改到 Alt 与整体回归。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US4）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（当前 20 个测试类），作为之后回归的对照
  - **结果**：改动前 `./gradlew compileJava test` 退出码 0，20 个测试类

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 选区集合本身。没有它，任何故事都无法实现。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelSelectionTest.java`（**先写，先失败**）：覆盖 `PanelSelection`：`toggle`（不在则加入、在则移除）；`addAll` 只加不减、重复加入无副作用；越界下标（`< 0` 或 `>= slotCount`）不会被加入（`slotCount` 由 `syncContext` 提供）；`clear`；`contains`、`isEmpty`；`snapshot` 返回**升序**拷贝（改拷贝不影响选区）；`syncContext(visible, refKey, slotCount)`：`visible == false`、`refKey` 变化、`slotCount` 变化各自清空，三者不变则保留，首次调用只记录不清空已有选区之外的东西；空格子也可以被选中（选区与格子内容无关）
- [X] T003 [P] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelSelection.java`：纯逻辑（无 Minecraft 依赖）。按 data-model.md：下标集合（升序、无重复）、`toggle/addAll/clear/contains/isEmpty/snapshot/syncContext`，以及一个单例访问器 `get()`（与 `ClientBackpackView.get()` 同风格）供输入、渲染、事件共用；`refKey` 用一个调用方传入的 `Object`/`long`，由调用方用 `(source, invIndex)` 组合。使 T002 通过

**Checkpoint**: `./gradlew compileJava test` 通过；T002 绿。

---

## Phase 3: User Story 1 - 用 Shift 选择一组格子 (Priority: P1) 🎯 MVP

**Goal**: 按住 Shift 在面板里单击切换、按住左键拖动加入；已选格子边框高亮；不按 Shift 点击清空；Shift 不拿起/放下任何物品。

**Independent Test**: quickstart 一（1–7）：Shift 单击切换；Shift 拖过一行（含空格）；从已选格拖动不取消；不按 Shift 点击清空；光标拿着物品时 Shift 单击只改选区；悬停与 013 预览与金色边框可区分。

- [X] T004 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/SelectionGestureTest.java`（**先写，先失败**），本任务只覆盖选择部分：按 data-model 的转移表——`beginSelect` 进入 `SELECTING` 且选区不变；停在起点内松开 → 切换起点（未选变选、已选变不选）；滑到别的格子 → 起点与滑过的格子都加入、松开时**不再切换起点**；拖动只加不减（路过已选格子保持选中）；空格子（客户端视图里为空）也加入；`visited` 去重（来回滑过只算一次）；线段采样不漏格（一帧从第 0 格直接移到第 8 格，9 个格子都加入，`SAMPLE_STEP = 4`、格子间距 18 px）；起点在被取消的点击里不切换；`cancel` 保留已加入的格子并丢弃未结算的点击切换；`slotAt` 返回 -1（面板外/虚拟格子）时不加入任何格子
- [X] T005 [US1] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/SelectionGesture.java`：纯逻辑状态机（无 Minecraft 依赖），本任务实现 `IDLE`/`SELECTING`：`beginSelect(slot, x, y)`、`advance(resolver, selection, x, y)`（线段采样，规则同 011/013，`SAMPLE_STEP = 4`，`resolver` 把 GUI 坐标映射到面板格子下标或 -1）、`release(selection)`、`cancel()`、`isActive()`。通过传入的 `PanelSelection` 修改选区。使 T004 通过。依赖 T003
- [X] T006 [US1] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelSelectionInput.java`（与游戏相连）：
  - **实现说明**：批量（Ctrl）分支在 T013 里加进同一个类；命中函数与 `PanelDragInput` 同规则（各写一份，未抽取）；`SelectionGesture` 复用 `PanelDragGesture.Resolver` 接口
  - `beginFromPanel(slot, isRight, x, y)`：按 contracts §1 的矩阵——左键 + `Screen.hasShiftDown()` + 非 `Screen.hasControlDown()` → `beginSelect`，返回 true；Shift+右键、Ctrl+Shift（任意键）→ 返回 true 但什么都不做（**吞掉**）；选择拖动进行中又按下另一个键 → 返回 true 不做事；**不按 Shift 也不按 Ctrl** 的左/右键按下 → `PanelSelection.get().clear()` 然后返回 false（交给 013/普通点击）；其余（Ctrl 单独等）本任务返回 false。命中规则：调用方已保证 `0 <= slot < ClientBackpackView.slotCount()`
  - `onRelease(button, x, y)`：当前有 `SELECTING` 手势且按键匹配时先 `advance` 一步再 `release`，返回 true
  - `tick(minecraft, x, y)`：每帧 `advance`；轮询到左键已松开时兜底结算（同 013 `PanelDragInput`）；手势期间 `Screen.hasControlDown()` 变为真 → `cancel`；
  - `stopIfInvalid(minecraft)`：面板隐藏、`minecraft.screen` 变化 → `cancel`；`reset()`
  - 命中函数 `slotAt(x, y)` 与 `PanelDragInput.slotAt` 同规则（`PanelSlots.indexAt` 且 `< slotCount`，面板不可见返回 -1）
  依赖 T005
- [X] T007 [US1] 接入现有输入与事件：修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java` 的 `dispatchMouseButton` 面板格子分支——在 011 的 `QuickTransferInput.beginFromPanel` **之前**调用 `PanelSelectionInput.beginFromPanel`，返回 true 则 `return true`；`RELEASE` 分支在 013 的 `PanelDragInput.onRelease` **之前**调用 `PanelSelectionInput.onRelease`；修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`：`onScreenRender` 与 `onRenderGui` 里每帧调用 `PanelSelectionInput.tick`（与 011/013 同样的规则：界面打开时只由 `onScreenRender` 驱动，`onRenderGui` 提前返回），`onLoggingIn` 里 `PanelSelectionInput.reset()` 并清空选区，`onClientTick` 里调用 `stopIfInvalid`。依赖 T006
- [X] T008 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：在 `drawSlotsWithItems` 里对每个可见格子，若 `PanelSelection.get().contains(i)`，在物品与数量之后、悬停层之前画格子内侧 1 像素金色（`0xFFFFD34D`）边框（4 条 `graphics.fill`），常量命名 `COLOR_SELECTED`；013 预览格子的白色填充与悬停层保持原样。依赖 T003
- [X] T009 [US1] 运行 `./gradlew compileJava test`，确认 T002、T004 与全部既有测试通过
  - **结果**：退出码 0；`PanelSelectionTest` 11 个用例、`SelectionGestureTest` 12 个选择用例通过
- [X] T010 [US1] 按 `quickstart.md` 一（1–7）在 `./gradlew runClient` 逐项验证（HUD 与打开界面各做一遍），**并特别确认金色边框的辨识度**；结果记到 `specs/014-panel-selection/DELIVERY.md`（没做的项记"未验证"）
  - **用户验证（2026-11-05）**：用户在游戏里验证"选区功能运行正常"，未逐项记录；按用户结论勾选

**Checkpoint**: US1 达成 FR-001～FR-006、FR-018（吞掉部分）、FR-020（选择部分）、SC-001。

---

## Phase 4: User Story 2 - 用 Ctrl 批量转出选区 (Priority: P1)

**Goal**: Ctrl 点击已选格子，松开时把选区里所有有物品的格子批量转出（目标按 011/012），放不下的留在原格，选区始终不变；Ctrl 点击未选格子保持 011/012 现状。

**Independent Test**: quickstart 二（8–15）：选 5 格（含空格）批量转到玩家物品栏/箱子；目标只能放下 3 格时按下标顺序转；放不下时选区不变；Ctrl 点击未选格子行为不变；松开前先松 Ctrl 取消；移动鼠标不额外转移；光标非空不触发。

- [X] T011 [P] [US2] 在 `SelectionGestureTest.java` 补批量用例（**先写，先失败**）：`beginBatch` 只在"左键、Ctrl、无 Shift、光标空、起点格已选"时进入 `BATCH_PENDING`，否则返回"不接管"；`releaseBatch(ctrlHeld, shiftHeld, visible, sameScreen, cursorEmpty)` 在全部满足时返回**升序、只含当前有物品**的选中格子，任一不满足返回空；选区里全是空格子时返回空（不发）；选区含超过当前可见范围的下标时仍被包含；`BATCH_PENDING` 期间的 `advance` 不产生任何结果也不改选区；`cancel` 后不再提交；批量不改变选区
- [X] T012 [US2] 在 `SelectionGesture.java` 实现 `BATCH_PENDING`：`beginBatch(slot, selection, cursorEmpty)`、`releaseBatch(...)`（返回升序 `int[]`，通过注入的 `hasItem(slot)` 判断是否有物品）。使 T011 通过。依赖 T005
- [X] T013 [US2] 在 `PanelSelectionInput.java` 增加 Ctrl 分支：`beginFromPanel` 里左键 + `Screen.hasControlDown()` + 非 Shift + 光标为空（用 `FloatingPanelRenderer.cursorStack` 同样的来源判断）+ 该格已选 → `beginBatch`，返回 true（按下被消费，松开由 `ConsumedPressTracker` 吞掉）；Ctrl 点击**未选**格子仍返回 false，交给 011（不改选区）；`onRelease` 在 `BATCH_PENDING` 时调用 `releaseBatch`（Ctrl/Shift 用当前按键状态、可见性用 `FloatingPanelState`、界面用 `minecraft.screen` 与按下时记录的比较、光标空用当前光标），非空列表则 `PacketDistributor.sendToServer(BackpackQuickTransferPayload.of(Direction.TO_INVENTORY, slots))`；`tick` 里 `BATCH_PENDING` 不发任何东西；`stopIfInvalid` 在面板隐藏/界面变化时取消。依赖 T012、T007
- [X] T014 [US2] 运行 `./gradlew compileJava test`，确认 T011 与全部既有测试通过
  - **结果**：退出码 0；`SelectionGestureTest` 共 19 个用例（含 7 个批量用例）通过
- [X] T015 [US2] 按 `quickstart.md` 二（8–15）在 `./gradlew runClient` 逐项验证（玩家物品栏与箱子两种目标，HUD 与打开界面各做一遍），**并留意松开才发送的手感，以及松开前先放开 Ctrl 的取消是否容易误触**；结果记到 `DELIVERY.md`
  - **用户验证（2026-11-05）**：用户在游戏里验证"选区功能运行正常"，未逐项记录；按用户结论勾选

**Checkpoint**: US2 达成 FR-007～FR-011、SC-002、SC-003。

---

## Phase 5: User Story 3 - 选区的生命周期与稳健性 (Priority: P2)

**Goal**: 选区在滚动、缩放、物品变化、HUD↔界面切换时保持；关闭面板、换背包、容量变化、登出时清空；选区按下标存储，滚出视野的选中格子仍参与批量转出。

**Independent Test**: quickstart 三（16–21）：滚动后选区保持且滚出的选中格仍被转出；缩放后保持；关闭重开清空；HUD↔界面保持；换背包清空；空格子被放入物品后仍选中。

- [X] T016 [US3] 接入"每 tick 同步上下文"：修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` 的 `onClientTick`，调用 `PanelSelection.get().syncContext(FloatingPanelState.get().isVisible(), refKey, ClientBackpackView.get().slotCount())`，`refKey` 由 `ClientBackpackView.get().getActiveRef()` 的 `(source().ordinal(), invIndex())` 组合（无 ref 时用固定值）；确认 `closePanel`、`stateClose`、`hideIfDisallowed` 等关闭路径都会在下一个 tick 清空选区（**不要**在每个关闭点重复清空）。依赖 T003
  - **实现说明**：在 `FloatingPanelEvents.syncSelectionContext()` 里每 tick 调用，`refKey` 为 `source.ordinal():invIndex`；没有在各个关闭点重复清空
- [X] T017 [US3] 静态检查：`grep -n "PanelSelection" src/main/java` 确认**滚动**（`scrollBy`/`setScrollRow`）、**缩放**（`beginResize/updateResize/finishResize`）、`PanelSlots.isRowVisible` 的代码路径里**没有**任何清空选区或按可见性过滤选区的调用；批量转出的格子来自 `PanelSelection.snapshot()` 而不是按可见行计算。结果与命令记到 `DELIVERY.md`
  - **结果**：`grep -rn PanelSelection src/main/java` 显示选区只被输入类、手势类、渲染器和 `FloatingPanelEvents` 引用；`PanelSelection`/`SelectionGesture`/`PanelSelectionInput` 里没有 `scrollBy`/`setScrollRow`/`beginResize`/`updateResize`/`finishResize`/`isRowVisible`；批量转出的格子来自 `PanelSelection.snapshot()`（`SelectionGesture.releaseBatch`），不按可见行过滤
- [X] T018 [US3] 运行 `./gradlew compileJava test`，确认全部测试通过（含 T002 里 `syncContext` 的用例）
  - **结果**：退出码 0，22 个测试类
- [X] T019 [US3] 按 `quickstart.md` 三（16–21）在 `./gradlew runClient` 逐项验证；结果记到 `DELIVERY.md`。**已知限制**：同一栏位里把背包换成另一只同容量背包时选区不会被清空（research Decision 8），如实记录
  - **用户验证（2026-11-05）**：用户在游戏里验证"选区功能运行正常"，未逐项记录；按用户结论勾选

**Checkpoint**: US3 达成 FR-012、FR-013、FR-014（读取接口）、SC-007。

---

## Phase 6: User Story 4 - 不干扰现有操作，原 Shift 换手改到 Alt (Priority: P2)

**Goal**: 主手交换改到 Alt（macOS Option）；Shift 在面板里只属于选区；普通点击、右键、013 的拖动、011/012 的 Ctrl 转出都保持原样（点击会清空选区除外）。

**Independent Test**: quickstart 四（22–29）：HUD 下 Alt+点击交换、Shift+点击不交换；光标拿着物品时 Alt+点击立即交换；打开界面时 Alt+点击与普通点击一致；Shift+Alt 按选区；Ctrl+Shift 与 Shift+右键被忽略；重跑 013、011/012 的 quickstart。

- [X] T020 [P] [US4] 修改 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackClickPayload.java`：字段 `shiftDown` 改名 `swapHand`（含义：主手与该格交换），`StreamCodec` 里写入/读取的位（`flags & 1`）**不变**，线上格式与协议版本（`"6"`）不变；`handleServer` 的交换分支逻辑原样保留，只改字段引用；在类上用一行注释写明字段含义变化与原因（014）。`grep -rn "shiftDown" src` 确认没有遗漏的引用
- [X] T021 [US4] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java` 的 `sendClick`：标志位由原来读 `Screen.hasShiftDown()` 与 GLFW 的 Shift 键，改为读 `Screen.hasAltDown()`（同时清理因此不再使用的 `InputConstants` 等导入）；变量名改 `swapHand`。依赖 T020
- [X] T022 [US4] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelDragInput.java` 的 `beginFromPanel`：**没有打开容器界面**（`!ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)`）且 `Screen.hasAltDown()` 时返回 false（让路），保证 HUD 下 Alt+点击（含光标拿着物品时）仍是按下即发的立即点击；打开着界面时 Alt 不让路。在方法上补一行注释写明原因（014）。依赖 T021
- [X] T023 [US4] 静态检查：对 014 新增与修改的文件运行 `grep -n "hasShiftDown\|KEY_LEFT_SHIFT\|KEY_RIGHT_SHIFT\|hasAltDown"`，确认 **Shift 只出现在 `PanelSelectionInput`（触发选择手势、吞掉其余 Shift 组合）以及 011/013 既有的让路判断里**，Alt 只出现在 `sendClick` 与 `PanelDragInput` 的让路判断里；`grep -n "net.p3pp3rf1y\|com.tiviacz"` 在新增文件上无命中（宪法 IV）；`git diff --stat -- src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferInput.java src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferGesture.java src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferRules.java` 为空（011/012 客户端手势文件未被 014 改动）。命令与结果记到 `DELIVERY.md`
  - **结果**：Shift 只出现在 `PanelSelectionInput`（选择手势与吞掉其余组合、批量提交时检查）以及 011/013 既有的让路判断；Alt 只出现在 `FloatingPanelInput.sendClick` 与 `PanelDragInput` 的让路判断；新增文件无 `p3pp3rf1y`/`tiviacz`；`git diff --stat` 对 `QuickTransferInput/Gesture/Rules` 为空
- [X] T024 [US4] 运行 `./gradlew compileJava test`，确认全部测试通过
  - **结果**：退出码 0，22 个测试类（原 20 个 + 014 新增 2 个）
- [ ] T025 [US4] 按 `quickstart.md` 四（22–29）验证：Alt+点击主手交换（主手是背包本身时的保护、副手打开面板才能构造交换场景，不能构造则记"未验证"）、Shift+点击不交换、光标拿着物品时 Alt+点击立即交换、打开界面时 Alt 与普通点击一致、Shift+Alt、Ctrl+Shift 与 Shift+右键被忽略、重跑 013 与 011/012 的 quickstart；再按五（30–31）做守恒与日志检查；用 `./gradlew runClientCompat` 按六（32–33）验证精妙背包、旅行者背包。Windows/Linux 无法验证（尤其 Alt+点击是否被窗口管理器占用），在 `DELIVERY.md` 里如实写"未验证"。结果记到 `DELIVERY.md`
  - **部分确认（2026-11-05）**：用户确认 Alt+点击切换主手功能没有问题；其余项（Shift+点击不交换、Shift+Alt、Ctrl+Shift/Shift+右键被忽略、重跑 013 与 011/012、守恒与日志、`runClientCompat`、Windows/Linux）未确认，保持未验证

**Checkpoint**: US4 达成 FR-010、FR-016～FR-020、SC-005、SC-006、SC-008。

---

## Phase 7: Polish & 文档收尾

- [X] T026 [P] 更新文档：`specs/003-cross-gui-drag/spec.md` 的主手 Shift 路径处（行 64、87 附近）加【014 修订】——主手交换改为 Alt；`specs/011-ctrl-quick-transfer/spec.md`、`specs/012-ctrl-transfer-to-open-container/spec.md`、`specs/013-panel-drag-split-gather/spec.md` 的"假设"处各加一条【014 说明】——Shift 在面板里现专用于选区，Ctrl 点击已选格子由 014 接管为批量转出，未选格子行为不变；`docs/MCFloatingBackpack.md` 第 32 行"面板内格子支持……Shift 点击"和 M5 条目里补"Shift 单击/拖动选择格子（金色边框），Ctrl 点击已选格子批量转出，HUD 下主手交换改为 Alt+点击"，并指向 `specs/014-panel-selection/`
- [X] T027 创建 `specs/014-panel-selection/DELIVERY.md`：记录 T010、T015、T019、T025 的结果，**包括没做到的和结论不确定的**；列出已知限制（选区只在客户端、同栏位换同容量背包不清空、Windows/Linux 未验证、Alt 键冲突未验证、金色边框辨识度结论）；记录行为变化（Shift 不再触发主手交换；Shift+右键与 Ctrl+Shift 被忽略；Ctrl 批量转出是松开才发送；不按 Shift 的面板点击会清空选区）及用户是否接受
- [ ] T028 运行最终回归 `./gradlew compileJava test`，并确认游戏能启动（`./gradlew runClient` 进入世界无报错；**启动前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"` 确认没有正在运行的客户端，不要杀用户的游戏**）；把 spec 的 Status 改为实现完成，并如实写明验证未完成的部分
  - **部分完成**：`./gradlew compileJava test` 退出码 0（22 个测试类）；**游戏启动与进入世界未验证**（本次没有启动客户端）；spec Status 已改为"代码与单元测试完成，未在游戏里验证"

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（T001）：无依赖
- Phase 2（T002～T003）：T002 与 T003 互相独立可并行（"测试先于实现"配对）。**阻塞所有用户故事**
- Phase 3 US1（T004～T010）：依赖 Phase 2；T004 可先写；T005 使 T004 通过；T006 依赖 T005；T007 依赖 T006；T008 只依赖 T003，可与 T004～T007 并行
- Phase 4 US2（T011～T015）：依赖 US1 的状态机与输入类；T011 可先写；T012 依赖 T005；T013 依赖 T012、T007
- Phase 5 US3（T016～T019）：T016 只依赖 T003；可在 US1 之后与 US2 并行
- Phase 6 US4（T020～T025）：T020 独立可并行；T021 依赖 T020；T022 依赖 T021；与 US2、US3 的代码互不冲突，但**回归验证（T025）要在 US1～US3 代码都完成后做**
- Phase 7：依赖前面的结果；T026 可与代码任务并行

### User Story Dependencies

- **US1**：依赖基础设施，是 MVP；引入输入接线与渲染
- **US2**：依赖 US1 的状态机与输入类
- **US3**：依赖 `PanelSelection`；与 US2 独立
- **US4**：代码上独立（改字段来源与让路），验证依赖其余故事

### Parallel Opportunities

- T002 与 T003、T004 与（T005 之前的）文档整理、T008 与 T004～T007 可并行
- US2、US3、US4 在 US1 完成后可并行
- T020 可在任何时候独立做

---

## Parallel Example: Phase 2 与 US1 起步

```text
并行启动：
  T002 PanelSelectionTest.java          （选区集合测试，先写）
  T003 PanelSelection.java              （使 T002 通过）
随后：
  T004 SelectionGestureTest.java        （手势测试，先写）  ‖  T008 FloatingPanelRenderer 边框
  T005 SelectionGesture.java → T006 PanelSelectionInput.java → T007 接线
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1、2（选区集合与测试）。
2. 完成 US1（选择 + 高亮 + 清空），**停下来在游戏里验证**（T010），重点确认金色边框的辨识度、Shift 与 013 的互不干扰。
3. 再做 US2（批量转出），然后 US3（生命周期）、US4（Alt 改键与整体回归）。
4. 最后文档与 DELIVERY。

### 与宪法的关系

- 每个会改变玩家所见/所操作的故事都有客户端会话验证任务（T010、T015、T019、T025）；没做的项在 DELIVERY.md 里记"未验证"，不能因为编译或单元测试通过就勾选（宪法 V）。
- 选区只是客户端 UI 状态，物品移动仍由服务端按 011/012 校验执行（宪法 II）；不引用第三方类型（T023 静态检查，宪法 IV）；不新增资源，边框用 `fill` 画（宪法 VI 不适用）；只做选区与因此必须改的 Shift 主手交换（宪法 III）。

### 风险提醒（实现时盯住）

- **Shift 主手交换改键**：HUD 下 Alt+点击必须回归，且不能被 013 的"松开才结算"拖成延后（T022）。
- **Ctrl 批量转出"松开才发"与 011 "按下即转"并存**：按在已选格子上走 014，按在未选格子上走 011，两条路径不能互相吞按下/松开（沿用 `ConsumedPressTracker`，T013）。
- **选区按下标存储**：背包或容量变化必须清空（T016），否则旧下标会套到新背包上。
- **不要重构 011/013 里各自的线段采样**（宪法 III）；`SelectionGesture` 里再写一份，登记为后续整理项。
