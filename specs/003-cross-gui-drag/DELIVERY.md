# M3 Delivery Note: 003-cross-gui-drag

**Date**: 2026-10-23

## Automated

- `./gradlew test` / `compileJava` — PASS

## Implemented

- Floating slot hover highlight (`0x80FFFFFF`)
- Container open: merge leftover panel cursor → menu carried
- Panel clicks with inventory/chest open use `AbstractContainerMenu.getCarried()`
- Client cursor draw prefers menu carried while `AbstractContainerScreen` is open

## Manual check (待人工验收)

See [quickstart.md](quickstart.md): panel↔E inventory, panel↔chest, hover, Shift path without screen.

## 后续验证的间接覆盖

（2026-11-04 补充）本项当时标为**待人工验收**，任务已勾但没有专门的验收记录。之后 005–008 的人工验证（`docs/plan/manual-verification-checklist.md`，用户逐项勾选，只记录通过与否）**间接覆盖**了下面列出的部分；没列出的仍是**未验证**。

- 覆盖：面板与原版物品栏、箱子之间互拖（007 E8、009/010 回归 I2，含在 JEI 界面上互拖）。
- **未验证**：悬停高亮的即时切换（SC-003）、连续 20 次交叉点击无复制/消失（SC-004）没有专门做。
