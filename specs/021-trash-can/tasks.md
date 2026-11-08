# Tasks: 第二个扩展工具——垃圾桶（本体）

**Input**: Design documents from `/specs/021-trash-can/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/trash-network-storage.md, contracts/trash-panel-ui.md, quickstart.md；宪法 2.1.1；018（标签管理器与点击分派模式）、019（宿主界面）、020（兼容策略）已实现并提交

**Tests**: 垃圾桶的追加/拿起/恢复、请求校验、"限放 1 个"规则、面板几何、拖放记录器、Ctrl 手势的新分组、配方与语言键，都是不依赖 Minecraft 运行环境的纯逻辑，plan 要求用单元测试覆盖，**测试任务排在对应实现之前**；存储同步、展开/收起、点击与拖放、容器界面上的松开、Ctrl 恢复、渲染手感与图标外观只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础（纯逻辑 + 数据组件）；US1（物品、配方、图标、展开/收起）是入口 MVP；US2（丢入，含协议 9 与服务端 ADD）、US3（拿回）是另外两个 P1，三者合起来才是可用的最小垃圾桶；US4（Ctrl 恢复）、US5（限放 1 个）、US6（持久化、兼容与回归）为 P2。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US6）
- 路径都相对于仓库根目录；下文路径里的 `…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**启动客户端或跑 `check_atlas_budget.sh` 前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，有正在运行的客户端就不要启动、更不要杀用户的游戏**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过
- 包名前缀 `pnnh.mcfloatingbackpack`；测试类路径里只有 JUnit（没有 Minecraft/Gson/Netty），所以涉及 `ItemStack`、编解码的逻辑要放进纯类里测，资源测试用文本方式

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（020 之后 42 个测试类、349 个用例）；结果记到 `specs/021-trash-can/DELIVERY.md`

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: `TrashBin`、`TrashRequest` 是客户端与服务端共用的纯逻辑；`TrashContents` 与 `BackpackContents.trash` 是数据基础。测试先写、先失败。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/trash/TrashBinTest.java`（**先写，先失败**），测试里自带一个简单的 `StackOps<T>`（种类 + 数量 + 最大堆叠），按 data-model.md 第 2 节：`push` 空栈不变；`push` 放第 1 位并让旧的后移；同种物品**不合并**；恰好 9 组再 `push` → 最后一组被挤出并作为 `destroyed`，总数仍 9；未满时 `destroyed` 为空；`take` 中间一组 → 后面的前移、顺序不变；`take` 越界/空组 → 原样；`restore`：下标去重升序、先叠进同种堆叠再填空位、全部放下则该组移除、**部分放下则剩余数量留在原位**、一点放不下原样、越界下标忽略、主区域满时全部留着；`restore` 前后"垃圾桶 + 主区域"总数不变；随机 200 组 `push/take/restore` 后组数 ≤ 9 且守恒（除 `destroyed`）；`sanitize(groups)`（读入时丢弃空栈、截到 9 个）
- [X] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/trash/TrashRequestTest.java`（**先写，先失败**）：`Action.fromByte(0/1/2)` 合法、其它返回 null；`validate`：`ADD` 只接受空下标；`TAKE` 恰好 1 个且 ∈ [0,9)；`RESTORE` 1～9 个、互不相同、∈ [0,9)；null/重复/负数/等于 9/超过 9 个全部拒绝
- [X] T004 [P] 新建 `…/inventory/trash/TrashBin.java`：纯逻辑静态泛型类，建在 `QuickTransferLogic.StackOps<T>` 上；`MAX_GROUPS = 9`；`push`/`take`/`restore`/`sanitize`，规则逐条照 data-model.md；`restore` 复用 `QuickTransferLogic.moveInto`。使 T002 通过
- [X] T005 [P] 新建 `…/inventory/trash/TrashRequest.java`：纯逻辑。`enum Action { ADD, TAKE, RESTORE }`（`toByte`/`fromByte`），`GROUPS = 9`，`validate(Action, int[] slots)`。使 T003 通过
- [X] T006 新建 `…/inventory/trash/TrashContents.java`：记录 `TrashContents(List<ItemStack> groups)`，`EMPTY`，`CODEC`（`ItemStack.OPTIONAL_CODEC.listOf()`，读入时经 `TrashBin.sanitize` 丢弃空栈并截到 9 个）、`STREAM_CODEC`（`ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9))`），构造时复制栈，见 data-model.md。依赖 T004
  - 注：测试类路径没有 Minecraft，编码层（往返、超限抛异常）无法单测，改在客户端会话里验证（登记为未单测），与 018 的 `ItemTags` 同样处理
- [X] T007 修改 `…/inventory/BackpackContents.java`、`…/inventory/BackpackInventory.java`、`…/client/panel/ClientBackpackView.java`、`…/inventory/recipe/BackpackUpgradeRecipe.java`：`BackpackContents(items, cursor, tools, tags, trash)`，`trash` 用 `optionalFieldOf("trash", TrashContents.EMPTY)`，`STREAM_CODEC` 加第 5 个字段（不超过 6）；`normalize/fit/copy/empty` 保留 `trash`；`BackpackInventory` 增加 `trash()/setTrash(TrashContents)`，`fromContents/toContents` 读写；`ClientBackpackView` 增加 `trash()/setTrash(...)`，`clear()` 复位（`hasTrashCan()` 依赖物品注册，放到 T010）；核对 `BackpackUpgradeRecipe` 升级时经 `fit`/`copy` 保留 `trash`（和 018 的 `tags` 同路，不经过就补上）；修正所有现有的 `BackpackContents` 构造调用点与测试。依赖 T006
- [X] T008 运行 `./gradlew compileJava test`，确认 T002、T003 与全部既有测试通过

**Checkpoint**: 编译通过；新增纯逻辑测试全绿；旧背包读成空垃圾桶。

---

## Phase 3: User Story 1 - 垃圾桶放进右侧区才有功能，点击向右展开/收起面板 (Priority: P1) 🎯 MVP 入口

**Goal**: 物品、配方、图标、语言；右侧区里左键展开/收起浮动面板右边缘之外的垃圾桶面板（本阶段面板里只有标题栏和 9 个空凹槽，点击只被消费）；其它区域里它是普通物品。

**Independent Test**: quickstart A（Q1～Q4）、B（Q5～Q11）：合成/创意栏拿到、放进四个区域、只有右侧区里左键展开；展开后在浮动面板右边、顶端对齐；点击被消费；拿走自动收起；与标签面板同时展开。

- [X] T009 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/resources/TrashCanResourcesTest.java`（**先写，先失败**，文本方式，参照 `TagManagerResourcesTest`）：`data/mcfloatingbackpack/recipe/trash_can.json`——`crafting_shaped`、图案 `[" I ", "ICI", " I "]`、`I = minecraft:iron_ingot`、`C = minecraft:cauldron`、结果 `mcfloatingbackpack:trash_can` ×1、不含 `sophisticated`/`travelersbackpack`；`models/item/trash_can.json` 的父模型是 `minecraft:item/generated` 且指向 `mcfloatingbackpack:item/trash_can`；`textures/item/trash_can.png` 存在且 PNG 头宽高为 16×16；`en_us.json` 与 `zh_cn.json` 同时含 `item.mcfloatingbackpack.trash_can`、`trash.mcfloatingbackpack.title`、`message.mcfloatingbackpack.limit.trash_can`、`message.mcfloatingbackpack.limit.tag_manager`、`message.mcfloatingbackpack.trash.too_large`；两份语言文件的键集合相同；中文名"垃圾桶"、英文名 "Trash Can"
- [X] T010 [US1] 新建 `…/item/TrashCanItem.java`（普通 `Item`，堆叠 1，无其它行为，javadoc 说明只有在右侧区里才有功能）；修改 `…/item/ModItems.java` 注册 `trash_can` 并加入本模组创意栏（标签管理器之后），并在 `BackpackInventory` 与 `ClientBackpackView` 里加 `hasTrashCan()`（扫描 `RIGHT` 区**整个列表**，`ItemStack.is(ModItems.TRASH_CAN.get())`，与现有 `hasTagManager()` 并排）；新建 `src/main/resources/data/mcfloatingbackpack/recipe/trash_can.json`、`src/main/resources/assets/mcfloatingbackpack/models/item/trash_can.json`；修改 `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` 与 `zh_cn.json` 加入全部新键（中文：垃圾桶；面板标题"垃圾桶"；"这里只能放一个垃圾桶"/"这里只能放一个标签管理器"；"这件物品的数据太大，放不进垃圾桶"；英文对应）。使 T009 除贴图外的部分通过
- [X] T011 [US1] 生成图标：按 research Decision 13，用 ludo.ai MCP（`mcp__ludo-ai__createImage`，`item-icon`、像素风、铁灰色垃圾桶、原版 Minecraft 物品图标风格、透明或白底）生成候选图，下载到 `design/demo/tools/trash_can_icon/`（**源图与脚本不随模组发布**）；新建 `design/demo/tools/make_trash_can_icon.py`（参照 `make_tag_manager_icon.py`：去背景、裁切、缩放到 16、alpha 二值化、调色板量化）并写入 `src/main/resources/assets/mcfloatingbackpack/textures/item/trash_can.png`；效果不好或额度不够就在同一脚本里手绘 16×16；把生成方式、提示词、来源文件、脚本记入 DELIVERY.md；用 `Read` 打开 PNG 目视检查。依赖 T010
- [X] T012 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/trash/TrashPanelLayoutTest.java`（**先写，先失败**），按 data-model.md 第 8 节：外框左上角 = `(panelX + panelWidth + 1, panelY)`；宽 62、高 72；`titleRect` 为 `(x, y, 62, 14)`；`cellRect(i)` 从左到右、从上到下共 9 个，互不重叠，都在外框内；`cellAt` 命中格子返回 0..8，命中标题栏/内边距/格子之间返回 -1；`contains` 对外框内任意点为真、外框外为假（含左边缘一像素、右下角边界）；浮动面板位置变化时整体平移；与浮动面板的矩形不相交
- [X] T013 [P] [US1] 新建 `…/client/panel/trash/TrashPanelLayout.java`：纯几何（无 Minecraft 类型），常量 `TITLE_H=14`、`PAD=4`、`COLS=3`、`ROWS=3`，`of(panelX, panelY, panelWidth)`，`rect()/titleRect()/cellRect(i)/cellAt(x,y)/contains(x,y)`，格子边长取 `PanelLayout.SLOT`。使 T012 通过
- [X] T014 [P] [US1] 新建 `…/client/panel/trash/TrashPanelState.java`（单例，字段 `open`，`toggle()/collapse()/reset()`，不依赖 Minecraft 类型）与测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/trash/TrashPanelStateTest.java`：`toggle` 在收起/展开间切换，`collapse` 与 `reset` 收起
- [X] T015 [US1] 新建 `…/client/panel/trash/TrashPanelRenderer.java` 并修改 `…/client/panel/FloatingPanelRenderer.java`：展开时在浮动面板内容之后、光标物品之前（同一个 `OVERLAY_Z` 的 `pushPose` 里）画垃圾桶面板——外框和底色用 `drawVanillaPanel` 的配色，标题栏文字 `trash.mcfloatingbackpack.title`（不画图标/按钮），9 个凹槽用 `COLOR_SLOT_RIGHT` 底色和现有的 `drawSlotWell`，悬停格子画 `COLOR_SLOT_HOVER`；常量集中在渲染类顶部，几何全部读 `TrashPanelLayout`（位置取 `FloatingPanelState` 的 `getPosX/getPosY/getWidth`）。本阶段不画物品。依赖 T013、T014
- [X] T016 [US1] 新建 `…/client/panel/trash/TrashPanelInput.java` 并修改 `…/client/panel/FloatingPanelInput.java`：①工具区点击分支里，命中 `RIGHT` 且该格是垃圾桶、左键、光标为空、无 Shift/Ctrl、`PanelDragInput.isActive()` 为假 → `TrashPanelState.toggle()`（不发包）并消费，与 018 的 `BOTTOM + 标签管理器` 并排；右键与光标非空照 016 原样；②`dispatchMouseButton` 的 PRESS 分支最前（在关闭按钮/缩放手柄判断之前）调用 `TrashPanelInput.onPress`：垃圾桶展开且点在外框内时，**左右键按下一律消费**（本阶段只消费，不做别的）。依赖 T013、T014
- [X] T017 [US1] 修改 `…/client/FloatingPanelEvents.java`：①`scrollPanel` 里鼠标在垃圾桶外框内时消费滚轮不做事（要在 `state.contains` 判断之前处理，因为外框在面板矩形之外）；②`onClientTick` 里垃圾桶面板展开但（浮动面板不可见 或 `!ClientBackpackView.get().hasTrashCan()`）→ `collapse()`；③`onLoggingIn/onLoggingOut` 与 `closePanel` 里收起/重置；渲染与输入都已挂在 `hostsPanel(screen)` 之后，确认 OFF 界面上不画不响应。依赖 T014、T016
- [X] T018 [US1] 修改 `…/compat/jei/FloatingBackpackJeiPlugin.java`：`getGuiExtraAreas` 在已有的浮动面板矩形之外，当 `TrashPanelState.get().isOpen()` 时再追加垃圾桶外框的 `Rect2i`（来自 `TrashPanelLayout.of(...)`）；仍由 `CompatConfig.policy().specificAdaptation("jei")` 控制。依赖 T013、T014
- [X] T019 [US1] 运行 `./gradlew compileJava test`，确认 T009（贴图部分在 T011 后）、T012、T014 与全部既有测试通过
- [X] T020（2026-11-07 用户逐项验证 Q1～Q3、Q5～Q11 通过；Q4 图集度量已由 Claude 代跑通过，见 DELIVERY） [US1] 按 `quickstart.md` A（Q1～Q4 中除图集度量外）、B（Q5～Q11）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：配方与 JEI 里的配方、贴图外观；右侧区左键展开/收起、右键拿起、光标非空时的普通点击、其它三个区域里只是普通物品；展开位置与对齐、没有图标按钮；拿走/换出/关闭浮动面板自动收起；与标签面板同时展开；点击与滚轮不穿透；结果记到 DELIVERY.md（**由用户确认后才打勾**）
  - **2026-11-05 用户反馈**：已进游戏验证过基本功能，没有问题；**未做全面验证**（没有逐项对照 quickstart），本任务保持未勾选，逐项结果在 DELIVERY.md 标"未验证"

**Checkpoint**: 可以拿到并摆放垃圾桶，面板能展开收起，但还不能丢东西。

---

## Phase 4: User Story 2 - 把光标上的物品丢进垃圾桶（点击与拖放） (Priority: P1)

**Goal**: 协议 9、服务端 `ADD`、同步；点击垃圾桶丢入；拖放（松开在垃圾桶上）在宿主界面与容器界面都可用；满 9 组挤出最旧的并记日志；体积保护。

**Independent Test**: quickstart C（Q12～Q14）、D（Q16～Q21）、E（Q22～Q23）、H（Q41～Q42）。

- [X] T021 [P] [US2] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/trash/TrashDropTrackerTest.java`（**先写，先失败**），按 data-model.md 第 9 节：按下在外 + 松开在内 + 左键 → 构成拖放；按下在内 → 不构成；右键/中键不构成；松开后记录清除（再松开不构成）；两个键互不影响；`clear` 清空；未记录过的键松开 → 不构成
- [X] T022 [P] [US2] 新建 `…/client/panel/trash/TrashDropTracker.java`：纯逻辑（无 Minecraft 类型），`press(button, onTrash)`、`release(button, onTrash)`、`clear()`。使 T021 通过
- [X] T023 [US2] 新建 `…/network/BackpackTrashContentsPayload.java`（服务端→客户端，`TrashContents`，`handleClient` 调 `ClientBackpackView.setTrash`）与 `…/network/BackpackTrashPayload.java`（客户端→服务端，`action` 字节 + `slots` VarInt 列表 + `screenOpen`，解码时数量 > 9 抛 `DecoderException`，`of(Action, int[], boolean)`）；修改 `…/network/BackpackNetwork.java`：协议 `"8"` → `"9"`，注册两个包，`sendSession(open)` 里紧跟标签包发垃圾桶内容，新增 `sendTrash(player, inventory)` 辅助。`handleServer` 的骨架：解析/核对背包 → `hasTrashCan()` → `TrashRequest.validate` → 按动作分派（本任务只留分派结构，具体动作在 T024/T031/T037 逐个接上，未实现的动作先忽略）；任何忽略路径都补发垃圾桶内容与光标同步。依赖 T005、T006、T007
- [X] T024 [US2] 在 `BackpackTrashPayload.handleServer` 实现 `ADD`（contracts 第 3 节步骤 4～8）：光标来源 `PanelCursor.of(screenOpen, menu, backpack)`，菜单分支先过 `ServerCompatGate.allows(Action.USE_MENU_CURSOR, MenuOwner.ownerOf(menu))` 再 `BackpackMenuBridge.mergePanelCursorIntoCarried`；光标为空 → 忽略；**体积保护**：用 `ItemStack.OPTIONAL_STREAM_CODEC` 把光标栈试编码进临时 `RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess())`，字节数 > `MAX_GROUP_ENCODED_BYTES`（常量放在 `TrashContents` 旁，初值 96 KiB，Q42 测量后调整）→ 忽略并给玩家一条 `message.mcfloatingbackpack.trash.too_large` 聊天提示；否则 `TrashBin.push`（生产适配器 `ItemStackTransferOps.INSTANCE`），光标置空，`destroyed` 非空则 `MCFloatingBackpack.LOGGER.debug("Trash can destroyed {} x{} (player {})", …)`；守恒核对（前后"垃圾桶 + 光标 + 主区域"总数，除 `destroyed` 外必须相等，不等记 `LOGGER.error`）；保存；`viaMenu` 时 `menu.broadcastChanges()`；发整份垃圾桶内容与 `sendSlotUpdate(player, ref, -1)`。依赖 T023
- [X] T025 [US2] 修改 `…/client/panel/trash/TrashPanelInput.java`：`onPress` 里光标非空（`FloatingPanelRenderer.cursorStack(minecraft)`）时点在外框内任意位置 → 发 `BackpackTrashPayload.of(ADD, [], screenOpen)`（`screenOpen = ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)`）；按住 Shift 或 `PanelDragInput.isActive()` 时只吞掉不发；右键只吞掉。修改 `TrashPanelRenderer`：按 `ClientBackpackView.trash()` 画 9 格里的物品与数量（`renderItem` + `renderItemDecorations`，最新的在第 1 格）。依赖 T015、T016、T023
- [X] T026 [US2] 实现松开在垃圾桶上的拖放（research Decision 8、contracts 第 4 节）。**先读** `build/moddev/artifacts/neoforge-21.1.251-sources.jar` 里的 `AbstractContainerScreen.mouseClicked/mouseDragged/mouseReleased`，确认"光标为空按下立即拿起并 `skipNextRelease`""光标非空按下进入 `isQuickCrafting`、松开时点击松开位置或 `-999` 扔出""携带物为空时松开是空操作"这些假设，把读到的结论（与假设不一致处）写进 DELIVERY.md；再实现：①`PanelDragInput` 增加公开的 `cancel()`（即现有 `stop()` 的公开入口）；②`FloatingPanelInput.onMouseButton` 的 PRESS 入口对所有被分派到的按下调用 `TrashDropTracker.press(button, onTrash)`（含未被消费、落在原版界面格子上的按下）；`resetPressTracking()`、面板隐藏、登出时 `clear()`；③RELEASE 分支**最前面**（早于选区、013、面板拖动、缩放）调用 `TrashPanelInput.onRelease`：左键、记录为"按下在外"、松开点在外框内、无 Shift/Ctrl、垃圾桶展开时 → `PanelDragInput.cancel()`、发 `ADD`、客户端预测清空光标（菜单携带物 `menu.setCarried(ItemStack.EMPTY)`，或面板光标 `ClientBackpackView.applySlotUpdate(MAIN, -1, EMPTY, EMPTY)`）；返回"是否吞掉"：按下被面板消费过（`PRESSES` 里有）→ 吞掉，否则（按下落在原版界面格子上）→ **放行**。依赖 T022、T024、T025
- [X] T027 [US2] 运行 `./gradlew compileJava test`，确认 T021 与全部既有测试通过；`grep -rn "setScreen(" …/client/panel/trash` 应无命中（本 feature 不新增任何 Screen）
- [X] T028（2026-11-07 用户逐项验证 Q12～Q14、Q16～Q23、Q41、Q42 通过；Q42 用塞满的下界合金背包、皮革背包、旅行者背包都没触及体积上限，实测上限未确定） [US2] 按 `quickstart.md` C（Q12～Q14）、D（Q16～Q21）、E（Q22～Q23）、H（Q41～Q42）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：宿主界面与容器界面（箱子、玩家物品栏）下点击丢入；主区域/工具区/箱子/物品栏按下后拖到垃圾桶松开；原版没有卡在拖动分配状态、物品没有被扔出或落进被遮住的格子；快速点击拿起原处松开不算；在垃圾桶上按下松开只丢一次；满 9 组挤出最旧的一组且服务端日志有调试行；嵌套背包/潜影盒数据完整；极端大物品被体积保护拒绝时有提示，并记录实测上限；若 Q18/Q19 在容器界面上仍有残留状态，**按 research Decision 8 的退路处理并向用户报告，不要自作主张降级**；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **2026-11-05 用户反馈**：已进游戏验证过基本功能，没有问题；**未做全面验证**（没有逐项对照 quickstart），本任务保持未勾选，逐项结果在 DELIVERY.md 标"未验证"

**Checkpoint**: 可以把东西丢进垃圾桶（点击与拖放），只留最近 9 组。

---

## Phase 5: User Story 3 - 把丢掉的物品拿回来（撤销） (Priority: P1)

**Goal**: 光标为空时点击有物品的垃圾格 → 拿起到光标，后面的前移；其余点击规则按 spec。

**Independent Test**: quickstart C（Q15）。

- [X] T029 [US3] 在 `BackpackTrashPayload.handleServer` 实现 `TAKE`：光标来源同 T024；光标非空 → 忽略；`TrashBin.take`，没有那一组 → 忽略；否则光标 = 被拿起的那一组（`PanelCursor.set`）；守恒核对、保存、`viaMenu` 时 `broadcastChanges`、发垃圾桶内容与光标同步。依赖 T024
- [X] T030 [US3] 修改 `…/client/panel/trash/TrashPanelInput.java`：光标为空且无 Shift/Ctrl、点在有物品的格子上（`TrashPanelLayout.cellAt` ≥ 0 且 `ClientBackpackView.trash()` 该格非空）→ 发 `TAKE`（该下标）；点在空格子/标题栏/空隙 → 吞掉不发；右键吞掉。依赖 T025、T029
- [X] T031 [US3] 运行 `./gradlew compileJava test`
- [X] T032 [US3] 按 `quickstart.md` C（Q15）在 `./gradlew runClient` 验证（先 `pgrep`）：拿起第 2 格后第 3 格前移；点空格无反应；光标非空点有物品的格子是追加不是交换；右键无效；拿起的物品可以放到主区域、其它工具区、容器里，内容与丢掉时完全一致；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **2026-11-05 用户确认**：已在游戏里实际验证，没有问题（按用户口头确认记录，没有逐项对照清单的明细）

**Checkpoint**: 丢入与拿回都可用，垃圾桶本体成立。

---

## Phase 6: User Story 4 - Ctrl 把垃圾桶里的组恢复到主区域 (Priority: P2)

**Goal**: 垃圾格上 Ctrl+点击/拖动把组移到主区域，放不下的留原位；不支持反方向；011/012 不回归。

**Independent Test**: quickstart F（Q24～Q29）。

- [X] T033 [P] [US4] 扩展测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferGestureTest.java`（**先写，先失败**）：`Side.TRASH` 起点只接受 `TRASH` 格子；`INVENTORY/CONTAINER` 起点不接受 `TRASH`、`PANEL` 起点不接受 `TRASH`，反之亦然；`TRASH` 格子一次拖动内最多报告一次；沿路径采样不跳格；既有用例全部保持通过
- [X] T034 [US4] 修改 `…/client/panel/QuickTransferGesture.java`：`Side` 增加 `TRASH`；把 `intoBackpack(Side)` 的二值判断换成"组号"比较（`INVENTORY`/`CONTAINER` 同组、`PANEL`、`TRASH` 各自一组）。使 T033 通过，且 011/012 的既有用例不变。依赖 T033
- [X] T035 [US4] 在 `BackpackTrashPayload.handleServer` 实现 `RESTORE`：不碰菜单光标；至少一个下标有组才继续；`TrashBin.restore`，目标顺序 = 主区域 `0..size-1`，读写用 `backpack.getStackInSlot/setStackInSlot`；守恒核对（垃圾桶 + 主区域）、保存、发垃圾桶内容与 `sendFullSync`。依赖 T024
- [X] T036 [US4] 修改 `…/client/panel/QuickTransferInput.java` 与 `…/client/panel/trash/TrashPanelInput.java`：`beginFromTrash(index, x, y)`（Ctrl 按住、Shift 没按、光标为空、该格有物品、垃圾桶展开）立即报告起点；`resolveAt` 先判断垃圾桶外框，命中某格返回 `Hit(Side.TRASH, i, hasItem)`，外框内的非格子部分返回 null，再走原有逻辑；`send` 把 `TRASH` 下标发成 `BackpackTrashPayload.of(RESTORE, …, false)`；`invalid` 条件不变（光标变非空、界面变了、面板隐藏、松开 Ctrl、按下 Shift 都结束）；`TrashPanelInput.onPress` 在光标为空 + Ctrl 时转入该手势，光标非空时 Ctrl+点击仍是追加。依赖 T034、T035、T025
- [X] T037 [US4] 运行 `./gradlew compileJava test`，确认 T033 与全部既有测试通过；`git diff` 确认 `QuickTransferRules`、`QuickTransferGesture` 对 011/012 的既有路径无行为改动
- [X] T038 [US4] 按 `quickstart.md` F（Q24～Q29）在 `./gradlew runClient` 验证（先 `pgrep`）：叠进已有堆叠再填空格；主区域只剩一个空格时能放的先放、放不下的留原位且顺序正确；主区域已满无反应；开着箱子时可用且箱子不变；主区域里 Ctrl+点击仍是 011/012 的行为、光标非空 Ctrl+点垃圾桶是追加；重跑 011/012 的 Ctrl 转移验证项；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **2026-11-05 用户确认**：已在游戏里实际验证，没有问题（按用户口头确认记录，没有逐项对照清单的明细）

**Checkpoint**: 批量恢复可用。

---

## Phase 7: User Story 5 - 右侧区最多 1 个垃圾桶，下方区最多 1 个标签管理器 (Priority: P2)

**Goal**: 服务端放入规则 + 客户端预判与聊天提示。

**Independent Test**: quickstart G（Q30～Q35）。

- [X] T039 [P] [US5] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/ToolAreaRulesTest.java`（**先写，先失败**），用 `String` 当物品并传入判定谓词，覆盖 data-model.md 第 4 节表格的每一行：右侧区空放垃圾桶放行；已有 1 个放别的格子拒绝；放在它那一格放行；放在装着别的物品的格子拒绝；下方区同样三种；遗留 2 个标签管理器时放第 3 个拒绝、放在其中一个那一格放行（不增加）；主区域/左区放垃圾桶放行；垃圾桶放进下区、标签管理器放进右侧区放行；右侧区放别的物品放行；`slotIndex` 越界按"没有被替换"处理
- [X] T040 [US5] 修改 `…/inventory/ToolAreaRules.java`：`canPlace(area, incoming, slotIndex, areaSlots, isTrashCan, isTagManager)` 纯泛型函数，常量 `LIMIT_PER_AREA = 1`，规则照 data-model.md（放行当且仅当 `after <= 1 || after <= before`）；更新类 javadoc。使 T039 通过。依赖 T039
- [X] T041 [US5] 修改 `…/network/BackpackClickPayload.java`：调用点换成新签名——`held` 沿用现有取法（菜单携带物 / 主手 / 面板光标），`areaSlots` 用 `inventory.handler(area)` 取出的整个区域内容，`isTrashCan/isTagManager` 用 `ModItems`；被拒绝时沿用"重发该格更新并返回"。修改 `…/client/panel/FloatingPanelInput.java` 的工具区分支：发请求前用同一个纯函数在客户端预判（`held` 取 `FloatingPanelRenderer.cursorStack` 或 Alt 时的主手），拒绝时 `FloatingPanelEvents.chat(minecraft, Component.translatable("message.mcfloatingbackpack.limit.trash_can" 或 ".tag_manager"))`、不发请求、消费这次按下。依赖 T010、T040
- [X] T042 [US5] 运行 `./gradlew compileJava test`，确认 T039 与全部既有测试通过；`grep -rn "canPlace(" …` 确认只有 `BackpackClickPayload` 与客户端预判两处调用，签名全部更新
- [X] T043 [US5] 按 `quickstart.md` G（Q30～Q35）在 `./gradlew runClient` 验证（先 `pgrep`）：右侧区第二个垃圾桶被拒绝并提示（中英文）；同格交换允许、点装着别的物品的格子被拒绝；下方区同理；主区域/左区放多个允许、垃圾桶进下区/标签管理器进右侧区允许；018 遗留的 2 个标签管理器不被删、不能放第 3 个；Alt 交换主手同样受限；另在 DELIVERY.md 注明服务端拒绝路径用了什么手段验证（客户端预判会先拦住，难以构造过期状态时记"仅单元测试覆盖"）；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **2026-11-05 用户确认**：已在游戏里实际验证，没有问题（按用户口头确认记录，没有逐项对照清单的明细）

**Checkpoint**: 两条限放规则生效。

---

## Phase 8: User Story 6 - 内容跟着背包走，且不影响现有功能 (Priority: P2)

**Goal**: 持久化、隔离、升级、旧背包、兼容界面、窗口缩窄、回归。

**Independent Test**: quickstart H（Q36～Q40）、I（Q43～Q46）、J（Q47～Q48）、K（Q49）。

- [X] T044 [US6] 审阅（读代码，不改行为）：①`BackpackUpgradeRecipe` 升级后 `trash` 保留（T007 已核对，再确认）；②`BackpackContents.normalize/fit` 在容量缩小时不丢 `trash`；③读入旧存档（无 `trash` 字段）为 `TrashContents.EMPTY`；④服务端所有非法请求路径（越界下标、光标为空却 `ADD`、光标非空却 `TAKE`、垃圾桶不在右侧区却发请求、`RESTORE` 下标全空、未知动作、`ADD` 带下标）都整个丢弃且背包数据零变化，只补发同步；⑤菜单分支都过 `ServerCompatGate`；发现缺口就补并记录，没有缺口也记"已审阅，无缺口"
- [ ] T045（2026-11-07 部分：Q36～Q40、Q43、Q45、Q46、Q48 已由用户验证通过；Q47（统一回归 REG-1～REG-9）已于 2026-11-07 由用户验证通过；Q44（OFF 的模组界面，会话 S2）、Q49（联机，没有条件，未做）仍待） [US6] 按 `quickstart.md` H（Q36～Q40）、I（Q43～Q46）、J（Q47～Q48）、K（Q49）在 `./gradlew runClient` 验证（先 `pgrep`）：退出重进/关闭浮动面板内容仍在；垃圾桶取走再放回内容还在；两只背包互不共享；锻造台升级保留；旧背包为空不报错；JEI 在 FULL 下避让、GENERIC/OFF 下不避让；OFF 的模组界面上垃圾桶面板整体隐藏且状态保留；窗口缩窄/面板靠右时被裁剪、拉宽恢复；右侧区滚动/缩小后垃圾桶不在可见格子里面板不收起；**重跑 011～020 的 quickstart 中与面板有关的场景**（点击、013 拖动均分与收集、014 选区、Ctrl 转移、缩放、滚动、标签管理器与标签面板、兼容开关、宿主界面键盘）；联机无条件则写"未验证"；结果记到 DELIVERY.md（由用户确认后才打勾）

---

## Phase 9: Polish & Cross-Cutting

- [X] T046 [P] 更新文档：`specs/016-panel-tool-areas/` 与 `specs/018-tag-manager/` 的 spec 加一段 021 说明（右侧区限放 1 个垃圾桶、下方区限放 1 个标签管理器，`ToolAreaRules` 不再恒为真）；`specs/013-panel-drag-split-gather/` 与 `specs/014-panel-selection/` 的 spec 加一句"松开在垃圾桶上取消手势、不结算"；`docs/MCFloatingBackpack.md` 在 020 说明之后加 021 说明（垃圾桶、最近 9 组、背包数据第 5 个字段、协议 9）；**不要修改 `docs/ideas`**
- [X] T047 按 `quickstart.md` Q4：`./gradlew compileJava test` 退出码 0；先 `pgrep` 确认没有运行中的客户端，再跑 `design/demo/tools/check_atlas_budget.sh`，图集应仍为 2048×1024（宪法 VI；本 feature 只新增一张 16×16 物品贴图），结果记到 DELIVERY.md——**跑不了（有客户端在运行或脚本失败）就如实写"未度量"，不要写成通过**
- [X] T048 静态检查：`git diff --stat` 确认没有改动 `PanelSelectionInput`、`PanelDragGesture`、`QuickTransferRules` 的逻辑，`PanelDragInput` 只增加 `cancel()`，`QuickTransferInput/Gesture` 只增加 `Side.TRASH` 相关；`grep -rn "p3pp3rf1y\|tiviacz\|mezz" src/main/java` 只有 `compat/jei/` 命中；`grep -rn "setScreen(" src/main/java` 与 020 一致；`neoforge.mods.toml` 与 `build.gradle` 无改动；`DependencyRulesTest` 通过；协议版本只在 `BackpackNetwork` 一处改为 `"9"`
- [X] T049 新建 `specs/021-trash-can/DELIVERY.md`（中文）：汇总自动化结果（T001 基线与各阶段编译与测试结果、新增测试类与用例数）、与 plan/tasks 的偏离、T026 读到的原版 `AbstractContainerScreen` 鼠标流程结论与最终采用的做法、体积保护的实测上限、**每个客户端验证项实际验证了什么 / 未验证什么**、图标的生成方式与来源（提示词、脚本、是否退回手绘）、图集度量结果或"未度量"、已知限制（Windows/Linux 未验证；联机；极端大物品被体积保护拒绝）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1（Setup）**：无依赖，先做。
- **Phase 2（Foundational）**：依赖 Phase 1。T002、T003 可并行写测试；T004、T005 可并行实现；T006 → T007 → T008。**阻塞所有用户故事。**
- **Phase 3（US1）**：依赖 Phase 2。T009 ∥ T012 ∥ T014；T010 → T011；T013 依赖 T012；T015、T016 依赖 T013/T014；T017、T018 依赖 T014/T016；T019 → T020。
- **Phase 4（US2）**：依赖 US1（面板与点击消费）和 Phase 2。T021 ∥ T023；T022 依赖 T021；T024 依赖 T023；T025 依赖 T015/T016/T023；T026 依赖 T022/T024/T025；T027 → T028。
- **Phase 5（US3）**：依赖 US2（请求包、同步、`TrashPanelInput`）。T029 → T030 → T031 → T032。
- **Phase 6（US4）**：依赖 US2（请求包、服务端骨架）；与 US3 互相独立。T033 → T034；T035 依赖 T024；T036 依赖 T034/T035/T025；T037 → T038。
- **Phase 7（US5）**：只依赖 Phase 2 与 US1 的物品注册（T010）；与 US2～US4 互相独立，可以在 US1 之后任何时候做。T039 → T040 → T041 → T042 → T043。
- **Phase 8（US6）**：依赖前面所有故事的代码。
- **Phase 9（Polish）**：T046 可在 US1 稳定后随时做；T047～T049 在所有故事完成后。

### User Story Dependencies

- US1 → US2 → US3 构成最小可用垃圾桶；US4 在 US2 之上，US5 独立，US6 汇总验证。

### Parallel Opportunities

- Phase 2：T002 ∥ T003，T004 ∥ T005。
- Phase 3：T009 ∥ T012 ∥ T014。
- Phase 4：T021 ∥ T023。
- Phase 6 与 Phase 7 的纯逻辑部分（T033、T039）可同时写。

### Parallel Example: Phase 2

```text
同时写测试：T002 TrashBinTest  T003 TrashRequestTest
同时实现：  T004 TrashBin  T005 TrashRequest
然后：      T006 TrashContents → T007 BackpackContents/Inventory/ClientBackpackView/升级配方 → T008 运行测试
```

---

## Implementation Strategy

### MVP First（US1 → US2 → US3）

1. 完成 Phase 1、Phase 2（纯逻辑 + 数据组件，不碰游戏界面）。
2. 完成 US1：物品、配方、图标、展开/收起——此时可以在游戏里看到垃圾桶面板，但还不能丢东西。
3. 完成 US2：协议 9、服务端 ADD、点击与拖放丢入。**到这里要先让用户验证 T028（尤其是容器界面上的拖放）再继续。**
4. 完成 US3：拿回。到这里才是可用的最小垃圾桶；用户验证 T020、T028、T032 后再继续。

### Incremental Delivery

1. MVP（US1 + US2 + US3）→ 用户验证。
2. US4（Ctrl 恢复）→ 验证。
3. US5（限放 1 个）→ 验证。
4. US6（持久化、兼容与回归）→ 验证。
5. Polish：文档、图集度量、静态检查、交付说明。

### 风险提示（实现时盯住）

- **容器界面上的拖放松开**（T026）：先读原版源码；方案是"先 ADD、客户端清光标、再放行松开"。不要拦截原版的松开事件，也不要反射改原版字段；方案走不通时按 research Decision 8 的退路并问用户。
- **服务端权威**：客户端只发动作 + 垃圾格下标 + `screenOpen`；光标来源、垃圾桶是否在右侧区、体积保护全在服务端判断；任何一步校验失败整个请求零变化，只补发同步（T023、T024、T044）。
- **物品守恒**：每个动作前后核对总数，只有溢出销毁可以少（T024、T029、T035）。
- **协议升级**：`"8"` → `"9"` 只在 `BackpackNetwork` 改一处；`BackpackContents.STREAM_CODEC` 增加第 5 个字段，不超过 `composite` 的 6 个上限（T007、T023）。
- **011～020 不回归**：垃圾桶面板对输入的消费只发生在 `FloatingPanelInput` 的分派层；013/014/011 的输入类只加 `cancel()` 与 `Side.TRASH`（T048）；唯一的有意行为变化是限放规则与"松开在垃圾桶上取消 013 手势"。
- **图集预算**：只新增一张 16×16 物品贴图；度量不了就如实写"未度量"（T047）。
