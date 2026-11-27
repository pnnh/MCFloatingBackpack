# Tasks: 五档快捷访问

**Input**: `/specs/068-tiered-quick-access/` 的 plan.md、spec.md、research.md、data-model.md、contracts/、quickstart.md

**Prerequisites**: plan.md、spec.md。research.md、data-model.md、contracts/、quickstart.md 都在。

**Tests**: 规格 SC-006 与 quickstart 要求纯逻辑单测和源码接线测试。单测与被测类同一阶段跑通。接线测试读实现之后的源码，不能先写。游戏内的 6 步只在最后一阶段做（宪法 V）。

**Organization**: 按用户故事分阶段。US1、US2 是 P1；US3、US4 是 P2。基础阶段只把五件物品注册出来，行为仍是「只有皮革档生效」。US1 加上同档限制。US2 改网格行数，不依赖新档。

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

- [X] T001 运行 `GRADLE_USER_HOME=$HOME/.gradle ./gradlew test --offline --console=plain`，把类数、用例数、失败数写入 `specs/068-tiered-quick-access/DELIVERY.md`（新建，中文）。不测方块图集（research R7：只改物品平面图）

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**: 五件物品能被注册、放进背包，数据修订升到 8。此阶段结束时，只有皮革档仍让 Z 生效，和 067 相同

**⚠️ CRITICAL**: 本阶段完成前不做用户故事

- [X] T002 `…/item/QuickAccessItem.java` 增加 `BackpackTier` 字段和 `tier()`。`…/item/ModItems.java` 保留皮革档 `QUICK_ACCESS`（注册名仍是 `quick_access`），再注册 `iron_quick_access`、`gold_quick_access`、`diamond_quick_access`、`netherite_quick_access`，并提供 `quickAccess(BackpackTier)`。创造栏在标签管理器之后按皮革到下界合金排列。不要改 `…/client/FloatingBackpackKeys.java` 的按键。现有 `ModItems.QUICK_ACCESS.get()` 调用先留着，使本阶段行为仍只认皮革档
- [X] T003 [P] `…res/lang/zh_cn.json` 与 `…res/lang/en_us.json` 写上五件物品的显示名：皮革快捷访问 / Leather Quick Access，以及铁、金、钻石、下界合金。皮革档改现有的 `item.mcfloatingbackpack.quick_access`，不要另起一个皮革 id
- [X] T004 [P] `…data/tags/item/never_trash.json` 在已有的 `quick_access` 之后加上四档的 id
- [X] T005 [P] `…/inventory/revision/DataRevision.java` 的 `CURRENT` 改为 8，注释写明 8 增加了铁、金、钻石、下界合金四件快捷访问。把写死 `CURRENT = 7` 或 `assertEquals(7, DataRevision.CURRENT)` 的测试改为 8，并让 `isNewer(8)` 为假、`isNewer(9)` 为真：`…test/inventory/revision/DataRevisionTest.java`、`…test/resources/QuickAccessWiringTest.java`、`TankWiringTest.java`、`PipeWiringTest.java`（两处）、`MobileFurnaceWiringTest.java`、`LightWiringTest.java`、`JukeboxWiringTest.java`、`MobileCraftingWiringTest.java`、`SnapshotCapacityWiringTest.java`。再 grep `CURRENT = 7`，漏网的一并改。协议断言仍是 `"25"`，不要改
- [X] T006 运行 `GRADLE_USER_HOME=$HOME/.gradle ./gradlew test --offline --console=plain`，确认基础阶段仍是 0 失败

**Checkpoint**: 创造栏能看到五件物品，旧背包仍能打开。放错档还不会被拒绝，网格仍是 4 行

---

## Phase 3: User Story 1 - 同档的快捷访问才让按键生效 (Priority: P1) 🎯 MVP

**Goal**: 只有同档背包的下方功能区能放下对应快捷访问，并且只有这一件让 Z 和交换生效

**Independent Test**: 五只背包各放同档能按住 Z 打开；错档放不进下方区；旧的皮革档留在更高档背包下方时不生效，并占住那一个名额。`QuickAccessPlaceTest` 通过

### Implementation for User Story 1

- [X] T007 [US1] 新增 `…/inventory/quick/QuickAccessPlace.java`（无 Minecraft 类型）。判定顺序按 [contracts/items.md](./contracts/items.md)：不是下方区或不是快捷访问则通过；档不同则 `WRONG_TIER`（同一格替换也不行）；档相同但下方任意档的快捷访问会变多则 `DUPLICATE`；同一格换成同档的允许
- [X] T008 [US1] 新增 `…test/inventory/quick/QuickAccessPlaceTest.java`：错档拒绝、同一格换成同档允许、已有一件时再放到别的格拒绝、主区域不限、更高档背包里已有的皮革档占名额（依赖 T007）
- [X] T009 [US1] `…/client/panel/FloatingPanelInput.java` 的 `refusedByLimit` 与 `…/network/BackpackClickPayload.java` 里那处只认 `QUICK_ACCESS` 的 `ToolSingleRules.check` 换成 `QuickAccessPlace`。错档用 `message.mcfloatingbackpack.quick.wrong_tier`；多一件仍用 `limit.quick_access`。客户端拒绝显示在聊天，服务端显示在快捷栏上方。不要改 `…/inventory/ToolSingleRules.java` 的签名（依赖 T007）
- [X] T010 [P] [US1] 两份语言文件：`quick.need_tool` 改为带 `%s`（中文「把%s放进这只背包的下方功能区」）；新增 `quick.wrong_tier`（「下方功能区只能放同档的快捷访问」/ “The bottom only takes a Quick Access of this backpack's tier”）
- [X] T011 [US1] `…/inventory/BackpackInventory.java` 的 `hasQuickAccess` 改为只在下方区有与 `tier()` 相同的一件时返回真（research R2；不要留一个仍认任意皮革档的旧方法）。`…/client/quick/QuickAccessOpener.java`、`…/client/quick/QuickNotices.java`、`…/network/QuickSwapPayload.java` 在没有同档时用 `quick.need_tool` 并带上这一档物品的显示名。冷却仍按翻译键，20 tick。修订更新时仍走 `revision.protected`，不打开确认窗口（依赖 T010）
- [X] T012 [US1] 更新 `…test/resources/QuickAccessWiringTest.java`：两处放入走 `QuickAccessPlace`；`never_trash` 含五档；协议仍是 `versioned("25")` 且没有新的包类型；打开与交换使用同档判定，而不是只认 `QUICK_ACCESS.get()`（依赖 T009、T011）
- [X] T013 [US1] 运行 `GRADLE_USER_HOME=$HOME/.gradle ./gradlew test --offline --console=plain`

**Checkpoint**: 同档才生效。网格仍一次最多 4 行。配方还是只有皮革档那一张

---

## Phase 4: User Story 2 - 一般窗口一次看见 6 行 (Priority: P1)

**Goal**: 54 组在一般窗口里一次看完；只有窗口特别矮或窄到不足 9 列时才滚动

**Independent Test**: 高至少 276 时 54 组是大档 9×6 且没有三角；高 240 与 270 是小档 9×6 且滚轮不换行；高不到 228 才滚动。`QuickViewportTest` 通过

### Implementation for User Story 2

- [X] T014 [US2] 改 `…/inventory/quick/QuickViewport.java`，按 [contracts/grid.md](./contracts/grid.md)：`PREFERRED_ROWS = 6`；高度至少 276 用物品 32、格子 40，否则物品 24、格子 32；`Fit` 带上这一次的 `item` 与 `slot`；`hit` 用 `Fit` 的格子尺寸。边距仍是 18。内容不超过可见行时滚动夹在 0
- [X] T015 [US2] 改 `…/client/quick/QuickAccessScreen.java`：悬停、物品、堆叠数和网格边界都用 `Fit` 的格子与物品尺寸，放大倍数是物品尺寸 / 16。三角仍是 3 像素，颜色不变（依赖 T014）
- [X] T016 [US2] 改写 `…test/inventory/quick/QuickViewportTest.java`：高 300、宽 400、54 组是大档 9×6 且 `moreBelow` 为假；高 270 与 240、宽不少于 324 是小档 9×6，滚动加 1 仍停在 0；高不到 228 才出现下方三角；宽不到 324 时列数少于 9。删掉「大窗口是 9×4 且有下方三角」那条旧断言（依赖 T014）
- [X] T017 [US2] 运行 `GRADLE_USER_HOME=$HOME/.gradle ./gradlew test --offline --console=plain --tests 'pnnh.mcfloatingbackpack.inventory.quick.*'`

**Checkpoint**: 一般窗口一次看见 6 行。图标仍是原来的金边

---

## Phase 5: User Story 3 - 按背包的锻造路线升级 (Priority: P2)

**Goal**: 皮革档仍在工作台合成；更高四档在锻造台逐级升级，不能跳级

**Independent Test**: 工作台做出皮革档；锻造台四步各得到下一档一件；少放模板没有产物

### Implementation for User Story 3

- [X] T018 [US3] 按 [contracts/recipes.md](./contracts/recipes.md) 新增四份 `minecraft:smithing_transform`：`…data/recipe/upgrade_iron_quick_access.json`（哨兵纹饰模板 + `quick_access` + 铁块）、`upgrade_gold_quick_access.json`（沙丘纹饰模板 + `iron_quick_access` + 金块）、`upgrade_diamond_quick_access.json`（眼纹饰模板 + `gold_quick_access` + 钻石块）、`upgrade_netherite_quick_access.json`（下界合金升级模板 + `diamond_quick_access` + 下界合金块）。不要改 `…data/recipe/quick_access.json` 的产物。不要使用 `mcfloatingbackpack:backpack_upgrade`，也不要改 JEI 插件

**Checkpoint**: 四步升级能在配方书里看到。图标还没按档分色

---

## Phase 6: User Story 4 - 五档图标能一眼分开 (Priority: P2)

**Goal**: 五张都是奶油底上的红色指针，外框改成对应背包的颜色

**Independent Test**: 五件排成一排时，皮革棕色框、铁浅灰、金金边、钻石青色、下界合金深色带一点红；物品栏、手上、掉落和展示框是同一张图

### Implementation for User Story 4

- [X] T019 [US4] 重做五张 16×16 物品平面图，构图沿用现有指针，只改外框（research R7）：`…res/textures/item/quick_access.png` 改为棕色框（覆盖现在的金边）；另存 `iron_quick_access.png`（浅灰）、`gold_quick_access.png`（保持原来的金边）、`diamond_quick_access.png`（青色）、`netherite_quick_access.png`（深色并带一点红）。不放入方块图集
- [X] T020 [P] [US4] 新增四份 `item/generated` 模型：`…res/models/item/iron_quick_access.json`、`gold_quick_access.json`、`diamond_quick_access.json`、`netherite_quick_access.json`，贴图层指向同名物品图。皮革档继续用现有的 `…res/models/item/quick_access.json`

**Checkpoint**: 五件在物品栏里能分开。游戏内的完整步骤还没走

---

## Phase 7: Polish

**Purpose**: 全量测试、游戏内核对、文档状态

- [X] T021 运行 `GRADLE_USER_HOME=$HOME/.gradle ./gradlew test --offline --console=plain`，把类数、用例数、失败数补进 `specs/068-tiered-quick-access/DELIVERY.md`
- [X] T022 按 `specs/068-tiered-quick-access/quickstart.md` 的 6 步进游戏核对。已有 `runClient` 时不要再开一个。没实际看过的步骤写成未验证，不要写成已完成（宪法 V）
- [X] T023 [P] 把实现状态写进 `README.md`、`README_CN.md`、`docs/plan/quick-access.md` 和 `docs/plan/README.md`。`DELIVERY.md` 写明哪些步在游戏里看过

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: 依赖 T001 — 阻塞全部用户故事
- **US1 (Phase 3)**: 依赖 Phase 2。不依赖 US2、US3、US4
- **US2 (Phase 4)**: 依赖 Phase 2。不依赖 US1 的同档规则，可与 US1 并行
- **US3 (Phase 5)**: 依赖 Phase 2 的五个注册名。不依赖 US1 的放入规则
- **US4 (Phase 6)**: 依赖 Phase 2 的注册名。不依赖 US3
- **Polish (Phase 7)**: 依赖想交付的那些故事。全部交付时依赖 US1–US4

### User Story Dependencies

- **US1 (P1)**: 基础阶段之后即可开始。单独完成时，网格仍是 067 的 4 行，但同档限制已经生效
- **US2 (P1)**: 基础阶段之后即可开始。单独完成时，仍只有皮革档生效，但 6 行已经一次可见
- **US3 (P2)**: 只依赖五个 id。配方不调用放入规则
- **US4 (P2)**: 只依赖五个 id 和现有的皮革模型。与 US3 并行

### Within Each User Story

- 纯逻辑类先于它的单测（T007 → T008，T014 → T016）
- 放入规则先于两处调用（T007 → T009）
- 提示文案先于带物品名的调用（T010 → T011）
- 接线测试在对应源码改完之后（T012）
- 故事内的 `./gradlew test` 在该故事最后

### Parallel Opportunities

- T003、T004、T005 可与 T002 并行（语言文件、标签、修订字面量，不同文件）
- T010 可与 T007 并行
- Phase 2 完成后，US1 与 US2 可并行；US3 与 US4 可并行
- T019 与 T020 可并行

---

## Parallel Example: User Story 1 与 User Story 2

```text
# Phase 2 完成后同时进行：
T007  QuickAccessPlace.java
T010  两份语言文件的 need_tool 与 wrong_tier
T014  QuickViewport.java

# 随后各自收口：
T008、T009、T011、T012  （US1）
T015、T016              （US2）
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 与 Phase 2
2. 完成 Phase 3（US1）
3. **停下来核对**：同档能打开，错档放不进去，全量测试 0 失败
4. 网格仍是 4 行也可以先停。US2 是同优先级的下一块

### Incremental Delivery

1. 基础阶段：五件物品存在，行为与 067 相同
2. US1：同档才生效
3. US2：一般窗口一次 6 行
4. US3：锻造升级
5. US4：五档图标
6. Phase 7：游戏内 6 步与文档

### Parallel Team Strategy

1. 一起做完 Phase 2
2. 之后一人做 US1，另一人做 US2
3. US3 与 US4 再并行
4. 最后一起做 Phase 7

---

## Notes

- [P] 表示不同文件、且不依赖本阶段里还没做完的任务
- 故事标签只用于 US1–US4
- 不要升协议，不要新加网络包，不要改 `ToolSingleRules` 的签名
- 不要把背包升档写成会自动换成新档快捷访问
- 游戏内没看过的步骤保持未验证
