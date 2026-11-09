# Data Model: 统一的"背着的背包"查找

没有新增持久化或网络数据。下面是内存里的类型。

## 纯逻辑（`inventory/worn/`，不引用 Minecraft 类）

```java
interface WornSource<P, S> {
    int id();                       // = BackpackRef.Source 的序号，发布后不改
    int order();                    // 查找顺序，小的在前，与 id 无关
    boolean active();               // 查询时判断
    int slotCount(P player);
    S get(P player, int slot);      // 越界 → 该栈类型的"空"
    void set(P player, int slot, S stack);
    default boolean hiddenRender(P player, int slot) { return false; }
}

final class BackpackSearch<P, S> {
    enum Kind { MAIN_HAND, OFF_HAND, WORN, INVENTORY }
    record Hit(Kind kind, int sourceId, int slot, boolean hidden) {}
    interface Probe<P, S> {         // 手、物品栏、"是不是背包"
        S mainHand(P p); S offHand(P p);
        int inventorySlots(P p);    // 36
        S inventory(P p, int index);
        boolean isBackpack(S stack);
    }
    BackpackSearch(Probe<P,S> probe, int mainHandId, int offHandId, int inventoryId);
    void register(WornSource<P,S> source);   // 按 order 插入
    Hit firstWorn(P player);                 // 可为 null
    Hit resolve(P player);                   // 主手 → 副手 → 背着的 → 物品栏；可为 null
}
```

## Minecraft 一侧

- `WornBackpacks`：单例 `BackpackSearch<LivingEntity, ItemStack>`（渲染要对任意实体用，所以是 `LivingEntity`）；`first(LivingEntity)`（渲染用，返回 `Found(stack, hidden)`）；`resolvePlayer(Player)`、`get/set(Player, 来源序号, 格子)`（`BackpackRef` 用，参数是 `Player`，让 `BackpackRef` 的代码里不出现 `LivingEntity`，纯单元测试才能加载它）；`activeSource(int)`；`register(WornSource<LivingEntity, ItemStack>)`。
- `ChestWornSource`：`id = Source.CHEST.ordinal()`、`order = 0`、`slotCount = 1`、`get/set` 读写 `EquipmentSlot.CHEST`、`active = true`。
- `BackpackRef.Source`：`MAIN_HAND(false)`、`OFF_HAND(false)`、`CHEST(true)`、`INVENTORY(false)`（括号里是 `worn` 标志）；`isWorn()`。

## 不变式

- 来源 `id` 一旦发布不改；`order` 可以在中间插入而不影响 `id`。
- `firstWorn` 的结果与调用的是服务端还是客户端无关，只取决于已注册的来源与玩家身上的物品。
- 越界的格子（客户端伪造）读到空栈，不抛异常。
- 隐藏渲染的槽**仍然是**背着的第一只；渲染取不到可画的背包，**不改画后面的**。
