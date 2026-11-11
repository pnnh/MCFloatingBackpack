# Research: 存档级降级拦截

**读过的源码**：NeoForge 21.1.251 附带的 Minecraft 1.21.1 反编译源码（Gradle 缓存 `neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*_output.jar`，三份内容一致）与 NeoForge event bus 8.0.5 源码。下面标"已查证"的都来自这些源码；标"推断"的是由源码推出、**还没有在游戏里实测**的，写进 quickstart 的验证项。

## D1. 钩子：`ServerAboutToStartEvent`（已查证）

- `IntegratedServer.initServer()`：`handleServerAboutToStart(this)` → `loadLevel()` → `handleServerStarting(this)`。
- `DedicatedServer.initServer()`：在 `setPlayerList(...)` 之后 `handleServerAboutToStart(this)` → `loadLevel()`。
- `handleServerAboutToStart` 里先 `ConfigTracker.loadConfigs(SERVER, ..., server.getWorldPath(SERVERCONFIG))` 再 `post(new ServerAboutToStartEvent(server))`：**事件触发时 `server.getWorldPath(...)` 已经可用**（NeoForge 自己就在事件之前调用了它），`LevelResource.ROOT` 是世界根目录。
- 玩家数据在**玩家登录时**才读：`PlayerList.load(player)`：单人游戏的房主用 `worldData.getLoadedPlayerTag()`（level.dat 里的 `Player` 原始 NBT），其他玩家用 `playerIo.load(player)`（`playerdata/<uuid>.dat`）；两者都在 `loadLevel()` 与"服务器已启动"之后。构造 `IntegratedPlayerList`/`DedicatedPlayerList` 本身不读玩家数据。**所以 `ServerAboutToStartEvent` 早于任何背包数据被解码**，单人与专用服务器都一样。

**决定**：在 `ServerAboutToStartEvent` 里做检查；不用 `ServerStartingEvent`（它在 `loadLevel()` 之后，区块与世界已经载入）。

## D2. 怎样"中止启动"：抛 `ReportedException`（已查证）

- NeoForge event bus 8.0.5 的 `post`：监听器抛出异常时先记 ERROR，**然后重新抛出**（`EventBus.java` 第 362～364 行）。所以在事件处理器里抛异常会一路抛出 `initServer()`。
- `MinecraftServer.runServer()` 捕获它：记 `Encountered an unexpected exception`，用 `constructOrExtractCrashReport` 取出异常链里的 `ReportedException` 的报告（所以我们可以带自己的标题与说明），写 `crash-reports/crash-…-server.txt`，调用 `onServerCrash`，然后 `finally` 里 `stopServer()`。
- **专用服务器**：`onServerCrash` 之后进程退出，控制台有 ERROR 与崩溃报告路径。**单人游戏**：`IntegratedServer.onServerCrash` → `minecraft.delayCrashRaw(report)`，`doWorldLoad` 的第二个等待循环每圈 `handleDelayedCrash()` → `Minecraft.crash(...)`：打印报告、`handleExit(-1)`，**游戏窗口关闭，没有游戏内的崩溃界面**（1.21.1 原版没有，由启动器提示崩溃；说明在报告文件里）。
- **⚠ 更正（2026-11-11，第一次真实试验推翻了这里的第一版结论）**：`doWorldLoad` 在那个循环**之前**还有一个循环 `while (progressListener.get() == null) Thread.yield();`（`Minecraft.java` 第 2095 行），它不画界面、也不查崩溃；`progressListener` 只有在服务器的 `loadLevel()` 里调用 `progressListenerFactory.create(...)` 时才会被设置（第 2080 行的 lambda）。我们在 `loadLevel()` **之前**抛异常，所以客户端**永远卡在这个循环里**（画面停在加载前的模糊背景，需要 Ctrl+C），崩溃报告只在服务器侧写了文件，客户端根本走不到 `handleDelayedCrash`。第一版 research 读到了第二个循环却漏看了第一个。**修复**：单人游戏拒绝前先用反射取服务器的私有字段 `progressListenerFactory` 并调用 `create(0)`，让客户端的第一个循环结束（`WorldMarkerEvents.releaseWaitingClient`）；专用服务器没有这样的客户端，不需要。
- 更友好的做法都不可行或代价过大：
  - `server.halt(false)`：`initServer` 会继续跑 `loadLevel()`（载入世界、生成出生点区块，会写区块文件），客户端 `isReady()` 永远等不到 → 加载界面卡死。
  - 从服务器线程让客户端换成错误界面：客户端主线程被困在 `doWorldLoad` 的等待循环里出不来。
  - 在客户端"打开世界之前"检查：NeoForge 没有这个事件，要用 Mixin 或界面事件的取巧办法；本项目没有 Mixin，宪法 VII 要求最小依赖。
  - 结论：**单人游戏里拦截的呈现是一份带明确说明的崩溃报告**（规格里"错误界面还是崩溃报告，plan 里验证"的答案）。作为已知局限写进交付说明，更友好的客户端预检留作以后（要 Mixin，另议）。

## D3. 中止之后"存档没被动"到什么程度（已查证 + 推断）

- **单人游戏**：`Minecraft.doWorldLoad` 在启动集成服务器**之前**就 `levelStorage.saveDataTag(...)` 写了 level.dat（原版行为；`Player` 标签按原样透传，是原始 NBT，没有解码，不丢东西）。这发生在我们的检查之前，我们阻止不了，也不需要阻止。（已查证）
- **我们的检查之后**：没有人读 `playerdata`、没有人写区块。中止后 `stopServer()` 里：`playerList.saveAll()` 没有在线玩家；`levels` 为空；`saveAllChunks` 里 `this.overworld()` 为 null，`serverlevel2.getWorldBorder()` 会抛 `NullPointerException`，被 `runServer` 的 `finally` 捕获并记 `Exception stopping the server`——它发生在 `storageSource.saveDataTag` **之前**，所以不会写 level.dat；`storageSource.close()` 被跳过（session.lock 一直占着直到进程退出，崩溃本来就要退出）。（读源码得出的**推断**，要在 quickstart 里实测：日志里会多一条 NPE，存档文件除原版启动前写的 level.dat 之外不变。）
- **因此规格里"存档目录除日志外没有任何文件变化"要改**：单人游戏里 level.dat 与 session.lock 由原版在我们之前动过。验收改成：**playerdata 目录、区域（region 等）文件、我们的标记文件都不变**。见规格的同步修改。

- **实测（2026-11-11，第一次真实试验，那次客户端卡死）**：被拒绝的那次启动里，`level.dat`（10:06:24）与 `session.lock`（10:06:23）被原版动过，`playerdata`、`region`、标记文件的修改时间都**早于**那次启动（分别是上一次 99 的运行留下的 10:05 与 10:04）——即**拒绝路径没有读写玩家数据、区域文件与标记**；日志里确实出现了预期的 `Exception stopping the server` + `NullPointerException`（`serverlevel2` 为 null）。

## D4. 标记文件（决定）

- **位置**：`<世界根目录>/mcfloatingbackpack/world_marker.properties`（`server.getWorldPath(LevelResource.ROOT)` 下的专属子目录）。原版与 NeoForge 的世界根目录文件有 `level.dat`、`session.lock`、`playerdata/`、`data/`、`serverconfig/` 等；原版不会清理不认识的目录，旧版本也不认识它，所以"旧版本读不到也不动它"成立（**推断**：未见任何代码会清理世界根目录里不认识的目录；实测见 quickstart）。放在专属子目录里，以后要加别的存档级文件也不会与原版冲突，世界被拷走或导出时一起走。
- **格式**：`key=value` 文本，手工好改：
  ```
  # MCFloatingBackpack world marker. Delete this file, or lower data_revision, to accept the downgrade risk.
  data_revision=1
  mod_version=0.1.0
  ```
  `data_revision` 是**唯一参与判断**的字段（整数 ≥ 0）；`mod_version` 只用来在错误信息里写"这个存档最后由 X 版保存"，缺了也不影响。用 `java.util.Properties` 解析（注释、空行、转义都现成）。**不用** NBT 或 JSON：NBT 不好手改；JSON 的逗号引号对玩家更容易写坏。
- **读写用 `java.nio.file`，不依赖 Minecraft 类**：纯逻辑部分（解析、序列化、决策）与读写文件的部分都能用 JUnit 在临时目录里测。
- **写入**：先写同目录的临时文件再 `Files.move(..., ATOMIC_MOVE)`，不支持原子移动时退回普通替换，避免写到一半崩溃留下半个文件（半个文件会被读成"损坏"，按 D6 当 0 处理，也不致命）。

## D5. 写入时机：只在检查通过之后写一次（决定，简化了规格里的假设）

规格里把"保存时写"与"停止时写"都列为待定。因为标记的含义是"这个存档被至多哪个修订的版本碰过"，而**版本开始碰它的时刻就是检查通过的那一刻**，所以：

- **检查通过后、`loadLevel()` 之前立即写一次**（缺失/损坏 → 写当前；修订低于当前 → 写当前；相等且 `mod_version` 不同 → 更新 `mod_version`）。
- **之后不再写**：不需要"保存时"与"停止时"的写入，崩溃也不会丢标记（它在任何数据被改动**之前**就已经落盘）。这也让"自动存档要不要写"这个问题消失。
- 放行（接受风险）时**一个字节都不写**：标记保持更高值，所以下次不带参数还会被拦；永久接受靠玩家删文件。
- 调试开关 `worldMarkerRevision=N`：把"要写的修订"换成 N（只在 N 大于现有标记时才写，仍然只升不降）；**判断用的当前修订仍是真实的 `DataRevision.CURRENT`**，所以先用开关写下 99，再不带开关启动就会被拦。

## D6. 异常情形（决定）

| 情形 | 处理 |
|---|---|
| 没有标记文件 | 读作 0 → 通过并写入 |
| 内容损坏（解析失败、`data_revision` 缺失/不是整数/为负） | 读作 0，WARN（写出路径与原因），**通过**并按"写当前"覆盖。拦错的代价（玩家进不了存档）大于漏拦 |
| 文件存在但读不了（权限/IO 错误） | 同"损坏"：读作 0、WARN、通过；写不进去再记一条 WARN |
| 写不进去（只读目录等） | WARN，**不影响启动**（只是这一次少了标记） |
| 标记 > 当前 | 拦截（无放行参数），或放行（有） |

## D7. 放行参数（决定）

- `-Dmcfloatingbackpack.acceptDowngrade=true`，`Boolean.getBoolean`；写进 `VersionDebug` 旁边的新读取方法（它不是调试开关，是玩家用的参数，所以**不**放在 `mcfloatingbackpack.debug.` 前缀下）。
- 开发里 `./gradlew runClient -Pmcfb.acceptDowngrade=true`：`build.gradle` 里已有"`mcfb.*` → `mcfloatingbackpack.debug.*`"的转发；给 `acceptDowngrade` 加一个**特例**：转成不带 debug 前缀的 `mcfloatingbackpack.acceptDowngrade`（两行代码）。`worldMarkerRevision` 走原有转发即可。
- 启动器里加 JVM 参数（单人）或启动脚本里加（专用服务器）都能传 `-D`，规格的假设成立。

## D8. 提示文字（决定）：一份双语纯文本，不走语言文件

拦截发生在服务器启动时，没有客户端的语言环境；呈现形式是崩溃报告与日志——都是纯文本。所以**不新增语言键**，用一段固定的"中文 + English"文本（两段分开写，各自完整），写进崩溃报告的描述与日志的 ERROR。内容见 `contracts/texts.md`。（规格 FR-011 的"中英文"由此满足。）

## D9. 与 040 的关系与影响面（已查证）

- 040 的代码、测试、协议号都**不改**；041 只新增一个包、一个事件监听器、`VersionDebug` 两个读取方法、`build.gradle` 两行。
- 041 不碰背包、不碰网络。
- 只在**服务器侧**（含单人的集成服务器）执行；`ServerAboutToStartEvent` 不会在客户端连接别人的服务器时触发。
- 数据生成（`runData`）与游戏测试不启动 `MinecraftServer` 的这条路径，不受影响（**推断**：`runData` 不触发服务器启动事件；游戏测试服务器 `GameTestServer` 会触发，标记会写进它的临时世界，无害，**不专门处理**）。

## 实验与验证（只能在客户端会话里做，宪法 V）

见 `quickstart.md`。必须实测、验证前不得声称满足的几项：
1. 抛 `ReportedException` 后，单人游戏里实际的表现（D2）：**第一版（没有释放客户端）已实测：卡死在加载界面；修复后要再测：游戏应当关闭并留下崩溃报告**。
2. 中止后 `playerdata`、区域文件、标记文件的确没有变化，日志里的 NPE 确实出现且无害（D3）。
3. 专用服务器的表现：用户 2026-11-11 撤销了"不运行专用服务器"的旧规矩，通过路径与拒绝路径都已实测（见 DELIVERY）。
4. 旧版本不会清理 `mcfloatingbackpack/` 目录（D4 的推断）。
