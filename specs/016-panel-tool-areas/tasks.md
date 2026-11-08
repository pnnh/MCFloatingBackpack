# Tasks: 浮动面板的三个工具区（左、右、下）与无滚动条的区域滚动

**Input**: Design documents from `/specs/016-panel-tool-areas/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/panel-layout.md, contracts/storage-and-network.md, quickstart.md

**Tests**: 容量规则、布局几何与命中、滚动夹取都是不依赖 Minecraft 的纯逻辑，plan 要求用单元测试覆盖（与 011～014 同一做法）；存储编码、同步、渲染、滚轮、点击只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的"存储与同步底座"（容量规则、数据组件、库存、网络包、客户端镜像）——它让客户端知道三个工具区有多少格子；US1（布局与外观）重写面板几何并删除滚动条，是 MVP；US2 在其上加滚动与箭头；US3 加工具区的点击与保存；US4 是对现有操作的回归与隔离验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US4）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**跑 `check_atlas_budget.sh` 或启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，不要杀用户的游戏**

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（当前 22 个测试类）；确认没有客户端在运行后跑 `design/demo/tools/check_atlas_budget.sh` 记录图集尺寸（应为 2048×1024，退出码 0）；结果记到 `specs/016-panel-tool-areas/DELIVERY.md`
  - **结果**：改动前 `./gradlew compileJava test` 退出码 0，22 个测试类；图集 2048×1024（估算显存 11.2MB）退出码 0，度量时没有客户端在运行

---

## Phase 2: Foundational（存储与同步底座，阻塞所有用户故事）

**Purpose**: 让服务端能存放三个工具区、客户端能镜像它们并知道容量。没有它，布局无从得知每个区域有多少格子，点击也无处可放。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/ToolAreaCapacityTest.java`（**先写，先失败**）：覆盖 `ToolAreaCapacity.of(area, mainCapacity)`：`LEFT`/`RIGHT` = `ceil(mainCapacity / BackpackTier.COLUMNS)`（皮革 36→4、铁 54→6、金 72→8、钻石 90→10、下界合金 108→12，非整除时向上取整），`BOTTOM` = `COLUMNS + 2` = 11，`MAIN` = `mainCapacity`；`fit(list, area, mainCapacity, empty)`：不足补空且保持前缀顺序；长度正好不变；**容量变小时不截断有物品的尾部**（长度 = `max(容量, 最后一个非空下标 + 1)`），全空的多余尾部会收缩；物品总数前后相等（随机 100 组）；`PanelArea.fromByte`（0..3 合法、未知值返回 null、`isTool()`）；`ToolAreaRules.canPlace` 本阶段对任意区域、任意栈恒为真
  - **结果**：`ToolAreaCapacityTest` 13 个用例通过（容量规则、向上取整、下区 = 默认列数 + 2、fit 补空/缩容不丢物品/随机 100 组守恒、`PanelArea` 字节往返、规则钩子恒为真）
- [X] T003 [P] 新建纯逻辑（无 Minecraft 依赖）：`src/main/java/pnnh/mcfloatingbackpack/inventory/PanelArea.java`（`enum MAIN(0)/LEFT(1)/RIGHT(2)/BOTTOM(3)`，`toByte/fromByte/isTool`）、`ToolAreaCapacity.java`（泛型 `fit` 与 `of`，规则见 data-model.md）、`ToolAreaRules.java`（`static <T> boolean canPlace(PanelArea area, T stack)` 本阶段返回 true，类注释写明这是 FR-011 的钩子）。使 T002 通过
- [X] T004 新建 `src/main/java/pnnh/mcfloatingbackpack/inventory/ToolAreaContents.java`：记录 `(List<ItemStack> left, right, bottom)`；`CODEC` 用三个 `ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf(..., List.of())`；`STREAM_CODEC` 三个列表每个上限 `MAX_TOOL_SLOTS = 64`，超限抛 `DecoderException`（同 `BackpackQuickTransferPayload` 的做法）；`EMPTY`、`empty(mainCapacity)`（各区按 `ToolAreaCapacity.of` 填空格）、规范化/复制空栈的 `normalize`。依赖 T003
  - **实现说明**：`STREAM_CODEC` 用 `ByteBufCodecs.list(MAX_TOOL_SLOTS)`，超限时由原版列表编码抛 `DecoderException`；`ToolAreaCapacity.fit` 增加了带 `Predicate` 的重载，因为 `ItemStack` 没有可用的 `equals`
- [X] T005 修改 `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackContents.java`：记录增加第三个字段 `ToolAreaContents tools`；`CODEC` 增加 `optionalFieldOf("tools", ToolAreaContents.EMPTY)`（旧存档读成空）；`STREAM_CODEC` 增加该字段（`composite` 共 3 个字段，不超 6）；保留现有的两参数 `normalize`/`fit` 作为便捷方法（`tools` 为空），新增三参数 `fit(items, cursor, tools, capacity)`：主格子沿用现有补齐/截断，三个工具区按 `ToolAreaCapacity.fit` 补齐（不丢物品）；`empty(capacity)`、`copy()` 同步更新。修正因此需要改的调用点（`ModItems`、`FloatingBackpackItem`、`ModDataComponents` 里如有）。依赖 T004
- [X] T006 修改 `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackInventory.java`：在主 `items` 之外为三个工具区各持有一个 `ItemStackHandler`（长度来自 `ToolAreaCapacity.fit` 后的列表）；`fromContents/toContents/fromItem/saveToItem` 读写它们；新增 `handler(PanelArea)`、`getStackInSlot(PanelArea, int)`、`setStackInSlot(PanelArea, int, ItemStack)`、`toolSize(PanelArea)`（越界忽略，同主格子）、`toolContents()`；**主格子的 `size()`、`items()`、`getStackInSlot(int)`、`MAX_SIZE` 不变**。依赖 T005
  - **实现说明**：读取时工具区 handler 的长度取 fit 之后列表的长度（可能大于容量）；主格子的 `size()/items()/getStackInSlot(int)` 未改
- [X] T007 修改 `src/main/java/pnnh/mcfloatingbackpack/inventory/recipe/BackpackUpgradeRecipe.java`：升级时调用三参数 `BackpackContents.fit(items, cursor, tools, capacity)`，使工具区内容在升级后保留、多出的格子为空（FR-010）。依赖 T005
- [X] T008 修改网络层：`src/main/java/pnnh/mcfloatingbackpack/network/BackpackFullSyncPayload.java` 与 `BackpackSessionPayload.java` 各增加一个 `ToolAreaContents` 字段（`from(...)/open(...)` 从 `BackpackInventory.toolContents()` 取；`StreamCodec.composite` 不超 6 个字段，会话包 5+1=6）；`BackpackSlotUpdatePayload.java` 增加 `area`（一个字节，`PanelArea.toByte`）；`BackpackNetwork.java` 把 `versioned("6")` 改成 `versioned("7")`，`sendSlotUpdate` 增加带 `PanelArea` 的重载（原有调用传 `MAIN`）。依赖 T006
- [X] T009 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/ClientBackpackView.java`：保存三个工具区的镜像；`applyFull(slots, tools, cursor)`、`applySlotUpdate(area, index, stack, cursor)`、`clear()` 同步更新；新增 `toolCount(PanelArea)`、`getToolStack(PanelArea, int)`；三个包的 `handleClient` 改调新签名。**本任务只保存数据**，滚动偏移与布局在后面的故事里接入。依赖 T008
  - **实现说明**：`ClientBackpackView` 还提供 `layout()`（用当前状态与各区格子数构造 `PanelLayout`），并在 `applyFull`/`clear` 里把各工具区格子数报告给 `FloatingPanelState`（用于夹取滚动，格子数变化时该区复位）
- [X] T010 运行 `./gradlew compileJava test`，确认 T002 与全部既有测试通过（既有布局测试此时尚未改，应仍通过）
  - **结果**：退出码 0，23 个测试类

**Checkpoint**: 编译通过；T002 绿；服务端能存三个工具区、旧存档读成空、客户端能镜像并知道每个区的格子数（协议版本 7）。

---

## Phase 3: User Story 1 - 三个工具区环绕主区域，整体仍是一个矩形 (Priority: P1) 🎯 MVP

**Goal**: 面板变成"主区域 + 左右各一列 + 下方一行（横跨左右区正下方）"的整体矩形；四块区域底色各不相同；主区域没有滚动条；缩放只改变主区域行列；面板最小 5×4。

**Independent Test**: quickstart 一（1–7）：布局与底色、无滚动条、默认尺寸下各区格子数、缩放只改主区域、缩到 5×4、拖大回来。

**注意**: T013～T017 互相耦合（`PanelSlots`、`FloatingPanelState`、输入、渲染共用新几何），按顺序做完后再编译（T018）。

- [X] T011 [P] [US1] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelLayoutGeometryTest.java`（**先写，先失败**），按 data-model.md 的 `PanelLayout`：宽 = `2·PAD_X + (cols+2)·18`、高 = `PAD_TOP + (rows+1)·18 + PAD_BOTTOM`；`cols=rows=3` 时恰为 5 列 × 4 行（宽 104、高 96）；四个矩形的位置与大小（左区与右区高度等于主区域，下区宽度 = `cols+2` 格且从面板最左起）；四块区域恰好铺满、**没有空角**（角落格子属于下区）；`hit(x, y)` 对主区域返回 `MAIN` 与 `(行+scroll)·cols+列`，对三个工具区返回对应区域与 `scroll+可见序号`，对填充格、面板空白、区域之间的边缘返回"无"；左右区可见格数多于容量时超出容量的可见位置是填充格（不可命中）；各区最大偏移（主 `max(0, 总行−行)`、左右 `max(0, 容量−行)`、下 `max(0, 容量−(cols+2))`）；`canBack = 偏移>0`、`canForward = 偏移<最大偏移`；`PanelSlots.indexAt` 对工具区坐标返回 -1
  - **结果**：`PanelLayoutGeometryTest` 15 个用例通过（宽高公式、最小 5×4 即 104×96、四区铺满无空角、可见格数、命中与滚动偏移、角落命中下区、填充格不可命中、主区域最后一行空位、各区最大偏移、箭头可见性、格子位置）
- [X] T012 [US1] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelLayout.java`：纯逻辑（无 Minecraft 依赖）。输入 `cols、rows、mainTotalRows、三个工具区的容量、四份滚动偏移、posX、posY`；输出面板宽高、四个区域的矩形、`hit(x, y)`、每个可见位置对应的下标或"填充格"、各区最大偏移与箭头可见性；常量沿用 `PAD_X=7`、`PAD_TOP=17`、`PAD_BOTTOM=7`、`SLOT=18`。使 T011 通过
- [X] T013 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelSlots.java`：保留常量与 `slotX/slotY` 之类仍被渲染使用的方法（改为委托 `PanelLayout`）；`panelWidth(cols)`、`panelHeight(rows)` 改为新公式（不再有滚动条宽度参数）；`indexAt` **保留原签名、只返回主区域下标**（内部委托 `PanelLayout.hit` 并要求 `area == MAIN`），使 011/013/014 的输入类不用改；删除滚动条相关常量与方法（`SCROLLBAR_*`、`scrollbarTrack*`、`scrollbarThumb*`、`hitsScrollbar`、`hitsScrollThumb`、`scrollRowAt`）。依赖 T012
  - **实现说明**：`PanelSlots.indexAt` 保留原签名，内部用 `layoutModel` 并要求 `area == MAIN`，主格子数用 `totalRows × visibleCols`（保持原来"虚拟网格"的语义，调用方仍自己检查 `< slotCount()`）；`slotX/slotY/isRowVisible` 与全部滚动条方法已删除
- [X] T014 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java`：`layout(screenW, screenH)` 的可用空间改为 `fitCols = (screenW − 2·PAD_X)/18 − 2`、`fitRows = (screenH − BOTTOM_HUD_MARGIN − PAD_TOP − PAD_BOTTOM)/18 − 1`（`MIN_VISIBLE_CELLS = 3` 仍压过屏幕过小），`width/height` 用新的 `PanelSlots.panelWidth/panelHeight`；缩放（`updateResize`）与上下限逻辑不变（只改主区域行列，所以面板最小自然是 5×4）；`DEFAULT_WIDTH/DEFAULT_HEIGHT` 按新公式；**删除**滚动条拖动相关成员（`scrolling`、`scrollGrabOffsetY`、`isScrolling`、`beginScrollDrag/updateScrollDrag/endScrollDrag`、`needsScrollbar`）；提供一个 `layoutModel()` 方法用当前状态与 `ClientBackpackView` 构造 `PanelLayout`。依赖 T013
  - **实现说明**：`layoutModel(counts)` 由调用方传入各区格子数（`FloatingPanelState` 保持纯类，不依赖 `ClientBackpackView`）
- [X] T015 [US1] 改写既有布局测试以适应新几何与无滚动条：`src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelLayoutTest.java`（宽高断言改用新公式；删除对 `needsScrollbar`/`endScrollDrag`/`panelWidth(true)` 的引用；新增"面板最小 5×4"与"缩放只改主区域行列"的用例）、`PanelSlotsResizeTest.java`、`PanelClampTest.java`、`FloatingPanelStateTitleIconTest.java`（凡依赖旧宽高数字的断言改成新公式算出的值）。依赖 T014
  - **结果**：`PanelLayoutTest`（14，新增 3 个：最小 5×4、缩放只改主区域、左右列与下行从屏幕预算里扣除）、`PanelSlotsResizeTest`（5，含"indexAt 只答主区域"）、`PanelClampTest`、`FloatingPanelStateTitleIconTest` 通过
- [X] T016 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java`：删除滚动条命中与拖动分支（`hitsScrollbar/hitsScrollThumb/beginScrollDrag/scrollRowAt`）及 `onMouseDragged`/`dispatchMouseButton` 里的滚动拖动处理；格子命中用 `state.layoutModel().hit(...)`：`MAIN` 走原有流程（014 选区 → 011 Ctrl → 013 拖动 → 点击）；**工具区本任务只消费这次按下并忽略**（点击在 US3 实现）；其余（关闭按钮、缩放手柄、拖动条、面板其余部分消费）不变。依赖 T014
  - **实现说明**：工具区按下在 US1 阶段只是消费，US3（T029）改为立即发点击
- [X] T017 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：按 `PanelLayout` 画四块区域——主区域保持现有底色，左区 `0xFFB5D3EE`、右区 `0xFFBFE5B5`、下区 `0xFFF0DDA0`（常量集中在类顶部），格子的高光/阴影边框沿用现有常量；工具区格子显示 `ClientBackpackView` 里的物品与数量并有悬停高亮；超出容量的可见位置画成面板底色 `0xFFC6C6C6` 的填充格；删除 `drawScrollbar`；主区域的选区边框、013 预览、标题栏、关闭按钮、缩放手柄、标题图标保持不变（013 预览与选区只在主区域）。依赖 T012、T009
  - **实现说明**：填充格（容量之外的可见位置、主区域最后一行的空位）不绘制，保持面板底色；工具区底色常量 `COLOR_SLOT_LEFT/RIGHT/BOTTOM` 在渲染类顶部
- [X] T018 [US1] 运行 `./gradlew compileJava test`，确认 T011 与改写后的 4 个布局测试及全部既有测试通过
  - **结果**：退出码 0，25 个测试类
- [X] T019 [US1] 按 `quickstart.md` 一（1–7）在 `./gradlew runClient` 逐项验证（HUD 与打开界面各做一遍），**特别留意没有空角、缩放手柄落在下区最后一格上的手感、四种底色的可辨性**；结果记到 `specs/016-panel-tool-areas/DELIVERY.md`（没做的项记"未验证"）
  - **用户验证（2026-11-05）**：用户进游戏测试，"3 个区域正常绘制，功能没有什么问题"；没有逐项记录具体观察，按用户结论勾选

**Checkpoint**: US1 达成 FR-001～FR-004、FR-004a、FR-014（布局部分）、SC-001、SC-004a。

---

## Phase 4: User Story 2 - 四块区域各自滚动，有隐藏内容时有指示 (Priority: P1)

**Goal**: 主区域和左右区上下滚动、下区左右滚动，各自独立；有隐藏内容时在对应边缘显示小扁箭头；放得下则不显示。

**Independent Test**: quickstart 二（8–14）：缩小面板后在四块区域上分别滚动，检查方向、范围、箭头的出现与消失。

- [X] T020 [P] [US2] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/AxisScrollTest.java`（**先写，先失败**）：`clamp(offset, total, visible)`（`total ≤ visible` 时恒为 0；越界夹到 `[0, total−visible]`）；`by(offset, delta, total, visible)`；`canBack(offset)`、`canForward(offset, total, visible)`（滚到头一侧为假）；`total` 变化后重新夹取不出现空白
  - **结果**：`AxisScrollTest` 5 个用例通过
- [X] T021 [US2] 新建 `src/main/java/pnnh/mcfloatingbackpack/client/panel/AxisScroll.java`：纯逻辑（无 Minecraft 依赖），按上面的规则实现。使 T020 通过
- [X] T022 [US2] 修改 `FloatingPanelState.java`：保存四份滚动偏移（主区域沿用 `scrollRow`，新增左、右、下三份），`scrollArea(PanelArea, int delta)`；每次 `layout()`、缩放、`setTotalRows`、同步长度变化后用 `AxisScroll` 重新夹取（FR-015）；某个工具区格子总数变化时该区偏移复位（在 `ClientBackpackView.applyFull` 里调用）；`setVisible(false)` 时四份全部复位（FR-016）；`layoutModel()` 带上四份偏移。依赖 T021、T014、T009
  - **实现说明**：滚动状态在 `FloatingPanelState`（`scrollRow` + `toolScroll[]`），`setToolCount` 在格子数变化时复位该区；`layout()`、缩放、`setTotalRows` 后统一用 `AxisScroll.clamp` 夹取
- [X] T023 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` 的 `scrollPanel`：鼠标在面板内时用 `layoutModel().hit...`/区域矩形判断悬停区域；滚轮朝上 = 向起点、朝下 = 向终点，每档 1 格，主区域与左右区调整纵向偏移，下区调整横向偏移；悬停区域放得下全部内容或悬停在标题栏/空白处时不滚动，但**仍返回已处理**（沿用现有的"面板内吞掉滚轮"）。依赖 T022
  - **实现说明**：滚轮朝上 = 向起点；面板内一律返回已处理（沿用原行为）；悬停区域放得下全部内容时 `scrollBy` 被夹取为 0，没有效果
- [X] T024 [US2] 修改 `FloatingPanelRenderer.java`：工具区格子按各自偏移取可见格子；画箭头——主区域与左右区在上/下边缘中点（朝上/朝下），下区在左/右边缘中点（朝左/朝右），`canBack/canForward` 各自独立，两侧都有则两个都画，放得下则一个都不画；箭头由 4 条 1 像素高的 `fill` 叠成（宽 7、5、3、1），深灰色加浅色描边，画在区域内侧，不参与命中；常量集中在类顶部。依赖 T022、T017
  - **实现说明**：箭头由 `drawTriangle` 用 1 像素高的 `fill` 叠成（浅色外框 + 深色内体，`ARROW_SIZE = 4`），常量在渲染类顶部；形状与位置**没有在游戏里看过**
- [X] T025 [US2] 运行 `./gradlew compileJava test`，确认 T020 与全部既有测试通过
  - **结果**：退出码 0，25 个测试类
- [X] T026 [US2] 按 `quickstart.md` 二（8–14）在 `./gradlew runClient` 逐项验证（下界合金背包缩小面板；主区域、左区、右区、下区各自滚动；箭头出现与消失；缩小到放不下时容量不变；主区域缩小列数时左右区的填充格），**记录箭头的大小/颜色/位置是否醒目且不挡视线、下区滚轮方向的直觉**；结果记到 `DELIVERY.md`
  - **用户验证（2026-11-05）**：用户进游戏测试，"3 个区域正常绘制，功能没有什么问题"；没有逐项记录具体观察，按用户结论勾选

**Checkpoint**: US2 达成 FR-005、FR-006、FR-015、FR-016、SC-002、SC-003。

---

## Phase 5: User Story 3 - 工具区能存放普通物品，只做单击 (Priority: P1)

**Goal**: 三个工具区里手动放入、取出、合并、交换物品（左键、右键、Alt 主手交换），由服务端校验；物品随背包保存、升级保留、旧存档可读。

**Independent Test**: quickstart 三（15–20）：放入/取出/合并/交换、关闭重开与退出重进、放进箱子再取出、升级、旧存档。

- [X] T027 [US3] 修改 `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackMenuBridge.java`：给 `clickWithMenuCarried` 增加接收 `IntFunction<ItemStack> getSlot` 与 `ObjIntConsumer<ItemStack> setSlot` 的重载；原有方法用主格子的 lambda 调它，行为不变（`mergePanelCursorIntoCarried` 不动）
- [X] T028 [US3] 修改 `src/main/java/pnnh/mcfloatingbackpack/network/BackpackClickPayload.java`：增加 `area` 字段（线上一个字节，`PanelArea.fromByte`，未知值整包忽略）；`handleServer`：校验 `slotIndex` 在该区域当前长度内（`MAIN` 沿用 `< 容量`）；按 `area` 取访问器（`MAIN` 用主格子，工具区用 `BackpackInventory.handler(area)`）；光标（`screenOpen` 时是菜单携带物，否则面板光标）非空、或 `swapHand` 时主手物品非空，先问 `ToolAreaRules.canPlace(area, stack)`，为假则整次点击忽略；其余三条路径（菜单携带物 / `swapHand` 主手交换（主手是浮动背包时不交换）/ 面板光标）共用同一套逻辑，只是 `getSlot/setSlot` 来自上面的访问器；最后 `ref.saveInventory` 并 `sendSlotUpdate(player, ref, area, slotIndex)`。依赖 T006、T008、T027
  - **实现说明**：`area` 字节写在点击包最前面；`BackpackClickLogic.applyTo` 增加了带 `PanelArea` 的重载；规则钩子被拒绝时只重发该格的槽位更新、不改任何东西
- [X] T029 [US3] 修改 `FloatingPanelInput.java` 与客户端发送：`sendClick(area, slot, rightClick)`（带 `area`；`swapHand` 仍读 `Screen.hasAltDown()`）；工具区格子的按下：按着 Shift 或 Ctrl → 消费并忽略（不拿起、不放下、不转移、**不改变主区域选区**）；否则**立即**发点击（不走 013 的"松开才结算"，不清空主区域选区，不进入 011/013/014 的任何手势）；`PanelDragInput` 调用 `sendClick` 处改传 `MAIN`。依赖 T028、T016
  - **实现说明**：工具区按下——Shift/Ctrl 或 013 拖动进行中则消费并忽略，否则立即 `sendClick(area, …)`；不清空主区域选区
- [X] T030 [US3] 运行 `./gradlew compileJava test`，确认全部测试通过
  - **结果**：退出码 0，25 个测试类
- [X] T031 [US3] 按 `quickstart.md` 三（15–20）在 `./gradlew runClient` 逐项验证：三个工具区的放入/取出/合并/交换/右键、Alt 主手交换（需要用副手打开面板才能构造，不能构造则记"未验证"）、关闭重开、退出重进、放进箱子再取出、锻造台升级后工具区内容保留且容量变为新等级的值、旧存档能打开且工具区为空；结果记到 `DELIVERY.md`
  - **用户验证（2026-11-05）**：用户进游戏测试，"3 个区域正常绘制，功能没有什么问题"；没有逐项记录具体观察，按用户结论勾选

**Checkpoint**: US3 达成 FR-007～FR-011、SC-004、SC-005、SC-008。

---

## Phase 6: User Story 4 - 不干扰现有操作，工具区只做单击 (Priority: P2)

**Goal**: 主区域所有现有操作与 015 完成时一致；011/012/013/014 只作用于主区域；工具区里的 Shift/Ctrl 与拖动不触发手势；标题栏、关闭、拖动、JEI 避让继续可用。

**Independent Test**: quickstart 四（21–26）：重跑主区域的 013/014/011/012 验证项；用 Ctrl 转移后工具区无变化；工具区里按 Shift/Ctrl/拖动没有手势；JEI 避开整个面板。

- [X] T032 [US4] 静态检查并记录：① `git diff --stat` 对 `QuickTransferInput.java`、`QuickTransferGesture.java`、`QuickTransferRules.java`、`PanelDragInput.java`、`PanelDragGesture.java`、`PanelSelectionInput.java`、`SelectionGesture.java`、`PanelSelection.java`、`BackpackQuickTransferPayload.java`、`BackpackTakeFromContainerPayload.java`、`BackpackCursorGesturePayload.java`、`PanelCursor*.java` 为空（它们一行不改）；② `grep -n "indexAt" src/main/java` 确认 011/013/014 的输入类都经 `PanelSlots.indexAt`（只返回主区域）；③ `grep -n "p3pp3rf1y\|com.tiviacz"` 在新增文件上无命中（宪法 IV）；④ `FloatingBackpackJeiPlugin.java` 读的是 `FloatingPanelState` 的位置与宽高（无需修改，面板变大后自动包含工具区）。命令与结果记到 `DELIVERY.md`
  - **结果**：`git diff --stat` 对 `QuickTransfer*`、`PanelDrag*`、`PanelSelection*`、`SelectionGesture`、`BackpackQuickTransferPayload`、`BackpackTakeFromContainerPayload`、`BackpackCursorGesturePayload`、`PanelCursor*` 为空；`indexAt` 的使用者仍是 `FloatingPanelInput`、`PanelDragInput`、`PanelSelectionInput`、`QuickTransferInput`，都经只返回主区域的 `PanelSlots.indexAt`；新增文件无 `p3pp3rf1y`/`tiviacz`；`FloatingBackpackJeiPlugin` 读 `FloatingPanelState` 的位置与宽高（`Rect2i(posX, posY, width, height)`），未改
- [ ] T033（2026-11-07 部分：016-19、22～27 与统一回归 REG-1～REG-5、守恒 CONS-1/2 已由用户验证通过；**`runClientCompat`（016-31）仍未验证**） [US4] 按 `quickstart.md` 四（21–26）、五（27–28）、七（31）验证：重跑 013 的拖动均分/右键拖动/拖动收集/双击收集、014 的 Shift 选区与 Ctrl 批量转出、011/012 的 Ctrl 单格/拖动转出，结果与 015 完成时一致；Ctrl 快速转移只进出主区域、工具区无变化；工具区里按 Shift/Ctrl 点击与按住拖过多格都没有手势；主区域有选区时点击工具区格子选区不变；标题栏/关闭按钮/整体拖动/ESC/JEI 避让可用；20 次以上工具区操作后物品总数守恒；`run/logs/latest.log` 无新增报错；`runClientCompat` 下精妙背包、旅行者背包不受影响。Windows/Linux **无法验证**，在 `DELIVERY.md` 里如实写"未验证"。结果记到 `DELIVERY.md`
  - **部分确认（2026-11-05）**：用户总体反馈"功能没有什么问题"，但 013/014/011/012 的逐项回归、`runClientCompat`、JEI 避让、守恒与日志没有被明确确认，保持未验证

**Checkpoint**: US4 达成 FR-012～FR-014、FR-017、SC-006、SC-007、SC-009。

---

## Phase 7: Polish & 文档收尾

- [X] T034 [P] 更新文档：`specs/007-floating-window-chrome/spec.md` 加【016 修订】——面板不再有滚动条（改为滚轮 + 箭头），面板最小尺寸由 3×3 变为 5×4（主区域下限 3×3 不变，外加左右各 1 列和下方 1 行）；`specs/011-ctrl-quick-transfer/spec.md`、`specs/013-panel-drag-split-gather/spec.md`、`specs/014-panel-selection/spec.md` 的"假设"处各加【016 说明】——这些功能只作用于主区域，三个工具区不参与；`specs/006-tiered-backpacks/research.md` 协议版本说明里补 "016 → `\"7\"`"；`docs/MCFloatingBackpack.md` M5 条目补三个工具区（输入/输出/功能工具区的概念、布局、滚动与箭头、只做单击、容量由等级默认尺寸决定），并指向 `specs/016-panel-tool-areas/`
- [X] T035 创建 `specs/016-panel-tool-areas/DELIVERY.md`：记录 T001、T019、T026、T031、T032、T033 的结果，**包括没做到的和结论不确定的**；列出已知限制（填充格的取舍、旧配置文件对应更大的面板、`ToolAreaRules` 钩子本阶段恒为真、Windows/Linux 未验证）；记录行为变化（滚动条改为滚轮 + 箭头、面板最小 5×4、协议升到 7、工具区只做单击）及用户是否接受
- [X] T036 运行最终回归 `./gradlew compileJava test`；确认没有客户端在运行后跑 `design/demo/tools/check_atlas_budget.sh`，图集仍 2048×1024（本 feature 不新增资源）；把 spec 的 Status 改为实现完成，并如实写明验证未完成的部分
  - **结果**：`./gradlew compileJava test` 退出码 0（25 个测试类）；图集 2048×1024（估算显存 11.2MB）退出码 0，度量时没有客户端在运行；这次启动日志（走到图集创建）没有 ERROR/Exception，但**没有进入世界**。spec Status 已改为"代码与单元测试完成，未在游戏里验证"

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（T001）：无依赖
- Phase 2（T002～T010）：T002 与 T003 先后成对（测试先于实现，可并行起步）；T004 → T005 → T006 → T008 → T009；T007 依赖 T005；T010 收尾。**阻塞所有用户故事**
- Phase 3 US1（T011～T019）：依赖 Phase 2；T011 可先写；T012 使 T011 通过；T013 → T014 → T016；T017 依赖 T012、T009；T015 依赖 T014；T018 编译在 T013～T017 之后
- Phase 4 US2（T020～T026）：依赖 US1 的布局与状态；T020 → T021 → T022 → T023；T024 依赖 T022、T017
- Phase 5 US3（T027～T031）：T027 独立；T028 依赖 T006、T008、T027；T029 依赖 T028、T016
- Phase 6 US4（T032～T033）：依赖前面所有故事的代码
- Phase 7：依赖前面的结果；T034 可与代码任务并行

### User Story Dependencies

- **US1**：依赖存储与同步底座，是 MVP；引入新几何并删除滚动条
- **US2**：依赖 US1 的布局与状态
- **US3**：依赖底座（存储与网络）和 US1 的输入分派；与 US2 互相独立
- **US4**：纯回归与隔离验证，依赖代码完成

### Parallel Opportunities

- T002 与 T003（测试先写 / 纯类实现）可并行起步
- T011（几何测试）与 T020（滚动测试）可并行先写
- T027 可与 US2 的任务并行；T034 可与任意代码任务并行

---

## Parallel Example: Phase 2 起步与 US1 起步

```text
并行启动：
  T002 ToolAreaCapacityTest.java        （容量与 fit 测试，先写）
  T003 PanelArea / ToolAreaCapacity / ToolAreaRules   （使 T002 通过）
  T011 PanelLayoutGeometryTest.java     （几何测试，先写）
  T020 AxisScrollTest.java              （滚动测试，先写）
随后顺序：T004 → T005 → T006 → T008 → T009 → T012 → T013 → T014 → T016/T017
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1、2（容量规则、数据组件、库存、网络包与客户端镜像）。
2. 完成 US1（新几何、四种底色、去掉滚动条、缩放只改主区域、最小 5×4），**停下来在游戏里验证**（T019），重点看没有空角、四种底色可辨、缩放手柄的手感。
3. 再做 US2（滚动与箭头），然后 US3（点击与保存）、US4（回归与隔离）。
4. 最后文档与 DELIVERY。

### 与宪法的关系

- 每个会改变玩家所见/所操作的故事都有客户端会话验证任务（T019、T026、T031、T033）；没做的项在 DELIVERY.md 里记"未验证"，不能因为编译或单元测试通过就勾选（宪法 V）。
- 物品存放与点击由服务端校验执行，放物品前有规则钩子（宪法 II）；不引用第三方类型（T032，宪法 IV）；不新增资源，箭头与底色全是 `fill`（宪法 VI 不适用，T001/T036 各度量一次图集作回归）；只做三个工具区与相关布局变化，扩展工具玩法与放物品限制留给后续（宪法 III）。

### 风险提醒（实现时盯住）

- **布局重写的回归面**：`PanelSlots`、缩放、命中、`FloatingPanelConfig`、JEI 避让都依赖旧几何；T015 改写 4 个测试，T033 重跑 005～015 的验证项。
- **存储与同步三处编码必须一致**：数据组件、全量同步/会话包、物品组件同步都带 `ToolAreaContents`，协议升到 7（T005、T008）。
- **主格子不动**：011/012/013/014 一行不改（T032 的 `git diff` 为空是硬指标）。
- **滚动状态**：四份偏移在缩放、容量变化、关闭面板时要夹取/复位，不出现空白行列（T022）。
- **缩容不丢物品**：`ToolAreaCapacity.fit` 不截断有物品的尾部（T002 钉住）。
