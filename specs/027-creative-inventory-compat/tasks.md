# Tasks: 创造物品栏界面兼容

**Input**: Design documents from `/specs/027-creative-inventory-compat/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/creative-cursor.md, quickstart.md；宪法 2.2.0；011～026 已实现（026 的客户端验证项 T043、T047、T049、T052 仍待用户确认，与本 feature 无依赖）

**Tests**: 光标报告的判定（`CreativeCursorRules`）与光标记账（`CursorMirror`）是不依赖 Minecraft 运行环境的纯逻辑，用单元测试覆盖，**测试任务排在对应实现之前**；网络收发、客户端输入钩子、创造界面里的实际光标表现、关界面守恒、旧栈覆盖的复现只能在客户端会话里验证（宪法 V 条），已作为任务列出

**Organization**: Phase 2 是阻塞所有故事的基础（两个纯类、两个网络包与协议版本、服务端桥接、回写钩子）。客户端报告（Phase 3）一次交付 US1～US3 三个故事（同一个镜像机制），随后按故事分别验证。US4（关界面守恒）几乎不需要新代码，靠服务端原版逻辑，只做审阅与验证。US5（旧栈覆盖）是**条件阶段**：先复现，复现了才做同步包（FR-009）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US6）
- 路径都相对于仓库根目录；下文路径里的 `…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…/creative/` 表示 `…/inventory/creative/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑 `check_atlas_budget.sh` 前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**，有就不要启动第二个，也不要杀用户的游戏
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过；联机验证需要两台设备，没有条件就写"未验证"
- 包名前缀 `pnnh.mcfloatingbackpack`；测试类路径里只有 JUnit（没有 Minecraft/Netty），所以纯类只用 JDK 类型与泛型
- **不新增命令、依赖、贴图、设置项**；**不引入任何"创造模式下限制面板"的逻辑**（FR-014）；011 的 Ctrl 快速转移排除保持原样（FR-015）；现有 7 处服务端光标处理的代码与协议不改（FR-013）

---

## Phase 1: Setup

- [x] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码、测试类数量与用例数（026 之后为 87 个类、637 个用例）；结果记到 `specs/027-creative-inventory-compat/DELIVERY.md`（新建，中文）

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 纯规则、网络包与协议版本、服务端桥接与回写钩子。测试先写、先失败。

- [x] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/creative/CreativeCursorRulesTest.java`（**先写，先失败**），按 data-model.md 第 1 节与 research D5：非创造 → `NOT_CREATIVE`（即使栈也非法，先判这一条）；玩家开着别的菜单（`inventoryMenuOpen` 假）→ `OTHER_MENU`；空栈（`empty` 真）在创造且开着自己菜单时永远 `ACCEPT`；`count > maxStack` 或 `count < 0` → `BAD_COUNT`；物品被禁用 → `DISABLED_ITEM`；数量刚好等于 `maxStack` 为 `ACCEPT`；最大堆叠为 1（工具）、16（末影珍珠）、64 各取一个数量验证；判定顺序即上面的顺序（构造同时命中多条的输入，确认返回第一条）
- [x] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/creative/CursorMirrorTest.java`（**先写，先失败**），按 data-model.md 第 2 节，用 `String` 当 T：初始 `known` 为空；`shouldSend(current)` 在 `current` 与 `known` 不同时为真、相同时为假；`sent(x)` 之后 `shouldSend(x)` 为假；`received(x)` 同理（**不回声**：收到对端的值后，本端同值不再发）；`reset()` 回到空；`sent` / `received` 存的是拷贝（改原值不影响 `known`）；两个实例模拟一次完整往返（A 发 → B received → B 的 `shouldSend` 同值为假 → B 改值后发 → A received），全程每个值只传一次
- [x] T004 [P] 新建 `…/creative/CreativeCursorRules.java`：纯逻辑，`enum Verdict { ACCEPT, NOT_CREATIVE, OTHER_MENU, BAD_COUNT, DISABLED_ITEM }`，`check(boolean creative, boolean inventoryMenuOpen, boolean empty, int count, int maxStack, boolean itemEnabled)`，顺序照 research D5。使 T002 通过
- [x] T005 [P] 新建 `…/creative/CursorMirror.java`：纯逻辑、泛型 `CursorMirror<T>(BiPredicate<T,T> same, UnaryOperator<T> copy, T empty)`，方法 `known()`、`shouldSend(T)`、`sent(T)`、`received(T)`、`reset()`，照 data-model.md 第 2 节。使 T003 通过
- [x] T006 新建 `…/network/CreativeCursorReportPayload.java`（客户端 → 服务端，字段 `ItemStack cursor`，`ItemStack.OPTIONAL_STREAM_CODEC`）与 `…/network/CreativeCursorPushPayload.java`（服务端 → 客户端，同字段）；修改 `…/network/BackpackNetwork.java`：`versioned("14")` 改成 `"15"`，注册两个包（`playToServer` 与 `playToClient`），处理器分别转给 `CreativeCursorBridge.onReport` 与 `CreativeCursorClient.onPush`（`enqueueWork` 里调用，与现有 `BackpackSlotUpdatePayload` 同写法）。依赖 T007（桥接类先建空壳）与 T011（客户端类先建空壳）可以在同一提交里一起建
- [x] T007 新建 `…/creative/CreativeCursorBridge.java`（服务端，主线程）：`Map<UUID, CursorMirror<ItemStack>>`（`same` 用 `ItemStack::matches`，`copy` 用 `ItemStack::copy`，`empty` 用 `ItemStack.EMPTY`）；`onReport(ServerPlayer, ItemStack)`：用 `CreativeCursorRules.check(player.isCreative(), player.containerMenu == player.inventoryMenu, stack.isEmpty(), stack.getCount(), stack.getMaxStackSize(), stack.isItemEnabled(player.level().enabledFeatures()))`，`ACCEPT` 时 `player.inventoryMenu.setCarried(stack.copy())` 并 `mirror.received(stack)`，其它 `Verdict` 不改任何东西、记一行 DEBUG 日志（玩家名与原因）；`pushIfChanged(ServerPlayer)`：仅当玩家是创造模式且 `containerMenu == inventoryMenu` 时，`computeIfAbsent` 取镜像，`shouldSend(menu.getCarried())` 为真才 `PacketDistributor.sendToPlayer` 一个 `CreativeCursorPushPayload` 并 `sent`；`onClose(ServerPlayer)` 与 `onLogout(ServerPlayer)`：`mirror.reset()` / 移除。javadoc 写明：服务端的菜单光标在创造界面期间镜像客户端；关界面时由原版 `AbstractContainerMenu.removed` 放回物品栏（research D4）
- [x] T008 修改 `…/network/BackpackNetwork.java` 的 `sendTrash`、`sendFullSync`、`sendSlotUpdate`（两个重载）：在各自末尾调 `CreativeCursorBridge.pushIfChanged(player)`（research D3：所有光标处理最后都走这几个方法；不改光标的调用方得到的是空操作）。同时 `ref == null || !ref.stillValid(player)` 提前返回的那条路径也要先 push（面板被关闭时光标结果仍要回写）
- [x] T009 修改 `…/MCFloatingBackpack.java`：注册服务端事件 `PlayerContainerEvent.Close`（仅当 `event.getContainer() == event.getEntity().inventoryMenu` 且是 `ServerPlayer` 时调 `CreativeCursorBridge.onClose`）与 `PlayerEvent.PlayerLoggedOutEvent`（调 `onLogout`），与现有监听并排
- [x] T010 运行 `./gradlew compileJava test`，确认 T002、T003 与全部既有测试通过

**Checkpoint**: 纯规则与服务端桥接就绪；客户端还没有报告光标，所以行为与之前相同。

---

## Phase 3: User Story 1/2/3 - 创造界面里面板的光标操作与生存界面一致 (Priority: P1) 🎯 MVP

**Goal**: 创造界面（两种标签页）打开且面板可见时，客户端把创造光标报告给服务端并接收回写，面板的放入、拿起、拖动、聚拢、垃圾桶都与生存界面一致。

**Independent Test**: 创造模式、打开创造界面，拿起一组物品点面板格子放入、从面板拿起再放进物品栏格子；与生存物品栏界面里的同一操作对照。

- [x] T011 [US1] 新建 `…/client/creative/CreativeCursorClient.java`（`@OnlyIn(Dist.CLIENT)`）：持有一个 `CursorMirror<ItemStack>`；`flush()`：仅当 `Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen`、`FloatingPanelState.get().isVisible()`、`player.isCreative()` 且 `mirror.shouldSend(player.inventoryMenu.getCarried())` 时，发 `CreativeCursorReportPayload` 并 `sent`；`onPush(ItemStack)`：当前界面仍是创造界面时 `player.inventoryMenu.setCarried(stack.copy())` 并 `received`，否则丢弃；`reset()`；`onOpening(Screen neu)`：创造界面即将打开时 `reset()` 并在 `MergePanelCursorPayload` 之前 `flush`（见 T012）。javadoc 写明 research D1、D2
- [x] T012 [US1] 修改 `…/client/FloatingPanelEvents.java`：在 `onScreenMousePressed`、`onScreenMouseReleased`、`onScreenKeyPressed`（`Pre`）的**最开头**（早于面板自己的消费与取消）调 `CreativeCursorClient.flush()`；在 `onScreenOpening` 里，创造界面打开时先 `CreativeCursorClient.reset()` 再 `flush()`，然后才发现有的 `MergePanelCursorPayload`（服务端按镜像光标合并，回写由 T008 的钩子送回）；在界面关闭处（`ScreenEvent.Closing` 或现有的关闭处理）调 `CreativeCursorClient.reset()`。**不要**吞掉或改变任何现有的事件取消逻辑
- [x] T013 [US1] 修改三处客户端发请求点，在发包**之前**显式调 `CreativeCursorClient.flush()`（事件钩子之外的保险，防止未来有绕过事件的发送）：`…/client/panel/FloatingPanelInput.java` 的 `sendClick`（两个重载）、`…/client/panel/PanelDragInput.java` 发 `BackpackCursorGesturePayload` 的位置、`…/client/panel/trash/TrashPanelInput.java` 发 `BackpackTrashPayload` 的位置。用 `grep "sendToServer"` 逐个确认没有漏掉依赖光标的请求
- [x] T014 [US1] 运行 `./gradlew compileJava test`，确认全部测试通过
- [x] T015 [US1] 按 `quickstart.md` A（A1～A5）在 `./gradlew runClient` 验证（先 `pgrep`）：创造界面物品栏标签页与物品标签页下，光标物品放入面板（整组、合并、交换、右键一个）；从创造物品列表里取的物品放入，带数据的物品放入后数据完整；功能区拒绝时光标与背包不变；每一项与生存界面对照；结果记到 DELIVERY.md（由用户确认后才打勾）
- [x] T016 [US2] [US3] 按 `quickstart.md` B（B1～B5）在 `./gradlew runClient` 验证（先 `pgrep`）：从面板拿起再放回物品栏格子数量守恒、右键拿一半；按住拖动平均分配与右键每格一个；双击聚拢；垃圾桶放入与取回；每一项与生存界面对照；结果记到 DELIVERY.md（由用户确认后才打勾）

**Checkpoint**: US1～US3 完成，到这里先让用户验证 T015、T016 再继续。

---

## Phase 4: User Story 4 - 关界面时不丢东西 (Priority: P1)

**Goal**: 面板里拿起、此刻在光标上的物品，关闭创造界面时不凭空消失、不被复制。

- [x] T017 [US4] 审阅（读代码，不改行为）：①`KeyPressed.Pre` 的 `flush()` 早于界面关闭，所以最后一次光标在关闭包之前送达（research F8）；②服务端 `PlayerContainerEvent.Close` 之前，原版 `AbstractContainerMenu.removed` 已把镜像光标放回物品栏并清空，之后 `onClose` 只 `reset`，不二次放回；③界面关闭后客户端 `reset`，下次打开从空开始；④面板不可见时没有任何报告，所以关界面行为与原版完全一致；发现缺口就补并记录，没有缺口也记"已审阅，无缺口"
- [x] T018 [US4] 按 `quickstart.md` C（C1～C4）在 `./gradlew runClient` 验证（先 `pgrep`）：从面板拿起带名字、附魔的物品，不放下，用 E、ESC、切到别的界面三种方式各关闭一次，物品回到物品栏（放不下掉落），数量与附加数据不变；**C3：光标上是创造物品列表里取的虚拟物品、面板可见时关闭，记录是否被放回物品栏，请用户确认这个偏差（research D4）是否可接受**；面板隐藏时关闭与原版一致；退出重进没有多出或复制；结果记到 DELIVERY.md（由用户确认后才打勾）
    放回物品栏可以接受

---

## Phase 5: User Story 6 - 服务端权威，原版其它界面不变 (Priority: P2)

**Goal**: 只有创造模式玩家的报告被接受并校验；生存模式、箱子、宿主界面行为与协议不变。

- [x] T019 [US6] 审阅（读代码，不改行为）：①`CreativeCursorBridge.onReport` 的每个非 `ACCEPT` 分支都不改菜单光标、不更新镜像、不回写；②`pushIfChanged` 对非创造玩家、`containerMenu != inventoryMenu` 的玩家永远不发；③所有对背包的修改仍走 `BackpackGate.acquire`（`git diff` 确认 7 处服务端光标处理文件**没有改动**）；④没有任何地方根据游戏模式或界面类型禁用面板的功能（`grep -rn "isCreative\|CreativeModeInventoryScreen" src/main/java` 只应命中本 feature 的桥接与客户端类、011 的 `QuickTransferRules` 与 `CreativeCursorClient`）；发现缺口就补并记录，没有缺口也记"已审阅，无缺口"
- [x] T020 [US6] 按 `quickstart.md` D、G1 在 `./gradlew runClient` 验证（先 `pgrep`）：切到生存模式，生存物品栏界面与箱子界面下重跑 011～026 的验证项，结果与之前一致；非法报告靠 T002 的单元测试覆盖，客户端会话里不手工构造；结果记到 DELIVERY.md（由用户确认后才打勾）
    没有问题

---

## Phase 6: User Story 5 - 旧栈覆盖新栈：先复现，再决定 (Priority: P2，条件阶段)

**Goal**: 在创造界面里用面板改背包，背包内容与 026 的编号、修订号不被客户端旧栈覆盖。**机制只是推断（research D7），复现之前不做改动（FR-009）。**

- [x] T021 [US5] 按 `quickstart.md` E（E1～E5）在 `./gradlew runClient` 验证（先 `pgrep`）：背包放在物品栏中间、胸甲位、快捷栏三处，创造界面的物品标签页与物品栏标签页下各做一遍"用面板放入物品 → 在创造界面里把那只背包本身拿起再放下 → 关界面 → 重开面板"；每一步之后读 `run/saves/<世界>/data/mcfloatingbackpack_registry.dat` 与 `playerdata`（026 用过的脚本）对账**背包内容**（2026-11-09：026 已没有修订号，只看内容有没有回退，以及物品栏里有没有同一只背包的多份副本）；**结果只有两种：复现了（写下在哪种位置/标签页下复现、修订号如何变化）或没复现（写"未复现"）**；记到 DELIVERY.md（由用户确认后才打勾）
    未复现
- [ ] T022 [US5] **（跳过：T021 用户验证没有问题，旧栈覆盖未复现；不勾选为已实现）** 新增 `…/network/BackpackStackSyncPayload.java`（服务端 → 客户端，字段 `int inventorySlot`、`ItemStack stack`），处理器在客户端把 `player.getInventory().setItem(inventorySlot, stack)`；在 `BackpackRef.saveInventory` 末尾（仅创造模式玩家、仅服务端）发给该玩家，绕过原版"创造物品标签页忽略非快捷栏格子更新"（research F7）；协议版本是否再升由实现时判断（与 T006 的 `"15"` 同一次发布则不再升）；先写测试（槽号映射的纯逻辑部分），再实现
    未复现跳过
- [ ] T023 [US5] **（跳过：T022 没有做）** 按 `quickstart.md` E 重做 T021 的全部场景，确认背包内容 0 次回退、；结果记到 DELIVERY.md（由用户确认后才打勾）
    未复现跳过

---

## Phase 7: Polish & Cross-Cutting

- [x] T024 [P] 更新文档：`specs/011-ctrl-quick-transfer/spec.md` 与 `specs/013-panel-drag-split-gather/spec.md` 各加一句 027 说明（创造界面里面板的光标操作由 027 修复；Ctrl 快速转移在创造界面里的缺口仍在，线索见 027 research D8）；`docs/MCFloatingBackpack.md` 在 026 说明之后加 027 说明（创造界面里面板与生存界面一致、服务端菜单光标镜像客户端的创造光标、仅创造模式玩家被接受、关界面光标放回物品栏及其与原版创造模式的偏差、协议 15）；`docs/plan/manual-verification-checklist.md` 补 027 的验证项；**不要修改 `docs/ideas`**
- [x] T025 按 `quickstart.md` G2：`./gradlew compileJava test` 退出码 0；先 `pgrep` 确认没有运行中的客户端，再跑 `design/demo/tools/check_atlas_budget.sh`，图集应仍为 2048×1024（宪法 VI；本 feature 不新增贴图），结果记到 DELIVERY.md——**跑不了就如实写"未度量"，不要写成通过**
- [x] T026 按 `quickstart.md` G3 做成本粗测（宪法 VI）：面板可见且在创造界面里连续操作 1 分钟，对比有无本 feature 的客户端帧率；面板不可见或非创造界面时应当零成本（审阅确认）；测不了就如实写"未测量"
    帧率变化不大
- [x] T027 静态检查：`git diff --stat` 确认除"本 feature 新增文件、`BackpackNetwork`、`FloatingPanelEvents`、三处客户端发送点、`MCFloatingBackpack`、文档"外，011～026 的逻辑文件没有改动；`grep -rn "isCreative" src/main/java` 命中范围见 T019；确认没有新增设置项、贴图、命令
- [x] T028 新建/补全 `specs/027-creative-inventory-compat/DELIVERY.md`（中文）：汇总自动化结果（T001 基线与各阶段编译与测试结果、新增测试类与用例数）、与 plan/tasks 的偏离（**含 research D4 的关界面偏差及用户的确认结论**）、**每个客户端验证项实际验证了什么 / 未验证什么**（尤其联机延迟、旧栈覆盖是否复现、关界面虚拟物品的去向）、图集度量结果或"未度量"、成本粗测结果或"未测量"、已知限制（Ctrl 快速转移在创造界面的缺口及 research D8 线索；Windows/Linux 未验证；断线时镜像光标上物品的去向待验证 U7）

---

## Dependencies & Execution Order

### Phase 依赖

- **Phase 1（T001）** → **Phase 2（T002～T010）** → **Phase 3（T011～T016）** → Phase 4（US4）、Phase 5（US6）、Phase 6（US5，条件）→ Phase 7（Polish）。
- Phase 4～6 都依赖 Phase 3 的实现；它们之间互相独立，可以按任意顺序做；T021 的复现结果决定 T022、T023 做不做。

### 任务依赖（关键）

- T002、T003 先写（先失败），T004 使 T002 通过，T005 使 T003 通过；T004、T005 互相独立（`[P]`）。
- T006 依赖 T007（桥接类）与 T011（客户端类）至少有空壳；建议 T006、T007、T011 的类文件一起建，T010 之前整体编译通过。
- T008 依赖 T007；T009 依赖 T007；T010 依赖 T002～T009。
- T012、T013 依赖 T011；T014 依赖 T011～T013；T015、T016 依赖 T014。

### Parallel Opportunities

- T002 与 T003（两个测试文件）；T004 与 T005（两个纯类）；T024（文档）可以与 Phase 4～6 并行。

---

## Implementation Strategy

### MVP First（US1 → US2 → US3 → US4）

1. 完成 Phase 1、Phase 2（纯规则、网络包与协议版本、服务端桥接与回写钩子；此时客户端没有报告，行为不变）。
2. 完成 Phase 3：客户端报告与回写。**到这里先让用户验证 T015、T016 再继续**。
3. Phase 4：审阅关界面守恒，用户验证 T018，并确认 research D4 的偏差。
4. Phase 5：审阅服务端权威，回归验证 T020。

### Incremental Delivery

1. MVP（US1～US4）→ 用户验证。
2. US6（权威与回归）→ 验证。
3. US5（旧栈覆盖）：先复现（T021）；复现了才做 T022、T023；没复现就记"未复现"。
4. Polish：文档、图集度量、成本粗测、静态检查、交付说明。

### 风险提示（实现时盯住）

- **关界面的偏差**（T017、T018）：面板可见时，创造物品列表里取的虚拟物品也会被放回物品栏，原版创造模式是丢弃；必须让用户确认（research D4）。
    可以接受
- **报告早于关闭包**（T012、T017）：`KeyPressed.Pre` 的 `flush()` 必须在界面处理 E/ESC 之前；否则最后一次光标晚于关闭包到达，服务端 `removed` 看到的是旧光标。
- **不回声**（T003、T007、T011）：`sent`/`received` 两端都要调，否则会出现服务端回写 → 客户端报告 → 服务端回写的循环。
- **打开创造界面时的合并**（T012）：`onScreenOpening` 里现有的 `MergePanelCursorPayload` 之前必须先 `reset` 与 `flush`，否则服务端镜像光标与客户端不一致，合并的结果回写不回去。
- **不绕过 026**（T019）：对背包内容的所有修改仍先过 `BackpackGate`；本 feature 不改那 7 处服务端处理。
- **不回避原版问题**（T019）：不得出现根据游戏模式或界面类型禁用面板功能的代码。
- **旧栈覆盖只是推断**（T021）：复现之前不做 T022；复现不了就如实写"未复现"。
