# Tasks: 搜索结果的灰格样式

**Input**: `/specs/046-search-grey-cells/` 的 spec.md、plan.md；宪法

**Tests**: 规则用 JUnit（纯 Java）；渲染接线用源码文本检查。**灰格的外观、不可点击、`/hidden` 文字可读性只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器见 `scripts/ai-gui/README.md`；AI 自动验证的勾选规则同前
- 不改服务端、网络、协议号 17、配置、语言键、依赖、贴图、**任何输入逻辑**；011～045 的测试不改
- 用户故事：US1 完整网格、结果亮其余灰；US2 灰格不可点、结果与留位照旧；US3 `/hidden` 与未知指令

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 169 个类、1260 个用例）；`git status`（工作区应只有 045 的未提交改动与 046 的文档）；记在 `specs/046-search-grey-cells/DELIVERY.md`（新建，中文）（基线 169 个类、1260 个用例）

## Phase 2: User Story 1 + 2 - 灰格 (Priority: P1) 🎯 MVP

- [X] T002 `…/client/panel/GreyCellRules.java`（纯逻辑，无 Minecraft 类型）：`greyed(boolean searching, boolean mainCovered, PanelArea area, int index)` = `searching && !mainCovered && area == MAIN && index < 0`
- [X] T003 [P] `…test/client/panel/GreyCellRulesTest.java`：搜索时主区域填充格为灰；不搜索时不灰；有结果的格子（index ≥ 0）不灰；工具区填充格不灰（四个区域逐一）；主区域被盖住时不灰；组合
- [X] T004 `…/client/panel/FloatingPanelRenderer.java`：加常量 `COLOR_SLOT_GREY`（先取 `0xFF606060`）；`drawSlotsWithItems` 里 `index < 0` 时：若 `GreyCellRules.greyed(searching, TagPanelState.get().isOpen() || PickupPanelState.get().isOpen(), area, index)`（该循环对被盖住的主区域本来就跳过，仍显式传入）则 `drawSlotWell(graphics, layout.cellX(area, ordinal), layout.cellY(area, ordinal), COLOR_SLOT_GREY)`；然后 `continue`；不搜索时行为逐字不变
- [X] T005 [P] 源码文本检查测试 `…test/resources/GreyCellWiringTest.java`：渲染里调用 `GreyCellRules.greyed`、有 `COLOR_SLOT_GREY`；`index < 0` 的分支先于原来的 `continue`；灰格代码段不含 `renderItem`/`SearchRenderer.drawHitFrame`/`drawRowDecor`；`GreyCellRules` 不含 `net.minecraft`；没有新增语言键、网络、配置，协议号仍 `"17"`；042 的输入文件（`FloatingPanelInput`、`PanelSlots`、`DeleteKeyInput`）未被本 feature 引用 `GreyCellRules`
- [X] T006 运行 `./gradlew compileJava test`；011～045 的测试不改而通过

**Checkpoint**: 灰格画法就绪

## Phase 3: User Story 3 - `/hidden` 文字垫底色 (Priority: P2)

- [X] T007 `…/client/panel/search/SearchRenderer.java` 的 `drawHiddenSummary`：把要画的行（标题、类别行、省略号、说明，含各自的颜色）先收集成列表，量最宽行与总高，在文字下面画面板底色矩形（四周各 2 像素，限制在主区域矩形内），再逐行画字；未知指令与 `HIDDEN` 以外不画；`HiddenSummaryFit` 与 037 的悬停提示不变
- [X] T008 `FloatingPanelRenderer`：`/hidden` 与未知指令时 `index < 0` 的格子同样是灰格（它们的结果数组是空的，T004 已覆盖，这里只核对：灰格先画、摘要文字后画（现有顺序：`drawSlotsWithItems` 在前，摘要在 `SearchRenderer.render` 之前，保持））（T004 已覆盖；顺序由 `GreyCellWiringTest` 断言）
- [X] T009 [P] 源码文本检查（`GreyCellWiringTest` 追加）：`drawHiddenSummary` 里先 `graphics.fill`（垫底）后 `drawString`；`FloatingPanelRenderer` 里 `drawSlotsWithItems` 的调用先于 `drawHiddenSummary` 的调用

## Phase 4: 游戏内验证与收尾

- [X] T010 [P] `README.md` 与 `README_CN.md` 搜索一节补一句：搜索时没有结果的格子是灰色的、不能点
- [X] T011 游戏内验证（AI 自动验证，容器，plan 的 C1～C10）：定灰色值；每项写实际验证了什么，做不了的写"未验证"；**不在 `src/main` 留任何调试入口**——**AI 自动验证已做**（C1～C9；C10 与 3 列最窄面板、滚动整屏未验证，详见 DELIVERY.md；灰色取 `0xFF606060`）
- [X] T012 `specs/046-search-grey-cells/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 plan/tasks 的偏离、每个游戏内验证项验证了什么/没验证什么、灰色最终取值、已知限制、宪法复查
- [X] T013 文档同步：`docs/plan/backpack-search.md` 第 7 节 046 一行改为已做；最终 `./gradlew compileJava test`；`git status` 确认无临时修改残留、协议号 17 未动；**不提交、不打 tag**（171 个类、1271 个用例通过；无临时修改残留）
- [X] T014 用户确认：用户在真机上看搜索时的灰格与 `/hidden`；**只有用户明确确认才勾**——**用户已确认（2026-11-12）；同时指出 `/hidden` 的垫块没盖满主区域（右、下露出灰格），改为整片遮罩，见 DELIVERY.md 的"确认后的调整"**
