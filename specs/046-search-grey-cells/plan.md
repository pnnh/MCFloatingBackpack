# Implementation Plan: 搜索结果的灰格样式

**Branch**: `046-search-grey-cells` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

## Summary

只改渲染层：搜索时主区域里"没有对应槽位"的格子（042 的填充格，现在被跳过、露出面板底色）画成灰色格子底；`/hidden` 的摘要文字下垫一块面板底色。**纯客户端、零输入与服务端改动**。核心思路（读代码得出，见 spec"已查证的事实"）：

1. **灰格本来就点不了**：填充格 `PanelLayout.slotIndex < 0`、`hit` 返回 null，所有输入都经它们，所以不需要任何输入逻辑（D1）。
2. **只在搜索时画**：`SearchState.isFiltering()` 为真且主区域没被标签面板/黑名单页盖住时，才画灰格；不搜索时填充格仍 `continue`，逐像素不变（D2）。
3. **规则写成纯函数** `GreyCellRules.greyed(searching, mainCovered, area, index)`，可单测（D3）。
4. **`/hidden` 文字垫底色**：`drawHiddenSummary` 先收集要画的行，算出最宽行与总高，在文字下面画面板底色的矩形，再画字（D4）。

## Technical Context

Java 21 · NeoForge 21.1.251 / Minecraft 1.21.1 · 无新增依赖 · Testing: JUnit 5 `GreyCellRulesTest` + 源码文本检查 `GreyCellWiringTest`，游戏里容器验证 · Constraints: 服务端、网络、协议号（17）、配置、语言键、依赖、贴图不动；011～045 的测试不改。

## Constitution Check

| 原则 | 结论 |
|---|---|
| I. Overlay Not Screen | 画在同一条面板渲染路径里 PASS |
| II. Server Owns Items | 不碰物品与请求；灰格不响应输入（`hit` 为 null）PASS |
| III. One Feature At A Time | 只改搜索结果的画法；覆盖层方案已放弃 PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方 PASS |
| V. Verify In A Client Session | quickstart 要求容器里截图与点击验证 PASS（实现阶段必须执行）|
| VI. Shared Resources Have A Budget | 无贴图；每帧多画 ≤ 可见格数的 fill（≤ 约 100 个），与普通格子同量级 PASS |
| VII. Minimal Dependencies | 无新依赖 PASS |

## Research

- **D1 输入**：点击、选区、拖拽、Ctrl 转移、Delete、双击聚拢都经 `PanelSlots.indexAt` / `PanelLayout.hit`；对填充格得到 -1 / null。042 的 DELIVERY 已有"点结果格拿起、留位、放回"的游戏内证据；灰格的点击要在本 feature 里再验证一次。**Decision**: 不改输入。
- **D2 画法**：`FloatingPanelRenderer.drawSlotsWithItems` 对每个区域的每个可见格 `ordinal` 调 `layout.slotIndex`；`index < 0` 就 `continue`。**Decision**: `index < 0` 且 `GreyCellRules.greyed(...)` 为真时，调用 `drawSlotWell(graphics, sx, sy, COLOR_SLOT_GREY)`（沿用格子底的立体边，填充色更暗）后 `continue`；其余不变。
- **D3 规则**：`greyed = searching && !mainCovered && area == MAIN && index < 0`。工具区的填充格（超出容量）不属于搜索，不置灰。
- **D4 `/hidden`**：`SearchRenderer.drawHiddenSummary` 现在边算边画。**Decision**: 先把"标题行、类别行、省略号、说明行"收集成 `(FormattedCharSequence, color)` 列表，量最宽行、总高，画 `COLOR_PANEL`（与 `drawVanillaPanel` 的背景色一致）矩形垫底（四周各 2 像素），再逐行画。未知指令不画文字所以不画垫底。
- **D5 灰色值**：普通格子底 `0xFF8B8B8B`；灰格先取 `0xFF606060`，游戏里对照后定，写入 DELIVERY。
- **风险**：灰格在最窄面板或有 037 扩展行时与行底色相邻的观感——容器截图对照。

## Project Structure

```text
specs/046-search-grey-cells/{spec.md,plan.md,tasks.md,DELIVERY.md,checklists/}
src/main/java/pnnh/mcfloatingbackpack/client/panel/
├── GreyCellRules.java            # ★ 新，纯逻辑
├── FloatingPanelRenderer.java    # 改：搜索时画灰格；COLOR_SLOT_GREY
└── search/SearchRenderer.java    # 改：/hidden 摘要文字垫底色
src/test/java/pnnh/mcfloatingbackpack/client/panel/GreyCellRulesTest.java
src/test/java/pnnh/mcfloatingbackpack/resources/GreyCellWiringTest.java
README.md / README_CN.md          # 改：搜索一节一句
```

## 验证（quickstart，容器）

C1 搜索 `pick`：结果在前、其余灰格铺满；C2 `zzzz` 无结果：整片灰格；C3 `@minecraft` 结果多于一屏：滚动后仍无漏灰/漏亮；C4 清除后与搜索前逐像素对比；C5 光标拿物品点灰格：无变化；Shift 点灰格：不选；悬停灰格：无 tooltip；C6 拿走结果格里的物品：留位不灰，可放回；C7 `/hidden`（有隐藏/无匹配）与 `/foo`：灰格 + 文字垫底色 / 无文字；C8 窄面板；C9 标签面板展开时不画灰格；C10 有分类扩展行时结果与灰格相邻的观感。

## Complexity Tracking

无需要论证的违例。
