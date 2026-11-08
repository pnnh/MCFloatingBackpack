# Implementation Plan: 标签总览——查看与管理这只背包给哪些物品设置过哪些标签

**Branch**: `025-tag-overview` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/025-tag-overview/spec.md`

## Summary

在 018 的标签面板里加一个**总览视图**：状态行右端的菜单图标在"选区视图"和"总览视图"间切换；总览分**列表页**（所有有标签的物品分类，含图标、名称、标签数量，悬停列出标签，可按单个标签筛选，每行可"清空"并需要行内二次确认）和**明细页**（点行进入，增删该分类的标签，规则沿用 018/022，物品不在背包里也能编辑）。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **没有新的存储**：标签数据仍是 018 的 `ItemTags`，`BackpackContents` 字段数不变；总览要的数据客户端已经有（整份标签同步）。
2. **视图状态放进新的纯单例 `TagOverviewState`**（视图、详情分类、筛选、列表滚动、筛选框、行内确认），**不改 `TagPanelState` 的结构**；明细页的添加框复用 `TagPanelState` 已有的 `editing/input/dropdown/pendingDelete/scroll`。
3. **明细页复用 018**：布局用 `TagPanelLayout`，数据用 `TagPanelModel.of(List.of(0), …)` 把"这个分类"当成"只选中一个分类的选区"，直接得到全部标签、`canAdd`、022 的保护判断。**列表页**新写纯几何 `TagOverviewLayout`。
4. **纯逻辑都进纯类**：`TagOverviewModel`（行、筛选、排序）、`ClearConfirm`（行内确认状态机，时间由调用方传入）、`TagOverviewRequest`（校验）、`TagOverviewRules`（增/删/清空的结果，含"只能对已有标签的分类添加"、已不在游戏里的物品不能添加、022 保护、≤ 10）、`TagCandidates`（下拉候选）、`TagBook.clear`。
5. **服务端：新增一个按物品注册 id 寻址的请求** `BackpackTagOverviewPayload(op, itemId, tag)`，`op` ∈ ADD/REMOVE/CLEAR；服务端核对"持有背包、下方功能区有标签管理器、id 合法、规则"，成功则保存并整份同步，失败零变化并补发同步；协议 `"12"` → `"13"`。018 的下标请求一行不改。
6. **明细页删光最后一个标签 → 自动回到列表页**（服务端只允许给已有标签的分类添加；spec 已据此修订）。
7. **不做乐观更新**：客户端发请求后等 `BackpackTagsPayload`，再按新数据显示。
8. **输入路由只加分支**：`view != SELECTION` 时 `TagPanelInput.onMainPress/onMainScroll` 转给 `TagOverviewInput`；键盘三事件的条件补上"筛选框有焦点"；`DeleteKeyInput` 的编辑判断补上筛选框；每 tick 收起检查与新会话时 `resetView()`。触点逐条列在 research Decision 7。
9. **菜单图标只用填充**，不新增贴图；渲染对行做可见范围裁剪与缓存排序，最多 1024 个分类也不卡。

范围：只做总览（列表、筛选、明细、清空）；批量、按名称搜索、给从未打过标签的物品加标签、重命名/合并、导入导出、格子角标、清空的撤销都不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`ScreenEvent`、`GuiGraphics`、`PacketDistributor`、注册表）；**无新增依赖，不涉及第三方模组**

**Storage**: 无新增（标签数据是物品数据组件里 018 的既有字段）；界面状态只在客户端会话里，不持久化

**Testing**: JUnit 5 纯逻辑测试：`TagBook.clear`、`TagOverviewRequest`、`TagOverviewRules`（全部分支）、`TagCandidates`、`TagOverviewModel`（过滤/排序/未注册物品）、`ClearConfirm`（超时、取代、复位）、`TagOverviewState`（视图转移与重置）、`TagOverviewLayout`（命中、滚动边界、窄宽截断位）；资源测试：两份语言文件的新键；加客户端会话手测（quickstart，宪法 V）

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（客户端界面与输入 + 一个网络请求 + 纯规则）

**Performance Goals**: 列表只画可见行；排序结果按"标签数据实例 + 筛选 + 语言"缓存；一次操作一个请求、一次整份标签同步（≤ 1024 个分类）

**Constraints**:
- 所有标签修改由服务端校验并执行；整个请求要么全成要么零变化（宪法 II）
- 011～024 的行为不变；唯一的可见变化是标签面板状态行右侧多一个菜单图标
- 不新增贴图、不新增依赖、不动数据格式；协议版本升到 `"13"`
- 键盘只在文本框有焦点时拦截

**Scale/Scope**: 约 9 个新类（`TagOverviewState`、`TagOverviewLayout`、`TagOverviewModel`、`ClearConfirm`、`TagOverviewContext`、`TagOverviewRenderer`、`TagOverviewInput`、`TagOverviewRequest`、`TagOverviewRules`、`TagCandidates`、`BackpackTagOverviewPayload`）；改动 `TagBook`（+`clear`）、`BackpackInventory`（+`hasTagManager`）、`BackpackNetwork`、`TagPanelRenderer`、`TagPanelInput`、`FloatingPanelRenderer`、`FloatingPanelInput`、`FloatingPanelEvents`、`DeleteKeyInput`、`BackpackSessionPayload`（一行）；约 8 个新测试类；资源：两份语言文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 总览是标签面板的另一个视图，画在同一条渲染路径里，输入走同一组 `ScreenEvent`；**不新增 Screen**，不替换/包装/拦截任何界面 | PASS |
| II. Server Owns Items | 标签是背包数据，服务端权威：新请求按物品 id 寻址，服务端核对"有标签管理器、id 合法、规则（只能给已有标签的分类添加、≤ 10、022 保护、已不在游戏里的物品不能添加）"，自己算"是否受保护"，不信客户端；失败零变化并补发同步；客户端不做乐观更新 | PASS |
| III. One Feature At A Time | 只做总览（列表、筛选、明细、清空）；批量、搜索、给新物品加标签、重命名、导入导出、角标明确不做；不改 018 选区视图、022 保护规则、024 黑名单页的行为 | PASS |
| IV. Vanilla Path Before Compat | 不涉及任何第三方模组 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证（切换、列表、筛选、明细、清空确认、保护、持久化、回归）；Windows/Linux 无法验证，登记为未验证；大量分类下的手感登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | **不新增任何贴图**，图标与面板只用 `fill` 与字体；每帧只画可见行并缓存排序，没有对所有玩家新增的成本（只在展开标签面板的总览视图时才计算）；实现完成后仍按惯例跑一次图集度量，度量不了就写"未度量" | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 不引用任何第三方类型，不新增依赖、`compat/` 代码或配置含义；`DependencyRulesTest` 照常通过 | PASS |

**Re-check after Phase 1 design**：没有违反项。实现阶段要盯住的风险：

1. **输入路由与键盘冲突**：总览新增两个文本框（筛选框、明细页添加框），键盘三事件与 Delete 键的判断要同时认；漏一处就会出现"打字触发快捷键"或"Delete 删了主区域物品"（research Decision 7 的表是清单）。
2. **大量分类下的手感**：1024 个分类的滚动、悬停与缓存失效（research 仍不确定项 1）；缓存键用 `ItemTags` 实例引用，注意同步后必须换实例。
3. **服务端规则的细节**：`ADD` 只允许对已有标签的分类、已不在注册表的物品拒绝添加、`CLEAR`/`REMOVE` 的幂等；都在纯类 `TagOverviewRules` 里，测试要覆盖每个分支。
4. **明细页与同步的竞态**：删光最后一个标签后要等同步再回列表；客户端每帧检查"该分类已没有标签"即回列表，避免停在空页。
5. **不改 018 已验证的行为**：`TagPanelInput/Renderer` 只加图标命中/绘制与视图分支；选区视图的全部验证项（018 的 quickstart）回归必须重跑。

## Project Structure

### Documentation (this feature)

```text
specs/025-tag-overview/
├── plan.md              # 本文件
├── research.md          # Phase 0：有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：TagBook.clear、请求与规则、候选、行模型、确认状态机、会话状态、列表布局、资源
├── quickstart.md        # Phase 1：客户端验收步骤（A～F）
├── contracts/
│   ├── overview-network.md   # Phase 1：协议 13、按 id 的请求与服务端流程、不变量
│   └── overview-ui.md        # Phase 1：状态行图标、列表页、明细页、行内确认、输入路由
├── checklists/requirements.md
└── tasks.md             # Phase 2（/speckit-tasks 生成，本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/
│   ├── BackpackInventory.java                   # 改：hasTagManager()（下方功能区整个列表）
│   └── tags/
│       ├── TagBook.java                         # 改：clear(keys)（纯）
│       ├── TagOverviewRequest.java              # 新：Op（ADD/REMOVE/CLEAR）+ validate
│       ├── TagOverviewRules.java                # 新：apply(book, op, key, tag, registered, isProtected)
│       └── TagCandidates.java                   # 新：预设 + 已用标签 − 排除，去重
├── network/
│   ├── BackpackTagOverviewPayload.java          # 新：客户端→服务端 ADD/REMOVE/CLEAR（按物品 id）
│   ├── BackpackNetwork.java                     # 改：协议 "13"，注册
│   └── BackpackSessionPayload.java              # 改：handleClient 里 TagOverviewState.resetView()（一行）
└── client/
    ├── FloatingPanelEvents.java                 # 改：键盘三事件条件、每 tick 收起时 resetView、登录/登出 reset
    └── panel/
        ├── FloatingPanelInput.java              # 改：编辑中点别处，筛选框也失焦
        ├── FloatingPanelRenderer.java           # 改：view != SELECTION 时画 TagOverviewRenderer
        ├── DeleteKeyInput.java                  # 改：编辑判断并上筛选框
        └── tags/
            ├── TagPanelRenderer.java            # 改：状态行右端画菜单图标，状态文字让位
            ├── TagPanelInput.java               # 改：菜单图标命中；view != SELECTION 时转给 TagOverviewInput
            ├── TagOverviewState.java            # 新：视图/详情/筛选/列表滚动/筛选框/行内确认（纯单例）
            ├── TagOverviewModel.java            # 新：行、筛选、排序（纯）
            ├── ClearConfirm.java                # 新：行内确认状态机（纯）
            ├── TagOverviewLayout.java           # 新：列表页几何与命中（纯）
            ├── TagOverviewContext.java          # 新：名称/图标/语言/缓存/候选，布局喂数
            ├── TagOverviewRenderer.java         # 新：列表页与明细页（fill + 字体 + 物品图标）
            └── TagOverviewInput.java            # 新：鼠标/滚轮/键盘，发 ADD/REMOVE/CLEAR

src/main/resources/assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json   # 改：overview.* 新键

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/tags/{TagBookClearTest,TagOverviewRequestTest,TagOverviewRulesTest,TagCandidatesTest}.java
├── client/panel/tags/{TagOverviewModelTest,ClearConfirmTest,TagOverviewStateTest,TagOverviewLayoutTest}.java
└── resources/TagOverviewResourcesTest.java
```

**Structure Decision**: 全部放在 018 已有的 `inventory/tags/` 与 `client/panel/tags/` 里，和标签面板并排，方便复用 `TagPanelLayout/Model/Context`；服务端规则用泛型纯类，客户端状态/几何/模型也是纯类，所以全部可单测。018 的类只做"加分支/加图标"的最小改动。

## Complexity Tracking

无违反项，不需要登记。
