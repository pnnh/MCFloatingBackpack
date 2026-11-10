# Quickstart: 验证 版本一致性提示

宪法 V：网络协商与聊天只能在客户端会话里验证。**先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，已有客户端就不要再启动第二个**（项目约定；第二个客户端由用户自己启动）。**不运行专用服务器。** 建议客户端与世界 serverconfig 的 `logging.level` 设为 DEBUG。

## 自动化部分（我在实现时跑，不替代游戏里的验证）

`./gradlew compileJava test`：`ModVersionsTest`（解析与比较：`0.1.0`、`0.2.1` vs `0.2.0`、预发布、构建元数据、不规范、空值、镜像对称、PATCH 不参与、前导 `v`）、`VersionNoticesTest`（结果 → 提示类型、老客户端文本含服务器版本与中英两段）、`RowLayoutTest`（`isUnknownRow`）、`VersionCheckResourcesTest`（语言键两种语言齐全且一致、握手注册用了 `optional()` 与 `"1"`、协议仍是 17、`hasChannel` 在发 hello 之前、没有新增配置项与命令）。

## 一个客户端里用调试开关验证（单人游戏即可）

开发运行加开关：`./gradlew runClient -Pmcfb.checkOwner=true -Pmcfb.serverVersion=0.2.0`（`checkOwner` 让单人游戏也走一遍握手）。

- **A. 客户端更旧**：`serverVersion=0.2.0`，客户端是 0.1.0：进入世界，聊天里出现一条"你的浮动背包版本（0.1.0）比服务器（0.2.0）旧……升级……备份存档"，只有自己看得到；日志有 WARN。
- **B. 客户端更新**：`serverVersion=0.0.9`：出现"比服务器新……降级……或联系服主升级服务器"。
- **C. PATCH 不同**：`serverVersion=0.1.7`：**没有任何提示**。
- **D. 一致**：`serverVersion=0.1.0`：没有任何提示，日志只有 DEBUG。
- **E. 预发布不同**：`serverVersion=0.1.0-rc1`：按不一致提示（客户端更新）。
- **F. 无法比较**：`serverVersion=banana`：提示"无法比较……"，不报错。
- **G. 退出再进**：同一个世界退出再进：每次登录各提示一次，没有重复。
- **H. 中英文**：切换语言，两种语言的警告都齐全。
- **I. 不加开关的单人游戏**：不传任何开关：没有提示、日志没有版本相关 WARN。
- **J. 老客户端的文字**：`-Pmcfb.checkOwner=true -Pmcfb.legacyClient=true`：聊天里出现服务器发来的中英双语纯文本，日志有 WARN。

## 两个客户端验证真实协商（需要你启动第二个客户端）

- **K. 老客户端仍能登录**：一个新构建的客户端开局域网当房主，另一个用 **038 的构建**（`git worktree` 或 `git checkout 23d478a` 构建的 jar）加入：**能登录**（这是整个机制的核心假设）；加入者聊天里出现服务器发来的纯文本提示；房主日志里有"无版本检测"的 WARN。
- **L. 局域网里别的玩家被提示**：两个都是新构建但 `mod_version` 不同（改 `gradle.properties` 再构建一个）：加入者按版本比较被提示，房主自己不提示。
- **M. 现有功能不受影响**：协议 17 的两端照常连接、面板与背包功能不变。

## 预测宽容

- **N. 未知类别行**：需要一个客户端不认识类别的行——用调试方式给背包写一个类别 id 为 `unknowncat` 的行（`/give` 带 `extension_rows`，或用 038 的构建当客户端连新构建房主、让房主放一个更新版本才有的类别——目前没有这样的版本，所以用 `/give` 或单元测试覆盖，游戏里记为"未验证"也可以）。已知类别（10 个与通用）的预览与红色高亮与以前一致。

## 记录

结果写进 `DELIVERY.md`：已验证 / 未验证 / 已知限制 / 测试数；用户确认之后才在 tasks.md 里勾客户端验证任务。
