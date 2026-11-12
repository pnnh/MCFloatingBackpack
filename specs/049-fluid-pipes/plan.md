# Implementation Plan: 水管、油管、岩浆管

**Branch**: `049-fluid-pipes` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

## Summary

三根无状态的管子物品 + 服务端的两条操作路径：**右键吸收**走物品自己的 `use()`（与空桶一样对着流体做射线），**左键放置**走 NeoForge 的 `LeftClickBlock` 事件（取消它，不让方块被挖，同时在服务端的 `START` 动作里放置）。操作的**判定顺序与提示选择写成不依赖 Minecraft 类的纯逻辑**（可穷举测试），世界交互用桶的同一套机制（`BucketPickup`、`FluidUtil.tryPlaceFluid`），液量用 048 的 `FluidStoreOps`，找背包用 `BackpackRef.resolve` + `BackpackGate.acquire`（与自动拾取同一模式）。**不新增网络包、存储字段，协议号与数据修订都不变。**

1. **纯逻辑 `pipe/PipePlanner`**：输入"有没有背包、有没有生效罐、液量、容量、流体 id"，输出"做什么 / 拒绝的原因 / 之后要不要给提示"。吸收与放置先**判定**，再由 MC 层**改世界**，成功后才**提交液量**（FR-016：同进退）（D1）。
2. **纯逻辑 `pipe/Cooldown`**：同一个类做两件事——提示的冷却（玩家 + 提示种类，20 tick）与左键放置的节流（玩家，4 tick；按住左键时原版每 tick 都发 `START`）（D2）。
3. **右键吸收**：`PipeItem.use()` 里 `getPlayerPOVHitResult(SOURCE_ONLY)`（与空桶同一个射线），检查 `mayInteract` / `mayUseItemAt`，再 `BucketPickup.pickupBlock`；潜行时 `pass`（留给 050）（D3）。
4. **左键放置**：`PipeEvents` 监听 `LeftClickBlock`：主手是管子就 `setCanceled(true)`（两端都取消：客户端不再预测挖掘、创造模式不再瞬间破坏），服务端的 `START` 才放置；放置用 `FluidUtil.tryPlaceFluid`（已含下界蒸发、含水方块、声音），位置的取法与桶一致（D4）。
5. **提示**：服务端 `player.sendSystemMessage`（带 `message.mcfloatingbackpack.prefix` 的个人聊天），7 种，经冷却过滤（D5）。
6. **同步**：改了液量就存回背包并 `BackpackNetwork.sendFluids`，面板的量条与 048 的隐藏液体随之更新（D6）。
7. **物品与资源**：3 个物品、3 个配方、3 张占位贴图（扩展 048 的生成脚本）、语言键；`build.gradle` 把 PNC 加进开发运行时依赖（`localRuntime`，在 `-PcompatRuntime` 那一组）（D7、D8）。

## Technical Context

**Language/Version**: Java 21 · NeoForge 21.1.251 / Minecraft 1.21.1

**Primary Dependencies**: 无新增运行时依赖；开发运行时加 `maven.modrinth:pneumaticcraft-repressurized:8.2.24`（`localRuntime`）

**Storage**: 无新增（液量沿用 048 的 `fluids`）

**Testing**: JUnit 5：`PipePlannerTest`（判定穷举 + 随机"同进退"）、`CooldownTest`、`PipeKindTest`（流体归类的纯函数）、`PipeResourcesTest`（物品、模型、贴图、配方、语言键、never_trash）、`PipeWiringTest`（源码文本：`use` 里潜行 pass、`LeftClickBlock` 取消、放置只在服务端 `START`、改世界先于提交液量、`sendFluids`、无新包、协议号仍 19、PNC 只在 `localRuntime`）；容器里游戏内验证（含 PNC 的石油）

**Target Platform**: Minecraft 客户端 + 服务端（NeoForge）

**Project Type**: 游戏模组（单工程，服务端权威）

**Performance Goals**: 每次操作 O(1)（一次射线、一次背包读写）；节流后按住左键每秒最多 5 次放置

**Constraints**: 无新增配置项；协议号仍 19；数据修订仍 2；011～048 的测试不改；PNC 不进发布 jar

**Scale/Scope**: 3 个物品、约 6 个新类、改动约 4 个既有文件

## Constitution Check

| 原则 | 评估 |
|---|---|
| I. Overlay Not Screen | 不涉及界面 PASS |
| II. Server Owns Items | 吸收、放置、液量、提示全在服务端；客户端不乐观更新液量（只有挥手由服务端结果决定）；左键事件在两端都取消只为不挖方块 PASS |
| III. One Feature At A Time | 只做三根管子的吸收与放置；牛奶、蜂蜜、排放到容器是 050 PASS |
| IV. Vanilla Path Before Compat | 水与岩浆只用原版机制；油只认 `#c:crude_oil` 标签，不引用任何模组的类 PASS |
| V. Verify In A Client Session | quickstart 要求容器里验证（含创造与生存、PNC 的石油），未验证项如实记录 PASS（实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 新增 3 张 16×16 物品贴图进共享图集：加之前后各量一次 `check_atlas_budget.sh`；未量不声称达标 PASS（待实现时度量） |
| VII. Minimal Dependencies | PNC 只作 `localRuntime`（开发用，不进 jar、不进 `neoforge.mods.toml`），主代码不引用它的类；`DependencyRulesTest` 允许的组 `maven.modrinth` 与配置 `localRuntime` 已覆盖 PASS |

## Research

完整记录见 [research.md](./research.md)。要点：

- **D1 判定与提交分开**：`PipePlanner` 只算"该不该做、做完液量是多少、给什么提示"；MC 层先改世界、成功才 `commit`。**Decision**: 不在改世界之前扣液量，也不在扣液量之后才发现世界没改成。
- **D2 一个冷却类**：`Cooldown<K>.tryAcquire(key, nowTick, ticks)`；提示键 = (玩家, 提示种类)；放置节流键 = 玩家。理由：按住左键在生存里原版会每 tick 发 `START`（`continueDestroyBlock` 里 `startDestroyBlock`），创造里每 5 tick 一次，服务端无法区分"点一下"与"按住"，所以用 4 tick 的节流——点一下恰好一次，按住不会一瞬间倒空。
- **D3 右键**：读 `BucketItem.use` 的源码：空桶用 `ClipContext.Fluid.SOURCE_ONLY` 的射线，`useOn` 返回 PASS 时原版会再调 `use`，所以水面下有方块（客户端点中湖底）也能吸到水面；管子照抄这套，不用事件。
- **D4 左键**：读 `ServerPlayerGameMode.handleBlockBreakAction` 与 `MultiPlayerGameMode.startDestroyBlock`：服务端第一行就触发 `LeftClickBlock`，被取消则直接返回（不破坏）；客户端创造模式里取消则不 `destroyBlock`，生存模式里 `setCanceled` 会把 `useItem` 置为 FALSE（不开始挖掘）。所以**两端都取消**，只在服务端的 `START` 放置，**不需要新的网络包**。
- **D5 放置**：`FluidUtil.tryPlaceFluid(player, level, hand, pos, IFluidHandler, FluidStack)` 已处理 `canBePlacedInLevel`、下界蒸发、`LiquidBlockContainer`（含水方块）与声音；我们给它一个"一次性一桶"的 `IFluidHandler`（模拟与执行都只给 1000 mB 并记下是否被取走），成功后提交；位置取法照抄 `BucketItem.use`（能容纳流体的方块用自己，否则取点击面的相邻格）；再补 `FLUID_PLACE` 游戏事件与 `mayInteract` / `mayUseItemAt` 检查。
- **D6 吸收**：照抄 `BucketItem.use` 的空桶分支：`state.getBlock() instanceof BucketPickup` → `pickupBlock`；先用 `level.getFluidState(pos)` 判断是源方块且属于管子的液体（水：`FluidTags.WATER`；岩浆：`FluidTags.LAVA`；油：`#c:crude_oil`），再检查罐的容量与原油锁定，最后才 `pickupBlock`。**未验证**：`pickupBlock` 对没有桶物品的模组流体返回空栈却已移除方块——判成功用"返回非空 **或** 该位置不再是源方块"。
- **D7 找背包**：与自动拾取同一模式：`BackpackRef.resolve(player)` → `ref.stillValid(player)` → `BackpackGate.acquire(player, ref)`（被保护的背包返回空 → 提示"这个背包暂时不能用"）→ `inventory.tankTier(kind)`（048）→ `ref.saveInventory`。
- **D8 PNC**：已读 8.2.24 的 jar：标签 `c:crude_oil` 含 `pneumaticcraft:oil`；有 `oil_bucket`；必需依赖只有 NeoForge；Modrinth 坐标 `maven.modrinth:pneumaticcraft-repressurized:8.2.24`；植物油在 `c:plantoil`，不吸。**未验证**：容器（arm64 Linux、软件渲染）能否起来；`setblock ~ ~ ~ pneumaticcraft:oil` 放出的是否源方块；`build.gradle` 里 `maven.modrinth` 的版本串写 `8.2.24` 能否解析（实现第一步验证）。

## Project Structure

```text
specs/049-fluid-pipes/{spec.md,plan.md,research.md,data-model.md,quickstart.md,contracts/,tasks.md,DELIVERY.md,checklists/}
src/main/java/pnnh/mcfloatingbackpack/
├── pipe/PipePlanner.java        # ★ 纯逻辑：判定、结果、提示
├── pipe/PipeKind.java           # ★ 纯逻辑：三种管子与流体归类
├── pipe/Cooldown.java           # ★ 纯逻辑：冷却与节流
├── pipe/PipeActions.java        # ★ MC 层：吸收、放置（桶的机制、FluidUtil）、找背包、存回、同步、提示
├── pipe/PipeEvents.java         # ★ LeftClickBlock 监听
├── item/PipeItem.java           # ★ use()：右键吸收
├── item/ModItems.java           # 改：3 个管子、创造栏
└── MCFloatingBackpack.java      # 改：注册 PipeEvents
src/main/resources/
├── assets/mcfloatingbackpack/{models/item,textures/item}/<kind>_pipe.*   # 3 + 3
├── assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json
├── data/mcfloatingbackpack/recipe/<kind>_pipe.json                       # 3
└── data/mcfloatingbackpack/tags/item/never_trash.json                    # 改
build.gradle                      # 改：localRuntime 加 PNC
design/tank-textures/generate.py  # 改：同时生成管子占位图
src/test/java/pnnh/mcfloatingbackpack/{pipe/*,resources/Pipe*Test.java}
README.md / README_CN.md / docs/plan/fluid-tanks.md
```

## 验证（quickstart，见 [quickstart.md](./quickstart.md)）

单元测试 → 容器里（带 PNC）：吸收 / 放置 / 提示 / 节流 / 创造与生存 / 面板量条随之变化 / PNC 的石油与植物油 / 没有背包与没有罐 / 满与空。

## Complexity Tracking

无需要论证的违例。左键用事件而不是新网络包，是为了不升协议号、不碰客户端输入。
