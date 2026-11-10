# Contract: 客户端预测

## 1. 规则来源

唯一的规则是 035 的 `ExtensionRules.canPlace(visibleCategories, baseCapacity, slot, stack, matcher)`（本 feature 加的"类别列表"重载；`RowSet` 版本委托给它，输出不变）与 `ItemCategoryMatcher`。客户端不得另写类别判断。

## 2. 客户端放行条件（只会多放行，不会误拦）

`ClientPlacement.canPlace` 在下列任一条件下返回 `true`，不再往下判：

1. 面板没有可见扩展行；
2. `slot < baseCapacity`（普通主网格）；
3. 物品栈为空（空栈不"放入"）；
4. 类别标签还没绑定到客户端的物品注册表（刚进世界、刚换数据包）；
5. `RowView` 与格子列表对不上（格子数 ≠ 基础容量 + 9 × 可见行数）——说明正在同步中。

只有以上都不成立时才调 `ExtensionRules.canPlace`。

## 3. 接入点

| 手势 | 位置 | 做法 |
|---|---|---|
| 点击放入（含 Alt 与主手交换） | `FloatingPanelInput.sendClick(MAIN, slot, …)` | `held` 与服务端读同一个东西（光标；Alt 且没开容器屏时是主手物品）；`held` 非空且 `!canPlace` → 不发包，返回；空手拿起永远不拦 |
| 拖拽分配（013） | `PanelDragGesture.beginPress/advance` 增加"接受谓词" | 选格时 `accepts(slot, cursor)` 不过的格子**不加入** `selected`（与服务端 `distribute` 的过滤一致）；预览只含接受的格子 |
| 悬停高亮 | `drawSlotsWithItems` | 光标非空且 `!canPlace(hoveredSlot, cursor)` → 红色高亮 |

服务端仍然是最终裁判：以上任何一处客户端判断出错，结果最多是"多发了一个会被服务端拒绝的请求"（和过渡期一样），不会丢失或复制物品。

## 4. 一致性要求（可测）

- `ExtensionRules` 新重载与旧 `RowSet` 版本对同一输入答案相同（`ExtensionRulesCategoriesTest` 用随机/穷举的类别序列对比）。
- 拖拽手势在谓词恒为真时与改动前**逐步等价**（`PanelDragGestureTest` 原有用例原样通过）。
- `PanelDragGestureAcceptTest`：谓词拒绝的格子不进入 `selected`、不进入预览；只剩一个被接受的格子时 `release` 给出 `CLICK` 那一格；一个都没有被接受时按 `slotUnderCursor` 退回 `CLICK`（交给 `sendClick` 的预判去拦）。

## 5. 不做预测的手势

选区、Ctrl 转出、聚拢、垃圾桶、Ctrl 转入（目标由服务端选）——见 research D3。它们的回归验证在 quickstart。
