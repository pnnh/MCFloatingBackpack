# Tasks: 浮动面板内的拖动均分与拖动收集

**Input**: Design documents from `/specs/013-panel-drag-split-gather/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/panel-cursor-gestures.md, quickstart.md

**Tests**: 分配/收集规则、请求校验、客户端状态机都是不依赖 Minecraft 的纯逻辑，plan 要求用单元测试覆盖（用假栈跑真实实现，与 011/012 同一做法）；玩家可见行为必须在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础设施（请求校验、服务端光标访问、网络包骨架与注册）；US1（拖动均分）是 MVP，它同时建立客户端状态机、预览渲染和"按下延后结算"这个行为变化；US2、US3、US4 在它之上增加收集、右键、双击；US5 是对现有操作与 011/012 的回归验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US5）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）

---

## Phase 1: Setup

- [ ] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（当前 17 个测试类），作为之后回归的对照
  - **未做**：改动前没有单独跑基线；改动后 `./gradlew compileJava test` 退出码 0，20 个测试类（原 17 个 + 013 新增 3 个）

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 请求校验、服务端光标访问、网络包骨架与注册。没有它们，任何故事都无法实现。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/PanelCursorRequestTest.java`（**先写，先失败**）：覆盖 `PanelCursorRequest.validate(Mode mode, int[] slots, int backpackSize)`：未知模式（`Mode.fromByte` 返回 null）不合法；空列表、null、越界、负数、重复、数量超过背包容量均不合法；`GATHER_ALL` 必须恰好 1 项；`DISTRIBUTE_*` 与 `GATHER_DRAG` 接受 1～背包容量项；`MAX_BACKPACK_SLOTS`（108）上限
- [X] T003 [P] 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/PanelCursorRequest.java`：纯逻辑（无 Minecraft 类型）。`enum Mode { DISTRIBUTE_LEFT, DISTRIBUTE_RIGHT, GATHER_DRAG, GATHER_ALL }`（`toByte/fromByte`，线上取值 0–3）与 `static boolean validate(...)`，按 data-model.md。使 T002 通过。**偏离 plan 的说明**：plan 的结构里没有单独列这个类，这里单列，因为被测校验必须不依赖 Minecraft 类型才能在测试里加载
- [X] T004 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/PanelCursor.java`：服务端光标读写的统一接口（data-model 的 `PanelCursor`）。`static PanelCursor of(boolean screenOpen, AbstractContainerMenu menu, BackpackInventory backpack)`：`screenOpen && menu != null` → 读写 `menu.getCarried()/setCarried`；否则 → `backpack.getCursor()/setCursor`（与 `BackpackClickPayload` 现有的判断一致）；提供 `ItemStack get()`、`void set(ItemStack)`、`boolean usesMenu()`
- [X] T005 新建 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackCursorGesturePayload.java`：客户端→服务端包，字段 `mode`（byte）、`slots`（VarInt 列表，读取时数量超过 108 抛 `DecoderException`）、`screenOpen`（boolean）（contracts §1）。`handleServer` 的**公共部分**：`ServerPlayer` 检查；`BackpackRef.resolve` 实时解析，无效则 `BackpackNetwork.sendSession(player, false, null)` 并返回；`ref.loadInventory`；`PanelCursor.of(...)`；光标为空则忽略；`PanelCursorRequest.validate` 失败整包忽略；然后按模式分派到四个**留空的**方法（US1～US4 分别实现），最后（有改动时）写回光标、`ref.saveInventory`、`player.getInventory().setChanged()`、菜单模式下 `broadcastChanges()`、`BackpackNetwork.sendFullSync`。依赖 T003、T004
- [X] T006 修改 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackNetwork.java`：把 `versioned("5")` 改成 `versioned("6")`，注册 `BackpackCursorGesturePayload`（`playToServer`）。依赖 T005

**Checkpoint**: `./gradlew compileJava test` 通过；T002 绿；游戏能启动并进入世界（协议版本 6）。

---

## Phase 3: User Story 1 - 拖动均分一组物品 (Priority: P1) 🎯 MVP

**Goal**: 光标上有物品时，在面板里按住左键滑过多个有效格子，松开时均分；拖动中有预览；只滑过一个格子退化为普通点击；HUD 与打开界面一致。

**Independent Test**: quickstart 一（1–8）：拿起 60 个圆石滑过 4 个空格各 15；10 个滑过 3 格各 3 余 1；已有 60 的格子只多 4；预览；单格退化；HUD 与打开界面一致。

- [X] T007 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/PanelCursorLogicTest.java`（**先写，先失败**）：复用 `QuickTransferLogicTest.FakeStack` 与 `OPS`，覆盖 `PanelCursorLogic` 的均分部分：`canQuickReplace`（空格真、同种真（**含已满的同种堆叠**）、不同种假）；`isValidDragSlot`（要求接受该物品、`光标数量 > 已选格子数`）；`placeCount`（左键 `floor(n ÷ k)`、右键 1）；`distribute`：60÷4 → 每格 15 光标空；10÷3 → 每格 3 光标剩 1；64 滑过已有 60 的格子与两个空格 → 已有 60 的格子只多 4、多出的留光标；已满的同种格子占一个份额但放不进东西；光标数量不足时新格子不加入；结算时 `光标数量 < 格子数` 整个拒绝；每格不超过 `min(最大堆叠, 格子上限)`；**性质检查**：随机 100 组布局，光标 + 各格该种物品总数前后相等（data-model 的不变量）
- [X] T008 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelDragGestureTest.java`（**先写，先失败**），本任务只覆盖分配部分：光标非空且 Ctrl/Shift 未按时按下 → `DISTRIBUTING`，不产生任何请求；Ctrl 或 Shift 按下时 `begin` 返回"不开始"；线段采样（18px 间距的一行格子，一帧掠过多格不漏）；`visited` 保证同一格一次拖动只处理一次；只有通过 `isValidDragSlot` 的格子进入 `selected`；松开时的结局：`selected ≥ 2` → 分配请求；`== 1` → 对该格的普通点击；`== 0` → 对松开位置格子的普通点击；取消条件（面板隐藏、界面切换、光标变空、按下另一个键、Ctrl/Shift 按下）→ 丢弃且不产生请求；预览数据（每格放入后的数量、光标剩余、超上限标记）
  - **实现说明**：Ctrl/Shift 让路与取消条件（面板隐藏、界面切换、光标变空）依赖 Minecraft 的按键与界面状态，放在 `PanelDragInput`，没有单元测试覆盖，要靠 T016/T034 在游戏里验证；状态机本身（采样、去重、结局、取消、预览）已测
- [X] T009 [US1] 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/PanelCursorLogic.java`：泛型纯逻辑（无 Minecraft 依赖，复用 `QuickTransferLogic.StackOps`），实现 `canQuickReplace`、`isValidDragSlot`、`placeCount`、`distribute`（返回每格新数量、光标剩余、结算是否被拒），规则逐条照搬原版（research Decision 2）。使 T007 通过
- [X] T010 [US1] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelDragGesture.java`：纯逻辑状态机（无 Minecraft 依赖），按 data-model 的 `PanelDragGesture`：状态 IDLE/DISTRIBUTING、`selected`、`visited`、线段采样（`SAMPLE_STEP = 4`）、通过注入的 `SlotView`/光标接口调用 `PanelCursorLogic.isValidDragSlot`、预览数据、取消。使 T008 通过。依赖 T009
- [X] T011 [US1] 服务端：在 `BackpackCursorGesturePayload` 里实现 `DISTRIBUTE_LEFT` 分支：按包内顺序逐个检查 `isValidDragSlot`（`selectedCount` 为已接受的格子数）；有效格子 ≥2 → `PanelCursorLogic.distribute` 结算并写回各格与光标；恰好 1 个 → 等同对该格做一次普通点击（`BackpackClickLogic.click(…, false)`，光标来自 `PanelCursor`）；0 个 → 忽略。依赖 T005、T009
  - **实现说明**：服务端只按 `canQuickReplace` 过滤格子（"光标数量 > 已选格子数"这一半由 `distribute` 结算时的 `光标数量 ≥ 格子数` 保证，与原版结算一致）；单格退化为对该格的普通点击
- [X] T012 [US1] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelDragInput.java`（与游戏相连的部分）：面板格子命中沿用 `PanelSlots.indexAt` 并检查 `< ClientBackpackView.slotCount()`；`onPress(slot, rightButton, x, y)`：Ctrl（`Screen.hasControlDown()`）或 Shift（`Screen.hasShiftDown()`，只为让路）按下 → 返回"不处理"；光标为空 → 返回"不处理，走现有立即点击"；光标非空 → 开始 `DISTRIBUTING`，**不发包**，返回"已处理"；`tick(minecraft, mouseX, mouseY)`：每帧采样、检查取消条件、轮询按键状态；松开（轮询到按键已松）时按 T008 的结局发 `BackpackCursorGesturePayload`（带 `screenOpen`，用与 `FloatingPanelInput.sendClick` 相同的判断）或对相应格子发现有的 `BackpackClickPayload`；光标来源判断复用 `FloatingPanelRenderer.cursorStack` 的条件；`reset()`。依赖 T010、T006
  - **实现说明**：入口命名为 `beginFromPanel`（与 `QuickTransferInput` 一致）；松开由 `FloatingPanelInput` 调 `onRelease` 结算，`tick` 轮询到按键已松时也会结算（松开事件被别处拿走的兜底）；光标为空时 `beginFromPanel` 返回 false 由调用方发现有的立即点击，同时在左键且该格有物品时进入 GATHERING
- [X] T013 [US1] 接入现有输入与事件：修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java` 的 `dispatchMouseButton` 面板格子分支——在 011 的 `QuickTransferInput.beginFromPanel` **之后**、`sendClick` **之前**调用 `PanelDragInput.onPress`，已处理则 `return true`；`RELEASE` 分支对分配进行中的手势返回 true（吞掉松开，并通过 `ConsumedPressTracker` 已登记的按下保证一致）；修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`：在 `onScreenRender` 与 `onRenderGui` 里每帧调用 `PanelDragInput.tick`（与 011 的 `QuickTransferInput.tick` 同样的规则：界面打开时只由 `onScreenRender` 驱动，`onRenderGui` 提前返回），`onLoggingIn` 里 `reset()`，`onClientTick` 里检查取消条件。依赖 T012
  - **实现说明**：拖动进行中再按另一个键，该按下被吞掉且不做任何事；松开的吞掉沿用 `ConsumedPressTracker`（按下被消费，松开才被吞）
- [X] T014 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：拖动分配进行中且选中格子数 ≥2 时，对每个选中格子渲染"放入后的数量"（超过上限时用黄色显示上限，与原版 `renderSlot` 一致），光标上显示"将剩余的数量"；选中格子数为 1 时不画预览；预览数据只读自 `PanelDragGesture`，不改变任何物品。依赖 T010
- [X] T015 [US1] 运行 `./gradlew compileJava test`，确认 T002、T007、T008 与全部既有测试通过
- [X] T016 [US1] 按 `quickstart.md` 一（1–8）在 `./gradlew runClient` 逐项验证（HUD 与打开界面各做一遍），**并特别确认"按下延后结算"带来的手感变化可以接受**（拿着物品单击放下现在是松开时生效）；结果记到 `specs/013-panel-drag-split-gather/DELIVERY.md`（没做的项记"未验证"）
  - **用户验证（2026-11-05）**：用户在游戏里验证"功能没有问题"，未逐项记录具体数值；按用户结论勾选

**Checkpoint**: US1 达成 FR-001～FR-006、FR-010、FR-012、FR-013（均分部分）、FR-014（均分取消）、SC-001、SC-002、SC-006。

---

## Phase 4: User Story 2 - 拖动收集同类物品 (Priority: P1)

**Goal**: 光标为空时单击拿起一格后不松手，滑过同种物品所在的面板格子，把它们收进光标直到最大堆叠；松开后光标上保持当前堆叠。

**Independent Test**: quickstart 三（10–14）：20/30/40 三格圆石，拿起 20 后滑过后两格，光标 20→50→64，第三格剩 26；不同种/空格被忽略；光标满后不再收；HUD 与打开界面一致。

- [X] T017 [P] [US2] 在 `PanelCursorLogicTest.java` 补收集用例（**先写，先失败**）：`gatherOne`：同种且光标未满 → 取 `min(格子数量, 最大堆叠 − 光标数量)`，格子里剩下的留在原格；不同种/空格/光标已满 → 忽略；20+30 → 50，再 +40 → 64 且该格剩 26；随机 100 组下光标 + 各格总数守恒
- [X] T018 [P] [US2] 在 `PanelDragGestureTest.java` 补 `GATHERING` 用例（**先写，先失败**）：光标为空且该格有物品时按下 → 立即请求"拿起"并进入 `GATHERING(kind = 按下时该格的物品)`；每帧只把**新**滑到的、客户端视图里与 `kind` 同种的格子批量放进 `GATHER_DRAG` 请求；`visited` 去重；不同种/空格被忽略；松开左键结束；取消不撤销已收的；起点格子本身不再作为候选
- [X] T019 [US2] 在 `PanelCursorLogic.java` 实现 `gatherOne`（使 T017 通过）；在 `PanelDragGesture.java` 实现 `GATHERING` 状态与批量候选（使 T018 通过）。依赖 T009、T010
- [X] T020 [US2] 服务端：在 `BackpackCursorGesturePayload` 实现 `GATHER_DRAG` 分支：按包内顺序，对每个与光标**实际**同种、光标未满的格子调用 `gatherOne`，写回各格与光标（不信任客户端的"同种"判断）。依赖 T005、T019
- [X] T021 [US2] 客户端：在 `PanelDragInput.java` 实现收集：`onPress` 光标为空且该格有物品且左键 → 仍走现有立即"拿起"（返回"不处理"以便调用方照旧发点击），但同时开始 `GATHERING`，记录 `kind`（发出拿起请求**之前**读取该格内容）；`tick` 里每帧把新候选批量发 `GATHER_DRAG`；Ctrl/Shift 按下时不进入。依赖 T019、T012
- [X] T022 [US2] 运行 `./gradlew compileJava test`，确认 T017、T018 与全部既有测试通过
- [X] T023 [US2] 按 `quickstart.md` 三（10–14）在 `./gradlew runClient` 逐项验证，并留意局域网/延迟下光标数量更新的手感；结果记到 `DELIVERY.md`
  - **用户验证（2026-11-05）**：用户在游戏里验证"功能没有问题"，未逐项记录具体数值；按用户结论勾选

**Checkpoint**: US2 达成 FR-007、FR-008、FR-009、FR-017、SC-004。

---

## Phase 5: User Story 3 - 右键拖动逐格放一个 (Priority: P2)

**Goal**: 光标非空时按住右键滑过多个有效格子，每格放 1 个。

**Independent Test**: quickstart 二（9）：拿起 10 个，按住右键滑过五个空格，每格 1 个，光标剩 5。

- [X] T024 [P] [US3] 在 `PanelCursorLogicTest.java` 与 `PanelDragGestureTest.java` 补右键用例（**先写，先失败**）：`distribute` 右键每格 `placeCount = 1`、光标数量用完后不再加格子、已满的同种格子占份额但不放入；手势：右键按下光标非空 → `DISTRIBUTING(right)`；右键按下光标为空 → 不处理（走现有"拿起一半"）；单格右键退化为 `BackpackClickLogic.click(…, true)`；左右键不能混用（拖动中按下另一个键 → 取消）
- [X] T025 [US3] 实现右键：`PanelCursorLogic.distribute` 已按 `Mode` 区分 `placeCount`（若 T009 未覆盖则补齐）；服务端 `BackpackCursorGesturePayload` 的 `DISTRIBUTE_RIGHT` 分支（单格时调用 `BackpackClickLogic.click(…, true)`）；`PanelDragInput.onPress` 接受右键。依赖 T011、T012
- [X] T026 [US3] 运行 `./gradlew compileJava test`，确认 T024 与全部既有测试通过
- [X] T027 [US3] 按 `quickstart.md` 二（9）在 `./gradlew runClient` 验证；结果记到 `DELIVERY.md`
  - **用户验证（2026-11-05）**：用户在游戏里验证"功能没有问题"，未逐项记录具体数值；按用户结论勾选

**Checkpoint**: US3 达成 FR-015。

---

## Phase 6: User Story 4 - 双击收集 (Priority: P2)

**Goal**: 面板里左键双击同一格，把面板里所有同种物品收集到光标直到最大堆叠（先未满后满）。

**Independent Test**: quickstart 四（15）：光标拿着 10 个圆石，面板里 20、30、64 三格圆石，双击一个空格 → 先收 20、30，再从满的补到 64。

- [X] T028 [P] [US4] 在 `PanelCursorLogicTest.java` 与 `PanelDragGestureTest.java` 补双击用例（**先写，先失败**）：`pickupAll`：光标非空且被双击的格子为空时，两遍遍历（先未满的同种堆叠，再收满的），每格取 `min(格子数量, 最大堆叠 − 光标数量)`，直到光标满；被双击的格子非空且能取出时忽略（与原版 `PICKUP_ALL` 触发条件一致）；守恒（随机 100 组）；手势：250ms 内同一格同一键的第二次按下标记为双击，松开时产生 `GATHER_ALL(该格)`，代替普通点击与分配；超时、换格、换键都不算双击
- [X] T029 [US4] 实现：`PanelCursorLogic.pickupAll`（使 T028 通过）；`PanelDragGesture` 的双击判定（记录上一次点击的格子、按键与时间，时间由调用方注入以便测试）；服务端 `BackpackCursorGesturePayload` 的 `GATHER_ALL` 分支；`PanelDragInput` 在松开时发 `GATHER_ALL`。依赖 T009、T010、T012
  - **实现说明**：服务端 `GATHER_ALL` 不检查被双击格子是否为空，客户端只在"第二次按下时光标非空"才会发它；背包里全部格子参与收集
- [X] T030 [US4] 运行 `./gradlew compileJava test`，确认 T028 与全部既有测试通过
- [X] T031 [US4] 按 `quickstart.md` 四（15）在 `./gradlew runClient` 验证，并留意双击与"点击后马上拖动"的区分手感；结果记到 `DELIVERY.md`
  - **用户验证（2026-11-05）**：用户在游戏里验证"功能没有问题"，未逐项记录具体数值；按用户结论勾选

**Checkpoint**: US4 达成 FR-016。

---

## Phase 7: User Story 5 - 不干扰现有操作 (Priority: P2)

**Goal**: 单击、右键点击、011/012 的 Ctrl 快速转移、菜单格子自己的行为都保持原样；只有"按住并滑到别的格子"才进入拖动。

**Independent Test**: quickstart 五（16–22）：单击放下/交换、拿起整组/一半、右键放一个、Ctrl 与 Shift 让路、玩家物品栏/箱子/精妙背包/旅行者背包自己的格子、重跑 011/012 的 quickstart。

- [X] T032 [US5] 静态检查：对 013 新增和修改的文件运行 `grep -n "hasShiftDown\|KEY_LEFT_SHIFT\|KEY_RIGHT_SHIFT"`，确认**读取 Shift 只出现在"按下 Shift 就让路"的判断里**（`PanelDragInput`），没有任何地方用 Shift 触发行为；`grep -n "net.p3pp3rf1y\|com.tiviacz"` 在新增文件上无命中（宪法 IV）；并运行 `git diff --stat -- src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferInput.java src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferGesture.java src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferRules.java`，确认 011/012 的客户端手势文件**没有被 013 改动**。结果与命令记到 `DELIVERY.md`
- [ ] T033 [US5] 按 `quickstart.md` 五（16–22）验证：HUD 与打开界面下单击放下/交换、光标为空时单击拿起整组与右键拿起一半、右键放一个；按着 Command/Shift 不触发本功能；用 `./gradlew runClientCompat` 确认精妙背包、旅行者背包、箱子、玩家物品栏**自己的格子**里的拖动不受影响；**重跑 011 和 012 的 quickstart**确认 Ctrl 快速转移的结果相同。结果记到 `DELIVERY.md`
- [ ] T034 [US5] 按 `quickstart.md` 六（23–24）补做：拖动分配中途按 E/ESC 或隐藏面板（分配作废、收集保留已收的）；50 次以上拖动均分与拖动收集（含已满、不同种、数量不足）前后物品总数相等；搜 `run/logs/latest.log` 没有新增报错；macOS Command 键只用于让路的验证；Windows/Linux **无法验证**，在 `DELIVERY.md` 里如实写"未验证"

**Checkpoint**: US5 达成 FR-010、FR-011、FR-014、FR-018、SC-003、SC-005、SC-007。

---

## Phase 8: Polish & 文档收尾

- [X] T035 [P] 更新 `specs/011-ctrl-quick-transfer/spec.md` 与 `specs/012-ctrl-transfer-to-open-container/spec.md` 的"假设"处各加一条【013 修订/说明】：面板里 Ctrl/Shift 按下时 013 的拖动让路，011/012 的 Ctrl 手势优先；网络协议版本现为 6（`specs/006-tiered-backpacks/DELIVERY.md` 的协议版本说明同步）
- [X] T036 [P] 更新 `docs/MCFloatingBackpack.md` M5 里关于面板操作的那段：补"面板里拿着物品按住左/右键拖过多个格子可均分/逐格放一个，单击拿起后按住滑过同种格子可收集，双击收集"，并指向 `specs/013-panel-drag-split-gather/`
- [X] T037 创建 `specs/013-panel-drag-split-gather/DELIVERY.md`：记录 T016、T023、T027、T031、T033、T034 的结果，**包括没做到的和结论不确定的**；列出已知限制（`screenOpen` 是客户端自报、未做客户端预测、菜单格子/混合拖动/中键/触摸屏/Shift+双击不做、Windows/Linux 未验证）；记录行为变化（拿着物品放下改为松开时结算）与用户是否接受
- [ ] T038 运行最终回归 `./gradlew compileJava test`，并确认游戏能启动（`./gradlew runClient` 进入世界无报错）；把 spec 的 Status 改为实现完成，并如实写明验证未完成的部分
  - **部分完成**：`./gradlew compileJava test` 退出码 0；**游戏启动与进入世界未验证**（本次没有启动客户端），spec Status 保持 Draft→实现完成但未验证，见 DELIVERY.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（T001）：无依赖
- Phase 2（T002～T006）：T002 与 T003 互相独立可并行（"测试先于实现"配对）；T004 独立；T005 依赖 T003、T004；T006 依赖 T005。**阻塞所有用户故事**
- Phase 3 US1（T007～T016）：依赖 Phase 2；T007、T008 可并行先写；T009 使 T007 通过，T010 依赖 T009，T011 依赖 T005、T009，T012 依赖 T010、T006，T013 依赖 T012，T014 依赖 T010
- Phase 4 US2（T017～T023）：依赖 US1 的状态机与客户端接入；T017、T018 可并行
- Phase 5 US3（T024～T027）：依赖 US1；与 US2 互相独立
- Phase 6 US4（T028～T031）：依赖 US1；与 US2、US3 互相独立
- Phase 7 US5（T032～T034）：依赖前面所有故事的代码；T032 可在每个故事后增量做
- Phase 8：依赖前面的结果；T035、T036 可并行

### User Story Dependencies

- **US1**：依赖基础设施，是 MVP；同时引入"按下延后结算"的行为变化
- **US2、US3、US4**：都依赖 US1 的状态机与客户端接入，彼此独立
- **US5**：纯回归验证与静态检查，依赖代码完成

### Parallel Opportunities

- T002 与 T003、T007 与 T008 可并行先写测试
- US2、US3、US4 在 US1 完成后可并行
- T035、T036 可并行

---

## Parallel Example: Phase 2 与 US1 起步

```text
并行启动：
  T002 PanelCursorRequestTest.java      （校验测试，先写）
  T003 PanelCursorRequest.java          （使 T002 通过）
  T004 PanelCursor.java                 （服务端光标访问）
随后顺序：T005 → T006
US1 起步并行：
  T007 PanelCursorLogicTest.java        （均分规则测试，先写）
  T008 PanelDragGestureTest.java        （状态机测试，先写）
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1、2（请求校验、光标访问、网络包骨架与注册）。
2. 完成 US1（拖动均分，含客户端状态机、预览、按下延后结算），**停下来在游戏里验证**（T016），重点确认手感变化能接受。
3. 再做 US2（拖动收集），然后 US3（右键）、US4（双击）。
4. 最后 US5 的回归与文档收尾。

### 与宪法的关系

- 每个会改变玩家所见/所操作的故事都有客户端会话验证任务（T016、T023、T027、T031、T033、T034）；没做的项在 DELIVERY.md 里记"未验证"，不能因为编译或单元测试通过就勾选（宪法 V）。
- 规则只在服务端结算，预览只是客户端显示（宪法 II）；不引用第三方类型（T032 静态检查，宪法 IV）；不新增资源（宪法 VI 不适用）。

### 风险提醒（实现时盯住）

- **拿着物品放下改为松开时结算**：行为变化，必须在 T016 与 T033 里逐条回归单击放下、交换、右键放一个。
- **预览与服务端结果必须一致**：两边共用 `PanelCursorLogic`；测试要钉住"已满的同种堆叠算有效格子"等原版怪癖。
- **`screenOpen` 是客户端自报**：沿用现有设计，写进已知限制。
- **读取 Shift 只为让路**：不得用 Shift 触发任何行为。
