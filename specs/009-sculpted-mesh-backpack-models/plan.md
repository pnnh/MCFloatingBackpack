# Implementation Plan: 雕刻网格背包外观

**Branch**: `009-sculpted-mesh-backpack-models` | **Date**: 2026-11-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-sculpted-mesh-backpack-models/spec.md`

**Status**: 补录。本 plan 描述的是**已经落地**的方案（见 `git log` 的 `504a56b` 至 `66140a8`），不是待实现的计划；与"实现先于规格"相关的违规记录在 Complexity Tracking。

## Summary

006 的五档背包外观是立方体元素模型，不够精致。006 之后，五个等级都换成了由 AI 生成工具产出的雕刻网格模型，通过 NeoForge 自带的 OBJ 模型加载器接入：每个等级一个独立的物品模型 JSON、一份 OBJ/MTL、一张 1024×1024 基础色贴图和一张 LabPBR 法线贴图；背部渲染不再手搭立方体，直接渲染物品自己的烘焙模型，因此图标、手持、背上是同一套外形。

没有改变任何玩法代码（容量、升级、开关、面板、库存）。Java 代码的改动只有 `BackpackBackLayer`（背部渲染）；其余全部是资源文件和设计素材管线。

已知缺口（共用方块图集膨胀、网格开放边、无性能预算、若干未在游戏内验证的参数）都记录在 spec 的"已知限制"一节，本 plan 不重复，也不在本 feature 内解决。

## Technical Context

**Language/Version**: Java 21（Java 部分只有一个渲染图层）；资源与素材管线用 Blender 5.1 无头脚本（Python）和 Pillow

**Primary Dependencies**: NeoForge 21.1.251（OBJ 加载器 `neoforge:obj` 是本体自带，非第三方）；Minecraft 1.21.1 渲染 API（`ItemRenderer`、`RenderLayer`）。无新增 Gradle 依赖

**Storage**: 无运行时存储；资源文件随模组打包，设计素材放在 `design/` 并纳入 Git

**Testing**: 没有新增自动化测试：改动是渲染外观与资源，纯逻辑部分几乎没有。`./gradlew compileJava test` 保证没有破坏既有逻辑；日志扫描（`./gradlew runClient` 起到主菜单后检查是否有模型/贴图加载异常）保证资源能被加载；**外观是否正确必须在客户端会话里目视验证**（宪法 V 条，见 quickstart.md）

**Target Platform**: Minecraft 1.21.1 桌面客户端；光影包支持可选，Iris/Oculus/NeOculus 下才会用到 `_n` 贴图

**Project Type**: 单一 NeoForge 模组；本功能几乎全是资源与素材，不引入新模块

**Performance Goals**: **未定义**（已知限制 3）。当前每档约 1.6 万~2.0 万三角面，图标、手持、背上（含他人）同时渲染

**Constraints**:
- 外观资源必须是完整文件，随模组打包的 `.obj` 不能走 Git LFS，否则没装 git-lfs 的构建环境会把指针文本打进 jar
- 不得改变既有玩法
- 仅 JEI 等可选兼容不得成为硬依赖（宪法 IV）；本功能不引入任何第三方依赖

**Scale/Scope**: 5 个等级 × （1 个模型 JSON + 1 个 OBJ + 1 个 MTL + 1 张基础色 + 1 张法线）= 25 个资源文件；1 个 Java 文件改动；设计目录下的素材、脚本与说明文档

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及面板；只改物品外观与背部渲染 | PASS（不适用） |
| II. Server Owns Items | 不涉及库存、网络与物品数据 | PASS（不适用） |
| III. One Feature At A Time | **违反**：外观工作在没有 spec 的情况下做了多轮，并且把多个不同性质的改动（模型重做、加载器引入、光照、法线贴图、LFS、背部渲染重写）分散在 `504a56b`~`66140a8` 十余个提交里；本 spec 是事后补录 | **VIOLATION**，见 Complexity Tracking |
| IV. Vanilla Path Before Compat | OBJ 加载器是 NeoForge 本体；法线贴图只在装了光影包时被用到，不是硬依赖；没有引入任何第三方模组依赖 | PASS |
| V. Verify In A Client Session | 外观改动必须在客户端会话里确认。目前只有部分被验证：皮革的图标/手持/背上、下界合金的手持与背上被截图确认过；铁/金/钻石的图标与手持、五档的 SC-001/003/004/006/007 都没有验证 | **PARTIAL**，验证任务写在 tasks.md，在做完之前本 feature 不得标记为完成 |

Re-check after Phase 1 design：设计在补录时已定型，没有新增的违反项。

## Project Structure

### Documentation (this feature)

```text
specs/009-sculpted-mesh-backpack-models/
├── plan.md              # This file
├── research.md          # 14 条决定及其证据（补录）
├── quickstart.md        # 客户端会话验证清单
├── tasks.md             # 补录任务：已完成项、待验证项、后续候选
└── checklists/
    └── requirements.md  # Spec 质量清单
```

本功能不新增、不修改任何网络协议或对外接口，不生成 `contracts/`；资源文件的约定写在下面的"资源契约"里。`data-model.md` 也不需要：没有新增数据实体。

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/client/render/
└── BackpackBackLayer.java      # 改动：不再手搭立方体，改为 ItemRenderer.renderStatic(FIXED) 渲染物品烘焙模型

src/main/resources/assets/mcfloatingbackpack/
├── models/item/
│   ├── <tier>_floating_backpack.json   # 5 个：loader=neoforge:obj，flip_v，emissive_ambient=false，textures.base，display
│   ├── <tier>_backpack.obj             # 5 个：OBJ 几何（0..1 范围，背面统一落在 z=0.238）
│   ├── <tier>_backpack.mtl             # 5 个：map_Kd #base
│   └── floating_backpack.json          # 遗留：006 的共用父模型，已无任何引用
└── textures/item/
    ├── <tier>_floating_backpack.png    # 5 个：1024×1024 基础色（已按 gamma 调亮）
    └── <tier>_floating_backpack_n.png  # 5 个：LabPBR 法线贴图

design/
├── demo/                       # 原始 GLB、效果图 JPG、处理产物（obj_export/）、README、tools/
│   └── tools/netherite_bottom_cap.py   # 下界合金补底板的 Blender 脚本
└── backpack-redesign/          # 006 之后立方体程序模型的设计历史（已被本 feature 取代，保留作记录）
```

`<tier>` ∈ `leather` / `iron` / `gold` / `diamond` / `netherite`。

### 资源契约（当前实现）

| 文件 | 约定 |
|---|---|
| `models/item/<tier>_floating_backpack.json` | 独立模型，不再继承共用父模型；必须带 `"flip_v": true`（缺了贴图错位）与 `"emissive_ambient": false`（缺了模型始终全亮）；`display` 使用 006 之前最早那套数值 |
| `models/item/<tier>_backpack.obj` | 三角面；顶点整体落在 0..1 范围；各档背面（贴着人的一面）统一在 z=0.238；正面（扣具/口袋）朝 +Z |
| `models/item/<tier>_backpack.mtl` | `map_Kd #base`，材质名 `Material_0` |
| `textures/item/<tier>_floating_backpack.png` | 1024×1024，RGB；方向与 OBJ 的 V 坐标对应（需要 `flip_v`） |
| `textures/item/<tier>_floating_backpack_n.png` | 同名加 `_n`；R=X，G=Y（DirectX，已翻转），B=255，A=255 |

**Structure Decision**: 单项目结构，不引入新模块。外观资源和设计素材分开存放：`src/main/resources` 只放随模组打包的最终产物，`design/` 放原始素材、脚本和过程记录，让以后重新生成时有据可查。

## Complexity Tracking

| 违反项 | 为什么发生 | 被否决的更简单方案 |
|---|---|---|
| 宪法 III：实现先于规格，且多个改动混在一起 | 外观工作是按对话反馈逐轮迭代的（"糊了"、"透光"、"偏暗"、"底部是空的"……），每一轮都是针对截图的小修，当时没有停下来补规格 | 严格按 speckit 先 specify 再实现：对一个靠截图反馈推进、方案会被推翻（例如 `flip_v` 的发现推翻了"分辨率问题"的判断，减面方案被整个撤销）的探索性工作，会让规格频繁作废。折中的做法是现在补录，并把被推翻的方案和证据写进 research.md，避免丢失 |

补救措施：本 spec/plan/research 的补录；006 里与现状冲突的条款已同步修订；今后外观类改动如果超出"微调数值"的范围，先走 `/speckit-specify`。
