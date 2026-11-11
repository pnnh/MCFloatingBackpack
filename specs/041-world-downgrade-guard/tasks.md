# Tasks: 存档级降级拦截

**Input**: `/specs/041-world-downgrade-guard/` 的 spec.md、plan.md、research.md、data-model.md、contracts/（marker.md、texts.md）、quickstart.md；宪法 2.2.1

**Tests**: 标记的解析与序列化、决策表、文字、临时目录里的文件读写用 JUnit 覆盖（纯 Java，不加载 Minecraft 类）；事件接线用源码文本检查覆盖。**单人游戏里被拦时玩家实际看到什么、中止后存档确实没变、放行与损坏标记的表现只能在客户端会话里做**（宪法 V）：编译与单元测试通过不等于拦截在游戏里成立。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（等用户要求再提交；**也不打 tag**）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，已有就不启动第二个**；专用服务器可以运行（用户 2026-11-11 撤销旧规矩），不替用户创建 `eula.txt`；每次 Gradle 运行后 `./gradlew --stop`
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不改** 040 的任何代码与测试、协议号 17、现有网络包；**不新增**配置项、命令、语言键；不修改 `docs/ideas`
- 用户故事：US1 存档更高被拦；US2 明确接受风险；US3 正常情形不受影响；US4 文档与分工；US5 开发里造出更高修订的存档

---

## Phase 1: Setup

- [x] T001 基线：`./gradlew test` 记下当前类数与用例数（上次 133 个类、944 个用例）；`git status` 确认工作区只有 040/041 的文档改动；记在 DELIVERY.md

## Phase 2: Foundational（纯 Java，不碰游戏对象）

- [x] T002 [P] `…/worldmarker/WorldMarker.java`：`record WorldMarker(int dataRevision, String modVersion)`（`modVersion` 可空）；`parse(String text)` 返回 `MarkerRead`（`MISSING` 不由它产生；`VALID(WorldMarker)` 或 `CORRUPT(原因)`），用 `java.util.Properties` 读，`data_revision` 缺失/不是整数/为负 → `CORRUPT`；`toText()` 固定输出一行注释、`data_revision=…`、（`modVersion` 非空时）`mod_version=…`，见 data-model.md。同文件或同包放 `MarkerRead`（`MISSING`、`VALID`、`CORRUPT`）。`…test/worldmarker/WorldMarkerTest.java`：往返一致；`\r\n`、前后空白、注释行、空行；`data_revision` 缺失、`abc`、`-1`、超出 int 范围 → `CORRUPT`；缺 `mod_version` 仍 `VALID`；`mod_version` 里有特殊字符的往返
- [x] T003 [P] `…/worldmarker/WorldMarkerGuard.java`：枚举 `Decision`（`PASS`、`PASS_AND_WRITE`、`REFUSE`、`ACCEPTED`）与 `decide(MarkerRead read, int current, boolean accept, String currentModVersion, int target)`（`target` 是要写的修订：开发开关把它抬高时，即使标记等于当前也要写；实现时发现的，见 DELIVERY），按 data-model.md 的决策表（`MISSING`/`CORRUPT`/低于 → `PASS_AND_WRITE`；等于且 `modVersion` 相同 → `PASS`，不同 → `PASS_AND_WRITE`；高于且未放行 → `REFUSE`；高于且放行 → `ACCEPTED`）；另提供 `revisionToWrite(Integer debugOverride, int current, MarkerRead read)`：默认 `current`，有开发开关 N 时仅当 N 大于现有标记才用 N，**永不低于现有标记**。`…test/worldmarker/WorldMarkerGuardTest.java`：决策表全部 6 行 × `accept` 取值；`revision == current` 的边界；`revision == current + 1`；`modVersion` 为空/不同；`revisionToWrite` 在无开关、N 大于/小于/等于标记、标记缺失、标记损坏时的结果；**不变量**：任何输入下 `REFUSE` 与 `ACCEPTED` 都不要求写
- [x] T004 [P] `…/worldmarker/WorldGuardTexts.java`：按 contracts/texts.md 生成纯字符串：`crashTitle(stored, current)`、`refuseDetail(stored, markerVersion, current, modVersion, markerPath)`（中英两段）、`acceptedWarn`、`corruptWarn`、`writeFailedWarn`、`markerSetInfo`；`markerVersion` 缺失写 `unknown / 未知`。`…test/worldmarker/WorldGuardTextsTest.java`：拒绝说明**必须出现**存档修订、当前修订、当前模组版本、`-Dmcfloatingbackpack.acceptDowngrade=true`、标记路径、"备份"、"Update"/"升级"，且中文段与英文段都在；标题含两个修订；`markerVersion` 缺失时出现 `unknown`
- [x] T005 [P] `…/worldmarker/WorldMarkerStore.java`（只用 `java.nio.file`）：`read(Path)`（不存在 → `MISSING`；存在则读 UTF-8 文本交给 `WorldMarker.parse`；`IOException` → `CORRUPT("unreadable: …")`）；`write(Path, WorldMarker)`（建父目录；写 `world_marker.properties.tmp` 再 `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)`，`AtomicMoveNotSupportedException` 时退回只用 `REPLACE_EXISTING`；`IOException` 抛给调用方）；常量 `RELATIVE_PATH = "mcfloatingbackpack/world_marker.properties"`。`…test/worldmarker/WorldMarkerStoreTest.java`（JUnit `@TempDir`）：缺失、写入后读回、覆盖已有文件、父目录不存在、损坏内容、路径是目录（读不了 → `CORRUPT`）、只读目录写失败抛 `IOException`（平台不支持时跳过）、写完后目录里没有残留 `.tmp`
- [x] T006 [P] `…/version/VersionDebug.java`：加 `acceptDowngrade()`（`Boolean.getBoolean("mcfloatingbackpack.acceptDowngrade")`，**不带 debug 前缀**，因为它是玩家用的参数）与 `worldMarkerRevisionOverride()`（`mcfloatingbackpack.debug.worldMarkerRevision`，整数，缺省或不是数字返回 `null`）；类注释补两条说明与用法 `-Pmcfb.worldMarkerRevision=99`、`-Pmcfb.acceptDowngrade=true`。`…test/version/VersionDebugTest.java`（已有则追加；用 `System.setProperty`/`clearProperty` 并在 `@AfterEach` 还原）：两个开关的缺省、`true`/`false`/乱写、数字与非数字
- [x] T007 运行 `./gradlew compileJava test`

**Checkpoint**: 解析、决策、文字、文件读写与开关都就绪，全部可在不启动游戏的情况下测试

## Phase 3: User Story 1 + 3 - 启动检查与接线 (Priority: P1) 🎯 MVP

**Goal**: 标记更高 → 拦截；缺失、损坏、不高于当前 → 通过并按需写标记。

**Independent Test**: 用 `-Pmcfb.worldMarkerRevision=99` 写出更高标记，再不带开关启动被拦（quickstart A～C、F）。

- [x] T008 [US1][US3] `…/worldmarker/WorldMarkerCheck.java`（**实现时拆出**：整个检查流程在无 Minecraft 类型的这个类里，返回要记的日志行与崩溃报告的标题和说明，这样"拒绝与放行不写文件"能在临时目录里用行为测试钉住，另有 `MarkerRead.java`）与 `…/worldmarker/WorldMarkerEvents.java`（唯一碰 Minecraft 类型的类，只把路径等交给 `WorldMarkerCheck`、记日志、必要时抛异常）。流程如下：`onServerAboutToStart(ServerAboutToStartEvent)`：`marker = server.getWorldPath(LevelResource.ROOT).resolve(WorldMarkerStore.RELATIVE_PATH)` → `read` → `decide(read, DataRevision.CURRENT, VersionDebug.acceptDowngrade(), 当前模组版本)`（模组版本取法同 `VersionNetwork`：`ModList.get().getModContainerById(MOD_ID)`，取不到就 `null`）；按 contracts/marker.md 与 texts.md：`PASS` 不做事；`PASS_AND_WRITE` 先对 `CORRUPT` 记 WARN，再写（要写的修订取 `revisionToWrite`），写成功记 INFO，写失败记 WARN 后**继续启动**；`ACCEPTED` 记 WARN，**不写**；`REFUSE` 先记 ERROR（说明全文），再 `throw new ReportedException(CrashReport.forThrowable(new IllegalStateException(detail), crashTitle))`，**不写任何文件**。日志用本类自己的 `LogUtils.getLogger()`
- [x] T009 [US1] `…/MCFloatingBackpack.java`：在现有 `ServerStartingEvent` 监听器附近加一行 `NeoForge.EVENT_BUS.addListener(WorldMarkerEvents::onServerAboutToStart)`（带注释：041，早于 `loadLevel()` 与任何玩家数据的读取）
- [x] T010 [US1][US3] `…test/worldmarker/WorldMarkerWiringTest.java`（源码文本检查）：`MCFloatingBackpack` 里为 `ServerAboutToStartEvent` 注册了 `WorldMarkerEvents`；`WorldMarkerStore.write` 只在 `WorldMarkerEvents` 里被调用（没有别的写标记的地方）；`WorldMarkerEvents` 里 `REFUSE` 分支出现 `ReportedException`、该分支内没有 `write`；`ACCEPTED` 分支内没有 `write`；比较基准出现 `DataRevision.CURRENT`；类里没有引用玩家数据相关的类（`PlayerList`、`playerdata`）
- [x] T011 运行 `./gradlew compileJava test`

**Checkpoint**: 拦截与正常通过的完整流程就绪

## Phase 4: User Story 2 + 5 - 放行与开发开关 (Priority: P1/P2)

**Goal**: 玩家有两种出口；开发里一个客户端就能造出"更高修订的存档"并放行。

**Independent Test**: quickstart C～E。

- [x] T012 [US2][US5] `build.gradle`：在 `runClient` 的 `mcfb.*` 转发循环里给 `mcfb.acceptDowngrade` 加特例，转成**不带 debug 前缀**的 `mcfloatingbackpack.acceptDowngrade`；`worldMarkerRevision` 走原有转发，不用改。更新循环上方的注释。`…test/worldmarker/WorldMarkerWiringTest.java` 追加文本检查：`build.gradle` 里有 `acceptDowngrade` 的特例
- [x] T013 [US2] 复核放行路径：`ACCEPTED` 与"删除标记文件"两种出口在 `WorldMarkerGuardTest`（T003）里都有用例；`texts.md` 的两种办法与代码里的参数名、路径常量一致（`WorldGuardTextsTest` 已断言），不一致就改文字
- [x] T014 运行 `./gradlew compileJava test`

## Phase 5: User Story 4 - 文档与分工 (Priority: P2)

**Goal**: 文档与实现一致，没有任何一处仍暗示"保护能防止降级丢数据"。

- [x] T015 [US4] `docs/plan/release-process.md` 的"存档级降级拦截（041）"一节：去掉"规格阶段/尚未实现"，改成已实现的描述，写明标记路径、决策、放行办法、"单人游戏里呈现为崩溃报告"的已知局限、"验证等用户确认"的链接
- [x] T016 [US4] `README.md` 与 `README_CN.md` 的 "Updating and downgrading" / "升级与降级"：在现有"不支持降级"之后补一段：存档由更新数据修订保存时，本模组会拒绝加载该存档（单人游戏表现为崩溃报告），说明两种接受风险的办法（`-Dmcfloatingbackpack.acceptDowngrade=true`、删除 `mcfloatingbackpack/world_marker.properties`）与"先备份存档"；两份语言一致
- [x] T017 [US4] 核对 `.github/release-notes/v0.1.0.md`：已有"不支持降级"，不需要再加就不改；若 README 新增了拦截说明且发布说明提到升级，补一句同样的提醒（不涉及就不动）
- [x] T018 [US4] `docs/plan/manual-verification-checklist.md` 的 041 一节：去掉"规格阶段，尚未实现"，对齐 quickstart 的 A～J
- [x] T019 [US4] `specs/041-world-downgrade-guard/DELIVERY.md`：已验证 / 未验证（修复后单人游戏被拦时的实际表现、中止后存档确实没变、NPE 无害、旧版本不清理标记目录、专用服务器的表现）/ 已知限制（单人游戏里呈现为崩溃报告；标记被删或改低即失效；本机制出现之前的版本不检查；原版在我们之前写 level.dat）/ 基线与完成后的类数与用例数 / `DATA_REVISION` **不递增**（本 feature 不改背包存储数据）/ 对规格的说明（plan 阶段的几处调整，见 plan.md"对规格的说明"）
- [x] T020 [US4] 记忆与 040 文档的指向核对：`specs/040-downgrade-protection/DELIVERY.md` 与 `spec.md` 的更正段指向 041 已存在，确认链接路径正确（只读检查，不改 040 的行为说明）

## Phase 6: Polish & 验证

- [x] T021 `./gradlew test`：确认 040 及以前的全部测试仍通过；记下类数与用例数（比 T001 多出 `worldmarker` 包的 5 个类与 `VersionDebugTest` 的追加）；`./gradlew --stop`
- [x] T022 `./gradlew build` 确认 jar 能构建（本 feature 无资源改动，不需要图集预算检查）；`./gradlew --stop`
- [x] T023 **客户端验证（quickstart A～H，由用户做，用户确认后才打勾）**：先 `pgrep`；备份世界；A 无标记旧存档、B 标记不高于当前、**C 标记更高被拦（观察项：游戏是否按预期关闭而不是卡在加载界面、崩溃报告的说明、日志的 ERROR 与 NPE、`playerdata`/`region`/标记指纹不变）**、D 启动参数放行、E 删除或改低标记、F 标记损坏、G 回归（040 的 `-Pmcfb.stampRevision=99` 仍被拒绝并提示、协议号 17）、H 对照实验（可选）；没做的写"未验证"（**用户 2026-11-11 确认**：A～F 已验证；G 只验证了几个基本功能、未全部回归，后续继续；H 可选未做）
- [ ] T024 **专用服务器与旧版本清理标记目录（quickstart I、J）**：专用服务器的通过路径（用户）与拒绝路径（Claude，2026-11-11）已实测，放行路径与"旧版本不清理标记目录"未做；由用户确认后才打勾

---

## Dependencies & Execution Order

- **Phase 1** → **Phase 2**（T002～T006 互相独立，可并行；T007 汇合）→ **Phase 3**（T008 依赖 T002～T006；T009、T010 依赖 T008）→ **Phase 4**（T012 依赖 T006；T013 依赖 T003/T004）→ **Phase 5**（文档在实现完成后再核对）→ **Phase 6**。
- T023、T024 只能由用户在游戏里完成；实现完成时它们保持未勾选。
- US1、US3 共用同一个事件处理器（T008），所以放在同一个阶段。

## Parallel Example

```text
Phase 2: T002、T003、T004、T005、T006 可同时做（各自一个新文件与对应测试；T006 改 VersionDebug）。
Phase 5: T015、T016、T018 可同时做（不同文档）。
```

## Implementation Strategy

**MVP = Phase 1～3**：有了解析、决策、文件读写与事件接线，用 `-Pmcfb.worldMarkerRevision=99` 在一个客户端里就能验证拦截（quickstart C）。Phase 4 补上开发里放行的转发；Phase 5 让文档与实现一致；最不确定的是单人游戏里崩溃界面的实际呈现与中止后存档没变（T023），它们只能在客户端会话里实测，结论写进 DELIVERY.md 的已知限制。

不新增语言键、网络包、配置项；不改 040 的代码与协议号 17。

## 实现说明（2026-11-10）

Phase 1～6 的代码与自动化任务（T001～T022）已完成：`./gradlew test` 140 个类、1005 个用例全部通过，`./gradlew build` 通过。**T023、T024 是客户端验证与专用服务器验证，等用户确认，未勾选。** 实现与任务的差别：整个启动检查拆进了 `WorldMarkerCheck`（以便行为测试），`WorldMarkerCheckTest` 取代了任务里对"拒绝与放行不写文件"的纯文本检查；`decide` 多了 `target` 参数。
