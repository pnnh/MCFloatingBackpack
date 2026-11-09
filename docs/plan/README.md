# 规划文档目录（docs/plan）

本目录存放**评审与迭代用**的玩法 / 架构规划，与 `specs/` 中已落地的 Speckit 交付文档互补：

| 文档 | 用途 |
|------|------|
| [005-gameplay-item-tiers.md](./005-gameplay-item-tiers.md) | 远期游戏性演进参考（分级、侧栏、升级、Curios 等）；**不代表全部会实现** |
| [perf-gui-model-cost.md](./perf-gui-model-cost.md) | 待办：背包高面数模型在 GUI（快捷栏）里把帧率从约 101 压到约 22 fps；已定位未修复 |
| [backup-admin-roadmap.md](./backup-admin-roadmap.md) | 备份与管理员命令的规划结论：去掉登记表，改为世界之外的单个背包快照（030）；管理员特殊功能不做 |
| [worn-backpack-compat.md](./worn-backpack-compat.md) | 规划：背包可放进 Curios / Accessories 的后背槽；查找顺序 胸甲 → Curios → Accessories，按 B 与渲染共用；不拦截、不数数；拆成 032～034，先做验证步骤 |

## 与现有文档关系

- `docs/MCFloatingBackpack.md`：初版目标与 Overlay 技术可行性（仍有效）。
- `specs/001`～`004`：已实现的浮动面板、服务端库存、跨 GUI 拖放、第三方兼容。
- **`specs/005-item-backpack/`**：已开做的 Speckit 特征——**仅 A（道具化门禁+物品库存）/ C（胸甲背负）/ E（开发 JEI）**。
- `docs/plan/005-*`：仍作远期参考；其中分级升级（B）、OP 指令（D）、功能升级、Curios、TB 侧栏等**未**纳入 005 实现范围。

## 修订约定

- 大方向变更：改本目录文档并升版本号。
- 真正开工：以 `specs/005-*`（及后续编号）为准，不以本目录全文为强制范围。
