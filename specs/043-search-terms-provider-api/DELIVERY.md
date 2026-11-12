# 043 搜索词提供者接口：交付说明

**状态**：完成（2026-11-12）。代码、自动化测试、文档完成；游戏内验证做了 AI 自动验证（**临时假提供者，没有验证任何真实模组**，见下）；**用户已确认 T032（没有问题）**。

## 基线与结果

- 基线（T001，实现前）：`./gradlew test` 150 个类、1091 个用例，全部通过；工作区只有 043 的文档改动与 042 前向提示的两处文字。
- 完成后：**158 个类、1164 个用例，全部通过**（新增 8 个类、73 个用例）。新增测试类：`ProviderRegistryTest`（25）、`TermsNormalizerTest`（10）、`ProviderHealthTest`（5）、`TermsCacheTest`（4）、`SearchTermsMatchingTest`（12）、`SearchTermsLevelsTest`（8）、`SearchTermsContractTest`（8）、`SearchTermsCostTest`（1）。042 的全部测试不改而通过；`DependencyRulesTest` 照常通过。
- 变异检查（T008）：把 `ProviderHealth` 的停用条件 `>=` 改成 `>`，3 个用例失败，还原后通过。
- 没有动服务端、网络包、协议号（仍 17，`SearchTermsContractTest` 断言）、`neoforge.mods.toml`（断言不含 search）、依赖、数据格式；没有新增配置项（只补了 `modLevels` 的注释与两份悬停提示）；没有新增贴图。

## 新增与改动

- 新增公开 API 包 `pnnh.mcfloatingbackpack.api`：`SearchTermsProvider`、`BackpackSearchApi`（`apiVersion()` 是方法不是常量；`registerSearchTermsProvider(modId, provider)`）。
- 新增通用（非客户端）实现包 `pnnh.mcfloatingbackpack.search`：`ProviderRegistry<S>`（泛型，注册、取词、容错、缓存）、`TermsNormalizer`、`ProviderHealth`、`TermsCache`、`SearchTermsLimits`、`StackKey`、`ProviderIds`、`SearchTerms`（生产单例：`S = ItemStack`、`modLevels` 判断、日志）。
- 改动：`SearchContext.matcherFor`（没有生效提供者时与 042 逐字相同；否则在名字与注册名之后并上提供者的词）、`ClientSetup`（资源重载时清缓存）、`CompatConfig` 与两份语言文件（`modLevels` 的说明补一句）、`README.md`/`README_CN.md`（搜索一节，不点名任何具体模组）。
- 文档：`docs/api/search-terms.md`（开发者说明）、`contracts/search-terms-api.md`（契约，数值表与代码常量由测试对照）。

## 与 plan/tasks 的偏离（随做随记）

- T010 与 T020 合并做：`SearchTerms` 一开始就接了 `CompatConfig.policy().specificAdaptation`，没有先恒为 true 再改。
- T013/T016/T018 的测试都并进 `ProviderRegistryTest`；API 门面的 id 校验抽成 `ProviderIds`（无 Minecraft 类型，便于测试），测在 `SearchTermsLevelsTest`。
- `ProviderHealth.recordError` 对"累计停用"也返回信息（`ErrorOutcome(warnThisClass, switchedOffNow)`），所以停用那条 WARN 与"首次该类异常"那条分开。
- 注册表捕获 `RuntimeException | LinkageError`（契约与开发者说明里写明）：提供者所在模组缺类时抛的 `NoClassDefFoundError` 也不会拖垮搜索。
- 失败物品的"空答案"也被缓存（plan 没写）：否则工具区每帧对同一个出错物品重试，20 帧就把提供者整个停用了。
- T024：`docs/` 没有总索引，所以没有补索引行；README 两份已链到开发者说明。
- 契约对照测试比 tasks 写的多了几项：API 包只有两个文件、两个包都不含客户端导入、README/文档里没有出现具体模组名。

## 游戏内验证（T027，AI 自动验证，2026-11-12，容器 `mcfb-c1`，创造模式世界，背包里 12 种物品）

**重要：用的是临时假提供者（`faketerms`：对钻石镐返回 `ZUANSHIGAO`、`ZSG`，对其它所有物品返回 `ZZZ`；`boomterms`：对下界合金物品抛 `IllegalStateException`，对其它返回 `bbb`），写在一个临时类里同步进容器，验证后已删除（`git status` 确认 `src/main` 无残留）。没有验证任何真实模组。**

| quickstart | 结果 |
|---|---|
| B1 提供者的词生效 | **通过**：背包里放了钻石镐后输入 `zsg`，主区域只剩钻石镐（命中数 1，命中框）；没有钻石镐时 `zsg` 命中数 0 |
| B2 全部共有的词 | **通过**：输入 `zzz`，背包里所有非空格子命中（放入钻石镐与下界合金锭之前命中数 12；之后 13——假提供者对钻石镐只给 `ZSG` 系列，没给 `ZZZ`，所以钻石镐不在其中，这是假提供者的设计，不是缺陷） |
| B3 工具区高亮 | **通过**：`zzz` 下工具区（过滤器、垃圾桶、标签管理器等）的物品也被框出；**帧率没有测**（容器是软件渲染，不可信） |
| B4 语法 | **通过**：`zs gao` → 1（钻石镐，两个词都在它的拼音里）；`zzz -zsg` → 13（**这一项不能证明"排除"**：钻石镐本来就没有 `zzz`；"排除提供者给的词"只有单元测试证据：`SearchTermsMatchingTest.aMinusExcludesWhatTheProvidersTermsFind`）；`zsg\|bbb` → 钻石镐与其它被 `bbb` 命中的物品，下界合金锭不在其中（见 B7） |
| B5 `modLevels` | **通过**：改配置文件 `faketerms=GENERIC`（**不重启**）后 `zzz` 命中数 0，同时另一个提供者的 `bbb` 仍命中 13；改回 `faketerms=FULL` 后 `zzz` 又命中 13 |
| B6 没有提供者 | **通过**：删除临时类、重新同步、重启客户端后，`zsg` 命中数 0（钻石镐就在背包里）、`pick` 命中 2 个镐加一个名字含 Pickup 的工具格；日志里**没有任何与 terms 相关的行** |
| B7 日志 | **通过**：注册时两条 INFO（各含模组 id）；多次搜索后只有**一条** WARN（`boomterms` 的 `IllegalStateException`，含模组 id、异常类型与物品，说明"该物品的词被忽略"）；同一次会话里别的提供者的词不受影响（B4 里下界合金锭仍被 `zzz` 命中）。其余 ERROR 行都是容器环境的噪声（旁白、声音、X11 光标） |

没做的：**累计 20 次异常后停用**只有单元测试证据（`ProviderRegistryTest`），游戏里没有触发；真实模组的接入；真机与 Windows/Linux。

## 度量（T028，宪法 VI）

- **纯逻辑部分（单元测试，开发机，`SearchTermsCostTest`）**：400 格、返回 5 个词的假提供者：冷（每格各问一次提供者）**约 5.2 毫秒**（含类初始化与 JIT），热（全部来自缓存）**约 0.41 毫秒/次查询**（含建匹配器与匹配）。热的数字低于 1 毫秒，符合 SC-003；冷的只发生在"第一次遇到某个物品"。
- **同一物品最多问一次**：`ProviderRegistryTest`（同一键 50 次取词只调用 1 次）与 `SearchTermsCostTest`（400 格冷之后再来 100 轮，提供者调用数仍为 400）证明。
- **未度量**：400 格全是装满物品的潜影盒（最坏的数据组件，`DataComponentPatch` 的哈希成本，research D3 的风险）——单元测试里没有 Minecraft 类，游戏里也没有造这个场景。**不声称达标**。若真机上发现带大组件的物品搜索时有卡顿，处理办法是把 `StackKey` 改成只含少数关键组件（药水内容、附魔、自定义名等）的白名单键，不改 API。

## 已知限制

- 提供者**只增加词**：不能改变显示名、顺序、语法含义或高亮；含空格的词只能被整段 `"…"` 查询命中。
- 搜索打开时工具区每帧都会走 `matcherFor`（042 的设计），有提供者时依赖缓存；缓存按"物品 + 数据组件补丁"取键，带大组件的物品哈希成本未度量（上节）。
- 累计 20 次异常后提供者在本会话被停用，重启游戏才恢复（不提供在游戏里恢复的命令）。
- `modLevels` 的改动对已经显示出来的搜索结果要到下一次输入才生效（结果只在查询变化时重算，042 的设计）；提供者被关掉后，当前结果里已命中的格子保持到查询变化。
- 真机、Windows/Linux、真实提供者模组：未验证。

## 给 044 的提醒

- `@`、`#`、`/` 前缀归 044，与提供者无关：它们在 `SearchQuery` 的解析处扩展；提供者的词只参与普通词的匹配。
- `SearchContext.matcherFor` 现在是"当前语言名 + 注册名 + 提供者词"；044 若要按模组、标签匹配，不要再往这个文字列表里塞，而是在 `SearchMatcher` 的项类型里分支。

## 宪法复查（T031）

I Overlay：不新增 Screen、不改渲染路径。II Server Owns Items：没有新网络包或服务端代码，提供者对服务端不可见。III One Feature：只做提供者接口。IV Vanilla：没有提供者时与 042 完全一致。V Verify：已在客户端会话里验证（假提供者，如实标注）。VI Budget：无新贴图；缓存有上限（每提供者 2048 条）；度量见上，大组件情形"未度量"。VII：无新依赖、无第三方类型、`neoforge.mods.toml` 不变；调用别的模组交来的代码受 `modLevels` 控制，每一级的含义写在规格、配置注释与悬停提示里。**结论：全部通过，宪法不需要修订。**
