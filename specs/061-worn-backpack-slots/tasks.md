# Tasks: 饰品模组里的专用槽位与通用槽位

**Input**: `/specs/061-worn-backpack-slots/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 纯逻辑（`SlotLimit`、来源表）用 JUnit；数据文件、tag、语言键、配置键、接线用资源/源码文本检查。**单元测试类路径里没有 Minecraft 与饰品模组的类，真实的槽位、条件、`canEquip` 只能在游戏会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；改配置前备份、验证后还原；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增依赖、mixin、反射；**协议号不改（22）**；**`DataRevision` 仍为 6**；面板显示逻辑不改；新增数据文件在槽位与实体文件上带条件
- **没装 Curios/Accessories、开关为默认、`back` 里的老背包：行为与 061 之前完全一致**：每个改动任务都要保住这一点
- 用户故事：US1 专用槽位（P1）；US2 每类一只（P1）；US3 `charm`（P2）；US4 启动开关（P2）；US5 档位、文档与测试（P3）

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`；类数与用例数记在 `specs/061-worn-backpack-slots/DELIVERY.md`（新建，中文）

## Phase 2: Foundational

- [X] T002 [P] `…/inventory/worn/SlotLimit.java`（纯）：`allows(int othersInType, boolean enforced)`；`…test/inventory/worn/SlotLimitTest.java`：真值表（已有 0/1/2 只 × enforced 真/假）
- [X] T003 [P] `…/inventory/BackpackRef.java`：`Source` 末尾追加 `CURIOS_DEDICATED`、`CURIOS_CHARM`、`ACCESSORIES_DEDICATED`、`ACCESSORIES_CHARM`（`worn = true`，注释写明 061）；`…test/inventory/BackpackRefSourcesTest.java`：前 6 项序号（0～5：MAIN_HAND、OFF_HAND、CHEST、INVENTORY、CURIOS、ACCESSORIES）不变，新 4 项为 6～9，`isWorn`；确认四处解码的越界保护仍在（研究 R5）
- [X] T004 [P] `…/compat/WornSlotsConfig.java`：`ModConfigSpec` 一节 `[slots]`，两项布尔 `curios`、`accessories`（默认 `true`，注释见 contracts/data-files.md）；`enabled(String modId)`（未载入/未知 id → `true`）；`…test/compat/WornSlotsConfigTest.java`：键名与默认值、未知 id、未载入（**配置值本身读不到**，纯逻辑部分用一个可替换的读取来源测试）
- [X] T005 `…/compat/SlotEnabledCondition.java`（只用 NeoForge 类型）：`record SlotEnabledCondition(String mod) implements ICondition`，`MapCodec` 字段 `mod`，`test` 读 `WornSlotsConfig.enabled(mod)`；`DeferredRegister<MapCodec<? extends ICondition>>` 挂到 `NeoForgeRegistries.CONDITION_SERIALIZERS`，名字 `slot_enabled`；`MCFloatingBackpack` 构造函数里注册它与 `modContainer.registerConfig(ModConfig.Type.STARTUP, WornSlotsConfig.SPEC)`（**STARTUP 必须在构造函数里、早于一切数据包加载**）；`…test/resources/WornSlotsWiringTest.java` 的第一部分：STARTUP 注册、条件挂载点、条件类不 import 任何 `top.theillusivec4`/`io.wispforest`
- [X] T006 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 专用槽位 (Priority: P1) 🎯 MVP

**Goal**: Curios、Accessories 各有一个只接受浮动背包的"浮动背包"槽，放进去与 `back` 一样工作。

**Independent Test**: quickstart 情形 1～3。

- [X] T007 [US1] `…/compat/curios/CuriosWornSource.java`、`…/compat/accessories/AccessoriesWornSource.java`：参数化（槽位类型、来源枚举、顺序）；其余方法行为不变（contracts/sources-and-limit.md）；`CuriosCompat.register()` 注册 `back`(CURIOS,10)、`floating_backpack`(CURIOS_DEDICATED,11)、`charm`(CURIOS_CHARM,12)；`AccessoriesCompat.register()` 同理(20/21/22)；`…test/resources/WornSlotsWiringTest.java` 第二部分：三个来源、顺序数值、`BackpackAccessory.canEquipFromUse` 仍为否
- [X] T008 [US1] 数据文件（contracts/data-files.md）：Curios `slots/floating_backpack.json`、`entities/player_floating_backpack.json`；Accessories `slot/floating_backpack.json`、`entity/player_floating_backpack.json`、`group/chest.json`；全部带条件；四个 tag 文件（`curios`/`accessories` × `floating_backpack`/`charm`）；**两张 16×16 贴图**（用脚本生成，灰色线稿背包轮廓）；语言键 `curios.identifier.floating_backpack`、`accessories.slot.floating_backpack`（en_us、zh_cn）；`…test/resources/WornSlotsResourcesTest.java`：文件齐全、JSON 可解析、条件的 `type`/`mod` 与注册名和配置键对得上、槽位 id 与 tag 文件名一致、`order` 值、图标路径与贴图文件对得上、语言键中英文齐
- [X] T009 [US1] 运行 `./gradlew test`
- [X] T010 [US1] 游戏内验证（quickstart 二、1～3）：槽位与图标出现（Curios、Accessories 各一次，记下 Accessories 的组是"胸部"还是"未分类"）；浮动背包放得进、别的物品放不进；`B` 键打开；背部渲染；`back` 不变；两个槽各一只时 `B` 取顺序里第一只；**不对就按 research R3 的备选路径改**；结果记在 DELIVERY.md

## Phase 4: User Story 2 - 每个槽位类型最多一只 (Priority: P1)

**Goal**: FULL 时同一槽位类型里放不进第二只；不同类型各一只可以；非 FULL 不拦。

**Independent Test**: quickstart 情形 4。

- [X] T011 [US2] `…/compat/curios/BackpackCurioItem.java`（`ICurioItem.canEquip`，装饰格放行、异常放行、`enforced` 与来源的 `active(entity)` 同一个判断）；`CuriosCompat.registerCurios()`（通用 setup、物品已存在时对五个等级调 `CuriosApi.registerCurio`，**只在 Curios 已加载时**）；`…/compat/accessories/BackpackAccessory.java` 加 `canEquip`；`MCFloatingBackpack` 里把 `registerCurios` 接到通用 setup（与 `registerAccessories` 同处）；纯判断全部走 `SlotLimit`；接线测试：`registerCurio` 只在 Curios 路径、`canEquipFromUse` 不覆盖、`SlotLimit` 被两处使用、没有 tick 里的清理
- [X] T012 [US2]（Accessories 一侧先验证，Curios 多格 2026-11-23 补验证，见 DELIVERY） 游戏内验证（quickstart 二、4）：把 `charm` 调成多格（数据包），同类型第二只放不进；不同类型各一只放得进；`curios=GENERIC` 不拦；命令绕过后多出的一只不被动；记下装饰格的观察（研究 R4 风险）

## Phase 5: User Story 3 - `charm` 槽 (Priority: P2)

- [X] T013 [US3]（Accessories 与 Curios 的 `charm` 都已验证，见 DELIVERY） 游戏内验证（quickstart 二、5）：Accessories 的 `charm`、Curios（数据包给玩家分配 `charm` 仅用于验证，验证后撤掉）；`B` 键、渲染、每类一只

## Phase 6: User Story 4 - 启动开关 (Priority: P2)

- [X] T014 [US4] 游戏内验证（quickstart 二、6～7）：`curios=false` 重启 → Curios 里没有专用槽，Accessories 的还在，`back`/`charm` 照常；日志里条件被拒的行；改回 true 恢复；**槽位消失时专用槽里背包的去向**（Curios、Accessories 各一次，背包里放东西，检查完整）——**若有任何丢失立即停下告诉用户**；结果决定 T016 的措辞

## Phase 7: User Story 5 - 档位、文档与测试 (Priority: P3)

- [X] T015 [US5]（老存档、`curios=GENERIC`、专用服务器的开关读取与联机以服务端为准都已验证，见 DELIVERY 的补验证） 游戏内验证（quickstart 二、8～10）：`curios=GENERIC` 来源不生效且物品不动、改回恢复；专用服务器上开关在启动时被读取、档位同样生效（`mcfb-srv`）；联机客户端开关与服务端不同时的实际表现；老存档 `back` 里的背包仍可用
- [X] T016 [US5] README.md 与 README_CN.md 兼容说明：三类位置（`back`、专用、`charm`）、每类一只、开关的位置/默认/重启/联机以服务端为准/关闭后的警告（按 T014 的结果）、客户端档位在联机时只影响视觉；配置注释同步
- [X] T017 [US5] `docs/plan/third-party-compat.md` 记设计原则（档位管适配代码，"向第三方注册内容"走按模组的启动配置）；`docs/plan/worn-backpack-compat.md` 与 `docs/plan/README.md` 的状态
- [ ] T018 [US5] 宪法 VII 修订（MINOR 2.3.0）：加子节"Registering content into another mod's data"，改 Sync Impact Report 与版本号；**措辞需用户确认后才算完成**（先写草案，在最终回复里请用户确认；用户确认后才勾）
- [X] T019 [US5] 漂移测试扩展：`ThirdPartyDriftTest`/`DependencyRulesTest` 保持通过（没有新坐标）；jar 里没有 `top/theillusivec4`、`io/wispforest` 的类；`…test/resources/WornSlotsWiringTest.java` 补 README 与数据文件的对齐检查（README 提到的键名与配置一致）
- [X] T020 [US5] `./gradlew build -PcleanRuntime -PnoJei`；DELIVERY.md 写完（已验证 / 没验证 / 取舍 / 协议号说明 / 宪法复查）；更新 memory
- [X] T021 用户确认（2026-11-23 用户说功能没问题；宪法 VII 修订措辞没有明确确认，T018 保持未勾）：真机上专用槽位的图标与位置、`B` 键与渲染、每类一只的手感、README 措辞与宪法修订措辞；**只有用户明确确认才勾**

## Dependencies & Order

- T002～T005 可并行（T005 依赖 T004）；T006 收口。
- US1：T007、T008 依赖 T003、T005；T010 依赖 T007～T009。
- US2：T011 依赖 T002、T007；T012 依赖 T011。
- US3：T013 依赖 T008、T007。
- US4：T014 依赖 T010；T016 依赖 T014、T015。
- T018 与 T016 可并行，但最终确认在 T021。
