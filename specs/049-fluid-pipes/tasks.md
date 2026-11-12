# Tasks: 水管、油管、岩浆管

**Input**: `/specs/049-fluid-pipes/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 判定、流体归类、冷却用 JUnit（纯 Java）；物品、模型、贴图、配方、语言键用资源测试；接线用源码文本检查。**吸收与放置对真实世界的效果、提示的实际出现、创造与生存的区别、PNC 的石油只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`，`…data/` = `src/main/resources/data/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`（宿主机有用户的客户端在运行时不启动容器客户端）；容器里用 `-PnoJei` 起客户端；改存档前先备份世界；容器见 `scripts/ai-gui/README.md`；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增配置项、网络包、存储字段；协议号仍 19，数据修订仍 2；011～048 的测试不改；PNC 只进 `localRuntime`
- 用户故事：US1 右键吸收；US2 左键放置；US3 满、空与失败的提示；US4 管子物品与配方；US5 用 PNC 的石油验证油管

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 181 个类、1371 个用例）；`git status` 干净（除 049 文档）；记在 `specs/049-fluid-pipes/DELIVERY.md`（新建，中文）；宪法 VI 的"之前"度量：048 之后的图集是 2048x1024（`design/demo/tools/check_atlas_budget.sh`），实现前再跑一次确认并记录（181 个类、1371 个用例，0 失败；图集"之前"：048 收尾时测得 2048x1024，之后没有新增贴图，"之后"一并再测）
- [X] T002 **先验证 PNC 能作为开发运行时依赖**：`build.gradle` 的 `-PcompatRuntime` 那一组（`if (!cleanRuntime)` 里）加 `localRuntime "maven.modrinth:pneumaticcraft-repressurized:8.2.24"` 并写一行注释（用途：049 的原油测试；只在开发运行里，不进发布 jar）；`./gradlew compileJava --offline` 之外还要联网解析一次（`./gradlew dependencies --configuration runtimeClasspath | grep pneumatic`）；解析不了就记录原因并换坐标（`maven.modrinth:pneumaticcraft-repressurized` 的版本串可能要写成 `8.2.24+mc1.21.1`）；`DependencyRulesTest` 照常通过；**不 `compileOnly`、主代码不引用它**（`./gradlew dependencies --configuration runtimeClasspath` 列出 `maven.modrinth:pneumaticcraft-repressurized:8.2.24` 且无解析失败；真正下载在容器同步时发生；`DependencyRulesTest` 通过）

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T003 `…/pipe/PipeKind.java`（WATER/OIL/LAVA、`fluid()`、`itemId()`、`classify(inWater, inLava, inCrudeOil)`、`accepts`）、`…/pipe/Cooldown.java`（`tryAcquire(key, nowTick, ticks)`、`forget(predicate)`）、`…/pipe/PipePlanner.java`（`Situation`、`Plan`、`Notice`、`absorb`、`place`，判定顺序见 data-model.md；用 048 的 `FluidStoreOps` 与 `TankCatalog.capacityMb`，不另写容量规则）（另有 `PipeCooldowns`（两个冷却的集中处）与 `PipeNotices`）
- [X] T004 [P] 单元测试 `…test/pipe/PipePlannerTest.java`：contracts/pipe-rules.md 的"单元测试要覆盖"1～4（判定表逐行、档次无关、原油锁定与放置的流体、同进退与 1000 组随机、空位与液量 999/1000/1001、刚好满与刚好空）
- [X] T005 [P] 单元测试 `…test/pipe/PipeKindTest.java` 与 `CooldownTest.java`：归类（水、岩浆、油、同时多个标签、都不是）；冷却（第一次通过、冷却内不通过且不更新、过后通过、键互不影响、`forget`）
- [X] T006 运行 `./gradlew compileJava test`；故意改错一处（把 `PipePlanner` 吸收的空位判断写成 `< 999`）确认 T004 失败，再还原（变异检查：吸收的空位判断 `< BUCKET_MB` 改成 `< 999`，`PipePlannerTest` 失败，还原后通过）

**Checkpoint**: 判定与冷却就绪，可在不启动游戏的情况下测试

## Phase 3: User Story 4 - 管子物品与配方 (Priority: P2，其余故事要用，所以先做)

- [X] T007 [US4] `…/item/PipeItem.java`（`kind`、`stacksTo(1)`、`appendHoverText`：`pipe.tooltip` 与 `pipe.needs_tank`；`use()` 先只返回 `pass`，T012 再填）；`…/item/ModItems.java`：用 `PipeKind.values()` 注册 3 个（id 为 `<液体>_pipe`）、`PIPES` 映射、`pipe(kind)` 取、创造栏里放在罐子之后、类别扩展之前，顺序水、油、岩浆
- [X] T008 [P] [US4] 扩展 `design/tank-textures/generate.py` 同时生成 3 张管子占位贴图到 `…res/textures/item/<id>.png`（斜放的铁灰色管身，管口一端为液体色）；运行一次，目视检查（生成 22 张：19 罐 + 3 管子；已目视检查管子；罐的贴图重新生成后与之前逐字节相同）
- [X] T009 [P] [US4] 3 个模型 `…res/models/item/<id>.json`；语言键中英文（3 个物品名、2 条物品说明、8 条消息，内容见 contracts）；3 个配方 `…data/recipe/<id>.json`（形状与材料见 contracts/recipes-and-assets.md）；3 个 id 加进 `…data/tags/item/never_trash.json`
- [X] T010 [P] [US4] 资源测试 `…test/resources/PipeResourcesTest.java`：契约"资源测试"一节全部断言
- [X] T011 运行 `./gradlew compileJava test`

**Checkpoint**: 三根管子可合成、可在创造栏取到（还不能用）

## Phase 4: User Story 1 + 3 - 右键吸收与提示 (Priority: P1) 🎯 MVP

- [X] T012 [US1] `…/pipe/PipeActions.java`（MC 层）：`absorb(ServerPlayer, PipeKind, BlockHitResult)`：`BackpackRef.resolve` → `stillValid` → `BackpackGate.acquire`（空则按"被保护"处理）→ 组装 `Situation`（`inventory.tankTier(kind.fluid())`、`inventory.fluids()`、世界里源方块的流体 id）→ `PipePlanner.absorb` → `REFUSE` 则发提示；`DO` 则 `BucketPickup.pickupBlock`，成功（返回非空，或该位置不再是源方块）才 `inventory.setFluids(plan.next())`、`ref.saveInventory`、`BackpackNetwork.sendFluids`，并发"满了"提示；世界没改成不扣不提示；播放 `getPickupSound`、`gameEvent(FLUID_PICKUP)`（`Result` 三态：不是这根管子的液体 / 拒绝 / 完成；`find` 用 `BackpackRef.resolve`、`stillValid`、`BackpackGate.acquire`）
- [X] T013 [US1] `…/pipe/PipeNotices.java`（MC 层）：`send(ServerPlayer, Notice, PipeKind / 流体 id)`：个人聊天（`message.mcfloatingbackpack.prefix` 开头），经 `Cooldown<(UUID, Notice)>`（20 tick，`level.getGameTime()`）过滤；玩家登出时 `forget`（在 `MCFloatingBackpack` 已有的登出监听里加一行）（`PipeCooldowns` 持有两个冷却，登出时 `forget`；时钟用服务端 `getTickCount()`）
- [X] T014 [US1] `PipeItem.use()`：服务端 `!level.isClientSide`；潜行 → `pass`；`getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY)`；非 BLOCK → `pass`；`mayInteract` 与 `mayUseItemAt` 不过 → `fail`；`level.getFluidState(pos)` 是源方块且 `PipeKind.classify(FluidTags.WATER, FluidTags.LAVA, #c:crude_oil)` 等于本管子 → `PipeActions.absorb`，成功 `sidedSuccess`，否则 `fail`；客户端一律 `pass`（`use()` 对非 BLOCK、客户端、副手、潜行返回 pass；`mayInteract`/`mayUseItemAt` 不过返回 fail）
- [X] T015 [P] [US1] `PipeWiringTest.java` 的吸收部分：`use` 里潜行 `pass`、射线用 `SOURCE_ONLY`、`mayInteract`/`mayUseItemAt`、先 `getFluidState` 判源方块与归类再 `absorb`；`absorb` 里改世界（`pickupBlock`）先于 `setFluids`/`saveInventory`，之后 `sendFluids`；找背包用 `BackpackRef.resolve`、`stillValid`、`BackpackGate.acquire`；提示经 `Cooldown`；不新增网络包与配置；主代码不含 `pneumaticcraft`（`PipeWiringTest`，含"什么都没新增"的断言）
- [X] T016 运行 `./gradlew compileJava test`

**Checkpoint**: 可以吸收，提示可用

## Phase 5: User Story 2 - 左键放置 (Priority: P1)

- [X] T017 [US2] `…/pipe/PipeActions.java` 加 `place(ServerPlayer, PipeKind, BlockPos, Direction)`：同样找背包、`PipePlanner.place`；目标位置照抄 `BucketItem.use`（能容纳这种流体的 `LiquidBlockContainer` 用自己，否则点击面的相邻格）；`mayInteract` 与 `mayUseItemAt`；流体 = 水/岩浆的固定流体，油为锁定的 `oil_fluid`（`BuiltInRegistries.FLUID` 取，找不到则提示"这种油的来源模组不在了"并不扣量）；用一次性的 `IFluidHandler` 调 `FluidUtil.tryPlaceFluid(player, level, InteractionHand.MAIN_HAND, pos, handler, new FluidStack(fluid, 1000))`，成功才 `setFluids`/`saveInventory`/`sendFluids`、补 `gameEvent(FLUID_PLACE)`、发"空了"提示；失败不扣不提示（另加两处：目标位置已是同种源方块时不放置不扣（防按住左键空耗）；原油的流体已不在注册表里时提示 `NO_FLUID` 不扣量，`Notice` 增加 `NO_FLUID`，语言键 `pipe.no_fluid`）
- [X] T018 [US2] `…/pipe/PipeEvents.java`：`onLeftClickBlock(PlayerInteractEvent.LeftClickBlock)`：主手是 `PipeItem` → `setCanceled(true)`（两端）；服务端且 `getAction() == START` 且 `Cooldown<UUID>`（4 tick）通过 → `PipeActions.place`；`MCFloatingBackpack` 构造里 `NeoForge.EVENT_BUS.addListener(PipeEvents::onLeftClickBlock)`
- [X] T019 [P] [US2] `PipeWiringTest.java` 的放置部分：`LeftClickBlock` 在主手是管子时取消、只在服务端 `START` 放置、有节流；放置用 `FluidUtil.tryPlaceFluid` 与一次性处理器；位置取法含 `LiquidBlockContainer`；改世界先于提交液量；`gameEvent(FLUID_PLACE)`；监听在 `MCFloatingBackpack` 里注册；协议号仍 19、数据修订仍 2、没有新网络包
- [X] T020 运行 `./gradlew compileJava test`

**Checkpoint**: 吸收与放置都可用，全部代码完成，等游戏内验证

## Phase 6: User Story 5 与收尾 - 游戏内验证与文档

- [X] T021 游戏内验证（AI 自动验证，容器，quickstart B1～B17，**带 PNC**；**不在 `src/main` 留调试入口**）：先验证 PNC 在容器里能起来、`setblock ~ ~ ~ pneumaticcraft:oil` 放出的是源方块；每项写实际验证了什么，做不了的写"未验证"（PNC 在容器里能起来，石油是源方块；做了与没做的逐项见 DELIVERY.md；用改绑到键盘的"使用/攻击"操作，经验写进了 `scripts/ai-gui/README.md`）
- [X] T022 宪法 VI 的"之后"度量：加管子贴图后再跑 `check_atlas_budget.sh`，与 T001 对比，都 ≤ 2048；没量写"未度量"（之后也是 2048x1024，通过）
- [X] T023 [P] `README.md` 与 `README_CN.md`：管子一节（水、油、岩浆；右键吸收、左键放一桶；需要背包底部区的对应罐；提示；潜行右键留给以后；牛奶与蜂蜜随后）；`docs/plan/fluid-tanks.md` 状态改为"049 已实现"，PNC 一节更新为已验证的结论（README 两份、`docs/plan/fluid-tanks.md` 都已更新）
- [X] T024 `specs/049-fluid-pipes/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么/没验证什么、**协议号与数据修订不变的理由**、PNC 作为开发依赖的说明（只在 `localRuntime`）、已知局限（按住左键的节流、没有桶的模组流体）、图集前后尺寸、宪法复查（已写）
- [X] T025 最终检查：`./gradlew compileJava test` 全部通过；`git status` 确认无临时修改残留；`DependencyRulesTest` 通过；发布 jar 里不含 PNC（`./gradlew jar` 后 `unzip -l` 抽查，或写在 DELIVERY 里"未检查"）；**不提交、不打 tag**（186 个类、1418 个用例，0 失败；`DependencyRulesTest` 通过；jar 里没有 PNC；未提交、未打 tag）
- [X] T026 用户确认：用户在真机上试吸收、放置、提示（水、岩浆，有 PNC 时再试油）；**只有用户明确确认才勾**（用户已确认（2026-11-12）：没有问题）

## Dependencies

- T002（PNC 依赖能否解析）独立，但越早越好，失败时 US5 与 T021 的方案要调整。
- T003 → T004/T005 → T006；T007 依赖 T003（`PipeKind`）；T008、T009 可并行；T010 依赖 T007～T009。
- T012、T013 依赖 T003 与 048 的接口；T014 依赖 T007、T012、T013；T015 依赖 T012～T014。
- T017 依赖 T012；T018 依赖 T017；T019 依赖 T017、T018。
- T021 依赖全部代码；T022、T023、T024 在 T021 之后。

## Implementation Strategy

- **MVP**：Phase 1～4（物品、吸收、提示）；Phase 5 加放置。一个 Phase 一次 `./gradlew compileJava test`，每个检查点都应是绿的。
- 水、岩浆是主路径（只用原版机制）；油靠 PNC 验证，PNC 起不来时油管只做单元测试与源码断言，DELIVERY 里写"油的游戏内验证未做"。
- 不提交；整个 feature 做完、用户确认后由用户决定何时提交（与 044～048 同样）。
