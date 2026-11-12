# Implementation Plan: 搜索词提供者接口

**Branch**: `043-search-terms-provider-api` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/043-search-terms-provider-api/spec.md`；背景见 `docs/plan/backpack-search.md` 第 4 节；研究结论见 [research.md](./research.md)。

## Summary

本模组第一次公开 API：一个包 `pnnh.mcfloatingbackpack.api`，只含两个类型——`SearchTermsProvider`（`ItemStack → Collection<String>`）与 `BackpackSearchApi`（`apiVersion()` 和 `registerSearchTermsProvider(modId, provider)`）。别的模组注册的提供者在背包搜索里**只增加可被搜到的文字**；本模组不读语言文件、不做拼音、不引用任何其它模组。核心思路（读代码得出）：

1. **只换一处**：042 的 `SearchContext.matcherFor(stack)` 是匹配文字的唯一来源（搜索重算与工具区每帧高亮都经过它），在"当前语言名 + 注册名"之后并上提供者的词；没有生效提供者时走与 042 逐字相同的快路径（D1）。
2. **逻辑进通用纯类**：`pnnh.mcfloatingbackpack.search` 包（非客户端、无 Minecraft 类型依赖的泛型 `ProviderRegistry<S>`）负责注册、词的归一与防御、容错（WARN 去重、累计停用）、按提供者的 LRU 缓存；生产实例把 `S` 定为 `ItemStack`，缓存键为"物品 + 数据组件补丁"。单元测试用字符串当 `S`，不需要 Minecraft 引导（D2、D3）。
3. **`modLevels` 在查询时应用**：每次取词前问 `CompatConfig.policy().specificAdaptation(modId)`；`GENERIC`/`OFF` 不调用提供者、缓存不动，所以不需要为配置变化做任何失效（D5）。
4. **每帧安全**：工具区命中框每帧调用 `matcherFor`，所以提供者结果必须按缓存取，同一物品在一代缓存内最多调用 1 次（D4）。
5. **版本号用方法不用常量**：`static final int` 会被调用方的编译器内联，运行时检查就失效（D6）。
6. **客户端资源重载时清缓存**：用 `RegisterClientReloadListenersEvent`（切换语言与 F3+T 都会触发重载）（D7）。

范围：只做接口与匹配；任何具体模组的接入、README 推荐、验收都不做（见 spec 的 Clarifications）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`ItemStack`、`DataComponentPatch`、`RegisterClientReloadListenersEvent`）；**无新增依赖，不引用任何第三方模组类型**

**Storage**: 无（注册表、缓存、健康记录只在内存里；不进配置之外的任何文件；`modLevels` 沿用既有配置项，不新增配置项）

**Testing**: JUnit 5 纯逻辑测试：`ProviderRegistryTest`、`TermsNormalizerTest`、`ProviderHealthTest`、`TermsCacheTest`、`SearchTermsLevelsTest`、`SearchTermsMatchingTest`（与 042 语法组合）、`SearchTermsContractTest`（契约文档的数值对照代码常量）；`DependencyRulesTest` 照常；游戏里用容器里的临时假提供者验证（quickstart）

**Target Platform**: Minecraft 1.21.1；API 与注册表在通用代码里（专用服务器上可被加载但无人调用），搜索接入点在客户端

**Project Type**: 单一 NeoForge 模组

**Performance Goals**: 400 格背包每次按键重算 < 1 毫秒量级（5 个词的提供者，已缓存）；没有生效提供者时每次 `matcherFor` 只多一次"是否有生效提供者"的判断

**Constraints**:
- 服务端、网络包、协议号（17）、`neoforge.mods.toml`、数据格式、依赖全部不动
- 不新增配置项（`modLevels` 的注释与悬停提示补一句）
- API 公开签名只含 JDK 与 Minecraft 类型；API 包不引用任何客户端类
- 042 的全部测试不改而通过；没有提供者时不调用任何外部代码、不新增日志

**Scale/Scope**: 新增 2 个 API 类型；`search` 包约 6 个类（`ProviderRegistry`、`TermsNormalizer`、`ProviderHealth`、`TermsCache`、`SearchTermsLimits`、`StackKey`）；改动 `SearchContext`、`ClientSetup`（挂重载监听）、语言文件（`modLevels` 注释与提示）、README ×2、`docs/api/search-terms.md`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不新增 Screen、不改渲染路径；只扩大匹配用的文字 | PASS |
| II. Server Owns Items | 不触碰物品的归属与修改；没有新网络包、服务端代码；提供者只影响"哪些格子被高亮/显示"，对服务端是不可见的 | PASS |
| III. One Feature At A Time | 只做提供者接口；`@`、`#`、`/hidden`、具体模组接入都明确不做；不改 011～042 的规则 | PASS |
| IV. Vanilla Path Before Compat | 没有提供者时与 042 一致，原版路径不受影响；不引用任何第三方类型 | PASS |
| V. Verify In A Client Session | quickstart 要求客户端会话验证；没有真实提供者时用容器里的临时假提供者，并在 DELIVERY 如实记录"假提供者"这一事实，不声称验证了任何真实模组 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增任何贴图或共享资源；缓存有大小上限（每提供者 2048 条）；DELIVERY 记录实测的重算耗时，没测写"未度量" | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新增依赖、无第三方类型、`neoforge.mods.toml` 不变；本模组调用别的模组交来的代码，属于与别的模组的主动交互 → 受 `modLevels` 控制（FR-006），每一级的含义已在规格写明；`DependencyRulesTest` 照常通过 | PASS |

**宪法是否要补"公开 API"条文**（spec 留给 plan 的问题）：**不补**。VII 管的是"本模组依赖别的模组"；这里是第一次出现"别的模组依赖本模组的 API"，稳定性承诺写进契约与开发者说明即可，且只有一个 API 包、一个注册入口，补条文为时过早。若以后有第二个公开接口，再评估补一条治理规则（D9）。

**设计后复查**：D2 的泛型通用类让纯逻辑可测，不引入任何新依赖；D5 让 `modLevels` 不需要失效逻辑，反而减少状态；其余无变化，全部 PASS。

## Project Structure

### Documentation (this feature)

```text
specs/043-search-terms-provider-api/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/search-terms-api.md
├── checklists/requirements.md
└── tasks.md                      # 由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── api/                                      # 新：唯一的公开 API 包
│   ├── SearchTermsProvider.java              # ★ 函数式接口 ItemStack → Collection<String>
│   └── BackpackSearchApi.java                # ★ apiVersion()、registerSearchTermsProvider(modId, provider)
├── search/                                   # 新：通用（非客户端）实现
│   ├── ProviderRegistry.java                 # ★ 泛型 <S>：注册、替换、按 enabled 过滤、取词（缓存、防御、容错）
│   ├── TermsNormalizer.java                  # ★ null/空/空白/超长/超量 → 归一的小写词表 + 违规标记
│   ├── ProviderHealth.java                   # ★ 错误计数、WARN 去重、停用
│   ├── TermsCache.java                       # ★ 有上限的 LRU（泛型键）
│   ├── SearchTermsLimits.java                # ★ 常量：MAX_TERMS=64、MAX_TERM_LENGTH=128、MAX_ERRORS=20、CACHE_SIZE=2048
│   ├── StackKey.java                         # record(Item, DataComponentPatch)；空补丁时只按物品
│   └── SearchTerms.java                      # 生产单例：ProviderRegistry<ItemStack> + CompatConfig + 日志 + 重载清缓存入口
└── client/
    ├── ClientSetup.java                      # 改：RegisterClientReloadListenersEvent → SearchTerms.onResourcesReloaded()
    └── panel/search/SearchContext.java       # 改：matcherFor 并上 SearchTerms.termsFor(stack)
src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json   # 改：modLevels 的悬停提示补一句
src/main/java/pnnh/mcfloatingbackpack/compat/CompatConfig.java         # 改：modLevels 的注释补一句
README.md / README_CN.md                      # 改：搜索一节补充（不点名具体模组）
docs/api/search-terms.md                      # 新：开发者说明（中文）

src/test/java/pnnh/mcfloatingbackpack/search/
├── ProviderRegistryTest.java  TermsNormalizerTest.java  ProviderHealthTest.java
├── TermsCacheTest.java  SearchTermsLevelsTest.java  SearchTermsMatchingTest.java
└── SearchTermsContractTest.java
```

**Structure Decision**: API 包薄、实现包厚：`BackpackSearchApi` 只做参数校验并委托给 `SearchTerms`；纯逻辑全在泛型 `ProviderRegistry<S>` 与小类里，用字符串当 `S` 做单元测试，生产用 `ItemStack`。客户端代码只有"重载监听"与 `SearchContext` 的一处并词。

## 实现要点（给 tasks 阶段）

1. **API 类型**：`SearchTermsProvider { Collection<String> termsFor(ItemStack stack); }`（`@FunctionalInterface`）；`BackpackSearchApi.registerSearchTermsProvider(String modId, SearchTermsProvider p)`：modId 须匹配 `[a-z0-9_]+`（与 `CompatPolicy` 的 id 规则一致，才能被 `modLevels` 指到），不匹配或 p 为 null → `IllegalArgumentException`；`apiVersion()` 返回 1。API 不加 `@OnlyIn`。
2. **`ProviderRegistry<S>`**：构造参数 `Function<S,Object> keyOf`、`UnaryOperator<S> prepare`、`Predicate<String> enabled`、`WarnSink`（`warn(String)`、`debug(String)`）。`register(modId, Function<S,? extends Collection<String>>)`（`ConcurrentHashMap`，替换时新建 entry，旧缓存随之丢弃）；`hasActive()`（任一 entry 未停用且 `enabled`）；`termsFor(S)`：逐个生效 entry，查其缓存，未命中才 `prepare` 后调用并 try/catch，归一，存缓存；返回合并去重的 `List<String>`。每个 entry 的缓存与健康记录用 `synchronized`（只在渲染线程使用，同步成本可忽略）。
3. **`TermsNormalizer`**：null→空；每个元素 null/空白丢弃，`toLowerCase(Locale.ROOT)`；超过 128 字符的词**直接丢弃**（截断会产生误导性的前缀）；超过 64 个词保留前 64 个；返回 `Result(List<String> terms, boolean truncated, boolean droppedLong)`，违规标记交给 `ProviderHealth` 去记一条 WARN（每提供者每类一次）。
4. **`ProviderHealth`**：`recordError(Class<?>)`（计数；同一异常类只 WARN 一次；累计达 20 → `disabled=true` 并再 WARN 一次）、`recordViolation(kind)`（每类一次 WARN，不计入停用）。
5. **`SearchTerms`（生产单例）**：`ProviderRegistry<ItemStack>`，`keyOf = StackKey.of`、`prepare = stack.copyWithCount(1)`、`enabled = id -> CompatConfig.policy().specificAdaptation(id)`、WARN 用 `MCFloatingBackpack.LOGGER`；`onResourcesReloaded()` 清所有缓存；`register` 记一条 INFO（`search terms provider registered by <id>`）。
6. **`SearchContext.matcherFor`**：`if (!SearchTerms.hasActive()) return 原来的 ofTexts(List.of(name, path));` 否则 `ofTexts(name, path, ...terms)`；其余（`toolMatches`、`SOURCE`）不变。
7. **重载监听**：`ClientSetup.register` 里 `modEventBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener((ResourceManagerReloadListener) rm -> SearchTerms.onResourcesReloaded()))`。
8. **语言与配置文本**：`CompatConfig.modLevels` 注释与两份语言文件的 `config.mcfloatingbackpack.modLevels.tooltip` 各补一句；`SearchResourcesTest`/语言一致性测试照常通过。
9. **文档**：契约（`contracts/search-terms-api.md`）为准；`docs/api/search-terms.md` 写依赖写法（`compileOnly` + `neoforge.mods.toml` 的 optional 依赖 + `ModList.isLoaded` 判断 + 在只在存在时才加载的类里调用）、注册时机、约定、示例；README 两份搜索一节补充，**不点名具体模组**。
10. **容器验证要点**：在容器里对源码做**临时**修改（`ClientSetup` 里注册一个假提供者，如返回 `"zuanshigao"`、`"zsg"`），验证后还原；`git status` 确认无残留；DELIVERY 写明"用的是临时假提供者，没有验证任何真实模组"。

## Complexity Tracking

无需要论证的违例。
