# Tasks: 分类扩展：界面

**Input**: `/specs/037-category-extension-ui/` 的 spec.md、plan.md、research.md、data-model.md、contracts/（ui.md、prediction.md）、quickstart.md；宪法 2.2.1

**Tests**: 几何（行归属、分隔线、行首）、放入判定的新重载、拖拽手势的接受谓词、类别样式表、隐藏提示、黄点几何、语言键与接线文本用 JUnit 覆盖，全部是不加载 Minecraft 的纯逻辑或文本检查。**绘制与交互只能在客户端会话里验证**（宪法 V）：编译与单元测试通过不等于外观与手势正确。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`，`RES/` 表示 `src/main/resources/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；跑完 Gradle 后 `./gradlew --stop`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增**物品、贴图、配置项、命令、网络包、数据组件；**协议仍是 17**；**不改 035 的规则**（只给 `ExtensionRules` 加重载，旧入口行为不变）；不修改 `docs/ideas`
- 用户故事：US1 外观（底色、分隔线、角标、图标、悬停说明）；US2 面板形状不被扩展行改动；US3 预览与手势知道类别规则；US4 黄点与隐藏提示；US5 共存与回归

---

## Phase 1: Setup

- [x] T001 基线：`./gradlew test` 记下当前类数与用例数（上一次是 113 个类、805 个用例，之后加了 `BackpackIconSizeTest`）；`git status` 确认工作区只有 037 的文档与未提交的背包图标放大（5 个 `*_floating_backpack_icon.json` 与 `BackpackIconSizeTest`）；记在 DELIVERY.md

## Phase 2: Foundational（纯逻辑、语言键、客户端判定）

**⚠️ 之后所有用户故事都依赖本阶段**

- [x] T002 [P] `…/inventory/extension/RowLayout.java`（不可变、无 Minecraft 类型）：`RowLayout(int baseCapacity, List<String> visibleCategories)`；`hasRows()`、`groupOf(index)`（-1 基础容量内、k 第 k 个可见行、-2 越界）、`categoryOf(index)`、`isRowStart(index)`、`topEdge(index, cols)`、`leftEdge(index, cols)`（语义见 data-model.md；主网格格子与越界格子的两个 edge 都是假；`cols < 1` 按 1）。`…test/inventory/extension/RowLayoutTest.java`：3～9 列各一组、基础容量整除与不整除列数、第一格就是扩展行、多个行、折行处的阶梯边界、`groupOf` 单调不减、主网格从不画线、没有行时全为假
- [x] T003 [P] `…/inventory/extension/ExtensionRules.java`：加"只给可见行类别列表"的重载 `canPlace(List<String> visibleCategories, int baseCapacity, int slot, T stack, CategoryMatcher<T>)` 与 `targetSegments(List<String>, int, T, CategoryMatcher<T>)`；原来 `RowSet` 版本改为委托（取 `rows.visibleCategories()`），**035 的测试一行不改**。`…test/inventory/extension/ExtensionRulesCategoriesTest.java`：新旧重载在穷举的小型输入上答案完全一致；越界、基础容量内、隐藏（不在列表里）的格子
- [x] T004 [P] `…/inventory/extension/HiddenHint.java`：`lines(List<RowSet.HiddenSummary>)` → `List<Line(category, cells)>`，只含 `cells > 0`，按固定类别顺序（未知 id 在最后）；`isEmpty()`。`…test/inventory/extension/HiddenHintTest.java`：空、全 0、多个类别、顺序、未知 id、口径与 `RowSet.hasHiddenItems` 一致
- [x] T005 [P] `…/client/panel/CategoryStyle.java`（只含整数色值与类别 id，不引用 Minecraft 类型）：10 个类别的 `edge`（不透明）与 `fill`（`edge` 加 `0x59` 透明度）、未知类别回退灰；色值按 contracts/ui.md 表；`ghostItemId(category)` 返回 `<类别>_extension` 字符串（物品由渲染器去取）。`…test/client/panel/CategoryStyleTest.java`：覆盖 `ExtensionCategories.IDS` 全部 10 个、`edge` 两两不同、`fill` 与 `edge` 的 RGB 一致、`edge` 不等于槽底 `0xFF8B8B8B`、未知类别不抛异常
- [x] T006 [P] `…/client/panel/PanelDragGesture.java`：加"接受谓词" `Acceptor<T>`（`boolean accepts(int slot, T cursor)`），`beginPress`/`advance` 的新签名带它，选格（`selected.add`）时先要 `accepts`；**旧签名委托、谓词恒为真，行为与改动前逐步等价**。`…test/client/panel/PanelDragGestureAcceptTest.java`：谓词拒绝的格子不进 `selected`、不进预览；只剩一个被接受的格子时 `release` 给 `CLICK` 那一格；一个都没被接受时按 `slotUnderCursor` 退回 `CLICK`；谓词恒为真时与旧行为相同（再对照 `PanelDragGestureTest` 原有用例一行不改并通过）
- [x] T007 [P] 语言键 `RES/assets/mcfloatingbackpack/lang/zh_cn.json` 与 `en_us.json`：`panel.mcfloatingbackpack.row.tooltip`、`.row.denied`、`.hidden.title`、`.hidden.line`、`.hidden.hint`（文字与参数见 contracts/ui.md，`row.tooltip` 只用 `%1$s`）；两个文件键集合保持一致
- [x] T008 `…/client/panel/ClientPlacement.java`：`canPlace(ClientBackpackView view, int slot, ItemStack stack)`，按 contracts/prediction.md 的五个放行条件，之后调 T003 的重载与 `new ItemCategoryMatcher(view.tags())`；"类别标签已绑定"的判据用 `Minecraft.level` 的物品注册表（`getTag(key).isPresent()`，十个类别的标签任一未绑定就算未就绪），写清楚判据与"拿不到 level 时放行"；另给 `canPlaceFor(hovered…)` 之类的小方法供渲染器用。**此类依赖 Minecraft，不做单元测试**，靠 T006/T003 的纯逻辑测试与游戏验证
- [x] T009 运行 `./gradlew compileJava test`

**Checkpoint**: 纯逻辑与判定就绪，035 的测试不变并通过

## Phase 3: User Story 2 - 放上或拿走扩展件，面板的形状不被改动 (Priority: P1)

**Independent Test**: 合法尺寸只随基础容量变（Phase 7 验证 20 次放上拿走）。先做它，因为它最小、独立，并且让后面的绘制验证在不乱跳的面板上进行。

- [x] T010 [US2] `…/client/panel/ClientBackpackView.java`：`reportCounts()` 的 `setMainCount(slots.size())` 改为 `setMainCount(baseCapacity())`；注释说明原因（036 过渡期合法尺寸随扩展行变化），`rows()`/`setTotalRows` 仍按含扩展行的总格数
- [x] T011 [US2] 同文件 `applyFull`：滚动归零的条件从"主区域格子数变"改为"基础容量变"（`baseCapacity` 前后比较，要在覆盖 `rowView` 之前取旧值）；扩展行增减只靠 `setTotalRows → clampScroll`
- [x] T012 [US2] `…test/client/panel/PanelShapeTest.java`（或新增小测试）：`main` 取基础容量时 `validCols`/`maxRows` 与没有扩展行时完全相同的断言；加一个源码文本检查（放进 T023 的测试类也可）断言 `reportCounts` 用的是 `baseCapacity()`
- [x] T013 运行 `./gradlew compileJava test`

## Phase 4: User Story 1 - 扩展行看得出是扩展行 (Priority: P1) 🎯 MVP

**Independent Test**: 放扩展件、改列数、滚动、悬停（quickstart A）

- [x] T014 [US1] `…/client/panel/FloatingPanelRenderer.java` `drawSlotsWithItems`：在 `drawSlotWell` 之后，若 `layout.hasRows()` 且 `groupOf(index) >= 0`：叠类别底色（`fill(sx+1, sy+1, sx+17, sy+17, fill)`）、`topEdge`/`leftEdge` 的 1 像素分隔线、`isRowStart` 的 3×3 角标（顺序与坐标见 contracts/ui.md）；`RowLayout` 在每帧一次由 `ClientBackpackView.rowView()` 建出（不在格子循环里重建）；**没有扩展行时这些分支不进入**，标签面板或拾取页盖住主区域时整体不画（沿用现有 `continue`）
- [x] T015 [US1] 同方法：空格（没有物品、不是拖拽预览要画的格子）画幽灵图标——从 `ModItems.CATEGORY_EXTENSIONS` 取该类别的扩展件，取其物品模型的粒子贴图，用 `GuiGraphics.blit(…, sprite, r, g, b, a)` 以 0.16 透明度画 16×16；**编译时核对 `blit` 的带颜色重载在 1.21.1 的确切签名**；若透明度画不出，回退成只画底色与角标，并在 DELIVERY.md 记录
- [x] T016 [US1] 同文件：悬停高亮——光标非空且悬停的扩展行格子 `!ClientPlacement.canPlace` 时用 `COLOR_SLOT_DENIED = 0x60FF3030` 代替 `COLOR_SLOT_HOVER`；悬停在扩展行任一格上画行说明提示（`row.tooltip`，类别名取 `tag.mcfloatingbackpack.preset.<类别>`），放不进时追加红字 `row.denied`；提示在格子循环之外、面板其它绘制之后画一次（只对悬停的那一格），沿用现有 `graphics.renderTooltip` 的用法
- [x] T017 运行 `./gradlew compileJava test`

**Checkpoint**: 此时外观可在游戏里看（用户验证 A）

## Phase 5: User Story 3 - 预览与手势知道类别规则 (Priority: P1)

**Independent Test**: 匹配与不匹配的物品点击、拖拽分配，预览与服务端一致（quickstart C）

- [x] T018 [US3] `…/client/panel/FloatingPanelInput.java` `sendClick(PanelArea, int, boolean)`：主区域分支，`held`（与服务端读同一个东西：光标；Alt 且没开容器屏时是主手物品，参照 `refusedByLimit` 的写法）非空且 `!ClientPlacement.canPlace` → 不发包，返回；空手拿起永远不拦；工具区的分支不动
- [x] T019 [US3] `…/client/panel/PanelDragInput.java`：构造手势或调用 `beginPress`/`advance` 时传入接受谓词 `(slot, cursor) -> ClientPlacement.canPlace(view, slot, cursor)`；`slotAt` 仍按 `slotCount()`（含可见行、不含隐藏行，保持）
- [x] T020 [US3] 逐个核对并在 DELIVERY.md 记录"不需要改"的手势（选区、Ctrl 转出、聚拢、垃圾桶倾倒、Ctrl 转入）：每个写一句"它的范围是主区域含可见行的下标，隐藏行不在列表里"，并把 `slotCount()` 的各调用点（`FloatingPanelInput:197`、`PanelDragInput:188`、`PanelSelectionInput:126/162`、`QuickTransferInput:165`、`DeleteKeyInput:50`、`TrashActions.dumpSlots`、`FloatingPanelEvents` 选区上下文）确认为"保持"
- [x] T021 运行 `./gradlew compileJava test`

## Phase 6: User Story 4 - 隐藏的物品有标识 (Priority: P2)

**Independent Test**: 拿走有物品的扩展件看黄点与悬停，放回消失（quickstart D）

- [x] T022 [P] [US4] `…/client/panel/TitleDotLayout.java`（纯逻辑）：黄点矩形 `x ∈ [panelX+15, panelX+20)`、`y ∈ [panelY+1, panelY+6)`，命中区 `x ∈ [panelX+14, panelX+21)`、`y ∈ [panelY, panelY+7)`；`hits(panelX, panelY, mx, my)`；常量与 `TitleIdLayout.ICON_OFFSET/ICON_SIZE/GAP` 对照（编号起点在 `panelX+23`，黄点右缘必须小于它）。`…test/client/panel/TitleDotLayoutTest.java`：不与编号起点相交、与图标矩形相交于右上角、命中区包含黄点、命中区不越出面板顶
- [x] T023 [US4] `FloatingPanelRenderer.drawTitleIcon`：`HiddenHint.lines(view.rowView().hidden())` 非空时在图标之后画黄点（5×5 去四角，`0xFFFFD34D`，外圈 1 像素 `0xFF3A2E00` 描边）；悬停黄点命中区时画隐藏提示（`hidden.title`、每类一行 `hidden.line`（类别名、格数）、`hidden.hint`），此时**不**画档位名提示；悬停图标其余部分仍是档位名；黄点画在图标之后、编号之前或之后均可，但不得改动图标与编号自己的位置
- [x] T024 运行 `./gradlew compileJava test`

## Phase 7: 收尾（自动化）与 User Story 5 的静态部分

- [x] T025 [P] `…test/resources/CategoryExtensionUiResourcesTest.java`（文本检查）：两个语言文件都有 5 个新键且键集合一致、`row.tooltip` 含 `%1$s`；`ClientBackpackView.java` 里 `reportCounts` 用 `baseCapacity()` 且 `applyFull` 的归零条件不再是格子数；`FloatingPanelInput.java` 里 `sendClick` 引用 `ClientPlacement`；`PanelDragInput.java` 引用 `ClientPlacement`；`FloatingPanelRenderer.java` 引用 `RowLayout`、`CategoryStyle`、`HiddenHint`
- [x] T026 静态检查：`grep -rn 'versioned(' src/main/java` 仍是 `"17"`；本 feature 没有新增任何 `CustomPacketPayload`、设置项、命令、贴图、物品；`git diff` 里 `…/inventory/extension/` 只有 `ExtensionRules` 的重载与两个新文件（`RowLayout`、`HiddenHint`），035 的任何规则与测试没有改动；`DependencyRulesTest` 通过
- [x] T027（图集未度量：本 feature 没有新增贴图；度量脚本会启动客户端并删除用户上次会话的日志，所以没跑；类数与用例数：114/806 → 121/851）全量 `./gradlew compileJava test`，记下类数与用例数；`design/demo/tools/check_atlas_budget.sh`（先 `pgrep`）确认图集仍是 2048×1024（本 feature 不新增贴图，应无变化；度量不了写"未度量"）；`./gradlew --stop`
- [x] T028（尚无新日志：latest.log 是新代码之前的会话，0 个 ERROR）读 `run/logs/latest.log`（如果用户已启动过新版本）：没有与本 feature 有关的 ERROR、渲染异常；没有就写"尚无新日志"

## Phase 8: 验证与文档（客户端验证由用户确认）

- [x] T029（用户 2026-11-10 确认没有问题）[US1] 按 quickstart A 验证外观：1 个到多个类别、列数 3～9、滚动、调整大小、悬停说明、10 个类别的可分辨度、幽灵图标透明度；截图或描述（先 `pgrep`；由用户确认后才打勾）
- [x] T030（用户 2026-11-10 确认没有问题）[US2] 按 quickstart B 验证形状不变：放上拿走 20 次，合法尺寸与当前尺寸不变、滚动不跳（由用户确认后才打勾）
- [x] T031（用户 2026-11-10 确认没有问题）[US3] 按 quickstart C 验证预览与服务端一致：点击、拖拽分配、三叉戟两行、手动标签、Alt 交换、选区/转出/聚拢/垃圾桶回归、标签未同步窗口（由用户确认后才打勾；`logging.level` 建议 DEBUG）
- [x] T032（用户 2026-11-10 确认没有问题）[US4] 按 quickstart D 验证黄点与隐藏提示（由用户确认后才打勾）
- [x] T033（用户 2026-11-10 确认没有问题）[US5] 按 quickstart E、F 验证共存、创造模式、主机屏、Curios/Accessories、35 行帧率、035 遗留的未验证项，并留意创造模式背包丢失事件是否再现（由用户确认后才打勾）
- [x] T034 [P] 更新文档：`docs/MCFloatingBackpack.md`（037 一句说明）、`docs/plan/manual-verification-checklist.md`（037 验证项 A～F）、`docs/plan/category-extensions.md`（037 状态；分类扩展三个 feature 至此全部实现，只等验证）；**不要修改 `docs/ideas`**
- [x] T035 `specs/037-category-extension-ui/DELIVERY.md`：已验证 / 未验证 / 已知限制（颜色是初稿、幽灵图标若回退、隐藏提示按格数不是件数、绘制开销未测量等）/ 基线与完成后的类数与用例数 / 对规格的三处修正（物品提示、面板单配色、手势预测的范围）/ 035 的"未验证"四项在本 feature 里验证的结果

---

## Dependencies & Execution Order

- Phase 1 → Phase 2（T002～T008 里除 T008 都可并行；T008 依赖 T003）→ T009。
- Phase 3（US2）依赖 Phase 2 的编译通过；它与 Phase 4、5、6 的代码互不冲突，但建议先做以便后面的游戏验证在稳定的面板上进行。
- Phase 4（US1）依赖 T002、T005、T007、T008；Phase 5（US3）依赖 T003、T006、T008，并与 Phase 4 改同一个文件 `FloatingPanelRenderer`（T016）与 `FloatingPanelInput`（T018）时需顺序进行；Phase 6（US4）依赖 T004、T007、T022，与 T014～T016 改同一文件，顺序进行。
- Phase 7 在全部实现之后；Phase 8 由用户确认，T034 可提前准备。

## Parallel Opportunities

- T002、T003、T004、T005、T006、T007 互相独立（不同文件）；T022 与 Phase 3 并行。

## Implementation Strategy

先纯逻辑与判定（Phase 2），让规则的客户端重载有测试保护；再做最小的形状修复（US2）；然后外观（US1，MVP）与预测（US3）；最后黄点（US4）。每个阶段结束都跑一次测试。游戏里的外观和手势没有自动化测试，每个阶段做完都要明确写"未在游戏里验证"，直到用户确认。颜色、幽灵图标的透明度与阶梯线的观感最不确定：T015 有回退，色值集中在 `CategoryStyle` 一张表，方便看过之后调。
