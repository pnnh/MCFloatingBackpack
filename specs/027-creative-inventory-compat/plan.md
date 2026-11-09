# Implementation Plan: 创造物品栏界面兼容

**Branch**: `027-creative-inventory-compat` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/027-creative-inventory-compat/spec.md`

## Summary

浮动面板叠在原版创造物品栏界面上时，"光标上的物品"在客户端与服务端对不上：创造界面里光标只存在于客户端，而面板的光标操作（点击、拖动、聚拢、垃圾桶、合并）全按服务端菜单的光标处理。本 feature 让**服务端的菜单光标镜像客户端的创造光标**，其余服务端代码一行不用改。研究阶段（见 [research.md](./research.md)）读源码得出：

1. **选"镜像"，不选"每次请求借用"，也不选"创造界面用面板自己的内部光标"**：镜像让现有 7 处服务端处理原样可用；关界面时服务端原版的 `removed()` 自然把光标上的物品放回玩家物品栏，**守恒不用另写**。
2. **客户端报告**：创造界面打开且面板可见时，客户端在每次输入事件（鼠标按下/松开、按键，都在界面自己处理之前）和每次发起光标请求之前，若光标与"上次报告"不同就发一个小包。只有创造模式的玩家才被服务端接受，且校验物品栈。
3. **结果回写**：服务端处理完面板操作后，如果菜单光标与"客户端已知的光标"不同，就发一个包让客户端的创造光标变成结果。钩子放在 `BackpackNetwork` 的发送辅助方法里——所有面板处理最后都走它们，所以只改一处。原版自己发的"光标同步包"在创造界面里被客户端忽略，必须走我们自己的包。
4. **旧栈覆盖新栈（规格的第二个后果）**：读源码后推断它**比想象的窄**——客户端监听器只在格子内容与上次记录不一致时才写回，所以需要玩家在创造界面里**本地改动过那一格**（拿起或放下那只背包本身），或做了整排快捷栏的拖动。plan 把它拆成"先复现再决定"：Phase B 是条件阶段，复现不出来就只登记，不做改动（FR-009）。026 里"栈落后但内容与影子相同则不判复制品"的补充已经覆盖了内容没变的情形。
5. 协议 `"14"` → `"15"`（新增两个网络包），不新增贴图、不新增设置、没有任何"创造模式下限制面板"的逻辑。

范围：只做光标往返与守恒；Ctrl+点击快速转移在创造界面里的缺口（`QuickTransferRules`）不动——光标镜像只解决它的三个障碍之一，另两个（物品栏标签页的格子编号是菜单序号、转移后客户端显示是旧的）见 research D8，留给后续 feature，并可与 Phase B 共用同步包；旧栈覆盖的修复依复现结果而定。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（自定义网络包、`ScreenEvent`、`PlayerContainerEvent.Close`、`AbstractContainerMenu` 光标）；**无新增依赖，不涉及第三方模组**

**Storage**: 无新增持久数据；服务端为每个创造玩家在内存里记"客户端已知的光标"（会话级，关界面与退出时清除）

**Testing**: JUnit 5 纯逻辑测试：`CreativeCursorRules`（接受/拒绝的全部原因与顺序）、`CursorMirror`（什么时候该报告、什么时候该回写、回写后不回声）；Minecraft 相关的收发由客户端会话验证

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（单人游戏用内置服务端）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（客户端输入钩子 + 两个网络包 + 服务端桥接）

**Performance Goals**: 只在面板可见且创造界面打开时、光标真的变化时才发包；每个输入事件一次引用比较；不增加每帧成本

**Constraints**:
- 服务端只接受创造模式玩家的光标报告；非法物品栈零变化（宪法 II，FR-010、FR-011）
- 对背包内容的所有修改仍先过 026 的 `BackpackGate`（FR-012）
- 原版其它界面（生存物品栏、箱子、宿主界面）行为与协议不变（FR-013）
- 不引入任何"创造模式下限制面板"的逻辑或设置（FR-014）
- 协议版本升到 `"15"`

**Scale/Scope**: 约 6 个新类（`CreativeCursorRules`、`CursorMirror`、两个网络包、服务端 `CreativeCursorBridge`、客户端 `CreativeCursorClient`）；修改 `BackpackNetwork`（注册、版本、回写钩子）、`FloatingPanelEvents`（输入钩子）、三处客户端发送点（面板点击、拖动、垃圾桶）、`MCFloatingBackpack`（关界面事件）；Phase B 条件新增一个网络包

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不替换、不包装、不拦截创造界面；只监听它的输入事件、读它的光标 | PASS |
| II. Server Owns Items | 创造界面的光标本来就是客户端持有的（原版创造模式对物品本就是客户端说了算）；服务端只在**创造模式玩家**身上接受报告并校验，面板里的真实存储修改仍全部在服务端并先过 `BackpackGate`；关界面时服务端把光标上的物品放回物品栏，守恒由服务端保证 | PASS |
| III. One Feature At A Time | 只做光标往返与守恒；Ctrl 快速转移的缺口明确留着；旧栈覆盖作为条件阶段，复现之前不改 | PASS |
| IV. Vanilla Path Before Compat | 这是修复原版路径本身；不引用第三方 | PASS |
| V. Verify In A Client Session | quickstart 要求在客户端逐场景验证（两种标签页、各类光标操作、关界面守恒、快捷栏/物品栏/胸甲位的背包、联机延迟）；联机没有条件则登记"未验证" | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增贴图；对所有玩家的新增成本：非创造、无创造界面、面板不可见时**零**；其余时候是每个输入事件一次引用比较——实现阶段粗测并写进 DELIVERY，测不了写"未测量" | PASS（已规划） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新增依赖；**这是第一档（原版）修复：不可配置，不新增设置，不在创造模式下限制面板**（宪法 2.2.0 Support priority） | PASS |

**Re-check after Phase 1 design**：没有违反项。要盯住的风险：
- **关界面行为与原版创造模式略有不同**：面板可见时，创造界面关闭时光标上的物品（包括从创造物品列表里取的虚拟物品）会被服务端放回物品栏，原版创造模式是直接丢弃。这是为了让面板里取出的真实物品守恒付出的代价，已写进 research D4，等用户确认；
- 联机延迟下，服务端的结果光标可能覆盖客户端刚做的本地操作（生存界面本来就是这个语义），记在 Edge Cases。

## Project Structure

### Documentation (this feature)

```text
specs/027-creative-inventory-compat/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── creative-cursor.md
├── checklists/requirements.md
└── tasks.md              # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/creative/                 # 新增（纯逻辑 + 服务端）
│   ├── CreativeCursorRules.java        # 纯：接受/拒绝一份光标报告的判定
│   ├── CursorMirror.java               # 纯、泛型：该不该报告 / 该不该回写
│   └── CreativeCursorBridge.java       # 服务端：应用报告、回写、关界面清理
├── network/
│   ├── CreativeCursorReportPayload.java    # 新增，客户端 → 服务端
│   ├── CreativeCursorPushPayload.java      # 新增，服务端 → 客户端
│   └── BackpackNetwork.java                # 修改：版本 "15"、注册、回写钩子
├── client/creative/
│   └── CreativeCursorClient.java       # 新增：报告时机、应用回写
├── client/FloatingPanelEvents.java     # 修改：输入事件里调用报告
├── client/panel/FloatingPanelInput.java, PanelDragInput.java, trash/TrashPanelInput.java   # 修改：发请求前先报告
└── MCFloatingBackpack.java             # 修改：关界面事件监听

src/test/java/pnnh/mcfloatingbackpack/inventory/creative/
├── CreativeCursorRulesTest.java
└── CursorMirrorTest.java
```

**Structure Decision**: 纯规则（校验、镜像记账）放在不依赖 Minecraft 的纯类里，沿用 018～026 的做法；网络包放 `network/`（与现有包并列）；客户端报告时机放 `client/creative/`。

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。
