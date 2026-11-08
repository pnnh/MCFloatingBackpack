# Delivery: 025-tag-overview

**日期**: 2026-11-08

**状态**: 代码、纯逻辑测试与文档已完成（T001～T026、T028～T031、T033～T037、T039～T044、T046～T048、T050、T052、T054）；**客户端验证（T027、T032、T038、T045、T049）等用户逐项确认，图集度量（T053）在游戏退出后跑，DELIVERY 最终定稿（T055）在这之后**。

## 自动化结果

| 项 | 结果 |
|---|---|
| 改动前基线（T001） | 024 收尾时 `./gradlew compileJava test` 通过：69 个测试类、515 个用例、0 失败（此后没有改动代码） |
| Phase 2（T021） | `./gradlew compileJava test` 通过；新增 `TagBookClearTest`、`TagOverviewRequestTest`、`TagOverviewRulesTest`、`TagCandidatesTest`、`TagOverviewModelTest`、`ClearConfirmTest`、`TagOverviewStateTest`、`TagOverviewLayoutTest`、`TagOverviewResourcesTest` |
| 全部代码（T044/T048） | `./gradlew test --rerun-tasks` 通过：**78 个测试类、580 个用例、0 失败**（基线 69/515） |
| 静态检查（T054） | `TagPanelState`、`TagPanelLayout`、`TagPanelModel`、`TagPanelContext`、`BackpackTagPayload` 无改动；`TagPanelRenderer`/`TagPanelInput` 只多了图标与视图分支；`build.gradle`、`neoforge.mods.toml`、`compat/` 无改动；第三方引用只在 `compat/jei/` 与 `ContainerScreenCompat` 原有位置；`setScreen(` 仍为 4 处；协议版本只在 `BackpackNetwork` 一处，`"13"` |

## 已实现（代码）

- **纯逻辑**：`TagBook.clear`；`TagOverviewRequest`（ADD/REMOVE/CLEAR + 校验）；`TagOverviewRules`（增删清空的规则：只能对已有标签的分类添加、已不在游戏里的物品不能添加、"垃圾"保护、≤ 10、幂等）；`TagCandidates`；`TagOverviewModel`（行、筛选、排序）；`ClearConfirm`（行内确认，超时 3000 ms，时间由调用方传入）；`TagOverviewState`（视图、详情、筛选、列表滚动、筛选框、确认）；`TagOverviewLayout`（列表页几何与命中）。
- **服务端**：`BackpackTagOverviewPayload`（客户端→服务端，按物品注册 id 寻址）：核对背包、功能区有标签管理器、id 合法、规则（`registered/isProtected` 由服务端自己算），成败都补发 `BackpackTagsPayload`；`BackpackInventory.hasTagManager()`；协议 `"12"` → `"13"`。
- **界面**：`TagOverviewContext`（行缓存：键 = `ItemTags` 实例 + 筛选 + 语言；名称、图标、下拉候选、布局）、`TagOverviewRenderer`（列表页、明细页、菜单图标；只用填充、字体与物品图标）、`TagOverviewInput`（鼠标、滚轮、两个文本框的键盘；修改只发请求，不做乐观更新）；018 的 `TagPanelRenderer`/`TagPanelInput` 只加图标与视图分支；`FloatingPanelRenderer`、`FloatingPanelInput`、`FloatingPanelEvents`（键盘三事件、每 tick 重置、登录登出）、`DeleteKeyInput`、`BackpackSessionPayload`（新会话回到选区视图）接线。

## 偏离说明

- **布局的清空钮宽度**：data-model 写的是 `clearLabelWidths` 数组，实现取单个 `clearWidth`（所有行的钮等宽，取"清空"与"确认清空？"中宽的加 8），省掉每帧为最多 1024 行建数组。
- **`TagOverviewState.isPristine()`**：每 tick 的"面板被隐藏/标签管理器离开功能区"重置只在状态不是初始时才执行，避免每 tick 做无用功；plan 里没有写，属实现细节。
- **明细页删光最后一个标签自动回列表**：spec 在 plan 阶段已修订（原为"仍可添加"，与服务端"只允许给已有标签的分类添加"冲突）。

## 还没有做 / 未验证

- **客户端验证 T027、T032、T038、T045、T049 需要用户在游戏里逐项确认**（quickstart A～F）；本轮没有启动客户端，**界面绘制、点击命中、键盘焦点、悬停提示、大量分类下的手感、行内确认的手感在真实游戏里都还没跑过**。
- **T053 图集度量**：写本文时 `pgrep` 发现游戏仍在运行，按约定不启动第二个客户端；本 feature 不新增贴图，游戏退出后跑。
- 编解码层（`BackpackTagOverviewPayload` 的 `StreamCodec`）无法单测，只靠编译与客户端会话。
- Windows/Linux；联机；已移除模组的物品（需要旧存档）的显示。

## 已知限制

- 总览只管**已经有标签的分类**；给从未打过标签的物品加标签仍用选区视图；已不在游戏里的物品只能删除/清空，不能添加。
- 不做乐观更新：操作后要等服务端同步才更新显示。
- 清空不可撤销（二次确认是它的保护）。

## 给后来者的提醒

- `TagPanel*`、`PickupPanel*`、`TagOverview*` 三套面板结构相近（复用了纯布局/输入/匹配组件，状态/输入/渲染各写了一套）；`TagCandidates` 与 024 的 `PickupPanelModel.candidates` 逻辑重复，可合并；第三处需要同类面板时再抽象。
- `BackpackContents` 的编码字段仍是 6 个（满），本 feature 没有动它。
