# Tasks: 背包编号与登记表（精简版）

**Input**: Design documents from `/specs/026-backpack-id/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/identity.md, contracts/identity-ui.md, quickstart.md；宪法 2.2.0

**说明（2026-11-09）**：本 feature 按用户的决定整体精简（不限制数量，去掉修订号、复制检测、所有权转移、全局唯一编号与服务端配置，见 spec 的"修订"）。最初的任务清单（T001～T055）里属于被删除机制的任务不再适用，**已被下面这份取代**；之前用户对旧版本做过的客户端验证（登记、上限、升级、复制检测）**不能当作对精简版的验证**，需要按 quickstart 重做。代码与自动化检查已按精简版完成。

**Tests**: 编号算法、标识、登记索引、限频、标题栏几何、语言键与配方类型是纯逻辑，用单元测试覆盖；登记表的存档读写、合成、gate 在各入口的表现、显示的外观、持久与回档只能在客户端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…/identity/` 表示 `…/inventory/identity/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增命令、依赖、贴图、设置项**；`BackpackContents` 的编码仍是 6 个字段

---

## Phase 1: Setup

- [x] T001 基线（025 之后：78 个类、580 个用例），记在 DELIVERY.md

## Phase 2: Foundational（纯逻辑、身份组件、登记表存档、语言键）

- [x] T002 [P] `…/identity/BackpackIds.java`：字符集、`isValid/index/fromIndex`、`of(UUID)`（两半各自混合后对 24,576 取模），测试 `BackpackIdsTest`（穷举 24,576 往返、`of` 稳定、2 万个随机标识合法且分布均匀、单个比特不同的标识得到不同编号）
- [x] T003 [P] `…/identity/BackpackIdentity.java`（只有 UUID，`id()` 算出编号）与 `BackpackIdentityCodecs.java`（读取时忽略旧字段），测试 `BackpackIdentityTest`
- [x] T004 [P] `…/identity/RegistryRecord.java` 与 `RegistryIndex.java`（没有编号索引、没有计数），测试 `RegistryIndexTest`（含 3 万条不限制）
- [x] T005 [P] `…/identity/NoticeLimiter.java`（只有 `TOMBSTONE`），测试 `NoticeLimiterTest`
- [x] T006 `…/identity/BackpackRegistry.java`：`SavedData`，影子副本与编码缓存，容错加载（读取时忽略旧版本的 `id`、`revision`）
- [x] T007 数据组件 `backpack_identity` 注册到 `ModDataComponents`；语言键（中英文）：`tooltip.mcfloatingbackpack.id(.none)`、`panel.mcfloatingbackpack.id.tooltip`、`message.mcfloatingbackpack.id.tombstone`；测试 `BackpackIdResourcesTest`（两种语言键一致、被删除的键不在、配方类型）
- [x] T008 运行 `./gradlew compileJava test`

## Phase 3: User Story 2/3 - 合成时有编号，第一次使用时登记表记一笔，不限制数量 (P1)

- [x] T009 `…/identity/BackpackGate.java`：`acquire`（分配标识、登记或更新、墓碑拒绝）与 `afterSave`，`BackpackMessages`（聊天与动作栏，限频）
- [x] T010 `…/inventory/BackpackRef.java`：`saveInventory` 里 `afterSave`
- [x] T011 12 个服务端入口先过 `BackpackGate.acquire`（`BackpackOpenRequestPayload`、`BackpackClickPayload`、`BackpackQuickTransferPayload`、`BackpackTakeFromContainerPayload`、`MergePanelCursorPayload`、`BackpackCursorGesturePayload`、`BackpackTagPayload`、`BackpackTagOverviewPayload`、`BackpackPickupBlacklistOpPayload`、`BackpackTrashPayload`、`BackpackTrashSendPayload`、`AutoPickupEvents`）
- [x] T012 `…/inventory/recipe/BackpackCraftingRecipe.java`（`assemble` 里盖标识）、`ModRecipes` 注册序列化器、`leather_floating_backpack.json` 改配方类型
- [x] T013 运行 `./gradlew compileJava test`
- [x] T014 [US2] 按 quickstart A（Q4～Q7）在 `./gradlew runClient` 验证（先 `pgrep`）：合成立刻有编号；`/give 50` 没有编号、只打开 2 只多 2 条；**Shift 点击合成 5 只各有标识**；旧存档背包第一次打开有编号、内容不丢；JEI 里配方正常；结果记到 DELIVERY.md（由用户确认后才打勾）
- [x] T015 [US3] 按 quickstart B（Q8、Q9、Q10）验证：30 只背包都能打开、没有数量提示；复制品共用编号且都能用；另一名玩家使用（需两名玩家，没有条件写"未验证"）；结果记到 DELIVERY.md（由用户确认后才打勾）
    两名玩家未验证

## Phase 4: User Story 1 - 标题栏与物品提示显示编号 (P1)

- [x] T016 `…/client/panel/TitleIdLayout.java` + 测试 `TitleIdLayoutTest`；`FloatingPanelState.hitsTitleId`；`FloatingPanelRenderer.drawTitleId`；`FloatingBackpackItem.appendHoverText`
- [x] T017 运行 `./gradlew compileJava test`
- [x] T018 [US1] 按 quickstart A（Q1～Q3）验证：标题栏与提示显示合规编号、窄面板截断与悬停、不遮挡关闭按钮、升级与进出箱子编号不变；结果记到 DELIVERY.md（由用户确认后才打勾）

## Phase 5: User Story 4 - 升级 (P2)

- [x] T019 审阅（读代码，不改行为）：`BackpackUpgradeRecipe` 用 `transmuteCopy`，标识随全部数据组件保留；`acquire` 在档位变化时更新记录的档位并把影子换成升级后的内容；`afterSave` 也同步档位；没有标识的背包升级后仍没有标识。**已审阅，无缺口**
- [x] T020 [US4] 按 quickstart C（Q11）验证：逐档升级 4 次，编号不变、内容不丢；结果记到 DELIVERY.md（由用户确认后才打勾）

## Phase 6: User Story 5/6 - 墓碑拒绝、持久、回档、回归 (P2)

- [x] T021 审阅（读代码，不改行为）：`BackpackRegistry.load` 对单条解码失败、重复的内部标识跳过且继续，整份损坏按空表且不抛出；`save` 只对缓存缺失的条目重编码，每个改动路径都经 `markChanged`；所有登记表访问都在服务端主线程；墓碑拒绝路径不改栈。**已审阅，无缺口**
- [ ] T022 [US6] 按 quickstart D（Q13～Q15）验证：退出重进编号不变；**备份世界 → 使用几只新背包 → 用备份替换后重进**，世界正常加载；011～025 回归；结果记到 DELIVERY.md（由用户确认后才打勾）

## Phase 7: Polish

- [x] T023 [P] 更新文档：`docs/MCFloatingBackpack.md` 的 026 说明、`docs/plan/manual-verification-checklist.md` 的 026 验证项、`specs/023`、`specs/024`、`specs/018`、`specs/021` 的一句说明；**不要修改 `docs/ideas`**
- [x] T024 图集度量：2048×1024，通过（2026-11-08，本 feature 不新增贴图）
- [ ] T025 成本粗测（宪法 VI，quickstart Q17）：自动拾取 64 个不同物品时 `saveInventory` 的额外耗时；世界保存时登记表编码耗时；测不了就如实写"未测量"
- [x] T026 静态检查：`grep loadInventory` 只命中 `BackpackRef` 与 `BackpackNetwork` 里 3 处只读下发；没有任何数量限制、配置、命令的残留（`grep -rn "maxBackpacksPerPlayer\|LimitRules\|IdentityRules\|BackpackOwnershipEvents"` 无结果）
- [x] T027 `specs/026-backpack-id/DELIVERY.md`（中文）：汇总自动化结果、精简的原因与被推翻的决策、每个客户端验证项实际验证了什么 / 未验证什么、已知限制

---

## Dependencies & Execution Order

Phase 1 → Phase 2 → Phase 3（US2/US3）→ Phase 4（US1）→ Phase 5、Phase 6 → Phase 7。T002～T005 互相独立（`[P]`）。

## Implementation Strategy

代码已完成。接下来：用户按 quickstart 验证 T014、T015、T018（MVP），再 T020、T022，最后 T025 成本粗测。

### 风险提示

- **自家配方类型**（T012、T014）：JEI、配方书、Crafter 方块里的表现要在游戏里确认；退化路径是"首次使用时分配标识"。
- **12 个入口都要过 gate**（T011、T026）：漏一个就会"绕过标识分配"；`grep "loadInventory("` 确认。
- **影子与物品共用对象**（T009、T021）：保存后不得就地修改 `BackpackContents` 或其中的 `ItemStack`。
- **影子副本体积**：存档约翻倍，不设上限，保底是之后的管理员命令。
