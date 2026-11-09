# Research: 创造物品栏界面兼容

读 NeoForge 21.1.251 反编译源码与本仓库源码得出（反编译源码解在 `/tmp/mcsrc`，不入库）。**"已读到"是事实，"推断"是没有在游戏里验证的结论。**

## 已读到的事实

| # | 事实 | 出处 |
|---|---|---|
| F1 | `ItemPickerMenu.getCarried/setCarried` 转给玩家的 `inventoryMenu`，**但只在客户端**；创造界面是客户端本地处理 | `CreativeModeInventoryScreen`（`ItemPickerMenu` 末尾） |
| F2 | 物品栏标签页点格子：客户端本地 `player.inventoryMenu.clicked(...)` 然后 `inventoryMenu.broadcastChanges()`；**不发容器点击包** | `slotClicked` 的物品栏分支 |
| F3 | 物品标签页点物品栏格子：`menu.clicked(...)`，再通过 `handleCreativeModeItemAdd` 把改动的格子的**整个栈**发给服务端；快捷拖动（`QUICK_CRAFT` 结束）重发 **9 个快捷栏格子** | `slotClicked` 的末尾分支 |
| F4 | 格子改动怎么传到服务端：创造界面给客户端的 `inventoryMenu` 装了一个 `CreativeInventoryListener`，它的 `slotChanged` 调 `handleCreativeModeItemAdd` → `ServerboundSetCreativeModeSlotPacket`；监听器**只在格子内容与 `lastSlots` 不一致时**触发（`triggerSlotListeners`） | `CreativeInventoryListener`、`AbstractContainerMenu.broadcastChanges` |
| F5 | 服务端 `handleSetCreativeModeSlot`：创造模式玩家就把客户端给的栈 `setByPlayer` 到 `inventoryMenu` 的格子（槽号 1～45），不校验来源 | `ServerGamePacketListenerImpl` |
| F6 | 客户端收到服务端"光标"同步包（`containerId == -1`）时，**当前是创造界面就直接忽略** | `ClientPacketListener.handleContainerSetSlot` |
| F7 | 客户端收到容器 0 的格子更新：快捷栏格子（36～44）总是应用；其它格子在"创造界面且当前不是物品栏标签页"时被忽略 | 同上 |
| F8 | `LocalPlayer.closeContainer()` 先发 `ServerboundContainerClosePacket`，再 `setScreen(null)`；服务端 `handleContainerClose` → `doCloseContainer` → `containerMenu.removed(player)` → **`AbstractContainerMenu.removed` 把服务端菜单光标放回玩家物品栏（放不下就掉落）并清空**；并发 `PlayerContainerEvent.Close` | `LocalPlayer`、`ServerPlayer`、`AbstractContainerMenu` |
| F9 | 客户端的 `AbstractContainerMenu.removed` 对非 `ServerPlayer` 什么都不做，所以原版创造模式里光标上的物品**不会被放回**（客户端保持/丢弃） | `AbstractContainerMenu.removed` |
| F10 | 本仓库依赖服务端光标的服务端处理：`BackpackClickPayload`、`BackpackCursorGesturePayload`、`MergePanelCursorPayload`、`BackpackTrashPayload`（放入/取回）、`BackpackTrashSendPayload`、`BackpackTakeFromContainerPayload`（只读判空）、`BackpackQuickTransferPayload`（只读判空，创造界面被排除）。它们都读 `player.containerMenu.getCarried()`；创造界面时 `containerMenu == inventoryMenu` | 读源码 |
| F11 | 这些处理最后都调 `BackpackNetwork.sendSlotUpdate/sendFullSync/sendTrash` 之一把结果发回面板 | 读源码 |
| F12 | 面板的鼠标事件挂在 `ScreenEvent.MouseButtonPressed.Pre`，**早于界面自己处理这次点击** | `FloatingPanelEvents` |
| F13 | 客户端判断"有容器界面"用 `ContainerScreenCompat.isOpenContainerScreen`：任何 `AbstractContainerScreen`（创造界面是）且所属模组未关闭；创造界面按原版处理，不可配置 | `ContainerScreenCompat` |

## Decisions

### D1. 光标往返的做法：服务端菜单光标镜像客户端的创造光标

**选 A（镜像）**：创造界面打开且面板可见期间，服务端 `player.inventoryMenu` 的光标始终等于客户端创造光标（落后至多一次输入事件）。
- 客户端：每次输入事件（鼠标按下/松开、按键，`Pre`，早于界面处理）和每次发起光标请求之前，若创造光标与"上次报告/回写"不同就发 `CreativeCursorReportPayload`。
- 服务端：校验后 `inventoryMenu.setCarried(copy)`；之后现有 7 处服务端处理**原样**读 `menu.getCarried()`，不用改。
- 服务端处理完，钩子在 `BackpackNetwork` 的发送辅助方法里：菜单光标与"客户端已知光标"不同就发 `CreativeCursorPushPayload`，客户端把创造光标设成它。

**弃 B（每次请求把光标带在请求里，用完即还）**：要改 7 个服务端处理与 4 个客户端发送点的格式；而且服务端光标用完就清空，**关界面时面板里拿起的物品落在客户端光标上被丢弃**，守恒要另写一套。
**弃 C（创造界面按"无界面"处理，用面板自己的内部光标）**：面板与创造界面的光标彼此独立，无法在面板和创造界面的格子之间搬东西，等于没修（用户的"叠在原版界面上"诉求落空）。

### D2. 报告时机

- 只在**面板可见且当前界面是创造物品栏界面**时报告；否则完全保持原版行为（零成本）。
- 触发点：`ScreenEvent.MouseButtonPressed.Pre`、`MouseButtonReleased.Pre`、`KeyPressed.Pre`（含 E 与 ESC，它们在界面关闭之前）；以及三处客户端发请求之前（面板点击、拖动、垃圾桶）显式调用一次。比较用 `ItemStack.matches`（数量与组件）。
- 这样关界面（E/ESC）的那一下 `KeyPressed.Pre` 就能把最后的光标送到服务端，早于关闭包（F8）。

**弃"每个客户端 tick 比较"**：多一个定时器，且仍有一个 tick 的缝；输入事件已经覆盖所有会让光标变的动作。
**弃"持续同步（每次变都发）"**：创造界面里拖动或滚轮不会改光标，但仍把光标变化与面板无关的事也发出去；只在面板可见时发已足够。

### D3. 结果回写的钩子：`BackpackNetwork` 的发送辅助方法

服务端处理最后都走 `sendSlotUpdate/sendFullSync/sendTrash`（F11）。在其中调用 `CreativeCursorBridge.pushIfChanged(player)`：
- 仅当玩家是创造模式、`containerMenu == inventoryMenu`、且曾收到过报告（`CursorMirror` 有记录）时才可能发；
- 菜单光标与"客户端已知光标"相同则什么都不发（自动拾取等不改光标的发送就是空操作）；
- 发出后把"客户端已知光标"更新为推送的值，客户端收到后也把"上次报告"设成它，避免回声。

**弃"每个处理末尾显式调用"**：要改 6 处，漏一处就坏一处；集中钩子更稳。
**弃"服务端 tick 里比较"**：多 50 ms 延迟，期间客户端可能用旧光标再次点击。

### D4. 关界面时的守恒（待用户确认的一处偏差）

服务端镜像光标之后，原版 `removed()`（F8）会把光标上的物品放回物品栏——**这正好解决守恒**，面板里拿起的物品不会因为客户端光标被丢弃而丢失，也不用另写代码。

**偏差**：原版创造模式关界面时光标上的物品直接丢弃（F9）。面板可见时，现在光标上**从创造物品列表里取的虚拟物品**也会被放回物品栏（或放不下时掉落）。这与生存模式的行为一致，也是规格"和生存物品栏界面一致"的要求；创造模式下物品本来无限，代价很小。面板不可见时完全是原版。**这是需要用户确认的一处，写进 DELIVERY 的偏离清单**。

**弃"客户端标记光标物品是否来自面板，只对来自面板的才放回"**：要在合并、交换、聚拢等每个操作里追踪来源，容易漏，漏了就丢真实物品。

**弃"关界面时丢弃"**：面板里取出的真实物品会因此永久消失，违反原则 II 的守恒。

### D5. 服务端校验（`CreativeCursorRules`，纯类）

按顺序判定一份报告，第一个命中者为准：
1. 玩家不是创造模式 → 忽略（`NOT_CREATIVE`）；
2. 玩家当前打开的不是自己的 `inventoryMenu`（比如打开了箱子）→ 忽略（`OTHER_MENU`）；
3. 栈非空且数量 > 该物品最大堆叠数，或数量 < 0 → 拒绝（`BAD_COUNT`）；
4. 栈非空且物品在当前世界被特性开关禁用 → 拒绝（`DISABLED_ITEM`）；
5. 否则接受（`ACCEPT`）。

被忽略或拒绝的一律零变化（不改菜单光标、不回写），只记一行 DEBUG 日志。数据组件由网络解码器保证合法（解码失败整个包被丢弃）。

### D6. 与 026 的关系

- 所有对背包的修改仍先过 `BackpackGate`（`acquire`），本 feature 不改它。
- 026 里"栈落后但内容与影子相同则不判复制品"的补充保留。

### D7. 旧栈覆盖新栈（规格的第二个后果）：先复现再决定

读源码（F4、F7）的推断：

- 客户端写回服务端只发生在**创造界面里玩家本地改动了那一格**（F3 的 `handleCreativeModeItemAdd`、F4 的监听器），或快捷拖动重发 9 个快捷栏格子（F3）。没有改动的格子**不会**被重发。
- 服务端对非快捷栏格子的更新，在创造界面的**物品标签页**下会被客户端忽略（F7），客户端手里的那只背包栈因此是旧的；此时玩家若在创造界面里**拿起或放下那只背包本身**，就会把旧栈写回服务端。
- 所以风险场景是：背包在**非快捷栏**（物品栏中间或胸甲位）、创造界面物品标签页、先用面板改了背包、再在创造界面里搬动那只背包；以及任何时候做快捷拖动（9 格快捷栏重发，快捷栏格子的更新是应用的，风险较小）。

这与 026 实测的现象（DSV 在快捷栏槽 0～2 落后 2、EBW 在胸甲位落后 4）**部分吻合但不完全解释**：快捷栏的格子更新会被应用（F7），所以 DSV 的落后另有来路。**因此不凭推断做改动**：Phase B 先复现，并用 026 加的拒绝日志与修订号对账，再决定要不要做"服务端保存背包后，创造玩家额外收一个自家的栈同步包，直接写进客户端物品栏"（`BackpackStackSyncPayload`，条件新增）。

### D8. Ctrl+点击快速转移在创造界面里的缺口（本 feature 不做，线索登记）

011 把创造界面排除在快速转移之外（`QuickTransferRules.isInterceptableScreenClass`）。本 feature 的光标镜像**只解决其中一个障碍**。读源码得出三个障碍：

| 障碍 | 镜像光标能解决吗 | 依据 |
|---|---|---|
| 服务端要求"光标为空"才允许快速转移（`BackpackQuickTransferPayload`），创造界面里服务端光标永远是空的，判断失真 | **能** | F10 |
| **物品栏标签页的格子编号是菜单序号，不是物品栏下标**：`SlotWrapper` 构造时 `super(slot.container, index, x, y)` 把菜单序号 `k`（0～45）当成容器下标传给了 `Slot`，而 `Slot.getContainerSlot()` 返回的正是它；011 的玩家物品栏一侧用 `slot.getContainerSlot()` 当物品栏下标，例如快捷栏第 0 格（菜单 36）会被当成"超出 0～35"。物品标签页下的快捷栏格子是真格子，下标正确 | 不能，要另做映射 | `CreativeModeInventoryScreen.SlotWrapper`（包可见类，字段 `target` 是真格子）、`Slot.getContainerSlot` |
| **转移之后客户端显示是旧的**：物品标签页下客户端忽略非快捷栏格子的服务端更新（F7），转移后那一格看起来还有物品，之后还可能被旧栈写回 | 不能，要另做同步 | F7 |

后续 feature（编号待定）要做的事：①映射格子下标（读 `SlotWrapper.target`，类不是公开的，要用访问转换器或反射，或按格子内容对应）；②创造玩家转移后由服务端把变化的物品栏格子直接写进客户端（与本 feature 的 Phase B 条件同步包是同一类机制，可以共用）；③解除 `isInterceptableScreenClass` 的排除并补创造界面下的验证。建议在本 feature 的光标镜像与 Phase B 结论出来之后再做。**"`getContainerSlot()` 返回菜单序号"是读源码的推断，没有在游戏里验证。**

## 待验证（实现阶段在客户端会话里做，验证前不得声称满足）

| # | 内容 | 在哪验证 |
|---|---|---|
| U1 | 物品栏标签页与物品标签页下，镜像光标后面板操作与生存界面一致 | quickstart A、B |
| U2 | 从创造物品列表取出的带数据物品放进面板后数据完整 | quickstart A |
| U3 | 关界面（E、ESC）时光标上的面板物品回到物品栏，不丢不复制 | quickstart C |
| U4 | D4 的偏差（虚拟物品也被放回）的实际表现是否可接受 | quickstart C |
| U5 | 旧栈覆盖的机制是否存在、在哪种场景下出现 | quickstart E |
| U6 | 联机延迟下的光标往返 | quickstart F，**没有条件则写"未验证"** |
| U7 | 断线或崩溃时服务端镜像光标上的物品去向（与生存模式一致？） | 读源码为主，写进已知限制 |
