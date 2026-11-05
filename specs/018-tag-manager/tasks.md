# Tasks: 第一个扩展工具——标签管理器

**Input**: Design documents from `/specs/018-tag-manager/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/tags-network-storage.md, contracts/tag-panel-ui.md, quickstart.md；宪法 2.0.0；**019（兜底透明宿主界面）已实现并提交**——本 feature 的键盘接管因此只走 `ScreenEvent` 一条路径，不再有 `TagInputScreen`

**Tests**: 标签规则、预设、模糊匹配、增删/交集/上限/原子性、请求校验、`ItemTags` 编码、文本编辑、面板模型与排版、配方与语言键，都是不依赖 Minecraft 运行环境的纯逻辑，plan 要求用单元测试覆盖，**测试任务排在对应实现之前**；存储同步、展开/收起、点击分派、键盘接管、渲染手感与图标外观只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础（纯逻辑 + 数据组件）；US1（物品、配方、图标、展开/收起）是入口 MVP；US2（查看标签，含服务端同步）、US3（增加标签，含文本框与键盘接管、服务端处理）是另外两个 P1，三者合起来才是可用的最小工具；US4（删除）、US5（10 个上限的灰色加号与提示）、US6（持久化与回归）为 P2。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US6）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**启动客户端或跑 `check_atlas_budget.sh` 前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，有正在运行的客户端就不要启动、更不要杀用户的游戏**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过
- 包名前缀 `pnnh.mcfloatingbackpack`，下文路径里的 `src/main/java/…` 省略为 `…/`

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（019 之后约 28 个测试类）；结果记到 `specs/018-tag-manager/DELIVERY.md`

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 标签规则、预设、匹配、`TagBook`、请求校验是客户端与服务端共用的纯逻辑；`ItemTags` 与 `BackpackContents.tags` 是数据基础。测试先写、先失败。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/TagRulesTest.java`（**先写，先失败**），按 data-model.md：`acceptsChar` 接受小写字母、数字、`_`、`-`、`.`、`!`、`~`，拒绝大写字母 `A`..`Z`、空格、制表符、`é`、中文；`isValid` 的边界（空串、1 个字符、16 个字符、17 个字符、含大写、含空格、含非 ASCII）；`filter` 逐字符保留合法字符并截到 16（粘贴 `Wood Axe!` → `ood`＋`xe!` 按规则过滤后截断）
- [X] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/PresetTagsTest.java`（**先写，先失败**）：`ids()` 恰好是 `stone, wood, food, ore, trash, tool, weapon, armor, plant, redstone`，共 10 个、唯一、全部满足 `TagRules.isValid`；`langKey("wood")` = `tag.mcfloatingbackpack.preset.wood`；`isPreset` 对预设为真、对自定义为假
- [X] T004 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/TagMatcherTest.java`（**先写，先失败**）：空查询返回全部、保持预设顺序；`wo` → `wood` 在 `weapon` 之前（前缀 > 子序列）；`st` 先 `stone` 再含子串的项；子序列匹配（`rds` → `redstone`）；不区分大小写；同时匹配 id 与显示名（显示名为"木材"、"Wood"的两种语言条目）；无匹配返回空
- [X] T005 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/TagBookTest.java`（**先写，先失败**），用 `String` 当键：`tagsOf` 返回拷贝；`add` 保持添加顺序且不重复；`common` 求交集（保持第一个分类的顺序，空集合返回空，单个分类就是它自己）；`full` 返回已有 10 个标签的分类；`canAdd` 在任一分类"尚无该标签且已满"时为假；`add` 失败时**返回失败且原对象零变化**（原子性）；某分类已有该标签时跳过不算超限；`remove` 对没有该标签的分类跳过；随机 200 组操作后任何分类 ≤ 10 个标签、无重复
- [X] T006 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/TagRequestTest.java`（**先写，先失败**）：`Op.fromByte(0/1)` 合法、其它返回 null；`validate(op, slots, tag, mainSize)`：合法请求通过；`op` 为 null、`slots` 为空、含重复、含负数、等于 `mainSize`、数量超过 `min(mainSize,108)`、标签为空/含大写/含空格/17 个字符，全部拒绝
- [X] T007 [P] 新建 `…/inventory/tags/TagRules.java`：纯逻辑（无 Minecraft 依赖）。`MIN_LENGTH=1`、`MAX_LENGTH=16`、`MAX_TAGS_PER_ITEM=10`、`acceptsChar`、`isValid`、`filter`，规则逐条照 data-model.md。使 T002 通过
- [X] T008 [P] 新建 `…/inventory/tags/PresetTags.java`：纯逻辑。10 个预设 id（顺序同 T003）、`langKey(id)`、`isPreset`。使 T003 通过
- [X] T009 [P] 新建 `…/inventory/tags/TagMatcher.java`：纯逻辑。`match(String query, List<Entry>)`，`Entry(id, displayName)`；相关度前缀 > 子串 > 子序列，不区分大小写，同档保持输入顺序，空查询返回全部。使 T004 通过
- [X] T010 [P] 新建 `…/inventory/tags/TagBook.java`：纯逻辑泛型 `TagBook<K>`，内部 `Map<K, List<String>>`（保持添加顺序）；`tagsOf`、`common`、`full`、`canAdd`、`add`（失败返回 `null` 或失败结果，不改自身；成功返回新的 `TagBook`）、`remove`、`asMap()`；不变量见 data-model.md。使 T005 通过。依赖 T007（上限常量）
- [X] T011 [P] 新建 `…/inventory/tags/TagRequest.java`：纯逻辑。`enum Op { ADD, REMOVE }`（`toByte`/`fromByte`），`validate(Op, int[] slots, String tag, int mainSize)`，规则照 data-model.md。使 T006 通过。依赖 T007
- [X] T012 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/tags/ItemTagsTest.java`（**先写，先失败**）：`CODEC` 往返；读入时丢弃不合法标签与重复项并把每个分类截到 10 个；`STREAM_CODEC` 往返；分类数超过 1024、单个标签超过 16 字符、单个分类超过 10 个标签时解码抛 `DecoderException`；`EMPTY` 编码为空且可读回
  - **实现偏离（2026-11-05）**：测试类路径里只有 JUnit，没有 Minecraft/Gson/Netty，所以 `ItemTags` 的 `CODEC`/`STREAM_CODEC` 无法单测；把"读入时丢弃非法与重复标签、每分类截到 10 个、分类数封顶"的逻辑放进纯类 `TagBook.sanitize`，用 `TagBookTest` 覆盖；`ItemTagsTest` 未建。编码层（往返、超限抛异常）改在客户端会话里验证（登记为未单测）
- [X] T013 新建 `…/inventory/tags/ItemTags.java`：记录 `ItemTags(Map<ResourceLocation, List<String>> map)`，`EMPTY`，对 `TagBook<ResourceLocation>` 的薄封装（`toBook()`/`fromBook`、`tagsOf`），`CODEC`（`Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING.listOf())`，读入时过滤与截断）、`STREAM_CODEC`（`ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, list(STRING_UTF8(16)).apply(list(10)), 1024)`），见 data-model.md。使 T012 通过。依赖 T010
- [X] T014 修改 `…/inventory/BackpackContents.java` 与 `…/inventory/BackpackInventory.java`：`BackpackContents(items, cursor, tools, tags)`，`tags` 用 `optionalFieldOf("tags", ItemTags.EMPTY)`，`STREAM_CODEC` 加第 4 个字段；`fit(...)`、`copy()`、`normalize(...)` 保留 `tags`；`BackpackInventory` 增加 `tags()`/`setTags(ItemTags)`，`fromContents`/`toContents` 读写；修改 `…/inventory/recipe/BackpackUpgradeRecipe.java` 使升级时保留 `tags`；修正所有现有的 `BackpackContents` 构造调用点与测试。依赖 T013
- [X] T015 运行 `./gradlew compileJava test`，确认 T002～T006、T012 与全部既有测试通过

**Checkpoint**: 编译通过；新增纯逻辑测试全绿；旧背包读成空标签。

---

## Phase 3: User Story 1 - 标签管理器放进功能区才有功能，点击展开/收起面板 (Priority: P1) 🎯 MVP 入口

**Goal**: 物品、配方、图标、语言；功能区里左键展开/收起覆盖主区域的标签面板（本阶段面板里还没有内容）；其他区域里它是普通物品；展开期间主区域被面板消费。

**Independent Test**: quickstart 一（1–6）、二（7–10）：合成/创意栏拿到、放进四个区域、只有功能区里左键展开；展开后覆盖主区域并随缩放变化；拿走自动收起。

- [X] T016 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/resources/TagManagerResourcesTest.java`（**先写，先失败**）：解析 `data/mcfloatingbackpack/recipe/tag_manager.json`——`crafting_shaped`、图案 `SPS`/`LBL`/`PIP`、键 `S=c:strings`、`P=minecraft:paper`、`L=c:leathers`、`B=minecraft:book`、`I` 为墨囊或黑色染料（`minecraft:ink_sac` 或 `minecraft:black_dye` 的任一合法写法）、结果 `mcfloatingbackpack:tag_manager` ×1；`en_us.json` 与 `zh_cn.json` 同时含 `item.mcfloatingbackpack.tag_manager`、10 个 `tag.mcfloatingbackpack.preset.*`、`tag.mcfloatingbackpack.none`、`tag.mcfloatingbackpack.hint.select`、`tag.mcfloatingbackpack.full`、`tag.mcfloatingbackpack.full.batch`、`tag.mcfloatingbackpack.add.placeholder`、`tag.mcfloatingbackpack.status`（键全集以 data-model.md 为准，后续任务新增键时同步补进本测试）；两份语言文件的键集合相同；`models/item/tag_manager.json` 的父模型是 `minecraft:item/generated` 且指向 `mcfloatingbackpack:item/tag_manager`
  - **实现说明**：测试类路径里没有 JSON 库，所以用文本检查（正则取键、规范化空白后查子串）；贴图检查读 PNG 头里的宽高；`ink` 用 `[ink_sac, black_dye]` 两个原料的列表。`TagManagerResourcesTest` 4 个用例通过
- [X] T017 [US1] 新建 `…/item/TagManagerItem.java`（普通 `Item`，堆叠 1，无其它行为）；修改 `…/item/ModItems.java` 注册 `tag_manager` 并加入本模组创意栏；新建 `src/main/resources/data/mcfloatingbackpack/recipe/tag_manager.json`、`src/main/resources/assets/mcfloatingbackpack/models/item/tag_manager.json`；修改 `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` 与 `zh_cn.json` 加入物品名与全部标签相关键（中文：标签管理器、石头/木材/食物/矿物/垃圾/工具/武器/盔甲/植物/红石、"无"、"请先在主区域用 Shift 选择物品"、已满与"标签已满的物品不支持批量新增"、占位文字等；英文对应）。使 T016 除贴图外的部分通过
- [X] T018 [US1] 生成图标：先 `pgrep` 无关；用 ludo.ai MCP（`mcp__ludo-ai__createImage`，必要时 `removeBackground`）按 research Decision 8 的提示词生成一张大图（像素风、原版 Minecraft 物品图标、带线的吊牌/标签、透明背景），下载到 `design/tools/` 旁的源素材目录（**源图与脚本不随模组发布**）；新建 `design/tools/make_tag_manager_icon.py` 把它缩成 16×16 RGBA（盒式缩放 + 调色板量化、保留透明）并写入 `src/main/resources/assets/mcfloatingbackpack/textures/item/tag_manager.png`；效果不好或额度不够就在同一脚本里用字符画 + 调色板手绘 16×16；把生成方式、提示词、来源文件、脚本记入 DELIVERY.md；用 `Read` 打开 PNG 目视检查。依赖 T017
  - **结果**：`mcp__ludo-ai__createImage`（`item-icon`、`16-Bit Pixel`、n=2，共 1 点数）生成两张 768×768 候选，选了第 1 张（直立的棕色吊牌，带线圈与打孔）；源图放在 `design/demo/tools/tag_manager_icon/`（`src1.png`、`src2.png`，另有 webp 原件，**不随模组发布**），用 `design/demo/tools/make_tag_manager_icon.py`（白底洪水填充去背景、裁切、按最长边缩放到 16、alpha 二值化、12 色量化、底部对齐）生成 `textures/item/tag_manager.png`（16×16，实际内容 9×16）。目视检查过放大预览：形状可辨，但偏棕色偏暗，**最终效果由用户在游戏里看**，不满意可换第 2 张或改手绘。（偏离 plan：脚本放在 `design/demo/tools/`，与现有 `check_atlas_budget.sh` 等同目录，而非 `design/tools/`）
- [X] T019 [P] [US1] 新建 `…/client/panel/tags/TagPanelState.java`：客户端会话单例，字段 `open`、`editing`、`text`、`caret`、`dropdownIndex`、`pendingDelete`、`scroll`，方法 `toggle()`、`collapse()`（`open=false`、`editing=false`，保留 `text`/`scroll`）、`reset()`（全部复位，登出用）；状态机照 data-model.md。**不依赖 Minecraft 类型**，便于测试
  - **实现说明**：`TagPanelState` 暂只含展开/编辑/滚动/待删除/下拉状态，文本缓冲由 US3 的 `TagTextInput` 持有（`text` 的"收起保留"由它负责）
- [X] T020 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/TagPanelStateTest.java`：`toggle` 在收起/展开间切换；`collapse` 保留 `text` 与 `scroll` 并结束编辑；`reset` 清空全部；`editing` 只在 `open` 时可能为真（收起时 `startEditing` 无效）。依赖 T019
  - **结果**：`TagPanelStateTest` 5 个用例通过
- [X] T021 [US1] 修改 `…/client/panel/FloatingPanelInput.java`：在工具区点击分支里，命中 `BOTTOM` 且该格是标签管理器、按下的是左键、光标为空、无 Shift/Ctrl、`PanelDragInput.isActive()` 为假 → `TagPanelState.toggle()`（不发包）并消费；其余一律按 016 原样处理（右键拿起、光标非空时交换、主区域/左区/右区里的标签管理器完全是普通物品）；**面板展开时**，命中 `MAIN` 区域的按下/松开/拖动先交给标签面板（本阶段：只消费并取消编辑/待删除，不进入 014 选区、011 Ctrl、013 拖动），`PanelSelection` 不被触碰。依赖 T019
  - **实现说明**：展开后主区域的所有按下都由 `TagPanelInput.onMainPress` 消费（取消编辑与待删除）；滚轮在 `FloatingPanelEvents.scrollPanel` 里交给 `TagPanelInput.onMainScroll`；011/013/014 的输入类未改
- [X] T022 [US1] 修改 `…/client/FloatingPanelEvents.java`：①`scrollPanel` 在标签面板展开且鼠标在主区域上时，把滚轮交给标签面板（本阶段只消费）；②`onClientTick` 里每 tick 检查功能区是否还有标签管理器（读 `ClientBackpackView`），没有、或浮动面板不可见时调用 `TagPanelState.collapse()`；③`onLoggingOut`/`onLoggingIn` 里 `TagPanelState.reset()`。依赖 T019
- [X] T023 [US1] 修改 `…/client/panel/FloatingPanelRenderer.java`：标签面板展开时，在主区域矩形（`PanelLayout.rect(MAIN)`）画面板背景，**隐藏**主区域格子、选区边框与滚动箭头，左区/右区/功能区/标题栏照常；新建 `…/client/panel/tags/TagPanelRenderer.java` 的骨架（本阶段只画背景与一行占位文字）；颜色与尺寸常量集中在渲染类顶部。依赖 T019、T021
- [X] T024 [US1] 运行 `./gradlew compileJava test`，确认 T016、T020 与全部既有测试通过
  - **结果**：`./gradlew compileJava test` 通过（35 个测试类）
- [X] T025 [US1] 按 `quickstart.md` 一（1–6）、二（7–10）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：创意栏/JEI/配方、中英文名、图标是一张平面贴图；四个区域的放入；功能区里左键展开/收起、右键拿起、光标非空时按普通点击；展开覆盖主区域、随缩放变化、20 次点击主区域无变化、拿走自动收起；**宿主界面（没开容器界面）与容器界面各做一遍**；结果记到 DELIVERY.md（没做的项记"未验证"，**由用户确认后才打勾**）
  - **用户确认（2026-11-05）：图标与配方、展开/收起与覆盖主区域没有问题（总体确认，未逐项记录四个区域的放入、20 次点击主区域无变化、缩放跟随、拿走自动收起）**

**Checkpoint**: US1 达成 FR-001～FR-005、FR-019（贴图）、SC-001、SC-002、SC-009（配方部分）。

---

## Phase 4: User Story 2 - 查看选中物品的标签 (Priority: P1)

**Goal**: 标签数据通过新网络包同步到客户端（协议 8）；面板按选区算出并显示共同标签、状态行、加号（本阶段加号只显示，不可点）。

**Independent Test**: quickstart 三（11–14）：先用创意栏/命令给背包写入标签数据，或等 US3 完成后再完整验证；本阶段可用一个临时调试命令或在 DELIVERY 里注明"待 US3 联合验证"。

- [X] T026 [P] [US2] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/TagPanelModelTest.java`（**先写，先失败**），用假分类 id（`String`）：单选一个有标签的分类 → `commonTags` 为它的全部标签；多选三种分类 → 只含交集，没有交集时为空；同种物品多格只算一个分类；空格子被忽略；没有任何有物品的选中格子 → `status == NO_ITEMS`、`canAdd` 为假；有分类标签数达 10 → `fullItems` 列出它、`canAdd` 为假；工具（功能区标签管理器）不存在时 `canAdd` 为假
  - **结果**：`TagPanelModelTest` 8 个用例通过
- [X] T027 [P] [US2] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/TagPanelLayoutTest.java`（**先写，先失败**），文字宽度用假的"每字符 6 像素"：状态行占第 1 行（18 像素）；标签块宽 = 文字宽 + 内边距，一行放不下换行；"无"状态下只有加号；加号在最后一个元素之后（必要时换行）；编辑中文本框独占一行、宽 = 面板宽，下拉最多 5 项叠在文本框下方；`pendingDelete` 的标签右侧多出 ✕ 区域；内容超出高度时 `maxScroll > 0`，滚动后的矩形整体上移；`hit(x, y)` 对标签、✕、加号、文本框、下拉项、状态行、空白、滚动后的位置都给出正确结果；最小主区域（3×3 = 54×54 像素）下仍可排版
  - **结果**：`TagPanelLayoutTest` 12 个用例通过；布局约定：状态行 18 像素；标签块高 14、行高 18、块内边距 4、间距 2；文本框宽 = 面板宽 − 4（左右各留 2）；下拉最多 5 项；滚动按整行（18 像素）
- [X] T028 [P] [US2] 新建 `…/client/panel/tags/TagPanelModel.java`：纯逻辑，输入选区下标集合、`stackAt(i)` 给出的分类 id（空格子为 null）、`ItemTags`（或 `TagBook<String>`）、功能区是否有工具；输出 `status`、`categories`（去重，首次出现顺序）、`commonTags`、`fullItems`、`canAdd`，字段照 data-model.md。使 T026 通过。依赖 T010
  - **实现说明**：`TagPanelModel<K>` 泛型（测试用 `String`，游戏里 K = `ResourceLocation`）
- [X] T029 [P] [US2] 新建 `…/client/panel/tags/TagPanelLayout.java`：纯逻辑，输入面板像素宽/高（主区域列数×18、行数×18）、`IntUnaryOperator`/函数式的文字宽度测量、标签列表、是否编辑中、下拉项数、滚动、`pendingDelete`；输出各矩形、`maxScroll`、`hit(x, y)`，规则照 data-model.md 与 contracts/tag-panel-ui.md §3。使 T027 通过
- [X] T030 [US2] 新建 `…/network/BackpackTagsPayload.java`（服务端→客户端，整份 `ItemTags`，`STREAM_CODEC` 用 `ItemTags.STREAM_CODEC`，客户端 `handleClient` 调 `ClientBackpackView.get().setTags(...)`）；修改 `…/network/BackpackNetwork.java`：协议 `versioned("7")` → `versioned("8")`，注册 `BackpackTagsPayload`，并在 `sendSession(open)` 之后向请求者发送当前背包的标签；修改 `…/client/panel/ClientBackpackView.java`：保存 `ItemTags`（`tags()`/`setTags`）、`clear()` 时复位。依赖 T014
  - **结果**：`BackpackTagsPayload` 用 `StreamCodec.composite(ItemTags.STREAM_CODEC, …)`；协议 `"7"` → `"8"`；`sendSession(open)` 之后发送标签；`ClientBackpackView` 保存/清除 `ItemTags`
- [X] T031 [US2] 修改 `…/client/panel/tags/TagPanelRenderer.java`：展开时用 `TagPanelModel` + `TagPanelLayout` 画状态行（`NO_ITEMS` 显示 `tag.mcfloatingbackpack.hint.select`，否则 `tag.mcfloatingbackpack.status` 带分类数）、共同标签块（预设显示本地化名称，自定义原样）、"无"、加号（灰/正常）；内容超出时随 `TagPanelState.scroll` 滚动；`FloatingPanelEvents.scrollPanel` 把滚轮交给 `TagPanelState.scroll`（夹在 `0..maxScroll`）；选区下标取自 `PanelSelection`（只读）。依赖 T028、T029、T030、T023
  - **实现说明**：新增 `TagPanelContext`（把纯类接到游戏：选区 → 分类 id、标签的本地化显示、排版）；渲染用 `enableScissor` 把内容裁在状态行以下；滚轮每档一行（钳制在 0..maxScroll）
- [X] T032 [US2] 运行 `./gradlew compileJava test`，确认 T026、T027 与全部既有测试通过；确认协议版本 `"8"` 只改了这一处
  - **结果**：`./gradlew compileJava test` 通过（37 个测试类）；协议版本 `"8"` 只在 `BackpackNetwork` 改了这一处
- [X] T033 [US2] 按 `quickstart.md` 三（11–14）在 `./gradlew runClient` 验证（先 `pgrep`）：本阶段还没有增加标签的界面，所以**数据入口用一个仅开发期的手段**（例如临时的 `/give` 带 `tags` 组件、或先做 US3 再合并验证）——在 DELIVERY.md 里如实写明用了什么手段；检查单选、多选交集、无交集、含重复同种与空格子、没有选区时的提示、标签多时换行与滚轮；宿主界面与容器界面各做一遍；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **用户确认（2026-11-05）：功能测试过，没有明显问题（总体确认，未逐项记录）；第一轮发现的两个问题（状态行截断、点击下拉项）已修复并在重测中没有再出现问题**

**Checkpoint**: US2 达成 FR-006、FR-018（面板文字）、SC-003、协议 8 的同步部分。

---

## Phase 5: User Story 3 - 增加标签：加号、下拉文本框、回车添加、ESC 取消 (Priority: P1)

**Goal**: 点击加号变文本框并获得键盘焦点，预设下拉模糊匹配，回车添加（服务端校验并原子执行），ESC 取消；键盘只在编辑时通过 `ScreenEvent` 被接管，宿主与容器界面同一条路径。

**Independent Test**: quickstart 四（15–23）、五（24–29）：预设与自定义添加、非法字符、ESC 只取消编辑、编辑时 `w a s d e t` 不触发任何游戏按键、没有焦点时不拦截。

- [X] T034 [P] [US3] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/tags/TagTextInputTest.java`（**先写，先失败**）：`type` 接受小写字母/数字/符号、拒绝大写与空格、长度到 16 为止；`paste` 逐字符过滤（大写被丢弃）并截断；`backspace`/`delete`/`left`/`right`/`home`/`end` 的光标行为与边界；`clear`；`caret` 始终在 `0..length`
  - **结果**：`TagTextInputTest` 7 个用例通过
- [X] T035 [P] [US3] 新建 `…/client/panel/tags/TagTextInput.java`：纯逻辑文本缓冲与光标，规则照 data-model.md。使 T034 通过。依赖 T007
- [X] T036 [US3] 新建 `…/network/BackpackTagPayload.java`（客户端→服务端：`op` 字节、`slots` VarInt 列表 ≤ 108、`tag` UTF-8 ≤ 16 字符，解码超限抛 `DecoderException`）与服务端 `handleServer`，**ADD 与 REMOVE 两种操作都在这里实现**，严格按 contracts/tags-network-storage.md §3 的六步：`ServerPlayer` 与 `BackpackRef.resolve`/`stillValid`（失败 `sendSession(false)`）→ 功能区里有 `ModItems.TAG_MANAGER` → `TagRequest.validate` → 下标解析成非空主格子的物品 id 去重 → ADD：`TagBook.canAdd` 为假或分类数将超过 1024 则整个请求拒绝，否则逐个添加 / REMOVE：逐个移除 → `ref.saveInventory` 并向请求者发 `BackpackTagsPayload`；在 `BackpackNetwork` 注册。任何一步失败 = 数据零变化。依赖 T011、T014、T030
  - **实现说明**：服务端不回显错误；校验顺序与 contracts/tags-network-storage.md §3 一致；REMOVE 与 ADD 在同一个处理里实现（US4 只需客户端界面）
- [X] T037 [US3] 新建 `…/client/panel/tags/TagPanelInput.java`：与游戏相连。①面板点击分派（contracts/tag-panel-ui.md §2）：点击可用加号 → `editing=true`（文本框获得焦点、`TagTextInput` 清空或沿用上次 `text`）；点击文本框 → 移动光标；点击下拉项 → 设 `dropdownIndex`；点击标签、✕、其它空白按 §2 取消编辑/待删除；②键盘分派：编辑中 `type`/退格/删除/方向键（左右移动光标，上下移动 `dropdownIndex`）/Home/End/粘贴（`Screen.isPaste`）；回车 → 若 `dropdownIndex ≥ 0` 添加该预设，否则 `text` 合法则添加 `text`，空或不合法什么都不做；添加时发 `BackpackTagPayload(ADD, 选区下标, tag)` 并把 `editing` 置假；**ESC → 取消编辑**（不关浮动面板、不弹暂停菜单）；③`TagMatcher` 的输入：`PresetTags.ids()` 配 `Component.translatable(langKey).getString()`。依赖 T028、T029、T035、T036
  - **实现说明**：回车时先取下拉里高亮的预设，否则取输入的文本，非法或为空什么都不做（保持编辑）；点击文本框以外取消焦点，主区域之外的按下在 `FloatingPanelInput` 开头取消，主区域内由 `TagPanelInput` 判断；ESC 取消编辑但保留文本，成功添加后清空文本
- [X] T038 [US3] 修改 `…/client/FloatingPanelEvents.java`：新增 `ScreenEvent.KeyPressed.Pre`、`ScreenEvent.KeyReleased.Pre`、`ScreenEvent.CharacterTyped.Pre` 三个处理器，仅当 `hostsPanel(event.getScreen())`（宿主界面或容器界面，019 已提供）且 `TagPanelState.editing` 时把事件交给 `TagPanelInput` 并取消；**没有焦点时一律不取消**；不修改 `onKey`（被 `ScreenEvent` 取消的按键不会再发出 `InputEvent.Key`，见 contracts/tag-panel-ui.md §4）。依赖 T037
  - **实现说明**：三个 `ScreenEvent` 前置事件只在 `hostsPanel(screen)` 且 `TagPanelState.isEditing()` 时取消；`onKey` 未改
- [X] T039 [US3] 修改 `…/client/panel/tags/TagPanelRenderer.java`：编辑中画文本框（含光标闪烁、占位文字 `tag.mcfloatingbackpack.add.placeholder`）与下拉（≤ 5 项，选中项高亮，显示预设的本地化名称与 id）。依赖 T037
- [X] T040 [US3] 运行 `./gradlew compileJava test`，确认 T034 与全部既有测试通过；`grep -rn "setScreen(" …/client/panel/tags` 应无命中（本 feature 不新增任何 Screen）
  - **结果**：测试通过（38 个测试类、320 个用例）；`grep -rn "setScreen(" …/client/panel/tags` 无命中
- [X] T041 [US3] 按 `quickstart.md` 四（15–23）、五（24–29）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：预设下拉与模糊匹配（中英文界面）、回车添加、自定义标签、大写/空格/中文输入不进、超长到 16 为止、ESC 只取消编辑且不关浮动面板不弹暂停菜单、重复添加不重复、多选添加到全部分类、空回车无反应；键盘：**没有焦点时按 E 仍能经宿主打开物品栏**，编辑时输入 `w a s d e t` 只进文本框、不开物品栏/聊天、宿主不让出不闪，点别处/回车/ESC 后焦点立即释放；宿主界面与容器界面各做一遍；结果记到 DELIVERY.md（由用户确认后才打勾）；**这一步完成后顺带补做 T033 因缺入口而未能完整验证的内容**
  - **用户确认（2026-11-05）：功能测试过，没有明显问题（总体确认，未逐项记录）；第一轮发现的两个问题（状态行截断、点击下拉项）已修复并在重测中没有再出现问题**

**Checkpoint**: US3 达成 FR-007～FR-010、FR-013、FR-015、FR-016、SC-004（添加部分）、SC-006、SC-007。**US1 + US2 + US3 合起来是可用的最小工具。**

---

## Phase 6: User Story 4 - 删除标签 (Priority: P2)

**Goal**: 点击标签出现 ✕，点 ✕ 把标签从所有选中分类上删除；点别处取消。

**Independent Test**: quickstart 六（30–32）。

- [X] T042 [US4] 修改 `…/client/panel/tags/TagPanelInput.java` 与 `TagPanelRenderer.java`：点击标签 → `pendingDelete = 该标签`（再点另一个标签则换成另一个）；点击其 ✕ → 发 `BackpackTagPayload(REMOVE, 选区下标, tag)` 并清空 `pendingDelete`；点击其它位置 → 清空 `pendingDelete`；渲染 ✕ 区域（用 `fill` + 字体画 "✕"，不新增贴图）。服务端的 REMOVE 已在 T036 实现。依赖 T037
  - **实现说明**：点击标签设 `pendingDelete`（换另一个标签即换掉），点 ✕ 发 `REMOVE`，点别处清空；✕ 区域用 `fill` + "x" 画，不新增贴图
- [X] T043 [US4] 补充 `TagPanelLayoutTest` 中 `pendingDelete` 的 ✕ 命中用例（若 T027 尚未覆盖）；运行 `./gradlew compileJava test`
  - **结果**：`TagPanelLayoutTest` 已含 `pendingDelete` 的 ✕ 命中用例；`./gradlew compileJava test` 通过
- [X] T044 [US4] 按 `quickstart.md` 六（30–32）在 `./gradlew runClient` 验证（先 `pgrep`）：点标签出 ✕、点 ✕ 删除、点别处取消、多选只显示共同标签且删除后所有选中分类都不再有它、删除后加号恢复可用；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **用户确认（2026-11-05）：点标签出 ✕、点 ✕ 删除没有问题（未逐项记录"点别处取消"、多选删除、删除后加号恢复）**

**Checkpoint**: US4 达成 FR-011。

---

## Phase 7: User Story 5 - 每个分类最多 10 个标签，满了加号变灰 (Priority: P2)

**Goal**: 选中物品里有分类已满 10 个时加号灰色、悬停提示列出已满的物品并说明不支持批量新增；服务端拒绝超限请求。

**Independent Test**: quickstart 七（33–36）。

- [X] T045 [US5] 修改 `…/client/panel/tags/TagPanelRenderer.java` 与 `TagPanelInput.java`：`canAdd` 为假时加号画灰色且点击无效；悬停灰色加号时用 `renderTooltip` 列出 `fullItems` 里每种物品的名称（`Item.getDescription()`）并附 `tag.mcfloatingbackpack.full.batch`（"标签已满的物品不支持批量新增"）；`NO_ITEMS` 时悬停提示"请先选择物品"。服务端的上限拒绝已在 T036 实现。依赖 T037
  - **实现说明**：灰色加号点击无效；悬停提示用 `renderComponentTooltip`：`NO_ITEMS` 时"请先选择物品"，否则列出已满物品名与"不支持批量新增"
- [X] T046 [US5] 补充服务端侧纯逻辑断言（若 T005/T006 尚未覆盖）："已满分类 + 新标签"整个请求拒绝且零变化、"恰好 9 个"添加成功后变 10、已有该标签的已满分类不算超限；运行 `./gradlew compileJava test`
  - **结果**：`TagBookTest` 已覆盖"已满分类 + 新标签整个请求拒绝且零变化"、"恰好 9 个添加成功后变 10"、"已有该标签的已满分类不算超限"
- [X] T047 [US5] 按 `quickstart.md` 七（33–36）在 `./gradlew runClient` 验证（先 `pgrep`）：加到 10 个后灰色、单选与多选都灰、悬停提示列出已满物品且多选时只列已满的那一种、删除后恢复、恰好 9 个添加成功；另在 DELIVERY.md 注明对服务端拒绝路径用了什么手段验证（客户端状态过期难以构造时记"仅单元测试覆盖"）；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **用户确认（2026-11-05）：功能测试过，没有明显问题（总体确认，未逐项记录）；第一轮发现的两个问题（状态行截断、点击下拉项）已修复并在重测中没有再出现问题**

**Checkpoint**: US5 达成 FR-012、SC-004（上限部分）、SC-007。

---

## Phase 8: User Story 6 - 标签跟着背包走、跟着分类走，并且不影响现有功能 (Priority: P2)

**Goal**: 持久化、隔离、升级保留、旧背包兼容；011～017 回归。

**Independent Test**: quickstart 八（37–42）。

- [X] T048 [US6] 审阅（读代码，不改行为）：①`BackpackUpgradeRecipe` 升级后 `tags` 保留（T014 已改，核对）；②`BackpackContents.normalize/fit` 在容量缩小时不丢 `tags`；③读入旧存档（无 `tags` 字段）为 `ItemTags.EMPTY`；④服务端对一只背包分类数 > 1024 的拒绝路径；发现缺口就补并记录，没有缺口也记"已审阅，无需改动"
  - **结果**：已审阅，无需改动：升级配方保留 `tags`；`normalize/fit/copy` 都传递 `tags`；旧存档读成 `ItemTags.EMPTY`；所有保存路径（点击、拖动、转移、合并光标、取出）都是 `loadInventory` 后 `saveInventory`，`BackpackInventory` 带着 `tags`；服务端对分类数 > 1024 的拒绝在 `BackpackTagPayload`
- [X] T049 [US6] 按 `quickstart.md` 八（37–42）在 `./gradlew runClient` 验证（先 `pgrep`）：取走物品标签仍在、关闭面板/退出重进标签仍在、收起再展开输入框内容与滚动位置仍在、两只背包互不共享、锻造台升级保留、旧背包（018 之前保存）能打开且无标签无报错；**重跑 011～017 的 quickstart 中与面板有关的场景**（Shift 选区、Ctrl 转移、拖动手势、缩放、工具区存放、合法尺寸），结果与之前一致；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **用户确认（2026-11-05）：功能测试过，没有明显问题（总体确认，未逐项记录）；第一轮发现的两个问题（状态行截断、点击下拉项）已修复并在重测中没有再出现问题**

**Checkpoint**: US6 达成 FR-014、FR-017、SC-005、SC-008。

---

## Phase 9: Polish & Cross-Cutting

- [X] T050 [P] 更新文档：`specs/016-panel-tool-areas/` 与 `specs/017-panel-valid-sizes/` 的 spec 加一段 018 说明（功能区现在可放标签管理器并展开覆盖主区域的标签面板）；`docs/MCFloatingBackpack.md` 在 017 说明之后加 018 说明（标签管理器、标签按物品分类存放在背包数据里、协议 8）；**不要修改 `docs/ideas`**
  - **结果**：016/017 的 spec 与 `docs/MCFloatingBackpack.md` 已加 018 说明；未改 `docs/ideas`
- [ ] T051 按 `quickstart.md` 九（43–46）：`./gradlew compileJava test` 退出码 0；先 `pgrep` 确认没有运行中的客户端，再跑 `design/demo/tools/check_atlas_budget.sh`，图集应仍为 2048×1024（宪法 VI；本 feature 只新增一张 16×16 物品贴图），结果记到 DELIVERY.md——**跑不了就写"未度量"，不要声称已满足**；检查 `run/logs/latest.log` 无新增报错；`runClientCompat` 下精妙背包、旅行者背包界面里面板与标签管理器行为不受影响（由用户确认后才打勾）
  - **状态（2026-11-05）**：自动部分已做（`./gradlew compileJava test` 通过）；**图集度量（需先 `pgrep`）、日志检查、`runClientCompat` 兼容检查未做**，等用户确认后才打勾
- [X] T052 静态检查：`git diff --stat` 确认没有改动 `QuickTransferInput/Gesture/Rules`、`PanelDragInput`、`PanelSelectionInput` 的逻辑，也没有改动 `ToolAreaRules`（仍恒为真）；`grep -rn "p3pp3rf1y\|tiviacz" src/main/java/pnnh/mcfloatingbackpack/inventory/tags src/main/java/pnnh/mcfloatingbackpack/client/panel/tags src/main/resources/data/mcfloatingbackpack/recipe/tag_manager.json` 无命中（宪法 IV）；`grep -rn "extends Screen" src/main/java` 只有 019 的 `PanelHostScreen`（本 feature 不新增任何 Screen）
  - **结果**：见 DELIVERY.md 静态检查一节，全部符合
- [X] T053 新建 `specs/018-tag-manager/DELIVERY.md`（中文）：汇总自动化结果（T001 基线与 T015/T024/T032/T040 的编译与测试结果、新增测试类与用例数）、与 plan/tasks 的偏离、**每个客户端验证项实际验证了什么 / 未验证什么**、图标的生成方式与来源（提示词、脚本、是否退回手绘）、图集度量结果或"未度量"、已知限制（Windows/Linux 未验证；输入法 IME 不支持；一只背包最多 1024 个分类的内部保护）、对 019 宿主界面的依赖
  - **说明**：已写；**所有客户端验证项均如实记为未验证**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1（Setup）**：无依赖，先做。
- **Phase 2（Foundational）**：依赖 Phase 1。T002～T006、T012 可并行写测试；T007～T011 可并行实现（T010、T011 依赖 T007）；T013 → T014 → T015。**阻塞所有用户故事。**
- **Phase 3（US1）**：依赖 Phase 2。T016 与 T019/T020 可并行；T017 → T018；T021、T022、T023 依赖 T019；T024 → T025。
- **Phase 4（US2）**：依赖 US1（展开面板与渲染骨架）和 T014。T026、T027、T028、T029 可并行（测试先于各自的实现）；T030 可与它们并行；T031 依赖 T028～T030；T032 → T033。
- **Phase 5（US3）**：依赖 US2（布局、模型、同步）。T034 ∥ T035；T036 依赖 T030；T037 依赖 T028/T029/T035/T036；T038、T039 依赖 T037；T040 → T041。
- **Phase 6（US4）**、**Phase 7（US5）**：依赖 US3（共用 `TagPanelInput`/`TagPanelRenderer`/`BackpackTagPayload`）；两者互相独立。
- **Phase 8（US6）**：依赖前面所有故事的代码。
- **Phase 9（Polish）**：T050 可在 US1 稳定后随时做；T051～T053 在所有故事完成后。

### User Story Dependencies

- US1 → US2 → US3 构成最小可用工具链；US4、US5 在 US3 之上各自独立；US6 汇总验证。

### Parallel Opportunities

- Phase 2：T002～T006、T012（六个测试文件）可同时写；T007～T009、T011 与 T010 可同时实现。
- Phase 3：T016 ∥ T019/T020。
- Phase 4：T026 ∥ T027 ∥ T030，T028 ∥ T029。
- Phase 5：T034 ∥ T035。
- Phase 9：T050 与其它不冲突。

### Parallel Example: Phase 2

```text
同时写测试：T002 TagRulesTest  T003 PresetTagsTest  T004 TagMatcherTest  T005 TagBookTest  T006 TagRequestTest  T012 ItemTagsTest
同时实现：  T007 TagRules  T008 PresetTags  T009 TagMatcher  （然后）T010 TagBook  T011 TagRequest
然后：      T013 ItemTags → T014 BackpackContents/Inventory/升级配方 → T015 运行测试
```

---

## Implementation Strategy

### MVP First（US1 → US2 → US3）

1. 完成 Phase 1、Phase 2（纯逻辑 + 数据组件，不碰游戏界面）。
2. 完成 US1：物品、配方、图标、展开/收起——此时可以在游戏里看到工具，但面板还是空的。
3. 完成 US2：同步与显示。
4. 完成 US3：增加标签与键盘接管。到这里才是可用的最小工具；用户验证 T025、T033、T041 后再继续。

### Incremental Delivery

1. MVP（US1 + US2 + US3）→ 用户验证。
2. US4（删除）→ 验证。
3. US5（10 个上限的灰色加号与提示）→ 验证。
4. US6（持久化与回归）→ 验证。
5. Polish：文档、图集度量、静态检查、交付说明。

### 风险提示（实现时盯住）

- **键盘不泄漏**：编辑时三个 `ScreenEvent` 前置事件必须在界面自己处理之前取消，否则宿主会把 `e`、`t` 当作"让出"的按键（019 的 `HostKeyRouter`）；没有焦点时绝不取消任何事件（T038、T041）。
- **服务端权威**：客户端只发选区下标与标签字符串，物品 id 由服务端从背包真实内容解析；任何一步校验失败整个请求零变化（T036）。
- **协议升级**：`"7"` → `"8"` 只在 `BackpackNetwork` 改一处；`BackpackContents.STREAM_CODEC` 增加第 4 个字段，不超过 `composite` 的 6 个上限（T014、T030）。
- **011～017 不回归**：标签面板展开时对主区域输入的消费只发生在 `FloatingPanelInput` 的分派层，不修改 011/013/014 的输入类（T052）。
- **图集预算**：只新增一张 16×16 物品贴图；度量不了就如实写"未度量"（T051）。
