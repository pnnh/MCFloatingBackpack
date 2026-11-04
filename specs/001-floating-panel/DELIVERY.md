# M1 Delivery Note: 001-floating-panel

**Date**: 2026-10-23

## Automated

- `./gradlew test` — PASS (`PanelClampTest`)
- `./gradlew compileJava` — PASS

## Client GUI (quickstart 1–8)

Not executed end-to-end in this implement session (no interactive `runClient` playthrough). Marked **待人工验收** in [quickstart.md](quickstart.md).

Suggested first manual pass: toggle `B`, drag title bar with inventory open (E), confirm `run/config/mcfloatingbackpack-panel.properties` updates.

## 后续验证的间接覆盖

（2026-11-04 补充）本项当时标为**待人工验收**，任务已勾但没有专门的验收记录。之后 005–008 的人工验证（`docs/plan/manual-verification-checklist.md`，用户逐项勾选，只记录通过与否）**间接覆盖**了下面列出的部分；没列出的仍是**未验证**。

- 覆盖：B 开关、ESC 关闭且不进暂停菜单（005 门禁）；按 E 打开原版物品栏时面板共存（005 门禁 4）；面板尺寸与位置在重启后保持（007 E4）；暂停/设置菜单上不显示面板。
- **未验证**：按本 quickstart 的"拖动标题栏并确认 `mcfloatingbackpack-panel.properties` 更新"、SC-004（拖出屏幕后仍留有可点击区域）没有专门做。
