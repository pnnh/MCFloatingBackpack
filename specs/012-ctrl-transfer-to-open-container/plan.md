# Implementation Plan: 从浮动面板转出时优先存入打开着的容器

**Branch**: `012-ctrl-transfer-to-open-container` | **Date**: 2026-11-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-ctrl-transfer-to-open-container/spec.md`

## Summary

011 的"转出"总是进玩家物品栏。012 改成：服务端处理转出请求时，如果玩家当前打开着容器（`containerMenu` 不是玩家自己的物品栏菜单、且有非玩家物品栏的槽位），物品存入该容器，结果与"玩家在这个容器里 Shift+点击自己物品栏里的同一件物品"一致；没有容器时仍进玩家物品栏。研究阶段（见 [research.md](./research.md)）的结论：

1. **只改服务端的一个分支**：客户端手势、命中测试、网络包字段、协议版本（`"4"`）全不变；011 客户端与 012 服务端互相兼容。
2. **复用菜单自己的 `quickMoveStack`**：把栈放进一个玩家物品栏槽位（临时槽位），调用该菜单的 `quickMoveStack`，再把没搬走的部分取回背包。这样原版箱子、熔炉、工作台，以及精妙背包（升级槽、合并规则）和旅行者背包（工具槽、记忆槽、升级槽）都按各自的规则工作，**零模组专属代码、不在编译期引用第三方类型**。
3. **临时槽位**：先选空槽位；物品栏满时换出一个主物品栏里有物品的槽位（要求 `mayPickup` 与 `mayPlace`，**永不选手持的快捷栏槽位**），调用后换回；都没有则本次不转移。
4. **安全措施**：照原版 `doClick` 的循环，加迭代上限；异常只记日志、`finally` 里取回临时槽位内容并换回原物品；转移后做物品总数守恒检查（记日志，不回滚第三方状态）。
5. **算法做成泛型纯逻辑**（`OpenContainerDelivery`），用"假菜单"（箱子式、工作台式、拒收式、抛异常式、永不收敛式）做单元测试；与游戏相连的部分只是一层薄适配器。

范围（2026-11-05 补充）：除了"面板 → 打开的容器"，**同时包含反向"打开的容器 → 面板"**（spec User Story 5，FR-017～FR-023）。反向与正向不对称：

7. **反向不能复用 `quickMoveStack`**（它对容器槽位的目标永远是玩家物品栏），所以直接 `Slot.safeTake` 取出 → 放进浮动背包 → 剩余 `safeInsert` 放回，放不下时依次回退到 `slot.set`、玩家物品栏、`player.drop`，保证不丢。
8. **输出类槽位用"能不能把这件物品放回去"排除**（`mayPlace` 恒假），不认识任何具体槽位类型。
9. **新网络包带菜单 id**（`BackpackTakeFromContainerPayload`，协议 `"4"` → `"5"`），服务端核对菜单 id、槽位下标、玩家槽位、光标。
10. **客户端要改**：取消 011 的"界面类名白名单"（改为任意容器界面，排除创造模式），命中测试扩展到容器槽位，**Shift 让路**（唯一一处读取 Shift，目的是不占用 Ctrl+Shift）。手势的"侧"增加 `CONTAINER`，与 `INVENTORY` 同属"转入背包"组。

仍不做：Shift+双击、Shift 手势、Windows/Linux 验证。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`AbstractContainerMenu`、`Slot`、`ServerPlayer`）；无新增依赖；**不引用精妙背包、旅行者背包的类型**（宪法 IV）

**Storage**: 不新增持久化；背包内容仍在物品实例的数据组件上（005/006），玩家物品栏与容器内容由各自的原有机制保存

**Testing**: JUnit 5 纯逻辑测试：`OpenContainerDelivery` 对假菜单的行为（临时槽位选择、换出换回、循环上限、异常、守恒、拒收、满容器）；加客户端会话手测（quickstart，宪法 V），其中精妙背包/旅行者背包必须在 `runClientCompat` 下实测

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（只改服务端逻辑）

**Performance Goals**: 一次拖动滑过一行 9 格 3 秒内完成（011 已满足，012 不应使其变慢）；每个栈的转移是常数次菜单调用（有迭代上限）

**Constraints**:
- 一切物品移动由服务端校验并执行（宪法 II）；客户端不判断、不指定目标容器（正向）；反向只上报"菜单 id + 槽位下标"，服务端核对
- 不引入对第三方模组的硬依赖，不在编译期引用其类型（宪法 IV）
- 正向不改网络包字段；**反向新增一个网络包，协议版本 `"4"` → `"5"`**；客户端需要改（反向的命中与手势）
- 不占用 Shift 手势（Ctrl+Shift 让路）；不改 011 的物品栏→背包方向

**Scale/Scope**（正向）: 3 个新类（泛型算法 `OpenContainerDelivery`、菜单适配器 `ContainerMenuAccess`、临时槽位选择的纯逻辑可并入算法类）、改动 `BackpackQuickTransferPayload` 一个分支；1 个新测试类（多个场景）；011 的 3 份文档加修订标注

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不打开或替换任何 `Screen`；只改服务端转出的目的地 | PASS |
| II. Server Owns Items | 去向由服务端按当前打开的菜单决定；客户端仍只发"哪些槽位转出"；所有改动在服务端完成 | PASS |
| III. One Feature At A Time | 范围限定在"转出去哪"；容器→面板、Shift+双击、Shift 手势均不做；对 011 的修订仅限 FR-018 | PASS |
| IV. Vanilla Path Before Compat | 011 已先完成原版路径；本功能对第三方容器只通用地工作（`quickMoveStack` 是原版菜单的公共方法），不引用其类型，不引入硬依赖 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证，尤其 `runClientCompat` 下两个模组、"物品栏满 + 手持模组背包"；Windows/Linux 无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增资源，不涉及共用图集与渲染；网络开销不变 | PASS（不适用） |

**Re-check after Phase 1 design**：设计阶段没有新增违反项。需要在实现阶段重点盯住的风险有两个，都已列入 quickstart 的必测项：
1. 物品栏满时的"换出再换回"对精妙背包/旅行者背包是否安全（research Decision 3，推断）；
2. 第三方 `quickMoveStack` 内部抛异常时无法回滚其内部状态（已知限制，守恒检查只做日志）。

## Project Structure

### Documentation (this feature)

```text
specs/012-ctrl-transfer-to-open-container/
├── plan.md              # 本文件
├── research.md          # Phase 0：9 条有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：槽位视图、临时槽位选择规则、状态转移
├── quickstart.md        # Phase 1：客户端验收步骤（含 runClientCompat）
├── contracts/
│   └── open-container-delivery.md  # Phase 1：服务端转出流程与不变量
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/
│   ├── OpenContainerDelivery.java     # 新增：泛型纯逻辑（临时槽位选择、换出换回、quickMove 循环与上限、异常、守恒），无 Minecraft 依赖
│   └── ContainerMenuAccess.java       # 新增：OpenContainerDelivery 对真实 AbstractContainerMenu 的适配（SlotView、读写槽位、quickMoveStack）
└── network/
    └── BackpackQuickTransferPayload.java  # 修改：TO_INVENTORY 分支先判断有无打开的容器；Direction 的 Javadoc 说明含义已扩展

src/test/java/pnnh/mcfloatingbackpack/
└── inventory/OpenContainerDeliveryTest.java  # 假菜单：箱子式/工作台式/拒收式/抛异常式/永不收敛式；满物品栏换出；守恒

specs/011-ctrl-quick-transfer/    # 修改：spec.md（FR-018 与澄清）、contracts/quick-transfer.md、DELIVERY.md 加【012 修订】
```

**Structure Decision**: 沿用单模块结构，算法放 `inventory/`（与 `QuickTransferLogic` 并列、无 Minecraft 依赖），与游戏相连的适配器单独成类，这样测试能直接加载算法。客户端目录不动。

### 反向（容器 → 面板）新增的结构（2026-11-05）

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/
│   ├── ContainerToBackpack.java                  # 新增：泛型纯逻辑（取出 → 放进背包 → 剩余放回，含回退链与守恒），无 Minecraft 依赖
│   └── QuickTransferRequest.java                 # 修改：新增 validateMenuSlots(...)
├── network/
│   ├── BackpackTakeFromContainerPayload.java     # 新增：客户端→服务端，containerId + 菜单槽位下标；服务端处理
│   └── BackpackNetwork.java                      # 修改：注册新包，版本 "4" → "5"
└── client/panel/
    ├── QuickTransferRules.java                   # 修改：白名单改为"任意容器界面、排除创造模式"；新增容器来源槽位判定
    ├── QuickTransferGesture.java                 # 修改：Side 增加 CONTAINER；INVENTORY 与 CONTAINER 同组
    └── QuickTransferInput.java                   # 修改：命中容器槽位；Shift 让路；每帧每种来源至多一个包

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/ContainerToBackpackTest.java        # 假槽位：取出放回、回退链、输出槽跳过、大堆叠、守恒
├── inventory/QuickTransferRequestTest.java       # 修改：补 validateMenuSlots 用例
├── client/panel/QuickTransferRulesTest.java      # 修改：新的界面规则与容器来源槽位判定
└── client/panel/QuickTransferGestureTest.java    # 修改：CONTAINER 侧与组的行为
```

**Constitution Check（反向补充）**：I 仍不使用 Screen；II 服务端取出并校验，客户端只上报菜单 id 与下标；III 在用户明确要求下扩充**当前** feature 的 spec 再实现（先改规格），仍是一个 feature；IV 不引用第三方类型，只用 `Slot`/`AbstractContainerMenu` 的公共方法；V 反向在 quickstart 八逐场景要求实测，尤其 `runClientCompat`；VI 不涉及。**PASS**。需要特别盯住的风险：第三方界面里 Ctrl 用法冲突、第三方槽位对 `safeTake`/`safeInsert` 的支持、被滚动隐藏的槽位是否被误命中。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违反项，不需要填写。
