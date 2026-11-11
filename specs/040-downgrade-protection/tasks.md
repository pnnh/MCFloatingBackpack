# Tasks: 降级保护

**Input**: `/specs/040-downgrade-protection/` 的 spec.md、plan.md、research.md、data-model.md、contracts/（protection.md、texts.md）、quickstart.md；宪法 2.2.1

**Tests**: 决策表、确认窗口、提示文字选择用 JUnit 覆盖（纯逻辑，时间由调用方传入）；语言键与接线用文本检查覆盖。**面板被拒、两步确认、自动拾取跳过、锻造台不匹配、以及真实降级实验只能在客户端会话里做**（宪法 V）：编译与单元测试通过不等于保护在游戏里成立。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`，`RES/` 表示 `src/main/resources/`；**不包含提交 Git 的任务**（等用户要求再提交；**也不打 tag**）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，已有就不启动第二个；不运行专用服务器、不创建 `eula.txt`；改动世界存档前先备份；跑完 Gradle 后 `./gradlew --stop`**
- 勾选规则：客户端验证与降级实验任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增**配置项、命令；**不改协议号 17、现有网络包与 `BackpackContents.STREAM_CODEC`**；不改快照；不修改 `docs/ideas`
- 用户故事：US1 被拒并提示；US2 两步确认；US3 其它路径同样保护；US4 正常情形不受影响；US5 日志、文档与约定

---

## Phase 1: Setup

- [x] T001 基线：`./gradlew test` 记下当前类数与用例数（上次 128 个类、917 个用例）；`git status` 确认工作区只有 040 的文档、`.gitignore` 修复与未提交的 `snapshot/net/` 源码；记在 DELIVERY.md

## Phase 2: Foundational（纯逻辑，不碰游戏对象）

- [x] T002 [P] `…/inventory/revision/DataRevision.java`：`CURRENT = 1`（注释说明含义与何时递增，指向 `docs/plan/release-process.md`）、`isNewer(int stored)`（`stored > CURRENT`）、`readStored(Integer)`（null 与负数读作 0）。`…test/inventory/revision/DataRevisionTest.java`：`isNewer` 在 `stored` 为 0、1（相等）、2、99 与负数时的结果；`CURRENT` 现在是 1
- [x] T003 [P] `…/inventory/revision/RevisionGuard.java`：`Decision`（`ALLOW`、`REFUSE_FIRST`、`CONFIRMED`、`REFUSE`）、`decide(stored, current, explicitOpen, windowOpen)`，按 data-model.md 的决策表。`…test/inventory/revision/RevisionGuardTest.java`：四行决策表全覆盖；`stored == current` 与 `stored < current` 为 `ALLOW`；`explicitOpen = false` 且窗口开着仍是 `REFUSE`（**自动拾取等路径不能确认**）；负数与 0 不被保护
- [x] T004 [P] `…/inventory/revision/ProtectionWindows.java`：`WINDOW_MS = 60_000`；`open(playerId, backpackKey, nowMs)`、`isOpen(…, nowMs)`、`consume(playerId, backpackKey)`、`forgetPlayer(playerId)`；另有限频表 `shouldLogPickupSkip(playerId, nowMs)`（每位玩家每 60 秒至多一次为真）；时间由调用方传入。`…test/inventory/revision/ProtectionWindowsTest.java`：60 秒内有效、第 60 秒起无效、`consume` 后无效、别的玩家与别的背包借不到、同一个玩家两只背包各自独立、`forgetPlayer` 清除该玩家的全部窗口、限频表的间隔
- [x] T005 [P] 语言键 `RES/assets/mcfloatingbackpack/lang/zh_cn.json` 与 `en_us.json`：`message.mcfloatingbackpack.revision.protected`、`message.mcfloatingbackpack.revision.accepted`（文字与参数见 contracts/texts.md）；两个文件键集合保持一致
- [x] T006 运行 `./gradlew compileJava test`

**Checkpoint**: 纯逻辑就绪，现有测试通过

## Phase 3: User Story 4 + 存储 - 修订的存储与保存盖章 (Priority: P1)

先做存储：它是其它一切的前提，并且必须保证"旧存档照常、往返不变"。

**Independent Test**: 旧背包（无字段）读作 0；保存后盖当前修订；`STREAM_CODEC` 不变。

- [x] T007 [US4] `…/inventory/BackpackContents.java`：加第 8 个记录组件 `int dataRevision`；`CODEC` 里 `Codec.INT.optionalFieldOf("data_revision", 0)`（总是写出）；**`STREAM_CODEC` 一行不动**（解码时 `dataRevision` 读作 0，编码时丢弃）；提供一个 7 参数的便利构造器（修订 0）让现有调用点照常编译；`empty`、`fit` 等工厂方法沿用传入的修订或 0；加 `withDataRevision(int)`；注释说明为什么不带进 `STREAM_CODEC`（协议号 17）与为什么不用单独的数据组件（旧版本会整只背包读成空）
- [x] T008 [US4] `…/inventory/BackpackInventory.java`：`fromContents` 保留栈上的修订（读入时记下，便于判断）；`toContents` 盖上 `DataRevision.CURRENT`（`VersionDebug.stampRevisionOverride()` 存在时用它）；不改其它逻辑
- [x] T009 [P] `…/version/VersionDebug.java`：加 `stampRevisionOverride()`（`mcfloatingbackpack.debug.stampRevision`，整数，缺省 `null`）；注释写明用法 `-Pmcfb.stampRevision=99`（`build.gradle` 已有 `-Pmcfb.*` 转发，不用改）
- [x] T010 [US4] 测试：`…test/inventory/BackpackContentsRevisionTest.java`（文本/纯逻辑，不加载 Minecraft 注册表）：源码里 `data_revision` 用 `optionalFieldOf` 且缺省 0；`STREAM_CODEC` 的编码与解码段没有出现 `dataRevision`（文本检查，防止误改）；`BackpackInventory` 的 `toContents` 引用 `DataRevision.CURRENT` 与 `stampRevisionOverride`
- [x] T011 运行 `./gradlew compileJava test`

## Phase 4: User Story 1 + 2 + 3 - 保护、两步确认与其它路径 (Priority: P1) 🎯 MVP

**Independent Test**: 用调试开关在一个客户端里验证（quickstart A～I）。

- [x] T012 [US1][US3] `…/inventory/identity/BackpackGate.java`：新增私有的只读 `storedRevision(ItemStack)`（`stack.get(BACKPACK_CONTENTS)` 的 `dataRevision`，没有组件读 0）；`acquire` 与 `acquireForPanel` **在分配身份之前**检查：`DataRevision.isNewer(stored)` → `RevisionGuard.decide(stored, CURRENT, false, false)` 为 `REFUSE` → 静默返回空（**不分配身份、不写回栈、不通知快照服务**）
- [x] T013 [US1][US2] `BackpackGate.acquireForOpen(ServerPlayer, BackpackRef)`：被保护时 `RevisionGuard.decide(stored, CURRENT, true, ProtectionWindows.isOpen(player, key, now))`：`REFUSE_FIRST` → `ProtectionWindows.open`、聊天提示（`revision.protected`，参数背包修订与当前修订）、WARN 日志、返回空；`CONFIRMED` → `ProtectionWindows.consume`、**立即把栈上的修订改成当前**（`ref.setStack(player, 栈的副本，BACKPACK_CONTENTS 的 dataRevision = CURRENT)`，内容不变）、聊天 `revision.accepted`、WARN 日志，再走正常的 `acquire` 流程；背包键 = 有身份时身份 UUID，否则 `"ref:<来源>:<下标>:<修订>"`；时间用服务器时钟（`System.currentTimeMillis()` 或 `server.getTickCount()` 换算，选一个并写清）；`BackpackGate.forget(player)` 里加 `ProtectionWindows.forgetPlayer`
- [x] T014 [US2] `…/network/BackpackOpenRequestPayload.java`：`wantOpen` 的分支里把 `BackpackGate.acquire(serverPlayer, resolved)` 改成 `BackpackGate.acquireForOpen(serverPlayer, resolved)`；被拒时沿用现有的 `sendSession(false)`（"没有可打开的背包"的提示不再弹：被保护的拒绝已经有自己的聊天提示，要避免重复提示——查看 `message.mcfloatingbackpack.need_backpack` 的分支不受影响）；其它 `acquireForPanel` 的调用点不改
- [x] T015 [US3] `…/inventory/pickup/AutoPickupEvents.java`：`acquire` 返回空时，如果原因是被保护，则按 `ProtectionWindows.shouldLogPickupSkip` 限频写一条 WARN（contracts/texts.md 的格式）；不发聊天、不改变"返回空就跳过"的既有逻辑（实现时让 `BackpackGate` 提供 `isProtected(ItemStack)` 供它判断原因）
- [x] T016 [US3] `…/inventory/recipe/BackpackUpgradeRecipe.java`：`matches` 在三个材料都匹配之后加一条：基础背包的 `BACKPACK_CONTENTS` 修订不大于 `DataRevision.CURRENT`（只读栈，不改它）；`assemble` 不改
- [x] T017 运行 `./gradlew compileJava test`

**Checkpoint**: 保护与两步确认完成；**此时可以在一个客户端里用调试开关验证**（quickstart 第一部分）

## Phase 5: 收尾（自动化）与文档 (User Story 5)

- [x] T018 [P] `…test/resources/DowngradeProtectionResourcesTest.java`（文本检查）：两个语言文件都有 `revision.protected` 与 `revision.accepted` 且键集合一致，含 `%1$s`、`%2$s` 与"60"；`BackpackGate.java` 里读栈修订的判断出现在分配身份（`new BackpackIdentity(`）**之前**；`acquireForOpen` 只被 `BackpackOpenRequestPayload` 调用（`grep` 其它文件没有）；`acquire`/`acquireForPanel` 里没有 `ProtectionWindows.consume`；`BackpackUpgradeRecipe.matches` 引用 `DataRevision`；`BackpackContents.java` 的 `STREAM_CODEC` 没有 `dataRevision`；协议号仍是 `versioned("17")` 且版本检查的 `versioned("1")` 未变；没有新增配置项与命令
- [x] T019 静态检查：`git diff` 里 `BackpackContents.STREAM_CODEC`、现有网络包（除 `BackpackOpenRequestPayload` 的一处调用）、快照服务没有改动；`DependencyRulesTest` 通过
- [x] T020 全量 `./gradlew compileJava test`，记下类数与用例数；`./gradlew --stop`
- [x] T021 [P] `design/demo/tools/downgrade_experiment.sh`（辅助脚本，**不在脚本里启动游戏、不改用户的世界**）：参数 `<世界目录> [旧提交，默认 e1207ea（030：031～034 编译不过）]`；做三件事：(1) 检查 `pgrep -f "fml.modFolders=mcfloatingbackpack"` 没有客户端在跑；(2) 把世界**复制**到 `~/Desktop/<世界名>-backup-<时间>`（只复制，已存在则拒绝覆盖）；(3) `git worktree add ../MCFloatingBackpack-old <旧提交>`（已存在则提示），把世界副本放进它的 `run/saves/`，然后**打印**后续步骤（`cd ../MCFloatingBackpack-old && ./gradlew runClient`，要观察什么、退出后怎样对比），仅此而已；脚本头部写明用途与风险
- [x] T022 [P] 文档：`docs/plan/release-process.md`：新增"何时递增 `DATA_REVISION`"一节（规则见 spec：新增存储字段、字段含义改变、新增只能存在背包里的物品种类才递增；纯规则或界面变化与修缺陷不递增）、"每个改变背包存储数据的 feature 在 DELIVERY.md 里写明是否递增"、本版本把它定为 1、保护只对带标记的版本有效；`README.md` 与 `README_CN.md` 的"多人游戏"之后加一小节或一句"升级与降级"：降级前先备份存档，被更新版本保存的背包在旧版本里会先被保护并提示（保护从第一个带标记的版本开始）；`docs/MCFloatingBackpack.md` 加 040 一句；`docs/plan/manual-verification-checklist.md` 加 040 验证项（见 quickstart）；**不要修改 `docs/ideas`**
- [x] T023 `specs/040-downgrade-protection/DELIVERY.md`：已验证 / 未验证（保护在游戏里的行为、降级实验 H1/H2/H3 的结论）/ 已知局限（见 research D10：创造模式写回洗掉修订、客户端看不出背包被保护、保护的是"别保存"不是"别读取"、解码层面整只背包读成空不在范围内）/ 基线与完成后的类数与用例数 / `DATA_REVISION` = 1 与"是否递增"的说明 / 对规格的说明（修订放在记录里而不是单独的组件、确认时立即盖章、自动拾取只写限频日志）

## Phase 6: 验证与实验（客户端验证与实验由用户确认）

- [ ] T024 [US1][US2][US3] 按 quickstart 第一部分（A～I）用调试开关在一个客户端里验证：被拒并提示、背包零变化、60 秒内再次打开放行、过期重新拒绝、别的背包借不到、自动拾取跳过且不刷屏、锻造台不匹配、不读写内容的操作不受限（先 `pgrep`；改世界前先备份；由用户确认后才打勾） ——**2026-11-11 AI 自动验证（容器内 runClient）：A、B、C、D、E、F、G、H 通过；I 只做了"丢弃再捡起"（放进箱子再取出、创造复制未做）；证据见 DELIVERY"AI 自动验证"一节；I 没做全，所以未勾** ——2026-11-11 第二轮：两个玩家的按玩家放行（E 的多人版）通过，详见 DELIVERY；I 剩余项仍未做，未勾
- [ ] T025 [US4] 按 quickstart 第二部分（J～M）回归：无标记的旧背包、修订相同或更低的背包、035～039 的功能、协议号 17；创造模式写回的局限（记录结果）（由用户确认后才打勾） ——2026-11-11 AI 只顺带观察到 M（创造物品栏界面里操作会把背包的 data_revision 写回覆盖，已知局限，见 DELIVERY）；J、K、L 没有系统验证，未勾
- [ ] T026 **降级实验**（quickstart 第三部分；用 T021 的脚本准备；**由用户做**）：用 030 的构建在备份过的世界副本上观察 H1/H2/H3，把观察逐条写进 research.md 的"实验结果"一节；**如果 H3 成立，建议另开 041**（由用户确认后才打勾；没做就写"未验证"）——**状态：实验已做，结果与分析已写入 research.md（H1、H2 成立，H3 未出现；另建议加一个"不点格子直接退出"的对照）；等用户确认后打勾**

---

## Dependencies & Execution Order

- Phase 1 → Phase 2（T002～T005 互相独立，可并行；T006 汇总）→ Phase 3 → Phase 4 → Phase 5 → Phase 6。
- Phase 3 内：T007 → T008；T009 独立；T010 依赖 T007、T008、T009。
- Phase 4 内：T012 依赖 T003、T004、T007；T013 依赖 T012、T005；T014、T015、T016 依赖 T012、T013（T016 只依赖 T002、T007）；T017 最后。
- T018～T023 在全部实现之后；T021、T022 可提前准备；T023 要等实验结论（没有就写未验证）。
- **降级实验（T026）与实现互不阻塞**：可以在实现之前或之后做；但它的结论决定 040 的价值范围与是否开 041，建议尽早做。

## Parallel Opportunities

- T002、T003、T004、T005；T009；T018、T021、T022。

## Implementation Strategy

先纯逻辑（Phase 2），让决策表与窗口有测试保护；再做存储（Phase 3）并保证旧存档与 `STREAM_CODEC` 不变；然后统一入口里的保护（Phase 4）。MVP 是 Phase 2～4：有了它，用 `-Pmcfb.stampRevision=99` 在一个客户端里就能验证主要行为。最不确定的是真实降级的后果（T026），它只能由用户在备份过的世界副本上做，结论决定保护的有效范围与是否另开 041；其余都能在一个客户端里验证。
