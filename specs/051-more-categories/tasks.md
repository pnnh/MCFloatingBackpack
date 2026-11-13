# Tasks: 更多分类扩展（9 个新类别）

**Input**: `/specs/051-more-categories/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 类别表、样式、预设用 JUnit（纯 Java）；标签 JSON、配方、模型、贴图、语言键用资源测试；`ItemCategoryMatcher` 的组件分支用源码文本检查。**放置、拒绝、行的外观、颜色与图标、黄点只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`，`…data/` = `src/main/resources/data/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；改存档前先备份世界；AI 自动验证的勾选规则同前（有明确证据才勾，主观项——底色好不好看、贴图能否分辨——只由用户确认）
- 不新增配置项、网络包、存储字段、依赖；协议号仍 19；**数据修订 2 → 3**；011～050 的测试除写死 10/13/11 的断言外不改
- 新类别 id（顺序固定）：`building natural plant decor mobdrop storage transport enchant meat`
- 用户故事：US1 新扩展件（物品、配方、行）；US2 各类别收对的东西（标签与匹配）；US3 界面（底色、幽灵图标、预设、语言）；US4 旧数据与修订

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 187 个类、1444 个用例，0 失败）；`git status` 干净（除 051 文档）；新建 `specs/051-more-categories/DELIVERY.md`（中文）记基线；宪法 VI "之前"度量：`design/demo/tools/check_atlas_budget.sh`（预期 2048x1024），量不了写"未度量"

## Phase 2: Foundational（类别表、预设、样式、修订、匹配）

- [X] T002 `…/inventory/extension/ExtensionCategories.java`：`IDS` 末尾追加 9 个 id；类注释里的"ten"改为实际数量；`ALL` 自动变 20 个
- [X] T003 [P] `…/inventory/tags/PresetTags.java`：`IDS` 末尾追加 8 个（`building natural decor mobdrop storage transport enchant meat`，`plant` 已有）；类注释补一句
- [X] T004 [P] `…/client/panel/CategoryStyle.java`：`EDGE` 加 9 行，颜色见 contracts/recipes-and-assets.md；类注释里的 ten/first draft 更新
- [X] T005 [P] `…/inventory/revision/DataRevision.java`：`CURRENT = 3`，类注释加"3 added the nine category extension items and categories (051)"
- [X] T006 `…/inventory/extension/ItemCategoryMatcher.java`：`enchant` 认 `DataComponents.STORED_ENCHANTMENTS`（紧跟 food、potion 的分支，**不认 `ENCHANTMENTS`**）；类注释补一句
- [X] T007 更新写死数量的现有测试（research R9）：`ExtensionCategoriesTest`（19 个 id、`ALL` 20、`subList(0, 19)`、`ALL.get(19)` 为 general、新 id ≤ 16 字符）、`RowSetTest`（`IDS` 列表）、`PresetTagsTest`（21 个，旧 13 个顺序不变）、`ExtensionTagsResourcesTest`（预设数、类别核对表）、`CategoryStyleTest`（改名、19 个边色互异）、`CategoryNamesTest`，其余（`HiddenHintTest`、`RowReconcilerTest`、`ExtensionRulesCategoriesTest`、`RowLayoutTest`、`UniversalExtensionResourcesTest`）`grep` 确认是否要改；`DataRevisionTest`、`BackpackContentsRevisionTest` 等对"当前是 2"的断言同步为 3（`grep -rn "CURRENT\|== 2\|(2," src/test` 逐个看）
- [X] T008 [P] `CategoryStyleTest` 新增：19 个类别的边色两两 ΔE76 ≥ 15（含通用）、与槽位灰 `0xFF8B8B8B` ≥ 20；Lab 换算写在测试里（纯 Java）

**Checkpoint**: 此时 `./gradlew test` 会因缺资源失败（模型、贴图、标签、配方、语言键），Phase 3～4 补齐后才绿——**Phase 2 与 3、4 之间不要停下运行全量测试**，各自只跑纯逻辑测试：`--tests '*ExtensionCategoriesTest' --tests '*PresetTagsTest' --tests '*CategoryStyleTest'`

## Phase 3: User Story 1 - 9 个新扩展件：物品、配方、贴图 (Priority: P1) 🎯 MVP

- [X] T009 [US1] `design/demo/tools/make_category_extension_icons.py`：`order` 与图案表加 9 个（图案见 research R7：砖墙、草方块、小花、画框、骨头、箱子、铁轨、书、牛排；沿用铁灰底板与 10×10 图案）；运行生成 9 张 `…res/textures/item/<id>_extension.png` 到仓库；用 `Read` 看放大预览核对 9 张两两不同、可辨
- [X] T010 [P] [US1] 9 个模型 `…res/models/item/<id>_extension.json`（同 `disc_extension.json`）
- [X] T011 [P] [US1] 9 个配方 `…data/recipe/<id>_extension.json`（图样 `IAI`/`BDB`/`IEI`，材料见 contracts/recipes-and-assets.md；`plant` 的 B 用 `{"tag": "minecraft:leaves"}`；`category: misc`；不加进度）
- [X] T012 [P] [US1] 语言键（`zh_cn.json`、`en_us.json`）：9 个物品名 `item.mcfloatingbackpack.<id>_extension`、8 个预设名 `tag.mcfloatingbackpack.preset.<id>`，内容见 contracts；`ModItems` 按 `IDS` 循环，创造栏顺序自动在现有分类扩展之后（核对：`ModItems` 里分类扩展循环在通用扩展之前）
- [X] T013 [US1] 更新 `CategoryExtensionResourcesTest`：`CORE` 表加 9 个配方的核心材料、模型、贴图 16×16、中英文名、配方图样与既有 10+5 个两两不同、图标互不相同；运行 `./gradlew compileJava` 与该测试（标签 JSON 尚未写，`ExtensionTagsResourcesTest` 留到 T016）

**Checkpoint**: 9 个扩展件可在创造栏取到、可合成（还没有标签，匹配只靠手动标签）

## Phase 4: User Story 2 - 每个类别收对的东西 (Priority: P1)

- [X] T014 [US2] 9 个数据标签 `…data/tags/item/extension/<id>.json`，内容严格按 contracts/categories.md：`#` 条目 `{"id": "#…", "required": false}`，物品 id 普通字符串；"另加物品"里的大家族（building 的约 89 个砖与铜家族、natural 的约 45 个地形杂物）用一次性脚本从 1.21.1 物品清单（client jar 的 `assets/minecraft/models/item`）按名字模式生成再写入，脚本放会话临时目录**不进仓库**；生成后逐个核对每个 id 都在物品清单里、没有重复
- [X] T015 [US2] `ExtensionTagsResourcesTest`：加 9 类的条目核对（`#` 条目都 `required: false`；`#c:shulker_boxes` 在 storage；`#minecraft:shulker_boxes` 与 `#c:dyed` 全部文件都不出现；quickstart 的"抽查物品表"里每类 ≥ 5 件应收的标签或物品 id 出现在文件里、不应收的不出现）；`ItemCategoryMatcher` 源码文本检查（`STORED_ENCHANTMENTS` 在 `enchant` 分支、没有 `ENCHANTMENTS` 的引用）
- [X] T016 [US2] 运行 `./gradlew compileJava test`（此时应全绿）；变异检查：把 `building.json` 里 `#minecraft:stairs` 去掉，`ExtensionTagsResourcesTest` 应失败，还原后通过

**Checkpoint**: 数据完整，`./gradlew test` 全绿

## Phase 5: User Story 3 + 4 - 界面与旧数据 (Priority: P2)

- [X] T017 [US3] 核对界面接线无需改动（`grep` 确认）：`ClientPlacement.categoryTagsBound` 遍历 `IDS`、`CategoryNames` 走 `PresetTags.langKey`、`TagPanelContext.dropdown` 用 `PresetTags.ids()`、`HiddenSummary` 用类别名键；任何硬编码 10 的地方记录并修正
- [X] T018 [US4] 数据修订接线测试：`DataRevisionTest` 当前为 3；`DELIVERY.md` 写明 2→3 的理由（研究 R6）与"用户自建的叫 `building` 等的手动标签升级后变类别标签"的已知变化
- [X] T019 运行 `./gradlew compileJava test`；`DependencyRulesTest` 通过

## Phase 6: 游戏内验证与文档

- [X] T020 游戏内验证（AI 自动验证，容器，quickstart B1～B14；**不在 `src/main` 留调试入口**；造背包用数据包函数）：每项写实际验证了什么，做不了的写"未验证"；颜色、贴图可辨性写"待用户确认"
- [X] T021 宪法 VI "之后"度量：再跑 `check_atlas_budget.sh`，与 T001 对比，都 ≤ 2048；没量写"未度量"
- [X] T022 [P] `README.md` 与 `README_CN.md`：分类扩展一节更新为 19 个类别（名字与收什么）；`docs/plan/category-extensions.md` 追加 051 一节；`docs/plan/release-process.md` 若列了修订历史则补"3 = 051"
- [X] T023 `specs/051-more-categories/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离（如标签清单的实际条数）、每个游戏内验证项验证了什么/没验证什么、**协议号不变（19）与数据修订 2→3 的理由**、已知变化（自定义标签重名）、已知缺口（刷怪蛋、模板、按钮与压力板等约 205 个）、图集前后尺寸、宪法复查
- [X] T024 最终检查：`./gradlew compileJava test` 全部通过；`git status` 无临时修改残留；**不提交、不打 tag**
- [X] T025 用户确认：9 种底色与贴图在游戏里的观感、9 vs 10 个类别、真机放置；**只有用户明确确认才勾**

## Dependencies

- T002～T006 可大体并行（不同文件），T007 依赖它们；T008 依赖 T004。
- T009 → T010～T012 可并行；T013 依赖 T009～T012。
- T014 依赖 T002；T015、T016 依赖 T014 与 T013。
- T017～T019 依赖 T016。T020 依赖全部代码与资源；T021～T023 在 T020 之后。

## Implementation Strategy

- **MVP**：Phase 1～4（表、物品、数据）——跑通就已经可用；Phase 5 只是核对与记录。
- Phase 2 之后全量测试是红的（缺资源），到 T016 才转绿；这期间只跑纯逻辑测试，不要被红的资源测试带偏。
- 不提交；做完、用户确认后由用户决定何时提交。
