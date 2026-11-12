# Quickstart: 搜索前缀语法的验证

语法与用例以 [contracts/query-syntax.md](./contracts/query-syntax.md) 为准；类型与状态见 [data-model.md](./data-model.md)。

## A. 单元测试（不需要游戏）

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.client.panel.search.*' --tests 'pnnh.mcfloatingbackpack.resources.*'
./gradlew compileJava test
```

预期：全部通过；**042、043 的测试不改**。覆盖点：

1. **解析**（`SearchQueryPrefixTest`）：契约"解析用例"表逐行：三种备选、`-` 与前缀、单独的 `@`/`#`、`@"…"`、引号内与词中的 `@`/`#`、`@a|#b`、大小写、`/hidden` 的位置与前导空白、`/hidden 词`、未知指令（`/`、`/h`、`/foo`、`/hiddenx`）、`a /hidden`、`"/hidden"`。
2. **匹配分派**（`SearchMatcherPrefixTest`）：假 `TextMatcher` 覆盖 `inMod`/`hasTag`；并且、或、排除与前缀组合；没有覆盖时 `@`/`#` 为假、`-@`/`-#` 为真。
3. **模组与标签规则**（`ModMatchTest`、`TagMatchTest`）：契约"匹配用例"表逐行：命名空间/显示名、物品标签路径子串与完整 id 前缀、`c:`、背包标签 id 与显示名、含冒号对背包标签不命中、并集、没有标签。
4. **隐藏摘要**（`HiddenSummaryRowsTest`、`HiddenSummaryFitTest`）：行生成、类别过滤（普通词、`-词`、`@`/`#` 不成立）、格数总和、没有隐藏物品；`fit` 在各种可用行数下的取舍（类别行 > 标题 > 说明，省略号）。
5. **状态**（`SearchCommandStateTest`）：指令查询的结果是空数组且 `isFiltering` 为真、`/hidden` 的计数 = 过滤后的格数总和、未知指令计数 0、进入/退出指令清选区并回滚动、042 的 `Source` 假实现（不覆盖 `hiddenLines`）照常工作。
6. **资源与不变量**（`SearchPrefixResourcesTest`）：两个新语言键中英文齐全；协议号仍 17；`neoforge.mods.toml`、依赖、配置未动；`TagsUpdatedEvent` 清缓存已接线（源码文本检查）；`SearchContext.toolMatches` 对指令返回 false。
7. **粗略计时**（`SearchPrefixCostTest`）：400 格、`@` 与 `#` 查询的纯逻辑部分，热的重算耗时打印出来写进 DELIVERY。

## B. 游戏内（容器自动验证）

前提：`scripts/ai-gui` 的容器；背包里有原版物品（几种原木、木板、锭）与开发环境里已有的模组物品；用 018 的标签面板给几个物品标 `trash` 与一个自定义标签；造一个隐藏行：背包装一个分类扩展、在该行放物品，再取下扩展（面板出现黄点）。

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 `@模组` | 输入 `@minecraft` | 只剩原版物品；换成模组的 id 片段、显示名片段（`@"…"` 带空格）也能命中；`@nosuchmod` 命中 0 |
| B2 `@` 加关键词 | `@minecraft log` | 只剩原版的、名字含 log 的物品 |
| B3 `#` 物品标签 | `#logs`、`#c:ingots`、`#minecraft:logs`、`#c:` | 命中符合的物品；`#nosuchtag` 命中 0 |
| B4 `#` 背包标签 | `#trash`；中文界面再试 `#垃圾`；自定义标签名 | 标了该标签的物品命中 |
| B5 组合 | `@minecraft #logs`、`#logs\|#planks`、`-#planks`、`"@minecraft"` | 符合并且/或/排除；引号内是普通词（几乎不命中） |
| B6 工具区 | 工具区里放有标签/模组符合的物品，输入对应查询 | 工具区的命中框同样按 `@`、`#` 判断；`/hidden` 时无框 |
| B7 `/hidden` | 输入 `/hidden` | 主区域没有物品格子，列出类别与格数、说明；计数 = 隐藏格总数；悬停主区域弹 037 的完整提示 |
| B8 `/hidden` 过滤 | `/hidden fish`、`/hidden @x` | 按类别名过滤；`@x` 无行；没有隐藏物品时（先把扩展放回）显示"没有隐藏的物品" |
| B9 未知指令 | `/foo`、`/` | 无结果；悬停搜索框出现"未知指令…（可用：/hidden）" |
| B10 窄面板 | 把窗口缩到面板自动收缩到 3 列（README 里的 xdotool 办法），`/hidden` | 文字不溢出面板，放不下时以省略号收尾，悬停仍有完整提示 |
| B11 状态 | `/hidden` 时点主区域、`Ctrl` 点击、`Delete` | 没有任何格子可操作；Esc/`×` 后恢复完整显示 |
| B12 标签数据更新 | `/reload` 或重进世界后再搜 `#logs` | 仍然正确（`TagsUpdatedEvent` 清缓存） |
| B13 042/043 不变 | `oak log`、`iron\|gold`、`-planks`、`"oak log"`；（043）临时假提供者的词 | 与之前一致；提供者的词不参与 `@`、`#` |

记录：每个场景实际验证了什么；没验证的写"未验证"（真机输入法、Windows/Linux、真实的模组标签）。

## C. 文档核对

- `README.md` / `README_CN.md` 搜索一节：补三个前缀的写法与例子，说明 `/hidden` 只显示摘要。
- `docs/plan/backpack-search.md`：状态与第 3 节已更正的 `/hidden` 说明一致。
- 契约与 DELIVERY 的"已知限制"一致（隐藏物品不在客户端）。

## D. 度量（宪法 VI，DELIVERY 记录）

- 400 格背包、`@` 与 `#` 查询：缓存热、冷两种情形的重算耗时（单元测试里的纯逻辑部分）。
- 游戏里首次遇到每个物品种类时 `getTags()` 与 `ModList` 的填充成本：容器里粗测或写"未度量"。
- 做不了的写"未度量"，不声称达标。
