# Tasks: 通用扩展

**Input**: `/specs/038-universal-extension/` 的 spec.md、plan.md、research.md、data-model.md、contracts/（rules.md、items.md）、quickstart.md；宪法 2.2.1

**Tests**: 生效计数、类别顺序、三段目标顺序、`general` 匹配的纯逻辑部分、类别名、样式表、核对器的通用扩展场景用 JUnit 覆盖；物品、模型、贴图、配方、语言键与源码接线用资源/文本检查覆盖。物品类、绘制、交互依赖 Minecraft，**只能在客户端会话里验证**（宪法 V）：编译与单元测试通过不等于游戏里正确。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`，`RES/` 表示 `src/main/resources/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；跑完 Gradle 后 `./gradlew --stop`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增**配置项、命令、网络包、数据组件；**协议仍是 17**；**唯一对 035 规则的变更是目标顺序三段化**（更新测试并写明原因）；不修改 `docs/ideas`
- 用户故事：US1 五个物品（合成、创意栏、外观）；US2 放进左/右区同档背包多一行任意格；US3 生效规则（区、档位、每区一个、最多 2、不拒绝）；US4 三段目标顺序；US5 通用行的外观与文字；US6 升级、共存与回归

---

## Phase 1: Setup

- [x] T001 基线：`./gradlew test` 记下当前类数与用例数（上次 121 个类、851 个用例）；`git status` 确认工作区干净（只有 038 的文档）；记在 DELIVERY.md

## Phase 2: Foundational（纯逻辑，不碰游戏对象）

**⚠️ 之后所有用户故事都依赖本阶段**

- [x] T002 [P] `…/inventory/extension/ExtensionCategories.java`：加 `GENERAL = "general"` 与 `ALL`（`IDS` 加 `GENERAL`，`general` 最后）；`IDS` 本身**不变**（10 个）；`orderOf`、`isKnown` 改用 `ALL`；注释说明为什么 `general` 不进 `IDS`（见 research D1）。`…test/inventory/extension/ExtensionCategoriesTest.java`（新）：`IDS` 仍是原来的 10 个且顺序不变；`ALL` = `IDS` + `general` 且 `general` 最后；`orderOf("general")` 大于所有 `IDS`、小于未知 id；`isKnown("general")` 为真，`isKnown("plant")` 仍为假；`RowSetTest` 里原有断言不改并通过
- [x] T003 [P] `…/inventory/extension/PlacedPacks.java`（纯逻辑）：`Place`（LEFT、RIGHT、BOTTOM、CURSOR）、`Pack(category, tier, place)`、`count(List<Pack>, String backpackTier)` → `Map<String,Integer>`，规则见 data-model.md（分类扩展每个算 1 任何位置；通用扩展丢弃别档、`backpackTier == null`、下区，`general = min(2, 左有 + 右有 + 光标有)`，为 0 不放进结果；与输入顺序无关）。`…test/inventory/extension/PlacedPacksTest.java`：分类扩展与改动前 `countPlaced` 等价；通用扩展在左 1 / 右 1 / 左右各 1 / 左区 2 个 / 左 1 右 0 / 下区 / 主区域外的别档 / 同区有别档与同档各一个（与先后无关，用打乱顺序的输入）/ 光标上一个与左右区合计不超过 2 / 备份 tier 为 null / 没有通用扩展时结果相同
- [x] T004 [P] `…/inventory/extension/ExtensionRules.java`：`targetSegments` 两个重载都改成**三段**（类别行 → 基础容量 → 通用行；`general` 行不进第一段；一个物品属于多个类别行时第一段内仍按行的先后）；`canPlace` 对 `general` 行恒为真（经 `CategoryMatcher` 时 `general` 由匹配器返回真，规则本身不特判）。更新 `…test/inventory/extension/ExtensionRulesTest.java` 与 `ExtensionRulesCategoriesTest.java` 里断言"两段"的用例，**每处改动在注释里写明"规则变更：通用行是第三段"**；新增用例：只有通用行、类别行与通用行并存、多个通用行的先后、没有行时 `[空, 全部, 空]` 的形状、两个重载答案一致
- [x] T005 [P] `…/inventory/extension/CategoryNames.java`（纯逻辑）：`langKey(category)`：`general` → `category.mcfloatingbackpack.general`，其余 → `PresetTags.langKey`。`…test/inventory/extension/CategoryNamesTest.java`：10 个分类各给预设键、`general` 给自己的键、未知 id 也给一个键不抛异常
- [x] T006 `…/inventory/extension/RowReconciler.java`：`categoriesOf` 起始列表由 `IDS` 改为 `ALL`（逻辑不变）。`…test/inventory/extension/RowReconcilerTest.java` 加通用扩展场景：放着 0→1→2 个时行的增减；降到 0 时有物品的通用行被隐藏、空行删除；重新为 1 时恢复最早隐藏的一行；与分类行并存时互不影响；行总数到 35 时 `refused` 含 `general` 且不新增行；已有的核对测试一行不改并通过
- [x] T007 运行 `./gradlew compileJava test`

**Checkpoint**: 纯逻辑就绪，035/036/037 的既有测试通过（除 T004 里按规则变更更新的断言）

## Phase 3: User Story 2 + 3 - 同档背包的左/右区生效，不拒绝 (Priority: P1) 🎯 MVP

**Independent Test**: 游戏里放进左/右/下区、同区第二个、别档、光标挪动（quickstart B）。本阶段先让规则与物品在游戏里能用（资源在 Phase 4）。

- [x] T008 [US3] `…/inventory/extension/ExtensionPacks.java`：加档位解析器 `registerTier(Function<ItemStack,String>)` 与 `tierOf(stack)`（非通用扩展返回 null；空栈 null）；`countPlaced` 保留（分类扩展仍可用）
- [x] T009 [US3] `…/inventory/BackpackInventory.java`：`placedPacks()` 改成收集观测（左区、右区、下区各格 + 光标，每个扩展件一个 `Pack(类别, 档位解析器给的档位, 位置)`，`count > 1` 的栈照旧记警告并算一个），背包档位取 `BackpackTier.byCapacity(baseCapacity)` 的 `name().toLowerCase()`（找不到为 null），调 `PlacedPacks.count`；`mainTargetOrder` 在没有可见行时返回 `[空, 全部, 空]`
- [x] T010 [US3] `…/inventory/extension/ExtensionPackRules.java`：`canPlace` 对通用扩展（`ExtensionPacks.tierOf(incoming) != null`）恒为真，其余不变；注释说明"超过行总数 35 时由核对器不新增行，不拒绝"。`…test/inventory/extension/ExtensionPackRulesTest.java`（如果已有则加用例，没有就不加，用文本检查覆盖）
- [x] T011 [US2] `…/inventory/extension/ItemCategoryMatcher.java`：`matches("general", stack)` 对非空栈恒为真，先于数据标签键的查找（`KEYS` 仍只含 `IDS`）；`general` 不查手动标签。该类依赖 Minecraft，靠 T020 的源码文本检查与 `ExtensionRulesTest` 的纯逻辑用例覆盖
- [x] T012 [US1] `…/item/CategoryExtensionItem.java` 去掉 `final`；新增 `…/item/UniversalExtensionItem.java`（`UniversalExtensionItem(BackpackTier tier, Properties)`，类别固定 `ExtensionCategories.GENERAL`，`tier()`，重写 `appendHoverText` 加一行灰色提示 `item.mcfloatingbackpack.universal_extension.tooltip`，参数是档位名键 `tier.mcfloatingbackpack.<档位>`，并且**不再**加父类的"只能存 %s"那一行）
- [x] T013 [US1] `…/item/ModItems.java`：加 `UNIVERSAL_EXTENSIONS`（`LinkedHashMap<BackpackTier, DeferredHolder<Item, UniversalExtensionItem>>`，对 `BackpackTier.values()` 循环注册 `<档位小写>_general_extension`）；创意栏 `displayItems` 在 10 个分类扩展之后按档位顺序 `output.accept`
- [x] T014 [US3] `…/MCFloatingBackpack.java`：构造函数里（两端都执行，紧接现有 `ExtensionPacks.register(...)`）加 `ExtensionPacks.registerTier(stack -> stack.getItem() instanceof UniversalExtensionItem item ? item.tier().name().toLowerCase() : null)`
- [x] T015 运行 `./gradlew compileJava test`

**Checkpoint**: 规则与物品注册完成；没有资源时游戏会报缺模型/缺贴图（Phase 4 补），**此时不要启动游戏验证**

## Phase 4: User Story 1 - 五个物品：合成、创意栏、外观 (Priority: P1)

**Independent Test**: 资源检查；创意栏、合成、JEI、外观在 Phase 8 由用户确认。

- [x] T016 [US1] 贴图：在 `design/demo/tools/make_universal_extension_icons.py`（Pillow）逐像素画 5 个 16×16 RGBA 图标：036 同款铁灰描边圆角底板 + 10×10 的"开口箱子，里面是格子网"图案，主色按档位（皮革棕、铁灰、金黄、钻石青、下界合金深紫黑），输出 `<档位>_general_extension.png`；`--preview` 放大 12 倍并出一张 5 个并排的总览图，另与 036 的 10 个分类扩展拼一张对照图；脚本头部写明"手绘，没有源图，脚本即来源，未使用 ludo.ai / pixellab"。生成到 `RES/assets/mcfloatingbackpack/textures/item/`，预览图放 scratchpad 目录；用 `Read` 逐张目视检查（形状可辨认、5 档之间分得出、与 10 个分类扩展分得出、没有多余半透明杂点），不满意就改脚本重画，检查结果记在 DELIVERY.md
- [x] T017 [P] [US1] 5 个模型 `RES/assets/mcfloatingbackpack/models/item/<档位>_general_extension.json`（`minecraft:item/generated`，`layer0` 指向 `mcfloatingbackpack:item/<档位>_general_extension`）
- [x] T018 [P] [US1] 5 个配方 `RES/data/mcfloatingbackpack/recipe/<档位>_general_extension.json`：`minecraft:crafting_shaped`、`category: "misc"`、版型 `["ITI","LCR","IBI"]`，`I` = `minecraft:iron_ingot`，核心材料按 contracts/items.md 的表（`minecraft:chest`、`leather`、`hopper`、`string`、`iron_ingot`、`gold_ingot`、`diamond`、`netherite_ingot`、`ender_chest`、`shulker_shell`），结果 `{"id": "mcfloatingbackpack:<档位>_general_extension", "count": 1}`；**配方是草案，等用户确认，改只动 JSON 与 T020 里那张表**
- [x] T019 [P] [US1][US5] 语言键 `RES/assets/mcfloatingbackpack/lang/zh_cn.json` 与 `en_us.json`：5 个名字、`item.mcfloatingbackpack.universal_extension.tooltip`、5 个 `tier.mcfloatingbackpack.<档位>`、`category.mcfloatingbackpack.general`、`panel.mcfloatingbackpack.row.tooltip.general`（文字见 contracts/items.md）；两个文件键集合保持一致
- [x] T020 [US1] `…test/resources/UniversalExtensionResourcesTest.java`（文本/字节检查）：5 个档位各有模型（`generated` 父模型、`layer0` 指向对应贴图）、贴图（PNG 头 16×16、颜色类型 6、不透明像素 ≥ 120；5 张两两不同，与 10 个分类扩展、`auto_pickup`、`trash_can`、`tag_manager` 的也不同）、配方（`crafting_shaped`、版型 `["ITI","LCR","IBI"]`、四角铁锭、5 格材料与测试里写死的 contracts/items.md 表一致、结果 id 与数量 1、只引用原版物品、5 个配方两两不同且与分类扩展的不同）、语言键（两个文件各有 5 个名字、提示、5 个档位名、类别名、行说明，键集合一致，提示含 `%s`）；源码文本：`ModItems.java` 有 `UNIVERSAL_EXTENSIONS` 与 `_general_extension` 注册和创意栏循环、`MCFloatingBackpack.java` 有 `registerTier`、`ItemCategoryMatcher.java` 里 `general` 的特判在标签查找之前、`ExtensionPackRules.java` 对通用扩展恒允许、`BackpackInventory.java` 用 `PlacedPacks.count`
- [x] T021 运行 `./gradlew compileJava test`

## Phase 5: User Story 5 - 通用行的外观与文字 (Priority: P2)

**Independent Test**: 放通用扩展与分类扩展，看外观、悬停与黄点（quickstart D）。

- [x] T022 [US5] `…/client/panel/CategoryStyle.java`：加 `general` 的样式（`edge` 近白 `#F2F2F2`，`fill` = `edge` + 同一透明度）；`known("general")` 为真，但 `ghostItemId("general")` 返回 null（不画幽灵图标）。`…test/client/panel/CategoryStyleTest.java`：现有遍历 `IDS` 的用例不改；加 `general`：边线色与 10 个都不同、不等于槽底 `0xFF8B8B8B`、`fill` 与 `edge` 的 RGB 一致、没有幽灵图标
- [x] T023 [US5] `…/client/panel/FloatingPanelRenderer.java`：`drawRowTooltip` 与 `drawHiddenHint` 里取类别名改用 `CategoryNames.langKey`；`general` 行的悬停说明用 `panel.mcfloatingbackpack.row.tooltip.general`（不套"X行：只能放X类物品"）；确认 `drawGhost` 对 `general` 因 `ghostItemId` 为 null 而不画；其余绘制（底色、分隔线、角标）沿用 037 不动。`ClientPlacement` 不改（`categoryTagsBound` 仍遍历 `IDS`；`general` 经 `ExtensionRules` + 匹配器自然恒放行），在 DELIVERY.md 里记一句核对结果
- [x] T024 运行 `./gradlew compileJava test`

## Phase 6: 收尾（自动化）与 User Story 6 的静态部分

- [x] T025 静态检查：`grep -rn 'versioned(' src/main/java` 仍是 `"17"`；本 feature 没有新增任何 `CustomPacketPayload`、设置项、命令、数据组件；`git diff` 里 `…/inventory/extension/` 只有 `ExtensionCategories`、`ExtensionRules`（三段）、`ExtensionPacks`、`ExtensionPackRules`、`ItemCategoryMatcher`、`RowReconciler`（`ALL`）的预期改动与新增的 `PlacedPacks`、`CategoryNames`；`ToolAreaRules` 没有改动；`DependencyRulesTest` 通过
- [x] T026 全量 `./gradlew compileJava test`，记下类数与用例数；`./gradlew --stop`
- [x] T027（2026-11-10：先备份日志为 latest.before-038.log，再度量：2048×1024，通过）图集度量（宪法 VI，5 张新贴图）：**先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`（有客户端在跑就不跑）**；**先把 `run/logs/latest.log` 备份成 `run/logs/latest.before-038.log`**（度量脚本会删它）；再跑 `design/demo/tools/check_atlas_budget.sh`，记录尺寸（现状 2048×1024，预算 2048）；度量不了就写"未度量"；`./gradlew --stop`
- [x] T028（启动到主菜单：0 个 ERROR，WARN 只有开发环境里原版资源包的 URL 提示；配方与标签要进世界后才加载，未验证）读 `run/logs/latest.log`（度量启动到主菜单后的新日志）：没有与本 feature 的模型、贴图、配方有关的 ERROR/WARN；注意配方与标签要进世界后才加载，所以这一项**只算启动阶段的证据**

## Phase 7: 验证与文档（客户端验证由用户确认）

- [x] T029（用户 2026-11-10 确认功能没有问题；配方草案与成本观感用户没有单独说，仍按草案）[US1] 按 quickstart A 验证物品：创意栏顺序与名字、逐个合成、JEI、外观、5 个图标可分辨、堆叠 1、提示、日志（先 `pgrep`；由用户确认后才打勾；**同时请用户确认配方草案与成本观感**）
- [x] T030（用户 2026-11-10 确认功能没有问题；配方草案与成本观感用户没有单独说，仍按草案）[US2][US3] 按 quickstart B 验证生效规则：左/右区生效、下区与主区域不生效且不拒绝、同区第二个、第三个、别档、混放、拿走与放回、光标挪动、保存重进（由用户确认后才打勾；`logging.level` 建议 DEBUG）
- [ ] T031 [US4] 按 quickstart C 验证三段顺序：转移、自动拾取、垃圾桶取回、从容器取物、三叉戟、点击与拖拽不受影响、没有行时不变（由用户确认后才打勾） ——2026-11-11 AI：C1、C2、C5（用铁斧）通过，C3 同路径，C4/C6/C7/C8 没测，未勾
- [ ] T032 [US5] 按 quickstart D 验证外观与文字：底色、悬停说明、无红色高亮、黄点"通用"、分类行预测仍有效（由用户确认后才打勾；**底色初稿近白，看过后可调**） ——2026-11-11 AI：D1 的色差、D2、D3、D4 通过；底色观感要你亲眼看，未勾
- [ ] T033 [US6] 按 quickstart E 验证升级换档、总格数 +18、行总数 35、快照、Curios/Accessories、创造模式与主机屏、旧背包回归、图集（由用户确认后才打勾） ——2026-11-12 AI：E1、E2（铁档）、E6 通过；E3、E4 没测，E5 部分，未勾
- [x] T034 [P] 更新文档：`docs/MCFloatingBackpack.md`（038 一句说明）、`docs/plan/manual-verification-checklist.md`（038 验证项 A～E）、`docs/plan/category-extensions.md`（038 状态）；**不要修改 `docs/ideas`**
- [x] T035 `specs/038-universal-extension/DELIVERY.md`：已验证 / 未验证 / 已知限制（配方是草案、成本是粗估且最低两档只是略低、底色初稿、升级换档后旧档扩展失效、图集度量结果）/ 基线与完成后的类数与用例数 / 对规格的说明（`general` 在 `ALL` 不在 `IDS`）/ 035 的目标顺序变更与更新了哪些测试

---

## Dependencies & Execution Order

- Phase 1 → Phase 2（T002～T006 互相独立，可并行；T007 汇总）→ Phase 3。
- Phase 3 内：T008 → T009；T010、T011 依赖 T008；T012 → T013 → T014；T015 最后。
- Phase 4（资源）依赖 T013 的物品 id；T016 → T020；T017、T018、T019 互相独立，可并行；T020 依赖 T016～T019。
- Phase 5 依赖 T005、T019；Phase 6 在全部实现之后；Phase 7 由用户确认，T034 可提前准备。
- **Phase 3 完成后不要启动游戏**，要等 Phase 4 的资源齐了才能进游戏（否则缺模型/贴图）。

## Parallel Opportunities

- T002、T003、T004、T005；T017、T018、T019。

## Implementation Strategy

先纯逻辑（Phase 2），让生效规则、三段顺序与类别名有测试保护，核对器本身不动；再接数据（Phase 3）与资源（Phase 4）；最后界面文字（Phase 5）。MVP 是 Phase 2 + 3 + 4：有了 5 个物品与规则，游戏里就能验证生效、顺序与升级换档。最不确定的是：光标算法、升级换档的观感、配方成本、近白底色与悬停高亮的相似度，这些都只能在游戏里看，T029～T033 由用户确认。
