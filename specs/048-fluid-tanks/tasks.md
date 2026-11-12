# Tasks: 液体罐

**Input**: `/specs/048-fluid-tanks/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 罐清单与容量、放入规则、液量运算、编码、量条像素用 JUnit（纯 Java）；物品、模型、贴图、配方、语言键用资源测试；接线用源码文本检查。**合成结果、放入的真实拒绝、量条与悬停外观、重进世界后的液量只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`，`…data/` = `src/main/resources/data/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`（宿主机有用户的客户端在运行时不启动容器客户端）；改背包与存档前先备份世界；容器见 `scripts/ai-gui/README.md`；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增配置项与依赖；协议号 18→19；数据修订 1→2；011～047 的测试除协议号与修订断言外不改
- 用户故事：US1 合成各档各种罐；US2 放入规则；US3 面板液量显示；US4 液体留在背包；US5 旧档、快照、降级

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 173 个类、1298 个用例）；`git status` 干净（除 048 文档与 `docs/plan/fluid-tanks.md`）；记在 `specs/048-fluid-tanks/DELIVERY.md`（新建，中文）（173 个类、1298 个用例，0 失败）
- [X] T002 [P] 宪法 VI 的"之前"度量：运行 `design/demo/tools/check_atlas_budget.sh`，把共享图集的宽高记到 DELIVERY.md；跑不起来就写"未度量"及原因（2048x1024，通过）

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T003 `…/tank/FluidKind.java`（枚举 WATER/OIL/LAVA/MILK/HONEY：`id()`、`perUseMb()`、`color()`、`locksFluid()`、`byId`）与 `…/tank/TankCatalog.java`（`Tank(kind, tier)` 记录、四档、`capacityMb`、`exists`、`all()` 固定顺序、`itemId`、`allowedIn(backpack)`；钻石抛 `IllegalArgumentException`），内容见 data-model.md
- [X] T004 `…/tank/FluidStore.java`（不可变记录、`EMPTY`、`amount`、`lockedFluid`、`with`、不变式清理）与 `FluidStoreOps`（`deposit`、`drain`、`canAccept`，结果记录带实际量与原因），规则见 data-model.md
- [X] T005 [P] `…/tank/FluidStoreCodecs.java`：磁盘 `Codec<FluidStore>`（对象：键为液体 id、值为整数，加 `oil_fluid`；缺字段 = 空、未知键忽略、坏值当 0）与网络 `StreamCodec<ByteBuf, FluidStore>`（条数 ≤ 5、`oilFluid` ≤ 256 字符，解码同样清理）
- [X] T006 [P] `…/tank/TankPlacement.java`（`Verdict`、`check(...)`，判定顺序见 data-model.md）与 `…/tank/TankBar.java`（`fillPixels`、常量）
- [X] T007 [P] 单元测试 `…test/tank/TankCatalogTest.java`：19 种清单与顺序、没有皮岩浆、容量 8/16/64/256 桶、钻石抛异常、`itemId`、`allowedIn` 的 5×4 表
- [X] T008 [P] 单元测试 `…test/tank/TankPlacementTest.java`：contracts/placement-rules.md 的 7 组（95 项穷举 + 互斥 + 容量 + 左右区 + 非罐与 null 档次 + 旧数据宽容 + 1000 组随机不变式）
- [X] T009 [P] 单元测试 `…test/tank/FluidStoreTest.java`（`deposit`/`drain`/`canAccept` 边界、原油锁定与解锁、其它种类不受影响、守恒、读入清理）、`FluidStoreCodecTest.java`（磁盘与网络往返、缺字段、未知键、超限被拒）、`TankBarTest.java`（0→0、极少量至少 1、满 14、超量夹到 14、容量 0→0）（`FluidStoreCodecTest` 做不了：测试类路径里没有 Minecraft、netty、DFU；编码规则放在纯逻辑 `FluidStore.of` 里测，编码本身用 TankWiringTest 的源码检查与游戏内验证（B9）覆盖）
- [X] T010 运行 `./gradlew compileJava test`；故意改错一处（把 `TankPlacement` 的档次比较写成 `<`）确认 T008 失败，再还原（变异检查：`allowedIn` 的 `<=` 改成 `<`，tank 包的测试失败，还原后通过）

**Checkpoint**: 罐的规则与液量运算就绪，可在不启动游戏的情况下测试

## Phase 3: User Story 1 - 合成各档各种液体的罐 (Priority: P1) 🎯 MVP

- [X] T011 [US1] `…/item/TankItem.java`（`kind`、`tier`、`stacksTo(1)`、`appendHoverText`：容量行与最低背包档次行）；`…/item/ModItems.java`：用 `TankCatalog.all()` 注册 19 个（id 为 `itemId`）、`tank(kind, tier)` 取、创造标签页里放在 `AUTO_PICKUP` 之后、类别扩展之前
- [X] T012 [P] [US1] `design/tank-textures/generate.py`（Pillow）：按 contracts/recipes-and-assets.md 的颜色表生成 19 张 16×16 PNG 到 `…res/textures/item/<id>.png`，运行一次并提交生成结果；脚本里写明"占位图，可用同名文件替换"（生成 19 张，已目视检查拼图）
- [X] T013 [P] [US1] 19 个模型 `…res/models/item/<id>.json`（`minecraft:item/generated` + `layer0`）；语言键中英文（19 个物品名、5 个液体名、容量、最低档次、悬停的"当前/容量"与"空"、三条放入拒绝消息），两个语言文件的键保持一致
- [X] T014 [US1] 19 个配方 `…data/recipe/<id>.json`（有序合成 14 个、锻造台 5 个，形状与材料见 contracts/recipes-and-assets.md）；19 个 id 加进 `…data/tags/item/never_trash.json`
- [X] T015 [P] [US1] 资源测试 `…test/resources/TankResourcesTest.java`：契约"资源测试"一节全部断言（19×物品/模型/贴图/配方、语言键齐全、没有 `leather_lava_tank`、配方形状与材料、`never_trash` 含 19 个、贴图 16×16）
- [X] T016 运行 `./gradlew compileJava test`；`./gradlew runData` 不涉及（资源是手写的）

**Checkpoint**: 19 种罐可合成、可在创造栏取到（还不能放进背包生效）

## Phase 4: User Story 4 + 5 - 液量数据、同步、旧档与降级 (Priority: P1)

- [X] T017 [US4] `…/inventory/BackpackContents.java`：记录新增分量 `FluidStore fluids`（在 `rows` 之后、`dataRevision` 之前），磁盘 `optionalFieldOf("fluids", FluidStore.EMPTY)`，网络编码末尾追加；`normalize/fit/copy/withDataRevision/empty` 全部带上；更新所有调用点（`grep -rn "new BackpackContents(\|BackpackContents.normalize(\|BackpackContents.fit(" src`，约 10 处，含测试）
- [X] T018 [US4] `…/inventory/BackpackInventory.java`：`fluids()`/`setFluids`、`tier()`、`tankTier(FluidKind)`（扫描底部区整张存储列表，返回生效罐的档次或 null）；`fromContents`/`toContents` 带上 `fluids`
- [X] T019 [US5] `…/inventory/revision/DataRevision.java`：`CURRENT = 2`，更新类注释里的"现在是 1"；改所有断言修订为 1 的历史测试（`grep -rn "CURRENT\|data_revision" src/test`，`WorldMarkerGuardTest` 自带常量不用改）；`docs/plan/release-process.md` 的"`CURRENT` 现在是 1"改为 2 并注明是 048 递增（`DataRevisionTest` 改为 2；`release-process.md` 已更新；`WorldMarker*Test` 自带常量不用改）
- [X] T020 [US4] `…/network/BackpackFluidsPayload.java`（S→C，复用 `FluidStoreCodecs` 的网络编码，`handleClient` 写 `ClientBackpackView`）；`…/network/BackpackNetwork.java`：`versioned("18")` → `versioned("19")`、注册载荷、`sendFluids(player, inventory)`、会话打开时随标签、垃圾桶、黑名单一起发；把所有断言协议号 18 的历史测试改为 19（`grep -rln 'versioned(\\"18\\")' src/test`）（9 个历史测试的协议号断言改为 19，另改了 `SearchTermsContractTest` 的一处与 `BackpackContentsRevisionTest` 的源码断言）
- [X] T021 [P] [US4] `…/client/panel/ClientBackpackView.java`：`fluids()`、`setFluids`、`tankTier(kind)`（扫描底部区）、`backpackTier()`；会话关闭与重置时清空
- [X] T022 [P] [US5] 单元测试 `…test/inventory/BackpackContentsFluidsTest.java`（尽量用不依赖 `ItemStack` 的部分：旧 JSON 没有 `fluids` 读作空、新旧字段往返、`BackpackContentsRevisionTest` 里加"修订 2"的断言）；快照往返以源码文本检查 `SnapshotOps` 仍走 `BackpackContents.CODEC` 为证，实际往返在游戏里验证（B9）（`BackpackContents` 依赖 `ItemStack`，测试类路径里没有 Minecraft，没法单元测试它的编码；改用 `TankWiringTest` 的源码断言覆盖字段位置、可选字段、网络末尾、升级保留，并更新 `BackpackContentsRevisionTest`；实际往返与旧背包读取在游戏里验证 B9）
- [X] T023 [P] 源码文本检查测试 `…test/resources/TankWiringTest.java` 的数据部分：`DataRevision.CURRENT = 2`、`versioned("19")`、`BackpackFluidsPayload` 已注册、会话打开时发送、`BackpackContents` 含 `fluids` 的磁盘与网络编码（并入 `TankWiringTest` 的"数据"部分，含编码键、`oil_fluid`、条数与序号校验的源码断言）
- [X] T024 运行 `./gradlew compileJava test`

**Checkpoint**: 液量随背包保存、同步、进快照；旧背包照常读

## Phase 5: User Story 2 - 放入规则 (Priority: P1)

- [X] T025 [US2] `…/network/BackpackClickPayload.java`：在 `ToolAreaRules.canPlace` 之后、改动之前，对 `held` 调用 `TankPlacement.check`（`tankOf` 由 `TankItem` 取 `Tank`；背包档次用 `inventory.tier()`；现量用 `inventory.fluids().amount(kind)`）；结果不是 `OK` → `sendSlotUpdate` 回滚并用 `displayClientMessage(..., true)` 发对应的消息键，不改任何东西（点击路径里用 `TankItem.check`，被拒时动作栏提示并回滚；检查读源码确认了其它写入路径都只写主区域）
- [X] T026 [US2] `…/client/panel/FloatingPanelInput.java`：`refusedByLimit` 里同样用 `TankPlacement.check`（数据来自 `ClientBackpackView`），被拒时发聊天提示并不发请求（预测在 `refusedByLimit` 里，被拒发聊天提示且不发请求）
- [X] T027 [P] [US2] `TankWiringTest.java` 的规则部分：`TankPlacement.check` 在 `ToolAreaRules.canPlace` 之后；`refusedByLimit` 用了同一个函数；三种结果各有对应消息键且中英文齐全；`tank` 包不含 `net.minecraft`（并入 `TankWiringTest` 的规则部分，另含"其它请求包不写工具区"的源码断言）
- [X] T028 运行 `./gradlew compileJava test`

**Checkpoint**: 罐可以按规则放进工具区

## Phase 6: User Story 3 - 面板里的液量显示 (Priority: P2)

- [X] T029 [US3] `…/client/panel/FloatingPanelRenderer.java`：`drawSlotsWithItems` 里，`area == BOTTOM` 且栈是 `TankItem` 时在 `renderItemDecorations` 之后（抬到 z 200）画量条（`TankBar`：底色、填充高度、液体色）；`drawItemTooltip` 里对底部区的罐用 `Screen.getTooltipFromItem` 取原版行再追加"水：8000 / 16000 mB"或"空"一行；左、右区的罐不画、不追加（量条在 `renderItemDecorations` 之后抬到 z 200；悬停对底部区的罐追加一行）
- [X] T030 [P] [US3] `TankWiringTest.java` 的渲染部分：量条只在底部区、只对 `TankItem`；提示追加用了 `tooltip.mcfloatingbackpack.tank.amount` 与 `…empty`（并入 `TankWiringTest` 的渲染部分）
- [X] T031 运行 `./gradlew compileJava test`

**Checkpoint**: 全部代码完成，等游戏内验证

## Phase 7: 游戏内验证与收尾

- [X] T032 游戏内验证（AI 自动验证，容器，quickstart B1～B10；先验证 `/item replace … fluids:{…}` 命令能解析，不行就加仅开发用的开关并在 DELIVERY 里说明；**不在 `src/main` 留调试入口**）：每项写实际验证了什么，做不了的写"未验证"（真实原油模组联调、蜂蜜瓶退回玻璃瓶以实际合成为准、创造模式整包写回）（AI 自动验证已做一部分，容器；做了与没做的逐项见 DELIVERY.md；`fluids` 命令语法已验证可用，不需要调试开关）
- [X] T033 宪法 VI 的"之后"度量：再运行图集脚本，与 T002 对比，宽高都 ≤ 2048；没量写"未度量"（之后也是 2048x1024，通过）
- [X] T034 [P] `README.md` 与 `README_CN.md`：液体罐一节（19 种、档次与容量、放入规则：同液体一个、档次不高于背包、三个工具区都查档次、主区域不限；048 阶段还不能装液体）；`docs/plan/fluid-tanks.md`：状态改为"048 已实现"，并与 plan 的"对 spec 的更正"一致（README 与 README_CN 各加一条液体罐；`docs/plan/fluid-tanks.md` 状态已更新）
- [X] T035 `specs/048-fluid-tanks/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 plan/tasks 的偏离、每个游戏内验证项验证了什么/没验证什么、**数据修订 1→2 及理由、协议号 18→19、降级前备份的提醒**、已知局限（创造模式整包写回、降级丢失液量与罐、原油真机联调未做）、图集前后尺寸、宪法复查；更新 `docs/plan/release-process.md` 里"发布说明提醒降级前备份"的待办（已写；`release-process.md` 的 CURRENT 说明已更新；"发布说明提醒降级前备份"留到发布时）
- [X] T036 最终检查：`./gradlew compileJava test` 全部通过；`git status` 确认无临时修改残留、没有新增依赖与配置项；`DependencyRulesTest` 通过；**不提交、不打 tag**（179 个类、1354 个用例，0 失败；`DependencyRulesTest` 通过；没有新增依赖与配置项；未提交、未打 tag）
- [X] T037 用户确认：用户在真机上试合成、放入各档背包、量条与提示；**只有用户明确确认才勾**（用户已确认（2026-11-12）：没有问题；覆盖液体罐本体与 US6 隐藏液体）

## Phase 8: User Story 6 - 隐藏液体：黄点与 /hidden（2026-11-12 补充，P2）

- [X] T038 [US6] `…/tank/HiddenLiquids.java`（纯逻辑，`Line(kind, amountMb)`、`lines(store, hasTank)`）与 `…test/tank/HiddenLiquidsTest.java`：量 > 0 且没有生效罐才算；左、右区的罐不算生效（由调用方的 `hasTank` 决定，测试用谓词）；按液体顺序；量为 0 不算；全部有罐时为空
- [X] T039 [US6] `…/client/panel/search/HiddenSummaryRows.java`：`Line` 增加 `liquidMb`（保留 3 参数构造）；`…test/client/panel/search/HiddenSummaryRowsTest.java` 扩展：液体行能被 `/hidden 词`、`-词`、引号筛选；`@`、`#` 对液体行不成立；`totalCells` 里每条液体算 1
- [X] T040 [US6] 语言键中英文：`panel.mcfloatingbackpack.hidden.liquid.name`（"%s（液体）"/"%s (liquid)"）、`.hidden.liquid.title`（"有液体没有罐"/"Some liquids have no tank"）、`.hidden.liquid.line`（"%s：%s mB"）、`.hidden.liquid.hint`（"把对应的罐放进底部工具区就能继续使用"）、`.hidden.mixed.title`（"有物品和液体被隐藏"/"Some items and liquids are hidden"）
- [X] T041 [US6] `…/client/panel/FloatingPanelRenderer.java`：黄点条件 = 有隐藏物品或有隐藏液体；`drawHiddenHint` 在物品段后加液体段（只有液体时用液体标题）；`…/client/panel/search/SearchContext.java`：`hiddenLines()` 追加液体行；`…/client/panel/search/SearchRenderer.java`：液体行用 `hidden.liquid.line`，标题与提示按有无物品行、液体行选键（新增 `HiddenTextKeys` 纯逻辑类按有无物品行/液体行选键，`HiddenTextKeysTest`）
- [X] T042 [P] [US6] `TankWiringTest.java` 补源码断言：黄点条件含 `HiddenLiquids`；`hiddenLines()` 追加液体行；渲染里液体行用 `hidden.liquid.line`；`HiddenLiquids` 不含 `net.minecraft`；新增语言键中英文齐全；无新网络包、配置项、依赖（并入 `TankWiringTest`；另改了 `SearchPrefixResourcesTest` 里一条对渲染器源码的断言，见 DELIVERY）
- [X] T043 运行 `./gradlew compileJava test`（181 个类、1371 个用例，0 失败）
- [X] T044 游戏内验证（容器，AI 自动验证；同样的数据包函数造"有液量没有罐"的背包）：黄点出现、悬停液体段的文字、`/hidden` 汇总列出液体与计数、`/hidden 液体` 与 `/hidden water` 筛选、放回罐后液体行消失、物品与液体都隐藏时黄点只有一个；没验证的写"未验证"（容器里做了黄点、悬停、`/hidden`、`/hidden milk`、`liquid -oil`、`zzz`、放回罐后液体行消失；物品+液体同时隐藏、中文界面、3 列窄面板未验证，见 DELIVERY）
- [X] T045 更新 `DELIVERY.md`、README 两份（一句话）、`docs/plan/backpack-search.md` 的 `/hidden` 说明里补"也列出没有罐的液体"；T037（用户确认）同时覆盖 US6

## Dependencies

- T003 → T004/T005/T006 → T007～T009 → T010；T011 依赖 T003；T012、T013 可并行；T014 依赖 T011（物品 id）；T015 依赖 T012～T014。
- T017 依赖 T004、T005；T018 依赖 T017；T019 独立；T020 依赖 T017、T018；T021 依赖 T020；T022、T023 依赖 T017～T020。
- T025 依赖 T006、T011、T018；T026 依赖 T021、T025；T029 依赖 T006、T011、T021。
- Phase 3 与 Phase 4 互不依赖（除 T011 外），但按 Phase 编号顺序做，一次编译一个检查点。

## Implementation Strategy

- **MVP**：Phase 1～3（罐物品、配方、资源），再 Phase 4～5（数据与规则）才让罐有意义；Phase 6 是显示。
- 一个 Phase 一次 `./gradlew compileJava test`，每个检查点都应是绿的；不一次性做完再编译。
- 不提交；整个 feature 做完、用户确认后由用户决定何时提交（与 044～047 同样）。
