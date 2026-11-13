# Tasks: 移动工作台

**Input**: `/specs/054-mobile-crafting-table/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 决策表、种类映射用 JUnit（纯 Java）；物品、模型、贴图、配方、语言键用资源测试；接线用源码文本检查。**打开原版界面、合成、关闭、工具离开时关闭只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`，`…data/` = `src/main/resources/data/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；改存档前先备份世界；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增配置项、依赖、mixin，不用反射；**协议号 19 → 20**（新的客户端到服务端的包）；**数据修订 4 → 5**；011～053 的测试除写死 19 与 4 的断言外不改
- 用户故事：US1 物品、配方、右区各一个；US2 点一下打开原版合成界面；US3 再点关闭、工具不在了就关；US4 数据与版本

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 197 个类、1530 个用例，0 失败）；记在 `specs/054-mobile-crafting-table/DELIVERY.md`（新建，中文）；宪法 VI "之前"度量：`design/demo/tools/check_atlas_budget.sh`（预期 2048x1024）

## Phase 2: Foundational（纯 Java，无 Minecraft 类型）

- [X] T002 `…/tool/MobileTool.java`（`CRAFTING_TABLE = 0`、`fromByte`、`id`）与 `…/tool/ToolOpenPlanner.java`（决策表，contracts/rules.md）；单元测试 `…test/tool/MobileToolTest.java`、`ToolOpenPlannerTest.java`（全部 8 行加未知种类）
- [X] T003 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 物品、配方、右区各一个 (Priority: P1)

- [X] T004 [US1] `…/item/MobileCraftingTableItem.java`；`…/item/ModItems.java` 注册 `MOBILE_CRAFTING_TABLE`、创造栏（移动唱片机之后）；`never_trash.json` 加它
- [X] T005 [P] [US1] `design/demo/tools/make_mobile_tool_icons.py` 加工作台图标，生成 `…res/textures/item/mobile_crafting_table.png`；模型 `…res/models/item/mobile_crafting_table.json`；目视检查放大预览
- [X] T006 [P] [US1] 配方 `…data/recipe/mobile_crafting_table.json`；语言键（contracts/resources.md）中英文
- [X] T007 [US1] 右区各一个：`BackpackClickPayload`（服务端）与 `FloatingPanelInput`（客户端预览）各加一条 `ToolSingleRules`，提示键 `limit.mobile_crafting_table`
- [X] T008 [P] [US1] 资源测试 `…test/resources/MobileCraftingTableResourcesTest.java`
- [X] T009 运行 `./gradlew compileJava test`

**Checkpoint**: 物品可合成、可放进右区、每区一个（点它还没有反应）

## Phase 4: User Story 2 + 3 - 打开原版界面、再点关闭、工具不在了就关 (Priority: P1) 🎯 MVP

- [X] T010 [US2] `…/tool/MobileMenu.java`、`…/tool/MobileTools.java`（`has`、`open`）、`…/tool/MobileCraftingMenu.java`（`CraftingMenu` 子类，`ContainerLevelAccess.create`，重写 `stillValid`）
- [X] T011 [US2] `…/network/OpenToolPayload.java`（包与处理器）；`BackpackNetwork` 注册并把协议改为 `"20"`
- [X] T012 [US2] `FloatingPanelInput`：点右区的移动工作台（光标空、左键、无 Shift / Ctrl / 拖拽）发 `OpenToolPayload` 并吞掉点击
- [X] T013 [US4] 协议与修订：`DataRevision.CURRENT = 5`；把写死 `versioned("19")` 的约 13 个历史测试改成 20，写死 `CURRENT = 4;` 的改成 5（research R6）
- [X] T014 [P] [US2] 接线测试 `…test/resources/MobileCraftingWiringTest.java`（contracts/resources.md "测试"）
- [X] T015 运行 `./gradlew compileJava test`

**Checkpoint**: 全部代码完成，等游戏内验证

## Phase 5: 游戏内验证与文档

- [X] T016 游戏内验证（AI 自动验证，容器，quickstart B1～B10；**不在 `src/main` 留调试入口**）：每项写实际验证了什么，做不了的写"未验证"；手感写"待用户确认"
- [X] T017 宪法 VI "之后"度量：`check_atlas_budget.sh`，与 T001 对比，都 ≤ 2048；没量写"未度量"
- [X] T018 [P] `README.md` 与 `README_CN.md`：移动工作台一节；`docs/plan/mobile-tools.md` 状态改为"054 已实现"
- [X] T019 `specs/054-mobile-crafting-table/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么 / 没验证什么、**协议号 19 → 20 与数据修订 4 → 5 的理由**、已知局限、图集前后尺寸、宪法复查
- [X] T020 最终检查：`./gradlew compileJava test` 全部通过；`git status` 无临时修改残留；**不提交、不打 tag**
- [ ] T021 用户确认：真机点工具打开、合成、再点关闭、界面衔接、JEI / EMI 转移；**只有用户明确确认才勾**

## Dependencies

- T002 → T003；T004 → T005 / T006 / T008 并行；T007 依赖 T006；T009 在其后。
- T010 → T011 → T012；T013 独立但要在 T011 之后一起跑；T014 依赖 T010～T013；T015 在其后。
- T016 依赖全部代码；T017、T018、T019 在 T016 之后。

## Implementation Strategy

- **MVP**：Phase 1～4（物品、请求、菜单、点击）。一个 Phase 一次 `./gradlew compileJava test`，每个检查点都应是绿的。
- 不提交；做完、用户确认后由用户决定何时提交。
