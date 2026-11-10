# Research: 分类扩展：界面

2026-11-10，读本仓库源码得出。标"已核对"的是读过代码确认的；标"待游戏里验证"的写进 quickstart，验证前不得声称已满足。

## D1. 几何不用改：主区域是平面列表，扩展行只是末尾的下标区间

- **已核对**：`PanelLayout` 按 `cols`、`scroll` 与每个区域的格子数给格子定位；主区域下标 `index = (行 + 滚动) * cols + 列`，超出 `counts[MAIN]` 的是"填充格"，**渲染时不画**（`drawSlotsWithItems` 里 `index < 0` 直接 `continue`，留面板底色）。`slotIndex/cellX/cellY/hit` 都只依赖总格数。
- 035 已定：主区域格子编号 = 基础容量格 + 各可见行的格子（按行的先后），所以"第 k 个扩展行"就是下标区间 `[base + 9k, base + 9k + 9)`。
- **决定**：不动 `PanelLayout`。新增纯逻辑 `RowLayout` 按下标回答"属于哪一组"。列数变化、滚动、折行全由原有几何处理，行归属只看下标，所以不会因为折行而错位。
- 最后一行不满时，尾部是"不画的填充格"，不是 017 说的"点不动的实心格"（017 的填充格就是这种不画的空白；017 的合法尺寸机制是为了**没有**这种空白）。规格 FR-005 的"不出现实心填充格"在这里成立：空白不是实心格。

## D2. 合法尺寸：问题出在 `setMainCount(slots.size())`

- **已核对**：`FloatingPanelState.counts()` 用 `mainCount` 作 `PanelShape.Counts.main`；`ClientBackpackView.reportCounts()` 调 `setMainCount(slots.size())`，而 `slots` 含可见扩展行，所以过渡期合法尺寸随扩展行变化（036 交付说明里的"45 格 → 合法列数 3、5、9"）。
- **决定**：`reportCounts()` 改成 `setMainCount(baseCapacity())`。`baseCapacity()` 是 036 加的（`RowView.baseCapacity`，没有 `RowView` 时回退到 `slots.size()`）。
- 其余用到 `totalRows` 的地方（`state.setTotalRows(view.rows())`、`clampScroll`、`layout()` 里的 `rowsUpper`）要的是**含扩展行的总行数**（滚动范围必须包含扩展行），保持不变。`PanelShape.maxRows` 用 `main/cols`，改成基础容量后，行数上限和没有扩展件时完全一样 → 用户拖好的形状不会被改（SC-003）。
- **`applyFull` 的滚动归零**：现在 `sizeChanged = count != slots.size()` 时 `setScrollRow(0)`，放上或拿走扩展件（格子数变）会让面板跳回顶部。改成 `baseCapacity` 变化才归零；扩展行增减只靠 `setTotalRows → clampScroll` 收敛。
- `slotCount()` 其余调用点逐一核对：`FloatingPanelInput:197`、`PanelDragInput:188`、`PanelSelectionInput:126/162`、`QuickTransferInput:165`、`DeleteKeyInput:50`、`TrashActions.dumpSlots`、`FloatingPanelEvents:625`（选区上下文）——它们都是"这个下标是不是一个真实格子"，需要含可见扩展行（隐藏行不在列表里），**保持 `slotCount()`，不改**。

## D3. 客户端预测只在两处有意义（点击放入、拖拽分配）

逐个手势核对（是否会把东西**放进**某个扩展行格子）：

| 手势 | 放进格子吗 | 现状 | 结论 |
|---|---|---|---|
| 点击（光标上有物品，含 Alt 与主手交换） | 是 | `sendClick` 直接发包，服务端 `canPlaceMain` 不过就零变化并回传槽位 | **加预判** |
| 拖拽分配（013，光标上有物品） | 是 | `PanelDragGesture` 选格只看 `isValidDragSlot`；服务端 `distribute` 用 `canPlaceMain` 过滤 | **加接受谓词** |
| 拖拽聚拢 / 双击聚拢 | 否（从格子拿到光标） | 无规则 | 不改，回归验证范围 |
| 选区（014）→ 转出 | 否 | 选的是有物品的格子 | 不改 |
| Ctrl 转出（012，`TO_INVENTORY`） | 否 | 服务端选目标 | 不改 |
| Ctrl 转入（011，`TO_BACKPACK`，在容器屏里从物品栏转进背包） | 是，但目标由服务端 `insertIntoMain` 选 | 客户端只发物品栏下标，没有目标预览 | 无可预测之物 |
| 垃圾桶倾倒 / 取回 | 取回由服务端 `mainTargetOrder` | 客户端只发要销毁的格子 | 不改 |
| 从打开的容器取物（服务端 `BackpackTakeFromContainerPayload`） | 服务端 | — | 不改 |

所以 spec 里"选区、快速转移手势、双击聚拢、倾倒垃圾要用同一套类别规则做预测"字面上在这些手势里是**空操作**；它们要的是回归验证（范围正确：可见行参与、隐藏行不在列表里）。

## D4. 同一套判定怎样给客户端用

- **已核对**：`ExtensionRules.canPlace/targetSegments` 是泛型纯逻辑，输入 `RowSet<T>`（可见行 + 隐藏行）、基础容量、`CategoryMatcher<T>`；真正用到的只有 `rows.visible().get(row).category()`。客户端没有 `RowSet`，只有 `RowView`（基础容量、可见行类别列表、隐藏摘要）。
- **决定**：给 `ExtensionRules` 加一组重载，参数是 `List<String> visibleCategories`；原来的 `RowSet` 版本改成委托（取 `rows.visibleCategories()`）。035 的单元测试一行不改并必须继续通过，这样"不另写一份判断"成立：服务端与客户端走同一段代码。
- **`ItemCategoryMatcher` 在客户端能用**：它用 `stack.is(TagKey)`、`stack.has(DataComponents.FOOD/POTION_CONTENTS)`、`BuiltInRegistries.ITEM.getKey`、背包的 `ItemTags`；物品标签随数据包同步到客户端（原版 `TagsUpdate`，所有同步的注册表的标签），`ItemTags`（手动标签）由 `BackpackTagsPayload` 在每次打开会话时发来（`ClientBackpackView.tags()`）。
- **标签未同步的保守处理（FR-012）**：客户端在 `Minecraft.level` 的物品注册表里查类别标签是否已绑定（`registry.getTag(key).isPresent()`，十个类别各有数据文件，绑定后必存在，哪怕为空）；**任一类别的标签还没绑定** → `ClientPlacement` 一律返回"可以放"。**待游戏里验证**：进世界后立刻（以及刚换数据包后）判定是否正常；如果"已绑定"的判据不对（比如永远 false 导致永远放行），预测会退化成"不预测"，不会误拦——代价只是回到过渡期的体验，所以风险可接受并在 quickstart 里专门验一次。
- 另一条保守规则：下标在隐藏行 / 没有的格子上时服务端本来就拒绝（`canPlace` 返回 false，因为 `row >= visible.size()`），客户端的格子列表里本来没有这些格，不会点到。

## D5. 分隔线与行首：按格子算，不按行画线

- 问题：列数可变（3～9），一个 9 格的行在 6 列的面板里占一行半，起点和终点都不在行边界上。
- **决定：每个格子自己决定画不画上边线和左边线**：
  - 上边线：格子 `j` 与它正上方的格子 `j - cols` **不在同一组** → 在 `j` 的顶部画一条 1 像素线；
  - 左边线：`j % cols != 0` 且 `j` 与 `j - 1` 不在同一组 → 在 `j` 的左边画一条 1 像素线；
  - 组 = 主区域（-1）或第 k 个扩展行。组按下标单调不减，所以"不同组"一定是后面那一格属于更靠后的扩展行，线用那一行的颜色。
- 这样折行的阶梯形边界（上一行的尾巴与下一行的头）自然画成阶梯线，滚动时跟着格子走，不需要任何"行的几何"。`RowLayout.topEdge(index, cols)` / `leftEdge(index, cols)` 是纯函数，有单元测试：9 列、6 列、3 列、第一行就是扩展行（基础容量刚好整除列数的各种情形）、基础容量不整除列数。
- 行首角标：`RowLayout.isRowStart(index)`（`index >= base && (index - base) % 9 == 0`）的格子左上角画 3×3 的类别色块，**与颜色无关地标出每一行从哪里开始**。
- 对不齐：不处理，允许。

## D6. 类别怎么分辨：底色 + 幽灵图标 + 角标 + 悬停说明

- 只靠 10 种颜色在灰色面板上不稳，规格也要求"不只靠颜色"。三层信息：
  1. 底色：槽底之上叠一层类别色（半透明），在 `0xFF8B8B8B` 的槽底上能看出色相；
  2. **幽灵图标**：空格里画该类别扩展件的物品图（36 的贴图，淡色），告诉玩家这行放什么——像原版盔甲槽的剪影；格子里有物品时不画；
  3. 行首角标与悬停说明（"食物行：只能放食物"）。
- 幽灵图标的实现：从 `ModItems.CATEGORY_EXTENSIONS.get(id)` 取物品，用物品模型的粒子贴图（`generated` 模型即 `layer0`）以 `GuiGraphics.blit(..., sprite, r, g, b, a)` 带透明度画 16×16。**待游戏里验证**：透明度生效、深度（面板整体抬到 `OVERLAY_Z`）正确、不压住格子里的物品。**回退**：如果透明度画不出，空格只画角标与底色（幽灵图标是增强，不是必需）。
- 不新增贴图：幽灵图标用的是物品图集里已有的 36 贴图，图集尺寸不变（宪法 VI）。

## D7. 悬停说明与黄点的位置

- 面板里已有的悬停提示用 `graphics.renderTooltip(font, Component|List<FormattedCharSequence>, mouseX, mouseY)`（标题图标、编号、按钮、垃圾桶面板）。扩展行说明与黄点提示沿用。
- **面板不画格子里物品自己的提示**（核对 `client/` 下所有 `renderTooltip` 调用，没有对面板格子的）。所以规格 US1-6 修正为只做类别说明。
- 黄点：档位图标在 `(posX+4, posY+1)`，16×16（`TitleIdLayout.ICON_OFFSET/ICON_SIZE`）；编号文字从 `posX + 4 + 16 + 3` 开始。黄点放在图标右上角 `x ∈ [posX+15, posX+20)`、`y ∈ [posY+1, posY+6)`（5×5，圆角用去掉四角的方块），完全在编号起点（`posX+23`）左边，不挡编号；快照按钮、关闭按钮在右边，不相交。黄点的命中区略大（7×7）以便悬停。
- 窄面板：黄点在图标上，图标位置固定，不受面板宽度影响。

## D8. 隐藏摘要的口径：格数，不是件数

- **已核对**：`RowView.hidden` 是 `List<RowSet.HiddenSummary(category, nonEmptyCells)>`，"有东西的格数"。件数（每格可能是 64 个）要升协议。
- **决定**：提示按格数写（"食物：3 格有物品"），协议保持 17。用户若要件数，另开一次升协议。`HiddenHint` 把摘要转成要显示的行（类别名的语言键 + 格数），纯逻辑。

## D9. 绘制开销

- 每个可见格多 ≤ 4 次 `fill` 与一次幽灵图标 blit；没有扩展行时走不到这些分支（`RowLayout.groupOf` 对基础容量内的下标直接返回 -1）。最多 35 行 = 315 格，最坏每帧约 1260 次 `fill` + 315 次 blit，在 GUI 里不算重，但**未测量**。
- **决定**：只画滚动窗口里可见的格子（现有循环本来就只遍历 `visibleCount(area)`，不是全部格子），所以实际每帧最多 `cols × rows` 个格子，与扩展行数无关。**待游戏里验证**：35 行背包帧率（宪法 VI，测不了写"未测量"）。

## D10. 与其它面板、模式的共存

- **已核对**：标签面板、拾取黑名单页打开时，`drawSlotsWithItems` 对 MAIN 区整体 `continue`（不画格子），所以扩展行的底色、分隔线、角标、幽灵图标都在同一个 `continue` 之后，不会透出（FR-015）。垃圾桶面板挂在面板右边，在矩形外，不受影响。
- 创造模式、主机屏、Curios/Accessories 背着的背包：绘制与输入走同一个 `ScreenEvent` 渲染钩子与同一个 `ClientBackpackView`，没有各自的分支；用游戏验证而不是推理。
- 036 的标题图标回归：黄点只加不改，图标与编号的绘制代码不动。

## D11. 不做的事（及原因）

- 不画"隐藏行"的占位或灰色行：035/规划已定隐藏行不在主区域里。
- 不给格子里的物品加提示：不是本 feature 的范围（D7）。
- 不显示件数：要升协议（D8）。
- 不为类别做可配置的颜色：没有新配置项的约束；色值在 `CategoryStyle` 一张表里，改色只改一处。

## 尚未验证（写进 quickstart，验证前不得声称已满足）

1. 标签未同步窗口里的放行是否正确；"标签已绑定"的判据是否可靠（D4）。
2. 幽灵图标的透明度与深度（D6）。
3. 10 个底色在灰色槽底、悬停高亮、选区边框、拖拽预览叠加下是否分得清，色觉障碍下靠角标与幽灵图标能否分辨（D6）。
4. 折行阶梯形分隔线在 3、4、5、6、7、8、9 列下的观感与滚动时是否稳定（D5）。
5. 35 行背包的帧率（D9）。
6. 创造模式、主机屏、Curios/Accessories 背着的背包下的外观与交互一致（D10）。
7. 035 里仍未验证的几项（可见行同步往返、各放入路径、保存时布局变化后的整份同步、行总数 35 的拒绝）在本 feature 的游戏验证里一并关闭或记录。
