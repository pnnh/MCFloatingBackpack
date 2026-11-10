# Tasks: 分类扩展：10 个扩展件物品

**Input**: `/specs/036-category-extension-items/` 的 spec.md、plan.md、research.md、data-model.md、contracts/（recipes.md、resources.md）、quickstart.md；宪法 2.2.1

**Tests**: 模型、贴图格式、配方、语言键与类别一一对应用资源/文本检查覆盖；物品类依赖 Minecraft 注册表，单元测试不加载它。放入、行出现与消失、转移等真实行为只能在客户端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` 表示 `src/test/java/pnnh/mcfloatingbackpack/`，`RES/` 表示 `src/main/resources/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；跑完 Gradle 后 `./gradlew --stop`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增**界面、设置项、命令、网络包、数据组件；**协议仍是 17**；不改 035 的规则
- 用户故事：US1 10 个物品（合成、创意栏、外观）；US2 放进功能区行出现、拿走时数据不丢；US3 类别判定；US4 转移、自动拾取、聚拢、垃圾桶；US5 数量上限、升级与保存

---

## Phase 1: Setup

- [x] T001 基线：`./gradlew test` 记下当前类数与用例数（035 完成后 110 个类、789 个用例）；`git status` 确认工作区只有 036 的文档改动；记在 DELIVERY.md

## Phase 2: Foundational（物品类、注册、解析器）

- [x] T002 `…/item/CategoryExtensionItem.java`：`extends Item`，构造 `(String category, Properties)` 里 `super(properties.stacksTo(1))`，`category()`；重写 `appendHoverText(stack, context, tooltip, flag)`，在 `super` 之后加一行灰色文字 `Component.translatable("item.mcfloatingbackpack.category_extension.tooltip", Component.translatable(PresetTags.langKey(category)))`
- [x] T003 `…/item/ModItems.java`：加 `CATEGORY_EXTENSIONS`（`LinkedHashMap<String, DeferredHolder<Item, CategoryExtensionItem>>`），静态初始化里按 `ExtensionCategories.IDS` 循环 `ITEMS.register(id + "_extension", () -> new CategoryExtensionItem(id, new Item.Properties()))`；创意栏 `displayItems` 在 `AUTO_PICKUP` 后面按同一顺序循环 `output.accept`
- [x] T004 `…/MCFloatingBackpack.java`：构造函数里（两端都执行）加 `ExtensionPacks.register(stack -> stack.getItem() instanceof CategoryExtensionItem item ? item.category() : null)`，并加注释说明 035 的规则从此有了真实的扩展件
- [x] T005 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 10 个物品：合成、创意栏、外观 (P1)

**Independent Test**: 资源检查；创意栏、合成、JEI 与外观在 Phase 6 由用户确认。

- [x] T006 [US1] `design/demo/tools/make_category_extension_icons.py`（Pillow）：用法 `make_category_extension_icons.py <输出目录> [--preview <放大图目录>]`；按 contracts/resources.md 的风格逐像素画出 10 个 16×16 RGBA 图标：统一的 1 像素深色描边铁灰圆角底板（左上亮右下暗）+ 10×10 的类别图案（面包、镐、剑、头盔、带矿点的石块、麦穗、原木截面、药水瓶、红石粉堆、唱片），每个类别有自己的主色；输出文件名 `<类别>_extension.png`；`--preview` 同时输出放大 12 倍的图（再加一张 10 个拼起来的总览图），脚本头部说明"手绘，没有源图，脚本即来源，未使用 ludo.ai / pixellab"
- [x] T007 [US1]（2026-11-10 已目视检查放大预览：10 个形状可辨认、彼此分得出、底板一致，在 18 像素格子大小下也清楚；一次出图，没有重画）运行脚本把 10 张图生成到 `RES/assets/mcfloatingbackpack/textures/item/`，预览图放到 scratchpad 目录；用 `Read` 逐张目视检查放大图（形状可辨认、10 个彼此分得出、底板一致、没有多余的半透明杂点），不满意就改脚本重画；检查结果记在 DELIVERY.md
- [x] T008 [P] [US1] 10 个物品模型 `RES/assets/mcfloatingbackpack/models/item/<类别>_extension.json`：`{"parent": "minecraft:item/generated", "textures": {"layer0": "mcfloatingbackpack:item/<类别>_extension"}}`（格式同 `auto_pickup.json`）
- [x] T009 [P] [US1] 10 个配方 `RES/data/mcfloatingbackpack/recipe/<类别>_extension.json`：`minecraft:crafting_shaped`、`category: "misc"`、版型 `["ITI","LCR","IBI"]`，键与材料严格按 contracts/recipes.md 的表（`I` = `minecraft:iron_ingot`；标签写 `{"tag": "minecraft:logs"}`、`{"tag": "c:music_discs"}`），结果 `{"id": "mcfloatingbackpack:<类别>_extension", "count": 1}`；同类别里相同材料共用同一个键字母
- [x] T010 [P] [US1] 语言键 `RES/assets/mcfloatingbackpack/lang/zh_cn.json` 与 `en_us.json`：10 个 `item.mcfloatingbackpack.<类别>_extension` 名字、通用提示 `item.mcfloatingbackpack.category_extension.tooltip`、提示 `message.mcfloatingbackpack.limit.extension_rows`（文字见 contracts/resources.md）；两个文件键集合保持一致
- [x] T011 [US1] `…test/resources/CategoryExtensionResourcesTest.java`（文本/字节检查）：`ExtensionCategories.IDS` 的每个类别都有模型（`generated` 父模型、`layer0` 指向对应贴图）、贴图（PNG 头 16×16、颜色类型 6、解码后不透明像素 ≥ 120 个——用 `javax.imageio` 读，没有的话只查头；10 张两两不同、与 `auto_pickup`、`trash_can`、`tag_manager` 的也不同）、配方（`crafting_shaped`、版型 `["ITI","LCR","IBI"]` 形状、四角是 `I` 且 `I` 为铁锭、5 格材料与测试里写死的 contracts/recipes.md 表一致、结果 id 与数量 1、没有引用任何第三方模组物品；10 个配方（版型 + 键）两两不同）、语言键（两个文件各有 10 个名字、提示、限制消息，且键集合一致）、`ModItems.java` 源码里有 `"_extension"` 的循环注册与创意栏循环、`MCFloatingBackpack.java` 里有 `ExtensionPacks.register`
- [x] T012 运行 `./gradlew compileJava test`

## Phase 4: User Story 5 - 数量上限的提示 (P2)

**Independent Test**: 下界合金背包放满 35 个扩展件再放第 36 个（Phase 6 验证）。

- [x] T013 [US5] `…/network/BackpackClickPayload.java`：`ExtensionPackRules.canPlace` 为假、拒绝放入的那一处，除了 `sendSlotUpdate` 与 `return` 之外，给玩家发一条 actionbar 消息 `serverPlayer.displayClientMessage(Component.translatable("message.mcfloatingbackpack.limit.extension_rows"), true)`；不改 `ExtensionPackRules` 本身的判定
- [x] T014 运行 `./gradlew compileJava test`

## Phase 5: 收尾（自动化）

- [x] T015 静态检查：`grep -rn 'versioned(' src/main/java` 仍是 `"17"`；本 feature 没有新增任何 `CustomPacketPayload`、设置项、命令；`git diff` 里 `…/inventory/extension/` 没有改动（035 的规则一行未动）；`DependencyRulesTest` 通过
- [x] T016 全量 `./gradlew compileJava test`，记下类数与用例数；`design/demo/tools/check_atlas_budget.sh`（先 `pgrep`）度量图集并记录（现状 2048×1024，预算不超过 2048；度量不了就写"未度量"）；`./gradlew --stop`
- [x] T017（2026-11-10：启动到主菜单，日志 0 个 ERROR，没有与我们的模型、贴图有关的警告；配方与标签要进世界后才加载，未验证）读 `run/logs/latest.log`：启动到主菜单，没有与我们的模型、纹理、配方有关的 ERROR/WARN（缺贴图、缺模型、配方解析失败都会在这里出现）；注意配方与标签要进世界后才加载，所以这一项**只算启动阶段的证据**，进世界后的日志留给 Phase 6

## Phase 6: 验证与文档（客户端验证由用户确认）

- [x] T018 [US1]（用户 2026-11-10 确认没有问题） 按 quickstart B 验证物品本身：创意栏顺序与名字（中英文）、提示、逐个合成（含农作物扩展还回空桶）、JEI 配方、手持/掉落物/展示框外观、10 个图标可分辨、堆叠 1；进世界后看 `latest.log` 没有配方/模型/贴图错误（先 `pgrep`；由用户确认后才打勾）
- [x] T019 [US2] 按 quickstart C 验证放进功能区行出现与消失（先 `pgrep`；由用户确认后才打勾；记录过渡期面板现象，见 G）
- [x] T020 [US3] 按 quickstart D 验证类别判定与手动标签（由用户确认后才打勾）
- [x] T021 [US4][US5] 按 quickstart E 验证转移、自动拾取、聚拢、拖拽、垃圾桶、行总数 35 与提示、升级与快照恢复，并留意创造模式背包丢失事件是否再现（由用户确认后才打勾；`logging.level` 建议设 DEBUG）
- [x] T022 按 quickstart F、G 回归与过渡期现象（由用户确认后才打勾）
- [x] T023 [P] 更新文档：`docs/MCFloatingBackpack.md`（036 一句说明）、`docs/plan/manual-verification-checklist.md`（036 验证项）、`docs/plan/category-extensions.md`（036 状态、037 是下一个）、`docs/plan/README.md` 若需要；**不要修改 `docs/ideas`**
- [x] T024 `specs/036-category-extension-items/DELIVERY.md`：已验证 / 未验证 / 已知限制（过渡期、专用服务器未验证、贴图观感）/ 基线与完成后的类数与用例数 / 图标检查结果与图集度量 / 与 035 的关系（FR-014 补的提示）/ 035 的"未验证"四项在本 feature 里验证的结果

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → Phase 3 与 Phase 4 可并行（Phase 4 只依赖 T010 的语言键与 Phase 2）。
- Phase 3 内：T006 → T007 → T011；T008、T009、T010 互相独立，可与 T006、T007 并行；T011 依赖 T007～T010。
- Phase 5 在全部实现之后；Phase 6 由用户确认，T023 可提前准备。

## Parallel Opportunities

- T008、T009、T010；T006 与 T008～T010。

## Implementation Strategy

先让物品类与注册编译通过（Phase 2），再做全部资源（Phase 3）与一条提示（Phase 4）。MVP 是 Phase 2 + Phase 3：有了 10 个物品与解析器，035 的规则就能在游戏里被验证。贴图是最不确定的一块：T007 里用放大预览目视检查，必要时重画；游戏里的观感由用户在 T018 确认。
