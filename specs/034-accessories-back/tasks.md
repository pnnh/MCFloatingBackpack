# Tasks: Accessories 后背槽兼容

**Input**: `/specs/034-accessories-back/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法 2.2.1

**Tests**: 数据文件内容、来源序号与依赖规则是纯文本/资源检查，用单元测试覆盖；与 Accessories 的交互（放入、背着、渲染、`modLevels`、保存）只能在客户端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增网络包、协议改动、语言键、贴图、配方、设置项**

---

## Phase 1: Setup

- [x] T001 基线（033 提交后：103 个类、734 个用例；完成后 104 个类、738 个用例），记在 DELIVERY.md

## Phase 2: Foundational（构建、数据、规则）

- [x] T002 `build.gradle`：加仓库 `https://maven.wispforest.io/releases`（`content { includeGroup "io.wispforest" }`）与 `compileOnly("io.wispforest:accessories-neoforge:1.1.0-beta.54+1.21.1") { transitive = false }`；确认 `./gradlew compileJava` 能联网解析；若 javac 报缺 owo / endec 的类型，按需再单独 `compileOnly` 那几个坐标（记在 DELIVERY.md）
- [x] T003 [P] `src/test/java/pnnh/mcfloatingbackpack/rules/DependencyRules.java`：`THIRD_PARTY_GROUPS` 加 `io.wispforest`；`src/main/templates/META-INF/neoforge.mods.toml`：加可选依赖 `accessories`（`type="optional"`、`versionRange="[1,)"`、`ordering="NONE"`、`side="BOTH"`，格式照已有的 Curios 依赖）；运行 `DependencyRulesTest`
- [x] T004 [P] [US1] 数据文件 `src/main/resources/data/accessories/tags/item/back.json`（内容见 contracts/data-files.md），测试 `src/test/java/pnnh/mcfloatingbackpack/resources/AccessoriesBackResourcesTest.java`（`replace=false`、5 个档位 id 与 `BackpackTier.id()` 一致且无多余项、路径正确、**没有**槽位分配文件）
- [x] T005 `…/inventory/BackpackRef.java`：`Source` 末尾追加 `ACCESSORIES(true)`（序号 5）；`BackpackRefTest` 增补（`ACCESSORIES` 序号 5 且 `isWorn()`，已有序号不变，`values().length == 6`，并把 033 的"`values().length == 5`"断言一并更新）
- [x] T006 运行 `./gradlew compileJava test`

## Phase 3: User Story 1~4 - Accessories 来源 (P1/P2)

- [x] T007 [US1][US3] `…/compat/accessories/AccessoriesWornSource.java`：实现 `WornSource<LivingEntity, ItemStack>`（`id = Source.ACCESSORIES.ordinal()`、`order = 20`、`active` 读 `CompatConfig.policy().specificAdaptation("accessories")`、`slotCount`/`get`/`set`/`hiddenRender` 按 research D3：只看真实栈、`!shouldRender(slot)` 为隐藏、`AccessoriesCapability.get` 为 `null` 或槽位缺失当没有，越界与任何 `RuntimeException` 返回空且不抛）
- [x] T008 [US1] `…/compat/accessories/AccessoriesCompat.java`（`register()` 调 `WornBackpacks.register(new AccessoriesWornSource())`）与 `…/MCFloatingBackpack.java`：构造函数里 `ModList.get().isLoaded("accessories")` 时调用（主类里不出现任何 Accessories 类型）
- [x] T009 [US1][US4] `…/compat/accessories/BackpackAccessoryRenderer.java`（实现 `AccessoryRenderer`，**不是** `EmptyRenderer`：`shouldRender(boolean)` 在 `accessories` 为 FULL 时返回 `false`、否则返回 `isRendering`；`render(...)` FULL 时什么都不做、否则转给 `DefaultAccessoryRenderer.INSTANCE.render(...)`；仅客户端）与 `AccessoriesClientCompat.java`（`registerRenderers()`：对 5 个档位各 `AccessoriesRendererRegistry.registerRenderer(item, () -> new BackpackAccessoryRenderer())`，每个档位一个新实例）；`…/client/ClientSetup.java`：`FMLClientSetupEvent` 的 `enqueueWork` 里，Accessories 已加载时调用 `registerRenderers()`
- [x] T010 [US4] 静态检查：`grep -rn "io.wispforest" src/main/java` 只命中 `compat/accessories/`；`DependencyRulesTest` 通过；协议版本仍是 `"16"`；没有新增语言键；`BackpackAccessoryRenderer` 与 `AccessoriesClientCompat` 没有被任何服务端代码引用
- [x] T011 运行 `./gradlew compileJava test`，并用 `FULL_MODS=1 design/demo/tools/check_atlas_budget.sh`（先 `pgrep`）与默认（干净、没有 Accessories）各启动一次，读日志确认两种情况下都没有与 Accessories 有关的错误（"没装不加载任何 Accessories 类"的自动证据）

## Phase 4: 验证与收尾

- [x] T012 [US1][US2] 按 quickstart A、B 验证放入/取出/背着/背上没有默认外观/按 B/保存重进与顺序（先 `pgrep`；由用户确认后才打勾）。用户 2026-11-09 验证：都没有问题
- [x] T013 [US3] 按 quickstart C 验证隐藏渲染（由用户确认后才打勾）。用户 2026-11-09 验证：都没有问题
- [x] T014 [US4] 按 quickstart D 验证 `modLevels` 与没装 Accessories（`-PcleanRuntime`）（由用户确认后才打勾）。用户 2026-11-09 验证：都没有问题
- [x] T015 [P] 更新文档：`docs/MCFloatingBackpack.md`（034 一句说明）、`docs/plan/manual-verification-checklist.md`（034 验证项）、`docs/plan/worn-backpack-compat.md`（034 状态，三个来源全部完成）；**不要修改 `docs/ideas`**
- [x] T016 图集度量：`design/demo/tools/check_atlas_budget.sh`（先 `pgrep`；本 feature 不新增贴图，应仍是 2048×1024；报"找不到主显示器"时写"未测量"并说明）
- [x] T017 `specs/034-accessories-back/DELIVERY.md`（中文）：自动化结果、验证项实际验证了什么 / 未验证什么、已知限制

---

## Dependencies & Execution Order

Phase 1 → Phase 2 → Phase 3 → Phase 4。T003、T004 互相独立（`[P]`）；T007 依赖 T002、T005；T008 依赖 T007；T009 依赖 T002；T012～T014 依赖 T008、T009。

## Implementation Strategy

MVP = Phase 2 + 3：数据文件、构建、来源、注册与渲染器。先把资源与依赖规则的测试做绿，再写来源与渲染器，最后由用户在游戏里验证。

### 风险提示

- **编译时缺 owo / endec 类型**（T002）：`transitive = false` 后若 javac 报错，按需单独 `compileOnly`。
- **渲染器注册时机**（T009、T012）：必须早于首次资源重载；quickstart A2（背上没有默认外观）直接验证。
- **类加载安全**（T007～T009、T014）：Accessories 类型只能出现在 `compat/accessories/`，客户端渲染器类只由客户端代码引用；干净环境启动（T011）是"没装也不报错"的自动证据。
- **Beta 版 API**：只用 `AccessoriesCapability`、`AccessoriesContainer`、`AccessoryRenderer`、`AccessoriesRendererRegistry`、`DefaultAccessoryRenderer`、`SlotReference` 几个公开 API。
