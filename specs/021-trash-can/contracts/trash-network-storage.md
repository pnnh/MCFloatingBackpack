# Contract: 垃圾桶的存储、网络与放入规则

## 1. 数据组件

`BackpackContents` 的存盘编码增加：

```text
{ "items": [...], "cursor": {...}?, "tools": {...}?, "tags": {...}?, "trash": [ {stack}, ... ]? }
```

- `trash` 可选，缺省为空；021 之前保存的背包读成空，其余字段不变。
- 读入时：丢弃空栈，超过 9 个只保留前 9 个（下标 0 是最新）。
- `fit(capacity)`、升级配方、复制都保留 `trash`（与 `tags` 同路）。
- 垃圾桶**物品**不持有内容；物品被取走、被销毁，内容都留在背包里。

`BackpackContents.STREAM_CODEC`（物品栈同步）增加 `TrashContents`：字段数 4 → 5，不超过 `composite` 的 6 个上限。

## 2. 协议版本

`BackpackNetwork`：`versioned("8")` → `versioned("9")`。

| 包 | 方向 | 变化 |
|---|---|---|
| `BackpackTrashPayload(action, slots, screenOpen)` | 客户端 → 服务端 | **新增** |
| `BackpackTrashContentsPayload(TrashContents)` | 服务端 → 客户端 | **新增** |
| `BackpackContents` 的物品栈同步编码 | 双向 | 增加 `TrashContents` |
| 其余全部包 | — | 不变 |

## 3. 请求 `BackpackTrashPayload`

字段：`action`（字节：ADD=0、TAKE=1、RESTORE=2）、`slots`（VarInt 列表，≤ 9，解码超限抛 `DecoderException`）、`screenOpen`（布尔，同 `BackpackClickPayload` 的含义：客户端报告当前是否有可用的容器界面，服务端据此选择光标来源）。

服务端（`handleServer`），按顺序，任一步失败则**整个请求忽略、背包数据零变化**（忽略时仍补发一次垃圾桶内容与光标同步，让客户端显示追上真相）：

1. 请求者是 `ServerPlayer`；`BackpackRef.resolve` 得到背包且 `stillValid`，否则 `sendSession(false)`。
2. **右侧区里有垃圾桶**（`BackpackInventory.hasTrashCan()`，按右侧区整个列表）；没有则忽略。
3. `TrashRequest.validate(action, slots)`。
4. ADD / TAKE 才用光标：
   - `viaMenu = screenOpen && player.containerMenu != null`；`viaMenu` 时先过 `ServerCompatGate.allows(Action.USE_MENU_CURSOR, MenuOwner.ownerOf(menu))`，不过则忽略；再 `BackpackMenuBridge.mergePanelCursorIntoCarried(backpack, menu)`。
   - 光标 = `PanelCursor.of(screenOpen, menu, backpack)`。
5. **ADD**：光标为空 → 忽略。体积保护：把光标栈用 `ItemStack.OPTIONAL_STREAM_CODEC` 试编码，字节数 > `MAX_GROUP_ENCODED_BYTES` → 忽略并给玩家一条聊天提示（`message.mcfloatingbackpack.trash.too_large`）。否则 `TrashBin.push`，光标置空，`destroyed` 非空则写调试日志。
   **TAKE**：光标非空 → 忽略。`TrashBin.take`；没有那一组 → 忽略；否则光标 = 被拿起的那一组。
   **RESTORE**：`TrashBin.restore` 以主区域 `0..size-1` 为目标顺序；全部下标都没有组 → 忽略。
6. 守恒核对：操作前后"垃圾桶 + 光标 + 主区域"里的物品总数，除 `destroyed` 外必须相等；不相等记 `LOGGER.error`（与 `BackpackCursorGesturePayload` 同样的做法）。
7. `ref.saveInventory`；`viaMenu` 时 `menu.broadcastChanges()`。
8. 发送：`BackpackTrashContentsPayload(整份)`；ADD/TAKE 再发光标同步（`sendSlotUpdate(player, ref, -1)`）；RESTORE 再发 `sendFullSync`。

客户端发出请求前也会做大部分检查（光标空不发 ADD 等），但那只是体验，服务端才是权威。

## 4. 同步 `BackpackTrashContentsPayload`

整份 `TrashContents`（≤ 9 组）。发送时机：每次 `sendSession(open)` 之后（紧跟标签包）；每次垃圾桶内容变化之后；忽略请求时的补发。客户端 `ClientBackpackView.setTrash(...)`，面板下一帧按新数据重画。`STREAM_CODEC` 解码超过 9 组抛 `DecoderException`。

## 5. 放入规则（`BackpackClickPayload`）

调用点不变（`area.isTool()` 且手里有东西时），签名换成：

```text
ToolAreaRules.canPlace(area, held, slotIndex, areaSlots, isTrashCan, isTagManager)
```

`held`：菜单分支 = 菜单携带物（为空则面板光标），否则 Alt 交换主手 = 主手物品，否则面板光标（沿用现有的取法）。`areaSlots` = 该区域**整个**存储列表。拒绝时沿用现有行为：重发该格的更新、不改变任何东西。规则本身见 [data-model.md](../data-model.md) 第 4 节。

## 6. 不变的服务端逻辑

011～020 的转入/转出、点击（除放入规则外）、拖动手势、选区、标签请求、兼容门禁**一行不改**；垃圾桶作为普通物品在任何区域、任何转移途径下都照常移动（只有"右侧区里放第二个"被点击放入规则拒绝）。
