# Data Model: 面板合法尺寸

## PanelShape.Counts（纯记录）

`Counts(main, left, right, bottom)`：四个区域当前的格子数（与 `PanelLayout.of` 的 `counts` 同口径，顺序按 `PanelArea`）。值为 0 表示"还没有上报"。

## PanelShape（纯逻辑，无 Minecraft 依赖）

常量：`MIN_CELLS = 3`、`MAX_COLS = 9`。

| 函数 | 规则 |
|---|---|
| `validCols(counts)` | `{c ∈ [3, 9] : counts.main % c == 0 且 c + 2 ≤ counts.bottom}`，升序列表；`counts.main` 或 `counts.bottom` 为 0 时为空 |
| `maxRows(counts, cols)` | `min(counts.left, counts.right, counts.main / cols)` |
| `restricted(counts)` | `validCols` 非空且至少一个合法列数的 `maxRows ≥ 3`；否则为假（退回 016 的任意尺寸） |
| `isValidCols(counts, c)` | `c ∈ validCols(counts)`；不受限时恒真 |
| `isValidRows(counts, cols, r)` | `3 ≤ r ≤ maxRows(counts, cols)`；不受限时恒真 |
| `snapCols(counts, target)` | 合法列数中不超过 `target` 的最大者；没有则取最小合法列数；不受限时返回 `clamp(target, 3, 9)` |
| `snapRows(counts, cols, target)` | `clamp(target, 3, maxRows(counts, cols))`；不受限时返回 `max(3, target)` |
| `isValid(counts, cols, rows)` | `isValidCols && isValidRows` |

不变量：`snapCols`/`snapRows` 的结果恒合法（受限时）；对已经合法的输入是恒等（幂等）；对五个标准等级，`(3, 3)` 恒合法。

## 合法形状表（标准等级）

| 等级 | main | left/right | bottom | 合法列数 | 行数范围 |
|---|---|---|---|---|---|
| 皮革 | 36 | 4 | 11 | 3, 4, 6, 9 | 3–4 |
| 铁 | 54 | 6 | 11 | 3, 6, 9 | 3–6 |
| 金 | 72 | 8 | 11 | 3, 4, 6, 8, 9 | 3–8 |
| 钻石 | 90 | 10 | 11 | 3, 5, 6, 9 | 3–10 |
| 下界合金 | 108 | 12 | 11 | 3, 4, 6, 9 | 3–12 |

## FloatingPanelState 的变化

- 新增 `mainCount`（`setMainCount`），与已有的 `toolCount[]` 合起来构成 `Counts`。
- `updateResize`：目标列数合法才写 `visibleCols`；目标行数用写入后的列数判定，合法才写 `visibleRows`（Decision 3）。
- `layout()`：偏好与自动适应的结果统一落到合法尺寸（Decision 4）。
- `setSizePreference`、`FloatingPanelConfig` 不变。

## 状态转移（缩放）

| 事件 | 结果 |
|---|---|
| 拖动，目标列数合法 | `visibleCols = 目标` |
| 拖动，目标列数不合法 | `visibleCols` 不变 |
| 拖动，目标行数（按当前列数）合法 | `visibleRows = 目标` |
| 拖动，目标行数不合法 | `visibleRows` 不变 |
| 下一次 `layout()` | 偏好/自动适应结果落到合法值；屏幕不够大时取最大合法尺寸；3×3 仍压过屏幕过小 |
| 格子数变化（升级、换背包） | 下一次 `layout()` 落到合法值 |
| 不受限（没有合法形状 / 计数为 0） | 与 016 完全一致 |

## 箭头（渲染常量）

`ARROW_SIZE`：4 → 3（深色三角形行宽 1、3、5；外框行宽 1、3、5、7）；外框尖端位于区域边缘的最外一个像素。
