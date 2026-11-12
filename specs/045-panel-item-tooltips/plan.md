# Implementation Plan: 面板格子的物品提示（tooltip）

**Branch**: `045-panel-item-tooltips` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

## Summary

悬停到面板里有物品的格子时，调用原版的 `graphics.renderTooltip(font, stack, x, y)`。**纯客户端、渲染层一处接线、零服务端改动**。核心思路（读代码得出，已写在 spec"已查证的事实"里）：

1. **复用现有命中**：`PanelLayout.hit(x, y)` 已经返回真实槽位（区域 + 编号），对填充格返回 null，搜索时按映射走——所以"悬停结果格"、"灰格/留位不弹"不需要任何额外逻辑（D1）。
2. **抑制规则写成纯函数** `ItemTooltipRules`（光标有物品、拖拽预览、确认弹窗、主区域被盖住、格子为空），可单测（D2）。
3. **与 037 行提示的取舍**：先试画物品 tooltip，画了就不再画行提示；没画（空格、拿着东西）再走原来的 `drawRowTooltip`，其代码不动（D3）。
4. **位置与深度**：放在 `drawRowTooltip` 原来的位置，和黄点提示、行提示同一个深度（面板的 `OVERLAY_Z` 之内），已知可用；确认弹窗出现时不画（D4）。

## Technical Context

**Language/Version**: Java 21 · **Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`GuiGraphics.renderTooltip(Font, ItemStack, int, int)`），无新增 · **Storage**: 无 · **Testing**: JUnit 5 纯逻辑测试 `ItemTooltipRulesTest` + 源码文本检查 `ItemTooltipWiringTest`；游戏里按 quickstart（下）验证 · **Constraints**: 服务端、网络、协议号（17）、配置、语言键、依赖、贴图全部不动；011～044 的测试不改 · **Scale/Scope**: 新增 1 个纯类、改 `FloatingPanelRenderer` 一处、README ×2

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 画在同一条面板渲染路径里，不新增 Screen | PASS |
| II. Server Owns Items | 只读客户端已有的格子内容，不发请求、不改物品 | PASS |
| III. One Feature At A Time | 只做物品 tooltip；搜索灰格另做 046 | PASS |
| IV. Vanilla Path Before Compat | 用原版 tooltip 入口；别的模组经 `ItemTooltipEvent` 加的行自然出现，不引用其类型 | PASS |
| V. Verify In A Client Session | quickstart 要求客户端验证（透明宿主界面与原版容器界面） | PASS（实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 无贴图、无缓存；每帧只在鼠标停在有物品的格子上时多一次原版 tooltip 调用 | PASS |
| VII. Minimal Dependencies | 无新依赖、无第三方类型 | PASS |

## Research（Phase 0）

- **D1 命中**：`PanelLayout.hit` 已是渲染、输入、搜索共用的"鼠标下是哪个槽位"，对填充格返回 null。`ClientBackpackView.getStackInSlot(index)`（主区域）与 `getToolStack(area, index)`（工具区）给出物品。**Decision**: 不新增命中逻辑。
- **D2 抑制**：原版容器界面只在 `getCarried().isEmpty()` 时画悬停物品的 tooltip；面板同样用 `cursorStack(minecraft)`。拖拽预览是 `PanelDragInput.preview() != null`；确认弹窗是 `TrashConfirmState.get().isPending()`；主区域被盖住是 `TagPanelState.get().isOpen() || PickupPanelState.get().isOpen()`（渲染里已有同样的判断）。**Decision**: 全部收进 `ItemTooltipRules.shouldShow(area, mainCovered, cursorHasItem, dragPreview, dialogPending, cellHasItem)`。
- **D3 037 取舍**：`drawRowTooltip` 对扩展行的任何格子都画。**Decision**: `drawItemTooltip` 返回是否画了；画了就跳过 `drawRowTooltip`；`drawRowTooltip` 本身不改（空格、拿着东西时它照旧）。
- **D4 深度**：`drawRowTooltip` 已在同一个位置用 `renderTooltip` 画，014 之后没有撕裂。**Decision**: 紧挨着它画，在垃圾桶面板与确认弹窗之前；弹窗等待时不画（FR-002）。
- **D5 内容**：`renderTooltip(Font, ItemStack, x, y)` 内部取 `Screen.getTooltipFromItem`（含 F3+H、`ItemTooltipEvent`、`TooltipComponent` 如潜影盒预览）。**Decision**: 完全沿用，不自己拼行。
- **风险（待实现时验证）**：面板挂在透明宿主界面时，tooltip 的深度与位置；容器里两种界面都截图。

## Project Structure

```text
specs/045-panel-item-tooltips/{spec.md,plan.md,tasks.md,quickstart.md,DELIVERY.md,checklists/}
src/main/java/pnnh/mcfloatingbackpack/client/panel/
├── ItemTooltipRules.java          # ★ 新，纯逻辑（无 Minecraft 类型）
└── FloatingPanelRenderer.java     # 改：drawItemTooltip，在 drawRowTooltip 之前
src/test/java/pnnh/mcfloatingbackpack/client/panel/ItemTooltipRulesTest.java
src/test/java/pnnh/mcfloatingbackpack/resources/ItemTooltipWiringTest.java
README.md / README_CN.md           # 改：面板说明里一句
```

## 实现要点

1. `ItemTooltipRules.shouldShow(PanelArea area, boolean mainCovered, boolean cursorHasItem, boolean dragPreview, boolean dialogPending, boolean cellHasItem)`：`cellHasItem && !cursorHasItem && !dragPreview && !dialogPending && !(area == MAIN && mainCovered)`。（`PanelArea` 是纯枚举，不带 Minecraft 类型。）
2. `FloatingPanelRenderer.drawItemTooltip(graphics, minecraft, mouseX, mouseY)`：`hit = layout().hit(...)`，null 返回 false；取物品；调用规则；`graphics.renderTooltip(minecraft.font, stack, mouseX, mouseY)`；返回 true。
3. 渲染里原来的 `drawRowTooltip(...)` 调用改为 `if (!drawItemTooltip(...)) { drawRowTooltip(...); }`。
4. README 两份：面板说明里一句"悬停有物品的格子显示物品提示"。

## 验证（quickstart）

- **A. 单元测试**：`ItemTooltipRulesTest`（各抑制条件的组合与扩展行取舍的前置条件）；`ItemTooltipWiringTest`（源码：`drawItemTooltip` 调 `renderTooltip(minecraft.font, stack`；先于 `drawRowTooltip`；确认弹窗用 `TrashConfirmState`；不新增语言键/网络/配置）。
- **B. 游戏内（容器）**：B1 主区域悬停（钻石镐）；B2 三个工具区悬停（自动拾取、垃圾桶、标签管理器）；B3 拿着物品悬停：不弹；B4 展开标签面板后悬停主区域：不弹、悬停露出的工具区：弹；B5 搜索结果格悬停：弹，灰格/留位：不弹；B6 扩展行：有物品弹物品、空格弹行提示；B7 F3+H 高级提示；B8 面板挂在透明宿主界面与物品栏界面上各一次，截图看深度；B9 拖拽预览与确认弹窗：不弹。

## Complexity Tracking

无需要论证的违例。
