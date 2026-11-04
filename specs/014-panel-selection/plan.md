# Implementation Plan: 浮动面板内的选区（Shift 选择，Ctrl 批量转移）

**Branch**: `014-panel-selection` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-panel-selection/spec.md`

## Summary

用户想在面板里用 Shift 标记一组格子（边框高亮），再用 Ctrl 点击其中任意一格，把所有选中格子的物品批量转出；以后很多玩法要用选区。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **选区只在客户端**：一个"背包格子下标"的集合（`PanelSelection`），不是物品状态，不涉及宪法 II 的服务端权威；服务端从不看选区。
2. **批量转出复用 011/012 的转出请求**（`BackpackQuickTransferPayload`，方向 `TO_INVENTORY` = 转出背包）：它本来就带格子列表、服务端逐项校验并按列表顺序处理。客户端发送**升序、只含有物品**的格子，顺序就是"从左到右、从上到下"。**没有新网络包，协议版本不变（仍为 `"6"`）。**
3. **三种选择输入由一个纯逻辑状态机 `SelectionGesture` 管理**：Shift+按下一个格子 → 待定；停在起点内松开 = 点击（切换起点）；滑到别的格子 = 拖动（起点和滑过的都加入，只加不减）。点击的"切换"放到松开时才做，所以拖动不会把起点切换掉。
4. **Ctrl 点击已选格子 = 松开时批量转出**：按下只记录；松开时检查"Ctrl 仍按着、面板仍可见、界面没变、光标为空"才发送。按在未选格子上的 Ctrl 手势原样交给 011/012，`QuickTransferInput` 不改。
5. **不按 Shift、不按 Ctrl 的面板点击先清空选区**，再交给现有的 013/点击逻辑；Shift 按着但不是选择手势的按下（Shift+右键、Ctrl+Shift）一律吞掉。
6. **Shift 不再触发主手交换，改为 Alt（macOS Option）**：只改客户端发出的标志位的来源（字段由 `shiftDown` 改名 `swapHand`，线上格式不变），服务端的交换逻辑原样保留；013 的 `PanelDragInput` 在"没有打开界面且按着 Alt"时让路，保持 Alt+点击立即交换。
7. **生命周期用"每 tick 同步上下文"一处解决**：面板不可见、所属背包或容量变化、登出时清空；滚动、缩放不清空，因为存的是下标。
8. **渲染**：已选格子画一圈金色内边框，画在物品之后；与悬停（半透明白填充）、013 拖动预览（白填充 + 黄色数量）都能区分。

范围：只改面板自己的格子；选区的其他用途、全选/反选快捷键、持久化、容器 → 面板的批量转移都不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`GuiGraphics`、`Screen.hasAltDown/hasShiftDown/hasControlDown`、`PacketDistributor`）；无新增依赖，不涉及第三方模组

**Storage**: 无。选区是客户端运行时状态，不持久化，不上服务端

**Testing**: JUnit 5 纯逻辑测试：`PanelSelection`（切换、批量添加、清空、上下文失效、升序快照）、`SelectionGesture`（点击/拖动区分、起点不被切掉、采样不漏格、去重、取消、Ctrl 待转移的提交/取消条件）；加客户端会话手测（quickstart，宪法 V），HUD 与打开界面都要测

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（客户端交互与渲染为主）

**Performance Goals**: 选区操作只改本地集合，不发包；批量转出一次请求（≤108 项，沿用现有上限）；渲染每帧只多画已选格子的边框

**Constraints**:
- 选区不得影响服务端物品规则；批量转出的校验、守恒、目标判定全部沿用 011/012
- 不改动 011/012 的客户端手势文件（`QuickTransferInput/Gesture/Rules`）
- Ctrl+Shift 时选区手势让路；Shift 在面板里只用于选区
- 选区状态独立于批量转出，供后续玩法读取

**Scale/Scope**: 3 个新类（`PanelSelection`、`SelectionGesture`、`PanelSelectionInput`）、改动 `FloatingPanelInput`、`FloatingPanelEvents`、`FloatingPanelRenderer`、`PanelDragInput`（Alt 让路）、`BackpackClickPayload`（字段改名，线上格式不变）；2 个新测试类；003/011/012/013 与 `docs/MCFloatingBackpack.md` 加说明

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不打开或替换任何 `Screen`；只改面板自己的鼠标处理与渲染，仍是叠层 | PASS |
| II. Server Owns Items | 选区不是物品状态，只是客户端的 UI 状态；批量转出仍由服务端按 011/012 校验并执行，服务端不信任也不读取选区 | PASS |
| III. One Feature At A Time | 范围限定在选区（选择、高亮、Ctrl 批量转出）以及因此必须改的 Shift 主手交换（用户已裁决改到 Alt）；选区的其他用途明确留给后续 feature | PASS |
| IV. Vanilla Path Before Compat | 只用原版修饰键判定；不引用精妙背包、旅行者背包的类型 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证，HUD 与打开界面都测；Alt+点击在 Windows/Linux 的窗口管理器冲突无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增资源（边框用 `fill` 画，不加贴图）；无网络协议变化 | PASS（不适用） |

**Re-check after Phase 1 design**：设计阶段没有新增违反项。需要在实现阶段盯住的风险：
1. **Shift 主手交换改键**是对现有行为的取代：HUD 下的 Alt+点击要回归，且 013 的延后结算不能把它变成"松开才交换"（research Decision 6）；
2. **Ctrl 批量转出"松开才发"**与 011 的"按下即转"不同，两条路径不能互相吞对方的按下/松开（`ConsumedPressTracker` 的规则）；
3. **选区按下标存储**：容量/背包变化必须清空，否则会把旧下标套在新背包上（research Decision 8）。

## Project Structure

### Documentation (this feature)

```text
specs/014-panel-selection/
├── plan.md              # 本文件
├── research.md          # Phase 0：有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：PanelSelection、SelectionGesture、状态与转移
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   └── panel-selection-gestures.md  # Phase 1：修饰键矩阵 + 批量转出请求 + 渲染约定
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── network/
│   └── BackpackClickPayload.java        # 修改：字段 shiftDown → swapHand（含义：主手交换；线上格式不变），服务端逻辑不变
└── client/
    ├── FloatingPanelEvents.java         # 修改：每帧驱动选区手势、每 tick 同步上下文（清空条件）、登录重置
    └── panel/
        ├── PanelSelection.java          # 新增：纯逻辑，选中格子集合（下标）；toggle/addAll/clear/contains/snapshot/syncContext
        ├── SelectionGesture.java        # 新增：纯逻辑状态机（IDLE/SELECTING/BATCH_PENDING、线段采样、visited、取消、批量提交条件）
        ├── PanelSelectionInput.java     # 新增：与游戏相连（修饰键判定、命中、发批量转出请求、每帧轮询、清空选区的点击）
        ├── FloatingPanelInput.java      # 修改：面板格子的按下/松开先走 PanelSelectionInput；sendClick 的标志位改读 Alt
        ├── FloatingPanelRenderer.java   # 修改：已选格子画金色内边框
        └── PanelDragInput.java          # 修改：没有打开界面且按着 Alt 时让路（Alt+点击保持立即交换）

src/test/java/pnnh/mcfloatingbackpack/client/panel/
├── PanelSelectionTest.java              # 切换/批量添加/清空/上下文失效/升序快照
└── SelectionGestureTest.java            # 点击 vs 拖动、起点保留、采样、去重、取消、Ctrl 批量提交与取消条件

specs/003-…/、011/、012/、013/ 与 docs/MCFloatingBackpack.md  # 修改：加 014 说明（Shift=选区，主手交换改 Alt；Ctrl 点击已选格子批量转出）
```

**Structure Decision**: 沿用单模块结构。选区状态、状态机是无 Minecraft 依赖的纯类，与游戏相连的部分单独成类（同 011/013 的做法）。**不重构** 011 的 `QuickTransferGesture` 和 013 的 `PanelDragGesture` 里各自的线段采样，`SelectionGesture` 里再写一份同规则的采样；三处重复在 research 里登记为后续可抽取的整理项（宪法 III：不在本 feature 里顺手重构）。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违反项，不需要填写。
