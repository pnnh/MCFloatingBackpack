# Tasks: 面板格子的物品提示（tooltip）

**Input**: `/specs/045-panel-item-tooltips/` 的 spec.md、plan.md；宪法

**Tests**: 抑制规则用 JUnit（纯 Java）；渲染接线用源码文本检查。**tooltip 的实际弹出、位置与深度只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器见 `scripts/ai-gui/README.md`；AI 自动验证的勾选规则同前
- 不改服务端、网络、协议号 17、配置、语言键、依赖、贴图；011～044 的测试不改
- 用户故事：US1 悬停看物品介绍；US2 该不弹的时候不弹

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 167 个类、1248 个用例）；`git status` 干净（除 045 文档）；记在 `specs/045-panel-item-tooltips/DELIVERY.md`（新建，中文）（基线 167 个类、1248 个用例）

## Phase 2: Foundational

- [X] T002 `…/client/panel/ItemTooltipRules.java`（纯逻辑，无 Minecraft 类型）：`shouldShow(PanelArea area, boolean mainCovered, boolean cursorHasItem, boolean dragPreview, boolean dialogPending, boolean cellHasItem)` = `cellHasItem && !cursorHasItem && !dragPreview && !dialogPending && !(area == MAIN && mainCovered)`
- [X] T003 [P] `…test/client/panel/ItemTooltipRulesTest.java`：每个抑制条件单独成立时不弹（光标有物品、拖拽预览、弹窗等待、主区域被盖住且区域为 MAIN、格子为空）；**主区域被盖住时工具区仍弹**；默认全开时弹；组合条件；对四个区域（MAIN、LEFT、RIGHT、BOTTOM）逐一

## Phase 3: User Story 1 + 2 - 悬停看物品介绍；该不弹的时候不弹 (Priority: P1) 🎯 MVP

- [X] T004 [US1] `…/client/panel/FloatingPanelRenderer.java`：新增 `private static boolean drawItemTooltip(GuiGraphics, Minecraft, int mouseX, int mouseY)`：`ClientBackpackView.get().layout().hit(...)` 为 null → false；按区域取物品（主区域 `getStackInSlot`、工具区 `getToolStack`）；`ItemTooltipRules.shouldShow(hit.area(), TagPanelState.get().isOpen() || PickupPanelState.get().isOpen(), !cursorStack(minecraft).isEmpty(), PanelDragInput.preview() != null, TrashConfirmState.get().isPending(), !stack.isEmpty())`；真则 `graphics.renderTooltip(minecraft.font, stack, mouseX, mouseY)` 并返回 true
- [X] T005 [US2] 同文件：把原来的 `drawRowTooltip(graphics, minecraft, mouseX, mouseY);` 调用改为 `if (!drawItemTooltip(graphics, minecraft, mouseX, mouseY)) { drawRowTooltip(graphics, minecraft, mouseX, mouseY); }`（`drawRowTooltip` 本身不改）；调用位置仍在垃圾桶面板与确认弹窗之前
- [X] T006 [P] [US1] 源码文本检查测试 `…test/resources/ItemTooltipWiringTest.java`：`drawItemTooltip` 里有 `renderTooltip(minecraft.font, stack, mouseX, mouseY)` 与 `ItemTooltipRules.shouldShow`；渲染里 `drawItemTooltip` 的调用先于 `drawRowTooltip` 的调用；用到 `TrashConfirmState`、`PanelDragInput.preview()`、`TagPanelState`、`PickupPanelState`；`ItemTooltipRules` 不含 `net.minecraft`；没有新增语言键（两份语言文件键集与基线一致）、没有 `PacketDistributor`、协议号仍 `"17"`
- [X] T007 运行 `./gradlew compileJava test`；011～044 的全部测试不改而通过

**Checkpoint**: 悬停提示与抑制规则就绪

## Phase 4: 游戏内验证与收尾

- [X] T008 [P] `README.md` 与 `README_CN.md` 面板说明里加一句：悬停有物品的格子显示该物品的提示（光标上拿着东西时不显示）
- [X] T009 游戏内验证（AI 自动验证，容器，plan 的 B1～B9）：每项写实际验证了什么，做不了的写"未验证"；**不在 `src/main` 留任何调试入口**——**AI 自动验证已做**（B1～B8；B9 拖拽预览与确认弹窗只有单元测试与源码文本检查证据，详见 DELIVERY.md）
- [X] T010 `specs/045-panel-item-tooltips/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 plan/tasks 的偏离、每个游戏内验证项验证了什么/没验证什么、已知限制、宪法复查
- [X] T011 文档同步：`docs/plan/backpack-search.md` 末尾加一句 045 与 046 的关系（045 tooltip 已做；046 搜索灰格待做）；最终 `./gradlew compileJava test`；`git status` 确认无临时修改残留、协议号 17 未动；**不提交、不打 tag**（169 个类、1260 个用例通过；无临时修改残留；`docs/plan` 的 045/046 说明见下）
- [X] T012 用户确认：用户在真机上悬停主区域与工具区的物品；**只有用户明确确认才勾**——**用户已确认（2026-11-12）：没有问题**
