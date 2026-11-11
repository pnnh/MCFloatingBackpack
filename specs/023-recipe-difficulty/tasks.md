# Tasks: 背包配方难度调整

**Input**: Design documents from `/specs/023-recipe-difficulty/`

**Prerequisites**: plan.md、spec.md、research.md、data-model.md、contracts/recipe-format.md、quickstart.md；宪法 2.1.1；006（升级配方与背包等级）、008/006-JEI 修复（`FloatingBackpackJeiPlugin` 里的锻造扩展）、022（协议 10）

**Tests**: 配方 JSON 的字段与值是纯文本，可以单测（测试类路径没有 Minecraft/JSON 库，沿用 `TrashCanResourcesTest` 的文本做法），**测试任务排在实现之前**；锻造台的真实匹配与消耗、配方同步、JEI 显示、旧存档只能在客户端会话里验证（宪法 V）。

**Organization**: 按用户故事分组。Phase 2 是阻塞所有故事的基础（先写测试、再改配方类与协议版本）；US1（四档升级改块 + 模板）与 US4（旧存档与内容保留）是 P1；US2（JEI 显示）、US3（皮革配方）是 P2。

> **注意（US2 的前置）**：006-JEI 修复（`FloatingBackpackJeiPlugin` 里给 `BackpackUpgradeRecipe` 注册的锻造扩展）**用户还没确认**；T010 会在它上面改 `setTemplate`，验证 T011 时一并确认"升级配方在 JEI 里可见"。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: 对应 spec 里的用户故事（US1～US4）
- 路径都相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（AGENTS.md：任务完成后由用户先检查再决定是否提交）；**启动客户端或跑 `check_atlas_budget.sh` 前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"` 确认没有正在运行的客户端，不要杀用户的游戏**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"，不要写成通过
- 包名前缀 `pnnh.mcfloatingbackpack`；**不要修改 `docs/ideas`**

---

## Phase 1: Setup

- [X] T001 在开始改动前跑一次基线：`./gradlew compileJava test`，记录退出码、测试类数量与用例数（当前 62 个测试类）；结果记到 `specs/023-recipe-difficulty/DELIVERY.md`（新建，中文）

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 配方类要先认识 `template` 字段，协议版本要先升，之后改数据才有意义。测试先写、先失败。

- [X] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/resources/BackpackRecipesResourcesTest.java`（**先写，先失败**），纯文本校验 `src/main/resources/data/mcfloatingbackpack/recipe/` 下五个配方（先去空白再断言；皮革的 `pattern` 要在去空白前用正则校验，因为行里有有意义的空格）：
  - `upgrade_iron_floating_backpack.json`：`type` 为 `mcfloatingbackpack:backpack_upgrade`；`template` 为 `minecraft:sentry_armor_trim_smithing_template`；`base` 为皮革背包；`addition` 为 `minecraft:iron_block`；`result` 为铁背包。
  - `upgrade_gold_…`：`dune_armor_trim_smithing_template`、铁背包、`minecraft:gold_block`、金背包。
  - `upgrade_diamond_…`：`eye_armor_trim_smithing_template`、金背包、`minecraft:diamond_block`、钻石背包。
  - `upgrade_netherite_…`：`netherite_upgrade_smithing_template`、钻石背包、`minecraft:netherite_block`、下界合金背包。
  - 四个模板 id **互不相同**；四个升级配方的 `addition` 都以 `_block` 结尾；没有任何配方引用 `sophisticated` 或 `travelersbackpack`。
  - `leather_floating_backpack.json`：`pattern` 为 `["LSL","LCL","ILI"]`，`key` 里 `L` 皮革、`S` 线、`C` 箱子、`I` 铁锭，结果为皮革背包 1 个。
- [X] T003 改 `…/inventory/recipe/BackpackUpgradeRecipe.java`，按 data-model.md 第 1 节：增加 `Ingredient template` 字段与构造参数；`CODEC` 里加 `Ingredient.CODEC.optionalFieldOf("template", Ingredient.EMPTY)`（**可选，缺省为空 = 006 行为**）；`matches` 里模板成分**非空**则要求 `template.test(input.template())`，为空则要求 `input.template().isEmpty()`；`isTemplateIngredient` 同理（非空 → `template.test(stack)`，空 → `stack.isEmpty()`）；`isIncomplete` **不**看模板；新增 `template()` 访问方法；`STREAM_CODEC` 编解码顺序改为 template、base、addition、result。**动手前先读 JDK/NeoForge 源码核实 `Ingredient.EMPTY` 在 `Ingredient.CODEC` 与 `CONTENTS_STREAM_CODEC` 里的行为，结论写进 DELIVERY.md**；若发现空成分不能往返，改用"不写字段 = 无模板"的 `Optional<Ingredient>` 方案并在 DELIVERY.md 里说明偏离。`assemble` 与 `getResultItem` 一行不改。
- [X] T004 [P] 把 `…/network/BackpackNetwork.java` 里的 `versioned("10")` 改为 `versioned("11")`；`grep -rn '"10"' src` 确认没有其它处硬编码协议版本（包括测试）；若有协议版本的说明性文字（`specs/006-tiered-backpacks/research.md`、`docs/MCFloatingBackpack.md`）留给 T014 一并更新。
- [X] T005（2026-11-07：只有 T002 的两个用例按预期失败，其余通过） 运行 `./gradlew compileJava test`：**T002 仍然失败**（数据还没改，这是预期），其余全部通过；记到 DELIVERY.md。

**Checkpoint**: 配方类认识 `template`，协议版本已升；数据还是旧的，所以升级行为暂时不变。

---

## Phase 3: User Story 1 - 四档升级统一要"块"并多一个锻造模板 (Priority: P1) 🎯 MVP

**Goal**: 四个升级配方依次是铁块、金块、钻石块、下界合金块 + 各自的原版模板；缺任何一项都不产出；内容保留。

**Independent Test**: quickstart 一（16 个正反用例）与二 6。

- [X] T006 [US1] 改写四个升级配方 `src/main/resources/data/mcfloatingbackpack/recipe/upgrade_{iron,gold,diamond,netherite}_floating_backpack.json`：各加 `"template": { "item": "..." }`（铁 `minecraft:sentry_armor_trim_smithing_template`、金 `minecraft:dune_armor_trim_smithing_template`、钻石 `minecraft:eye_armor_trim_smithing_template`、下界合金 `minecraft:netherite_upgrade_smithing_template`），`addition` 依次为 `iron_block`、`gold_block`、`diamond_block`、`netherite_block`；`base` 与 `result` 不变。完全按 data-model.md 第 2 节的表。
- [X] T007 [US1] 运行 `./gradlew compileJava test`：**T002 的测试此时转为通过**，全部测试绿；记到 DELIVERY.md。
- [X] T008（2026-11-07 用户验证 023-1、023-6 通过：四档 16 个正反用例、内容一路保留） [US1] 按 `quickstart.md` 一（1～5，四档各一遍，共 16 个正反用例）与二 6（内容保留：主区域、三个工具区、标签、垃圾桶一路升到下界合金）在 `./gradlew runClient` 逐项验证（先 `pgrep`）：三样齐全才有产出，模板与材料各消耗 1 个；缺模板、缺材料、材料换成单个钻石/下界合金锭、别档的模板都不产出；位置放错（模板放进基础格）不产出；材料块堆叠 ≥ 2 时只消耗 1 个；升级后内容零丢失。结果记到 DELIVERY.md（**由用户确认后才打勾**）

**Checkpoint**: US1 完成；升级规则就是用户要的样子。

---

## Phase 4: User Story 4 - 老存档与已有背包不受影响 (Priority: P1)

**Goal**: 已经存在的背包原样保留；旧格式（没有 `template`）的数据包配方仍然可用；协议不匹配被拒。

**Independent Test**: quickstart 二 7、五 13、14。

- [X] T009 [P] [US4] 静态检查：`git diff --stat` 确认本 feature **没有改动** `BackpackContents`、`FloatingBackpackItem`、`BackpackTier`、任何保存/编码相关类、任何面板或网络载荷类；`BackpackUpgradeRecipe.assemble` 一行未改；`grep -n "optionalFieldOf(\"template\"" …/BackpackUpgradeRecipe.java` 有命中（旧格式兼容）；`grep -rn "p3pp3rf1y\|tiviacz" …/inventory/recipe` 无命中（宪法 IV）。命令与结果记到 DELIVERY.md
- [ ] T010（2026-11-07 部分：二 7 旧世界已由用户验证通过；**五 13（无 `template` 的旧格式数据包配方）、五 14（局域网协议版本）未验证**，保持未勾选） [US4] 按 `quickstart.md` 二 7（改动前保存的世界）、五 13（临时数据包里没有 `template` 的旧格式升级配方仍可加载且要求模板格为空）、五 14（局域网：新版客户端连新版服务端一致；旧版客户端被协议版本拒绝；**没有条件就写"未验证"**）在 `./gradlew runClient` 验证（先 `pgrep`）。结果记到 DELIVERY.md（**由用户确认后才打勾**）

---

## Phase 5: User Story 2 - 配方在 JEI 里可见且完整 (Priority: P2)

**Goal**: JEI 里每档升级配方的模板格显示对应的模板。

**Independent Test**: quickstart 四（10～12）。

- [X] T011 [US2] 改 `…/compat/jei/FloatingBackpackJeiPlugin.java`：锻造扩展的 `setTemplate` 改为 `acceptor.addIngredients(recipe.template())`（模板为空则保持空槽）；JEI 类型仍只在这个文件里引用（宪法 VII）；`setBase`、`setAddition`、`setOutput` 不变。
- [X] T012（2026-11-07 用户验证 023-10/11/12 通过，含 `-PnoJei`；同时确认 006-JEI 修复） [US2] 按 `quickstart.md` 四（10～12）在 `./gradlew runClient`（有 JEI）与 `./gradlew runClient -PnoJei` 验证（先 `pgrep`）：JEI 里铁、金、钻石、下界合金背包各有一条锻造台配方，模板格显示对应模板、基础与材料正确；皮革配方显示新摆法；`-PnoJei` 进游戏无报错、升级与合成正常。**这一步同时确认上一轮 006-JEI 修复（升级配方在 JEI 里可见）**。结果记到 DELIVERY.md 与 `specs/006-tiered-backpacks/DELIVERY.md`（**由用户确认后才打勾**）

---

## Phase 6: User Story 3 - 皮革背包配方多用 2 个铁锭 (Priority: P2)

**Goal**: 皮革背包要 5 皮革 + 2 铁锭 + 1 线 + 1 箱子。

**Independent Test**: quickstart 三（8、9）。

- [X] T013 [P] [US3] 改 `src/main/resources/data/mcfloatingbackpack/recipe/leather_floating_backpack.json`：`key` 增加 `"I": { "item": "minecraft:iron_ingot" }`，`pattern` 改为 `["LSL", "LCL", "ILI"]`；`result` 不变。
- [X] T014（2026-11-07 用户验证 023-8/9 与 JEI 里的皮革新摆法通过） [US3] 按 `quickstart.md` 三（8、9）与四 11 验证（先 `pgrep`）：新摆法得 1 个皮革背包；旧摆法（7 皮革）无产出；JEI 里显示新摆法。结果记到 DELIVERY.md（**由用户确认后才打勾**）

---

## Phase 7: Polish & Cross-Cutting

- [X] T015 [P] 更新文档：`specs/006-tiered-backpacks/spec.md` 的 US2 验收与升级描述处加【023 修订】（四档材料改为铁块、金块、钻石块、下界合金块；要求各自的原版锻造模板；皮革配方改为 5 皮革 + 2 铁锭 + 线 + 箱子；**原文保留**）；`specs/006-tiered-backpacks/research.md` 里协议版本说明补 "023 → `\"11\"`"；`docs/MCFloatingBackpack.md` 的升级/配方相关处补一句 023 说明并指向 `specs/023-recipe-difficulty/`；**不要修改 `docs/ideas`**。
- [ ] T016（2026-11-07 部分：`compileJava test` 通过；`pgrep` 发现用户的客户端正在运行，图集度量与日志检查未做，待退出游戏后补跑） 运行最终回归：`./gradlew compileJava test`；先 `pgrep` 确认没有客户端在运行再跑 `design/demo/tools/check_atlas_budget.sh`，图集仍应为 2048×1024（本 feature 不新增资源，宪法 VI）；按 `quickstart.md` 六（15～17）重跑 006 升级部分与 021 Q39、检查 `run/logs/latest.log` 无新增配方相关报错；跑不了的写"未度量/未验证"，不要声称已满足。 ——2026-11-11 AI 自动验证：图集度量已跑（容器内 `check_atlas_budget.sh`，干净环境与带全部模组都是 2048×1024，通过）；006 升级部分与 021 Q39 的重跑、日志检查仍未做，未勾
- [X] T017 完成 `specs/023-recipe-difficulty/DELIVERY.md`（中文）：记录 T001、T003（`Ingredient.EMPTY` 核实结论）、T005、T007、T016 的结果，**每个客户端验证项实际验证了什么 / 没验证什么**；已知限制（模板概率读自战利品表、没有游戏内实测；盔甲纹饰模板在锻造台里的空槽提示图标与背包无关；Windows/Linux 未验证）；行为变化（升级要块 + 模板、皮革配方多铁锭）及用户是否接受；成本对照表引用 research D7；把 spec 的 Status 改为实现完成并如实写明验证未完成的部分。

---

## Dependencies & Execution Order

- **Phase 1（T001）** 最先；**Phase 2**：T002 ∥ T003 ∥ T004，T005 依赖前三项；T003 先于 T006（否则 `template` 字段没有被识别）。
- **US1（T006～T008）** 依赖 Phase 2；T006 → T007 → T008。
- **US4（T009、T010）** 依赖 T003/T004/T006；T009 随时可做。
- **US2（T011、T012）** 依赖 T003（`recipe.template()`）与 T006；T011 与 T013 改不同文件，可并行。
- **US3（T013、T014）** 只依赖 Phase 1，与 US1 互不冲突。
- **Phase 7**：T015 可与代码任务并行；T016、T017 在所有故事完成后。

### Parallel Opportunities

- T002 ∥ T003 ∥ T004；T011 ∥ T013 ∥ T015；T009 可随时做。

## Implementation Strategy

- **MVP = Phase 1 + Phase 2 + US1**：先让四档升级变成"块 + 模板"，用 T008 验证；然后补 US4（旧存档）、US3（皮革）、US2（JEI）。
- 每个故事结束都跑一次 `./gradlew compileJava test`。
- 宪法检查：每个会改变玩家所见/所操作的故事都有客户端会话验证任务（T008、T010、T012、T014）；没做的项在 DELIVERY.md 里记"未验证"，不能因为编译或单元测试通过就勾选（宪法 V）；不新增资源（T016 度量作回归，宪法 VI）；不引用第三方类型（T009，宪法 IV）；只做配方与材料（宪法 III）。
