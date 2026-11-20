# Tasks: 第三方模组版本管理与集成终端主动兼容（面板 → 终端）

**Input**: `/specs/058-third-party-compat/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 纯逻辑（版本解析、五种结论、多段与要求、状态机与配置、桥分派与出错）用 JUnit；`mods.toml`、README、`build.gradle`、语言键、配置注释、接线用资源/源码文本检查。**单元测试类路径里没有 Minecraft 类，桥对真实集成终端的调用、启动日志、登录聊天只能在游戏会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`（宿主上有游戏在跑就等用户退出，不要绕过 `launch.sh` 的检查）；容器里 `-PnoJei`；改存档前先备份世界；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增运行时依赖、mixin、反射；新增的四个依赖只是 `compileOnly`；**协议号仍 21（不新增包）**；**`DataRevision` 仍为 6**；客户端不改
- 用户故事：US1 范围外警告（版本范围表 + 日志 + 管理员聊天）；US2 集成终端面板 → 终端；US3 强制启用开关；US4 出错兜底；US5 README 与防漂移测试
- 第三方类型只能出现在 `…/compat/integratedterminals/` 的两个类里；其余全是纯类或不含第三方类型的 MC 层类

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`；类数与用例数记在 `specs/058-third-party-compat/DELIVERY.md`（新建，中文）
- [X] T002 `build.gradle`：在 `dependencies` 里加四条 `compileOnly`（`integrated-terminals:1.21.1-1.11.2`、`integrated-dynamics:1.21.1-1.39.1`、`common-capabilities:1.21.1-2.11.6`、`cyclops-core:mbABgx56`，都 `{ transitive = false }`，与已有 `localRuntime` 同版本，注释说明）；`./gradlew compileJava` 确认坐标可解析、jar 下载成功（research R4）；`./gradlew build -PcleanRuntime -PnoJei` 确认不带开发依赖的构建仍通过
- [X] T003 `src/main/templates/META-INF/neoforge.mods.toml`：加四条可选依赖 `integratedterminals`、`integrateddynamics`、`commoncapabilities`、`cyclopscore`（`type="optional"`、`versionRange="[0,)"`、`ordering="NONE"`、`side="BOTH"`），注释写明"范围在 compat/ThirdPartyTable，不在这里"（contracts/config-and-texts.md §5）

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T004 [P] `…/compat/ThirdPartyVersion.java`（`parse` 取第一个数字起的"数字(.数字)*"、其余忽略、无数字/超 long 为空；`compareTo` 缺段补 0）+ `…test/compat/ThirdPartyVersionTest.java`（contracts/version-table.md §1 表里的全部真实串、`1..2`、`v2.0`、null/空串、`1.11`=`1.11.0`、超大数字不抛异常）
- [X] T005 [P] `…/compat/VersionRange.java`（`min < max` 否则抛 `IllegalArgumentException`；`contains`；`toString` 为 `[1.11.0, 1.12.0)`，每个版本至少补成三段）+ `…test/compat/VersionRangeTest.java`（边界：下限含、上限不含、`1.12.0-beta` 不在 `[1.11.0, 1.12.0)`、`toString` 补零）
- [X] T006 `…/compat/Requirement.java`、`ThirdPartyCompat.java`（`variant` 恒 null，预留）、`VersionChecker.java`（`VersionVerdict` 五种 + `check(ranges, installed)`）、`CompatFinding.java`（`check(entry, installedLookup)`：自己或某个要求没装 → 空；先自己再按声明顺序逐个要求，第一个不合格的为 subject）+ `…test/compat/VersionCheckerTest.java`（段内、过低、过高、两段空隙、无法判断、多段、要求不合格指明模组、要求没装为空、自己没装为空）
- [X] T007 `…/compat/ThirdPartyTable.java`（单一事实来源：集成终端一行，段 `[1.11.0, 1.12.0)`，要求 `integrateddynamics` `[1.39.0, 1.40.0)` 与 `commoncapabilities` `[2.11.0, 2.12.0)`；`validate()` 检查完整性规则）+ `…test/compat/ThirdPartyTableTest.java`（真实表通过完整性规则；反例：段重叠、乱序、`min ≥ max`、`modId` 重复、`supported` 为空、`variant` 非 null 都被 `validate` 指出）
- [X] T008 `…/compat/CompatRuntimeState.java`（`CompatStatus` 四种；`evaluate(table, installedLookup, allowUntested)`；`isActive`、`fail`（只有第一次返回 true）、`findingsOutOfRange`；`ConcurrentHashMap`）+ `…test/compat/CompatRuntimeStateTest.java`（在范围 → ACTIVE；范围外 + 不强制 → OUT_OF_RANGE 未启用；范围外 + 强制 → FORCED 已启用；没装的行不在表里；`ACTIVE`/`FORCED` 可 `fail` 一次，之后 `FAILED` 不再触发；`FAILED` 不出现在 `findingsOutOfRange`；表顺序保持）
- [X] T009 `…/compat/BridgeDispatch.java`（泛型 `Bridge<P,M,S>`，`tryInsert` 按 contracts/bridge.md §2：未启用跳过、`handles` 与 `insert` 都在 try 里、捕获 `LinkageError`/`RuntimeException`、返回值非空时须同种且数量 ≤ 原数量，null 与不合法都当出错、传给桥的是拷贝、失败回调只在 `state.fail` 返回 true 时调用，使用 012 已有的 `QuickTransferLogic.StackOps<S>`）+ `…test/compat/BridgeDispatchTest.java`（contracts/bridge.md §5 表的全部场景）
- [X] T010 运行 `./gradlew compileJava test`

## Phase 3: Foundational（Minecraft 层，无第三方类型）

- [X] T011 [P] `…/version/VersionDebug.java`：`fakeModVersions()`（读 `mcfloatingbackpack.debug.fakeModVersion`，逗号分隔 `modid=版本`，空项/缺 `=`/空键忽略，默认空映射）；更新类注释；改 `…test/version/VersionDebugTest.java`
- [X] T012 [P] `…/compat/InstalledMods.java`：`versionOf(modId)`（没装为空；装了则优先伪造值，否则 `ModInfo.getVersion().toString()`；伪造不能让没装的模组变成已装，research R11）
- [X] T013 `…/compat/ThirdPartyConfig.java`：`define(builder)`（`[compat].allowUntestedVersions`，默认 `false`，注释按 contracts/config-and-texts.md §1，全部英文，含 WARNING、backup、`NoSuchMethodError`、set back to false、reopened）与 `allowUntested()`（未载入的 `IllegalStateException` 取 `false`）；`…/diag/DiagConfig.java` 静态块里在 `SnapshotConfig.define` 之后调用；更新 `DiagConfig` 类注释
- [X] T014 运行 `./gradlew compileJava test`

## Phase 4: User Story 1 - 范围外时管理员被告知 (Priority: P1) 🎯 MVP

**Goal**: 范围外的已装模组启动日志一条 WARN、op 登录聊天一条、非 op 无提示、没装不提示。

**Independent Test**: `-Pmcfb.fakeModVersion=integratedterminals=2.0.0` 起一个世界，核对日志与聊天；改成 `1.11.2` 无提示。

- [X] T015 [US1] 语言键（contracts/config-and-texts.md §2 的 `compat.untested`、`compat.state.off`、`compat.state.forced`、`compat.verdict.below|above|gap|unknown`、`compat.failed`）写进 `…res/lang/en_us.json` 与 `zh_cn.json`；占位符个数与参数数量一致
- [X] T016 [US1] `…/compat/ThirdPartyCompatService.java`：`start(server)`（取 `InstalledMods`、`ThirdPartyConfig.allowUntested()`、`ThirdPartyTable`，`CompatRuntimeState.evaluate`，对 `findingsOutOfRange` 每条写一条 WARN，英文，含显示名、subject 与已装版本、全部受支持版本段、四种结论之一、`compat disabled` / `compat FORCED by allowUntestedVersions`；表自己 `validate` 失败时记 ERROR 并视为全部未启用）、`stop()`（清空状态）、`state()`（未启动时为空，调用方当"不启用"）
- [X] T017 [US1] `…/compat/ThirdPartyNotices.java`：`onLogin(ServerPlayer)`（`hasPermissions(2)` 才发；对 `findingsOutOfRange` 每条一条 `compat.untested`，`state` 参数取 `compat.state.off`/`compat.state.forced`，`verdict` 取四个 verdict 键之一，`ranges` 用 `VersionRange.toString()` 以 `、` 连接；不记名单，每次登录都发）
- [X] T018 [US1] `…/MCFloatingBackpack.java`：`ServerStartingEvent` 里调 `ThirdPartyCompatService.start`（放在 `SnapshotService.start` 之后）、`ServerStoppingEvent` 里 `stop`、已有的 `PlayerLoggedInEvent` 监听里调 `ThirdPartyNotices.onLogin`
- [X] T019 [US1] 测试：资源测试 `…test/resources/ThirdPartyCompatResourcesTest.java`（两份语言文件键集合相同、占位符个数一致、`compat.untested` 的参数数与 `ThirdPartyNotices` 传的一致）；接线测试 `…test/resources/ThirdPartyCompatWiringTest.java`（源码文本：`ServerStartingEvent` 启动与 `ServerStoppingEvent` 停止、登录里调 `onLogin`、`onLogin` 只对 `hasPermissions(2)` 发、不写名单；没装的模组不产生日志——由 `CompatRuntimeState` 测试覆盖）
- [X] T020 运行 `./gradlew compileJava test`

**Checkpoint**: 范围外警告可用，等游戏内验证（情形 6 的前半）

## Phase 5: User Story 2 - 集成终端面板 → 终端 (Priority: P1)

**Goal**: 终端物品存储页上 Ctrl+点击/拖动面板里的物品，存入终端的物品网络；合成页、末影箱页、别的成分不处理。

**Independent Test**: 容器里搭集成动力学网络，quickstart 情形 1～5。

- [X] T021 [US2] `…/compat/NetworkStorageBridge.java`（接口，`BridgeDispatch.Bridge<ServerPlayer, AbstractContainerMenu, ItemStack>`，无第三方类型）与 `…/compat/NetworkStorageBridges.java`（`CopyOnWriteArrayList` 注册表；`register`；`tryInsert(player, menu, stack)` 用 `ThirdPartyCompatService.state()` 构造/持有 `BridgeDispatch`，状态未建立时返回空；失败回调先留成调用 `ThirdPartyNotices.failed`，T027 实现）
- [X] T022 [US2] `…/compat/integratedterminals/IntegratedTerminalsBridge.java`：`modId()="integratedterminals"`；`handles`（`ContainerTerminalStorageBase` + `getTabServer(getSelectedTab())` 是 `TerminalStorageTabIngredientComponentServer` + 不是 `…ItemStackCraftingServer` + `getIngredientNetwork().getComponent() == IngredientComponent.ITEMSTACK`）；`insert`（`getChannel(getSelectedChannel()).insert(stack, false)`，未检查强转用 `@SuppressWarnings("unchecked")`，不碰玩家物品栏，不发包）；类注释写明它与 `IntegratedTerminalsCompat` 是仅有的引用集成终端类型的代码（宪法 VII），模组没装时从不加载
- [X] T023 [US2] `…/compat/integratedterminals/IntegratedTerminalsCompat.java`：`register()` 调 `NetworkStorageBridges.register(new IntegratedTerminalsBridge())`；`…/MCFloatingBackpack.java`：在与 `CuriosCompat.register()` 相同的位置，`ModList.get().isLoaded("integratedterminals")` 时调用
- [X] T024 [US2] `…/network/BackpackQuickTransferPayload.java`：`backpackOut` 里在 `ServerCompatGate.allows` 之后、对每个物品调 `OpenContainerDelivery.deliver` 之前先 `NetworkStorageBridges.tryInsert(player, player.containerMenu, stack)`；有值就 `backpack.setStackInSlot(index, 值)` 并 `continue`，空则走原有路径（contracts/bridge.md §3）；类注释补一句
- [X] T025 [US2] 测试：接线测试 `…test/resources/IntegratedTerminalsWiringTest.java`（源码文本：桥在 `deliver` 之前被问、有值时 `continue`；第三方包名 `org.cyclops.` 只出现在 `compat/integratedterminals/` 的两个文件里；`IntegratedTerminalsBridge` 用到 `ItemStackCraftingServer` 的排除与 `IngredientComponent.ITEMSTACK`；`register` 只在 `isLoaded("integratedterminals")` 里调用；桥类里没有 `getInventory`/`setItem` 一类碰玩家物品栏的调用）；`…test/rules/DependencyRulesTest.java` 补一条：桥类放在 `compat/` 根目录（不在 `compat/<modid>/` 下）被检查器判为违规（FR-023 的证明）
- [X] T026 运行 `./gradlew compileJava test`（此时第三方类型第一次被编译，若 API 与 research R3 不符在这里发现并回到 research 修订）

**Checkpoint**: 终端存入可用，等游戏内验证（情形 1～5）

## Phase 6: User Story 3 - 强制启用开关 (Priority: P2)

**Goal**: `allowUntestedVersions=true` 时范围外也启用，警告改成"强制启用"；默认 false，注释写明风险。

- [X] T027 [US3] 资源测试补进 `…test/resources/ThirdPartyCompatResourcesTest.java`：`ThirdPartyConfig` 源码里的注释含 "WARNING"、"backup"、"set it back to false"（或 contracts 里的等价措辞）、"reopened"，默认值 `false`；`FORCED` 的日志文字含 "FORCED"（读 `ThirdPartyCompatService` 源码）；`compat.state.forced` 两种语言都含"备份"/"backup"
- [X] T028 [US3] 接线检查（补进 `ThirdPartyCompatWiringTest`）：`ThirdPartyCompatService.start` 读 `ThirdPartyConfig.allowUntested()` 并传给 `CompatRuntimeState.evaluate`；服务端配置里 `compat` 节由 `DiagConfig` 定义
- [X] T029 运行 `./gradlew compileJava test`

## Phase 7: User Story 4 - 出错兜底 (Priority: P2)

**Goal**: 桥出错（链接错误、运行时异常、返回值不合法）时不丢物品、本次运行里关闭、一条 ERROR + 在线 op 各一条聊天，之后不再重复。

- [X] T030 [US4] `…/compat/ThirdPartyNotices.java`：`failed(modId, Throwable)`（取表里的显示名；写一条 ERROR 含异常；给 `server.getPlayerList().getPlayers()` 里 `hasPermissions(2)` 的玩家各发一条 `compat.failed`，参数 `feature` 与异常类名）；`NetworkStorageBridges` 的失败回调接到它（服务端从 `ThirdPartyCompatService` 保存的 `MinecraftServer` 取）
- [X] T031 [US4] 测试：`BridgeDispatchTest`（T009）已覆盖"出错后关闭、不重复回调"；接线测试补进 `ThirdPartyCompatWiringTest`（`failed` 只给 `hasPermissions(2)` 的玩家发；`NetworkStorageBridges` 的失败回调调用 `ThirdPartyNotices.failed`；全工程没有 `Class.forName`/`java.lang.reflect` 新增，FR-015）
- [X] T032 运行 `./gradlew compileJava test`

## Phase 8: User Story 5 - README 与防漂移测试 (Priority: P2)

- [X] T033 [US5] `…test/resources/ThirdPartyDriftTest.java`（contracts/version-table.md §4）：(a) 读 `neoforge.mods.toml`：表里每个 `modId`、每个 `also` 的 `modId` 与 `cyclopscore` 都有依赖块，`type="optional"`、`versionRange="[0,)"`；**同时给一个把范围改成 `[1.11,1.12)` 的反例输入，证明检查器会失败**；(b) 读 `README.md` 与 `README_CN.md`：表里每行的显示名与每一个 `VersionRange.toString()` 原文都出现；反例同样证明会失败；(c) 读 `build.gradle`：三个模组（`integrated-terminals`、`integrated-dynamics`、`common-capabilities`）的 `localRuntime` 版本（去掉 `1.21.1-` 前缀）落在表里对应的某一段内，同三个的 `compileOnly` 版本与 `localRuntime` 相同，`cyclops-core` 的 `compileOnly` 带 `transitive = false`；所有四条 `compileOnly` 都带 `transitive = false`
- [X] T034 [P] [US5] `README.md`：`## Compatibility` 一节加兼容表（contracts/config-and-texts.md §7 的三行，集成终端一行写出三个版本段原文）与说明段落（范围外默认不启用 + 管理员警告；`[compat].allowUntestedVersions` 与备份提示；**按模组的档位 `modLevels` 是客户端配置，专用服务器上容器物品交换、Curios/Accessories 的背着来源与集成终端的存入暂时不能单独关闭，计划 059 做**；客户端 `integratedterminals=OFF` 让面板不在其界面显示，`GENERIC` 不会关闭服务端的存入）
- [X] T035 [P] [US5] `README_CN.md`：与 T034 相同内容的中文版
- [X] T036 [P] [US5] `docs/plan/third-party-compat.md` 状态改为"058 已实现，待用户确认"；`docs/plan/` 里若有总览/路线文件提到第三方兼容的，同步编号（059 服务端档位、060 反向）
- [X] T037 运行 `./gradlew compileJava test` 与 `./gradlew build -PcleanRuntime -PnoJei`

## Phase 9: 游戏内验证与交付

- [X] T038 游戏内验证（AI 自动验证，容器，quickstart 第二节六种情形 + 额外三项；**不在 `src/main` 留调试入口**，伪造版本只用 `-Pmcfb.fakeModVersion=…`）：容器里搭集成动力学网络（逻辑线缆 + 箱子 + 物品接口 + 终端存储部件）；情形 1 存入、2 部分存入、3 离线、4 合成页、5 末影箱页（记录实际行为）、6 范围外（日志 WARN、op 聊天、非 op 无、没装无、Ctrl+点击回到通用路径；再把 `allowUntestedVersions` 改 `true` 重开世界：改成"强制启用"且存得进）；额外：非集成终端界面（箱子、精妙存储、AE2 终端）行为不变；终端界面里 Shift+点击玩家物品栏（不开面板）的服务端行为（坐实规格 Assumptions）。每项写实际验证了什么，做不了的写"未验证"；**精妙存储双向与 AE2 正向**在 spec 里已有容器验证记录，DELIVERY 引用即可，不重做
- [X] T039 `specs/058-third-party-compat/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么 / 没验证什么、**协议号仍 21 的理由、`DataRevision` 仍 6**、已知局限（`insert` 中途抛异常时可能重复——research R7；客户端 GENERIC 关不掉服务端的存入——FR-030；集成终端以外的版本表只有一行；出错兜底在游戏里没有真实故障可测）、宪法复查
- [X] T040 最终检查：`./gradlew compileJava test` 与 `./gradlew build -PcleanRuntime -PnoJei` 全部通过；无临时修改残留；`git status` 里只有 058 相关文件；**不提交、不打 tag**
- [ ] T041 用户确认：真机集成终端上的存入手感、范围外警告在真实服务器上的样子、README 兼容表；**只有用户明确确认才勾**

## Dependencies

- T001 → T002、T003（T002 的编译检查是后面所有第三方编译的前提）。
- T004、T005 可并行；T006 依赖 T004、T005；T007 依赖 T006；T008 依赖 T006；T009 独立（只依赖 012 已有的 `QuickTransferLogic.StackOps`）；T010 在其后。
- T011、T012 可并行；T013 独立；T014 在其后。
- US1：T015 独立；T016 依赖 T007、T008、T012、T013；T017 依赖 T008、T015；T018 依赖 T016、T017；T019、T020 在其后。
- US2：T021 依赖 T009、T016；T022 依赖 T002、T021；T023 依赖 T022；T024 依赖 T021；T025、T026 在其后。
- US3：T027、T028 依赖 T013、T016；T029 在其后。
- US4：T030 依赖 T017、T021；T031、T032 在其后。
- US5：T033 依赖 T003、T007；T034、T035 可并行，依赖 T007（用 `VersionRange.toString()` 的格式）；T036 独立；T037 在其后。
- T038 依赖全部代码与文档；T039 在 T038 之后；T040、T041 最后。

## Implementation Strategy

- **MVP**：Phase 1～5（版本范围表 + 警告 + 集成终端存入）：用户看得见的功能与基础设施的核心。
- Phase 6（强制开关）、7（出错兜底）、8（README 与防漂移）按优先级依次加；一个 Phase 一次 `./gradlew compileJava test`，每个检查点都应是绿的。
- T026 是第一次让编译器检查第三方 API：若与 research R3（`javap` 读出的签名）不符，**停下来回到 research 修订**，不在实现里绕。
- 不提交；做完、用户确认后由用户决定何时提交。
