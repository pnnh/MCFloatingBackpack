# Feature Specification: Curios 后背槽兼容

**Feature Branch**: `033-curios-back`

**Created**: 2026-11-09

**Status**: Draft

**Input**: 用户描述："允许浮动背包放到 Curios 的后背槽位，放置后和放在玩家胸甲上效果是一样的，都认为是背在了身上。"整体规划与决策见 `docs/plan/worn-backpack-compat.md`；032 已经把"背着的背包"做成有固定顺序的来源列表（目前只有胸甲），本 feature 把 Curios 的 `back` 槽作为第二个来源加在胸甲后面。

## 背景与已确认的决策

032 之后：背上模型的渲染与按 B / 面板的服务端操作 / 自动拾取都走同一条规则——**主手 → 副手 → 背着的第一只 → 物品栏**；"背着的"是一个按固定顺序排好的来源列表，只取第一只。目前列表里只有胸甲。

已确认的决策（来自用户，不重新讨论）：

1. **只做 Curios 的 `back` 槽**，不做 `body`、`curio`、`charm` 等其它槽。
2. **按官方 Curios 9.x 做**（官方 1.21.1 版本 `9.5.1+1.21.1`，要求 NeoForge ≥ 21.1.60，已在本项目的开发运行时里与我们的模组一起启动验证过）。Adorned（Curios 的 NeoForge 延续版，已停更，模组 id 仍是 `curios`，声称保持同样的 API 与标签结构）不单独适配、不单独验证。
3. **顺序与不拦截**：顺序是**胸甲 → Curios 后背槽**（034 再把 Accessories 加在最后）。同时在胸甲槽和 Curios 后背槽各放一只，只取顺序里的第一只（胸甲），Curios 里的那只就是普通物品（不画，按 B 也不会打开它）。不拦截、不数数、不去重。手里拿着的背包仍然优先于背着的。
4. **尊重 Curios 的"隐藏渲染"开关**：Curios 后背槽里第一只所在的格子被玩家设为不渲染，就不画，但仍算"背着的"，按 B 照常打开。**只看真实栈，不看装饰（cosmetic）栈**。
5. **Curios 的后背槽由我们无条件提供**：Curios 官方包只定义槽位、不给任何实体分配槽位，配置项 `slots` 默认也是空——只装 Curios 时玩家一个 Curios 槽都没有。所以我们自带一份数据文件，给玩家一个 `back` 槽，**装了 Curios 就能用**。不用配置控制这份数据：同一个槽 id 在不同数据文件里重复时 Curios 只保留一个，不会叠加成两个槽，所以对已经配置过后背槽的整合包没有副作用；代价是没配置过后背槽的整合包的玩家会多出一个槽，接受。
6. **`modLevels`（020）对 `curios` 的含义**：`GENERIC` / `OFF` 时，我们为 Curios 写的代码不运行（读取槽位、写回、隐藏渲染开关），所以 Curios 槽里的背包不算"背着的"；数据（标签、槽位分配）始终存在，所以背包仍能放进槽位，只是当普通物品。**已知限制**：`modLevels` 是客户端配置，专用服务器读不到、按"生效"处理，所以这个开关只对单人游戏与局域网房主成立。
7. Curios 对没注册渲染器的物品什么都不画（已读源码确认），所以 **Curios 一侧不需要任何渲染抑制代码**；背上的模型始终由我们自己的渲染层画（只画第一只）。

由我按已有惯例设计的部分（可在 plan 阶段微调）：

- 来源的顺序号取 **10**（胸甲 0，034 的 Accessories 将是 20）；它在线路上的序号是 `BackpackRef.Source` 末尾追加的新常量，已有序号不变，**不新增网络包字段，协议版本仍是 16**。
- 一个引用指向"Curios 后背槽里的第 N 格"，N 是该槽里的下标；该槽有几格就看几格（整合包可能把后背槽设成多格），按下标从小到大，取第一只。
- **未激活的格子**（Curios 里某些槽位可以被设为未激活）不算。
- 来源是否生效在**每次查找时**判断（读 `modLevels`），所以改了配置立刻生效。
- 兼容类放在 `compat/curios/`，**Curios 没装时不被加载**，我们的模组照常工作（宪法 VII）。

## Out of Scope（本阶段不做）

- Accessories（034）、Curios 的其它槽位（`body`、`curio`、`charm` 等）、装饰栈。
- 渲染抑制（Curios 一侧不需要）、拦截第二只/第三只、去重与计数。
- Adorned 的单独验证；给 `modLevels` 的 GENERIC / OFF 加专用服务器支持。
- 新贴图、新配方、新设置项、新语言键、新网络包字段、协议改动。
- 改 Shift+右键换到胸甲槽的行为（`FloatingBackpackItem` 不动）。

## 已查证的事实（读 Curios 9.5.1 的 jar 与源码包，以及本仓库源码；规格要尊重）

- Curios 的槽位定义在 `data/curios/curios/slots/*.json`，官方包里有 `back`、`belt`、`body`、`bracelet`、`charm`、`curio`、`hands`、`head`、`necklace`、`ring` 十个；`back` 的验证器是 `curios:tag`（物品必须在 `curios:back` 物品标签里才能放进去）。
- **没有任何实体分配槽位**：jar 里没有 `curios/entities`，配置 `slots` 默认为空；实体的槽位来自 `data/<命名空间>/curios/entities/<名字>.json`（所有命名空间的这类文件会合并；同一个槽 id 重复时保留最后一个、不叠加；文件名以 `_` 开头的被跳过）。
- `CuriosApi.getCuriosInventory(LivingEntity)` 返回 `Optional<ICuriosItemHandler>`；`getStacksHandler("back")` 得到该槽的处理器，`getStacks()` 是真实栈（`IDynamicStackHandler`），`getCosmeticStacks()` 是装饰栈，`getRenders()` 是每格是否渲染，`getActiveStates()` 是每格是否激活。Curios 自己的渲染层 `CuriosLayer` 就在**客户端**对任意实体读取这些数据。
- `CuriosLayer` 对没注册 `ICurioRenderer` 的物品什么都不画。
- Curios 在 Modrinth 上发布了 `curios-neoforge-9.5.1+1.21.1-api.jar`（仅 API）与 `-sources.jar`；Curios 的 Maven 仓库是 `https://maven.theillusivec4.top/`。
- 本项目的 `./gradlew runClient` / `runServer` 现在默认带着 Curios 9.5.1、Accessories 与 owo-lib（`localRuntime`）；2026-11-09 已实际启动确认它们与我们的模组一起加载无错误。
- `CompatConfig.policy().specificAdaptation("curios")` 在客户端读 `modLevels`，配置未加载（专用服务器）时返回 `DEFAULT`（全部 FULL）。
- 032 的来源接口 `WornSource<LivingEntity, ItemStack>` 与 `WornBackpacks.register(...)`、`BackpackRef.Source` 的 `worn` 标志已就位；`BackpackQuickTransferPayload.holderOf` 对 worn 来源一律映射到 `Holder.CHEST`。

## 尚未验证（plan 阶段先查证；验证前不得声称已满足）

- 编译用的 API jar 在 Gradle 里能否直接从 `maven.theillusivec4.top` 解析（`top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1:api`），以及 `compileOnly` 之后 Curios 没装时不会触发 `NoClassDefFoundError`。
- `data/curios/tags/item/back.json` 的格式与路径（1.21 的 `tags/item`），以及 `curios/entities` 文件的确切字段（`entities`、`slots`）。
- 写回时 Curios 对"槽内物品变化"的处理：是否发出变化事件、是否同步给客户端、背包内容每次点击都变时的开销与副作用；是否必须在主线程。
- 客户端对"别的玩家"的 Curios 数据是否总是同步到位。
- 在 Curios 界面里，玩家能否把浮动背包放进 `back` 槽（标签生效）、取出、与槽内别的物品交换；Curios 的"物品被放进/取出"校验是否会拒绝我们的背包。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 把背包放进 Curios 后背槽，就是背着 (Priority: P1)

**Independent Test**: 装了 Curios 的游戏里，把浮动背包放进 Curios 的后背槽：背上画出背包模型，按 B 打开它，面板所有功能照常，内容保存后不丢。

**Acceptance Scenarios**:

1. **Given** 装了 Curios 的新玩家，**When** 打开 Curios 界面，**Then** 有一个"后背"槽（我们提供的槽位分配）。
2. **Given** 背包在物品栏，**When** 把它放进 Curios 后背槽，**Then** 放得进去（标签生效），背上画出这只背包的模型。
3. **Given** 背包在 Curios 后背槽、手里没有背包，**When** 按 B，**Then** 打开它；放入、拿出、Ctrl 快速转移、标签管理器、垃圾桶、自动拾取、升级都照常。
4. **Given** 在面板里改了背包内容，**When** 退出重进，**Then** 内容还在（写回到 Curios 的槽里）。
5. **Given** 把背包从 Curios 后背槽取出，**Then** 背上立刻不再画它，按 B 回到其它位置的查找。
6. **Given** 在 Curios 后背槽里放别的物品（有标签的别的饰品），**Then** 行为与放别的饰品一样，不受影响。

---

### User Story 2 - 顺序：胸甲在前，手里最优先 (Priority: P1)

**Independent Test**: 胸甲槽与 Curios 后背槽各放一只：背上画胸甲那只，按 B 打开胸甲那只；手里拿着一只则按 B 打开手里的。

**Acceptance Scenarios**:

1. **Given** 胸甲槽与 Curios 后背槽各有一只，**Then** 背上画胸甲那只，按 B 打开胸甲那只；Curios 里的那只是普通物品。
2. **Given** 把胸甲那只摘掉，**Then** 背上改画 Curios 里的那只，按 B 打开它。
3. **Given** 手里拿着一只、Curios 后背槽里还有一只，**Then** 按 B 打开手里的；背上仍画 Curios 里的那只。
4. **Given** Curios 后背槽有多格（整合包设置），第 1 格是别的物品、第 2 格是背包，**Then** 取第 2 格的背包；两格都是背包则取第 1 格。

---

### User Story 3 - 隐藏渲染开关与未激活的格子 (Priority: P2)

**Independent Test**: 在 Curios 界面里把后背槽设为不渲染：背上不画，但按 B 仍打开它。

**Acceptance Scenarios**:

1. **Given** Curios 后背槽里的背包所在格子被设为"不渲染"，**Then** 背上不画；按 B 仍打开它；它仍然排在胸甲之后（胸甲里有一只时仍是胸甲那只）。
2. **Given** 把渲染开关打开，**Then** 背上立刻画出。
3. **Given** 一个未激活的格子里有背包，**Then** 它不算背着的。
4. **Given** 背包放在装饰栈里，**Then** 不算（只看真实栈）。

---

### User Story 4 - `modLevels` 与没装 Curios (Priority: P2)

**Independent Test**: 把 `curios` 配成 GENERIC：Curios 后背槽里的背包不再算背着的（只在单人游戏与局域网房主上成立）；没装 Curios 时一切与现在一样。

**Acceptance Scenarios**:

1. **Given** 单人游戏，`modLevels` 里 `curios=GENERIC`（或 OFF），**Then** Curios 后背槽里的背包不画、按 B 不打开它，但仍能放进槽位（数据还在）；改回 FULL 立刻恢复。
2. **Given** 没装 Curios，**Then** 一切与现在一样，我们的模组正常加载，没有任何与 Curios 有关的错误或日志噪音；数据文件无害。
3. **Given** 专用服务器，**Then** `modLevels` 不生效（按 FULL 处理），这是已知限制。
4. **Given** 装了 Curios 但某个玩家的客户端没装（或相反）：不崩溃；没装的一端看不到那只背包。

---

### Edge Cases

- Curios 被移除（世界里槽里还有背包）：我们不读它，物品随 Curios 的数据留在原处；不崩溃。
- 同一个槽 id 被整合包的数据文件重复分配：Curios 只保留一个，不叠加。
- 客户端发来的引用指向不存在的格子或未加载的来源：读到空栈，服务端按现状拒绝或关闭会话。
- 在 Curios 界面里把背包放进槽、同时面板开着：面板按现状处理（背包被拿走则会话关闭）。
- 玩家死亡：Curios 的掉落规则决定背包去向，我们不改。
- Curios 的 `back` 槽被整合包设为 0 格：没有背包可找，与没有槽一样。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 浮动背包的 5 个档位都在 `curios:back` 物品标签里（一份数据文件，Curios 没装时被忽略）；因此 Curios 的后背槽接受它们。
- **FR-002**: 提供一份 `curios/entities` 数据文件，把 `back` 槽分配给玩家（`minecraft:player`）；无条件提供，不受任何配置控制；同一个槽 id 重复时 Curios 只保留一个。
- **FR-003**: 在 032 的来源列表里增加一个 **Curios 来源**：顺序号 10（胸甲之后），线路序号是 `BackpackRef.Source` 末尾追加的新常量；已有序号、已有网络包字段、协议版本（16）都不变。
- **FR-004**: Curios 来源读取玩家 `back` 槽的**真实栈**，按格子下标从小到大；跳过未激活的格子；对任意玩家实体都可用（客户端渲染别的玩家）；Curios 数据缺失、格子越界或任何异常都返回空栈，不抛。
- **FR-005**: Curios 来源能把改过的栈**写回**同一个格子，使背包内容的每次变化都保存在 Curios 的槽里（退出重进不丢）。
- **FR-006**: Curios 来源回答"该格子的渲染开关是否关闭"；关闭时渲染层不画，但它仍然是背着的第一只（按 B 照常打开，且后面的来源不会顶替它）。
- **FR-007**: 来源是否生效在**每次查找时**读取 `modLevels` 里 `curios` 的级别：只有 FULL 才生效；GENERIC / OFF 不生效，Curios 槽里的背包不算背着的。
- **FR-008**: 兼容类放在 `compat/curios/`，只在 Curios 已加载时才被注册与引用；没装 Curios 时模组正常工作、不触发任何 Curios 类的加载错误。构建里 Curios 只作为 `compileOnly` 依赖，不进发布 jar。
- **FR-009**: 背上的模型始终由我们自己的渲染层画（只画 `WornBackpacks.first` 的那一只）；不注册任何 Curios 渲染器。
- **FR-010**: 按 B / 面板的所有服务端操作 / 自动拾取对 Curios 里的背包与对胸甲里的背包行为一致；Ctrl 快速转移对 Curios 里的背包同样不会把背包转进它自己（它不在物品栏 0～35，没有要保护的下标）。
- **FR-011**: 不新增网络包、语言键、贴图、配方、设置项；`modLevels` 的配置键沿用现有的 `curios`。
- **FR-012**: 服务端对客户端引用的处理不变：始终用 `resolve` 重新解析，只在与解析结果完全一致时才采用声称的引用。

### Key Entities

- **Curios 后背槽来源**：032 的 `WornSource` 的一个实现，顺序号 10。
- **引用**：`BackpackRef(CURIOS, 格子下标)`。
- **数据文件**：`curios:back` 标签（5 个档位）、玩家的 `back` 槽分配。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 装了 Curios 的玩家把背包放进 Curios 后背槽，**两步以内**（打开 Curios 界面、放进去）就背着了，背上有模型，按 B 能打开。
- **SC-002**: 面板的所有功能在 Curios 里的背包上与在胸甲里的背包上**行为一致**（放入、拿出、快速转移、标签管理器、垃圾桶、自动拾取、升级、编号、快照），内容保存后不丢。
- **SC-003**: 顺序规则在真实游戏里与 032 的单元测试一致：胸甲 → Curios；手里最优先；隐藏渲染仍算背着的。
- **SC-004**: 没装 Curios 时，游戏日志里没有任何与 Curios 兼容有关的错误；自动化测试全部通过。
- **SC-005**: 协议版本仍是 16；图集仍是 2048×1024；数据文件（标签、槽位分配）内容由单元测试检查。

## Assumptions

- 玩家用的是官方 Curios 9.x（`9.5.1+1.21.1` 或更新的 9.x）。
- Curios 在服务端与客户端都装着；只有一端装了时，没装的一端看不到那只背包，不崩溃。
- Curios 对"槽内物品变化"的处理足以让我们写回的栈被保存与同步（plan 阶段验证；不行就补一个显式的同步调用）。
- `modLevels` 在专用服务器上不生效是可以接受的已知限制（用户 2026-11-09 已接受）。
