# Contract: 创造光标往返

## 1. 客户端报告 `CreativeCursorReportPayload`（客户端 → 服务端）

```text
字段: ItemStack cursor   (ItemStack.OPTIONAL_STREAM_CODEC，空栈合法)
```

**何时发**（`CreativeCursorClient.flush()`）：当前界面是 `CreativeModeInventoryScreen`、面板可见、`player.isCreative()`，且 `mirror.shouldSend(player.inventoryMenu.getCarried())` 为真。触发点：

1. `ScreenEvent.MouseButtonPressed.Pre`、`MouseButtonReleased.Pre`、`KeyPressed.Pre`（早于界面自己的处理）；
2. 三处客户端发起光标请求之前：面板点击（`FloatingPanelInput.sendClick`）、拖动与聚拢（`PanelDragInput`）、垃圾桶（`TrashPanelInput`）、"合并面板光标"（`FloatingPanelEvents` 里发 `MergePanelCursorPayload` 之前）。

**服务端处理**（`CreativeCursorBridge.onReport`，主线程）：

```text
verdict = CreativeCursorRules.check(player.isCreative(), player.containerMenu == player.inventoryMenu,
                                    stack.isEmpty(), stack.getCount(), stack.getMaxStackSize(),
                                    stack.isItemEnabled(level.enabledFeatures()))
ACCEPT -> player.inventoryMenu.setCarried(stack.copy()); mirror(player).received(stack)
其它   -> 不改任何东西；DEBUG 日志一行（玩家、原因）
```

不回应、不踢人（与创造模式物品包的原版做法一致）。

## 2. 服务端回写 `CreativeCursorPushPayload`（服务端 → 客户端）

```text
字段: ItemStack cursor   (ItemStack.OPTIONAL_STREAM_CODEC，空栈合法)
```

**何时发**（`CreativeCursorBridge.pushIfChanged(player)`，由 `BackpackNetwork` 的 `sendSlotUpdate` / `sendFullSync` / `sendTrash` 调用）：

```text
player 是创造模式 且 player.containerMenu == player.inventoryMenu 且 mirror(player) 存在
  且 mirror.shouldSend(menu.getCarried())
-> 发 Push(menu.getCarried()); mirror.sent(...)
```

**客户端处理**（`CreativeCursorClient.onPush`）：`Minecraft.screen instanceof CreativeModeInventoryScreen` 时
`player.inventoryMenu.setCarried(stack.copy()); mirror.received(stack)`；创造界面已经关了就丢弃。

## 3. 关界面

- 客户端：`KeyPressed.Pre`（E、ESC）里先 `flush()`，所以最后一次光标已在关闭包之前送达。
- 服务端：原版 `removed()` 把 `inventoryMenu` 光标放回玩家物品栏（放不下掉落）并清空；`PlayerContainerEvent.Close` 里 `mirror.reset()`。
- 客户端界面关闭时 `mirror.reset()`。

## 4. 不变量

1. **非创造玩家**：`CreativeCursorReportPayload` 永远被忽略；`pushIfChanged` 永远不发。
2. **面板不可见**或**不是创造界面**：客户端不发报告；服务端行为与原版完全相同（关界面时服务端光标是空的，`removed()` 什么都不做）。
3. 现有 7 处服务端处理的**代码与协议不变**；对背包的修改仍先过 `BackpackGate`。
4. 同一份光标不会往返两次：发出后 `sent`、收到后 `received`，`shouldSend` 在两端都为假。

## 5. 协议版本

`BackpackNetwork` 的 `versioned("14")` → `"15"`；新增两个包的注册；客户端服务端版本不一致时按 NeoForge 的原有方式拒绝连接。
