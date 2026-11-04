# Tasks: 分级浮动背包

**Input**: Design documents from `/specs/006-tiered-backpacks/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/tiered-backpack.md, quickstart.md

**Tests**: 计划要求用 JUnit 覆盖等级容量与升级后前缀保留。门禁、锻造、外观、滚动用 `runClient`（quickstart）。

**Organization**: 按用户故事分组。等级容量与可变长度同步是所有故事的前置。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 仅用户故事阶段使用（US1–US4）

---

## Phase 1: Setup

**Purpose**: 本特征要新增的包与资源目录

- [x] T001 Create package directory `src/main/java/pnnh/mcfloatingbackpack/inventory/recipe/`
- [x] T002 [P] Create asset directories `src/main/resources/assets/mcfloatingbackpack/models/item/` and `src/main/resources/assets/mcfloatingbackpack/textures/item/` if either is missing

---

## Phase 2: Foundational

**Purpose**: 五档容量与最多 108 格的组件/同步。完成前不要注册新物品或配方。

- [x] T003 Implement `BackpackTier` (LEATHER 36, IRON 54, GOLD 72, DIAMOND 90, NETHERITE 108, next tier, addition item) in `src/main/java/pnnh/mcfloatingbackpack/item/BackpackTier.java`
- [x] T004 Change `BackpackContents` in `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackContents.java` so normalize/empty take an explicit capacity, pad short lists, and truncate long ones
- [x] T005 Change `BackpackInventory` in `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackInventory.java` to an instance capacity with `MAX_SIZE = 108`, loaded from the source item's tier
- [x] T006 Add JUnit coverage for the five capacities and pad/truncate in `src/test/java/pnnh/mcfloatingbackpack/item/BackpackTierTest.java`
- [x] T007 Raise the payload registrar to version `3` and encode slot lists up to 108 in `src/main/java/pnnh/mcfloatingbackpack/network/BackpackNetwork.java`, `BackpackSessionPayload.java`, `BackpackFullSyncPayload.java`, and `BackpackSlotUpdatePayload.java`
- [x] T008 Size `ClientBackpackView` from the synced list length in `src/main/java/pnnh/mcfloatingbackpack/client/panel/ClientBackpackView.java`

**Checkpoint**: 皮革背包仍为 36 格；编解码不再写死 36。测试通过。

---

## Phase 3: User Story 1 - 五个等级、格子随等级增加 (Priority: P1) 🎯 MVP

**Goal**: 五只背包可在游戏画面打开，面板为 4 / 6 / 8 / 10 / 12 行 × 9 列。无背包仍不能开。

**Independent Test**: 创造取出五只，主手按 B，数行数。无背包按 B 只有提示。

- [x] T009 [P] [US1] Register iron, gold, diamond, and netherite backpacks and list all five in the creative tab in `src/main/java/pnnh/mcfloatingbackpack/item/ModItems.java`
- [x] T010 [P] [US1] Add `tier()` on `FloatingBackpackItem` and keep right-click open / shift equip behavior in `src/main/java/pnnh/mcfloatingbackpack/item/FloatingBackpackItem.java`
- [x] T011 [P] [US1] Add en_us and zh_cn names for the four new items in `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` and `src/main/resources/assets/mcfloatingbackpack/lang/zh_cn.json`
- [x] T012 [US1] Lay out rows from the active view size (not a fixed 36) in `src/main/java/pnnh/mcfloatingbackpack/client/panel/PanelSlots.java` and `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`
- [x] T013 [US1] Reject clicks whose slot index is outside that item's capacity in `src/main/java/pnnh/mcfloatingbackpack/network/BackpackClickPayload.java`

**Checkpoint**: 五档格子数正确；点击不会写到容量外。

---

## Phase 4: User Story 2 - 锻造升级并保留内容 (Priority: P1)

**Goal**: 锻造台模板槽为空时，用低一级背包 + 1 个材料升到下一档，原有物品还在，新格为空。不能降级、不能跳级。

**Independent Test**: 皮革装入至少 5 种物品，加铁块锻成铁背包，标记物仍在且多出空行。材料不配则无结果。

- [x] T014 [US2] Add a failing-then-passing JUnit test that copied prefix stacks survive a capacity increase in `src/test/java/pnnh/mcfloatingbackpack/inventory/BackpackUpgradeTest.java`
- [x] T015 [US2] Implement content copy onto the next tier (pad empty slots, keep cursor) in `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackUpgrade.java`
- [x] T016 [US2] Implement smithing recipe `mcfloatingbackpack:backpack_upgrade` (empty template slot, one addition item, copy components) in `src/main/java/pnnh/mcfloatingbackpack/inventory/recipe/BackpackUpgradeRecipe.java`
- [x] T017 [US2] Register that recipe serializer from `src/main/java/pnnh/mcfloatingbackpack/MCFloatingBackpack.java`
- [x] T018 [P] [US2] Add four smithing recipes (iron block, gold block, diamond, netherite ingot) under `src/main/resources/data/mcfloatingbackpack/recipe/`

**Checkpoint**: 四级升级链可用，物品不丢不复制。

---

## Phase 5: User Story 3 - 可辨认的背包外形与图标 (Priority: P1)

**Goal**: 五档都是旅行背包轮廓（包身、翻盖、肩带、扣具），配色可分，贴图至少 32×32。手持和背部都不是立方体或皮革胸甲。

**Independent Test**: 快捷栏五图标可分；手持能看出包；胸甲栏 F5 背部是对应配色的背包。贴图边长 ≥ 32。

- [x] T019 [US3] Stop extending `ArmorItem`; equip the chest slot with the equippable component and no humanoid armor texture in `src/main/java/pnnh/mcfloatingbackpack/item/FloatingBackpackItem.java`
- [x] T020 [P] [US3] （**【009 修订】已被取代**：当前为五张 1024×1024 贴图 + 法线贴图）Author five 32×32 item textures (leather brown, iron gray, gold trim, diamond cyan, netherite dark with purple edge) in `src/main/resources/assets/mcfloatingbackpack/textures/item/`
- [x] T021 [P] [US3] （**【009 修订】已被取代**：当前是五个独立的 neoforge:obj 模型，`floating_backpack.json` 已无引用）Author shared backpack element model `src/main/resources/assets/mcfloatingbackpack/models/item/floating_backpack.json` plus one child model per tier that only swaps the texture
- [x] T022 [US3] （**【009 修订】已被取代**：当前直接渲染物品烘焙模型，无肩带）Draw the same body, flap, and straps from the tier texture in `src/main/java/pnnh/mcfloatingbackpack/client/render/BackpackBackLayer.java`, replacing the unit cube

**Checkpoint**: 物品栏、手持、第三人称三者都不再是立方体。

---

## Phase 6: User Story 4 - 创造栏与既有玩法不回退 (Priority: P2)

**Goal**: 高等级面板在小窗口里每一格都能点到。丢弃、装箱、与原版容器互拖、暂停菜单隐藏保持可用。

**Independent Test**: GUI 比例调高后打开 12 行背包，滚轮能点到首行和末行。打开原版物品栏互拖数量不变。暂停菜单上没有面板。

- [x] T023 [US4] Add a client scroll offset and mouse-wheel handling when tier rows exceed the screen in `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java`, `PanelSlots.java`, and `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`
- [x] T024 [US4] Reset that scroll offset when the panel closes or the synced slot count changes in `src/main/java/pnnh/mcfloatingbackpack/client/panel/ClientBackpackView.java` and `src/main/java/pnnh/mcfloatingbackpack/network/BackpackSessionPayload.java`

**Checkpoint**: 12 行在小窗口可完整操作；005 的打开与互拖规则仍在。

---

## Phase 7: Polish

- [x] T025 [P] Note M7 task list location in `docs/plan/005-gameplay-item-tiers.md` (pointer only; do not expand scope)
- [x] T026 Run `./gradlew test` and `./gradlew compileJava`
- [ ] T027 Execute `specs/006-tiered-backpacks/quickstart.md` in `runClient` and record the result in `specs/006-tiered-backpacks/DELIVERY.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup → Foundational → US1 → US2 与 US3（都依赖五只物品已存在）→ US4 → Polish
- US2 的配方结果物品来自 US1 的注册
- US3 的贴图按 US1 的物品 ID 命名
- US4 的滚动依赖 US1 的可变行数

### User Story Dependencies

- **US1**: Foundational 之后即可做，不依赖 US2–US4
- **US2**: 依赖 US1 的四个新物品 ID
- **US3**: 依赖 US1 的物品 ID；与 US2 无代码依赖，可在 US1 之后并行
- **US4**: 依赖 US1 的高行数面板；不依赖外观是否完成

### Parallel Opportunities

- T001 ∥ T002
- T009 ∥ T010 ∥ T011
- T018 ∥ T016 完成后的其它配方文件
- T020 ∥ T021
- US2 ∥ US3 after US1

## Parallel Example: User Story 1

```text
T009 ModItems.java 注册四只新背包并放入创造栏
T010 FloatingBackpackItem.java 暴露 tier()
T011 lang/en_us.json 与 lang/zh_cn.json
```

## Implementation Strategy

1. 完成容量与协议（Foundational），皮革 36 格不回退。
2. 注册五档并让面板行数跟随物品（US1，MVP）。
3. 锻造升级保留内容（US2）。
4. 换掉立方体与皮革胸甲外观（US3）。
5. 小窗口滚动并按 quickstart 回归（US4 + Polish）。

## Notes

- 升级附加物是 1 个物品：铁块、金块、钻石、下界合金锭（见 research.md）。不要改成一次消耗 4 个锭。
- 不实现工具栏、功能升级、Curios、OP 指令。
- 皮革物品 ID `leather_floating_backpack` 保持不变。
