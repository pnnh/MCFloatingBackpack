# Tasks: 面板只允许"没有实心空格"的尺寸，并缩小"更多"箭头

**Input**: Design documents from `/specs/017-panel-valid-sizes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/panel-shape.md, quickstart.md

**Tests**: 合法尺寸判定、拖动缩放的取舍、`layout()` 的落点都是不依赖 Minecraft 的纯逻辑，plan 要求用单元测试覆盖（含"没有实心格子"的穷举测试）；拖动手感与箭头外观只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础（`PanelShape` 与格子数上报）；US1（拖动缩放只停在合法尺寸）是 MVP；US2（其他入口落到合法尺寸）在其上改 `layout()`；US3（箭头缩小）与前两个互相独立。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US3）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**跑 `check_atlas_budget.sh` 或启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，不要杀用户的游戏**

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（当前 25 个测试类）；结果记到 `specs/017-panel-valid-sizes/DELIVERY.md`（图集度量放到最后 T019 做，本 feature 不新增资源，016 完成时已测为 2048×1024）
  - **结果**：改动前 `./gradlew compileJava test` 退出码 0，25 个测试类

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 合法尺寸的判定与格子数上报。没有它们，缩放和 `layout()` 都不知道什么是合法尺寸。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelShapeTest.java`（**先写，先失败**），按 data-model.md：`validCols` 对五个等级（`Counts(main, left, right, bottom)` = 36/4/4/11、54/6/6/11、72/8/8/11、90/10/10/11、108/12/12/11）分别为 {3,4,6,9}、{3,6,9}、{3,4,6,8,9}、{3,5,6,9}、{3,4,6,9}；`maxRows(counts, cols)` = `min(left, right, main/cols)`（皮革 3 列为 4、不是 12；铁 6 列为 6）；`isValidRows` 的边界（3、`maxRows`、`maxRows+1`、2）；`snapCols`（不超过目标的最大合法值，如铁目标 8→6、7→6、5→3、10→9，目标小于最小合法值时取最小合法值）与 `snapRows`（夹到 `[3, maxRows]`）；`restricted`：计数为 0、`main` 为 37 这类没有合法列数、下区格子数太小（`bottom = 6` 时 `cols+2 ≤ 6` 只剩 3、4）时的退回（不受限时 `isValidCols/isValidRows` 恒真、`snapCols` 只夹到 3..9）；随机 200 组 `Counts` 与目标，吸附结果恒合法且对合法输入幂等；**穷举无填充格**：对五个等级、每个合法 `(cols, rows)`、每个区域的每个滚动偏移（`0..PanelLayout.maxScroll`），用 `PanelLayout.of(...)` 检查该区域所有可见格子的 `slotIndex(area, ordinal) ≥ 0`（SC-002）；对照一个非法尺寸（铁 7 列 × 8 行）确实出现 `slotIndex == -1`（证明测试能抓到问题）
  - **结果**：`PanelShapeTest` 13 个用例通过：五个等级的合法列数 {3,4,6,9}/{3,6,9}/{3,4,6,8,9}/{3,5,6,9}/{3,4,6,9}、行数上限、吸附、没有合法形状时不受限、随机 200 组吸附恒合法且幂等；**穷举**：每个等级 × 每个合法尺寸 × 每个区域的每个滚动偏移，`PanelLayout` 里没有一个填充格；反向也成立——所有非法尺寸（行 3–14、列 3–9）都至少出现一个填充格，所以"合法"的判定与"没有实心格子"完全等价；铁背包 7×8（用户截图）确实被判非法且出现填充格
- [X] T003 [P] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelShape.java`：纯逻辑（无 Minecraft 依赖）。`record Counts(int main, int left, int right, int bottom)`；常量 `MIN_CELLS = 3`、`MAX_COLS = 9`；`validCols`、`maxRows`、`restricted`、`isValidCols`、`isValidRows`、`snapCols`、`snapRows`、`isValid`，规则逐条照 data-model.md。使 T002 通过
- [X] T004 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java` 与 `ClientBackpackView.java`：状态类新增 `setMainCount(int)`（与已有的 `toolCount[]` 一起保存），并提供 `PanelShape.Counts counts()`（主、左、右、下）；`ClientBackpackView.applyFull`/`clear` 在上报三个工具区格子数的同时上报主格子数（把现有的 `reportToolCounts` 改名为 `reportCounts`）。**本任务只保存数据**，缩放与布局在后面的故事里使用。依赖 T003
  - **实现说明**：`FloatingPanelState.setMainCount/counts()`；`ClientBackpackView` 的 `reportToolCounts` 改名 `reportCounts` 并上报主格子数
- [X] T005 运行 `./gradlew compileJava test`，确认 T002 与全部既有测试通过（既有布局测试在计数为 0 时不受限，应仍通过）
  - **结果**：退出码 0，26 个测试类

**Checkpoint**: 编译通过；T002 绿；状态类知道各区格子数。

---

## Phase 3: User Story 1 - 缩放时只停在"没有实心格子"的尺寸上 (Priority: P1) 🎯 MVP

**Goal**: 拖动缩放手柄时，目标列数/行数不合法就保持当前值，合法才采用；列、行各自判断；没有合法形状时不限制。

**Independent Test**: quickstart 一（1–8）：铁背包拖动扫过各种大小，每个停下来的尺寸都合法；不合法的目标面板不动；对角拖动两个方向各自判断；各区域在任何滚动位置都没有实心格子。

- [X] T006 [P] [US1] 在 `src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelLayoutTest.java` 补受限缩放的用例（**先写，先失败**）：设置计数为铁背包（`setMainCount(54)`、`setToolCount` 左右 6、下 11）、`setTotalRows(6)`、`layout` 到 9×6 之后——向左拖使目标列依次为 8、7：`getVisibleCols()` 保持 9；目标为 6：变为 6；目标为 5、4：保持 6；目标为 3：变为 3；向下拖使 6 列时目标行为 7、8：`getVisibleRows()` 保持 6；列行同时拖动时一个方向被挡住另一个方向照常变化；目标行数按**采用后的列数**判定（例如 36 格背包 4 行 → 列变为 4 时行数上限仍是 4）；缩到最小仍是 3×3（面板 5×4）；计数为 0（不受限）时行为与 016 完全一致（现有用例原样通过）
  - **实现说明**：`FloatingPanelState` 是单例，测试里每个用例前后把计数复位为 0（不受限），避免影响其他测试类；先跑出 4 个失败，再实现
- [X] T007 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java` 的 `updateResize`：仍按"相对抓取点的增量"算出目标 `(cols', rows')`（列夹到 3..9，行 ≥ 3）；`PanelShape.restricted(counts())` 为真时——目标列数 `isValidCols` 才写入 `visibleCols`，否则保持；目标行数用写入后的列数 `isValidRows` 判定，合法才写入 `visibleRows`，否则保持；不受限时沿用原逻辑；之后照常 `clampScroll()`。使 T006 通过。依赖 T004
- [X] T008 [US1] 运行 `./gradlew compileJava test`，确认 T006 与全部既有测试通过
  - **结果**：`PanelLayoutTest` 20 个用例通过（含 6 个新增）
- [X] T009 [US1] 按 `quickstart.md` 一（1–8）在 `./gradlew runClient` 逐项验证（铁、皮革为主，其余三档抽查），**重点记录面板在合法尺寸之间"跳"的手感是否可接受**；结果记到 `specs/017-panel-valid-sizes/DELIVERY.md`（没做的项记"未验证"）
  - **用户验证（2026-11-05）**：用户进游戏测试拖动缩放："跳"动的手感不算太明显，可以接受；没有逐项记录其余观察

**Checkpoint**: US1 达成 FR-001、FR-002、FR-005（拖动部分）、SC-001～SC-003。

---

## Phase 4: User Story 2 - 加载、换背包、屏幕变化时也落在合法尺寸 (Priority: P1)

**Goal**: 保存的偏好、自动适应、换背包/升级、屏幕变小之后，面板尺寸都落在合法值；没有合法形状时退回 016。

**Independent Test**: quickstart 二（9–13）：改配置文件为非法尺寸后打开；换不同等级的背包；缩小窗口；升级背包。

- [X] T010 [P] [US2] 在 `PanelLayoutTest.java` 补 `layout()` 落点的用例（**先写，先失败**）：铁背包计数下，`setSizePreference(rows=8, cols=7)` 后 `layout(WIDE_SCREEN, 400)` → 列 6、行 ≤ 6；无偏好、屏幕可用列数为 8 时自动适应得到 6 列（"能放下的最大合法值"），可用列数为 9 以上得到 9 列、行数 = 6；屏幕极小（可用列 < 3、可用行 < 3）时仍是 3×3；换成皮革计数（`setMainCount(36)`、左右 4）后下一次 `layout()` 行数 ≤ 4、列在 {3,4,6,9}；换成钻石计数后 6 列 × 4 行保持不变（已合法）；升级时计数变化后非法的尺寸被改成合法值；计数为 0（不受限）时偏好与自动适应与 016 完全一致；多次调用 `layout()` 结果幂等
  - **实现说明**：`PanelLayoutTest` 补 7 个用例：偏好 7 列 8 行 → 6×6；自动适应屏幕只放得下 8 列 → 6 列；极小屏幕仍是 3×3；换背包（铁 → 钻石保持 6×6，→ 皮革行数变 4）；升级（皮革 4 列 → 铁 3 列）；幂等；没有合法尺寸时保留偏好
- [X] T011 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java` 的 `layout`：在算出 `colsUpper/rowsUpper`（屏幕与总行数限制）之后，受限时——列：`visibleCols = snapCols(counts(), clamp(偏好或 colsUpper, 3, colsUpper))`；行：`rowsUpper' = min(rowsUpper, maxRows(counts(), visibleCols))`，`visibleRows = clamp(偏好或 rowsUpper', 3, rowsUpper')`；自动适应（没有偏好）同样走这条路，得到"能放下的最大合法尺寸"；`MIN_VISIBLE_CELLS = 3` 仍压过屏幕过小的情况；不受限时沿用原逻辑；`width/height`、`posY` 修正与 `clampScroll()` 保持。使 T010 通过。依赖 T007
- [X] T012 [US2] 运行 `./gradlew compileJava test`，确认 T010 与全部既有测试通过
  - **结果**：`PanelLayoutTest` 27 个用例通过，26 个测试类全过
- [ ] T013 [US2] 按 `quickstart.md` 二（9–13）在 `./gradlew runClient` 逐项验证（改配置文件前先关闭游戏；换背包、缩小窗口、锻造台升级），结果记到 `DELIVERY.md`
  - **未确认（2026-11-05）**：用户这次反馈只涉及拖动手感与箭头；配置文件非法尺寸、换背包、缩小窗口、锻造台升级这几项没有被提到，保持未验证

**Checkpoint**: US2 达成 FR-003、FR-004、FR-005（落点部分）、SC-004。

---

## Phase 5: User Story 3 - "更多"箭头更小、更靠近边缘 (Priority: P2)

**Goal**: 016 的小扁箭头缩到最大 7×4 像素（含外框），尖端紧贴区域边缘；出现条件、不参与命中、颜色不变。

**Independent Test**: quickstart 三（14–16）：缩小面板让各区域出现箭头，对比改前改后的大小与位置。

- [X] T014 [P] [US3] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：`ARROW_SIZE` 由 4 改为 3（深色三角形行宽 1、3、5；外框行宽 1、3、5、7，最大 7 宽 × 4 深）；`drawScrollArrows` 里外框尖端放在区域边缘的最外一个像素——顶部 `r.y()`、底部 `r.y() + r.h() - 1`、左侧 `r.x()`、右侧 `r.x() + r.w() - 1`，深色三角形尖端仍在外框尖端内侧 1 像素（`drawArrow` 现有的 `tip - d` 偏移不变）；颜色常量、出现条件、不参与命中不变；更新常量上方的注释。**不依赖其他任务**
  - **实现说明**：`ARROW_SIZE` 4→3（深色行宽 1、3、5；外框 1、3、5、7，最大 7 宽 × 4 深）；四个方向的外框尖端都放在区域最外一个像素；**形状与位置没有在游戏里看过**
- [X] T015 [US3] 运行 `./gradlew compileJava test`，确认全部测试通过
  - **结果**：退出码 0，26 个测试类
- [X] T016 [US3] 按 `quickstart.md` 三（14–16）在 `./gradlew runClient` 验证：缩小面板让主区域、左右区、下区的箭头都出现，**记录箭头大小与位置、是否仍清晰可辨、尖端贴边时与格子边框的叠放**；结果记到 `DELIVERY.md`
  - **用户验证（2026-11-05）**：用户进游戏测试：小箭头的形状和位置没有问题；没有逐项记录

**Checkpoint**: US3 达成 FR-007、SC-005。

---

## Phase 6: Polish & 文档收尾

- [X] T017 [P] 更新文档：`specs/007-floating-window-chrome/spec.md` 加【017 修订】——面板的行列不再能任意取值，只停在不会出现实心格子的合法尺寸（规则见 `specs/017-panel-valid-sizes/`）；`specs/016-panel-tool-areas/spec.md` 加【017 说明】——Edge Cases 里的"填充格"在标准等级下不再出现（017 的合法尺寸规则保证），代码里的填充格不绘制只保留为兜底；`docs/MCFloatingBackpack.md` M5 里 016 的那句补"拖动缩放只停在合法尺寸、箭头更小更贴边"
- [X] T018 创建 `specs/017-panel-valid-sizes/DELIVERY.md`：记录 T001、T009、T013、T016 的结果，**包括没做到的和结论不确定的**；列出已知限制（合法尺寸之间"跳"的手感、屏幕极小时的取舍、偏好被改写成合法值后不会记住、Windows/Linux 未验证）；记录行为变化（缩放不再能任意行列、各等级的合法形状表）及用户是否接受
- [ ] T019 运行最终回归：`./gradlew compileJava test`；确认没有客户端在运行后跑 `design/demo/tools/check_atlas_budget.sh`，图集仍 2048×1024（本 feature 不新增资源）；按 `quickstart.md` 四（17–18）重跑 016 的布局/滚动/存放与 011～014 的主区域手势验证项并记录结果；把 spec 的 Status 改为实现完成，并如实写明验证未完成的部分
  - **部分完成**：`./gradlew compileJava test` 退出码 0（26 个测试类）；**图集度量没有做**——检测到用户的客户端正在运行（`pgrep` 有进程），按规则不去杀它；本 feature 没有新增或修改任何资源，图集应不变，但**没有实测**；quickstart 四（重跑 016 与 011～014 的回归）未做；spec Status 已改为"代码与单元测试完成，未在游戏里验证"

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（T001）：无依赖
- Phase 2（T002～T005）：T002 与 T003 先后成对（测试先于实现，可并行起步）；T004 依赖 T003；T005 收尾。**阻塞 US1、US2**（US3 不依赖）
- Phase 3 US1（T006～T009）：依赖 Phase 2；T006 可先写；T007 使 T006 通过
- Phase 4 US2（T010～T013）：T011 依赖 T007（同一个类里先改缩放再改布局）；T010 可与 T006 同时先写
- Phase 5 US3（T014～T016）：**只依赖 Phase 1**，可与 Phase 2～4 并行
- Phase 6：依赖前面的结果；T017 可与代码任务并行

### User Story Dependencies

- **US1**：依赖 `PanelShape` 与计数上报，是 MVP
- **US2**：依赖 US1 里对 `FloatingPanelState` 的改动（同一个类）
- **US3**：完全独立（只改渲染常量）

### Parallel Opportunities

- T002 与 T003（测试先写 / 纯类实现）、T006 与 T010（两组状态测试先写）可并行起步
- T014（箭头）可在任何时候独立做
- T017 可与任意代码任务并行

---

## Parallel Example: 起步

```text
并行启动：
  T002 PanelShapeTest.java              （合法集合、吸附、穷举无填充格，先写）
  T003 PanelShape.java                  （使 T002 通过）
  T014 FloatingPanelRenderer.java       （箭头缩小，独立）
随后顺序：T004 → T006/T010（先写）→ T007 → T011
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1、2（`PanelShape`、穷举测试、格子数上报）。
2. 完成 US1（拖动缩放按维度判定），**停下来在游戏里验证**（T009），重点看"跳"的手感能否接受。
3. 再做 US2（`layout()` 落点），然后 US3（箭头，可随时插入）。
4. 最后文档与 DELIVERY。

### 与宪法的关系

- 每个会改变玩家所见/所操作的故事都有客户端会话验证任务（T009、T013、T016）；没做的项在 DELIVERY.md 里记"未验证"，不能因为编译或单元测试通过就勾选（宪法 V）。
- 不涉及物品逻辑（宪法 II）与第三方（宪法 IV）；不新增资源，T019 跑一次图集度量作回归（宪法 VI）；只改缩放判定与箭头，016 的其他规则不动（宪法 III）。

### 风险提醒（实现时盯住）

- **汇合点漏掉入口**：偏好、自动适应、升级、屏幕变化都要经 `layout()` 落到合法尺寸（T010/T011 + quickstart 二）。
- **先定列数再判行数**：行数上限依赖采用后的列数，`updateResize` 与 `layout()` 的顺序不能反。
- **计数为 0 时不受限**：现有布局测试依赖"任意尺寸"，靠 `restricted` 为假保持通过。
- **穷举测试要能抓到问题**：T002 里用非法尺寸（铁 7×8）证明测试会失败。
