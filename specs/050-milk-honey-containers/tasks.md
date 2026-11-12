# Tasks: 牛奶管、蜂蜜管与排放到容器

**Input**: `/specs/050-milk-honey-containers/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 排放与吸收的判定、容器的选择顺序、单位用 JUnit（纯 Java）；物品、模型、贴图、配方、语言键用资源测试；接线用源码文本检查。**牛、山羊、蜂巢、成品容器的发放、提示的实际出现、PNC 的桶只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`，`…data/` = `src/main/resources/data/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器里用 `-PnoJei` 起客户端，"使用/攻击"改绑到键盘 `u`/`y`（见 `scripts/ai-gui/README.md`）；改存档前先备份世界；AI 自动验证的勾选规则同前
- 不新增配置项、网络包、存储字段、依赖；协议号仍 19，数据修订仍 2；011～049 的测试除提示文字相关的断言外不改；PNC 只进 `localRuntime`
- 用户故事：US1 潜行右键排放到容器；US2 牛奶管挤奶；US3 蜂蜜管取蜜；US4 管子物品、配方与提示措辞

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 186 个类、1418 个用例）；`git status` 干净（除 050 文档）；记在 `specs/050-milk-honey-containers/DELIVERY.md`（新建，中文）；宪法 VI 的"之前"度量：049 之后的图集是 2048x1024，实现前再确认一次并记录（186 个类、1418 个用例，0 失败；图集"之前"：049 收尾时测得 2048x1024，之后"一并再测"）

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T002 `…/pipe/PipeKind.java`：加 MILK、HONEY；`unitMb()`、`hasBlockForm()`（水、油、岩浆为真）、`emptyContainer()`（枚举 `BUCKET`/`GLASS_BOTTLE`，蜂蜜为瓶）；`classify`/`accepts` 不变；`…/pipe/ContainerPicker.java`（`firstMatch(mainSize, inventorySize, IntPredicate)`、`isInBackpack`）（`PipeKind` 加 `Container` 枚举、`unitMb`、`hasBlockForm`、`emptyContainer`；`ContainerPicker` 另有 `inventoryIndex`）
- [X] T003 `…/pipe/PipePlanner.java`：吸收、放置的单位改用 `pipe.unitMb()`（`BUCKET_MB` 常量保留）；`Supply`、`discharge`；`Notice` 加 `NO_EMPTY_CONTAINER`、`NO_BUCKET_FOR_FLUID`、`NO_BLOCK_FORM`；`place` 对 `!hasBlockForm()` 返回 `NO_BLOCK_FORM`（先于一切判定）；判定顺序见 data-model.md（`PipeNotices.key` 的三个新种类在这里一并加了，否则 switch 不完整无法编译）
- [X] T004 [P] 单元测试：`…test/pipe/PipePlannerTest.java` 扩展（排放判定表逐行、蜂蜜 249/250/251 与牛奶 999/1000/1001 的吸收、五种液体的单位与空容器种类、`NO_BLOCK_FORM`、油排放放的是锁定流体、随机同进退：成品数 × 一份 = 液量的减少）；`…test/pipe/PipeKindTest.java` 扩展；`…test/pipe/ContainerPickerTest.java`（主区域优先、只有物品栏、都没有、边界）
- [X] T005 运行 `./gradlew compileJava test`；故意改错一处（把 `discharge` 里"成品不存在"与"没有空容器"两步互换，或把蜂蜜单位写成 1000）确认 T004 失败，再还原（变异检查：把 `discharge` 里"没有空容器"与"成品不存在"两步互换，`PipePlannerTest` 失败，还原后通过）

**Checkpoint**: 判定与容器选择就绪，可在不启动游戏的情况下测试

## Phase 3: User Story 4 - 管子物品、配方与提示措辞 (Priority: P2，其余故事要用，所以先做)

- [X] T006 [US4] `…/item/ModItems.java` 与 `PipeItem`：`PipeKind.values()` 已覆盖新增的两种，确认 `PIPES` 与创造栏顺序（水、油、岩浆、牛奶、蜂蜜）；`PipeItem.appendHoverText` 加 `pipe.discharge`（蜂蜜用 `pipe.discharge_bottle`）、牛奶管的 `pipe.milk.tooltip`、蜂蜜管的 `pipe.honey.tooltip`
- [X] T007 [P] [US4] 扩展 `design/tank-textures/generate.py` 生成 `milk_pipe.png`、`honey_pipe.png`（管口颜色取牛奶白、蜂蜜琥珀），运行一次并目视检查；2 个模型；2 个配方（竖三格 铁锭/牛奶桶或蜂蜜瓶/铁锭）；2 个 id 加进 `never_trash.json`（生成 24 张：19 罐 + 5 管子）
- [X] T008 [P] [US4] 语言键中英文：2 个物品名、`pipe.discharge`、`pipe.discharge_bottle`、`pipe.milk.tooltip`、`pipe.honey.tooltip`、`pipe.no_container`、`pipe.no_bucket`、`pipe.no_block_form`；**改** `pipe.cannot_absorb`、`pipe.cannot_place` 的文字为与单位无关的说法（内容见 contracts/discharge-rules.md）；`PipeNotices.key` 加三个新种类（另在 `PipeItem.appendHoverText` 里按种类选说明键）
- [X] T009 [P] [US4] 扩展 `…test/resources/PipeResourcesTest.java` 到 5 根（模型、贴图 16×16、配方形状与材料、结果 `count 1`、比对应铁罐便宜、`never_trash`、语言键、创造栏顺序）
- [X] T010 运行 `./gradlew compileJava test`

**Checkpoint**: 五根管子可合成、可在创造栏取到

## Phase 4: User Story 1 - 潜行右键排放到容器 (Priority: P1) 🎯 MVP

- [X] T011 [US1] `…/pipe/PipeActions.java` 加 `discharge(ServerPlayer, PipeKind)`：找背包（`find`）→ 扫描空容器（背包主区域各格 `inventory.getStackInSlot(i)`，然后 `player.getInventory().items`，用 `ContainerPicker`，容器 = `Items.BUCKET` 或 `Items.GLASS_BOTTLE`）→ 确定成品（水桶、岩浆桶、牛奶桶、蜂蜜瓶；油为锁定流体 `BuiltInRegistries.FLUID.get(id).getBucket()`，`Items.AIR` 或不在注册表算不存在）→ `PipePlanner.discharge` → `REFUSE` 则发提示（`NO_EMPTY_CONTAINER` 的参数是容器名，`NO_BUCKET_FOR_FLUID` 的参数是流体 id）→ `DO` 则取走一个空容器（背包主区域用 `setStackInSlot`）、`Inventory.add` 成品、失败 `player.drop(stack, false)`、`commit`、空容器取自主区域时 `BackpackNetwork.sendFullSync`、声音（`BUCKET_FILL`/`BOTTLE_FILL`）与 `gameEvent(FLUID_PICKUP)`、发"空了"提示；**先判定、再动手，任何一步判定不过不改任何东西**
- [X] T012 [US1] `PipeItem.use()`：潜行（服务端、主手）→ `PipeActions.discharge`（`DONE` → `success`，否则 `fail`）；未潜行保持 049 的行为（水、油、岩浆吸收；牛奶、蜂蜜 `pass`）；`PipeNotices.send` 对三个新种类的参数
- [X] T013 [P] [US1] `PipeWiringTest.java` 的排放部分：`use` 里潜行走 `discharge`、未潜行走 `absorb`；`discharge` 里"找背包 → 扫描 → 成品 → 判定 → 取容器 → 发放 → 提交"的顺序；容器只认 `Items.BUCKET`/`Items.GLASS_BOTTLE`；成品放不下 `player.drop`；空容器取自主区域时 `sendFullSync`；无新包、协议号仍 19、修订仍 2
- [X] T014 运行 `./gradlew compileJava test`

**Checkpoint**: 五种液体都可以排放到容器

## Phase 5: User Story 2 + 3 - 牛奶与蜂蜜 (Priority: P1)

- [X] T015 [US2] `PipeActions.absorbEntity(ServerPlayer, PipeKind, LivingEntity)`：`PipePlanner.absorb`（单位 1000）→ `REFUSE` 发提示；`DO` → 挤奶声（牛与哞菇 `COW_MILK`；山羊按 `isScreamingGoat()` 选 `GOAT_MILK`/`GOAT_SCREAMING_MILK`，`player.playSound`）→ `commit`；`PipeItem.interactLivingEntity`：牛奶管、未潜行、`Cow` 或 `Goat` 且 `!isBaby()` → 客户端 `sidedSuccess(true)`，服务端 `absorbEntity`（`DONE` → `sidedSuccess(false)`，`REFUSED` → `fail`）；其余 `pass`
- [X] T016 [US3] `PipeActions.absorbHive(ServerPlayer, BlockPos, BlockState)`：`PipePlanner.absorb`（单位 250）→ `REFUSE` 发提示；`DO` → 世界（`BOTTLE_FILL` 声、`gameEvent(FLUID_PICKUP)`；`CampfireBlock.isSmokeyPos` 为假：有蜜蜂时重写的 `angerNearbyBees`（AABB 膨胀 (8,6,8)，对没有目标的蜜蜂随机指定范围内一名玩家）再 `releaseBeesAndResetHoneyLevel(level, state, pos, player, EMERGENCY)`；为真：`resetHoneyLevel`）→ `commit`；`PipeItem.useOn`：蜂蜜管、未潜行、`BeehiveBlock` 且 `honey_level >= 5` → 客户端 `sidedSuccess(true)`，服务端 `mayInteract`/`mayUseItemAt` 后 `absorbHive`；其余 `pass`
- [X] T017 [US4] `…/pipe/PipeEvents.java`：牛奶管、蜂蜜管左键 → 取消事件（已有）后，服务端 `START` 且节流通过时 `PipeNotices.send(NO_BLOCK_FORM)`，不放置；`PipeActions.place` 对 `!hasBlockForm()` 直接返回（不找背包）
- [X] T018 [P] [US2] [US3] `PipeWiringTest.java` 的牛奶与蜂蜜部分：`interactLivingEntity` 里潜行 `pass`、`Cow`/`Goat`、`!isBaby()`、客户端 `sidedSuccess(true)`、服务端 `absorbEntity`；挤奶声（牛、山羊 `isScreamingGoat()`）；`useOn` 里潜行 `pass`、`BeehiveBlock`、`honey_level >= 5`、`isSmokeyPos`、`releaseBeesAndResetHoneyLevel`/`resetHoneyLevel`、重写的 `angerNearbyBees` 的 AABB 与"没有目标的蜜蜂"；"判定 → 世界 → 提交"的顺序；左键牛奶管、蜂蜜管取消且不放置
- [X] T019 运行 `./gradlew compileJava test`

**Checkpoint**: 全部代码完成，等游戏内验证

## Phase 6: 游戏内验证与收尾

- [X] T020 游戏内验证（AI 自动验证，容器，quickstart B1～B17，**带 PNC**；**不在 `src/main` 留调试入口**）：每项写实际验证了什么，做不了的写"未验证"；顺带验证 PNC 的 `getBucket()` 是不是 `pneumaticcraft:oil_bucket`、牛奶桶与蜂蜜瓶作配方材料的退回（PNC 的 `getBucket()` 已验证是 `pneumaticcraft:oil_bucket`；配方材料退回未验证，见 DELIVERY.md）
- [X] T021 宪法 VI 的"之后"度量：加两张管子贴图后再跑 `check_atlas_budget.sh`，与 T001 对比，都 ≤ 2048；没量写"未度量"（之后也是 2048x1024，通过）
- [X] T022 [P] `README.md` 与 `README_CN.md`：管子一节补牛奶管、蜂蜜管与"潜行右键装进空桶/空玻璃瓶"，去掉"潜行右键暂时什么也不做"；`docs/plan/fluid-tanks.md` 状态改为"050 已实现"并补充 050 的要点（README 两份、`docs/plan/fluid-tanks.md` 已更新）
- [X] T023 `specs/050-milk-honey-containers/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么/没验证什么、协议号与数据修订不变的理由、提示措辞的改动、已知局限、图集前后尺寸、宪法复查（已写）
- [X] T024 最终检查：`./gradlew compileJava test` 全部通过；`git status` 确认无临时修改残留（含 `.tmp_*` 之类的临时文件）；`DependencyRulesTest` 通过；jar 里没有 PNC（`./gradlew jar` 后 `unzip -l` 抽查）；**不提交、不打 tag**（187 个类、1444 个用例，0 失败；`DependencyRulesTest` 通过；jar 里没有 PNC；没有临时文件残留；未提交、未打 tag）
- [X] T025 用户确认：用户在真机上试五种排放、挤奶、取蜜与提示；**只有用户明确确认才勾**（用户已确认（2026-11-12）：没有问题）

## Dependencies

- T002 → T003 → T004 → T005；T006 依赖 T002；T007、T008 可并行；T009 依赖 T006～T008。
- T011 依赖 T002、T003；T012 依赖 T006、T011；T013 依赖 T011、T012。
- T015、T016 依赖 T003、T006 与 049 的 `PipeActions`；T017 依赖 T003；T018 依赖 T015～T017。
- T020 依赖全部代码；T021、T022、T023 在 T020 之后。

## Implementation Strategy

- **MVP**：Phase 1～4（五根管子、排放到容器）；Phase 5 加牛奶与蜂蜜的吸收。一个 Phase 一次 `./gradlew compileJava test`，每个检查点都应是绿的。
- 容器里验证时的坑（049 已记入 `scripts/ai-gui/README.md`）：键位改绑、平台试验台、换液体前清空上面的空间、F5 视角。
- 不提交；整个 feature 做完、用户确认后由用户决定何时提交。
