# Research: 创造物品栏界面里的 Ctrl 快速转移

读 NeoForge 21.1.251 反编译源码与本仓库源码（反编译源码解在 `/tmp/mcsrc`，不入库）。**"已读到"是事实，"推断"是没有在游戏里验证的结论。**

## 已读到的事实

| # | 事实 | 出处 |
|---|---|---|
| F1 | 物品栏标签页：`for (int k = 0; k < inventoryMenu.slots.size(); k++) { new SlotWrapper(inventoryMenu.slots.get(k), k, x, y) }`；`SlotWrapper(Slot slot, int index, …)` 调 `super(slot.container, index, x, y)`，而 `Slot.getContainerSlot()` 返回构造时的第二个参数，所以是**菜单序号 k** | `CreativeModeInventoryScreen.selectTab`、`SlotWrapper`、`Slot` |
| F2 | 玩家 `inventoryMenu` 的菜单序号：0 合成结果、1～4 合成格、5～8 护甲、9～35 主物品栏、36～44 快捷栏、45 副手；`inventoryMenu.getSlot(k).getContainerSlot()` 给真实的物品栏下标（快捷栏 36→0） | `InventoryMenu`、同上 |
| F3 | `SlotWrapper.container` 就是被包装的格子的容器，所以物品栏、快捷栏、护甲、副手的包装格满足 `slot.container == player.getInventory()`；合成格的容器是 `CraftingContainer` | 同上 |
| F4 | 物品标签页：玩家物品栏只显示快捷栏，是真格子（`Slot(player.getInventory(), i, …)`）；其余格子是创造物品列表，容器是创造界面自己的 | `ItemPickerMenu` |
| F5 | `CreativeModeInventoryScreen.isInventoryOpen()` = 当前标签页是物品栏标签页 | 同上 |
| F6 | 客户端 `handleContainerContent`：容器 0 的整份同步**无条件**调 `player.inventoryMenu.initializeContents(stateId, items, carried)`；`handleContainerSetSlot` 单格更新在创造界面非物品栏标签页下对非快捷栏格子忽略 | `ClientPacketListener` |
| F7 | `AbstractContainerMenu.sendAllDataToRemote()` 把整份格子与光标发给客户端（`ClientboundContainerSetContentPacket`） | `AbstractContainerMenu` |
| F8 | 011 的客户端：`own = slot.container == player.getInventory()`，真实下标用 `slot.getContainerSlot()`；非 own 的格子在"容器已打开"时当容器来源（`Side.CONTAINER`）；`supports(screen)` 用 `isInterceptableScreenClass` 排除创造界面 | `QuickTransferInput`、`QuickTransferRules` |
| F9 | 011 的服务端：`BackpackQuickTransferPayload` 要求服务端光标为空，按真实下标操作 `player.getInventory()`；面板 → 物品栏方向用 `ContainerMenuAccess.hasOpenContainer` 判断有没有打开的容器，创造界面时没有 | `BackpackQuickTransferPayload` |
| F10 | `Ctrl+点击面板格子`（`beginFromPanel`）不检查屏幕类型，所以面板 → 物品栏方向在创造界面里本来就能发出；缺的是物品栏 → 面板方向与转移后的显示 | `QuickTransferInput` |

## Decisions

### D1. 客户端换算下标，服务端协议不变

**决定**：物品栏标签页用 `inventoryMenu.getSlot(k).getContainerSlot()` 换算，物品标签页的快捷栏直接用；服务端照旧收真实下标。**理由**：F1、F2、F9；服务端不需要知道创造界面。**弃**：给服务端发菜单序号让它换算——要改协议且服务端要识别客户端界面。**弃**：反射读 `SlotWrapper.target`——类不是公开的，而换算用公开 API 就够了。

### D2. 创造物品列表不是来源

**决定**：创造界面里非自己物品栏的格子一律返回 null（不当成容器来源）。**理由**：它们是虚拟物品；当成 `Side.CONTAINER` 会向服务端发一个不存在的菜单格子。把它们 Ctrl+点击转进面板是另一件事（要服务端接受虚拟物品），不在本 feature。

### D3. 转移后服务端同步整份物品栏

**决定**：`BackpackQuickTransferPayload` 处理完后，对创造模式且 `containerMenu == inventoryMenu` 的玩家调 `inventoryMenu.sendAllDataToRemote()`。**理由**：F6、F7：物品标签页下客户端会忽略单格更新，转移进不可见的物品栏格子后客户端显示是旧的，切到物品栏标签页看到旧内容，之后在那里搬动会把旧内容写回服务端；整份同步不被忽略。携带的光标为空（转移的前提：服务端光标为空，027 之后与客户端一致）。**弃**：只在客户端切换标签页时请求同步——要新增网络包与时机判断。**弃**：对所有服务端库存修改都同步——范围太大，本 feature 只管转移。

### D4. 不引入新网络包，协议不变

沿用 `BackpackQuickTransferPayload`；整份同步用原版的包。协议仍是 "15"。

## 待验证

| # | 内容 |
|---|---|
| U1 | F1 的换算：物品栏标签页里点快捷栏第 0 格与某个主物品栏格子，转进面板的是对的那一格 |
| U2 | 物品标签页下面板 → 物品栏转进不可见格子，切到物品栏标签页看到的是真实内容 |
| U3 | 转移后在创造界面里搬动其它格子，转移过的格子不被覆盖 |
| U4 | Ctrl+拖动与 014 选区 + Ctrl 在创造界面里可用 |
| U5 | 创造物品列表里的 Ctrl+点击保持原版行为 |
