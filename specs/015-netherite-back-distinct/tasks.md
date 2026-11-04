# Tasks: 下界合金背包前后面可区分

**Input**: Design documents from `/specs/015-netherite-back-distinct/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: 素材改动，没有 Java 单元测试可写；脚本内置纹素级自检，另用 `check_brightness.py`、`check_atlas_budget.sh`、渲染对照；玩家可见效果必须在游戏里验证（宪法 V），已列为任务，没做的项在 DELIVERY.md 里记"未验证"。

**Organization**: US1（背面一眼可辨）是主体，US2（不破坏其他与预算）是回归；方案 B 是条件任务。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；**不包含提交 Git 的任务**（AGENTS.md）；**跑 `check_atlas_budget.sh` 或启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，不要杀用户的游戏**

---

## Phase 1: Setup

- [X] T001 改动前基线：记录两张贴图的 SHA-256（`shasum -a 256 src/main/resources/assets/mcfloatingbackpack/textures/item/netherite_floating_backpack*.png`）、`netherite_backpack.obj` 的三角面数；`git stash` 不需要，原文件在 Git 里。先确认没有客户端在运行，再跑 `design/demo/tools/check_atlas_budget.sh` 记录图集尺寸与退出码；跑 `python3 design/demo/tools/check_brightness.py` 记录下界合金偏差。结果记到 `specs/015-netherite-back-distinct/DELIVERY.md`
  - **结果**：贴图 SHA-256（改动前）：基础色 `997d4c40…18fb`、`_n` `dfff2a69…bac4f`；三角面 20267；图集 2048×1024（估算显存 11.2MB）退出码 0；亮度偏差 netherite +2.9%
- [X] T002 [P] 用 Blender（MCP）导入当前随包的 `netherite_backpack.obj` 与贴图，从背面（-z）与正面（+z）各渲一张作为改动前对照，同时渲钻石、铁的背面；图放在 `specs/015-netherite-back-distinct/` 之外的会话临时目录，只在 DELIVERY 里记结论
  - **结果**：Blender MCP 在会话中途恢复可用；改动前的渲染用纯 Python 渲染器完成（正反两面几乎一样），改动后用 Blender（EEVEE，Emission + 最近邻）从背面与正面各渲一张，另渲钻石、铁的背面对照（Python 渲染器）

---

## Phase 2: User Story 1 - 一眼分出前后 (Priority: P1) 🎯 MVP

**Goal**: 背面独占纹素重画成平整深色背板（带肩带垫、接缝、小铆钉），法线写平；其余像素不变。

**Independent Test**: quickstart 一（1～3）+ 渲染对照；游戏内 quickstart 二（1、2、6）。

- [X] T003 [US1] 新建 `design/demo/tools/netherite_back_panel.py`（Pillow + 标准库）：解析 `netherite_backpack.obj`；选背面三角形（三个顶点 `z < zmin + 0.09` 且中心 `0.2 < x < 0.8`）；把背面与其余三角形分别栅格化到 512×512 UV 掩码（图像行 = `(1 - v) * 512`）；输出 `--check` 报告（背面三角形数、背面/其余/共享/独占纹素数）。预期 4997 / 47280 / 167918 / 714 / 46566
  - **结果**：`--check` 报告 背面三角形 4997 / 20267，纹素：背面 47280、其余 167918、共享 714、背面独占 46566，与 research 一致
- [X] T004 [US1] 在同一脚本里实现重画：独占纹素膨胀 1 格（仅膨胀到不被其他面使用的纹素）；对每个背面三角形逐纹素做重心插值得到 3D (x, y)，按 research Decision 3 的图案取色（固定随机种子；底色从掩码之外的非金色深灰纹素里取；两条竖向肩带垫、一条横向接缝、包边、四个小金铆钉）；基础色与 `_n` 同时写回 `src/main/resources/assets/mcfloatingbackpack/textures/item/netherite_floating_backpack.png` 与 `_n.png`；`_n` 的背面独占纹素写成当前 `_n` 里平整区域的众数（实测 `(127,129,255,255)`）。依赖 T003
  - **实现说明**：重画 61454 个纹素（独占 + 膨胀 1 格）；金色占比 0.70%。**发现并修复**：背板中央出现透光的方孔（原扣具环里的贯穿孔），新增 `design/demo/tools/patch_netherite_back_hole.py` 堵孔（FR-011，spec 已补）。已知小瑕疵：背板右下边缘有一条细金线（来自被其它面共用/不在背板范围的纹素），孔下方有一小段金线
- [X] T005 [US1] 自检（脚本内，失败则非零退出且不写文件）：掩码之外的像素改动前后逐像素相同（基础色与 `_n`）；背板范围内金色像素占比 ≤ 3%；背面独占纹素的 `_n` 全部等于平面值；再运行一次内容不变（幂等）。依赖 T004
  - **结果**：自检通过；对比改动前的原始贴图（git 中的版本）：基础色 61389 个像素变化、`_n` 56471 个，**全部落在背面独占掩码内，没有一个像素落在被其它面使用的纹素里**；重复运行不变（固定点，补孔板之后同样）
- [X] T006 [US1] 运行脚本并渲染对照：Python 渲染器与 Blender 各从背面、正面渲染，确认背面无翻盖/大扣具/底部口袋、正面不变、侧口袋不变；对照钻石、铁的背面；把结论写进 DELIVERY（含改动前后对比）。依赖 T004、T002
  - **结果**：Blender 与 Python 渲染都确认：背面是平整深色背板（两条肩带垫带缝线、接缝、4 个小金铆钉），无翻盖/大扣具/底部口袋；正面与改动前一致；侧口袋保持不变。与钻石、铁背面对照：平整程度相当。这只是渲染，**不能代替游戏内确认**（T007）
- [X] T007 [US1] 人工验证（用户）：按 `quickstart.md` 二（1、2、6）在 `./gradlew runClient` 看掉落物、展示框/第三人称、与钻石铁背面对比；记录是否"一眼是背面"。结果记到 `DELIVERY.md`（没做的项记"未验证"）
  - **用户验证（2026-11-05）**：用户进游戏验证 quickstart 二里需要确认的点，"暂无问题"；没有逐项记录具体观察。按用户结论勾选

**Checkpoint**: US1 达成 FR-001～FR-004、SC-001、SC-002、SC-003。

---

## Phase 3: User Story 2 - 不破坏其他表现与预算 (Priority: P1)

**Goal**: 图集预算、亮度、模型与贴身位置、其他四档都不受影响。

**Independent Test**: quickstart 一（4～6）+ 游戏内（3、5）。

- [X] T008 [US2] 回归：确认没有客户端在运行后，`design/demo/tools/check_atlas_budget.sh`（图集仍 2048×1024、退出码 0）；`python3 design/demo/tools/check_brightness.py`（下界合金偏差 ±10% 内）；`./gradlew compileJava test` 退出码 0；`git diff --stat` 确认只改了下界合金的两张贴图与新脚本/文档（其他四档、OBJ、Java 均无改动）；模型包围盒与背面最外平面 z≈0.238 不变（脚本/OBJ 未动即满足，记录）。结果记到 `DELIVERY.md`
  - **结果**：图集 2048×1024、估算显存 11.2MB、退出码 0（改动后，度量时没有客户端在运行，度量后无残留）；`check_brightness.py` 下界合金 −1.7%（限制 ±10%）；`./gradlew compileJava test` 退出码 0；`git diff --stat -- src` 只有 OBJ（+15 行，补孔板）和两张贴图；包围盒未变（补孔板在背面内侧 z=0.30，x 0.47–0.55，y 0.69–0.77，在原包围盒内）；三角面 20267 → 20271（+4，补孔板）
- [X] T009 [US2] 人工验证（用户）：按 `quickstart.md` 二（3～5）验证图标、手持、背上与改动前无可见差别、背面贴身无穿模、光影包下背面不再出现翻盖凸起；结果记到 `DELIVERY.md`
  - **用户验证（2026-11-05）**：用户进游戏验证 quickstart 二里需要确认的点，"暂无问题"；没有逐项记录具体观察。按用户结论勾选

**Checkpoint**: US2 达成 FR-005、FR-006、FR-009、SC-004、SC-005。

---

## Phase 4: 条件任务 - 方案 B（只有 A 不够才做）

- [X] T010 条件：若 T007/T009 里用户仍能认出翻盖形状（浮雕或光影法线），在 Blender 里对 `netherite_backpack.obj` 压平背面浅浮雕：选 T003 的背面三角形，把顶点 z 向外补到 0.238 平面（只向外、不削 0.238 平面），不增加面；用 `design/demo/tools/firstperson_holes.py` 复查手持视角是否出现新漏光；重新导出到随包 OBJ（`design/` 里的 OBJ 走 LFS，提交说明里注明仓库增长）；记录三角面数。**未触发则标记"未触发"，不勾选为完成**
  - **触发（2026-11-05）**：游戏内没有发现问题（T007/T009 暂无问题），但用户明确要求"继续处理方案 B"，所以做压平；这不是由"游戏里仍能认出翻盖"触发的，而是用户决定
  - **实现（2026-11-05）**：`design/demo/tools/flatten_netherite_back.py`；手持视角洞压平前后相同 [0,0,1,0,0,0,0]；包围盒/面数不变；脚本链重放逐字节一致；详见 DELIVERY“方案 B”。**用户已在游戏中验证没有问题（2026-11-05，未逐项记录），按用户结论勾选**

---

## Phase 5: Polish & 文档收尾

- [X] T011 [P] 文档：`design/demo/README.md` 补"下界合金背面"一节（背景、测量结论、脚本用法、`build_textures.py --install` 覆盖后需要重跑）；`specs/009-sculpted-mesh-backpack-models/spec.md` 的"下界合金正面不确定"处与 `specs/010-backpack-texture-budget/DELIVERY.md` 加【015 说明】；`docs/MCFloatingBackpack.md` M5 里五档背包外观一句补"下界合金背面为平整背板（015）"
- [X] T012 创建 `specs/015-netherite-back-distinct/DELIVERY.md`：记录 T001、T006、T008 的数值与 T007、T009 的用户确认情况，**包括没做到的和结论不确定的**；已知限制（脚本覆盖风险、只在背面可见场合才看得到差别、Windows/Linux 无差别）；方案 B 是否触发及原因
- [X] T013 运行最终回归（`./gradlew compileJava test`），把 spec 的 Status 改为实现完成，并如实写明验证未完成的部分

---

## Dependencies & Execution Order

- T001、T002 可并行先做（T001 的图集度量先确认无客户端在运行）。
- T003 → T004 → T005 → T006；T006 还依赖 T002。
- T007、T009 是用户的游戏内验证，在 T006 之后。
- T008 在 T004 之后任意时刻（需要无客户端运行）。
- T010 只在 T007/T009 结论显示 A 不够时做。
- T011 可与 T006～T009 并行；T012、T013 最后。

## Implementation Strategy

1. T001/T002 基线与对照渲染。
2. T003～T006：脚本 + 自检 + 渲染对照（MVP）。
3. 用户游戏内确认（T007、T009）；不够再 T010。
4. T008 回归、T011～T013 文档收尾。

### 与宪法的关系

- 宪法 V：背面外观必须在游戏里确认，渲染与脚本不代替；没确认的项在 DELIVERY 里记"未验证"。
- 宪法 VI：改动前后各度量一次图集，贴图仍 512、面数不增加；若动 OBJ 记录三角面数。
- 宪法 III：只改下界合金背面；其他四档与显示变换不动。
- AGENTS.md：不提交 Git，等你检查后再说。

### 风险提醒

- 误改其他面纹素：T005 的逐像素对比兜底。
- `build_textures.py --install` 覆盖：记录在 DELIVERY/README。
- 跑图集脚本会 `pkill` 客户端：先 `pgrep` 确认。
