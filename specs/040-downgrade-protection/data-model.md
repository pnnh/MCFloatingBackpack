# Data Model: 降级保护

## 存储

`BackpackContents`（记录，磁盘编码 `CODEC`）新增第 8 个字段：

| 字段 | 类型 | 磁盘 | 网络（`STREAM_CODEC`） |
|---|---|---|---|
| `data_revision` | int | `optionalFieldOf("data_revision", 0)` | **不带**（协议号 17 不变） |

- 缺省 0 = 没有标记的旧背包；`BackpackContents.empty(...)` 与所有现有构造（`fit`、`copy` 之类）保持 0 或沿用传入的值（保存时由 `BackpackInventory.toContents` 盖上当前修订）。
- 常量：`DataRevision.CURRENT = 1`（本 feature 的版本）。

## 修订与决策（纯逻辑）

```text
DataRevision.isNewer(stored: int) -> stored > CURRENT

Decision = ALLOW          // 没有被保护（stored <= CURRENT）
         | REFUSE_FIRST   // 被保护，明确的打开，没有窗口 → 拒绝、开窗口、给提示
         | CONFIRMED      // 被保护，明确的打开，窗口内 → 放行并盖章、清窗口
         | REFUSE         // 被保护，其它路径（或窗口外的非打开） → 静默拒绝

RevisionGuard.decide(stored, current, explicitOpen: boolean, windowOpen: boolean) -> Decision
```

决策表：

| stored > current | explicitOpen | windowOpen | 结果 |
|---|---|---|---|
| 否 | * | * | `ALLOW` |
| 是 | 否 | * | `REFUSE`（静默，不算确认） |
| 是 | 是 | 否 | `REFUSE_FIRST` |
| 是 | 是 | 是 | `CONFIRMED` |

## 确认窗口（`ProtectionWindows`，纯逻辑）

```text
key = (playerId: UUID, backpackKey: String)         // backpackKey：身份 UUID；没有身份时 "ref:<来源>:<下标>:<修订>"
open(key, nowMs)      -> 记下过期时间 now + 60_000
isOpen(key, nowMs)    -> 过期时间存在且 nowMs < 过期时间
consume(key)          -> 清除
forgetPlayer(playerId)-> 清除这位玩家的全部窗口
```

不变量：窗口只对自己的键有效（别的玩家、别的背包借不到）；过期后 `isOpen` 为假；`consume` 之后再 `isOpen` 为假；时间只由调用方传入（便于测试）。

## 日志限频

`AutoPickupEvents` 对被保护的背包的 WARN：键 = 玩家 UUID，每分钟至多一条（`ProtectionWindows` 旁的一个小的限频表，或同一个类里的 `shouldLog(playerId, nowMs)`）。

## 调试开关

`mcfloatingbackpack.debug.stampRevision`（`-Pmcfb.stampRevision=N`）：`toContents` 盖章用 N 代替 `CURRENT`（默认缺省，行为与不存在完全相同）。
