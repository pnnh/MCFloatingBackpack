# Implementation Plan: 存档级降级拦截

**Branch**: `041-world-downgrade-guard` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

## Summary

在存档里放一个标记文件，服务器启动存档时（`ServerAboutToStartEvent`，**早于任何玩家数据被读取**，见 [research.md](./research.md) D1）读它：标记的数据修订大于当前 `DataRevision.CURRENT` 就**抛一个带说明的 `ReportedException` 中止启动**；否则通过，并把当前修订写进标记（只升不降）。

1. **纯逻辑（无 Minecraft 类型）**：
   - `WorldMarker`：标记的内容（`data_revision`、`mod_version`）与文本的解析/序列化（`key=value`，`java.util.Properties`）；损坏判定。
   - `WorldMarkerGuard.decide(读到的标记状态, 当前修订, 是否带放行参数, 模组版本, 要写的修订)` → `PASS` / `PASS_AND_WRITE` / `REFUSE` / `ACCEPTED`（决策表见 `data-model.md`）；`revisionToWrite` 给出要写的修订（开发开关只抬高、永不低于现有标记）。
   - `WorldMarkerCheck.run(标记路径, 当前修订, 放行, 开发开关, 模组版本)`：整个启动检查（读 → 决策 → 写或拒），返回要记的日志行与崩溃报告的标题和说明；**拒绝与放行路径不写任何文件**由它的测试在临时目录里钉住。
   - `WorldGuardTexts`：拦截说明、日志行的文字（中英双语，纯字符串）。
2. **读写文件（只用 `java.nio`，也无 Minecraft 类型）**：`WorldMarkerStore`：`read(Path)` → `MISSING` / `VALID` / `CORRUPT`；`write(Path, WorldMarker)` 先写临时文件再原子替换。
3. **接线（唯一碰 Minecraft 类型的地方）**：`WorldMarkerEvents`：在 `MCFloatingBackpack` 里注册 `ServerAboutToStartEvent` 监听器（与 030 的 `ServerStartingEvent` 注册方式一致）；取 `server.getWorldPath(LevelResource.ROOT)/mcfloatingbackpack/world_marker.properties` 交给 `WorldMarkerCheck`，按返回值记日志，需要拒绝时抛 `ReportedException(CrashReport)`。
4. **放行参数与开发开关**：`-Dmcfloatingbackpack.acceptDowngrade=true`（玩家用，一次性）；`-Pmcfb.worldMarkerRevision=N`（开发用，让"要写的修订"变成 N）；`VersionDebug` 加两个读取方法，`build.gradle` 给 `acceptDowngrade` 加一个特例转发。
5. **文档**：规格 FR-014 列的各处（README 两份在实现完成后再核对，见验证清单 041-I）。

不新增配置项、命令、语言键、网络包；不改协议号 17；不改 040 的任何代码。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖（解析用 JDK 的 `Properties`，读写用 `java.nio.file`）

**Storage**: 存档根目录下 `mcfloatingbackpack/world_marker.properties`（一个几行的文本文件）

**Testing**: JUnit 5：`WorldMarker` 的解析与序列化、`WorldMarkerGuard` 的决策表（全部分支）、`WorldMarkerStore` 在临时目录里的读写（缺失、损坏、读不了、原子替换、只读目录）、`WorldGuardTexts`、源码接线文本（监听器注册、没有其它写标记的地方、`DataRevision.CURRENT` 作为比较的基准）。游戏里的行为只能在客户端会话里验证（宪法 V）

**Target Platform**: 服务器侧（含单人的集成服务器）；开发机 macOS；专用服务器可以运行（`./gradlew runServer`）

**Constraints**: 宪法 II（服务器权威：检查与写入都在服务器）、III（只做存档级拦截，不改 040）、V、VII；协议 17 不变

**Scale/Scope**: 新增 1 个包 7 个类/接口（`MarkerRead`、`WorldMarker`、`WorldMarkerGuard`、`WorldMarkerStore`、`WorldMarkerCheck`、`WorldGuardTexts`、`WorldMarkerEvents`）+ 小改 `MCFloatingBackpack`（注册监听器）、`VersionDebug`（两个读取方法）、`build.gradle`（特例转发）+ 约 5 个测试类

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面（单人游戏里拒绝时游戏关闭、由启动器提示崩溃） | PASS |
| II. Server Owns Items | 检查与写入全在服务器启动路径；不经过客户端 | PASS |
| III. One Feature At A Time | 只做存档级拦截；不改 040、不改快照、不做数据保留 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方模组 | PASS |
| V. Verify In A Client Session | quickstart 用 `worldMarkerRevision` 开关在一个客户端里验证拦截、放行、损坏、删除；专用服务器用 `runServer` 验证（通过与拒绝路径）；"中止后存档没被动"与"单人游戏里实际看到什么"必须实测，**验证前不得声称满足** | PASS（已规划，限制已记录） |
| VI. Shared Resources Have A Budget | 无贴图、无图集、无渲染成本；启动时多读写一个几行的文件 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖；不用 Mixin | PASS |

## 对规格的说明（plan 阶段查证后的调整）

1. **单人游戏里"拒绝启动"的呈现是游戏关闭加崩溃报告**，不是错误界面（research D2）：没有可行的更友好办法（`halt` 会卡死加载界面、客户端预检要 Mixin）；原版 1.21.1 没有游戏内崩溃界面。崩溃报告的"描述"就是拦截说明。**第一版实现漏了一步**：客户端先在一个不画界面的循环里等服务器的区块进度监听器，而它在被我们跳过的 `loadLevel()` 里才创建，结果游戏卡死；已加 `releaseWaitingClient` 修复（research D2 的更正）。已知局限，写进交付说明。
2. **"存档目录除日志外没有任何文件变化"不成立**：单人游戏里原版在我们的检查之前就写了 level.dat（原始 NBT 透传，不丢东西）并持有 session.lock（research D3）。验收改为：**`playerdata`、区域文件与我们的标记文件都不变**。规格 FR-005、User Story 1 第 4 条与 SC-001 已据此改写。
3. **写入时机简化为"检查通过后写一次"**（research D5）：不需要"保存时"与"停止时"的写入；崩溃也不丢标记。规格 FR-007/FR-008 与此一致（FR-008 的"具体时机由 plan 给出"就是这里）。
4. **提示文字是一份双语纯文本，不走语言文件**（research D8）：拦截发生在服务器启动时，没有客户端语言环境；呈现形式本来就是崩溃报告与日志。
5. **标记文件含 `mod_version`**（规格没提）：只用于错误信息里写"这个存档最后由哪个版本保存"，不参与判断。

## Project Structure

### Documentation (this feature)

```text
specs/041-world-downgrade-guard/
├── plan.md
├── research.md        # 钩子与时序、中止方式、存档动了什么、标记文件、写入时机、异常情形（含源码出处）
├── data-model.md
├── quickstart.md      # 开发开关验证 + 观察项 + 专用服务器（runServer）
├── contracts/
│   ├── marker.md      # 标记文件格式、决策表、事件接线
│   └── texts.md       # 拦截说明与日志行的文字
├── checklists/requirements.md
└── tasks.md           # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── worldmarker/
│   ├── MarkerRead.java          # 新：读文件的结果（Missing / Valid / Corrupt）
│   ├── WorldMarker.java         # 新：内容 + 文本解析/序列化（纯逻辑）
│   ├── WorldMarkerGuard.java    # 新：决策表（纯逻辑）
│   ├── WorldMarkerStore.java    # 新：java.nio 读写（无 Minecraft 类型）
│   ├── WorldMarkerCheck.java    # 新：整个启动检查（无 Minecraft 类型，可在临时目录里测）
│   ├── WorldGuardTexts.java     # 新：拦截说明与日志行（纯字符串）
│   └── WorldMarkerEvents.java   # 新：ServerAboutToStartEvent 接线（唯一碰 Minecraft 类型的类）
├── MCFloatingBackpack.java      # 改：注册监听器（一行）
└── version/VersionDebug.java    # 改：acceptDowngrade()、worldMarkerRevisionOverride()
build.gradle                     # 改：mcfb.acceptDowngrade → mcfloatingbackpack.acceptDowngrade
docs/plan/release-process.md, README.md, README_CN.md, docs/plan/manual-verification-checklist.md
src/test/java/pnnh/mcfloatingbackpack/worldmarker/
├── WorldMarkerTest.java
├── WorldMarkerGuardTest.java
├── WorldMarkerStoreTest.java
├── WorldMarkerCheckTest.java    # 整个流程：拒绝与放行不写文件、只升不降、损坏与写不进去不拦
├── WorldGuardTextsTest.java
└── WorldMarkerWiringTest.java   # 源码接线文本
src/test/java/pnnh/mcfloatingbackpack/version/VersionDebugTest.java
```

**Structure Decision**: 解析、决策、读写、文字都是无 Minecraft 类型的纯 Java，能用 JUnit 在临时目录里完整测试；对游戏只在一个事件监听器里接线。

## Complexity Tracking

无需要说明的违规。
