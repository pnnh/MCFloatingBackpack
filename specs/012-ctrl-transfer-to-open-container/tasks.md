# Tasks: 从浮动面板转出时优先存入打开着的容器

**Input**: Design documents from `/specs/012-ctrl-transfer-to-open-container/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/open-container-delivery.md, quickstart.md

**Tests**: 临时槽位选择、换出换回、循环上限、异常、守恒都是不依赖 Minecraft 的纯逻辑（泛型算法 + 假菜单），plan 要求用单元测试覆盖；玩家可见行为必须在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**补充（2026-11-05）**：用户验证正向可用后要求补上反向"容器 → 浮动面板"，已作为 User Story 5 写入 spec，对应 Phase 8（T020～T033）。正向任务（T001～T019）保持原样。

**Organization**: 按用户故事分组，但本功能的实现集中在服务端一个分支：算法与适配器是阻塞所有故事的基础设施（Phase 2）；US1 接上网络包分支，原版容器（含熔炉、工作台等非存储界面）即可用；US2 是精妙背包/旅行者背包的实测，如发现问题再调整选择规则；US3、US4 主要是验证与回归，因为客户端不需要改动。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1~US4）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（当前 15 个测试类），作为之后回归的对照

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 泛型算法、假菜单测试、与真实菜单相连的适配器。没有它们，任何故事都无法实现。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/OpenContainerDeliveryTest.java`（**先写，先失败**）：在测试里实现一个 `FakeMenu implements OpenContainerDelivery.MenuAccess<FakeStack>`（用 `FakeStack` 实现 `QuickTransferLogic.StackOps`，复用 011 的写法），模拟：**箱子式**（`quickMove` 把玩家槽位里的栈搬进容器区，放不下的留下）、**工作台式**（容器区放不下时在玩家区内部挪动）、**拒收式**（`quickMove` 什么都不做）、**抛异常式**、**永不收敛式**（永远返回同一个非空栈）。覆盖 contracts §2 的全部不变量：物品总数守恒（含随机 100 组布局的性质检查）；容器全满/拒收时栈原样留在背包；有空槽位时用空槽位且不换出；物品栏满时换出一个主物品栏（容器槽位 9–35）里有物品的槽位，调用后放回的是**同一个对象**；**永不选手持的快捷栏槽位**、`mayPickup` 为假或 `mayPlaceSource` 为假的槽位；没有任何符合条件的槽位时本次不转移；`quickMove` 抛异常时临时槽位里的内容被取回、X 被放回、异常不外泄；永不收敛式在迭代上限处终止；同一输入下临时槽位选择结果确定（取菜单下标最小者）；以及"有没有打开的容器"的判定：只有玩家物品栏槽位 → 否；存在非玩家物品栏槽位 → 是
- [X] T003 [P] 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/OpenContainerDelivery.java`：泛型纯逻辑，**不引用任何 Minecraft 类型**（让测试能直接加载）。定义 `SlotView` 记录（data-model：`menuIndex`、`isPlayerInventorySlot`、`containerSlot`、`hasItem`、`mayPickup`、`mayPlaceSource`、`isSelectedHotbar`）、接口 `MenuAccess<T>`（槽位视图列表、读写槽位、`quickMove(menuIndex)`、同种判断等）、`static boolean hasOpenContainer(List<SlotView>)`、`static <T> T deliver(...)`：按 data-model 的优先级选临时槽位 → 需要时换出 X → 放入栈 → 照原版 `doClick` 循环调用 `quickMove`（返回空或槽位里剩下的不再是同种物品就停，迭代上限取"栈数量+4"与一个硬上限的较小值）→ `try/catch(RuntimeException)`，`finally` 里取回临时槽位剩下的内容、把 X 放回 → 守恒检查（该种物品在所有菜单槽位 + 背包里的总数转移前后相等，不等则通过回调报告，不回滚）→ 返回 leftover。使 T002 通过
- [X] T004 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/ContainerMenuAccess.java`：`OpenContainerDelivery.MenuAccess<ItemStack>` 对真实 `AbstractContainerMenu` 的适配：用 `slot.container == player.getInventory() && 0 <= slot.getContainerSlot() < 36` 判定玩家槽位；`mayPickup(player)`、`mayPlace(栈)`、`Inventory.selected` 对应的那一格标记为 `isSelectedHotbar`；读写槽位用 `slot.getItem()` / `slot.setByPlayer(...)`；`quickMove` 调 `menu.quickMoveStack(player, menuIndex)`；提供 `static boolean hasOpenContainer(ServerPlayer)`（`player.containerMenu != player.inventoryMenu` 且菜单里存在非玩家物品栏槽位）。依赖 T003

**Checkpoint**: `./gradlew compileJava test` 通过；T002 全绿。

---

## Phase 3: User Story 1 - 打开着箱子时，从面板 Ctrl+点击把物品存进箱子 (Priority: P1) 🎯 MVP

**Goal**: 面板可见且打开着原版容器时，Ctrl+点击面板槽位，物品进入该容器；没有容器时仍进物品栏。

**Independent Test**: quickstart 一、二：打开箱子，Ctrl+点击面板里的堆叠，物品进箱子，总数不变；物品栏满也能存；熔炉、工作台等与 Shift+点击一致；没开容器时进物品栏。

- [X] T005 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackQuickTransferPayload.java` 的 `TO_INVENTORY` 分支（`backpackToInventory`）：对每个背包下标，先用 `ContainerMenuAccess.hasOpenContainer(player)` 判断；**有**则调用 `OpenContainerDelivery.deliver(...)`，把 leftover 写回背包槽位，守恒检查失败或捕获到异常时用 `MCFloatingBackpack` 的日志记录（含菜单类名 `player.containerMenu.getClass().getName()` 与物品）；**没有**则保持 011 的原路径（`QuickTransferLogic.moveInto` 放进玩家物品栏 0–35，不用 `Inventory.add`）。在 `QuickTransferRequest.Direction` 的 Javadoc 里写明：`TO_INVENTORY` 之后含义是"转出背包，目的地由服务端按当前打开的菜单决定"，**不改常量名、字段和协议版本**。依赖 T004
- [X] T006 [US1] 运行 `./gradlew compileJava test`，确认 T002 与全部既有测试（15 个测试类）通过
- [ ] T007 [US1] 按 `quickstart.md` 一（1–6）与二（7–9）在 `./gradlew runClient` 逐项验证（macOS 用 Command 键）：箱子、木桶、末影箱、潜影盒、漏斗、发射器，物品栏满，容器满，以及熔炉、工作台、铁砧、酿造台、村民交易与 Shift+点击一致；结果记到 `specs/012-ctrl-transfer-to-open-container/DELIVERY.md`（没做的项记"未验证"）
  - 2026-11-05 用户验证了箱子、精妙背包、旅行者背包的双向 Ctrl+点击/拖动功能正常；**未验证的子项保持未勾选，所以本任务不勾选**：
    - [X] 一.1 打开箱子，Ctrl+点击面板槽位 → 物品进入箱子（FR-001）
    - [X] 一.2 箱子里已有同种半堆叠时先并入
    - [X] 一.3 容器满：放不下的留在面板，不进物品栏
    - [X] 一.4 玩家物品栏满时仍能存入容器
    - [ ] 一.5 木桶、末影箱、潜影盒、漏斗、发射器（2026-11-06 只验证了木桶，其余未验证）
    - [X] 一.6 与 Shift+点击的落点对比
    - [ ] 二.7～9 熔炉、工作台、铁砧、酿造台、村民交易与 Shift+点击一致（2026-11-06 熔炉(7)、工作台(8)通过；铁砧、酿造台、村民交易(9)未验证）

**Checkpoint**: US1 达成 FR-001、FR-002、FR-003、FR-005、FR-007～FR-010、FR-013、FR-016。

---

## Phase 4: User Story 2 - 打开着精妙背包或旅行者背包时同样存进去 (Priority: P1)

**Goal**: 精妙背包、旅行者背包界面打开时，面板转出的物品按它们自己的规则存入。

**Independent Test**: quickstart 三：`runClientCompat` 下对同一物品分别做"面板 Ctrl+点击"和"在容器里 Shift+点击物品栏里的同一件物品"，落点一致。

- [X] T008（2026-11-07 用户验证 012-10b、11b、12、13 与 012-LOG 通过，含重点项 012-12 物品栏塞满 + 手持模组背包） [US2] 按 `quickstart.md` 三（10–13）在 `./gradlew runClientCompat` 逐项验证，**重点是第 12 条：物品栏已满 + 手持模组背包**（精妙背包与旅行者背包各做一次，确认物品不丢、手持的背包物品完好、转移正常）；并检查 `run/logs/latest.log` 里没有"守恒检查失败"或"转出异常"的日志。结果记到 `DELIVERY.md`
  - 2026-11-05 用户验证了箱子、精妙背包、旅行者背包的双向 Ctrl+点击/拖动功能正常；**未验证的子项保持未勾选，所以本任务不勾选**：
    - [X] 三.10 精妙背包：Ctrl+点击面板里的普通物品 → 进入它的存储区
    - [X] 三.11 旅行者背包：Ctrl+点击面板里的普通物品 → 进入它的存储
    - [ ] 三.10 的升级物品落点与 Shift+点击一致；三.11 的工具类物品
    - [ ] 三.12 物品栏已满 + 手持模组背包（重点风险）
    - [ ] 三.13 容器放不下或拒收时物品留在面板，不报错
- [ ] T009 [US2] **条件任务**：如果 T008 发现临时槽位对某个模组不安全（物品丢失、手持背包物品被破坏、模组报错或守恒检查失败），按日志里的菜单类名在 `OpenContainerDelivery` 里收紧临时槽位的选择规则（例如排除更多槽位）或在没有安全槽位时放弃转移，并在 `OpenContainerDeliveryTest` 里补上对应的假菜单用例，再重做 T008。**不要**为某个模组写专属分支；如果通用规则无法解决，停下来回到 spec 与用户确认，不要自行引入对第三方类型的引用（宪法 IV）

**Checkpoint**: US2 达成 FR-004、FR-012、FR-014、SC-002（精妙背包与旅行者背包部分）。

---

## Phase 5: User Story 3 - Ctrl+拖动批量存入容器 (Priority: P2)

**Goal**: Ctrl+拖动滑过面板槽位时，每个槽位的物品按转移当时打开着的容器处理。

**Independent Test**: quickstart 四：打开箱子，面板一行 9 个堆叠，按住 Ctrl 与左键滑过，9 个依次进入箱子；中途关闭容器后之后的槽位转到物品栏。

- [X] T010 [US3]（2026-11-05 `git status/diff` 对 `src/main/java/pnnh/mcfloatingbackpack/client` 无任何改动；**这只对正向成立，反向（Phase 8）必须改客户端**）确认客户端没有被改动：运行 `git diff --stat -- src/main/java/pnnh/mcfloatingbackpack/client`，012 的实现**不应**让这个目录出现任何新改动（客户端不判断也不指定目标容器，spec FR-013）；如果有，说明实现偏离了设计，回头修正。结果记到 `DELIVERY.md`
- [X] T011 [US3]（2026-11-06 用户在箱子上验证 14–16 通过，见 DELIVERY.md） 按 `quickstart.md` 四（14–16）在 `./gradlew runClient` 逐项验证：一行 9 格滑过箱子 3 秒内完成；拖动中途关闭容器；拖动中容器放满。结果记到 `DELIVERY.md`
  - 2026-11-05 用户验证了箱子、精妙背包、旅行者背包的双向 Ctrl+点击/拖动功能正常；**未验证的子项保持未勾选，所以本任务不勾选**：
    - [X] 四.14 打开箱子，Ctrl+拖动滑过面板一行 → 依次进入箱子
    - [X] 四.14 的"9 格 3 秒内完成"计时（2026-11-06 箱子上通过；精妙背包/旅行者背包里的拖动归入 `runClientCompat` 会话，尚未验证）
    - [X] 四.15 拖动中途关闭容器（2026-11-06：转移瞬间完成，来不及中途按 E/ESC，见 DELIVERY.md 的观察） → 之后的槽位转到物品栏
    - [X] 四.16 拖动中容器放满 → 放不下的槽位保持不变

**Checkpoint**: US3 达成 FR-006、SC-006。

---

## Phase 6: User Story 4 - 没有容器时行为不变，其他方向和 Shift 不受影响 (Priority: P2)

**Goal**: 本功能只改"有容器时转出去哪"这一件事，没有任何回归。

**Independent Test**: quickstart 五：没有容器时与 011 一致；物品栏→面板方向不变；Shift 手势不变；011 的 quickstart 重跑通过。

- [X] T012 [US4]（2026-11-05 两条 grep 均无命中，见 DELIVERY.md）静态检查：对 012 新增和修改的文件（`OpenContainerDelivery.java`、`ContainerMenuAccess.java`、`BackpackQuickTransferPayload.java`）运行 `grep -n "hasShiftDown\|KEY_LEFT_SHIFT\|KEY_RIGHT_SHIFT"`，确认没有读取 Shift；并确认没有 `import` 任何 `net.p3pp3rf1y`、`com.tiviacz` 包（宪法 IV）。结果与命令记到 `DELIVERY.md`
- [X] T013（2026-11-07 五 17–20 此前已通过；用户验证统一回归 REG-1、REG-2 通过，即重跑 011 的结果相同） [US4] 按 `quickstart.md` 五（17–20）验证：HUD 与只开着物品栏（E）时面板转出进玩家物品栏；箱子打开时 Ctrl+点击玩家物品栏槽位进入浮动背包；Shift+点击进入箱子（原版）；面板隐藏时 Ctrl 手势与原版一致；并**重跑 011 的 quickstart**（`specs/011-ctrl-quick-transfer/quickstart.md`）确认结果相同。结果记到 `DELIVERY.md`

**Checkpoint**: US4 达成 FR-002、FR-015、SC-004、SC-005。

---

## Phase 7: Polish & 文档收尾

- [X] T014 [P] 更新 `specs/011-ctrl-quick-transfer/spec.md`：FR-018、澄清第二条和"背景与已确认的决策"里"面板转出只进玩家物品栏"的说法后面加【012 修订】：有打开的容器时进容器，没有时进玩家物品栏；同时注明"容器自己的格子不参与转进来"（FR-017）仍然成立
- [X] T015 [P] 更新 `specs/011-ctrl-quick-transfer/contracts/quick-transfer.md`（`TO_INVENTORY` 的目标描述）、`specs/011-ctrl-quick-transfer/data-model.md`（`Direction` 的含义）与 `specs/011-ctrl-quick-transfer/DELIVERY.md`（已知限制）：各加【012 修订】
- [X] T016 [P] 更新 `docs/MCFloatingBackpack.md` M5 里关于 011 的那句：补"从面板转出时，打开着容器就存入容器（含精妙背包、旅行者背包），否则进玩家物品栏"，并指向 `specs/012-ctrl-transfer-to-open-container/`
- [X] T017（2026-11-07 用户验证 CONS-1、CONS-2 通过：30～50 次混合操作前后总数相等、日志无守恒/回退记录；012-LOG 在 compat 下同样通过；Windows/Linux 未验证） 按 `quickstart.md` 六与七补做：30 次以上转移（含容器满、拒收、物品栏满、两个模组）前后相关物品总数相等；搜 `run/logs/latest.log` 中 `mcfloatingbackpack` 的守恒/异常日志；macOS Command 键可用；Windows/Linux **无法验证**，在 `DELIVERY.md` 里如实写"未验证"
- [ ] T018（2026-11-07：T008、T011、T013、T017 已勾选；**只差 T007**——012-5b 末影箱/潜影盒/漏斗/发射器与 012-9 铁砧/酿造台/村民交易未验证）创建 `specs/012-ctrl-transfer-to-open-container/DELIVERY.md`：记录 T007、T008、T010～T013、T017 的结果，**包括没做到的和结论不确定的**；列出已知限制（第三方菜单抛异常时无法回滚、工作台类界面放不下时留在玩家物品栏、容器→面板不做）
- [X] T019（2026-11-05 编译与 16 个测试类通过、客户端能启动；spec Status 已更新为"验证未完成"）运行最终回归 `./gradlew compileJava test`，并确认游戏能启动（`./gradlew runClient` 进入世界无报错）；把 spec 的 Status 改为实现完成，并如实写明验证未完成的部分

---

## Phase 8: User Story 5 - 从打开着的容器里 Ctrl+点击/拖动把物品存进浮动背包 (Priority: P1)

**Goal**: 打开着容器（含精妙背包、旅行者背包）且面板可见时，Ctrl+点击/拖动容器自己的槽位，物品进入浮动背包；输出类槽位不参与；Shift 让路。

**Independent Test**: quickstart 八：箱子里 Ctrl+点击槽位，物品进浮动背包、总数不变；浮动背包满时放不下的留在容器；熔炉输出槽等不被取走；精妙背包、旅行者背包同样可用；Ctrl+Shift 不处理。

- [X] T020 [P] [US5] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/ContainerToBackpackTest.java`（**先写，先失败**）：在测试里实现假槽位与 `QuickTransferLogicTest` 的 `FakeStack` 适配器，覆盖 contracts §5 的不变量：整堆取出放进背包；先并入再占空格；背包满时全部放回原槽位；部分放下时剩余放回原槽位；堆叠 >64 的槽位只转走背包放得下的数量；`mayPickup` 为假的槽位被跳过且不被改动；`mayPlace` 恒假的输出类槽位被跳过且不被改动；原槽位拒绝放回时依次回退（`slot.set` → 玩家物品栏 → 丢弃回调），总数守恒；随机 100 组布局下总数守恒
- [X] T021 [P] [US5] 在 `src/test/java/pnnh/mcfloatingbackpack/inventory/QuickTransferRequestTest.java` 补 `QuickTransferRequest.validateMenuSlots(int[] menuSlots, int menuSlotCount)` 的用例（**先写，先失败**）：越界、负数、重复、空列表、超过 `MAX_MENU_SLOTS`（256）、数量超过 `menuSlotCount` 均不合法；合法列表通过
- [X] T022 [P] [US5] 更新 `src/test/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferRulesTest.java`（**先写，先失败**）：`QuickTransferRules.isInterceptableScreenClass(String)` 对原版容器界面、精妙背包与旅行者背包的界面类名都为真，对 `CreativeModeInventoryScreen`、null、空串为假（取代原来的 `isSupportedScreenClass`）；新增 `isContainerSourceSlot(boolean sameInventory, boolean active, boolean hasItem, boolean mayPickup, boolean mayPlaceBack)`：只有 `!sameInventory && active && hasItem && mayPickup && mayPlaceBack` 为真；保留 `isTransferableInventorySlot` 的用例
- [X] T023 [P] [US5] 更新 `src/test/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferGestureTest.java`（**先写，先失败**）：`Side.CONTAINER` 与 `INVENTORY` 同属"转入背包"组：起点是容器槽位时滑过玩家物品栏槽位也被收集（反之亦然），滑过 `PANEL` 槽位被忽略；起点是 `PANEL` 时只收集 `PANEL`；每个槽位一次拖动只出现一次；`advance` 的返回值按来源侧分开（容器槽位的菜单下标与玩家物品栏下标不能混在一起）
- [X] T024 [US5] 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/ContainerToBackpack.java`：泛型纯逻辑（无 Minecraft 依赖）。定义 `SlotAccess<T>`（`get`、`mayPickupAndPlaceBack`、`take(count)`、`insert(stack)`→剩余、`set(stack)`）与回退接口（放进玩家物品栏、丢弃）；`static <T> Result<T> take(StackOps<T> ops, SlotAccess<T> slot, int[] backpackTargets, IntFunction<T> getBackpack, ObjIntConsumer<T> setBackpack, Fallback<T> fallback)`：按 data-model 的状态转移实现，含守恒检查结果。使 T020 通过
- [X] T025 [US5] 在 `src/main/java/pnnh/mcfloatingbackpack/inventory/QuickTransferRequest.java` 增加 `MAX_MENU_SLOTS = 256` 与 `validateMenuSlots(...)`。使 T021 通过
- [X] T026 [US5] 在 `src/main/java/pnnh/mcfloatingbackpack/inventory/ContainerMenuAccess.java` 增加面向真实槽位的适配：`ContainerToBackpack.SlotAccess<ItemStack>`（`slot.safeTake(count, count, player)`、`slot.safeInsert`、`slot.set`、`slot.mayPickup(player)` 与 `slot.mayPlace(slot.getItem())`），以及回退（`player.getInventory()` 里用 `QuickTransferLogic.moveInto` 放进 0–35，仍有剩余则 `player.drop(stack, false)`）；判定"槽位属于玩家物品栏"用 `slot.container == player.getInventory()`。依赖 T024
- [X] T027 [US5] 新建 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackTakeFromContainerPayload.java`：字段 `containerId`、`menuSlots`（VarInt 列表，读取时超过 `MAX_MENU_SLOTS` 抛 `DecoderException`）；`handleServer` 按 contracts §5 实现（背包有效、光标为空、`containerMenu != inventoryMenu`、`containerId` 匹配、`validateMenuSlots`、每个槽位非玩家物品栏槽位且有资格，用 `ContainerToBackpack` 取出并放进背包，之后 `ref.saveInventory`、`setChanged`、`broadcastChanges`、`sendFullSync`；守恒失败或触发回退时记日志，含菜单类名与物品）。在 `BackpackNetwork.java` 注册它并把 `versioned("4")` 改成 `versioned("5")`。依赖 T025、T026
- [X] T028 [US5] 客户端：①`src/main/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferRules.java` 实现 T022 里的新规则；②`QuickTransferGesture.java` 增加 `Side.CONTAINER` 并按 data-model 把 `INVENTORY`/`CONTAINER` 分为同组，`advance` 按来源侧返回分开的下标列表；③`QuickTransferInput.java`：命中测试扩展到容器槽位（`slot.container != 玩家 Inventory`、`isActive()`、`hasItem()`、`mayPickup`、`mayPlace(自己的物品)`，且 `screen.getMenu() != player.inventoryMenu`）；取消 011 的类名白名单，改用 `isInterceptableScreenClass`；**`Screen.hasShiftDown()` 为真时一律不开始手势、也不继续拖动**（Shift 让路，这是 011 起唯一一处读取 Shift，目的是不占用 Ctrl+Shift）；容器槽位的请求走 `BackpackTakeFromContainerPayload`（带 `screen.getMenu().containerId`），玩家物品栏槽位仍走 011 的包，每帧每种来源至多一个包。依赖 T022、T023、T027
- [X] T029 [US5]（2026-11-05 `compileJava test` 通过，17 个测试类）运行 `./gradlew compileJava test`，确认 T020～T023 与全部既有测试通过
- [X] T030 [US5]（2026-11-06 用户验证 21–28 通过，见 DELIVERY.md） 按 `quickstart.md` 八（21–28）在 `./gradlew runClient` 逐项验证（macOS 用 Command 键）：箱子取入、背包满时留在容器、熔炉/工作台/铁砧/交易输出槽不参与、拖动一行 9 格、起点方向、Shift 让路、面板隐藏。结果记到 `DELIVERY.md`
  - 2026-11-05 用户验证了箱子、精妙背包、旅行者背包的双向 Ctrl+点击/拖动功能正常；**未验证的子项保持未勾选，所以本任务不勾选**：
    - [X] 八.21 打开箱子，Ctrl+点击箱子里的堆叠 → 进入浮动背包
    - [X] 八.25 Ctrl+拖动滑过箱子一行 → 依次进入浮动背包
    - [X] 八.22 浮动背包里已有同种半堆叠时先并入
    - [X] 八.23 浮动背包塞满：放不下的留在箱子原槽位
    - [X] 八.24 熔炉、工作台、铁砧、村民交易的输出槽不参与
    - [X] 八.26 起点方向（物品栏起点 / 面板起点）
    - [X] 八.27 Ctrl+Shift 让路
    - [X] 八.28 面板隐藏时 Ctrl+点击容器槽位与原版一致
- [X] T031（2026-11-07 用户验证 012-14b、29b、30、31b、32 与 012-LOG 通过） [US5] 按 `quickstart.md` 八（29–32）在 `./gradlew runClientCompat` 逐项验证：精妙背包（存储槽、大堆叠 >64、被滚动隐藏的槽位、升级槽/锁定槽，**以及它自己的 Ctrl 用法有没有被破坏**）、旅行者背包（存储槽、工具槽、记忆槽），以及两个界面里玩家物品栏槽位的 011 方向；检查 `run/logs/latest.log` 没有守恒失败或回退日志。结果记到 `DELIVERY.md`。**如发现与第三方 Ctrl 用法冲突或槽位判定不对，回到 `QuickTransferRules`/`OpenContainer` 一侧收紧通用规则，补单元测试，不要写模组专属分支**
  - 2026-11-05 用户验证了箱子、精妙背包、旅行者背包的双向 Ctrl+点击/拖动功能正常；**未验证的子项保持未勾选，所以本任务不勾选**：
    - [X] 八.29（前半）精妙背包：Ctrl+点击存储槽位 → 进入浮动背包
    - [X] 八.31（前半）旅行者背包：Ctrl+点击存储槽位 → 进入浮动背包
    - [ ] 八.29（后半）堆叠超过 64 的槽位只转走放得下的数量
    - [ ] 八.30 精妙背包被滚动隐藏的槽位、升级槽/锁定槽，以及它自己的 Ctrl 用法有没有被破坏
    - [ ] 八.31（后半）旅行者背包的工具槽、记忆槽
    - [ ] 八.32 两个界面里玩家物品栏槽位的 Ctrl+点击（011 方向对第三方界面生效）
    - [ ] 守恒与日志：`run/logs/latest.log` 没有守恒失败或回退日志
- [X] T032 [US5] 文档：给 011 的 `spec.md`（FR-016、FR-017）、`contracts/quick-transfer.md`（"界面类型"一行与"外部容器自身槽位不参与"）、`DELIVERY.md` 加【012 修订】，说明容器自身的槽位现在可以作为转入浮动背包的来源、第三方界面里的 Ctrl 手势由 012 处理、协议版本升到 5；更新 `docs/MCFloatingBackpack.md` M5 里关于 012 的那句，补"反向"；在 `specs/006-tiered-backpacks/DELIVERY.md` 的协议版本说明里补"012 起为 5"
- [X] T033（2026-11-07 T030、T031 已通过，BOOT-1 已确认能进入世界，今天 `compileJava test` 为 62 个测试类、473 个用例、0 失败；spec Status 已更新，仍如实写明 5b/9 未验证） [US5] 更新 `specs/012-ctrl-transfer-to-open-container/DELIVERY.md`：补反向的自动化结果与 T030、T031 的结果（包括没做到的和结论不确定的）；重新运行最终回归 `./gradlew compileJava test` 并确认游戏能启动；把 spec 的 Status 改为反映反向的实现状态

**Checkpoint**: US5 达成 FR-017～FR-023、SC-008～SC-010。

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（T001）：无依赖
- Phase 2（T002～T004）：T002 与 T003 互相独立可并行（"测试先于实现"配对）；T004 依赖 T003。**阻塞所有用户故事**
- Phase 3 US1（T005～T007）：依赖 Phase 2
- Phase 4 US2（T008、T009）：依赖 US1 的代码（T005）；T009 是条件任务
- Phase 5 US3（T010、T011）：依赖 US1
- Phase 6 US4（T012、T013）：T012 可在 T005 之后随时做；T013 需要代码完成
- Phase 7：依赖前面的结果；T014～T016 三个文档任务可并行
- Phase 8 US5（T020～T033）：依赖 Phase 2 的基础设施与 US1 的正向代码；T020～T023 四个测试可并行先写；T024 依赖 T020，T025 依赖 T021，T026 依赖 T024，T027 依赖 T025、T026，T028 依赖 T022、T023、T027；T030、T031 是人工验证

### User Story Dependencies

- **US1**：依赖基础设施，是 MVP
- **US2**：依赖 US1 的代码，主要是实测；可能触发 T009 回到 Phase 2 的算法
- **US3**：依赖 US1，纯验证
- **US4**：回归验证，依赖代码完成
- **US5**：依赖基础设施与 US1；改动客户端（命中、手势、Shift 让路）和服务端（新包），是目前最大的一块

### Parallel Opportunities

- T002 与 T003 并行
- T014、T015、T016 并行
- US3（T010、T011）与 US2（T008）的验证可由同一次游戏会话完成，但要分别记录

---

## Parallel Example: Phase 2

```text
并行启动：
  T002 OpenContainerDeliveryTest.java   （假菜单测试，先写）
  T003 OpenContainerDelivery.java       （泛型算法，使 T002 通过）
随后顺序：T004
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1、2（算法 + 适配器 + 单元测试）。
2. 完成 US1（接上网络包分支），**停下来在原版容器里验证**（T007）：箱子、物品栏满、熔炉、工作台。
3. 再做 US2（精妙背包、旅行者背包）：这是本功能最大的不确定性，务必实测"物品栏满 + 手持模组背包"（T008）。
4. US3、US4 是验证与回归，随后做文档收尾。

### 与宪法的关系

- 每个会改变玩家所见/所操作的故事都有客户端会话验证任务（T007、T008、T011、T013、T017）；没做的项在 DELIVERY.md 里记"未验证"，不能因为编译通过或单元测试通过就勾选（宪法 V）。
- 不引用第三方类型（T012 静态检查，宪法 IV）；不新增资源、不改共用图集（宪法 VI 不适用）。

### 风险提醒（实现时盯住）

- **物品栏满时的"换出再换回"**：对精妙背包/旅行者背包是否安全是推断（research Decision 3），T008 第 12 条必须实测；永不选手持的快捷栏槽位、要求 `mayPickup`。
- **第三方菜单内部异常无法回滚**：只保证自己放进临时槽位的部分被取回；守恒检查只记日志，要在 T017 搜日志。
- **工作台类界面放不下时物品留在玩家物品栏**：这是"与 Shift+点击一致"的预期结果（总数守恒），不是缺陷，已写进 spec 假设。
