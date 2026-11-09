# Tasks: 创造物品栏界面里的 Ctrl 快速转移

**Input**: Design documents from `/specs/029-creative-quick-transfer/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md；宪法 2.2.0；011/012/014、026、027、028 已实现并提交

**Tests**: 下标换算与"何时同步"是不依赖 Minecraft 的纯逻辑，用单元测试覆盖，**测试排在实现之前**；创造界面里的实际手势、转移后的显示只能在客户端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾
- **不新增网络包、设置、贴图；不限制任何模式或界面**；011/012/014 在其它界面的代码路径与协议不变

---

## Phase 1: Setup

- [x] T001 基线：`./gradlew compileJava test`（028 之后 87 个类、629 个用例）；记到 `specs/029-creative-quick-transfer/DELIVERY.md`

## Phase 2: User Story 1/2/3 - 创造界面里的 Ctrl 快速转移 (P1) 🎯 MVP

- [x] T002 [P] 修改测试 `src/test/java/pnnh/mcfloatingbackpack/client/panel/QuickTransferRulesTest.java`（**先写，先失败**）：创造界面类名现在可拦截（原来的"不可拦截"断言改掉，空名与 null 仍不可拦截）；`creativeInventoryIndex`：物品栏标签页的菜单序号 36～44 → 下标 0～8、9～35 不变、0～8 与 45（合成、护甲、副手）→ -1、越界 → -1；物品标签页下序号 0～8 原样、其它 → -1，**且不调用换算函数**
- [x] T003 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/inventory/creative/CreativeInventorySyncTest.java`（**先写，先失败**）：`shouldSync` 只有（创造、打开自己物品栏菜单）为真
- [x] T004 `…/client/panel/QuickTransferRules.java`：`isInterceptableScreenClass` 不再排除创造界面；新增 `creativeInventoryIndex(boolean inventoryTab, int containerSlot, IntUnaryOperator menuSlotToInventoryIndex)`。使 T002 通过
- [x] T005 新建 `…/inventory/creative/CreativeInventorySync.java`：`shouldSync(creative, inventoryMenuOpen)` 与 `afterInventoryChange(ServerPlayer)`（`inventoryMenu.sendAllDataToRemote()`）。使 T003 通过
- [x] T006 `…/network/BackpackQuickTransferPayload.java`：`player.containerMenu.broadcastChanges()` 之后调 `CreativeInventorySync.afterInventoryChange(player)`
- [x] T007 `…/client/panel/QuickTransferInput.java`：`screenSlotAt` 对创造界面：自己物品栏的格子用 `creativeInventoryIndex`（物品栏标签页用 `inventoryMenu.getSlot(k).getContainerSlot()` 换算），创造物品列表的格子返回 null；更新注释
- [x] T008 运行 `./gradlew compileJava test`，确认 T002、T003 与全部既有测试通过
- [x] T009 [US1] 按 `quickstart.md` A（A1～A6）在 `./gradlew runClient` 验证（先 `pgrep`）：物品栏 → 面板，**尤其 A2（下标换算）**；结果记到 DELIVERY.md（由用户确认后才打勾）
- [x] T010 [US2] 按 `quickstart.md` B（B1～B3）验证：面板 → 物品栏，**尤其 B2（物品标签页下转进不可见格子，切到物品栏标签页看到真实内容）**；结果记到 DELIVERY.md（由用户确认后才打勾）
    功能正常
- [x] T011 [US3] 按 `quickstart.md` C（C1、C2）验证：Ctrl+拖动与选区；结果记到 DELIVERY.md（由用户确认后才打勾）

## Phase 3: User Story 4 - 守恒与回归 (P2)

- [x] T012 [US4] 按 `quickstart.md` D（D1～D3）验证：转移后搬动其它格子不覆盖；创造物品列表的 Ctrl+点击不被拦截；011/012/014 在生存界面与箱子界面回归；结果记到 DELIVERY.md（由用户确认后才打勾）

## Phase 4: Polish

- [x] T013 [P] 更新文档：`specs/011-ctrl-quick-transfer/spec.md` 的 027 补充句改为"创造界面里的 Ctrl 快速转移由 029 补上"；`specs/027-creative-inventory-compat/research.md` D8 加"已由 029 处理"；`docs/MCFloatingBackpack.md` 加 029 说明；`docs/plan/manual-verification-checklist.md` 补 029 的验证项；**不要修改 `docs/ideas`**
- [x] T014 图集度量（`quickstart.md` D4）：先 `pgrep`；本 feature 不新增贴图，应仍为 2048×1024，结果记到 DELIVERY.md；跑不了写"未度量"
- [x] T015 静态检查：`git status` 确认只改了 `QuickTransferRules`、`QuickTransferInput`、`BackpackQuickTransferPayload`、新增 `CreativeInventorySync`、两个测试类与文档；`grep -rn "isCreative" src/main/java` 命中范围是 027 的桥接与客户端类、本 feature 的 `CreativeInventorySync`；没有新增设置、网络包、贴图
- [x] T016 新建/补全 `specs/029-creative-quick-transfer/DELIVERY.md`（中文）：自动化结果、与 plan 的偏离、每个客户端验证项实际验证了什么 / 未验证什么（尤其 A2 下标换算与 B2 转移后显示）、已知限制（创造物品列表作为来源与创造界面的 Delete 键不做；Windows/Linux 未验证）

---

## Dependencies & Execution Order

Phase 1 → Phase 2（T002、T003 先写，T004 使 T002 通过，T005 使 T003 通过，T006 依赖 T005，T007 依赖 T004，T008 依赖 T002～T007，T009～T011 依赖 T008）→ Phase 3 → Phase 4。

## 风险提示

- **下标换算**（T004、T007、T009）：快捷栏在菜单序号里是 36～44，在物品栏里是 0～8；换错会把别的格子的东西转走。A2 必须验证。读源码推断，没有在游戏里验证。
- **物品标签页下转进不可见格子**（T006、T010）：客户端会忽略单格更新；整份同步是修复，B2 必须验证。
- **不拦截创造物品列表**（T007、T012）：当成容器来源会向服务端发不存在的格子。
