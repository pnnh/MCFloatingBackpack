# Research: 水管、油管、岩浆管

读本仓库源码、NeoForge 21.1.251 与 Minecraft 1.21.1 的源码（`neoformruntime` 里带 NeoForge 补丁的反编译源码）、PNC 8.2.24 的 jar 得出；每条标明**已查证**或**未验证**。

## R1 右键吸收的入口（D3）

- **已查证**（`BucketItem.use`）：空桶做 `getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY)`；命中方块时检查 `level.mayInteract(player, pos)` 与 `player.mayUseItemAt(pos.relative(face), face, stack)`；`state.getBlock() instanceof BucketPickup` → `pickupBlock`，返回非空栈即成功，再播放 `getPickupSound`、`level.gameEvent(player, GameEvent.FLUID_PICKUP, pos)`。
- **已查证**：客户端对着水面看时命中的可能是湖底的方块（客户端射线不看流体），此时原版先走 `useItemOn`（物品默认 `PASS`），再走 `use`，所以 `use` 里重新做一次流体射线就能拿到水面。
- **Decision**: 管子是自己的 `Item` 子类，`use()` 照抄这套；`useOn` 保持默认（`PASS`）。服务端做事，客户端 `use` 返回 `pass`（动画由服务端的结果决定）。潜行时 `pass`，留给 050。

## R2 左键的入口（D4）

- **已查证**：
  - 服务端 `ServerPlayerGameMode.handleBlockBreakAction` 的第一行就是 `CommonHooks.onLeftClickBlock(player, pos, face, action)`，被取消则 `return`，什么也不做（不挖、不改方块）；该方法对每个 `START_DESTROY_BLOCK`、`STOP`、`ABORT` 都会触发。
  - 客户端 `MultiPlayerGameMode.startDestroyBlock`：创造模式在预测闭包里触发事件，**被取消就不 `destroyBlock`**（但仍发包）；生存模式触发事件后若 `event.getUseItem().isFalse()` 就不开始挖掘；`LeftClickBlock.setCanceled(true)` 会把 `useBlock`、`useItem` 都置为 `FALSE`。
  - 对着空气左键没有服务端事件（`LeftClickEmpty` 只在客户端）；放置需要目标方块，所以不需要它。
- **已查证**：生存模式下取消后 `isDestroying` 一直为假，按住左键时每 tick 都会重新 `startDestroyBlock`（发 `START`）；创造模式 `continueDestroyBlock` 每 5 tick 一次。服务端分不清"点一下"与"按住"。
- **Decision**: `LeftClickBlock` 在**两端都取消**（主手是管子时），服务端的 `START` 触发放置，用 4 tick 的节流（点一下恰好一次；按住最多每秒 5 次，同一位置再放同种源方块不会成功，所以也不会扣量）。不加网络包。
- **备选（放弃）**：客户端 `InputEvent.InteractionKeyMappingTriggered` 发自己的包（每次点击一次，不用节流），但要升协议号、碰客户端输入，而且按住时 `continueAttack` 仍会走挖掘，还得取消 `LeftClickBlock`，所以收益不大。

## R3 放置（D5）

- **已查证**（`FluidUtil.tryPlaceFluid(Player, Level, InteractionHand, BlockPos, IFluidHandler, FluidStack)`）：先 `canBePlacedInLevel`，再用 `fluidSource.drain(resource, SIMULATE)` 判断能不能给，检查目标格（空气、非实心、可替换，或 `LiquidBlockContainer`），`isVaporizedOnPlacement` 的走蒸发（下界的水），否则交给方块处理器放源方块并播放 `BUCKET_EMPTY` 声音；成功才返回 true。
- **Decision**: 给它一个一次性的 `IFluidHandler`（`drain` 在模拟时返回 1000 mB 的资源、执行时返回并记下"已取走"，`fill` 不可用）；成功后 `FluidStoreOps.drain` 提交。位置取法照抄 `BucketItem.use`：目标方块是 `LiquidBlockContainer` 且能放这种液体就放在它里面，否则放在点击面的相邻格；`level.mayInteract` 与 `player.mayUseItemAt` 检查；成功后补 `level.gameEvent(player, GameEvent.FLUID_PLACE, pos)`。
- 与桶的差别：水桶在 `emptyContents` 里还处理 `checkExtraContent`、统计与 `PLACED_BLOCK` 触发器（这些与物品绑定），管子不做。

## R4 吸收的实现（D6）

- **已查证**（`LiquidBlock.pickupBlock`）：`state.getValue(LEVEL) == 0`（源方块）才移除并返回 `new ItemStack(fluid.getBucket())`；含水方块的 `pickupBlock` 把水含量去掉并返回水桶。没有桶物品的流体返回空栈（但方块已被移除）。
- **Decision**: 吸收前先用 `level.getFluidState(pos)`：`isSource()` 且属于管子的液体（见 R5）才继续；检查罐的容量（`FluidStoreOps.deposit` 的模拟）与原油锁定；`pickupBlock` 之后判成功用"返回非空，或该位置的流体状态不再是源方块"（防没有桶的模组流体被移除而没记账）。
- **未验证**：对没有桶物品的流体的上述处理只有源码推断，没有真实模组可测（PNC 有桶）。

## R5 液体的归类（纯函数 `PipeKind`）

- 水：`FluidTags.WATER`（原版标签，含 `minecraft:water`、`flowing_water`）；岩浆：`FluidTags.LAVA`；油：`TagKey<Fluid>(c:crude_oil)`。
- **已查证**：NeoForge 的牛奶流体在 `c:milk`，不在水标签里；PNC 的植物油在 `c:plantoil`，不在 `c:crude_oil` 里，所以不会被水管、油管误吸。
- 油的流体 id：`BuiltInRegistries.FLUID.getKey(fluidState.getType())`，取**源**流体（`FlowingFluid.getSource()`），存进 048 的 `oil_fluid`。放置时用 `BuiltInRegistries.FLUID.get(id)`；id 找不到（那个模组被卸载）时视为放不了，提示"这种油的来源模组不在了"（归入 `NO_FLUID`）。

## R6 找背包与存回（D7）

- **已查证**（`AutoPickupEvents`）：`BackpackRef.resolve(player)` → `ref.stillValid(player)` → `BackpackGate.acquire(player, ref)`（被保护的背包返回空）→ 读写 `BackpackInventory` → `ref.saveInventory(player, inventory)`；这是服务端不经过面板访问背包的既有模式。
- 048 提供：`inventory.tankTier(kind)`、`inventory.fluids()`/`setFluids`、`BackpackNetwork.sendFluids(player, inventory)`。
- 主手拿着管子时，`BackpackRef.resolve` 会跳过主手（不是背包）去找背着的、物品栏里的。

## R7 提示（D5）

- **已查证**：服务端发个人聊天用 `player.sendSystemMessage(Component.translatable("message.mcfloatingbackpack.prefix")....)`（`BackpackTrashPayload`、`BackpackGate` 都这么做）。
- 冷却用服务端 `level.getGameTime()`（不受暂停与时间设置影响的 tick 计数）；`Cooldown` 的表在玩家登出时清掉对应键，避免无限增长。

## R8 PNC（D8，用户指定的原油测试模组）

- **已查证**（8.2.24 jar）：`data/c/tags/fluid/crude_oil.json` = `pneumaticcraft:oil`；`c:plantoil` = `pneumaticcraft:vegetable_oil`；有 `pneumaticcraft:oil_bucket`；`neoforge.mods.toml` 里必需依赖只有 neoforge 与 minecraft，patchouli、jei、emi 可选。
- Modrinth 的最新 1.21.1 版本是 8.2.24（2026-10-29，11.9 MB）。`DependencyRulesTest` 允许 `maven.modrinth` 组与 `localRuntime` 配置。
- **未验证**：能否在容器里起来；`setblock` 放出的是不是源方块；版本串能否解析；PNC 的世界生成会不会干扰容器里的测试世界（只对新生成的区块有影响）。

## R9 图集预算（宪法 VI）

- 新增 3 张 16×16 的物品贴图进共享图集：实现前后各跑一次 `design/demo/tools/check_atlas_budget.sh`（048 之后是 2048×1024）；PNC 在干净客户端（`-PcleanRuntime`）度量时不在里面，不影响度量。
