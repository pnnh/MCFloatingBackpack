# Data Model: 分类扩展：数据与规则

## 常量（`inventory/extension/ExtensionCategories`，纯逻辑）

```java
final class ExtensionCategories {
    static final List<String> IDS = List.of(
        "food", "tool", "weapon", "armor", "ore", "crop", "wood", "potion", "redstone", "disc"); // 顺序固定，发布后不改
    static final int ROW_CELLS = 9;        // 一行 9 格，与档位无关
    static final int MAX_ROWS = 35;        // 可见 + 隐藏，所有档位统一
    static final int MAX_TIER_CAPACITY = 108;
    static final int MAX_MAIN_SLOTS = MAX_TIER_CAPACITY + MAX_ROWS * ROW_CELLS;   // 423
    static boolean isKnown(String id);
}
```

类别 id 同时是：行的类别、背包标签册里的手动标签、数据标签路径 `mcfloatingbackpack:extension/<id>` 的最后一段。

## 行与行集合（纯逻辑，泛型）

```java
record Row<T>(String category, List<T> cells) {}          // cells.size() == 9；category 可以是未知 id（保留，不丢）

record RowSet<T>(List<Row<T>> visible, List<Row<T>> hidden) {
    static <T> RowSet<T> empty();
    int total();                                           // visible + hidden
    int visibleCells();                                    // visible.size() * 9
    int visibleOf(String category);   int hiddenOf(String category);
    List<String> visibleCategories();                      // 可见行的类别序列（"布局"的标识）
    List<HiddenSummary> hiddenSummary(Predicate<T> isEmpty);   // 按类别（类别顺序）：HiddenSummary(category, 非空格数)；没有隐藏物品就是空列表
}
```

不变式（`RowSet` 的构造与 `sanitize` 保证）：每行恰好 9 格；`visible + hidden ≤ 35`；`hidden` 里的行**一定非空**（空行在隐藏时直接删除）；可见行可以是空的。

### 核对

```java
final class RowReconciler {
    record Result<T>(RowSet<T> rows, boolean layoutChanged, List<String> refused) {}
    static <T> Result<T> reconcile(RowSet<T> rows, Map<String, Integer> placed,
                                   Predicate<T> isEmpty, Supplier<T> emptyCell, int maxRows);
}
```

状态转移见 [contracts/rules.md](./contracts/rules.md)。

### 规则

```java
final class ExtensionRules {
    /** 一格能不能放这个物品：基础容量内恒为真；可见行看类别；隐藏行与越界为假。 */
    static <T> boolean canPlace(RowSet<T> rows, int baseCapacity, int slot, T stack, CategoryMatcher<T> matcher);

    /** 一个物品的目标格顺序：先是匹配的可见行（按行的顺序、每行 9 格），再是 0..baseCapacity-1。 */
    static <T> List<int[]> targetSegments(RowSet<T> rows, int baseCapacity, T stack, CategoryMatcher<T> matcher);

    /** 放着的数量变成 placedAfter 时，需要新建几行（超出现有行数的部分），不超过 maxRows 才算放得下。 */
    static <T> boolean fits(RowSet<T> rows, Map<String, Integer> placedAfter, int maxRows);

    /** 某个主区域下标属于哪一行；基础容量内返回 -1，越界或隐藏返回 -2。 */
    static int rowOfSlot(int baseCapacity, int visibleRowCount, int slot);
}

interface CategoryMatcher<T> {
    boolean matches(String category, T stack);                // = dataMatches || manualTagged
}
```

`targetSegments` 返回两段：第一段是匹配行的下标（可能为空），第二段是基础容量内的下标；调用方对每段各调一次 `QuickTransferLogic.moveInto`，剩余带到下一段。

## 游戏一侧

### `ExtensionPacks`（"扩展件解析器"，适配类）

```java
final class ExtensionPacks {
    static Optional<String> categoryOf(ItemStack stack);   // 035 里恒为空（没有扩展件物品）
    static void register(Predicate<ItemStack> isPack, Function<ItemStack,String> category);   // 037 注册
}
```

### `ItemCategoryMatcher`（适配类）

构造参数是这只背包的 `ItemTags`；`matches(category, stack)`：`stack.is(TagKey(mcfloatingbackpack:extension/<category>))`，食物另加 `FOOD` 组件，药水另加 `POTION_CONTENTS` 组件，或 `tags.tagsOf(registryId).contains(category)`。

### `BackpackContents`（改）

```java
record BackpackContents(
    List<ItemStack> items,          // 档位容量那么长，与原来一样
    ItemStack cursor,
    ToolAreaContents tools,
    ItemTags tags,
    TrashContents trash,
    PickupBlacklist pickupBlacklist,
    RowSet<ItemStack> rows)         // 新：可见行 + 隐藏行
```

- 磁盘：`optionalFieldOf("extension_rows", RowSet.empty())`，旧背包读成空。
- 网络：手写 `StreamCodec`（前 6 段与原顺序一致，最后是 `rows`）。
- `normalize/fit/copy/empty` 都带上 `rows`；`fit(capacity)` 不动 `rows`（行与档位容量无关）。

### `BackpackInventory`（改）

| 成员 | 变化 |
|---|---|
| `MAX_SIZE` | 删除，拆成 `MAX_TIER_CAPACITY`、`MAX_MAIN_SLOTS` |
| `baseCapacity()` | 新：档位容量 |
| `size()` | 不变的含义"主区域格数"，现在是 `baseCapacity + 可见行 × 9` |
| `hiddenRows()` | 新：隐藏行（不在处理器里） |
| `visibleRowCategories()` | 新：给客户端同步与 `layoutChanged` 比较 |
| `mainTargetOrder(stack, matcher)` | 新：返回两段目标下标（见 `ExtensionRules.targetSegments`） |
| `canPlaceMain(slot, stack)` | 新：用自己的标签册构造 `ItemCategoryMatcher` |
| `fromContents(contents, capacity)` | 载入时核对；处理器长度 = 容量 + 可见行 × 9 |
| `toContents()` | 保存时核对；拆回 `items`（前 `baseCapacity` 格）、`rows.visible`、`rows.hidden` |
| `ToolAreaCapacity.of(area, …)` 的实参 | 改用 `baseCapacity` |

## 客户端 `ClientBackpackView`（改）

新增只读字段：`baseCapacity`、`visibleRowCategories`（≤35）、`hiddenSummary`（≤35 项）。035 不用它们改布局；`applyFull` 里工具区容量回退改用 `baseCapacity`。

## 数据文件

`data/mcfloatingbackpack/tags/item/extension/<id>.json`（10 个）：

```json
{
  "replace": false,
  "values": [
    { "id": "#c:crops", "required": false },
    { "id": "#c:seeds", "required": false },
    { "id": "#minecraft:saplings", "required": false }
  ]
}
```

每个类别的内容见 spec 决策 4 的并入关系表；食物与药水另有组件判定（不在文件里）。红石类别的显式元件清单按 spec 保持现状：`minecraft:repeater comparator piston sticky_piston observer lever hopper dropper dispenser redstone_lamp redstone_torch daylight_detector target`，并引用 `#c:dusts/redstone`、`#c:storage_blocks/redstone`（`required: false`）。

## 与已有数据的关系

- `ItemTags`（018）：每只背包一份，类别判定的第二个来源，**只读**，035 不改它的结构。
- `PresetTags`：追加 `crop`、`potion`、`disc`。
- `ToolAreaContents`：不改；扩展件占用的是工具区格子，行数由它们核对出来。
- `PickupBlacklist`（024）：与标签共用名字（`food` 同时是黑名单可填的标签），既有行为，不改。
