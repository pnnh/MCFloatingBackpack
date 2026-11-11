# Tasks: 垃圾桶的删除手段与保护

**Input**: Design documents from `/specs/022-trash-delete-tools/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/trash-send-network.md, contracts/trash-tools-ui.md, quickstart.md；宪法 2.1.1；021（垃圾桶本体、`TrashBin`、协议 9）、018（标签面板）、019（宿主界面）、020（`MenuOwner`/`ServerCompatGate`）已实现并提交

**Tests**: 倾倒筛选、销毁数与阈值、"只差耐久度"、可删格子范围、请求校验、标签保护、Delete 键判定、确认弹窗与图标几何、确认状态、保护标签与语言键，都是不依赖 Minecraft 运行环境的纯逻辑，plan 要求用单元测试覆盖，**测试任务排在对应实现之前**；键盘流向（Delete、ESC）、蒙版与弹窗的输入消费、菜单格子的取出与同步、栈级跳过对真实栈的覆盖、渲染手感只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础（纯规则、保护标签、语言键、请求包与服务端的共用流水线、确认状态）；US1（一键倾倒）、US2（保护）、US3（删除图标与主区域 Delete）是三个 P1；US4（二次确认）、US5（原版界面 Delete）、US6（改键、兼容与回归）为 P2。

> **注意（US1 与 US4 的先后）**：US1 完成时，"会销毁超过 9 组"的倾倒会先打开 `TrashConfirmState`，但弹窗的绘制与输入在 US4 才有，所以 **US1 期间这种大批量倾倒只会被服务端拒绝（没带已确认标志）**；验证 US1（T029）时只测不超过阈值的倾倒，大批量留到 US4。

> **注意（FR-008 措辞）**：plan/research 的 Decision 4 把 spec FR-008 与 US2 的"加号变灰"收窄成"'垃圾'预设变灰、添加被拒绝（加号仍可用）"，**等用户确认 spec 措辞**；本任务清单按 plan 实现。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US6）
- 路径都相对于仓库根目录；下文路径里的 `…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**启动客户端或跑 `check_atlas_budget.sh` 前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，有正在运行的客户端就不要启动、更不要杀用户的游戏**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过
- 包名前缀 `pnnh.mcfloatingbackpack`；测试类路径里只有 JUnit（没有 Minecraft/Gson/Netty），所以涉及 `ItemStack`、编解码的逻辑要放进纯类里测，资源测试用文本方式

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码、测试类数量与用例数（021 之后 50 个测试类、409 个用例）；结果记到 `specs/022-trash-delete-tools/DELIVERY.md`

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 纯规则被客户端与服务端共用；保护标签、语言键、请求包与服务端流水线是三个来源（倾倒、主区域下标、菜单格子）的共同后半段。测试先写、先失败。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/trash/TrashDeletePlanTest.java`（**先写，先失败**），按 data-model.md 第 1 节的表：`THRESHOLD = 9`；`destroyed(0,5)=0`、`(4,3)=0`、`(9,1)=1`、`(9,9)=9`、`(9,10)=10`、`(0,20)=11`、`(5,12)=8`、`(5,14)=10`；负数按 0；`needsConfirm(9)` 假、`needsConfirm(10)` 真；单次放入 1 组永远不需要确认
- [X] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/trash/TrashDumpTest.java`（**先写，先失败**），用 `String` 当栈并传入四个谓词：只选"非空且带 trash 且不受保护且只差耐久度"的格子，返回升序下标；空格子、没标 trash、受保护、带数据的各被排除一次；全部不满足返回空数组；`size` 之外的下标不会出现
- [X] T004 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/trash/StackExtraDataTest.java`（**先写，先失败**）：空集合为真；只含 `minecraft:damage` 为真；含 `minecraft:enchantments`、`minecraft:custom_name`、`minecraft:container`、`minecraft:potion_contents`、`minecraft:banner_patterns`、`minecraft:profile`、`minecraft:map_id` 任一为假；`damage` 加别的为假；null 集合按空处理
- [X] T005 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/trash/DeletableSlotsTest.java`（**先写，先失败**），按 data-model.md 第 4 节：玩家物品栏 `containerSlot` 9～35 为真，0～8（快捷栏）、36～39（装备）、40（副手）为假；非玩家物品栏时只有 `chestOwnSlot` 为真；两者都不是为假
- [X] T006 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/trash/TrashSendRequestTest.java`（**先写，先失败**），按 data-model.md 第 5 节：`Source.fromByte(0/1/2)` 合法、其它返回 null；`DUMP` 只接受空下标；`MAIN_SLOTS` 1～`min(mainSize,108)` 个、互不相同、∈ [0,mainSize)；`MENU_SLOT` 恰好 1 个且 ∈ [0,256)、`containerId` ≥ 0；null、重复、负数、越界、数量超限全部拒绝
- [X] T007 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/trash/TagProtectionTest.java`（**先写，先失败**）：`blocks("trash", 1)` 真、`blocks("trash", 0)` 假、`blocks("food", 3)` 假；`TagProtection.TRASH` 等于 `PresetTags` 里的预设 id（`PresetTags.isPreset(TagProtection.TRASH)` 为真）
- [X] T008 [P] 新建 `…/inventory/trash/TrashDeletePlan.java`：纯逻辑，`THRESHOLD`、`destroyed`、`needsConfirm`，建在 `TrashBin.MAX_GROUPS` 上。使 T002 通过
- [X] T009 [P] 新建 `…/inventory/trash/TrashDump.java`：纯泛型静态类，`select(size, stackAt, isEmpty, hasTrashTag, isProtected, onlyDefaultData)` 返回 `int[]`。使 T003 通过
- [X] T010 [P] 新建 `…/inventory/trash/StackExtraData.java`：纯逻辑，`DAMAGE = "minecraft:damage"`，`onlyDamage(Collection<String>)`。使 T004 通过
- [X] T011 [P] 新建 `…/inventory/trash/DeletableSlots.java`：纯逻辑，`OWN_FIRST = 9`、`OWN_LAST = 35`，`allowed(boolean, int, boolean)`。使 T005 通过
- [X] T012 [P] 新建 `…/inventory/trash/TrashSendRequest.java`：纯逻辑，`enum Source { DUMP, MAIN_SLOTS, MENU_SLOT }`（`toByte`/`fromByte`），`MAX_MENU_SLOT = 256`，`validate(source, slots, containerId, mainSize)`。使 T006 通过
- [X] T013 [P] 新建 `…/inventory/trash/TagProtection.java`：纯逻辑，`TRASH = "trash"`，`blocks(tag, protectedCategories)`。使 T007 通过
- [X] T014 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/trash/TrashConfirmStateTest.java`（**先写，先失败**），按 data-model.md 第 10 节：初始不待确认；`open` 后待确认并记住来源、销毁数、界面；`cancel` 清空；`shouldDiscard(panelVisible, hasTrashCan, currentScreen)` 在面板不可见、垃圾桶不在、界面与记录不同三种情况下各为真，全部正常时为假；没有待确认时 `shouldDiscard` 为假
- [X] T015 [P] 新建 `…/client/panel/trash/TrashConfirmState.java`：纯逻辑单例（无 Minecraft 类型；界面用 `Object`），`Pending` 记录（`DUMP` 或 `MAIN_SLOTS(int[])`）、`open/cancel/isPending/pending()/destroyed()/shouldDiscard`。使 T014 通过
- [X] T016 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/resources/NeverTrashResourcesTest.java`（**先写，先失败**，文本方式）：`data/mcfloatingbackpack/tags/item/never_trash.json` 含 `"replace": false`；`values` 里有本模组 7 个物品（`mcfloatingbackpack:leather_floating_backpack`、`iron_…`、`gold_…`、`diamond_…`、`netherite_floating_backpack`、`tag_manager`、`trash_can`）、`minecraft:shulker_box` 与 16 色、`dragon_egg`、`nether_star`、`elytra`、`totem_of_undying`、`beacon`、`heart_of_the_sea`、`enchanted_golden_apple`；`bundle` 与 16 色收纳袋都带 `"required": false`
- [X] T017 新建 `…/item/ModTags.java`（`TagKey<Item> NEVER_TRASH = ItemTags.create(ResourceLocation.fromNamespaceAndPath(MOD_ID, "never_trash"))`）与 `src/main/resources/data/mcfloatingbackpack/tags/item/never_trash.json`（`replace: false`，条目按 data-model.md 第 8 节；收纳袋条目写成 `{"id": "...", "required": false}`）。使 T016 通过。依赖 T016
- [X] T018 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/resources/TrashToolsResourcesTest.java`（**先写，先失败**）：`en_us.json` 与 `zh_cn.json` 键集合相同，且同时含 `key.mcfloatingbackpack.delete_to_trash`、`trash.mcfloatingbackpack.icon.dump`、`trash.mcfloatingbackpack.dump.tooltip`、`trash.mcfloatingbackpack.icon.delete`、`trash.mcfloatingbackpack.delete.tooltip.disabled`、`trash.mcfloatingbackpack.delete.tooltip.enabled`、`trash.mcfloatingbackpack.confirm.text`、`trash.mcfloatingbackpack.confirm.yes`、`trash.mcfloatingbackpack.confirm.no`、`message.mcfloatingbackpack.trash.nothing_to_dump`、`message.mcfloatingbackpack.trash.changed`、`tag.mcfloatingbackpack.protected`；`en_us` 里 `trash.mcfloatingbackpack.title` 为 "Trash"；中文 `confirm.text` 含 `%s`
- [X] T019 修改 `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` 与 `zh_cn.json`：加入 T018 列出的全部键（中文：删除到垃圾桶；倾倒垃圾；"把主区域里带‘垃圾’标签的物品移进垃圾桶。受保护的物品和带附魔等数据的物品会被跳过。"；删除；"先在主区域用 Shift 选择物品，再点这里删除"；"删除选中的物品（可从垃圾桶拿回）"；"将永久销毁 %s 组物品，确认吗？"；确认；取消；"没有可倾倒的物品（受保护或带数据的物品会被跳过）"；"背包内容已变化，操作已取消，请重试"；"这些物品不能标记为垃圾：%s"；英文对应），并把英文 `trash.mcfloatingbackpack.title` 由 "Trash Can" 改为 "Trash"。使 T018 通过（021 的 `TrashCanResourcesTest` 仍通过）
- [X] T020 新建 `…/network/BackpackTrashSendPayload.java`（客户端→服务端，`source` 字节 + `confirmed` + `containerId` VarInt + `slots` VarInt 列表，解码数量 > 108 抛 `DecoderException`，`of(...)`），修改 `…/network/BackpackNetwork.java`：协议 `"9"` → `"10"`、注册新包。`handleServer` 实现 contracts 第 2 节**与来源无关的流水线**：解析/核对背包 → `hasTrashCan()` → `TrashSendRequest.validate` → 调用 `collect(source, …)` 得到"要放入的栈列表 + 逐个取走的动作"（本任务先让三种来源的 `collect` 都返回空，由 T025、T040、T053 分别实现）→ 列表空则忽略 → `TrashDeletePlan` 算销毁数、超过 9 且未带 `confirmed` 则忽略并发 `message.mcfloatingbackpack.trash.changed` → 每个栈的体积保护（复用 `TrashContents.MAX_GROUP_ENCODED_BYTES`，任一超限整个忽略并发 `message.mcfloatingbackpack.trash.too_large`）→ 依次 `TrashBin.push` 并取走来源、被挤出的各记一行调试日志 → 守恒核对 → 保存 → `menu.broadcastChanges()`（仅菜单来源）→ 发 `BackpackTrashContentsPayload`、`sendFullSync`、`sendSlotUpdate(-1)`；任何忽略路径也补发这三项同步。依赖 T008、T012、T017
- [X] T021 运行 `./gradlew compileJava test`，确认 T002～T007、T014、T016、T018 与全部既有测试通过

**Checkpoint**: 编译通过；新增纯逻辑与资源测试全绿；协议为 10；请求包可发但三种来源都还没有内容。

---

## Phase 3: User Story 1 - 一键倾倒带"垃圾"标签的物品 (Priority: P1) 🎯 MVP 入口

**Goal**: 垃圾桶标题栏出现倾倒图标；点击把主区域里分类带"垃圾"标签的栈按格子顺序移进垃圾桶；没有可倾倒的物品时聊天提示。

**Independent Test**: quickstart A（Q1～Q2）、B（Q3～Q7）。

- [X] T022 [P] [US1] 扩展测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/trash/TrashPanelLayoutTest.java`（**先写，先失败**），按 data-model.md 第 9 节：`iconRect(DELETE)` 在标题栏内右对齐、`iconRect(DUMP)` 在它左边隔 2 像素；两图标互不相交且都在标题栏矩形内；`iconAt` 命中各自矩形返回对应图标、命中标题文字区或标题栏以外返回 null；`titleTextWidth()` > 0 且文字区与 `DUMP` 图标不重叠；021 已有的用例保持通过
- [X] T023 [US1] 修改 `…/client/panel/trash/TrashPanelLayout.java`：新增 `enum Icon { DUMP, DELETE }`、`ICON = 9`、`ICON_GAP = 2`、`ICON_PAD = 4`、`iconRect(Icon)`、`iconAt(x, y)`、`titleTextWidth()`；其余几何不变。使 T022 通过。依赖 T022
- [X] T024 [US1] 新建 `…/client/panel/trash/TrashActions.java`：客户端动作入口。本任务实现 `dump()`：用 `TrashDump.select`（读 `ClientBackpackView` 的主区域栈；`hasTrashTag` 读 `ClientBackpackView.tags()`；`isProtected` 用 `stack.is(ModTags.NEVER_TRASH)`；`onlyDefaultData` 用 `StackExtraData.onlyDamage(stack.getComponentsPatch()` 里每个组件类型的注册名)）数出组数；0 组 → `FloatingPanelEvents.chat(…, "message.mcfloatingbackpack.trash.nothing_to_dump")` 并返回；`TrashDeletePlan.needsConfirm(destroyed(现有组数, 组数))` 为真 → `TrashConfirmState.open(DUMP, destroyed, minecraft.screen)` 并返回；否则发 `BackpackTrashSendPayload(DUMP, false, 0, [])`。依赖 T009、T010、T015、T020
- [X] T025 [US1] 在 `BackpackTrashSendPayload` 里实现 `DUMP` 的 `collect`：服务端用真实栈做同样的 `TrashDump.select`（`hasTrashTag` 读 `backpack.tags().tagsOf(registryId).contains("trash")`，`isProtected` 用 `stack.is(ModTags.NEVER_TRASH)`，`onlyDefaultData` 同 T024），返回这些主区域下标里的栈（升序）与"把该主格子置空"的取走动作。依赖 T020
- [X] T026 [US1] 修改 `…/client/panel/trash/TrashPanelRenderer.java`：标题文字按 `titleTextWidth()` 用 `font.plainSubstrByWidth` 截断；用 `fill` 画两个图标（倾倒 = 向下小三角 + 托盘线；删除 = `×`），置灰与否由 `TrashActions.deleteEnabled()`（本任务先返回 false，T041 实现）决定，悬停图标时底色高亮并 `renderComponentTooltip`（倾倒：`dump.tooltip`；删除：置灰时 `delete.tooltip.disabled`、可用时 `delete.tooltip.enabled`）；颜色与尺寸常量集中在类顶部。依赖 T023
- [X] T027 [US1] 修改 `…/client/panel/trash/TrashPanelInput.java`：`onPress` 里光标为空、无 Shift、`PanelDragInput` 不在途、点在倾倒图标上 → `TrashActions.dump()`；点在删除图标上先不做事（T041 接上）；标题栏其它位置吞掉；光标非空时仍按 021 的"追加"处理（不管点在哪个图标上）。依赖 T024、T026
- [X] T028 [US1] 运行 `./gradlew compileJava test`，确认 T022 与全部既有测试通过；`grep -rn "setScreen(" …/client/panel/trash` 应无命中
- [X] T029（2026-11-07 用户逐项验证 Q1～Q7 通过） [US1] 按 `quickstart.md` A（Q1～Q2）、B（Q3～Q7）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：图标位置与外观、悬停说明（中英文）、倾倒顺序与保留、不超过阈值时直接执行、没有可倾倒的物品时聊天提示、其它三个区域不被倾倒、拿回与倾倒前一致；**只测销毁数不超过 9 的倾倒，大批量留到 US4**；结果记到 DELIVERY.md（由用户确认后才打勾）

**Checkpoint**: 一键倾倒可用（不超过阈值的）。

---

## Phase 4: User Story 2 - 保护：不让重要物品被一键倾倒或标成垃圾 (Priority: P1)

**Goal**: 标签面板对受保护物品拦截"垃圾"（只拦这一个标签）；服务端同样拒绝；一键倾倒的两层保护生效。

**Independent Test**: quickstart C（Q8～Q14）。

- [X] T030 [P] [US2] 扩展测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/TagPanelModelTest.java`（**先写，先失败**）：模型多一个 `protectedAt` 谓词；选区含受保护分类时 `protectedItems()` 列出它们（去重、保持顺序）、`blocks("trash")` 为真、`blocks("food")` 为假；`canAdd()` 语义不变（只看工具与"已满"）；选区没有受保护物品时 `blocks("trash")` 为假；018 已有用例保持通过
- [X] T031 [US2] 修改 `…/client/panel/tags/TagPanelModel.java`：`of(...)` 增加 `protectedAt` 参数，保存 `protectedItems`，增加 `protectedItems()` 与 `blocks(tag)`（调用 `TagProtection.blocks`）；修正所有现有调用点与测试。使 T030 通过。依赖 T013、T030
- [X] T032 [US2] 修改 `…/client/panel/tags/TagPanelContext.java`、`TagPanelInput.java`、`TagPanelRenderer.java`：`TagPanelContext.model()` 传入 `protectedAt`（`BuiltInRegistries.ITEM.get(id)` 的默认栈 `.is(ModTags.NEVER_TRASH)`）；`TagPanelInput.add(tag)` 在 `model.blocks(tag)` 时直接返回**不停止编辑**，并在聊天区发 `tag.mcfloatingbackpack.protected`（列出物品名，用 `TagPanelContext.itemName`）；`TagPanelInput` 的下拉点击、方向键回车、手打回车三条路径都经过 `add`；`TagPanelRenderer` 把下拉里的 `trash` 预设在 `blocks("trash")` 时画成灰色，悬停时 `renderComponentTooltip` 列出受保护物品；其它标签与加号可用性不变。依赖 T031
- [X] T033 [US2] 修改 `…/network/BackpackTagPayload.java`：在解析出分类之后、修改 `TagBook` 之前，若 `op == ADD` 且 `TagProtection.blocks(tag, 受保护分类数)`（受保护 = 被选中非空格子里 `stack.is(ModTags.NEVER_TRASH)` 的不同分类）→ 整个请求忽略、数据零变化，并补发一次 `BackpackTagsPayload`；`REMOVE` 与其它标签不受影响。依赖 T013、T017
- [X] T034 [US2] 运行 `./gradlew compileJava test`，确认 T030 与全部既有测试（含 018 的 `TagPanelModelTest`）通过
- [ ] T035（2026-11-07 部分：Q8～Q10 用户验证通过；**Q11（栈级跳过实测，research Decision 3）、Q12、Q13、Q14 仍未验证**） [US2] 按 `quickstart.md` C（Q8～Q14）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：受保护物品选中时"垃圾"预设灰色、悬停提示、点击/回车/手打都不添加且有聊天提示，加号与其它标签仍可用；10 类受保护物品各试一次；圆石 + 潜影盒混选被拒；受保护物品已带 trash 标签时倾倒被跳过；附魔、改名、药水、旗帜、头颅、地图、书的栈倾倒时被跳过而磨损的镐被倾倒（**这一条验证 research Decision 3 的组件补丁判断，要实测**）；数据包增删条目生效；`bundle` 缺失不报错；主动删除不受保护；**重跑 018 的标签面板场景**（加号、预设下拉、回车添加、点击添加、删除）；结果记到 DELIVERY.md（由用户确认后才打勾） ——**2026-11-11 AI 自动验证（容器内）：Q12、Q13、Q14 通过；Q11 只做了钻石镐（磨损 / 附魔 / 改名）与装了东西的潜影盒，药水、旗帜、头颅、地图、书没测**，所以未勾；证据见 DELIVERY"AI 自动验证" ——2026-11-11 第二轮：Q36 通过（见 DELIVERY）

**Checkpoint**: 倾倒有了两层保护，标签面板拦截生效。

---

## Phase 5: User Story 3 - 选中后点"删除"图标，或按 Delete 键，删除主区域的物品 (Priority: P1)

**Goal**: 删除图标随选区亮起/置灰；点击删除选区；Delete 键在主区域删除选区或悬停格子；删除后清选区。

**Independent Test**: quickstart D（Q15～Q16）、E（Q17～Q23）。

- [X] T036 [P] [US3] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/DeleteKeyRulesTest.java`（**先写，先失败**），按 data-model.md 第 12 节的表逐行：面板不可见/无垃圾桶/确认待定/标签面板展开/正在编辑 → 各自 `NONE`；有选区物品 → `MAIN_SELECTION`（即使同时悬停在别的格子）；无选区、悬停主区域有物品 → `MAIN_HOVER`；`INVENTORY`/`CHEST` 且悬停菜单格子允许且有物品 → `MENU_SLOT`；`HOST` 或 `OTHER` 上菜单目标一律不会出现；`OTHER` 界面上主区域目标仍成立；全部不满足 → `NONE`
- [X] T037 [US3] 新建 `…/client/panel/DeleteKeyRules.java`：纯逻辑，`Inputs` 记录、`ScreenKind { HOST, INVENTORY, CHEST, OTHER }`、`Target { NONE, MAIN_SELECTION, MAIN_HOVER, MENU_SLOT }`、`decide`。使 T036 通过。依赖 T036
- [X] T038 [US3] 修改 `…/client/FloatingBackpackKeys.java` 与注册处（`registerKeys`、`…/client/ClientSetup.java` 如需）：新增 `DELETE_TO_TRASH`（名称 `key.mcfloatingbackpack.delete_to_trash`、`KeyConflictContext.GUI`、默认键 `Minecraft.ON_OSX ? GLFW_KEY_BACKSPACE : GLFW_KEY_DELETE`、分类沿用 `CATEGORY`），在 `registerKeys` 里 `event.register`；更新类 javadoc 说明两个映射的上下文。依赖 T019
- [X] T039 [US3] 扩展 `…/client/panel/trash/TrashActions.java`：`deleteEnabled()`（`PanelSelection` 里至少一个格子在 `ClientBackpackView` 里有物品）；`deleteSelection()`（取选区里有物品的下标快照，`TrashDeletePlan` 判断确认，需要确认则 `TrashConfirmState.open(MAIN_SLOTS(快照), …)`，否则发 `BackpackTrashSendPayload(MAIN_SLOTS, false, 0, 快照)` 并 `PanelSelection.get().clear()`）；`deleteMainSlots(int[])`（Delete 悬停单格用，同样的确认判断，不清选区）。依赖 T024、T012
- [X] T040 [US3] 在 `BackpackTrashSendPayload` 里实现 `MAIN_SLOTS` 的 `collect`：下标升序，取其中**非空**格子的栈与"置空该主格子"的取走动作（空格子跳过）；不看标签、不看保护。依赖 T020
- [X] T041 [US3] 修改 `…/client/panel/trash/TrashPanelRenderer.java` 与 `TrashPanelInput.java`：删除图标用 `TrashActions.deleteEnabled()` 决定置灰；`TrashPanelInput.onPress` 里点在可用的删除图标上 → `TrashActions.deleteSelection()`，置灰时吞掉不做事。依赖 T039
- [X] T042 [US3] 修改 `…/client/FloatingPanelEvents.java`：新增 `@SubscribeEvent onScreenKeyPressedPost(ScreenEvent.KeyPressed.Post)`：`hostsPanel(screen)`、`FloatingBackpackKeys.DELETE_TO_TRASH.matches(keyCode, scanCode)` 且其冲突上下文活动才继续；组装 `DeleteKeyRules.Inputs`（面板可见、`hasTrashCan()`、`TrashConfirmState.isPending()`、`TagPanelState` 的展开与编辑、界面种类先只区分 `HOST` 与 `OTHER`——物品栏/箱子在 T054 接入、选区是否有物品、悬停主区域格子 = `PanelSlots.indexAt(state, guiMouseX, guiMouseY)` 且 `< slotCount` 且有物品）→ `decide`；`MAIN_SELECTION` → `TrashActions.deleteSelection()`；`MAIN_HOVER` → `TrashActions.deleteMainSlots(new int[]{slot})`；**只有目标不是 `NONE` 时才 `event.setCanceled(true)`**。依赖 T037、T038、T039
- [X] T043 [US3] 运行 `./gradlew compileJava test`，确认 T036 与全部既有测试通过
- [X] T044（2026-11-07 用户逐项验证 Q15～Q23 通过；只测了销毁数不超过 9 的删除） [US3] 按 `quickstart.md` D（Q15～Q16）、E（Q17～Q23）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：删除图标置灰/可用与悬停说明；点图标删除选区且清选区；Delete（macOS Backspace）删除选区/悬停格子、选区优先；工具区与垃圾桶面板上不触发且**不吞键**；标签面板展开或文本框有焦点时不触发；没有垃圾桶时不触发；宿主界面与开着玩家物品栏/箱子时主区域的 Delete 都可用，不引起宿主让位；删除本模组的背包/标签管理器/垃圾桶允许；删除后拿回一致；**只测销毁数不超过 9 的删除**；结果记到 DELIVERY.md（由用户确认后才打勾）

**Checkpoint**: 三种主动删除手段中的两种（图标、主区域 Delete）可用；MVP（US1 + US2 + US3）成立。

---

## Phase 6: User Story 4 - 会销毁很多组时先确认 (Priority: P2)

**Goal**: 销毁数超过 9 时弹整面板蒙版 + 居中确认框；所有输入先过它；ESC 只取消确认；换界面/关面板丢弃；服务端要求已确认标志。

**Independent Test**: quickstart F（Q24～Q30）。

- [X] T045 [P] [US4] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/trash/TrashConfirmLayoutTest.java`（**先写，先失败**），按 data-model.md 第 11 节：`mask()` 等于面板矩形；弹窗宽 = `min(panelW − 12, 120)`、居中、完全在蒙版内；两个按钮完全在弹窗内、互不相交、宽 `(弹窗宽 − 24) / 2`、高 14、在弹窗底部；`hit` 对按钮内返回 `CONFIRM`/`CANCEL`、弹窗内其它位置 `DIALOG`、蒙版内弹窗外 `MASK`、面板外 `OUTSIDE`；**最小面板 104×96、`textLines = 1/2/3`** 时弹窗高 ≤ 面板高且按钮不越界；面板位置变化时整体平移
- [X] T046 [US4] 新建 `…/client/panel/trash/TrashConfirmLayout.java`：纯几何（无 Minecraft 类型），`of(panelX, panelY, panelW, panelH, textLines, lineHeight)`、`mask()/dialog()/confirmButton()/cancelButton()/hit(x, y)`、`enum Hit`。使 T045 通过。依赖 T045
- [X] T047 [US4] 新建 `…/client/panel/trash/TrashConfirmRenderer.java` 并修改 `…/client/panel/FloatingPanelRenderer.java`：待确认时在垃圾桶面板之后、光标物品之前（同一个 `OVERLAY_Z` 的 `pushPose` 里）画蒙版（`fill`，半透明黑，覆盖整个浮动面板矩形）、弹窗（面板同色框）、文字（`trash.mcfloatingbackpack.confirm.text` 带销毁数，按弹窗宽 `font.split` 折行，行数传给 `TrashConfirmLayout`）、"确认""取消"两个按钮（悬停高亮）；几何全部读 `TrashConfirmLayout`。依赖 T046、T015
- [X] T048 [US4] 新建 `…/client/panel/trash/TrashConfirmInput.java` 并修改 `…/client/panel/FloatingPanelInput.java`：`dispatchMouseButton` 的 PRESS 分支**最前面**先问 `TrashConfirmInput.onPress`：待确认时**所有左右键按下都被消费**；点"确认"→ 按 `pending` 发带 `confirmed = true` 的 `BackpackTrashSendPayload`（`DUMP` 或 `MAIN_SLOTS`）、`MAIN_SLOTS` 时清选区、关弹窗；点"取消"或任何其它位置 → 关弹窗不发请求。依赖 T046、T047、T015
- [X] T049 [US4] 修改 `…/client/FloatingPanelEvents.java`：①`onScreenKeyPressed`（`KeyPressed.Pre`）里：`hostsPanel(screen)` 且 `TrashConfirmState.isPending()` 且 `keyCode == GLFW_KEY_ESCAPE` → `TrashConfirmState.cancel()` 并 `event.setCanceled(true)`（优先于 018 的编辑分支，确认期间不会在编辑）；②`scrollPanel` 里待确认时一律消费（先于垃圾桶面板判断）；③`onClientTick` 里 `TrashConfirmState.shouldDiscard(面板可见, hasTrashCan(), minecraft.screen)` 为真 → `cancel()`；④`closePanel`、登入/登出里 `cancel()`。依赖 T015、T048
- [X] T050 [US4] 修改 `…/client/panel/trash/TrashPanelInput.java`：`onRelease` 在 `TrashConfirmState.isPending()` 时直接返回 false（弹窗打开前的拖动按下仍可能在途，松开不当作丢入）。依赖 T015
- [X] T051 [US4] 运行 `./gradlew compileJava test`，确认 T045 与全部既有测试通过
- [X] T052（2026-11-07 用户逐项验证 Q24～Q30 通过，含 ESC 与蒙版输入；确认框遮罩层级与取消时保留选区的修复另见 DELIVERY） [US4] 按 `quickstart.md` F（Q24～Q30）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：倾倒 20 个栈弹出确认且文字显示销毁数 11；"确认"执行、"取消"/点蒙版空白/点面板外含垃圾桶面板/ESC 都只取消，**ESC 不关面板不弹暂停菜单**（宿主界面和开着箱子两种情况都测）；弹窗期间 Delete/滚轮/拖动/缩放无效，点关闭按钮只取消；热键关闭面板、换界面、垃圾桶离开右侧区时待确认被丢弃；阈值边界（9 不弹、10 弹，选 12 格删除不弹、选 20 格弹）；最小面板下文字与按钮完整；服务端拒绝没带标志的请求（没有条件构造就写"仅单元测试覆盖/未验证"）；结果记到 DELIVERY.md（由用户确认后才打勾）

**Checkpoint**: 大批量操作有确认保护。

---

## Phase 7: User Story 5 - 在原版玩家物品栏和箱子界面里按 Delete 删除 (Priority: P2)

**Goal**: 在原版玩家物品栏（第 9～35 格）与箱子界面里 Delete 悬停格子的整栈；服务端按菜单核对；其它界面不处理不吞键。

**Independent Test**: quickstart G（Q31～Q38）。

- [X] T053 [US5] 在 `BackpackTrashSendPayload` 里实现 `MENU_SLOT` 的 `collect`（contracts 第 2 节步骤 4）：`containerId` 等于 `player.containerMenu.containerId`；菜单是 `player.inventoryMenu` 或 `ChestMenu`；`MenuOwner.ownerOf(menu)` 是 `minecraft`；`ServerCompatGate.allows(Action.TAKE_FROM_CONTAINER, owner)`；菜单格子号 ∈ `[0, menu.slots.size())`；由 `slot.container` 推出 `ownInventory`（`== player.getInventory()`）与 `chestOwnSlot`（`== chestMenu.getContainer()`）后过 `DeletableSlots.allowed(ownInventory, slot.getContainerSlot(), chestOwnSlot)`；`slot.hasItem()` 且 `slot.mayPickup(player)`；返回这一个栈与"`slot.remove(count)` + `slot.setChanged()`"的取走动作；**先读 `Slot` 与 `ChestMenu` 源码**确认 `remove` 无需 `onTake`，把结论记到 DELIVERY.md。依赖 T020、T011
- [X] T054 [US5] 修改 `…/client/FloatingPanelEvents.java`（`onScreenKeyPressedPost`）与 `…/client/panel/trash/TrashActions.java`：界面种类细分——`InventoryScreen` → `INVENTORY`，`AbstractContainerScreen` 且菜单是 `ChestMenu` 且 `MenuOwner.ownerOf` 为 `minecraft` → `CHEST`，宿主 → `HOST`，其它 → `OTHER`；`hoverMenuAllowedWithItem` = `getSlotUnderMouse()` 非空、有物品、`DeletableSlots.allowed(slot.container == player.getInventory(), slot.getContainerSlot(), chest 时 slot.container == chestMenu.getContainer())`；目标 `MENU_SLOT` 时 `TrashActions.deleteMenuSlot(containerId, slot.index)` 发 `BackpackTrashSendPayload(MENU_SLOT, false, containerId, [slot.index])`（单格，不需要确认），并取消事件。依赖 T042、T053
- [X] T055 [US5] 运行 `./gradlew compileJava test`
- [X] T056（2026-11-07 用户验证 Q31～Q35、Q37、Q38 通过；**Q36 箱子里 Delete 后另一位玩家那边的同步没有条件，记为未验证**，按本任务原文允许） [US5] 按 `quickstart.md` G（Q31～Q38）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：玩家物品栏主物品栏 27 格各试一次；快捷栏、装备栏、副手、合成格 0 次触发 0 次吞键；箱子/大箱子/木桶/末影箱的箱子格子与下方第 9～35 格成功、快捷栏不处理；潜影盒、漏斗、熔炉、创造模式界面菜单格子不触发不吞键但主区域仍可用；**JEI 或配方书搜索框有焦点时按 Backspace 改的是文字、不删物品**；箱子里删除后同步（有第二位玩家才验，否则"未验证"）；光标上拿着物品时只处理悬停格子；面板不可见或右侧区没有垃圾桶时不拦截；结果记到 DELIVERY.md（由用户确认后才打勾）

**Checkpoint**: 三种来源都可用。

---

## Phase 8: User Story 6 - 按键可改、两种平台默认键、标签与兼容不受影响 (Priority: P2)

**Goal**: 改键行为、OFF 界面、宿主键转发、回归。

**Independent Test**: quickstart H（Q39～Q42）。

- [X] T057 [US6] 审阅（读代码，不改行为）：①`PanelHostScreen.boundMappings` 对 `DELETE_TO_TRASH`（GUI 上下文）在"无界面"检查里不活动，所以 Delete 在宿主上是 `PASS`、`Post` 会触发；②所有 Delete/ESC 处理都挂在 `hostsPanel(screen)` 之后，020 的 OFF 界面不生效；③`DeleteKeyRules` 返回 `NONE` 的所有路径都**没有** `setCanceled`；④服务端三种来源的非法请求（越界下标、菜单 id 不匹配、格子不在允许范围、`mayPickup` 为假、菜单不是原版、垃圾桶不在右侧区、没带 `confirmed` 却销毁超过 9 组、体积超限、给含受保护物品的选区加 trash）都整个丢弃且零变化，只补发同步；⑤菜单分支过 `ServerCompatGate`；⑥`BackpackTrashPayload`（021）与确认弹窗不互相干扰；发现缺口就补并记录，没有缺口也记"已审阅，无缺口"
- [ ] T058（2026-11-07 部分：Q39、Q40、Q42 用户验证通过（Q42 的 021/018 回归并入清单 K 组）；统一回归 REG-1～REG-9 已于 2026-11-07 由用户验证通过；**Q41（OFF 的模组界面，会话 S2）仍未做**） [US6] 按 `quickstart.md` H（Q39～Q42）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：控制设置里有"删除到垃圾桶"（中英文名），macOS 默认 Backspace；改成 X 后 X 触发、Backspace 不触发也不被吞；改成 W 时宿主上的表现（记录到已知限制，不要求修复）；OFF 的模组界面上面板、垃圾桶面板、Delete 都不生效；**重跑 021 的垃圾桶本体（点击丢入、拖放、拿回、Ctrl 恢复、限放 1 个）与 011～020 的验证项**；联机无条件则写"未验证"；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI：Q41 的 Delete 在 OFF 界面上不生效、面板与垃圾桶面板不画、热键有聊天提示，已验证；"面板开着时切到 OFF 界面"的状态保留没验证，仍未勾

---

## Phase 9: Polish & Cross-Cutting

- [X] T059 [P] 更新文档：`specs/021-trash-can/spec.md` 加一段 022 说明（标题栏现在有两个图标，英文标题改为 "Trash"，倾倒/删除/Delete/确认/保护见 022）；`specs/018-tag-manager/spec.md` 加一句（标签面板对受保护物品拦截"垃圾"，只拦这一个标签）；`docs/MCFloatingBackpack.md` 在 021 说明之后加 022 说明（一键倾倒、删除图标、Delete 键的三种界面、`never_trash` 保护标签与栈级跳过、二次确认、协议 10）；**不要修改 `docs/ideas`**
- [X] T060 先 `pgrep` 确认没有运行中的客户端，再跑 `./gradlew compileJava test`（退出码 0）与 `design/demo/tools/check_atlas_budget.sh`，图集应仍为 2048×1024（宪法 VI；本 feature 不新增任何贴图），结果记到 DELIVERY.md——**跑不了就如实写"未度量"，不要写成通过**；跑完确认没有残留的客户端进程
- [X] T061 静态检查：`git diff --stat` 确认没有改动 `PanelSelectionInput`、`PanelDragGesture`、`QuickTransferRules`、`TrashBin`、`BackpackTrashPayload`（021 的请求包）的逻辑；`neoforge.mods.toml` 与 `build.gradle` 无改动；协议版本只在 `BackpackNetwork` 一处为 `"10"`；`grep -rn "setScreen(" src/main/java` 与 021 一致（宿主管理器与宿主，没有新增）；`grep -rn "p3pp3rf1y\|tiviacz\|mezz" src/main/java` 只命中 `compat/jei/` 与 `ContainerScreenCompat` 里原有的类名字符串；`DependencyRulesTest` 通过；`grep -rn "setCanceled" …/client/FloatingPanelEvents.java` 逐处核对 Delete 分支只在目标非 `NONE` 时取消
- [X] T062 新建 `specs/022-trash-delete-tools/DELIVERY.md`（中文）：汇总自动化结果（T001 基线与各阶段编译与测试结果、新增测试类与用例数）、与 plan/tasks 的偏离、FR-008 措辞的处理结果、T053 读到的 `Slot`/`ChestMenu` 结论、栈级跳过的实测覆盖面（T035）、**每个客户端验证项实际验证了什么 / 未验证什么**、图集度量结果或"未度量"、已知限制（Windows/Linux 未验证；联机与箱子多人同步；Delete 改到与世界动作冲突的键时宿主会先吞掉；确认被服务端拒绝时只给聊天提示、不自动重新弹）

---

## Phase 10: 验证后发现的问题

- [X] T063 用户验证时发现：点击浮动面板不会让 JEI 搜索框失去焦点，只有点原版物品栏才会。原因读 JEI 19.70 源码确认：JEI 的搜索框焦点由它在**运行时**注册的 `ScreenEvent.MouseButtonPressed.Pre`/`MouseButtonReleased.Pre`（默认优先级，`GuiInputController.mouseReleased` 里"松开点不在搜索框上就 `clearFocus`"）管理，而我们的同名处理先注册、先取消了事件，JEI 收不到，焦点就留着。修复（**与模组无关，不涉及 JEI 类型，所以不需要兼容门禁**）：`…/client/FloatingPanelEvents.java` 的 `onScreenMousePressed` 里，面板消费了按下就先调 `event.getScreen().clearFocus()`（原版屏幕"点了别处就失去焦点"的那一步，会对当前焦点路径上的控件 `setFocused(false)`），再取消事件。受 `hostsPanel(screen)` 控制，所以 020 的 OFF 界面不受影响
- [X] T064 在 `./gradlew runClient` 验证（先 `pgrep`）：JEI 搜索框有焦点时点浮动面板（主区域、工具区、标题栏、垃圾桶面板）→ 搜索框失去焦点，之后按键不再进搜索框；原版配方书搜索框同理；点 JEI 搜索框本身仍能聚焦；创造模式物品栏的搜索框同理；结果记到 DELIVERY.md（由用户确认后才打勾）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1（Setup）**：无依赖，先做。
- **Phase 2（Foundational）**：依赖 Phase 1。T002～T007、T014、T016、T018 可并行写测试；T008～T013、T015 可并行实现；T017 依赖 T016；T019 依赖 T018；T020 依赖 T008、T012、T017；T021 最后。**阻塞所有用户故事。**
- **Phase 3（US1）**：依赖 Phase 2。T022 → T023；T024 依赖 T009/T010/T015/T020；T025 依赖 T020；T026 依赖 T023；T027 依赖 T024/T026；T028 → T029。
- **Phase 4（US2）**：依赖 Phase 2（`ModTags`、`TagProtection`）；与 US1 互相独立（倾倒的保护已在 T025 里用 `ModTags`）。T030 → T031 → T032；T033 依赖 T013/T017；T034 → T035。
- **Phase 5（US3）**：依赖 US1（图标与 `TrashActions`）和 Phase 2。T036 → T037；T038 依赖 T019；T039 依赖 T024；T040 依赖 T020；T041 依赖 T039；T042 依赖 T037/T038/T039；T043 → T044。
- **Phase 6（US4）**：依赖 US1/US3（`TrashActions` 打开确认）与 T015。T045 → T046；T047、T048 依赖 T046；T049 依赖 T048；T050 独立；T051 → T052。
- **Phase 7（US5）**：依赖 US3（Delete 事件与规则）。T053 依赖 T020/T011；T054 依赖 T042/T053；T055 → T056。
- **Phase 8（US6）**：依赖前面所有故事的代码。
- **Phase 9（Polish）**：T059 可在 US3 稳定后随时做；T060～T062 在所有故事完成后。

### User Story Dependencies

- US1 → US3 → US4 构成"图标 + 确认"主线；US2 独立；US5 在 US3 之上；US6 汇总验证。

### Parallel Opportunities

- Phase 2：T002～T007 六个测试并行写，T008～T013 六个实现并行；T014 ∥ T016 ∥ T018。
- Phase 3 与 Phase 4 的纯逻辑部分（T022、T030）可同时写；US2 可与 US1 并行推进。
- Phase 6：T045 可与 T036 同时写。

### Parallel Example: Phase 2

```text
同时写测试：T002 TrashDeletePlanTest  T003 TrashDumpTest  T004 StackExtraDataTest  T005 DeletableSlotsTest  T006 TrashSendRequestTest  T007 TagProtectionTest  T014 TrashConfirmStateTest  T016 NeverTrashResourcesTest  T018 TrashToolsResourcesTest
同时实现：  T008 TrashDeletePlan  T009 TrashDump  T010 StackExtraData  T011 DeletableSlots  T012 TrashSendRequest  T013 TagProtection  T015 TrashConfirmState
然后：      T017 ModTags + never_trash.json → T019 语言键 → T020 请求包与流水线 → T021 运行测试
```

---

## Implementation Strategy

### MVP First（US1 → US2 → US3）

1. 完成 Phase 1、Phase 2（纯规则 + 保护标签 + 语言键 + 请求包与流水线，不碰游戏界面）。
2. 完成 US1：倾倒图标与一键倾倒（不超过阈值）。
3. 完成 US2：保护。**倾倒是破坏性操作，US1 验证通过后应尽快做 US2，再让用户大范围试用倾倒。**
4. 完成 US3：删除图标与主区域 Delete。到这里 MVP 成立；让用户验证 T029、T035、T044 后再继续。

### Incremental Delivery

1. MVP（US1 + US2 + US3）→ 用户验证。
2. US4（二次确认）→ 验证（此后才可测大批量倾倒与删除）。
3. US5（原版界面 Delete）→ 验证。
4. US6（改键、兼容与回归）→ 验证。
5. Polish：文档、图集度量、静态检查、交付说明。

### 风险提示（实现时盯住）

- **Delete 的键盘流向**（T042、T054）：`Post` 事件只在界面没处理时触发；`DeleteKeyRules` 返回 `NONE` 时**绝不能取消事件**；在宿主、玩家物品栏、箱子三种界面都要实测；改成与世界动作冲突的键时宿主先吞掉是已知限制。
- **确认期间的 ESC**（T049）：必须在 `KeyPressed.Pre` 里取消，宿主与容器界面才都看不到；先于 018 的编辑分支，但确认期间不会在编辑。
- **服务端权威**：客户端只发来源 + 下标 + 菜单 id + `confirmed`；筛选、销毁数、菜单核对、体积保护全在服务端；任何一步失败整个请求零变化，只补发同步（T020、T025、T040、T053、T057）。
- **物品守恒**：每次放入前后核对总数，只有溢出销毁可以少（T020）。
- **栈级跳过**（T035）：`getComponentsPatch()` 的判断要用真实栈实测；跳过的是"带非默认数据"的栈，磨损的镐要能被倾倒。
- **011～021 不回归**：垃圾桶标题栏、确认弹窗、Delete 都只在分派层与事件层挂接；`BackpackTrashPayload`（021）与 `TrashBin` 一行不改（T061）；018 的标签面板场景要回归（T035）。
- **图集预算**：不新增任何贴图；度量不了就如实写"未度量"（T060）。
