# Tasks: 快捷访问网格

**Input**: `/specs/067-quick-access-grid/` 的 plan.md、spec.md、research.md、data-model.md、contracts/、quickstart.md

**Prerequisites**: plan.md、spec.md。research.md、data-model.md、contracts/、quickstart.md 都在。

**Tests**: 规格 SC-007 与 quickstart 要求纯逻辑单测和源码接线测试。单测与被测类同一检查点跑通。接线测试读实现之后的源码，不能先写。游戏内的 10 步只在最后一阶段做（宪法 V）。

**Organization**: 按用户故事分阶段。五条都是 P1。US1 只加预设标签就能单独核对；按键、网格和交换依次叠加上去。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与本阶段其它 [P] 任务并行（不同文件、不依赖未完成的任务）
- **[Story]**: 只出现在用户故事阶段
- 描述里写完整路径

## Path Conventions

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`
- `…test/` = `src/test/java/pnnh/mcfloatingbackpack/`
- `…res/` = `src/main/resources/assets/mcfloatingbackpack/`
- `…data/` = `src/main/resources/data/mcfloatingbackpack/`
- 不提交 Git。不新开第二个开发客户端：若已有 `runClient` 在跑，游戏内验证留到那次进程关掉之后。

---

## Phase 1: Setup

**Purpose**: 记下改动前的测试基线

- [X] T001 运行 `./gradlew test`，把类数、用例数、失败数写入 `specs/067-quick-access-grid/DELIVERY.md`（新建，中文）。不测方块图集（research R8：只加一张物品平面图）

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**: 候选名单和视口。US4、US5 和打开条件都要用，且无 Minecraft 类型

**⚠️ CRITICAL**: 本阶段完成前不做用户故事

- [X] T002 实现 `…/inventory/quick/QuickCandidates.java`：主区域下标从小到大，再是可见扩展行（每行 9 格）；跳过空格；只留带 `quick` 的；长度不超过 `baseCapacity/2`（contracts 与 data-model）
- [X] T003 [P] 实现 `…/inventory/quick/QuickViewport.java`：格子 18、边距 18、目标 9×4、至少 1×1、内容更少时缩小、滚动夹取、命中、上下「还有内容」（[contracts/grid.md](./contracts/grid.md)）
- [X] T004 [P] 单测 `…test/inventory/quick/QuickCandidatesTest.java`：空格、顺序、扩展行、隐藏行不传入、五档上限 18/27/36/45/54、同种多组、无标签不入选（依赖 T002）
- [X] T005 [P] 单测 `…test/inventory/quick/QuickViewportTest.java`：大窗口 9×4、内容少则缩小、极小窗口 1×1 且原点在画面内、滚动夹取、三角方向、最后一行靠左命中（依赖 T003）
- [X] T006 运行 `./gradlew test --tests pnnh.mcfloatingbackpack.inventory.quick.*`

**Checkpoint**: 名单和视口单测通过。还没有物品、按键和界面

---

## Phase 3: User Story 1 - 给物品打上内置「快捷」标签 (Priority: P1) 🎯 MVP

**Goal**: 标签管理器的下拉里有「快捷」，存成 `quick`，搜索和总览能认出它

**Independent Test**: 下方区放好标签管理器，给圆石加上「快捷」，总览和 `#quick` 能找到；`PresetTagsTest` 通过

### Implementation for User Story 1

- [X] T007 [US1] 把 `…test/inventory/tags/PresetTagsTest.java` 的期望改成 22 个：原 21 个顺序不变，末尾是 `quick`，且 `TagRules.isValid("quick")`
- [X] T008 [US1] `…/inventory/tags/PresetTags.java` 在 `meat` 之后追加 `quick`；`…res/lang/zh_cn.json` 与 `…res/lang/en_us.json` 加 `tag.mcfloatingbackpack.preset.quick`（快捷 / Quick）
- [X] T009 [US1] 运行 `./gradlew test --tests pnnh.mcfloatingbackpack.inventory.tags.PresetTagsTest`

**Checkpoint**: 可以给物品打 `quick`。按 Z 还没有这个键

---

## Phase 4: User Story 2 - 下方区放上「快捷访问」才让按键有用 (Priority: P1)

**Goal**: 物品、配方、创造栏、下方区至多一个。按键本身在 US3

**Independent Test**: 合成或创造栏拿到物品；下方区再放第二个被拒绝；主区域可以多放且只是普通物品；`./gradlew test` 里修订断言为 7

### Implementation for User Story 2

- [X] T010 [US2] `…/item/QuickAccessItem.java`（`stacksTo(1)`）；`…/item/ModItems.java` 注册 `quick_access`，创造栏紧挨标签管理器之后
- [X] T011 [P] [US2] `…data/recipe/quick_access.json`（`RIR` / `LCL` / `RIR`：红石粉、铁锭、皮革、指南针）；`…res/models/item/quick_access.json`（与 `tag_manager.json` 一样只有一层贴图）；`…res/textures/item/quick_access.png`（16×16 平面图）
- [X] T012 [P] [US2] 两份语言文件加 `item.mcfloatingbackpack.quick_access` 与 `message.mcfloatingbackpack.limit.quick_access`；`…data/tags/item/never_trash.json` 加上 `mcfloatingbackpack:quick_access`
- [X] T013 [US2] `…/inventory/revision/DataRevision.java` 的 `CURRENT` 改为 7 并在注释里写明是快捷访问这个物品。把写死 `CURRENT = 6` 的断言改为 7：`…test/inventory/revision/DataRevisionTest.java`、`…test/resources/TankWiringTest.java`、`PipeWiringTest.java`、`MobileFurnaceWiringTest.java`、`LightWiringTest.java`、`JukeboxWiringTest.java`、`MobileCraftingWiringTest.java`、`SnapshotCapacityWiringTest.java`。再 grep `CURRENT = 6`，漏网的一并改
- [X] T014 [US2] `…/inventory/BackpackInventory.java` 加 `hasQuickAccess`（扫整个下方区）。`…/client/panel/FloatingPanelInput.java` 的 `refusedByLimit` 与 `…/network/BackpackClickPayload.java` 各加一处 `ToolSingleRules.check`，目标区是下方，提示 `limit.quick_access`。不要给这个物品加左键展开
- [X] T015 [US2] 运行 `./gradlew test`

**Checkpoint**: 物品能放进下方区，第二个被拒绝。还没有 Z 键

---

## Phase 5: User Story 3 - 只在游戏画面里弹出 (Priority: P1)

**Goal**: 默认 Z。游戏画面且面板关着才打开一块不暂停的透明界面；其它界面无提示。打不开时快捷栏上方有对应的一句

**Independent Test**: 游戏画面按 Z 出现界面（此时可以还是空的）；物品栏、暂停、浮动面板开着时按了没反应；没有背包、没有快捷访问、背包在主手、没有候选时各有一句提示且 1 秒内不重复

### Implementation for User Story 3

- [X] T016 [US3] `…/client/FloatingBackpackKeys.java` 增加 `QUICK_ACCESS`，默认 Z，`KeyConflictContext.IN_GAME`，并注册。两份语言文件加 `key.mcfloatingbackpack.quick_access`
- [X] T017 [P] [US3] `…/client/quick/QuickNotices.java`：同一句 20 tick 内不重复，用快捷栏上方（`displayClientMessage` 的第二个参数为 true）
- [X] T018 [US3] `…/client/quick/QuickAccessScreen.java`：`isPauseScreen` 为假；`renderBackground` 留空；Esc、右键、再按这个键关闭；快捷栏数字、丢弃、攻击不传给游戏。本阶段可以先不画物品
- [X] T019 [US3] `…/client/FloatingPanelEvents.java` 的按键处理：有 overlay、有任意 Screen、或面板可见则直接返回且不提示。否则按 [contracts/grid.md](./contracts/grid.md) 的打开条件决定提示或 `setScreen`。提示键：复用 `need_backpack`；新键 `quick.need_tool`、`quick.in_hand`、`quick.none`（两份语言文件）。修订比本版本新时用现有的 `revision.protected`，不打开、不启动 040 的确认窗口。处理之后 `setCanceled(true)`；若物理键也是原版与副手交换，再 `consumeClick()`
- [X] T020 [US3] 运行 `./gradlew compileJava test`

**Checkpoint**: 按 Z 能在游戏画面打开并关掉一块空的透明界面。格子还没画

---

## Phase 6: User Story 4 - 看见带「快捷」的物品，窗口小时可以滚动 (Priority: P1)

**Goal**: 网格居中，只画候选物品，滚轮一行，边缘小三角，悬停有物品提示

**Independent Test**: 皮革背包若干组一次看完且没有三角；下界合金超过 36 组时默认看见 4 行，滚轮能看到后面；窗口缩小时网格仍完整在画面内

### Implementation for User Story 4

- [X] T021 [US4] 在 `…/client/quick/QuickAccessScreen.java` 里从 `BackpackRef.resolve` 的物品读 `BACKPACK_CONTENTS`（不读 `ClientBackpackView`），用 `QuickCandidates` 与 `QuickViewport` 画物品、堆叠数和耐久。悬停填 `0x80FFFFFF` 并 `renderTooltip`。三角尺寸与颜色同 `FloatingPanelRenderer` 的箭头（深色 `0xFF3A3A3A`、浅色 `0xFFF2F2F2`、深 3 像素），不参与命中，也不把面板里的私有方法改成公共的。滚轮 `scrollDeltaY > 0` 时滚动减 1，并取消事件。`init` 在窗口变化时重算并夹住滚动。点在视口外关闭且不发包
- [X] T022 [US4] 运行 `./gradlew compileJava test`

**Checkpoint**: 网格能看、能滚。点击还不交换

---

## Phase 7: User Story 5 - 点一格，与主手整组交换 (Priority: P1)

**Goal**: 左键整组对调。成功关闭网格。失败留着并提示。协议 25

**Independent Test**: 主手的剑与一组带「快捷」的食物对调，总数不变，网格关闭；剑再按 Z 不出现。分类行拒收时物品不动、网格还在。`./gradlew test` 0 失败，协议断言为 25

### Implementation for User Story 5

- [X] T023 [US5] `…/network/QuickSwapPayload.java`（C→S：来源序号、格号、主区域下标）与 `…/network/QuickSwapResultPayload.java`（S→C：一个布尔）。频道名见 [contracts/protocol.md](./contracts/protocol.md)
- [X] T024 [US5] `…/network/BackpackNetwork.java` 改为 `versioned("25")` 并注册两个包。`versioned("1").optional()` 不动。`QuickSwapPayload` 按 [contracts/swap.md](./contracts/swap.md) 的八步处理：容器仍是玩家物品栏、来源与 `BackpackRef.resolve` 规范化后相同、`BackpackGate.acquire`（不用 `acquireForOpen`）、下方区有快捷访问、格子在重算的名单里、主手不是浮动背包且嵌套检查通过、`canPlaceMain`、整组对调后 `saveInventory`。失败时快捷栏上方一句（`quick.wrong_row` 或 `quick.refused` 或 `quick.need_tool`），20 tick 内不重复，并回 `ok=false`。面板会话若开着这只背包，再 `sendSlotUpdate`
- [X] T025 [US5] `…/client/quick/QuickAccessScreen.java`：左键点中格子才发 `QuickSwapPayload`（下标是候选名单里的背包格子，不是视口上的第几格）；结果到来前忽略连点；`ok=true` 关闭；`ok=false` 留着。两份语言文件补上 `quick.wrong_row` 与 `quick.refused`
- [X] T026 [US5] 把写死 `versioned("24")` 的断言改为 `"25"`：`…test/resources/` 下的 `AllWiringTest.java`、`TankWiringTest.java`、`CategoryExtensionUiResourcesTest.java`、`IntegratedTerminalsWiringTest.java`、`NetworkTakeWiringTest.java`、`CombinedWiringTest.java`、`MobileFurnaceWiringTest.java`、`GreyCellWiringTest.java`、`PipeWiringTest.java`、`SearchPrefixResourcesTest.java`、`JukeboxWiringTest.java`、`MobileCraftingWiringTest.java`、`ItemTooltipWiringTest.java`、`BackpackSortWiringTest.java`、`SearchResourcesTest.java`、`LightWiringTest.java`、`SnapshotCapacityWiringTest.java`、`DowngradeProtectionResourcesTest.java`、`VersionCheckResourcesTest.java`，以及 `…test/worldmarker/WorldMarkerWiringTest.java`、`…test/resources/ServerCompatWiringTest.java`。再 grep `versioned(\"24\")`。注释里过时的协议号一并改掉。`versioned("1")` 不动
- [X] T027 [US5] `…test/resources/QuickAccessWiringTest.java`：协议 25、两个包已注册、`CURRENT = 7`、按键默认 Z 且 `IN_GAME`、`isPauseScreen` 为假、两处 `ToolSingleRules` 的目标区是下方、`never_trash` 含 `quick_access`、没有 mixin 和 `setAccessible`
- [X] T028 [US5] 运行 `./gradlew test`

**Checkpoint**: 代码完整。游戏内还没走 quickstart

---

## Phase 8: Polish

**Purpose**: 游戏内验证和文档

- [X] T029 按 `specs/067-quick-access-grid/quickstart.md` 的 10 步在客户端会话里验证。每步在 `specs/067-quick-access-grid/DELIVERY.md` 写实际看到了什么；没做的写「未验证」。不在 `src/main` 留调试入口
- [X] T030 [P] 更新 `README.md`、`README_CN.md`（快捷访问、默认键 Z、只在游戏画面、网格与交换）、`docs/plan/quick-access.md` 与 `docs/plan/README.md` 的状态
- [X] T031 写完 `specs/067-quick-access-grid/DELIVERY.md`：测试前后数量、与 spec/plan 的偏离、修订 6→7 的理由、协议 24→25、已知局限、宪法复查
- [X] T032 再跑 `./gradlew test`，确认 0 失败且没有临时修改。不提交

## Phase 9: 实机修订（按住显示、放大、落点改放空位）

**Purpose**: 按住 Z 才显示，松开消失；物品 32 像素、格子 40 像素；放不进被点格子时改放第一个允许的空位。网格和 Alt+左键同一条规则。不升数据修订，不升协议。

- [X] T033 `HandSwapPlace` 与 `HandSwapSlots`：空手或被点格子能放时用这一格；否则按 `mainTargetOrder` 找第一个空的且 `canPlaceMain` 的其它格子；没有则 -1。`HandSwapPlaceTest` 覆盖这四种
- [X] T034 `QuickSwapPayload` 与 `BackpackClickPayload.swapHand`（仅主区域）改用落点。没有落点时 `quick.no_room`，不改格子。落点不是被点格子时两个格子都 `sendSlotUpdate`。普通点击放进分类行仍拒绝且不说话
- [X] T035 按住显示：`GLFW_PRESS` 才打开，`GLFW_REPEAT` 忽略，松开物理键关闭。右键和点在网格外不关闭。`QuickViewport` 物品 32、格子 40，绘制时放大 2 倍。两份语言文件加上 `quick.no_room`
- [X] T036 规格、契约、数据模型、`docs/plan/quick-access.md`、两份 README、quickstart 改成上述规则。再跑 `./gradlew test`。游戏内用 `scripts/ai-gui` 核对按住、大小和分类行落点，结果写入 `DELIVERY.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup（Phase 1）**: 无依赖
- **Foundational（Phase 2）**: 依赖 T001，并挡住全部用户故事
- **US1（Phase 3）**: 依赖 Phase 2。不依赖 US2–US5
- **US2（Phase 4）**: 依赖 Phase 2。与 US1 都改语言文件，顺序做，不并行
- **US3（Phase 5）**: 依赖 US2（物品与 `hasQuickAccess`）和 Phase 2（名单）
- **US4（Phase 6）**: 依赖 US3（界面已经能打开）
- **US5（Phase 7）**: 依赖 US4（点击知道是哪一格）
- **Polish（Phase 8）**: 依赖 US5

### User Story Dependencies

- **US1**: 预设标签。没有物品和按键也能核对
- **US2**: 物品与限放。按键提示要等 US3
- **US3**: 打开与关闭。格子内容等 US4
- **US4**: 只画、只滚。不交换
- **US5**: 唯一会移动物品的阶段

### Within Each User Story

- US1：先改测试期望（T007），再改预设（T008）
- US2：物品（T010）与配方、语言（T011、T012）可并行；修订与限放在其后
- US5：包（T023）→ 注册与八步（T024）→ 客户端点击（T025）→ 历史协议断言（T026）与接线测试（T027）→ 全量测试（T028）

### Parallel Opportunities

- T002 与 T003 并行；T004 与 T005 在各自的类完成之后并行
- T011 与 T012 并行
- T016 与 T017 并行
- T030 与 T031 可在 T029 之后并行
- US1 与 US2 不并行：都改 `zh_cn.json` / `en_us.json`

---

## Parallel Example: Foundational

```text
T002 QuickCandidates.java
T003 QuickViewport.java
# 两类都落地之后：
T004 QuickCandidatesTest.java
T005 QuickViewportTest.java
```

## Parallel Example: User Story 2

```text
T011 recipe + model + texture
T012 lang item name + limit message + never_trash.json
```

---

## Implementation Strategy

### MVP First（User Story 1）

1. Phase 1、Phase 2
2. Phase 3：下拉里有「快捷」，可以打在物品上
3. 停下来跑 `PresetTagsTest`，并在游戏里给一件物品打上这个标签

US1 单独还打不开网格。五条都是 P1，玩家能用的最小范围是做到 Phase 7。

### Incremental Delivery

1. US1：标签
2. US2：物品与下方区限放
3. US3：按 Z 只在游戏画面打开
4. US4：看见物品、滚动
5. US5：点一下换到主手
6. Phase 8：按 quickstart 在游戏里走一遍，再写 DELIVERY

### Parallel Team Strategy

Phase 2 完成后，一人做 US1 的预设测试与 `PresetTags`，另一人做 US2 的物品类、配方和贴图。语言文件交给其中一人，避免同时改。US3 及以后顺序做。

---

## Notes

- 不提交、不打 tag
- 不新增依赖、配置项、mixin，不用反射
- 受 040 保护的背包：不打开网格、不交换、不消耗「再打开一次」的确认窗口
- 失败的交换不乐观关闭网格，等 `quick_swap_result`
