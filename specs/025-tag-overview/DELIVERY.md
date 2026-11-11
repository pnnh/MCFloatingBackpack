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

- **客户端验证 T027、T032、T038、T045、T049 需要用户在游戏里逐项确认**（quickstart A～F；2026-11-11 已有一轮 AI 自动验证，见下一节"AI 自动验证"，没测的项仍在这里）；写本文时没有启动客户端，**界面绘制、点击命中、键盘焦点、悬停提示、大量分类下的手感、行内确认的手感在真实游戏里都还没跑过**。
- **T053 图集度量**：写本文时 `pgrep` 发现游戏仍在运行，按约定不启动第二个客户端；本 feature 不新增贴图，游戏退出后跑。
- 编解码层（`BackpackTagOverviewPayload` 的 `StreamCodec`）无法单测，只靠编译与客户端会话。
- Windows/Linux；联机；已移除模组的物品（需要旧存档）的显示。

## 2026-11-11 AI 自动验证（容器里的客户端，创造模式单人世界，下界合金背包 ×2）

环境与做法见 `scripts/ai-gui/README.md`。用数据包函数造了两只背包：A 里有圆石/橡木原木/钻石/潜影盒，标签管理器在下方功能区，预置标签 51 个分类（圆石 `stone`、橡木原木 `wood`+`food`、钻石 `treasure`、潜影盒之外的 48 种羊毛/混凝土/陶瓦各一个 `bulk`）；B 是只放了标签管理器的空背包。判定靠截图与日志，没有在日志里发现本 feature 相关的 ERROR（日志里的 ERROR 只有容器没有声卡/光标主题/旁白）。

| 项 | 结果 |
|---|---|
| Q1 状态行右端有菜单图标；悬停提示 | **通过**：总览里悬停显示 "Back to selection"；选区视图里的提示 "标签总览" 没有单独截到 |
| Q2、Q3 切换保留选区 | **通过**：Shift 选中圆石（显示 `1 item types selected`、标签 `stone`），来回切换 10 次，选区与标签原样 |
| Q4 收起再展开回到总览与滚动位置 | **部分通过**：滚动位置保留；筛选保留没有单独测 |
| Q5 关闭面板再打开回到选区视图、选区清空 | **通过**（关闭面板再按 B）；拿走标签管理器、退出世界再进没测 |
| Q6 与 024 黑名单页互斥 | **没测** |
| Q7 列出分类、数量、悬停全部标签 | **部分通过**：总览标题 `Tag overview 51` 与预置的 51 个分类一致，每行数量 1，圆石悬停 "Cobblestone - stone"，白羊毛悬停 "White Wool - bulk"；橡木原木 2 个标签与钻石的行没有逐行看 |
| Q8 物品从背包拿走后行仍在 | **没测**（预置的 48 种羊毛等本来就不在背包里，行照常列出——这一点间接支持） |
| Q9 没打过标签的背包显示空状态 | **通过**：背包 B 显示 `Tag overview 0`、"No item has any tags yet"，与 A 互不共享 |
| Q10 大量分类滚动 | **通过**：51 行，滚轮一次一行（7 格滚轮使首行从第 1 行变成第 8 行），滚到最后一行（Yellow Wool）后再滚 8 格不动，滚轮没有穿透到下面的快捷栏（选中的快捷栏格子不变）。"手感"和 200 个以上分类没测 |
| Q11 切换语言重排 | **没测** |
| Q12 筛选下拉与模糊匹配 | **通过**：输入 `tr` 只剩 `trash`、`treasure` 两个候选；选 `treasure` 后总览只剩 Diamond，标题 `Tag overview 1`，筛选行带 `×` |
| Q13 清除筛选、筛选无结果 | **通过**：点 `×` 回到全部（51）；选 `trash`（没有物品带）显示 "No matching items"，`×` 仍可清除 |
| Q14 文本框焦点下的键盘 | **部分通过**：有焦点时输入 `e`、`t` 不开物品栏也不开聊天，Esc 只取消编辑、面板不关（`019 host` 事件数不变）；没有焦点时按 Esc 关闭面板（事件 +1）、按 B 能关面板。Delete 与数字没有单独验证；"Esc 不清除已应用的筛选"没验证 |
| Q15 非法字符被过滤 | **通过**：输入 `aB c` 得到 `ac`（大写与空格被丢掉） |
| Q16 点行进入明细 | **通过**：返回按钮、图标、名称、标签块、加号都在；"返回后滚动与筛选保持"没测 |
| Q17 增删标签 | **部分通过**：加号 → 文本框 + 下拉（预设 stone/wood/food/ore/trash，不含该分类已有的），输入 `x1` 回车添加成功；点标签出现红色删除标记，点它删除成功；Esc 取消没测 |
| Q18 物品不在背包里也能增删 | **通过**：用的黑色混凝土本来就不在背包里 |
| Q19 加到 10 个标签 | **没测** |
| Q20 受保护物品加 `trash` | **通过**：潜影盒的明细页里 `trash` 变灰，悬停/聊天提示 "These items cannot be marked as trash: Shulker Box"；手输 `trash` 回车被拒，标签仍只有 `sbx` |
| Q21 删掉最后一个标签自动回列表 | **通过**：删到最后一个后回到列表，标题 50（少一个），该行消失 |
| Q22 已移除模组的物品 | **没测** |
| Q23 清空二次确认 | **部分通过**：第一次点只变成红色 "Clear all?"，数量不变；3 秒后恢复；点另一行的"清空"取代前一行；点别处/滚动/切换视图没单独测 |
| Q24 第二次确认后清空 | **通过**：3 秒内再点一次，该行消失，标题 50→49，其它行不变 |
| Q25 清空后选区视图/022/024 | **没测** |
| Q26 受保护物品的分类可清空 | **通过**：潜影盒（`sbx`）的清空成功，该行消失，显示 "No matching items" |
| Q27～Q31 | 没做（Q31 图集度量见 T053 的备注） |

### 发现的问题（2026-11-11 已修复，已在容器里复测）

1. **总览的物品图标压在筛选下拉的上面**：在总览里点筛选框，下拉候选（`stone`、`wood`、`trash` 等）的左端被下面行的物品图标盖住，只露出后半个词（如 `one`、`ood`、`ash`），候选只剩一个时也一样（`sbx` 的前两个字母被盖住）。物品图标的绘制层级比下拉高。代码印证：`TagOverviewRenderer.drawDropdown` 画下拉时没有抬高深度，而 `drawItemIcon` 里的 `renderItem` 会把图标抬高约 150 层；容器里是软件渲染，但深度关系是确定的，真机应该也会复现。修法大概是画下拉（和明细页的下拉）前后加一个 `pose().translate(0, 0, …)`，
2. **筛选框里"未应用的文字"会残留并累积**：在筛选框里输入但没有选候选，按 Esc 后框里看起来清空了，但进入明细再返回（或再次点进框）后，之前输入的字都还在，且新输入追加在后面（出现过 `acetracstsbsb`）。Ctrl+A 在框里无效，只能退格一个个删。
3. 不是问题但值得留意：9 列宽的面板里行名会被截断（"Black Concr"、"Brown Terr"），这是规格里写明的（窄面板按宽度截断，悬停给完整名称），没有影响使用。

**修复**：1）`TagOverviewRenderer` 画筛选下拉前把深度抬高（`DROPDOWN_Z = 250`），复测下拉文字完整可读；2）筛选框的缓冲区只在编辑期间存在：Esc、点别处、离开列表页（进明细、返回）都会丢掉没选定的文字（`TagOverviewState.stopFilterEditing` 清空缓冲区），复测输入 `tr` 后 Esc、进明细再返回，再输入 `x`，框里只有 `x`。单元测试改了两处（`resetViewForgetsTheViewAndTheTypedFilterText`、`theFilterBoxOnlyHasTheFocusOnTheList`）并新增 `leavingTheListDropsTheUnchosenFilterText`，`./gradlew compileJava test` 通过。

这些项没有勾任务；勾选需要你在真机上确认修复，以及没测的项。

## 已知限制

- 总览只管**已经有标签的分类**；给从未打过标签的物品加标签仍用选区视图；已不在游戏里的物品只能删除/清空，不能添加。
- 不做乐观更新：操作后要等服务端同步才更新显示。
- 清空不可撤销（二次确认是它的保护）。

## 给后来者的提醒

- `TagPanel*`、`PickupPanel*`、`TagOverview*` 三套面板结构相近（复用了纯布局/输入/匹配组件，状态/输入/渲染各写了一套）；`TagCandidates` 与 024 的 `PickupPanelModel.candidates` 逻辑重复，可合并；第三处需要同类面板时再抽象。
- `BackpackContents` 的编码字段仍是 6 个（满），本 feature 没有动它。
