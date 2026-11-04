# Data Model: 010-backpack-texture-budget

本功能没有运行时数据，这里的"实体"是资源与度量记录，用来固定术语和约束。

## 实体 1：等级贴图集（TierTextureSet）

每个背包等级一套，共 5 套。

| 字段 | 说明 | 约束 |
|---|---|---|
| `tier` | `leather` / `iron` / `gold` / `diamond` / `netherite` | 与 `BackpackTier` 的 id 前缀一致 |
| 来源原图 | GLB 内嵌的 2048×2048 基础色与法线（`design/demo/<等级>背包.glb`） | 只读，不进入游戏包 |
| 基础色 `base` | `textures/item/<tier>_floating_backpack.png` | **512×512**，RGB；BOX 缩放 + 轻锐化 + 该档 gamma |
| 法线 `normal` | `textures/item/<tier>_floating_backpack_n.png` | **512×512**，RGBA（LabPBR：R=X，G=Y 已翻转，B=255，A=255） |
| gamma | 皮革 0.62、铁 0.89、下界合金 0.91、金 1.0、钻石 1.0 | 沿用 009，不随尺寸变化 |

**关系**：`base` 被模型 JSON 的 `textures.base` 引用并进入共用图集；`normal` **不**进入共用图集（被图集过滤剔除），只供光影包按"精灵名 + `_n`"读取。

**验证规则**：
- `base` 与 `normal` 同尺寸（推断：光影包期望同尺寸）
- `base` 的按网格 UV 面积加权平均亮度与当前版本偏差 ≤ ±10%（实测 ≤ ±3%）
- `normal` 文件名必须是 `<base 文件名>_n.png`，否则光影包找不到，也无法被图集过滤规则匹配

## 实体 2：贴图预算（TextureBudget）

| 字段 | 值 | 来源 |
|---|---|---|
| 图集宽上限 | 2048 | spec FR-001（用户确认） |
| 图集高上限 | 2048 | spec FR-001（用户确认） |
| 估算显存上限 | 约 22MB | 2048² × 4 字节 × 1.332（5 级 mipmap） |
| 亮度偏差上限 | ±10% | spec SC-004 |
| 目标图集尺寸 | 2048×1024 | research Decision 2（已实测） |

## 实体 3：度量记录（AtlasMeasurement）

由度量脚本产出，用于判断是否超预算。

| 字段 | 说明 |
|---|---|
| 环境 | 干净客户端（原版 + 本模组，无其它模组与资源包） |
| `blocks.png-atlas` 宽、高 | 取自游戏日志 `Created: WxHxN minecraft:textures/atlas/blocks.png-atlas` |
| mip 级别 N | 同一行末尾的数字 |
| 估算显存 | `W × H × 4 × 1.332` |
| 结论 | 宽、高都 ≤ 预算则通过；否则脚本非零退出 |

**状态转换**：无。每次测量独立，不依赖历史。

## 约束与已知取舍

- 图集尺寸由**所有**进入图集的精灵（原版 + 所有已装模组）共同决定；本功能只能控制本模组的那一份，不能保证装了大量其它模组时图集仍不超过 2048×2048，但 512 版本留有一倍面积的余量（推断）。
