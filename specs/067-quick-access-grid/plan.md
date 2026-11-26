# Implementation Plan: 快捷访问网格

**Branch**: `067-quick-access-grid` | **Date**: 2026-11-26 | **Spec**: [spec.md](./spec.md)

**Input**: [spec.md](./spec.md)；决策全文 [docs/plan/quick-access.md](../../docs/plan/quick-access.md)

## Summary

在游戏画面按 Z 弹出一块居中的透明网格，列出「按 B 会打开的那只背包」里带预设标签 `quick` 的物品组，左键与主手整组对调。读码后的做法（详见 [research.md](./research.md)）：

1. **自己的透明界面**（R1）：`QuickAccessScreen` 只在没有别的界面、且浮动面板关着时打开。不暂停、不压暗、不存放物品。不借用面板宿主。
2. **名单和视口与游戏无关**（R4）：`QuickCandidates` 按格子顺序取前一半；`QuickViewport` 默认 9×4，窗口不够就缩小，滚轮一次一行，三角只提示。
3. **新的一对网络包**（R5）：面板关掉时现有点击包会被丢掉，所以交换走 `quick_swap` / `quick_swap_result`。服务端用 `BackpackGate.acquire` 重算名单再对调。失败时网格留着。
4. **下方区至多一个**（R6）：两处加上现成的 `ToolSingleRules`，不改它的签名。
5. **修订 7、协议 25**（R7）：新物品会进背包；新包必须升协议。预设 `quick` 加在列表末尾。

## Technical Context

**Language/Version**: Java 21，Minecraft 1.21.1，NeoForge 21.1.251

**Primary Dependencies**: 仅原版与 NeoForge；不新增 `compat/` 代码（宪法 VII）

**Storage**: 无新字段。标签 id 进现有标签书；新物品使 `DataRevision.CURRENT` 6 → 7

**Testing**: JUnit 5 纯逻辑测试 + 源码文本接线；游戏内验证见 [quickstart.md](./quickstart.md)

**Target Platform**: 双端（服务端权威 + 客户端界面）

**Project Type**: NeoForge 模组（单项目）

**Performance Goals**: 没按键时零额外开销（无每 tick 扫描）。打开时一次遍历主区域（下界合金最多 108 + 可见扩展行）。滚动和命中是整数运算。

**Constraints**: 协议 24 → 25；不用 mixin / 反射；网格不暂停；视口至少 1×1 且完整留在画面内；交换不合并、不拆分

**Scale/Scope**: 2 个纯逻辑类、1 个物品、1 个界面、2 个包、两处限放、预设与文案、约 21 个历史协议断言和 8 处修订断言要改字面量

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结论 | 说明 |
|---|---|---|
| I. Overlay Not Screen | ✅ | 网格是本 feature 自己的 Screen，只在没有任何界面且面板不可见时出现；不替换、不包裹物品栏或箱子；不暂停、不存放物品。面板宿主仍只托面板（R1） |
| II. Server Owns Items | ✅ | 对调只在服务端 `acquire` 之后写入；客户端名单只是预览；结果包告诉界面关不关 |
| III. One Feature At A Time | ✅ | 只做 067。不钉格、不轮盘、不和副手交换、不在已打开的界面里弹出 |
| IV. Vanilla Path Before Compat | ✅ | 不碰第三方类型。联合背包、快照页只是「界面开着则按键无效」 |
| V. Verify In A Client Session | ⏳ | [quickstart.md](./quickstart.md) 的 10 步；未进游戏的不记成完成 |
| VI. Shared Resources Have A Budget | ✅ | 一张 16×16 物品平面图，不新增方块，不进方块图集，不测 010 的 2048 预算（R8） |
| VII. Minimal Dependencies | ✅ | 无新依赖、无新 `compat/`。`DependencyRulesTest` 不用改 |

**Phase 1 后复核**：契约没有引入第三方类型、没有把宿主挪作他用、没有新的存档字段。受保护背包不走「再打开一次确认」，避免网格变成第二条确认路径（R5）。无 Complexity Tracking 条目。

## Project Structure

### Documentation (this feature)

```text
specs/067-quick-access-grid/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── protocol.md          # 24 → 25，两个包
│   ├── grid.md              # 打开条件、视口、关闭
│   └── swap.md              # 服务端八步
└── tasks.md                 # /speckit-tasks 生成，本命令不写
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/quick/
│   ├── QuickCandidates.java             # 新：名单，无 Minecraft 类型
│   └── QuickViewport.java               # 新：视口、滚动、命中，无 Minecraft 类型
├── inventory/BackpackInventory.java     # 改：hasQuickAccess，扫下方区
├── inventory/revision/DataRevision.java # 改：CURRENT = 7
├── inventory/tags/PresetTags.java       # 改：末尾加 quick
├── item/QuickAccessItem.java            # 新
├── item/ModItems.java                   # 改：注册，创造栏紧挨标签管理器
├── network/QuickSwapPayload.java        # 新：C→S
├── network/QuickSwapResultPayload.java  # 新：S→C
├── network/BackpackNetwork.java         # 改：versioned("25")，注册两个包
├── network/BackpackClickPayload.java    # 改：下方区多一处 ToolSingleRules
├── client/FloatingBackpackKeys.java     # 改：QUICK_ACCESS，默认 Z，IN_GAME
├── client/FloatingPanelEvents.java      # 改：按下时开门或提示；吃掉副手交换
├── client/quick/
│   ├── QuickAccessScreen.java           # 新：透明、不暂停
│   └── QuickNotices.java                # 新：客户端 20 tick 冷却
└── client/panel/FloatingPanelInput.java # 改：refusedByLimit 多一处

src/main/resources/
├── assets/mcfloatingbackpack/lang/zh_cn.json, en_us.json
├── assets/mcfloatingbackpack/models/item/quick_access.json
├── assets/mcfloatingbackpack/textures/item/quick_access.png   # 16×16 占位
└── data/mcfloatingbackpack/
    ├── recipe/quick_access.json
    └── tags/item/never_trash.json        # 加上 quick_access

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/quick/QuickCandidatesTest.java
├── inventory/quick/QuickViewportTest.java
├── inventory/tags/PresetTagsTest.java    # 改：22 个
├── inventory/revision/DataRevisionTest.java
└── resources/QuickAccessWiringTest.java
```

历史接线测试里的 `"24"` 和 `CURRENT = 6` 按 [research.md](./research.md) R7 改字面量。

**Structure Decision**: 单项目。名单和视口放在 `inventory/quick`，测试能直接加载。界面和冷却放在 `client/quick`。物品与包沿用现有的 `item/`、`network/`。

## Complexity Tracking

无宪法违反，无条目。

## 实现顺序（供 /speckit-tasks 参考）

1. 预设、物品、配方、贴图、`never_trash`、创造栏、修订 7、限放两处。
2. `QuickCandidates` 与 `QuickViewport` 及单测。
3. 按键、提示、`QuickAccessScreen`（先能开、能滚、能关，点击只在本地算中哪一格）。
4. 两个包、协议 25、服务端八步、结果包关网格；接线测试与历史字面量。
5. 游戏内走 [quickstart.md](./quickstart.md)，写 DELIVERY，改 README 与规划状态。
