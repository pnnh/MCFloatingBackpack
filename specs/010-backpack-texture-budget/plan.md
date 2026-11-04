# Implementation Plan: 背包贴图预算与共用图集占用

**Branch**: `010-backpack-texture-budget` | **Date**: 2026-11-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-backpack-texture-budget/spec.md`

## Summary

009 的五档雕刻网格背包把共用方块图集从 1024×1024 撑到了 4096×4096（估算显存约 5.6MB → 89MB，影响所有玩家）。研究阶段（见 [research.md](./research.md)）用实测查清了真正的原因和可行方案：

1. **原因不止基础色贴图**：图集会扫描 `textures/item/` 下的所有 PNG，五张法线贴图 `_n.png` 也被拼进了图集；一共 **10 张** 1024² 贴图撑出 4096×4096。（推翻了 spec 里"法线贴图不进图集"的假设。）
2. **方案**：(a) 加一个图集 `filter` 源，把本模组的 `_n` 从共用图集里剔除（光影包按"精灵名 + `_n`"自己读取，预期不受影响，需实测确认）；(b) 基础色贴图改为 **512×512**，从 GLB 的 2048 原图用"盒式缩放 + 轻锐化"出图，沿用各档原有的 gamma；(c) 法线贴图同样降到 512 并重新按 LabPBR 转换。
3. **预期结果（尺寸已实测）**：共用图集 **2048×1024**，满足 2048×2048 的上限且有一倍余量；估算显存约 11MB（现状约 89MB，纯原版约 5.6MB）；亮度偏差 ≤ ±3%（实测，要求 ±10%）。
4. **没有 Java 代码改动**：只改资源文件（贴图、一个图集定义）和素材处理脚本。
5. **被否决的路线**：让背包贴图不进图集（要重写物品渲染与背部渲染，而且独立贴图没有 mipmap）；按硬件自动选质量（单一 512 档已满足上限）。

仍然**未验证**、必须在游戏里确认的：512 在 UV 正确的情况下是否达到"近看略软但不糊"；光影包下法线是否仍然可见。

## Technical Context

**Language/Version**: 资源与素材管线：Python 3（Pillow）+ Blender 5.1 无头脚本；本功能没有 Java 改动

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1 的原版图集定义机制（`atlases/blocks.json` 的 `filter` 源）；无新增依赖

**Storage**: 无运行时存储；随模组打包的贴图与一个图集 JSON；素材处理脚本放 `design/demo/tools/`

**Testing**: 没有适合写成 JUnit 的纯逻辑。验证靠：(1) `./gradlew compileJava test`；(2) 度量脚本读 `runClient` 日志里的图集尺寸并与预算比较（FR-008 / SC-007）；(3) 客户端会话里目视对比清晰度和亮度（宪法 V 条）；(4) 装了光影包的客户端确认法线

**Target Platform**: Minecraft 1.21.1 桌面客户端；光影包支持可选（Iris / Oculus / NeOculus）

**Project Type**: 单一 NeoForge 模组；资源/素材改动

**Performance Goals**: 共用图集宽高都 ≤ 2048（实测目标 2048×1024）；估算显存增量 ≤ 约 22MB（实测尺寸推算 11MB）；亮度偏差 ≤ ±10%（实测 ≤ ±3%）。**不含**帧率/面数预算（009 F002，另行立项）

**Constraints**:
- 只改贴图与图集定义，不改模型几何、不改任何玩法
- 不得引入对光影包或其它模组的硬依赖
- 图集过滤只匹配 `mcfloatingbackpack` 命名空间，不影响其它模组的贴图
- 五档可区分、正面朝向、亮度、法线贴图继续可用（009 已确认的外观要求）

**Scale/Scope**: 10 张贴图替换（5 基础色 + 5 法线）、1 个新的图集 JSON、2~3 个素材脚本、文档更新

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及面板与界面 | PASS（不适用） |
| II. Server Owns Items | 不涉及库存、网络与物品数据 | PASS（不适用） |
| III. One Feature At A Time | 先写 spec、再做 plan，范围限定在贴图预算与图集占用；明确不含模型几何、开放边、帧率/面数预算、高光贴图 | PASS |
| IV. Vanilla Path Before Compat | 用的是原版图集 `filter` 源，不是第三方依赖；对光影包是可选增强，没有光影包时本功能没有任何额外行为 | PASS |
| V. Verify In A Client Session | quickstart.md 要求：读游戏日志里的图集尺寸、游戏内对比清晰度与亮度、装光影包确认法线；其中"512 是否够清晰"和"光影包法线是否仍可见"目前**没有**在游戏里验证过，实现完成后必须做 | PASS（已规划，实现阶段必须执行） |

Re-check after Phase 1 design：设计阶段没有新增违反项。

## Project Structure

### Documentation (this feature)

```text
specs/010-backpack-texture-budget/
├── plan.md              # This file
├── research.md          # 实测矩阵与 8 条决定（含推翻的假设）
├── data-model.md        # 贴图集、预算、度量记录三个实体
├── quickstart.md        # 度量与验证清单
├── contracts/
│   └── texture-budget.md  # 资源契约：文件名、尺寸、图集过滤、预算数值
├── checklists/
│   └── requirements.md
├── tasks.md             # /speckit-tasks 生成
└── DELIVERY.md          # 实现与验证阶段记录结果（实现时新建）
```

### Source Code (repository root)

```text
src/main/resources/assets/
├── minecraft/atlases/
│   └── blocks.json                                  # 新增：filter 源，把 mcfloatingbackpack:item/*_n 剔出共用图集
└── mcfloatingbackpack/textures/item/
    ├── <tier>_floating_backpack.png                 # 替换：1024² → 512²（BOX 缩放 + 轻锐化 + 原 gamma）
    └── <tier>_floating_backpack_n.png               # 替换：1024² → 512²（从 2048 原图重做 LabPBR 转换）

design/demo/tools/
├── extract_textures.py        # 新增：Blender 无头脚本，从 GLB 取出 2048 基础色与法线原图
├── build_textures.py          # 新增：Pillow 脚本，生成最终 512 基础色与 _n
├── check_atlas_budget.sh      # 新增：起客户端读 blocks 图集尺寸，与预算比较，超标则非零退出
└── check_brightness.py        # 新增：按网格 UV 面积加权比较新旧贴图亮度，偏差超过 ±10% 则非零退出

design/demo/README.md          # 更新：纠正"法线不进图集"和"512 糊"的结论，记录新处理流程
```

`<tier>` ∈ `leather` / `iron` / `gold` / `diamond` / `netherite`。

**Structure Decision**: 没有 Java 改动。资源契约见 `contracts/texture-budget.md`；素材处理流程与 009 的 `design/demo/` 一脉相承（原始素材在 GLB 里，脚本重新生成）。图集过滤文件放在 `assets/minecraft/atlases/`（原版图集定义的约定位置，各资源包的同名文件会叠加，而不是互相覆盖）。

## Complexity Tracking

*无违反项，本节留空。*
