# 契约：服务端对"全部"请求的处理

065 的统一解析 `PanelTargets.resolve(player)` 在"全部"会话里**不再有单一目标**。新增两个入口：

- `PanelTargets.inAll(player)`：会话是否在"全部"。
- `AllContext.begin(player, epoch)`：校验并返回 `AllContext`（失败返回 null，并已重发"全部"，`epoch`+1）。

## 12 个面板类处理器逐个怎么办

| 处理器 | 具体标签 | "全部" |
|---|---|---|
| `BackpackClickPayload` | 不变（`epoch` 必须 0） | 只接受 `MAIN` 区；`begin` → 平铺号 `locate` → 成员 `inventory` → **借用光标** → `applyClick(... loan)`；工具区请求拒绝 |
| `BackpackCursorGesturePayload` | 不变 | 平铺号分组 → `SlotAccess` 适配 → 复用泛型 `distribute/gather` → 光标走占位容器 + `PanelCursor` → 保存动过的成员 |
| `BackpackQuickTransferPayload` | 不变 | `TO_BACKPACK`：`AllSink.insertIntoMain`（成员顺序）；`FROM_BACKPACK`：平铺号 `SlotAccess`；`protectedIndex` 传 -1（嵌套规则已使背包栈不会被放入） |
| `BackpackTakeFromContainerPayload` | 不变 | `AllSink.insertIntoMain` |
| `BackpackNetworkTakePayload` | 不变 | `AllSink.insertIntoMain`；`tryGiveBack` 等现有流程不变 |
| `MergePanelCursorPayload` | 不变 | 内存光标并入菜单携带物；不保存任何成员 |
| `BackpackTrashPayload` / `BackpackTrashSendPayload` | 不变 | **忽略**（不关闭面板） |
| `BackpackSortPayload` | 不变 | **忽略**（"全部"的整理是客户端显示） |
| `BackpackTagPayload` / `BackpackTagOverviewPayload` | 不变 | **忽略** |
| `BackpackPickupBlacklistOpPayload` | 不变 | **忽略** |

接线测试（延伸 065 的 `CombinedWiringTest`）断言：
1. 前 6 个处理器都含对 `PanelTargets.inAll` 或 `AllContext.begin` 的分支，且都在 `acquireForPanel` 之前；
2. 后 6 个处理器在"全部"里直接返回（含对 `inAll` 的判断）；
3. `AutoPickupEvents` 不再直接 `PacketDistributor`，改走 `BackpackNetwork.sendPickupUpdate`；
4. 065 的"非面板调用仍用 `BackpackRef.resolve`"清单不变。

## `AllContext.begin` 的校验（R8、R9）

1. 联合背包仍穿着、`enabled`、会话在 `ALL`；
2. `payload.epoch == session.all.epoch`；
3. 对**本请求涉及**的每个成员：引用处仍是浮动背包、身份等于快照身份、档位不超、不在保护中（O(1)/成员）；
4. 平铺号在范围内（`AllAddressing.locate` 有值）。

任何一条不满足 → 拒绝：不动任何物品，`epoch`+1，冲洗光标，重发成员表与"全部"内容。

## 一个请求里的保存与同步

`AllContext` 记录被动过的成员；`finish()`：按成员顺序对**动过的**逐个 `ref.saveInventory`（各自走快照通知），然后对动过的成员发 `CombinedMemberSyncPayload`（多格改动）或 `CombinedCellUpdatePayload`（单格），光标随包带上。同一请求内每成员最多解码一次。成员背包的存档里**永远**不含内存光标（借用的光标在保存前取回，见 R6）。

## 光标冲洗 `CombinedSessions.flush(player)`

触发点（R10）：关闭联合面板、B 键打开、切标签（从"全部"出去）、成员表/大小/可见行变化、`enabled=false`、下线、停服、请求被拒后重同步。

顺序：成员（按**会话快照**的顺序，逐个核对身份，对不上跳过）`insertIntoMain` → 玩家物品栏（`ContainerMenuAccess.fallback().toInventory`）→ 丢在脚下（`fallback().drop`）。总数守恒（纯逻辑 `CursorFlush` 可单测）；有东西没放回成员时给动作栏提示（`message.mcfloatingbackpack.combined.flushed`）并写 `DiagLog`。

## 每秒钩子（`CombinedSessions.tick`，仅"全部"会话）

重算成员表；若成员列表 / 任一成员主区域长度 / 可见行分类有变 → 冲洗光标、`epoch`+1、重建 `AllState`、重发成员表与全部成员同步包；否则对每个成员做 `AllContentDiff`，变了的发 `CombinedMemberSyncPayload`。

## 进入"全部"

`CombinedSessions.activateAll`：对成员表里每个成员 `BackpackGate.acquire`（给没身份的分配身份；失败的从本次成员表去掉并重算）→ 建 `AllState(epoch = 上一 epoch + 1)` → 发 `CombinedMembersPayload(all = true)`（成员表在前）→ 按成员顺序发满成员同步包。`BackpackGate.noteOpened` 不调用（"全部"没有单一"面板打开的身份"）；`tickOpenSession` 仍先问 `CombinedSessions.tick`，有会话就不走单背包的过期检查。
