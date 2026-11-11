# Quickstart: 存档级降级拦截（实现后的验证）

前置：先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`，确认没有客户端或服务器在跑（**专用服务器可以运行**，用户 2026-11-11 撤销了"不运行专用服务器"的旧规矩；我们不替用户创建 `eula.txt`）；**改世界前先备份**（用 `cp -R` 备份一份 "New World"）。每次 Gradle 运行后 `./gradlew --stop`。下面所有"启动"指 `./gradlew runClient …`，一次只开一个客户端。

自动化部分：`./gradlew test`（`worldmarker` 包的单元测试，全部分支；不需要游戏）。

## A. 无标记的旧存档照常进入（对应 041-A）

1. 用没有 `mcfloatingbackpack/` 目录的世界副本启动，进入世界，退出。
2. 看到：正常进入，没有提示；日志有 INFO `world marker set to data revision 1 (was none)`；世界根目录多了 `mcfloatingbackpack/world_marker.properties`，`data_revision=1`。

## B. 标记等于或小于当前修订（041-B）

1. 手工把标记改成 `data_revision=0`，启动 → 正常进入，退出后标记是 1。
2. 再启动一次 → 正常进入，日志里没有标记相关行。

## C. 标记更高：被拦（041-C，**必须实测的几个观察项**）

1. `./gradlew runClient -Pmcfb.worldMarkerRevision=99`，进入世界，退出（标记写成 99）。**前提：标记必须不高于当前修订 1**（没有、或已被改回 1）——开关只决定写什么，判断仍用真实的当前修订，所以标记已经是 99 时这一步本身就会被拦；先删掉标记文件或把它改成 1。
2. 先记下**拦截前**的指纹——**必须在第 1 步退出游戏之后、第 3 步启动之前记**（第 1 步进了世界、退出时会保存，指纹本来就会变；第一次试验把它记在第 1 步之前，所以前后对不上）：`find <世界>/playerdata <世界>/region -type f -exec shasum {} \;` 存成文件，另存 `mcfloatingbackpack/world_marker.properties` 的内容。也可以不算指纹，只看修改时间：被拦那次启动里只有 `level.dat` 与 `session.lock` 会变新。
3. **不带开关**再启动，从世界列表打开这个世界。
4. 看到（记录实际呈现）：
   - **游戏窗口在几秒内关闭**（不应该卡在加载界面；卡住就是 `releaseWaitingClient` 没生效，Ctrl+C 并告诉我）；原版 1.21.1 没有游戏内崩溃界面，终端里会有 `#@!@# Game crashed! Crash report saved to: …`，启动器（如有）提示崩溃。
   - `run/crash-reports/crash-…-server.txt` 的 `Description:` 是我们的双语标题，正文有两段说明、两种放行办法、标记路径（不带 `./`）。
   - 日志：我们的 ERROR（说明全文）；随后原版的 `Encountered an unexpected exception`；`stopServer` 里一条 `Exception stopping the server` + `NullPointerException`（预期，research D3）。
   - 重做第 2 步的指纹并对比：**`playerdata`、`region`、标记文件都没变**（level.dat、session.lock 由原版在前面动过，不看）。
5. 记录：游戏是否按预期关闭？崩溃报告与控制台是否清楚地说出了原因？（决定单人游戏的呈现是否可接受，写进 DELIVERY 的已知局限。）

## D. 启动参数放行（041-D）

1. `./gradlew runClient -Pmcfb.acceptDowngrade=true`（世界标记仍是 99）→ 正常进入；日志 WARN `accepted by -Dmcfloatingbackpack.acceptDowngrade=true`。
2. 退出后标记**仍是 99**（没被写低）；不带参数再启动，再次被拦。

## E. 删除或改低标记（041-E）

1. 删掉标记文件（或把 `data_revision` 改成 1），不带参数启动 → 正常进入；标记被写成当前修订。

## F. 标记损坏（041-F）

1. 把标记内容改成乱码（或 `data_revision=abc`），启动 → 正常进入；日志 WARN `unreadable … treating it as revision 0`；退出后标记被重写成正确内容。

## G. 回归（041-G）

放行后与正常情形里：背包的打开、点击、自动拾取、锻造台升级、040 的物品级保护（`-Pmcfb.stampRevision=99` 造出的高修订背包仍被拒绝并提示）都与以前一致；联机握手与协议号 17 不变。

## H. 对照实验（041-H，可选）

用带保护的构建，在 C 的世界上**不带放行参数**启动被拦；`playerdata` 的指纹与拦截前一致——这就实测了"没有任何背包数据被读取或改写"。

## I. 专用服务器（可以运行；用 `./gradlew runServer`，世界是 `run/world`）

先 `pgrep` 确认没有服务器在跑；**改世界前先备份 `run/world`**。服务器的 `run/` 目录与客户端共用。

1. **通过路径**：不带标记（或标记 ≤ 1）启动 → 日志有 `world marker set to data revision 1 (was none)`，随后 `Done`；`run/world/mcfloatingbackpack/world_marker.properties` 出现。
2. **拒绝路径**：把 `run/world/mcfloatingbackpack/world_marker.properties` 的 `data_revision` 改成 99，先记 `playerdata`、`region` 的指纹，再 `./gradlew runServer` → 约 1 秒内日志出现我们的 ERROR（双语全文）、`Encountered an unexpected exception`、`crash-reports/crash-…-server.txt`、`Exception stopping the server` + NPE，**进程自己退出**；`playerdata`、`region`、标记指纹不变。（观察：Gradle 的退出码是 0，见 DELIVERY。）
3. **放行路径**（需要能停服）：`-Dmcfloatingbackpack.acceptDowngrade=true` 启动，日志 WARN，服务器正常 `Done`；用 `stop` 命令停服；标记仍是 99。（`runServer` 没有 `-Pmcfb.*` 转发，需要时手工给 JVM 加参数。）
4. 还原标记（改回 1 或用备份）。

## J. 旧版本不会清理标记目录（research D4 的推断）

做 C 的第 1 步后，把世界拷一份给一个更旧的构建打开再退出，确认 `mcfloatingbackpack/` 目录还在（可选；旧构建不好得到时记"未验证"）。
