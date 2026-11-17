# Research: 移动熔炉

对应 spec 的"尚未验证"清单。依据：NeoForge 21.1.251 的 1.21.1 源码（`build/moddev/artifacts/neoforge-21.1.251-sources.jar`）与本仓库当前代码。每条先写结论，再写依据。

## R1 · 原版熔炉的各部件与能复用的部分

**决策**：复用原版 `FurnaceMenu`（`MenuType.FURNACE`）与它的 `FurnaceScreen`；**不**复用 `AbstractFurnaceBlockEntity`（需要方块实体，且 `serverTick` 里会 `level.setBlock`）；烧制规则自己移植。

**依据**：
- `FurnaceMenu(id, Inventory, Container, ContainerData)` 公开；容器 3 格（原料 0、燃料 1、产物 2），`ContainerData` 4 项（0 燃烧剩余、1 燃烧总时长、2 烧制进度、3 烧制总时长）；`AbstractFurnaceMenu` 在构造里 `checkContainerSize(container, 3)`、`checkContainerDataCount(data, 4)`。客户端用 `FurnaceMenu(id, inv)`（`SimpleContainer(3)`、`SimpleContainerData(4)`）自己构造，数据靠 `addDataSlots` 同步。
- **1.21.1 没有 `FuelValues`**（那是 1.21.2 之后）。燃料查询是 NeoForge 的 `ItemStack.getBurnTime(RecipeType)`（先看物品自己的实现，再看燃料数据映射，再触发 `FurnaceFuelBurnTimeEvent`）；`AbstractFurnaceMenu.isFuel` 也是用它。本 feature 用同一个调用，所以别的模组加的燃料自动可用。
- 配方：`RecipeManager.createCheck(RecipeType.SMELTING)` 得到 `CachedCheck`，`getRecipeFor(new SingleRecipeInput(stack), level)`；`AbstractCookingRecipe` 给 `getCookingTime()`、`getExperience()`、`assemble(...)`（产物）。
- `AbstractFurnaceMenu.stillValid` 默认问容器，我们重写；`removed` 不清容器（工作台的菜单才会），所以关界面不会把三格退给玩家——东西留在工具物品里，正是我们要的。
- `RecipeBookMenu` 的配方书转移（`ServerPlaceRecipe`）要求容器是 `StackedContentsCompatible`：我们的容器实现 `fillStackedContents`。

## R2 · 状态机的移植与"快进"

**决策**：把 `serverTick` 逐刻搬进纯逻辑 `FurnaceEngine.tickOnce`，再在 `advance(core, ticks)` 里加快进；界面开着时 `advance(core, 1)`，打开时 `advance(core, 经过的刻)`。

**原版逐刻逻辑**（记 L=`litTime`、LD=`litDuration`、P=`cookingProgress`、T=`cookingTotalTime`，A 原料、F 燃料、O 产物）：

```text
wasLit = L>0
if L>0: L--
if L>0 || (F非空 && A非空):
    recipe = A非空 ? 配方(A) : null
    if L<=0 && canBurn(recipe):           # 点燃
        L = LD = 燃烧时间(F)
        if L>0: 燃料用掉一个（有剩余物就换成剩余物；数量归零则格子变成剩余物或空）
    if L>0 && canBurn(recipe):
        P++
        if P == T: P = 0; T = 总烧制时间(A); burn(): 产物 +1、原料 -1、湿海绵+桶→水桶的特例；记 recipesUsed[recipe]++
    else: P = 0
elif L<=0 && P>0:
    P = clamp(P-2, 0, T)
```

`canBurn`：有原料、有配方、`assemble` 非空、产物格空或同种且 `count+n ≤ min(64, 物品堆叠上限)`。注意几个原版性质必须原样保留（因为"补算 = 原版"）：燃料点燃后即使原料没了也烧完这一份；点着但 `canBurn` 为假时 `L` 照常递减、`P` 归零（燃料白烧，与原版一致）；`T` 只在换原料（`setItem(0)`）与烧完一件时刷新。

**快进**：在**状态稳定**的一段里，每刻的变化是线性的，可以一次跳 `k` 刻而不经过事件：
- 点着且 `canBurn`：每刻 `L--`、`P++`，不触发事件的最长一段 `k = min(n, L-1, T-P-1)`（`L-k ≥ 1`，保证每刻开头仍点着、减一后仍点着；`P+k ≤ T-1`，保证不碰 `P==T`）。`k ≥ 1` 就一次跳。
- 点着但不能烧（产物满、没配方）：`L` 每刻减一、`P=0`；`k = min(n, L)` 之后熄灭。
- 熄灭、原料与燃料都有但不能烧：什么也不变（冻结），一次跳完。
- 熄灭、`P>0`、没有"点燃条件"：`P` 每刻 −2 直到 0，`k = min(n, ceil(P/2))`。
- 熄灭、`P==0`、没有点燃条件：冻结。
- 其余（`P==T`、需要点燃、燃料用尽那一刻）都是**事件刻**，用 `tickOnce` 单刻处理。

事件刻的个数 ≤ 原料件数 + 燃料件数 + 常数（≤ 64 个原料、≤ 64 个燃料），所以开销与经过的刻数无关（SC-005：100 万刻也是几百次迭代）。**一致性不靠论证，靠测试**：随机的（三格、L、LD、P、T、n≤数千）输入，快进结果必须与 `tickOnce` 循环 n 次逐字段相等（三格、四个计数、配方计数）。

## R3 · 状态放在哪里、怎么随背包走

**决策**：新数据组件 `mobile_furnace_state`，挂在工具物品自己的 `ItemStack` 上。不碰 `BackpackContents` 的编码。

**依据**：
- 背包内容里的工具区是 `List<ItemStack>`，编码用 `ItemStack.OPTIONAL_CODEC` / `OPTIONAL_STREAM_CODEC`（`ToolAreaContents`），**嵌套的物品组件补丁会一并编码**：磁盘、网络、030 快照（存的是同一份 `BackpackContents` 编码）、背包升级配方（复制内容）都自动带上，不需要改任何保存路径。
- 组件注册照 `ModDataComponents.BACKPACK_CONTENTS` 的写法（`persistent(codec).networkSynchronized(streamCodec)`）；没有该组件 = 空熔炉。
- 工具被拿出、放回、放进别的背包，状态都在这个 `ItemStack` 上，满足"随物品不随背包"。
- 在服务端不碰 `BackpackGate` 就**读**（`MobileTools.has` 已是这个做法）；**写**必须走 `BackpackGate.acquire`（身份、040 保护、快照）→ 改工作副本右区里的工具物品 → `BackpackRef.saveInventory`（盖修订章、通知快照服务）。
- 客户端不需要这份数据（界面是原版菜单，数据来自 `ContainerData` 与槽位同步）；组件随背包物品同步到客户端会多带三格的编码，量很小。**待游戏里确认**：写回时面板不闪烁、不丢选中（风险低：面板的数据来自会话载荷而不是物品栈）。

## R4 · 什么时候写回（及为什么不需要定期写）

**决策**：写回时机 = 打开、关闭、**三格内容每变一次**（容器 `setChanged`）、取走产物发完经验之后。写回的是"当前引擎状态 + 该状态对应的游戏时间（stamp）"。**不做定期写回。**

**依据**：烧制引擎是确定的。界面开着期间，只要玩家没动格子，下一刻的状态完全由"上一次写回的状态 + stamp"推出；玩家动格子时立刻写回，所以"写回的状态 + stamp"与"此后到现在"之间没有任何不可复现的输入。因此：
- 服务端崩溃、玩家下线（`PlayerList.remove` 不会先 `closeContainer`，直接 `save`）：物品上是最后一次写回的状态；下次打开按补算从 stamp 推到现在，结果与没有中断一致。**没有"进度落后"。**
- 工具被拿走、背包被换：拿走发生的那一刻，物品上的状态已是最近一次变化时的状态；之后界面被 `stillValid` 关掉（`ServerPlayer.tick` 在 `broadcastChanges` 之后问 `stillValid`，`handleContainerClick` 一开头也问，所以拿走后的点击会被忽略，不会出现"拿走后还写进去"）。
- 没有定期写回还有一个好处：不会每秒让背包物品变化、触发物品栏同步包与快照。

**stamp 的定义**：引擎状态已推进到的游戏时间。`MobileFurnaceMenu` 记着 `lastAdvance`；`broadcastChanges` 里先 `advance(core, now - lastAdvance)`（一刻内被调多次时为 0，是空操作——`ServerPlayer.tick` 与点击、放置配方等处理里至少有 5 个调用点会调 `broadcastChanges`，所以不能"每次调用推进一刻"）。

**游戏时间的来源**：`level.getGameTime()`。`DerivedLevelData.getGameTime` 委托给主世界的数据，所以各维度同一个时钟；`/time set`、`/time add`、睡觉只改 `dayTime`、不改 `gameTime`，所以熔炉不会因睡觉多烧（与地上的熔炉一致）。spec 原先"睡觉按跳过的时间补算"的假设不对，已更正。

## R5 · 经验

**决策**：状态里记 `recipesUsed`（配方 id → 烧出个数）；自己的产物槽（`FurnaceResultSlot` 的子类）在取走时按原版公式发经验。

**依据**：原版 `FurnaceResultSlot.checkTakeAchievements` 只在 `container instanceof AbstractFurnaceBlockEntity` 时发经验——我们的容器不是，所以**原版槽不会发**，必须自己做。做法：构造 `MobileFurnaceMenu` 时把 `slots[2]` 换成子类（`slots` 是公开字段，槽的 `index` 也可写；`lastSlots` / `remoteSlots` 的长度不变），子类重写 `checkTakeAchievements`：先 `super`（物品的 `onCraftedBy`、`PlayerSmeltedEvent` 照旧），再对每条 `recipesUsed`：`i = floor(个数 × 经验)`、`f = frac(...)`，`f != 0 && random < f` 则 `i++`，`ExperienceOrb.award(level, player.position(), i)`；`player.awardRecipes(配方们)`、`triggerRecipeCrafted(配方, 三格)`；清零 `recipesUsed`，**然后写回**（否则物品上还留着计数，下次再领一次）。公式取自 `AbstractFurnaceBlockEntity.createExperience` / `awardUsedRecipesAndPopExperience`。

补算期间烧出的产物也记进 `recipesUsed`（`burn` 里 `recipesUsed[recipe]++` 在引擎里），所以补算与逐刻的经验一致（SC-001 一并比较）。配方 id 在补算后已经不存在（数据包删了）：`byKey` 找不到就跳过，与原版一致。

## R6 · 打开、有效性、关闭

**决策**：`OPEN` 时先 `BackpackGate.acquire(player, ref)`（给没有身份的背包补身份；被拒说明是更新版本保存的受保护背包）：被拒 → 不打开、发提示（新键 `message.mcfloatingbackpack.furnace.protected`）；通过 → 记下背包身份 UUID，读状态，`advance` 补算到现在，`player.openMenu(...)`。有效性 = **按 B 解析出的背包的身份仍等于打开时记的** 且 **右区仍有移动熔炉**。

**依据**：
- 054 的 `stillValid` 只问"按 B 的背包右区有没有工作台"。工作台没有状态，换一只也有工作台无所谓；熔炉有状态，如果玩家把背包换了（比如手里换了另一只也带熔炉的），菜单会继续往**另一只**的熔炉里写——串了状态。加身份比较就能避免（身份 UUID 在 `BackpackIdentity` 组件里，`acquire` 保证有）。
- 右区里取"第一个"移动熔炉。右区上限（053 的 `ToolSingleRules`）从本物品诞生起就存在，所以不会有"旧背包里已有多个"的情况；只有命令、创造栏复制之类的绕过手段能造出多个，取第一个已足够。
- `ToolOpenPlanner` 不改：它只看"种类、右区有没有、已开的是哪种"。

## R7 · 协议与修订对历史测试的影响

**协议号：不变（20）。** 054 的 `OpenToolPayload(byte)` 对 `FURNACE = 1` 原样可用；服务端对未知字节本来就忽略，旧版客户端不会发这个字节（它没有这个物品）。
**数据修订：5 → 6。** 理由：新的只能存在背包里的物品种类（`mobile_furnace`），且工具物品上有新的数据组件；旧版本读到会丢掉它们（040 的保护正为此）。

写死修订 5 的断言（逐一同改为 6，并更新 `DataRevision` 的文字说明与测试里的注释）：
`DataRevisionTest`；`MobileCraftingWiringTest`、`JukeboxWiringTest`、`LightWiringTest`、`TankWiringTest`、`PipeWiringTest`（两处）里的 `contains("CURRENT = 5;")`。
`WorldMarkerTest` / `WorldMarkerGuardTest` 里的 5 是任意输入，不是 `CURRENT`，不用改（实现时再确认）。
`MobileToolTest` 若断言了枚举的全部取值，同改。其余 011～054 的测试不改。

## R8 · 补算的边界（汇总）

| 情形 | 处理 |
|---|---|
| 当前时间 < stamp（跨世界、时间回退） | 经过时间取 0，把 stamp 校正为当前时间 |
| 没有 stamp（没有状态的物品） | 空熔炉，不推进；第一次写回时才有 stamp |
| 燃料中途烧完 | 引擎的熄灭分支：进度每刻 −2 直到 0，原版行为 |
| 产物放满 / 不同种 | `canBurn` 为假：点着的燃料白烧、进度归零，冻结（原版行为） |
| 原料或燃料格里的东西不再有配方 / 不再是燃料 | 同上，不烧、不丢 |
| 配方被数据包改了（烧制时间变了） | 按当前配方表；`T` 只在换原料与烧完一件时刷新（原版行为） |
| 经验用的配方已不存在 | 跳过（原版 `byKey` 行为） |
| 极长的时间（100 万刻） | 事件刻个数有界，开销与刻数无关 |
| 同时多个玩家 | 状态在各自的物品上，互不干扰 |
| 死亡 | 背包随玩家物品栏的规则走，状态在工具物品里，不额外掉出三格 |

## 未决项（不阻塞 plan，需游戏里确认）

- 写回时面板有无闪烁或丢选中（R3）。
- 容器里 `FurnaceScreen` 的配方书（绿色书本按钮）与 JEI/EMI 转移在自定义容器上能用（`StackedContentsCompatible` 已实现）。
- 创造模式取走产物是否也发经验（与原版熔炉一致）。
