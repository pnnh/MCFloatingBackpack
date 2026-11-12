# 044 搜索前缀语法（`@模组`、`#标签`、`/hidden`）：交付说明

**状态**：完成（2026-11-12）。代码、自动化测试、文档完成；游戏内验证做了 AI 自动验证（容器，见下）；**用户已确认 T036（没有问题）**。

## 基线与结果

- 基线（T001，实现前）：`./gradlew test` 158 个类、1164 个用例，全部通过；工作区只有 044 的文档改动。
- 完成后：**167 个类、1248 个用例，全部通过**（新增 9 个类、84 个用例）。新增测试类：`SearchQueryPrefixTest`、`SearchMatcherPrefixTest`、`ModMatchTest`、`TagMatchTest`、`HiddenSummaryRowsTest`、`HiddenSummaryFitTest`、`SearchCommandStateTest`、`SearchPrefixCostTest`（均在 `client/panel/search/`）、`SearchPrefixResourcesTest`（`resources/`）；`SearchResultsTest` 追加 2 条用例。
- 变异检查（T006）：把 `SearchMatcher` 的 TAG 分派写成 `inMod`，`SearchMatcherPrefixTest` 4 个用例失败，还原后通过。
- 没有动服务端、网络包、协议号（仍 17，`SearchPrefixResourcesTest` 断言）、`neoforge.mods.toml`、依赖、数据格式；没有新增配置项（断言 `CompatConfig` 里仍只有一个 `defineListAllowEmpty`）；没有新增贴图。

## 新增与改动

- 解析：`SearchQuery.Alternative` 加 `Kind`（词 / 模组 / 标签），保留 `Alternative(boolean, String)`；`Command`（`NONE` / `HIDDEN` / `UNKNOWN`，`raw` 保留输入的写法）。前缀只在备选开头（`-` 之后）、不在引号内生效；`@` 之后的引号是整段；整条查询开头（允许前导空白）的 `/` 才是指令。
- 匹配：`TextMatcher` 加默认返回 false 的 `inMod`、`hasTag`；`SearchMatcher` 按种类分派。
- 纯规则：`ModMatch`、`TagMatch`（物品标签路径子串 / 完整 id 前缀；背包标签 id 或显示名；含冒号对背包标签不命中）、`HiddenSummaryRows`（类别行的过滤，复用同一套匹配）、`HiddenSummaryFit`（放不下时的取舍）。
- 客户端：`ItemFacts`（按物品种类缓存命名空间、模组显示名、物品标签；背包标签表按 `ItemTags` 对象身份缓存）、`SearchFactsEvents`（`TagsUpdatedEvent` 失效）、`ClientSetup` 的资源重载监听里补一次失效；`SearchContext.matcherFor` 覆盖 `inMod`/`hasTag`；`toolMatches` 对指令返回 false；`SearchResults` 对指令返回空数组；`SearchState` 加 `command()`、`hiddenRows()`、`liveCount` 的指令分支与 `Source.hiddenLines()`（`default`）；`SearchRenderer`/`FloatingPanelRenderer` 画摘要与未知指令提示。
- 语言键：新增 3 个（`search.hidden.none`、`search.hidden.nomatch`、`search.unknown_command`），其余复用 037 的键。
- 文档：`README.md`/`README_CN.md` 搜索一节；`contracts/query-syntax.md`（044 版）。

## 与 plan/tasks 的偏离（随做随记）

- **042 里两条占位用例必须更新**：`SearchQueryTest` 的 `@iron #x /hidden`（现在 `@`、`#` 是前缀，预期 `iron,x,/hidden`）与 `SearchMatcherTest.theSymbolsOf044AreOrdinaryCharactersHere`（改名为 `aPlainMatcherKnowsNothingAboutModsAndTagsSoThePrefixesOf044Fail`，预期随之改）；它们本来就是 042 为 044 留的"这些符号现在是普通字符"的占位。**其余 042、043 测试一行没改。**
- T008 与 T014 合并写在 `ItemFacts` 里；失效用 `AtomicInteger` 计数而不是直接清表，因为 `TagsUpdatedEvent` 也会在集成服务端线程触发，而缓存只允许渲染线程碰。
- `SearchFactsEvents` 用 `@EventBusSubscriber(value = Dist.CLIENT)` 自动注册，没有手动接线。
- **计划外的一处**：`/hidden 词` 过滤后没有类别时，原规格只有"没有隐藏的物品"一句，会误导（其实有隐藏物品，只是被词过滤掉了）；新增键 `search.hidden.nomatch`（"No hidden category matches" / "没有符合的隐藏类别"），规格契约已补一句。
- 摘要画在 `FloatingPanelRenderer` 里 `SearchRenderer.render` 之前，并且在标签面板或黑名单页展开时不画（它们自己盖住主区域）。

## 游戏内验证（T032，AI 自动验证，2026-11-12，容器 `mcfb-c1`，创造模式世界，英文界面）

背包是下界合金档，主区域有原版物品（原木、木板、锭、镐、剑、斧、面包、钻石、绿宝石等，共 14 种），工具区有自动拾取、垃圾桶、标签管理器；用 018 的标签面板给钻石镐加了自定义标签 `mytools`（空格被标签规则去掉）与预设标签 `ore`；造隐藏行：放食物扩展、在食物行放面包、取下扩展。**没有在代码里留任何调试入口，没有用临时类**（本 feature 不需要提供者）。

| quickstart | 结果 |
|---|---|
| B1 `@模组` | **通过**：`@minecraft` 命中全部 14 个原版物品；`@nosuchmod` 命中 0；`@floating`（命名空间 `mcfloatingbackpack` 的子串）命中工具区里本模组的自动拾取与垃圾桶（框出）、主区域 0。**模组显示名路径未在游戏里验证**：本模组的显示名与 id 小写相同，开发环境里没有显示名与 id 不同的模组的物品（需要带 `-PcompatRuntime` 的 Sophisticated Backpacks 之类），只有单元测试证据（`ModMatchTest`）；`ModList` 对 `minecraft`/`neoforge` 返回的显示名同样**未实测**（`@minecraft` 靠命名空间就命中了） |
| B2 `@` 加关键词 | **通过**：`@minecraft log` → 3 个原木类物品（木板不在） |
| B3 `#` 物品标签 | **通过**：`#logs` → 3；`#minecraft:logs` → 3；`#c:ingots` → 1（下界合金锭）；`#nosuchtag` → 0；`#tool`/`#wood`/`#food` 命中符合的物品（具体是哪类标签命中的没有逐一区分）；`#trash` 在主区域 0，工具区的垃圾桶被框出（推测是它带模组自己的 `never_trash` 之类路径含 `trash` 的物品标签，没有逐一查证） |
| B4 `#` 背包标签 | **通过**：`#ore` → 钻石、绿宝石、钻石镐、下界合金锭——**钻石镐只可能来自预设背包标签 `ore`**（它不在任何物品标签里）；`#mytools`、`#my` → 钻石镐（自定义背包标签）；`#c:ore` → 0，**含冒号时背包标签不参与**（钻石镐不出现）。**中文界面的 `#垃圾` 未验证**（容器里没有切中文界面，只有 `TagMatchTest` 覆盖显示名匹配） |
| B5 组合 | **通过**：`#logs\|#planks` → 4；`@minecraft -#logs` → 11（14 − 3）；`"@minecraft"` → 0（引号内是普通词）；`@minecraft #logs` 的"并且"由 `@minecraft log`、`@minecraft -#logs` 间接证明 |
| B6 工具区 | **通过**：`@floating`、`#trash`、`pick` 都会框出符合的工具区格子；`/hidden`、`/foo` 时无框。**每帧判断的帧率没测**（容器是软件渲染，不可信） |
| B7 `/hidden` | **通过**：主区域没有物品格子，写出标题、"food: 1 slots hold items"、说明（037 的数据与说法）；计数 1；悬停主区域弹 037 的完整提示；取下扩展后黄点出现、放回后黄点消失 |
| B8 `/hidden` 过滤 | **通过**：`/hidden fo` → 1 行；`/hidden fish`、`/hidden @x`、`/hidden @food` → "No hidden category matches"、计数 0；放回扩展后 `/hidden` → "No hidden items"、计数 0 |
| B9 未知指令 | **通过**：`/foo`、`/` → 无结果、计数 0；悬停搜索框出现"Unknown command: /foo (availab…"（**提示在容器的窗口里被窗口顶边与右上角的新手提示条遮住一部分**，内容可读到一半；这是容器窗口太小的问题，042 的计数提示同理）；`bread /hidden`、`"/hidden"` 不是指令（0 命中，普通词）；重启客户端后隐藏状态与摘要仍正确 |
| B10 窄面板 | **部分通过**：把窗口缩到 190×210 与 130×210，面板收窄到约 4～5 列，摘要文字**按宽度换行、不溢出面板**；**没有量到 3 列的最窄情形**（窗口再小面板就不再收窄）；`HiddenSummaryFit` 的取舍由单元测试覆盖 |
| B11 `/hidden` 下操作 | **通过（弱）**：主区域点击、`Ctrl` 点击、`Delete` 后光标上的物品与摘要都没变，日志无本模组的警告 |
| B12 标签更新 | **通过**：`/reload` 后（日志 `Reloading!`）`#logs` 3、`#c:ingots` 1、`#mytools` 1、`@minecraft` 14，与之前一致 |
| B13 042/043 不变 | **通过**：`pick`（2 个镐 + 名字含 Pickup 的工具格）、`iron\|diamond`（5）、`"stone bricks"`（0，容器里那个格子的名字不同）与 042 的行为一致；以 `-` 开头的写法没能用 xdotool 输入（`type` 把它当选项），由 `SearchQueryPrefixTest`/042 的测试覆盖 |

没做的：真机输入法、Windows/Linux、中文界面的 `#垃圾`、模组显示名路径与 `ModList` 对原版/NeoForge 的实测、3 列最窄面板的 `/hidden`、每帧帧率。

## 度量（T031，宪法 VI）

- **纯逻辑部分（单元测试，开发机，`SearchPrefixCostTest`）**：400 格、150 种物品、每种 12 个标签：冷（先算每个物品种类的属性）**约 0.41 毫秒**，热（全部来自缓存）**约 0.11 毫秒/次查询**。
- **未度量**：游戏里第一次遇到每个物品种类时 `getTags()` 与 `ModList` 的填充成本（需要 Minecraft 类；容器是软件渲染、计时不可信）；带大组件物品（装满的潜影盒）对 `@`、`#` 没有影响（`ItemFacts` 按物品种类而不是按组件缓存，`@`、`#` 只看物品种类）。
- 缓存大小：按物品种类，不超过物品注册表的大小（原版约 1500 项，每项几十个小字符串）；背包标签表按 `ItemTags` 对象身份，随背包标签变化而换。

## 已知限制

- **隐藏物品本身不在客户端**（只同步摘要），所以 `/hidden` 只能按类别过滤，不显示物品、不能按物品名过滤；这是用户选择的摘要模式（2026-11-12）。要同步隐藏物品需另开 feature，走协议升级。
- `#` 的物品标签包含所有命名空间（原版、`c:`、别的模组的）。
- 模组显示名取自模组元数据，不随游戏语言变化。
- 前缀只在备选开头与引号外生效；词中间的 `@`、`#` 是普通字符。
- 提供者（043）的词只参与普通词，不参与 `@`、`#`。
- 已显示的搜索结果在标签或背包标签变化后要到下一次输入才更新（042 的"结果只在查询变化时重算"）。
- 真机输入法、Windows/Linux、真实的第三方模组标签与显示名：未验证。

## 给后续的提醒

- 若要同步隐藏物品并显示灰色只读格子：另开 feature，要改会话载荷与协议号（17→18），并重新核对宪法 II；`HiddenSummaryRows.Line` 可以扩展成带物品的行。
- `@`、`#`、`/` 之外的前缀/指令：在 `SearchQuery.parseTerms` 的前缀分支与 `Command` 里扩展；`TextMatcher` 的 `default` 方法是"这个对象知道不知道某种属性"的约定。

## 宪法复查（T035）

I Overlay：摘要画在同一条面板渲染路径里，不新增 Screen。II Server Owns Items：没有新网络包或服务端代码；`/hidden` 只读 037 已有的摘要，主区域没有可点击的格子，不发请求。III One Feature：只做三个前缀；隐藏物品同步等明确不做。IV Vanilla：只读注册表、标签与模组列表这些公开数据，不引用第三方类型。V Verify：已在客户端会话里验证（容器；未验证项如实列出）。VI Budget：无新贴图；缓存有界；度量见上，游戏里首次填充成本"未度量"。VII：无新依赖、无第三方类型、`neoforge.mods.toml` 不变；读别的模组的标签与显示名是读 NeoForge 的公开数据，不是对别的模组的主动适配。**结论：全部通过，宪法不需要修订。**
