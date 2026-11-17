# Tasks: 背包快照保留策略调整

**Input**: `/specs/057-snapshot-retention/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 纯逻辑（保留与淘汰、定时窗口、版本变化计划、状态文件、容量夹取、节流、文件名、编解码、请求规则）用 JUnit；语言键、配置注释、接线用资源/源码文本检查。**单元测试类路径里没有 Minecraft 类，文件级的钉住、窗口、登录提示、容量淘汰只能在客户端/服务端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`（宿主上有游戏在跑就等用户退出，不要绕过 `launch.sh` 的检查）；容器里 `-PnoJei`；改存档前先备份世界；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增依赖、mixin、反射；**协议号 20 → 21**；**`DataRevision` 仍为 6**；快照仍在世界之外
- 用户故事：US1 找回十几个小时内的内容（三类、定时窗口、去重、保留）；US2 版本变化时钉住旧版本写的最新快照；US3 提醒；US4 容量上限与总开关；US5 窗口与命令（分页、类别、序号）；US6 文件格式版本与读取规则

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 207 个类、1591 个用例，0 失败）；记在 `specs/057-snapshot-retention/DELIVERY.md`（新建，中文）

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T002 [P] `…/snapshot/SnapshotKind.java`（TIMED=1/CHANGE=2/UPGRADE=3，后缀 `-t`/``/`-u`，保留数 60/20/5，`fromByte`、`fromSuffix`、`protectedKind()`）+ `…test/snapshot/SnapshotKindTest.java`
- [X] T003 [P] `…/snapshot/SnapshotFiles.java`：文件名带后缀（`fileName(time, kind)`、`parseTime` 认 `<digits>(-t|-u)?.dat`、`kindOf(name)`，不认识的返回 -1）；改 `…test/snapshot/SnapshotFilesTest.java`
- [X] T004 `…/snapshot/SnapshotMeta.java`：加 `kind`、`fromVersion`、`toVersion` 与便利构造 `of(...)`（类别 CHANGE、版本为空）；同步改用到旧 6 字段构造的地方与测试
- [X] T005 [P] `…/snapshot/SnapshotRetention.java`（按类别选出要删的）+ `…test/snapshot/SnapshotRetentionTest.java`（60/20/5、每类独立、只动指定类别、条目不足不删、乱序输入）；`SnapshotPolicy` 与 `SnapshotPolicyTest` 并入或改写（`KEEP = 5` 不再存在）
- [X] T006 [P] `…/snapshot/SnapshotCapacity.java`（淘汰顺序：定时先于变更、同类最旧先；受保护 = 升级前或该背包最新一份；返回要删的列表与 `stillOver`）+ `…test/snapshot/SnapshotCapacityTest.java`
- [X] T007 [P] `…/snapshot/TimedWindow.java`（`mark`、`due(now)`：到点取走全部脏的并重置 `nextAt`、替换式、间隔可配）+ `…test/snapshot/TimedWindowTest.java`
- [X] T008 [P] `…/snapshot/SnapshotState.java`（`lastModVersion`、`changeId`、`fromVersion`、`pinned`、`notified`；文本往返；缺失/损坏/坏 UUID 容错；名单上限 10000 丢最早）+ `…test/snapshot/SnapshotStateTest.java`
- [X] T009 [P] `…/snapshot/UpgradePlan.java`（每只背包：Pin/UpdateTo/Drop/Skip）+ `…test/snapshot/UpgradePlanTest.java`（最新一份已是升级前、第 6 份挤掉最旧、没有快照、只有升级前）
- [X] T010 [P] `…/snapshot/SnapshotSettings.java`（`from(enabled, mb, debugSeconds)`：夹 128～4096 并记 `clamped`、默认间隔 900 秒、调试秒数 < 1 当默认）+ `…test/snapshot/SnapshotSettingsTest.java`
- [X] T011 [P] `…/snapshot/Throttle.java`（每小时最多一次）+ `…test/snapshot/ThrottleTest.java`
- [X] T012 [P] `…/inventory/LostCells.java`（线程本地计数：`add`、`take`、`reset`）+ `…test/inventory/LostCellsTest.java`（含"另一个线程的计数不串"）
- [X] T013 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 三类快照、定时窗口、去重、保留 (Priority: P1) 🎯 MVP

- [X] T014 [US1] `…/snapshot/SnapshotStore.java`：`write(meta, tag)` 按类别写文件（`<time><后缀>.dat`、头里写 `kind` 与 `version` 2）、写完只对该背包该类别做保留清理；列出时读头得到类别（缺失按 CHANGE，与文件名不一致以文件名为准）；`versions(uuid)` 带类别；`times(dir)` 用 `kindOf`
- [X] T015 [US1] `…/version/VersionDebug.java`：`timedSnapshotSeconds()`（读 `mcfloatingbackpack.debug.timedSnapshotSeconds`，非数字或 < 1 返回 null）；更新类注释；改 `…test/version/VersionDebugTest.java`
- [X] T016 [US1] `…/snapshot/SnapshotService.java`：`mark` 同时喂 `scheduler`（变更）与 `TimedWindow`（定时）；`tick()` 里再取定时到点的脏背包写 TIMED；`lastHash` 键改为 (UUID, 类别)，首次从磁盘读该类别最新一份初始化；`forget` 清该背包所有类别；`noteUse` 的基线仍只走变更路径
- [X] T017 [US1] 接线测试 `…test/resources/SnapshotRetentionWiringTest.java`（源码文本：两条写路径、(UUID,类别) 去重、保留清理只对该类别、定时间隔来自 `SnapshotSettings`）
- [X] T018 运行 `./gradlew compileJava test`

**Checkpoint**: 三类快照可写可留，等游戏内验证

## Phase 4: User Story 2 - 版本变化时钉住旧版本写的最新快照 (Priority: P1)

- [X] T019 [US2] `…/version/ModVersion.java`（`current()`，从 `WorldMarkerEvents.modVersion()` 提出；`WorldMarkerEvents` 改调它，行为不变）
- [X] T020 [US2] `…/snapshot/SnapshotStore.java`：`readState()`/`writeState()`（`state.properties`，临时文件 + 原子改名，只在主线程用）；`pin(uuid, time, from, to)`（读压缩 NBT → 只改 `kind`/`from_mod_version`/`to_mod_version`/`version` → 写 `-u` 文件 → **重读比较 `contents` 相等才删旧文件，不等回滚并记 ERROR**）、`updateUpgradeTarget(...)`（同样先写后校验）、`backpacks()` 已有
- [X] T021 [US2] `…/snapshot/SnapshotService.java`：`start` 里读设置与状态 → 比较版本（`strip` 后字符串不等即变化，升级降级都算）→ `UpgradePlan` → `store.pin` → 写新状态（`changeId + 1`、旧版本、`pinned`、名单清空）；状态缺失/损坏只写入当前版本（`changeId = 1`）不钉住；`enabled = false` 时只更新状态
- [X] T022 [US2] 接线测试 `…test/resources/SnapshotUpgradeWiringTest.java`（源码文本：先写后校验再删旧的顺序、不经 `BackpackContents.CODEC`、状态文件原子写、`ServerStartingEvent` 里启动）
- [X] T023 运行 `./gradlew compileJava test`

## Phase 5: User Story 3 - 提醒 (Priority: P1)

- [X] T024 [US3] `…/inventory/LenientStacks.java`：回调里再 `LostCells.add(n)`；`…test/resources/LenientStacksWiringTest.java` 补一条
- [X] T025 [US3] `…/snapshot/SnapshotNotices.java`：`Pending(changeId, from, to, pinned, direction)`（本次运行检测到的，或重启后 `changeId > 0` 时从状态重建）、`onLogin(player)`（不在名单才发、发后记名单并写状态）、丢失提示（`Map<UUID,Integer>`）、文案选择（升级/降级/中性/备份已关闭）
- [X] T026 [US3] `…/MCFloatingBackpack.java`：监听 `PlayerEvent.LoadFromFile`（把玩家文件里的背包再解码一次并计数，见 research R10）、登录里调 `SnapshotNotices.onLogin`、登出里清理
- [X] T027 [US3] `…/snapshot/SnapshotOps.java`：`restore` 改返回 `RestoreResult(ResultCode, lostCells)`（解码前 `reset`、解码后 `take`）；`…/snapshot/SnapshotServer.java`：`RESTORED` 的 `n1 = lostCells`；`…/client/snapshot/SnapshotResultText.java`：`RESTORED` 且 `n1 > 0` 用 `restored_lost` 键；`…/snapshot/SnapshotCommands.java`：`restore.lost`
- [X] T028 [US3] 语言键（`contracts/config-and-texts.md` 的聊天提示与 `restored_lost`、`restore.lost`）中英文；资源测试 `…test/resources/SnapshotRetentionResourcesTest.java`（两份语言文件键集合相同、占位符个数与 `SnapshotResultText.placeholders` 一致）
- [X] T029 [US3] 接线测试 `…test/resources/SnapshotNoticesWiringTest.java`（`LoadFromFile` 里取计数、登录里发、登出清理、`ServerTickEvent.Pre` 清零、名单写状态）
- [X] T030 运行 `./gradlew compileJava test`

## Phase 6: User Story 4 - 容量上限与总开关 (Priority: P2)

- [X] T031 [US4] `…/snapshot/SnapshotConfig.java`（`define(builder)`：`[snapshots].enabled`、`maxTotalMb`，注释按 `contracts/config-and-texts.md`，`maxTotalMb` 用不限范围的整数）；`…/diag/DiagConfig.java` 的静态块里调用；配置值读取包在 try/catch 里（未载入当默认值）
- [X] T032 [US4] `…/snapshot/SnapshotService.java`：启动时 `SnapshotSettings.from(...)`，被夹过记 WARN；`enabled = false` 时 `noteUse`、`noteSaved`、`tick`、`onLogout`、`close` 的写、钉住、容量淘汰全部不做
- [X] T033 [US4] `…/snapshot/SnapshotStore.java` 与 `SnapshotService`：`totalBytes`（写线程里首次遍历求和，之后增减；`invalidateSize()` 给命令/窗口删除后用）；写完一份后超限则 `SnapshotCapacity.victims`，删前读头确认版本已知；`stillOver` 时 `Throttle` 记 WARN；`SnapshotOps` 的删除路径调 `invalidateSize()`
- [X] T034 [US4] 资源测试补：配置注释含 "WARNING" 与 "only backup"；接线测试 `…test/resources/SnapshotCapacityWiringTest.java`（淘汰前读头确认、受保护不删、开关关闭时不写）
- [X] T035 运行 `./gradlew compileJava test`

## Phase 7: User Story 5 - 窗口与命令（分页、类别、序号）(Priority: P2)

- [X] T036 [US5] `…/snapshot/SnapshotRequestRules.java`：`MAX_VERSIONS = 100`、`validKind`；改 `…test/snapshot/SnapshotRequestRulesTest.java`
- [X] T037 [US5] `…/snapshot/net/VersionRow.java`（加 `kind`、`toVersion`）、`SnapshotCodecs`（行的编解码、`kind` 不是 1..3 抛 `DecoderException`、`toVersion` ≤ 32）、`SnapshotVersionsRequestPayload`（加 `page`、`pageSize`、`kind`）、`SnapshotVersionsPayload`（加 `kind`、`page`、`pages`、`total`，解码时行数 ≤ 16）；改 `…test/snapshot/SnapshotNetTypesTest.java`
- [X] T038 [US5] `…/snapshot/SnapshotServer.java`：`onVersions` 校验 → 按类别筛选 → 分页（越界回最后一页）→ 发应答
- [X] T039 [US5] `…/network/BackpackNetwork.java`：`versioned("21")`；同改八处写死 `versioned("20")` 的测试断言（`JukeboxWiringTest`、`BackpackSortWiringTest`、`MobileFurnaceWiringTest`、`LightWiringTest`、`PipeWiringTest`、`CategoryExtensionUiResourcesTest`、`WorldMarkerWiringTest`、`MobileCraftingWiringTest`）
- [X] T040 [US5] 客户端：`SnapshotScreenState`（`versionPage`、`versionPages`、`versionTotal`、`versionKind`；换背包回第 1 页与"全部"；翻页/换类别清选择）、`SnapshotScreenLayout`（版本区头部的页码、上一页/下一页、类别按钮；页大小 = `versionRows`）、`SnapshotScreen`（按钮、滚轮翻页、请求带页与类别、行前类别标记与 `→ 版本`）；纯逻辑部分（类别循环、页码夹取）放进可测的类并加 `…test/client/snapshot/…Test`
- [X] T041 [US5] `…/snapshot/SnapshotCommands.java`：`restore`/`delete` 的 `<版本>` 为 `integer(1, 100)`；`info` 每份带类别标记（`T`/`C`/`U`，升级前再带新版本）
- [X] T042 [US5] 语言键（窗口：`button.kind`、`kind.*`、`kind.mark.*`、`version.to`；命令：`info.version` 加类别）中英文；资源测试补
- [X] T043 运行 `./gradlew compileJava test`

## Phase 8: User Story 6 - 文件格式版本与读取规则 (Priority: P3)

- [X] T044 [US6] `…/snapshot/SnapshotStore.java`：读头时 `version` 1 → CHANGE；`version` > 2 → 列表不显示、不参与保留与淘汰、**绝不删除**、日志 INFO 每个文件每次运行一次；不认识的后缀（`parseTime` 返回 -1）本来就看不见；`…test/snapshot/SnapshotFilesTest.java` 补"不认识的名字"用例；接线测试补（保留与淘汰只对"读出且版本已知"的文件动手）
- [X] T045 运行 `./gradlew compileJava test`

## Phase 9: 游戏内验证与文档

- [X] T046 游戏内验证（AI 自动验证，容器，quickstart 1～19；用 `-Pmcfb.timedSnapshotSeconds=5` 缩短间隔；**不在 `src/main` 留调试入口**）：每项写实际验证了什么，做不了的写"未验证"；窗口手感写"待用户确认"
- [X] T047 [P] `README.md` 与 `README_CN.md`：快照一节更新（三类、保留数、总容量与开关、升级前保护与提示、恢复在新版本里有损的说明、配置项、调试开关不写）；`docs/plan/snapshot-retention.md` 状态改为"057 已实现"
- [X] T048 `specs/057-snapshot-retention/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么 / 没验证什么、**协议号 20 → 21 的理由、`DataRevision` 不变**、已知局限（预览不提示丢失格数、读元数据的成本实测结果、重启后名单补发的行为）、宪法复查
- [X] T049 最终检查：`./gradlew compileJava test` 全部通过；无临时修改残留；**不提交、不打 tag**
- [X] T050 用户确认：真机快照窗口（分页、类别按钮、恢复提示）、升级后登录提示、容量与开关；**只有用户明确确认才勾**

## Dependencies

- T002～T012 大部分可并行（T004 依赖 T002，T005 依赖 T002，T009 依赖 T004）；T013 在其后。
- T014 依赖 T002～T005；T016 依赖 T007、T014；T015 独立；T017、T018 在其后。
- T019 独立；T020 依赖 T008、T014；T021 依赖 T009、T019、T020；T022、T023 在其后。
- T024 依赖 T012；T025 依赖 T008、T021；T026 依赖 T024、T025；T027 依赖 T024；T028、T029、T030 在其后。
- T031 依赖 T010；T032 依赖 T016、T031；T033 依赖 T006、T014、T032；T034、T035 在其后。
- T036 → T037 → T038 → T039；T040 依赖 T037、T038；T041 依赖 T036；T042、T043 在其后。
- T044 依赖 T014；T045 在其后。
- T046 依赖全部代码；T047、T048 在 T046 之后。

## Implementation Strategy

- **MVP**：Phase 1～4（三类快照 + 钉住）：这是"保得久、保得住"的核心。
- Phase 5（提醒）、6（容量与开关）、7（窗口与命令）、8（格式规则）按优先级依次加；一个 Phase 一次 `./gradlew compileJava test`，每个检查点都应是绿的。
- 协议号升级放在 Phase 7（T039），之前的 Phase 不改协议。
- 不提交；做完、用户确认后由用户决定何时提交。
