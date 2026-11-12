# Implementation Plan: 背包搜索核心

**Branch**: `042-backpack-search-core` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/042-backpack-search-core/spec.md`；方案背景见 `docs/plan/backpack-search.md`；研究结论见 [research.md](./research.md)。

## Summary

在浮动面板标题栏加放大镜（代码绘制），点击后背包图标右边整段变搜索框，实时过滤主区域并高亮命中，支持基础语法（空格并且、`|` 或、`-` 排除、`"…"` 整段）。**纯客户端、零服务端改动**。核心思路（研究阶段读代码得出）：

1. **一处映射改变一切**：`PanelLayout` 是面板几何的唯一来源，渲染、命中、选区、转移、拖拽、Delete、滚动箭头都经 `layoutModel`/`PanelSlots.indexAt`。给 `PanelLayout` 增加可选的"主区域映射"（命中格子编号的有序数组），这些消费者全部自动按映射工作；发往服务端的始终是原来的背包格子编号，所以结果里的操作与平时完全一致，协议号仍 17（research D1）。
2. **搜索只影响滚动的总行数，不影响面板尺寸**（D2）。
3. **留位**：结果集只在查询变化/面板重开时重算，拿空的格子留在数组里，点它就是点原来的格子，所以"拿走再放回"回到原位（D7）。
4. **标题栏**：放大镜、关闭键、快照键都是 `fill` 画的，**不新增贴图**；新增纯算术的 `TitleBarLayout`/`SearchBoxLayout`，3 列去掉关闭键，搜索框占背包图标右边到右端（D4）。
5. **键盘**：沿用 018/025 的三个键盘事件接管方式，字符不设限，所以不复用 `TagTextInput`，新写按码点处理的 `SearchTextInput`（D5）。
6. 纯逻辑全进纯类：`SearchQuery`（解析）、`SearchMatcher`、`SearchResults`、`SearchTextInput`、`TitleBarLayout`、`SearchBoxLayout`，以及 `PanelLayout` 的映射；匹配通过 `TextMatcher` 小接口，043 换拼音实现时不改其它代码（D6）。

范围：只做搜索核心；拼音与多语言（043）、`@模组` `#标签` `/hidden`（044）、Ctrl+F、自动找空位放回都不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`ScreenEvent`、`GuiGraphics`、`BuiltInRegistries`）；**无新增依赖，不涉及第三方模组**

**Storage**: 无（搜索状态只在客户端会话里，不进背包数据、配置或网络包）

**Testing**: JUnit 5 纯逻辑测试：`SearchQueryTest`、`SearchMatcherTest`、`SearchResultsTest`、`SearchTextInputTest`、`PanelLayoutMappingTest`、`TitleBarLayoutTest`（取代并扩展 `TitleButtonsLayoutTest`）、`SearchBoxLayoutTest`、`SearchStateTest`、`SearchResourcesTest`；游戏里按 quickstart 验证

**Target Platform**: Minecraft 1.21.1 桌面客户端（服务端不涉及）；开发机 macOS，容器自动验证见 `scripts/ai-gui`

**Project Type**: 单一 NeoForge 模组（只改客户端界面与输入）

**Performance Goals**: 400 格背包每次按键重算 < 1 毫秒量级；没打开搜索时每帧只多一次空判断（D11）

**Constraints**:
- 服务端、网络包、协议号（17）、配置、命令、数据格式全部不动（宪法 II 不受影响）
- 不开搜索时 011～041 的行为不变；唯一的可见变化：3 列时不画关闭键
- 键盘只在搜索框有焦点时拦截
- 不新增贴图、不新增依赖

**Scale/Scope**: 约 9 个新类（`search/` 包：`SearchQuery`、`TextMatcher`、`SearchMatcher`、`SearchResults`、`SearchTextInput`、`SearchState`、`SearchInput`、`SearchRenderer`、`SearchContext`；`TitleBarLayout`、`SearchBoxLayout`）加对 `PanelLayout`、`FloatingPanelState`、`FloatingPanelRenderer`、`FloatingPanelInput`、`FloatingPanelEvents`、`DeleteKeyInput`、`BackpackSessionPayload`（只加一行重置）与两份语言文件的小改动

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 搜索框、高亮都画在同一条面板渲染路径里，输入走同一组 `ScreenEvent`；不新增 Screen，不替换/拦截任何界面 | PASS |
| II. Server Owns Items | **不触碰物品的归属与修改**：所有改变背包内容的操作仍由服务端按原来的请求执行，客户端只改"显示哪个格子"；没有新增请求，客户端不做乐观更新 | PASS |
| III. One Feature At A Time | 只做搜索核心；拼音、`@`、`#`、`/hidden`、Ctrl+F、自动找空位都明确不做；不改 011～041 的任何规则 | PASS |
| IV. Vanilla Path Before Compat | 不涉及任何第三方模组；匹配用的名字是物品自己的显示名与注册名 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证（A～H）；中文输入若容器里验证不了，如实登记"未验证"；Windows/Linux 无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | **不新增任何贴图**（放大镜用 `fill` 画）；没打开搜索时没有额外渲染成本；完成后仍度量一次图集（2048×1024），度量不了就写"未度量"，不声称通过 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 不新增依赖、不引用第三方类型；`DependencyRulesTest` 照常通过 | PASS |

**设计后复查**：D1 的映射只加在客户端纯几何类上，没有引入新的网络包；D4 据实修订了规格里"新贴图"的前提（已同步改 spec），宪法 VI 的结论更好；其余无变化，全部 PASS。

## Project Structure

### Documentation (this feature)

```text
specs/042-backpack-search-core/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/query-syntax.md
├── checklists/requirements.md
└── tasks.md              # 由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/client/panel/
├── search/                       # 新
│   ├── SearchQuery.java          # ★ 解析（contracts/query-syntax.md）
│   ├── TextMatcher.java          # ★ 函数式接口 + ofTexts（043 换拼音实现）
│   ├── SearchMatcher.java        # ★ 并且/或/排除
│   ├── SearchResults.java        # ★ compute、liveCount
│   ├── SearchTextInput.java      # ★ 码点文本、全选、长度 100
│   ├── SearchState.java          # 会话状态；变化时清选区、回滚动、推映射给 FloatingPanelState
│   ├── SearchContext.java        # 把背包格子/工具区格子变成 TextMatcher（显示名 + 注册名路径）
│   ├── SearchInput.java          # 键盘、字符、鼠标（放大镜、×、搜索框、焦点离开）
│   └── SearchRenderer.java       # 放大镜、搜索框、命中边框、命中数
├── TitleBarLayout.java           # ★ 新，取代 TitleButtonsLayout（保留其常量与让位规则）
├── SearchBoxLayout.java          # ★ 新（可放进 search/）
├── PanelLayout.java              # 改：可选 mainMap（D1）
├── FloatingPanelState.java       # 改：searchSlots、showClose、hitsSearchButton、hitsCloseButton/hitsDragBar 的新判断、clampScroll
├── FloatingPanelRenderer.java    # 改：关闭键按宽度画、放大镜、搜索框、搜索时 drawRowDecor 只画底色、命中边框与内圈选框、编号在搜索时隐藏
├── FloatingPanelInput.java       # 改：按下的顺序（放大镜 → × → 搜索框 → 关闭键 → 缩放 → 拖动）与焦点离开
└── DeleteKeyInput.java           # 改：编辑判断加上搜索焦点

src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java   # 改：三个键盘事件加搜索分支；closePanel/登录/登出重置
src/main/java/pnnh/mcfloatingbackpack/network/BackpackSessionPayload.java  # 改：新会话处 SearchState.reset()（与 TagOverviewState.resetView 同处）
src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json    # 改：4 个语言键

src/test/java/pnnh/mcfloatingbackpack/client/panel/
├── search/{SearchQueryTest,SearchMatcherTest,SearchResultsTest,SearchTextInputTest,SearchStateTest,SearchResourcesTest}.java
├── PanelLayoutMappingTest.java
├── TitleBarLayoutTest.java       # 取代并扩展 TitleButtonsLayoutTest
└── SearchBoxLayoutTest.java
```

**Structure Decision**: 与 018/025/024 同样的结构：纯逻辑进纯类并单测，客户端状态单例，输入与渲染各一个类，对既有文件只加分支。服务端、网络、数据格式一行不动。

## 实现要点（给 tasks 阶段）

1. **PanelLayout 映射**（D1）：`of(..., mainMap)`；`slotCount/slotIndex/mainTotalRows/maxScroll` 的主区域分支；无映射的路径逐字不变（现有 `PanelLayoutTest`/`PanelLayoutGeometryTest` 全部照常通过，再加 `PanelLayoutMappingTest`）。
2. **FloatingPanelState**：`searchSlots` + 在 `layoutModel` 传入；`clampScroll` 主区域用结果行数；`showClose()`（`visibleCols > MIN_VISIBLE_CELLS`）；`hitsCloseButton` 在无关闭键时为 false；新增 `hitsSearchButton`、`hitsSearchBox`、`hitsSearchClear`；`hitsDragBar` 保持整条，顺序靠 `FloatingPanelInput` 保证。
3. **输入顺序**（`dispatchMouseButton` 按下分支）：先"焦点离开"（点在搜索框以外且搜索有焦点 → 失焦），再 放大镜 → `×` → 搜索框（置焦点、按位置定光标）→ 快照键 → 关闭键 → 缩放把手 → 拖动把手 → …（其余不变）。
4. **键盘**：三个事件各加 `SearchState.get().isFocused()` 分支；Esc = `SearchState.close()`；Ctrl+V 粘贴用 `Screen.isPaste`；Ctrl+A 全选；`DeleteKeyInput` 的编辑条件加上搜索焦点。
5. **渲染**：`drawSlotsWithItems` 里主区域命中格画命中边框（选框为内圈）；`drawRowDecor` 搜索时只画底色；`drawTitleId` 搜索时不画；工具区命中格画命中边框；搜索框与命中数在 `SearchRenderer`。
6. **重置点**：`closePanel`、`onLoggingIn`、登出处、`BackpackSessionPayload`（D9）。
7. **语言键**（en/zh）：`panel.mcfloatingbackpack.search.tooltip`、`search.clear`、`search.placeholder`、`search.count`（悬停命中数的说明）。
8. **容器自动验证**要点（写进 quickstart 之外的 tasks）：搜索框焦点与 Esc、窄面板截图、拿走放回用 `/data get` 核对格子编号；中文输入先试 xdotool，不行记"未验证"。

## Complexity Tracking

无需要论证的违例。
