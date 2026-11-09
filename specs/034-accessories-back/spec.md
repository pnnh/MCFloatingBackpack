# Feature Specification: Accessories 后背槽兼容

**Feature Branch**: `034-accessories-back`

**Created**: 2026-11-09

**Status**: Draft

**Input**: 用户描述："允许浮动背包放到 Accessories（Blodhgarm）的后背槽位，放置后和放在玩家胸甲上效果是一样的，都认为是背在了身上。"整体规划与决策见 `docs/plan/worn-backpack-compat.md`；032 已经把"背着的背包"做成有固定顺序的来源列表，033 加了 Curios 的 `back` 槽，本 feature 把 Accessories 的 `back` 槽作为第三个来源加在最后。

## 背景与已确认的决策

033 之后：背上模型的渲染与按 B / 面板的服务端操作 / 自动拾取都走同一条规则——**主手 → 副手 → 背着的第一只 → 物品栏**；"背着的"是按固定顺序排好的来源列表（胸甲 → Curios），只取第一只。

已确认的决策（来自用户，不重新讨论）：

1. **只做 Accessories 的 `back` 槽**，不做 `cape`、`charm`、`necklace` 等其它槽。
2. **顺序与不拦截**：胸甲 → Curios → **Accessories**。同时在几处各放一只，只取顺序里的第一只，后面的就是槽里的普通物品（不画，按 B 也不会打开它）。不拦截、不数数、不去重。手里拿着的背包仍然优先于背着的。
3. **尊重 Accessories 的"隐藏渲染"开关**（每格的"是否渲染"）：第一只所在的格子被玩家设为不渲染，就不画，但仍算"背着的"，按 B 照常打开。**只看真实栈，不看装饰（cosmetic）栈**。
4. **`modLevels`（020）对 `accessories` 的含义**：`GENERIC` / `OFF` 时，我们为 Accessories 写的代码不运行（读取槽位、写回、隐藏渲染开关、渲染器注册），所以 Accessories 槽里的背包不算"背着的"；数据（标签）始终存在，所以背包仍能放进槽位，只是当普通物品。**已知限制**：`modLevels` 是客户端配置，专用服务器读不到、按"生效"处理，只对单人游戏与局域网房主成立。
5. **Accessories Compatibility Layer**（把 Curios 与 Trinkets 包装进 Accessories 的模组）**已停止维护**：不为它做任何适配；Accessories 自己把兼容层 ≤0.1.9 声明为不兼容。因为只取"第一只"，兼容层是否镜像物品对我们不影响，不去重。
6. 与 033 不同：**Accessories 默认就把 `back` 槽（数量 1）分配给玩家**，所以**不需要自带槽位分配数据**。

由我按已有惯例设计的部分（可在 plan 阶段微调）：

- 来源的顺序号取 **20**（胸甲 0、Curios 10）；它在线路上的序号是 `BackpackRef.Source` 末尾追加的新常量（序号 5），已有序号不变，**不新增网络包字段，协议版本仍是 16**。
- 一个引用指向"Accessories 后背槽里的第 N 格"，N 是该槽里的下标；该槽有几格就看几格（整合包可能把数量设成多格），按下标从小到大，取第一只。
- 来源是否生效在**每次查找时**判断（读 `modLevels`），所以改了配置立刻生效。
- 兼容类放在 `compat/accessories/`，**Accessories 没装时不被加载**，我们的模组照常工作（宪法 VII）。

## Out of Scope（本阶段不做）

- Curios（033 已做）、Accessories 的其它槽位、装饰栈。
- 拦截第二只/第三只、去重与计数。
- Accessories Compatibility Layer 的任何适配；给 `modLevels` 的 GENERIC / OFF 加专用服务器支持。
- 新贴图、新配方、新设置项、新语言键、新网络包字段、协议改动。
- 改 Shift+右键换到胸甲槽的行为（`FloatingBackpackItem` 不动）。

## 已查证的事实（读 Accessories 1.1.0-beta.54+1.21.1 的 jar 与源码包，以及本仓库源码；规格要尊重）

- Accessories 的槽位定义在 `data/accessories/accessories/slot/*.json`，`back` 的数量是 1，验证器是 `accessories:tag` 与 `accessories:component`（物品必须在 `accessories:back` 物品标签里，或带有"槽位验证"数据组件）。
- **默认就给玩家（和盔甲架）分配 12 个槽**，包括 `back`：`data/accessories/accessories/entity/default.json` 的目标是 `#accessories:defaulted_targets`，其中含 `minecraft:player`。
- 读取：`AccessoriesCapability.get(LivingEntity)`（可能为 `null`）→ `getContainers().get("back")` → `AccessoriesContainer`；`getAccessories()` 是真实栈，`getCosmeticAccessories()` 是装饰栈，`shouldRender(int)` 是每格的渲染开关。Accessories 自己的渲染层 `AccessoriesRenderLayer` 在**客户端**对任意实体读取。
- **渲染**：对没有注册渲染器的物品，Accessories 会用 `DefaultAccessoryRenderer` 画一个默认外观（会与我们自己的背上模型重叠）。`AccessoriesRendererRegistry.registerNoRenderer(item)` 注册的是它自带的 `EmptyRenderer`，而客户端选项 `forceNullRenderReplacement` 打开时，`EmptyRenderer` 会被换回默认渲染器——所以**不能依赖 `registerNoRenderer`**，要注册我们自己的、不是 `EmptyRenderer` 的"什么都不画"的渲染器。
- 它的 `neoforge.mods.toml`：要求 NeoForge `[21,)`、owo `≥0.12.15.0+1.21`；把 `accessories_compat_layer ≤0.1.9` 声明为不兼容。它的 Maven 仓库是 `https://maven.wispforest.io/releases`，当前 1.21.1 的最新版是 `1.1.0-beta.54+1.21.1`（**beta**）。
- 本项目的 `./gradlew runClient` / `runServer` 现在默认带着 Curios 9.5.1、Accessories 1.1.0-beta.54、owo-lib（`localRuntime`）；2026-11-09 已实际启动确认它们与我们的模组一起加载无错误。
- `CompatConfig.policy().specificAdaptation("accessories")` 在客户端读 `modLevels`，配置未加载（专用服务器）时返回 `DEFAULT`（全部 FULL）。
- 032 的来源接口 `WornSource<LivingEntity, ItemStack>` 与 `WornBackpacks.register(...)`、033 的 `compat/curios/` 做法（`CuriosCompat.register()` + 来源类）、`DependencyRules` 的检查都已就位。

## 尚未验证（plan 阶段先查证；验证前不得声称已满足）

- 写回 `getAccessories()` 之后，Accessories 是否发现变化、同步给客户端并保存（是否需要显式的"已变化"调用）。
- 让渲染器"什么都不画"需要实现哪些方法，渲染层怎样判断一个渲染器是不是"空"的（以及 `EmptyRenderer` 以外的空实现会不会被当成空而被替换）；渲染器在哪个客户端事件里注册。
- 编译依赖 `io.wispforest:accessories-neoforge:1.1.0-beta.54+1.21.1` 能否从 `maven.wispforest.io` 解析，API 类是否引用 owo-lib / endec 的类型而需要它们也在编译类路径上；Accessories 没装时不触发 `NoClassDefFoundError`。
- 标签文件 `data/accessories/tags/item/back.json` 的格式与路径；`back` 槽验证器对我们的背包的实际放行效果。
- 与 Curios 同时装、兼容层可能存在时的行为（我们不适配，只记录）。
- 客户端对"别的玩家"的 Accessories 数据是否总是同步到位。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 把背包放进 Accessories 后背槽，就是背着 (Priority: P1)

**Independent Test**: 装了 Accessories 的游戏里，把浮动背包放进 Accessories 的后背槽：背上画出**我们的**背包模型，按 B 打开它，面板所有功能照常，内容保存后不丢。

**Acceptance Scenarios**:

1. **Given** 装了 Accessories 的玩家，**When** 打开 Accessories 界面，**Then** 本来就有"后背"槽。
2. **Given** 背包在物品栏，**When** 把它放进 Accessories 后背槽，**Then** 放得进去（标签生效），背上画出这只背包的模型，**没有 Accessories 的默认外观叠在上面**。
3. **Given** 背包在 Accessories 后背槽、手里没有背包，**When** 按 B，**Then** 打开它；放入、拿出、Ctrl 快速转移、标签管理器、垃圾桶、自动拾取、升级都照常。
4. **Given** 在面板里改了背包内容，**When** 退出重进，**Then** 内容还在。
5. **Given** 把背包从 Accessories 后背槽取出，**Then** 背上立刻不再画它，按 B 回到其它位置的查找。
6. **Given** 玩家在 Accessories 的客户端选项里打开了 `forceNullRenderReplacement`，**Then** 背上仍然只有我们的模型，没有默认外观。

---

### User Story 2 - 顺序：胸甲 → Curios → Accessories，手里最优先 (Priority: P1)

**Independent Test**: 在几处各放一只：背上画顺序里靠前的那只，按 B 打开它；手里拿着一只则按 B 打开手里的。

**Acceptance Scenarios**:

1. **Given** 胸甲槽与 Accessories 后背槽各有一只（没有装 Curios），**Then** 背上画胸甲那只，按 B 打开胸甲那只；Accessories 里的那只是普通物品。
2. **Given** 三处各有一只，**Then** 取胸甲那只；摘掉它则取 Curios 的；再摘掉则取 Accessories 的。
3. **Given** 手里拿着一只、Accessories 后背槽里还有一只，**Then** 按 B 打开手里的；背上仍画 Accessories 里的那只。
4. **Given** Accessories 后背槽有多格（整合包设置），第 1 格是别的物品、第 2 格是背包，**Then** 取第 2 格；两格都是背包则取第 1 格。

---

### User Story 3 - 隐藏渲染开关 (Priority: P2)

**Independent Test**: 在 Accessories 界面里把后背槽设为不渲染：背上不画，但按 B 仍打开它。

**Acceptance Scenarios**:

1. **Given** Accessories 后背槽里的背包所在格子被设为"不渲染"，**Then** 背上不画；按 B 仍打开它；它仍排在顺序里（前面有背包时仍是前面那只）。
2. **Given** 把渲染开关打开，**Then** 背上立刻画出。
3. **Given** 背包放在装饰栈里，**Then** 不算（只看真实栈）。

---

### User Story 4 - `modLevels` 与没装 Accessories (Priority: P2)

**Independent Test**: 把 `accessories` 配成 GENERIC：Accessories 后背槽里的背包不再算背着的（只在单人游戏与局域网房主上成立）；没装 Accessories 时一切与现在一样。

**Acceptance Scenarios**:

1. **Given** 单人游戏，`modLevels` 里 `accessories=GENERIC`（或 OFF），**Then** Accessories 后背槽里的背包不画（也没有默认外观）、按 B 不打开它，但仍能放进槽位；改回 FULL 立刻恢复。
2. **Given** 没装 Accessories，**Then** 一切与现在一样，模组正常加载，没有任何与 Accessories 有关的错误；标签文件无害。
3. **Given** 专用服务器，**Then** `modLevels` 不生效（按 FULL 处理），这是已知限制。
4. **Given** Curios 与 Accessories 都装了，**Then** 两个后背槽都能放背包，顺序 胸甲 → Curios → Accessories。
5. **Given** 同时装了已停止维护的 Accessories Compatibility Layer，**Then** 我们不保证行为，也不为它做适配。

---

### Edge Cases

- Accessories 被移除（世界里槽里还有背包）：我们不读它，物品随 Accessories 的数据留在原处；不崩溃。
- `AccessoriesCapability.get(entity)` 返回 `null`（该实体没有 Accessories 槽位）：当作没有这个来源的背包。
- 客户端发来的引用指向不存在的格子或未加载的来源：读到空栈，服务端按现状拒绝或关闭会话。
- 玩家死亡：Accessories 的掉落规则决定背包去向，我们不改。
- 整合包把 `back` 槽数量设为 0：没有背包可找，与没有槽一样。
- 兼容层把 Curios 物品镜像进 Accessories：同一只背包可能被两个来源各看到一次；因为只取第一只，不影响。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 浮动背包的 5 个档位都在 `accessories:back` 物品标签里（一份数据文件，Accessories 没装时被忽略）；因此 Accessories 的后背槽接受它们。**不**自带槽位分配（Accessories 默认已经给玩家）。
- **FR-002**: 在 032 的来源列表里增加一个 **Accessories 来源**：顺序号 20（Curios 之后），线路序号是 `BackpackRef.Source` 末尾追加的新常量；已有序号、已有网络包字段、协议版本（16）都不变。
- **FR-003**: Accessories 来源读取实体 `back` 槽的**真实栈**，按格子下标从小到大；对任意实体都可用（客户端渲染别的玩家）；没有 Accessories 数据、槽位缺失、格子越界或任何异常都返回空栈，不抛。
- **FR-004**: Accessories 来源能把改过的栈**写回**同一个格子，使背包内容的每次变化都保存在 Accessories 的槽里（退出重进不丢，客户端能看到）。
- **FR-005**: Accessories 来源回答"该格子的渲染开关是否关闭"；关闭时渲染层不画，但它仍然是背着的第一只（按 B 照常打开，且后面的来源不会顶替它）。
- **FR-006**: 来源是否生效在**每次查找时**读取 `modLevels` 里 `accessories` 的级别：只有 FULL 才生效；GENERIC / OFF 不生效，Accessories 槽里的背包不算背着的，也不注册我们的渲染器。
- **FR-007**: 为 5 个档位在客户端注册**我们自己的"什么都不画"的 Accessories 渲染器**（不是 Accessories 自带的 `EmptyRenderer`），使 Accessories 不再用默认外观画我们的背包，且不会被 `forceNullRenderReplacement` 选项换回默认渲染器；只在客户端、只在 Accessories 已加载且 `accessories` 为 FULL 时生效。
- **FR-008**: 兼容类放在 `compat/accessories/`，只在 Accessories 已加载时才被注册与引用；没装 Accessories 时模组正常工作、不触发任何 Accessories 类的加载错误。构建里 Accessories 只作为 `compileOnly` 依赖，不进发布 jar；`neoforge.mods.toml` 里它是**可选**依赖。
- **FR-009**: 背上的模型始终由我们自己的渲染层画（只画 `WornBackpacks.first` 的那一只）。
- **FR-010**: 按 B / 面板的所有服务端操作 / 自动拾取对 Accessories 里的背包与对胸甲里的背包行为一致；Ctrl 快速转移对它同样不会把背包转进它自己。
- **FR-011**: 不新增网络包、语言键、贴图、配方、设置项；`modLevels` 的配置键沿用 `accessories`。
- **FR-012**: 服务端对客户端引用的处理不变：始终用 `resolve` 重新解析，只在与解析结果完全一致时才采用声称的引用。

### Key Entities

- **Accessories 后背槽来源**：032 的 `WornSource` 的一个实现，顺序号 20。
- **引用**：`BackpackRef(ACCESSORIES, 格子下标)`。
- **空渲染器**：我们注册给 5 个档位的、什么都不画的 Accessories 渲染器。
- **数据文件**：`accessories:back` 标签（5 个档位）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 装了 Accessories 的玩家把背包放进 Accessories 后背槽，**两步以内**（打开 Accessories 界面、放进去）就背着了，背上只有我们的模型（没有默认外观），按 B 能打开。
- **SC-002**: 面板的所有功能在 Accessories 里的背包上与在胸甲里的背包上**行为一致**（放入、拿出、快速转移、标签管理器、垃圾桶、自动拾取、升级、编号、快照），内容保存后不丢。
- **SC-003**: 顺序规则在真实游戏里与 032 的单元测试一致：胸甲 → Curios → Accessories；手里最优先；隐藏渲染仍算背着的。
- **SC-004**: 没装 Accessories 时，游戏日志里没有任何与 Accessories 兼容有关的错误；自动化测试全部通过。
- **SC-005**: 协议版本仍是 16；图集仍是 2048×1024；数据文件内容由单元测试检查。

## Assumptions

- 玩家用的是 Accessories 1.1.0-beta.54+1.21.1 或更新的 1.21.1 版本（beta 版）。
- Accessories 在服务端与客户端都装着；只有一端装了时，没装的一端看不到那只背包，不崩溃。
- Accessories 对"槽内物品变化"的处理足以让我们写回的栈被保存与同步（plan 阶段验证；不行就补一个显式的"已变化"调用）。
- `modLevels` 在专用服务器上不生效是可以接受的已知限制（用户 2026-11-09 已接受）。
