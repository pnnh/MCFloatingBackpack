# Contract: 服务端交换

处理在服务器主线程（`enqueueWork`）。任一步失败：不写背包，向该玩家快捷栏上方发一句（20 tick 内同一句不重复），回 `ok = false`。

1. 玩家是 `ServerPlayer`，并且 `containerMenu == inventoryMenu`。否则 `quick.refused`。
2. `BackpackRef.resolve` 的 `normalized(selectedSlot)` 与请求里的来源 `normalized` 相同。否则 `quick.refused`。不接受客户端指定的另一只背包。
3. `BackpackGate.acquire`。empty（不是背包，或 040 保护）则 `quick.refused`；保护的情况不调用 `acquireForOpen`，不打开确认窗口。
4. 下方区存在 `quick_access`。否则 `quick.need_tool`。
5. `slot` 属于此刻的 `QuickCandidates`。否则 `quick.refused`。
6. 主手是浮动背包，或 `BackpackNesting.refusal(player, ref, hand)` 不是 OK：`quick.refused`。
7. 用 `HandSwapSlots.destination` 决定主手物品的落点。主手为空，或被点格子 `canPlaceMain` 为真：落点就是这一格。否则按 `mainTargetOrder` 找第一个空的、且 `canPlaceMain` 为真的其它格子（能收这种物品的分类行，然后默认格子，然后通用行）。找不到：`quick.no_room`，两边都不动。
8. 被点格子的整组进主手。主手的整组写入落点；落点不是被点格子时，被点格子清空。`ref.saveInventory`。若该玩家的面板会话正开在这只背包上，或联合会话开着，对被点格子发 `sendSlotUpdate`；落点不同时再发一次。回 `ok = true`。

交换不合并、不拆分、不动背包光标。主手原来的物品没有 `quick` 时，下一步名单自然不会有它；不另写标记。

浮动面板的 Alt+左键（`BackpackClickPayload` 的 `swapHand`，且区域是主区域）用同一个 `HandSwapSlots.destination`。没有落点时不写背包，快捷栏上方 `quick.no_room`。落点不是被点格子时，两个格子都 `sendSlotUpdate`。工具区的 Alt+左键仍是原格对调。普通点击放进分类行仍在 `canPlaceMain` 为假时整次拒绝，不提示。

下方区放入规则（与本次对调无关，走现有点击包）：`ToolSingleRules.check(BOTTOM, area, incoming, slot, areaSlots, isQuickAccess)` 为 `DUPLICATE` 时整次点击拒绝，提示 `limit.quick_access`。客户端 `FloatingPanelInput.refusedByLimit` 用同一调用预先拒绝，避免发出必失败的请求。
