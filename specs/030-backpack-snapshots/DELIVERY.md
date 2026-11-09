# Delivery: 背包快照（去掉登记表）

**日期**: 2026-11-09 | **状态**: 代码与自动化检查完成；T020～T022 用户已验证；**T023（回档）未做**；没有提交 Git。

## 这个 feature 是什么、为什么改方向

030 最初是"登记表管理员命令"，已实现但没提交、没验证。同一天分析之后，用户决定**整体改方向**（推翻了 026 精简版里保留的登记表）：

- 原版与 NeoForge 都没有全局物品索引，登记表不知道箱子、未加载区块里的背包；
- 它在世界目录里，回档时跟着回去，对"回档后找回背包"没有帮助，还让存档翻倍；
- 管理员的"删除/恢复"都要先找到背包，找得到的范围玩家自己就能处理；墓碑、待办标记会让用户困惑。

所以：**去掉登记表、影子副本、墓碑**；**在世界之外存单个背包的快照**；命令只管快照；**编号改 4 位**。详见 [spec.md](./spec.md) 与 `docs/plan/backup-admin-roadmap.md`。

## 做了什么

- 删除：`BackpackRegistry`、`RegistryRecord`、`RegistryIndex`、`NoticeLimiter`、`BackpackMessages`、`admin/` 包、相关测试与墓碑的语言键。
- 新增 `snapshot/` 包：纯逻辑 `SnapshotMeta`、`SnapshotPolicy`、`SnapshotFiles`、`SnapshotScheduler`、`SnapshotListing`、`BackpackSelector`、`AgeText`；服务端 `SnapshotStore`（NBT 文件）、`SnapshotService`（待写集合、后台线程、去重）、`SnapshotCommands`。
- `BackpackGate`：只分配标识，并通知快照服务（本次运行里第一次使用 + 每次保存）；不再拒绝任何请求。
- `MCFloatingBackpack`：服务器启动/停止、每秒检查、玩家退出写出、命令注册。
- `BackpackIds`：4 位（24×32³ = 786,432 种）。
- 语言键：`command.mcfloatingbackpack.*` 重写（25 个，中英文，英文后备与语言文件一致由测试保证）；`tooltip.mcfloatingbackpack.id.none` 改成"还没有编号（首次使用时分配）"。

## 自动化结果

| 项 | 结果 |
|---|---|
| `./gradlew compileJava` | 通过（`NbtIo`、`getServerDirectory()` 的签名与预期一致） |
| `./gradlew test` | 93 个类、655 个用例，0 失败 |
| T026 静态检查 | `grep BackpackRegistry\|RegistryRecord\|RegistryIndex\|tombstone\|NoticeLimiter\|BackpackMessages` 只剩 `BackpackIdResourcesTest` 里"已删除的键不应存在"的断言 |
| 图集度量（T025） | 2048×1024，通过（用户关闭游戏后重跑；本 feature 不新增贴图） |
| 专用服务器冒烟 | **没有做**：专用服务器需要 `eula.txt` 写入 `eula=true`，那是你对 Mojang EULA 的决定，我不替你做 |

## 验证状态

| 任务 | 内容 | 状态 |
|---|---|---|
| T020 | 快照写出、去重、保留 5 份、退出时写出、基线快照、日志与卡顿（A1～A7） | 用户验证，没有问题 |
| T021 | 命令、权限、恢复为新背包 | 用户验证，没有问题（非 op 与专用服务器控制台是否单独验证未说明，按"未验证"记） |
| T022 | 4 位编号显示 | 用户验证，没有问题 |
| T023 | 回档后快照仍在 | 未验证 |

步骤见 [quickstart.md](./quickstart.md)；清单见 `docs/plan/manual-verification-checklist.md` 的 030-A～E。验证前需要**重启客户端**（旧进程还是旧代码）。

## 已知限制与取舍

- **快照目录固定**（`<实例目录>/mcfloatingbackpack/snapshots/<世界文件夹名>/`），没有配置；只持久化世界目录的托管环境里，实例目录可能随重启丢失。
- **崩溃时最近 10 秒～5 分钟的变化可能没有快照**；退出、停服、命令执行前会写出。
- 去重用内容哈希（`CompoundTag.hashCode()`），32 位，理论上有极小概率误判为"没变"而少写一份，对备份可以接受。
- 旧世界里遗留的登记表存档文件（`data/mcfloatingbackpack_registry.dat`）不再读取，也不删除。
- 未解决的上游事项（与本 feature 无关）：026 T022 回档验证与成本粗测、027 的联机验证、ESC 菜单掉帧原因。

## 对其它 feature 的影响

- 026：登记表部分被取代（`specs/026-backpack-id/spec.md` 末尾已注明）；标识、编号算法、配方、标题栏显示不变；编号由 3 位变 4 位。
- 031（原"登记表备份"）、原管理员面板、玩家命令：**取消/暂缓**，见 roadmap。

## 验证中发现并修复的缺陷

- `/mcfb snapshot info <编号>` 报"TranslatableContents' arguments must be either a Component, Number, Boolean, or a String. Was given <UUID>"：`info.head` 把 `UUID` 对象直接当参数。已改为字符串，并让 `msg()` 把任何其它类型的参数自动转成字符串，防止同类错误。其余 24 条消息的参数类型已逐条核对。用户重新验证该命令前，这一项仍待确认。

## 验证后的改动：目录名带编号

用户验证 T020～T022 后提出：存档目录用的是背包 UUID，玩家在游戏里看不到 UUID，没法对照着游戏里的背包操作快照文件。现在目录名是 `<UUID>-<4位编号>`；程序只认开头的 UUID，分割线后面的内容忽略；旧的纯 UUID 目录仍认，并在这只背包下次写快照时改名。自动化：`SnapshotFilesTest` 新增 3 个用例，全部 93 个类、658 个用例通过。**客户端验证（quickstart E）未做。**
