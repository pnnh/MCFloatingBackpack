# Data Model: 分类扩展：界面

没有新的存档数据、数据组件或网络字段。下面是界面层的内存模型（全部来自 035 已同步的信息）。

## 输入（035 已有）

| 名称 | 来源 | 内容 |
|---|---|---|
| `RowView` | `BackpackSessionPayload` / `BackpackFullSyncPayload` → `ClientBackpackView.rowView()` | `baseCapacity`；`visibleCategories`（可见行的类别，按行的先后）；`hidden`（每类别 `nonEmptyCells`） |
| 主区域格子 | 同上 → `ClientBackpackView.slots` | 基础容量格 + 可见行的格子，一个平面列表；`slotCount()` 是它的长度 |
| `ItemTags` | `BackpackTagsPayload` → `ClientBackpackView.tags()` | 这只背包的手动标签（给 `ItemCategoryMatcher` 用） |
| 物品标签 | 原版 `TagsUpdate` | `mcfloatingbackpack:extension/<类别>`（客户端注册表里） |

## 新增的纯逻辑模型

### 组（Group）——`RowLayout`

主区域下标 → 组：

| 值 | 含义 |
|---|---|
| `-1` | 基础容量内的格子（普通主网格） |
| `k`（0…n-1） | 第 k 个可见扩展行，下标区间 `[base + 9k, base + 9k + 9)` |
| `-2` | 不属于任何可见行（下标越界；不画、不可点） |

`RowLayout(baseCapacity, visibleCategories)` 是不可变值：

- `groupOf(index)` → 上表
- `categoryOf(index)` → 该格所在行的类别 id，基础容量内与越界返回 `null`
- `isRowStart(index)` → 是某扩展行的第一格
- `topEdge(index, cols)` → `index - cols` 与 `index` 不在同一组且 `groupOf(index) >= 0`
- `leftEdge(index, cols)` → `index % cols != 0` 且 `index - 1` 与 `index` 不在同一组且 `groupOf(index) >= 0`
- `hasRows()` → 有没有可见行（没有时所有绘制分支都不进入）

不变量：`groupOf` 对下标单调不减；`topEdge`/`leftEdge` 只对扩展行里的格子为真（主网格的格子从不画线）；`cols ≥ 1`。

### 类别样式（`CategoryStyle`，客户端常量表）

每个类别 id：`fill`（底色 ARGB，半透明）、`edge`（分隔线与角标色 ARGB，不透明）、`ghostItem`（幽灵图标用的扩展件物品）。覆盖 `ExtensionCategories.IDS` 全部 10 个，未知类别（035 保留未知 id 的行）回退到一个中性样式，不抛异常。

### 隐藏提示（`HiddenHint`）

`HiddenHint.lines(List<HiddenSummary>)` → `List<Line(category, cells)>`：只含 `cells > 0` 的类别，按固定类别顺序（未知 id 在最后）；`isEmpty()` 即"不画黄点"。判定口径与 035 的 `RowSet.hasHiddenItems` 一致（有格有东西才算）。

### 客户端放入判定（`ClientPlacement`，客户端）

`canPlace(view, slot, stack)`：

1. 没有扩展行（`!layout.hasRows()`）或 `slot < base` → `true`；
2. 标签未就绪 → `true`（放行，服务端裁判）；
3. 否则 `ExtensionRules.canPlace(view.rowView().visibleCategories(), base, slot, stack, new ItemCategoryMatcher(view.tags()))`。

与服务端 `BackpackInventory.canPlaceMain` 唯一的差别是第 2 步的放行。

## 状态变化

- **主区域格子数变**（行增减）：`applyFull` → `reportCounts()`（`setMainCount(baseCapacity())`、`setTotalRows(rows())`）→ 合法尺寸不变，滚动由 `clampScroll` 收敛，**不归零**（除非基础容量变）。
- **隐藏摘要变**：随下一次 `RowView` 到达而变，黄点立即跟随（没有客户端缓存）。
