# Data Model: 水管、油管、岩浆管

没有新增存储字段、网络包与配置。纯逻辑部分（`pipe` 包）不依赖 Minecraft 类。

## PipeKind（枚举，纯逻辑）

| 值 | 对应的液体（`FluidKind`） | 物品 id | 认哪些流体（MC 层用标签判断） |
|---|---|---|---|
| WATER | WATER | `water_pipe` | `FluidTags.WATER` |
| OIL | OIL | `oil_pipe` | `#c:crude_oil` |
| LAVA | LAVA | `lava_pipe` | `FluidTags.LAVA` |

- `PipeKind.classify(boolean inWater, boolean inLava, boolean inCrudeOil)` → 属于哪种管子或 `null`（一个流体同时在两个标签里时按 WATER、LAVA、OIL 的顺序取第一个）；`accepts(kind, ...)`。
- `fluid()`：对应的 `FluidKind`；`itemId()`。

## PipePlanner（纯函数）

### 输入

```java
record Situation(
    boolean backpackFound,     // 找到了按 B 会打开的背包，且没被保护
    boolean backpackProtected, // 找到了，但被 040 的保护拒绝
    BackpackTier tankTier,     // 底部区该液体的生效罐的档次，null = 没有
    FluidStore store,
    String fluidId)            // 目标的流体 id（吸收：世界里那个；放置：不用，取 store 里锁定的）
```

### 吸收：`absorb(PipeKind kind, Situation s)` → `Plan`

判定顺序（第一个不成立的就是结果）：

1. `!backpackFound` → `REFUSE`，提示 `NO_BACKPACK`（`backpackProtected` 时提示 `BACKPACK_PROTECTED`）。
2. `tankTier == null` → `REFUSE`，`NO_TANK`。
3. 原油且已锁定为另一种流体 → `REFUSE`，`OIL_LOCKED`。
4. 罐里空位 `capacity − amount < 1000` → `REFUSE`，`CANNOT_ABSORB_FULL`。
5. 否则 `DO`：新液量 = `FluidStoreOps.deposit(...)` 的结果；若 `amount + 1000 == capacity` 则提示 `TANK_FULL_NOW`，否则无提示。

### 放置：`place(PipeKind kind, Situation s)` → `Plan`

1. 同上第 1、2 步。
2. `store.amount(kind) < 1000` → `REFUSE`，`CANNOT_PLACE_EMPTY`。
3. 油且锁定的流体 id 为空 → `REFUSE`，`CANNOT_PLACE_EMPTY`。
4. 否则 `DO`：新液量 = `FluidStoreOps.drain(...)`；放置的流体 id = 水/岩浆的固定流体，油为锁定的那种；若扣完后该液体为 0 则提示 `TANK_EMPTY_NOW`。

### 输出

```java
record Plan(Kind kind /* DO | REFUSE */, FluidStore next, int mb /* 1000 */, String fluidId, Notice notice /* 可为 null */)
enum Notice { NO_BACKPACK, BACKPACK_PROTECTED, NO_TANK, OIL_LOCKED, CANNOT_ABSORB_FULL, CANNOT_PLACE_EMPTY, TANK_FULL_NOW, TANK_EMPTY_NOW }
```

- `REFUSE` 时 `next == store`（不变）；`DO` 时 MC 层必须先改世界，成功才把 `next` 存回；世界没改成则丢弃 `next`、不提示"满/空"（但可以不提示任何东西）。
- 所有判定只看 `FluidStoreOps` 与 `TankCatalog.capacityMb(tankTier)`，不另写容量规则。

## Cooldown\<K\>（纯逻辑）

```java
final class Cooldown<K> {
    boolean tryAcquire(K key, long nowTick, int ticks);   // nowTick − last ≥ ticks 才返回 true 并记下；否则 false 且不更新
    void forget(java.util.function.Predicate<K> which);   // 登出时清掉
}
```

- 提示冷却：键 = `(玩家 UUID, Notice)`，20 tick；放置节流：键 = 玩家 UUID，4 tick。两个实例。

## MC 层（不单元测试，用源码断言与游戏内验证）

- `PipeItem(kind, properties)`：`stacksTo(1)`；`use()`：右键吸收；`appendHoverText`：说明。
- `PipeActions`：`absorb(ServerPlayer, PipeKind, BlockHitResult)`、`place(ServerPlayer, PipeKind, BlockPos, Direction)`：找背包 → 组装 `Situation` → `PipePlanner` → 改世界 → 提交 → `sendFluids` → 发提示。
- `PipeEvents.onLeftClickBlock(PlayerInteractEvent.LeftClickBlock)`：主手是管子 → `setCanceled(true)`；服务端且 `action == START` 且节流通过 → `PipeActions.place`。
- 一次性的 `IFluidHandler`：`drain(FluidStack, action)`：资源与 1000 mB 相符时返回它，`EXECUTE` 时记下 `taken = true`；`fill` 返回 0。

## 状态转移

- 吸收：`use()` → 射线 → 判定流体 → `PipePlanner.absorb` → `pickupBlock` → 成功则 `setFluids`、`saveInventory`、`sendFluids`、提示。
- 放置：`LeftClickBlock(START)` → 节流 → `PipePlanner.place` → `tryPlaceFluid` → 成功则同上。
- 面板打开着：`sendFluids` 让量条与 048 的隐藏液体立刻更新。
