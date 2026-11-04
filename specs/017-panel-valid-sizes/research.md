# Research: 面板只允许"没有实心空格"的尺寸

结论来自读本仓库 016 完成后的源码（`FloatingPanelState` 的缩放与 `layout`、`PanelLayout`、`ClientBackpackView`、`FloatingPanelConfig`、`FloatingPanelRenderer` 的箭头）和用户的截图；**没有在游戏里验证**的地方在"仍不确定"里列出。

## Decision 1: "合法尺寸"由四个数决定，用一个纯函数判定

**Decision**: 新增无 Minecraft 依赖的 `PanelShape`，输入各区格子数 `main / left / right / bottom` 和一个尺寸 `(cols, rows)`，判定：

- 列数合法：`3 ≤ cols ≤ 9`，`main % cols == 0`（主区域没有半行），`cols + 2 ≤ bottom`（下区放得下可见格数）；
- 行数合法（对某个列数）：`3 ≤ rows ≤ min(left, right, main / cols)`（左右区只有 `left/right` 个格子；主区域总行数是 `main / cols`）。

**Rationale**: 实心格子只可能来自三处：主区域最后一行不满（`main % cols ≠ 0`）、左右区可见行数超过它们的格子数（`rows > left/right`）、下区可见格数超过它的格子数（`cols + 2 > bottom`）。三条条件正好覆盖，且与滚动位置无关（滚到最后一行也是满的）。这样判定**不依赖当前滚动位置**，只依赖格子数与尺寸，容易单元测试。

**Alternatives considered**: 在渲染里发现填充格才回退（太晚，已经出现了）；只限制主区域整除（左右区仍会出现）。

## Decision 2: 行数上限 = 左右区格子数，所以"整块显示"的形状不一定合法

**Decision**: 不改容量规则。例如 36 格背包 3 列共 12 行，但左右区只有 4 格，所以 3 列只能显示 3～4 行（用户举例的 3×12 因此不合法）；其余行用滚动。

**Rationale**: 用户的原则是"不允许实心格子"，让左右区的容量跟着可见行数变化会改变 016"容量只取决于默认尺寸"的规则，属于另一个决定，不在本阶段（spec 的 Out of Scope）。

**各等级的合法形状**（`cols` 整除 `main`，`cols ≤ 9`；`rows ≤ 默认行数`）：皮革 36：列 {3,4,6,9}、行 3–4；铁 54：{3,6,9}、3–6；金 72：{3,4,6,8,9}、3–8；钻石 90：{3,5,6,9}、3–10；下界合金 108：{3,4,6,9}、3–12。

## Decision 3: 拖动时按维度各自判断，不合法就整次移动不生效

**Decision**: `FloatingPanelState.updateResize` 仍用"相对抓取点的增量"算出目标 `(cols', rows')`（夹到 3..9 和 ≥3），然后：目标列数合法才写入 `visibleCols`，否则保持；目标行数用**写入后的列数**判定，合法才写入 `visibleRows`，否则保持。没有合法形状（Decision 7）时不做限制。

**Rationale**: 用户要求"目标尺寸不合法时直接返回，直到继续拖动、目标合法才调整"；保持"当前值"就是上一个合法尺寸，所以拖动时面板会在合法尺寸之间跳，这是用户已接受的代价。列、行各自判断，让对角拖动时一个方向被挡住不影响另一个方向。

**Alternatives considered**: 把目标吸附到最近的合法尺寸（手感更连续，但不是用户要的"忽略"）；整个尺寸要么都合法要么都不动（对角拖动时容易两个方向都卡住）。

## Decision 4: `layout()` 把偏好、自动适应、屏幕限制都落到合法尺寸

**Decision**: 在 `layout(screenW, screenH)` 里，原来算出 `colsUpper = max(3, min(9, fitCols))`、`rowsUpper = max(3, min(totalRows, fitRows))` 之后加一步：若有合法形状，则
- 列：`visibleCols = snapCols(clamp(偏好或 colsUpper, 3, colsUpper))`——取**不超过目标的最大合法列数**，没有则取最小合法列数；
- 行：`rowsUpper' = min(rowsUpper, maxRows(visibleCols))`，`visibleRows = clamp(偏好或 rowsUpper', 3, rowsUpper')`。

这一处同时覆盖：保存的偏好（`FloatingPanelConfig` 读出的任意值）、换背包/升级后格子数变化、自动适应（没有偏好时取"能放下的最大合法尺寸"）、屏幕变小。

**Rationale**: 016 的 `layout()` 本来就每 tick 把偏好夹进屏幕允许的范围，是所有入口的汇合点，在这里落到合法尺寸最不容易漏。`MIN_VISIBLE_CELLS = 3` 仍然压过屏幕过小的情况；标准等级的 3×3 总是合法（36…108 都能被 3 整除，行数 3 ≤ 默认行数）。

**Alternatives considered**: 只在 `updateResize` 里判断（加载偏好、升级、换背包会漏）；在每个入口各写一份（容易漏）。

## Decision 5: 格子数由客户端视图上报给状态

**Decision**: `FloatingPanelState` 新增 `setMainCount(int)`（和已有的 `setToolCount` 一样保存各区格子数），`ClientBackpackView.applyFull`/`clear` 一并上报；`layoutModel` 与 `PanelShape` 用同一份计数。`FloatingPanelState` 仍是不依赖 `ClientBackpackView` 的纯类。

**Rationale**: 状态类需要知道格子数才能判定合法，但它不能依赖含 `ItemStack` 的视图类（单元测试不起 Minecraft）。已有 `setToolCount` 的先例，沿用。上报前（计数为 0）判定函数认为"没有合法形状"，退回旧行为，所以现有布局测试不受影响。

## Decision 6: 配置文件格式不变

**Decision**: `FloatingPanelConfig` 仍保存 `visibleRows/visibleCols`；加载后由 `layout()` 落到合法值，之后的保存写的是合法值。

**Rationale**: 旧配置里的非法值（例如铁背包 7 列）不需要迁移，下一次布局自然修正（FR-003、SC-004）。

## Decision 7: 没有合法形状时退回 016 的行为

**Decision**: `PanelShape.restricted(counts)` 为假——也就是没有任何合法列数，或对所有合法列数都没有 ≥ 3 的合法行数，或计数还没上报——时，`updateResize` 与 `layout()` 不做合法性限制；016 的"填充格不绘制"代码保留作为兜底。

**Rationale**: spec FR-005：非标准容量不能让缩放失效或卡住。保留填充格绘制是最小的兜底。

## Decision 8: 箭头缩到 7×4 像素并贴边

**Decision**: `FloatingPanelRenderer.ARROW_SIZE` 由 4 改为 3：深色三角形宽 1、3、5，浅色外框宽 1、3、5、7（共 4 行，最大 7 宽 × 4 深）；外框尖端放在区域边缘的最外一个像素（顶部/左侧是 `r.y()`/`r.x()`，底部/右侧是 `r.y() + r.h() - 1`/`r.x() + r.w() - 1`），深色三角形尖端在外框尖端内侧 1 像素。箭头出现的条件、不参与命中、颜色不变。

**Rationale**: spec FR-007（≤ 7×4，间隙 ≤ 1 像素）；原来是 9×5 且尖端距边缘 1 像素。用现有的 `drawTriangle` 只改常量与两处起点，不改结构。

## Decision 9: 测试策略

**Decision**: 纯逻辑单元测试：`PanelShape`（五个等级的合法列数与行数范围、`snapCols/snapRows`、没有合法形状时退回、随机输入下吸附结果恒合法且幂等）；**穷举测试**：对每个等级、每个合法 `(cols, rows)`、每个区域的每个滚动偏移，用 `PanelLayout` 检查所有可见格子的 `slotIndex ≥ 0`（SC-002：没有实心格子）；状态测试：`updateResize` 目标不合法时尺寸不变、合法时才变、列行各自判断；`layout()` 把非法偏好落到合法尺寸、屏幕变小时取最大合法尺寸。箭头与手感只能在客户端会话里验证（宪法 V）。

## 仍不确定 / 需要游戏内确认

- 面板在合法尺寸之间"跳"的手感，尤其是铁背包（列只有 3、6、9）；用户已接受，但实际拖动时是否太别扭要看。
- 屏幕很小时，"最大合法尺寸"是否仍能让面板有用（最小仍是 3×3）。
- 缩小后的箭头是否仍清晰可辨、尖端贴边后与格子边框的叠放（边框像素会被外框盖住）。
- 旧配置文件落到合法值后，玩家"放大再缩小"会不会觉得尺寸记不住（`layout()` 把偏好改写成合法值，和 016 之前夹取偏好的行为一致）。
