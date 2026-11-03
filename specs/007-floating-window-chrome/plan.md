# Implementation Plan: 浮动背包窗口化改造（第一阶段：缩放与关闭）

**Branch**: `007-floating-window-chrome` | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-floating-window-chrome/spec.md`

## Summary

浮动背包面板目前尺寸完全由内容量和屏幕空间自动决定，玩家没有任何手段主动让它变小，高等级背包会强制遮挡玩家物品栏。本阶段只交付三项纯客户端 UI 改动：(1) 面板右下角可拖拽缩放手柄，允许行数、列数独立调整（下限各 3，上限沿用现有自适应算法）；调整列数的本质是把背包格子按新列数重新排布（虚拟总行数 = 容量 ÷ 当前列数，向上取整），超出可见行数的部分始终只靠**纵向**滚动条访问，不存在横向滚动条；(2) 标题栏关闭按钮，复用现有热键关闭路径；(3) 标题栏文字按当前背包等级动态显示，而非固定文案。三项都不涉及新的网络协议或服务端物品存储逻辑——标题文字可由客户端已持有的背包容量反查等级得到，关闭按钮复用既有的 `closePanel()` 路径，尺寸偏好复用既有的本地 Properties 持久化文件。原规划中的"点击下层容器穿透"与"Shift+点击双向转移"已在 spec 澄清阶段移出本阶段范围；"横向滚动条"是第二轮澄清后排除的一个错误假设，不在本次设计中。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251（`net.neoforged.moddev` 2.0.148）；Minecraft 1.21.1 客户端 GUI API（`GuiGraphics`、`Font`、`Component`）；LWJGL/GLFW（经 `Minecraft.getInstance().getWindow()` 间接访问，用于缩放光标）

**Storage**: 无新增服务端存储；面板行列尺寸偏好扩展进现有客户端本地文件 `config/mcfloatingbackpack-panel.properties`（`FloatingPanelConfig`，目前只存 `posX`/`posY`）

**Testing**: JUnit 5 纯逻辑单元测试（延续 `PanelLayoutTest`/`PanelClampTest` 的"不启动游戏、直接测 `FloatingPanelState`/`PanelSlots` 静态逻辑"模式）通过 `./gradlew test` 验证；按宪法 V 条，玩家可见的行为还必须在 `./gradlew runClient` 的真实客户端会话里手动验证一遍

**Target Platform**: Minecraft 1.21.1 桌面客户端（Windows/macOS/Linux）；开发机为 macOS（Darwin），需特别验证 GLFW 对角缩放光标在 macOS 上的回退行为

**Project Type**: 单一 NeoForge 模组（client+server 共用 jar）；本功能的改动面完全在 client 侧（`client/panel`、`client/FloatingPanelEvents`），不新增/修改任何 `network/` 下的 `CustomPacketPayload`

**Performance Goals**: 不改变现有每 tick 一次 `FloatingPanelState.layout()` 的调用量级；列数变化时重算虚拟总行数、以及缩放手柄命中判定，都必须是 O(1) 几何计算，不得引入可感知掉帧

**Constraints**: 不得破坏 004 功能遗留的 `./gradlew runClientCompat`/`runServerCompat` 联调入口；GLFW 对角缩放光标（`GLFW_RESIZE_NWSE_CURSOR`）在部分平台（含 macOS）可能返回 `NULL`，必须做空值回退，不能让缩放功能本身因光标不可用而失效

**Scale/Scope**: 改动集中在 `client/panel/FloatingPanelState.java`、`client/panel/PanelSlots.java`、`client/panel/FloatingPanelRenderer.java`、`client/panel/FloatingPanelInput.java`、`client/panel/FloatingPanelConfig.java`、`client/FloatingPanelEvents.java` 六个既有文件，外加对应的 `src/test/.../panel/` 单元测试；不触碰 `inventory/`、`network/` 包

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 本功能仍是对既有 Overlay 面板的尺寸/标题/关闭控件改动，不引入任何 `setScreen` 调用，面板继续可与其它容器 GUI 共存 | PASS |
| II. Server Owns Items | 本阶段不新增任何物品转移路径（点击穿透、Shift 转移已移出范围）；缩放/关闭/标题三项都是纯客户端 UI 状态，不触碰物品数据 | PASS（不适用） |
| III. One Feature At A Time | spec.md 已用 `## Out of Scope` 明确排除点击穿透与 Shift 转移，本 feature 只做已列出的 3 个用户故事 | PASS |
| IV. Vanilla Path Before Compat | 本功能不涉及第三方背包兼容 | PASS（不适用） |
| V. Verify In A Client Session | quickstart.md 要求在 `./gradlew runClient` 的真实会话里逐项验证缩放、关闭、标题文字，纯逻辑部分另有单元测试 | PASS（已规划，实现阶段需真正执行） |

无违反项，`Complexity Tracking` 留空。

## Project Structure

### Documentation (this feature)

```text
specs/007-floating-window-chrome/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

本功能不新增或修改任何 `CustomPacketPayload`（见 research.md Decision 1/2），因此不生成 `contracts/` 目录——没有新的客户端↔服务端接口需要文档化。

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── client/
│   ├── FloatingPanelEvents.java        # 新增：关闭按钮复用 closePanel()；缩放手柄的光标设置挂在 tick/mouse-move 事件里
│   └── panel/
│       ├── FloatingPanelState.java     # 新增：visibleCols/resizing 等字段与 begin/update/endResize；不新增 totalCols/scrollCol，列数变化通过重新计算 totalRows 复用现有 scrollRow
│       ├── PanelSlots.java             # 修改：slotX/slotY/indexAt 的列计算从固定常量 COLS 改为按 state.visibleCols 重新排布（虚拟总行数 = 容量 ÷ 当前列数，向上取整）；新增 resize 手柄命中区域常量；不新增横向滚动条函数
│       ├── FloatingPanelRenderer.java  # 修改：标题文字来源（按背包等级）、绘制关闭按钮与缩放手柄
│       ├── FloatingPanelInput.java     # 修改：关闭按钮点击分发、缩放手柄的 mouse-down/drag/up 分发
│       └── FloatingPanelConfig.java    # 修改：持久化新增 visibleRows/visibleCols 两个 key
│   └── (不变) ClientBackpackView.java  # 仅读取 slots().size() 做等级反查，不修改其结构
└── item/
    └── (不变) BackpackTier.java        # 仅作为只读查表依据（容量 → 等级 → 物品翻译 key）

src/test/java/pnnh/mcfloatingbackpack/client/panel/
├── PanelLayoutTest.java    # 扩展：独立行/列调整、下限3/上限裁剪、与现有纵向逻辑的回归用例
├── PanelClampTest.java     # 保持不变（位置夹紧逻辑不受本功能影响）
└── PanelSlotsResizeTest.java  # 新增：按列数重新排布的虚拟总行数/索引换算、resize 手柄命中测试等纯逻辑用例（不含横向滚动条用例）
```

**Structure Decision**: 单项目结构（现有 NeoForge 模组布局），不引入新模块或新包；全部改动落在既有 `client/panel` 包与一个既有的 `client/FloatingPanelEvents.java`，与现有拖动/滚动实现同构，便于复用而不是新建一套输入体系。

## Complexity Tracking

*无违反项，本节留空。*
