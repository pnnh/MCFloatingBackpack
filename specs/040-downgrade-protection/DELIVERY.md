# Delivery: 降级保护

**状态**：代码与自动化完成（2026-11-10）；**游戏里的验证（quickstart 第一、二部分 / 任务 T024、T025）与降级实验（第三部分 / T026）等用户确认，尚未做**。保护的行为与真实降级的后果没有任何自动化测试能代替游戏里看一眼（宪法 V）。没有提交 Git，没有打 tag。

## 基线与结果

| | 类数 | 用例数 | 失败 |
|---|---|---|---|
| 开始前（039 完成后） | 128 | 917 | 0 |
| 完成后 | 133 | 944 | 0 |

新增测试类：`DataRevisionTest`、`RevisionGuardTest`（决策表四种结果，自动拾取等路径在窗口开着时也不能确认）、`ProtectionWindowsTest`（60 秒内有效、第 60 秒起无效、用掉后无效、别的玩家与别的背包借不到、同一个玩家两只背包各自独立、玩家退出清除、限频）、`BackpackContentsRevisionTest`（`data_revision` 是磁盘编码的可选字段、**`STREAM_CODEC` 没有带它**、保存盖章、复制/适配/升级保留修订）、`DowngradeProtectionResourcesTest`（语言键、保护先于身份与快照、只有 `acquireForOpen` 能确认且只被打开请求调用、自动拾取不发聊天、锻造台不匹配、确认立即盖章、协议与其它网络包不变、没有新增配置与命令）。

## 数据修订：本 feature 递增吗？

**这是第一个盖章的版本：`DataRevision.CURRENT = 1`**（没有标记的旧背包读作 0）。本 feature 自己没有改变背包的存储数据语义（只是新增了这个标记字段），所以不存在"更早版本的修订"。以后每个改变背包存储数据的 feature 要在交付说明里写明是否递增（见 `docs/plan/release-process.md`）。

## 做了什么

- **存储**：`BackpackContents` 第 8 个记录组件 `dataRevision`，磁盘编码 `optionalFieldOf("data_revision", 0)`；**`STREAM_CODEC` 一行没动**（协议号 17 不变）；复制、适配容量、锻造台升级都保留修订。**没有用单独的数据组件**：对更早的版本它是"不认识的组件类型"，物品栏里的背包会整只读成空（见 research D3）。
- **保存**：`BackpackInventory.toContents` 盖上 `DataRevision.CURRENT`；调试开关 `-Pmcfb.stampRevision=N`（系统属性 `mcfloatingbackpack.debug.stampRevision`）改盖 N，用来造出"更新版本保存的背包"。
- **统一入口 `BackpackGate`**：`acquire` 在**分配身份与通知快照之前**读栈上的修订，被保护就静默返回空；新增 `acquireForOpen`，**只有 `BackpackOpenRequestPayload` 用它**：第一次拒绝并给本人聊天提示、开 60 秒窗口、写 WARN；窗口内再次打开 → 放行并**立即把栈上的修订改成当前**（所以之后锻造台与自动拾取也恢复正常）、聊天"已接受风险"、写 WARN。窗口按"玩家 + 背包"各自独立（有身份时用身份，没有身份时用位置加修订），玩家退出清除。
- **自动拾取**：被保护就跳过（原版流程），只写限频 WARN（每位玩家每分钟至多一条），不发聊天。**锻造台**：`BackpackUpgradeRecipe.matches` 对被保护的基础背包为假。其余面板请求经 `acquireForPanel`/`acquire` 静默拒绝。
- 文档：`docs/plan/release-process.md` 的"背包数据修订"一节（何时递增、每个 feature 要写明、保护从第一个带标记的版本开始）、README 与 README_CN 的"升级与降级"、`docs/MCFloatingBackpack.md`、验证清单 040-A～J。
- 实验辅助：`design/demo/tools/downgrade_experiment.sh`（只准备：检查没有客户端在跑、复制备份世界、建旧版本的 worktree、把世界副本放进去、打印步骤；`--dry-run` 只打印；**不启动游戏、不改原世界**；本机 dry-run 通过）。

## 对规格的说明

1. **修订放在记录里的可选字段，不是单独的数据组件**（规格没有指定，plan 里说明了原因）。
2. **确认时立即盖章**，而不是"等下一次保存"：否则背包仍是高修订，锻造台与自动拾取继续被挡。效果与规格"放行后按当前修订保存"相同，只是更早。
3. **自动拾取只写限频日志，不发聊天**（规格只要求"不刷屏"）。
4. **降级实验的默认旧版本是 030（`e1207ea`），不是 034**：031～034 的提交引用的 `snapshot/net` 包被 `.gitignore` 的 `net/` 误忽略、从没进过 git，检出后编译不过；030 是 035 之前最后一个能编译的提交。（`.gitignore` 的修复已在工作区，等你提交。）

## 未验证（等用户）

- **保护在游戏里的全部行为**（quickstart A～I）：被拒并提示、背包零变化、60 秒内再次打开放行、过期重新拒绝、别的背包借不到、自动拾取跳过且不刷屏、锻造台不匹配、不读写内容的操作不受限、正常情形回归。
- **降级实验**（J）：你已做并把记录写进 research.md 末尾；Claude 对比 NBT 后的结论：**H1 成立、H2 成立、H3 未出现**（详见 research"实验分析"）。**待你确认后勾选 T026/040-J**。还缺一个对照：旧版本里**不点任何格子**直接退出，看是否仍丢——决定"读入即丢"的推断（拦不住的丢失）是否成立。
- 两个玩家同时在线时窗口的隔离（别的玩家借不到）：单元测试覆盖，游戏里需要两个客户端，可记"未验证"。

## 已知限制

- 保护从第一个带标记的版本开始：早于它的版本不认识 `data_revision`，也不会保留它。
- **创造模式下客户端把背包栈写回服务器时修订会丢**（读作 0，因为网络编码不带修订），等于洗掉保护；客户端界面上看不出背包是被保护的（只有被拒绝时的聊天提示）。要彻底解决得升协议号，另议。
- **保护拦不住读入时的丢失**：实验证实旧版本解码时会丢掉不认识的字段（`extension_rows`）与不认识的嵌套物品（工具区里的扩展件）；这份变短的内容随玩家数据写回（推断，待对照实验），所以 040 的作用是"警告 + 阻止面板在确认前继续改写"，**不是"防止丢数据"**。解码层面的保留**已放弃**；改由 **041（存档级降级拦截，`specs/041-world-downgrade-guard/`）** 在读取玩家数据之前拒绝启动更高修订的存档来补上。总口径：本模组不支持降级，降级前请备份存档。
- 整只背包读成空（H3）：实验未出现。
- 没有身份的被保护背包用"位置加修订"认同一只背包；手工编辑存档把修订写大也会触发保护，这是预期的。

## AI 自动验证（2026-11-11，Linux 容器内 `runClient`）

环境与方法见 `docs/plan/ai-gui-verification.md`（容器、Xvfb、llvmpipe；xdotool 点击与按键、截图、日志、存档 NBT 对比）。**造数据**：`-Pmcfb.stampRevision=99`，在**生存模式**下通过物品栏界面把物品放进背包面板、正常退出（Save and Quit）；然后不带开关重启。判定证据主要是日志（`[CHAT]` 与 `[diag server]` WARN）和 `playerdata` 的 NBT。

| quickstart | 结果 | 证据 |
|---|---|---|
| A 被拒并提示 | 通过 | 面板没有打开；CHAT：`This backpack was saved by a newer version of MCFloatingBackpack (data revision 99; this version is 1). Using it may lose data: … open it again within 60 seconds, which means you accept the risk.`；WARN `Backpack ECSU of Dev was saved by a newer version (revision 99, this version 1): refused to open it; asked the player to confirm` |
| B 背包零变化 | 通过 | 背包 UU94（修订 99、64 个圆石）被拒、丢出、捡回、正常退出后，与退出前的 `playerdata` **逐字段一致**（`data_revision=99`、`uuid`、内容；没有分配新身份） |
| C 60 秒内再次打开 | 通过 | CHAT：`Risk accepted: this backpack is now used as this version (data revision 1) sees it.`；WARN `Dev accepted the risk for backpack ECSU (revision 99 -> 1): the data this version does not know is dropped`；面板正常打开（截图） |
| D 过期 | 通过 | 09:45:00 被拒，62 秒后（09:46:23）再次被拒并重新提示 |
| E 别的背包借不到 | 通过 | 放行第一只（ECSU）之后，第二只（EJL8）第一次打开仍被拒（WARN 09:46:49） |
| F 自动拾取 | 通过 | 带着被保护且未确认的背包（左区有自动拾取），脚下生成 5 + 7 个泥土：全部进物品栏（共 12 个）；聊天只有命令回显、没有提示；日志只有一条 WARN `Auto pickup skipped for Dev: the backpack EJL8 was saved by a newer version (revision 99, this version 1)`（两次拾取只记一条） |
| G 锻造台 | 通过 | 下界合金升级模板 + 受保护背包 + 下界合金块：箭头红叉、结果格为空；换成已放行的背包：出现下界合金背包结果 |
| H 面板请求 | 以 B 为准 | 被拒后面板没有打开，B 已证明零变化 |
| I 不读写内容的操作 | **部分** | 丢弃通过（掉落物存在，`data_revision` 仍是 99，可捡回）；放进箱子再取出、创造复制**未做** |

**M（创造模式写回的局限）已在这次观察到**：在创造物品栏界面里点击、搬动之后，客户端写回的背包栈没有 `data_revision`，99 被覆盖（读作无标记）。这符合 spec 里的已知局限；造受保护背包要用生存模式，或者不碰创造界面。

**造数据的注意事项**：带 `stampRevision=99` 的会话里，每只背包第一次打开就被盖章 99，**同一会话里第二次打开会被拒**（预期行为）。所以每只背包只开一次面板、一次放好东西再退出。

**一次虚惊**：丢出去的一只被保护背包在世界里找不到，原因是被另一只**已放行、带自动拾取**的背包吸了进去（存档里它嵌套在那只背包里）。不是缺陷，但测试时要留意。

**没做**：J、K、L（正常情形回归，T025）；T026 是你的降级实验。
