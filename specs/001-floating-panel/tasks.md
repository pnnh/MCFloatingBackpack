# Tasks: 可见可拖动空面板

**Input**: Design documents from `/specs/001-floating-panel/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 计划要求坐标钳制写 JUnit（`PanelClampTest`）。玩家可见行为用 `runClient` 按 quickstart 抽查。规格未要求 TDD 全覆盖，故仅含钳制单测任务。

**Organization**: 按用户故事分组。状态与事件骨架为所有故事的前置。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- 单模块：`src/main/java/pnnh/mcfloatingbackpack/`，测试在 `src/test/java/pnnh/mcfloatingbackpack/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 补齐 M1 源码包骨架（工程已存在，无需重建 Gradle）

- [x] T001 Create `src/main/java/pnnh/mcfloatingbackpack/client/panel/` package directory for panel classes
- [x] T002 [P] Create `src/test/java/pnnh/mcfloatingbackpack/client/panel/` package directory for clamp tests

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 运行时状态、钳制纯函数、客户端事件总线挂载。完成前不交付可玩的开关/拖动故事。

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Implement `FloatingPanelState` (visible, pos, size, dragging, drag offsets) in `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java`
- [x] T004 [P] Implement screen-bound clamp helper (pure function) on or beside `FloatingPanelState` in `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java`
- [x] T005 [P] Write clamp unit tests in `src/test/java/pnnh/mcfloatingbackpack/client/panel/PanelClampTest.java`
- [x] T006 Create `FloatingPanelEvents` subscriber stub (register from client setup, no behavior yet) in `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java`
- [x] T007 Wire `FloatingPanelEvents` registration from `src/main/java/pnnh/mcfloatingbackpack/client/ClientSetup.java`

**Checkpoint**: Foundation ready — `./gradlew test` passes clamp tests; client still compiles; panel not yet user-visible.

---

## Phase 3: User Story 1 - 快捷键开关浮动空面板 (Priority: P1) 🎯 MVP

**Goal**: 进世界后按开关键显示/隐藏可辨认的空面板；控制设置中可改绑。

**Independent Test**: `runClient` 进世界，按默认键看到面板出现与消失；控制设置中有本模组开关键条目。

### Implementation for User Story 1

- [x] T008 [US1] Implement empty-panel draw (title bar + body, no item slots) in `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`
- [x] T009 [US1] Hook overlay/GUI render to draw when `visible` in `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` (must not call `setScreen`)
- [x] T010 [US1] Implement toggle on key press (in-world only; ignore chat screen; ignore main menu) in `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` using `src/main/java/pnnh/mcfloatingbackpack/client/FloatingBackpackKeys.java`
- [x] T011 [US1] Ensure world-join resets `visible` to false in `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` or `FloatingPanelState.java`
- [x] T012 [P] [US1] Confirm lang entries for key category and toggle exist in `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` and `src/main/resources/assets/mcfloatingbackpack/lang/zh_cn.json`

**Checkpoint**: User Story 1 independently testable — toggle works; no drag/persistence required yet.

---

## Phase 4: User Story 2 - 拖动面板并记住位置 (Priority: P2)

**Goal**: 标题栏拖动跟手；松开后钳制并写入本地配置；重启后恢复位置。

**Independent Test**: 拖到一角 → 隐藏再开位置不变 → 完全退出客户端后再开仍在该位置。

### Implementation for User Story 2

- [x] T013 [US2] Implement `FloatingPanelConfig` load/save of `posX`/`posY` in `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelConfig.java`
- [x] T014 [US2] Load config into state (then clamp) on client setup / game start in `src/main/java/pnnh/mcfloatingbackpack/client/ClientSetup.java` or `FloatingPanelEvents.java`
- [x] T015 [US2] Implement hit zones and drag (press dragBar / move / release → clamp → save) in `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java`
- [x] T016 [US2] Wire mouse Pre events to `FloatingPanelInput` from `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` (consume on panel; pass-through outside)

**Checkpoint**: User Stories 1 and 2 both work; position survives restart.

---

## Phase 5: User Story 3 - 打开其它界面时面板仍在 (Priority: P3)

**Goal**: 打开物品栏/箱子时面板不关；GUI 打开时快捷键仍可切换；关闭 GUI 后面板状态保持。

**Independent Test**: 先开面板再按 E / 开箱子，两套 UI 同时可见；GUI 打开时 toggle 仍有效。

### Implementation for User Story 3

- [x] T017 [US3] Ensure render path draws after/open-over container screens so inventory and panel coexist in `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` and `FloatingPanelRenderer.java`
- [x] T018 [US3] Verify toggle still fires while a non-chat Screen is open (UNIVERSAL key + input handler) in `src/main/java/pnnh/mcfloatingbackpack/client/FloatingPanelEvents.java` and `FloatingBackpackKeys.java`
- [x] T019 [US3] Ensure opening/closing vanilla inventory or chest does not clear `visible` in `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java` / event handlers
- [x] T020 [US3] Consume mouse over panel body while a container Screen is open so clicks do not fall through as item logic in `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java`

**Checkpoint**: All three user stories independently demonstrable per spec acceptance scenarios.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 边界与验收收尾

- [x] T021 [P] Document chat-screen toggle ignore behavior in `specs/001-floating-panel/quickstart.md` if not already explicit
- [x] T022 Run `./gradlew test` and `./gradlew compileJava`
- [x] T023 Manually execute client checks in `specs/001-floating-panel/quickstart.md` via `./gradlew runClient` and note any skipped steps in the delivery note

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational — MVP
- **User Story 2 (Phase 4)**: Depends on Foundational + US1 visible panel (needs something to drag)
- **User Story 3 (Phase 5)**: Depends on Foundational + US1 toggle/render; benefits from US2 input but coexistence can be validated without persistence
- **Polish (Phase 6)**: After targeted stories complete

### User Story Dependencies

- **US1 (P1)**: After Foundational only — MVP slice
- **US2 (P2)**: Needs US1 renderer/state visibility; adds config + input
- **US3 (P3)**: Needs US1 overlay (not Screen); hardens render order + input while other GUIs open

### Within Each User Story

- State/helpers before events
- Renderer before toggle demo
- Config before drag-end save
- Input wiring after hit-zone logic

### Parallel Opportunities

- T001 and T002 (different dirs)
- T004 and T005 after T003 state skeleton exists (T005 can be written against clamp API)
- T012 lang check parallel with T008–T011 once keys already registered
- T021 docs parallel with T022 compile

---

## Parallel Example: User Story 1

```bash
# After foundational checkpoint:
Task: "Implement empty-panel draw in .../FloatingPanelRenderer.java"
Task: "Confirm lang entries in en_us.json and zh_cn.json"

# Then sequentially:
Task: "Hook overlay/GUI render in FloatingPanelEvents.java"
Task: "Implement toggle on key press in FloatingPanelEvents.java"
```

## Parallel Example: User Story 2

```bash
# Can start config file while renderer exists:
Task: "Implement FloatingPanelConfig in .../FloatingPanelConfig.java"
# After config API ready:
Task: "Implement FloatingPanelInput drag/hit zones"
Task: "Wire mouse Pre events in FloatingPanelEvents.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE** with `runClient` toggle check
5. Demo empty panel before drag work

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → toggle empty panel (MVP)
3. US2 → drag + persist position
4. US3 → survive inventory/chest screens
5. Polish → tests + quickstart sign-off

### Parallel Team Strategy

With multiple developers:

1. Together: Setup + Foundational
2. Then: A owns US1 render/toggle; B prepares `FloatingPanelConfig` + clamp tests; C reviews contracts
3. Integrate US2 input, then US3 coexistence

---

## Notes

- [P] = different files, no incomplete-task dependencies
- Do **not** call `Minecraft.setScreen` to open the panel
- Do **not** add item stacks, menus, or network packets in this feature
- Commit after each task or logical group
- Stop at any checkpoint to validate the story independently
