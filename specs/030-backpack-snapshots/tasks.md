# Tasks: 背包快照（去掉登记表）

**Input**: `/specs/030-backpack-snapshots/` 的 spec.md、plan.md、quickstart.md；宪法 2.2.0

**Tests**: 调度、保留策略、文件名、列表、选择器、编号、时间文本、语言键是纯逻辑，用单元测试覆盖；文件读写、后台线程、命令、权限、回档只能在客户端/服务端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增网络包、贴图、配方、设置项、依赖**

---

## Phase 1: Setup

- [x] T001 基线（029 提交后加上 030 旧版：92 个类、658 个用例；本 feature 完成后：93 个类、655 个用例），记在 DELIVERY.md
- [x] T002 重命名规格目录 `specs/030-registry-admin-commands` → `specs/030-backpack-snapshots`，`.specify/feature.json` 指向它；重写 spec、plan、tasks、quickstart、checklist

## Phase 2: Foundational（删除登记表、4 位编号、纯逻辑）

- [x] T003 删除登记表：`…/inventory/identity/{BackpackRegistry,RegistryRecord,RegistryIndex,NoticeLimiter,BackpackMessages}.java`、`…/admin/` 包、对应测试（`RegistryIndexTest`、`NoticeLimiterTest`、`admin/*Test`、`AdminCommandResourcesTest`）
- [x] T004 `…/inventory/identity/BackpackIds.java` 改 4 位（`LENGTH = 4`、`TOTAL = 24×32³`），更新 `BackpackIdsTest`（穷举 786,432 往返、分布、单比特差异）与 `BackpackIdentity`、`FloatingBackpackItem` 的注释/占位
- [x] T005 [P] `…/snapshot/SnapshotMeta.java`、`SnapshotPolicy.java`（保留最近 N 份）、`SnapshotFiles.java`，测试 `SnapshotPolicyTest`、`SnapshotFilesTest`
- [x] T006 [P] `…/snapshot/SnapshotScheduler.java`（泛型，静默 10 秒或最长 5 分钟到期；`drainAll`、`drainWhere`），测试 `SnapshotSchedulerTest`
- [x] T007 [P] `…/snapshot/BackpackSelector.java`（4 位编号 / UUID / UUID 开头，对 UUID 集合解析）、`AgeText.java`（自 admin 迁入），测试 `BackpackSelectorTest`、`AgeTextTest`
- [x] T008 [P] `…/snapshot/SnapshotListing.java`（按所有者/不活跃天数过滤、最近使用在前、分页、清理候选），测试 `SnapshotListingTest`
- [x] T009 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 使用后写出快照 (P1)

- [x] T010 [US1] `…/snapshot/SnapshotStore.java`：根目录 = 实例目录 + `mcfloatingbackpack/snapshots/<世界文件夹名>`；写（临时文件 + 改名）、列出每只背包的各份元数据、读内容、删一份/全部；读不了的文件跳过并记日志
- [x] T011 [US1] `…/snapshot/SnapshotService.java`：待写集合（主线程）、后台单线程、内容哈希去重、保留策略；`mark`、`tick`、`onLogout`、`flushAllAndWait`、`close`；写失败只记日志
- [x] T012 [US1] `…/inventory/identity/BackpackGate.java`：`acquire` 只分配标识并在本次运行第一次见到时 `mark`；`afterSave` → `mark`；去掉登记表与墓碑拒绝；语言键去掉 `message.mcfloatingbackpack.id.tombstone`（两种语言），更新 `BackpackIdResourcesTest`
- [x] T013 [US1] `…/MCFloatingBackpack.java`：`ServerStartingEvent` 建服务、`ServerStoppingEvent` 写完并关闭、`ServerTickEvent.Post` 每秒 `tick`、`PlayerLoggedOutEvent` 写该玩家的；去掉 030 旧的命令注册改为新命令
- [x] T014 运行 `./gradlew compileJava test`

## Phase 4: User Story 2/3/4 - 命令 (P1/P2)

- [x] T015 [US2] `…/snapshot/SnapshotCommands.java`：`snapshot list [页]`（自己的）、`info <背包>`；op：`list all|owner|inactive`；非 op 只匹配自己的背包；多个匹配时列候选
- [x] T016 [US3] `restore <背包> [版本] [玩家]`（op）：生成无标识的新背包，放回物品栏；档位与任何档位都不符时拒绝
- [x] T017 [US4] `delete <背包> [版本]`、`delete <背包> confirm`、`trim <天数> [confirm]`（op，默认只预览）
- [x] T018 [US2] 语言键（`command.mcfloatingbackpack.snapshot.*` 中英文，带英文后备）；测试 `SnapshotCommandResourcesTest`
- [x] T019 运行 `./gradlew compileJava test`

## Phase 5: 验证与收尾

- [x] T028 [US1] 快照目录名改成 `<UUID>-<编号>`（用户在验证后提出：玩家在游戏里看不到 UUID，无法对照游戏里的背包操作文件）：`SnapshotFiles.folderName/parseFolder`（只认开头的 UUID，旧形式仍认）、`SnapshotStore` 一次列目录建 UUID→目录表、旧目录下次写入时改名；测试 `SnapshotFilesTest`。**客户端验证未做**（quickstart E）

- [x] T020 [US1] 按 quickstart A 在 `./gradlew runClient` 验证快照写出、去重、保留 5 份、退出时写出（先 `pgrep`；由用户确认后才打勾）
- [x] T021 [US2/US3/US4] 按 quickstart B 验证命令、权限（非 op 玩家需第二个账号或关闭作弊；没有条件写"未验证"）、恢复为新背包（由用户确认后才打勾）
- [x] T022 [US5] 按 quickstart C 验证 4 位编号显示（由用户确认后才打勾）
- [x] T023 [US1] 按 quickstart D 验证回档后快照仍在（由用户确认后才打勾） ——**2026-11-11 AI 自动验证（容器内 runClient）：quickstart D 通过**——备份世界、用新背包、换回备份后世界正常加载，`snapshot list` 里仍有备份之后才造的背包，`restore` 取回；证据见 DELIVERY"AI 自动验证"
    未验证
- [x] T024 [P] 更新文档：`docs/MCFloatingBackpack.md`（026、030 的说明）、`docs/plan/manual-verification-checklist.md`（030 验证项）、`docs/plan/backup-admin-roadmap.md`（改写为结论）、`specs/026-backpack-id/spec.md` 的修订说明；**不要修改 `docs/ideas`**
- [x] T025 图集度量：2048×1024，通过（2026-11-09，用户关闭游戏后在本机重跑；本 feature 不新增贴图）
- [x] T026 静态检查：`grep -rn "BackpackRegistry\|RegistryRecord\|RegistryIndex\|tombstone\|NoticeLimiter"` 无残留；`grep -rn "loadInventory("` 仍只命中既有位置
- [x] T027 `specs/030-backpack-snapshots/DELIVERY.md`（中文）：自动化结果、每个验证项实际验证了什么 / 未验证什么、已知限制

---

## Dependencies & Execution Order

Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5。T005～T008 互相独立（`[P]`）。

## Implementation Strategy

MVP = Phase 2 + 3：去掉登记表、4 位编号、快照写出。命令（Phase 4）在其上。

### 风险提示

- **后台线程与主线程的边界**（T011）：后台线程只处理已编码的 NBT；命令里"等后台写完"要有超时，超时只记日志不挂起服务器。
- **内容对象不可变**（T011）：`mark` 持有的 `BackpackContents` 在编码前不得被修改（026 的约定）。
- **服务器目录 API**（T010）：`getServerDirectory()` 与 `NbtIo` 的签名以编译为准。
