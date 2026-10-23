# Tasks: 精妙/旅行者背包兼容

**Input**: `/specs/004-backpack-compat/`

## Phase 1: Setup

- [x] T001 Write spec/plan/research/contracts/quickstart under `specs/004-backpack-compat/`

## Phase 2: Foundational

- [x] T002 Add `ContainerScreenCompat` soft detection in `src/main/java/pnnh/mcfloatingbackpack/compat/ContainerScreenCompat.java`
- [x] T003 [P] Unit tests for class-name markers in `src/test/java/pnnh/mcfloatingbackpack/compat/ContainerScreenCompatTest.java`

## Phase 3: US1/US2 wire-through (P1)

- [x] T004 [US1] Use compat helper for `screenOpen` in `FloatingPanelInput.java`
- [x] T005 [US1] Use compat helper for cursor draw in `FloatingPanelRenderer.java`
- [x] T006 [US2] Use compat helper for opening merge in `FloatingPanelEvents.java`
- [x] T007 [US2] Server: trust screenOpen only when menu non-null; broadcastChanges unchanged

## Phase 4: US3 optional deps + docs (P2)

- [x] T008 [US3] `build.gradle` `-PcompatRuntime` localRuntime CurseMaven coords
- [x] T009 [US3] Optional entries in `neoforge.mods.toml`
- [x] T010 [US3] Quickstart + DELIVERY known limitations

## Phase 5: Polish

- [x] T011 Run `./gradlew test compileJava`
- [x] T012 Write `specs/004-backpack-compat/DELIVERY.md`
