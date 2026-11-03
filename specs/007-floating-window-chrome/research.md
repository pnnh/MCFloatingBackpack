# Research: 007-floating-window-chrome

## Decision 1: 标题栏文字来源，不新增网络字段

**Decision**: 标题栏文字不通过网络传输，而是客户端用当前 `ClientBackpackView` 持有的背包槽位数量（`slots().size()`，即 `BackpackSessionPayload.slots()` 的长度，等于背包容量）反查 `BackpackTier`（容量 36/54/72/90/108 分别对应 LEATHER/IRON/GOLD/DIAMOND/NETHERITE），再用 `Component.translatable("item." + MCFloatingBackpack.MOD_ID + "." + tier.id())` 取得与背包物品本身完全一致的本地化名称，替换掉 `FloatingPanelRenderer` 中现在固定使用的 `Component.translatable("panel." + MOD_ID + ".title")`。

**Rationale**: `BackpackSessionPayload` 打开会话时已经把当前背包的 `slots` 列表发给客户端，列表长度与等级一一对应（`BackpackTier` 枚举、`PanelSlots.COLS = 9` 固定列数下 `capacity() = rows() * 9`），客户端已经具备反查所需的全部信息，不需要再为一个纯展示文本改动网络协议。

**Alternatives considered**: 在 `BackpackSessionPayload` 里新增一个字段直接携带物品显示名或 tier 序号——需要改动已有的 5 字段 `StreamCodec.composite`（打开/关闭两处构造函数、`handleServer`/`handleClient` 两端），对一个纯展示文本来说代价不成比例，排除。

## Decision 2: 关闭按钮复用既有的 closePanel() 路径

**Decision**: `FloatingPanelEvents` 中已有的私有方法 `closePanel()`（隐藏面板 + 清空 `ClientBackpackView` + 发送 `BackpackOpenRequestPayload.close()` + `syncMouseToPanelVisibility()`）改为包内可见（或新增一个同语义的桥接静态方法），标题栏关闭按钮被点击时，由 `FloatingPanelInput` 调用这同一个方法，而不是另起一套隐藏逻辑。

**Rationale**: `closePanel()` 已经是热键关闭背后的真正实现，复用它能保证"点击关闭按钮"与"按热键关闭"在隐藏面板、清空客户端镜像、通知服务端关闭会话这几步上完全一致，不会出现两条逻辑分叉导致状态不同步（例如点关闭按钮却没通知服务端）。

**Alternatives considered**: 在 `FloatingPanelInput` 里为关闭按钮单独实现一遍"隐藏 + 通知服务端"——逻辑重复，且未来如果热键关闭逻辑变化（例如增加额外清理步骤），容易漏改关闭按钮这条路径，排除。

## Decision 3: 列数独立调整——重新排布格子，只复用现有纵向滚动条，不新增横向滚动

> 2026-11-02 第二次澄清后更正：最初设计打算新增一套横向滚动条与纵向对称实现，用户明确纠正——调整尺寸不是开一个可以双向滚动的"窗口"，而是把背包容器按新列数重新排布格子；滚动条自始至终只有纵向一种。以下为更正后的 Decision。

**Decision**: `FloatingPanelState` 新增 `visibleCols`（玩家可调整的可见列数，范围 [3, 当前可用空间算出的上限]）。不新增 `totalCols`、不新增 `scrollCol`、不新增任何横向滚动条函数。`PanelSlots` 的列计算从"固定常量 `COLS = 9`"改为"使用 `state.visibleCols` 作为当前排布列数"：`slotX`、`slotY`、`indexAt` 按 `col = index % state.visibleCols`、`row = index / state.visibleCols` 重新换算；"虚拟总行数"= `ceil(背包容量 / state.visibleCols)`，这个值替代现有 `totalRows` 直接喂给已有的纵向滚动条逻辑（`scrollbarTrackY`、`scrollbarThumbHeight`、`scrollbarThumbY`、`hitsScrollbar`、`hitsScrollThumb`、`scrollRowAt`、`FloatingPanelState.setTotalRows`/`clampScroll`），不需要新增任何函数，只需要在 `visibleCols` 变化时重新调用一次 `setTotalRows(ceil(容量 / visibleCols))`（与打开不同等级背包时重新设置 `totalRows` 是同一调用路径）。

**Rationale**: 现有纵向滚动条的坐标换算（track/thumb/travel/ratio）已经跑了几个 feature、被 `PanelClampTest`/`PanelLayoutTest` 覆盖过，是被验证过的模式；把"列数变化"建模成"虚拟总行数变化"后，可以完整复用这套逻辑，连新函数都不需要写，只需要在 `visibleCols` 变化的那一刻重算一次 `totalRows`。这比最初设想的"横向滚动条"方案改动量更小、也更符合玩家的直觉——调整列数看到的效果就是"同样这堆物品，换了个更窄/更宽的排法"，而不是出现一个新的可独立滚动的维度。

**Alternatives considered**:
- （已否决，见上方更正说明）横向滚动条与纵向对称实现——玩家明确指出这不是预期行为，排除。
- 列数缩小时直接裁掉多出来的格子、超出部分不可访问——不满足 spec FR-004"虚拟总行数大于可见行数时必须能通过滚动条访问"的要求，排除。
- 列数变化时物理上把 9 列的原版对齐网格拆成多组更窄的网格分别显示——比"单纯重算虚拟总行数"复杂得多，且不是玩家描述的行为，排除。

## Decision 4: 缩放手柄的输入状态机与光标反馈

**Decision**: `FloatingPanelState` 新增 `resizing` 状态（与现有 `dragging`/`scrolling` 同一组互斥的"当前交互模式"），配套 `hitsResizeHandle(mouseX, mouseY)`（面板右下角固定像素大小的命中区域，写法比照现有 `hitsDragBar`）、`beginResize`/`updateResize`/`endResize`（比照现有 `beginDrag`/`updateDrag`/`endDrag` 与 `beginScrollDrag`/`updateScrollDrag`/`endScrollDrag`）。`updateResize` 按"每 `PanelSlots.SLOT_SIZE` 像素一格"把鼠标位移换算成行/列增量，分别夹到各自的 [3, 上限] 范围。`endResize` 之后调用一次 `FloatingPanelConfig.saveFrom(state)`，与现有 `endDrag` 后的保存调用点一致。

光标反馈：鼠标悬停在缩放手柄上时，通过 `Minecraft.getInstance().getWindow().getWindow()`（GLFW 窗口句柄）调用 `GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR)`（GLFW 3.4+ 标准光标，Minecraft 1.21.1 自带的 LWJGL 版本已包含该常量）并 `glfwSetCursor` 应用；离开手柄区域时 `glfwSetCursor(window, MemoryUtil.NULL)` 恢复默认箭头光标。已通过网络搜索确认：该光标形状在部分平台（含 macOS）可能不受支持，`glfwCreateStandardCursor` 此时返回 `NULL` 而不是抛异常（来源：[GLFW 论坛讨论](https://discourse.glfw.org/t/error-creating-standard-cursors/3019)、[GLFW 3.4 对角缩放光标变更记录](https://fsunuc.physics.fsu.edu/git/gwm17/glfw/commit/02461dc843b63be1c58da473dbdb961c8acf9f00)），因此必须判空，返回 `NULL` 时直接跳过 `glfwSetCursor` 调用（保留系统默认箭头光标），不得让缩放拖拽功能本身因光标创建失败而不可用。

**Rationale**: 复用现有拖动/滚动的状态机写法，能继续用同一套 `FloatingPanelInput.onMouseButton`/`onMouseDrag`/`onMouseReleased` 事件分发，不需要新开一条输入路径；光标 API 的 null 回退行为已经过官方文档与社区讨论交叉确认，不是臆测。

**Alternatives considered**: 用面板角落的自绘图标代替系统光标形状变化——不符合 spec 里"鼠标指针移动到...时，指针变为...形状"的明确描述（那描述的是光标本身的变化，不是面板上多一个按钮/图标），且需要额外美术资源，排除；记录为光标在不支持平台上的保底参考方案，但不作为本阶段实现选择。

## Decision 5: 面板尺寸持久化方式

**Decision**: 扩展现有 `FloatingPanelConfig`（客户端本地 `config/mcfloatingbackpack-panel.properties` 文件），新增 `visibleRows`、`visibleCols` 两个 key，复用现有 `loadInto`/`saveFrom` 方法签名与所有既有调用点（`FloatingPanelEvents.ensureConfigLoaded()`、`onLoggingIn`），并在新增的 `endResize()` 之后追加一次 `saveFrom` 调用。

**Rationale**: 与现有 `posX`/`posY` 的持久化完全同构，不需要新文件格式或新的加载/保存时机；面板尺寸是纯客户端 UI 偏好，不属于宪法"Server Owns Items"约束的游戏状态，不需要上行到服务端。

**Alternatives considered**: 把尺寸偏好也同步/存进玩家数据（服务端权威）——尺寸只是客户端渲染偏好，不是游戏状态，没有必要让服务端知道，也会模糊"客户端本地外观设置"与"服务端权威物品数据"之间的边界，排除。

## Known limitations

- "列数变化 = 重算虚拟总行数"这个映射本身很简单，但 `PanelSlots.slotX/slotY/indexAt` 从"固定常量 `COLS`"改成"读取 `state.visibleCols`"是对现有坐标计算的改写而非纯新增，需要靠 `PanelLayoutTest`/新增的 `PanelSlotsResizeTest` 覆盖"列数变化后旧槽位索引仍能换算出正确坐标"这类回归用例，避免改出偏移。
- GLFW 对角缩放光标在 macOS 上的具体支持情况需要在 `./gradlew runClient` 的真实会话里实测确认一次（而不是仅凭文档判断），因为开发机就是 macOS，正好可以在 quickstart 验证时顺带确认 fallback 是否触发。
