# Tasks: 分类扩展：数据与规则

**Input**: `/specs/035-category-extension-rules/` 的 spec.md、plan.md、research.md、data-model.md、contracts/（rules.md、wire.md）、quickstart.md；宪法 2.2.1

**Tests**: 行列表核对、放入规则、目标格顺序、类别判定、上限是**不依赖 Minecraft 类的纯逻辑**，用泛型 + 假类型的单元测试覆盖（沿用 032 `BackpackSearch`）；数据文件与预设标签用文本/资源检查。`ItemStack` 的磁盘与网络编码往返需要游戏注册表，**只能在客户端会话里验证**（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；跑完 Gradle 后 `./gradlew --stop`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增**设置项、命令、配方、贴图、物品、新网络包；唯一新增的语言键是 3 个预设标签名
- 用户故事：US1 逻辑行随扩展件增减；US2 扩展行只收匹配的物品；US3 转移、聚拢、自动拾取的目标顺序；US4 数据永远不丢、限制可控；US5 现有功能不回归

---

## Phase 1: Setup

- [x] T001 基线（104 个类、738 个用例；完成后 109 个类、784 个用例，见 DELIVERY.md）：`./gradlew test` 记下当前类数与用例数（上一次是 104 个类、738 个用例），记在 DELIVERY.md；`git status` 确认工作区只有 035 文档与 `docs/plan` 的改动

## Phase 2: Foundational（常量与纯逻辑的基本类型）

- [x] T002 [P] `…/inventory/extension/ExtensionCategories.java`：`IDS`（`food tool weapon armor ore crop wood potion redstone disc`，顺序固定）、`ROW_CELLS = 9`、`MAX_ROWS = 35`、`MAX_TIER_CAPACITY = 108`、`MAX_MAIN_SLOTS = 423`、`isKnown(id)`（纯逻辑，不引用 Minecraft 类）
- [x] T003 [P] `…/inventory/extension/Row.java`（`record Row<T>(String category, List<T> cells)`）与 `RowSet.java`（`visible`/`hidden`、`empty()`、`total()`、`visibleCells()`、`visibleOf`/`hiddenOf`、`visibleCategories()`、`hiddenSummary(isEmpty)`、`sanitize`：每行补齐/截断到 9 格、类别截到 16 字符、总数超 35 先截 `hidden` 末尾再截 `visible` 末尾、`hidden` 里的空行删除；见 data-model.md）
- [x] T004 [P] `…/inventory/extension/CategoryMatcher.java`（接口：`boolean matches(String category, T stack)`）
- [x] T005 `…test/inventory/extension/RowSetTest.java`：不变式（9 格、总数 ≤ 35、隐藏行非空）、`sanitize` 的各种异常输入、`hiddenSummary`（按类别顺序、只数非空格）、`visibleCategories`
- [x] T006 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 逻辑行随扩展件增减 (P1)

**Independent Test**: 纯逻辑测试：给一个 `RowSet` 与各种 `placed`，核对结果（contracts/rules.md 第 1 节 R1～R12）。

- [x] T007 [US1] `…/inventory/extension/RowReconciler.java`：`reconcile(rows, placed, isEmpty, emptyCell, maxRows)` 返回 `Result(rows, layoutChanged, refused)`，按 contracts/rules.md 第 1 节：类别固定顺序；少了先删最后一个空行、没有空行才把最后一个可见行移到 `hidden` 末尾；多了先取 `hidden` 里最靠前的该类别行追加到 `visible` 末尾、否则总数 < 35 才新建空行、否则记入 `refused`；未知类别按 `N = 0`
- [x] T008 [US1] `…/inventory/extension/ExtensionRules.java` 的 `fits(rows, placedAfter, maxRows)`：需要新建的行数 = Σ max(0, `placedAfter[c]` − (`visibleOf(c)` + `hiddenOf(c)`))，不超过 `maxRows − total()` 才为真；接回隐藏行不受限
- [x] T009 [US1] `…test/inventory/extension/RowReconcilerTest.java`：R1～R11 逐条；R12 随机序列（固定种子，几千步放置/拿走/清空）每一步后所有格子里的物品总数不变、不变式成立；`layoutChanged` 与 `refused` 的取值；以及 `fits`（上限 35、接回不受限、光标上的已算入时不重复加）
- [x] T010 运行 `./gradlew compileJava test`

## Phase 4: User Story 2 - 扩展行只收匹配的物品 (P1)

**Independent Test**: 纯逻辑测试 + 资源检查；游戏里的放入路径在 Phase 6 才接上。

- [x] T011 [US2] `…/inventory/extension/ExtensionRules.java` 的 `canPlace(rows, baseCapacity, slot, stack, matcher)` 与 `rowOfSlot(baseCapacity, visibleRowCount, slot)`：基础容量内恒真；可见行看类别；隐藏行与越界为假
- [x] T012 [P] [US2] `…test/inventory/extension/ExtensionRulesTest.java`：`canPlace`（主区域恒真、匹配与不匹配、隐藏行与越界为假）、`rowOfSlot` 的边界
- [x] T013 [P] [US2] `…test/inventory/extension/CategoryMatcherTest.java`：用一个假的 `CategoryMatcher` 实现（数据来源 ∪ 手动标签）验证"或"关系、事后删标签后已放入的物品仍在行里（放入规则只管放入）、标签册每只背包各一份、同时命中同一类别不重复
- [x] T014 [P] [US2] 10 个数据标签文件 `src/main/resources/data/mcfloatingbackpack/tags/item/extension/{food,tool,weapon,armor,ore,crop,wood,potion,redstone,disc}.json`：`replace: false`；`#c:`/`#minecraft:` 引用都写成 `{"id": "...", "required": false}`；内容按 spec 决策 4 的并入关系表（食物只放 `#c:foods` 与显式补充，`food` 组件在代码里判；药水放 `#c:potions`；红石放 `#c:dusts/redstone`、`#c:storage_blocks/redstone` 与显式元件 `repeater comparator piston sticky_piston observer lever hopper dropper dispenser redstone_lamp redstone_torch daylight_detector target`）
- [x] T015 [P] [US2] `…/inventory/tags/PresetTags.java`：`IDS` 末尾追加 `crop`、`potion`、`disc`（前 10 个顺序不变）；`src/main/resources/assets/mcfloatingbackpack/lang/zh_cn.json`（农作物、药水、唱片）与 `en_us.json`（crop、potion、disc）各加 3 个键 `tag.mcfloatingbackpack.preset.<id>`
- [x] T016 [US2] 测试：更新 `…test/inventory/tags/PresetTagsTest.java`（完整 id 列表）；新增 `…test/resources/ExtensionTagsResourcesTest.java`（10 个文件存在、`replace: false`、所有 `#` 引用带 `required: false`、每个文件的内容与 spec 并入关系一致、`ExtensionCategories.IDS` 与文件名一一对应、`PresetTags` 含全部 10 个类别 id 且 13 个预设的中英文语言键齐全）；确认 `TagManagerResourcesTest`、`TagMatcherTest`、`TagProtectionTest` 仍通过
- [x] T017 [US2] `…/inventory/extension/ItemCategoryMatcher.java`（引用 Minecraft 类，构造参数是这只背包的 `ItemTags`）：`dataMatches` = `stack.is(TagKey<Item> mcfloatingbackpack:extension/<id>)`，食物另加 `DataComponents.FOOD`，药水另加 `DataComponents.POTION_CONTENTS`；`manualTagged` = 标签册里该物品注册 id 带有标签 `<id>`
- [x] T018 [US2] `…/inventory/extension/ExtensionPacks.java`（"扩展件解析器"）：`categoryOf(ItemStack)` 默认恒为空；`register(isPack, category)` 供 037 注册；`count > 1` 防御性按 1 个计并记日志
- [x] T019 运行 `./gradlew compileJava test`

## Phase 5: User Story 4 - 数据永远不丢，限制可控 (P1)

**Independent Test**: 单元测试覆盖常量与不变式；编码往返在游戏里验证（Phase 8 的 B3、B4）。

- [x] T020 [US4] 常量拆分：`…/inventory/BackpackInventory.java` 删除 `MAX_SIZE`，加 `MAX_TIER_CAPACITY`（= `ExtensionCategories.MAX_TIER_CAPACITY`）与 `MAX_MAIN_SLOTS`；让编译器指出所有引用，逐个按 contracts/wire.md 第 1 节改：`BackpackContents`（`items` 的编码与 `normalize/fit` 用 `MAX_TIER_CAPACITY`）、`BackpackSessionPayload`、`BackpackFullSyncPayload`（格子列表用 `MAX_MAIN_SLOTS`）、`BackpackClickPayload`（格子范围）、`QuickTransferRequest.MAX_BACKPACK_SLOTS`、`inventory/tags/TagRequest.MAX_SLOTS`；`SnapshotRequestRules.MAX_PREVIEW_SLOTS` 保持 108；注释里的 `MAX_SIZE` 一并更新
- [x] T021 [US4] `…/inventory/extension/ExtensionRowsCodecs.java`（引用 Minecraft 类）：`RowSet<ItemStack>` 的磁盘 `CODEC`（`visible`/`hidden` 各一个行列表，行 = `category` 字符串 + 9 个 `ItemStack.OPTIONAL_CODEC`）与 `STREAM_CODEC`（类别 ≤16 字符、列表 ≤35、9 × `ItemStack.OPTIONAL_STREAM_CODEC`），解码后 `sanitize` 并在截断时记日志
- [x] T022 [US4] `…/inventory/BackpackContents.java`：加第 7 个字段 `rows`；磁盘 `CODEC` 加 `optionalFieldOf("extension_rows", RowSet.empty())`；**网络编码改成手写 `StreamCodec.of(encode, decode)`**（前 6 段顺序不变，最后是 `rows`）；`normalize/fit/copy/empty/hasAutoPickup` 相关的构造都带上 `rows`；`fit(capacity)` 不动 `rows`；更新类注释里"六个字段"的说明
- [x] T023 [US4] `…/inventory/BackpackInventory.java`：`baseCapacity()`；处理器长度 = `baseCapacity + 可见行 × 9`；`ToolAreaCapacity.of(area, …)` 的实参改成 `baseCapacity`；`hiddenRows()`、`visibleRowCategories()`；`fromContents` 载入时核对（`placed` 从三个工具区与光标用 `ExtensionPacks.categoryOf` 现算）；`toContents` 保存时核对并拆回 `items`（前 `baseCapacity` 格）、`rows.visible`、`rows.hidden`；`fromItem/saveToItem` 随之；全文核对其余 `size()` 调用没有"其实想要基础容量"的（grep `\.size()` 与 `ToolAreaCapacity`）
- [x] T024 [US4] `…/inventory/recipe/BackpackUpgradeRecipe.java`：`assemble` 里的 `BackpackContents.fit(…)` 带上 `current.rows()`（**否则升级会丢掉所有扩展行与隐藏行**）；`…/inventory/ModItems`/`FloatingBackpackItem` 里的 `BackpackContents.empty(...)` 签名变化时一并适配
- [x] T025 [US4] `…/inventory/BackpackRef.java`：`saveInventory` 比较保存前后 `visibleRowCategories()`，不同就补发一次整份同步（`BackpackNetwork.sendFullSync`，仅对 `ServerPlayer`）
- [x] T026 [US4] 网络：`…/network/BackpackNetwork.java` 协议版本 `"16"` → `"17"`；`BackpackSessionPayload`、`BackpackFullSyncPayload` 按 contracts/wire.md 第 3.2 节新增 `baseCapacity`、`visibleCategories`、`hidden`（隐藏行汇总，不发物品本身）；`BackpackNetwork` 发会话/整份同步前量编码大小，超过 `SnapshotRequestRules.MAX_PREVIEW_BYTES`（900,000）不发、记 `WARN`、改发 `BackpackSessionPayload.closed()`
- [x] T027 [US4] `…/client/panel/ClientBackpackView.java`：保存 `baseCapacity`、`visibleRowCategories`、`hiddenSummary` 并提供只读访问；`applyFull` 里工具区容量的回退改用 `baseCapacity`；035 不用它们改布局；`clear()` 里复位
- [x] T028 [US4] 静态检查：`grep -rn "MAX_SIZE" src` 无命中；`grep -rn "versioned(" src/main/java` 是 `"17"`；`grep -rn "108" src/main/java` 只剩预期位置（档位容量、`MAX_PREVIEW_SLOTS`、注释）；没有引入第三方类型（`DependencyRulesTest`）
- [x] T029 运行 `./gradlew compileJava test`

## Phase 6: User Story 3 - 转移、聚拢、自动拾取的目标顺序 (P1)

**Independent Test**: 纯逻辑测试（目标格顺序、拖拽过滤）；游戏里的路径在 035 里只有"没有扩展行时与现状一致"可以验证。

- [x] T030 [US3] `…/inventory/extension/ExtensionRules.java` 的 `targetSegments(rows, baseCapacity, stack, matcher)`：两段，匹配的可见行（按行顺序、每行 9 个下标）→ `0..baseCapacity-1`；`…/inventory/BackpackInventory.java` 加 `mainTargetOrder(stack)` 与 `canPlaceMain(slot, stack)`（用自己的标签册构造 `ItemCategoryMatcher`）
- [x] T031 [P] [US3] `…test/inventory/extension/ExtensionRulesTest.java` 增补 `targetSegments`：没有扩展行或没有匹配行时第一段为空、第二段与现状一致；多类别匹配（工具行在前武器行在后，三叉戟）；匹配行都满了进主段；用 `QuickTransferLogic.moveInto`（假栈类型）验证"每段先并进没满的栈再放空格、剩余带到下一段"
- [x] T032 [US3] 序列型调用点改用 `mainTargetOrder`（两段各调一次 `QuickTransferLogic.moveInto`，剩余带到下一段）：`…/network/BackpackQuickTransferPayload.java`（物品栏 → 面板，两处）、`…/network/BackpackTakeFromContainerPayload.java` 与 `…/inventory/ContainerToBackpack.java`、`…/network/BackpackTrashPayload.java`（垃圾桶取回）、`…/inventory/pickup/PickupInsert.java`（加接受目标格顺序的重载，保留旧签名）与 `…/inventory/pickup/AutoPickupEvents.java`；**不改 `QuickTransferLogic.moveInto`**
- [x] T033 [US3] 单格型调用点放入前检查 `canPlaceMain`，不合格 = 拒绝、零变化并 `sendSlotUpdate`：`…/inventory/BackpackClickLogic.java`、`…/inventory/BackpackMenuBridge.java`、`…/network/BackpackClickPayload.java`（主手物品换进格子）、`…/network/BackpackCursorGesturePayload.java`（放入、拖拽分配过滤不合格的格子、聚拢取出永远允许、`pickupAll` 范围是 `0..size()-1`）；拖拽的客户端预览（`…/client/panel/PanelDragGesture.java`）不用改（035 没有可见行）
- [x] T034 [US3] `…/inventory/ToolAreaRules.java`：扩展件不受"每区一个"限制（核对现状已满足）；会新增行但行总数已达 35 时拒绝（`ExtensionRules.fits`，光标上已算着的扩展件不重复加；`ExtensionPacks.categoryOf` 默认认不出，所以 035 里不会触发）；`…/network/BackpackClickPayload.java` 把需要的参数传进来；`…test/inventory/ToolAreaRulesTest.java` 增补（用假解析器）
- [x] T035 [US3] 核对清单：`grep -rn "setStackInSlot\|moveInto\|PickupInsert" src/main/java` 逐个确认每一处往主区域写入的路径都已走 `mainTargetOrder` 或 `canPlaceMain`（含 `OpenContainerDelivery`、`CreativeCursorPush`、`MergePanelCursorPayload` 这些看起来只动光标的），把核对结果写进 DELIVERY.md
- [x] T036 运行 `./gradlew compileJava test`

## Phase 7: 收尾（自动化）

- [x] T037 [US5] 全量 `./gradlew compileJava test`，记下类数与用例数；`design/demo/tools/check_atlas_budget.sh`（先 `pgrep`）确认仍是 2048×1024；`./gradlew --stop`
- [x] T038 [US5] 读日志（2026-11-10：启动到主菜单，日志 0 个 ERROR；**物品标签是进世界后才加载，所以标签加载的报错要在 B1 进世界后看，未验证**）：用 `./gradlew runClient`（先 `pgrep`）启动到主菜单再退出，确认 `required: false` 的标签引用缺失时没有标签加载错误、没有新的警告（"没有任何扩展件物品时标签数据可安全加载"的自动证据）

## Phase 8: 验证与文档

- [x] T039 [US5][US4]（B2 用户 2026-11-10 看到下拉只有前 5 项、搜索能找到新标签，见 DELIVERY；B1 未确认）按 quickstart B1、B2 验证旧背包与现有功能不回归、标签预设多 3 项（先 `pgrep`；由用户确认后才打勾）
- [x] T040 [US4]（2026-11-10 搁置）按 quickstart B3 验证 `/give` 构造的行数据：组件同步没有断线、非空可见行被移到隐藏行、空可见行被删除、重进世界与升级后隐藏行和物品仍在（先把命令里的组件字段名改成真实写法；由用户确认后才打勾） ——**注意：这一项当时勾选只表示"搁置"，B3 的游戏内验证从未做过（`/data get` 输出太长无法检查），不能当作通过**；可改用存档 NBT 对比验证
- [x] T041 [US4] 按 quickstart B4 验证大背包没有回归、日志没有保护警告（由用户确认后才打勾） ——**注意：B4 的游戏内验证没有记录，勾选当时是随 035 收尾，不能当作"已验证大背包无回归"**
- [x] T042 [P] 更新文档：`docs/MCFloatingBackpack.md`（035 一句说明）、`docs/plan/manual-verification-checklist.md`（035 验证项）、`docs/plan/category-extensions.md`（035 状态）、`docs/plan/README.md`；**不要修改 `docs/ideas`**
- [x] T043 `specs/035-category-extension-rules/DELIVERY.md`：已验证 / 未验证（quickstart 第 C 节的四条原样列出）/ 已知限制（组件同步通路无保护、最坏体积未实测）/ 基线与完成后的类数与用例数 / T035 的核对结果

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → 其后：Phase 3（US1）与 Phase 4（US2）都只依赖 Phase 2，可以并行；T017/T018 依赖 T004、T014。
- Phase 5（US4）依赖 Phase 2、Phase 4 的 T017/T018（核对要用解析器）；T020 先做，其余按序。
- Phase 6（US3）依赖 Phase 5（运行时视图）与 Phase 4（`ItemCategoryMatcher`）。
- Phase 7、8 在全部实现之后；T039～T041 由用户确认。

## Parallel Opportunities

- T002、T003、T004 互相独立；T012、T013、T014、T015 互相独立；T031 与 T032 之后的检查可并行准备。

## Implementation Strategy

先做纯逻辑（Phase 2～4，不碰任何已有行为），再接数据与网络（Phase 5），最后改各条放入路径（Phase 6）。因为 035 没有扩展件物品，Phase 5、6 的改动在没有扩展行时必须与现状**完全一致**——每个 Phase 结束都跑一次测试，Phase 8 的 B1 是对这一点的最终检查。
