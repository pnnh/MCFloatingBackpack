# 契约：面板目标解析

## 规则

1. **面板类请求**（R5 的 13 处）取背包只许用 `PanelTargets.resolve(player)`；`BackpackRef.resolve(player)` 只留给"B 会打开的那只"的语义（B 键、自动拾取、背部渲染、移动工具、唱片机、灯笼、管子、诊断）。
2. `PanelTargets.resolve` 在联合会话外**完全等于** `BackpackRef.resolve`——没穿联合背包的玩家行为与现在逐字节一致。
3. 联合会话内，返回会话目标（`INVENTORY`/`OFF_HAND`/穿戴来源引用，**不会是 `MAIN_HAND`**）；失效时退回 `BackpackRef.resolve`，由既有的过期保护拒绝并自愈。
4. `BackpackClickPayload` 的"声称引用"：联合会话内与目标不等 → 拒绝并 `resync`；会话外保持旧行为。
5. `resync`（`BackpackGate`）在联合会话内先 `CombinedSessions.recover`。

## 逐处检查清单（tasks 里逐项勾）

对每个面板类处理器，读到"手里的背包"或"主手"假设时核对：

- `BackpackClickPayload`：`nestingHeld` 的"手里"表达式、Alt 交换主手（014）——目标可能不是手里那只；064 的嵌套规则已保证主手背包放不进目标。
- `BackpackQuickTransferPayload`：`holderOf(ref)` 对 `INVENTORY` 来源与 `protectedInventoryIndex` 的组合（保护目标自己所在的物品栏格，不能被"整理进自己"）。
- `BackpackTakeFromContainerPayload` / `BackpackNetworkTakePayload`：容器 → 目标背包，目标不是手里那只时无差别。
- `BackpackTrashPayload` / `BackpackTrashSendPayload`：垃圾桶页属于目标背包，`tell`/还原都按目标。
- `BackpackSortPayload` / `BackpackTagPayload` / `BackpackTagOverviewPayload` / `BackpackPickupBlacklistOpPayload`：按目标背包的数据。
- `snapshot/net/*`：快照窗口按"面板当前背包"还是"B 的那只"——读码后归类；若按面板背包则同样改用 `PanelTargets`（若归为 B 的那只，则文档注明并在联合界面里对非激活标签灰掉快照按钮——决策放 tasks 的核对结果里）。
- `CreativeCursor*`：只传光标，不涉及背包选择，预期不用改。

## 接线测试（`CombinedWiringTest`，源码文本断言）

- 上列 13 个文件含 `PanelTargets.resolve(` 且不含 `BackpackRef.resolve(`。
- `BackpackOpenRequestPayload`、`AutoPickupEvents`、`MobileTools`、`MobileFurnaceStore`、`MobileFurnaceMenu`、`PipeActions`、`PlayerLights`、`BackpackWatchService`、`JukeboxPlayback` 仍含 `BackpackRef.resolve(`。
- `BackpackNetwork` 含 `versioned("23")` 与两个新包的注册。
- `BackpackGate` 的关闭路径调用 `CombinedSessions`；`MCFloatingBackpack` 的下线/停服监听清理 `CombinedSessions`。
