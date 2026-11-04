# Implementation Plan: 浮动面板内的拖动均分与拖动收集

**Branch**: `013-panel-drag-split-gather` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-panel-drag-split-gather/spec.md`

## Summary

用户想在浮动面板里有原版风格的"拖动均分"和"拖动收集同类物品"，另外确认纳入"右键拖动逐格放 1 个"和"双击收集"，并裁决"光标已有物品时按住左键拖动 = 均分优先"。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **照搬原版的结算时机**：光标非空时按下面板格子只"开始"一次拖动分配，**松开才结算**（滑过 ≥2 个有效格子 → 分配；恰好 1 个 → 对该格做普通点击；0 个 → 对松开位置做普通点击）；光标为空时按下仍立即拿起。这是对现有"按下即放下"的**行为变化**，与原版一致。
2. **规则逐条照搬原版**，包括"已满的同种堆叠也算有效格子"这个怪癖：均分每格 `floor(数量 ÷ 格子数)`，右键每格 1，每格不超过上限，光标不足时不再加格子，单格退化为点击。服务端与客户端预览共用同一份纯逻辑 `PanelCursorLogic`。
3. **拖动收集**：光标为空时按下有物品的格子 → 立即拿起；此后按住左键滑过的、与刚拿起的物品同种的格子，其物品被收进光标直到最大堆叠。原版没有这个手势（原版的 `mouseDragged` 在这种状态下什么都不做），所以不冲突；精妙背包、旅行者背包也没有。
4. **双击收集**：照原版 `PICKUP_ALL`（250ms、同一格同一键，松开时触发，两遍先未满后满）。
5. **光标两种来源统一成 `PanelCursor`**：开着界面用菜单携带物，没开界面用面板自己的光标；沿用现有点击的 `screenOpen` 自报标志。
6. **一个新网络包**（`BackpackCursorGesturePayload`，模式 + 格子列表 + screenOpen），协议 `"5"` → `"6"`；服务端整包校验，执行后 `sendFullSync`（含光标）。
7. **客户端**：`PanelDragGesture` 纯逻辑状态机 + `PanelDragInput` 与游戏相连（每帧轮询、线段采样、取消条件、预览数据）；预览只在客户端渲染，不改物品。按着 Ctrl 或 Shift 时让路。

范围：只改面板自己的格子；菜单格子、混合拖动、中键、触摸屏、Shift+双击均不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`GuiGraphics`、`ScreenEvent`、`RenderGuiEvent`、`PacketDistributor`）；无新增依赖，不涉及第三方模组

**Storage**: 不新增持久化；背包内容在物品实例的数据组件上（005/006），光标是运行时状态

**Testing**: JUnit 5 纯逻辑测试：`PanelCursorLogic`（均分、右键、单格退化、满堆叠算有效格子、收集、双击收集、守恒，用假栈跑真实实现）、`PanelDragGesture`（状态机、采样、取消、双击判定）、请求校验；加客户端会话手测（quickstart，宪法 V），HUD 与打开界面都要测

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（服务端规则 + 客户端交互与渲染）

**Performance Goals**: 一次拖动每帧至多一个请求；包体积随滑过的格子数线性且有上限（≤108）；预览渲染每帧只重算选中格子

**Constraints**:
- 一切物品移动由服务端校验并执行（宪法 II）；客户端只发"模式 + 滑过的面板格子"
- 规则与原版逐条一致（含满堆叠算有效格子的怪癖），客户端预览与服务端结果必须一致
- 不改变 011/012 的 Ctrl 手势；Ctrl/Shift 按下时让路
- 不改变菜单格子（玩家物品栏、箱子、精妙背包、旅行者背包）自己的行为

**Scale/Scope**: 约 5 个新类（`PanelCursorLogic`、`PanelCursor`、`BackpackCursorGesturePayload`、`PanelDragGesture`、`PanelDragInput`）、改动 `FloatingPanelInput`、`FloatingPanelRenderer`、`FloatingPanelEvents`、`BackpackNetwork`；3 个新测试类；011/012 与 007 的文档加说明

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不打开或替换任何 `Screen`；只改面板自己的鼠标处理与渲染，仍是叠层 | PASS |
| II. Server Owns Items | 分配、收集的结果都由服务端按光标与格子的真实内容计算；客户端只发模式与格子下标；预览只是显示 | PASS |
| III. One Feature At A Time | 范围限定在面板内的拖动分配与收集（含已澄清的右键拖动与双击）；菜单格子、混合拖动、中键、触摸屏、Shift+双击不做 | PASS |
| IV. Vanilla Path Before Compat | 复刻的是原版机制；不引用精妙背包、旅行者背包的类型，也不依赖它们 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证，HUD 与打开界面都测，并重跑 011/012 的回归；Windows/Linux 无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增资源；网络开销有上限（每帧至多一个请求、格子数 ≤108） | PASS（不适用） |

**Re-check after Phase 1 design**：设计阶段没有新增违反项。需要在实现阶段盯住的风险：
1. **结算时机变化**（拿着物品时放下从按下改为松开）可能影响手感，必须在 quickstart 五里逐条回归（research Decision 9）；
2. **客户端预览与服务端结果必须一致**，两边共用 `PanelCursorLogic`，测试里钉住"满堆叠算有效格子"等原版怪癖；
3. **`screenOpen` 是客户端自报**（现有设计限制，沿用）。

## Project Structure

### Documentation (this feature)

```text
specs/013-panel-drag-split-gather/
├── plan.md              # 本文件
├── research.md          # Phase 0：10 条有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：模式、请求、PanelCursor、分配规则、客户端状态机
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   └── panel-cursor-gestures.md  # Phase 1：网络包 + 客户端手势契约
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/
│   ├── PanelCursorLogic.java            # 新增：泛型纯逻辑（有效格子判定、placeCount、均分/右键结算、单格退化、收集、双击收集），无 Minecraft 依赖
│   └── PanelCursor.java                 # 新增：服务端光标读写（菜单携带物 / 面板光标）
├── network/
│   ├── BackpackCursorGesturePayload.java # 新增：客户端→服务端，模式 + 格子列表 + screenOpen；服务端处理
│   └── BackpackNetwork.java             # 修改：注册新包，版本 "5" → "6"
└── client/panel/
    ├── PanelDragGesture.java            # 新增：纯逻辑状态机（DISTRIBUTING/GATHERING、visited、selected、线段采样、取消、双击判定）
    ├── PanelDragInput.java              # 新增：与游戏相连（命中、发包、每帧驱动、预览数据、按键轮询）
    ├── FloatingPanelInput.java          # 修改：面板格子的按下/松开改走 PanelDragInput（Ctrl/Shift 让路）
    ├── FloatingPanelRenderer.java       # 修改：绘制拖动预览（放入后的数量、光标剩余）
    └── （FloatingPanelEvents.java 在 client/）  # 修改：每帧调用 PanelDragInput.tick，重置

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/PanelCursorLogicTest.java        # 假栈：均分/右键/单格/满堆叠/收集/双击/守恒/随机
├── inventory/PanelCursorRequestTest.java      # 请求校验（模式、越界、重复、超限、GATHER_ALL 恰好 1 项）
└── client/panel/PanelDragGestureTest.java     # 状态机：起点条件、取消、采样、visited、双击判定

specs/011-ctrl-quick-transfer/、specs/012-…/   # 修改：互相引用处加 013 说明（Ctrl/Shift 让路，协议 6）
```

**Structure Decision**: 沿用单模块结构。规则放 `inventory/`（与 `QuickTransferLogic`、`BackpackClickLogic` 并列，无 Minecraft 依赖），状态机与游戏相连的部分分开（同 011 的做法），使规则和状态机都能直接单测；渲染预览只读取状态机的数据。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违反项，不需要填写。
