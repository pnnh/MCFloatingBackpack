# Contract: "放进垃圾桶"请求、协议 10 与标签请求的新拒绝

## 1. 协议版本

`BackpackNetwork`：`versioned("9")` → `versioned("10")`。

| 包 | 方向 | 变化 |
|---|---|---|
| `BackpackTrashSendPayload(source, confirmed, containerId, slots)` | 客户端 → 服务端 | **新增** |
| `BackpackTagPayload` | 客户端 → 服务端 | 线上格式不变；服务端多一条拒绝（第 4 节） |
| `BackpackTrashPayload`、`BackpackTrashContentsPayload` | — | 不变 |
| 其余全部包 | — | 不变 |

`BackpackContents` 不再改（022 没有新的持久数据）。

## 2. 请求 `BackpackTrashSendPayload`

字段：`source`（字节：DUMP=0、MAIN_SLOTS=1、MENU_SLOT=2）、`confirmed`（布尔）、`containerId`（VarInt，仅 MENU_SLOT 有意义）、`slots`（VarInt 列表；解码时数量 > 108 抛 `DecoderException`）。

服务端（`handleServer`），按顺序，任一步失败则**整个请求忽略、背包与来源菜单零变化**；忽略时仍补发垃圾桶内容与光标/整份同步，让客户端显示追上真相：

1. 请求者是 `ServerPlayer`；`BackpackRef.resolve` 得到背包且 `stillValid`，否则 `sendSession(false)`。
2. 右侧区里有垃圾桶（`BackpackInventory.hasTrashCan()`）。
3. `TrashSendRequest.validate(source, slots, containerId, 主格子数)`。
4. 确定**要放入的栈列表**（有序，每个栈一组）：
   - **DUMP**：`TrashDump.select(...)`（data-model 第 2 节）选出主区域下标，取这些格子里的栈。
   - **MAIN_SLOTS**：下标升序，取其中**非空**格子的栈（空格子跳过）。
   - **MENU_SLOT**：`containerId` 等于 `player.containerMenu.containerId`；菜单是 `player.inventoryMenu` 或 `ChestMenu`；`MenuOwner.ownerOf(menu)` 是 `minecraft`；`ServerCompatGate.allows(Action.TAKE_FROM_CONTAINER, owner)`；菜单格子号 ∈ `[0, menu.slots.size())`；`DeletableSlots.allowed(...)`（由 `slot.container` 推出 `ownInventory`/`chestOwnSlot`）；`slot.hasItem()` 且 `slot.mayPickup(player)`；取这一个栈。
   - 列表为空 → 忽略（DUMP 时客户端已经提示过"没有可倾倒的物品"）。
5. `destroyed = TrashDeletePlan.destroyed(现有组数, 列表长度)`；`needsConfirm(destroyed)` 且 `!confirmed` → 忽略，并给玩家一条 `message.mcfloatingbackpack.trash.changed`。
6. **体积保护**：对列表里每个栈用 `ItemStack.OPTIONAL_STREAM_CODEC` 试编码，任何一个超过 `TrashContents.MAX_GROUP_ENCODED_BYTES` → 忽略并给 `message.mcfloatingbackpack.trash.too_large`。
7. 执行：按顺序对每个栈 `TrashBin.push(ItemStackTransferOps.INSTANCE, ...)`，被挤出的各写一行调试日志 `Trash can destroyed {item} x{count} (player {name})`；来源取走：主区域格子置空；菜单格子 `slot.remove(count)` + `slot.setChanged()`。
8. 守恒核对：操作前后"垃圾桶 + 主区域 + 来源菜单（仅 MENU_SLOT）"的物品总数，除被销毁的外必须相等；不相等记 `LOGGER.error`。
9. `ref.saveInventory`；MENU_SLOT 时 `menu.broadcastChanges()`。
10. 发送：`BackpackTrashContentsPayload(整份)`、`sendFullSync`（主区域变了）、`sendSlotUpdate(player, ref, -1)`。

客户端在发请求之前会做同样的筛选与确认判断，但那只是体验，服务端才是权威。

## 3. 客户端发送时机

- 倾倒图标：`TrashDump.select`（读客户端视图）数出组数 → 0 组则聊天提示不发；`destroyed > 9` → 弹确认；否则发 `DUMP`。
- 删除图标 / Delete（`MAIN_SELECTION`）：取选区里**有物品**的格子下标快照 → 同上判断 → 发 `MAIN_SLOTS`，**发出时清选区**（确认路径在点"确认"时清）。
- Delete（`MAIN_HOVER`）：发 `MAIN_SLOTS`（一个下标）。
- Delete（`MENU_SLOT`）：发 `MENU_SLOT`（`containerId = menu.containerId`，悬停格子的 `slot.index`）。
- 点"确认"：发同一个请求，`confirmed = true`。

## 4. 标签请求的新拒绝（`BackpackTagPayload`）

在现有校验（背包、功能区有标签管理器、`TagRequest.validate`、解析分类）之后、`TagBook` 修改之前：

- 若 `op == ADD` 且 `TagProtection.blocks(payload.tag(), 受保护分类数)` → **整个请求忽略、数据零变化**，并补发一次 `BackpackTagsPayload`。
- "受保护分类"：被选中的非空格子里，物品满足 `stack.is(ModTags.NEVER_TRASH)` 的**不同分类**数。
- 其它标签、`REMOVE` 不受影响；已经带 `trash` 的受保护分类可以被 `REMOVE`。

## 5. 不变的服务端逻辑

011～021 的转入/转出、点击、放入规则、拖动手势、选区、021 的垃圾桶请求、兼容门禁**一行不改**。
