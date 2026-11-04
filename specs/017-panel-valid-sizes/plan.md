# Implementation Plan: 面板只允许"没有实心空格"的尺寸，并缩小"更多"箭头

**Branch**: `017-panel-valid-sizes` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-panel-valid-sizes/spec.md`

## Summary

016 允许缩放到任意行列，会出现实心的不可点击格子（主区域最后一行不满、左右区行数超过它们的格子数）。用户要求缩放时不允许这种尺寸，目标尺寸不合法就忽略；并把"更多"箭头缩小、贴边。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **纯函数 `PanelShape` 判定合法尺寸**：`cols` 在 3..9、整除主格子数、`cols + 2 ≤ 下区格子数`；`rows` 在 3 与 `min(左, 右, 主格子数 / cols)` 之间。判定只依赖四个区域的格子数和尺寸，与滚动位置无关；用"对每个等级、每个合法尺寸、每个滚动位置检查 `PanelLayout` 没有填充格"的穷举测试证明没有实心格子。
2. **拖动缩放**：`updateResize` 对目标列数、目标行数各自判定，**合法才写入，不合法保持当前值**（不吸附）。
3. **其他入口汇合在 `layout()`**：保存的偏好、自动适应、换背包/升级、屏幕变小，都在每 tick 的 `layout()` 里落到合法尺寸（列取不超过目标的最大合法值，行夹到 `[3, maxRows]`）；`MIN_VISIBLE_CELLS = 3` 仍压过屏幕过小的情况。
4. **格子数由视图上报**：`FloatingPanelState` 新增 `setMainCount`（和已有的 `setToolCount` 同一套），保持状态类不依赖含 `ItemStack` 的视图类。
5. **没有合法形状（含计数未上报）时退回 016 的行为**，填充格不绘制的兜底保留，不会让缩放失效。
6. **箭头**：`ARROW_SIZE` 4 → 3（外框最大 7×4），外框尖端贴区域最外一个像素；出现条件、不参与命中、颜色不变。
7. **配置文件格式、网络协议、存储都不变**。

范围：只改客户端的缩放判定、布局落点和箭头常量；不改容量规则、不改最小尺寸、不改 016 的其他行为。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（仅客户端渲染与输入）；无新增依赖，不涉及第三方模组

**Storage**: 无变化（`config/mcfloatingbackpack-panel.properties` 格式不变，读入的非法值由 `layout()` 修正）

**Testing**: JUnit 5 纯逻辑测试：`PanelShape`（五个等级的合法集合、吸附、幂等、退回）、**穷举无填充格测试**（每个等级 × 每个合法尺寸 × 每个滚动位置，经 `PanelLayout` 检查）、`FloatingPanelState` 的缩放与 `layout()` 用例；加客户端会话手测（quickstart，宪法 V）

**Target Platform**: Minecraft 1.21.1 桌面客户端；开发机 macOS

**Project Type**: 单一 NeoForge 模组（客户端布局与渲染）

**Performance Goals**: 合法性判定是对至多 7 个列数的常数时间计算，每 tick 一次，可忽略

**Constraints**:
- 016 的布局、滚动、容量、最小 5×4 不变；011～014 只作用于主区域
- 不合法的拖动目标不产生任何变化（不吸附、不提示）
- 没有合法形状时不得让缩放失效
- 不新增资源，不改协议或存储

**Scale/Scope**: 1 个新纯类（`PanelShape`）、改动 `FloatingPanelState`、`ClientBackpackView`、`FloatingPanelRenderer`；2 个测试类（新增 1、扩充 1）；007/016 文档加说明

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 仍是叠层，不触碰 `Screen` | PASS |
| II. Server Owns Items | 不涉及物品逻辑；只限制客户端面板的可选尺寸 | PASS（不适用） |
| III. One Feature At A Time | 范围限定在缩放的合法尺寸与箭头大小；容量规则、最小尺寸、滚动方式明确不动；修订 007/016 的相关条款已写进 spec | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方 | PASS（不适用） |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证（拖动、偏好落点、换背包/升级/缩窗、箭头）；Windows/Linux 无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增贴图或模型；跑一次图集度量作为回归 | PASS（不适用） |

**Re-check after Phase 1 design**：设计阶段没有新增违反项。实现阶段要盯住的风险：
1. **汇合点漏掉入口**：偏好、自动适应、升级、屏幕变化都要在 `layout()` 里落到合法尺寸，穷举测试 + quickstart 二逐项覆盖；
2. **行数上限受列数影响**：先定列数再判行数，`layout()` 的顺序不能反（research Decision 3/4）；
3. **计数未上报时的退回**：现有 `PanelLayoutTest` 等依赖"任意尺寸"，必须靠"计数为 0 即不受限"保持通过。

## Project Structure

### Documentation (this feature)

```text
specs/017-panel-valid-sizes/
├── plan.md              # 本文件
├── research.md          # Phase 0：9 条有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：Counts、PanelShape 规则、合法形状表、状态转移
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   └── panel-shape.md   # Phase 1：合法尺寸、拖动与其他入口的处理、箭头尺寸
├── checklists/requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/client/panel/
├── PanelShape.java              # 新增：纯逻辑，合法列数/行数、吸附、受限判定
├── FloatingPanelState.java      # 修改：setMainCount、updateResize 按维度判定、layout() 落到合法尺寸
├── ClientBackpackView.java      # 修改：applyFull/clear 上报主格子数
└── FloatingPanelRenderer.java   # 修改：ARROW_SIZE 4→3，箭头尖端贴边

src/test/java/pnnh/mcfloatingbackpack/client/panel/
├── PanelShapeTest.java          # 新增：合法集合、吸附、幂等、退回、穷举无填充格
└── PanelLayoutTest.java         # 扩充：受限时 updateResize 与 layout() 的用例

specs/007-floating-window-chrome/、specs/016-panel-tool-areas/ 与 docs/MCFloatingBackpack.md   # 修改：加 017 说明
```

**Structure Decision**: 沿用单模块。合法性判定是不依赖 Minecraft 的纯类，状态类只多保存一个计数并调用它，渲染类只改常量；不引入新的存储或网络。

## Complexity Tracking

无违反项，不需要填写。
