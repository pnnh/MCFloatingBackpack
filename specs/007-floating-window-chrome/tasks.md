# Tasks: 浮动背包窗口化改造（第一阶段：缩放与关闭）

**Input**: Design documents from `/specs/007-floating-window-chrome/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md（无 `contracts/`，本功能不新增网络协议，见 plan.md）

**Tests**: 延续本项目既有的"纯逻辑 JUnit + `runClient` 手动验证"模式（见 `PanelLayoutTest`/`PanelClampTest`/006 的 `BackpackTierTest`），每个用户故事的测试任务排在该故事实现任务之前。

**Organization**: 按用户故事分组，三个故事相互独立，均不依赖彼此的代码改动，可按任意顺序交付。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖同一故事内未完成的任务）
- **[Story]**: 仅用户故事阶段使用（US1–US3）

---

## Phase 1: Setup

本功能不新增包、目录或依赖，六个改动文件均已存在；无 Setup 任务。

---

## Phase 2: Foundational

US1/US2/US3 分别只新增/修改各自需要的字段与函数，没有新的跨故事共享抽象（标题文字的 `BackpackTier.byCapacity` 只被 US3 使用；`closePanel()` 只被 US2 使用）；无 Foundational 任务。

---

## Phase 3: User Story 1 - 按需调整浮动面板的行数与列数 (Priority: P1) 🎯 MVP

**Goal**: 玩家可以拖拽面板右下角独立调整行数、列数（各自下限 3，上限为现有自适应算法结果），列数变化时背包格子重新排布（虚拟总行数 = 容量 ÷ 当前列数，向上取整），超出可见行数的部分靠纵向滚动条访问；尺寸与位置一样持久化。

**Independent Test**: 打开一个格子数较多的浮动背包，分别单独调小列数、单独调小行数、同时调到 3×3，再调大到接近屏幕上限；验证纵向滚动条可访问被裁切的格子、不出现横向滚动条；关闭重开后尺寸保留。

### Tests for User Story 1

- [X] T001 [P] [US1] 新建 `src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelSlotsResizeTest.java`：覆盖 `PanelSlots.slotX/slotY/indexAt` 在可变列数下的坐标换算（例如列数从 9 改为 5 后，索引 0~4 在第 0 行、索引 5~9 在第 1 行）、`hitsResizeHandle` 的命中边界
- [X] T002 [P] [US1] 扩展 `src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelLayoutTest.java`：覆盖行、列可独立调整且互不联动、各自下限 3、上限裁剪到屏幕可用空间、`resizing` 与 `dragging`/`scrolling` 三者互斥

### Implementation for User Story 1

- [X] T003 [US1] 在 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java` 新增 `visibleCols`、`resizing`、`resizeGrabOffsetX`/`resizeGrabOffsetY` 字段，`beginResize`/`updateResize`/`endResize`（比照现有 `beginDrag`/`updateDrag`/`endDrag`），`hitsResizeHandle(mouseX, mouseY)`（比照现有 `hitsDragBar`，命中面板右下角固定像素区域）；`beginResize` 需要把 `dragging`/`scrolling` 置为 `false`，`beginDrag`/`beginScrollDrag` 也需要把 `resizing` 置为 `false`
- [X] T004 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelSlots.java`：`slotX`、`slotY`、`indexAt` 的列计算从固定常量 `COLS` 改为读取 `state.getVisibleCols()`（`col = index % visibleCols`、`row = index / visibleCols`）；`isRowVisible` 等纵向裁剪逻辑不变
- [X] T005 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/ClientBackpackView.java`：`rows()` 改为 `ceil(slots.size() / (double) FloatingPanelState.get().getVisibleCols())`，替换硬编码的 `/ PanelSlots.COLS`
- [X] T006 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java`：新增缩放手柄的鼠标按下/拖动/松开分发（命中 `hitsResizeHandle` 时调用 `beginResize`/`updateResize`/`endResize`；`endResize` 后调用 `FloatingPanelConfig.saveFrom(state)`）。实现时发现列数变化后的虚拟总行数重算不需要在这里单独调用——`FloatingPanelRenderer.render()`/`FloatingPanelEvents.onClientTick()` 本来就每帧/每 tick 无条件调用 `state.setTotalRows(ClientBackpackView.get().rows())`，缩放期间自动跟着刷新。另外发现并修复了一个真实 bug：列数不能整除背包容量时，最后一个重排行会比真实格子数多出"幽灵格"，`indexAt` 原样会把点击落在幽灵格上的点击发给服务端（服务端会把它当成越界点击直接关闭会话），已在这里加了 `slot < ClientBackpackView.get().slotCount()` 的边界检查
- [X] T007 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：在面板右下角绘制缩放手柄
- [X] T008 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`：鼠标悬停在缩放手柄上时，通过 GLFW 设置对角缩放光标（`GLFW_RESIZE_NWSE_CURSOR`），离开时恢复默认光标；`glfwCreateStandardCursor` 返回 `NULL`（如 macOS 等不支持的平台）时跳过设置，不得影响缩放拖拽功能本身
- [X] T009 [P] [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelConfig.java`：`loadInto`/`saveFrom` 新增 `visibleRows`、`visibleCols` 两个 key 的读写（仅当玩家真正手动缩放过一次后才写入，避免把旧版本"自适应撑满"的默认行为误锁成固定尺寸）

**Checkpoint**: 独立缩放行列、纵向滚动条、尺寸持久化均可用，且不出现横向滚动条；US1 可独立验收。

---

## Phase 4: User Story 2 - 一键关闭浮动面板 (Priority: P2)

**Goal**: 玩家点击标题栏右侧的关闭按钮即可隐藏浮动面板，效果与热键关闭完全一致。

**Independent Test**: 打开浮动面板，点击标题栏右侧关闭按钮，确认面板立即隐藏且服务端会话也被关闭（等同于热键 `B`）；重新打开后物品、位置、尺寸不变。

### Implementation for User Story 2

- [X] T010 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`：把既有的私有 `closePanel()` 改为 `public`，供 `FloatingPanelInput` 调用（`FloatingPanelInput` 新增对 `pnnh.mcfloatingbackpack.client.FloatingPanelEvents` 的 import；这是本功能里唯一一处 `client.panel` 反向依赖 `client` 包的地方，为了不让"热键关闭"和"按钮关闭"分叉成两套逻辑，权衡后接受）
- [X] T011 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java`：新增 `hitsCloseButton(mouseX, mouseY)`（标题栏右侧固定像素区域的命中判断，写法比照 `hitsDragBar`）
- [X] T012 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：在标题栏右侧绘制关闭按钮（X）
- [X] T013 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java`：命中关闭按钮时调用 `FloatingPanelEvents.closePanel()`

**Checkpoint**: 关闭按钮行为与热键关闭完全一致；US2 可独立验收。

---

## Phase 5: User Story 3 - 标题栏显示背包等级名称 (Priority: P3)

**Goal**: 标题栏文字按当前打开背包的等级动态显示（皮革/铁/金/钻石/下界合金浮动背包），替换固定文案。

**Independent Test**: 依次打开五个等级的浮动背包，确认标题栏文字分别显示对应的本地化物品名称。

### Tests for User Story 3

- [X] T014 [P] [US3] 扩展 `src/test/java/pnnh/mcfloatingbackpack/item/BackpackTierTest.java`：覆盖按容量（36/54/72/90/108）反查 `BackpackTier` 的五档映射，以及非法容量的兜底行为

### Implementation for User Story 3

- [X] T015 [US3] 修改 `src/main/java/pnnh/mcfloatingbackpack/item/BackpackTier.java`：新增 `byCapacity(int capacity)` 静态查表方法；容量不匹配任何已知档位时返回 `null`，调用方（渲染器）据此回退到通用标题文案，而不是猜一个可能错误的等级名
- [X] T016 [US3] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：标题栏文字改为 `BackpackTier.byCapacity(ClientBackpackView.get().slotCount())` 反查等级后取 `Component.translatable("item." + MOD_ID + "." + tier.id())`，替换现有固定的 `Component.translatable("panel." + MOD_ID + ".title")`

**Checkpoint**: 标题栏文字随背包等级正确切换；US3 可独立验收。

---

## Phase 6: Polish

- [X] T017 [P] 更新 `docs/MCFloatingBackpack.md`，在 M5 打磨相关段落补充本阶段（缩放/关闭/标题）的说明
- [X] T018 运行 `./gradlew test compileJava`，确认全部通过（9 个测试类、0 失败）
- [X] T019（2026-11-04 用户在 runClient 下 8 步全部通过，见 DELIVERY.md）按 `specs/007-floating-window-chrome/quickstart.md` 的 8 步清单在 `./gradlew runClient` 下逐项验证（含 macOS 光标回退），把结果记录到 `specs/007-floating-window-chrome/DELIVERY.md` — **需要真实客户端会话，无法由我代为执行，留给你验证**

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup、Foundational 均为空，可直接从 Phase 3 开始
- US1（Phase 3）、US2（Phase 4）、US3（Phase 5）三者互不依赖，可按任意顺序实现，但 US1 改动量最大、最贴近用户反馈的核心痛点，建议作为 MVP 优先完成
- Polish（Phase 6）依赖所有计划交付的用户故事完成

### User Story Dependencies

- **US1**：无依赖，可直接开始
- **US2**：无依赖，可直接开始；但 T010/T012/T013 分别与 T008/T007/T006 共享同一文件（`FloatingPanelEvents.java`/`FloatingPanelRenderer.java`/`FloatingPanelInput.java`），若两个故事并行开发需注意合并冲突，建议 US1 的对应任务先落地
- **US3**：无依赖，可直接开始；T016 与 T007/T012 共享 `FloatingPanelRenderer.java`，同样建议避免真正并发编辑同一文件

### Within Each User Story

- 测试任务（若有）先于实现任务编写
- `FloatingPanelState` 的字段/状态机先于依赖它的 `PanelSlots`/`FloatingPanelInput`/`FloatingPanelRenderer` 改动
- 故事内最后一个任务完成后即为该故事的交付检查点

### Parallel Opportunities

- T001 ∥ T002（不同测试文件）
- T009 可与 T004–T008 并行（独立文件 `FloatingPanelConfig.java`，只需要 T003 定下的字段名）
- T014 可独立于 US1/US2 的所有任务并行
- US1、US2、US3 三个故事之间在不同开发者手上可并行，但要留意上面提到的同文件合并冲突

## Parallel Example: User Story 1

```text
T001 PanelSlotsResizeTest.java：列数变化后的坐标换算与 resize 手柄命中测试
T002 PanelLayoutTest.java：行列独立缩放边界与 resizing 互斥测试
T009 FloatingPanelConfig.java：visibleRows/visibleCols 持久化（只需 T003 的字段名，可与 T004-T008 同时推进）
```

## Implementation Strategy

### MVP 优先（仅 US1）

1. 完成 Phase 3（US1）：独立缩放行列 + 纵向滚动 + 持久化。
2. **停下验证**：按 quickstart.md 步骤 1–4、7 手动验证一遍。
3. 这一项本身就能显著缓解面板遮挡问题，可独立交付。

### 增量交付

1. US1（MVP）→ 独立验证 → 可发布。
2. US2（关闭按钮）→ 独立验证 → 可发布。
3. US3（标题文字）→ 独立验证 → 可发布。
4. Polish：跑完整测试 + quickstart 全量验证 + 写 `DELIVERY.md`。

## Notes

- 本阶段不实现"点击下层容器穿透"与"Shift+点击双向转移"，见 `spec.md` 的 `## Out of Scope`；不要在这几个任务里顺手夹带这两项。
- 列数调整的滚动条**只有纵向**，不要实现横向滚动条或 `scrollCol`/`totalCols` 字段（见 `research.md` Decision 3 的第二轮澄清更正）。
- `hitsResizeHandle` 和 `hitsCloseButton` 都落在 `FloatingPanelState` 上（与 `hitsDragBar` 同一模式），不要放进 `PanelSlots`。
