# Research: 搜索词提供者接口

每条：Decision / Rationale / Alternatives。凡写"已查证"的，都是读本仓库源码或 NeoForge 21.1.251 的 jar 得出的；"待实现时验证"的在 DELIVERY 里如实记录。

## D1 接入点：只改 `SearchContext.matcherFor`

- **Decision**: 提供者的词在 `SearchContext.matcherFor(stack)` 里并入 `TextMatcher.ofTexts`；没有生效提供者时走与 042 逐字相同的路径。
- **Rationale（已查证）**: `matcherFor` 被两处调用——`SearchContext.SOURCE.matcherOf`（查询变化时重算主区域，约 ≤400 格一次）与 `toolMatches`（工具区命中框，`FloatingPanelRenderer` 每帧对每个工具格调用）。`SearchQuery`/`SearchMatcher`/`SearchResults` 只依赖 `TextMatcher.contains`，所以不用动。
- **Alternatives**: 新的 `TextMatcher` 实现类（组合模式）——多一层类型，没有收益，因为词是简单的小写子串；在 `SearchMatcher` 里加"提供者"参数——污染纯逻辑。

## D2 逻辑放在通用泛型类里，API 包只做薄门面

- **Decision**: `pnnh.mcfloatingbackpack.search.ProviderRegistry<S>`（泛型 `S`，无 Minecraft 依赖）承载注册、归一、容错、缓存；生产单例 `SearchTerms` 把 `S` 定为 `ItemStack`；`api.BackpackSearchApi` 只校验参数并委托。
- **Rationale**: 项目约定纯逻辑不依赖 Minecraft 类以便单元测试（构造 `ItemStack` 需要 Minecraft 引导）。泛型让测试用 `String` 当 `S`。注册必须在通用代码里：别的模组的构造器在专用服务器上也会调用注册，不能触到客户端类（`@OnlyIn`）。
- **规格的修正**: FR-001 原写"API 包不引用本模组其它包的类型"，与"门面委托给实现"矛盾，已改为"公开签名只含 JDK 与 Minecraft 类型、不引用客户端类"。
- **Alternatives**: 实现直接写进 `BackpackSearchApi`——测试要带 `ItemStack`；把注册表放客户端包——服务端上注册会 `NoClassDefFoundError`。

## D3 缓存键与"每次调用的 `ItemStack`"

- **Decision**: 缓存键 `StackKey(Item, DataComponentPatch)`（补丁为空时只按物品，省去哈希）；调用提供者时传 `stack.copyWithCount(1)`。每个提供者一份 LRU，上限 2048 条（400 格背包加工具区加不同的组件变体仍有余量）。
- **Rationale**: 用户选择以 `ItemStack` 为参数，意图是药水、附魔书等"名字由数据组件决定"的物品。按"物品+组件补丁"缓存保留这个能力。`copyWithCount(1)` 让"提供者不得依赖数量"成为事实，也避免提供者改动玩家的堆叠（`ItemStack` 是可变的）。`DataComponentPatch` 有基于内容的 `equals/hashCode`（待实现时核对 1.21.1 的行为与对大组件的哈希成本）。
- **风险**: 带大组件的物品（装满的潜影盒、书与笔）哈希较贵。缓解：补丁为空时跳过；实现后在 DELIVERY 记录 400 格全是装满潜影盒时的重算耗时（SC-003），没测就写"未度量"；若太贵，再改为"物品 + 自定义名与药水/附魔等少数组件"的白名单键（不改 API）。
- **Alternatives**: 只按 `Item` 缓存——药水全部同一个词，丢掉用户要的能力；不缓存——每帧工具区调用、每键 400 次调用，提供者开销不可控。

## D4 为什么必须缓存：工具区每帧调用

- **Decision**: 提供者的结果一律走缓存；同一物品在一代缓存内最多被调用一次。
- **Rationale（已查证）**: 搜索打开时，`FloatingPanelRenderer` 每帧对每个工具区格子调用 `SearchContext.toolMatches` → `matcherFor`。042 里这只是取显示名与注册名，便宜；加上外部代码后必须有缓存，否则每帧 N 次外部调用。
- **Alternatives**: 在渲染层对每帧结果做记忆——要碰渲染代码，且仍要缓存提供者结果。

## D5 `modLevels` 在查询时应用，缓存不因配置失效

- **Decision**: 取词前用 `CompatConfig.policy().specificAdaptation(modId)`（只有 FULL 为真）判断；`GENERIC`/`OFF` 不调用提供者。缓存按提供者分开，`modLevels` 变化不清缓存。
- **Rationale（已查证）**: `CompatConfig.policy()` 在配置未改变时返回同一个对象（对 `MOD_LEVELS.get()` 做引用比较），每次调用只有一次引用比较，可以每帧用。配置被改后下一次调用自然拿到新策略，所以"不重启、下一次搜索起生效"（FR-006）不需要任何额外机制。宪法 VII 要求写明每一级含义：FULL 用提供者；GENERIC 不用；OFF 与 GENERIC 相同（这个接口没有"在对方界面上显示面板"的含义），已在规格与配置提示里写明。
- **Alternatives**: 注册时读一次级别——配置改动不生效；级别变化时清缓存——多出失效逻辑，且被关闭的提供者的缓存无害。

## D6 版本号：方法，不是常量

- **Decision**: `BackpackSearchApi.apiVersion()` 返回 1（方法），不是 `public static final int API_VERSION`。
- **Rationale**: `static final int` 初始化为字面量是编译期常量，调用方的编译器会把它的值内联进自己的字节码，运行时再读它永远是编译时的值，检查等于摆设。方法调用在运行时解析。规格里原写"整数常量 `API_VERSION`"，已改。
- **Alternatives**: 非常量的 `static final`（如 `Integer.valueOf(1)`）——能用但写法容易被"优化"回常量；注解携带版本——需要反射，调用方更麻烦。

## D7 资源重载时清缓存

- **Decision**: 在 `ClientSetup.register` 里用 `RegisterClientReloadListenersEvent`（已在 `neoforge-21.1.251-universal.jar` 里核对类存在，`net.neoforged.neoforge.client.event`）注册一个 `ResourceManagerReloadListener`，回调 `SearchTerms.onResourcesReloaded()` 清所有提供者的缓存。
- **Rationale**: 切换语言与 F3+T 都会触发客户端资源重载；提供者的词（按语言/资源生成）此后可能变化。本模组不关心词从哪里来，只在重载后丢掉旧的。
- **Alternatives**: 监听语言切换事件——漏掉资源包变化；不清缓存——重载后搜到旧词。

## D8 防御与容错的具体数值

- **Decision**: 每次返回最多 64 个词（超出的丢弃后几个）、单个词最长 128 字符（超长的整词丢弃，不截断）、空串与纯空白丢弃、`null` 视为空；每个提供者累计 20 次异常后本会话停用；WARN：同一提供者同一异常类只记一次，违规（超量/超长）每类一次且不计入停用。
- **Rationale**: 一个物品的各语言名、全拼、首字母、几个模糊写法通常不到 10 个词，64 留足余量；128 字符覆盖最长的全拼名字（"下界合金锭"之类远小于此）；整词丢弃而不是截断，因为截断会造出误导性的前缀。20 次对应"不是偶发错误，而是坏掉了"。
- **Alternatives**: 无上限——坏提供者能撑爆缓存与匹配时间；出错一次就停用——偶发异常（如某个物品的数据异常）会让整个提供者失效。

## D9 宪法是否补"公开 API"条文：不补

- **Decision**: 不改宪法；稳定性承诺写进契约与开发者说明。
- **Rationale**: 宪法 VII 管"本模组依赖别的模组"，此处的"别的模组依赖本模组"是第一次出现，只有一个 API 包、一个注册入口；补治理条文会在没有第二个案例时定规则。若以后出现第二个公开接口，届时评估补一条（MINOR 修订）。
- **Alternatives**: 现在补——规则无依据可校验，且会让 043 变成宪法修订 feature（范围蔓延，违反宪法 III）。

## D10 职责边界与给具体模组的草稿

- **Decision**: 本 feature 不引用、不推荐、不验证任何具体模组；之前起草的"给另一个模组的规格草稿"已从本 feature 移除。
- **Rationale**: 用户澄清：兼容的主动权在对方，本模组只公开 API；联动是否正常是对方的事。README 只写"其它模组可以通过公开接口增加可搜索的文字"。接口契约（`contracts/search-terms-api.md`）足以让对方独立接入。
- **Alternatives**: 保留草稿作参考——会让规格文件里出现与验收无关的、对具体模组的假设。

## D11 游戏内验证：容器里的临时假提供者

- **Decision**: 在容器自动验证里，对源码做**临时**修改，让 `ClientSetup` 注册一个假提供者（如对钻石镐返回 `zuanshigao`、`zsg`，对所有物品返回 `zzz`），验证实时过滤、工具区高亮、`modLevels` 开关（改 `options`/配置文件后再搜）后**还原**；DELIVERY 如实写"临时假提供者，未验证任何真实模组"。
- **Rationale**: 项目已有"对源码临时改动做验证再还原"的做法（023 的协议号临时修改）；不需要为验证在发布 jar 里留任何入口。
- **Alternatives**: `src/main` 里加调试入口——违反规格（假提供者不得进入发布 jar）；做一个独立的测试模组——为一个验证新增整个子项目。

## D12 注册时机与线程

- **Decision**: 注册表是通用静态结构，`ConcurrentHashMap`；注册可在任何线程、任何阶段（模组构造、`FMLClientSetupEvent`、之后）。读取（取词）只在客户端渲染线程；每个 entry 的缓存与健康记录用 `synchronized`，成本可忽略。
- **Rationale**: 别的模组的构造器线程不固定；不能要求它们在特定事件里注册。`ItemStack` 与 `DataComponentPatch` 的读取发生在渲染线程。
- **Alternatives**: 要求只在客户端启动完成事件里注册——把本模组的时序约束强加给调用方。
