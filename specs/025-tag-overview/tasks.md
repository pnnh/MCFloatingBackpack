# Tasks: 标签总览——查看与管理这只背包给哪些物品设置过哪些标签

**Input**: Design documents from `/specs/025-tag-overview/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/overview-network.md, contracts/overview-ui.md, quickstart.md；宪法 2.1.1；018（标签面板、标签规则、输入与布局组件）、022（保护规则与"垃圾"标签）、024（互斥关系）已实现

**Tests**: 清空（`TagBook.clear`）、请求校验、增/删/清空的规则、下拉候选、列表行模型（筛选/排序）、行内确认状态机、视图状态转移、列表页几何与命中、语言键，都是不依赖 Minecraft 运行环境的纯逻辑，plan 要求用单元测试覆盖，**测试任务排在对应实现之前**；视图切换、列表与悬停、筛选、明细页编辑、行内确认的手感、持久化、回归只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础（全部纯逻辑 + 语言键 + `hasTagManager`）；US1（菜单图标与视图切换）、US2（列表页）、US3（按标签筛选）、US4（明细页与服务端请求，含协议 13）是四个 P1，合起来才是完整的总览；US5（清空，需要 US4 的请求）、US6（持久化与回归）为 P2。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US6）
- 路径都相对于仓库根目录；下文路径里的 `…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…/tags/` 表示 `…/client/panel/tags/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑 `check_atlas_budget.sh` 前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，有正在运行的客户端就不要启动、更不要杀用户的游戏**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过
- 包名前缀 `pnnh.mcfloatingbackpack`；测试类路径里只有 JUnit（没有 Minecraft/Gson/Netty），所以涉及 `ResourceLocation`、`ItemStack`、编解码的逻辑要放进纯类里测（纯类用泛型 `K` 或 `String` 代替 `ResourceLocation`），资源测试用文本方式
- **不改 018/022/024 已有行为**：对 `TagPanelRenderer`、`TagPanelInput` 只加图标与视图分支；`TagPanelState`、`TagPanelLayout`、`TagPanelModel`、`TagPanelContext`、`BackpackTagPayload` 不改

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码、测试类数量与用例数（024 之后为 69 个类、515 个用例）；结果记到 `specs/025-tag-overview/DELIVERY.md`（新建，中文）

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 总览的规则、状态、模型、几何都是纯逻辑，客户端与服务端共用；先写测试、先失败。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/TagBookClearTest.java`（**先写，先失败**）：`clear` 一个存在的分类 → 该分类消失、其它分类不变；`clear` 多个；`clear` 不存在的分类 → 原内容；`clear` 返回新书，原书不变；清空后 `tagsOf` 为空
- [X] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/TagOverviewRequestTest.java`（**先写，先失败**）：`Op.fromByte(0/1/2)` = ADD/REMOVE/CLEAR，其它（-1、3）返回 `null`；`toByte` 往返；`validate`：`op == null` 拒绝；`itemId` 为 `null`/空串/长度 > 256 拒绝；`ADD`/`REMOVE` 的 `tag` 不满足 `TagRules.isValid`（空、含大写、含空格、超长）拒绝；`CLEAR` 的 `tag` 必须为空串（非空拒绝）；合法组合通过
- [X] T004 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/TagOverviewRulesTest.java`（**先写，先失败**，用 `TagBook<String>`），按 data-model.md 第 3 节逐行：`ADD` 对没有标签的分类 → `null`；`ADD` 已不在注册表（`registered=false`）→ `null`；`ADD trash` 且 `isProtected=true` → `null`，而 `ADD stone` 且 `isProtected=true` → 成功；`ADD` 已有该标签 → 返回原书（幂等）；`ADD` 已有 10 个标签 → `null`；`ADD` 正常 → 追加在末尾；`REMOVE` 分类或标签不存在 → 原书；`REMOVE` 最后一个标签 → 分类从书里消失；`CLEAR` 存在 → 分类消失；`CLEAR` 不存在 → 原书；`CLEAR` 对 `isProtected` 与 `registered=false` 的分类仍成功；所有方法不改动传入的书
- [X] T005 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/TagCandidatesTest.java`（**先写，先失败**）：预设在前、已用标签在后、各自保持顺序、去重；`exclude` 里的标签被排除；预设与已用重复只出现一次；全空输入返回空列表
- [X] T006 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/TagOverviewModelTest.java`（**先写，先失败**，用 `TagBook<String>` 与假的 `nameOf/idOf`）：`rows` 只含有标签的分类；`filter` 非空时只含带该标签的分类；`filter` 为 `null`/空 → 全部；排序按 `name` 忽略大小写、同名按 `idOf`；`name` 为注册 id 字符串的分类（已不在游戏里）照常出现；行的 `tags` 与书里一致；空书 → 空列表
- [X] T007 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/ClearConfirmTest.java`（**先写，先失败**，时间用传入的毫秒数）：初始 `isArmed` 为假；`arm(a, 0)` 后 `isArmed(a, 0)` 为真、`isArmed(b, 0)` 为假；`arm(b, 100)` 取代 a（`isArmed(a, 100)` 为假）；`confirm(a, 2999)` 在 `arm(a, 0)` 后为真并复位（再 `isArmed` 为假）；`confirm(a, 3000)`（超时）为假；`confirm` 对未 arm 的 key 为假；`cancel()` 复位；`TIMEOUT_MS == 3000`
- [X] T008 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/TagOverviewStateTest.java`（**先写，先失败**，状态是单例，测试前后 `reset()`）：默认视图 `SELECTION`；`enterList()` → `LIST`，`enterDetail("minecraft:stone")` → `DETAIL` 且 `detailItem()` 正确；`backToList()` 回 `LIST` 并**保留** `listScroll` 与 `filter`；`toSelection()` → `SELECTION`；`resetView()` 回 `SELECTION` 并清筛选、详情、`listScroll`、行内确认、筛选框编辑状态（**不清**筛选框文本缓冲）；`reset()` 在 `resetView()` 之外还清筛选框文本缓冲；`setFilter/clearFilter`；`startFilterEditing()` 只有在 `LIST` 视图有效、`stopFilterEditing()`；`setListScroll` 不为负；`confirm()` 取到同一个 `ClearConfirm` 实例，`enterList/enterDetail/backToList/toSelection/resetView` 都会 `cancel` 它；视图切换时 `TagPanelState` 的编辑状态与待删标签被清、`scroll` 置 0
- [X] T009 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/TagOverviewLayoutTest.java`（**先写，先失败**），按 data-model.md 第 8 节：`menuIconRect(width)` 在状态行内、距右 2、距上 2、14×14；`of(...)` 的行从筛选行下方开始、每行高 18、相邻不重叠；滚动 `scroll` 夹在 `[0, maxScroll]`，`maxScroll = max(0, rowCount − 可见行数)`；每行的 `iconRect`、`clearRect`、`countRect` 都在行内，`clearRect` 在最右、`countRect` 在其左；`hit` 返回：状态行右端图标 `MENU`、筛选行 `FILTER`（有筛选时右端 `FILTER_CLEAR`）、编辑时下拉各条 `DROPDOWN i`、行 `ROW i`、行内清空钮 `CLEAR i`、其余 `NONE`；下拉盖在行之上（下拉区域内命中 `DROPDOWN` 而不是 `ROW`）；宽度很窄时名称位宽度 ≥ 0 且不与右侧控件重叠；`rowCount == 0` 无行
- [X] T010 [P] 修改 `…/inventory/tags/TagBook.java`：新增 `public TagBook<K> clear(Collection<K> keys)`（返回新书，移除这些分类的全部标签，不存在的忽略，原书不变），写 javadoc。使 T002 通过
- [X] T011 [P] 新建 `…/inventory/tags/TagOverviewRequest.java`：纯逻辑。`enum Op { ADD, REMOVE, CLEAR }`（`toByte`/`fromByte`），`MAX_ITEM_ID_LENGTH = 256`，`validate(Op, String itemId, String tag)`，规则照 data-model.md 第 2 节。使 T003 通过
- [X] T012 新建 `…/inventory/tags/TagOverviewRules.java`：纯逻辑静态泛型 `apply(TagBook<K> book, Op op, K key, String tag, boolean registered, boolean isProtected)`，规则逐行照 data-model.md 第 3 节（`ADD` 用 `book.tagsOf(key).isEmpty()`、`registered`、`TagProtection.blocks(tag, isProtected ? 1 : 0)`、已有同名标签返回原书、`addOrNull`；`REMOVE` 用 `remove`；`CLEAR` 用 `clear`），javadoc 写明"`null` = 拒绝，等于原书 = 没有变化"。使 T004 通过。依赖 T010、T011
- [X] T013 [P] 新建 `…/inventory/tags/TagCandidates.java`：纯逻辑 `of(Collection<String> presetIds, Collection<String> usedTags, Collection<String> exclude)`，规则照 data-model.md 第 4 节。使 T005 通过
- [X] T014 [P] 新建 `…/tags/TagOverviewModel.java`：纯逻辑泛型类，`record Row<K>(K key, String name, List<String> tags)`，`rows(TagBook<K> book, Function<K,String> nameOf, Function<K,String> idOf, String filter)`，规则照 data-model.md 第 5 节。使 T006 通过
- [X] T015 [P] 新建 `…/tags/ClearConfirm.java`：纯逻辑，`TIMEOUT_MS = 3000`，`arm/isArmed/confirm/cancel`，key 为 `String`，规则照 data-model.md 第 6 节。使 T007 通过
- [X] T016 新建 `…/tags/TagOverviewState.java`：纯单例（无 Minecraft 类型）。`enum View { SELECTION, LIST, DETAIL }`；字段与方法照 data-model.md 第 7 节与 research Decision 1：`view()`、`enterList()`（同时 `TagPanelState.get().cancelTransient()` 与 `setScroll(0)`、`confirm.cancel()`）、`enterDetail(String itemId)`（同上）、`backToList()`（保留 `listScroll` 与 `filter`；同上清瞬时状态）、`toSelection()`、`resetView()`、`reset()`、`detailItem()`、`filter()/setFilter/clearFilter`、`listScroll()/setListScroll`（不为负）、`filterInput()`（`TagTextInput`）、`isFilterEditing()/startFilterEditing()（仅 LIST 视图有效）/stopFilterEditing()`、`filterDropdownIndex()/setFilterDropdownIndex`、`confirm()`。使 T008 通过。依赖 T015
- [X] T017（实现时布局的"清空钮宽度"取单个 `clearWidth`，不是数组：所有行的钮等宽） [P] 新建 `…/tags/TagOverviewLayout.java`：纯几何（无 Minecraft 类型），常量 `ROW_H=18`、`FILTER_H=14`、`PAD=2`、`ICON=14`、`DROPDOWN_MAX=5`，`menuIconRect(width)`、`of(width, height, rowCount, scroll, filterHasValue, filterEditing, dropdownCount, clearLabelWidths)`，`Kind { MENU, FILTER, FILTER_CLEAR, DROPDOWN, ROW, CLEAR, NONE }` 与 `hit(x, y)`，`maxScroll`，各矩形访问器，规则照 data-model.md 第 8 节。使 T009 通过
- [X] T018 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/resources/TagOverviewResourcesTest.java`（**先写，先失败**，文本方式，参照 `AutoPickupResourcesTest`）：`en_us.json` 与 `zh_cn.json` 的键集合相同，且同时含 data-model.md 第 9 节列出的全部 `overview.mcfloatingbackpack.*` 键（`icon.open`、`icon.close`、`title`、`empty`、`filter.placeholder`、`filter.none`、`filter.clear`、`detail.back`、`detail.missing`、`clear`、`clear.confirm`、`clear.tooltip`）
- [X] T019 修改 `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` 与 `zh_cn.json`：加入上述全部新键（中文：标签总览 / 返回选区 / 标签总览 %s / 还没有给任何物品打过标签 / 按标签筛选 / 没有符合的物品 / 清除筛选 / 返回 / 这个物品已不在游戏里，只能删除标签 / 清空 / 确认清空？ / 清空这个物品的所有标签；英文对应：Tag overview / Back to selection / Tag overview %s / No item has any tags yet / Filter by tag / No matching items / Clear filter / Back / This item is no longer in the game; you can only remove its tags / Clear / Clear all? / Clear all tags of this item）。使 T018 通过。依赖 T018
- [X] T020 [P] 修改 `…/inventory/BackpackInventory.java`：新增 `hasTagManager()`（扫描 `size(PanelArea.BOTTOM)`，**整个列表**，`.is(ModItems.TAG_MANAGER.get())`），与 `hasTrashCan()`、`hasAutoPickup()` 并排；不改 `BackpackTagPayload` 里原有的私有同名方法
- [X] T021 运行 `./gradlew compileJava test`，确认 T002～T009、T018 与全部既有测试通过

**Checkpoint**: 编译通过；新增纯逻辑测试全绿；还没有任何界面或网络变化。

---

## Phase 3: User Story 1 - 在标签面板里切换到"总览"，再切回来 (Priority: P1) 🎯 MVP 入口

**Goal**: 状态行右端的菜单图标；点它在选区视图与总览之间切换（本阶段总览只有标题与空状态，点击被消费）；选区不被改动；重置规则生效；与 024 黑名单页互斥、020 OFF 界面行为不变。

**Independent Test**: quickstart A（Q1～Q6）。

- [X] T022 [US1] 新建 `…/tags/TagOverviewContext.java`（客户端，`@OnlyIn(Dist.CLIENT)`）：本阶段只含 `title(count)` 文本、图标悬停提示键选择（`view == SELECTION ? icon.open : icon.close`）与 `hasTool()`（读 `ClientBackpackView.hasTagManager()`）；后续阶段再扩展。新建 `…/tags/TagOverviewRenderer.java`：`render(graphics, mainRect, mouseX, mouseY)` 画与 `TagPanelRenderer` 同样的底板与边框（颜色常量复制），状态行左侧标题 `overview.mcfloatingbackpack.title`（参数 0 → 后续换成真实数量）、右端菜单图标（高亮态）、`LIST` 且无行时在内容区显示 `overview.mcfloatingbackpack.empty`；悬停图标显示提示。新建 `…/tags/TagOverviewInput.java`：`onMainPress(button, mouseX, mouseY)`——非左键消费；点菜单图标 → `TagOverviewState.get().toSelection()`；其余本阶段只消费；`onMainScroll` 本阶段只消费
- [X] T023 [US1] 修改 `…/client/panel/tags/TagPanelRenderer.java`：`drawStatus` 在状态行右端画菜单图标（三条横线用 `fill`，位置取 `TagOverviewLayout.menuIconRect(area.w())`，悬停时底色变亮，悬停提示 `overview.mcfloatingbackpack.icon.open`），状态文字可用宽度改为 `area.w() - 8 - 18`；修改 `…/client/panel/tags/TagPanelInput.java`：`onMainPress` 开头加 `if (TagOverviewState.get().view() != View.SELECTION) return TagOverviewInput.onMainPress(button, mouseX, mouseY);`，并在 `layout.hit` 之前判菜单图标命中（左键）→ `TagOverviewState.get().enterList()` 并 `return true`；`onMainScroll` 开头同样转给 `TagOverviewInput.onMainScroll`。不改选区视图的其它行为。依赖 T016、T017、T022
- [X] T024 [US1] 修改 `…/client/panel/FloatingPanelRenderer.java`：`TagPanelState.get().isOpen()` 时，`TagOverviewState.get().view() == SELECTION` 画 `TagPanelRenderer`，否则画 `TagOverviewRenderer`（两处"跳过主区域格子绘制"判断因为仍是面板展开而不变）。依赖 T022
- [X] T025 [US1] 修改 `…/client/FloatingPanelEvents.java`：①`onClientTick` 里现有的"标签面板需要标签管理器与可见面板"的 `collapse()` 分支同时调用 `TagOverviewState.get().resetView()`；②`onLoggingIn`、`onLoggingOut` 里与 `TagPanelState.get().reset()` 并排加 `TagOverviewState.get().reset()`；修改 `…/network/BackpackSessionPayload.java` 的 `handleClient`（开会话、关会话两个分支）调用 `TagOverviewState.get().resetView()`（一行，换背包回到选区视图）。依赖 T016
- [X] T026 [US1] 运行 `./gradlew compileJava test`，确认全部测试通过
- [ ] T027 [US1] 按 `quickstart.md` A（Q1～Q6）在 `./gradlew runClient` 验证（先 `pgrep`）：状态行右端有菜单图标与悬停提示；点图标进入总览、再点回到选区视图且选区没变（切换 10 次）；收起再展开保留视图；拿走标签管理器/关闭面板/退出世界/再按 B 打开都回到选区视图；与 024 黑名单页互斥；OFF 界面上随面板隐藏；018 选区视图的增删标签行为不变；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI 自动验证：Q1～Q3 通过、Q4 部分、Q5 部分、Q6 没测；详见 DELIVERY.md，未勾

**Checkpoint**: 能切换视图；总览还只是空壳。

---

## Phase 4: User Story 2 - 看到这只背包给哪些物品打过标签（列表页） (Priority: P1)

**Goal**: 列表页显示所有有标签的分类（含不在背包里的、已不在游戏里的），每行图标/名称/标签数，悬停列出标签，按显示名排序，可滚动，空状态。

**Independent Test**: quickstart B（Q7～Q11）。

- [X] T028 [US2] 扩展 `…/tags/TagOverviewContext.java`：`rows()`——用 `ClientBackpackView.get().tags().toBook()` 与 `TagOverviewModel.rows(...)` 取行（`nameOf`：注册表里有（`BuiltInRegistries.ITEM.containsKey`）则 `item.getDescription().getString()`，否则注册 id 字符串；`idOf`：`ResourceLocation.toString()`），**缓存**：键 = `(ItemTags 实例引用, filter, 语言代码)`（语言取 `Minecraft.getInstance().getLanguageManager().getSelected()`），键不变就返回上次结果；`registered(key)`；`stackFor(key)`（已注册则 `new ItemStack(item)`，否则空）；`tagLabels(row)`（用 `TagPanelContext.label` 把标签转成显示名）；`layout(width, height)`——用 `TagOverviewLayout.of(...)` 喂行数、`TagOverviewState` 的滚动/筛选/编辑与下拉数，并把滚动夹到 `maxScroll`；`title` 参数改为真实行数。依赖 T014、T017、T022
- [X] T029 [US2] 扩展 `…/tags/TagOverviewRenderer.java`：列表页内容区只画**可见行**（按滚动算起止，`enableScissor` 裁到内容区）：行底色（隔行略不同）、物品图标（`graphics.renderItem(stack, x, y)`；已不在游戏里的画灰底方块加"?"占位）、名称（`font.plainSubstrByWidth` 按名称位宽截断）、标签数量；空书时显示 `overview.mcfloatingbackpack.empty`；悬停某一行时 `graphics.renderComponentTooltip`：第一行物品名（或注册 id），其后每行一个标签（`TagPanelContext.label`）。本阶段不画"清空"按钮与筛选行。依赖 T028
- [X] T030 [US2] 扩展 `…/tags/TagOverviewInput.java`：`onMainScroll`——在 `LIST` 视图 `TagOverviewState.setListScroll(scroll + (scrollDeltaY > 0 ? -1 : 1))`（布局会夹范围），始终返回 `true`；`onMainPress`——`LIST` 视图左键点行本阶段只消费（进入明细在 US4）。依赖 T028
- [X] T031 [US2] 运行 `./gradlew compileJava test`，确认全部测试通过
- [ ] T032 [US2] 按 `quickstart.md` B（Q7～Q11）在 `./gradlew runClient` 验证（先 `pgrep`）：有标签的物品全部列出且数量正确；物品从背包拿走后仍在列表；没打过标签的背包显示空状态；悬停显示完整标签；40 种以上物品可滚动、滚轮不穿透；切换语言后按新显示名重排；**造 200 个以上分类看滚动手感**（research 不确定项 1）；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI 自动验证：Q9、Q10 通过，Q7 部分，Q8、Q11 没测，"手感/200 个以上分类"没测；详见 DELIVERY.md，未勾

**Checkpoint**: 能看到全部有标签的物品；还不能筛选、不能点进去。

---

## Phase 5: User Story 3 - 按标签筛选，找出"哪些被标成了垃圾" (Priority: P1)

**Goal**: 列表页顶部的筛选输入（文本框 + 下拉），选定一个标签后只显示带该标签的物品；可清除；键盘只在文本框有焦点时拦截。

**Independent Test**: quickstart C（Q12～Q15）。

- [X] T033 [US3] 扩展 `…/tags/TagOverviewContext.java`：`filterDropdown()`——仅在 `isFilterEditing()` 时，用 `TagCandidates.of(PresetTags.ids(), 本背包已用标签（遍历 ClientBackpackView.tags().map() 的所有标签，去重，保持顺序）, 空)` 得到候选 id，包成 `TagMatcher.Entry(id, TagPanelContext.label(id))`，按筛选框文本 `TagMatcher.match`；`filterLayoutInputs()` 给布局喂 `filterHasValue/filterEditing/dropdownCount`。依赖 T013、T028
- [X] T034 [US3] 扩展 `…/tags/TagOverviewRenderer.java`：画筛选行——未筛选未编辑：占位字 `overview.mcfloatingbackpack.filter.placeholder`；已筛选：标签块（`TagPanelContext.label`）+ 右端 `×`（悬停提示 `filter.clear`）；编辑：文本框（光标闪烁、占位 `tag.mcfloatingbackpack.add.placeholder`）+ 下拉（高亮项与悬停项底色、显示名与 id 并排，同 `TagPanelRenderer`）；筛选结果为空且有筛选时显示 `overview.mcfloatingbackpack.filter.none`。依赖 T033
- [X] T035 [US3] 扩展 `…/tags/TagOverviewInput.java`：`LIST` 视图的 `onMainPress`——`FILTER` → `startFilterEditing()`（并取消其它瞬时状态）；`FILTER_CLEAR` → `clearFilter()` 并 `setListScroll(0)`；`DROPDOWN i` → `setFilter(候选 i)`、`stopFilterEditing()`、清文本、`setListScroll(0)`；点其它位置 → `stopFilterEditing()`；新增 `onKeyPressed(keyCode, scanCode, modifiers)` / `onCharTyped(c)`（只处理筛选框：ESC 停止编辑不改筛选、Enter 选定高亮候选或输入文本（合法标签才选定）、Backspace/Delete/左右/Home/End、上下选下拉、粘贴走 `TagRules.filter`；字符走 `TagTextInput.type`），写法照 `PickupPanelInput`/`TagPanelInput`。依赖 T033
- [X] T036 [US3] 修改 `…/client/FloatingPanelEvents.java` 的键盘三事件（`KeyPressed.Pre`、`KeyReleased.Pre`、`CharacterTyped.Pre`）：在现有 `hostsPanel(screen) && TagPanelState.get().isEditing()` 旁加 `|| TagOverviewState.get().isFilterEditing()`，并在处理处按视图分支（`TagOverviewState.view() == LIST` 且 `isFilterEditing()` → `TagOverviewInput`；否则走原有 `TagPanelInput`）；修改 `…/client/panel/FloatingPanelInput.java`：现有"编辑中点面板以外失焦"分支旁加 `TagOverviewState.get().stopFilterEditing()`；修改 `…/client/panel/DeleteKeyInput.java`：传给 `DeleteKeyRules.Inputs` 的 `tagPanelEditing` 改为 `TagPanelState.get().isEditing() || PickupPanelState.get().isEditing() || TagOverviewState.get().isFilterEditing()`。依赖 T035
- [X] T037 [US3] 运行 `./gradlew compileJava test`，确认全部测试通过（含 `DeleteKeyRulesTest`）
- [ ] T038 [US3] 按 `quickstart.md` C（Q12～Q15）在 `./gradlew runClient` 验证（先 `pgrep`）：下拉候选与模糊匹配；选定 `trash` 只剩带 `trash` 的物品、标签块与 `×` 显示；清除恢复；筛选无结果的空状态；文本框焦点下打字不触发快捷键（含 Delete）、ESC 只取消编辑、没有焦点不拦截键盘；非法字符被过滤；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI 自动验证：Q12、Q13、Q15 通过，Q14 部分（Delete、数字、Esc 不清除已应用筛选没测）；**发现下拉被物品图标压住、未应用文字残留两个问题**；详见 DELIVERY.md，未勾

**Checkpoint**: 能按标签筛选出所有"垃圾"物品。

---

## Phase 6: User Story 4 - 查看并编辑某个物品的标签（明细页，含服务端请求） (Priority: P1)

**Goal**: 点行进入明细页；增删标签走新的按物品 id 的请求（协议 13）；规则沿用 018/022；物品不在背包里也能编辑；已不在游戏里的物品只能删；删光最后一个标签自动回列表。

**Independent Test**: quickstart D（Q16～Q22）。

- [X] T039 [US4] 新建 `…/network/BackpackTagOverviewPayload.java`（客户端→服务端）：`record BackpackTagOverviewPayload(byte op, String itemId, String tag)`，`TYPE` 用 `"tag_overview"`，`STREAM_CODEC` 用 `StreamCodec.of`（`writeByte(op)`、`writeUtf(itemId, TagOverviewRequest.MAX_ITEM_ID_LENGTH)`、`writeUtf(tag, TagRules.MAX_LENGTH)`），`of(Op, ResourceLocation, String)` 工厂；`handleServer` 严格按 contracts/overview-network.md 第 3 节：`ServerPlayer` → `BackpackRef.resolve` + `stillValid`（否则 `sendSession(false)`）→ `loadInventory` → `hasTagManager()` → `TagOverviewRequest.validate` → `ResourceLocation.tryParse`（空则忽略）→ `registered/isProtected`（服务端用 `BuiltInRegistries.ITEM` 与 `ModTags.NEVER_TRASH` 自己算）→ `TagOverviewRules.apply`（`null` 忽略；与原书相等不保存；否则核对 `ItemTags.MAX_CATEGORIES`、`setTags`、`ref.saveInventory`）→ 无论成败都 `PacketDistributor.sendToPlayer(player, new BackpackTagsPayload(backpack.tags()))`。修改 `…/network/BackpackNetwork.java`：`versioned("12")` → `versioned("13")`，注册该包（`playToServer`）。协议版本只在这一处改。依赖 T012、T020
- [X] T040 [US4] 扩展 `…/tags/TagOverviewContext.java`：`detailModel()`——`TagPanelModel.of(List.of(0), i -> detailKey, ClientBackpackView.get().tags().toBook(), hasTool() && registered(detailKey), TagPanelContext::isProtected)`（得到 `commonTags`、`canAdd`、`blocks(tag)`、`protectedItems`）；`detailDropdown()`——`TagCandidates.of(预设, 已用标签, 该分类已有标签)` 经 `TagMatcher.match`，仅在 `TagPanelState.isEditing()` 时；`detailLayout(width, height, model)`——`TagPanelLayout.of(...)`，写法同 `TagPanelContext.layout`（用 `TagPanelState` 的 `scroll/pendingDelete/isEditing`，`noneLabel` 用 `tag.mcfloatingbackpack.none`）；`syncDetail()`——若 `view == DETAIL` 且 `book.tagsOf(detailKey)` 为空则 `TagOverviewState.backToList()`（每帧调用）。依赖 T016、T028
- [X] T041 [US4] 扩展 `…/tags/TagOverviewRenderer.java`：画明细页——状态行：左端返回按钮（`‹`，悬停提示 `detail.back`）、物品图标与名称（已不在注册表显示注册 id）、右端菜单图标；内容区照 `TagPanelRenderer.drawContent` 画标签块、删除标记、"无"、文本框、下拉（`model.blocks(id)` 的候选变灰，悬停提示 `TagPanelContext.protectedMessage(model)`）、加号（`canAdd` 绿/灰；灰时悬停提示：已满用 `tag.mcfloatingbackpack.full`，已不在游戏里用 `overview.mcfloatingbackpack.detail.missing`）；渲染入口先调 `syncDetail()`。依赖 T040
- [X] T042 [US4] 扩展 `…/tags/TagOverviewInput.java`：`LIST` 的 `ROW i` → `enterDetail(行的注册 id)`；`DETAIL` 的 `onMainPress`——返回按钮 → `backToList()`；菜单图标 → `toSelection()`；`TAG` → `stopEditing` + `setPendingDelete`；`DELETE` → `setPendingDelete(null)` 并发 `REMOVE`；`PLUS` → `model.canAdd()` 才 `TagPanelState.startEditing()`；`TEXTBOX` → 放置插入点；`DROPDOWN i` → 发 `ADD`；其余 `cancelTransient`；`DETAIL` 的键盘（用 `TagPanelState` 的 `input/editing/dropdownIndex`，写法照 `TagPanelInput`，提交时 `add(tag)` 先 `TagRules.isValid`、`model.blocks(tag)` 为真则发聊天提示 `protectedMessage` 且不发请求、`!model.canAdd()` 则关闭文本框、否则发 `ADD`）；发送一律 `PacketDistributor.sendToServer(BackpackTagOverviewPayload.of(op, key, tag))`，**不改本地数据**（等 `BackpackTagsPayload`）；`onMainScroll` 在 `DETAIL` 视图用 `TagPanelState.setScroll`。依赖 T039、T040
- [X] T043 [US4] 修改 `…/client/FloatingPanelEvents.java` 的键盘三事件分支：`TagPanelState.get().isEditing()` 且 `TagOverviewState.view() == DETAIL` → 转给 `TagOverviewInput`（添加走按 id 的请求），`view == SELECTION` 仍转给 `TagPanelInput`；`KeyReleased/CharacterTyped` 同。依赖 T042
- [X] T044 [US4] 运行 `./gradlew compileJava test`，确认全部测试通过；`grep -rn "versioned(" …/network/BackpackNetwork.java` 为 `"13"`；`BackpackTagPayload`、`TagPanelState`、`TagPanelLayout`、`TagPanelModel`、`TagPanelContext` 没有改动（`git diff --stat`）
- [ ] T045 [US4] 按 `quickstart.md` D（Q16～Q22）在 `./gradlew runClient` 验证（先 `pgrep`）：点行进入明细、返回保持滚动与筛选；增删标签；物品不在背包里也能增删；加到 10 个加号灰且服务端拒绝；受保护物品加 `trash` 被拒绝（下拉灰、提示）；删掉最后一个标签自动回列表；明细页添加框键盘规则；（可选）已不在游戏里的物品的显示与限制；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI 自动验证：Q16、Q17（Esc 取消没测）、Q18、Q20、Q21 通过，Q19、Q22 没测；详见 DELIVERY.md，未勾

**Checkpoint**: 总览的查看、筛选、编辑都能用；还不能清空。

---

## Phase 7: User Story 5 - 清空某个物品的所有标签（二次确认） (Priority: P2)

**Goal**: 列表页每行的"清空"，行内两步确认，确认后发 `CLEAR`。

**Independent Test**: quickstart E（Q23～Q26）。

- [X] T046 [US5] 扩展 `…/tags/TagOverviewRenderer.java`：每行右端画"清空"按钮（`overview.mcfloatingbackpack.clear`，灰底、悬停提亮，悬停提示 `clear.tooltip`）；`TagOverviewState.confirm().isArmed(行 key, now)` 时该行按钮变红底、文字 `clear.confirm`；按钮宽度 = 文本宽 + 8，并把这些宽度喂给 `TagOverviewLayout`（`clearLabelWidths`）。依赖 T017、T029
- [X] T047 [US5] 扩展 `…/tags/TagOverviewInput.java`：`LIST` 的 `CLEAR i`——`confirm().confirm(key, now)` 为真则发 `CLEAR`（`BackpackTagOverviewPayload.of(Op.CLEAR, key, "")`）；否则 `confirm().arm(key, now)`；所有其它按下（点别处含筛选框、行、图标、面板外）、`onMainScroll`、切换视图都先 `confirm().cancel()`（`TagOverviewState` 的视图转移方法已 cancel；这里补按下/滚动）；`now` 用 `System.currentTimeMillis()`。依赖 T039、T046
- [X] T048 [US5] 运行 `./gradlew compileJava test`，确认全部测试通过
- [ ] T049 [US5] 按 `quickstart.md` E（Q23～Q26）在 `./gradlew runClient` 验证（先 `pgrep`）：第一次点"清空"只变成"确认清空？"；点别处/滚动/切换视图/等 3 秒/点另一行的"清空"都取消或取代，数据不变；第二次点后该分类标签清空、行消失；清空后选区视图里该物品共同标签为"无"、022 的倾倒不再倾倒它、024 黑名单不再命中；对受保护物品与已移除模组的物品同样成功；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI 自动验证：Q23（点别处/滚动/切换视图没测）、Q24、Q26 通过，Q25 没测；详见 DELIVERY.md，未勾

---

## Phase 8: User Story 6 - 服务端权威、持久，不影响现有功能 (Priority: P2)

**Goal**: 持久化、隔离、非法请求零变化、回归。

**Independent Test**: quickstart F（Q27～Q31）。

- [X] T050 [US6] 审阅（2026-11-08 已审阅，无缺口：服务端处理器各早退路径都不改背包数据且（有背包时）补发 `BackpackTagsPayload`；`registered/isProtected` 由服务端自己算；`REMOVE/CLEAR` 幂等；`TagPanelInput/Renderer` 只加图标与视图分支）（读代码，不改行为）：①`BackpackTagOverviewPayload.handleServer` 每个早退路径都不改背包数据，且（有背包时）都补发 `BackpackTagsPayload`；②没有标签管理器的请求被拒；③`ADD` 对没有标签的分类/已不在注册表的物品/`trash` 加受保护物品/第 11 个标签/非法标签都被 `TagOverviewRules` 拒绝；④`REMOVE`/`CLEAR` 对不存在的分类或标签是幂等无变化；⑤`isProtected` 与 `registered` 都由服务端自己算；⑥`ItemTags.MAX_CATEGORIES` 在保存前核对；⑦`TagPanelInput`/`TagPanelRenderer` 的改动只有图标与视图分支，选区视图路径未变；⑧键盘三事件与 `DeleteKeyInput` 对"筛选框/添加框有焦点"的判断一致；发现缺口就补并记录，没有缺口也记"已审阅，无缺口"
- [ ] T051 [US6] 按 `quickstart.md` F（Q27～Q31）在 `./gradlew runClient` 验证（先 `pgrep`）：拿走标签管理器后无法操作；退出重进/关闭面板/升级/换背包后标签与之前一致且两只背包互不共享，旧存档正常；**重跑 018 的 quickstart（选区视图）、022（倾倒与 Delete 键与保护提示）、024（黑名单与拾取与互斥）与 011～023 中与面板有关的场景**；结果记到 DELIVERY.md（由用户确认后才打勾）

---

## Phase 9: Polish & Cross-Cutting

- [X] T052 [P] 更新文档：`specs/018-tag-manager/spec.md` 加一段 025 说明（标签面板状态行右端多一个菜单图标，总览视图：列表、按标签筛选、明细、清空；新请求按物品 id 寻址，协议 13；选区视图行为不变）；`specs/022-trash-delete-tools/spec.md` 加一句（总览里清空标签后一键倾倒不再倾倒该物品；"垃圾"标签保护在总览明细页同样生效）；`specs/024-auto-pickup/spec.md` 加一句（黑名单的命中依赖标签，总览是管理它们的地方）；`docs/MCFloatingBackpack.md` 在 024 说明之后加 025 说明；`docs/plan/manual-verification-checklist.md` 补 025 的验证项；**不要修改 `docs/ideas`**
- [ ] T053 按 `quickstart.md` Q31：`./gradlew compileJava test` 退出码 0；先 `pgrep` 确认没有运行中的客户端，再跑 `design/demo/tools/check_atlas_budget.sh`，图集应仍为 2048×1024（宪法 VI；本 feature 不新增贴图），结果记到 DELIVERY.md——**跑不了（有客户端在运行或脚本失败）就如实写"未度量"，不要写成通过** ——2026-11-11 AI 自动验证：图集度量 2048×1024 通过（容器内 `check_atlas_budget.sh`，干净与带全部模组两种环境）；`compileJava test` 未在本次重跑，所以未勾
- [X] T054 静态检查（2026-11-08：`TagPanelState`、`TagPanelLayout`、`TagPanelModel`、`TagPanelContext`、`BackpackTagPayload` 无改动；`TagPanelRenderer`/`TagPanelInput` 只多了图标与视图分支；`build.gradle`、`neoforge.mods.toml`、`compat/` 无改动；第三方引用只在 `compat/jei/` 与 `ContainerScreenCompat` 原有位置；`setScreen(` 仍为 4 处；协议版本只在 `BackpackNetwork` 一处为 `"13"`）：`git diff --stat` 确认没有改动 `TagPanelState`、`TagPanelLayout`、`TagPanelModel`、`TagPanelContext`、`BackpackTagPayload`、`QuickTransfer*`、`PanelDrag*`、`TrashPanel*`、`PickupPanel*` 的逻辑，`TagPanelRenderer`/`TagPanelInput` 只多了图标与视图分支；`neoforge.mods.toml`、`build.gradle`、`compat/` 无改动；`grep -rn "p3pp3rf1y\|tiviacz\|mezz" src/main/java` 只命中 `compat/jei/` 与 `ContainerScreenCompat` 里原有的位置；`setScreen(` 仍为 4 处；协议版本只在 `BackpackNetwork` 一处为 `"13"`；`DependencyRulesTest` 通过
- [ ] T055 新建/补全 `specs/025-tag-overview/DELIVERY.md`（中文）：汇总自动化结果（T001 基线与各阶段编译与测试结果、新增测试类与用例数）、与 plan/tasks 的偏离、**每个客户端验证项实际验证了什么 / 未验证什么**、大量分类下的手感结论、图集度量结果或"未度量"、已知限制（Windows/Linux 未验证；联机；不做乐观更新；总览只管已有标签的分类；已移除模组的物品只能删除/清空）、**给后来者的提醒**（`TagPanel*`、`PickupPanel*`、`TagOverview*` 三套结构相近，第三处需要同类面板时再抽象；`TagCandidates` 与 `PickupPanelModel.candidates` 重复，可合并；`BackpackContents` 字段数仍是 6，满）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1（Setup）**：无依赖，先做。
- **Phase 2（Foundational）**：依赖 Phase 1。T002～T009、T018 可并行写测试；T010、T011、T013、T014、T015、T017、T020 可并行实现；T012 依赖 T010/T011；T016 依赖 T015；T019 依赖 T018；T021 最后。**阻塞所有用户故事。**
- **Phase 3（US1）**：依赖 Phase 2。T022 → T023/T024/T025（T023 依赖 T016/T017/T022，T024 依赖 T022，T025 依赖 T016）→ T026 → T027。
- **Phase 4（US2）**：依赖 US1。T028 → T029/T030 → T031 → T032。
- **Phase 5（US3）**：依赖 US2（列表与上下文）。T033 → T034/T035 → T036 → T037 → T038。
- **Phase 6（US4）**：依赖 US2（列表行点击）与 Phase 2；服务端部分（T039）只依赖 Phase 2，可与 US2/US3 同时做。T039 ∥ T040；T041、T042 依赖 T040（T042 还依赖 T039）；T043 依赖 T042；T044 → T045。
- **Phase 7（US5）**：依赖 US2（列表行）与 US4 的请求（T039）。T046 → T047 → T048 → T049。
- **Phase 8（US6）**：依赖前面所有故事的代码。
- **Phase 9（Polish）**：T052 可在 US4 稳定后随时做；T053～T055 在所有故事完成后。

### User Story Dependencies

- US1 → US2 → US3、US4 构成完整总览（四个 P1）；US5 在 US2 与 US4 之上；US6 汇总验证。

### Parallel Opportunities

- Phase 2：T002 ∥ T003 ∥ T004 ∥ T005 ∥ T006 ∥ T007 ∥ T008 ∥ T009 ∥ T018；T010 ∥ T011 ∥ T013 ∥ T014 ∥ T015 ∥ T017 ∥ T020。
- Phase 6 的 T039（服务端请求）可与 Phase 4、Phase 5 的客户端任务同时做（不同文件）。

### Parallel Example: Phase 2

```text
同时写测试：T002 TagBookClearTest  T003 TagOverviewRequestTest  T004 TagOverviewRulesTest  T005 TagCandidatesTest
            T006 TagOverviewModelTest  T007 ClearConfirmTest  T008 TagOverviewStateTest  T009 TagOverviewLayoutTest  T018 资源测试
同时实现：  T010 TagBook.clear  T011 TagOverviewRequest  T013 TagCandidates  T014 TagOverviewModel  T015 ClearConfirm  T017 TagOverviewLayout  T020 hasTagManager
然后：      T012 TagOverviewRules → T016 TagOverviewState → T019 语言键 → T021 运行测试
```

---

## Implementation Strategy

### MVP First（US1 → US2 → US3 → US4）

1. 完成 Phase 1、Phase 2（纯逻辑 + 语言键 + `hasTagManager`，不碰界面）。
2. 完成 US1（图标与切换），用户验证 T027。
3. 完成 US2（列表），用户验证 T032。
4. 完成 US3（筛选），用户验证 T038。
5. 完成 US4（明细页 + 服务端请求 + 协议 13），用户验证 T045——到这里总览才算可用（查看、筛选、编辑）。

### Incremental Delivery

1. MVP（US1～US4）→ 用户验证。
2. US5（清空）→ 验证。
3. US6（持久化与回归）→ 验证。
4. Polish：文档、图集度量、静态检查、交付说明。

### 风险提示（实现时盯住）

- **键盘与 Delete 键**（T036、T043）：总览有两个文本框（筛选框、明细页添加框），键盘三事件、`FloatingPanelInput` 的失焦、`DeleteKeyInput` 都要认；漏一处就会"打字触发快捷键"或"Delete 删了主区域物品"。
- **不改 018/022/024 已验证的行为**（T023、T054）：`TagPanelInput/Renderer` 只加图标与视图分支，`TagPanelState` 等一概不改；T051 要重跑 018 选区视图的验证项。
- **服务端权威**（T039、T050）：客户端只发"动作 + 物品注册 id + 标签"；`registered/isProtected` 由服务端自己算；任何一步失败零变化并补发标签同步；`ADD` 只允许对已有标签的分类。
- **大量分类**（T028、T032）：只画可见行，排序结果按"`ItemTags` 实例 + 筛选 + 语言"缓存；同步后必须换实例才会失效。
- **明细页与同步的竞态**（T040）：每帧 `syncDetail()` 检查分类是否已没有标签，没有就回列表；不做乐观更新。
- **协议升级**：`"12"` → `"13"` 只在 `BackpackNetwork` 一处改（T039）；数据格式、`BackpackContents` 字段数都不变。
- **图集预算**：本 feature 不新增贴图；度量不了就如实写"未度量"（T053）。
