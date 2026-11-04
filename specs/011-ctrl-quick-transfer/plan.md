# Implementation Plan: 浮动背包与玩家物品栏之间的 Ctrl 快速转移

**Branch**: `011-ctrl-quick-transfer` | **Date**: 2026-11-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-ctrl-quick-transfer/spec.md`

## Summary

浮动背包的存储不在任何容器菜单里，原版 `QUICK_MOVE` 找不到它；原版和第三方的 Shift 手势又不能抢。本功能新增 **Ctrl+点击 / Ctrl+拖动** 两种手势，在玩家物品栏（0–35 格）与浮动背包之间双向转移。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **请求只说"玩家物品栏第 N 格"**：客户端把悬停的 `Slot` 换算成 `getContainerSlot()`（0–35），服务端直接读写 `player.getInventory()`，不依赖当前菜单、不传菜单槽位编号。
2. **客户端自己做命中测试**（不用上一帧的 `hoveredSlot`）；**只在原版界面（类名以 `net.minecraft.` 开头，排除创造模式物品栏）上生效**——因为精妙背包等的玩家槽位也绑定真实 `Inventory`，只看槽位无法排除它们。
3. **拖动 = 每帧轮询 + 线段采样**：每帧读鼠标位置，在上一帧到当前的线段上采样命中，新槽位加入待发列表；松开判定也靠轮询，不依赖松开事件（BUG-005-1 的教训）。
4. **按下事件取消、松开事件由 `ConsumedPressTracker` 一并吞**：否则原版会把 Ctrl+点击当成普通拿起；Shift 一律不碰。
5. **搬运算法做成泛型纯逻辑**（`QuickTransferLogic`），单元测试测的是真正的实现，不是镜像。
6. **一个批量网络包**（方向 + 槽位下标列表），协议版本 `"3"` → `"4"`，服务端整包校验、执行后 `sendFullSync` 一次。
7. "Ctrl"用原版的 `Screen.hasControlDown()`：macOS 上是 Command（原版会把物理 Ctrl+左键重映射成右键）。

范围：只涉及玩家 36 格；箱子等外部容器、第三方界面、Shift+双击不做（已在 spec 澄清）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`ScreenEvent`、`InputEvent`、`PacketDistributor`、`AbstractContainerScreen`）；无新增依赖

**Storage**: 不新增持久化。背包内容仍在物品实例的数据组件上（`BackpackContents`，005/006）；玩家物品栏是原版 `Inventory`

**Testing**: JUnit 5 纯逻辑测试（不启动游戏）：搬运算法（`QuickTransferLogic`，泛型、用 `FakeStack` 适配器跑真实实现）、手势状态机（`QuickTransferGesture`）、请求校验；加客户端会话手测（quickstart，宪法 V）

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS（Command 键）

**Project Type**: 单一 NeoForge 模组（客户端交互 + 服务端校验）

**Performance Goals**: 一次拖动滑过 36 格 3 秒内完成；每帧至多一个网络包；不引起断线或卡顿（FR-013）

**Constraints**:
- 一切物品移动由服务端校验并执行（宪法 II）；校验失败整包拒绝，不改任何物品
- 不覆盖任何 Shift 手势；不改第三方界面
- 保持 Overlay 路径，不使用 `setScreen`（宪法 I）
- 不新增资源、不改共用图集（宪法 VI 不适用）

**Scale/Scope**: 7 个新类（搬运逻辑 `QuickTransferLogic`、请求校验 `QuickTransferRequest`、`ItemStack` 适配器 `ItemStackTransferOps`、手势状态机 `QuickTransferGesture`、纯判定规则 `QuickTransferRules`、与游戏相连的 `QuickTransferInput`、网络包 `BackpackQuickTransferPayload`）、改动 `FloatingPanelInput`、`FloatingPanelEvents`、`BackpackNetwork`；4 个新测试类

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不打开、替换任何 `Screen`；只拦截已有界面上的鼠标事件，面板仍是叠层 | PASS |
| II. Server Owns Items | 客户端只发"哪些格、往哪一侧"；服务端校验（越界/重复/超限/光标非空/背包有效）后才改物品；失败整包拒绝 | PASS |
| III. One Feature At A Time | 范围限定在玩家 36 格 ↔ 浮动背包的 Ctrl 手势；箱子/第三方界面/Shift+双击/穿透判定均不做 | PASS |
| IV. Vanilla Path Before Compat | 只做原版界面；第三方界面明确排除，不引入对任何模组的依赖 | PASS |
| V. Verify In A Client Session | quickstart.md 逐场景要求在客户端验证，含 macOS Command 键；Windows/Linux 本机无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增贴图/模型，不涉及共用图集与渲染开销；网络开销有上限（每帧至多一包，槽位数封顶） | PASS（不适用） |

Additional Constraints 检查：快捷键约束（"开关面板的快捷键在其他界面打开时仍可用"）不受影响；`design/`、LFS、开发期依赖不涉及。

**Re-check after Phase 1 design**：设计阶段没有新增违反项。一处需要在实现阶段重点盯住的风险：第三方界面的玩家槽位也绑定真实 `Inventory`，必须靠"界面类名白名单"排除（research Decision 10），否则会违反 spec FR-016——这是 quickstart 三.7 的必测回归项。

## Project Structure

### Documentation (this feature)

```text
specs/011-ctrl-quick-transfer/
├── plan.md              # 本文件
├── research.md          # Phase 0：11 条有依据的决定
├── data-model.md        # Phase 1：槽位集合、请求、手势状态机
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   └── quick-transfer.md  # Phase 1：网络包 + 手势契约
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/
│   ├── QuickTransferLogic.java            # 新增：泛型纯逻辑（合并 → 空格 → 剩余），无 Minecraft 依赖
│   ├── QuickTransferRequest.java          # 新增：请求校验与"要保护的物品栏下标"，纯逻辑
│   └── ItemStackTransferOps.java          # 新增：QuickTransferLogic 的 ItemStack 适配器
├── network/
│   ├── BackpackQuickTransferPayload.java  # 新增：客户端→服务端，方向 + 下标列表；服务端处理
│   └── BackpackNetwork.java               # 修改：注册新包，版本 "3" → "4"
└── client/
    ├── FloatingPanelEvents.java           # 修改：按下事件分流、每帧轮询（Render.Post / RenderGuiEvent.Post）
    └── panel/
        ├── QuickTransferGesture.java      # 新增：手势状态机（方向、visited、线段采样、结束条件），可单测
        ├── QuickTransferRules.java        # 新增：纯判定规则（界面类名白名单、哪些槽位可转移），不导入 Minecraft 类型，可单测
        ├── QuickTransferInput.java        # 新增：连接游戏的部分（命中测试、发包、每帧驱动）
        └── FloatingPanelInput.java        # 修改：面板侧起点；Ctrl 时不走原有点击逻辑

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/QuickTransferLogicTest.java        # 合并/溢出/空格/堆叠上限/跳过槽位/总数守恒
├── inventory/QuickTransferRequestTest.java      # 请求校验（越界、重复、超限、方向与范围对应）；放在 inventory/ 因为被测类不依赖 Minecraft
├── client/panel/QuickTransferRulesTest.java     # 界面类名白名单、可转移槽位判定
└── client/panel/QuickTransferGestureTest.java   # 方向锁定、同槽只一次、线段采样、结束条件
```

**Structure Decision**: 沿用现有单模块结构。算法放 `inventory/`（与 `BackpackClickLogic` 并列，不依赖玩家/世界）；手势状态机放 `client/panel/`，把"和游戏有关的部分"（拿 `Screen`、读 GLFW、发包）与"可单测的部分"（状态机）分开；网络包放 `network/`。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违反项，不需要填写。
