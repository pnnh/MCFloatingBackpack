# Tasks: Curios 后背槽兼容

**Input**: `/specs/033-curios-back/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法 2.2.1

**Tests**: 数据文件内容、来源序号与依赖规则是纯文本/资源检查，用单元测试覆盖；与 Curios 的交互（放入、背着、渲染、`modLevels`、保存）只能在客户端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增网络包、协议改动、语言键、贴图、配方、设置项**

---

## Phase 1: Setup

- [x] T001 基线（032 完成后：102 个类、729 个用例；完成后 103 个类、734 个用例），记在 DELIVERY.md

## Phase 2: Foundational（构建、数据、规则）

- [x] T002 `build.gradle`：加仓库 `https://maven.theillusivec4.top/`（`content { includeGroup "top.theillusivec4.curios" }`）与 `compileOnly "top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1:api"`；确认 `./gradlew compileJava --offline` 之外的联网解析成功（`./gradlew dependencies --configuration compileClasspath` 里有它）
- [x] T003 [P] `src/test/java/pnnh/mcfloatingbackpack/rules/DependencyRules.java`：`THIRD_PARTY_GROUPS` 加 `top.theillusivec4.curios`；`src/main/templates/META-INF/neoforge.mods.toml`：加可选依赖 `curios`（`type="optional"`、`versionRange="[9,)"`、`ordering="NONE"`、`side="BOTH"`，格式照已有的 JEI 依赖）；运行 `DependencyRulesTest`
- [x] T004 [P] [US1] 数据文件：`src/main/resources/data/curios/tags/item/back.json`、`src/main/resources/data/mcfloatingbackpack/curios/entities/player_back.json`（内容见 contracts/data-files.md），测试 `src/test/java/pnnh/mcfloatingbackpack/resources/CuriosBackResourcesTest.java`（标签 `replace=false`、5 个档位的 id 与 `BackpackTier.id()` 一致且无多余项；实体文件含 `minecraft:player` 与 `back`、文件名不以 `_` 开头、没有 `neoforge:conditions`）
- [x] T005 `…/inventory/BackpackRef.java`：`Source` 末尾追加 `CURIOS(true)`（序号 4）；`BackpackRefTest` 增补（`CURIOS` 序号 4 且 `isWorn()`，已有序号不变）
- [x] T006 运行 `./gradlew compileJava test`

## Phase 3: User Story 1~4 - Curios 来源 (P1/P2)

- [x] T007 [US1][US3] `…/compat/curios/CuriosWornSource.java`：实现 `WornSource<LivingEntity, ItemStack>`（`id = Source.CURIOS.ordinal()`、`order = 10`、`active` 读 `CompatConfig.policy().specificAdaptation("curios")`、`slotCount`/`get`/`set`/`hiddenRender` 按 research D3：只看真实栈、未激活当空、隐藏渲染照抄 Curios 渲染层、越界与任何 `RuntimeException` 返回空且不抛）
- [x] T008 [US1] `…/compat/curios/CuriosCompat.java`（`register()` 调 `WornBackpacks.register(new CuriosWornSource())`）与 `…/MCFloatingBackpack.java`：构造函数里 `ModList.get().isLoaded("curios")` 时调用（主类里不出现任何 Curios 类型）
- [x] T009 [US4] 静态检查：`grep -rn "top.theillusivec4" src/main/java` 只命中 `compat/curios/`；`DependencyRulesTest` 通过；协议版本仍是 `"16"`；没有新增语言键
- [x] T010 运行 `./gradlew compileJava test`

## Phase 4: 验证与收尾

- [x] T011 [US1][US2] 按 quickstart A、B 验证放入/取出/背着/按 B/保存重进与顺序（先 `pgrep`；由用户确认后才打勾）。用户 2026-11-09 验证：功能没问题
- [x] T012 [US3] 按 quickstart C 验证隐藏渲染（由用户确认后才打勾）。用户 2026-11-09 验证：功能没问题
- [x] T013 [US4] 按 quickstart D 验证 `modLevels` 与没装 Curios（`-PcleanRuntime`）（由用户确认后才打勾）。用户 2026-11-09 验证：功能没问题
- [x] T014 [P] 更新文档：`docs/MCFloatingBackpack.md`（033 一句说明）、`docs/plan/manual-verification-checklist.md`（033 验证项）、`docs/plan/worn-backpack-compat.md`（033 已拆出、状态）；**不要修改 `docs/ideas`**
- [x] T015 图集度量：`design/demo/tools/check_atlas_budget.sh`（先 `pgrep`；本 feature 不新增贴图，应仍是 2048×1024；报"找不到主显示器"时写"未测量"并说明）
- [x] T016 `specs/033-curios-back/DELIVERY.md`（中文）：自动化结果、验证项实际验证了什么 / 未验证什么、已知限制

---

## Dependencies & Execution Order

Phase 1 → Phase 2 → Phase 3 → Phase 4。T003、T004 互相独立（`[P]`）；T007 依赖 T002、T005；T008 依赖 T007；T011～T013 依赖 T008。

## Implementation Strategy

MVP = Phase 2 + 3：数据文件、构建、来源与注册。先把资源与依赖规则的测试做绿，再写来源，最后由用户在游戏里验证。

### 风险提示

- **写回触发的 Curios 副作用**（T007、T011）：背包内容每次变化都会同步整只栈并发变化事件；验证时看有没有卡顿或刷屏日志。
- **类加载安全**（T007、T008、T013）：Curios 类型只能出现在 `compat/curios/`；`-PcleanRuntime` 启动是唯一能证明"没装也不报错"的检查。
- **隐藏渲染的判断要照抄 Curios 的**（T007）：`renders.size() > slot && renders.get(slot)` 才算渲染。
