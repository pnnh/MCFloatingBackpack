# Data Model: 移动熔炉

## 熔炉状态 `FurnaceState`（物品上的数据组件 `mobile_furnace_state`）

| 字段 | 类型 | 含义 | 缺省 |
|---|---|---|---|
| `input` | 物品栈 | 原料格（格 0） | 空 |
| `fuel` | 物品栈 | 燃料格（格 1） | 空 |
| `output` | 物品栈 | 产物格（格 2） | 空 |
| `litTime` | int ≥ 0 | 燃烧剩余刻数（原版 `litTime`） | 0 |
| `litDuration` | int ≥ 0 | 当前这份燃料的总刻数（原版 `litDuration`；原版从燃料格推，这里显式存，因为燃料已被消耗） | 0 |
| `cookProgress` | int ≥ 0 | 烧制进度（原版 `cookingProgress`） | 0 |
| `cookTotal` | int ≥ 0 | 当前原料的总烧制刻数（原版 `cookingTotalTime`） | 0 |
| `recipesUsed` | 配方 id → int | 已烧出、还没发经验的个数（原版 `recipesUsed`） | 空 |
| `stamp` | long | 这份状态已推进到的游戏时间 | （无组件时没有） |

- **组件缺失 = 空熔炉**（没有 stamp，不推进）。
- 编码：磁盘用 Codec（物品栈用 `ItemStack.OPTIONAL_CODEC`，各字段 `optionalFieldOf` 带缺省，将来加字段向后兼容；配方 id 用 `ResourceLocation.CODEC` 的无界映射）；网络用 `ItemStack.OPTIONAL_STREAM_CODEC` 与 `ByteBufCodecs`。组件按 `BACKPACK_CONTENTS` 的写法注册（`persistent` + `networkSynchronized`）。
- 不变量（读入时规整）：整数字段小于 0 读作 0；`recipesUsed` 里个数 ≤ 0 的条目丢掉；`stamp` 小于 0 读作"没有"。

## 引擎的可变状态 `FurnaceCore<S>`（纯逻辑，运行时）

`FurnaceState` 的运行时形式：`S[3] slots`、四个 int、`Map<String,Integer> recipesUsed`；不含 stamp（stamp 属于调用者：菜单的 `lastAdvance`）。`S` 是抽象的物品栈类型（真实运行是 `ItemStack`，测试里是一个小记录）。

## `FurnaceRules<S>`（引擎需要的外部知识）

| 方法 | 含义 |
|---|---|
| `Recipe<S> recipeFor(S input)` | 原料的熔炉配方，没有则 null；`Recipe` = (id, 烧制刻数, 产物 `S`, 经验) |
| `int burnTime(S fuel)` | 燃料的燃烧刻数，非燃料为 0 |
| `S remainder(S fuel)` | 燃料用掉一份后的剩余物（岩浆桶 → 桶），没有则空 |
| `S afterSmelt(S input, S fuel)` | 烧完一件后的燃料格（湿海绵 + 桶 → 水桶，否则不变） |
| 栈操作 | `empty`、`isEmpty`、`count`、`withCount`、`sameStack`（`isSameItemSameComponents`）、`maxStack` |

## 状态转移

```text
空熔炉 --放入原料与燃料（setItem）--> 点燃 --(每刻)--> 烧完一件 --> 产物 +1 / 原料 −1 / recipesUsed +1
   ^                                      |
   |                                      +--燃料用尽--> 熄灭（进度每刻 −2）
   +------------取走产物（发经验、清 recipesUsed）
```

`advance(core, ticks)` 把上面从当前时刻推进 `ticks` 刻（快进只是跳过没有事件的一段，等价于 `tickOnce` 循环 `ticks` 次）。

## 写回的时机（见 research R4）

| 事件 | 写回 | stamp |
|---|---|---|
| 打开（补算之后） | 是 | 当前游戏时间 |
| 三格内容变化（放入、取出、取走产物） | 是（立即） | 菜单已推进到的时间 |
| 取走产物、发完经验、清零 `recipesUsed` 之后 | 是 | 同上 |
| 关闭（`removed`） | 是 | 当前游戏时间（先推进到现在） |
| 时间自然前进（进度、燃料、烧出的产物） | **否**（由状态 + stamp 推出） | — |

## 与既有数据的关系

- 工具物品在右区，随 `BackpackContents.tools.right` 一起编码；背包内容的结构**不变**，所以只有"工具物品上多了一个组件"这一件事需要修订号。
- 数据修订 5 → 6：6 版本写出的背包里可能有 `mobile_furnace` 物品与其组件；旧版本读到会丢（040 的保护）。
- 030 快照存同一份 `BackpackContents` 编码，自动带上三格与时间戳；恢复后 stamp 可能晚于当前游戏时间（跨世界）→ 补算时经过时间取 0。
