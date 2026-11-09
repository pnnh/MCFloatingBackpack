# Implementation Plan: 背包快照（去掉登记表）

**Branch**: `030-backpack-snapshots` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

## Summary

去掉世界里的登记表（`BackpackRegistry`、`RegistryRecord`、`RegistryIndex`、影子副本、墓碑与它的提示），换成**单个背包的快照文件**：

1. **标记**：`BackpackGate.afterSave` 只在内存里记"这只背包有变化"（持有同一个不可变 `BackpackContents`）。`acquire` 在本次运行里第一次见到一只背包时也记一次（补基线）。
2. **调度**：纯逻辑 `SnapshotScheduler`（静默 10 秒或最长 5 分钟到期；退出、停服、命令前全部到期）。主线程每秒检查一次（挂在 `ServerTickEvent.Post`，待写集合为空时零成本）。
3. **写出**：到期的在主线程编码成 NBT，交给**单个后台线程**写文件（临时文件 + 改名，内容哈希与最近一份相同则跳过，之后按保留策略删旧）。失败只记日志。
4. **命令**：`/mcfloatingbackpack snapshot …`（别名 `/mcfb`）。列表过滤分页、选择器、时间文本是纯逻辑；`restore` 生成一只**无标识的新背包**。
5. **编号 4 位**：`BackpackIds` 长度由 3 改 4，其余算法不变。

不新增网络包（协议不变）、贴图、配方与设置项；`BackpackContents` 编码不变；背包栈上的数据组件不变。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`NbtIo`、`RegisterCommandsEvent`、`ServerTickEvent`、`ServerStoppingEvent`）；无新增依赖

**Storage**: `<服务器实例目录>/mcfloatingbackpack/snapshots/<世界文件夹名>/<UUID>-<编号>/<毫秒>.dat`（压缩 NBT）

**Testing**: JUnit 5 纯逻辑：`SnapshotSchedulerTest`、`SnapshotPolicyTest`、`SnapshotFilesTest`、`SnapshotListingTest`、`BackpackSelectorTest`、`AgeTextTest`、`BackpackIdsTest`（4 位）、`SnapshotCommandResourcesTest`（语言键与英文后备一致）；文件读写、命令、后台线程只能在游戏里验证

**Target Platform**: 服务端逻辑，客户端无改动（编号长度由 `BackpackIds` 决定，标题栏已按文本宽度自适应）

**Constraints**: 026 的"保存后不得就地修改内容"约定；写失败不影响游戏；主线程不做磁盘写（命令里的等待除外，数据很小）；宪法 V（客户端验证）、VI（成本：只在使用时有开销）

**Scale/Scope**: 新增 `snapshot/` 包（纯逻辑 6 个类 + 服务 3 个类）；删除登记表相关 5 个类和 `admin/` 包；改 `BackpackGate`、`BackpackIds`、`MCFloatingBackpack`、语言文件、2 份文档

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面 | PASS |
| II. Server Owns Items | 快照只在服务端写；`restore` 由服务端生成物品；权限在服务端检查 | PASS |
| III. One Feature At A Time | 只做快照与它的命令；管理员面板、直接改背包、墓碑都不做 | PASS |
| IV. Vanilla Path Before Compat | 不引用第三方；不碰原版容器 | PASS |
| V. Verify In A Client Session | quickstart 要求在游戏里验证写出、保留、列表、恢复、权限、回档 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图；使用背包时只做内存标记；写文件在后台线程；磁盘占用 = 每只背包 ≤5 份 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新增依赖、无设置项；第一档（原版）不受影响 | PASS |

## Design Decisions

- **D1 快照放世界之外**：回档会换掉整个世界目录，放在里面的备份会一起回到过去。按世界文件夹名分目录，单人游戏多个世界互不混。
- **D2 一个文件 = 一份快照**：元数据与内容在同一个文件里，列表读文件头即可，不需要索引文件，也就没有"索引与文件不一致"的问题；删除就是删文件。
- **D3 去重用内容哈希**：后台线程为每个 UUID 记最近一份内容的哈希（`CompoundTag.hashCode()`，几个字节）；没有记录时读最近一份文件算一次。内容相同不写。
- **D4 编码在主线程、写文件在后台线程**：编码要用服务器的注册表访问，且读的是主线程拥有的内容对象；后台线程只拿到编码好的 NBT。命令执行前用"提交一个空任务并等待"的方式等后台线程写完。
- **D5 恢复 = 新背包，不带标识**：与 030 旧版的 `give` 一致；第一次使用时分配新标识，所以不与原背包共用标识，也不会让两只背包的快照混在一起。
- **D6 权限**：根节点不要求权限；`list`、`info` 对所有玩家开放但只匹配自己的；op 的命令各自 `requires(permission 2)`。控制台等同 op。
- **D7 不拒绝任何请求**：`BackpackGate.acquire` 只分配标识。登记表、墓碑、`NoticeLimiter`、`BackpackMessages` 一起删除。
- **D8 编号 4 位**：只改 `BackpackIds`（长度、总数）与它的测试、文档、选择器的说明文字；标题栏用文本宽度布局，不改。

## Project Structure

```text
src/main/java/pnnh/mcfloatingbackpack/
├── snapshot/                         # 新增
│   ├── SnapshotMeta.java             # 纯：uuid、owner、ownerName、capacity、time、slots
│   ├── SnapshotPolicy.java           # 纯：保留最近 N 份，哪些该删
│   ├── SnapshotScheduler.java        # 纯：待写变化的到期规则
│   ├── SnapshotFiles.java            # 纯：文件名与时间互转、避免同毫秒重名
│   ├── SnapshotListing.java          # 纯：按所有者/不活跃天数过滤、排序、分页、清理候选
│   ├── BackpackSelector.java         # 纯：4 位编号 / UUID / UUID 开头（自 admin/ 迁入）
│   ├── AgeText.java                  # 纯（自 admin/ 迁入）
│   ├── SnapshotStore.java            # 服务端：读写 NBT 文件、列出、删除
│   ├── SnapshotService.java          # 服务端：待写集合、后台线程、各个触发点
│   └── SnapshotCommands.java         # 服务端：命令
├── inventory/identity/BackpackGate.java   # 改：只分配标识 + 标记快照
├── inventory/identity/BackpackIds.java    # 改：4 位
└── MCFloatingBackpack.java                # 改：服务器启动/停止/每秒检查/退出/命令注册

删除：inventory/identity/{BackpackRegistry,RegistryRecord,RegistryIndex,NoticeLimiter,BackpackMessages}.java、admin/ 包

src/test/java/pnnh/mcfloatingbackpack/
├── snapshot/*Test.java（6 个）
├── inventory/identity/BackpackIdsTest.java            # 改：4 位
├── resources/SnapshotCommandResourcesTest.java        # 取代 AdminCommandResourcesTest
└── 删除：RegistryIndexTest、NoticeLimiterTest、admin/ 下的测试
```

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。
