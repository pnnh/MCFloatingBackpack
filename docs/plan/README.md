# 规划文档目录（docs/plan）

本目录存放**评审与迭代用**的玩法 / 架构规划，与 `specs/` 中已落地的 Speckit 交付文档互补：

| 文档 | 用途 |
|------|------|
| [005-gameplay-item-tiers.md](./005-gameplay-item-tiers.md) | 远期游戏性演进参考（分级、侧栏、升级、Curios 等）；**不代表全部会实现** |
| [perf-gui-model-cost.md](./perf-gui-model-cost.md) | 待办：背包高面数模型在 GUI（快捷栏）里把帧率从约 101 压到约 22 fps；已定位未修复 |
| [backup-admin-roadmap.md](./backup-admin-roadmap.md) | 备份与管理员命令的规划结论：去掉登记表，改为世界之外的单个背包快照（030）；管理员特殊功能不做 |
| [worn-backpack-compat.md](./worn-backpack-compat.md) | 规划：背包可放进 Curios / Accessories 的后背槽；查找顺序 胸甲 → Curios → Accessories，按 B 与渲染共用；不拦截、不数数；拆成 032～034，先做验证步骤 |
| [category-extensions.md](./category-extensions.md) | 规划（讨论稿）："分类扩展"功能件——放进功能区给背包加一行只能存某一类物品的存储；逻辑行列表、拿走时隐藏行、黄点提示、行总数上限、10 个类别与配方草案；拆成 035～037，搜索与整理单独做 |
| [ai-gui-verification.md](./ai-gui-verification.md) | 规划：让 Claude 在隔离环境里自动验证游戏界面（Linux 容器先试，macOS 虚拟机后议）；虚拟机方案对比、已查证与未验证、自动验证结果的记录规矩 |
| [backpack-search.md](./backpack-search.md) | 规划（已与用户确认主要决策）：背包搜索——标题栏放大镜与搜索框、实时过滤主区域并高亮命中、`@模组` `#标签` `/hidden` 语法、中英文与拼音改由别的模组经本模组公开的搜索词提供者接口提供；拆成 042～044 |
| [mobile-tools.md](./mobile-tools.md) | 规划（已与用户确认主要决策）：移动工作台、移动熔炉、移动唱片机——右区新物品、各至多一个；工作台与熔炉打开原版界面（熔炉状态记在工具物品上、打开时补算），唱片机是客户端播放器（播放列表读可见的唱片扩展行、倒带、单曲循环、列表随机、不做暂停）；配方草案；旅行者背包的做法对照；拆成 053（唱片机）～055 |

## 与现有文档关系

- `docs/MCFloatingBackpack.md`：初版目标与 Overlay 技术可行性（仍有效）。
- `specs/001`～`004`：已实现的浮动面板、服务端库存、跨 GUI 拖放、第三方兼容。
- **`specs/005-item-backpack/`**：已开做的 Speckit 特征——**仅 A（道具化门禁+物品库存）/ C（胸甲背负）/ E（开发 JEI）**。
- `docs/plan/005-*`：仍作远期参考；其中分级升级（B）、OP 指令（D）、功能升级、Curios、TB 侧栏等**未**纳入 005 实现范围。

## 修订约定

- 大方向变更：改本目录文档并升版本号。
- 真正开工：以 `specs/005-*`（及后续编号）为准，不以本目录全文为强制范围。
