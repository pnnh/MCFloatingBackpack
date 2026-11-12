# 数据模型：更多分类扩展

没有新的存储字段。变化只有三处。

## 类别表（`ExtensionCategories`）

| 序 | id | 来源 |
|---|---|---|
| 1–10 | food tool weapon armor ore crop wood potion redstone disc | 035（不动） |
| **11–19** | **building natural plant decor mobdrop storage transport enchant meat** | 本 feature，追加 |
| 末 | general | 038（`ALL` 的最后一个，不在 `IDS`） |

- `IDS` 19 个，`ALL` = `IDS` + `general` = 20 个；顺序一旦发布固定，只追加。
- 每个 id ≤ 16 字符（`MAX_CATEGORY_LENGTH`）：最长的 `transport` 9 个。
- 行上限 `MAX_ROWS = 35`、每行 9 格：不变。

## 存储

- `extension_rows` 里每行的类别 id 是字符串：新 id 直接存，旧版本按"不认识的类别"保留（035）。
- 扩展件物品 `<id>_extension`：无数据组件、堆叠 1；旧版本读到是未知物品 → `DataRevision.CURRENT` **2 → 3**（research R6）。
- 快照（030）与降级拦截（040/041）按已有路径，不改。

## 与其它表的对应关系（一个 id 一行）

| 表 | 位置 | 新增 |
|---|---|---|
| 数据标签 | `data/mcfloatingbackpack/tags/item/extension/<id>.json` | 9 个 |
| 预设标签 | `PresetTags.IDS` | 8 个（`plant` 已有） |
| 样式 | `CategoryStyle.EDGE` | 9 行 |
| 物品 | `ModItems`（按 `IDS` 循环） | 自动 9 个 |
| 语言 | `tag.mcfloatingbackpack.preset.<id>`、`item.mcfloatingbackpack.<id>_extension` | 8 + 9 个键 ×2 语言 |
| 模型、贴图、配方 | `<id>_extension` | 9 + 9 + 9 |

## 匹配（`ItemCategoryMatcher`）

`matches(category, stack)`：数据标签 ∨（`food`：食物组件）∨（`potion`：药水内容）∨（**`enchant`：`STORED_ENCHANTMENTS`**）∨ 手动标签。其余新类别只看数据标签与手动标签。
