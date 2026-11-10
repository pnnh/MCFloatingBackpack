# Implementation Plan: 分类扩展：界面

**Branch**: `037-category-extension-ui` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

## Summary

给含扩展行的面板做界面（设计依据见 [research.md](./research.md)）。**核心结论：布局几何一行不用改**——主区域本来就是"一个按列数折行的平面格子列表"，扩展行只是列表末尾的下标区间；所以全部工作是"按下标算出每个格子属于哪个行，再据此画与判"。

1. **一个纯逻辑类 `RowLayout`**（`inventory/extension/`，无 Minecraft 类型）：给定基础容量与可见行的类别，回答"某个下标属于哪个组（主 / 第 k 行 / 无）"、"该格子的上边与左边要不要画分隔线（按当前列数）"、"某组是不是行的第一格"。底色、分隔线、行首角标、悬停说明、黄点之外的一切几何判断都从它来；有完整单元测试。
2. **类别样式表 `CategoryStyle`**（客户端，代码里一张表）：10 个类别各一个底色、一个分隔线色；空格里画该类别扩展件的物品图（淡色"幽灵图标"，复用已有的 36 贴图，不新增贴图），行的第一格左上角画一个小色块角标——不只靠颜色。具体色值见 [contracts/ui.md](./contracts/ui.md)，游戏里看过可调。
3. **绘制**（`FloatingPanelRenderer.drawSlotsWithItems`）：每个格子在原有的槽底之后叠一层类别底色、分隔线、角标、空格的幽灵图标；光标上有物品且这一格放不进去时，悬停高亮换成淡红。没有扩展行时这些分支一个都不走。
4. **悬停说明**：悬停在扩展行的任一格上，在面板里画一条提示"食物行：只能放食物"；**面板现在不画物品自己的提示**（核对过），所以这里只有类别说明（规格验收场景 6 据此修正，见下）。
5. **黄点**（`drawTitleIcon`）：有隐藏内容时，在档位图标右上角画一个 5×5 的黄点；悬停黄点列出各类别"N 格有物品"和"把对应的分类扩展放回去"。点的位置不与编号（起点在图标右边缘之后 3 像素）冲突。
6. **合法尺寸只按基础容量**：唯一要改的是 `ClientBackpackView.reportCounts()` 把 `setMainCount` 从 `slots.size()` 改成 `baseCapacity()`；滚动用的总行数仍按含扩展行的总格数（`rows()` 不变）。另一个小修：`applyFull` 现在在格子数变化时把滚动归零，放上或拿走扩展件会让面板跳回顶部——改成只有基础容量变化才归零，其余交给 `clampScroll`。
7. **客户端预测**（同一套规则，不另写）：把 035 的 `ExtensionRules.canPlace`/`targetSegments` 加一组"只给可见行类别列表"的重载（旧的按 `RowSet` 的版本委托给它，行为与测试不变），客户端用 `RowView` 的类别列表 + `ItemCategoryMatcher(view.tags())` 判定；标签还没同步到客户端时一律放行（服务端裁判）。接入两处**真正会落到扩展行的客户端手势**：
   - **点击**（`FloatingPanelInput.sendClick` 的主区域分支）：光标上有物品且放不进，直接不发包（和 021 的 `refusedByLimit` 一样"省一次往返"）；
   - **拖拽分配**（`PanelDragGesture`）：加一个"这一格接不接受"的谓词，选格与预览都跳过放不进的格子，和服务端 `distribute` 的过滤一致。
8. **不需要改的手势（查证过）**：选区（014）与 Ctrl 转出（012）、双击聚拢（013）、垃圾桶倾倒（021）都是"把东西从格子里拿出来"，规则允许任意格子拿出；Ctrl 转入（011）由服务端按 `insertIntoMain` 选目标格，客户端没有目标格预览。所以 spec 里"选区、快速转移、聚拢、倾倒要知道类别规则"的字面要求在这里是**空操作**，只需回归验证它们的范围正确（可见行参与、隐藏行不在列表里）。
9. **协议不改（仍是 17）**：`RowView` 已含基础容量、可见行类别、隐藏行"有物品的格数"（按类别），足够画底色、分隔线、黄点与提示。"件数"需要升协议，不做。

不新增物品、贴图、配置项、命令、网络包、数据组件；不改 035 的规则（只给它加重载）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 无（纯客户端显示；类别样式是代码里的常量表）

**Testing**: JUnit 5。纯逻辑（`RowLayout`、`ExtensionRules` 新重载、`PanelDragGesture` 的接受谓词、`CategoryStyle` 表的完整性、隐藏提示行的生成、语言键一致）不加载 Minecraft；绘制与交互只能在客户端会话里验证（宪法 V）

**Target Platform**: 客户端（绘制与手势）；服务端不改；开发机 macOS，运行时带 Accessories/Curios/JEI 等

**Project Type**: Minecraft 模组（NeoForge）

**Performance Goals**: 没有扩展行的背包**零额外开销**（分支不进入）；最多 35 行（315 格）时面板不明显掉帧——**未测量**，按宪法 VI 测量并记录，测不了写"未测量"

**Constraints**: 宪法 I（仍是叠加层，不新增 Screen）、II（服务端是最终裁判，客户端只预判，且只会多放行不会误拦）、III（只做界面，不改 035 规则）、V、VI、VII；协议保持 17

**Scale/Scope**: 新增 `RowLayout`、`CategoryStyle`、`HiddenHint`（纯逻辑）与一个客户端判定类 `ClientPlacement`；改 `FloatingPanelRenderer`、`ClientBackpackView`（两处）、`FloatingPanelInput.sendClick`、`PanelDragGesture`/`PanelDragInput`、`ExtensionRules`（重载）、语言文件（中英文各约 6 条）；约 6 个测试类

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 所有绘制都在现有的面板叠加层里（`ScreenEvent.Render` 钩子），不新增 Screen，不改主机屏 | PASS |
| II. Server Owns Items | 客户端判定只用来**不发必败的请求**和**画预览**；服务端仍用同一套规则裁判。客户端在标签未同步时放行，宁多放行不误拦 | PASS |
| III. One Feature At A Time | 只做界面；035 的规则不改（只加重载，旧入口行为不变并有测试）；搜索、整理、标签面板只读类别都不做 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方模组；Accessories/Curios 背着的背包只作回归 | PASS |
| V. Verify In A Client Session | quickstart 要求在客户端会话里验证外观、不同列数/滚动/缩放、预览与服务端一致、黄点与提示、创造模式与主机屏；绘制没有自动化测试，**不会用编译通过代替验证** | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增任何贴图，图集不变（空格里的幽灵图标复用 036 的物品贴图，从物品图集取）；**绘制开销未测量**，在游戏里用 35 行背包看帧率并记录，测不了写"未测量" | PASS（待测量） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖；`DependencyRulesTest` 继续通过 | PASS |

## 对规格的两处修正（plan 阶段查证后）

1. **验收场景 US1-6**：规格写"悬停在有物品的格子上，物品自己的提示照常显示，并带上所属行的类别说明"。核对过 `client/` 目录：面板**从来不画格子里物品的提示**（只画标题图标、编号、按钮、垃圾桶面板的提示）。所以本 feature 只做"扩展行格子的类别说明"，**不新增物品提示**（那是另一个功能）。
2. **"浅色与深色面板"**：面板只有一种配色（原版灰 `0xFFC6C6C6`，槽底 `0xFF8B8B8B`），没有深浅主题；SC-001 的"两种外观"改为"在槽的普通状态与悬停高亮 / 选区 / 拖拽预览叠加状态下都分得清"。

## Project Structure

### Documentation (this feature)

```text
specs/037-category-extension-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── ui.md            # 几何、色值、角标、幽灵图标、黄点、悬停文字、语言键
│   └── prediction.md    # 客户端判定：输入、放行条件、接入点、一致性
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/extension/
│   ├── RowLayout.java            # 新：下标 → 组、分隔线、行首（纯逻辑）
│   ├── ExtensionRules.java       # 改：加"可见行类别列表"重载，RowSet 版本委托
│   └── HiddenHint.java           # 新：RowView 隐藏摘要 → 提示用的行（纯逻辑）
├── client/panel/
│   ├── CategoryStyle.java        # 新：10 个类别的色值与幽灵图标物品
│   ├── ClientPlacement.java      # 新：客户端"这一格放不放得进"（同一套规则 + 标签就绪检查）
│   ├── ClientBackpackView.java   # 改：reportCounts 用基础容量；滚动只在基础容量变时归零
│   ├── FloatingPanelRenderer.java# 改：底色、分隔线、角标、幽灵图标、放不进的红色高亮、行说明、黄点与提示
│   ├── FloatingPanelInput.java   # 改：sendClick 主区域分支的预判
│   ├── PanelDragGesture.java     # 改：接受谓词（旧签名委托，行为不变）
│   └── PanelDragInput.java       # 改：把 ClientPlacement 接进去
src/main/resources/assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json   # 约 6 条新键
src/test/java/pnnh/mcfloatingbackpack/
├── inventory/extension/{RowLayoutTest, ExtensionRulesCategoriesTest, HiddenHintTest}.java
├── client/panel/{PanelDragGestureAcceptTest, CategoryStyleTest}.java
└── resources/CategoryExtensionUiResourcesTest.java   # 语言键、源码里的接线文本检查
```

**Structure Decision**: 几何与判定放在无 Minecraft 类型的纯逻辑类（`RowLayout`、`HiddenHint`、`ExtensionRules` 重载）里，客户端类只做"取输入、画"，沿用 017/035 的做法；样式表与客户端判定因为要读 `ItemStack`、标签注册表，放在 `client/panel/`。

## Complexity Tracking

无需要说明的违规。
