# Tasks: 搜索前缀语法（`@模组`、`#标签`、`/hidden`）

**Input**: `/specs/044-search-prefix-syntax/` 的 spec.md、plan.md、research.md、data-model.md、contracts/query-syntax.md、quickstart.md；宪法

**Tests**: 查询解析、匹配分派、模组与标签规则、隐藏摘要行与取舍、指令状态用 JUnit 覆盖（纯 Java，不加载 Minecraft 类）；语言键、协议号 17、`TagsUpdatedEvent` 接线、`toolMatches` 对指令返回 false 用源码文本检查覆盖。**真实的物品标签与模组显示名、工具区命中框每帧判断、`/hidden` 在 3 列窄面板里的排布与悬停提示只能在客户端会话里验证**（宪法 V）：编译与单元测试通过不等于界面成立。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` 表示 `src/main/resources/assets/mcfloatingbackpack/`，`…/search/` 表示 `…/client/panel/search/`
- **不包含提交 Git 的任务**（等用户要求再提交；**也不打 tag**）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，已有就不启动第二个**；容器自动验证见 `scripts/ai-gui/README.md`
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；AI 自动验证写"AI 自动验证"备注与证据；没做的写"未验证"
- **不改**服务端代码、任何网络包、协议号 17、配置项、依赖、数据格式、`neoforge.mods.toml`；**不新增贴图**；**不在 `src/main` 留任何调试入口**；不修改 `docs/ideas`
- 042、043 的全部测试**不改而通过**（042 的 `Alternative(boolean, String)` 构造、`TextMatcher.ofTexts`、`SearchState.Source` 的假实现都保持可用）
- 用户故事：US1 `@模组`；US2 `#标签`；US3 `/hidden` 摘要与未知指令；US4 与基础语法自由组合（042、043 不变）

---

## Phase 1: Setup

- [X] T001 基线：`./gradlew test` 记下当前类数与用例数（预期 158 个类、1164 个用例）；`git status` 确认工作区只有 044 的文档改动；在 `specs/044-search-prefix-syntax/DELIVERY.md`（新建，中文）记下基线（基线 158 个类、1164 个用例）

## Phase 2: Foundational（纯 Java，不碰游戏对象；T002～T004 互相独立可并行）

- [X] T002 [P] `…/search/SearchQuery.java`：按 data-model 与 contracts/query-syntax.md：`enum Kind {WORD, MOD, TAG}`；`Alternative(Kind kind, boolean negated, String text)`，**保留** `Alternative(boolean negated, String text)`（= WORD）；`record Command(Type type, String raw)`（`Type {NONE, HIDDEN, UNKNOWN}`，`Command.NONE`）；`command()`；`isEmpty()` = 没有 terms 且没有指令。`parse`：先 `stripLeading()`，首字符是 `/` → 切出第一个词（到第一个空白）：转小写等于 `/hidden` → HIDDEN，其它（含 `/`、`/h`、`/hiddenx`）→ UNKNOWN（`raw` 保留输入的写法），剩余文字按原语法解析成 `terms`；扫描状态加 `prefixAllowed`（备选开头、`-` 之后、还没有任何文字或引号）与待定 `kind`：不在引号内遇到 `@`/`#` 且 `prefixAllowed` → 设种类并 `prefixAllowed=false`；其它位置的 `@`/`#`（词中、引号内、`"@x"`）是普通字符；`@` 之后的引号照旧是整段；文字为空的备选被丢弃（单独 `@`/`#`/`-@`/`-#`）；`MAX_CODEPOINTS` 不变
- [X] T003 [P] `…/search/TextMatcher.java`：加 `default boolean inMod(String needleLowerCase) { return false; }` 与 `default boolean hasTag(String needleLowerCase) { return false; }`（`ofTexts` 不变，仍是函数式接口）；`…/search/SearchMatcher.java`：`holds` 按 `alternative.kind()` 选 `contains`/`inMod`/`hasTag`，`negated ? !hit : hit` 与并且/或的合并不变
- [X] T004 [P] `…/search/SearchResults.java`：`compute` 在 `query.command().type() != NONE` 时返回 `new int[0]`（主区域没有格子）；其它路径逐字不变
- [X] T005 [P] 单元测试（`…test/client/panel/search/`）：`SearchQueryPrefixTest`（contracts"解析用例"表逐行：三种备选、`-` 与前缀、单独的 `@`/`#`/`-@`/`-#`/`@ `、`@ mekanism`、`@"a b"`、`"@x"`/`a@b`/`a#b`/`ab@` 是词、`@a|#b`、`@a|-#b`、大小写、`/hidden` 的各种前导空白与大小写、`/hidden fish`、`/hidden @x`、`/`、`/h`、`/foo`、`/hiddenx`、`/hidden2`、`a /hidden`、`"/hidden"`、`a/b`、`-/hidden`、042 的全部用例逐条仍成立、100 码点上限）；`SearchMatcherPrefixTest`（假 `TextMatcher` 覆盖 `inMod`/`hasTag`：并且、或、排除与前缀组合；没覆盖时 `@`/`#` 为假、`-@`/`-#` 为真；042 的 `ofTexts` 用法不变）；在 `SearchResultsTest` 旁新增指令用例（指令查询返回空数组，非指令路径不变，空查询仍返回 null）（实际：`SearchQueryPrefixTest`、`SearchMatcherPrefixTest` 新增；`SearchResultsTest` 追加两条指令用例；**042 里两条本来就是给 044 留的占位用例必须更新**：`SearchQueryTest` 的 `@iron #x /hidden`（现在 `@`、`#` 是前缀，预期改为 `iron,x,/hidden`）与 `SearchMatcherTest.theSymbolsOf044AreOrdinaryCharactersHere`（改名并改预期）；其余 042、043 测试不改）
- [X] T006 运行 `./gradlew compileJava test`；042、043 的测试不改而通过；故意把一处分派写错（如把 `TAG` 也分派到 `inMod`）确认 T005 会失败，再还原（变异检查：把 TAG 分派到 `inMod`，4 个用例失败，还原后通过；160 个类、1196 个用例）

**Checkpoint**: 解析与匹配分派都就绪，可在不启动游戏的情况下测试

## Phase 3: User Story 1 - 按模组找物品：`@模组` (Priority: P1) 🎯 MVP

**Goal**: `@minecraft`、`@<模组 id 片段>`、`@<显示名片段>`、`@"含 空格"`，可与关键词组合；工具区命中框同样按 `@` 判断。

**Independent Test**: quickstart A.3（模组规则）与 B1、B2、B6。

- [X] T007 [P] [US1] `…/search/ModMatch.java`：`matches(needleLowerCase, namespace, modNameLower)`：命名空间或模组显示名含 needle（子串）
- [X] T008 [US1] `…/search/ItemFacts.java`（客户端，`@OnlyIn(Dist.CLIENT)`）：按 `Item` 缓存 `Facts(namespace, modNameLower, tagIds)`（本任务先填前两项，`tagIds` 留空列表，T014 再接）；命名空间取 `BuiltInRegistries.ITEM.getKey(item).getNamespace()`；模组显示名取 `ModList.get().getModContainerById(namespace)` 的 `getModInfo().getDisplayName()`，没有则空串（实现时核对 `minecraft`、`neoforge` 是否返回容器与显示名，记入 DELIVERY）；`clear()`（`ItemFacts` 一次写好 T008 与 T014 的内容；对 `minecraft`/`neoforge` 的 `ModList` 实测留给 T032）
- [X] T009 [US1] `…/search/SearchContext.java`：`matcherFor` 返回覆盖了 `inMod` 的 `TextMatcher`（惰性取 `ItemFacts`；043 的"没有生效提供者"快路径保留，只省提供者的调用）；文字列表（名字、注册名、提供者的词）与 `contains` 的行为逐字不变
- [X] T010 [P] [US1] 单元测试 `…test/client/panel/search/ModMatchTest.java`：contracts"匹配用例"表里模组的各行（`@minecraft`/`@mine`/`@CRAFT`、命名空间 `sophisticatedbackpacks` 与显示名 `Sophisticated Backpacks` 的 `@sophisticated`/`@"sophisticated backpacks"`/`@pack`、`@minecraft` 不命中它、显示名为空时只按命名空间）；以及用 T003 的分派加 `ModMatch` 搭起来的 `@mekanism ingot`（并且）、`-@mod`（排除）、`@a|@b`（或）
- [X] T011 [US1] 运行 `./gradlew compileJava test`；`DependencyRulesTest` 照常通过（无新依赖、无第三方类型）

**Checkpoint**: `@模组` 在主区域与工具区都能用

## Phase 4: User Story 2 - 按标签找物品：`#标签` (Priority: P1)

**Goal**: `#logs`、`#ingots`、`#c:ingots`、`#minecraft:logs`、`#trash`/`#垃圾`、自定义标签名；三类标签取并集。

**Independent Test**: quickstart A.3（标签规则）与 B3、B4、B6、B12。

- [X] T012 [P] [US2] `…/search/TagMatch.java`：`itemTag(needle, List<String> tagIdsLower)`（needle 含 `:` → 任一 id `startsWith(needle)`；否则任一 id 的 `:` 之后的路径含 needle）、`backpackTag(needle, List<Entry(id, labelLower)>)`（含 `:` → false；否则 id 或 label 含 needle）；`Entry` 为记录
- [X] T013 [P] [US2] 单元测试 `…test/client/panel/search/TagMatchTest.java`：contracts"匹配用例"表里标签的各行（`minecraft:logs`/`minecraft:oak_logs` 的 `#logs`/`#oak_logs`/`#minecraft:logs`/`#minecraft:`、`#c:logs` 不命中；`c:ingots/iron` 的 `#ingots`/`#iron`/`#c:ingots`/`#c:ingots/iron`/`#c:`/`#ingots/iron`、`#minecraft:ingots` 不命中；背包标签 `trash`/显示名"垃圾"的 `#trash`/`#垃圾`/`#tra`、`#c:trash` 不命中；自定义标签 `my tools` 的 `#my`；并集；没有标签时 `#x` 不成立、`-#x` 为真）
- [X] T014 [US2] `…/search/ItemFacts.java`：填 `tagIds`：`stack.getTags()` 转 `namespace:path` 小写（实现时核对 1.21.1 里 `ItemStack.getTags()` 的签名与客户端数据完整性）；新增 `backpackTags(ResourceLocation item)`：按 `ClientBackpackView.get().tags()` 的**对象身份**缓存 `Entry(id, labelLower)`（`ItemTags.tagsOf(item)` 加 `TagPanelContext.label(tag)` 小写），身份变了就整表重建；`clear()` 清两张表（与 T008 一起写在 `ItemFacts` 里；失效用 `AtomicInteger` 计数而不是直接清表，因为 `TagsUpdatedEvent` 也会在集成服务端线程触发）
- [X] T015 [US2] `…/search/SearchContext.java`：`matcherFor` 再覆盖 `hasTag`：`TagMatch.itemTag(...)` **或** `TagMatch.backpackTag(...)`（并集）；工具区的 `toolMatches` 经同一个 `matcherFor`，无需另改
- [X] T016 [US2] `…/search/SearchFactsEvents.java`（客户端）：订阅 `TagsUpdatedEvent` → `ItemFacts.clear()`（在 `FloatingPanelEvents.init()` 或 `ClientSetup` 里注册，与现有事件接线同一风格）；`ClientSetup` 里 043 已有的资源重载监听里补一次 `ItemFacts.clear()`（语言变了，预设标签显示名也变）（`SearchFactsEvents` 用 `@EventBusSubscriber(value = Dist.CLIENT)` 自动注册，不需要手动接线）
- [X] T017 [US2] 运行 `./gradlew compileJava test`；`DependencyRulesTest` 照常通过

**Checkpoint**: `#标签` 三类取并集，缓存按事件失效

## Phase 5: User Story 3 - `/hidden` 摘要与未知指令 (Priority: P2)

**Goal**: `/hidden` 主区域改为列出"类别 + 格数"（037 的数据与说法）；`/hidden 词` 过滤类别行；未知指令无结果并悬停提示；3 列窄面板不溢出。

**Independent Test**: quickstart A.4、A.5 与 B7～B11。

- [X] T018 [P] [US3] `…/search/HiddenSummaryRows.java`：`record Line(String categoryId, String displayName, int cells)`；`visible(List<Line> lines, SearchQuery rest)`：每行建 `TextMatcher.ofTexts([displayName 小写, categoryId 小写])`，用 `SearchMatcher.matches(rest, matcher)` 过滤（`@`/`#` 备选因 T003 的默认实现自然为假）；`totalCells(List<Line>)`
- [X] T019 [P] [US3] `…/search/HiddenSummaryFit.java`：`fit(categoryLinesNeeded, titleLines, hintLines, availableLines)` → `Plan(categoryLines, showTitle, showHint, ellipsis)`：优先类别行，再标题，再说明；类别行放不下就只显示前几行并 `ellipsis`；`availableLines <= 0` 时全不显示
- [X] T020 [P] [US3] 单元测试 `…test/client/panel/search/HiddenSummaryRowsTest.java`（契约"类别行"一行：`fish` 命中、`-fish` 不命中、`@x`/`#x` 不命中、`-@x` 命中；并且、`|`、引号；空列表；总格数；没有隐藏物品）与 `HiddenSummaryFitTest.java`（各种可用行数下的取舍与省略号、可用行数为 0 或负数）
- [X] T021 [US3] `…/search/SearchState.java`：`Source` 加 `default List<HiddenSummaryRows.Line> hiddenLines() { return List.of(); }`（042 的假 `Source` 不用改）；暴露 `command()`；`liveCount(source)`：HIDDEN → `HiddenSummaryRows.totalCells(visible(source.hiddenLines(), query))`、UNKNOWN → 0、其它沿用；`textChanged` 的清选区、回滚动、`setSearchSlots` 沿用（指令查询的 `slots` 为空数组，`isFiltering()` 为真）；`reset()` 照旧
- [X] T022 [US3] `…/search/SearchContext.java`：`SOURCE.hiddenLines()` 从 `ClientBackpackView.get().rowView().hidden()` 经 037 的 `HiddenHint.lines(...)` 取，显示名用 `Component.translatableWithFallback(CategoryNames.langKey(类别), 类别).getString()`（未知类别显示 id，与 037 一致）；`toolMatches` 在 `query.command().type() != NONE` 时返回 false
- [X] T023 [P] [US3] `…res/lang/en_us.json` 与 `zh_cn.json` 各加 2 个键：`panel.mcfloatingbackpack.search.hidden.none`（"No hidden items" / "没有隐藏的物品"）、`panel.mcfloatingbackpack.search.unknown_command`（"Unknown command: %s (available: /hidden)" / "未知指令：%s（可用：/hidden）"）；两份文件键保持一致
- [X] T024 [US3] `…/search/SearchRenderer.java` 与 `…/client/panel/FloatingPanelRenderer.java`：`command()` 为 HIDDEN 时在 `layout.rect(MAIN)` 里按字体换行画摘要：标题（037 的 `hidden.title`）、类别行（`hidden.line`，`CategoryNames.langKey`）、说明（`hidden.hint`，灰色），行数由 `HiddenSummaryFit` 决定，放不下以 `…` 收尾；没有隐藏物品画 `search.hidden.none`；鼠标悬停主区域调用 037 的完整提示（把 `FloatingPanelRenderer.drawHiddenHint` 改成包内可见或搬到共用位置，行为不变）；`drawBox` 在 UNKNOWN 且悬停搜索框时弹 `search.unknown_command`（取代计数提示）；工具区不加命中框、主区域没有格子（T004 保证）
- [X] T025 [P] [US3] 单元测试 `…test/client/panel/search/SearchCommandStateTest.java`：用假 `Source`（覆盖与不覆盖 `hiddenLines` 两种）：`/hidden` 后 `slots()` 是空数组、`isFiltering()` 为真、`liveCount` = 过滤后格数总和；`/hidden fish` 的计数；`/foo`、`/` 计数 0；进入与退出指令清选区并回滚动；`close()` 后恢复；042 的 `SearchStateTest` 不改而通过
- [X] T026 [P] [US3] 资源与不变量测试 `…test/resources/SearchPrefixResourcesTest.java`：两个新语言键中英文齐全且两份文件键一致；协议号仍 `"17"`；`neoforge.mods.toml` 无变化（不含 search 相关）；`SearchContext.toolMatches` 源码里对指令返回 false；`TagsUpdatedEvent` 清缓存与资源重载清缓存的接线存在（源码文本检查）；`ModMatch`/`TagMatch`/`HiddenSummaryRows`/`HiddenSummaryFit`/`SearchQuery` 不含 `net.minecraft` 导入
- [X] T027 [US3] 运行 `./gradlew compileJava test`

**Checkpoint**: `/hidden` 与未知指令可用；3 列窄面板的排布待容器验证（T032）

## Phase 6: User Story 4 - 与基础语法自由组合（042、043 不变）(Priority: P2)

**Goal**: 前缀项与并且/或/排除/整段自由组合；042 的写法与 043 提供者的词不受影响。

**Independent Test**: quickstart A.1～A.2 与 B5、B13。

- [X] T028 [P] [US4] 单元测试（`SearchMatcherPrefixTest` 追加）：`@minecraft #logs`（并且）、`#logs|#planks`（或）、`-#planks`、`@mekanism -ingot`、`"@minecraft"`（词）、`@minecraft #logs oak`（三项）、`@a|-#b`；043 的提供者词只在 `contains` 里生效：用带 `inMod`/`hasTag` 与额外文字的假 `TextMatcher`，断言 `@`、`#` 不看提供者的词
- [X] T029 [P] [US4] `README.md` 与 `README_CN.md` 搜索一节补充三个前缀的写法与例子（`@minecraft`、`@mekanism ingot`、`#logs`、`#c:ingots`、`#trash`、`/hidden`、`-#planks`），说明 `/hidden` 只显示摘要（隐藏物品本身不在客户端）、`#` 的三类标签取并集、前缀只在备选开头与引号外生效
- [X] T030 [US4] 运行 `./gradlew compileJava test`；042、043 的全部测试不改而通过（对照基线 T001）

**Checkpoint**: 全部用户故事的纯逻辑与文档完成

## Phase 7: 游戏内验证、度量与收尾

- [X] T031 `SearchPrefixCostTest`（`…test/client/panel/search/`）：400 格、`@` 与 `#` 查询的纯逻辑部分，冷与热的重算耗时（宽松上限，如热 < 50 毫秒、冷 < 200 毫秒，实测值打印出来）；游戏里首次遇到物品种类时 `getTags()` 与 `ModList` 填充成本：容器里粗测或写"未度量"（单元测试部分：400 格、150 种物品、每种 12 个标签：冷约 0.41 ms、热约 0.11 ms 每次查询；游戏里首次填充 `getTags()`/`ModList` 的成本留给 T032）
- [X] T032 游戏内验证（AI 自动验证，容器，quickstart B）：造数据（原版物品与开发环境里已有模组的物品；018 标签面板标 `trash` 与自定义标签；造隐藏行：装分类扩展、放物品、取下扩展）；做 B1～B13：`@`、`#`（物品标签、`c:`、背包标签、中文界面 `#垃圾`）、组合、工具区命中框、`/hidden`（有/无隐藏物品、加过滤词、`@x`）、未知指令悬停提示、3 列窄面板不溢出与悬停完整提示、`/hidden` 时点击/`Ctrl` 点击/`Delete` 无格子可操作、`/reload` 后 `#logs` 仍正确、042/043 写法不变；**不在 `src/main` 留任何调试入口**（043 用临时类的做法，验证后删除并用 `git status` 确认）；每项写实际验证了什么，做不了的写"未验证"——**AI 自动验证已做**（B1～B13 的证据与未验证项见 DELIVERY.md；模组显示名路径、`ModList` 对原版/NeoForge、中文界面 `#垃圾`、3 列最窄面板、帧率未验证）
- [X] T033 `specs/044-search-prefix-syntax/DELIVERY.md`（中文）：基线与完成后的类数与用例数、新增测试类、与 plan/tasks 的偏离、**每个游戏内验证项实际验证了什么/未验证什么**、已知限制（隐藏物品本身不在客户端，`/hidden` 只能按类别过滤；`#` 的物品标签含所有命名空间；模组显示名不随语言变化；真机输入法、Windows/Linux 未验证）、`ModList` 对 `minecraft`/`neoforge` 的实测结果、度量结果或"未度量"、给后续的提醒（若要同步隐藏物品：另开 feature，走协议升级）
- [X] T034 文档同步：`docs/plan/backpack-search.md` 的状态（044 已实现与自动验证、等用户确认）与第 3 节 `/hidden` 说明一致；`docs/plan/README.md` 对应行；042 的 DELIVERY"给 043/044 的提醒"与最终实现一致；`contracts/query-syntax.md` 与实际行为一致（有出入以实际为准并改契约）
- [X] T035 最终检查：`./gradlew compileJava test` 全部通过；`git status` 确认无临时修改残留、没有新增依赖、`neoforge.mods.toml` 与协议号 17 未动、没有新增配置项；对照宪法 I～VII 复查并在 DELIVERY 记结论；**不提交、不打 tag**，等用户要求（167 个类、1248 个用例通过；无临时修改残留）
- [X] T036 用户确认：用户在真机上核对 `@`、`#`、`/hidden`、窄面板排布与悬停提示（尤其真实的模组标签与显示名）；**只有用户明确确认才勾**——**用户已确认（2026-11-12）：没有问题**

---

## Dependencies & Execution Order

- **Phase 2**：T002～T004 互相独立可并行；T005 依赖它们；T006 收尾。
- **US1（P1，MVP）** 依赖 Phase 2：T007 → T008 → T009；T010 可与 T008 并行（只依赖 T003、T007）；T011 收尾。
- **US2** 依赖 US1 的 `ItemFacts` 与 `matcherFor`：T012 并行于 T013；T014 → T015；T016 依赖 T014；T017 收尾。
- **US3** 依赖 Phase 2（指令解析与空结果）：T018、T019、T023 可并行；T020 依赖 T018、T019；T021 依赖 T018；T022 依赖 T021 与 US1/US2 对 `SearchContext` 的改动（同一文件，顺序做）；T024 依赖 T019、T021～T023；T025、T026 并行；T027 收尾。
- **US4** 依赖 US1～US3：T028、T029 并行；T030 收尾。
- **Phase 7** 依赖全部用户故事；T032 先于 T033；T036 在最后，由用户完成。

### Parallel Opportunities

- T002/T003/T004；T007 与 T010；T012 与 T013；T018/T019/T023；T025/T026；T028/T029。

## Implementation Strategy

- **MVP = Phase 2 + US1 + US2**：`@模组` 与 `#标签`（纯客户端、数据都在手）；之后是 `/hidden`（US3，要碰渲染）、组合与文档（US4）。
- 先纯逻辑后接游戏：T006 通过后才动 `SearchContext` 与渲染，减少对游戏的触碰面。
- `SearchContext.java` 被 T009、T015、T022 三次修改，**必须按顺序做**，每次后跑测试。
