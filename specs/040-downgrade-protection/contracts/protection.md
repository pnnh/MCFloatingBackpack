# Contract: 保护

## 入口

| 入口 | 谁用 | 被保护时 | 能确认？ |
|---|---|---|---|
| `BackpackGate.acquireForOpen(player, ref)` | **只有** `BackpackOpenRequestPayload` | 第一次：拒绝 + 聊天提示 + 开 60 秒窗口；窗口内：放行并立即盖章为当前修订、清窗口 | 能 |
| `BackpackGate.acquireForPanel(player, ref)` | 面板的十个请求处理器 | 静默返回空（面板本来就没有打开；伪造或过期的请求也不能改写） | 不能 |
| `BackpackGate.acquire(player, ref)` | 自动拾取 | 静默返回空；`AutoPickupEvents` 记限频 WARN | 不能 |

所有入口 **先读栈上的修订，再做任何会写背包的事**：被保护时**不分配身份、不写回栈、不通知快照服务**。

## 被保护的判定

只读 `stack.get(ModDataComponents.BACKPACK_CONTENTS)` 的 `dataRevision`；`> DataRevision.CURRENT` 即被保护；没有组件或没有字段读作 0。

## 路径一览

| 路径 | 受保护？ | 说明 |
|---|---|---|
| 打开面板 | 是（两步确认） | 见上 |
| 面板的点击、转移、标签、垃圾桶、光标手势、合并光标、黑名单、取容器物品 | 是（静默拒绝） | 面板没有打开时它们不会发生；收到也拒绝 |
| 自动拾取 | 是（跳过，限频 WARN） | 物品走原版流程，背包不被改动 |
| 锻造台升级 | 是（配方不匹配） | `BackpackUpgradeRecipe.matches` 看基础背包的修订 |
| 丢弃、放进箱子、交易、放进工具区或主区域、创造复制 | 否 | 不解码也不保存内容；复制的背包带着同样的修订 |
| 快照服务的记录 | 否（只读） | 被保护的背包不触发 `noteUse`（在入口里先返回） |

## 保存

`BackpackInventory.toContents` 盖上 `DataRevision.CURRENT`（调试开关 `stampRevision` 可覆盖）。被保护且未确认的背包走不到保存；确认时已经盖章。

## 不变

协议号 17、现有网络包、`BackpackContents.STREAM_CODEC`、快照、配置与命令都不变。
