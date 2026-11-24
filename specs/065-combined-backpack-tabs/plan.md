# Implementation Plan: 联合背包 · 单背包标签

**Branch**: `065-combined-backpack-tabs` | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/065-combined-backpack-tabs/spec.md`；方案全文 [docs/plan/combined-backpack.md](../../docs/plan/combined-backpack.md)

## Summary

新增五档无数据物品"联合背包"。穿戴后按 U 打开联合界面：面板上沿之上挂一条联合标题栏 + 标签栏，每个成员背包一个标签；点标签 = 服务端把**会话目标**改成那只背包并发一次现有的 session 整包同步，面板本身一字不改。关键设计（读码得出，详见 [research.md](./research.md)）：

1. **会话目标 + 统一解析**：`PanelTargets.resolve(player)` 替换 13 处面板类 `BackpackRef.resolve`，无联合会话时与旧行为逐字节一致；非面板的"B 激活"语义（自动拾取、唱片机、移动工具、背部渲染……）一律不改。
2. **复用既有机制**：`OpenSessions` 的过期保护、`BackpackNetwork.sendSession` 的整包同步、`BackpackGate.resync` 的自愈、`WornSource` 体系、`BackpackSearch` 泛型。新增的只有成员表、会话、两个包、客户端条形界面。
3. **标签栏挂在面板外沿**（不改 `PanelLayout`），像垃圾桶页：先于面板认领点击与滚轮，JEI 多一个排除区矩形，位置钳制加下限。**这偏离了规格 FR-020 的 `topExtra`**，规格已改写为行为描述。
4. **移动工具（唱片机、工作台、熔炉）、自动拾取保持"B 激活的那只"**；联合界面里非激活标签的三个工具控件灰掉（规格原只写唱片机，已补全）。

## Technical Context

**Language/Version**: Java 21，Minecraft 1.21.1，NeoForge 21.1.251

**Primary Dependencies**: 仅原版与 NeoForge；Curios/Accessories 只改数据标签，不新增 `compat/` 代码（宪法 VII）

**Storage**: 无（联合背包物品无数据组件；`DataRevision` 仍 6；不写存档新字段）

**Testing**: JUnit 5 纯逻辑测试 + 源码文本接线测试（沿用 `NestingWiringTest` 风格）；游戏内验证见 [quickstart.md](./quickstart.md)，AI 容器 `mcfb-c1`

**Target Platform**: 双端（服务端权威 + 客户端界面）

**Project Type**: NeoForge 模组（单项目）

**Performance Goals**: 没有联合会话的玩家零额外开销（每秒钩子一次 map 查找）；开着联合界面的玩家每秒一次成员表计算（约 50 个栈的类型判断，估算，未实测）；请求内校验 O(1)

**Constraints**: 协议号 22 → 23；`DataRevision` 不变；不用 mixin / 反射；一个 session 包 ≤ 1 MiB（沿用 `MAX_SYNC_BYTES`）；成员上限固定 16

**Scale/Scope**: 约 10 个新类（纯逻辑 5、服务端 3、客户端 3）、13 处服务端解析替换、客户端输入/渲染小改、5 件物品资源、2 个包、2 个配方、语言键约 25 个

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结论 | 说明 |
|---|---|---|
| I. Overlay Not Screen | ✅ 通过 | 联合标题栏/标签栏是面板的外沿延伸，仍是叠加层；不新增 Screen，不改宿主界面职责；U 键沿用 `KeyConflictContext.UNIVERSAL` 与宿主按键路由 |
| II. Server Owns Items | ✅ 通过 | 所有物品移动仍在服务端；会话目标、成员表、身份校验全在服务端；客户端的"加载中/灰掉"只是体验，不是安全边界 |
| III. One Feature At A Time | ✅ 通过 | 只做 065；"全部"标签、显示搜索/整理、跨背包寻址留给 066，标签栏不为它留占位 |
| IV. Vanilla Path Before Compat | ✅ 通过 | 纯原版可用（胸甲槽）；Curios/Accessories 只加物品标签，沿用 061 的开关与槽位 |
| V. Verify In A Client Session | ⏳ 待做 | 见 quickstart B；界面观感由用户真机确认；DELIVERY 如实记录未验证项 |
| VI. Shared Resources Have A Budget | ⏳ 待做 | 5 张新物品贴图进共享图集：**加入前后各运行 `design/demo/tools/check_atlas_budget.sh` 并记录**；无模型（三角形 0）；对未持有背包的玩家无渲染成本 |
| VII. Minimal Dependencies | ✅ 通过 | 无新依赖、无新 `compat/` 代码；`DependencyRulesTest` 无需改动 |

**Phase 1 后复核**：设计没有引入新的第三方类型、没有新 Screen、没有新持久化；偏离规格一处（FR-020 的 `topExtra`），理由与替代见 research R8，**不构成宪法违反**。无 Complexity Tracking 条目。

## Project Structure

### Documentation (this feature)

```text
specs/065-combined-backpack-tabs/
├── plan.md              # 本文件
├── research.md          # Phase 0：13 条读码结论与决定
├── data-model.md        # Phase 1：运行时类型、成员表规则、会话与解析规则、语言键
├── quickstart.md        # Phase 1：自动测试清单与游戏内 15 项验证
├── contracts/
│   ├── protocol.md      # 两个新包的字段、服务端处理步骤、客户端 loading 规则
│   ├── panel-targets.md # 统一解析规则与 13 处逐项检查清单、接线测试
│   └── ui-layout.md     # 标题栏/标签栏几何、输入认领、灰掉的控件
└── tasks.md             # Phase 2（/speckit-tasks，本命令不生成）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── item/
│   ├── CombinedBackpackItem.java            # 新：五档物品（Equipable，无数据）
│   └── ModItems.java                        # 改：注册五件 + 创造栏
├── inventory/
│   ├── BackpackRef.java                     # 改：加 normalized(...)
│   ├── BackpackEmptiness.java               # 新：配方用的"空背包"纯规则
│   ├── combined/                            # 新包
│   │   ├── CombinedMembers.java             #   纯：成员表规则与签名
│   │   ├── CombinedWorn.java                #   纯：多个联合背包取最高档
│   │   ├── PanelTargetRules.java            #   纯：解析判定
│   │   ├── CombinedSessions.java            #   会话（内存）、open/select/recover/tick/forget
│   │   ├── PanelTargets.java                #   适配：resolve(player)
│   │   └── CombinedCandidates.java          #   适配：从玩家收集候选（穿戴/副手/物品栏）
│   ├── worn/WornBackpacks.java              # 改：register 同时登记到联合背包的第二个 BackpackSearch
│   ├── identity/BackpackGate.java           # 改：noteClosed/forget 联动、resync 先 recover、tickOpenSession 联合钩子
│   ├── nesting/NestingRules / BackpackNesting  # 改：联合背包 id 视为"不进背包"
│   └── recipe/
│       ├── CombinedCraftingRecipe.java      # 新：ShapedRecipe 子类，只覆盖 matches（中间必须空背包）
│       └── ModRecipes.java                  # 改：登记 combined_crafting
├── network/
│   ├── CombinedSelectPayload.java           # 新（C→S）
│   ├── CombinedMembersPayload.java          # 新（S→C）
│   ├── BackpackNetwork.java                 # 改：协议 23、注册两包
│   └── （13 个面板类处理器）                # 改：BackpackRef.resolve → PanelTargets.resolve；点击包联合会话内的声称引用校验
├── diag/DiagConfig.java + combined/CombinedConfig.java   # 新/改：[combined].enabled
├── MCFloatingBackpack.java                  # 改：下线/停服清理 CombinedSessions
└── client/
    ├── FloatingBackpackKeys.java            # 改：COMBINED（U）
    ├── FloatingPanelEvents.java             # 改：U 键、滚轮认领
    ├── panel/
    │   ├── HostKeyRouter.java               # 改：认第二个 toggle 键
    │   ├── FloatingPanelState.java          # 改：clampToScreen(topReserve)
    │   ├── FloatingPanelInput.java          # 改：先问 CombinedBarInput；loading 时不发请求；三个移动工具入口灰掉
    │   ├── FloatingPanelRenderer.java       # 改：联合模式下画条形界面；非激活标签的工具灰化
    │   └── combined/                        # 新包
    │       ├── ClientCombinedState.java
    │       ├── CombinedBarLayout.java       #   纯：几何
    │       ├── CombinedColors.java          #   纯：16 色表
    │       ├── CombinedBarInput.java
    │       └── CombinedBarRenderer.java
    └── compat/ (JEI) FloatingBackpackJeiPlugin  # 改：多一个排除区矩形（联合模式）

src/main/resources/
├── assets/mcfloatingbackpack/
│   ├── models/item/<tier>_combined_backpack.json   # 新 ×5（item/generated）
│   ├── textures/item/<tier>_combined_backpack.png  # 新 ×5（占位，待用户确认）
│   └── lang/en_us.json, zh_cn.json                 # 改
└── data/
    ├── mcfloatingbackpack/recipe/combined_<tier>_backpack.json ×5 + upgrade_<tier>_combined_backpack.json ×4
    └── curios|accessories/tags/item/floating_backpack.json    # 改：加五个联合背包

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/combined/…Test（成员表、最高档、解析判定、会话）
├── inventory/BackpackEmptinessTest
├── client/panel/combined/CombinedBarLayoutTest
└── resources/CombinedResourcesTest, CombinedWiringTest
```

**Structure Decision**: 单项目、沿用现有包布局；纯逻辑放 `inventory/combined`、`client/panel/combined`（泛型、无 Minecraft 类型），适配层单独成类，保持"纯逻辑能被单元测试直接加载"的现有约定。

## Complexity Tracking

无宪法违反，无条目。

## 实现顺序（供 /speckit-tasks 参考）

1. 物品与资源（物品、模型、贴图占位、lang、标签、配方、`BackpackEmptiness`、图集预算测量）。
2. 纯逻辑与测试（成员表、取最高档、解析判定、归一化、条形布局、颜色表）。
3. 服务端：成员候选收集、`CombinedSessions`、`PanelTargets`、`WornBackpacks` 第二个查找、两个包与协议 23、`BackpackGate` 联动、13 处替换 + 点击包声称引用校验、配置、清理。
4. 客户端：键、状态、条形界面（输入/渲染）、钳制、JEI、loading 拦截、工具控件灰化。
5. 064 嵌套规则认联合背包；README 中英文、docs/plan 状态、DELIVERY。
6. 容器验证 quickstart B；用户真机确认 C。
