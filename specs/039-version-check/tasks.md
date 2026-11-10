# Tasks: 版本一致性提示

**Input**: `/specs/039-version-check/` 的 spec.md、plan.md、research.md、data-model.md、contracts/（handshake.md、texts.md）、quickstart.md；宪法 2.2.1

**Tests**: 版本解析与比较、提示选择、老客户端文本、`RowLayout.isUnknownRow` 用 JUnit 覆盖（纯逻辑）；语言键与接线用文本检查覆盖。**网络协商、聊天显示、老客户端能否登录只能在客户端会话里验证**（宪法 V）：编译与单元测试通过不等于机制在游戏里成立。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`，`RES/` 表示 `src/main/resources/`；**不包含提交 Git 的任务**（等用户要求再提交；**也不打 tag**）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，已有就不启动第二个；不运行专用服务器、不创建 `eula.txt`；跑完 Gradle 后 `./gradlew --stop`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增**配置项、命令；**不改现有网络包与协议号 17**；服务器**只提醒，不拒绝、不踢人**；不修改 `docs/ideas`
- 用户故事：US1 不一致时的警告；US2 该安静时安静；US3 老客户端由服务器提醒；US4 服务器日志与诊断头部；US5 未知类别预测宽容；文档为收尾

---

## Phase 1: Setup

- [x] T001 基线：`./gradlew test` 记下当前类数与用例数（上次 125 个类、890 个用例）；`git status` 确认工作区里只有 039 的文档、README 与发布流水线等未提交的改动；记在 DELIVERY.md

## Phase 2: Foundational（纯逻辑，不碰游戏对象）

**⚠️ 之后所有用户故事都依赖本阶段**

- [x] T002 [P] `…/version/ModVersions.java`（无 Minecraft 类型）：`Parsed(major, minor, patch, prerelease)`、`parse(String)`（`MAJOR.MINOR[.PATCH][-预发布][+构建元数据]`，PATCH 缺省 0，容忍前导 `v`；null、空、非数字、形状不对 → 空）、`Result`（`MATCH`、`CLIENT_OLDER`、`CLIENT_NEWER`、`UNCOMPARABLE`）、`compare(clientVersion, serverVersion)`（先比 MAJOR.MINOR，再比预发布优先级：没有预发布 > 有预发布，逐标识符比、数字按数值、字母按字典序、数字 < 字母、前缀更短更小；PATCH 与构建元数据不参与；任一端解析失败 → `UNCOMPARABLE`）。`…test/version/ModVersionsTest.java`：规格的全部边界——`0.1.0`/`0.1.0` 一致、`0.2.1` vs `0.2.0` 一致、`0.1.0` vs `0.2.0` 旧、`0.3.0` vs `0.2.0` 新、`1.0.0` vs `0.9.9` 新、`0.2.0-rc1` vs `0.2.0` 旧、`0.2.0` vs `0.2.0-rc1` 新、`0.2.0-rc1` vs `0.2.1` 旧、`0.2.0-rc1` vs `0.2.1-rc1` 一致、`rc1` vs `rc2`、`rc.2` vs `rc.10`（数字按数值）、构建元数据 `0.2.0+abc` vs `0.2.0` 一致、`v0.2.0`、`0.2`（缺 PATCH）、`banana`/空串/null/`1.x.0`/`0.2.0.0` 无法比较、镜像对称（对一批输入 `compare(a,b)` 与 `compare(b,a)` 互为镜像）、PATCH 与构建元数据不改变结果
- [x] T003 [P] `…/version/VersionNotices.java`（纯逻辑）：`Notice`（`SILENT`、`OLDER`、`NEWER`、`UNKNOWN`）、`noticeFor(Result)`、`legacyClientText(serverVersion)`（contracts/texts.md 里老客户端的中英双语纯文本，两行，带服务器版本）。`…test/version/VersionNoticesTest.java`：四种结果对应的提示类型；老客户端文本含服务器版本、含"备份"与"backup"、中英两段、服务器版本为空时写"未知"不抛异常
- [x] T004 [P] `…/inventory/extension/RowLayout.java`：加 `isUnknownRow(index)`——该格属于某个可见行（`groupOf(index) >= 0`）且该行的类别不在 `ExtensionCategories.ALL`（用 `isKnown`）。`…test/inventory/extension/RowLayoutTest.java` 加用例：已知类别（10 个与 `general`）的行为假；不认识的类别（如 `zzz`、空串）的行里的格子为真；基础容量内的格子、越界格子为假；混合行里只有未知那一行为真
- [x] T005 [P] 语言键 `RES/assets/mcfloatingbackpack/lang/zh_cn.json` 与 `en_us.json`：`message.mcfloatingbackpack.version.older`、`.newer`、`.unknown`（文字见 contracts/texts.md，参数 `%1$s` 客户端版本、`%2$s` 服务器版本）；两个文件键集合保持一致
- [x] T006 运行 `./gradlew compileJava test`

**Checkpoint**: 纯逻辑就绪，现有测试通过

## Phase 3: User Story 5 - 未知类别预测宽容 (Priority: P2)

先做它：最小、独立，不依赖任何网络代码，并且是对已有行为的一个小修。

**Independent Test**: 客户端遇到不认识的类别行时不显示"放不进"（quickstart N）。

- [x] T007 [US5] `…/client/panel/ClientPlacement.java`：`canPlace` 里在调用 `ExtensionRules` 之前，若 `new RowLayout(base, rows.visibleCategories()).isUnknownRow(slot)` 为真则返回放行（注释说明：版本落后的客户端遇到不认识的类别行宁可多放行，由服务器裁判；服务器对未知类别仍拒绝）；已知类别的预测不变；`ItemCategoryMatcher` 与 `ExtensionRules` 不动
- [x] T008 运行 `./gradlew compileJava test`

## Phase 4: User Story 1 + 2 + 3 + 4 - 握手、提示与日志 (Priority: P1) 🎯 MVP

**Independent Test**: 用调试开关在一个客户端里验证（quickstart A～J），再用第二个客户端验证真实协商（K～M）。

- [x] T009 [P] `…/version/VersionDebug.java`：读四个系统属性 `mcfloatingbackpack.debug.checkOwner`、`serverVersion`、`clientVersion`、`legacyClient`（全部缺省时行为与不存在完全相同）；提供 `checkOwner()`、`serverVersionOverride()`、`clientVersionOverride()`、`legacyClient()`；注释写明"开发验证用，默认关"
- [x] T010 [P] `…/version/PlayerVersions.java`：`ConcurrentHashMap<UUID,String>`；`expectReport(uuid)`（"尚未报告"）、`noVersionCheck(uuid)`（"无版本检测（版本过旧）"）、`report(uuid, version)`、`forget(uuid)`、`describe(uuid)`（没有记录返回"未知"）
- [x] T011 [US1][US2] `…/version/VersionNetwork.java` 的版本字符串部分：`currentVersion()`（两端通用）：`ModList.get().getModContainerById(MOD_ID)` 的 `getModInfo().getVersion().toString()`，取不到返回 `null`；**编译时确认 `IModInfo.getVersion()` 的确切类型**；遇到 `VersionDebug.clientVersionOverride()`（客户端）或 `serverVersionOverride()`（服务器）时返回覆盖值
- [x] T012 [US1][US3] `…/network/VersionHelloPayload.java`（`serverVersion`，字符串限长 64，超长或空按"无版本"解码，不抛异常断开连接）、`…/network/VersionReportPayload.java`（`clientVersion`，同样限长）：`TYPE` 用 `mcfloatingbackpack:version_hello` 与 `version_report`，`STREAM_CODEC`，静态 `handleClient` / `handleServer`（处理逻辑在 `VersionNetwork`，见下）
- [x] T013 [US1][US3] `…/network/BackpackNetwork.java`：在 `onRegisterPayloads` 里**另起一个注册器** `event.registrar(MCFloatingBackpack.MOD_ID).versioned("1").optional()`，`playToClient(Hello…)` 与 `playToServer(Report…)`；现有 `versioned("17")` 的注册器与所有现有包**一行不动**；加注释说明 `"1"` 永远不改（两端都有而版本不同会登录失败）
- [x] T014 [US1][US2][US3] `…/version/VersionNetwork.java` 的服务器登录钩子：`onLogin(ServerPlayer)`：`server.isSingleplayerOwner(player.getGameProfile())` 为真且没开 `VersionDebug.checkOwner()` → 返回；`VersionDebug.legacyClient()` 或 `!player.connection.hasChannel(VersionHelloPayload.TYPE)` → 老客户端分支：`player.sendSystemMessage(Component.literal(VersionNotices.legacyClientText(serverVersion)))`、`PlayerVersions.noVersionCheck`、WARN 日志（contracts/texts.md 的格式）；否则发 hello（**绝不对没有通道的客户端调用 `sendToPlayer`**）、`PlayerVersions.expectReport`。`MCFloatingBackpack.java`：构造函数里加 `PlayerLoggedInEvent` 监听（调 `VersionNetwork.onLogin`）；现有的 `PlayerLoggedOutEvent` 里加 `PlayerVersions.forget`
- [x] T015 [US1][US2] `…/version/VersionNetwork.java` 的客户端处理：`handleHello(serverVersion)`：用 `ModVersions.compare(currentVersion(), serverVersion)` → `VersionNotices.noticeFor`；非 `SILENT` 则把"待显示提示"（Notice + 两个版本字符串，缺失写"未知"）存成一个静态单值；**总是**回 `VersionReportPayload`（含 `currentVersion()`，null 时发空串）；`MCFloatingBackpack`（客户端部分，或 `FloatingPanelEvents`）里加 `ClientTickEvent.Post` 监听：`minecraft.player != null && minecraft.gui != null` 时显示待显示提示一次（`FloatingPanelEvents.chat` + 对应语言键，`older`/`newer`/`unknown`）并清空；`ClientPlayerNetworkEvent.LoggingOut` 时清空；不要让客户端类被服务器加载（沿用现有 `@OnlyIn`/`Dist.CLIENT` 的做法，参照 `FloatingPanelEvents`）
- [x] T016 [US4] `…/version/VersionNetwork.java` 的服务器 report 处理：`handleReport(player, clientVersion)`：`PlayerVersions.report`；用 `ModVersions.compare(clientVersion, serverVersion)`；`MATCH` 记 DEBUG，其余记 WARN（contracts/texts.md 的格式），经 `DiagLog.log(Side.SERVER, …)`，遵守已有 `logging.level`；report 里的空串按无法比较处理
- [x] T017 [US4] `…/diag/BackpackWatchService.java`：背包"不见了"的 WARN 头部末尾追加 ` Client mod version: <PlayerVersions.describe(uuid)>.`；不改其它逻辑与级别
- [x] T018 `build.gradle`：`runs.client` 里把 `-Pmcfb.<名字>=<值>` 转成 `-Dmcfloatingbackpack.debug.<名字>=<值>`（遍历 `project.properties` 里以 `mcfb.` 开头的键；没传就什么都不加）；在 `docs/plan/release-process.md` 或 DELIVERY.md 里记一句用法
- [x] T019 运行 `./gradlew compileJava test`

**Checkpoint**: 握手、提示与日志完成；**此时可以在单人游戏里用调试开关验证**（quickstart A～J）

## Phase 5: 收尾（自动化）与文档

- [x] T020 [P] `…test/resources/VersionCheckResourcesTest.java`（文本检查）：两个语言文件都有 `version.older/newer/unknown` 且键集合一致，含 `%1$s` 与 `%2$s`，文字里含"备份"/"back up"；`BackpackNetwork.java` 里有 `versioned("17")`（现有注册器未变）**且**另有 `versioned("1").optional()` 的注册；`VersionNetwork.java` 里 `hasChannel(` 出现在发 hello 之前；握手包不在现有的 `versioned("17")` 注册器里注册；没有新增配置项（`DiagConfig`/`CompatConfig` 没有版本相关的定义）与命令（`Commands.literal` 里没有 `version`）；`ClientPlacement.java` 里有 `isUnknownRow`；协议仍是 `"17"`
- [x] T021 静态检查：`grep -rn 'versioned(' src/main/java` 现有的仍是 `"17"`，新增的是 `"1"` 并带 `optional()`；`git diff` 里现有网络包（除 `BackpackNetwork` 的注册事件里新增的一段）没有改动；`ExtensionRules`、`ItemCategoryMatcher`、服务器的放入规则没有改动；`DependencyRulesTest` 通过
- [x] T022 全量 `./gradlew compileJava test`，记下类数与用例数；`./gradlew --stop`
- [x] T023 [P] 文档：`README.md`、`README_CN.md` 的"多人游戏/Multiplayer"一节写明版本规则（MAJOR.MINOR 必须一致，PATCH 可不同；登录时会提示不一致）；`.github/release-notes/_footer.md` 加一句；`docs/plan/release-process.md` 里 039 的状态改为"已实现，等用户验证"；`docs/MCFloatingBackpack.md` 加 039 一句；`docs/plan/manual-verification-checklist.md` 加 039 验证项 A～N（见 quickstart）；**不要修改 `docs/ideas`**
- [x] T024 `specs/039-version-check/DELIVERY.md`：已验证 / 未验证（老客户端真实登录、`hasChannel` 的真实取值、聊天位置、专用服务器）/ 已知限制（开发版同版本检测不出来、纯文本不随语言、局域网依据房主版本、未知类别行的悬停说明显示语言键、只提醒不阻止）/ 基线与完成后的类数与用例数 / 对规格的说明（用 `isSingleplayerOwner` 一个判断代替 `isSingleplayer` 加所有者）/ 调试开关的用法

## Phase 6: 验证（客户端验证由用户确认）

- [ ] T025 [US1][US2] 按 quickstart A～I 用调试开关在一个客户端里验证：客户端更旧、更新、PATCH 不同、一致、预发布不同、无法比较、退出再进、中英文、单人游戏不提示（先 `pgrep`；由用户确认后才打勾）
- [ ] T026 [US3][US4] 按 quickstart J 验证老客户端分支的文字与日志（`legacyClient` 开关），并按 K 用第二个客户端（038 的构建）验证**老客户端真实登录**与服务器侧提示、日志（由用户确认后才打勾；**这一项失败要回到 plan 重新设计机制**）
- [ ] T027 [US1][US2] 按 quickstart L 验证局域网里别的玩家被提示、房主不提示（需要两个客户端，由用户确认后才打勾）
- [ ] T028 [US5] 按 quickstart M、N 验证现有功能不受影响（协议 17 的两端照常连接）与未知类别行的预测宽容（由用户确认后才打勾；N 不好构造时记为"未验证"）

---

## Dependencies & Execution Order

- Phase 1 → Phase 2（T002～T005 互相独立，可并行；T006 汇总）→ Phase 3 → Phase 4 → Phase 5 → Phase 6。
- Phase 3（US5）只依赖 T004，与握手无关，先做。
- Phase 4 内：T009、T010 可并行；T011 → T012 → T013；T014 依赖 T009、T010、T011、T012、T013；T015 依赖 T011、T012、T013、T005；T016 依赖 T010、T012；T017 依赖 T010；T018 独立；T019 最后。
- T020～T024 在全部实现之后；T023 的文档可提前准备。
- **Phase 4 完成后才能在游戏里验证**；T026 的真实登录验证需要用户启动第二个客户端。

## Parallel Opportunities

- T002、T003、T004、T005；T009、T010、T018；T020、T023。

## Implementation Strategy

先纯逻辑（Phase 2），让比较与提示选择有测试保护；再做最小独立的预测宽容（Phase 3）；然后握手本身（Phase 4）。MVP 是 Phase 2 + 4：有了握手与提示，单人游戏里用调试开关就能验证大部分场景。最不确定的是 NeoForge 可选包在真实两个版本之间的协商（T026），它只能由用户用第二个客户端验证，结果决定机制是否成立；其余都能在一个客户端里验证。
