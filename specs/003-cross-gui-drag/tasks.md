# Tasks: 与原版物品栏/箱子互拖

**Input**: `/specs/003-cross-gui-drag/`

## Phase 1: Setup

- [x] T001 Confirm M2 click/sync path remains entry point in `BackpackClickPayload.java`

## Phase 2: Foundational

- [x] T002 Add helper to merge panel cursor into menu carried in `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackMenuBridge.java`
- [x] T003 [P] Slot hover highlight in `FloatingPanelRenderer.java`

## Phase 3: US1 物品栏互拖 (P1)

- [x] T004 [US1] When `screenOpen`, apply click against `menu.getCarried()` in `BackpackClickPayload.java`
- [x] T005 [US1] Call `broadcastChanges` after carried/backpack mutation
- [x] T006 [US1] Client draws menu carried while `AbstractContainerScreen` open in `FloatingPanelRenderer.java`

## Phase 4: US2 箱子互拖 (P2)

- [x] T007 [US2] Verify same bridge works for chest menus (no special-case; smoke via quickstart)
- [x] T008 [US2] Ensure panel click still cancels screen mouse so chest slots under panel are not hit

## Phase 5: US3 高亮与光标一致 (P3)

- [x] T009 [US3] Merge panel cursor into empty carried when container interaction begins (click or open)
- [x] T010 [US3] Confirm hover highlight only on floating slots

## Phase 6: Polish

- [x] T011 Run `./gradlew test compileJava`
- [x] T012 Write `specs/003-cross-gui-drag/DELIVERY.md`
