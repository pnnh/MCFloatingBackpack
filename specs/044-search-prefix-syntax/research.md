# Research: 搜索前缀语法

每条：Decision / Rationale / Alternatives。"已查证"指读本仓库源码或 NeoForge 21.1.251 的 jar；"待实现时验证"的在 DELIVERY 里如实记录。

## D1 解析：备选加"种类"，指令另放

- **Decision**: `SearchQuery.Alternative(Kind kind, boolean negated, String text)`；保留 `Alternative(boolean negated, String text)`（= `WORD`）。前缀只在备选的最开头（可以在 `-` 之后）、不在引号内才生效；整条查询的开头可以有一个"指令"（`/hidden` 或未知）。
- **Rationale（已查证）**: 042 的解析是单遍扫描，状态有 `quoted`、`negated`、`started`；`@`、`#` 现在落进"普通字符"分支。加一个 `prefixAllowed`（备选开头、`-` 之后、还没有任何文字或引号）就能区分"前缀"与"文字里的 `@`"（`a@b` 的 `@` 在文字中间，是普通字符；`@"phrase"` 的引号在前缀之后，照旧是整段；`"@x"` 引号先开始，所以 `@` 是普通字符）。保留旧的两参数构造让 042 的全部测试（直接构造 `Alternative` 的那些）不改。
- **指令位置**: 规格说"整条查询最开头，允许前导空白"。解析先 `stripLeading()`，若首字符是 `/` 就切出第一个词；`/hidden` 之后的剩余文字照原语法解析（`/hidden fish`、`/hidden -fish`、`/hidden "a b"` 都可）。引号内的 `/` 不在首字符，所以是普通字符；`"/hidden"` 的首字符是引号。
- **Alternatives**: 把前缀当作普通词再在匹配时判断开头字符——无法区分引号内的 `@`，也让排除、整段的规则变复杂；为前缀另写一个解析器——两套规则会不一致。

## D2 匹配：`TextMatcher` 加 `default` 方法

- **Decision**: `TextMatcher` 增加 `default boolean inMod(String)`、`default boolean hasTag(String)`，默认 false；`SearchMatcher` 按种类分派。
- **Rationale（已查证）**: `TextMatcher` 是 042/043 的唯一匹配接口，`ofTexts` 与测试里的 lambda 都把它当函数式接口用；`default` 方法不破坏函数式（仍只有一个抽象方法）。默认 false 让"没有这类信息的 matcher"（测试里的假 matcher、`/hidden` 的类别行）自动满足"`@`/`#` 对它不成立"。
- **Alternatives**: 新接口 `ItemMatcher extends TextMatcher`，`SearchMatcher` 里 `instanceof` 判断——多一层类型与分支；把模组与标签折成"带前缀的文字"塞进 `contains`（如 `@minecraft`、`#minecraft:logs`）——子串规则会误命中，且不能表达"带冒号按完整前缀、不带冒号按路径子串"。

## D3 `@模组`

- **Decision**: `ModMatch.matches(needle, namespace, modName)`：`namespace` 或 `modName`（都小写）含 needle。命名空间取物品注册名的命名空间；模组显示名取 `ModList.get().getModContainerById(namespace)` 的 `getModInfo().getDisplayName()`，没有时用空串（只剩命名空间匹配）。
- **Rationale**: 用户方案第 3 节原话"既匹配命名空间也匹配模组显示名"。显示名里有空格（"Sophisticated Backpacks"），所以 `@sophisticated` 靠命名空间命中，`@"sophisticated backpacks"`（FR-004）靠显示名命中。
- **待实现时验证**: `getModContainerById("minecraft")` 与 `"neoforge"` 是否返回容器、显示名是什么；读不到就退回命名空间（行为不坏）。
- **Alternatives**: 只匹配命名空间——显示名与 id 不一致的模组按显示名搜不到。

## D4 `#标签`：三类标签并集

- **Decision**: `hasTag(needle)` = 物品标签命中 **或** 背包标签命中。
  - 物品标签（含 `minecraft:`、`c:`、别的模组的）：`TagMatch.itemTag(needle, tagIds)`——needle 含冒号 → 任一标签 id 以它开头（"`c:ingots` 命中 `c:ingots/iron`"）；不含冒号 → 任一 id 的冒号后路径含它（"`logs` 命中 `minecraft:logs`、`minecraft:oak_logs`"）。
  - 背包标签（018）：`TagMatch.backpackTag(needle, entries)`——needle 含冒号 → false；否则 `id` 或当前语言显示名（小写）含 needle。
- **Rationale（已查证）**: 背包标签数据在客户端：`ClientBackpackView.tags()` 是 `ItemTags`（物品注册 id → 标签字符串列表，随背包同步，`BackpackTagsPayload` 与标签总览的载荷都会 `setTags`）。标签字符串对预设标签是稳定的英文 id（`PresetTags`：`trash`、`wood`、……），对自定义标签是玩家输入的文字；显示名对预设标签用语言键 `tag.mcfloatingbackpack.preset.<id>`（`TagPanelContext.label`），自定义标签就是它本身。所以"`#垃圾` 与 `#trash` 都命中"是"id 或显示名含 needle"。物品标签来自 `ItemStack.getTags()`（`Item` 的内置注册表持有者的标签，随世界同步到客户端）。
- **物品标签包含所有命名空间**（规格的假设），因为数据已经在手，不额外区分。
- **Alternatives**: 只取原版与 `c:`——要额外过滤命名空间，且让 `#mekanism:xxx` 这类用法失效；对背包标签也支持带冒号——背包标签没有命名空间，规格明确"含冒号对背包标签不命中"。

## D5 每帧安全：按物品缓存

- **Decision**: `ItemFacts`（客户端）按 `Item` 缓存 `Facts(namespace, modNameLower, List<String> tagIdsLower)`；在 `TagsUpdatedEvent`（NeoForge 的 `net.neoforged.neoforge.event.TagsUpdatedEvent`，已在 jar 里核对类存在）与资源重载时清空。背包标签表（`Entry(id, labelLower)`）按 `ClientBackpackView.tags()` 的**对象身份**缓存（`ItemTags` 是不可变的 record，每次 `setTags` 换新对象），资源重载（语言变）时清空。
- **Rationale（已查证）**: 工具区命中框每帧对每个工具格问 `matcherFor`（042 的 `toolMatches`）；主区域每次按键问 ≤400 次。每次现算 `getTags()` 流与 `ModList` 查询对每帧太贵；按物品种类缓存后，每次判断只剩字符串包含。缓存大小受物品注册表大小限制（原版约 1500，大整合包约 10⁴，每项几十个小字符串，可接受），所以不另设上限；`TagsUpdatedEvent` 在客户端于登录与 `/reload` 时触发。
- **Alternatives**: 不缓存——每帧代价不可控；缓存整个 `matcherFor` 的结果——文字里含改过名的自定义名与 043 提供者的词，失效点太多。

## D6 `/hidden` 摘要：空结果加覆盖层

- **Decision**: `/hidden` 查询的结果格子列表是 `new int[0]`（主区域没有格子、没有可点的东西、选区清空），`SearchState.isFiltering()` 为真；渲染在主区域矩形里画摘要文字；工具区无框。
- **Rationale（已查证）**: 042 的映射已经支持"零个结果"（`zsg` 无命中时就是这样：主区域一片灰），所以主区域的点击、选区、拖拽、滚动、Delete 全部不需要新代码就"没有格子可操作"。`FR-014` 因此自然成立。数据来自 `ClientBackpackView.rowView().hidden()` → `HiddenHint.lines(...)`（037），不新增任何同步。
- **Alternatives**: 新的"摘要面板"替换主区域——要碰面板布局与命中测试；把摘要画成假格子——会被命中测试当作格子。

## D7 `/hidden` 之后的过滤：复用同一套匹配

- **Decision**: `HiddenSummaryRows.visible(lines, rest)`：每个类别行当成 `TextMatcher.ofTexts([显示名小写, id 小写])`，用 `SearchMatcher.matches(rest, matcher)` 过滤。`rest` 是指令之后的那部分查询。
- **Rationale**: 规格 FR-012：剩余文字只过滤类别行；`@`、`#` 对类别行不成立。因为 D2 让 `inMod`/`hasTag` 默认 false，`@mod`、`#tag` 备选自然为假（`-@mod`、`-#tag` 为真，等于不排除任何行，这与"物品没有任何标签时 `-#x` 不排除它"一致）。并且/或/排除/整段的规则免费继承。
- **Alternatives**: 单独写类别过滤——两套规则会漂移。

## D8 3 列面板里放不下

- **Decision**: 渲染时用字体把"类别行"换行到主区域宽度；`HiddenSummaryFit`（纯算术）决定在可用行数里显示哪些：先类别行，再标题，再说明；放不下就以 `…` 收尾；悬停主区域用 037 的完整提示（`drawHiddenHint`）。
- **Rationale**: 面板最窄是 3 列（约 104 像素宽），主区域最少一行高；类别名在中文里短，在英文里较长（"Redstone: 3 slots hold items"），换行后可能超过可用行数。截断加悬停保证"不溢出、信息不丢"（SC-005）。
- **Alternatives**: 把面板撑大——违反 042 的"搜索不改变面板尺寸"。

## D9 未知指令的提示

- **Decision**: `Command.unknown(原文)` → 结果空；搜索框悬停（取代计数提示）显示 `panel.mcfloatingbackpack.search.unknown_command`（"Unknown command: %s (available: /hidden)"）。`/`、`/h`、`/foo` 都是未知指令。
- **Rationale**: 规格 FR-010/013。不做补全，所以不为不完整的指令做特殊处理。

## D10 与 043 提供者的关系

- **Decision**: 提供者的词只出现在 `contains()` 的文字列表里，不参与 `inMod`/`hasTag`（FR-017）。`matcherFor` 的"没有生效提供者"快路径要保留，同时返回带 `inMod`/`hasTag` 的 matcher；因此快路径只省提供者的调用，不再是"同一个 `ofTexts` 对象"。
- **Rationale**: 043 的契约说"提供者只增加可被搜到的文字，不改变语法含义（`@`、`#`、`/` 归本模组）"，这里兑现。

## D11 性能

- **Decision**: 带前缀的查询每次按键重算 ≤400 格：每格一次 `ItemFacts` 的哈希查找（命中缓存后）加几次字符串包含与最多几十个标签 id 的循环。目标小于 1 毫秒量级；用单元测试粗略计时（纯逻辑部分），游戏里的实际（含 `getTags()` 首次填充）记"未度量"或用容器粗测。
- **Rationale**: 与 043 的度量方式一致；首次遇到某个物品种类要付一次 `getTags()` 与 `ModList` 的成本，之后是缓存。

## D12 容器自动验证的造数据办法

- **Decision**: 隐藏行：背包带分类扩展并在该行放物品，再取下扩展；背包标签：用 018 的标签面板标几个物品（`trash`、一个自定义标签）；物品标签与模组：开发环境里已有原版与 Curios/Accessories 等的物品（`@curios` 之类）；3 列窄面板：用 xdotool 缩小窗口（README 里已有办法）。
- **Rationale**: 沿用 042、043 的容器做法；不新增调试入口。
