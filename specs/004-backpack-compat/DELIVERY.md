# M4 Delivery Note: 004-backpack-compat

**Date**: 2026-10-23

## Automated

- `./gradlew test` / `compileJava` — PASS

## Implemented

- `ContainerScreenCompat` soft class-name tagging (Sophisticated / Traveler's)
- Client `screenOpen` / cursor / opening-merge routed through compat helper (still covers all `AbstractContainerScreen`)
- Optional CurseMaven `localRuntime` via `-PcompatRuntime`
- `mods.toml` optional deps for sophisticatedcore / sophisticatedbackpacks / travelersbackpack
- Known limitations documented in quickstart (fluids, ghosts, upgrade pages)

## Manual check (待人工验收)

See [quickstart.md](quickstart.md): with `-PcompatRuntime`, panel↔Sophisticated and panel↔Traveler's main storage.

## 后续验证的间接覆盖

（2026-11-04 补充）本项当时标为**待人工验收**，任务已勾但没有专门的验收记录。之后 005–008 的人工验证（`docs/plan/manual-verification-checklist.md`，用户逐项勾选，只记录通过与否）**间接覆盖**了下面列出的部分；没列出的仍是**未验证**。

- 覆盖：`runClientCompat` 下精妙背包、旅行者背包界面里，浮动面板互拖仍可用（清单 K5，用户勾选通过；**没有分别记录两个模组、也没有记录"主存储"之外的页面**）。
- **未验证**：已知限制（流体、假槽、升级页）的实际表现。
