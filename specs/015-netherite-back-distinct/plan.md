# Implementation Plan: 下界合金背包前后面可区分

**Branch**: `015-netherite-back-distinct` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-netherite-back-distinct/spec.md`

## Summary

下界合金背包的网格前后镜像对称，辨识度几乎全靠贴图（浮雕只有约 0.04 方块）。研究阶段（见 [research.md](./research.md)）测量得出的方案 A：

1. **只重画背面独占的纹素**：把背面中央背板的 4997 个三角形栅格化到 UV 掩码，只改"背面独占"的 46566 个纹素（共享的 714 个不动）。
2. **按 3D 位置取色的程序化图案**：深色背板、深色包边、两条肩带垫带缝线、一条接缝、四个小金铆钉；底色取自贴图里现有的深灰纹素，保持颗粒风格。
3. **法线写平**：背面独占纹素的 `_n` 写成平面值，光影下不再出现翻盖/扣具轮廓。
4. **脚本可重复**：`design/demo/tools/netherite_back_panel.py` 原地改写随包的 512 贴图；`build_textures.py --install` 之后需要重跑。
5. **验证**：纹素级对比（其余像素不变）、金色占比、亮度、图集预算；Blender 与 Python 渲染对照；**游戏内确认由用户做**。
6. **方案 B 条件触发**：只有游戏里仍能认出翻盖形状时，才压平背面的浅浮雕（只向外补到 z=0.238）。

范围：只改下界合金一档的两张贴图（可能的方案 B 再加 `netherite_backpack.obj`）；Java 代码不改。

## Technical Context

**Language/Version**: Python 3（脚本，仅用标准库与 Pillow，与 `build_textures.py` 一致）；Java 21 不涉及

**Primary Dependencies**: Pillow；Blender（MCP，仅辅助检查）；NeoForge 21.1.251 / Minecraft 1.21.1 的游戏内验证

**Storage**: `src/main/resources/assets/mcfloatingbackpack/textures/item/netherite_floating_backpack.png` 与 `_n.png`（512×512，普通 Git，不走 LFS）；设计源 `design/demo/obj_export/`（OBJ 走 LFS）默认不改

**Testing**: 没有 Java 单元测试可写；用脚本做纹素级断言（改动范围、其余像素不变、金色占比、法线平整度）、`check_brightness.py`、`check_atlas_budget.sh`；玩家可见效果必须在游戏里验证（宪法 V）

**Target Platform**: Minecraft 1.21.1 桌面客户端；开发机 macOS

**Project Type**: 素材改动（贴图 + 可能的模型），无运行时代码

**Performance Goals**: 无变化：贴图尺寸、图集尺寸、面数都不增加

**Constraints**:
- 图集必须保持 2048×1024（宪法 VI，`check_atlas_budget.sh` 度量；**跑之前先确认没有正在运行的客户端**）
- 只改背面独占纹素；背面最外平面 z≈0.238 与包围盒不变
- 随包文件不走 LFS；若动 `design/` 里的 OBJ 要在提交说明里注明仓库增长

**Scale/Scope**: 1 个新脚本、2 张贴图被改写；可能的 1 个 OBJ；文档（009/010 说明、`design/demo/README.md`）与 DELIVERY

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面 | PASS（不适用） |
| II. Server Owns Items | 不涉及物品逻辑 | PASS（不适用） |
| III. One Feature At A Time | 只做下界合金背面可区分；其他四档、模型大小、显示变换不动 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方 | PASS（不适用） |
| V. Verify In A Client Session | 背面外观必须在游戏里确认（掉落物旋转、第三人称、展示框、光影包开关）；Blender/Python 渲染只是辅助；按报告条件检查（真实的背面可见场合），不止在近似条件下看 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 贴图仍 512×512，图集仍 2048×1024；改动前后各跑一次 `check_atlas_budget.sh` 记录结果；面数不增加（记录三角面数） | PASS（已规划） |

**Re-check after Phase 1 design**：没有新增违反项。需要实现阶段盯住的风险：
1. **其他面的纹素被误改**：用纹素级逐像素对比断言 FR-003/FR-004；
2. **`build_textures.py --install` 覆盖**：DELIVERY 与 README 写明，必要时重跑脚本；
3. **跑图集脚本前确认没有客户端在运行**（它会 `pkill` 客户端进程）。

## Project Structure

### Documentation (this feature)

```text
specs/015-netherite-back-distinct/
├── plan.md              # 本文件
├── research.md          # Phase 0：决定与仍不确定项
├── quickstart.md        # Phase 1：验收步骤（脚本 + 游戏内）
├── checklists/requirements.md
├── tasks.md             # Phase 2
└── DELIVERY.md          # 实现后写
```

（没有 `data-model.md` 与 `contracts/`：不涉及数据实体与外部接口。）

### Source Code (repository root)

```text
design/demo/tools/netherite_back_panel.py     # 新增：重画背面独占纹素（基础色 + 法线），可重复，--check 只报告不写
src/main/resources/assets/mcfloatingbackpack/textures/item/
├── netherite_floating_backpack.png           # 修改：背面独占纹素
└── netherite_floating_backpack_n.png         # 修改：背面独占纹素写成平面法线
src/main/resources/assets/mcfloatingbackpack/models/item/netherite_backpack.obj   # 仅方案 B 触发时修改
design/demo/README.md                         # 修改：补"下界合金背面"一节（原因、脚本、覆盖风险）
specs/009-…、010-…                             # 修改：加 015 说明
```

**Structure Decision**: 素材改动，不引入运行时代码；脚本放 `design/demo/tools/`（与 `patch_iron_slit.py`、`netherite_bottom_cap.py` 同处），不随模组发布。

## Complexity Tracking

无违反项，不需要填写。
