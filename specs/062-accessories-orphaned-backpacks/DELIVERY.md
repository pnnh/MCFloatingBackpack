# 交付记录：被 Accessories 丢掉的背包（062）

日期：2026-11-23。状态：已实现并在容器里验证，用户确认（T013，2026-11-23）。

## 基线与结果

- 基线（061 提交后）：243 类 1857 例；现在 245 类 1872 例，`./gradlew build -PcleanRuntime -PnoJei` 通过，jar 里没有 `io/wispforest` 的类。
- 协议号不改（22），`DataRevision` 仍 6。

## 做了什么

- `compat/OrphanRules`（纯）：带编号的孤立背包，玩家身上没有该编号才还；无编号的，按物品 id 比较"保存数据里的无编号数"与"现在的无编号数"，缺几只还几只，不超过孤立数。
- `compat/accessories/OrphanedBackpackScan`：读玩家数据文件，只用普通 NBT（`neoforge:attachments` → `accessories:inventory_holder` → `accessories_containers` → `items`/`cosmetics`），不引用 Accessories 类型，读不懂当没有。
- `compat/accessories/OrphanedBackpacks`：`LoadFromFile` 时读，登录后 20 刻（或下线事件里）核对：现存槽位类型用 `AccessoriesCapability.getContainers().keySet()`，在场的编号与无编号计数覆盖物品栏、光标、Accessories 现有容器（真实+装饰）、地上 16 格；归还走 `ContainerMenuAccess.fallback()`；聊天一行、日志一行；服务端对 `accessories` 的有效档位 FULL 才做；任何异常只写日志。
- 事件只挂在 `MCFloatingBackpack` 的 Accessories 分支里；语言键中英文；README 两份、配置注释、061 的 DELIVERY 与 `docs/plan` 同步。

## 游戏内验证（容器 `mcfb-c1`，Accessories 1.1.0-beta.54，单人）

| 项 | 结果 |
|---|---|
| 带编号的背包 | Accessories 专用槽里的金背包（编号 194dcd7b），`accessories=false` 重启后**回到物品栏**，日志与聊天各一行 ✓ |
| 无编号的背包 | 专用槽里的钻石背包（"No ID yet"），同样回到物品栏（日志 "number none"）✓ |
| 不复制 | 改回开关再启动：没有任何"Gave back"，钻石背包仍只有一只 ✓；之后的正常登录都没有提示、没有多余日志 ✓ |
| **下线路径**（登录 20 刻内就下线） | 把延迟临时改成 6000 刻后做：进世界时没有归还；Save & Quit 时日志出现 "Gave back"；改回开关重新启动，背包**在物品栏里**——说明下线事件在保存之前触发，物品随这次保存写进了文件 ✓（延迟已改回 20，源码里没有临时代码） |
| 档位门 | 客户端 `accessories=GENERIC` 再做一次：**不归还、不提示**（背包在那一轮里因此仍然丢失，符合预期）✓ |

## 没验证的

- **物品栏满时掉在脚边**：用的是 060 已验证的 `fallback().drop`，这次没有单独塞满物品栏再试；聊天的 "dropped" 变体没有看到过。
- **装饰（cosmetic）槽里的背包**：代码读 `cosmetics` 列表，没有在游戏里放进装饰格验证。
- 专用服务器（读文件的路径相同）；Accessories 的其它版本。
- 无编号背包在登录后 20 刻内被打开而得到编号的竞态（计数会少算一只，偏向少还）：只在研究里分析过。

## 已知局限与取舍

1. 只处理**整个槽位类型都不存在**的情形（Accessories 对格数变少自己归还，字节码查证）；只还浮动背包，别的物品还是被 Accessories 丢掉。
2. 若服务器在登录后核对之前崩溃，且期间已发生保存，物品仍会丢（背包内容可以从 030 的快照恢复）。
3. Accessories 是 beta，数据格式若变，读不懂时一律不动。
4. 容器已还原：启动配置两项为 true，客户端配置 `modLevels` 为空，客户端停在标题界面。

## 宪法复查

II（找出、核对、归还全在服务端）、III（只做整个槽位类型不存在的归还）、V（容器里验证如上，没验证的如实写明）、VI（登录时多读一次文件，只在装了 Accessories 时）、VII（第三方类型只在 `compat/accessories/`，读数据只用普通 NBT；档位 FULL 才做；无新依赖、无反射、无 mixin；出错不动）：通过。
