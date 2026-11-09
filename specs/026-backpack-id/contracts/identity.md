# Contract: 背包编号、登记表与可用性检查

## 1. 内部标识的来源

| 来源 | 什么时候有标识 |
|---|---|
| 工作台合成皮革背包 | 合成的那一刻：配方类型 `mcfloatingbackpack:backpack_crafting`（`ShapedRecipe` 子类）在 `assemble` 里盖 `BackpackIdentity(UUID.randomUUID())`；图案与材料不变；Shift 点击一次合成多只时每次 `assemble` 各盖一个 |
| 创造栏、`/give`、旧存档 | 出现时没有；第一次经过 `BackpackGate.acquire` 时分配 |
| 复制（创造模式中键等） | 复制出的栈带着同一个标识 |
| 升级（锻造台） | `transmuteCopy` 保留全部数据组件，标识不变 |

## 2. 可用性检查 `BackpackGate.acquire(ServerPlayer, BackpackRef)`

返回 `Optional<BackpackInventory>`；**空 = 拒绝**，调用方按各自"非法请求"路径提前返回（不改背包内容）。流程见 data-model.md 第 5 节。

**入口**（全部从 `ref.loadInventory(player)` 换成 `BackpackGate.acquire(player, ref)`）：`BackpackOpenRequestPayload`、`BackpackClickPayload`、`BackpackQuickTransferPayload`、`BackpackTakeFromContainerPayload`、`MergePanelCursorPayload`、`BackpackCursorGesturePayload`、`BackpackTagPayload`、`BackpackTagOverviewPayload`、`BackpackPickupBlacklistOpPayload`、`BackpackTrashPayload`、`BackpackTrashSendPayload`，以及自动拾取 `AutoPickupEvents`。自动拾取在 gate 拒绝时不接管，拾取按原版进物品栏。`BackpackNetwork` 里 3 处只读下发（`sendSession`、`sendFullSync`、`sendSlotUpdate`）仍用 `loadInventory`，不改数据。

## 3. 保存 `BackpackRef.saveInventory`

`saveToItem(stack)` 之后、`setStack` 之前调 `BackpackGate.afterSave(player, stack)`（仅服务端）。影子副本与栈共用同一个 `BackpackContents` 对象，保存后不得就地修改。

## 4. 显示

- **标题栏**：`FloatingPanelRenderer.drawTitleIcon` 画完 16×16 的等级图标后，在其右侧 3 像素处画 `identity.id()`（客户端栈上的标识算出）；可用宽度到关闭按钮左缘 − 2，超出 `plainSubstrByWidth` 截断，不遮挡关闭按钮；悬停该区域（`FloatingPanelState.hitsTitleId`）显示完整编号（`panel.mcfloatingbackpack.id.tooltip`）；图标悬停仍是等级名称。
- **物品提示**：有标识 → 灰色一行 `tooltip.mcfloatingbackpack.id`（"编号：%s" / "ID: %s"）；没有 → `tooltip.mcfloatingbackpack.id.none`（"未登记（首次使用时登记）" / "Unregistered (registered on first use)"）。

## 5. 语言键

`tooltip.mcfloatingbackpack.id`、`tooltip.mcfloatingbackpack.id.none`、`panel.mcfloatingbackpack.id.tooltip`、`message.mcfloatingbackpack.id.tombstone`（"这个背包已被管理员删除" / "This backpack was deleted by an administrator"）。最初版本的 `id.limit`、`id.limit.craft`、`id.no_id`、`id.duplicate`、`id.readopt_changed`、`id.pickup_blocked(.owner)`、`id.claim_limit` 与 `config.mcfloatingbackpack.maxBackpacksPerPlayer` 已删除（`BackpackIdResourcesTest` 检查它们不在）。

## 6. 协议

`BackpackNetwork` 的版本与 027 一起升到 `"15"`；身份组件的网络编码是 16 字节 UUID。
