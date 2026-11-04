# Tasks: 服务端库存与面板内取放

> **【005 修订】** 这些任务描述的是 Attachment 版库存，已被 005 取代（见 `spec.md` 顶部说明）；任务保留作历史。

**Input**: Design documents from `/specs/002-server-inventory/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 计划要求 `BackpackClickLogic` 写 JUnit。存盘与点击用 `runClient` 抽查。

**Organization**: 按用户故事分组。Attachment + 网络注册为前置。

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

**Purpose**: 包目录与注册钩子占位

- [x] T001 Create packages `src/main/java/pnnh/mcfloatingbackpack/inventory/` and `src/main/java/pnnh/mcfloatingbackpack/network/`
- [x] T002 [P] Create `src/test/java/pnnh/mcfloatingbackpack/inventory/` for click-logic tests

---

## Phase 2: Foundational

**Purpose**: 库存模型、Attachment、网络注册。完成前不做面板取放 UI。

- [x] T003 Implement 27-slot backpack + panel cursor container in `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackInventory.java`
- [x] T004 Register persistent player Attachment in `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackAttachments.java` and wire from `src/main/java/pnnh/mcfloatingbackpack/MCFloatingBackpack.java`
- [x] T005 [P] Implement pick/place/swap/merge in `src/main/java/pnnh/mcfloatingbackpack/inventory/BackpackClickLogic.java`
- [x] T006 [P] Write unit tests in `src/test/java/pnnh/mcfloatingbackpack/inventory/BackpackClickLogicTest.java`
- [x] T007 Add `BackpackFullSyncPayload`, `BackpackSlotUpdatePayload`, `BackpackClickPayload` in `src/main/java/pnnh/mcfloatingbackpack/network/` and register codecs/handlers from `MCFloatingBackpack.java`

**Checkpoint**: Attachments + payloads compile; click logic tests pass.

---

## Phase 3: User Story 1 - 真实物品并跨会话保留 (Priority: P1) 🎯 MVP

**Goal**: 客户端镜像库存，登录全量同步，面板绘制真实物品。

**Independent Test**: 服务端写入物品后重进，面板显示一致。

- [x] T008 [US1] Implement `ClientBackpackView` in `src/main/java/pnnh/mcfloatingbackpack/client/panel/ClientBackpackView.java`
- [x] T009 [US1] Handle full sync / slot update on client in network handlers; apply to `ClientBackpackView`
- [x] T010 [US1] Send `BackpackFullSyncPayload` on player login in server event subscriber under `src/main/java/pnnh/mcfloatingbackpack/`
- [x] T011 [US1] Render synced stacks in slots via `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`

**Checkpoint**: Synced items visible; survive relog once items exist on server.

---

## Phase 4: User Story 2 - 面板内点击取放 (Priority: P2)

**Goal**: 左键槽位经服务端点击逻辑更新并回推。

**Independent Test**: 拾取、放下、交换、堆叠后重开面板仍正确。

- [x] T012 [US2] Map slot hit-testing and send `BackpackClickPayload` from `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelInput.java` when panel visible
- [x] T013 [US2] Server handler applies `BackpackClickLogic`, saves attachment, replies `BackpackSlotUpdatePayload`
- [x] T014 [US2] Draw panel cursor stack at mouse in `FloatingPanelRenderer.java` / `FloatingPanelEvents.java`
- [x] T015 [US2] Ignore clicks when panel hidden; require `panelOpen` on payload

**Checkpoint**: Panel-only cursor take/place works.

---

## Phase 5: User Story 3 - 无容器界面主手存取 (Priority: P3)

**Goal**: 无容器 Screen 时 Shift+左键主手 ↔ 槽。

**Independent Test**: 不按 E，Shift 存入与取出各一次。

- [x] T016 [US3] Server Shift path swaps/moves main hand ↔ slot when no container menu in click handler
- [x] T017 [US3] Client sends `shiftDown` and `screenOpen` from `FloatingPanelInput.java`; disable Shift main-hand path while container Screen open
- [x] T018 [US3] Sync player inventory side-effects (main hand) after Shift ops (vanilla sync / force inventory update as needed)

**Checkpoint**: Usable backpack without opening inventory GUI.

---

## Phase 6: Polish

- [x] T019 [P] Update `specs/002-server-inventory/quickstart.md` with any final key/behavior notes
- [x] T020 Run `./gradlew test` and `./gradlew compileJava`
- [x] T021 Note manual `runClient` verification status in `specs/002-server-inventory/DELIVERY.md`

---

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → US3 → Polish
- US1 is MVP (storage + sync + render)
- US2 needs US1 view + payloads
- US3 extends US2 click handler

### Parallel Opportunities

- T001∥T002; T005∥T006; T008 can start after T003 API known; T019∥T020

## Implementation Strategy

1. Foundational + US1 → see real items after debug fill/login sync  
2. US2 → panel cursor clicks  
3. US3 → Shift main-hand path  
4. Polish / delivery note  
