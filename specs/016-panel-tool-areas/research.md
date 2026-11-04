# Research: 浮动面板的三个工具区

结论来自读本仓库源码（`BackpackContents`、`BackpackInventory`、`BackpackClickPayload`、全量同步/会话/槽位更新三个包、`PanelSlots`、`FloatingPanelState`、`FloatingPanelRenderer`、`FloatingPanelInput`、`FloatingPanelEvents`、`ClientBackpackView`、011～014 的输入类、升级配方、JEI 插件、现有的 4 组布局测试）；**没有在游戏里验证**的地方在"仍不确定"里列出。

## Decision 1: 工具区是独立的存储，不并入主格子

**Decision**: 在 `BackpackContents` 里新增一个 `ToolAreaContents(left, right, bottom)`（三个 `List<ItemStack>`），用 `optionalFieldOf` 读写，缺省为空；`BackpackInventory` 里为三个区各持有一个 `ItemStackHandler`，主格子的 `items()`/`size()`/`getStackInSlot` 原样不动。

**Rationale**: 服务端有很多地方按 `backpack.size()` 遍历主格子——011 的转入目标（`ascending(backpack.size())`）、012 的 `BackpackTakeFromContainerPayload`、013 的 `gatherAll`/`distribute`、014 的批量转出。只要工具区不在主格子里，这些逻辑天然不会碰到工具区（FR-013），不需要给它们逐个加判断。旧存档没有这几个字段，`optionalFieldOf` 让它们读成空列表（FR-009）。

**Alternatives considered**: 把工具区追加在主列表后面（`BackpackInventory.MAX_SIZE = 108` 要改，所有按 size 遍历的地方都要排除工具区，漏一处就把物品塞进工具区）；每个工具区一个数据组件（升级配方和同步都要改三处）。

## Decision 2: 容量 = 由主容量推出的动态值，且缩容不丢物品

**Decision**: 纯函数 `ToolAreaCapacity.of(area, mainCapacity)`：左、右 = `ceil(mainCapacity / BackpackTier.COLUMNS)`（等级默认行数：皮革 4 … 下界合金 12）；下 = `BackpackTier.COLUMNS + 2`（默认 11）。读取和升级（`BackpackContents.fit`）时按它补齐；**列表长度 = `max(容量, 最后一个非空格子下标 + 1)`**：容量变小时，容量之外仍有物品的格子保留（继续显示、可取出），直到清空；容量变大时补空格。

**Rationale**: 用户明确要求容量跟着等级的默认尺寸、是动态条件、与当前缩放无关（Clarifications）；下区 +2 是用户后来补充的"面板下沿一整行"的结果。主格子的 `fit` 目前是直接截断，但那只在升级（只会变大）时用；工具区的容量由将来的程序调整，可能变小，spec 的 Edge Cases 要求**不得丢失物品**，所以工具区的 `fit` 不能截断有物品的尾部。客户端只看同步下来的列表长度，所以不需要知道这条规则。

**Alternatives considered**: 容量写死成常量（违背"动态"）；缩容时直接截断（会丢物品）；缩容时把多出来的物品退还给玩家（需要玩家在线和背包位置，复杂，且没人要求）。

## Decision 3: 点击寻址 = (区域, 区域内下标)，只加一个字段

**Decision**: 新增 `enum PanelArea { MAIN, LEFT, RIGHT, BOTTOM }`（线上一个字节）；`BackpackClickPayload` 增加 `area` 字段，`BackpackSlotUpdatePayload` 增加 `area` 字段；主区域用 `MAIN` 与原来的下标，行为不变。011～014 的请求（`BackpackQuickTransferPayload`、`BackpackCursorGesturePayload` 等）**不动**，它们永远是主区域。

**Rationale**: 工具区只需要最基本的点击，沿用同一个点击包最小；把区域放进包里比再造一个包少一份注册和校验。

**Alternatives considered**: 把工具区格子映射到主下标之后的一段（`≥ size()` 表示工具区）——会让 `BackpackClickPayload` 里 `slotIndex >= MAX_SIZE` 之类的校验和 011/013 的 `validate` 变得含糊；新增独立的"工具区点击包"（多一份样板，没有额外好处）。

## Decision 4: 服务端点击逻辑用访问器泛化，放物品前过一个规则钩子

**Decision**: `BackpackMenuBridge.clickWithMenuCarried` 增加一个重载，接收 `IntFunction<ItemStack> getSlot` 与 `ObjIntConsumer<ItemStack> setSlot`（现有方法用主格子的 lambda 调它）；`BackpackClickPayload.handleServer` 按 `area` 选出对应的 `ItemStackHandler` 的 lambda，其余流程（菜单携带物、面板光标、`swapHand` 主手交换、保存、`sendSlotUpdate`）共用。新增 `ToolAreaRules.canPlace(area, stack)`，本阶段恒为真；点击前若光标（或主手交换时的主手物品）非空且 `!canPlace`，整次点击忽略。

**Rationale**: `BackpackClickLogic.click` 本来就是通过 `getSlot/setSlot` 访问器工作的，泛化代价很小；规则钩子对应 FR-011，将来按扩展工具性质限制时只改这一处。

## Decision 5: 网络协议 `"6"` → `"7"`，用嵌套记录避开 6 字段上限

**Decision**: 全量同步包与会话包各增加一个 `ToolAreaContents` 字段（它自己有 `STREAM_CODEC`，是三个有长度上限的列表），`StreamCodec.composite` 仍不超过 6 个字段；`BackpackContents.STREAM_CODEC` 同样加一个字段（物品栈同步用到它）。`BackpackNetwork` 里 `versioned("6")` → `"7"`。

**Rationale**: 全量同步/会话/物品组件的编码都变了，旧客户端无法解析，必须升协议。每个工具区列表长度上限取 `MAX_TOOL_SLOTS = 64`（远大于任何等级的 12/11），解码时超过抛 `DecoderException`（同 011～013 的做法）。

## Decision 6: 布局用纯类 `PanelLayout`，`PanelSlots` 变薄，滚动条代码整块删除

**Decision**: 新增无 Minecraft 依赖的 `PanelLayout`：输入主区域可见列/行、四块区域的格子总数、四个滚动偏移；输出整个面板的像素宽高、四个区域的矩形、`hit(x, y)` → `(area, 下标)`/无、每个可见格子对应的下标或"填充格"。公式（格子 18 像素，`PAD_X=7`、`PAD_TOP=17`、`PAD_BOTTOM=7` 不变）：

- 面板宽 = `2·PAD_X + (列 + 2)·18`；面板高 = `PAD_TOP + (行 + 1)·18 + PAD_BOTTOM`；不再有滚动条宽度。
- 主区域左上角 = `(posX + PAD_X + 18, posY + PAD_TOP)`，大小 `列 × 行`；左区 = 主区域左边一列、同高；右区 = 主区域右边一列、同高；下区 = 主区域下方一行，从面板最左（`posX + PAD_X`）起，宽 `列 + 2`（横跨左右区正下方）。
- 主区域下限 3×3 不变，所以面板最小 = `5 列 × 4 行`（宽 104、高 96 像素）。

`PanelSlots` 保留常量与 `panelWidth(cols)`/`panelHeight(rows)`，其余（`slotX/slotY/indexAt/isRowVisible/scrollbar*`）要么委托给 `PanelLayout`，要么删除；`FloatingPanelState` 里的 `beginScrollDrag/updateScrollDrag/endScrollDrag/isScrolling/needsScrollbar` 与 `FloatingPanelInput` 里的滚动条命中一并删除（FR-003）。

**Rationale**: 几何与命中是整个功能最容易出错的部分，放进纯类才能用单元测试钉住（含滚动偏移、填充格、最小尺寸）；渲染、输入、JEI 都读同一份矩形，不再各算各的。

**Alternatives considered**: 在 `PanelSlots` 上继续堆静态方法（每个区域一套 x/y/可见/命中，重复且难测）。

## Decision 7: 主区域保持 007 的"虚拟行重排"，工具区沿长度方向跟随主区域可见格数

**Decision**: 主区域不变：`visibleCols`（3..9）、`visibleRows`（3..虚拟总行数）、`index = (行 + scrollRow)·visibleCols + 列`。左、右区可见格数 = `visibleRows`，下区可见格数 = `visibleCols + 2`；每个区域有自己的滚动偏移，最大偏移 = `max(0, 格子数 − 可见格数)`。缩放（`updateResize`）只改 `visibleCols/visibleRows`，所以只有主区域的行列在变，工具区厚度恒为 1（用户补充）。`layout(screenW, screenH)` 的可用宽高要扣掉左右两列和下面一行：`fitCols = (screenW − 2·PAD_X) / 18 − 2`，`fitRows = (可用高度) / 18 − 1`，`MIN_VISIBLE_CELLS = 3` 仍然压过屏幕过小的情况。

**Rationale**: 这是用户补充里的"3 个区域始终附加在主区域外侧、只改主区域行列"的直接实现；`FloatingPanelConfig` 里存的本来就是主区域的 `visibleRows/visibleCols`，所以旧的配置文件原样可用（面板会因此变大 2 列 1 行，`clampToScreen` 兜底）。

## Decision 8: 左、右区可见格数多于它的容量时，多出的格子画成"填充格"

**Decision**: 主区域把列数缩小时虚拟总行数变大（例如皮革 36 格，5 列 → 8 行），`visibleRows` 可以大于左、右区的容量（4）。此时左、右区**超出容量的那些格子**画成面板底色的填充格，不可点击；下区的可见格数 `visibleCols + 2 ≤ 11 = 容量`，不会出现填充格（除非将来容量变小）。

**Rationale**: spec 要求容量只取决于默认尺寸、与缩放无关，所以不能为了填满而凭空造格子；但整个面板必须仍是整体矩形，所以空出来的位置要用底色填满。这是 spec 没有明确覆盖的边界，登记在"仍不确定"，建议补进 spec 的 Edge Cases。

**Alternatives considered**: 限制 `visibleRows ≤ 等级默认行数`（会改变 007 的缩放上限，主区域将不能再显示完整的 8 行）。

## Decision 9: 滚轮作用于悬停的区域，沿用"面板内一律吞掉滚轮"

**Decision**: `FloatingPanelEvents.scrollPanel` 改为：鼠标在面板内时，用 `PanelLayout` 判断悬停在哪个区域，滚轮朝上 = 向起点方向、朝下 = 向终点方向，每格一档；主区域和左、右区调整纵向偏移，下区调整横向偏移；悬停区域放得下全部内容、或悬停在标题栏/空白处时不滚动，但仍返回"已处理"（和现在一样，防止滚轮漏到下面的界面）。

**Rationale**: 现有行为就是面板内吞掉滚轮；保持一致比"放得下就漏给下层"更不容易出怪事。

## Decision 10: 滚动提示用小扁箭头，画在区域边缘内侧

**Decision**: 每块区域在"该方向还有隐藏内容"（`偏移 > 0` 为向起点，`偏移 < 最大偏移` 为向终点）时画一个小三角形：主区域与左右区在上/下边缘的中点（朝上/朝下），下区在左/右边缘的中点（朝左/朝右）。三角形由 4 条 1 像素高的 `fill` 叠成（宽 7、5、3、1），深灰色加一条浅色描边，不用贴图；箭头不参与命中，不挡点击。

**Rationale**: 用户说"依实现难度而定"，箭头只要几条 `fill`，不新增资源（宪法 VI 不适用），比整条边线高亮更清楚。具体大小与颜色要在游戏里看，常量集中放在渲染类顶部。

## Decision 11: 四种底色

**Decision**: 主区域保持现有底色；左区浅蓝 `0xFFB5D3EE`、右区浅绿 `0xFFBFE5B5`、下区浅琥珀 `0xFFF0DDA0`；格子的高光/阴影边框沿用现有 `COLOR_SLOT_LIGHT/DARK`。

**Rationale**: 三个浅色色相不同、亮度相近，都与主区域的深灰（`0xFF8B8B8B`）和面板底（`0xFFC6C6C6`）区分得开；纯 `fill` 绘制。具体值需要在游戏里确认可辨性。

## Decision 12: 输入分派——工具区只做点击，Shift/Ctrl 吞掉，不碰选区

**Decision**: `FloatingPanelInput` 的格子分支先用 `PanelLayout.hit` 判断区域：
- `MAIN`：流程不变（014 选区 → 011 Ctrl → 013 拖动 → 点击）；
- 工具区：按着 Shift 或 Ctrl 的按下被消费并忽略（和 014"Shift 只属于选区"同一原则）；否则**立即**发 `BackpackClickPayload(area, slot, …)`（不走 013 的"松开才结算"，不清空主区域选区）。

`PanelDragInput`、`PanelSelectionInput`、`QuickTransferInput` 都通过 `PanelSlots.indexAt` 命中格子；让它保留原签名、**只返回主区域下标**即可，所以这三个文件不用改，拖动经过工具区的格子只会得到 -1（FR-012/FR-013）。

**Rationale**: 把"工具区只做点击"集中在入口一处，下游手势类几乎不用改。

## Decision 13: 测试策略

**Decision**: 纯逻辑单元测试：`PanelLayout`（尺寸公式、最小 5×4、四个矩形与角落、`hit`、滚动偏移与填充格、每个区域的最大偏移与箭头可见性）、`AxisScroll`/滚动夹取、`ToolAreaCapacity`、工具区 `fit`（补空、缩容保留有物品的尾部）、`ToolAreaRules` 钩子；改写现有的 `PanelLayoutTest`/`PanelSlotsResizeTest`/`PanelClampTest`/`FloatingPanelStateTitleIconTest` 以适应新的宽高与无滚动条。服务端点击沿用 `BackpackClickLogicTest` 已覆盖的逻辑。布局、渲染、滚轮、同步、保存和升级只能在客户端会话里验证（宪法 V）。

## 仍不确定 / 需要游戏内确认

- 左、右区的"填充格"（Decision 8）是否让人困惑，建议补进 spec 的 Edge Cases。
- 小扁箭头的大小、颜色、位置是否醒目又不挡视线（Decision 10）；四种底色是否互相可辨（Decision 11）。
- 缩放手柄（右下角 8×8）落在下区最后一格上，会不会让下区最后一格的点击变难（与原来落在主区域最后一格上的情况相同，但现在是工具区的格子）。
- 滚轮方向在下区的直觉（朝下 = 向右）。
- 旧配置文件里的 `visibleRows/visibleCols` 现在对应更大的面板，在小屏幕上是否被 `clampToScreen` 挤出屏幕。
- 同步包体积：每次工具区点击发一个槽位更新（和主区域一样），全量同步多带最多 12+12+11 个格子，应当可以忽略，未实测。
