# Tasks: 统一的"背着的背包"查找

**Input**: `/specs/032-worn-backpack/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法 2.2.1

**Tests**: 顺序规则（来源排序、跳过不生效、第一只、越界、隐藏渲染、手里优先、物品栏下标最小）是纯逻辑，用单元测试覆盖；`WornBackpacks`、渲染层与面板各功能的不回归只能在客户端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增网络包、协议改动、语言键、贴图、配方、设置项、依赖**；玩家可见行为与 031 之后完全一致

---

## Phase 1: Setup

- [x] T001 基线（031 提交后：101 个类、711 个用例；完成后 102 个类、729 个用例），记在 DELIVERY.md

## Phase 2: Foundational（纯逻辑核心）

- [x] T002 [P] `…/inventory/worn/WornSource.java`（来源接口：`id`、`order`、`active`、`slotCount`、`get`、`set`、`hiddenRender` 默认假；不引用 Minecraft 类）
- [x] T003 `…/inventory/worn/BackpackSearch.java`（泛型 `BackpackSearch<P, S>`：`Probe`、`Hit`/`Kind`、`register` 按 `order` 插入、`firstWorn`、`resolve`；越界读到"空"由来源负责；手里优先；物品栏取下标最小的）与测试 `…/inventory/worn/BackpackSearchTest.java`（假来源与假玩家：排序、跳过不生效、第一只、都没有、隐藏渲染仍是第一只、手里优先、副手其次、物品栏下标最小、同一个列表对两个不同的假玩家给出各自正确的结果、`order` 与 `id` 无关）
- [x] T004 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 + 2 + 3 - 接入并保持行为不变 (P1)

- [x] T005 [US1] `…/inventory/worn/ChestWornSource.java` 与 `WornBackpacks.java`：单例 `BackpackSearch<Player, ItemStack>`，胸甲来源（`id = Source.CHEST.ordinal()`、`order = 0`、`slotCount = 1`、`get/set` 读写 `EquipmentSlot.CHEST`，越界返回 `EMPTY`，`active = true`）；`first(LivingEntity)` 返回 `Found(stack, hidden)` 或空；`resolve(Player)`；`byId(int)`；`register(...)`
- [x] T006 [US1] `…/inventory/BackpackRef.java`：`Source` 常量加 `worn` 标志与 `isWorn()`（`CHEST` 为真，序号不变）；`resolve` 改用 `WornBackpacks.resolve`（胸甲引用的下标从 `-1` 变成 `0`）；`getStack/setStack/stillValid` 对 `worn` 来源转给 `WornBackpacks.byId`（来源找不到 → `EMPTY`/假）；修正 `saveInventory` 里提到登记表的过时注释；更新 `BackpackRefTest`（胸甲引用下标为 0、`worn` 标志、`Source` 序号不变）
- [x] T007 [US1] `…/client/render/BackpackBackLayer.java`：改用 `WornBackpacks.first(player)`：空或 `hidden` 为真不画，否则画那一只的栈（其余姿态、缩放、`renderStatic` 调用保持不变）
- [x] T008 [US1] `…/network/BackpackQuickTransferPayload.java`：`holderOf` 对 `worn` 来源返回 `Holder.CHEST`
- [x] T009 [US3] 静态检查：`grep -rn "EquipmentSlot.CHEST"` 在 `…/` 下只剩 `ChestWornSource`、`FloatingBackpackItem`（`Equipable`）；`grep -rn "BackpackRef.resolve"` 的调用点没有变化；协议版本仍是 `"16"`；没有新增语言键
- [x] T010 运行 `./gradlew compileJava test`

## Phase 4: 验证与收尾

- [x] T011 [US1] 按 quickstart A、B 在 `./gradlew runClient`（用 2026-11-09 之后新建的开发世界；先 `pgrep`）对照 8 个场景与面板各功能不回归；由用户确认后才打勾。用户 2026-11-09 验证：功能没有问题
- [x] T012 [P] 更新文档：`docs/MCFloatingBackpack.md`（032 一句说明）、`docs/plan/manual-verification-checklist.md`（032 验证项）、`docs/plan/worn-backpack-compat.md`（032 已拆出、状态）；**不要修改 `docs/ideas`**
- [x] T013 图集度量：`design/demo/tools/check_atlas_budget.sh`（先 `pgrep`；本 feature 不新增贴图，应仍是 2048×1024；若报"找不到主显示器"，是 Gradle 守护进程没有图形会话，写"未测量"并说明）
- [x] T014 `specs/032-worn-backpack/DELIVERY.md`（中文）：自动化结果、验证项实际验证了什么 / 未验证什么、已知限制（`modLevels` 在专用服务器上不生效的预告）

---

## Dependencies & Execution Order

Phase 1 → Phase 2 → Phase 3 → Phase 4。T002 先于 T003；T005 依赖 T002、T003；T006、T007、T008 依赖 T005，彼此独立；T011 依赖 T006～T008。

## Implementation Strategy

MVP = Phase 2 + 3（整个 feature 就这么大）：纯逻辑核心 + 接入 + 不回归。先有测试再接入，接入后跑全部测试，再由用户在游戏里对照行为。

### 风险提示

- **胸甲引用从 `-1` 变成 `0`**（T006）：quickstart A6（打开面板后摘下胸甲背包）要实际操作。
- **渲染层是每帧热路径**（T007）：不要在每帧里分配列表。
- **`modLevels` 在专用服务器上不生效**（plan Risks）：这是 033 要明确写出的限制，本 feature 只在文档里预告。
