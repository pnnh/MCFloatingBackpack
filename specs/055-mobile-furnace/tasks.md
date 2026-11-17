# Tasks: 移动熔炉

**Input**: `/specs/055-mobile-furnace/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 引擎（逐刻 vs 快进的随机对照）、经验取整、`MobileTool` 映射用 JUnit（纯 Java）；物品、模型、贴图、配方、语言键、组件编码用资源测试；接线用源码文本检查。**打开原版界面、烧制、关闭后补算、工具离开时关闭、退出再进入只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`，`…data/` = `src/main/resources/data/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；改存档前先备份世界；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增配置项、依赖、mixin，不用反射；**协议号保持 20（不新增包）**；**数据修订 5 → 6**；011～054 的测试除写死修订 5 的断言外不改
- 用户故事：US1 物品、配方、右区各一个；US2 点一下打开原版熔炉界面并烧制；US3 关掉后继续烧（补算）；US4 再点关闭、工具不在了就关、状态随物品走；US5 数据与版本

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 201 个类、1552 个用例，0 失败）；记在 `specs/055-mobile-furnace/DELIVERY.md`（新建，中文）；宪法 VI "之前"度量：`design/demo/tools/check_atlas_budget.sh`

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T002 `…/tool/furnace/FurnaceRules.java`、`FurnaceCore.java`、`FurnaceEngine.java`（`tickOnce` 逐刻移植 + `advance` 快进，contracts/rules.md §3）、`FurnaceExperience.java`（§6）
- [X] T003 [P] 单元测试 `…test/tool/furnace/FurnaceEngineTest.java`：具体用例（单件、多件、燃料中途烧完、原料中途烧完、产物放满、有剩余物的燃料、湿海绵、时间为 0、时间极大）+ **随机对照**（快进 = 逐刻，逐字段相等）+ 迭代次数有界；`FurnaceExperienceTest.java`
- [X] T004 `…/tool/MobileTool.java` 加 `FURNACE((byte) 1)`；同改 `…test/tool/MobileToolTest.java`
- [X] T005 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 物品、配方、右区各一个 (Priority: P1)

- [X] T006 [US1] `…/item/MobileFurnaceItem.java`；`ModItems` 注册 `MOBILE_FURNACE`、创造栏（移动工作台之后）；`never_trash.json` 加它
- [X] T007 [P] [US1] `design/demo/tools/make_mobile_tool_icons.py` 加熔炉图标，生成 `…res/textures/item/mobile_furnace.png`；模型 `…res/models/item/mobile_furnace.json`；目视检查放大预览
- [X] T008 [P] [US1] 配方 `…data/recipe/mobile_furnace.json`；语言键（contracts/resources.md）中英文
- [X] T009 [US1] 右区各一个：`BackpackClickPayload` 与 `FloatingPanelInput` 各加一条 `ToolSingleRules`，提示键 `limit.mobile_furnace`
- [X] T010 [P] [US1] 资源测试 `…test/resources/MobileFurnaceResourcesTest.java`
- [X] T011 运行 `./gradlew compileJava test`

## Phase 4: User Story 2～4 - 菜单、状态、补算、关闭 (Priority: P1) 🎯 MVP

- [X] T012 [US3] `…/tool/furnace/FurnaceState.java`（组件记录、编解码、与 `FurnaceCore` 互转）；`ModDataComponents.MOBILE_FURNACE_STATE`；组件编码测试
- [X] T013 [US2] `McFurnaceRules`（`RecipeManager` + `getBurnTime`）、`FurnaceStateContainer`、`MobileFurnaceMenu`（含产物槽子类、`ContainerData`、`broadcastChanges`、`stillValid`、`removed`）
- [X] T014 [US3] `MobileFurnaceStore`（读右区、经 `BackpackGate` 写回）；`MobileTools.itemOf` / `open` 加熔炉分支（受保护提示）
- [X] T015 [US2] `FloatingPanelInput`：点右区的移动熔炉发 `OpenToolPayload(FURNACE)` 并吞掉点击
- [X] T016 [US5] `DataRevision.CURRENT = 6`（含说明文字）；把写死 `CURRENT = 5;` 的 7 处历史断言改成 6（research R7）
- [X] T017 [P] [US2] 接线测试 `…test/resources/MobileFurnaceWiringTest.java`（包不新增、协议仍为 20、菜单形状、写回触发点、两处右区限制、客户端点击、无 mixin 与反射）
- [X] T018 运行 `./gradlew compileJava test`

**Checkpoint**: 全部代码完成，等游戏内验证

## Phase 5: 游戏内验证与文档

- [X] T019 游戏内验证（AI 自动验证，容器，quickstart 1～15；**不在 `src/main` 留调试入口**）：每项写实际验证了什么，做不了的写"未验证"；手感写"待用户确认"
- [X] T020 宪法 VI "之后"度量：`check_atlas_budget.sh`，与 T001 对比；没量写"未度量"
- [X] T021 [P] `README.md` 与 `README_CN.md`：移动熔炉一节；`docs/plan/mobile-tools.md` 状态改为"055 已实现"
- [X] T022 `specs/055-mobile-furnace/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么 / 没验证什么、**修订 5 → 6 的理由、协议号 20 不变**、已知局限、图集前后尺寸、宪法复查
- [X] T023 最终检查：`./gradlew compileJava test` 全部通过；无临时修改残留；**不提交、不打 tag**
- [X] T024 用户确认：真机点工具打开、烧制、关闭后补算、拿走工具时关闭、退出再进入；**只有用户明确确认才勾**

## Dependencies

- T002 → T003 / T004 → T005；T006 → T007 / T008 / T010 并行；T009 依赖 T008；T011 在其后。
- T012 → T013 → T014 → T015；T016 独立但要一起跑；T017 依赖 T012～T016；T018 在其后。
- T019 依赖全部代码；T020、T021、T022 在 T019 之后。

## Implementation Strategy

- **MVP**：Phase 1～4。一个 Phase 一次 `./gradlew compileJava test`，每个检查点都应是绿的。
- 不提交；做完、用户确认后由用户决定何时提交。
