# Delivery: 版本一致性提示

**状态**：代码与自动化完成（2026-11-10）；**游戏里的验证（quickstart A～N / 任务 T025～T028）等用户确认，尚未做**。网络协商、聊天显示与老客户端能否登录没有任何自动化测试能代替游戏里看一眼（宪法 V）。没有提交 Git，没有打 tag。

## 基线与结果

| | 类数 | 用例数 | 失败 |
|---|---|---|---|
| 开始前（038 完成后） | 125 | 890 | 0 |
| 完成后 | 128 | 917 | 0 |

新增测试类：`ModVersionsTest`（解析与比较：一致、PATCH 不参与、MINOR/MAJOR、预发布优先级与标识符排序、构建元数据、前导 `v`、缺 PATCH、15 种无法比较的输入、超大数字、对一批输入 `compare(a,b)` 与 `compare(b,a)` 互为镜像）、`VersionNoticesTest`、`VersionCheckResourcesTest`（语言键、握手包可选且单独注册、协议仍是 `"17"`、先 `hasChannel` 再发 hello、没有踢人或断开、登录与退出钩子、诊断头部、没有新增配置与命令、调试开关默认关）；`RowLayoutTest` 加 2 个用例（`isUnknownRow`）。

## 做了什么

- **`ModVersions`**（纯逻辑）：`MAJOR.MINOR[.PATCH][-预发布][+构建元数据]`（允许前导 `v`、缺 PATCH）；比较 MAJOR.MINOR，再比预发布的语义化版本优先级（没有预发布 > 有预发布）；PATCH 与构建元数据不参与；解析失败 → `UNCOMPARABLE`。`VersionNotices`：结果 → 提示类型，老客户端的中英双语纯文本。
- **握手**：`VersionHelloPayload`（服务器 → 客户端）、`VersionReportPayload`（客户端 → 服务器），在 `BackpackNetwork` 里用**单独的注册器** `event.registrar(MOD_ID).versioned("1").optional()` 注册；现有 `versioned("17")` 的注册器与所有现有包**一行没改**；版本 `"1"` 永远不改。
- **服务器**（`VersionNetwork.onLogin`，`PlayerLoggedInEvent`）：`server.isSingleplayerOwner(profile)`（单人游戏与局域网房主）→ 什么都不做；其余玩家：`hasChannel(VersionHelloPayload.TYPE)` 为真才发 hello，否则（老客户端）发中英双语纯文本并记 WARN。**绝不对没有通道的客户端发包**（NeoForge 对此抛 `UnsupportedOperationException`）。report 到达：只接受对 hello 的回应；不一致记 WARN，一致记 DEBUG（经 `DiagLog`，遵守已有的 `logging.level`）。
- **客户端**（`client/VersionClient`）：收到 hello → 比较 → 不一致就在**下一个客户端 tick** 用现有的聊天输出（带模组名前缀，只给本人）显示一条警告（`older`/`newer`/`unknown`，中英文），并总是回 report；每次登录最多一次，退出登录清空。
- **诊断**：`PlayerVersions`（内存）；背包"不见了"的 WARN 头部末尾带 `Client mod version: <版本>`。
- **预测宽容**：`RowLayout.isUnknownRow`；`ClientPlacement.canPlace` 对不认识的类别行放行；服务器与 `ItemCategoryMatcher` 没改（仍拒绝，测试里有断言）。
- **调试开关**（默认关，`VersionDebug`）：`-Pmcfb.checkOwner=true`、`serverVersion=X`、`clientVersion=X`、`legacyClient=true`；`build.gradle` 把 `-Pmcfb.*` 转成 `-Dmcfloatingbackpack.debug.*`（`--dry-run` 配置通过）。
- 文档：README 与 README_CN 的"多人游戏"、发布说明页脚、`docs/plan/release-process.md`、`docs/MCFloatingBackpack.md`、验证清单（039-A～J）。
- 协议仍是 17；没有新增配置项、命令；服务器只提醒，不拒绝、不踢人。

## 对规格的说明

- 判断"单人游戏与局域网房主"只用了一个 `server.isSingleplayerOwner(profile)`（规格里设想的是 `isSingleplayer()` 加所有者）：单人游戏的玩家与局域网房主本人为真，局域网里别的玩家为假，专用服务器上恒为假。
- 握手包的字符串解码上限是 256 个字符（超过会让**发送方**的连接按协议错误断开）；发送前已截断到 64。合法版本没有超过这个长度的，所以只有恶意客户端会碰到。规格里"超长按无版本处理、不断开"没有做到字面一致，记为已知限制。

## 未验证（等用户）

- **老客户端真实登录**（quickstart K，任务 T026）：新构建房主、038 的构建加入，应当能登录并收到纯文本提示。这是整个机制的核心假设，目前只有 NeoForge 字节码层面的核对（可选组件被"禁用"而不是拒绝连接、`hasChannel` 在配置结束时设置、对无通道客户端发包会抛异常）。**这一项失败要回到 plan 重新设计。**
- 两个客户端的局域网（L）：别的玩家被提示、房主不提示；`hasChannel` 在 `PlayerLoggedInEvent` 里的真实取值。
- 聊天里警告实际出现的位置与可见性；一个客户端里调试开关的各种情形（A～J）。
- 未知类别行的预测宽容（N）：不好构造时记为未验证（单元测试已覆盖 `isUnknownRow` 与 `ClientPlacement` 的接线文本）。
- 专用服务器没有验证（项目约定不运行专用服务器）。

## 已知限制

- 开发版之间版本都是 `0.1.0`，代码不同也检测不出来（"发布前才改版本号"的约定）。
- 本功能只提醒，不阻止登录；玩家可以无视。
- 老客户端的提示是纯文本，不随语言切换。
- 局域网里别的玩家被提示依据的是房主客户端的版本。
- 不认识类别的行，悬停说明会显示找不到的语言键（`CategoryNames.langKey` 对未知 id 给预设键），这一版不处理。
