# Tasks: 取消"无界面 HUD 模式"，改为兜底的透明宿主界面

**Input**: Design documents from `/specs/019-fallback-host-screen/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/host-screen-lifecycle.md, quickstart.md；宪法 2.0.0 原则 I

**Tests**: 宿主何时出现/消失（策略表）、按键怎么分类（吞掉/让出/不拦截/开关热键）是不依赖 Minecraft 的纯逻辑，plan 要求用单元测试覆盖；宿主能否在真实游戏里让位、恢复、转发按键、不卡键只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的纯逻辑基础（策略与按键分类）；US1（宿主承载面板，删掉 HUD 驱动）是 MVP；US2（按键转发：E 与模组按键仍能打开别的界面）是第二个 P1，**没有它宿主会挡住 E，不可单独交付**；US3（ESC 与非容器界面隐藏）、US4（状态与光标物品）、US5（创造模式与按键状态）依次在其上完成或验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US5）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，有正在运行的客户端就不要启动、更不要杀用户的游戏**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（当前 26 个测试类左右，以实际为准）；结果记到 `specs/019-fallback-host-screen/DELIVERY.md`

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 宿主的两个决策——"此刻该不该有宿主"和"这个按键怎么处理"——都是纯逻辑，先写测试再实现；之后所有故事都复用它们。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/HostScreenPolicyTest.java`（**先写，先失败**），按 data-model.md 的策略表：`decide(panelVisible, kind, playerAlive)` 对 `ScreenKind {NONE, HOST, CONTAINER, OTHER}` × 可见 × 存活的全部 16 种组合逐一断言——可见且存活：`NONE` → `OPEN_HOST`，`HOST`/`CONTAINER`/`OTHER` → `NONE`；可见但死亡：任意界面 → `CLEAR_PANEL`；不可见：`HOST` → `CLOSE_HOST`，其余 → `NONE`；再加三个不变量用例：`OPEN_HOST` 只在 `kind == NONE` 时出现（遍历全部组合）、`CLOSE_HOST` 只在 `kind == HOST` 时出现、不可见时除 `CLOSE_HOST` 外没有任何动作
- [X] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/HostKeyRouterTest.java`（**先写，先失败**），按 data-model.md 的按键分类表：绑定集合含面板开关热键（`key.mcfloatingbackpack.toggle`）→ `TOGGLE`（含它同时绑了别的功能的情况，仍是 `TOGGLE`）；集合为空 → `PASS`；每个名字都在世界动作清单里 → `SWALLOW`（逐个断言清单里的 22 个名字：`key.forward/left/back/right/jump/sneak/sprint/attack/use/pickItem/drop/swapOffhand`、`key.hotbar.1`～`9`、`key.saveToolbarActivator`、`key.loadToolbarActivator`，以及两个清单内名字同时绑定仍是 `SWALLOW`）；含任何清单外的名字 → `YIELD`（`key.inventory`、`key.chat`、`key.command`、`key.advancements`、`key.togglePerspective`、一个模拟的模组按键名 `key.sophisticatedbackpacks.open_backpack`；以及"清单内 + 清单外"冲突绑定 → `YIELD`）
- [X] T004 [P] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/HostScreenPolicy.java`：纯逻辑（无 Minecraft 依赖）。`enum ScreenKind { NONE, HOST, CONTAINER, OTHER }`、`enum Action { NONE, OPEN_HOST, CLOSE_HOST, CLEAR_PANEL }`、`static Action decide(boolean panelVisible, ScreenKind kind, boolean playerAlive)`，规则逐条照 data-model.md 的策略表。使 T002 通过
- [X] T005 [P] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/HostKeyRouter.java`：纯逻辑。`enum KeyRole { TOGGLE, PASS, SWALLOW, YIELD }`、`static KeyRole classify(Collection<String> boundMappingNames, String toggleMappingName)`；世界动作清单作为 `Set<String>` 常量，名字与 T003 一致；规则自上而下先到先得，逐条照 data-model.md。使 T003 通过
- [X] T006 运行 `./gradlew compileJava test`，确认 T002、T003 与全部既有测试通过

**Checkpoint**: 编译通过；`HostScreenPolicyTest`、`HostKeyRouterTest` 绿；既有测试不变。

---

## Phase 3: User Story 1 - 没开界面时，面板出现在透明宿主上，用起来和原来一样 (Priority: P1) 🎯 MVP

**Goal**: 面板可见且没有其它界面时出现宿主；面板的渲染和鼠标输入只走界面事件；HUD 专用的第二套驱动全部删除；面板的所有现有交互在宿主上与原 HUD 一致。

**Independent Test**: quickstart 一（1–6）：按 B 面板出现、画面不变暗、不暂停、鼠标释放；W/A/S/D 与鼠标移动不动角色；再按 B 关闭；011～017 的面板验收项在宿主上与原来一致。（本阶段宿主还不转发 E 等按键，所以不可单独交付；US2 补上。）

- [X] T007 [US1] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelHostScreen.java`（`@OnlyIn(Dist.CLIENT)`，继承 `Screen`）：标题 `Component.empty()`；`isPauseScreen()` 返回 false；`shouldCloseOnEsc()` 返回 false；`renderBackground(...)` 覆盖为空操作（不变暗不模糊，FR-001）；`keyPressed`：ESC → `FloatingPanelEvents.closePanel()` 并返回 true；其余用 `HostKeyRouter.classify` 查出该键绑定的映射名（遍历 `Minecraft.options.keyMappings`，用 `KeyMapping.matches(key, scanCode)`），`TOGGLE`/`PASS` 返回 false，`SWALLOW` 返回 true，**`YIELD` 本阶段先返回 false（US2 再接上重放）**；`keyReleased`：被吞掉的键返回 true；`charTyped` 返回 false；`tick()`：面板不可见时 `HostScreenManager.ensure()`（让策略的 `CLOSE_HOST` 关掉自己）；`removed()`：调用 `FloatingPanelInput.resetPressTracking()`。依赖 T005
- [X] T008 [US1] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/HostScreenManager.java`：与游戏相连。`ensure()`：取 `Minecraft.screen` 并分类为 `ScreenKind`（`null` → NONE；`PanelHostScreen` → HOST；`ContainerScreenCompat.isOpenContainerScreen` → CONTAINER；其余 → OTHER），取玩家存活状态（`player == null` 或 `isDeadOrDying()` 视为不存活；无玩家/无世界时直接返回），调用 `HostScreenPolicy.decide` 并执行：`OPEN_HOST` → `minecraft.setScreen(new PanelHostScreen())`，`CLOSE_HOST` → `minecraft.setScreen(null)`，`CLEAR_PANEL` → `FloatingPanelEvents.closePanel()`。`setScreen(PanelHostScreen)` 全工程只出现在这里。依赖 T004、T007
- [X] T009 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`：①新增 `hostsPanel(Screen)`（宿主或容器界面），`allowsPanel(Screen)` 改为 `screen == null || hostsPanel(screen)`；②`onScreenRender`、`onScreenMousePressed`、`onScreenMouseReleased`、`onScreenMouseDragged`、`onScreenMouseScroll` 里的 `ContainerScreenCompat.isOpenContainerScreen(...)` 过滤改成 `hostsPanel(...)`（`QuickTransferInput.beginFromScreen` 自己判断 `supports(screen)`，宿主不会被它处理）；③**删除** `onRenderGui`、`onWorldMouseButton`、`onWorldMouseScroll`、`syncMouseToPanelVisibility`，以及 `onClientTick` 里对它的调用；`onScreenRender` 里"onRenderGui returns early"的注释改成现状；④新增 `@SubscribeEvent` 的 `RenderFrameEvent.Pre` 处理器，调用 `HostScreenManager.ensure()`；新增 `ClientTickEvent.Post` 的最低优先级处理器（`EventPriority.LOWEST`）调用 `ensure()`；⑤`closePanel()` 末尾把 `syncMouseToPanelVisibility()` 换成 `HostScreenManager.ensure()`；⑥`guiMouseX/Y` 保留给 `updateResizeCursor` 与拖动更新使用。依赖 T008
- [X] T010 [P] [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackSessionPayload.java` 与 `src/main/java/pnnh/mcfloatingbackpack/client/ClientBackpackActions.java`：把两处 `FloatingPanelEvents.syncMouseToPanelVisibility()` 换成 `HostScreenManager.ensure()`（会话打开/关闭、手持使用关闭路径）；不改任何网络包字段与协议版本。依赖 T008
- [X] T011 [US1] 更新 011/013/014 输入类里提到"HUD / onRenderGui"的注释使之与现状一致（只改注释，不改逻辑）：`src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferInput.java`（`activeScreen` 注释里的"null = HUD"）、`PanelDragInput.java`、`PanelSelectionInput.java`，以及 `FloatingPanelEvents` 里残留的相关注释；`grep -n "HUD\|onRenderGui" src/main/java` 逐处过一遍
- [X] T012 [US1] 运行 `./gradlew compileJava test`，确认全部测试通过；`grep -rn "RenderGuiEvent\|InputEvent.MouseButton\|isMouseGrabbed\|grabMouse\|releaseMouse" src/main/java` 不再命中 HUD 专用驱动（宿主相关的合理出现需逐处说明）
  - **结果**：`./gradlew compileJava test` 通过（28 个测试类、252 个用例）；`grep` 无 HUD 专用驱动命中
- [X] T013 [US1] 按 `quickstart.md` 一（1–6）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：宿主出现、画面不暗不糊、不暂停（30 秒世界继续）、W/A/S/D 不动角色、B 再按关闭、右键背包物品打开；再把 005～017 的面板交互在宿主上与改动前对照一遍；结果记到 `specs/019-fallback-host-screen/DELIVERY.md`（没做的项记"未验证"，**由用户确认后才打勾**）
  - **用户确认（2026-11-05）：宿主上的交互没有明显问题，A 键向左走与面板闪烁已修复（总体确认，未逐项记录；30 秒世界继续、右键背包物品打开、005～017 逐项对照没有单独记录）**

**Checkpoint**: US1 达成 FR-001、FR-002、FR-005、FR-006、FR-008（移动/转向失效）、FR-010、FR-011、FR-014（宿主上用热键开关）、SC-004、SC-005。

---

## Phase 4: User Story 2 - 面板开着时，仍能用 E 或其它按键打开别的界面，关闭后面板自动回来 (Priority: P1)

**Goal**: 宿主收到"让出"类按键时，同一 tick 内让出并重放，E、聊天、成就以及其它模组的按键一次生效；其它界面关闭后宿主自动恢复；被重放按键的残留点击不留幽灵触发。

**Independent Test**: quickstart 二（7–14）：面板开着，按 E 一次就开物品栏、面板挂上去、关闭后面板自动回来；聊天、成就、工作台、箱子同理；`runClientCompat` 下精妙背包快捷键；20 次连续开关不出错。

- [X] T014 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelHostScreen.java`：`YIELD` 的按键记入 `pendingReplay`（键值、扫描码、是否已松开）并返回 true；`keyReleased` 命中 `pendingReplay` 时标记已松开并返回 true；`tick()` 里若有 `pendingReplay`：取 `InputConstants.getKey(key, scanCode)`，`KeyMapping.set(k, true)` → `KeyMapping.click(k)`，若已松开再 `KeyMapping.set(k, false)`，把这个键绑定的映射集合交给 `HostScreenManager.rememberReplayed(...)`，清空 `pendingReplay`，然后 `minecraft.setScreen(null)`（**同一 tick 内**让出，使随后的 `handleKeybinds` 消费这次点击，见 research D3）；`removed()` 清空 `pendingReplay`。一次只处理一个待重放按键（同一 tick 内后来的 `YIELD` 键覆盖或排队均可，选覆盖并在代码注释里写明）
- [X] T015 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/HostScreenManager.java` 与 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`：`HostScreenManager` 新增 `rememberReplayed(Collection<KeyMapping>)` 与 `afterTick()`；`afterTick()` 在最低优先级的 `ClientTickEvent.Post` 里先执行——对被重放的映射反复 `consumeClick()` 直到为 false（清掉没被消费的残留点击，防止宿主恢复后"幽灵触发"，research D4 第 4 点），清空记录——再调用 `ensure()`；`FloatingPanelEvents` 的最低优先级处理器改为调用 `afterTick()`。依赖 T014
  - **实现修订（2026-11-05）**：残留点击的清理与宿主的回归改到**下一个 tick 的 `ClientTickEvent.Pre`（最低优先级）**（`HostScreenManager.beforeTick`），重放之后宿主一直离开到那时（`awayUntilNextTick`），以覆盖在 tick 开头才轮询按键的模组（旅行者背包）；`afterTick` 只做 `ensure`。见 DELIVERY.md 第二轮
- [X] T016 [US2] 运行 `./gradlew compileJava test`，确认全部测试通过；`grep -rn "setScreen(" src/main/java` 核对：`setScreen(new PanelHostScreen` 只在 `HostScreenManager`，`setScreen(null)` 只在 `HostScreenManager` 与宿主的让出处（契约"唯一性"一条）
  - **结果**：测试通过；`setScreen(` 只在 `HostScreenManager` 与宿主的 `replay()`
- [X] T017 [US2] 按 `quickstart.md` 二（7–11、13–14）在 `./gradlew runClient` 逐项验证：E 一次开物品栏并挂面板、再按 E/ESC 关后面板自动回来、聊天键与成就键、工作台与箱子上挂面板后关闭、20 次"E 开关 + 聊天开关"无失败、恢复耗时目测 ≤ 0.3 秒；**同时记录**让出/恢复时鼠标是否回到屏幕中心以及手感；结果记到 DELIVERY.md（**由用户确认后才打勾**）
  - **用户确认（2026-11-05）：E、T、F5、精妙背包与旅行者背包快捷键都正常，面板不再闪烁，交互没有明显问题（总体确认，未逐项记录成就键、工作台与箱子、20 次连续开关、恢复耗时）**
- [X] T018 [US2] 按 `quickstart.md` 二（12）在 `./gradlew runClientCompat` 验证：面板开着按精妙背包的"打开背包"快捷键，界面打开、面板挂上去、关闭后面板回来；旅行者背包同样抽查一次。**无效就如实记入已知限制，不要声称通过**；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **用户验证（2026-11-05）**：`runClientCompat` 下，面板开着用精妙背包与旅行者背包的快捷键都能打开其界面。遗留：重放时面板有一次约 1 秒的明显闪缩（见 DELIVERY.md 第三轮），不影响功能；旅行者背包的修复靠"宿主在重放之后离开到下一个 tick 的 Pre"这一通用机制

**Checkpoint**: US2 达成 FR-003、FR-004、FR-007、FR-009（容器界面上的行为不变，由 T017 对照）、SC-001、SC-002、SC-003（或登记为已知限制）。**US1 + US2 合起来才是可交付的 MVP。**

---

## Phase 5: User Story 3 - ESC 与非容器界面 (Priority: P2)

**Goal**: 非容器界面（聊天、暂停菜单等）把面板隐藏而不是关闭，可见状态保留；ESC 在宿主下关面板、在容器界面下先关容器；死亡与登出清除可见状态。

**Independent Test**: quickstart 三（15–18）：宿主下 ESC 只关面板不进暂停菜单；物品栏上 ESC 先关物品栏、再按才关面板；聊天关闭后面板回来；死亡/断线后面板不可见。

- [X] T019 [US3] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`：①删除 `hideIfDisallowed` 及其在 `onClientTick` 里的调用（不再因非托管界面而 `closePanel()`）；②`onScreenOpening` 只保留"容器界面 → 发 `MergePanelCursorPayload`"，**删除**"可见时暂停菜单被取消并关面板"与"可见时其它非容器界面 → 关面板"两段；③`onKey` 删除 ESC 分支（宿主已处理），开关热键逻辑不变（`allowsPanel` 已含宿主）；④`onClientTick` 里：面板可见但 `!hostsPanel(minecraft.screen)` 且 `state.isDragging()/isResizing()` 时调用 `state.endDrag()/endResize()`（保存配置与否沿用 `FloatingPanelInput.finishDrag/finishResize` 的做法，不要留"粘住鼠标"的状态）；⑤新增 `ClientPlayerNetworkEvent.LoggingOut` 处理：`state.setVisible(false)`、结束拖动、`ClientBackpackView.get().clear()`、重置 011/013/014 的输入状态（同 `onLoggingIn` 的清理）。依赖 T015
- [X] T020 [US3] 运行 `./gradlew compileJava test`，确认全部测试通过
  - **结果**：测试通过
- [X] T021 [US3] 按 `quickstart.md` 三（15–18）在 `./gradlew runClient` 逐项验证：宿主下 ESC 关面板且不开暂停菜单、再按 ESC 才开暂停菜单；物品栏上 ESC 先关物品栏、面板仍在、再按 ESC 关面板；聊天开关后面板回来、可见状态保留；死亡重生后与断线重进后面板不可见；第 17 条能触发的非容器界面都试，弹不出来的记"未验证"；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **用户确认（2026-11-05）：ESC 先关面板、聊天关闭后面板恢复，交互没有明显问题（总体确认；物品栏上 ESC 的两段行为、死亡/断线没有单独记录）**

**Checkpoint**: US3 达成 FR-012、FR-013、FR-015、SC-006（部分）。

---

## Phase 6: User Story 4 - 状态不丢、光标物品不丢 (Priority: P2)

**Goal**: 宿主与其它界面来回切换，面板状态不丢、进行中的手势安全取消、面板光标上的物品总数守恒。

**Independent Test**: quickstart 四（19–23）。

- [X] T022 [US4] 审阅（读代码，不改行为）宿主 ↔ 容器界面、宿主 ↔ 非容器界面切换时各手势的取消路径：`QuickTransferInput.stopIfInvalid`、`PanelDragInput.stopIfInvalid`、`PanelSelectionInput.stopIfInvalid` 都靠 `minecraft.screen != activeScreen` 取消——确认宿主让位/恢复时 `activeScreen` 与 `minecraft.screen` 的比较对新增的 `PanelHostScreen` 同样成立；确认取消时不发请求；确认 `PanelSelection.syncContext` 以"可见"为清空条件（隐藏期间选区保留）。发现缺口就在 `FloatingPanelEvents` 或对应输入类里补，并在 DELIVERY.md 记录；没有缺口也记"已审阅，无需改动"
  - **结果**：已审阅，无需改动：三个手势都用 `minecraft.screen != activeScreen` 取消，宿主每次都是新实例，宿主 ↔ 容器/其它界面切换必然取消，取消时不发请求；`PanelSelection.syncContext` 以"可见"为条件，隐藏期间选区保留
- [ ] T023 [US4] 按 `quickstart.md` 四（19–23）在 `./gradlew runClient` 逐项验证：面板光标上拿物品 + E / 聊天 / 关面板的物品总数；拖动均分、Ctrl 转出、选区拖选、面板拖动、缩放进行中按 E 或弹出聊天后无残留预览与"粘住鼠标"；宿主 ↔ 物品栏来回 10 次后位置/尺寸/滚动/选区不变；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **状态（2026-11-05）**：客户端验证**未做**，等用户确认后才打勾

**Checkpoint**: US4 达成 FR-016、FR-017、SC-006（物品守恒部分）。

---

## Phase 7: User Story 5 - 创造模式与按键状态 (Priority: P3)

**Goal**: 创造模式下宿主可用；按着移动键开关面板不卡键。

**Independent Test**: quickstart 五（24–26）。

- [ ] T024 [US5] 按 `quickstart.md` 五（24–26）在 `./gradlew runClient` 验证：创造模式没开界面时按 B 面板在宿主上可用；按 E 打开创造物品栏时面板的表现（沿用现状，**只记录观察**，未验证则写"未验证"）；按住 W 时按 B 开面板、松开 W、再关面板，角色不会自己继续走（SC-007）；结果记到 DELIVERY.md（由用户确认后才打勾）。若发现卡键，修复点在 `PanelHostScreen`/`HostScreenManager` 的按键释放处理，回到 US2 的任务补测试或观察
  - **状态（2026-11-05）**：客户端验证**未做**，等用户确认后才打勾

**Checkpoint**: US5 达成 FR-008（不卡键）、FR-018、SC-007。

---

## Phase 8: Polish & Cross-Cutting

- [X] T025 [P] 更新受影响的历史文档，把"无界面 HUD"改写成"没有容器界面时的宿主界面（019）"，已勾选的历史验证项不改：`specs/003-cross-gui-drag/`、`specs/007-floating-window-chrome/`、`specs/011-ctrl-quick-transfer/`、`specs/012-ctrl-transfer-to-open-container/`、`specs/013-panel-drag-split-gather/`、`specs/014-panel-selection/`、`specs/016-panel-tool-areas/` 里 spec/plan/quickstart 的相关描述，以及 `docs/MCFloatingBackpack.md`；先 `grep -rn "HUD\|没有打开任何界面\|无界面" specs/003* specs/007* specs/011* specs/012* specs/013* specs/014* specs/016* docs/MCFloatingBackpack.md` 列出清单，逐处改。**不要修改 `docs/ideas`**
- [X] T026 [P] 在 `specs/018-tag-manager/spec.md` 与 `specs/018-tag-manager/plan.md` 顶部背景里各加一段说明：018 依赖 019（宿主界面）；019 完成后 018 的 plan 需简化——去掉 `TagInputScreen` 的宪法例外与 Complexity Tracking 条目、去掉 HUD 键盘捕获与 `hostsPanel` 的特殊扩展；**只加说明，不在本 feature 里改 018 的设计**
- [ ] T027 按 `quickstart.md` 六（27–33）逐项观察并记录已知限制与未验证项：F1/F3 在宿主下是否生效、F2/F11 是否生效、窗口失焦是否弹暂停菜单、JEI 叠层在宿主上的表现、切换维度前后的面板状态、隐藏期间背包被取走时的收敛、关闭界面后鼠标是否回到屏幕中心及可否接受、功能键（如 F5）按一次时宿主是否闪烁；每项写观察到什么，没做的写"未验证"
  - **状态（2026-11-05）**：观察**未做**，等用户确认后才打勾
- [ ] T028 按 `quickstart.md` 七（34、36）回归与压力：重跑 011/012/013/014/016/017 的 quickstart 里"打开着界面"的场景；50 次随机开关（B、E、ESC、聊天混合）后检查无卡键、无残留宿主、物品守恒、`run/logs/latest.log` 无新增报错；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **状态（2026-11-05）**：回归与压力验证**未做**，等用户确认后才打勾
- [X] T029 静态检查（`quickstart.md` 七 35）：`grep -rn "RenderGuiEvent\|InputEvent.MouseButton\|isMouseGrabbed\|grabMouse\|releaseMouse" src/main/java`（无 HUD 专用驱动）；`grep -rn "setScreen(" src/main/java`（唯一性）；`git diff --stat` 确认没有改动 `QuickTransferInput/Gesture/Rules` 的逻辑（只允许 T011 的注释改动）、没有改动任何网络包与 `BackpackNetwork` 的协议版本（仍为 `"7"`）；`grep -rn "p3pp3rf1y\|tiviacz" src/main/java/pnnh/mcfloatingbackpack/client/panel/Host*` 无命中（宪法 IV）；结果记到 DELIVERY.md
  - **结果**：见 DELIVERY.md 静态检查一节，全部符合
- [ ] T030 启动检查（`quickstart.md` 七 37）：先 `pgrep` 确认没有运行中的客户端，能进入世界、日志无报错；本 feature 不新增资源，宪法 VI 不适用，无需度量图集（在 DELIVERY.md 里写明"不适用"，不要声称已度量）
  - **状态（2026-11-05）**：启动检查**未做**，等用户确认后才打勾
- [X] T031 新建 `specs/019-fallback-host-screen/DELIVERY.md`（中文）：汇总自动化结果（T001 基线与 T006/T012/T016/T020 的编译与测试结果、新增测试用例数）、与 plan/tasks 的偏离、行为变化（HUD 模式取消：宿主在场期间世界交互失效；非容器界面不再关闭面板而是隐藏；ESC 行为）、**每个客户端验证项实际验证了什么 / 未验证什么**、已知限制（F1/F3、不经 `consumeClick` 的模组按键、JEI/EMI、Windows/Linux 未验证）、对 018 的后续影响

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1（Setup）**：无依赖，先做。
- **Phase 2（Foundational）**：依赖 Phase 1；T002/T003 可并行写测试，T004/T005 可并行实现；**阻塞所有用户故事**。
- **Phase 3（US1）**：依赖 Phase 2；内部顺序 T007 → T008 → T009，T010、T011 可在 T008 后并行，T012 → T013。
- **Phase 4（US2）**：依赖 US1（宿主与管理器已存在）；T014 → T015 → T016 → T017/T018。**US1 与 US2 都完成才能交给用户当 MVP 使用**（只做 US1 时宿主会吞掉 E）。
- **Phase 5（US3）**：依赖 US2（共用 `FloatingPanelEvents` 与管理器）；T019 → T020 → T021。
- **Phase 6（US4）**：T022 依赖 US1～US3 的代码已完成；T023 为验证。
- **Phase 7（US5）**：只有验证任务，依赖 US2。
- **Phase 8（Polish）**：T025、T026 只改文档，可在 US1 稳定后并行进行；T027～T031 在所有故事完成后。

### User Story Dependencies

- US1 与 US2 构成一个不可拆的交付单元（宿主 + 不挡按键）。
- US3、US4、US5 都在其上，彼此独立，可按 P2 → P3 顺序做。

### Parallel Opportunities

- Phase 2：T002 ∥ T003（不同测试文件）；T004 ∥ T005（不同源文件，各自对应一个测试）。
- Phase 3：T010 ∥ T011（不同文件），均在 T008 之后。
- Phase 8：T025 ∥ T026。

### Parallel Example: Phase 2

```text
同时写：T002 HostScreenPolicyTest   T003 HostKeyRouterTest
同时实现：T004 HostScreenPolicy      T005 HostKeyRouter
然后：T006 运行测试
```

---

## Implementation Strategy

### MVP First（US1 + US2）

1. 完成 Phase 1、Phase 2（纯逻辑 + 测试，不碰游戏）。
2. 完成 Phase 3（US1）：宿主出现，删掉 HUD 驱动。此时可在游戏里看到宿主，但按 E 还打不开物品栏。
3. 完成 Phase 4（US2）：补上按键转发。到这里才是可交付的 MVP。
4. 用户在游戏里验证 T013、T017、T018 后再继续。

### Incremental Delivery

1. MVP（US1 + US2）→ 用户验证。
2. US3（ESC 与非容器界面隐藏）→ 验证。
3. US4（审阅 + 验证状态与光标物品）→ 验证。
4. US5（创造模式与按键状态）→ 验证。
5. Polish：文档、已知限制观察、回归、交付说明。

### 风险提示（实现时盯住）

- **让位的完整性**：宿主只能在 `screen == null` 时被打开；任何其它 `setScreen` 调用都不得绕过 `HostScreenManager`（T016、T029 的 `grep`）。
- **让出再重放的时序**：必须在宿主的 `tick()` 里让出，不能在按键回调里；否则按键回调与下一个 tick 之间可能插进一个无界面的渲染帧（research D3）。
- **残留点击**：`afterTick()` 必须在 `ensure()` 之前清掉被重放映射的残留点击（research D4）。
- **鼠标位置**：每次让出/恢复鼠标回中心，手感需在 T017 里由用户确认可否接受。
