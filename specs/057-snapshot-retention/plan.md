# Implementation Plan: 背包快照保留策略调整

**Branch**: `057-snapshot-retention` | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

## Summary

把 030/031 的快照从"每只背包最新 5 份、只有防抖"改成三类、分别保留、带总容量上限与总开关，并在版本变化时把旧版本写的最新快照钉住、提醒玩家（设计依据与源码查证见 [research.md](./research.md)）：

1. **三类快照**：定时（每 15 分钟写"自上次定时以来变过"的背包，保留 60）、变更（现有防抖，保留 20）、升级前（保留 5）。类别写在**文件名里**（`<time>.dat` = 变更，`<time>-t.dat` = 定时，`<time>-u.dat` = 升级前）和文件头里：保留、清理、容量淘汰只看文件名与大小，不必解开文件。哈希去重改成按 (背包, 类别)。
2. **升级前钉住**：快照服务启动时比较"当前模组版本"与快照目录里 `state.properties` 记的版本；不同就对每只背包取最新一份快照，**读出压缩 NBT 复合标签 → 只改头部字段（类别、旧版本、新版本）→ 写成 `-u` 文件 → 重新读出新文件、与原来的 `contents` 子树比较相等 → 相等才删旧文件，不等就删新文件、保留旧文件并记 ERROR**；内容子树不经 `BackpackContents.CODEC`，原样搬运。每只最多 5 份。
3. **提醒**：登录时按 `state.properties` 里的"版本变化编号 + 已提示玩家名单"发一次；读玩家数据期间被留空的格子数：在 `PlayerEvent.LoadFromFile` 里把该玩家的 `<uuid>.dat` 里的背包**再解码一次**（游戏里实测玩家数据不是在主线程上解码的，见 research R10），用 `LenientStacks` 回调累加的线程本地计数 `LostCells` 在这个线程上取数；快照恢复结果带"无法恢复的格数"（复用 `SnapshotResultPayload.n1`，不用新包）。
4. **容量与开关**：`[snapshots].maxTotalMb`（默认 768，夹到 128～4096）与 `[snapshots].enabled`（默认 true）放进已有的服务端配置（`DiagConfig.SERVER_SPEC`）；写线程里每写完一份检查总大小，超限按"全局最旧定时 → 全局最旧变更"淘汰，永不删每只最新一份和升级前类。
6. **协议号 20 → 21**：版本行加类别与新版本号；版本列表请求/应答改成**分页 + 类别筛选**；`MAX_VERSIONS` 16 → 100；GUI 版本区加上一页/下一页与类别按钮（预览、恢复、删除本来就按快照时间标识，不用改）；命令序号 1..100，`info` 显示类别。
7. **文件格式 `version` 1 → 2**；读到 1 按变更类；读到更高版本的文件不列出、不计数、不淘汰、不删除。
8. **调试开关** `-Dmcfloatingbackpack.debug.timedSnapshotSeconds`（`VersionDebug`，默认 900；`build.gradle` 现有的 `-Pmcfb.<name>` 转发已涵盖）。
9. **测试**：纯逻辑（保留与淘汰、定时窗口、版本变化计划、状态文件、容量夹取、节流、文件名往返、序号规则、编解码）、资源（语言键、配置注释）、源码文本检查（接线、协议号、事件、先写后校验再删旧）；**单元测试的类路径里没有 Minecraft 的类（没有 `NbtIo`），所以文件级的钉住只能在游戏里验证**（容器里用 `bp_dump.py` 与哈希对照，见 quickstart），运行时再加一道自检（写完重读比较，不等就回滚）；游戏里验证三类、钉住、提示、窗口分页与类别按钮。

不做：解码层保留（不认识的物品原样保存）、备份管理员功能、离线通知、迁移旧快照文件、新增网络包（只改现有两个版本载荷与一个行记录）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖（测试里已有 `datafixerupper`，056 加的）

**Storage**: 快照目录（世界之外，`<服务器目录>/mcfloatingbackpack/snapshots/<世界名>/`）里每只背包一个文件夹，文件格式 `version` 2；新增目录根下的 `state.properties`；服务端配置多一节 `[snapshots]`

**Testing**: JUnit 5；纯逻辑 + 资源与接线文本检查（单元测试类路径里没有 Minecraft 类，文件级的 NBT 操作不能在单元测试里跑）；钉住、窗口、登录提示只能在客户端会话里验证

**Target Platform**: 客户端 + 服务端（专用服务器与单人游戏同一套）；开发机 macOS，AI 验证用容器 `mcfb-c1`

**Constraints**: 宪法 II（所有写、删、钉住、提示都在服务端，客户端只发请求）、III（一个 feature）、V（客户端会话验证）、VII（无新依赖、无 mixin、无反射）；**协议号 20 → 21**；`DataRevision` 不变（6）；快照仍在世界之外

**Scale/Scope**: 新增约 10 个类（纯逻辑 7 个：`SnapshotKind`、`SnapshotRetention`、`SnapshotCapacity`、`TimedWindow`、`SnapshotState`、`UpgradePlan`、`SnapshotSettings`、`Throttle`；MC 层：`SnapshotConfig`、`SnapshotNotices`、`LostCells`），改约 12 个（`SnapshotStore`、`SnapshotService`、`SnapshotMeta`、`SnapshotFiles`、`SnapshotOps`、`SnapshotServer`、`SnapshotCommands`、`SnapshotRequestRules`、`SnapshotCodecs`、两个版本载荷、`VersionRow`、客户端三个类、`LenientStacks`、`DiagConfig`、`VersionDebug`、`MCFloatingBackpack`），语言键约 20 个（中英）

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 快照窗口是 031 的独立界面，本 feature 只在里面加分页与按钮 | PASS |
| II. Server Owns Items | 写快照、钉住、淘汰、提示、恢复都在服务端；窗口只发"要哪一页/哪个类别"和以时间标识的预览/恢复/删除请求，服务端每次都重新校验 | PASS |
| III. One Feature At A Time | 只做快照保留策略 | PASS |
| IV. Vanilla Path Before Compat | 不涉及别的模组 | PASS |
| V. Verify In A Client Session | 容器里用调试开关缩短定时间隔，验证三类、钉住、提示、窗口；手感待用户 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图或模型；类别标记用文字，不进图集 | PASS（不适用） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖、无 mixin、无反射 | PASS |

设计后复核：无违规项。**一个需要写进交付说明的事实**：服务端配置值夹到范围内是自己做的（`ModConfigSpec` 的范围校验会把越界值重置成默认值，不是夹取），所以这两个条目按"不限范围"定义，再由 `SnapshotSettings` 夹取并记 WARN（research R6）。

## Project Structure

### Documentation (this feature)

```text
specs/057-snapshot-retention/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── storage.md              # 文件名、文件头、state.properties、读取规则
│   ├── rules.md                # 保留、淘汰、定时、版本变化计划、提示
│   ├── protocol-and-ui.md      # 载荷、窗口、命令
│   └── config-and-texts.md     # 配置项与注释、语言键、调试开关
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── snapshot/SnapshotKind.java        # 新（纯）：TIMED/CHANGE/UPGRADE，字节码、文件名后缀、保留数
├── snapshot/SnapshotRetention.java   # 新（纯）：按类别选出要删的（替代 SnapshotPolicy.toPrune）
├── snapshot/SnapshotPolicy.java      # 改：KEEP 常量拆成三个；或并入 SnapshotRetention 后删除
├── snapshot/SnapshotCapacity.java    # 新（纯）：总容量淘汰顺序
├── snapshot/TimedWindow.java         # 新（纯）：下一次定时时间、哪些背包是脏的
├── snapshot/SnapshotState.java       # 新（纯）：state.properties 的内容与文本往返、名单上限
├── snapshot/UpgradePlan.java         # 新（纯）：版本变化时要钉住哪些、哪些要挤掉
├── snapshot/SnapshotSettings.java    # 新（纯）：enabled、maxTotalMb 夹取、定时间隔
├── snapshot/Throttle.java            # 新（纯）：每小时最多一条
├── snapshot/SnapshotConfig.java      # 新（MC）：[snapshots] 配置项，定义在 DiagConfig 的服务端配置里
├── snapshot/SnapshotNotices.java     # 新（MC）：登录提示与丢失提示
├── snapshot/SnapshotMeta.java        # 改：加 kind、fromVersion、toVersion
├── snapshot/SnapshotFiles.java       # 改：文件名带类别后缀
├── snapshot/SnapshotStore.java       # 改：写（带类别与保留）、钉住（文件级）、淘汰、读头部（版本 1/2/更高）、容量统计
├── snapshot/SnapshotService.java     # 改：定时窗口、按 (背包, 类别) 去重、启动时版本检测、开关
├── snapshot/SnapshotOps.java         # 改：restore 返回无法恢复的格数；版本页；删除后让容量缓存失效
├── snapshot/SnapshotServer.java      # 改：版本页请求
├── snapshot/SnapshotCommands.java    # 改：序号 1..100、info 带类别、restore 提示格数
├── snapshot/SnapshotRequestRules.java# 改：MAX_VERSIONS=100；版本页大小与类别筛选的校验
├── snapshot/net/VersionRow.java      # 改：加 kind、toVersion
├── snapshot/net/SnapshotCodecs.java  # 改：行的编解码
├── snapshot/net/SnapshotVersionsRequestPayload.java  # 改：加 page、pageSize、kind
├── snapshot/net/SnapshotVersionsPayload.java         # 改：加 kind、page、pages、total
├── inventory/LostCells.java          # 新：被留空格子的线程本地计数（取走并清零）
├── inventory/LenientStacks.java      # 改：回调里再累加 LostCells
├── network/BackpackNetwork.java      # 改：versioned("21")
├── version/VersionDebug.java         # 改：timedSnapshotSeconds()
├── diag/DiagConfig.java              # 改：服务端配置里调用 SnapshotConfig.define
├── MCFloatingBackpack.java           # 改：LoadFromFile、登录、登出的监听
└── client/snapshot/                  # 改：SnapshotScreen（翻页、类别按钮、类别标记）、SnapshotScreenState（页、类别、总数）、SnapshotScreenLayout（版本区的翻页条与按钮）
src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json   # 加键（contracts/config-and-texts.md）
src/test/java/pnnh/mcfloatingbackpack/snapshot/…                         # 见 quickstart 与 tasks
build.gradle                                                              # 不改（-Pmcfb.<name> 的转发已有）
```

**Structure Decision**: 纯逻辑与 Minecraft 层分开，沿用 030 的"纯类 + `SnapshotStore`/`SnapshotService` 薄封装"。所有"选谁删、选谁钉、几点写"的决定都在纯类里，用假数据测；`SnapshotStore` 只做文件操作；`SnapshotService` 只调度。

## Complexity Tracking

无违规项。
