# 规划：把背包背在 Curios / Accessories 的后背槽位（2026-11-09）

**状态**: 规划文档，**不是规格**（真正开工以后续的 `specs/032-*` 等为准）。目标：允许浮动背包放进 **Curios** 和 **Accessories** 两个饰品模组的**后背槽**，放进去之后和穿在玩家胸甲槽里一样，都算"背在身上"；按 B 打开哪一只、背上画哪一只，**走同一套全局查找顺序**。

## 已确认的决策（用户，2026-11-09）

1. **只支持后背槽**：Curios 的 `back`、Accessories 的 `back`，加上原有的玩家胸甲槽。不做 `body`、`curio`、`charm` 等其它槽位。
2. **全局一致的查找顺序**：玩家胸甲 → Curios 后背槽 → Accessories 后背槽。按 B 打开与模型渲染都用这一套。
3. **不拦截**第二只、第三只：同时放几只都允许，只是查找顺序里排在前面的那一只算数，别的不画、不参与"背着的"判断。
4. **不再做"数量检测"与去重**（简化的直接结果，见下）。
5. **Accessories Compatibility Layer 已停止维护**：不为它做任何适配；它可能带来的冲突作为已知风险记录，不为它加代码。
6. **`modLevels` 的含义沿用 020**：`GENERIC` = 我们为该模组写的专有代码不运行（数据标签仍在，所以背包仍能放进槽位，只是当普通物品）；`OFF` 按 020 的定义。
7. **Curios 按官方版本做**：官方 Curios 已有 1.21.1 的版本（见下），Adorned（Curios 的 NeoForge 延续版，已停更，模组 id 仍是 `curios`，声称保持同样的 API 与标签结构）不单独适配、不单独验证。

## 查找顺序

定义**一个**函数（暂名 `WornBackpacks`）：返回玩家"背着"的背包，按顺序取第一只：

| 顺序 | 位置 | 说明 |
|---|---|---|
| 1 | 玩家胸甲槽 | 现状 |
| 2 | Curios `back` 槽（所有该槽的格子，按槽内顺序） | 新增，仅当 Curios 已加载且 `modLevels` 不是 GENERIC/OFF |
| 3 | Accessories `back` 槽（同上） | 新增，同上 |

两处使用同一个结果：

- **渲染**（`BackpackBackLayer`）：只画这个函数返回的那一只。同时放几只也只画一只，不会出现多个模型重叠。
- **按 B / 自动拾取**（`BackpackRef.resolve`）：新顺序是 `主手 → 副手 → 背着的（上表）→ 物品栏 0～35`。背着的那一只就是上表的第一只。手里拿着的背包仍优先于背着的（现状如此：渲染只看背着的，按 B 先看手），这一点**保持不变**，只是把"背着的"从"胸甲槽"扩成上表。

没被选中的背包（排在后面的）就是槽里的普通物品：不画、按 B 也不会打开它（除非把前面的拿掉），取出来放进物品栏后又能按物品栏顺序被找到。

## 简化带来的好处

- **不需要数"穿了几只"**，也就**不需要去重**：Accessories Compatibility Layer 是否会让同一只物品在两个 API 里各出现一次，已经不重要了——我们只取第一只。
- **不需要拦截事件**：Curios 的穿戴前事件、Accessories 的穿戴检查（在 NeoForge 上怎么暴露还没确认）都不用碰。
- 影响面很集中：`BackpackRef`（服务端 11 处、客户端 3 处调用）、`BackpackBackLayer`、`AutoPickupEvents`（已经走 `BackpackRef.resolve`）、`FloatingBackpackItem` 不用改（仍然是胸甲槽的 `Equipable`，Shift+右键仍换到胸甲槽）。

## 具体要做的事

### 数据（零代码依赖）
- `data/curios/tags/item/back.json`：加入 5 个档位的背包。
- `data/accessories/tags/item/back.json`：同上。
- 模组没装时这两个文件被忽略，无害。标签名已从两个模组的源码核实：Curios 是 `curios:back`（`CuriosTags` 用 `curios:<槽id>`），Accessories 是 `accessories:<槽名>`；物品标签目录是 1.21 通用的 `tags/item/`。**放进槽位的实际效果仍要在游戏里验证**。
- **Curios 还需要一份"给玩家分配后背槽"的数据**：Curios 官方 jar 里只有槽位定义，**没有给任何实体分配槽位**，配置项 `slots` 默认也是空——只装 Curios 时玩家**一个 Curios 槽都没有**。要让功能开箱即用，我们得自带 `data/mcfloatingbackpack/curios/entities/<名字>.json`（内容 `{"entities":["minecraft:player"],"slots":["back"]}`）。源码确认：不同命名空间的这类文件会合并，同一个槽 id 重复时保留最后一个、不会叠加成两个槽。代价是会给没配置过后背槽的整合包的玩家多出一个 `back` 槽。**这是要你决定的**，见"待决定"。
- **Accessories 不需要**：它的默认配置已经给玩家（和盔甲架）12 个槽，包括 `back`（数量 1）。

### 代码（`compat/curios/`、`compat/accessories/`，宪法 VII）
- 读取：
  - Curios：`CuriosApi.getCuriosInventory(LivingEntity)` 里取 `back` 槽的物品（文档里也有不依赖编译的做法：能力 `curios:item_handler`，是个扁平的 `IItemHandler`，但分不出槽位 id，所以更可能用 API）；`SlotResult(SlotContext, ItemStack)` 已确认存在。
  - Accessories：`AccessoriesCapability.get(entity)`，取 `back` 槽（`getEquipped(谓词)`、`SlotEntryReference` 的 `stack()`/`reference()`）。
- 写回：`BackpackRef` 要能把改过的栈写回这两个槽（背包内容变了要存）。
- **渲染抑制**（避免第三方渲染器再画一遍）：
  - **Curios：不需要任何代码**。源码确认 `CuriosLayer` 对没注册渲染器的物品什么都不画（`getRenderer(...).ifPresent(...)`）。
  - **Accessories：要注册一个自己写的"什么都不画"的渲染器**。没注册渲染器的物品会被 `DefaultAccessoryRenderer` 画出来；`registerNoRenderer(item)` 注册的是它自带的 `EmptyRenderer`，而 Accessories 有一个客户端选项 `forceNullRenderReplacement`，打开后会把 `EmptyRenderer` 又换回默认渲染器，所以**不能依赖它**。我们自己写一个不是 `EmptyRenderer`、`render` 里什么都不做的渲染器。
  - 不管第三方怎样，`BackpackBackLayer` 仍然只画 `WornBackpacks` 的那一只。
- **读取与写回（已对照源码）**：
  - Curios：`CuriosApi.getCuriosInventory(LivingEntity)` 返回 `Optional<ICuriosItemHandler>`；取 `getStacksHandler("back")` 的 `getStacks()` 逐格读、`setStackInSlot` 写；`getRenders()` 是每格的"是否渲染"开关。`findCurios(谓词)` 会跳过未激活的格子，只看真实栈（不含装饰栈）。`CuriosLayer` 自己就在**客户端**对任意 `LivingEntity` 调这个 API，所以客户端读别的玩家的槽没问题。
  - Accessories：`AccessoriesCapability.get(entity)`（可能为 null）→ `getContainers().get("back")` → `getAccessories()` 逐格读写；`shouldRender(i)` 是渲染开关；`getCosmeticAccessories()` 是装饰栈。它的渲染层同样在客户端对任意实体读取。
- 构建：`compileOnly`（Curios 用 `top.theillusivec4.curios:curios-neoforge:<版本>:api`，仓库 `https://maven.theillusivec4.top/`；Accessories 用 `io.wispforest:accessories-neoforge:<版本>`，仓库 `https://maven.wispforest.io/releases`）；开发运行用 `localRuntime`（已在默认的 `./gradlew runClient` 里）。**Accessories 需要 owo-lib 才能运行**（它的必需依赖），开发运行时也要带上。

### 网络
`BackpackRef.Source` 的序号会随包传输（`BackpackClickPayload.sourceOrdinal`）；新来源加在枚举末尾，并且要能定位到具体槽位（Curios 槽 id + 格子下标，Accessories 同理）。服务端始终用 `resolve` 重新解析，不信任客户端的引用（现状如此）。具体怎么编码留到 plan，协议版本要升。

## 版本事实（2026-11-09 查到）

| | 版本 | 说明 |
|---|---|---|
| Curios（官方） | `9.5.1+1.21.1`（release，2025-05-14） | 有 `-api.jar`；依赖：无 |
| Accessories | `1.1.0-beta.54+1.21.1`（**beta**，2026-10-16） | 必需依赖 owo-lib；Modrinth 上没有把 Curios / Trinkets 列为依赖或不兼容 |

**最低 NeoForge 版本（读 jar 里的 `neoforge.mods.toml`）**：Curios 要求 NeoForge `[21.1.60,)`；Accessories 与 owo-lib 都要求 `[21,)`；Accessories 另外要求 owo `≥0.12.15.0+1.21`。所以都满足本项目的 21.1.251。**已实际启动验证**：开发运行时同时加载 Curios 9.5.1、Accessories 1.1.0-beta.54、owo 0.12.15.6-beta.1 与我们的模组，日志里没有加载错误。
**Accessories 的 `mods.toml` 把 `accessories_compat_layer ≤0.1.9` 声明为不兼容**——也就是说它自己拒绝了兼容层的旧版本，更新的兼容层版本不受限制。我们不适配兼容层，只在这里记录。

## 拆分建议（一次一个 feature，宪法 III）

| 暂定编号 | 内容 |
|---|---|
| 032 worn-backpack | **已实现**（2026-11-09，待游戏内验证）：`BackpackSearch`/`WornSource`/`WornBackpacks`，`BackpackRef.resolve` 与渲染改用它；先只接胸甲槽，行为不变 |
| 033 curios-back | **已实现**（2026-11-09，待游戏内验证）：`curios:back` 标签、无条件的玩家后背槽分配、`compat/curios/` 的来源（读写、隐藏渲染、`modLevels`）；Curios 一侧不需要渲染抑制 |
| 034 accessories-back | **已实现**（2026-11-09，待游戏内验证）：`accessories:back` 标签、`compat/accessories/` 的来源（读写、隐藏渲染、`modLevels`）与**自己写的空渲染器**（不用 `registerNoRenderer`，避开 `forceNullRenderReplacement`）；三个来源全部完成 |

## 验证步骤的结果（2026-11-09）

已经做完、不需要游戏界面就能确认的部分（方法：下载两个模组的 jar 与源码包，读元数据、数据文件与源码；把 Curios 9.5.1、Accessories 1.1.0-beta.54、owo-lib 放进开发运行时，启动一次看日志；这些现在已并进默认的 `./gradlew runClient`）：

| 问题 | 结果 |
|---|---|
| 能否在 NeoForge 21.1.251 上启动 | **能**，三个模组与我们的模组、JEI 同时加载，没有错误 |
| 玩家有没有后背槽 | Accessories：**有**（默认给玩家，数量 1）；Curios：**没有**（官方不给任何实体分配槽，配置默认空）→ 需要我们自带分配数据 |
| 标签名 | `curios:back`、`accessories:back`（源码确认） |
| 没渲染器的背包渲不渲染 | Curios：**不渲染**；Accessories：**会用默认渲染器渲染**，且 `registerNoRenderer` 可能被 `forceNullRenderReplacement` 选项绕过 → 要自己写空渲染器 |
| 读取 API | 已对照源码确认（见上），Curios 的 `ICuriosItemHandler` 的 `getStacksHandler`、`findCurios`、`SlotResult` 都在 |
| 兼容层 | Accessories 声明与兼容层 ≤0.1.9 不兼容；是否镜像物品未确认，但因为只取第一只，不影响 |

**还需要在游戏里做的**（我没法操作界面）：

1. 给背包加标签后，能不能真的放进两个模组的后背槽（Curios 要先有槽，见"待决定"1）；
2. 放进去之后背上是不是只有我们画的那一只、没有 Accessories 的默认渲染；
3. 两个模组同时装、三处都放背包时，背上只有一只。

## 决定（用户，2026-11-09）

1. **Curios 的后背槽**：采用 A——我们**无条件**自带 `data/mcfloatingbackpack/curios/entities/<名字>.json`，给玩家一个 `back` 槽，装了 Curios 就能用。**不再用配置控制这份数据**（曾设想过 GENERIC / OFF 时不提供，需要内置数据包加动态数据包来源，复杂且只对单人/局域网房主有效，放弃）。理由：同一个槽 id 在不同数据文件里重复时 Curios 只保留一个（`buildKeepingLast`），不会叠加成两个槽，所以对已经配置过后背槽的整合包没有副作用。代价：没配置过后背槽的整合包的玩家会多出一个 `back` 槽，接受。
2. **渲染开关**：尊重玩家在两个模组里对槽位设置的"隐藏渲染"——第一只所在的槽被隐藏就不画，但仍算"背着的"，按 B 照常打开。
3. **装饰栈**：不看，只看真实栈。
4. **`modLevels` 对这两个模组的含义**（沿用 020）：`GENERIC` / `OFF` 时**我们的代码适配不运行**（读取槽位、写回、渲染抑制、渲染开关）；**数据（标签、Curios 的槽位分配）始终存在**，所以背包仍能放进槽位，只是当普通物品——不算"背着的"、不被渲染层和按 B 的顺序看到。

## 仍未确认（不要当成已知）

- 放进槽位、渲染、三处同时放的实际表现（上面的游戏内验证）。
- Accessories 的 API 我读的是 beta.54 的源码包（这次直接下载的，与要用的版本一致）；只有 `forceNullRenderReplacement` 之外的渲染选项没逐个看。
- 与 Accessories Compatibility Layer 同装时的行为（我们不适配，也没验证）。
- 来源：
  [Accessories 默认槽位](https://docs.wispforest.io/accessories/general/defaulted_slots)、
  [Accessories 标签绑定](https://docs.wispforest.io/accessories/general/binding_accessories_to_slots)、
  [Accessories 渲染 API](https://docs.wispforest.io/accessories/developer/rendering_api)、
  [Accessories Compatibility Layer](https://modrinth.com/mod/accessories-compat-layer)、
  [Curios 分配槽位](https://docs.illusivesoulworks.com/curios/items/assign-slots)、
  [Curios 1.21.1 源码](https://github.com/TheIllusiveC4/Curios/tree/1.21.1)、
  [Adorned (Curios)](https://www.curseforge.com/minecraft/mc-mods/adorned)、
  以及 Modrinth 上 Curios 9.5.1+1.21.1、Accessories 1.1.0-beta.54+1.21.1、owo-lib 0.12.15.6-beta.1+1.21 的 jar 与 `-sources.jar`（本次直接读取）。
