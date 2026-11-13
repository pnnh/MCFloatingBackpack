# Feature Specification: 灯笼光源（背包里的灯笼让玩家发光，靠 LambDynamicLights）

**Feature Branch**: `052-lantern-light`

**Created**: 2026-11-13（2026-11-13 改方案：从"服务端光源方块"改为"接 LambDynamicLights"，见 Clarifications）

**Status**: Draft

**Input**: 用户（2026-11-13）："支持把灯笼放到下方工具区，允许普通灯笼或其它原版灯笼（或除火把外的其它光源），彼此互斥只能放 1 个。放置后可以实现移动光源效果。"——最初做成服务端光源方块；试玩后用户觉得"一格一格跳、忽明忽暗"，决定"**采用和旅行者背包同样的方案：要求玩家另外安装 LambDynamicLights，不安装则不发光；我们背包模组本身不处理这个**"。

## Clarifications

### Session 2026-11-13（用户已确认）

- Q: 技术路线？ → A: **接 LambDynamicLights（LDL）**，与 Traveler's Backpack 同一做法：本模组声明 `lambdynlights:initializer` 入口，给 LDL 注册一个"玩家的自定义亮度"（读背包下方区的光源）。**本模组不自己放方块、不自己做任何光照**；玩家没装 LDL 就不发光。LDL 是客户端模组、只在客户端算光，所以**不需要服务端代码、不需要网络包**。
- Q: 之前做的服务端光源方块方案？ → A: **整个删除**（放方块、世界数据文件、清理残留、跟随器等）。该方案的问题：光只能按整格移动，加上服务端异步算光与客户端自己预测光的差异，走路时一格一格跳、有明暗抖动；LDL 按精确的小数位置算光，平滑得多。
- Q: 哪些光源？ → A: 灯笼、灵魂灯笼、海晶灯、荧石、菌光体、蛙明灯（三色）、末地烛、南瓜灯；**不含**火把（"像要把背包点着"）、蜡烛、营火、岩浆。彼此在下方区互斥，只能放 1 个。
- Q: 亮度？ → A: **每种光源固定亮度，等于原版同一方块的亮度**（灯笼、荧石、海晶灯、菌光体、南瓜灯、蛙明灯 15，末地烛 14，灵魂灯笼 10；原版火把是 14），光源之间的关系与游戏里一致；不做用户配置。（光不再改世界，所以不存在融冰融雪的问题。）
- Q: 颜色？ → A: 没有（LDL 只有 0～15 的亮度）。
- Q: 没装 LDL 怎么提醒？ → A: README 写明；并且玩家第一次把光源放进下方区时，聊天栏提示一次"光源需要另外安装 LambDynamicLights 才会发光"（每次游戏会话一次，已装 LDL 则不提示）。
- Q: 老背包下方区里已经放着灯笼？ → A: 装了 LDL 的玩家升级后开始发光，写进发布说明。

## 背景

三个工具区本来就接受任何物品，灯笼本来就能放进下方区，只是没有效果。本 feature 让"下方区里的光源"通过 LambDynamicLights 变成"跟着玩家走的光"。按 B 会打开的那只背包（手 → 背 → 物品栏）的下方区里的第一个光源起作用；其它背包不参与，避免多个背包互相争抢。

LDL 每个客户端 tick 对它认识的每个玩家询问亮度（我们的 `EntityLuminance`）；背着的背包（胸甲位、Curios、Accessories）别的玩家也看得到，所以别人的光在装了 LDL 的客户端上同样会亮；拿在手上、放在物品栏里的背包别的玩家的客户端读不到，所以只有自己能看到自己的光（与 Traveler's Backpack 一样的限制）。

## 光源与亮度（与原版一致，只改这张表）

| 光源 | 亮度 | 说明 |
|---|---|---|
| 灯笼、荧石、海晶灯、菌光体、南瓜灯、蛙明灯（橙、紫、绿三色） | **15** | 原版 15 |
| 末地烛 | **14** | 原版 14（与火把同级） |
| 灵魂灯笼 | **10** | 原版 10（与灵魂火把同级） |

- 亮度**只由这张表决定**，不受任何配置影响；数值与 1.21.1 `Blocks.java` 里同一方块的发光值一致（单元测试逐个断言，并断言"灵魂灯笼比灯笼暗、不比末地烛亮"这类关系不被调反）。
- 物品标签 `mcfloatingbackpack:tool_lights` 决定"哪些物品算光源"，默认含上表 10 种物品；数据包与别的模组可以往标签里加物品，**不在表里的成员**取它自己方块的发光值（FR-004）。
- 火把、蜡烛、营火、岩浆等不在默认标签里，放进下方区没有效果。

## 已查证的事实

- 三个工具区接受任何物品，且已有"某种工具每个区至多 1 个"的规则模式（`ToolAreaRules`、`TankPlacement`）。
- LDL 4.8.11+1.21.1 支持 NeoForge，客户端模组；API 是 `dev.lambdaurora.lambdynlights:lambdynamiclights-api:4.8.11+1.21.1`（maven.gegy.dev，**mojmap 分类**的 jar 才与 NeoForge 的名字对得上）。
- **Traveler's Backpack（本仓库开发运行里就有它的 jar）正是这样做的**：`BackpackDynamicLight implements DynamicLightsInitializer`、`neoforge.mods.toml` 里 `[modproperties.<id>."yumi:entrypoints"]` 声明 `lambdynlights:initializer`、对 `EntityType.PLAYER` 注册一个自定义 `EntityLuminance`（读穿着的背包）。本 feature 照这个形状做。
- LDL 在 Sodium 下也能用（它改的是原版 `LevelRenderer.getLightColor`，Sodium 的光照数据也调这个方法）；与 Iris 光影包的搭配只能在真机上看。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 灯笼放进下方区，光跟着我走（装了 LDL）(Priority: P1)

装了 LDL 的玩家把灯笼放进背包下方区，走到黑暗处，身边就亮了，走到哪亮到哪；拿走灯笼光就没了。

**Independent Test**: 开发运行（带 LDL）里造一只下方区带灯笼的背包，夜里站在平地上，画面明显变亮；清掉背包后变暗。

**Acceptance Scenarios**:

1. **Given** LDL 已安装、当前背包的下方区有一个灯笼，**Then** 玩家身边亮起（亮度 15）；移动时光跟着，按 LDL 的方式平滑变化。
2. **Given** 灯笼在左区或右区，**Then** 没有效果（放得下但不生效，也不计入互斥）。
3. **Given** 多只背包，**Then** 只看"按 B 会打开的那只"；别的背包里的灯笼不发光。
4. **Given** 没有装 LDL，**Then** 什么都不发生（不报错、不崩溃）；本模组的其它功能不受影响。
5. **Given** 配置 `compat.modLevels` 里 `lambdynlights=GENERIC` 或 `=OFF`，**Then** 本模组不再给 LDL 提供亮度（宪法 VII 的"模组专属适配"开关）。

---

### User Story 2 - 八类光源，各有亮度，彼此互斥 (Priority: P1)

下方区里可以是上述光源中的任意一个，只能同时放一个，亮度按上表。

**Independent Test**: 纯逻辑测试覆盖亮度表与互斥规则；游戏里放进去后亮度由 LDL 决定，这里只验"被拒与提示"。

**Acceptance Scenarios**:

1. **Given** 下方区已有一个光源，**When** 玩家再放另一个（不论种类），**Then** 被拒并提示"这个背包的下方区已经有一个光源了"；**同格替换**允许。
2. **Given** 保存时已经有多个光源的旧背包，**Then** 都保留，不能再加；起作用的是**按格子顺序的第一个**。
3. **Given** 火把、蜡烛、营火等不在标签里的发光物，**Then** 放得进（工具区接受任何物品）但没有光。
4. **Given** 数据包往标签里加了一个别的光源，**Then** 它也算光源，亮度取它自己方块的发光值。

---

### User Story 3 - 没装 LDL 时被告知该装什么 (Priority: P2)

没装 LDL 的玩家第一次把光源放进下方区时，收到一条聊天提示，说明需要另外安装 LambDynamicLights。

**Acceptance Scenarios**:

1. **Given** 没装 LDL，**When** 玩家把光源放进下方区（被接受），**Then** 聊天出现一次提示；同一次游戏会话里不再重复。
2. **Given** 装了 LDL，**Then** 不提示。

---

### User Story 4 - 数据与版本兼容 (Priority: P3)

没有任何新的存储数据、网络包、世界数据；协议号与数据修订不变；本模组对 LDL 是可选依赖。

**Acceptance Scenarios**:

1. **Given** 本 feature，**Then** `DataRevision.CURRENT` 仍为 3，协议号仍为 19，没有新的网络包、物品、数据组件、配置项、世界数据文件。
2. **Given** 发布的 jar，**Then** 不含 LDL；`neoforge.mods.toml` 里 LDL 是 `optional`、`side="CLIENT"`；LDL 的 API 只在 `compileOnly`，运行时依赖只在 `localRuntime`。

---

### Edge Cases

- **别人的光**：别的玩家背着的背包（胸甲位、Curios、Accessories）在装了 LDL 的客户端上会让该玩家发光；拿在手里、放在物品栏里的读不到（客户端没有同步别人的物品栏）。
- **LDL 自己的设置**（动态光源模式、更新频率、关掉）归 LDL 管；本模组不管。
- **别的动态光源模组**（RyoamicLights、Sodium Dynamic Lights 等）：与 LDL 互不兼容，是它们自己的问题。
- **光影包**：Complementary 等光影包的手持光只认主手物品，不会认我们的背包；LDL 的光是烤进网格顶点光照里的，光影包读到的就是这个值；具体观感以真机为准。
- **旧版本 LDL**（4.x 以前）：不支持，`versionRange` 写 `[4,)`。
- **降级**：不支持（口径不变）；本 feature 没有新存储数据。

## Requirements *(mandatory)*

### Functional Requirements

**光源与亮度**

- **FR-001**: MUST 有物品标签 `mcfloatingbackpack:tool_lights`，默认含：`minecraft:lantern`、`soul_lantern`、`sea_lantern`、`glowstone`、`shroomlight`、`ochre_froglight`、`verdant_froglight`、`pearlescent_froglight`、`end_rod`、`jack_o_lantern`（原版物品 id 用普通字符串，同现有扩展标签的写法）。
- **FR-002**: MUST 有一张**固定的亮度表**（上表），亮度只由它决定，数值 MUST 等于原版同一方块的发光值，光源之间的大小关系 MUST 与原版一致；MUST NOT 有用户配置项。
- **FR-003**: 火把、蜡烛、营火、岩浆、红石火把等 MUST NOT 在默认标签里。
- **FR-004**: 标签里不在亮度表里的物品 MUST 取"其方块自己的发光值"（1 到 15）；它不是方块物品时 MUST 取 8。

**放置与互斥**

- **FR-005**: 光源 MUST 只在**下方工具区**生效；左、右区放得下但不生效，也不计入互斥。
- **FR-006**: 下方区里光源物品（标签成员）MUST 至多 1 个，不论种类；服务端校验（客户端预览同口径）；被拒时 MUST 给玩家一条提示 `message.mcfloatingbackpack.light.duplicate`（中英文）；同格替换 MUST 允许；保存时已多于 1 个的旧背包 MUST 保留、不能再加（沿用 `ToolAreaRules` 的口径）。
- **FR-007**: 一个背包的下方区若有多个光源，**起作用的 MUST 是按格子顺序的第一个**。
- **FR-008**: 这条互斥 MUST 与"底部区最多一个标签管理器"的现有规则互相独立。

**LambDynamicLights**

- **FR-009**: MUST 通过 LDL 的 `lambdynlights:initializer` 入口（`neoforge.mods.toml` 的 `[modproperties."${mod_id}"."yumi:entrypoints"]`）向 LDL 注册对 `EntityType.PLAYER` 的自定义 `EntityLuminance`；它读"按 B 会打开的那只背包"（`BackpackRef.resolve`）的下方区第一个光源，返回亮度，没有则 0；MUST 只读背包、不改写任何东西。
- **FR-010**: 本模组 MUST NOT 自己放置任何方块、MUST NOT 自己做任何光照、MUST NOT 有 mixin；MUST NOT 有服务端代码或网络包。
- **FR-011**: LDL MUST 是**可选**依赖（`type="optional"`、`side="CLIENT"`、`versionRange="[4,)"`）；其 API 只在 `compileOnly`（mojmap 分类，`transitive = false`），运行时的 LDL 只在 `localRuntime`；LDL 的类型只出现在 `compat/lambdynlights/`；没装 LDL 时这些类 MUST NOT 被加载（主代码不引用它们）。
- **FR-012**: `compat.modLevels` 里 `lambdynlights` 为 `GENERIC` 或 `OFF` 时，`EntityLuminance` MUST 返回 0（宪法 VII：模组专属适配可关）。

**提醒**

- **FR-013**: 没装 LDL 的玩家第一次把光源放进下方区（被接受）时，MUST 在聊天栏提示一次 `message.mcfloatingbackpack.light.needs_mod`（中英文）；同一游戏会话内不重复；装了 LDL 则不提示。README 与 DELIVERY MUST 写明"需要另外安装 LambDynamicLights（1.21.1 的 4.x 版本）"。

**数据与兼容**

- **FR-014**: MUST NOT 新增背包存储字段、数据组件、物品、网络包、配置项、世界数据文件；`DataRevision.CURRENT` 仍为 3，协议号仍为 19；011～051 的测试不改（除非写死了某条被本 feature 改变的规则）。

**测试与文档**

- **FR-015**: 亮度表（各光源值等于原版、关系不反、FR-004 的取值）、互斥规则 MUST 有纯逻辑单元测试；标签、语言键用资源测试；接线（入口声明、可选依赖、只在 compat 包引用 LDL、没有 mixin 与网络包）用源码文本检查；`DependencyRulesTest` 通过；游戏里验证 LDL 装与不装两种情况（宪法 V）。
- **FR-016**: 文档 MUST 更新：`README.md`/`README_CN.md`（灯笼光源一节，含"需要另外安装 LambDynamicLights"）、本 feature 的 `DELIVERY.md`。

### Key Entities *(include if feature involves data)*

- **光源物品（Tool light）**：`mcfloatingbackpack:tool_lights` 标签的成员；有一个固定亮度。
- **玩家亮度（Player luminance）**：LDL 对玩家询问时，本模组返回的 0～15 的数。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 亮度表的 10 种默认光源，每种的亮度等于原版同一方块的发光值（单元测试逐个断言）。
- **SC-002**: 带 LDL 的开发运行里，夜里带灯笼背包与不带相比画面明显变亮（截图对比）；没带 LDL（`-PcleanRuntime`）时游戏正常运行、无任何来自本模组的错误、世界不变亮。
- **SC-003**: 第二个光源被拒并有提示；没装 LDL 时第一次放入有一次提示，装了 LDL 没有。
- **SC-004**: `./gradlew compileJava test` 通过；`DependencyRulesTest` 通过；数据修订与协议号测试不变；011～051 的测试不改而通过（除写死的规则）。

## Assumptions

- 亮度表与原版一致；颜色另议。
- "按 B 的背包"的口径与管子、自动拾取一致。
- LDL 的平滑程度、与 Iris 光影包的搭配以真机为准；容器里（软件渲染）只验"亮不亮"。
- 别的玩家看不到拿在手里或放在物品栏里的背包带来的光，写进已知局限。
