# Data Model: 牛奶管、蜂蜜管与排放到容器

没有新增存储字段、网络包与配置。纯逻辑部分（`pipe` 包）不依赖 Minecraft 类。

## PipeKind（扩展）

| 值 | 液体 | 物品 id | 世界里的流体 | 一份 | 空容器 | 成品 |
|---|---|---|---|---|---|---|
| WATER | WATER | `water_pipe` | `FluidTags.WATER` | 1000 | 桶 | 水桶 |
| OIL | OIL | `oil_pipe` | `#c:crude_oil` | 1000 | 桶 | 锁定流体的桶（可能没有） |
| LAVA | LAVA | `lava_pipe` | `FluidTags.LAVA` | 1000 | 桶 | 岩浆桶 |
| MILK | MILK | `milk_pipe` | 无 | 1000 | 桶 | 牛奶桶 |
| HONEY | HONEY | `honey_pipe` | 无 | 250 | 玻璃瓶 | 蜂蜜瓶 |

- 新方法：`unitMb()`（= `fluid().perUseMb()`）、`hasBlockForm()`（水、油、岩浆为真）、`emptyContainer()`（枚举 `BUCKET`/`GLASS_BOTTLE`，蜂蜜为 `GLASS_BOTTLE`）。`classify(...)`、`accepts(...)` 只涉及前三种，不变。

## PipePlanner（扩展）

### 常量与单位

`BUCKET_MB` 保留给 049 的测试引用，实际用 `pipe.unitMb()`：吸收需要空位 ≥ 一份；放置、排放需要液量 ≥ 一份；"满了"= 吸收后 `amount >= capacity`；"空了"= 扣完后 `amount <= 0`。

### 新增输入与输出

```java
record Supply(boolean hasEmptyContainer, boolean productExists) { }   // 排放用

enum Notice { …049 的…, NO_EMPTY_CONTAINER, NO_BUCKET_FOR_FLUID, NO_BLOCK_FORM }
```

### 排放：`discharge(PipeKind pipe, Situation s, Supply supply)` → `Plan`

1. 没找到背包 / 被保护 / 没有生效罐 → 同 049 的 `NO_BACKPACK`、`BACKPACK_PROTECTED`、`NO_TANK`。
2. 液量 `< unitMb()`（油：锁定为空也算）→ `CANNOT_PLACE_EMPTY`（文字改为"罐里不够了"）。
3. `!supply.productExists()` → `NO_BUCKET_FOR_FLUID`（只会发生在油）。
4. `!supply.hasEmptyContainer()` → `NO_EMPTY_CONTAINER`。
5. 否则 `DO`：新液量 = `FluidStoreOps.drain(store, kind, unitMb())`；`fluidId` = 油的锁定流体（其它为空串）；扣完为 0 则提示 `TANK_EMPTY_NOW`。

### 放置（扩展）

`place(pipe, s)`：`!pipe.hasBlockForm()` → `REFUSE` + `NO_BLOCK_FORM`（在 049 的判定之前，连背包都不用找）。

### 吸收（扩展）

单位改为 `unitMb()`；其余不变。牛奶、蜂蜜：`s.fluidId() == ""`，不涉及锁定。

## ContainerPicker（纯逻辑）

```java
/** 在一串有序的位置里找第一个满足谓词的；位置用整数编号：0..mainSize-1 是背包主区域，mainSize.. 是物品栏。 */
static int firstMatch(int mainSize, int inventorySize, IntPredicate matches);  // 没有返回 -1
static boolean isInBackpack(int position, int mainSize);                       // position < mainSize
```

## MC 层（不单元测试，用源码断言与游戏内验证）

- `PipeItem`：
  - `use`：服务端、主手；潜行 → `PipeActions.discharge`；未潜行 → 水、油、岩浆走 049 的 `absorb`，牛奶、蜂蜜 `pass`。
  - `interactLivingEntity`：牛奶管、未潜行、成年 `Cow`/`Goat` → 客户端 `sidedSuccess(true)`；服务端 `PipeActions.absorbEntity`；其余 `pass`。
  - `useOn`：蜂蜜管、未潜行、`BeehiveBlock` 且 `honey_level >= 5` → 客户端 `sidedSuccess(true)`；服务端 `PipeActions.absorbHive`；其余 `pass`。
- `PipeActions.discharge(player, pipe)`：找背包 → 找空容器（`ContainerPicker`）→ 确定成品 → `PipePlanner.discharge` → 取走空容器、发放成品、`commit` → 空容器来自背包主区域时 `sendFullSync` → 声音（`BUCKET_FILL` / `BOTTLE_FILL`）、`gameEvent(FLUID_PICKUP)` → 提示。
- `PipeActions.absorbEntity(player, pipe, target)`：`PipePlanner.absorb` → 挤奶声（牛 `COW_MILK`；山羊按 `isScreamingGoat()`）→ `commit` → 提示。
- `PipeActions.absorbHive(player, pos, state)`：`PipePlanner.absorb` → 世界（`BOTTLE_FILL` 声、`FLUID_PICKUP` 事件、篝火判断、激怒蜜蜂、`releaseBeesAndResetHoneyLevel`/`resetHoneyLevel`）→ `commit` → 提示。
- `PipeEvents`：牛奶管、蜂蜜管左键 → 取消事件（049 已有）+ `PipeNotices.send(NO_BLOCK_FORM)`（经 `PLACE_TICKS` 节流与提示冷却）。

## 状态转移

- 排放：潜行右键 → `use` → 判定 → 取容器、发成品、扣液、保存、同步 → 提示。
- 牛奶：右键牛 → `interactLivingEntity` → 判定 → 声音、加液 → 保存、同步 → 提示。
- 蜂蜜：右键蜂巢 → `useOn` → 判定 → 世界、加液 → 保存、同步 → 提示。
