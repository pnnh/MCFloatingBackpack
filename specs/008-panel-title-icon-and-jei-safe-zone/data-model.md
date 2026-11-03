# Data Model: 008-panel-title-icon-and-jei-safe-zone

本功能不新增服务端数据结构、网络负载或持久化字段。以下是 spec.md 两个 Key Entity 的具体落实，全部是客户端运行时状态或无状态函数。

## 标题栏显示状态 → `FloatingPanelState`（既有单例，新增一个命中测试方法）

| 方法 | 说明 |
|---|---|
| `hitsTitleIcon(double mouseX, double mouseY)` | 新增。标题栏左侧固定像素区域的命中判断，写法比照既有 `hitsCloseButton`（同样是标题栏范围内的一个固定小方块）。不新增字段——图标本身没有状态，只有"当前鼠标是否命中"这一个每帧现算的布尔值。 |

标题图标与悬停提示的**内容**（选哪个 tier、显示什么名字）不来自新字段，复用 007 已经建立的路径：`ClientBackpackView.get().slotCount()` → `BackpackTier.byCapacity(...)` → `ModItems.stackFor(tier)`（取图标用的 `ItemStack`）/ `Component.translatable("item." + MOD_ID + "." + tier.id())`（取 tooltip 文字）。容量不匹配任何已知档位时（`byCapacity` 返回 `null`），回退绘制一个空槽位图标或跳过图标绘制，不弹 tooltip——这种情况理论上不应该发生（只有已知五档背包能打开面板），属于防御性兜底，不是本功能的正常路径。

## 面板遮挡区域 → 新增 `FloatingBackpackJeiPlugin`（`compat/jei/` 包，无状态）

不是一个持久化实体，而是每次 JEI 调用 `getGuiExtraAreas()` 时现算的一个值对象：

```
FloatingPanelState.get().isVisible()
  → false: 返回空集合（Collections.emptyList()）
  → true: 返回一个 Rect2i(posX, posY, width, height)，直接读 FloatingPanelState 现有的 getPosX/getPosY/getWidth/getHeight
```

不缓存、不监听面板状态变化——JEI 自己会在需要重新布局时（面板拖动、缩放、开关容器界面等场景）主动重新调用 `getGuiExtraAreas()`，本插件只需要保证"每次被调用时返回当前最新的矩形"，不需要主动通知 JEI 状态变了（见 research.md Decision 2 对 `GuiEventHandler` 调用时机的确认）。

## 渲染顺序修正（FR-010）→ `FloatingPanelRenderer.render()` 内部顺序调整

不是数据实体，是一个纯粹的调用顺序约束，记录在此处便于实现阶段对照：

```
render(graphics, state, mouseX, mouseY):
    if !state.isVisible(): return
    graphics.flush()              # 新增：先把本帧此前（含 JEI）任何已排队但未刷新的绘制内容冲刷掉
    ...（原有的 state.layout/布局/背景/槽位/滚动条/关闭按钮/缩放手柄绘制，不变）
    画标题图标 + （命中时）画 tooltip   # 替换原来的 drawString(title, ...)
    ...（原有的 cursor 物品绘制，不变）
    graphics.flush()              # 原有，结尾保留不变
    RenderSystem.enableDepthTest()
```
