# 契约：客户端"全部"视图

## 合成视图装进单例（R4）

`ClientCombinedView` 收齐一个 epoch 的所有成员同步包后，调用 `ClientBackpackView.applyCombined(flatSlots, cursor)`：槽 = 各成员主区域按顺序拼接（平铺号）、`rowView = RowView.none(total)`、三个工具区长度 0、光标 = 内存光标、`activeRef = null`。具体标签时 `ClientBackpackView` 不变。

## 显示映射（R3）

`PanelLayout` 不改。`FloatingPanelState.searchSlots`（即 `mainMap`）在"全部"里始终非空：

| 情形 | `mainMap` |
|---|---|
| 默认视图 | `AllGrid.displayMap(memberSizes)`：各背包的格子首尾相连（不再每只从新的一行开始），无空位（-1 = 填充格，不可点、不画） |
| 整理后 | 排列表（显示位置 → 平铺号，不含空位；非空在前、空在后） |
| 搜索中 | 命中的平铺号，按当前显示顺序（默认或整理后）紧凑排列 |

列数变化（玩家改面板大小）→ 重算默认视图映射；排列表与搜索不依赖列数。

## 请求与点击翻译

输入层发送请求时槽号用**平铺号**（`PanelLayout.hit` 返回的 `index` 就是平铺号），附带 `ClientCombinedState.epoch`（具体标签为 0）。`ClientCombinedState.ready == false`（加载中）时不发任何面板请求。

## 灰格

`ClientPlacement.canPlace(view, slot, stack)`：当前是"全部"时，平铺号 → `AllGrid.locate` → 该成员的 `RowView`、`ItemTags` 走同一套 `ExtensionRules.canPlace`；仍然"只可能过宽、不会过严"。

## 染色、悬停提示、标签高亮（FR-023）

- 画每个有效格时先画淡染（成员颜色，约 20～25% 不透明度，`CombinedColors.of(memberIndex)`），在选中高亮与灰格之下、物品之上不叠；
- 悬停某格：提示最后追加一行"来自 ■编号（位置）"（空格也显示）；`ClientCombinedState.hoverMember` 置位，标签栏对应标签高亮；鼠标离开清除；
- 不画边框，不画分区标题行。

## "全部"里面板标题栏上的搜索与整理（R11）

- **不再有联合标题栏**：标签栏直接贴在面板上沿（`BARS_H = TAB_H`）。
- 面板自己的标题栏在"全部"里照常显示：拖动、关闭、搜索框（放大镜）、整理按钮不变；背包编号与快照按钮不画不响应；底部与左右区画成填充格、不响应。
- 搜索：`SearchState.Source` 换成"全部"版（`slotCount` = 平铺总数，`matcherOf(i)` = 平铺号 i 的物品匹配器，`hiddenLines` 汇总各成员的隐藏行）；语法与 `TermsCache` 不变。
- 整理按钮：`FloatingPanelInput` 里点击该按钮的分支在"全部"里改调 `AllSortPermutation.of(...)`（客户端排出排列表，不发任何包）；只在点整理与成员表变化（`epoch` 变）时重算；切到具体标签再回来保留；关闭联合界面清除。具体标签里仍发 `BackpackSortPayload`。

## 标签栏

`CombinedBarLayout` 去掉标题栏行（`BARS_H = TAB_H`），在最前加"全部"标签（固定宽度，不参与颜色表；悬停提示含联合背包档位）；"+n"未连接徽标放到标签栏右端；点击发 `SELECT_ALL`；选中状态由 `CombinedMembersPayload.all` 决定。光标规则的客户端侧只做体验：具体标签里光标非空、或携带物非空时不发切换（服务端仍权威）。

## 外部变化与错位修复（R7）

收到 `CombinedCellUpdatePayload`：`epoch` 与就绪的一致才应用到合成视图对应格并更新光标；否则丢弃。具体标签时单背包更新包的应用不变（服务端已保证不会发来非目标的更新）。
