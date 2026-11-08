# Tasks: 第三个扩展工具——自动拾取（标签黑名单；与其它背包模组只做被动礼让）

**Input**: Design documents from `/specs/024-auto-pickup/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/pickup-network-storage.md, contracts/pickup-ui-compat.md, quickstart.md；宪法 2.1.1；016（工具区与放入规则）、018（标签与标签页）、019（宿主界面）、021（限放 1 个、垃圾桶面板）、022（Delete 键）、023（配方）已实现

**Tests**: 黑名单的清洗/上限/匹配、请求校验、拾取决策表、搬运守恒、左侧区限放、页面模型与状态、配方与语言键，都是不依赖 Minecraft 运行环境的纯逻辑，plan 要求用单元测试覆盖，**测试任务排在对应实现之前**；拾取的去向与声音动画、背包满回落、激活背包切换、页面展开/互斥/输入、黑名单留在地上、与精妙背包/旅行者背包同时拾取时的表现、持久化、图标外观只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础（纯逻辑 + 物品注册 + 数据组件）；US1（物品、配方、图标、服务端拾取接管）、US2（点击展开/收起页面）、US3（黑名单增删与过滤，含协议 12）是三个 P1，合起来才是可用的最小自动拾取；US4（被动礼让，只验证安全性）、US5（左侧区限放 1 个）、US6（持久化、兼容与回归）为 P2。

> **2026-11-08 变更**：用户裁决**取消主动避让**（它必须依赖精妙背包、旅行者背包的内部类，不是公开 API，风险与收益不成比例）。US4 改为"被动礼让"（实现已经在 `AutoPickupEvents` 里：`LOW` 优先级 + 事件已是 `FALSE` 就不插手）；原 T033～T043（探测器、避让开关包、档位同步、`compileOnly`）全部取消，编号保留不重排。原 T002 的 `build.gradle` 改动已回退。详见 research Decision 6。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US6）
- 路径都相对于仓库根目录；下文路径里的 `…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**启动客户端或跑 `check_atlas_budget.sh` 前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，有正在运行的客户端就不要启动、更不要杀用户的游戏**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过；**关于精妙背包/旅行者背包的行为（读字节码得出）在实测前一律是"未验证"**
- 包名前缀 `pnnh.mcfloatingbackpack`；测试类路径里只有 JUnit（没有 Minecraft/Gson/Netty），所以涉及 `ItemStack`、编解码的逻辑要放进纯类里测，资源测试用文本方式；第三方 `import` 只允许出现在 `…/compat/<modid>/<文件>`（`DependencyRulesTest` 自动检查）

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码、测试类数量与用例数；结果记到 `specs/024-auto-pickup/DELIVERY.md`（新建，中文）
- [X] T002 **（已取消并回退，2026-11-08）** 原计划给 `build.gradle` 加三条 `compileOnly` 第三方 jar；随"取消主动避让"回退，`build.gradle` 与 024 之前一致（`git diff build.gradle` 为空），不再有"普通构建需要 CurseMaven"的要求

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 黑名单、请求校验、拾取决策、搬运是客户端与服务端共用的纯逻辑；物品注册与 `BackpackContents.pickupBlacklist` 是数据基础。测试先写、先失败。

- [X] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/pickup/PickupBlacklistTest.java`（**先写，先失败**），按 data-model.md 第 1 节：`EMPTY` 为空；`add` 合法标签追加在末尾；`add` 已存在 → 返回原值（幂等，不是 `null`）；`add` 非法（空串、含空格、含大写、超过 16 字符）→ `null`；已满 64 个再 `add` 新标签 → `null`，但 `add` 已存在的仍返回原值；`remove` 存在则移除、不存在返回原值；`isFull()`；`matches(tagsOfItem)`：任一标签在黑名单即真、物品没有标签为假、黑名单为空为假；`sanitize(raw)`（读入清洗）：丢弃非法与重复、截到 64 个、保持顺序；构造后列表不可变
- [X] T004 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/pickup/PickupBlacklistRequestTest.java`（**先写，先失败**）：`Op.fromByte(0/1)` 合法（ADD/REMOVE）、其它返回 `null`；`validate(op, tag)`：`op` 为 `null` 拒绝；`TagRules.isValid(tag)` 为假拒绝（空串、含空格、含大写、超长、`null`）；合法通过
- [X] T005 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/pickup/AutoPickupDecisionTest.java`（**先写，先失败**），按 data-model.md 第 4 节的决策表：`toolActive=false` → `VANILLA`（无论是否命中黑名单）；`toolActive=true` 且命中 → `BLOCK`；`toolActive=true` 且未命中 → `TAKE`。（原含避让的版本已随主动避让取消而简化）
- [X] T006 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/pickup/PickupInsertTest.java`（**先写，先失败**），测试里自带一个简单的 `QuickTransferLogic.StackOps<T>`（种类 + 数量 + 最大堆叠）与假格子数组：全部放得下（先叠进同种未满堆叠，再占空格）→ `rest` 为空、`moved` = 原数量；只放下一部分 → `rest` 为剩余、`moved` 正确；一点放不下 → `moved == 0`、格子不变；`changedSlots` 恰好是被改动的下标且升序无重复；目标顺序是 `0..size-1`；`moved + rest.count == 原数量` 对随机 200 组输入成立；空栈输入 → `moved == 0`
- [X] T007 [P] 新建 `…/inventory/pickup/PickupBlacklist.java`：不可变记录 `PickupBlacklist(List<String> tags)`，`MAX_TAGS = 64`，`EMPTY`，`CODEC`（`Codec.STRING.listOf()` 的 `xmap`，读入时经 `sanitize` 清洗）、`STREAM_CODEC`（`ByteBufCodecs.stringUtf8(TagRules.MAX_LENGTH).apply(ByteBufCodecs.list(MAX_TAGS))` 的 `map`），构造时复制列表，纯函数 `add/remove/contains/size/isFull/matches/sanitize`，规则逐条照 data-model.md 第 1 节。使 T003 通过。（测试类路径没有 Minecraft/Netty，两个编解码器无法单测，改在客户端会话里验证，登记为未单测，与 018 的 `ItemTags` 同样处理）
- [X] T008 [P] 新建 `…/inventory/pickup/PickupBlacklistRequest.java`：纯逻辑。`enum Op { ADD, REMOVE }`（`toByte`/`fromByte`），`validate(Op, String tag)`。使 T004 通过
- [X] T009 [P] 新建 `…/inventory/pickup/AutoPickupDecision.java`：纯逻辑。`enum Outcome { VANILLA, BLOCK, TAKE }`，`decide(boolean toolActive, Set<String> avoidMods, Predicate<String> detects, boolean blacklisted)`，规则与短路照 data-model.md 第 4 节（`avoidMods` 用调用方给的迭代顺序）。使 T005 通过
- [X] T010 [P] 新建 `…/inventory/pickup/PickupInsert.java`：纯逻辑静态泛型类，`record Result<T>(T rest, int moved, int[] changedSlots)`，`insert(StackOps<T> ops, T stack, int size, IntFunction<T> get, ObjIntConsumer<T> set)`：复用 `QuickTransferLogic.moveInto`（目标顺序 `0..size-1`），在 `set` 回调里收集被改动的下标（去重升序），`moved = 原数量 − rest 数量`。使 T006 通过
- [X] T011 新建 `…/item/AutoPickupItem.java`（普通 `Item`，堆叠 1，无其它行为，javadoc 说明只有放在左侧区才有功能，同 `TrashCanItem`）；修改 `…/item/ModItems.java` 注册 `auto_pickup`（`AUTO_PICKUP`）并加入本模组创意栏（垃圾桶之后）。（配方、模型、贴图、语言留给 US1）
- [X] T012 修改 `…/inventory/BackpackContents.java`、`…/inventory/BackpackInventory.java`、`…/client/panel/ClientBackpackView.java`、`…/inventory/recipe/BackpackUpgradeRecipe.java`：`BackpackContents(items, cursor, tools, tags, trash, pickupBlacklist)`，新字段用 `PickupBlacklist.CODEC.optionalFieldOf("pickup_blacklist", PickupBlacklist.EMPTY)`，`STREAM_CODEC` 加第 6 个字段（**`composite` 用满 6 个**，在 javadoc 里写明"再加字段必须先改手写编码"）；`empty/normalize/fit/copy` 全部带上新字段（`null` 保护，同 `trash`）；新增 `BackpackContents.hasAutoPickup()`（左侧区列表里有 `ModItems.AUTO_PICKUP` 的栈，供预检用，不建 `BackpackInventory`）；`BackpackInventory` 增加 `pickupBlacklist()/setPickupBlacklist(...)`、`hasAutoPickup()`（扫描 `size(PanelArea.LEFT)`，**整个列表**）、`fromContents/toContents` 读写；`ClientBackpackView` 增加黑名单镜像 `pickupBlacklist()/setPickupBlacklist(...)`（`clear()` 复位为空）与 `hasAutoPickup()`（扫描 `LEFT` 区整个列表，与 `hasTrashCan()` 并排）；`BackpackUpgradeRecipe` 调 `BackpackContents.fit(...)` 时补传 `current.pickupBlacklist()`（升级保留）；修正所有现有的 `BackpackContents` 构造调用点与测试。依赖 T007、T011
- [X] T013 运行 `./gradlew compileJava test`，确认 T003～T006 与全部既有测试通过

**Checkpoint**: 编译通过；新增纯逻辑测试全绿；旧背包读成空黑名单。

---

## Phase 3: User Story 1 - 自动拾取放进左侧区才有功能，掉落物直接进背包 (Priority: P1) 🎯 MVP 入口

**Goal**: 配方、图标、语言；服务端接管拾取：激活背包左侧区里有自动拾取时，掉落物先进主区域，放不下的回落到物品栏；命中黑名单的留在地上（黑名单此时只能通过数据写入，页面在 US2/US3）；只认"按 B 会打开的那一只"；面板开关无关。

**Independent Test**: quickstart A（Q1～Q4）、B（Q5～Q10）、C（Q11～Q13）。

- [X] T014 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/resources/AutoPickupResourcesTest.java`（**先写，先失败**，文本方式，参照 `TrashCanResourcesTest`）：`data/mcfloatingbackpack/recipe/auto_pickup.json`——`crafting_shaped`、图案 `["IEI", "RHR", "IDI"]`、`I = minecraft:iron_ingot`、`E = minecraft:ender_pearl`、`R = minecraft:redstone`、`H = minecraft:hopper`、`D = minecraft:diamond`、结果 `mcfloatingbackpack:auto_pickup` ×1、不含 `sophisticated`/`travelersbackpack`；`models/item/auto_pickup.json` 的父模型是 `minecraft:item/generated` 且指向 `mcfloatingbackpack:item/auto_pickup`；`textures/item/auto_pickup.png` 存在且 PNG 头宽高为 16×16；`en_us.json` 与 `zh_cn.json` 同时含 `item.mcfloatingbackpack.auto_pickup`、`pickup.mcfloatingbackpack.title`、`pickup.mcfloatingbackpack.status`、`pickup.mcfloatingbackpack.none`、`pickup.mcfloatingbackpack.full`、`message.mcfloatingbackpack.limit.auto_pickup`；两份语言文件的键集合相同；中文名"自动拾取"、英文名 "Auto Pickup"
- [X] T015 [US1] 新建 `src/main/resources/data/mcfloatingbackpack/recipe/auto_pickup.json`（research Decision 10：`IEI / RHR / IDI`，写法对照 `trash_can.json`，用 `item` 键）、`src/main/resources/assets/mcfloatingbackpack/models/item/auto_pickup.json`（`item/generated`，对照 `trash_can.json`）；修改 `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` 与 `zh_cn.json` 加入全部新键（中文：物品名"自动拾取"；页面标题"自动拾取黑名单"；状态行 `pickup.mcfloatingbackpack.status` = "自动拾取黑名单  %s/%s"；"无"；"黑名单已满（%s）"；"这里只能放一个自动拾取"；英文对应：Auto Pickup / "Auto Pickup Blacklist  %s/%s" / "None" / "Blacklist is full (%s)" / "Only one Auto Pickup fits here"）。使 T014 除贴图外的部分通过。依赖 T011
- [X] T016 [US1] 生成图标：按 research Decision 10，用 ludo.ai MCP（`mcp__ludo-ai__createImage`，`item-icon`、像素风、一个漏斗、上方一颗蓝绿色末影珍珠正落入其中、原版 Minecraft 物品图标风格、透明或白底）生成候选图，下载到 `design/demo/tools/auto_pickup_icon/`（**源图与脚本不随模组发布**）；新建 `design/demo/tools/make_auto_pickup_icon.py`（参照 `make_trash_can_icon.py`：去背景、裁切、缩放到 16、alpha 二值化、调色板量化）并写入 `src/main/resources/assets/mcfloatingbackpack/textures/item/auto_pickup.png`；ludo 不可用或额度不够时，改用 pixellab MCP 或在同一脚本里手绘 16×16；把生成方式、提示词、来源文件、脚本记入 DELIVERY.md；用 `Read` 打开 PNG 目视检查。依赖 T015
- [X] T017 [US1] 新建 `…/inventory/pickup/AutoPickupEvents.java` 并修改 `…/MCFloatingBackpack.java`：构造函数里 `NeoForge.EVENT_BUS.addListener(EventPriority.LOW, AutoPickupEvents::onItemPickup)`（`ItemEntityPickupEvent.Pre`）。流程逐条照 data-model.md 第 8 节与 research Decision 1/2：`event.canPickup().isFalse()`、实体栈为空、`entity.hasPickUpDelay()` → 返回；`BackpackRef.resolve(player)` 为 `null` → 返回；**预检**读该背包物品上的 `BackpackContents.hasAutoPickup()`（不通过则返回，不建 `BackpackInventory`）；事件的 `canPickup()` 已是 `FALSE` 就跳过（对其它背包的礼让，2026-11-08 起 `decide` 只有 `toolActive`、`blacklisted` 两个输入）；`blacklisted = contents.pickupBlacklist().matches(contents.tags().tagsOf(物品注册 id))`；`AutoPickupDecision.decide(...)`：`VANILLA` 返回、`BLOCK` → `setCanPickup(FALSE)`、`TAKE` → 物主检查（`entity.getTarget()`）、`loadInventory`、`PickupInsert.insert`（`StackOps` 用 `ItemStackTransferOps.INSTANCE`）、`moved == 0` 返回、**先** `ref.saveInventory` **再** `stack.shrink(moved)`、对 `changedSlots` 逐个 `BackpackNetwork.sendSlotUpdate`、`player.take(entity, moved)`、`player.awardStat(Stats.ITEM_PICKED_UP.get(item), moved)`、`player.onItemPickup(entity)`、实体栈空则 `entity.discard()` + `setCanPickup(FALSE)`、否则保持默认让原版回落；`ref.stillValid` 不成立或保存异常 → 不缩小实体栈直接返回；不触发 `ItemEntityPickupEvent.Post`（javadoc 写明原因，research Decision 1）；调试日志只在异常与失败时写。依赖 T010、T012
- [X] T018 [US1] 运行 `./gradlew compileJava test`，确认 T014（贴图部分在 T016 后）与全部既有测试通过
- [X] T019（2026-11-08 用户口头确认已手动验证、功能正常；具体逐项结果未记录） [US1] 按 `quickstart.md` A（Q1～Q4）、B（Q5～Q10）、C（Q11～Q13）在 `./gradlew runClient` 验证（先 `pgrep`）：创意栏/配方/贴图；放进左侧区后关着面板捡东西进背包、不进物品栏，有动画声音；先叠后填；放进其它区域/拿走后进物品栏；背包满/部分放下/全满的回落与守恒；拾取延迟与物主不变；激活背包 = 按 B 打开的那只，换手持物后下一次拾取即切换；记录部分搬运时是否出现两次拾取声（research 不确定项 3），结果记到 DELIVERY.md（由用户确认后才打勾）。**到这里先让用户验证再继续**

**Checkpoint**: 放进左侧区的自动拾取能接管拾取，物品守恒，原版路径在不满足条件时原样保留。

---

## Phase 4: User Story 2 - 左键点击展开/收起标签黑名单页面 (Priority: P1)

**Goal**: 左侧区里左键自动拾取展开覆盖主区域的黑名单页面（本阶段页面里只有状态行、"无"、加号占位，点击被消费），再次点击收起；与标签页互斥、与垃圾桶面板共存；其它区域里它是普通物品。

**Independent Test**: quickstart D（Q14～Q18）。

- [X] T020 [P] [US2] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/pickup/PickupPanelModelTest.java`（**先写，先失败**）：`PickupPanelModel.of(tags, hasTool)`：`canAdd` = `hasTool && tags.size() < 64`；`isFull()`；`count()/max()`；`candidates(presetEntries, usedTags, blacklist)`：预设在前、本背包已用标签在后、去重、**排除已在黑名单里的**、保持各自顺序；`candidates` 用 `TagMatcher.match` 前的原始集合
- [X] T021 [P] [US2] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/pickup/PickupPanelStateTest.java`（**先写，先失败**，结构照 `TagPanelState` 的既有测试或按其行为）：`toggle` 在展开/收起间切换；`collapse` 保留文本与滚动、结束编辑与待删；`reset` 清文本与滚动；`startEditing` 只有展开时有效；`stopEditing`；`setScroll` 不为负
- [X] T022 [P] [US2] 新建 `…/client/panel/pickup/PickupPanelState.java`（单例，字段与方法照 `TagPanelState`：`open/editing/dropdownIndex/pendingDelete/scroll/input(TagTextInput)`，不依赖 Minecraft 类型）与 `…/client/panel/pickup/PickupPanelModel.java`（纯，见 T020）。使 T020、T021 通过
- [X] T023 [US2] 新建 `…/client/panel/pickup/PickupPanelContext.java`（标签显示名复用 `TagPanelContext.label`；下拉候选 = `PickupPanelModel.candidates` 后用 `TagMatcher.match` 过滤；布局 = `TagPanelLayout.of(...)`，状态行文字用 `pickup.mcfloatingbackpack.status` 与 `…none`；已满时加号灰、悬停提示 `…full`）与 `…/client/panel/pickup/PickupPanelRenderer.java`（画在主区域矩形上，几何全读布局，样式照 `TagPanelRenderer`）；修改 `…/client/panel/FloatingPanelRenderer.java`：页面展开时在主区域矩形上画页面，并在两处 `area == MAIN && TagPanelState.get().isOpen()` 判断补上 `|| PickupPanelState.get().isOpen()`（跳过主区格子绘制）。依赖 T022
- [X] T024 [US2] 新建 `…/client/panel/pickup/PickupPanelInput.java`（本阶段 `onMainPress`：左键只消费，右键消费；`onMainScroll`：一行一档，布局夹住范围）并修改 `…/client/panel/FloatingPanelInput.java`：①工具区点击分支里，命中 `LEFT` 且该格是自动拾取、左键、光标为空、无 Shift/Ctrl、`PanelDragInput.isActive()` 为假 → `PickupPanelState.toggle()`（不发包）并消费，与 018/021 的分派并排；右键与光标非空照 016 原样；②主区域按下消费：在 `TagPanelState.isOpen()` 分支旁加同构的 `PickupPanelState.isOpen()` 分支，转给 `PickupPanelInput.onMainPress`；③编辑中点页面以外 → 失焦（现有 `TagPanelState.isEditing()` 失焦分支补上对应一条）；④互斥：`PickupPanelState.toggle()` 展开前调用 `TagPanelState.get().collapse()`，并修改 `…/client/panel/tags/TagPanelState.java` 的 `toggle()` 展开前调用 `PickupPanelState.get().collapse()`（**仅此一行改动 018 的类**）。依赖 T022、T023
- [X] T025 [US2] 修改 `…/client/FloatingPanelEvents.java`：①滚轮分支：`area == MAIN && PickupPanelState.get().isOpen()` 时 `PickupPanelInput.onMainScroll` 并消费（与现有 `TagPanelState` 分支并排）；②`onClientTick`：黑名单页展开但（浮动面板不可见 或 `!ClientBackpackView.get().hasAutoPickup()`）→ `collapse()`；③`onLoggingIn/onLoggingOut` 与 `closePanel`：`PickupPanelState.get().reset()`；渲染与输入都已挂在 `hostsPanel(screen)` 之后，确认 OFF 界面上不画不响应。依赖 T022、T024
- [X] T026 [US2] 运行 `./gradlew compileJava test`，确认 T020、T021 与全部既有测试通过
- [x] T027 [US2] 按 `quickstart.md` D（Q14～Q18）在 `./gradlew runClient` 验证（先 `pgrep`）：左侧区左键展开/收起、右键拿起、光标非空时交换、其它区域只是普通物品；页面展开时主区点击/滚轮不穿透；与标签页互斥（展开一个收起另一个）、与垃圾桶面板共存；拿走自动拾取/关闭浮动面板自动收起；OFF 的模组界面上页面随面板隐藏且状态保留；结果记到 DELIVERY.md（由用户确认后才打勾）

**Checkpoint**: 页面能展开/收起/互斥；还不能增删标签。

---

## Phase 5: User Story 3 - 在黑名单里添加/删除标签，命中的物品不再被拾取 (Priority: P1)

**Goal**: 协议 12；黑名单增删（服务端权威）、同步、页面里的完整交互（标签块、删除、加号、文本框、下拉、键盘）；命中的物品留在地上（US1 的服务端已支持，这里让玩家真正能设置）。

**Independent Test**: quickstart E（Q20～Q26）。

- [X] T028 [US3] 新建 `…/network/BackpackPickupBlacklistOpPayload.java`（`op` 字节 + `tag` 用 `writeUtf(tag, TagRules.MAX_LENGTH)`，解码时 `op` 非法抛 `DecoderException`；`handleServer` 严格按 contracts/pickup-network-storage.md 第 3 节：`ServerPlayer`、`BackpackRef.resolve`+`stillValid`、`PickupBlacklistRequest.validate`、**左侧区有自动拾取**（`BackpackInventory.hasAutoPickup()`）、`add`/`remove`（`null` 即忽略，已存在幂等）、`setPickupBlacklist`、`saveInventory`、发 `BackpackPickupBlacklistPayload`；任一步失败整个请求零变化并补发黑名单）与 `…/network/BackpackPickupBlacklistPayload.java`（服务端→客户端整份 `PickupBlacklist`，`handleClient` 调 `ClientBackpackView.setPickupBlacklist`）；修改 `…/network/BackpackNetwork.java`：`versioned("11")` → `versioned("12")`，注册两个包，`sendSession` 里在标签包与垃圾桶包之后补发黑名单（`sendPickupBlacklist(player, inventory)` 辅助方法）。协议版本只在这一处改。依赖 T008、T012
- [X] T029 [US3] 扩展 `…/client/panel/pickup/PickupPanelInput.java`（照 `TagPanelInput` 的结构）：`onMainPress`：命中标签 → 停止编辑、设待删；命中删除 → 发 `REMOVE`；命中加号 → `model.canAdd()` 才 `startEditing`；命中文本框 → 放置插入点；命中下拉项 → 立即添加该候选；其余 `cancelTransient`；`onKeyPressed`（ESC 停止编辑、Enter 提交、Backspace/Delete/左右/Home/End、上下选下拉、粘贴走 `TagRules.filter`）；`onCharTyped`；`add(tag)`：`TagRules.isValid` 为假忽略；已在黑名单里直接关闭文本框（不发请求）；`!canAdd` 关闭文本框；否则发 `BackpackPickupBlacklistOpPayload` 并清空/结束编辑；发送走 `PacketDistributor.sendToServer`。依赖 T024、T028
- [X] T030 [US3] 修改 `…/client/FloatingPanelEvents.java` 的键盘三事件（`KeyPressed.Pre`、`KeyReleased.Pre`、`CharacterTyped.Pre`）：在现有 `hostsPanel(screen) && TagPanelState.get().isEditing()` 旁补 `|| PickupPanelState.get().isEditing()` 并转给 `PickupPanelInput`（只在文本框有焦点时拦截，没有焦点不拦截任何键盘事件）；修改 `…/client/panel/DeleteKeyInput.java`：传给 `DeleteKeyRules.Inputs` 的 `tagPanelOpen/tagPanelEditing` 取标签页与黑名单页的**并集**（页面覆盖主区域时 Delete 不删主区物品；编辑时 Delete 属于文本框）。依赖 T029
- [X] T031 [US3] 运行 `./gradlew compileJava test`，确认全部测试通过（含 `DeleteKeyRulesTest`）
- [x] T032 [US3] 按 `quickstart.md` E（Q20～Q26）在 `./gradlew runClient` 验证（先 `pgrep`）：给圆石打 `stone`、黑名单加 `stone` → 圆石留在地上不进背包也不进物品栏，泥土进背包，删除后恢复；下拉有预设与本背包已用标签；点标签出现删除标记；已满 64 个加号变灰并提示；重复添加不重复；两个标签只命中一个；`trash` 预设；自己丢出的物品同样不被拾取；文本框焦点与 Delete 键行为；结果记到 DELIVERY.md（由用户确认后才打勾）。**到这里（US1～US3）是可用的最小自动拾取，先让用户验证 T019、T027、T032 再继续**

**Checkpoint**: 黑名单可设置、可同步、能过滤拾取；MVP 完成。

---

## Phase 6: User Story 4 - 与其它背包模组同时拾取时被动礼让 (Priority: P2)

**Goal**: 不检测、不依赖第三方模组；晚一步出手并尊重别人已处理的结果；不崩溃、不复制、不丢失。**实现已经在 T017**（`LOW` 优先级 + `canPickup().isFalse()` 跳过 + 只搬运实体里剩余的数量），本阶段只做审阅与验证。

**Independent Test**: quickstart F（Q27～Q34），用 `./gradlew runClientCompat`。

- [X] T033 [US4] 审阅（2026-11-08 已审阅，无缺口：`LOW` 优先级、`canPickup().isFalse()` 首行返回、`moved` 来自真实搬运结果且先保存后 `shrink`、本 feature 无第三方引用、`compat/` 与 `build.gradle` 无改动）（读代码，不改行为）：①`AutoPickupEvents` 用 `EventPriority.LOW` 注册；②`canPickup().isFalse()` 时第一行就返回；③`take` 只对 `entity.getItem()` **此刻**的数量做搬运，`moved` 来自 `PickupInsert` 的真实结果，保存背包成功后才 `shrink`；④`grep -rn "p3pp3rf1y\|tiviacz" src/main/java` 在本 feature 的文件里没有命中，`src/main/java/pnnh/mcfloatingbackpack/compat/` 与 `build.gradle` 无改动，`DependencyRulesTest` 通过；发现缺口就补并记录，没有缺口也记"已审阅，无缺口"
- [ ] T034 [US4] 按 `quickstart.md` F（Q27～Q34）在 `./gradlew runClientCompat` 验证（先 `pgrep`）：无其它背包时正常拾取；带拾取升级（有/无过滤器）的精妙背包、旅行者背包时总量守恒、不崩溃，对方先拿时本模组跳过、对方过滤器不放行的物品进本模组背包；两者同时开着不复制；旅行者拾取升级/精妙磁铁的按范围吸取拿走的物品本模组拿不到（预期）；没装这两个模组（`runClient`）功能完整无报错；专用服务器/局域网无条件则写"未验证"；**把实测到的先后顺序与分配如实写进 DELIVERY.md**（各背包拿多少不作要求）；结果由用户确认后才打勾

**已取消的任务（编号保留）**：T035～T043（原：避让开关表 `PickupAvoidRegistry`、探测器接口与注册表、两个第三方探测器、`BackpackPickupAvoidPayload` 与客户端发送、`CompatConfig` 说明、依赖规则复核、`runClientCompat` 档位验证）；原 T033、T034 的测试一并取消。

**Checkpoint**: 与第三方背包同时拾取时安全；没有任何第三方引用。

---

## Phase 7: User Story 5 - 左侧区最多 1 个自动拾取 (Priority: P2)

**Goal**: 服务端放入规则：左侧区同时最多 1 个自动拾取；客户端提前判断并提示。

**Independent Test**: quickstart D 的 Q19。

- [X] T044 [P] [US5] 扩展测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/ToolAreaRulesTest.java`（**先写，先失败**）：签名新增第三个谓词后更新所有既有调用；新增 `LEFT` 用例：左侧区已有 1 个，再放第 2 个到空格 → 拒绝；放到别的物品的格子（换进第二个）→ 拒绝；放到那个自动拾取自己的格子（交换）→ 允许；左侧区没有时放入 → 允许；旧背包已有 2 个，同格交换 → 允许、再放第三个 → 拒绝；其它区域（`MAIN/RIGHT/BOTTOM`）放自动拾取 → 允许；垃圾桶/标签管理器在 `LEFT` 不受限、既有的 `RIGHT`/`BOTTOM` 限制不变
- [X] T045 [US5] 修改 `…/inventory/ToolAreaRules.java`：`canPlace(area, incoming, slotIndex, areaSlots, isTrashCan, isTagManager, isAutoPickup)`，`case LEFT -> isAutoPickup`，更新 javadoc；修改两处调用点补谓词 `stack -> stack.is(ModItems.AUTO_PICKUP.get())`：`…/network/BackpackClickPayload.java`（服务端唯一权威）与 `…/client/panel/FloatingPanelInput.java` 的 `refusedByLimit`（提示键：`held.is(AUTO_PICKUP)` → `message.mcfloatingbackpack.limit.auto_pickup`，其余保持）。使 T044 通过
- [X] T046 [US5] 运行 `./gradlew compileJava test`，确认 T044 与全部既有测试通过
- [x] T047 [US5] 按 `quickstart.md` Q19 在 `./gradlew runClient` 验证（先 `pgrep`）：左侧区已有一个时再放第二个（空格 / 别的物品的格子）被拒绝并在聊天区提示；用它自己的格子交换成功；Alt 交换主手同样被拒；其它区域放多个照旧允许；垃圾桶/标签管理器的既有限制不变；遗留 2 个的背包（用创造模式/命令构造）两个都在、不能放第三个；结果记到 DELIVERY.md（由用户确认后才打勾）

---

## Phase 8: User Story 6 - 黑名单跟着背包走，且不影响现有功能 (Priority: P2)

**Goal**: 持久化、隔离、升级、旧背包、多人隔离、兼容界面、回归。

**Independent Test**: quickstart G（Q35～Q37）。

- [X] T048 [US6] 审阅（2026-11-08 已审阅：升级保留、`normalize/fit` 带黑名单、旧存档读成空、非法请求整个丢弃且零变化、拾取早退路径不改动实体与背包、保存在缩小实体栈之前；无缺口）（读代码，不改行为）：①`BackpackUpgradeRecipe` 升级后 `pickupBlacklist` 保留（T012 已核对，再确认）；②`BackpackContents.normalize/fit` 在容量缩小时不丢黑名单；③读入旧存档（无 `pickup_blacklist` 字段）为 `PickupBlacklist.EMPTY`；④服务端所有非法请求路径（`op` 非法、标签非法、超过 64 个、左侧区没有自动拾取、背包不在）都整个丢弃且数据零变化，只补发同步；⑤`AutoPickupEvents` 的所有早退路径不改动实体栈与背包；保存失败时不缩小实体栈；⑥多人：事件只处理触发拾取的那名玩家，不碰别人的物品栏与背包。发现缺口就补并记录，没有缺口也记"已审阅，无缺口"
- [ ] T049 [US6] 按 `quickstart.md` G（Q35～Q37）在 `./gradlew runClient` 验证（先 `pgrep`）：退出重进/关闭浮动面板黑名单仍在；自动拾取取走再放回黑名单还在（取走期间拾取与过滤都不起作用）；两只背包互不共享；锻造台升级保留；024 之前保存的旧背包黑名单为空不报错；多人无条件则写"未验证"；**重跑 011～023 的 quickstart 中与面板有关的场景**（点击、013 拖动均分与收集、014 选区、Ctrl 转移、缩放、滚动、标签管理器与标签面板、垃圾桶与 022 的删除手段、兼容开关、宿主界面键盘、023 配方）；结果记到 DELIVERY.md（由用户确认后才打勾）

---

## Phase 9: Polish & Cross-Cutting

- [X] T050 [P] 更新文档：`specs/016-panel-tool-areas/` 与 `specs/021-trash-can/` 的 spec 加一段 024 说明（左侧区限放 1 个自动拾取，`ToolAreaRules` 增加第三个谓词）；`specs/018-tag-manager/` 的 spec 加一句"024 起标签页与自动拾取的黑名单页互斥"；`docs/MCFloatingBackpack.md` 在 023 说明之后加 024 说明（自动拾取、黑名单、避让、背包数据第 6 个字段、协议 12）；`docs/plan/manual-verification-checklist.md` 补 024 的验证项；**不要修改 `docs/ideas`**
- [ ] T051 按 `quickstart.md` Q4：`./gradlew compileJava test` 退出码 0；先 `pgrep` 确认没有运行中的客户端，再跑 `design/demo/tools/check_atlas_budget.sh`，图集应仍为 2048×1024（宪法 VI；本 feature 只新增一张 16×16 物品贴图），结果记到 DELIVERY.md——**跑不了（有客户端在运行或脚本失败）就如实写"未度量"，不要写成通过**
- [ ] T052 成本粗测（宪法 VI，quickstart Q38、Q39）：①在开发机上对"无背包 / 有背包但左侧区没有自动拾取 / 激活"三种状态各测一次拾取事件的额外成本（`System.nanoTime` 对 10 万次调用求平均，临时调试日志，**测完删掉**）；②一次拾取 64 个不同掉落物，记录客户端收到的容器同步字节数；测不了就如实写"未测量"，超标则在 DELIVERY 的已知限制里登记为后续"拾取批处理"的依据（research Decision 11）
- [X] T053 静态检查（2026-11-08：`src/main` 里第三方引用只在 `compat/jei/` 与 `ContainerScreenCompat` 原有位置；`setScreen(` 仍为 4 处（与 HEAD 相同）；`build.gradle`、`neoforge.mods.toml`、`compat/` 无改动；协议版本只在 `BackpackNetwork` 一处为 `"12"`；`ItemEntityPickupEvent` 只出现在 `AutoPickupEvents` 与它的注册处 `MCFloatingBackpack`）：`git diff --stat` 确认没有改动 `PanelSelectionInput`、`PanelDragGesture`、`QuickTransferRules`、`QuickTransferInput`、`TrashPanel*` 的逻辑，`TagPanelState` 只多了互斥那一行，`TagPanelInput/Layout/Renderer` 无改动；`setScreen(` 与 023 一致（没有新增）；`neoforge.mods.toml` 与 `build.gradle` 无改动、`compat/` 无改动；`DependencyRulesTest` 通过；协议版本只在 `BackpackNetwork` 一处改为 `"12"`；`grep -rn "ItemEntityPickupEvent" src/main/java` 只命中 `AutoPickupEvents`
- [ ] T054 新建/补全 `specs/024-auto-pickup/DELIVERY.md`（中文）：汇总自动化结果（T001 基线与各阶段编译与测试结果、新增测试类与用例数）、与 plan/tasks 的偏离、**每个客户端验证项实际验证了什么 / 未验证什么**、与精妙背包 3.26.19、旅行者背包 10.1.40 同时拾取的实测结果（先后顺序、分配、是否复制）、图标的生成方式与来源（提示词、脚本、是否退回手绘）、图集度量结果或"未度量"、成本粗测结果或"未测量"、已知限制（Windows/Linux 未验证；联机；不触发 `ItemEntityPickupEvent.Post`；旅行者拾取升级的 `tick` 与精妙磁铁按范围吸取不经过拾取事件、本模组拿不到；高频拾取的同步成本；多个背包同时拾取时各拿多少不保证）、**给后来者的提醒**（`BackpackContents` 编码字段已用满 6 个，再加字段先改手写编码；`TagPanel*` 与 `PickupPanel*` 结构重复，第三处需要同类面板时再抽象）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1（Setup）**：无依赖，先做。T002 依赖 T001。
- **Phase 2（Foundational）**：依赖 Phase 1。T003～T006 可并行写测试；T007～T010 可并行实现；T011 与它们互相独立；T012 依赖 T007、T011；T013 最后。**阻塞所有用户故事。**
- **Phase 3（US1）**：依赖 Phase 2。T014 ∥ T017 的前置；T015 依赖 T011；T016 依赖 T015；T017 依赖 T010、T012；T018 → T019。
- **Phase 4（US2）**：依赖 Phase 2 与 US1 的物品注册（T011）；**不依赖 US1 的服务端拾取（T017）**，但验证时需要自动拾取物品存在。T020 ∥ T021；T022 依赖 T020/T021；T023 依赖 T022；T024 依赖 T022/T023；T025 依赖 T022/T024；T026 → T027。
- **Phase 5（US3）**：依赖 US2（页面与输入骨架）和 Phase 2；服务端过滤部分已在 US1（T017）。T028 → T029 → T030 → T031 → T032。
- **Phase 6（US4）**：依赖 US1（`AutoPickupEvents` 已含礼让逻辑）；与 US2/US3 互相独立。T033 → T034。
- **Phase 7（US5）**：只依赖 Phase 2 与物品注册（T011）；与 US1～US4 互相独立，可以在 Phase 2 之后任何时候做。T044 → T045 → T046 → T047。
- **Phase 8（US6）**：依赖前面所有故事的代码。
- **Phase 9（Polish）**：T050 可在 US3 稳定后随时做；T051～T054 在所有故事完成后。

### User Story Dependencies

- US1 → US2 → US3 构成最小可用自动拾取（MVP）；US4 在 US1 之上；US5 独立；US6 汇总验证。

### Parallel Opportunities

- Phase 2：T003 ∥ T004 ∥ T005 ∥ T006；T007 ∥ T008 ∥ T009 ∥ T010。
- Phase 4：T020 ∥ T021。
- Phase 6 只有两个顺序任务；Phase 7 的 T044 可与它同时进行。

### Parallel Example: Phase 2

```text
同时写测试：T003 PickupBlacklistTest  T004 PickupBlacklistRequestTest  T005 AutoPickupDecisionTest  T006 PickupInsertTest
同时实现：  T007 PickupBlacklist  T008 PickupBlacklistRequest  T009 AutoPickupDecision  T010 PickupInsert   （T011 物品注册也可同时）
然后：      T012 BackpackContents/Inventory/ClientBackpackView/升级配方 → T013 运行测试
```

---

## Implementation Strategy

### MVP First（US1 → US2 → US3）

1. 完成 Phase 1、Phase 2（纯逻辑 + 物品注册 + 数据组件，不碰游戏界面）。
2. 完成 US1：配方、图标、服务端接管拾取。**到这里先让用户验证 T019（拾取去向、背包满回落、激活背包）再继续。**
3. 完成 US2：页面展开/收起/互斥。用户验证 T027。
4. 完成 US3：协议 12、黑名单增删、过滤。用户验证 T032 后才算 MVP 完成。

### Incremental Delivery

1. MVP（US1 + US2 + US3）→ 用户验证。
2. US4（被动礼让）→ 用 `runClientCompat` 验证安全性（不崩溃、守恒）。
3. US5（限放 1 个）→ 验证。
4. US6（持久化、兼容与回归）→ 验证。
5. Polish：文档、图集度量、成本粗测、静态检查、交付说明。

### 风险提示（实现时盯住）

- **与第三方背包同时拾取**（T033、T034）：行为来自读字节码，未在游戏里验证；只保证不崩溃、守恒；不得为此在本 feature 里引用任何第三方类型（已决定不做主动避让，research Decision 6）。
- **拾取接管的守恒**（T017、T048）：先保存背包、再缩小实体栈；保存失败不动实体；回落靠原版 `Inventory.add`；`TAKE` 满栈时 `discard` + `FALSE`，部分搬运保持默认。
- **服务端权威**（T028、T045）：客户端只发"增删 + 标签名"；黑名单增删必须核对"左侧区有自动拾取"；限放规则在 `BackpackClickPayload` 一处。
- **互斥与既有触点多**（T023～T025、T029、T030）：黑名单页要在输入、渲染、键盘、滚轮、Delete 键、每 tick 收起检查等约十处接入，漏一处就会穿透或键盘冲突；对 018 的类只允许在 `TagPanelState.toggle()` 加一行。
- **协议升级**：`"11"` → `"12"` 只在 `BackpackNetwork` 改一处（T028）；`BackpackContents.STREAM_CODEC` 用满 6 个字段（T012）。
- **011～023 不回归**：唯一的有意变化是左侧区限放 1 个与标签页/黑名单页互斥（T053）。
- **图集预算**：只新增一张 16×16 物品贴图；度量不了就如实写"未度量"（T051）。
