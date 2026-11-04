# M2 Delivery Note: 002-server-inventory

**Date**: 2026-10-23

## Automated

- `./gradlew test` — PASS (`PanelClampTest`, `ModIdTest`, `BackpackClickLogicTest` fake-stack rules)
- `./gradlew compileJava` — PASS

## Client / server playthrough (quickstart)

Not fully exercised interactively in this implement session. **待人工验收**:

1. `./gradlew runClient` → open panel (`B`)
2. Without E: hold item, **Shift+left-click** empty slot to deposit; Shift again to withdraw
3. Left-click pick/place/swap with panel cursor
4. Relog; contents persist
5. Optional: `runServer` with mod on both sides

## Notes

- Cross-GUI drag with open inventory/chest is **out of scope** (M3).
- NeoForge `unitTest { enable() }` was not left enabled (module-path conflict in this environment); click rules covered by pure fake-stack tests mirroring production logic.

## 后续验证的间接覆盖

（2026-11-04 补充）本项当时标为**待人工验收**，任务已勾但没有专门的验收记录。之后 005–008 的人工验证（`docs/plan/manual-verification-checklist.md`，用户逐项勾选，只记录通过与否）**间接覆盖**了下面列出的部分；没列出的仍是**未验证**。

- 覆盖：点击取放（005 内容随物品、007 滚动条测试里放满物品）、丢出再捡与放进箱子再取后内容一致（005 B1、B2）、客户端完全重启后面板状态保持（007 E4）。
- **未验证**：本 quickstart 的"不开物品栏、Shift+左键存入/取出"这一路径（005 的 C3 只验证了无背包时 Shift 无效）、`runServer` 专用服务端联机。
- 注意：002 的库存存储方式已被 005 取代（见 `spec.md` 顶部说明），上面的覆盖针对的是现在的物品绑定库存。
