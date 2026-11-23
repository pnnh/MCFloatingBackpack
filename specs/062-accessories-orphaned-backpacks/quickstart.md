# 验证指南：被 Accessories 丢掉的背包

容器 `mcfb-c1`（见 [scripts/ai-gui/README.md](../../scripts/ai-gui/README.md)）。061 的步骤已经跑通，这里复用。启动配置在 `/work/run/config/mcfloatingbackpack-startup.toml`，改前备份、验证后还原。

## 一、自动测试

- `OrphanRulesTest`：带编号在场/不在场；无编号缺几只还几只；不超过孤立数；槽位类型有效时不还；没有孤立的；同一只不重复；多种物品 id 分开计。
- 资源：两个语言键中英文齐。
- 接线：`LoadFromFile` 里只在 Accessories 加载时读；核对在登录后 20 刻与下线事件里；档位门；`OrphanedBackpackScan`、`OrphanRules` 没有 `io.wispforest` 引用；归还用 `ContainerMenuAccess(...).fallback()`；jar 里没有 `io/wispforest` 的类。

## 二、游戏内

1. **复现丢失**（确认前提）：把一只**装了东西**的背包放进 Accessories 的专用槽 → Save & Quit → `accessories=false` → 启动进世界 → 背包在物品栏里（修复后），内容完整，聊天一行；日志一行。
2. **不复制**：再 Save & Quit → 改回 `true` → 启动：物品总数不变（专用槽是空的，背包仍只有一只）；退出再进一次，不再有提示。
3. **无编号的背包**：放一只没打开过的背包进专用槽，重复 1：归还一只。
4. **装饰槽**：背包放在专用槽的装饰（cosmetic）格里（Accessories 界面里切到装饰视图），重复 1：归还。
5. **下线竞态**：在放有背包的情形下关开关、重启、进世界后**立刻**（1 秒内）Save & Quit，再进一次：背包在物品栏里（说明下线事件里已归还且被保存）；若不在，这是已知局限，记入交付并调整。
6. **物品栏满**：把物品栏塞满再做 1：背包掉在脚边，聊天提示说明。
7. **档位门**：`accessories=GENERIC`（客户端或服务端配置）再做 1：不归还、不提示（背包仍然丢失，符合预期）。
8. **没有槽位消失的玩家**：正常登录，无提示、无多余日志。
9. **损坏的数据**：（只做单元/接线层面）读取遇到缺层不抛异常。

## 三、不做的验证

- 专用服务器（读文件的路径相同）；Accessories 其它版本的数据格式。
