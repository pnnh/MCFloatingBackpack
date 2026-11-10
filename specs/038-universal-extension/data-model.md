# Data Model: 通用扩展

没有新的存档数据、数据组件或网络字段。行里存的类别是字符串 `"general"`（035 已把行的类别存成字符串并保留未知类别）。

## 类别

| 名称 | 内容 |
|---|---|
| `ExtensionCategories.IDS` | 10 个分类类别（**不变**，有数据标签文件、`<id>_extension` 物品、标签预设名） |
| `ExtensionCategories.GENERAL` | `"general"` |
| `ExtensionCategories.ALL` | `IDS` 加 `GENERAL`（`general` 最后）；`orderOf`、`isKnown`、核对顺序用它 |

## 扩展件观测与生效计数（`PlacedPacks`，纯逻辑）

```text
Place  = LEFT | RIGHT | BOTTOM | CURSOR
Pack   = (category: String, tier: String|null, place: Place)     // tier 只有通用扩展有，如 "leather"
count(packs, backpackTier) -> Map<String, Integer>
```

规则：

1. `tier == null`（分类扩展）：每个 `+1` 到自己的类别，任何位置（含光标）；与 035/036 现在完全一样。
2. `category == "general"`：
   - 丢弃 `tier != backpackTier`（别档）、`backpackTier == null`（异常容量）、`place == BOTTOM`；
   - `left = 左区有 ≥1 个 ? 1 : 0`；`right = 右区有 ≥1 个 ? 1 : 0`；`cursor = 光标上有 ≥1 个 ? 1 : 0`；
   - `general = min(2, left + right + cursor)`；为 0 时不放进结果。
3. 结果与输入顺序无关。

不变量：`general ≤ 2`；分类类别的计数不受通用扩展影响；没有通用扩展时结果与改动前的 `countPlaced` 相同。

## 目标顺序（`ExtensionRules.targetSegments`）

`List<int[]>` 三段：

| 段 | 内容 |
|---|---|
| 0 | 匹配该物品的**类别行**（不含通用行）的格子，按行的先后 |
| 1 | 基础容量内的格子 `0..base-1` |
| 2 | 所有可见**通用行**的格子，按行的先后 |

没有可见扩展行时：`[空, 0..size-1, 空]`。

## 物品

| 物品 id | 类 | 档位 | 类别 |
|---|---|---|---|
| `leather_general_extension` | `UniversalExtensionItem` | LEATHER | `general` |
| `iron_general_extension` | 同上 | IRON | `general` |
| `gold_general_extension` | 同上 | GOLD | `general` |
| `diamond_general_extension` | 同上 | DIAMOND | `general` |
| `netherite_general_extension` | 同上 | NETHERITE | `general` |

`ModItems.UNIVERSAL_EXTENSIONS`：`LinkedHashMap<BackpackTier, DeferredHolder<Item, UniversalExtensionItem>>`，按档位顺序。

## 类别名

`CategoryNames.langKey(category)`：`general` → `category.mcfloatingbackpack.general`；其它 → `tag.mcfloatingbackpack.preset.<id>`（`PresetTags.langKey`）。

## 状态变化

- 通用扩展被放入、拿走、移到下区、同区第二个、升级换档 → 下一次保存/载入时 `placedPacks` 重算 → `RowReconciler` 增删行（035 的规则不变）。
- 行总数达到 35 时通用扩展仍可放入，但不新增行（核对器的 `refused`），不拒绝。
