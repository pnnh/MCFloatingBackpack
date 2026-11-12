# Data Model: 液体罐

除"物品与接线"外全部是不依赖 Minecraft 类的纯逻辑，可以直接单元测试。

## FluidKind（枚举）

| 值 | id（物品名、存储键） | 一次的量（mB，049/050 用） | 颜色（ARGB，量条） | 显示名语言键 |
|---|---|---|---|---|
| WATER | `water` | 1000 | `0xFF3F76E4` | `fluid.mcfloatingbackpack.water` |
| OIL | `oil` | 1000 | `0xFF6A5A7A` | `…oil` |
| LAVA | `lava` | 1000 | `0xFFE8651A` | `…lava` |
| MILK | `milk` | 1000 | `0xFFF2F2F2` | `…milk` |
| HONEY | `honey` | 250 | `0xFFE3A82B` | `…honey` |

- `locksFluid()`：只有 OIL 为真（它可能是不同模组的不同原油）。
- `byId(String)`：未知 id 返回 null。

## TankCatalog（纯逻辑）

- 档次（`BackpackTier` 的四个值）：`LEATHER、IRON、GOLD、NETHERITE`；没有 `DIAMOND`。
- `capacityMb(tier)`：皮 8 000、铁 16 000、金 64 000、下界合金 256 000（桶 × 1000）；钻石抛 `IllegalArgumentException`。
- `exists(kind, tier)`：`LAVA` 与 `LEATHER` 组合为假，其余（4×5 − 1 = 19）为真；`all()` 按"档次先、液体后"的固定顺序返回这 19 个 `Tank(kind, tier)`。
- `itemId(kind, tier)`：`<tier>_<kind>_tank`，如 `leather_water_tank`、`iron_oil_tank`、`netherite_honey_tank`。
- `tier.allowedIn(backpack)`：`tier.ordinal() <= backpack.ordinal()`；`backpack` 为 null 时只放 `LEATHER`。

## FluidStore（不可变记录）

```java
public record FluidStore(Map<FluidKind, Integer> amounts, String oilFluid) {   // oilFluid: 锁定的原油流体 id，空串 = 未锁定
    public static final FluidStore EMPTY;
    int amount(FluidKind kind);                    // 缺省 0
    String lockedFluid();                          // 只对 OIL；没有锁定为 ""
    boolean isEmpty();
    FluidStore with(FluidKind kind, int mb, String fluidId);   // 内部用；mb ≤ 0 删除该项；OIL 排空时清除锁定
}
```

- **不变式**：每个数 ≥ 0；`oilFluid` 非空当且仅当 OIL 的数 > 0；其余种类没有锁定。读入时按不变式**清理**（负数当 0，OIL 为 0 时清锁定，OIL > 0 但锁定为空保持空——旧数据不会有，但不拒绝）。
- 上限不在记录里（容量由罐决定，记录不知道），超量的旧数据**不裁剪**。

### FluidStoreOps（纯函数，给 049/050 用，048 只测不接）

| 方法 | 规则 |
|---|---|
| `Deposit deposit(store, kind, fluidId, mb, capacityMb)` | 按容量放入：实际放入 `min(mb, capacityMb - amount)`（≥ 0）；OIL 已锁定且 `fluidId` 不同 → 放入 0、原因 `LOCKED_OTHER`；OIL 空时放入 > 0 → 锁定为 `fluidId`；返回新记录、实际放入量、原因（`OK / FULL / LOCKED_OTHER`） |
| `Drain drain(store, kind, mb)` | 取出 `min(mb, amount)`；OIL 取空时清锁定；返回新记录、实际取出量、该次取出的流体 id（OIL）、原因（`OK / EMPTY`） |
| `canAccept(store, kind, fluidId)` | OIL 已锁定且不同 → false；其余 true |

- 守恒：`deposit` 与 `drain` 前后，`amount(kind)` 的变化等于返回的实际量；其它种类不变。

## 编码（FluidStoreCodecs）

- **磁盘**（`Codec`）：`{ "water": 8000, … , "oil": 4000, "oil_fluid": "modern_industrialization:crude_oil" }` 的可选形式；写成一个对象，键是液体 id，数是整数，`oil_fluid` 是字符串；缺字段 = 空；未知键忽略；不合规的数当 0。在 `BackpackContents.CODEC` 里是 `optionalFieldOf("fluids", FluidStore.EMPTY)`。
  - 命令里写：`fluids:{water:8000}`（对象而不是 plan 之前设想的列表，便于写与读）。
- **网络**（`StreamCodec<ByteBuf, FluidStore>`）：VarInt 条数（≤ 5），每条 液体 ordinal（VarInt）+ 数（VarInt），再一个字符串（≤ 256 字符，`oilFluid`）；解码时做同样的清理。追加在 `BackpackContents.STREAM_CODEC` 的末尾；独立的 `BackpackFluidsPayload` 复用它。

## BackpackContents（改）

- 记录新增第 9 个分量 `FluidStore fluids`，放在 `rows` 之后、`dataRevision` 之前（磁盘 Codec 里 `fluids` 在 `extension_rows` 之后）。
- `normalize/fit/copy/withDataRevision/empty` 全部带上它；缺省 `FluidStore.EMPTY`；调用点（约 10 处）机械更新。
- 磁盘字段名 `fluids`；旧数据没有它，读作空；`data_revision` 2。

## TankPlacement（纯函数）

```java
public enum Verdict { OK, TIER_TOO_HIGH, DUPLICATE_KIND, STORE_EXCEEDS_CAPACITY }
public record Tank(FluidKind kind, BackpackTier tier) { }

static <T> Verdict check(
    PanelArea area,              // 目标区：MAIN 恒为 OK（不限制）；LEFT/RIGHT/BOTTOM 查档次；只有 BOTTOM 查互斥与容量
    T incoming,                  // 要放入的栈；不是罐 → OK
    int slotIndex,               // 目标格（替换时用）
    List<T> areaSlots,           // 目标区的整张存储列表
    Function<T, Tank> tankOf,    // 栈 → 罐（非罐返回 null）
    BackpackTier backpack,       // 背包档次，可为 null
    int storedMb);               // 背包里记着的该液体现量
```

判定顺序（第一个不成立的就是结果）：

1. 不是罐，或 `area == MAIN` → `OK`。
2. 罐档次 > 背包档次 → `TIER_TOO_HIGH`（LEFT/RIGHT/BOTTOM 都查）。
3. `area != BOTTOM` → `OK`（不生效，不查互斥与容量）。
4. 底部区里同液体的罐数 `before`（整张列表）；若目标格本就是同液体的罐，替换它：`after = before - 1 + 1`，否则 `after = before + 1`；`after > 1 && after > before` → `DUPLICATE_KIND`（与 `ToolAreaRules` 同样宽容：旧数据已有多个时只是不能再加）。
5. `storedMb > capacityMb(罐档次)` → `STORE_EXCEEDS_CAPACITY`。
6. `OK`。

## 物品与接线

- `TankItem extends Item`：字段 `kind`、`tier`；`stacksTo(1)`；`appendHoverText` 追加"容量 N mB"、"可放入 X 及以上的背包"。注册 19 个，id 见 `TankCatalog.itemId`；`ModItems.tank(kind, tier)` 取。
- `BackpackInventory`：`FluidStore fluids()`、`setFluids`；`BackpackTier tier()`（由 `baseCapacity` → `byCapacity`）；`BackpackTier tankTier(FluidKind)`：扫描底部区整张存储列表，返回该液体生效罐的档次或 null（给 049 用）。
- `ClientBackpackView`：`fluids()`、`setFluids()`、`tankTier(kind)`，会话关闭时清空。
- `BackpackFluidsPayload(FluidStore)`：S→C；`BackpackNetwork.sendFluids(player, inventory)`；会话打开时随标签、垃圾桶、黑名单一起发。

## TankBar（纯逻辑）

- `fillPixels(amountMb, capacityMb, heightPx)`：`amount <= 0 → 0`；否则 `max(1, round(heightPx × min(1, amount/capacity)))`；容量 ≤ 0 → 0。
- `tooltipKey(amountMb)`：0 → 空的键；否则带 液体名、当前 mB、容量 mB 的键。
- 常量：宽 2、高 14、格内偏移 (1, 1)、底色 `0xFF1A1A1A`。

## 状态转移

- 放入：客户端 `refusedByLimit` 预测并提示；服务端 `check` 判定，拒绝则 `sendSlotUpdate` 回滚并给动作栏提示；通过后沿用既有点击逻辑。
- 取下罐子：不改 `FluidStore`。
- 液量改变（049、050）：`FluidStoreOps` → `setFluids` → `saveInventory` → `sendFluids`；048 不触发。
