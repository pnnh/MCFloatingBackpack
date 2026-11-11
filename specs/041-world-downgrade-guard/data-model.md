# Data Model: 存档级降级拦截

## WorldMarker（存档里的标记）

| 字段 | 类型 | 含义 |
|---|---|---|
| `dataRevision` | int ≥ 0 | 这个存档被**至多哪个数据修订**的版本碰过。**唯一参与判断的字段** |
| `modVersion` | String（可空） | 最后一次写标记的模组版本，只用于错误信息；缺失不影响 |

磁盘文本（`<世界根目录>/mcfloatingbackpack/world_marker.properties`）：

```
# MCFloatingBackpack world marker. Delete this file, or lower data_revision, to accept the downgrade risk.
data_revision=1
mod_version=0.1.0
```

- 解析：`java.util.Properties`；`data_revision` 缺失、不是整数、为负 → 整个文件按"损坏"处理（`mod_version` 即使有也不用）。
- 序列化：固定三行（一行注释、`data_revision`、`mod_version`）；`mod_version` 为空时省略那一行。

## MarkerRead（读文件的结果）

`MISSING`（没有文件）、`VALID(WorldMarker)`、`CORRUPT(原因)`（解析失败，或文件在但读不了）。纯数据，无 Minecraft 类型。

## Decision（决策结果）

输入：`MarkerRead`、当前修订 `current`（`DataRevision.CURRENT`）、`accept`（是否带放行参数）。

| MarkerRead | accept | Decision | 要写标记 | 日志 |
|---|---|---|---|---|
| `MISSING` | 任意 | `PASS_AND_WRITE` | 写 `current` | INFO |
| `CORRUPT` | 任意 | `PASS_AND_WRITE` | 写 `current` | WARN（原因） |
| `VALID`，`revision < current` | 任意 | `PASS_AND_WRITE` | 写 `current` | INFO |
| `VALID`，`revision == current` | 任意 | `PASS` | 仅 `modVersion` 与当前不同才重写 | 无 |
| `VALID`，`revision > current` | `false` | `REFUSE` | **不写** | ERROR + 崩溃报告 |
| `VALID`，`revision > current` | `true` | `ACCEPTED` | **不写** | WARN |

- "要写的修订"通常是 `current`；开发开关 `worldMarkerRevision=N` 把它换成 N，且**只在 N 大于现有标记时写**（只升不降）。比较用的仍是真实的 `current`。
- 每次启动最多做一次决策与（至多）一次写。

## 状态与生命周期

无内存状态：每次服务器启动做一次"读 → 决策 → 写或拒"，之后不再触碰标记。没有表、没有窗口、没有玩家相关状态。
