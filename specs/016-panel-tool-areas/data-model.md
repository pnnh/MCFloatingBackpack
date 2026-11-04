# Data Model: 浮动面板的三个工具区

## PanelArea（纯枚举）

`MAIN(0)`、`LEFT(1)`、`RIGHT(2)`、`BOTTOM(3)`；线上一个字节，`fromByte` 对未知值返回 null。`isTool()` = 非 `MAIN`。

## ToolAreaCapacity（纯函数）

| 函数 | 规则 |
|---|---|
| `of(area, mainCapacity)` | `LEFT`/`RIGHT` = `ceil(mainCapacity / BackpackTier.COLUMNS)`（等级默认行数）；`BOTTOM` = `BackpackTier.COLUMNS + 2`；`MAIN` = `mainCapacity` |
| `fit(list, area, mainCapacity, empty)` | 返回长度 = `max(of(area, mainCapacity), 最后一个非空元素下标 + 1)` 的新列表：前缀按原顺序保留，不足补空；**不截断有物品的尾部** |

不变量：容量只取决于 `mainCapacity`，与面板当前可见行列数无关；`fit` 不会让物品总数变化。

## ToolAreaContents（记录，三个列表）

| 字段 | 内容 |
|---|---|
| `left` / `right` / `bottom` | `List<ItemStack>`，每项空格为 `ItemStack.EMPTY` |

- `CODEC`：三个 `ItemStack.OPTIONAL_CODEC.listOf()` 字段，各自 `optionalFieldOf(..., List.of())`；复制并规范化空栈。
- `STREAM_CODEC`：三个列表，每个上限 `MAX_TOOL_SLOTS = 64`，超限解码抛 `DecoderException`。
- `empty(mainCapacity)`：三个区各填满空格。

## BackpackContents（扩展）

`BackpackContents(items, cursor, tools)`：`tools` 是 `ToolAreaContents`；旧存档没有该字段时读成空列表，`fit(capacity)` 时按 `ToolAreaCapacity.fit` 补齐。现有的 `items`/`cursor` 行为不变；两参数的 `normalize/fit` 保留为便捷方法（`tools` 为空）。

## BackpackInventory（扩展）

在主 `items` handler 之外增加三个 `ItemStackHandler`（左/右/下），长度来自 `ToolAreaCapacity.fit` 后的列表；`fromContents/toContents` 读写它们。访问器：`handler(area)`、`getStackInSlot(area, slot)`、`setStackInSlot(area, slot, stack)`（越界忽略，同主格子）。主格子的 `size()`、`items()`、`getStackInSlot(int)` 不变。

## 点击与同步（见 contracts/storage-and-network.md）

- `BackpackClickPayload(area, slotIndex, swapHand, rightClick, screenOpen, panelOpen, sourceOrdinal, invIndex)`。
- `BackpackSlotUpdatePayload(area, slotIndex, stack, cursor)`。
- `BackpackFullSyncPayload` / `BackpackSessionPayload` 各带一份 `ToolAreaContents`。

## PanelLayout（客户端纯类）

**输入**（每次布局时给定）：

| 名称 | 含义 |
|---|---|
| `cols`、`rows` | 主区域当前可见列数（3..9）、可见行数（≥3） |
| `mainTotalRows` | 主区域的虚拟总行数（`ceil(主格子数 / cols)`） |
| `capacity[LEFT/RIGHT/BOTTOM]` | 三个工具区的格子数（来自同步下来的列表长度） |
| `scroll[MAIN/LEFT/RIGHT/BOTTOM]` | 四份滚动偏移（格） |
| `posX`、`posY` | 面板左上角 |

**输出**：

| 名称 | 公式 |
|---|---|
| `width` | `2·PAD_X + (cols + 2)·SLOT` |
| `height` | `PAD_TOP + (rows + 1)·SLOT + PAD_BOTTOM` |
| 主区域矩形 | `x = posX + PAD_X + SLOT`、`y = posY + PAD_TOP`、`cols × rows` 格 |
| 左区矩形 | `x = posX + PAD_X`、`y = 主区域 y`、`1 × rows` 格 |
| 右区矩形 | `x = 主区域 x + cols·SLOT`、`y = 主区域 y`、`1 × rows` 格 |
| 下区矩形 | `x = posX + PAD_X`、`y = 主区域 y + rows·SLOT`、`(cols + 2) × 1` 格 |
| 可见格数 | 主：`cols × rows`；左右：`rows`；下：`cols + 2` |
| 最大偏移 | 主：`max(0, mainTotalRows − rows)`；左右：`max(0, capacity − rows)`；下：`max(0, capacity − (cols + 2))` |
| `hit(x, y)` | 返回 `(area, 下标)`；主区域下标 = `(行 + scroll[MAIN])·cols + 列`；左右/下区下标 = `scroll + 可见序号`；落在填充格、箭头以外的空白、角落以外返回"无" |
| 填充格 | 左右/下区里 `scroll + 可见序号 ≥ capacity` 的可见位置：画底色、不可点击 |
| 箭头 | 每区 `canBack = scroll > 0`、`canForward = scroll < 最大偏移`；主与左右区的箭头朝上/下，下区朝左/右 |

不变量：面板是整体矩形，四块区域恰好铺满（主 + 左右列 + 下行），没有空角；最小尺寸 5×4；`cols`、`rows` 的夹取仍由 `FloatingPanelState` 负责（主区域下限 `MIN_VISIBLE_CELLS = 3`）。

## AxisScroll（客户端纯类）

`clamp(offset, total, visible)`、`canBack(offset)`、`canForward(offset, total, visible)`、`by(offset, delta, total, visible)`；`total ≤ visible` 时偏移恒为 0。

## 滚动状态的生命周期

| 事件 | 四份偏移 |
|---|---|
| 缩放或可见行列变化 | 夹取到合法范围（FR-015） |
| 某区格子总数变化（全量同步长度变化） | 该区偏移复位为 0 后夹取 |
| 关闭面板（`setVisible(false)`） | 全部复位为 0（FR-016） |
| 滚轮 | 只改悬停区域的偏移 |
| 物品被放入/取出 | 不变 |
