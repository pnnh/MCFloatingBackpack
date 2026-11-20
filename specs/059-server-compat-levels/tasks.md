# Tasks: 按模组的服务端档位

**Input**: `/specs/059-server-compat-levels/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 纯逻辑（有效档位、门禁允许表、限流、`BridgeDispatch` 新条件、`BackpackSearch` 的 `active(player)`）用 JUnit；配置注释、语言键、README、接线用资源/源码文本检查。**单元测试类路径里没有 Minecraft 类，提示的真实发送、配置热读、专用服务器上的 Curios 来源只能在游戏里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`（宿主上有游戏在跑就等用户退出，不要绕过 `launch.sh` 的检查）；容器里 `-PnoJei`；改配置前备份、验证后还原；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增依赖、mixin、反射；**协议号仍 21（不新增包）**；**`DataRevision` 仍为 6**；客户端面板显示逻辑不改
- **空配置时行为与 059 之前完全一致**（FR-006）：每个改动任务都要保住这一点
- 用户故事：US1 OFF 拒绝交换并提示；US2 GENERIC/OFF 关闭专用适配；US3 取更严者、单人现有行为不变；US4 配置容错与热读；US5 README 与接线测试

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`；类数与用例数记在 `specs/059-server-compat-levels/DELIVERY.md`（新建，中文）

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T002 [P] `…/compat/EffectiveLevel.java`（`stricter(a, b)`：`FULL < GENERIC < OFF`，null 当 FULL；`effective(serverPolicy, clientPolicy, modId)`）+ `…test/compat/EffectiveLevelTest.java`（contracts/levels-and-gate.md §1 的 9 格全部、原版与空/null id 恒 FULL、客户端 `CompatPolicy.DEFAULT` 是中性元素）
- [X] T003 [P] `…/compat/DenialThrottle.java`（`shouldNotify(uuid, containerId, modId)`、`forget(uuid)`，`ConcurrentHashMap`）+ `…test/compat/DenialThrottleTest.java`（contracts §4 的 6 行全部；另有：多线程同时调用同一键只有一个得到 true）
- [X] T004 `…/compat/ServerCompatGate.java`：加纯重载 `allows(Action, CompatLevel)`（OFF 否，其余是）、可替换的档位来源 `setLevelSource(Function<String, CompatLevel>)`（默认全 FULL），`allows(Action, String)` 改为按来源回答；类注释更新（不再"恒为真"）；改 `…test/compat/ServerCompatGateTest.java`：保留"默认来源下恒允许"，加 4 动作 × 3 档的允许表、替换来源后按档位回答、测试结束还原来源
- [X] T005 `…/compat/BridgeDispatch.java`：构造参数加 `Predicate<String> levelAllows`；`tryInsert` 里在 `state.isActive` 之后问它，不允许就跳过（**不是失败**：不调回调、不改状态）；类注释更新；改 `…test/compat/BridgeDispatchTest.java`（现有 helper 传 `id -> true`；加：档位不允许时不问 `handles`、不记失败、之后档位允许了又可用；两个桥其中一个被档位跳过另一个照常）
- [X] T006 `…/inventory/worn/WornSource.java`：加 `default boolean active(P player) { return active(); }`；`…/inventory/worn/BackpackSearch.java`：`firstWorn`、`resolve` 里改问 `source.active(player)`；`activeSource` 加重载 `activeSource(int id, P player)`（问 `active(player)`），原 `activeSource(int id)` 保留（问 `active()`）；改 `…test/inventory/worn/BackpackSearchTest.java`（假来源覆盖 `active(Fake)`：同一来源对"服务端玩家"不活跃、对"客户端玩家"活跃，`firstWorn`/`resolve`/`activeSource(id, player)` 各按玩家回答；原有测试不变）
- [X] T007 运行 `./gradlew compileJava test`

## Phase 3: Foundational（Minecraft 层）

- [X] T008 `…/compat/ThirdPartyConfig.java`：在同一个 `[compat]` 节里加 `modLevels`（`defineListAllowEmpty("modLevels", List.of(), () -> "modid=FULL", element -> element instanceof String)`，注释按 contracts/config-and-texts.md §1）并提供 `rawModLevels()`（未载入返回 null）；更新类注释
- [X] T009 `…/compat/ServerCompatLevels.java`：服务端 `CompatPolicy` 的缓存（`rawModLevels()` 的列表对象不变就不重解析；变了才 `CompatPolicy.parse`，每个 `Warning` 写一条 WARN `Server config compat.modLevels: …`，同一个列表对象只写一次；未载入 → `CompatPolicy.DEFAULT`）；`effective(modId)` = `EffectiveLevel.effective(serverPolicy, CompatConfig.policy(), modId)`；`specificAdaptation(modId)` = `effective(modId) == FULL`
- [X] T010 `…/compat/ServerCompatDenials.java`：`allows(ServerPlayer, AbstractContainerMenu, Action)`（owner = `MenuOwner.ownerOf(menu)`；`ServerCompatGate.allows(action, owner)` 为真 → true；否则 `DenialThrottle.shouldNotify(player.getUUID(), menu.containerId, owner)` 为真时发 `compat.server_off`（带 `message.mcfloatingbackpack.prefix` 的同 058 `say` 写法），DEBUG 日志每次一条，返回 false）；`forget(UUID)`
- [X] T011 语言键 `message.mcfloatingbackpack.compat.server_off`（en/zh，contracts §2）
- [X] T012 `…/MCFloatingBackpack.java`：模组构造器里 `ServerCompatGate.setLevelSource(ServerCompatLevels::effective)`；已有的 `PlayerContainerEvent.Close` 监听对服务端玩家加 `ServerCompatDenials.forget(player.getUUID())`；`PlayerLoggedOutEvent` 里同样清除
- [X] T013 `…/compat/NetworkStorageBridges.java`：构造 `BridgeDispatch` 时传 `ServerCompatLevels::specificAdaptation`
- [X] T014 运行 `./gradlew compileJava test`

## Phase 4: User Story 1 - OFF 拒绝交换并提示 (Priority: P1) 🎯 MVP

**Goal**: 服务端 `somemod=OFF` 后，7 处门禁调用对该模组的菜单一律拒绝、物品不动、每次打开界面恰好一条提示。

**Independent Test**: quickstart 情形 2～4。

- [X] T015 [US1] 把 7 处 `ServerCompatGate.allows(Action.X, MenuOwner.ownerOf(menu))` 换成 `ServerCompatDenials.allows(player, menu, Action.X)`：`network/BackpackQuickTransferPayload.java`（`backpackOut`，`player.containerMenu`）、`BackpackTakeFromContainerPayload.java`、`MergePanelCursorPayload.java`（`serverPlayer`）、`BackpackCursorGesturePayload.java`、`BackpackClickPayload.java`、`BackpackTrashPayload.java`（`add`、`take` 两处）；`BackpackTrashSendPayload.java` **不改**；清理不再用的 `MenuOwner`/`ServerCompatGate` import；每处询问仍在状态改变之前
- [X] T016 [US1] 接线测试 `…test/resources/ServerCompatWiringTest.java`（源码文本）：7 处调用都是 `ServerCompatDenials.allows(`，`network/` 下除 `BackpackTrashSendPayload` 外不再直接调用 `ServerCompatGate.allows(`；每处询问出现在该方法里第一处改动背包/容器的调用之前（以现有的 `return;` 结构为准）；`ServerCompatDenials` 先问 `ServerCompatGate.allows`、再问 `shouldNotify`、DEBUG 日志；`MCFloatingBackpack` 里 `Close` 与登出清限流、启动设档位来源
- [X] T017 [US1] 资源测试 `…test/resources/ServerCompatResourcesTest.java`：两份语言文件都有 `compat.server_off`、占位符数一致（1 个）、zh 含"服务器"、en 含 "server setting"
- [X] T018 运行 `./gradlew compileJava test`

**Checkpoint**: OFF 拒绝与提示可用，等游戏内验证

## Phase 5: User Story 2 - GENERIC/OFF 关闭专用适配 (Priority: P1)

**Goal**: 有效档位不是 FULL 时，集成终端桥、Curios、Accessories 背着来源在服务端不生效；客户端渲染侧不变。

**Independent Test**: quickstart 情形 6、8、9。

- [X] T019 [US2] `…/compat/curios/CuriosWornSource.java` 与 `…/compat/accessories/AccessoriesWornSource.java`：覆盖 `active(LivingEntity entity)`：`entity.level().isClientSide()` → `CompatConfig.policy().specificAdaptation(id)`（原样），否则 `ServerCompatLevels.specificAdaptation(id)`；无参 `active()` 保留；更新注释（去掉"专用服务器上恒为 FULL"的说法）
- [X] T020 [US2] `…/inventory/worn/WornBackpacks.java`：`first(entity)`、`get(player,…)`、`set(player,…)` 里的 `activeSource(id)` 改成 `activeSource(id, entity/player)`；`activeSource(int id)` 的公共方法如无外部调用则带上玩家参数或保留重载（按编译与调用点决定）
- [X] T021 [US2] 接线测试补进 `ServerCompatWiringTest`：两个来源都覆盖 `active(LivingEntity`、含 `isClientSide()` 与 `ServerCompatLevels.specificAdaptation(`、客户端分支仍是 `CompatConfig.policy().specificAdaptation(`；`BackpackSearch` 里不再有 `source.active()`（无参）而是 `source.active(player)`；`NetworkStorageBridges` 传 `ServerCompatLevels::specificAdaptation`
- [X] T022 运行 `./gradlew compileJava test`

## Phase 6: User Story 3 - 取更严者，单人现有行为不变 (Priority: P2)

- [X] T023 [US3] 测试补进 `EffectiveLevelTest`/`ServerCompatWiringTest`：`ServerCompatLevels.effective` 用 `CompatConfig.policy()`（不判断 dist）；空服务端配置时 `effective` 恒等于客户端档位（任意客户端 `CompatPolicy` 与模组 id）——用纯 `EffectiveLevel` 以空 `CompatPolicy.DEFAULT` 作服务端档位验证
- [X] T024 运行 `./gradlew compileJava test`

## Phase 7: User Story 4 - 配置容错、热读、原版不可配置 (Priority: P2)

- [X] T025 [US4] 测试：`…test/compat/CompatPolicyTest.java` 已覆盖解析规则，补一条"服务端与客户端用同一个 `CompatPolicy.parse`"的源码文本断言（`ServerCompatLevels` 调用 `CompatPolicy.parse`）；资源测试补进 `ServerCompatResourcesTest`：`ThirdPartyConfig` 的 `modLevels` 注释含 "on the server"、"stricter"、"GENERIC"、"OFF"、"Vanilla (minecraft) cannot be configured"、"without reopening"、例子；默认列表为空；`defineListAllowEmpty("modLevels"`
- [X] T026 运行 `./gradlew compileJava test`

## Phase 8: User Story 5 - README 与防漂移 (Priority: P2)

- [X] T027 [P] [US5] `README.md`：把 058 写的 "The per-mod levels (`compat.modLevels`) are a **client** setting …" 一条替换为 contracts/config-and-texts.md §4 的新说明（保留 "Key conflict in Integrated Terminals' screens" 一条）
- [X] T028 [P] [US5] `README_CN.md`：中文版同义
- [X] T029 [US5] 改 `…test/resources/ThirdPartyDriftTest.java` 里 058 的 `theReadmesSayThePerModLevelsAreAClientSetting`：改成断言新说明的关键词（en：`serverconfig/mcfloatingbackpack-server.toml`、`stricter`、`once per opened screen`；zh：对应中文词），并确认旧句 "暂时不能单独关闭"/"cannot be switched off separately yet" 已不在两份 README 里
- [X] T030 [P] [US5] `docs/plan/third-party-compat.md` 的状态行补一句"按模组的服务端档位见 specs/059-server-compat-levels/"；`docs/plan/README.md` 不需要改
- [X] T031 运行 `./gradlew compileJava test` 与 `./gradlew build -PcleanRuntime -PnoJei`

## Phase 9: 游戏内验证与交付

- [X] T032 游戏内验证（AI 自动验证，容器，quickstart 第二节 A 的情形 1～8；改服务端配置前备份 `/work/run/config/mcfloatingbackpack-server.toml`；**情形 5（不重开世界热改）是 FR-004 的唯一证据，必须做；若热读不成立，停下来回 research R5**）：每项写实际验证了什么，做不了的写"未验证"
- [ ] T033 游戏内验证（专用服务器，quickstart 第二节 B 的情形 9、10；用 `scripts/ai-gui/multi-setup.sh` 建 `mcfb-srv` + `mcfb-c1`，服务端 `online-mode=false`）；起不来或资源不够就写"未验证"并说明原因，不硬凑
- [X] T034 `specs/059-server-compat-levels/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么 / 没验证什么、协议号仍 21 与 `DataRevision` 仍 6 的理由、已知局限（客户端不知道服务端档位；背部层不一致；提示限流只在内存）、宪法复查
- [X] T035 最终检查：`./gradlew compileJava test` 与 `./gradlew build -PcleanRuntime -PnoJei` 全部通过；容器里的配置已还原；无临时修改残留；**不提交、不打 tag**
- [ ] T036 用户确认：真机上被拒绝时的提示文字与时机、README 说明；**只有用户明确确认才勾**

## Dependencies

- T002、T003 可并行；T004 依赖 T002（用 `CompatLevel`，实际独立）；T005 独立；T006 独立；T007 在其后。
- T008 独立；T009 依赖 T002、T008；T010 依赖 T003、T004、T009、T011；T011 独立；T012 依赖 T004、T009、T010；T013 依赖 T005、T009；T014 在其后。
- US1：T015 依赖 T010；T016、T017 依赖 T015、T011；T018 在其后。
- US2：T019 依赖 T006、T009；T020 依赖 T006；T021 依赖 T013、T019、T020；T022 在其后。
- US3：T023 依赖 T002、T009。
- US4：T025 依赖 T008、T009。
- US5：T027、T028 可并行；T029 依赖 T027、T028；T030 独立；T031 在其后。
- T032、T033 依赖全部代码与文档；T034 在其后；T035、T036 最后。

## Implementation Strategy

- **MVP**：Phase 1～4（OFF 拒绝并提示）：这是服主最需要的"安全阀"。
- Phase 5（专用适配）、6～8 依次加；每个 Phase 一次 `./gradlew compileJava test`，检查点都应是绿的。
- **保住空配置的行为**：T004、T006 的改动都在"默认来源/默认方法"下与旧行为相同，用现有测试证明。
- 热读（T032 情形 5）是唯一依赖 NeoForge 行为的假设；不成立就先回 research R5 再继续。
- 不提交；做完、用户确认后由用户决定何时提交。
