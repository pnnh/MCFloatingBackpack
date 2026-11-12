# Tasks: 背包搜索核心

**Input**: `/specs/042-backpack-search-core/` 的 spec.md、plan.md、research.md、data-model.md、contracts/query-syntax.md、quickstart.md；宪法

**Tests**: 查询解析、匹配、结果集、输入模型、面板几何映射、标题栏与搜索框布局、状态重置用 JUnit 覆盖（纯 Java，不加载 Minecraft 类）；语言键与"没有新增网络包/配置/协议号仍 17"用源码文本检查覆盖。**搜索框的焦点与键盘接管、窄面板上的实际排布、命中高亮与选框叠放的观感、结果里各种操作与平时一致、拿走再放回、中文输入只能在客户端会话里验证**（宪法 V）：编译与单元测试通过不等于界面成立。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` 表示 `src/main/resources/assets/mcfloatingbackpack/`；`…/client/panel/search/` 是新增的包
- **不包含提交 Git 的任务**（等用户要求再提交；**也不打 tag**）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，已有就不启动第二个**；容器自动验证见 `scripts/ai-gui/README.md`
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；AI 自动验证写"AI 自动验证"备注与证据；没做的写"未验证"
- **不改**服务端代码、任何网络包、协议号 17、配置、命令、数据格式、依赖；**不新增贴图**；不修改 `docs/ideas`
- 用户故事：US1 按名字找东西；US2 窄面板（3 列）也能用；US3 结果里照常操作；US4 结果与原主区域不会搞混；US5 拿走再放回仍在原位；US6 基础搜索语法

---

## Phase 1: Setup

- [X] T001 基线：`./gradlew test` 记下当前类数与用例数；`git status` 确认工作区只有 042 的文档改动；记在 DELIVERY.md（新建，中文）

## Phase 2: Foundational（纯 Java，不碰游戏对象；T002～T007 互相独立可并行）

- [X] T002 [P] `…/client/panel/search/SearchQuery.java`：按 contracts/query-syntax.md 与 research D6 解析：`SearchQuery.parse(String)`，`terms()`（`List<Term>`，`Term` 是 `List<Alternative>`，`Alternative(boolean negated, String text)`，text 已 `toLowerCase(Locale.ROOT)` 且非空）、`isEmpty()`；引号切换整段模式、未闭合到末尾、`|` 与空白只在整段模式外起作用、`-` 只在备选第一个字符时是排除、空备选与空项忽略；常量 `MAX_CODEPOINTS = 100`。`…test/client/panel/search/SearchQueryTest.java`：contracts 里解析用例表逐行（含 `@iron #x /hidden` 当普通字符、`"a | b" c`、`iron|-gold`）
- [X] T003 [P] `…/client/panel/search/TextMatcher.java`（函数式接口 `boolean contains(String needleLowerCase)`，静态 `ofTexts(List<String>)`：任一已小写的文字含该子串）与 `SearchMatcher.java`（`matches(SearchQuery, TextMatcher)`：空查询 true；项成立 = 任一备选成立；正备选 = `contains`，排除备选 = `!contains`；所有项都成立）。`…test/client/panel/search/SearchMatcherTest.java`：并且（词序无关、多词名字 Oak Log / Stripped Oak Log）、或、`-planks`、只有排除项、整段 `"oak log"` 要求连续、`-"oak log"`、大小写、`iron|-gold` 的字面语义
- [X] T004 [P] `…/client/panel/search/SearchResults.java`：`compute(SearchQuery, int slotCount, IntFunction<TextMatcher>)` 返回升序 `int[]`（`matcherOf(i)` 对空格子返回 null → 永不命中；空查询返回 null = 不过滤）；`liveCount(int[] slots, IntPredicate hasItem)`。`…test/client/panel/search/SearchResultsTest.java`：空格不命中、升序、空查询 null、只有排除项 = 有物品且不被排除、留位（给定数组后 `liveCount` 随 `hasItem` 变化而下降）、名字相同的多格各占一格
- [X] T005 [P] `…/client/panel/search/SearchTextInput.java`：按 data-model §4：UTF-16 文本、按码点的 `insert(int)`（控制字符丢弃、满 100 个码点丢弃）、`paste`、`backspace/delete/left/right/home/end/clear`（按码点移动，代理对不被劈开）、`selectAll/hasSelection`（全选后 `insert/backspace/delete` 先清空全部文字，其它移动键取消全选）。`…test/client/panel/search/SearchTextInputTest.java`：中文字符、补充平面字符（emoji）、长度上限、粘贴截断、全选后替换/退格、光标边界
- [X] T006 [P] `…/client/panel/PanelLayout.java`：`of(…)` 增加可选 `int[] mainMap` 参数（保留原六参数的 `of` 重载等价于 null）；有映射时 `slotCount(MAIN)`、`slotIndex(MAIN, ordinal)`、`mainTotalRows`、`maxScroll`、`hit` 按 research D1/data-model §6；无映射的路径逐字不变。`…test/client/panel/PanelLayoutMappingTest.java`：映射的行列排布、滚动偏移、越界返回 -1、`hit` 返回映射后的格子编号、行数不足时多余 ordinal 为 -1、**无映射时与现状逐点相同**（取一组代表性参数对照）；现有 `PanelLayoutTest`、`PanelLayoutGeometryTest` 必须不改而通过
- [X] T007 [P] `…/client/panel/TitleBarLayout.java` 与 `SearchBoxLayout.java`（纯算术，research D4 与 data-model §7）：`TitleBarLayout.of(panelX, panelWidth, showClose, searching)` 给出关闭键位置（无则 none）、放大镜与快照键位置、编号文字右限、搜索框 `boxLeft/boxRight`；让位优先级关闭 > 放大镜 > 快照 > 编号，放大镜任何宽度都保留；常量沿用 `TitleButtonsLayout`（按钮 10、右边距 3、间隔 2、编号起点 `+23`、`MIN_TEXT = 24`）。`SearchBoxLayout.of(boxLeft, boxRight, countTextWidth)` 给出文字区、命中数位置（文字区 < 36 像素时 none）、`×` 位置。`…test/client/panel/TitleBarLayoutTest.java` 与 `SearchBoxLayoutTest.java`：research D4 表里 3、4、9 列的每个数（3 列无关闭键且放大镜 79～89、快照 91～101、搜索框 23～101；4 列关闭键 109～119…）、各宽度下元素互不重叠不越界、编号不足 24 时快照先让位、3 列搜索框命中数显示且更窄时让位
- [X] T008 运行 `./gradlew compileJava test`

**Checkpoint**: 解析、匹配、结果集、输入模型、几何映射、标题栏与搜索框布局都就绪，全部可在不启动游戏的情况下测试

## Phase 3: User Story 1 - 按名字找东西 (Priority: P1) 🎯 MVP

**Goal**: 点放大镜 → 搜索框 → 实时过滤主区域 → Esc/× 清除；搜索框有焦点时键盘归它。

**Independent Test**: quickstart A。

- [X] T009 [US1] `…/client/panel/search/SearchState.java`（会话单例）与 `SearchContext.java`：`SearchState` 持 `open/focused/input/query/slots`；`openBox()`、`close()`（清空并关闭，等于 Esc）、`setText` 后统一走 `onQueryChanged()`：重新解析、`SearchResults.compute`、`PanelSelection.get().clear()`、`FloatingPanelState` 滚动回第 0 行、把 `slots` 推给 `FloatingPanelState.setSearchSlots`；`reset()` 全部清空并推 null。`SearchContext.matcherOf(slot)`：空格返回 null，否则 `TextMatcher.ofTexts(List.of(stack.getHoverName().getString(), 注册名路径))` 全部转小写（research D6/D7）；另给 `toolMatches(area, index)`（工具区格子，带按"物品 + 自定义名"的小缓存）。`…test/client/panel/search/SearchStateTest.java`：用假的 `matcherOf`（不依赖 Minecraft 类的重载）测 `reset`、查询变化清选区与滚动、`close` 等于清空、空文字 `slots == null`
- [X] T010 [US1] `…/client/panel/FloatingPanelState.java`：新增 `searchSlots` 与 `setSearchSlots(int[])`；`layoutModel(counts)` 把它传给 `PanelLayout.of`（`ClientBackpackView.layout()` 与 `PanelSlots.indexAt` 都经它，research D1）；`clampScroll` 的主区域用 `searchSlots == null ? totalRows : ceil(searchSlots.length / visibleCols)`，`totalRows` 与尺寸逻辑不动（D2）；设置映射与列数变化后重新夹取。`…test/client/panel/` 追加用例（可放进现有 `FloatingPanelStateTitleIconTest` 同目录的新类 `FloatingPanelStateSearchTest`）：有映射时滚动上限按结果行数、`totalRows` 不变、清空映射后恢复、`PanelSlots.indexAt` 在有映射时返回映射后的格子编号
- [X] T011 [US1] 标题栏改用 `TitleBarLayout`：`FloatingPanelState.titleButtons()` 改为返回 `TitleBarLayout`（`showClose` 先恒为 true，T018 才加 3 列规则），`closeButtonLeft`、`hitsCloseButton`、`hitsSnapshotButton`、`FloatingPanelRenderer.drawCloseButton/drawSnapshotButton/drawTitleId` 与 `TitleIdLayout` 的调用点全部改为读它；删除 `TitleButtonsLayout`，把 `TitleButtonsLayoutTest` 的有效用例并入 `TitleBarLayoutTest`；`./gradlew test` 后行为与改前逐点相同（编号截断、快照键让位）；新增 `FloatingPanelState.hitsSearchButton`
- [X] T012 [US1] `…/client/panel/search/SearchInput.java` 与接线：`onKeyPressed(keyCode, scanCode, modifiers)`（Esc → `SearchState.close()`；Backspace/Delete/左右/Home/End；Ctrl+A 全选；`Screen.isPaste` 粘贴；其余忽略但**总是被取消**）、`onCharTyped(int codePoint)`（过滤控制字符后按码点插入）、`onMousePress(button, x, y)`（放大镜 → 打开并聚焦；`×` → 清除并关闭；搜索框内 → 聚焦并按 x 定光标；返回是否消费）、`loseFocusIfOutside(x, y)`。`…/client/FloatingPanelEvents.java`：`onScreenKeyPressed`、`onScreenKeyReleased`、`onScreenCharTyped` 各加一个 `SearchState.get().isFocused()` 分支（取消事件；**Esc 在弹窗确认之后处理**，保持 `TrashConfirmInput.onEscape` 的优先）；`…/client/panel/FloatingPanelInput.java` 的按下分支按 research D4 的顺序：先失焦判断，再放大镜 → `×` → 搜索框 → 快照键 → 关闭键 → 缩放把手 → 拖动把手；`…/client/panel/DeleteKeyInput.java` 的"正在编辑"条件加上搜索焦点
- [X] T013 [US1] `…/client/panel/search/SearchRenderer.java` 与 `FloatingPanelRenderer` 接线：放大镜按钮（10×10，`fill` 画圆圈加手柄，风格与快照键一致，悬停提示"搜索"）；搜索打开时画搜索框（背景、搜索色边框、占位提示"搜索…"、文字与光标闪烁、框内右端 `×` 与命中数，命中数 = `SearchResults.liveCount`，悬停框显示命中数提示）；搜索打开时编号、放大镜、快照键不画；主区域命中格画命中边框（先用候选深蓝 `0xFF1E3FD0`，T024 对照后定色）。不新增贴图
- [X] T014 [US1] 重置点与语言键：`FloatingPanelEvents.closePanel()`、`onLoggingIn`、登出处各加 `SearchState.get().reset()`；`…/network/BackpackSessionPayload.java` 新会话处（与 `TagOverviewState.get().resetView()` 同处）加 `reset()`；`…res/lang/en_us.json` 与 `zh_cn.json` 加 `panel.mcfloatingbackpack.search.tooltip`、`search.clear`、`search.placeholder`、`search.count`（命中数的悬停说明）；`…test/client/panel/search/SearchResourcesTest.java`（源码文本检查）：四个键两种语言齐全且一致、`MCFloatingBackpack`/网络注册里没有新增载荷（协议仍 `"17"`）、`SearchState.reset()` 在 `closePanel` 与 `BackpackSessionPayload` 里被调用、`FloatingPanelEvents` 的三个键盘事件都有搜索分支
- [X] T015 运行 `./gradlew compileJava test`

**Checkpoint**: 单个关键词的搜索可用（MVP）：打开、实时过滤、命中框、Esc/× 清除、键盘归搜索框

## Phase 4: User Story 2 - 窄面板（3 列）也能用 (Priority: P1)

**Goal**: 3 列时没有关闭键，放大镜、搜索框、`×`、拖动、Esc 退出都可用。

**Independent Test**: quickstart B。

- [X] T016 [US2] `FloatingPanelState.showClose()`（`visibleCols > MIN_VISIBLE_CELLS`）接入 `TitleBarLayout.of(...)` 的 `showClose` 参数；`hitsCloseButton` 在无关闭键时为 false；`FloatingPanelRenderer.drawCloseButton` 无关闭键时不画；`FloatingPanelInput` 里关闭键判断同步。`…test/client/panel/` 追加：3 列 `hitsCloseButton` 恒 false、4 列照旧、3 列的快照键与放大镜位置与 `TitleBarLayoutTest` 一致、`showClose` 随列数变化
- [X] T017 [US2] 搜索时的拖动与缩放：按下落在搜索框内只定光标不拖动、`×`/放大镜不拖动、背包图标与框外标题栏仍拖动（`FloatingPanelInput` 顺序已由 T012 定，这里补用例）；缩放（`updateResize`）期间搜索保持、结果按新列数重排、滚动夹取；`…test/client/panel/` 追加（`FloatingPanelStateSearchTest`）：搜索中把 `visibleCols` 从 9 改到 3，`scrollRow` 被夹到合法范围、`searchSlots` 保持
- [X] T018 运行 `./gradlew compileJava test`

**Checkpoint**: 任何宽度下标题栏元素不重叠、不越界；3 列可搜索可拖动

## Phase 5: User Story 3 - 结果里照常操作 (Priority: P1)

**Goal**: 结果里的格子上所有既有操作与平时一致；查询变化清选区；空白处点击什么都不发生。

**Independent Test**: quickstart C。

- [X] T019 [US3] 核对而非改写：读 `FloatingPanelInput`（点击、`PanelSelectionInput.beginFromPanel`、`QuickTransferInput`、`PanelDragInput`、`DeleteKeyInput`、`sendClick`）确认它们取格子编号都走 `PanelSlots.indexAt`/`ClientBackpackView.layout().hit`（research D1），没有绕过映射的直接算法；发现绕过的地方改成走映射并补单元测试。结果里没有格子的空白处：`indexAt` 返回 -1 → 落到 `state.contains` 分支被消费且什么都不发生，确认光标物品不被丢弃/放入。`…test/client/panel/` 追加（`FloatingPanelStateSearchTest`）：映射后 `indexAt` 对"留空位的 ordinal"返回 -1、对命中格返回映射格子编号；在 `DELIVERY.md` 记下核对结论
- [X] T020 [US3] `SearchState.onQueryChanged` 清选区已在 T009；补 `…test/client/panel/search/SearchStateTest.java`：`PanelSelection` 有选中 → 改查询后为空，清空查询（Esc/×）后也为空；与既有 `PanelSelectionTest` 不冲突
- [X] T021 运行 `./gradlew compileJava test`

## Phase 6: User Story 4 - 结果和原来的主区域不会搞混 (Priority: P2)

**Goal**: 命中边框、搜索框同色、工具区命中高亮、扩展行底色保留、"无结果"呈现。

**Independent Test**: quickstart D。

- [X] T022 [US4] `SearchRenderer`/`FloatingPanelRenderer`：三个工具区的**可见**格子，名字命中（`SearchContext.toolMatches`）画同色边框，不过滤不改位置；主区域搜索时 `drawRowDecor` 只画底色填充（不画与上方/左方的分隔线与行首角标，research D8），留位的空格仍画类别淡图标；"既命中又被选中"的格子：命中外圈在最外、选框画成内缩 1 像素的内圈；无结果时主区域不画任何格子、命中数为 0、搜索框边框仍是搜索色
- [ ] T023 [US4] 命中色与叠放观感：进游戏对照 Shift 黄选框，在灰、蓝、绿、米黄四种格底和扩展行底色上截图，确认命中色（候选深蓝 `0xFF1E3FD0`）都看得清且与黄色选框明显不同；不合适就换色或把选框退成盖过命中框，把最终色号与理由写进 DELIVERY；`CategoryStyle` 不改 ——2026-11-12 AI：命中色候选深蓝在灰色与左区浅蓝格底上看得清，其它格底（右区绿、下区米黄、扩展行）没逐一截图，**要你亲眼确认**；未勾
- [X] T024 运行 `./gradlew compileJava test`

## Phase 7: User Story 5 - 拿走再放回仍在原位 (Priority: P2)

**Goal**: 拿空的格子留在原位置；放回回到原格子；查询变化/重开才重算；新命中的不自动加入。

**Independent Test**: quickstart E。

- [X] T025 [US5] 确认重算时机只有三处（查询变化、`closePanel`/新会话后重新打开、无）：`SearchState` 不监听背包内容变化；已显示格子内容由渲染每帧读 `ClientBackpackView.getStackInSlot` 更新；留位格子（结果集里、当前为空）不画命中边框、不计入命中数。`…test/client/panel/search/SearchStateTest.java` 追加：拿走（假 `hasItem` 变 false）后 `slots` 数组不变、`liveCount` 减一；同一查询再次 `setText` 同文字不重算（文字没变不触发）或重算后空位消失（按实现选其一并记入 DELIVERY，**以"文字变化才重算"为准**）；新装入的格子不出现在 `slots`
- [X] T026 运行 `./gradlew compileJava test`

## Phase 8: User Story 6 - 基础搜索语法 (Priority: P3)

**Goal**: 并且、或、排除、整段在游戏里按契约工作。

**Independent Test**: quickstart F。解析与匹配的用例已在 T002、T003；这里只做游戏内核对。

- [ ] T027 [US6] 游戏内逐条核对 `contracts/query-syntax.md` 的表（`oak log`、`"oak log"`、`log -stripped`、`iron|gold`、`-planks`、引号未闭合、`@`/`#`/开头 `/` 当普通字符、大小写不同）：用容器自动验证脚本在背包里放 Oak Log、Stripped Oak Log、Oak Planks、Spruce Log、Iron Axe、Gold Axe 并截图/读命中数；结果记 DELIVERY（AI 自动验证备注，不替用户勾） ——2026-11-12 AI：`log -stripped`、`axe -iron`、`iron|gold`、未闭合引号、`@`、大小写已在游戏里核对；`-planks` 第二轮补验通过（12 格）；整段 `"oak log"` 因为橡木原木已被我转走没能在游戏里验证，靠单元测试，未勾

## Phase 9: Polish & 文档 & 验证

- [X] T028 文档（中文）：`README.md` 与 `README_CN.md` 的功能列表加"搜索"（放大镜、实时过滤、语法一览、`Esc` 与 `×`、3 列时没有关闭键）；`docs/MCFloatingBackpack.md` 面板说明处加搜索与标题栏变化；`docs/plan/backpack-search.md` 状态改为"042 已规格/已实现"并标出 043、044 仍待做；`docs/plan/README.md` 索引行如需更新
- [X] T029 `…test` 全量：`./gradlew test` 确认 041 及以前全部测试仍通过；记下类数与用例数（比 T001 多出的新类）；`./gradlew --stop`
- [X] T030 `./gradlew build` 确认 jar 能构建；先 `pgrep` 后跑 `design/demo/tools/check_atlas_budget.sh`（宪法 VI：本 feature 不新增贴图，应仍为 2048×1024；度量不了就写"未度量"，**不要写成通过**）；`./gradlew --stop`
- [ ] T031 AI 自动验证（容器，quickstart A～H 中能自动做的）：A（入口、实时过滤、Esc/×、焦点离开）、B（3/4/9 列截图、拖动）、C 能点击做的各项（点击、右键、Shift 选区、Ctrl 转移、Alt、Delete、双击聚拢——记下"整个背包里的同类"的观察）、D、E（`/data get` 核对放回的格子编号）、G；中文输入先试 xdotool，不行记"未验证"；每项写"AI 自动验证"备注与证据，不替用户勾 T032 ——2026-11-12 AI：A、B、C（点击/放回/Shift/Ctrl）、D、E、F 部分已做，G、右键/Alt/Delete/拖拽分配/双击聚拢未做；详见 DELIVERY，未勾
- [ ] T032 **客户端验证（quickstart A～H，由用户做，用户确认后才打勾）**：先 `pgrep`；重点观察：搜索框与 `×` 的手感、窄面板标题栏、命中边框与 Shift 选框叠放的观感、结果里各操作与平时一致、拿走再放回回到原位、**中文输入法上屏**、双击聚拢的范围、没开搜索时 011～041 行为不变
- [X] T033 `specs/042-backpack-search-core/DELIVERY.md`（中文）：汇总自动化结果（基线与完成后的类数与用例数、新增测试类）、与 plan/tasks 的偏离（含"放大镜不用贴图"）、**每个客户端验证项实际验证了什么/未验证什么**、已知限制（双击聚拢范围是整个背包；搜索时不能往没显示的空位放；新装入的不自动加入结果；标签面板/黑名单页展开时结果被盖住；Windows/Linux 未验证）、图集度量结果或"未度量"、命中色的最终取值、给 043/044 的提醒（`TextMatcher` 是拼音的接入点；`SearchQuery` 的前缀要在解析处扩展；044 的隐藏行只读格子要进映射之外的另一条渲染路径）

---

## Dependencies & Execution Order

- **Phase 1** → **Phase 2**（T002～T007 互相独立可并行；T008 汇合）→ **Phase 3**（T009 依赖 T002～T005；T010 依赖 T006；T011 依赖 T007；T012 依赖 T005、T009～T011；T013 依赖 T009～T011；T014 依赖 T009、T012；T015 汇合）→ **Phase 4**（T016 依赖 T011；T017 依赖 T010、T012）→ **Phase 5**（依赖 Phase 3）→ **Phase 6**（依赖 T013）→ **Phase 7**（依赖 T009）→ **Phase 8**（依赖 Phase 3，可与 5～7 并行）→ **Phase 9**。
- T032 只能由用户在游戏里完成；实现完成时保持未勾选。T031 是 AI 自动验证，不勾 T032。
- US1 是 MVP；US2 与 US1 一起才算"3 列可用"；US3、US4、US5 主要是核对与渲染细节，几乎不增加新的状态。

## Implementation Strategy

先纯逻辑（Phase 2），让解析、匹配、映射、布局有测试保护；再做 MVP（Phase 3：一个关键词的搜索可用）；接着 3 列（Phase 4）；其余是渲染细节与核对。最不确定的是：命中色与选框叠放的观感（T023）、中文输入法上屏（T032）、窄面板标题栏的实际排布（T031 的截图），都只能在游戏里确认。本 feature **不改服务端、不新增网络包、不新增依赖与贴图**，所以回归风险集中在标题栏重构（T011）与 `PanelLayout` 的映射（T006）两处，它们都先用不改动的既有测试钉住。
