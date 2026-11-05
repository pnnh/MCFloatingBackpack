# Implementation Plan: 取消"无界面 HUD 模式"，改为兜底的透明宿主界面

**Branch**: `019-fallback-host-screen` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-fallback-host-screen/spec.md`

## Summary

用户决定去掉"面板开着但没有任何界面"的 HUD 模式，改成"面板可见且没有其它界面时，由模组显示一个透明宿主界面"，宿主只是兜底：任何其它界面一出现它就让位，其它界面关闭后它自动回来；玩家仍然能用 E 和其它模组的按键打开别的界面。研究阶段（见 [research.md](./research.md)）读 NeoForge 21.1.251 与本仓库源码得出的方案：

1. **宿主是一个普通的 `Screen` 子类 `PanelHostScreen`**：不暂停游戏、不画背景（不变暗不模糊）、不持有任何物品；它自己什么都不画，面板仍由现有的 `ScreenEvent.Render.Post` 画。
2. **生命周期由"每帧/每 tick 检查一次策略"驱动，不靠事件**：`setScreen(null)` 不会触发 `ScreenEvent.Opening`，`Closing` 里又不能再 `setScreen`。纯逻辑 `HostScreenPolicy.decide(面板可见, 当前界面种类, 玩家是否存活)` 给出"打开宿主 / 关闭宿主 / 清除面板 / 什么都不做"，由 `HostScreenManager.ensure` 执行。它在 `RenderFrameEvent.Pre`（画面渲染之前，所以不会有"面板消失的一帧"）、`ClientTickEvent.Post`（最低优先级，让别的模组先处理完），以及服务端会话打开/关闭的那一刻各调用一次。
3. **按键转发用"让出再重放"，并且发生在宿主自己的 `tick()` 里**：读 `Minecraft.tick` 得知顺序是 `screen.tick()` → （`screen == null` 才执行）`handleKeybinds` → `ClientTickEvent.Post`。所以宿主在 `tick()` 里让出（`setScreen(null)`），再把这次按键交给游戏自己的 `keyboardHandler.keyPress`（走没有界面时的完整路径），并在下一个 tick 开头才回来，让在 tick 开头轮询按键的模组也能读到，**同一 tick 里**原版的 `handleKeybinds` 就会消费这次点击（E 开物品栏、聊天键开聊天……），随后的 `Post` 看到的是最终状态：开了新界面就不管，没开就把宿主放回来。中间没有渲染帧。
4. **按键分三类**，由纯逻辑 `HostKeyRouter` 判定：面板自己的开关热键与没有绑定功能的按键 → 不拦截；移动、跳跃、潜行、冲刺、攻击、使用、选取、丢弃、副手交换、快捷栏 1–9、保存/加载工具栏 → 吞掉；其余所有已绑定的按键（原版的物品栏、聊天、命令、成就、视角……以及其它模组的）→ 让出再重放。
5. **ESC 由宿主自己处理**：宿主在场时 ESC 关面板，不开暂停菜单；容器界面上的 ESC 仍由原版先关容器。因此现有的"`Opening` 里取消暂停菜单"和 `onKey` 里的 ESC 分支都可以删除。
6. **非容器界面（聊天、选项、暂停菜单……）把面板隐藏而不是关闭**：面板的渲染和输入本来就只在"宿主或容器界面"上挂钩，所以隐藏是自然结果；要补的只是不再 `closePanel()`，并在界面切换时结束进行中的面板拖动/缩放。
7. **删除 HUD 专用的第二套驱动**：`onRenderGui`（`RenderGuiEvent.Post` 里的输入与手势驱动）、`onWorldMouseButton`、`onWorldMouseScroll`、`syncMouseToPanelVisibility`（实现中只为宿主让出的那一个 tick 保留了一个只画不交互的桥接绘制，见 research D4）（含 `BackpackSessionPayload`、`ClientBackpackActions` 里的调用）。面板的渲染与鼠标输入只剩界面事件这一条路径。
8. **不改服务端、不改协议**：`MergePanelCursorPayload`、`screenOpen`（宿主不是容器界面，所以仍为 false，沿用面板光标）、Alt 换主手原样保留。

范围：宿主界面与它的生命周期、按键转发、删除 HUD 驱动、ESC 与非容器界面的隐藏语义、死亡/登出清除、受影响文档的更新。Q 丢弃等原版光标功能、轮盘、018 标签管理器、创造物品栏界面上的新行为都不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`Screen`、`KeyMapping`、`ScreenEvent`、`ClientTickEvent`、`RenderFrameEvent`）；无新增依赖，不涉及第三方模组类型（对其它模组的按键只通过 `KeyMapping` 通用接口转发）

**Storage**: 无。可见状态是客户端运行时状态（背包会话沿用现有的服务端会话）

**Testing**: JUnit 5 纯逻辑测试：`HostScreenPolicy`（各种"可见 × 界面种类 × 存活"组合的决定）、`HostKeyRouter`（按键分类：吞掉/让出/不拦截/开关热键，含一个键绑定了多个功能的情况）；加客户端会话手测（quickstart，宪法 V），宿主与各种界面的组合都要测；兼容测试用 `runClientCompat`

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（客户端交互与渲染为主）

**Performance Goals**: 每帧一次 O(1) 的策略判定；无新增渲染；宿主不画任何东西

**Constraints**:
- 宿主只在"面板可见且没有其它界面"时存在，任何其它界面出现时必须让位，且不得暂停游戏、不得持有物品、不得挡住打开其它界面的按键（宪法 2.0.0 原则 I）
- 不替换、包装、继承或拦截原版或其它模组的界面
- 不改网络协议（保持 `"7"`），不改服务端规则
- 面板挂在容器界面上的行为必须与改动前完全一致（011–017）

**Scale/Scope**: 4 个新类（`PanelHostScreen`、`HostScreenManager`、`HostScreenPolicy`、`HostKeyRouter`）、改动 `FloatingPanelEvents`（删三个 HUD 钩子并改界面判定）、`BackpackSessionPayload`、`ClientBackpackActions`；2 个新测试类；003/007/011–014/016 与 `docs/MCFloatingBackpack.md` 加说明；018 文档记录依赖

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

对照宪法 **2.0.0**：

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 面板仍是叠层：挂在容器界面时不替换、不包装、不拦截该界面；唯一的新界面是宿主，且完全符合 2.0.0 的四条约束——(a) 任何其它界面打开即让位、关闭后恢复（策略每帧检查）；(b) 不暂停（`isPauseScreen()` 为 false）、不持有物品（无菜单、无槽位）；(c) 不挡打开其它界面的按键（让出再重放，见 research D4）；(d) 只承载面板（无其它用途）。面板热键在宿主与容器界面上都可用 | PASS（带实现条件，需客户端验证 (a)(c)） |
| II. Server Owns Items | 不改任何物品规则；宿主不存放物品；让位给容器界面时的光标合并仍走现有的服务端 `MergePanelCursorPayload` | PASS |
| III. One Feature At A Time | 范围限定在宿主界面及其生命周期、按键转发、删除 HUD 驱动、必须随之改变的 ESC/非容器界面语义和受影响文档；Q 丢弃、快捷栏互拖、轮盘、018 明确不做 | PASS |
| IV. Vanilla Path Before Compat | 先在原版（物品栏、聊天、成就、工作台、箱子）上跑通；对精妙背包、旅行者背包与其它模组的按键只用 `KeyMapping` 通用接口，不引用它们的类型；兼容结果登记为已验证/未验证，不作硬依赖 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证，宿主与各界面组合都测；JEI/EMI 表现、其它模组按键、F1/F3、Windows/Linux 登记为未验证或已知限制 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增任何资源（宿主不画任何东西）；无网络协议变化 | PASS（不适用） |

**Re-check after Phase 1 design**：设计阶段没有新增违反项。实现阶段要盯住的风险：

1. **让位的完整性**（原则 I）：必须证明"不管谁打开的界面，宿主都不会盖在它上面"。策略只在 `kind == NONE` 时才打开宿主，且打开宿主前不得有任何界面；`setScreen` 的所有调用点只有 `HostScreenManager` 和"让出"，两处都在测试和 quickstart 里覆盖。
2. **让出再重放对其它模组按键的有效性**：不经 `consumeClick` 的模组按键可能无效；遗留的未被消费的点击会在宿主关闭后"幽灵触发"，所以在 `Post` 里要把被重放按键的残留点击清掉（research D4）。
3. **鼠标位置**：每次让出/恢复宿主，原版都会把鼠标放回窗口中心，需在客户端看手感是否可接受（spec 假设已接受）。
4. **进行中的手势**：界面切换时 011/013/014 的手势靠 `minecraft.screen != activeScreen` 取消；宿主与容器界面互相切换同样会触发，要验证取消后物品总数守恒。

## Project Structure

### Documentation (this feature)

```text
specs/019-fallback-host-screen/
├── plan.md              # 本文件
├── research.md          # Phase 0：有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：界面种类、策略表、按键分类表、状态与转移
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   └── host-screen-lifecycle.md  # Phase 1：宿主的生命周期、按键转发与事件路由契约
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── network/
│   └── BackpackSessionPayload.java        # 修改：会话打开/关闭后调用 HostScreenManager.ensure，去掉 syncMouseToPanelVisibility
└── client/
    ├── ClientBackpackActions.java         # 修改：toggleFromUse 关闭路径改为调用 HostScreenManager.ensure
    ├── FloatingPanelEvents.java           # 修改：删除 onRenderGui / onWorldMouseButton / onWorldMouseScroll / syncMouseToPanelVisibility / hideIfDisallowed 的关闭行为 / onScreenOpening 里关面板与取消暂停菜单 / onKey 的 ESC 分支；
    │                                      #       allowsPanel 加入宿主；新增 hostsPanel(screen)（宿主或容器界面）供渲染与鼠标输入使用；
    │                                      #       新增 RenderFrameEvent.Pre 与最低优先级 ClientTickEvent.Post 调用 ensure；界面切换时结束面板拖动/缩放；登出时清除
    └── panel/
        ├── PanelHostScreen.java           # 新增：透明宿主 Screen（不暂停、不画背景、ESC 关面板、转发按键、tick 里让出再重放）
        ├── HostScreenManager.java         # 新增：与游戏相连，ensure()/dismiss()，执行 HostScreenPolicy 的决定
        ├── HostScreenPolicy.java          # 新增：纯逻辑，(可见, 界面种类, 存活) → 动作
        └── HostKeyRouter.java             # 新增：纯逻辑，按键绑定的功能名 → 吞掉/让出/不拦截/开关热键

src/test/java/pnnh/mcfloatingbackpack/client/panel/
├── HostScreenPolicyTest.java              # 全部组合的决定；容器界面与其它界面从不被宿主替换
└── HostKeyRouterTest.java                 # 吞掉清单、让出、未绑定、开关热键、一键多绑定

specs/003-…/、007-…/、011/、012/、013/、014/、016/ 与 docs/MCFloatingBackpack.md  # 修改：把"无界面 HUD"改写成"宿主界面"，并记 019
specs/018-tag-manager/spec.md、plan.md                                             # 修改：记录对 019 的依赖（简化在 019 完成后做）
```

**Structure Decision**: 沿用单模块结构。决策逻辑（策略、按键分类）是无 Minecraft 依赖的纯类，可以直接单测；与游戏相连的部分（宿主界面、管理器）单独成类，同 011/013/014 的做法。**不重构** 011/013/014 的输入文件（它们靠 `minecraft.screen` 判断取消，对宿主同样成立）；`FloatingPanelEvents` 里 HUD 专用的三个钩子直接删除，不留兼容开关（宪法 III：不保留两套）。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违反项，不需要填写。"让出再重放"是满足原则 I 第 (c) 条（不得挡住打开其它界面的按键）所必须的机制，不是额外复杂度；研究里记录了被否决的更简单做法及原因。
