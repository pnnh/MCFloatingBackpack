# Tasks: 第三方模组兼容开关与客户端配置，并把依赖规则变成自动检查

**Input**: Design documents from `/specs/020-compat-switches/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/compat-config.md, quickstart.md；宪法 2.1.0（原则 VII）

**Tests**: 配置解析与三档查询（`CompatPolicy`）、服务端门禁（`ServerCompatGate`）、依赖规则检查器（`DependencyRules`，含三类反例）都是不依赖 Minecraft 的纯逻辑，plan 要求用单元测试覆盖，**测试任务排在对应实现之前**；配置的实时生效、OFF 的真实表现、菜单命名空间归属、JEI 三档、配置界面只能在客户端会话里验证（宪法 V 条），已作为任务列出，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的纯逻辑基础（`CompatLevel`/`CompatPolicy`）；US1（OFF：容器类模组的界面上面板不互动）是 MVP；US2（JEI 的三档）、US3（配置界面与坏配置不崩溃）是另外两个 P1；US4（依赖规则自动检查）与 US5（服务端恒真门禁）彼此独立，可并行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US5）
- 路径都相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，有正在运行的客户端就不要启动、更不要杀用户的游戏**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过
- 包名前缀 `pnnh.mcfloatingbackpack`，下文路径里的 `src/main/java/…` 省略为 `…/`

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码与测试类数量（当前 38 个测试类左右，以实际为准）；结果记到 `specs/020-compat-switches/DELIVERY.md`
  - **结果**：基线 `./gradlew compileJava test` 通过，38 个测试类；开始时发现有一个客户端进程在运行（pid 33478），**没有启动或终止任何客户端**

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 配置字符串到"模组 id → 档位"的解析与三个查询是所有故事共用的纯逻辑；先写测试、先失败。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/compat/CompatPolicyTest.java`（**先写，先失败**），按 data-model.md 与 contracts/compat-config.md 的表：`CompatLevel.parse`（`FULL`/`full`/` Off `/`GENERIC` 认得，`HALF`、空串、null 返回 null）；`CompatPolicy.parse(List<String>)` 返回策略与警告列表——合法条目生效；`x=off`/`x=Off`/`x=OFF` 都是 OFF；两侧空白被去掉；档位认不出（`x=HALF`）→ 该条按 FULL 且产生 1 条警告；id 非法（`Bad Id=OFF`、`=OFF`、`abc`（没有 `=`）、含大写、含连字符之外的符号）→ 忽略且产生警告；`minecraft=OFF` → 忽略且产生警告；同一 id 出现多次 → 取最后一条且产生警告；一条合法 + 一条非法 → 合法那条仍生效；空列表/null → 全部 FULL 且无警告。三个查询：`levelOf`（没记录 FULL、`minecraft` 恒 FULL、其余取记录）、`allowsInteraction`（仅 OFF 为假；owner 为 null/空为真）、`specificAdaptation`（仅 FULL 为真）；再加：`jei=OFF` 与 `jei=GENERIC` 的 `specificAdaptation` 都为假；`sophisticatedbackpacks=GENERIC` 的 `allowsInteraction` 为真
  - **结果**：`CompatPolicyTest` 12 个用例通过
- [X] T003 [P] 新建 `…/compat/CompatLevel.java` 与 `…/compat/CompatPolicy.java`：纯逻辑（无 Minecraft 依赖）。`CompatLevel`：`FULL`、`GENERIC`、`OFF`，`static CompatLevel parse(String)`（去空白、不区分大小写，认不出返回 null）。`CompatPolicy`：`static Parsed parse(List<String>)`（`Parsed` 含 `policy()` 与 `warnings()`），`levelOf`、`allowsInteraction`、`specificAdaptation`，常量 `static final CompatPolicy DEFAULT`；规则逐条照 research Decision 2。使 T002 通过
- [X] T004 运行 `./gradlew compileJava test`，确认 T002 与全部既有测试通过

**Checkpoint**: 编译通过；`CompatPolicyTest` 绿；既有测试不变。

---

## Phase 3: User Story 1 - 把冲突的模组设为 OFF，面板不再出现在它的容器界面上 (Priority: P1) 🎯 MVP

**Goal**: 客户端配置 `modLevels`（先用改配置文件的方式使用）；容器界面所属模组为 OFF 时，该界面按 019 的"其它界面"处理；原版容器与未列出的模组不受影响；OFF 界面上按热键给提示；面板首次挂在非原版模组界面上时记一行日志。

**Independent Test**: quickstart 一（1–3）、二（4–8）：`runClientCompat` 下默认挂在精妙背包界面上并读到日志里的模组 id；写 `sophisticatedbackpacks=OFF` 后面板隐藏、Ctrl 转移不触发、热键给提示、关闭后恢复；原版箱子与旅行者背包不受影响。

- [X] T005 [US1] 新建 `…/compat/CompatConfig.java`：`ModConfigSpec` 的 `CLIENT` 配置，`MOD_LEVELS = builder.comment(<见 contracts/compat-config.md §1 的说明>).translation("config.mcfloatingbackpack.modLevels").defineListAllowEmpty("modLevels", List.of(), () -> "modid=FULL", e -> e instanceof String)`（**元素校验器只接受字符串**，research Decision 1）；`public static CompatPolicy policy()`：取 `MOD_LEVELS.get()`，列表引用与上次相同直接返回缓存，不同则 `CompatPolicy.parse` 并把每条警告写 `MCFloatingBackpack.LOGGER.warn`；`get()` 抛 `IllegalStateException`（配置未加载）时返回 `CompatPolicy.DEFAULT`。修改 `…/MCFloatingBackpack.java`：构造函数增加 `ModContainer modContainer` 参数并 `modContainer.registerConfig(ModConfig.Type.CLIENT, CompatConfig.SPEC)`。依赖 T003
- [X] T006 [P] [US1] 新建 `…/compat/MenuOwner.java`：`static String ownerOf(AbstractContainerMenu menu)`——取 `menu.getType()`，在 `BuiltInRegistries.MENU` 里查键并返回命名空间；`getType()` 抛 `UnsupportedOperationException`、类型未注册、menu 为 null 时返回 `"minecraft"`；`static String ownerOf(Screen screen)`：`screen instanceof AbstractContainerScreen<?> c` 则取 `c.getMenu()` 的归属，否则返回 `"minecraft"`（不是容器界面的不在本判断范围）。服务端与客户端共用（只用 `net.minecraft` 类型，不用客户端专有类以便服务端也能加载——把 `Screen` 版本放进 `@OnlyIn(Dist.CLIENT)` 的内部类或单独方法并避免在服务端触发类加载）
  - **实现偏离**：`MenuOwner` 只提供 `ownerOf(AbstractContainerMenu)`（服务端安全，只用 `net.minecraft`）；`Screen` 版本不单列，`ContainerScreenCompat.isOpenContainerScreen` 直接用 `AbstractContainerScreen.getMenu()` 取归属
- [X] T007 [US1] 修改 `…/compat/ContainerScreenCompat.java`：`isOpenContainerScreen(screen)` 在原有判断为真之后，再要求 `CompatConfig.policy().allowsInteraction(MenuOwner.ownerOf(screen))`；类名匹配那条分支（`identifyByClassName` 的 SB/TB）同样要过这一判断；更新方法注释说明 OFF 的效果。修改 `…/client/panel/QuickTransferInput.java`：`supports(screen)` 额外要求 `ContainerScreenCompat.isOpenContainerScreen(screen)`；其 `invalid(...)` 增加 `|| !FloatingPanelEvents.hostsPanel(minecraft.screen)`。修改 `…/client/panel/PanelDragInput.java` 与 `…/client/panel/PanelSelectionInput.java` 的 `invalid(...)`：同样增加 `|| !FloatingPanelEvents.hostsPanel(minecraft.screen)`（运行中把某模组改成 OFF 时安全取消手势，不发任何请求）。依赖 T005、T006
- [X] T008 [P] [US1] 新建 `…/compat/AttachNotice.java`：`@OnlyIn(Dist.CLIENT)`，`static void onAttach(String ownerModId)`——`ownerModId` 为 `"minecraft"` 或已记过时直接返回；否则把它加进静态 `Set<String>` 并写一行 INFO："面板挂在 `<modid>` 的界面上；若有冲突，可在配置 `modLevels` 里加入 `<modid>=OFF`"（FR-018，每个模组每次启动一次）。修改 `…/client/FloatingPanelEvents.java` 的 `onScreenOpening`：容器界面（`isOpenContainerScreen` 为真）时调用 `AttachNotice.onAttach(MenuOwner.ownerOf(neu))`
- [X] T009 [US1] 修改 `…/client/FloatingPanelEvents.java` 的 `onKey`：在 `!allowsPanel(minecraft.screen)` 即将 `return` 之前，若当前界面是 `AbstractContainerScreen` 但 `!ContainerScreenCompat.isOpenContainerScreen(screen)`（即被配置为 OFF），用 `minecraft.player.displayClientMessage(Component.translatable("message.mcfloatingbackpack.panel_off", owner), true)` 给 action bar 提示；修改 `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` 与 `zh_cn.json` 增加 `message.mcfloatingbackpack.panel_off`（英文如 "Floating panel is turned off for %s's screens in the config"；中文如"配置里已关闭浮动面板在 %s 的界面上显示"）。依赖 T007
- [X] T010 [US1] 运行 `./gradlew compileJava test`，确认全部测试通过；`grep -rn "ContainerScreenCompat.isOpenContainerScreen\|hostsPanel" src/main/java` 核对 OFF 的落点只有 `isOpenContainerScreen` 与 `QuickTransferInput.supports`（contracts §2）
  - **结果**：测试通过（39 个测试类、332 个用例）；`isOpenContainerScreen` 的使用点（`hostsPanel`、`HostScreenManager.kindOf`、渲染光标来源、`screenOpen` 标志）都经过同一判断，`QuickTransferInput.supports` 另加同一判断，与 contracts §2 一致
- [ ] T011 [US1] 按 `quickstart.md` 一（1–3）、二（4–8）在 `./gradlew runClientCompat` 逐项验证（先 `pgrep`，**用改配置文件的方式**添加条目；改后用游戏内重载或重进世界即可，实际要不要重启也如实记录）：默认挂载与 Ctrl 转移回归、日志里的模组 id（**如实记下精妙背包、旅行者背包界面的命名空间**，核对是不是 `sophisticatedbackpacks`/`travelersbackpack`/`sophisticatedcore`）、OFF 后面板隐藏/不转移/关闭后恢复、热键提示、原版容器与旅行者背包不受影响、运行中改成 OFF 时手势取消且物品守恒；结果记到 DELIVERY.md（没做的项记"未验证"，**由用户确认后才打勾**） ——2026-11-11 AI 自动验证：1（精妙背包与旅行者背包界面）、2（模组 id：`sophisticatedbackpacks`、`travelersbackpack`）、4、5（面板回来）、6、8（面板隐藏）通过；箱子/工作台、Ctrl 转移、手势取消、GENERIC 没测；详见 DELIVERY.md，未勾
  - **状态（2026-11-05）**：客户端验证**未做**，等用户确认后才打勾（注意：做之前需要用户的客户端已退出，我不会启动或终止它）

**Checkpoint**: US1 达成 FR-001～FR-007、FR-009、FR-018、SC-001、SC-002、SC-009（FR-002 的坏配置容错由 US3 验证）。

---

## Phase 4: User Story 2 - 对有专用适配的模组，三档的实际表现不同（JEI） (Priority: P1)

**Goal**: JEI 的避让适配受配置控制：FULL 避让；GENERIC 与 OFF 不避让；改配置无需重启。

**Independent Test**: quickstart 三（9–11）：默认 JEI 避让面板；`jei=GENERIC` 后不再避让；来回切换 5 次无需重启。

- [X] T012 [US2] 修改 `…/compat/jei/FloatingBackpackJeiPlugin.java`：`getGuiExtraAreas()` 在原有"面板可见才返回区域"之前先判断 `CompatConfig.policy().specificAdaptation("jei")`，为假返回 `Collections.emptyList()`；更新类注释说明受 `modLevels` 控制（FULL 才避让）。依赖 T005
  - **实现说明**：`getGuiExtraAreas` 先判断 `specificAdaptation("jei")`，为假返回空列表；JEI 每次布局都会询问，所以改配置不需要重启
- [X] T013 [US2] 运行 `./gradlew compileJava test`，确认通过；`grep -rn "mezz.jei" src/main/java` 确认第三方类型仍只在 `compat/jei/`
  - **结果**：测试通过；`grep -rln "mezz.jei" src/main/java` 只命中 `compat/jei/FloatingBackpackJeiPlugin.java`
- [ ] T014 [US2] 按 `quickstart.md` 三（9–11）在 `./gradlew runClientCompat` 验证（先 `pgrep`）：默认避让；`jei=GENERIC` 后不再避让并**记录多久之后生效**；`jei=OFF` 与 GENERIC 相同；删除条目/改回 FULL 又避让；来回切换 5 次无需重启；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI 自动验证：9～11 全部通过：GENERIC 约 1～2 秒生效、无需重开界面，OFF 与 GENERIC 相同，来回切换 5 次无需重启；可由你确认后勾；详见 DELIVERY.md，未勾
  - **状态（2026-11-05）**：客户端验证**未做**，等用户确认后才打勾（需用户的客户端先退出）

**Checkpoint**: US2 达成 FR-008、SC-003。

---

## Phase 5: User Story 3 - 配置界面，坏配置不崩溃 (Priority: P1)

**Goal**: 在游戏"模组"列表里能打开配置界面（含三档说明、中英文）；各种坏写法都不崩溃、坏条目只影响自己。

**Independent Test**: quickstart 四（12–13）。

- [X] T015 [US3] 修改 `…/client/ClientSetup.java`：`register(IEventBus, ModContainer)` 里 `modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`（对应改 `MCFloatingBackpack` 里的调用）；修改 `src/main/resources/assets/mcfloatingbackpack/lang/en_us.json` 与 `zh_cn.json` 增加 `config.mcfloatingbackpack.modLevels`（配置项标题）与 `config.mcfloatingbackpack.modLevels.tooltip`（三档含义；容器类模组 FULL 与 GENERIC 相同、要解决冲突用 OFF；JEI 的 OFF 等同 GENERIC；`minecraft` 不可配置；示例 `sophisticatedbackpacks=OFF`），并增加 NeoForge 配置界面需要的标题键（以 `ConfigurationScreen` 实际查找的键为准：读源码确认键名后再加，如 `mcfloatingbackpack.configuration.title`）；中英文键集合必须相同。依赖 T005
  - **实现说明**：读 `ConfigurationScreen` 源码确认键名：标题 `<modid>.configuration.title`、客户端文件分区 `<modid>.configuration.section.mcfloatingbackpack.client.toml`、子分区 `<modid>.configuration.<key>`、值的标签 `translation()` 指定的键、提示 `<键>.tooltip`；新增 `mcfloatingbackpack.configuration.*` 与 `config.mcfloatingbackpack.modLevels(.tooltip)` 中英文
- [X] T016 [US3] 更新 `src/test/java/pnnh/mcfloatingbackpack/resources/TagManagerResourcesTest.java` 或新建 `ConfigResourcesTest.java`：两份语言文件都含 T015 新增的全部键且键集合相同（沿用现有文本检查方式）；运行 `./gradlew compileJava test`
  - **结果**：新建 `CompatConfigResourcesTest`（2 个用例）：两份语言文件含全部新键且键集合相同；配置项的 `translation` 键存在。`./gradlew compileJava test` 通过
- [ ] T017 [US3] 按 `quickstart.md` 四（12–13）在 `./gradlew runClientCompat` 验证（先 `pgrep`）：逐个写入 10 种坏配置（`HALF`、`Bad Id=OFF`、`=OFF`、`abc`、`minecraft=OFF`、小写 `off`、同一 id 两条、一条合法一条非法、没装的模组 id、整个文件乱码）并启动，**每种都要进得了世界**，核对 `latest.log` 的 WARN；配置界面里三档说明（中英文）可见，列表项能否新增/修改/删除（**不能就如实记为已知限制**，改文件仍可用）；保存后是否立即生效；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI 自动验证：12 的各种坏写法（运行中改文件）与启动时整个文件乱码通过，13 的配置界面没测，启动时其它坏配置没测；详见 DELIVERY.md，未勾
  - **状态（2026-11-05）**：客户端验证**未做**，等用户确认后才打勾（需用户的客户端先退出）

**Checkpoint**: US3 达成 FR-001～FR-002（含容错）、FR-010、FR-011、SC-004、SC-007。

---

## Phase 6: User Story 4 - 依赖规则由自动检查守住 (Priority: P2)

**Goal**: 宪法 VII 的三类规则变成会失败的测试，三类反例各被判为失败，真实仓库通过。

**Independent Test**: quickstart 六（16–17）。

- [X] T018 [P] [US4] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/rules/DependencyRulesTest.java`（**先写，先失败**）：用内联夹具文本测三个检查——`checkModsToml`：夹具里 `neoforge`/`minecraft` required + 一个 optional 通过；把某个第三方改成 `required` → 失败并指出 modId；`checkImports`：`pnnh.mcfloatingbackpack.*`、`net.minecraft.*`、`java.util.*` 等白名单通过；`compat/jei/X.java` 里 `import mezz.jei.api...` 通过；`client/panel/Y.java` 里 `import mezz.jei.api...` → 失败并指出文件与 import；`import static` 同样检查；`checkBuildGradle`：`compileOnly "mezz.jei:..."`、`localRuntime "curse.maven:..."`、`localRuntime "maven.modrinth:..."` 通过；`implementation "curse.maven:..."`、`runtimeOnly "mezz.jei:..."` → 失败；`includeGroup "curse.maven"`、仓库 `url = "https://cursemaven.com"` 这类非依赖声明行不被误判；再加三个对**真实仓库文件**的用例（读 `src/main/templates/META-INF/neoforge.mods.toml`、遍历 `src/main/java`、读 `build.gradle`），当前必须通过
  - **结果**：`DependencyRulesTest` 13 个用例（检查器的 10 个 + 对真实仓库的 3 个）
- [X] T019 [P] [US4] 新建 `src/test/java/pnnh/mcfloatingbackpack/rules/DependencyRules.java`（测试源码里的纯 Java 检查器，无外部库）：`checkModsToml(String)`、`checkImports(List<SourceFile>)`、`checkBuildGradle(String)`，各返回违规列表（文件/块 + 原因，空表为通过）；白名单前缀与规则照 research Decision 10。使 T018 通过
  - **实现说明**：`DependencyRules` 在测试源码里（`src/test/.../rules/`）；`compat/` 根目录下的文件不算 `compat/<modid>/`，引用第三方类型会失败（有用例）
- [X] T020 [US4] 运行 `./gradlew compileJava test`，确认 T018 通过；**负向验证**（自己做，做完必须改回）：①临时把 `src/main/templates/META-INF/neoforge.mods.toml` 里 `jei` 的 `type="optional"` 改成 `"required"` 跑测试，应失败；②临时在 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java` 加一行 `import mezz.jei.api.IModPlugin;` 跑测试，应失败；③临时把 `build.gradle` 里 `localRuntime "mezz.jei:jei-1.21.1-neoforge:…"` 改成 `runtimeOnly` 跑测试，应失败；三次都**还原后**再跑一次确认通过，并用 `git status` 确认这三个文件没有残留改动；结果记到 DELIVERY.md
  - **结果**：三次临时破坏都使 `DependencyRulesTest` 失败，还原后通过，`git status` 确认无残留；第一次用 BSD `sed` 没有插入成功，改用 Python 重做（见 DELIVERY.md）

**Checkpoint**: US4 达成 FR-014、SC-005。

---

## Phase 7: User Story 5 - 服务端预留门禁，现阶段不改变任何行为 (Priority: P3)

**Goal**: 服务端 5 个入口先问同一个恒真的门禁；行为与改动前一字不变。

**Independent Test**: quickstart 六（18）。

- [X] T021 [P] [US5] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/compat/ServerCompatGateTest.java`（**先写，先失败**）：对 `Action` 的每个取值 × 一组归属（`"minecraft"`、`"sophisticatedbackpacks"`、`"travelersbackpack"`、`"jei"`、`""`、null）`allows` 全部返回 `true`
  - **结果**：`ServerCompatGateTest` 2 个用例通过
- [X] T022 [P] [US5] 新建 `…/compat/ServerCompatGate.java`：纯逻辑，`enum Action { DELIVER_TO_CONTAINER, TAKE_FROM_CONTAINER, MERGE_CURSOR, USE_MENU_CURSOR }`，`static boolean allows(Action, String ownerModId)` 恒返回 `true`；类注释说明"以后有服务端名单时只改这里"。使 T021 通过
- [X] T023 [US5] 在服务端 5 个入口先问门禁（`ServerCompatGate.allows(<动作>, MenuOwner.ownerOf(player.containerMenu))`，为假直接 `return`，不改数据、不回包）：`…/network/BackpackTakeFromContainerPayload.java`（取出前，`TAKE_FROM_CONTAINER`）、`…/network/BackpackQuickTransferPayload.java`（向打开的容器投递的 `hasOpenContainer` 分支，`DELIVER_TO_CONTAINER`）、`…/network/MergePanelCursorPayload.java`（`MERGE_CURSOR`）、`…/network/BackpackClickPayload.java` 与 `…/network/BackpackCursorGesturePayload.java`（`screenOpen` 时使用菜单携带物的分支，`USE_MENU_CURSOR`）。依赖 T006、T022
  - **实现说明**：5 个入口都先问门禁；`BackpackQuickTransferPayload` 放在 `backpackOut` 的 `hasOpenContainer` 之后，被拒绝时该次转出不生效
- [X] T024 [US5] 运行 `./gradlew compileJava test`，确认 T021 与全部既有测试通过；`grep -rn "ServerCompatGate.allows" src/main/java` 应命中 5 个入口；`git diff --stat` 确认**没有**改动任何网络包的编码或协议版本（仍为 `"8"`）
  - **结果**：测试通过（42 个测试类、349 个用例）；`grep` 命中 5 个入口；没有改动任何网络包编码，协议仍为 `"8"`
- [ ] T025 [US5] 按 `quickstart.md` 六（18）重跑 011（Ctrl 转移）、012（转出到打开的容器、从容器转入）、013（光标拖动手势）的手测场景，结果与改动前一致；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **状态（2026-11-05）**：客户端验证**未做**，等用户确认后才打勾（需用户的客户端先退出）

**Checkpoint**: US5 达成 FR-012、SC-006。

---

## Phase 8: Polish & Cross-Cutting

- [X] T026 [P] 更新文档：`specs/008-panel-title-icon-and-jei-safe-zone/spec.md` 与 `docs/MCFloatingBackpack.md` 加 020 说明（JEI 避让受 `modLevels` 控制；配置项、三档含义、原版不可配置、容器类模组 FULL 与 GENERIC 相同、OFF 的效果、OFF 对只叠加界面的模组等同 GENERIC、019 的按键转发不受影响）；在 `specs/019-fallback-host-screen/spec.md` 顶部加一句 020 说明（OFF 的模组界面按"其它界面"处理，复用 019 语义）；**不要修改 `docs/ideas`**
  - **结果**：008 与 019 的 spec、`docs/MCFloatingBackpack.md` 已加 020 说明；未改 `docs/ideas`
- [X] T027 静态检查：`git diff --stat` 确认没有改动 011/013/014 输入类的逻辑（只有 T007 的 `invalid`/`supports` 小改）；没有新增必需依赖（`neoforge.mods.toml` 无改动）；没有新增 mixin；`grep -rn "setScreen(" src/main/java` 与 019 一致；协议版本仍为 `"8"`；`DependencyRulesTest` 通过
  - **结果**：见 DELIVERY.md 静态检查一节，全部符合
- [ ] T028 按 `quickstart.md` 五（14–15）、六（19–20）观察并记录：JourneyMap 全屏地图（没装就记"未验证"）、小地图 HUD 与面板的重叠（只记录）、`run/logs/latest.log` 无新增报错、文档已更新；结果记到 DELIVERY.md（由用户确认后才打勾）
  - **状态（2026-11-05）**：观察**未做**，等用户确认后才打勾
- [X] T029 新建 `specs/020-compat-switches/DELIVERY.md`（中文）：汇总自动化结果（T001 基线与 T004/T010/T013/T016/T020/T024 的测试结果、新增测试类与用例数）、与 plan/tasks 的偏离、行为变化（新增客户端配置 `modLevels`；OFF 的模组容器界面上面板隐藏）、**每个客户端验证项实际验证了什么 / 未验证什么**（尤其真实精妙背包与旅行者背包界面的命名空间、配置实时生效的实际表现、列表项能否在配置界面里编辑、JEI 切档的生效时机）、已知限制（Windows/Linux 未验证；容器类模组 FULL 与 GENERIC 无区别；EMI/JourneyMap 小地图不适配）、对 019 的依赖
  - **说明**：已写；**所有客户端验证项均如实记为未验证**

---

## Phase 9: 第一轮客户端验证后的修订（2026-11-05）

用户在第一轮验证中提出：①`GENERIC` 下 Ctrl+点击仍能转移物品，问为什么；②OFF 时的热键提示一闪而过，改到玩家自己的聊天区；③配置写错要在聊天区提示；④配置文件"被重置成空数组"的原因。

> **关于①**：我先把 GENERIC 误读成"不交换物品"并改了一版代码（T030–T033 的第一版），用户随后补充说明：FULL = 专用适配（我们为该模组写的专有代码，代表主动兼容，可视为功能增强）；GENERIC = 没有专用适配（关闭这些专有代码，没写过的模组与 FULL 一样），**它不决定**面板能否显示、能否交换物品，那取决于该模组自己的实现；OFF = 面板不显示，二选一。所以那一版被**撤销**，下面的任务记录的是最终状态。

- [X] T030 [P] 修宪：`.specify/memory/constitution.md` 原则 VII 把三档解释清楚（FULL = 专用适配；GENERIC = 没有专用适配，不决定面板能否显示或交换物品；OFF = 面板不显示、与该模组界面二选一），版本 **2.1.1**（PATCH：只补充说明，规则没变）；中间一度写成 2.2.0 "GENERIC 不交换物品"，因为误读，未提交即撤销
- [X] T031 [P] 更新规格与设计文档：`spec.md`（背景第 2 条三档含义、FR-006/FR-007/FR-019、Edge Cases、SC-010/SC-011、修订一节）、`plan.md`（修订一节）、`research.md`（Decision 12）、`data-model.md`（三档含义矩阵、ConfigNotices/ConfigFileWatch）、`contracts/compat-config.md`、`quickstart.md`（二b 说明、12b、12c）、`docs/MCFloatingBackpack.md`、008 的 020 说明、配置文件注释与配置界面提示（中英文）按用户的说明重写
- [X] T032 [P] 先写测试再实现 `CompatPolicy` 的结构化警告（`Warning(Problem, entry)`）：`CompatPolicyTest` 增加各种警告的 `Problem` 与 `entry`、GENERIC 不隐藏面板；新建 `…/compat/ConfigNotices.java` 与 `ConfigNoticesTest`（警告 → 聊天提示、每种问题各有语言键、相同提示只一条、超过 5 条合并"还有 N 条"、只有 10 秒内刚生成的本配置 `.bak` 才算）。（撤销了第一版的 `exchangesItems`）
- [X] T033 （已撤销）第一版曾把 `ContainerScreenCompat` 拆成 `usesMenuCursor`/`blocksItemExchange` 并让 GENERIC 不交换物品；用户补充说明后**全部回退**到原设计：`isOpenContainerScreen`（OFF 为假）仍是唯一的显示判断，光标来源、`screenOpen`、合并光标、Alt 让路、`QuickTransferInput.supports` 都用它，`grep` 确认代码里不再有 `usesMenuCursor`/`blocksItemExchange`/`exchangesItems`
- [X] T034 聊天提示：`FloatingPanelEvents.chat(...)`（只写进本机聊天，前缀 `[浮动背包]`）；OFF 的界面上按热键改为聊天；`onClientTick` 在玩家进入世界后排空 `ConfigNotices`；`CompatConfig.policy()` 重新解析时把警告放进队列；新增 `…/compat/ConfigFileWatch.java`（`ModConfigEvent.Loading/Reloading` 里发现刚生成的 `.bak` 就加一条"文件有错误"）并在 `ClientSetup` 注册；中英文语言键
- [X] T035 运行 `./gradlew compileJava test` 通过；读 FML `ConfigTracker.loadConfig` 与第一轮验证的日志、`run/config/*.bak` 确认"配置被重置"的原因（见 research Decision 12）
- [ ] T036 按 `quickstart.md` 六 12b–12c 在 `./gradlew runClientCompat` 验证（先 `pgrep`，**不要有运行中的客户端**）：OFF 的界面上按热键聊天里有提示且按 T 可再看；各种坏配置进入世界后聊天里有提示；游戏运行时存一个写了一半的文件，聊天里提示备份；结果记到 DELIVERY.md（由用户确认后才打勾） ——2026-11-11 AI 自动验证：12b/12c 主要点通过（热键提示、坏条目聊天提示、写了一半的文件备份并提示；发现该提示出现两次）；"一次最多 5 条"没测；详见 DELIVERY.md，未勾

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1（Setup）**：无依赖，先做。
- **Phase 2（Foundational）**：依赖 Phase 1；T002 → T003 → T004。**阻塞 US1～US3**；US4、US5 不依赖它。
- **Phase 3（US1）**：依赖 Phase 2；T005 → T007、T009；T006 可与 T005 并行；T008 依赖 T006；T010 → T011。
- **Phase 4（US2）**：依赖 T005（`CompatConfig`）；T012 → T013 → T014。
- **Phase 5（US3）**：依赖 T005；T015 → T016 → T017。
- **Phase 6（US4）**：不依赖 Phase 2；T018 ∥ T019 → T020。可与其它阶段并行。
- **Phase 7（US5）**：T021 ∥ T022；T023 依赖 T006（`MenuOwner`）与 T022；T024 → T025。
- **Phase 8（Polish）**：T026 可随时；T027～T029 在所有故事完成后。

### User Story Dependencies

- US1 是 MVP 与其它客户端故事的基础（`CompatConfig` 与 `MenuOwner`）；US2、US3 在 `CompatConfig` 之后可并行；US4、US5 彼此独立、与 US1～US3 基本独立。

### Parallel Opportunities

- Phase 2 之后：T006 ∥ T005；T012 ∥ T015（不同文件，均依赖 T005）；US4 的 T018 ∥ T019 与 US5 的 T021 ∥ T022 可与客户端故事并行。

### Parallel Example

```text
同时：  T018 DependencyRulesTest  T019 DependencyRules   （US4，可随时）
        T021 ServerCompatGateTest T022 ServerCompatGate   （US5，可随时）
先：    T002 → T003 → T004 → T005 ∥ T006 → T007/T009 → T010 → T011
然后：  T012 ∥ T015 → …
```

---

## Implementation Strategy

### MVP First（US1）

1. 完成 Phase 1、Phase 2（纯逻辑 + 测试）。
2. 完成 US1（配置 + 归属 + OFF 的落点 + 热键提示 + 首次挂载日志）；用改配置文件的方式验证。
3. 用户验证 T011（尤其真实模组界面的命名空间）后再继续。

### Incremental Delivery

1. MVP（US1）→ 用户验证。
2. US2（JEI 三档）→ 验证；US3（配置界面与坏配置）→ 验证。
3. US4（依赖规则自动检查）、US5（服务端门禁）→ 随时做，各自验证。
4. Polish：文档、静态检查、观察记录、交付说明。

### 风险提示（实现时盯住）

- **元素校验器**：`MOD_LEVELS` 的元素校验器必须接受任何字符串，否则一个坏条目会让整个列表被纠正回默认（T005、T017）。
- **归属判断**：命名空间不一定等于玩家以为的模组 id（精妙背包的存储类界面可能是 `sophisticatedcore`）；以首次挂载日志读到的为准（T011）。
- **运行中改配置**：同一个界面对象被改成 OFF 时，进行中的手势靠 `invalid` 新条件取消，必须实测物品守恒（T011）。
- **服务端门禁**：现阶段恒真，任何入口都不得因为它改变行为（T024、T025）。
- **依赖检查的误判**：构建文件里的 `includeGroup`、仓库 `url` 不是依赖声明，不得误报（T018）。
