# Tasks: 搜索词提供者接口

**Input**: `/specs/043-search-terms-provider-api/` 的 spec.md、plan.md、research.md、data-model.md、contracts/search-terms-api.md、quickstart.md；宪法

**Tests**: 注册、归一、容错、缓存、`modLevels` 判定、无提供者时与 042 一致、与 042 语法组合、契约数值对照，全部用 JUnit 覆盖（泛型类用 `String` 当 `S`，不加载 Minecraft 类）；`DependencyRulesTest` 照常。**游戏内的实际效果（工具区每帧高亮、实时过滤、`modLevels` 改配置后不重启生效、日志内容）只能在客户端会话里验证**（宪法 V）：编译与单元测试通过不等于界面成立。**没有真实提供者，游戏内验证只用容器里的临时假提供者，DELIVERY 必须如实写明。**

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` 表示 `src/main/resources/assets/mcfloatingbackpack/`；`…/search/` 与 `…/api/` 是新增的包
- **不包含提交 Git 的任务**（等用户要求再提交；**也不打 tag**）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，已有就不启动第二个**；容器自动验证见 `scripts/ai-gui/README.md`
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；AI 自动验证写"AI 自动验证"备注与证据；没做的写"未验证"
- **不改**服务端代码、任何网络包、协议号 17、`neoforge.mods.toml`、依赖、数据格式；**不新增配置项**（只补 `modLevels` 的注释与提示）；**不新增贴图**；**不在 `src/main` 留任何假提供者或调试入口**；**不引用、不推荐、不验证任何具体模组**（spec Clarifications）；不修改 `docs/ideas`
- 用户故事：US1 有提供者时多出它给的词；US2 没有提供者时与 042 完全一致；US3 提供者出错或太慢不影响搜索；US4 玩家可在配置里关掉某个提供者；US5 开发者能照说明使用接口

---

## Phase 1: Setup

- [X] T001 基线：`./gradlew test` 记下当前类数与用例数；`git status` 确认工作区只有 043 的文档改动（以及 042 前向提示的两处文字）；在 `specs/043-search-terms-provider-api/DELIVERY.md`（新建，中文）记下基线

## Phase 2: Foundational（纯 Java，泛型，不碰游戏对象；T002～T005 互相独立可并行）

- [X] T002 [P] `…/search/SearchTermsLimits.java`：四个常量 `MAX_TERMS=64`、`MAX_TERM_LENGTH=128`、`MAX_ERRORS=20`、`CACHE_SIZE=2048`（data-model）；私有构造
- [X] T003 [P] `…/search/TermsNormalizer.java`：`normalize(Collection<String>)` → `Result(List<String> terms, boolean tooMany, boolean tooLong)`：`null`→空；丢弃 null/空白；`toLowerCase(Locale.ROOT)`；超过 `MAX_TERM_LENGTH` 的整词丢弃并标 `tooLong`（不截断）；去重并保序；超过 `MAX_TERMS` 个只留前 `MAX_TERMS` 并标 `tooMany`
- [X] T004 [P] `…/search/TermsCache.java`：泛型 LRU（访问序 `LinkedHashMap`，超限淘汰最久未用）：`get/put/clear/size`，上限由构造参数给；非线程安全
- [X] T005 [P] `…/search/ProviderHealth.java`：`recordError(Class<?>)` 返回要不要 WARN（该异常类首次才 WARN）、累计 `MAX_ERRORS` 次后 `disabled=true` 并返回"停用 WARN"一次；`recordViolation(kind)`（`TOO_MANY`/`TOO_LONG`，每类一次 WARN，不计入停用）；`isDisabled()`
- [X] T006 `…/search/ProviderRegistry.java`（泛型 `<S>`，依赖 T002～T005）：构造参数 `Function<S,Object> keyOf`、`UnaryOperator<S> prepare`、`Predicate<String> enabled`、`WarnSink`（`warn/debug/info`，纯接口）；`register(modId, Function<S,? extends Collection<String>>)`（`ConcurrentHashMap`；同 id 替换 = 新建 entry，旧缓存丢弃，记 debug）；`hasActive()`（存在 `enabled(modId) && !disabled` 的 entry）；`termsFor(S)`：对每个生效 entry 先查缓存，未命中才 `fn.apply(prepare(s))`，try/catch 异常 → 无词并按 `ProviderHealth` 记 WARN，归一并按违规记 WARN，写缓存；合并去重返回；`clearCaches()`（保留健康记录）；entry 的缓存与健康用 `synchronized`
- [X] T007 [P] 单元测试（`…test/search/`）：`TermsNormalizerTest`（null、空、空白、大小写、重复、整词超长被丢弃、超量只留前 64、保序）、`TermsCacheTest`（命中、LRU 淘汰顺序、`clear`）、`ProviderHealthTest`（同类只 WARN 一次、不同类各一次、达到 20 次停用并再 WARN 一次、违规每类一次且不计入停用）
- [X] T008 运行 `./gradlew compileJava test`；T007 全部通过（含故意写错一处看它真的会失败）（变异检查：把 `ProviderHealth` 的停用条件 `>=` 改成 `>`，3 个用例失败，还原后通过）

**Checkpoint**: 归一、缓存、健康、注册表都就绪，全部可在不启动游戏的情况下测试

## Phase 3: User Story 1 - 有提供者时，背包搜索多出它给的词 (Priority: P1) 🎯 MVP

**Goal**: 别的模组注册提供者后，背包搜索框里输入它给的词，对应物品命中；工具区同样高亮；只增加、不替换。

**Independent Test**: quickstart A（测试里的假提供者）与 B1～B4。

- [X] T009 [US1] `…/api/SearchTermsProvider.java`（`@FunctionalInterface`，`Collection<String> termsFor(ItemStack stack)`，Javadoc 写明调用约定）与 `…/api/BackpackSearchApi.java`（`apiVersion()` 返回 1，**方法不是常量**；`registerSearchTermsProvider(String modId, SearchTermsProvider provider)`：`modId` 不匹配 `[a-z0-9_]+` 或 `provider` 为 null → `IllegalArgumentException`；委托给 `SearchTerms`）；API 包不加 `@OnlyIn`、不引用任何客户端类
- [X] T010 [US1] `…/search/StackKey.java`（`record StackKey(Item item, DataComponentPatch patch)`，`of(ItemStack)`：补丁为空时用 `DataComponentPatch.EMPTY`；实现时核对 1.21.1 里 `getComponentsPatch()` 的 `equals/hashCode`）与 `…/search/SearchTerms.java`（生产单例：`ProviderRegistry<ItemStack>`，`keyOf=StackKey.of`、`prepare=copyWithCount(1)`、`WarnSink` 用 `MCFloatingBackpack.LOGGER`；`register` 记一条 INFO `search terms provider registered by <modId>`；对外 `hasActive()`、`termsFor(ItemStack)`、`onResourcesReloaded()`）；此时 `enabled` 先恒为 true，T018 再接 `modLevels`
- [X] T011 [US1] `…/client/panel/search/SearchContext.java` 的 `matcherFor`：`if (!SearchTerms.hasActive())` 返回与 042 逐字相同的 `TextMatcher.ofTexts(List.of(name, path))`；否则 `ofTexts` 在 `name`、`path` 之后并上 `SearchTerms.termsFor(stack)`；`toolMatches`、`SOURCE` 不变
- [X] T012 [US1] `…/client/ClientSetup.java`：`register` 里 `modEventBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener((ResourceManagerReloadListener) rm -> SearchTerms.onResourcesReloaded()))`（research D7）
- [X] T013 [P] [US1] 单元测试 `…test/search/ProviderRegistryTest.java`：注册与取词（词并入、转小写）、多个提供者取并集、同 id 再注册替换且旧缓存丢弃（提供者调用次数）、非法 id 与 null 在 API 门面处抛 `IllegalArgumentException`（门面逻辑若依赖 `ItemStack` 则把校验抽成无 Minecraft 类型的静态方法再测）、物品按键区分（用两个不同的 `String` 键证明"按组件区分"的路径）、同一键在一代内只调用一次、`clearCaches` 后重新调用（实际并入 `ProviderRegistryTest`；API 门面的 id 校验抽成 `ProviderIds`，测在 `SearchTermsLevelsTest`）
- [X] T014 [P] [US1] 单元测试 `…test/search/SearchTermsMatchingTest.java`：用 `TextMatcher.ofTexts(name, path, 假提供者词)` 与 042 的 `SearchQuery`/`SearchMatcher`：SC-001 的 10 种物品 × 四种写法（提供者给的中文名、英文名、全拼、首字母）= 40 次全部命中；大写词与小写查询命中；当前语言名里的词仍命中（只增加不替换）；`zs gao`（并且）、`-zsg`（排除）、`zsg|jpg`（或）、`"zuan shi"`（整段）的语义
- [X] T015 [US1] 运行 `./gradlew compileJava test`；042 的全部测试不改而通过

**Checkpoint**: 有提供者时词生效，纯逻辑与接入点都已测

## Phase 4: User Story 2 - 没有提供者时与 042 完全一致 (Priority: P1)

**Goal**: 没有任何提供者时，搜索行为、性能、日志与 042 完全一致。

**Independent Test**: quickstart A.6 与 B6。

- [X] T016 [P] [US2] 单元测试 `…test/search/ProviderRegistryTest.java`（追加）与 `SearchTermsMatchingTest.java`（追加）：空注册表 `hasActive()` 为假且 `termsFor` 返回空、**没有调用任何外部函数**（计数器为 0）、不产生任何 `WarnSink` 调用（SC-002）；全部提供者都被 `enabled` 判为假或已停用时也是 `hasActive()` 为假；`SearchContext.matcherFor` 的"没有提供者"分支用源码文本检查它与 042 的两项文字一致（`getHoverName().getString()` 与注册名路径，`ofTexts(List.of(name, path))`）
- [X] T017 [US2] `README.md` 与 `README_CN.md` 搜索一节补充：默认按当前语言的名字与注册名搜索；其它模组可通过本模组的公开接口增加可搜索的文字，指向 `docs/api/search-terms.md`；**不点名任何具体模组**；`SearchResourcesTest` 同类的文档检查（若有）照常通过

**Checkpoint**: 042 行为与文档不变，只多了一句说明

## Phase 5: User Story 3 - 提供者出错或太慢时，搜索不受影响 (Priority: P1)

**Goal**: 坏提供者不崩溃、不卡死、不刷屏；其它提供者与 042 的匹配照常。

**Independent Test**: quickstart A.3 与 B7。

- [X] T018 [P] [US3] 单元测试 `…test/search/ProviderRegistryTest.java`（追加，实现已在 T006，这里只补验证）：抛异常 → 该物品无额外词、其它提供者的词照常；同一异常类只 WARN 一次、不同类各一次；累计 20 次后停用且再 WARN 一次、停用后不再被调用；返回 `null`/空集合不记错误；返回含空串、纯空白、超长词、超量集合的结果（只留合规词、各类违规只 WARN 一次且不计入停用）；一个坏提供者不影响另一个；`WarnSink` 消息里含模组 id 与异常类型名
- [X] T019 [US3] 对照 research D8 与 contracts 的数值：T006/T003/T005 的实现与契约里的"返回值的规则""容错"两节逐条一致；不一致的改代码（不改契约）

**Checkpoint**: 容错行为有测试保护

## Phase 6: User Story 4 - 玩家可以在配置里关掉某个提供者 (Priority: P2)

**Goal**: `modLevels` 里 `GENERIC`/`OFF` 让提供者不被调用、词不生效；改回 `FULL` 立即恢复；不重启。

**Independent Test**: quickstart A.4 与 B5。

- [X] T020 [US4] `…/search/SearchTerms.java`：`enabled = id -> CompatConfig.policy().specificAdaptation(id)`（只有 FULL 为真；`CompatConfig` 在配置未加载时返回 `DEFAULT`，一切 FULL）；其余不变
- [X] T021 [P] [US4] `…/compat/CompatConfig.java` 的 `modLevels` 注释补一句（英文）："For a mod that registered search terms, GENERIC and OFF mean its search terms are ignored."；`…res/lang/en_us.json` 与 `zh_cn.json` 的 `config.mcfloatingbackpack.modLevels.tooltip` 各补对应一句（中文："对注册了搜索词的模组，GENERIC 与 OFF 表示忽略它提供的搜索词。"）；两份语言文件的键保持一致
- [X] T022 [P] [US4] 单元测试 `…test/search/SearchTermsLevelsTest.java`：`enabled` 为假时提供者**调用次数为 0**；`enabled` 由假变真后立刻生效，缓存没有被清空（用调用计数证明）；未列出的 id 为真；`GENERIC` 与 `OFF` 都为假（用 `CompatPolicy.parse` 造策略，只测 `specificAdaptation` 的组合，不碰 `ModConfigSpec`）；语言文件检查：两份 `modLevels.tooltip` 都含"搜索"/"search terms"字样
- [X] T023 [US4] 运行 `./gradlew compileJava test`；`DependencyRulesTest` 照常通过（无新依赖、无第三方类型、`neoforge.mods.toml` 不变）

**Checkpoint**: `modLevels` 控制接通且有测试

## Phase 7: User Story 5 - 开发者能照说明使用接口 (Priority: P2)

**Goal**: 别的模组作者只读文档就能接入；契约与代码不会悄悄分叉。

**Independent Test**: quickstart A.8 与 C。

- [X] T024 [P] [US5] `docs/api/search-terms.md`（中文，开发者说明）：以 `contracts/search-terms-api.md` 为准，写依赖写法（`compileOnly`、`neoforge.mods.toml` 的 optional 依赖、`ModList.isLoaded` 判断、只在存在时才加载引用 API 的类、`apiVersion()` 运行时检查）、注册时机与线程、调用约定、返回值规则与上限、容错、玩家的 `modLevels` 控制、稳定性承诺、最小示例；**不涉及任何具体模组**；`docs/` 的索引（如 `docs/plan/README.md` 类的目录说明）里补一行（`docs/` 没有总索引，所以没有补索引行；README 两份已链到它）
- [X] T025 [P] [US5] 单元测试 `…test/search/SearchTermsContractTest.java`：读 `specs/043-search-terms-provider-api/contracts/search-terms-api.md` 末尾的"数值"表，与 `SearchTermsLimits` 四个常量逐一相等；契约里的方法名 `apiVersion`、`registerSearchTermsProvider`、类型名 `SearchTermsProvider` 出现在源码里（文本检查）；`BackpackSearchApi` 里没有 `static final int` 形式的版本常量（文本检查，防止被"优化"回内联常量）；API 包里的源码不含 `net.neoforged.api.distmarker` 与 `client` 包的导入
- [X] T026 [US5] 对照 `docs/api/search-terms.md`、契约、`BackpackSearchApi`/`SearchTermsProvider` 的 Javadoc 三处一致；不一致的以契约为准改另外两处

**Checkpoint**: 文档与代码互相对得上

## Phase 8: 游戏内验证、度量与收尾

- [X] T027 游戏内验证（AI 自动验证，容器，quickstart B）：**临时**在 `ClientSetup` 里注册假提供者（钻石镐返回 `zuanshigao`、`zsg`，所有物品额外返回 `zzz`，模组 id 设为测试用的 `faketerms`）；依次做 B1 提供者的词生效、B2 `zzz` 全部命中、B3 工具区高亮与帧率无可见下降、B4 语法、B5 改配置 `faketerms=GENERIC` 不重启生效与改回 `FULL`、B7 日志（注册 INFO、无 WARN；另用抛异常的临时提供者验证只 WARN 一次）；**验证后还原临时修改**，`git status` 确认 `src/main` 无残留，再做 B6（没有提供者时 `zsg` 不命中、行为同 042、日志无新增）；每项写实际验证了什么，做不了的写"未验证"；AI 自动验证的勾选规则见 Format（有明确证据可勾，证据不足只备注）——**AI 自动验证已做**（临时假提供者，没有验证任何真实模组；B1～B7 的证据见 DELIVERY.md；累计 20 次异常停用只有单元测试证据；帧率未测；`zzz -zsg` 不能证明排除，排除只有单元测试证据）
- [X] T028 度量（宪法 VI，DELIVERY）：400 格背包、返回 5 个词的假提供者，已缓存与未缓存两种情况下一次查询重算的耗时；400 格全是装满物品的潜影盒（最坏的数据组件）时的重算耗时（research D3 的风险）；耗时太大则改 `StackKey` 为只含少数关键组件的白名单键（不改 API）并重测；度量不了的写"未度量"，不声称达标——**部分度量**：400 格 5 个词冷约 5.2 ms、热约 0.41 ms（单元测试）；装满潜影盒的最坏情形**未度量**
- [X] T029 `specs/043-search-terms-provider-api/DELIVERY.md`（中文）：基线与完成后的类数与用例数、新增测试类、与 plan/tasks 的偏离、**每个游戏内验证项实际验证了什么/未验证什么**（**明确写：用的是临时假提供者，没有验证任何真实模组**）、已知限制（提供者只增加词；含空格的词只能被整段查询命中；工具区每帧取词依赖缓存；大组件物品的哈希成本；真机与 Windows/Linux 未验证）、度量结果或"未度量"、给 044 的提醒（`@`、`#`、`/` 前缀归 044，与提供者无关；`SearchContext.matcherFor` 现在是"当前语言名 + 注册名 + 提供者词"）
- [X] T030 文档同步：`docs/plan/backpack-search.md` 的状态（043 已实现与自动验证、等用户确认）；`docs/plan/README.md` 对应行；042 DELIVERY 里"给 043 的提醒"保持与最终实现一致
- [X] T031 最终检查：`./gradlew compileJava test` 全部通过；`git status` 确认无临时修改残留、没有新增运行时依赖、`neoforge.mods.toml` 与协议号 17 未动；对照宪法 I～VII 复查一遍并在 DELIVERY 记结论；**不提交、不打 tag**，等用户要求
- [X] T032 用户确认：用户在真机上用一个真实提供者（或自备的测试提供者）核对搜索效果、`modLevels` 开关与日志；**只有用户明确确认才勾**——**用户已确认（2026-11-12）：没有问题**

---

## Dependencies & Execution Order

- **Phase 2** 的 T002～T005 互相独立可并行；T006 依赖它们；T007 依赖 T002～T005；T008 收尾。
- **US1（P1，MVP）** 依赖 Phase 2：T009 → T010 → T011/T012；T013、T014 可并行，依赖 T006；T015 收尾。
- **US2** 依赖 US1（要有接入点才能证明"没有提供者时不变"）；T016、T017 可并行。
- **US3** 的实现已在 T006，T018、T019 只补验证与对照；可与 US2 并行。
- **US4** 依赖 US1 的 `SearchTerms`：T020 → T023；T021、T022 可并行。
- **US5** 依赖 US1～US4 的最终签名与数值：T024、T025 可并行，T026 收尾。
- **Phase 8** 依赖全部用户故事；T027 先于 T028/T029；T032 在最后，由用户完成。

### Parallel Opportunities

- T002～T005 四个小类并行；T013/T014、T016/T017、T021/T022、T024/T025 各成对并行。

## Implementation Strategy

- **MVP = Phase 2 + US1 + US2**：有提供者时词生效、没有提供者时与 042 一致；之后是容错的补验证（US3）、`modLevels`（US4）、文档与契约对照（US5）。
- 先纯逻辑后接入：T008 通过后才动客户端与 API 类，减少对游戏的触碰面。
- 假提供者只在容器里临时存在，任何时候 `src/main` 都不得留下它。
