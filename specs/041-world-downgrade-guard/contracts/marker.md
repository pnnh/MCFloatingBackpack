# Contract: 标记文件与启动检查

## 事件接线

- 监听 `net.neoforged.neoforge.event.server.ServerAboutToStartEvent`（默认优先级，`NeoForge.EVENT_BUS`）。它在 `IntegratedServer`、`DedicatedServer` 的 `initServer()` 里都早于 `loadLevel()`，也早于任何玩家数据的读取（research D1）。
- 处理器（`WorldMarkerEvents`，唯一碰 Minecraft 类型的类）只做：
  1. 把 `server.getWorldPath(LevelResource.ROOT).resolve("mcfloatingbackpack/world_marker.properties")`、`DataRevision.CURRENT`、`VersionDebug.acceptDowngrade()`、`VersionDebug.worldMarkerRevisionOverride()`、模组版本交给 `WorldMarkerCheck.run(...)`；
  2. 把返回的日志行按级别记下；
  3. `result.refused()` 时抛 `new ReportedException(CrashReport.forThrowable(new IllegalStateException(crashDetail), crashTitle))`。
- 整个流程（读 → `revisionToWrite` → `decide` → 写或拒）在无 Minecraft 类型的 `WorldMarkerCheck` 里，所以可以在临时目录里测；`WorldMarkerGuard.decide(read, current, accept, currentModVersion, target)` 的 `target` 是要写的修订，开发开关把它抬高时，即使标记等于当前也要写。
- **不得**：读写任何玩家数据；在别的地方写标记；在拒绝与放行路径上写任何文件（包括标记）。

## 文件读写

- `WorldMarkerStore.read(Path)`：不存在 → `MISSING`；读到的文本交给 `WorldMarker.parse`；`IOException`/权限错误 → `CORRUPT("unreadable: …")`。
- `WorldMarkerStore.write(Path, WorldMarker)`：创建父目录；写 `world_marker.properties.tmp`，再 `Files.move(tmp, marker, REPLACE_EXISTING, ATOMIC_MOVE)`；`AtomicMoveNotSupportedException` 时退回 `REPLACE_EXISTING`；任何 `IOException` 抛给调用方，由接线代码记 WARN 后**继续启动**（写不进去不得阻止启动）。
- 编码 UTF-8；解析容忍 `\r\n`、前后空白、注释行、空行。

## 放行参数与开发开关

| 开关 | 谁用 | 作用 |
|---|---|---|
| `-Dmcfloatingbackpack.acceptDowngrade=true` | 玩家 | 标记高于当前时放行一次；**不改标记**；标记不高于当前时无影响 |
| `-Pmcfb.acceptDowngrade=true`（runClient） | 开发 | `build.gradle` 特例转成上一行的系统属性 |
| `-Pmcfb.worldMarkerRevision=N`（runClient） | 开发 | 经原有转发成为 `mcfloatingbackpack.debug.worldMarkerRevision=N`；"要写的修订"变成 N，且仅在 N 大于现有标记时写；不是数字就忽略 |

## 不变量（测试要钉住）

1. 标记里的修订**只升不降**：决策与写入都不会产生比读到的值更小的标记。
2. 拒绝路径**不写**任何文件。
3. 放行路径**不写**标记。
4. 标记损坏或写不进去**不阻止**启动。
5. 比较基准是 `DataRevision.CURRENT`，不是模组版本号；开发开关只影响"写什么"，不影响"比什么"。
