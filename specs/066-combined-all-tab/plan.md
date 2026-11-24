# Implementation Plan: 联合背包 · "全部"标签

**Branch**: `066-combined-all-tab` | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: [spec.md](./spec.md)；方案全文 [docs/plan/combined-backpack.md](../../docs/plan/combined-backpack.md) §4、§6、§7

## Summary

在 065 的联合界面里加"全部"标签：把成员背包的主区域按顺序拼成一个**平铺网格**，存取仍落在各自背包里。关键设计（读码得出，详见 [research.md](./research.md)）：

1. **平铺号 + epoch**：服务端只认"平铺号"（成员主区域长度的前缀和，与列数无关）；客户端把"各背包的格子首尾相连（不再每只从新的一行开始）"做成带 -1 的显示映射（复用 `PanelLayout.mainMap`，几何/滚动/命中不改）。每个请求带 `epoch`，成员表/布局一变、切了标签，旧请求就被拒并重发，**不会落到别的背包**（R9）。
2. **复用现有逻辑，不复制**：`BackpackClickPayload` 拆出 `applyClick`；拖动/收集的泛型逻辑抽成 `SlotAccess`；放入用 `AllSink` 包住 `insertIntoMain`；搜索用 `SearchState.Source` 换一个"全部"版（R2、R3）。
3. **内存光标 = 借用**：请求内把会话光标临时放进被操作的成员背包、保存前取回并清空，成员存档里永远没有这个光标；多成员手势用占位容器 + `PanelCursor`（R6）。冲洗在所有会话结束路径之前（R10）。
4. **合成视图装进客户端单例**：渲染/拖动/选择/快速转移几乎不改，只改灰格、请求的 `epoch`、染色与悬停、搜索与整理的数据源（R4）。
5. **顺手修 065 的隐患**：自动拾取与管子的单背包更新在联合面板上会错位；所有发送入口认会话（R7）。

用户决定（2026-11-24）：**取消 065 的联合标题栏**，"全部"里直接复用面板自己的标题栏上的搜索与整理（R11）；规格 FR-004、FR-019 已同步。

## Technical Context

**Language/Version**: Java 21，Minecraft 1.21.1，NeoForge 21.1.251

**Primary Dependencies**: 仅原版与 NeoForge；不新增 `compat/` 代码（宪法 VII）

**Storage**: 无。联合背包仍无数据；内存光标只在会话里；`DataRevision` 不变

**Testing**: JUnit 5 纯逻辑测试 + 源码文本接线测试；游戏内验证见 [quickstart.md](./quickstart.md)，AI 容器 `mcfb-c1`

**Target Platform**: 双端（服务端权威 + 客户端界面）

**Project Type**: NeoForge 模组（单项目）

**Performance Goals**: 没打开联合界面的玩家零开销；打开"全部"的玩家每秒一次成员表 + 最多 16 次内容比较（估算，未实测）；一个请求最多解码 16 个成员；每个成员同步包不超过现有整包

**Constraints**: 协议号 23 → 24；不用 mixin/反射；`MAX_SYNC_BYTES` 1 MiB；成员上限固定 16；平铺号范围 ≤ 16 × `MAX_MAIN_SLOTS`(423)

**Scale/Scope**: 约 14 个新类（纯逻辑 8、服务端适配 4、客户端 4）、3 个旧包加 `epoch`、2 个新包、6 个处理器接入、6 个处理器忽略、`BackpackClickPayload`/`BackpackCursorGesturePayload`/`BackpackQuickTransferPayload` 重构

## Constitution Check

| 原则 | 结论 | 说明 |
|---|---|---|
| I. Overlay Not Screen | ✅ | "全部"仍是叠加层上的面板；不新增 Screen；宿主界面路由不变 |
| II. Server Owns Items | ✅ | 所有物品移动在服务端；平铺号/epoch/身份校验在服务端；内存光标在服务端会话；客户端的显示整理/搜索/染色只是体验 |
| III. One Feature At A Time | ✅ | 只做 066；"已有同种物品优先"、EMI 排除区不在本期；标题栏融合已不需要 |
| IV. Vanilla Path Before Compat | ✅ | 纯原版可用；不碰第三方模组类型 |
| V. Verify In A Client Session | ⏳ | quickstart B；主观项由用户真机确认；DELIVERY 如实记录 |
| VI. Shared Resources Have A Budget | ✅ | 不新增贴图/模型；图集预算无变化 |
| VII. Minimal Dependencies | ✅ | 无新依赖、无新 `compat/` 类型；`DependencyRulesTest` 无需改动 |

**Phase 1 后复核**：设计未引入第三方类型、新 Screen、新持久化；唯一偏离是规格 FR-004 的措辞，理由见 research R11，不构成宪法违反。无 Complexity Tracking 条目。

## Project Structure

### Documentation (this feature)

```text
specs/066-combined-all-tab/
├── plan.md
├── research.md                      # 12 条读码结论与决定
├── data-model.md                    # 会话/成员/视图类型、纯逻辑清单、状态转移、语言键
├── quickstart.md                    # 自动测试清单、容器 14 项、真机项
├── contracts/
│   ├── protocol.md                  # 23 → 24 的包
│   ├── server-requests.md           # 12 个处理器逐个怎么办、校验、冲洗、每秒钩子
│   └── client-view.md               # 合成视图、显示映射、染色、搜索与整理
└── tasks.md                         # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/combined/
│   ├── AllAddressing / AllRequestRules / AllContentDiff / CursorFlush / SlotAccess   # 新：纯逻辑
│   ├── AllState / AllMember / AllContext / CursorLoan / AllSink / AllSync           # 新：服务端适配
│   ├── CombinedSessions.java            # 改：Session 加 mode/all；activateAll/flush/tick 的 ALL 分支
│   └── PanelTargets.java                # 改：inAll
├── network/
│   ├── CombinedMemberSyncPayload / CombinedCellUpdatePayload                        # 新（S→C）
│   ├── CombinedSelectPayload / CombinedMembersPayload                               # 改：SELECT_ALL、all
│   ├── BackpackClickPayload             # 改：epoch；拆 applyClick（loan）
│   ├── BackpackCursorGesturePayload     # 改：epoch；distribute/gather 抽成 SlotAccess 泛型
│   ├── BackpackQuickTransferPayload     # 改：epoch；放入用 AllSink、取出用 SlotAccess
│   ├── BackpackTakeFromContainerPayload / BackpackNetworkTakePayload / MergePanelCursorPayload  # 改："全部"分支
│   ├── Backpack{Trash,TrashSend,Sort,Tag,TagOverview,PickupBlacklistOp}Payload     # 改："全部"里忽略
│   └── BackpackNetwork.java             # 改：协议 24、注册；sendSlotUpdate/sendFullSync 认会话；sendPickupUpdate
├── inventory/pickup/AutoPickupEvents.java  # 改：走 sendPickupUpdate
├── pipe/PipeActions.java                # 改：经会话认得的入口（只发给目标是它的会话）
├── inventory/identity/BackpackGate.java # 改：noteClosed/forget 联动冲洗（需要 ServerPlayer）
├── MCFloatingBackpack.java              # 改：下线先 flush；停服先 flush 再 clear
└── client/
    ├── panel/ClientBackpackView.java    # 改：applyCombined（合成视图）
    ├── panel/ClientPlacement.java       # 改：全部下的灰格
    ├── panel/FloatingPanelState.java    # 改：显示映射来源、All 模式下搜索/整理基准
    ├── panel/FloatingPanelInput.java / PanelDragInput / QuickTransferInput          # 改：请求带 epoch；加载中不发
    ├── panel/FloatingPanelRenderer.java # 改：染色、悬停来源行、"全部"下不画编号与快照、填充格
    ├── panel/search/SearchContext.java  # 改：Source 在"全部"里换成全部版
    └── panel/combined/   # 065 的 CombinedBarLayout/Renderer/Input 去掉标题栏行
        ├── AllGrid / AllSortPermutation / AllSearchMap                              # 新：纯逻辑
        ├── ClientCombinedView                                                       # 新
        ├── ClientCombinedState / CombinedBarLayout / CombinedBarInput / CombinedBarRenderer  # 改："全部"标签、选中、悬停高亮、搜索/整理
        └── CombinedColors                                                           # 沿用

src/main/resources/assets/mcfloatingbackpack/lang/en_us.json, zh_cn.json            # 改

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/combined/{AllAddressing,AllRequestRules,AllContentDiff,CursorFlush,SlotAccess}Test
├── client/panel/combined/{AllGrid,AllSortPermutation,AllSearchMap}Test
└── resources/AllWiringTest                                                          # 新；旧接线测试协议号 23 → 24
```

**Structure Decision**: 单项目、沿用包布局；纯逻辑放 `inventory/combined`、`client/panel/combined`（泛型、无 Minecraft 类型，可被单元测试直接加载），适配层单独成类。

## Complexity Tracking

无宪法违反，无条目。

## 实现顺序（供 /speckit-tasks 参考）

0. **先验证有风险的假设**：`applyClick` + `CursorLoan` 在点击路径上的可行性（先做一个只含点击的垂直切片，容器里点验）；顺带做 065 的瘦身：取消联合标题栏（`BARS_H = TAB_H`），确认标签栏、面板、JEI 排除区、位置钳制仍对。
1. 纯逻辑与测试（寻址、显示映射、请求规则、内容比较、冲洗、`SlotAccess`、整理排列、搜索映射）。
2. 服务端：`AllState/AllContext/AllSink/AllSync`、会话扩展（`activateAll`/`flush`/`tick`）、两个新包 + 三个旧包加 `epoch`、协议 24、`BackpackNetwork` 发送入口认会话、点击/手势/快速转移/取出/网络取出/合并的"全部"分支、6 个单背包处理器忽略、`AutoPickupEvents`/`PipeActions` 改道、`BackpackGate`/下线/停服的冲洗联动。
3. 客户端：`ClientCombinedView` + 合成视图、显示映射、请求 `epoch`/加载中、灰格、染色/悬停/标签高亮、"全部"标签与选中、标题栏精简、搜索 `Source`、整理排列表、关闭/切换清理。
4. 文档：README 中英文、docs/plan 状态、DELIVERY、旧测试更新。
5. 容器验证 quickstart B；用户真机确认 C。
