# Delivery: 存档级降级拦截

**状态**：代码与自动化完成（2026-11-10）；真实试验（用户，2026-11-11）：A～F 的结果已报告为通过（C 先发现并修了一个卡死缺陷，复测通过），G 只验证了几个基本功能，未全部回归；专用服务器的通过路径（用户）与拒绝路径（Claude）已实测；**T023 已由用户确认勾选（G 仅部分验证）；T024 未勾选**。拦截在单人游戏里实际长什么样、中止后存档是否真的没变，没有任何自动化测试能代替游戏里看一眼（宪法 V）。没有提交 Git，没有打 tag。

## 基线与结果

| | 类数 | 用例数 | 失败 |
|---|---|---|---|
| 开始前（040 完成后） | 133 | 944 | 0 |
| 完成后 | 140 | 1007 | 0 |

新增测试类（`worldmarker` 包 6 个 + `VersionDebugTest`）：`WorldMarkerTest`（文本往返、手工编辑、损坏的各种形态）、`WorldMarkerGuardTest`（决策表全部分支，含"版本号不参与比较""拒绝与放行不要求写""标记只升不降""开发开关抬高已是当前的标记"）、`WorldMarkerStoreTest`（临时目录里的读写、不残留 `.tmp`、非 UTF-8、目录占位、只读目录）、`WorldMarkerCheckTest`（整个流程：**拒绝与放行不写任何文件**、放行不降低标记所以下次仍被拦、删除或改低标记放行、损坏的重写且不拦、写不进去不拦、开发开关）、`WorldGuardTextsTest`（拒绝说明里必须有的信息、中英两段）、`WorldMarkerWiringTest`（监听器在 `ServerAboutToStartEvent` 上而不是 `ServerStartingEvent`、只有检查会写标记、不碰玩家数据与 040 的入口、只有事件类碰 Minecraft 类型、没有新增命令/语言键/配置/网络包、协议号仍是 17、`build.gradle` 的转发）、`VersionDebugTest`（两个开关）。`./gradlew build` 通过，jar 里有 `worldmarker` 包。

## 第一次真实试验（用户，2026-11-11）：A、B 通过；C 发现了一个会卡死游戏的缺陷，已修，待复测

- **A**：无标记的旧存档照常进入，`run/saves/New World/mcfloatingbackpack/world_marker.properties` 生成（`data_revision=1`、`mod_version=0.1.0`），日志有 INFO。
- **B**：标记改成 0 后进入，标记变成 1。
- **C（第一版）**：标记 99、不带开关进入世界 → 日志里有我们的 ERROR（双语全文）、`Exception caught during firing event`、`Encountered an unexpected exception`、崩溃报告写到了 `run/crash-reports/crash-…-server.txt`、`Exception stopping the server` + NPE（与 research D3 的推断一致）；标记仍是 99。**但游戏卡死在加载界面，需要 Ctrl+C。** 原因：客户端 `doWorldLoad` 里有一个不画界面的 `while (progressListener.get() == null) Thread.yield();`，监听器只在被我们跳过的 `loadLevel()` 里创建，客户端永远等不到。plan 的 research D2 读到了后面的循环却漏看了这个，**写的"游戏以崩溃报告收场"是错的**（也没有游戏内崩溃界面，原版 1.21.1 只是退出）。
- **修复**：`WorldMarkerEvents.releaseWaitingClient`——单人游戏拒绝前，反射取服务器私有字段 `progressListenerFactory` 并 `create(0)`，客户端的等待循环结束，之后走 `handleDelayedCrash` 退出游戏。**修复后的行为还没有复测。**
- **C（修复后，复测，2026-11-11 10:34）**：标记已是 99 时带 `-Pmcfb.worldMarkerRevision=99` 启动（开关不改比较基准，所以这一次本身就是一次拦截）→ **游戏没有卡死，直接退出**，崩溃报告 `crash-2026-11-11_10.34.48-server.txt` 的 `Description:` 是双语标题，路径已无 `./`；日志里仍有预期的 NPE。**`playerdata`、`region` 的 6 个文件哈希与启动前完全一致（用户贴的启动前哈希与事后重算的一致），标记仍是 99**；只有 `level.dat`、`session.lock` 被原版动过。这是对"拒绝路径不读写玩家数据、区域文件与标记"的第二次独立证据。
- **指纹对比（第一次）**：用户的前后指纹全部不同，是因为"之前"取在了第 1 步（99 的那次运行）之前；那次运行退出时本来就会保存。看修改时间更说明问题：被拦那次启动（10:06:23～24）只动了 `level.dat` 与 `session.lock`，`playerdata`、`region`、标记文件的修改时间都早于它（10:05 与 10:04）——**拒绝路径没有读写它们**。quickstart C 已把取指纹的时机写清楚。
- 顺带修了：错误信息里的标记路径有 `./`（`run/./saves/New World/./mcfloatingbackpack/…`），现在规整成干净的绝对路径。

## 后续试验（用户，2026-11-11 10:40～10:55）与专用服务器

- **D 放行**：`-Pmcfb.acceptDowngrade=true` 进入世界，控制台与 latest.log 都有 WARN `world marker revision 99 is higher than this version's 1; accepted by -Dmcfloatingbackpack.acceptDowngrade=true …`；退出正常。（标记仍是 99，已由 C 之后的核对看到。）
- **E**：把 `data_revision` 改成 1，正常进入。
- **F**：把 `data_revision` 改成 `abc`，正常进入；WARN `… is unreadable (data_revision is not a whole number: abc); treating it as revision 0 and rewriting it.`。
- **G**：验证了几个基本功能正常，**未全部回归**（如实记录，没有声称完整）。
- **J（旧版本不清理标记目录）**：未验证。
- **专用服务器（撤销"不运行专用服务器"旧规矩后）**：
  - **通过路径（用户）**：`runServer` 启动 `run/world`，日志 10:53:29 `world marker set to data revision 1 (was none)`，随后 `Done (1.385s)`；客户端用 `127.0.0.1` 连接多人游戏正常进入。
  - **拒绝路径（Claude，10:56，已备份 `run/world`）**：标记改成 99 后 `./gradlew runServer`：约 0.8 秒后日志出现我们的 ERROR（双语全文）、`Exception caught during firing event`、`Encountered an unexpected exception`、`crash-reports/crash-2026-11-11_10.56.53-server.txt`、`Exception stopping the server` + NPE（与单人游戏一致），**进程自己退出**；`playerdata` 与 `region` 的 6 个文件哈希前后**完全相同**，标记仍是 99；与备份相比只有 `level.dat`、`level.dat_old` 不同（专用服务器启动早期原版自己写的，在我们的检查之前）。随后已把标记还原成 1。
  - **一个观察**：这次 Gradle 的退出码是 0（`BUILD SUCCESSFUL`），即**进程在拒绝后以成功状态退出**；靠"失败重启"的守护脚本不会因为拒绝而反复重启，但也不会把它当失败告警，运维要看日志。是否需要让它以非零码退出，另议（没有改动）。
  - **放行路径**：未做（需要手工给 JVM 加参数并用 `stop` 停服）。

## 清单勾选依据（用户 2026-11-11 确认 041-A～F 已验证）

用户确认 A～F 的步骤与他测试时一致，可以勾选；Claude 用当天的轮转日志核对了时间线：A（09:23）、B（09:56）、C 第 1 步（10:04）与第一次被拦（10:06）是**修复前**的构建，但它们走的是写标记路径，修复只改了拒绝路径（放行客户端的等待）与错误信息里的路径文字，路径本身没变；C 复测（10:34）、D（10:42）、E（10:47）、F（10:48）与专用服务器（10:53）都是**修复后**的构建。因此 A～F 可以勾。**随后用户确认**：G 暂时只测到几个基本功能，后续继续做，清单 041-G 与任务 T023 已勾并备注"部分验证"。**仍没有勾的**：H（可选）、I（文档核对，等游戏里确认后再勾）、J（专用服务器放行路径未做）、T024。游戏里没亲眼看到、只有单元测试覆盖的：B 的"模组版本号变而修订不变不拦"，D 的"放行后不带参数再启动再次被拦"（D 的日志证明标记没被写低）。

## 数据修订：本 feature 递增吗？

**不递增**：`DataRevision.CURRENT` 仍是 1。本 feature 没有改变背包的存储数据，只是在存档里多了一个标记文件。

## 做了什么

- **标记**：`<世界>/mcfloatingbackpack/world_marker.properties`（`data_revision`、`mod_version`，手工好改）；`WorldMarker` 解析/序列化，`WorldMarkerStore` 读写（先写 `.tmp` 再原子替换）。
- **检查**：`WorldMarkerEvents` 监听 `ServerAboutToStartEvent`（早于 `loadLevel()` 与任何玩家数据的读取；两种服务器都一样），把路径等交给 `WorldMarkerCheck.run`：没有标记/损坏/低于当前 → 通过并写；等于当前 → 通过（仅 `mod_version` 变了才更新）；高于当前 → 拒绝（抛带说明的 `ReportedException`，拒绝路径不写任何文件）或在带 `-Dmcfloatingbackpack.acceptDowngrade=true` 时放行（记 WARN，不写）。
- **文字**：一份固定的中英双语纯文本（`WorldGuardTexts`），写进日志 ERROR 与崩溃报告；**没有新增语言键**。
- **开关**：`VersionDebug.acceptDowngrade()`（玩家用的参数，不带 `debug.` 前缀）与 `worldMarkerRevisionOverride()`；`build.gradle` 给 `-Pmcfb.acceptDowngrade` 加了特例转发，`-Pmcfb.worldMarkerRevision` 走原有转发。
- **文档**：`docs/plan/release-process.md` 的"存档级降级拦截"一节（已实现的描述）、README 与 README_CN 的"升级与降级"（拦截说明与两种放行办法）、验证清单 041-A～I、040 的 spec/DELIVERY 的更正与指向。`.github/release-notes/v0.1.0.md` 已有"不支持降级"，不需要再改。
- 不改 040 的任何代码与测试、协议号 17、现有网络包；不新增配置项、命令。

## 对规格的说明（plan 与实现阶段的调整）

1. **单人游戏里拦截的呈现是游戏关闭加崩溃报告**，不是错误界面（没有可行的更友好办法，research D2）；第一版还漏了"先让客户端的等待循环出得来"，被第一次真实试验发现，见上。
2. **"存档目录除日志外无变化"改成**：`playerdata`、区域文件与标记文件不变；level.dat 与 session.lock 由原版在我们之前动过（research D3）。
3. **写入时机是"检查通过后写一次"**，之后不再写（research D5）。
4. **提示文字是双语纯文本，不走语言文件**（research D8）。
5. **标记里多了 `mod_version`**（规格没提），只用于说明，不参与判断。
6. **整个启动检查拆进 `WorldMarkerCheck`**（任务里原本写在事件类里）：为了能在临时目录里用行为测试钉住"拒绝与放行不写文件"，事件类只剩接线。
7. **`decide` 多了 `target` 参数**：实现时发现，开发开关把要写的修订抬高时（标记已等于当前），也必须写；没有它，`-Pmcfb.worldMarkerRevision=99` 在已经进过一次的世界上不会生效。

## 未验证（等用户）

- **修复后**单人游戏里被拦时的实际表现（quickstart C）：游戏窗口应当在几秒内关闭（不卡在加载界面），崩溃报告与控制台的说明是否清楚。
- **中止后存档确实没变**：`playerdata`、`region`、标记文件的指纹前后一致；日志里的 `Exception stopping the server` + `NullPointerException` 确实出现且无害（research D3 的推断，读源码得出）。
- **G 的完整回归**：040 的物品级保护（`-Pmcfb.stampRevision=99`）与其它功能，用户只验证了几个基本功能，未全部回归。
- **专用服务器的放行路径**：未做（拒绝与通过路径已实测，见上）。
- **旧版本不清理 `mcfloatingbackpack/` 目录**（research D4 的推断）：未验证。

## 已知限制

- 单人游戏里被拦时游戏会关闭（像崩溃一样，启动器提示崩溃），说明写在 `crash-reports` 与控制台里；原版 1.21.1 没有游戏内崩溃界面，更友好的客户端预检要 Mixin，另议。
- 拒绝前释放客户端的做法依赖服务器的私有字段名 `progressListenerFactory`（只针对 1.21.1）；反射失败时拒绝照常进行，但单人游戏可能卡在加载界面，日志里会说明。
- 标记被手工删除或改低即失效（这也是"永久接受风险"的出口）。
- 本机制出现之前的版本不认识标记，也不检查；降级到它们仍会丢数据，只能靠备份。
- 原版在我们之前对 level.dat 的写入（原始 NBT 透传，不丢东西）与 session.lock 的占用无法避免。
- 标记写不进去（只读目录等）时只记 WARN，本次不受保护。
- 总口径不变：**本模组不支持降级，降级前请备份存档**；041 只让"低版本拒绝读高版本数据"在存档级成立。
