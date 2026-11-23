# 研究：饰品模组里的专用槽位与通用槽位

读了 Curios 9.5.1（含 ATM10 整合包里的完整 jar）、Accessories 1.1.0-beta.54 的 jar 与 NeoForge 21.1.251、FML 4.0.44 的源码。**除标注"未验证"的，都是读源码或字节码得到的，不是游戏内结果**；游戏内验证见 [quickstart.md](./quickstart.md)。

## R1　两个模组的槽位与实体文件都支持 `neoforge:conditions`

- Curios：`CuriosSlotManager.apply` 与 `CuriosEntityManager` 都对每个 JSON 调 `ICondition.conditionsMatched(ops, json)`，条件不满足时日志 "Skipping loading slot {} as its conditions were not met"。
- Accessories：`SlotTypeLoader`、`EntitySlotLoader`、`SlotGroupLoader` 都调 `AccessoriesInternals.isValidOnConditions(json, directory, id, registries)`，NeoForge 实现里用 `registries.createSerializationContext(JsonOps)`（普通 `RegistryOps`）再调 `ICondition.conditionsMatched`。
- `ICondition.conditionsMatched` 对**不是** `ConditionalOps` 的 ops 用 `IContext.EMPTY`：不依赖标签的条件可以用，依赖标签的会读到空。我们的条件只读一个布尔，不用上下文。
- 结论：自定义条件可行。条件序列化器必须在两端、在数据包加载前注册（`NeoForgeRegistries.CONDITION_SERIALIZERS`，`DeferredRegister`），不依赖饰品模组。

## R2　开关要用 FML 的 `STARTUP` 配置类型

- SERVER 配置在 `ServerLifecycleHooks.handleServerAboutToStart` 里才读入，而数据包在服务器对象创建**之前**就已加载（单人与专用服务器都是），所以服务端档位首次加载时不可读；CLIENT 配置专用服务器不加载。
- FML 4.0.44 的 `ModConfig.Type.STARTUP`："Loaded as soon as the config is registered to FML"，两端都有，存放在全局 `config/`，文件名后缀 `-startup`。它的文档警告：用它开关"注册"可能让客户端连上配置不同的服务器时出问题——那说的是**注册表**条目。槽位是数据包数据、由服务端同步给客户端（Curios 的 `CuriosSlotManager.getSyncPacket/applySyncPacket`），客户端自己的开关值不影响联机时看到的槽位，所以这条警告在这里不成立；仍要写进配置注释与 README（联机时以服务端的值为准）。
- 之前设想的 COMMON 也能用（构造后、数据包前读入），但 STARTUP 的语义更直接（"尽早、用于开关注册"），所以选 STARTUP。**决定：STARTUP。**
- 否决：按档位条件注册 + 服务器启动后发现不一致再自动 reload（首次加载读不到服务端档位；reload 是 hack）；无条件注册（强制）。

## R3　槽位 id、tag、图标、语言键

- 槽位 id 取资源路径的最后一段，与命名空间无关：Curios `slots/floating_backpack.json` → `floating_backpack`；Accessories `slot/floating_backpack.json` → `floating_backpack`（`SlotTypeLoader` 按 `/` 切路径，取最后一段，前面的目录作前缀）。
- 物品进槽靠 `tag` 校验器：Curios `curios:<id>`，Accessories `accessories:<id>`，所以是 `data/curios/tags/item/floating_backpack.json` 与 `data/accessories/tags/item/floating_backpack.json`；`charm` 同理（现有的 `back.json` 是同一格式）。
- Curios 图标：槽位 JSON 的 `icon` 是图集里的精灵名；Curios 在 `assets/minecraft/atlases/blocks.json` 里加了一个目录源 `slot` 前缀 `slot/`，该源对**所有命名空间**的 `textures/slot/` 生效，所以 `mcfloatingbackpack:slot/floating_backpack` ← `assets/mcfloatingbackpack/textures/slot/floating_backpack.png` 应当可用（**未验证**，第一步在游戏里确认，不行再把贴图放进 `assets/curios/textures/slot/`）。
- Accessories 图标：`icon: "accessories:gui/slot/back"` ← `assets/accessories/textures/gui/slot/back.png`，走原版 gui 图集的 `gui/` 目录，所以 `mcfloatingbackpack:gui/slot/floating_backpack`（**未验证**）。
- 语言键：Curios `curios.identifier.floating_backpack`（`curios.identifier.back` 是例子）；Accessories `accessories.slot.floating_backpack`（`accessories.slot.hat` 是例子）。
- Accessories 的槽位要在"组"里才有位置：没有任何组包含的槽位进 `unsorted` 组。`group/chest.json` 里有 `back`；我们加一份同名文件 `group/chest.json`，`replace: false`，`slots: ["floating_backpack"]`（组加载器也支持条件）。组文件是否按名字合并（而不是后者覆盖前者）**未验证**；不行就用 `unsorted`，只是显示位置差一点。

## R4　"每类一只"：两个模组都只在**放入**时问"能否装备"

- Curios：`DynamicStackHandler.isItemValid` → `ICurio.canEquip(SlotContext)`；除此之外的 `canEquip` 引用只有 `ItemizedCurioCapability`（转发给 `ICurioItem`）。**没有**在每 tick 里对已有物品调用，所以绕过限制放进去的背包不会被踢出。
- Curios 的物品能力：`Curios.registerCaps` 为**所有**物品注册一个提供器，在查询时调 `CuriosImplMixinHooks.getCurioFromRegistry(item)`；所以 `CuriosApi.registerCurio(item, ICurioItem)` 只要在第一次查询前调用即可（通用 setup）。`ICurioItem.canEquip` 默认转给默认实例（真），`canEquipFromUse` 默认假，所以注册一个只覆盖 `canEquip` 的实现不会引入右键装备。
- `SlotContext` 有 `identifier()`（槽位类型）、`entity()`、`index()`、`cosmetic()`；数别的格子靠 `CuriosApi.getCuriosInventory(entity).getStacksHandler(identifier).getStacks()`（与现有 `CuriosWornSource` 同一套 API）。
- Accessories：`Accessory.canEquip(stack, SlotReference)`（默认真），由 `AccessoriesAPI.canEquip` 调用；`SlotReference` 有 `slotName()`、`entity()`、`slot()`、`slotContainer()`（→ `getAccessories()` 数格子）。现有 `BackpackAccessory` 已在通用 setup 里按五个物品注册，加一个方法即可。
- 风险（**未验证**）：装饰（cosmetic）格子放入时传来的 `SlotContext`/`SlotReference` 是否能区分；最坏情况是"真格子里有一只时，往装饰格放一只也被拒"——可接受，记入交付说明。

## R5　来源枚举与协议

- `BackpackRef.Source` 在三处网络载荷里以整数序号编码（`BackpackOpenRequestPayload`、`BackpackFullSyncPayload`、`BackpackSessionPayload`、`BackpackClickPayload`），末尾追加值不改变已有序号，也不改线格式。
- 发布情况：v0.1.0 的协议是 21；当前代码是 22（060 引入），**22 没有发布过**。061 若与 060 一起发布，协议号仍 22，没有问题；若 060 先单独发布，旧 22 的客户端收到序号 ≥ 6 会出错，所以那种情况下必须升到 23。写进交付说明。
- 解码处已经有越界保护（已读源码）：`BackpackSessionPayload`、`BackpackFullSyncPayload` 在序号越界时退回 `MAIN_HAND`，`BackpackClickPayload.decodeRef` 返回 `null`；所以序号 ≥ 6 的引用到了不认识它们的构建上不会抛异常，只是退化。这不替代协议号的判断，只是兜底。

## R6　每个槽位类型一个独立来源，而不是编码进下标

- `BackpackRef` 只有一个 `invIndex`；把槽位类型编进去要么限定每类的最大格数（跨度编码，`slotCount` 要返回很大的上界，背部层每帧对每个可见玩家遍历），要么随格数变化而漂移。
- 独立来源：每个来源只遍历自己槽位类型的实际格数（通常 1），顺序固定，枚举只追加。代价是枚举多 4 个值、两个 `WornSource` 类变成带参数的类。
- 顺序（先找到的生效）：胸甲 0，Curios 的 `back` 10 → 专用 11 → `charm` 12，Accessories 20 → 21 → 22。`back` 在专用之前，是为了不改变老玩家的现状（老玩家背包在 `back`，不会因为专用槽里放了别的背包而换）。

## R7　槽位被条件去掉（开关从 true 改成 false）时槽里的物品

- Curios：`CurioInventory.invalidStacks`、`CurioInventoryCapability.handleInvalidStacks`（有一个以 `Player` 为参数的 lambda，推断是还给玩家）；Accessories：`AccessoriesHolderImpl.invalidStacks` 与 `InvalidStacksAttribute`。**两个模组都有"无效物品"处理，但去向（还给玩家、掉在脚下、还是保留）没有读到底，也没有游戏内验证**。
- 决定：不替它们做；游戏内验证（quickstart 场景 7）决定 README 的措辞：若还给玩家，写"物品会回到物品栏"；若发现会丢，写"改开关前先取下背包"并把配置注释改成同样的警告。

## R8　宪法 VII 的修订

- 现文："Every proactive interaction with another mod MUST be controlled by a setting that maps that mod's id to a level"。注册槽位是一种主动交互（向另一个模组的数据里添加内容），不属于"适配代码"，档位在这件事上用不上（R2）。
- 修订方案（MINOR，2.3.0）：在 VII 加一个子节"Registering content into another mod's data"：这类主动交互由 `STARTUP` 配置里的**按模组布尔开关**控制，默认开，改动需重启；FULL/GENERIC/OFF 仍只管适配代码。同时在 `docs/plan/third-party-compat.md` 记同一条设计原则。**需要用户确认措辞**（宪法治理规则）。

## R9　与现有代码的接点

- `CuriosWornSource.active(entity)`/`AccessoriesWornSource.active(entity)` 已经是侧感知的（059）；参数化后每个实例传自己的 modId。
- 现有 `curios/entities/player_back.json` 保持无条件（033 的决定）。
- `BackpackAccessory.canEquipFromUse` 保持为否（036）。
- `CuriosCompat.register()`、`AccessoriesCompat.register()` 在构造函数里、`ModList.isLoaded` 之后调用；`registerCurio`、`registerAccessories` 在通用 setup 里调用（物品已存在）。
- 不改 `hiddenRender`：Curios 按槽位类型读 `getRenders()`，Accessories 按容器 `shouldRender(slot)`，参数化后每个实例读自己类型的。

## 否决的方案

- 按档位条件注册 + 自动 reload（R2）。
- 无条件注册（强制，违背用户的要求）。
- 把槽位类型编进 `invIndex`（R6）。
- `accessories:any` 作为通用槽（会让背包能放进所有槽位，而我们只认指定类型，放进去后不被识别）。
- 在 Curios 里强行给玩家分配 `charm`（那是整合包的决定）。
