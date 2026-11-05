# Contract: 垃圾桶面板的界面与输入

## 1. 展开 / 收起

- 触发：浮动面板可见，**右侧区**里的垃圾桶，鼠标**左键**、光标为空、没按 Shift/Ctrl → `TrashPanelState.toggle()`，不发请求、不拿起物品。右键 → 照 016 的普通点击（拿起 / 放下）。其它区域里的垃圾桶完全是普通物品。
- 自动收起（每个客户端 tick 检查）：浮动面板不可见，或右侧区**整个列表**里没有垃圾桶。关闭浮动面板、退出世界时也收起。
- 与标签面板相互独立：两者的状态各自一份，可以同时展开。

## 2. 位置与几何

见 [data-model.md](../data-model.md) 第 8 节：挂在浮动面板右边缘之外，顶端对齐，`62 × 72`，标题栏 14 高，3×3 个 18 的格子。

- 绘制：与浮动面板同一个 `OVERLAY_Z`，在浮动面板内容之后、光标物品之前。窗口太窄时被画到屏幕外即被裁剪，不做特殊处理。
- 标题栏只写标题文字，**没有图标或按钮**（022 才加）。
- 格子凹槽用右侧区的底色；有物品的格子画物品与数量；鼠标悬停的格子画与其它区域一致的高亮。**空格子也画凹槽**（共 9 个）。

## 3. 按下（`FloatingPanelInput.dispatchMouseButton`）

垃圾桶面板展开且点在其外框内时，**所有左右键按下都被消费**，不会穿透到下面的界面，也不进入主区域的任何手势。先于关闭按钮/缩放手柄/拖动条判断（外框与它们不重叠）。

| 条件 | 行为 |
|---|---|
| 右键 | 吞掉，什么都不做 |
| 按住 Shift | 吞掉，什么都不做 |
| 013 的拖动手势正在进行（`PanelDragInput.isActive()`） | 吞掉，什么都不做 |
| 光标非空（包括按住 Ctrl） | 发 `ADD`（点在外框内任意位置，含标题栏和空隙）|
| 光标为空，按住 Ctrl，点在有物品的格子上 | 开始 Ctrl 恢复手势（`QuickTransferInput.beginFromTrash`），立即发该格的 `RESTORE` |
| 光标为空，点在有物品的格子上 | 发 `TAKE`（该下标）|
| 光标为空，点在空格子 / 标题栏 / 空隙 | 吞掉，什么都不做 |

"光标"= `FloatingPanelRenderer.cursorStack(minecraft)`：有可用容器界面时是菜单携带物，否则是面板光标；`screenOpen = ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)`。

## 4. 松开（拖放，`FloatingPanelInput.onMouseButton` 的 RELEASE）

`TrashDropTracker` 在每次按下时记录"是否在垃圾桶外框之外"（`FloatingPanelInput.onMouseButton` 的 PRESS 入口，对所有被分派到的按下记录，不论是否被消费）。

`TrashPanelInput.onRelease(button, x, y)` 在 RELEASE 分支**最前面**调用，满足全部条件才处理：左键；记录为"按下在外"；松开点在外框内；没按 Shift/Ctrl；垃圾桶展开。处理：

1. `PanelDragInput.cancel()`；
2. 发 `BackpackTrashPayload(ADD, [], screenOpen)`；
3. 客户端预测：清空当前光标（菜单携带物或面板光标）；
4. 返回"是否吞掉这次松开"：按下被面板消费过 → 吞掉；否则（按下落在原版容器界面的格子上）→ **放行**，让原版带着空光标收尾。

不满足条件则走原有的松开分派（选区 → 013 拖动 → 面板拖动 → 缩放），不变。

## 5. Ctrl 恢复手势

- `QuickTransferGesture.Side.TRASH`，自成一组；起点在垃圾格上则整个拖动只接受垃圾格。
- 起点要求：Ctrl 按住、Shift 没按、光标为空、该格有物品。拖动中松开 Ctrl、按下 Shift、光标变非空、界面变了、面板被隐藏都结束手势（沿用 `QuickTransferInput.invalid`）。
- 每帧把新经过的、有物品的垃圾格下标聚成一个 `RESTORE` 请求发出；一个格子一次拖动内最多报告一次。
- 目标是主区域，所以在容器界面（使用菜单光标）上同样可用；不支持反方向。

## 6. 滚轮

垃圾桶外框内的滚轮被消费，不做任何事，不穿透。

## 7. JEI

`FloatingBackpackJeiPlugin.getGuiExtraAreas`：浮动面板可见且 `CompatConfig.policy().specificAdaptation("jei")` 为真时，已有的浮动面板矩形之外，垃圾桶面板展开则再返回它的外框矩形。

## 8. 020 的 OFF 界面

垃圾桶面板的渲染、按下、松开、滚轮都挂在 `hostsPanel(screen)` 之后；该界面被配置成 OFF 时面板整体不画不响应，展开状态保留，回到允许的界面后恢复。
