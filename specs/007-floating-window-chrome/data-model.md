# Data Model: 007-floating-window-chrome

本功能不新增任何服务端数据结构或网络负载（见 [research.md](./research.md) Decision 1/2）。以下是对 spec.md 中两个 Key Entity 的具体字段落实，全部落在既有的客户端类上。

## 浮动面板窗口状态 → `FloatingPanelState`（既有单例，client-only）

### 既有字段（不变）

| 字段 | 类型 | 说明 |
|---|---|---|
| `posX`, `posY` | int | 面板左上角位置（已持久化） |
| `width`, `height` | int | 面板像素尺寸，由行列数派生 |
| `totalRows` | int | 当前背包的总行数，随背包等级变化（`BackpackTier.rows()`） |
| `visibleRows` | int | 当前可见行数，此前完全由屏幕可用空间自适应算出 |
| `scrollRow` | int | 纵向滚动偏移 |
| `dragging` / `scrolling` | boolean | 当前是否处于拖动位置 / 拖动纵向滚动条 |

### 新增字段

| 字段 | 类型 | 说明 | 约束 |
|---|---|---|---|
| `visibleCols` | int | 玩家设置的列数，同时也是格子重新排布时每行的列数 | `3 <= visibleCols <= min(PanelSlots.COLS, 当前屏幕可用空间算出的列上限)` |
| `resizing` | boolean | 是否正处于拖拽缩放手柄 | 与 `dragging`、`scrolling` 三者互斥（同一时刻最多一个为 `true`） |
| `resizeGrabOffsetX` / `resizeGrabOffsetY` | int | 开始拖拽缩放时记录的鼠标相对偏移 | 仅 `resizing == true` 时有效，与现有 `dragOffsetX/Y`、`scrollGrabOffsetY` 同一模式 |

**说明（2026-11-02 第二次澄清后更正）**：不新增 `totalCols`、不新增 `scrollCol`、不新增任何横向滚动字段。调整列数不是开一个独立可横向滚动的维度，而是把格子按新的 `visibleCols` 重新排布：既有的 `totalRows` 字段改为"按当前 `visibleCols` 换算出的虚拟总行数"（`ceil(背包容量 / visibleCols)`），`visibleCols` 变化时调用既有的 `setTotalRows(...)` 重新赋值即可，纵向滚动（`scrollRow`、`visibleRows`）沿用现状，不需要改动。

### 状态转换（resizing）

```
idle --(鼠标左键在 hitsResizeHandle() 命中区域按下)--> resizing
resizing --(鼠标移动)--> resizing（持续调用 updateResize：
    - 水平位移 → 更新 visibleCols，并立即重算 totalRows = ceil(背包容量 / visibleCols)
    - 垂直位移 → 更新 visibleRows
    - 两者都会连带触发现有的 clampScroll() 把 scrollRow 夹到新范围内）
resizing --(鼠标左键释放)--> idle（调用一次 FloatingPanelConfig.saveFrom(state) 持久化）
```

与现有 `dragging`、`scrolling` 的状态转换完全同构，三者共享"同一时刻只能有一种拖拽交互生效"的既有约束（`beginDrag` 已经会把 `scrolling` 置为 `false`，`beginScrollDrag` 已经会把 `dragging` 置为 `false`；`beginResize` 需要同样把另外两者置为 `false`，反之亦然）。

## 背包物品栏 → 既有 `BackpackInventory`（服务端）/ `ClientBackpackView`（客户端镜像）

不新增字段。本功能只读取客户端已有的 `ClientBackpackView.get().slots().size()`（等于当前打开背包的容量）用于标题栏等级反查：

```
容量(slots().size()) → BackpackTier（按 BackpackTier.values() 的 capacity() 匹配）→ tier.id() → "item." + MOD_ID + "." + tier.id() 翻译 key → 标题栏文字
```

该映射是纯函数、无状态，不需要缓存或持久化，每次渲染标题栏时按当前 `ClientBackpackView` 内容现算即可（渲染频率与现有 `layout()` 相同，一帧一次，开销可忽略）。

## `PanelSlots` 改动的纯函数（无状态，不属于数据模型，但影响上面字段的读取方式）

> 2026-11-02 第二次澄清后更正：不新增横向滚动条相关函数，也不新增 `isColVisible`。列数变化通过"重新排布 + 复用纵向滚动条"实现。

- `slotX`、`slotY`：列计算从固定常量 `COLS` 改为读取 `state.getVisibleCols()`；`col = index % visibleCols`、`row = index / visibleCols`。
- `indexAt`：同样改用 `state.getVisibleCols()` 做行列换算；可见性判断仍只用既有的 `isRowVisible`（纵向裁剪），不需要新增列向裁剪，因为一行里显示的列数本来就等于 `visibleCols`，不存在"一行内部分列可见、部分列不可见"的情况。
- 滚动条相关函数（`scrollbarTrackY`、`scrollbarThumbHeight`、`scrollbarThumbY`、`hitsScrollbar`、`hitsScrollThumb`、`scrollRowAt`）：不改动，继续只服务纵向滚动，输入的 `totalRows` 由 `FloatingPanelState` 在 `visibleCols` 变化时重新计算后传入。

`hitsResizeHandle(mouseX, mouseY)` 不在 `PanelSlots` 里新增，而是和 `hitsDragBar`/`hitsBody`/`contains` 一样作为 `FloatingPanelState` 的实例方法（面板右下角固定像素大小的命中区域，写法比照既有 `hitsDragBar`），与 research.md Decision 4 的落点保持一致。
