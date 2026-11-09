# Tasks: 背包在 GUI 里用平面图标

**Input**: Design documents from `/specs/028-gui-flat-icons/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md；宪法 2.2.0；011～027 已实现（026、027 提交在 `3f36e7f`）

**Tests**: 模型 JSON 的结构与图标文件是文本与文件检查，用单元测试覆盖，**测试任务排在实现之前**；资源重载是否报错用启动客户端看日志；帧率与外观只能在客户端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/models/` 表示 `src/main/resources/assets/mcfloatingbackpack/models/item/`，`…/textures/` 表示 `…/assets/mcfloatingbackpack/textures/item/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- 五档的前缀：`leather`、`iron`、`gold`、`diamond`、`netherite`（`<tier>_floating_backpack`）
- **不修改 OBJ、皮肤贴图、手持与背在背上的变换、配方；不新增设置；不限制任何模式或界面**

---

## Phase 1: Setup

- [x] T001 基线：`./gradlew compileJava test`，记录退出码、测试类数量与用例数（027 之后为 86 个类、625 个用例）；图集改动前为 2048×1024（2026-11-08）；结果记到 `specs/028-gui-flat-icons/DELIVERY.md`（新建，中文）

---

## Phase 2: Foundational

- [x] T002 [P] 新建测试 `src/test/java/pnnh/mcfloatingbackpack/resources/BackpackIconResourcesTest.java`（**先写，先失败**），文本与文件检查，五档逐个：①`…/models/<tier>_floating_backpack.json` 含 `"loader": "neoforge:separate_transforms"`、`"gui_light": "front"`，`base` 的 `parent` 是 `mcfloatingbackpack:item/<tier>_floating_backpack_3d`，`perspectives` 里有 `gui`、**不含 `ground`、`fixed` 与任何 `firstperson_`/`thirdperson_`/`head`**，`gui` 的 `parent` 是 `mcfloatingbackpack:item/<tier>_floating_backpack_icon`；②`…/models/<tier>_floating_backpack_3d.json` 含 `neoforge:obj` 与 `display` 里的 `fixed`（背在背上依赖它）、`firstperson_righthand`；③`…/models/<tier>_floating_backpack_icon.json` 的 `parent` 是 `minecraft:item/generated`，`layer0` 是 `mcfloatingbackpack:item/<tier>_floating_backpack_icon`；④`…/textures/<tier>_floating_backpack_icon.png` 存在，用 `javax.imageio` 读出是 32×32、有透明像素、有不透明像素、四周一圈像素是透明的（图集接缝）；⑤五张图标两两不同

---

## Phase 3: User Story 1/2 - 图标与模型重组 (Priority: P1) 🎯 MVP

**Goal**: 五档背包在 GUI 用平面图标，外形与 3D 一致；手持、背在背上不变。

- [x] T003 [US2] 新建 `design/demo/tools/render_backpack_icons.py`（纯 Python + PIL）：读 `…/models/<tier>_backpack.obj` 与 `…/textures/<tier>_floating_backpack.png`，按 research D3 渲染，输出 `…/textures/<tier>_floating_backpack_icon.png`；五档共用缩放比例；确定性（同样输入同样输出）；运行后**读五张 PNG 目视检查**朝向、配色、边缘，与 2026-11-08 快捷栏截图的外观对照，朝向不对就调旋转符号再渲
- [x] T004 [US1] 重组模型：每档把 `…/models/<tier>_floating_backpack.json` 原样复制为 `<tier>_floating_backpack_3d.json`（**内容不改**）；新建 `<tier>_floating_backpack_icon.json`（`item/generated`）；把 `<tier>_floating_backpack.json` 改成 `separate_transforms` 入口（research D4）。使 T002 通过
- [x] T005 运行 `./gradlew compileJava test`，确认 T002 与全部既有测试通过
- [x] T006 先 `pgrep`；确认没有游戏在跑后，跑 `design/demo/tools/check_atlas_budget.sh`（会启动客户端并触发资源重载）：记录图集宽高（改动后），并在 `run/logs/latest.log` 里找模型烘焙错误（`Unable to`、`Missing`、`Failed`、`separate_transforms`、`_floating_backpack` 相关的 ERROR/WARN）；**有错就走 research D4 的退化方案（把 3D 模型内容内联进 `base`）再测**；结果记到 DELIVERY.md；图集超 2048 或有模型错误则不得继续
- [x] T007 [US1] 按 `quickstart.md` A（A1～A4）在 `./gradlew runClient` 验证（先 `pgrep`）：与 2026-11-08 完全相同条件的帧率对照；**记录数值**到 DELIVERY.md（由用户确认后才打勾）
- [x] T008 [US2] [US3] 按 `quickstart.md` B（B1～B7）在 `./gradlew runClient` 验证（先 `pgrep`）：五档外观、手持、背在背上、展示框、掉落物（仍是 3D）、标题栏与 JEI；结果记到 DELIVERY.md（由用户确认后才打勾）

---

## Phase 4: User Story 4 - 不影响其它功能与预算 (Priority: P2)

- [x] T009 [US4] 按 `quickstart.md` C（C1、C3）在 `./gradlew runClient` 验证（先 `pgrep`）：011～027 的验证项回归；**ESC 菜单的 fps 复测并记录**；结果记到 DELIVERY.md（由用户确认后才打勾）

---

## Phase 5: Polish & Cross-Cutting

- [x] T010 [P] 更新文档：`docs/plan/perf-gui-model-cost.md`（状态改为"已由 028 处理"，结果待验证后补数据）、`docs/MCFloatingBackpack.md` 加 028 说明（GUI 用平面图标、手持与背在背上不变、图标由脚本渲出、图集增量与度量）、`specs/009-sculpted-mesh-backpack-models/spec.md` 加一句（GUI 与掉落物改用平面图标，见 028）、`docs/plan/manual-verification-checklist.md` 补 028 的验证项；**不要修改 `docs/ideas`**
- [x] T011 静态检查：`git diff --stat` 确认除"新增的图标、模型 JSON、脚本、测试与文档"外，OBJ、皮肤贴图、`BackpackBackLayer`、配方、任何 Java 文件**没有改动**；`<tier>_floating_backpack_3d.json` 与改动前的 `<tier>_floating_backpack.json` 内容逐字相同（`git show HEAD:…` 对比）
- [x] T012 新建/补全 `specs/028-gui-flat-icons/DELIVERY.md`（中文）：汇总自动化结果、与 plan 的偏离、**每个客户端验证项实际验证了什么 / 未验证什么**（尤其帧率数值、五档外观、手持与背在背上不变）、图集度量（改动前后）、ESC 菜单复测结果、已知限制（Windows/Linux 未验证；展示框与背在背上仍是 3D；光影下未验证）

---

## Dependencies & Execution Order

- Phase 1（T001）→ Phase 2（T002）→ Phase 3（T003 → T004 → T005 → T006 → T007/T008）→ Phase 4（T009）→ Phase 5。
- T003 与 T002 互相独立（`[P]` 的只有 T002 与 T010）。T004 依赖 T003（图标文件要先有）。

## Implementation Strategy

1. T001、T002、T003、T004、T005：全部可以不开游戏完成。
2. T006：启动客户端的资源重载检查（没有人玩的情况下）；有模型错误先走退化方案。
3. 用户验证 T007（帧率）与 T008（外观），确认修复有效且 3D 不变。
4. T009 回归与 ESC 菜单复测，最后文档与交付。

### 风险提示

- **`base` 引用 OBJ 模型**（T004、T006）：读源码倾向可以但没有在游戏里验证；退化方案是内联。
- **FIXED 不能换**（T002、T004）：背在背上依赖它；测试里明确检查 `perspectives` 不含 `fixed`。
- **图标朝向**（T003）：旋转顺序与符号容易搞反，必须目视检查，并与快捷栏截图对照。
- **图集接缝**（T002、T003）：图标四周必须有透明边。
- **帧率只能由用户测**（T007）：不得在没有测量时声称"已改善"。
