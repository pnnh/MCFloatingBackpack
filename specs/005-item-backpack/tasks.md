# Tasks: 背包道具化（A/C/E）

**Input**: Design documents from `/specs/005-item-backpack/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: `BackpackRef` 解析与 contents 编解码可 JUnit；门禁/胸甲/JEI 用 `runClient`（quickstart）。

**Organization**: 按用户故事分组。删 Attachment + 物品/组件注册为前置。

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

**Purpose**: 包与注册占位

- [x] T001 Create package `src/main/java/pnnh/mcfloatingbackpack/item/`
- [x] T002 [P] Create package `src/main/java/pnnh/mcfloatingbackpack/client/render/` for back layer
- [x] T003 [P] Create `src/test/java/pnnh/mcfloatingbackpack/inventory/` tests for `BackpackRef` / contents size

---

## Phase 2: Foundational

**Purpose**: Data component、物品、Ref 解析、去掉 Attachment。完成前不改门禁 UX。

- [x] T004 Implement `BackpackContents` data component (36 slots + cursor) and register `DataComponentType` from `MCFloatingBackpack.java`
- [x] T005 Refactor `BackpackInventory.java` to SIZE=36 and read/write via item component helpers (no player attachment)
- [x] T006 Implement `BackpackRef.java` resolve order MAIN_HAND → OFF_HAND → CHEST → INV 0–35
- [x] T007 [P] Unit tests for resolve order and contents round-trip in `src/test/java/pnnh/mcfloatingbackpack/inventory/`
- [x] T008 Register `FloatingBackpackItem` + `ModItems` (stack size 1, chest-equippable); creative tab entry; basic crafting recipe + lang
- [x] T009 Remove `BackpackAttachments` registration and all `player.getData(BACKPACK)` usages; delete or gut `BackpackAttachments.java`
- [x] T010 Update network payloads (`BackpackFullSync`, `BackpackClick`, `BackpackSlotUpdate`, merge cursor) to carry/validate `BackpackRef` and mutate item-bound contents
- [x] T011 Update `PanelSlots` / renderer / `ClientBackpackView` for 4×9 (36) layout

**Checkpoint**: Compiles; tests pass; no Attachment type registered.

---

## Phase 3: User Stories 1–3 - 门禁与物品库存 (Priority: P1) 🎯 MVP

**Goal**: 无背包不能开；有背包可 B / 主手右键；内容存在物品上。

**Independent Test**: quickstart 门禁 + 丢弃捡起内容保持。

- [x] T012 [US1] Gate key toggle in `FloatingPanelEvents` / keys: resolve null → deny + actionbar hint
- [x] T013 [US2] Implement main-hand right-click toggle on `FloatingBackpackItem` without setScreen
- [x] T014 [US2] On successful open, server full-sync contents for resolved ref; client binds active ref
- [x] T015 [US3] Ensure click handlers write component back to the correct hand/chest/inv slot after each op
- [x] T016 [US3] Close or reject clicks when resolved stack no longer present (REF_LOST)

**Checkpoint**: MVP playable with item-bound storage and gate.

---

## Phase 4: User Story 4 - 胸甲与背负 (Priority: P2)

**Goal**: 可装备胸甲栏；第三人称可见背模；仅胸甲时也可 B。

**Independent Test**: quickstart 胸甲与 F5。

- [x] T017 [US4] Ensure item equippable in CHEST (ArmorItem or Equippable); verify resolve includes chest
- [x] T018 [US4] Add client back render layer / event for players wearing floating backpack (placeholder mesh OK)
- [x] T019 [US4] Add item model/texture placeholders under `src/main/resources/assets/mcfloatingbackpack/`

**Checkpoint**: F5 shows back backpack; B opens chest-equipped instance.

---

## Phase 5: User Story 5 - 开发 JEI (Priority: P3)

**Goal**: runClient 有 JEI；发布无 required JEI。

**Independent Test**: JEI 能搜到物品；mods.toml 无 required jei。

- [x] T020 [US5] Add JEI via `localRuntime` in `build.gradle` (property or default for client runs); document flag in quickstart if gated
- [x] T021 [US5] Confirm `neoforge.mods.toml` does not require JEI; optional dependency only if declared at all

**Checkpoint**: Dev JEI works; cold start without JEI loads mod.

---

## Phase 6: Polish & Regression

- [x] T022 [P] Update `docs/plan/005-gameplay-item-tiers.md` with “005 已开做 A/C/E；B/D 仍延后” pointer to `specs/005-item-backpack`
- [x] T023 Run `./gradlew test` and `./gradlew compileJava`
- [x] T024 Manual `runClient` per quickstart; note status in `specs/005-item-backpack/DELIVERY.md` when done
- [x] T025 Regression: cross-GUI drag with item-bound backpack still works (003 path)

---

## Dependencies & Execution Order

- Setup → Foundational → US1–3 (MVP) → US4 → US5 → Polish
- US4 needs item equippable from Foundational
- US5 independent of US4 after item exists
- T009 must complete before treating Attachment as gone

### Parallel Opportunities

- T001∥T002∥T003; T007∥T008 after T004 API known; T018∥T019; T020∥T022

## Implementation Strategy

1. Land component + item + delete Attachment (Foundational).
2. Gate + sync + clicks (MVP).
3. Chest render + JEI.
4. quickstart verification + DELIVERY note.
