# Implementation Plan: 搜索前缀语法（`@模组`、`#标签`、`/hidden`）

**Branch**: `044-search-prefix-syntax` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/044-search-prefix-syntax/spec.md`；背景见 `docs/plan/backpack-search.md` 第 3 节；研究结论见 [research.md](./research.md)。

## Summary

在 042 的查询解析与匹配上增加三个前缀。**纯客户端、零服务端改动**（协议号仍 17）。核心思路（读代码得出）：

1. **解析只扩展一处**：`SearchQuery.Alternative` 增加"种类"（词 / 模组 / 标签），原来的 `(negated, text)` 构造保留（= 词），所以 042 的全部测试不改；整条查询另有可选的"指令"（`/hidden`、未知）。前缀只在备选的最开头（`-` 之后）、不在引号内时生效（D1）。
2. **匹配只扩展一处**：`TextMatcher` 加两个 `default` 方法 `inMod(needle)`、`hasTag(needle)`（默认 false），`SearchMatcher` 按备选的种类分派，其余（并且/或/排除的合并）不变。042 的函数式用法（`TextMatcher.ofTexts`）一行不改（D2）。
3. **物品属性来自客户端已有的数据**：命名空间（注册名）、模组显示名（`ModList`）、物品标签（`ItemStack.getTags()`，含 `minecraft:`、`c:` 与别的模组的）、背包标签（`ClientBackpackView.tags().tagsOf(物品 id)`，预设标签用语言键取显示名）。匹配规则写成纯函数（`ModMatch`、`TagMatch`），可单测（D3、D4）。
4. **每帧安全**：工具区命中框每帧问同一个入口，所以"物品种类 → 命名空间、模组名、物品标签 id 表"按物品缓存，在 `TagsUpdatedEvent` 时清空；背包标签的显示名表按 `ItemTags` 的对象身份缓存，语言/资源重载时清空（D5）。
5. **`/hidden` 复用空结果的机制**：指令查询的结果是**空的格子列表**（主区域没有格子、没有可点的东西），在主区域的矩形里画摘要文字（037 的数据与说法）；类别行的过滤**复用同一套匹配**（把每个类别行当成一个只有"名字 + id"的 `TextMatcher`，`@`、`#` 默认 false，正好满足规格 FR-012）（D6、D7）。
6. **摘要放不下就截断加悬停**：3 列最窄面板里文字用字体换行，行数超出就先去掉说明、再加省略号；悬停主区域弹出完整提示（沿用 037 的 `drawHiddenHint`）（D8）。

范围：只做三个前缀；隐藏物品本身的同步与显示、其它指令、其它前缀、自动补全、Ctrl+F 都不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`ItemStack.getTags()`、`ModList`、`TagsUpdatedEvent`、`GuiGraphics`）；**无新增依赖，不引用第三方模组类型**

**Storage**: 无（搜索状态与缓存只在客户端内存里）

**Testing**: JUnit 5 纯逻辑测试：`SearchQueryPrefixTest`、`SearchMatcherPrefixTest`、`ModMatchTest`、`TagMatchTest`、`HiddenSummaryRowsTest`、`HiddenSummaryFitTest`、`SearchCommandStateTest`、`SearchPrefixResourcesTest`、`SearchPrefixCostTest`；042 的全部测试不改；游戏里按 quickstart 验证（容器自动验证）

**Target Platform**: Minecraft 1.21.1 桌面客户端；服务端不涉及

**Project Type**: 单一 NeoForge 模组（只改客户端搜索）

**Performance Goals**: 400 格背包每次按键重算（`@`、`#`）小于 1 毫秒量级；没有前缀的查询（042 的写法）成本不变；没有搜索时每帧成本不变

**Constraints**:
- 服务端、网络包、协议号（17）、配置、依赖、数据格式、贴图全部不动；`neoforge.mods.toml` 不变
- 042 的全部测试不改而通过；042 的写法结果完全一致
- 新增 2 个语言键（"没有隐藏的物品"、"未知指令"），其余复用 037 的键

**Scale/Scope**: 新增约 6 个类（`ModMatch`、`TagMatch`、`HiddenSummaryRows`、`HiddenSummaryFit`、客户端 `ItemFacts`、`SearchFactsEvents`）；改动 `SearchQuery`、`TextMatcher`、`SearchMatcher`、`SearchResults`、`SearchState`、`SearchContext`、`SearchRenderer`/`FloatingPanelRenderer`、语言文件、README ×2

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 摘要文字画在同一条面板渲染路径的主区域里，不新增 Screen，输入走原有的搜索框事件 | PASS |
| II. Server Owns Items | 不触碰物品的归属与修改；没有新网络包、服务端代码；`/hidden` 只读 037 已有的摘要，没有可点击的格子，不发请求 | PASS |
| III. One Feature At A Time | 只做三个前缀；同步隐藏物品、其它指令与前缀、自动补全明确不做；不改 011～043 的规则 | PASS |
| IV. Vanilla Path Before Compat | 不引用任何第三方类型；`@`、`#` 只读注册表、标签与模组列表这些原版/NeoForge 的公开数据 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证；真实输入法上屏、Windows/Linux 如实登记"未验证" | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增贴图；缓存大小有界（按物品种类，不超过物品注册表大小；背包标签表按 `ItemTags` 身份，随背包标签变化而换）；DELIVERY 记录实测的重算耗时，没测写"未度量" | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖；读取别的模组的物品标签与显示名是读 NeoForge 的公开数据，不引用其类型，也不是"对别的模组的主动适配"，不受 `modLevels` 控制 | PASS |

**设计后复查**：D2 把新判断放在 `TextMatcher` 的 `default` 方法里，042 的函数式用法与测试不受影响；D6 让 `/hidden` 不需要任何新数据通道；D5 的缓存只在客户端，失效点是 `TagsUpdatedEvent` 与背包标签对象变化。全部 PASS。

## Project Structure

### Documentation (this feature)

```text
specs/044-search-prefix-syntax/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/query-syntax.md      # 044 版（含 042 的全部写法 + 三个前缀）
├── checklists/requirements.md
└── tasks.md                       # 由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/client/panel/search/
├── SearchQuery.java            # 改：Alternative 加 Kind；Command；前缀与开头的 /
├── TextMatcher.java            # 改：default inMod/hasTag
├── SearchMatcher.java          # 改：按 Kind 分派
├── SearchResults.java          # 改：有指令的查询没有格子（int[0]）
├── SearchState.java            # 改：Source 加 default hiddenLines()；指令状态；liveCount
├── ModMatch.java               # ★ 新：命名空间/显示名子串
├── TagMatch.java               # ★ 新：物品标签（路径子串/完整 id 前缀）与背包标签（id/显示名）
├── HiddenSummaryRows.java      # ★ 新：隐藏摘要行 + 类别过滤（复用 SearchMatcher）
├── HiddenSummaryFit.java       # ★ 新：行数放不下时的取舍（纯算术）
├── ItemFacts.java              # 新（客户端）：按物品缓存 命名空间/模组名/物品标签；背包标签表
├── SearchFactsEvents.java      # 新（客户端）：TagsUpdatedEvent 清缓存
├── SearchContext.java          # 改：matcherFor 带 inMod/hasTag；hiddenLines；toolMatches 对指令返回 false
└── SearchRenderer.java         # 改：主区域摘要文字、未知指令悬停提示
src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java   # 改：指令状态下画摘要（调用 SearchRenderer）
src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json            # 改：2 个新键
README.md / README_CN.md                                                          # 改：搜索一节补三个前缀

src/test/java/pnnh/mcfloatingbackpack/client/panel/search/
├── SearchQueryPrefixTest.java  SearchMatcherPrefixTest.java  ModMatchTest.java  TagMatchTest.java
├── HiddenSummaryRowsTest.java  HiddenSummaryFitTest.java  SearchCommandStateTest.java
└── SearchPrefixCostTest.java
src/test/java/pnnh/mcfloatingbackpack/resources/SearchPrefixResourcesTest.java
```

**Structure Decision**: 与 042/043 相同的结构：纯逻辑进纯类并单测，客户端状态与缓存各一个小类，对既有文件只加分支。服务端、网络、数据格式一行不动。

## 实现要点（给 tasks 阶段）

1. **`SearchQuery`**：`Alternative(Kind kind, boolean negated, String text)`，`enum Kind { WORD, MOD, TAG }`；保留 `Alternative(boolean negated, String text)`（WORD）。解析状态加 `prefixAllowed`（备选开头、`-` 之后、没有任何文字或引号时为真）和待定的 `kind`：不在引号内遇到 `@`/`#` 且 `prefixAllowed` → 设种类、`prefixAllowed=false`；`@` 之后的引号照旧按整段（FR-004）；文字为空的备选被丢弃（单独 `@`/`#`/`-@`）。**指令**：`parse` 先去掉前导空白，若以 `/` 开头：到第一个空白为止转小写为指令词，`/hidden` → `Command.HIDDEN`，其它 → `Command.unknown(原文)`；剩余文字按原语法解析成 `terms`。`isEmpty()` = 没有 terms **且**没有指令。`MAX_CODEPOINTS` 不变。
2. **`TextMatcher`**：`default boolean inMod(String needleLowerCase) { return false; }`、`default boolean hasTag(String needleLowerCase) { return false; }`；`SearchMatcher.holds` 按 `alternative.kind()` 选 `contains/inMod/hasTag`，其余逻辑不变。
3. **纯匹配规则**：`ModMatch.matches(needle, namespace, displayName)`（任一小写子串含 needle）；`TagMatch.itemTag(needle, List<String> tagIds)`（needle 含冒号 → 任一 id 以它开头；否则任一 id 的冒号后路径含它）；`TagMatch.backpackTag(needle, List<Entry(id, label)>)`（含冒号 → false；否则 id 或 label 的小写含它）。
4. **`ItemFacts`（客户端）**：`Facts(namespace, modNameLower, List<String> tagIds)` 按 `Item` 缓存；模组显示名用 `ModList.get().getModContainerById(namespace)`，没有则用空串；标签取 `stack.getTags()` 转 `namespace:path` 小写；`backpackTags(ResourceLocation item)` 按 `ClientBackpackView.tags()` 对象身份缓存 `Entry(id, label小写)`，label 用 `TagPanelContext.label`；`clear()`。
5. **`SearchContext.matcherFor`**：在 043 已有的"名字 + 注册名 + 提供者词"文字列表之外，返回覆盖了 `inMod`、`hasTag` 的 `TextMatcher`（惰性取 `ItemFacts`）；`toolMatches` 在 `query.command() != NONE` 时返回 false。
6. **`SearchResults.compute`**：`query.command() != NONE` → 返回 `new int[0]`（主区域没有格子，`isFiltering` 为真）；其它不变。
7. **`SearchState`**：`Source` 加 `default List<HiddenSummaryRows.Line> hiddenLines() { return List.of(); }`；`liveCount`：`/hidden` 时 = `HiddenSummaryRows.visible(lines, rest).cells 总和`；未知指令与普通结果同理；暴露 `command()`；退出路径沿用 `textChanged`。
8. **`HiddenSummaryRows`**：`Line(String categoryId, String displayNameLower, String displayName, int cells)`；`visible(lines, SearchQuery rest)`：每行建 `TextMatcher.ofTexts([displayNameLower, categoryId小写])` 后用 `SearchMatcher.matches(rest, …)` 过滤（`@`/`#` 备选自动为假）；`totalCells`。`rest` 是指令之后的那部分查询（`SearchQuery` 的 `terms`）。
9. **`HiddenSummaryFit`**：输入"类别行换行后的行数列表、标题行数、说明行数、可用行数"，输出显示哪些行、是否加省略号：优先类别行，再标题，再说明；放不下的整体用 `…` 收尾。
10. **渲染**：`FloatingPanelRenderer` 在画主区域格子之后，若 `SearchState.command() == HIDDEN`，调用 `SearchRenderer.drawHiddenSummary(...)`：在 `layout.rect(MAIN)` 里按字体换行画类别行（用 037 的 `hidden.line` 键与 `CategoryNames.langKey`），没有隐藏物品画 `search.hidden.none`；鼠标悬停主区域时调用 037 的 `drawHiddenHint` 弹完整提示（需把它改成包内可见或搬到共用位置）；`SearchRenderer.drawBox` 在 `command` 为 UNKNOWN 且悬停搜索框时弹"未知指令"提示（取代计数提示）。
11. **语言键**：`panel.mcfloatingbackpack.search.hidden.none`、`panel.mcfloatingbackpack.search.unknown_command`（带 `%s`，文字为"Unknown command: %s (available: /hidden)"）。
12. **事件**：`SearchFactsEvents` 用 `NeoForge.EVENT_BUS` 订阅 `TagsUpdatedEvent` → `ItemFacts.clear()`；资源重载监听（043 已有）里补一次 `ItemFacts.clear()`（语言变了，背包标签显示名也变）。
13. **容器自动验证要点**：用 `/give` 把原版与（开发环境里已有的）模组物品放进背包；`#logs`/`#c:ingots`/`#trash`（背包标签用 018 的标签面板标几个）；造一个隐藏行：背包带分类扩展并放物品再取下扩展，`/hidden`；3 列窄面板截图；`TagsUpdatedEvent` 的失效用重进世界或 `/reload` 验证。

## Complexity Tracking

无需要论证的违例。
