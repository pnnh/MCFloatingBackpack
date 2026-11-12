# Research: 牛奶管、蜂蜜管与排放到容器

读本仓库源码与 Minecraft 1.21.1（带 NeoForge 补丁的反编译源码，`neoformruntime`）得出；每条标明**已查证**或**未验证**。

## R1 实体的交互顺序（牛奶管，D1、D6）

- **已查证**（`Player.interactOn`）：先触发 NeoForge 的 `onInteractEntity`，再 `entityToInteractOn.interact(this, hand)`（牛的 `mobInteract` 只在 `itemstack.is(Items.BUCKET) && !isBaby()` 时挤奶，否则 `super.mobInteract` 对非食物返回 `pass`）；**不消耗动作**时才 `itemstack.interactLivingEntity(this, (LivingEntity) entity, hand)`；它也消耗动作才算成功。
- **已查证**：`Cow`（含 `MushroomCow`）与 `Goat` 的挤奶声：牛 `SoundEvents.COW_MILK`；山羊 `getMilkingSound()` 是 `protected`，但等价于 `isScreamingGoat() ? GOAT_SCREAMING_MILK : GOAT_MILK`，`isScreamingGoat()` 是公开的。
- **已查证**：客户端 `MultiPlayerGameMode.interact` 也本地跑 `player.interactOn`，结果决定挥手；实体交互失败（`pass`）后 `startUseItem` 才继续调 `use`。
- **Decision**: `PipeItem.interactLivingEntity`：牛奶管、未潜行、目标是 `Cow` 或 `Goat` 且不是幼体 → 服务端 `PipeActions.absorbEntity`；客户端返回 `sidedSuccess(true)`（只为挥手）。潜行或不符合 → `pass`（于是 `use` 会被调，潜行时排放）。

## R2 蜂巢的交互顺序（蜂蜜管，D1、D2）

- **已查证**（`BeehiveBlock.useItemOn`）：`honey_level >= 5` 时只认剪刀（`canPerformAction(SHEARS_HARVEST)`）与玻璃瓶；管子都不是 → `flag` 为假 → `super.useItemOn`（默认 `PASS_TO_DEFAULT_BLOCK_INTERACTION`）→ 之后物品的 `useOn` 被调用。
- **已查证**：取蜜的世界效果：`CampfireBlock.isSmokeyPos(level, pos)` 为假 → 若蜂巢里有蜜蜂 `angerNearbyBees`（`private`）→ `releaseBeesAndResetHoneyLevel(level, state, pos, player, EMERGENCY)`（公开）；为真 → `resetHoneyLevel`（公开）。`angerNearbyBees` 的内容：`AABB(pos).inflate(8, 6, 8)` 里的 `Bee`，若范围内有玩家，对没有目标的蜜蜂 `setTarget(随机一名范围内的玩家)`。`hiveContainsBees` = 方块实体是 `BeehiveBlockEntity` 且 `!isEmpty()`（`isEmpty` 公开）。
- **Decision**: `PipeItem.useOn(UseOnContext)`：蜂蜜管、未潜行、方块是 `BeehiveBlock` 且 `honey_level >= 5` → 服务端 `PipeActions.absorbHive`（先判定，再用上面的公开方法与重写的两小段改世界，再提交）；客户端返回 `sidedSuccess(true)`。
- 蜂窝（`bee_nest`）也是 `BeehiveBlock` 的实例，一并支持。

## R3 潜行与 `use`（D1）

- **已查证**（`ServerPlayerGameMode.useItemOn`）：潜行（`isSecondaryUseActive`）且手里有物品时，方块自己的 `useItemOn` 被跳过，但物品的 `useOn` 仍被调用；`useOn` 返回 `PASS` 后，客户端 `startUseItem` 继续 `useItem` → 服务端 `Item.use`。实体分支同理（`interactLivingEntity` 返回 `pass` 后继续 `useItem`）。
- **Decision**: 管子的 `useOn`、`interactLivingEntity` 里**先判断潜行**，潜行就 `pass`；排放只写在 `use()`。

## R4 容器与成品（D4、D5）

- **已查证**：`BucketItem` 的 `getEmptySuccessItem`、`ItemUtils.createFilledResult` 说明原版给成品的方式：创造模式不消耗；我们的规格要求创造与生存一致，所以不用它们，自己做"取走一个空容器、`Inventory.add`、失败 `drop`"。
- **已查证**（`BeehiveBlock.useItemOn`）：原版瓶子取蜜"放不进物品栏就 `player.drop(stack, false)`"——与规格一致。
- **Decision**: 空容器只认 `minecraft:bucket` 与 `minecraft:glass_bottle`（`stack.is(Items.BUCKET)`）；扫描顺序：背包主区域的格子 0..size-1，然后玩家物品栏的 `items`（36 格，快捷栏在前）。
- **已查证**：背包主区域的改动用 `inventory.setStackInSlot` + `ref.saveInventory`（与 049 的 `commit` 同一个保存），面板同步用 `BackpackNetwork.sendFullSync(player, ref)`（空容器被取走时才需要；只改液量时 `sendFluids` 就够）。
- **未验证**：掉在地上的桶被自动拾取的背包收走时，是进主区域（`PickupInsert` 的目标顺序）——只在游戏里看一眼。

## R5 原油的桶（D4）

- **已查证**（PNC 8.2.24 jar）：有 `pneumaticcraft:oil_bucket` 物品、`FluidOil` 类引用 `OIL_BUCKET`；NeoForge 的 `Fluid.getBucket()` 返回流体的桶物品（没有则 `Items.AIR`）。
- **Decision**: 成品 = `new ItemStack(fluid.getBucket())`；`fluid` 取自 048 锁定的 `oil_fluid`；`getBucket() == Items.AIR`（或流体不在注册表里）→ `productExists = false`。**未验证**：PNC 的 `FluidOil.getBucket()` 确实返回 `oil_bucket`（游戏里看成品物品 id）。

## R6 提示措辞（D7）

- 049 的两条提示在蜂蜜下不准确；只改语言文件里的文字，键名与逻辑不变；`PipeResourcesTest` 只断言键存在，不断言文字，所以测试不用改。

## R7 图集预算（宪法 VI）

- 新增 2 张 16×16 物品贴图：前后各跑 `design/demo/tools/check_atlas_budget.sh`（049 之后是 2048×1024）。
