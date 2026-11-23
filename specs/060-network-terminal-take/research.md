# Research: 网络终端 → 面板

方案取舍已与用户确认（spec Clarifications）。本文记 plan 阶段新查证（`javap` 读集成终端 1.11.2、集成动力学 1.39.1、公共能力 2.11.6、AE2 19.2.20 的 jar；读本仓库客户端手势代码）的事实与设计决定。AE2 jar 与前几个 feature 一样只下载到会话临时目录，没进仓库。

## R1　现有的 Ctrl+点击手势与网络条目（**修订：集成终端的条目不是菜单格子**）

`QuickTransferInput.beginFromScreen(screen, button, x, y)`（面板可见、Ctrl+左键、屏幕支持、光标为空）用自己的命中测试遍历 `menu.slots`，对每个 `slot.isActive()` 的格子判断鼠标是否在它的 16×16 里，非玩家物品栏的格子给 `Hit(CONTAINER, slot.index, slot.hasItem())`，随后 `start(...)` → 发 `BackpackTakeFromContainerPayload`（菜单编号 + 格子号）。集成终端的网格格子与 AE2 的 `RepoSlot` 都是客户端菜单里**真的 `Slot` 对象**（有位置、有 `getItem()`），所以今天点它们会走这条路，服务端看到同一个格子号却是空的/没有对应物品，**什么也不取**——这正是"反向通用路径无效但无害"。

**设计后果**：不需要自己去算网格几何，也不需要读终端屏幕的私有状态：在命中测试得到那个 `Slot` 之后，问"这个格子是不是某个终端的网络条目"即可。新手势挂在 `beginFromScreen` 里、`screenSlotAt` 之前（用同一个命中结果），认得就改发新请求并**不调用 `start`**——不进入 `QuickTransferGesture`，所以没有拖动（FR-004）。

## R2　集成终端（1.11.2）：怎么在客户端认出"物品存储页的网络条目"

- `ContainerScreenTerminalStorage.getSlotUnderMouse()` 与 IT 自己 Shift+点击的处理都证明：网格格子是菜单 `Slot`，`slot.getItem()` 是该格显示的物品栈（空格子为空，IT 的 Shift+点击先判 `slot.getItem().isEmpty()`），`slot.index` 就是 IT 传给 `ITerminalStorageTabClient.handleClick` 的"悬停的存储格号"。
- `ContainerTerminalStorageBase.getTabSlots(String tabName)` 返回 `List<Pair<Slot, ISlotPositionCallback>>`：这个页自己的格子（网格格子）。判"该格子是当前页的网格格子"：`menu.getTabSlots(menu.getSelectedTab())` 的左元素里有它。
- 当前页是物品存储页：`ContainerScreenTerminalStorage.getSelectedClientTab()`（`Optional<ITerminalStorageTabClient<?>>`）是 `TerminalStorageTabIngredientComponentClient`，**不是**它的合成页子类 `TerminalStorageTabIngredientComponentItemStackCraftingClient`（与 058 服务端同理，合成页是存储页的子类，必须排除；末影箱页是别的类）；`getIngredientComponent() == IngredientComponent.ITEMSTACK`。
- 条目的物品栈 = `slot.getItem()`（取单个：`copyWithCount(1)`）。用屏幕的公开方法与菜单的公开方法，无需 mixin/反射。

**未验证**：`slot.getItem()` 的数量语义（可能是真实数量、可能被截断）不影响本设计——请求不带数量。

## R3　集成终端（1.11.2）：服务端怎么取

`TerminalStorageTabIngredientComponentServer<T, M>.getIngredientNetwork().getChannel(c)` 返回 `INetworkIngredientsChannel<T, M>`（继承 `IIngredientComponentStorage<T, M>`）：`extract(T prototype, M matchCondition, boolean simulate)`（按原型的数量取）与 `extract(long maxQuantity, boolean simulate)`。物品成分的精确匹配条件 = `IngredientComponent.ITEMSTACK.getMatcher().getExactMatchCondition()`（`IIngredientMatcher` 有 `getExactMatchCondition`、`getExactMatchNoQuantityCondition`）。取一组 = `extract(what.copyWithCount(amount), exact, false)`，返回实际取到的物品栈（可能少于 `amount`，可能为空）。网络的变化由 IT 自己的观察者同步给终端界面，无需我们发包（与 058 的存入同理）。服务端的判断条件与 058 的 `handles` 完全一样（页是物品存储页、非合成页、`getComponent() == ITEMSTACK`）。**还**回网络直接用 058 的 `insert`。

## R4　AE2（19.2.20）：客户端怎么认出网络条目

- `MEStorageScreen<C extends MEStorageMenu>`（`appeng.client.gui.me.common`）是所有显示 ME 网格的终端屏幕的基类（普通 ME 终端、合成终端、样板编码终端、无线终端、便携元件都继承它或用同一个菜单基类 `MEStorageMenu`）。
- 网格格子是 `RepoSlot`（`appeng.client.gui.me.common`，继承 `ClientReadOnlySlot` → 是菜单 `Slot`）：`getEntry()` 返回 `GridInventoryEntry`（`getSerial()`、`getWhat()`（`AEKey`）、`getStoredAmount()`、`getRequestableAmount()`、`isCraftable()`）；`getStoredAmount()` 是网络里真有的数量，`getRequestableAmount()` 含可合成的。
- 条目要求：`slot instanceof RepoSlot`、`getEntry()` 非空、`getWhat() instanceof AEItemKey`（只做物品，不做流体）、`getStoredAmount() > 0`（只显示可合成的条目不取、不触发合成，规格 US2.3）。物品栈 = `AEItemKey.toStack(1)`（带数据）；标识 `handle` = `getSerial()`。
- **范围**：这些终端共用同一个网格、同一套服务端 `handleInteraction`，所以一律支持（规格 FR-004 已据此修订）。样板编码终端、合成终端的别的区域（合成网格、样板格）不是 `RepoSlot`，不会被认。

## R5　AE2（19.2.20）：服务端怎么取——用菜单自己的"拿起/放下"，不碰能量与权限

`MEStorageMenu` 里 `public final void handleInteraction(long serial, InventoryAction action)`（`IMEInteractionHandler`）：先 `canInteractWithGrid()`（电力、权限、网络在线都在里面判），然后：
- `PICKUP_OR_SET_DOWN`，菜单携带物**为空**：`StorageHelper.poweredExtraction(energySource, storage, key, key.getMaxStackSize(), actionSource)` 取至多一个满堆叠，`setCarried(...)`；
- 同一个动作，携带物**非空**：`putCarriedItemIntoNetwork(...)`（`poweredInsert`）把携带物放回网络。
（`SHIFT_CLICK` 走 `moveOneStackToPlayer`，放进玩家物品栏，不是背包，所以不用。）

**设计**：服务端的"取"= 携带物为空时 `handleInteraction(serial, PICKUP_OR_SET_DOWN)`，读 `menu.getCarried()` 得到取到的物品栈，立刻 `menu.setCarried(ItemStack.EMPTY)`（这一步之后物品只在我们手里）；"还"= `menu.setCarried(剩余)`，再 `handleInteraction(serial, PICKUP_OR_SET_DOWN)`（携带物非空 → 放回网络），读 `getCarried()` 得到**仍没放回去**的部分并清空。能量、权限、网络离线都由 AE2 自己的 `canInteractWithGrid()` 判，我们不引用它们的类型（规格里"能量与权限 SecurityPermissions.EXTRACT 的检查"由 AE2 自己做）。过期的序号：`getStackBySerial` 找不到 → AE2 什么也不做 → 携带物仍为空 → 我们视为"没取到"。

**前提**：玩家光标（菜单携带物）为空——与 012 的取出请求相同的前置条件，已在服务端检查。

## R6　请求不带数量，服务端定为最大堆叠

客户端不需要报数量：服务端取 `min(what.getMaxStackSize(), 网络里实际有的)`。集成终端：`extract(..., amount = what.getMaxStackSize())` 本身只返回实际有的；AE2：`PICKUP_OR_SET_DOWN` 自己取到 `key.getMaxStackSize()` 为止。校验：`what` 非空、数量为 1（单个原型）、`handle` 是任意 long。这样既不信任客户端，又少一个要校验的字段。

## R7　请求包放什么、放不放第三方类型

`BackpackNetworkTakePayload(int containerId, ItemStack what, long handle)`，`what` 用 `ItemStack.STREAM_CODEC`（非空、带数据组件），`handle` 用 `ByteBufCodecs.VAR_LONG`；不含任何第三方类型。AE2 的 `AEKey` 不需要序列化：服务端用序号 `handle` 让 AE2 自己解析成键；物品栈 `what` 只用来做"是不是同一种物品"的合法性检查（AE2 取回的物品栈与 `what` 必须是同一种，否则当作异常丢弃/退回）。

## R8　桥接口的扩展与分派

`BridgeDispatch.Bridge` 增加：
- `default boolean handlesTake(M menu) { return false; }`
- `default S take(P player, M menu, S what, long handle) { throw new UnsupportedOperationException(); }`——返回实际取到的物品栈，空表示没取到；
- `default S giveBack(P player, M menu, S stack, long handle) { throw new UnsupportedOperationException(); }`——返回**仍没放回去**的部分。
`BridgeDispatch.tryTake` / `tryGiveBack` 与 `tryInsert` 同规则：桥未启用（状态/档位）跳过；`handlesTake`/`take`/`giveBack` 在 `try` 里；`LinkageError`、`RuntimeException`、返回值不合法（null、与请求不是同一种物品、数量超过请求/给出的数量）当作失败：状态置 `FAILED`、回调一次、返回"没处理"。`tryTake` 返回 `Optional<S>`（有值 = 桥处理了，值是取到的，可能为空），`tryGiveBack` 同理。

058 的 `handles/insert`（存入）不受影响。**AE2 的桥 `handles` 恒为 false**（AE2 的存入继续走 012 通用路径，那条路验证过可用，不被这个 feature 改动）。

## R9　服务端"放进背包 / 退回 / 兜底"的守恒

`取到 N` = `放进背包 a` + `退回网络 b` + `兜底 c`（c = 玩家物品栏 + 脚下），`a + b + c = N`：
1. `leftoverAfterBackpack = backpack.insertIntoMain(取到的)`（返回放不下的）；`a = N − leftover`。
2. `leftover` 非空 → `tryGiveBack(leftover)` 返回仍没放回去的 `r`（`b = leftover − r`）；桥不处理/失败（返回空 Optional）→ `r = leftover`。
3. `r` 非空 → 012 已有的 `ContainerMenuAccess.fallback()`：`toInventory`（玩家物品栏 0–35），剩余 `drop`（脚下）；写一条 WARN。
纯逻辑 `NetworkTakeRules.distribute(...)` 用抽象的"放得下多少"回调算出 `(a, b, c)` 并断言守恒，用假背包、假桥测。

## R10　客户端启用条件：不用 `CompatRuntimeState`

`CompatRuntimeState`/`ThirdPartyCompatService` 只在**服务端**存在（客户端连远程服务器时没有）。客户端自己判断：`ClientCompatVersions.inRange(modId)` = `CompatFinding.check(表里对应的行, InstalledMods::versionOf)` 是否 `IN_RANGE`（缓存，因为已装版本在一次运行里不变；伪造版本的调试开关照样生效）；再加客户端档位 `CompatConfig.policy().specificAdaptation(modId)`（FULL 才启用）。**不看** `allowUntestedVersions`（客户端读不到）：范围外时客户端一律不启用（规格已定）。

## R11　AE2 的编译依赖能否只用 `compileOnly` + `transitive = false`

AE2 的屏幕类继承 `AEBaseScreen`（继承 `AbstractContainerScreen`），部分类型引用 GuideME 等，但只被 AE2 自己的方法体用到；我们只引用 `MEStorageScreen`、`RepoSlot`、`GridInventoryEntry`、`AEItemKey`、`MEStorageMenu`、`InventoryAction`，javac 解析这些类的父类链时若缺 GuideME 的类可能要求它在类路径上。**不确定，任务里第一个编译检查会证实**；若需要，加 `compileOnly guideme`（同样 `transitive = false`，开发运行里已有 `localRuntime` 的同版本），规格与测试不变。

## R12　AE2 的存入桥

`Ae2Bridge.handles(menu)` 恒 false：AE2 的面板 → 终端存入不走桥（058 验证过通用路径可用）。这样 AE2 的桥只管取出，不改变已验证的正向行为。

## R13　没有选用的做法

| 做法 | 为什么不选 |
|---|---|
| 请求里带数量 | 不需要，且多一个要校验的字段（R6） |
| 对 AE2 直接调 `MEStorage.extract` + `poweredExtraction` | 要引用能量源/动作源类型并自己做权限检查；用菜单自己的 `handleInteraction` 全部由 AE2 判（R5） |
| 对 AE2 用 `SHIFT_CLICK` | 物品进玩家物品栏，不是背包 |
| 在 `QuickTransferGesture` 里加"网络条目"一类 | 手势层是纯逻辑、只认槽位；网络条目是特例，挂在 `beginFromScreen` 入口更小，也天然排除拖动（R1） |
| 用 `getSlotUnderMouse()`（渲染时才刷新） | 现有手势已用自己的命中测试（避免错过快速移动的鼠标），沿用（R1） |
| 客户端读 `CompatRuntimeState` | 客户端没有它（R10） |

## R14　修订（实现时游戏内发现）：集成终端的条目不是菜单 `Slot`

R1/R2 起初假设集成终端的网格格子是客户端菜单里的 `Slot`（读 Shift+点击的字节码时把"玩家物品栏格子"误当成了存储格子）。游戏里第一次 Ctrl+点击条目什么也没发生，回头读 `ContainerScreenTerminalStorage.mouseClicked` 才看清：它 Shift+点击用的 `getSlotUnderMouse()` 是**玩家物品栏**的格子；存储条目由屏幕自己画，鼠标下是哪一个由公开方法 `getStorageSlotIndexAtPosition(double, double)`（返回含已滚动掉的行的存储格序号，不在存储区返回 -1）算出，条目的物品由页客户端 `TerminalStorageTabIngredientComponentClient.getSlot(channel, index)`（`Optional<TerminalStorageSlotIngredient>`，`getInstance()` 是物品栈）给出。AE2 的 `RepoSlot` 才是真的菜单 `Slot`（R4 不变）。

**修订后的设计**：识别器接口改成 `entryAt(screen, mouseX, mouseY)`（不再收 `Slot`）；集成终端用上面两个公开方法；AE2 用 `ScreenSlots.slotAt`（从 `QuickTransferInput` 抽出的同一个命中测试）找 `RepoSlot`。挂接点不变：`beginFromScreen` 先问注册表。**R1 里"今天点它们会发一个无效取出请求"只对 AE2 成立**；集成终端的条目今天没有对应的菜单格子，点它就是终端自己的 Ctrl+点击。

## R15　修订（用户真机反馈后在容器里复现）：集成终端"拿起"的物品不是菜单携带物

**发现**：集成终端里普通左键拿起条目后，`containerMenu.getCarried()` 仍是空的（容器里的临时日志：屏幕的菜单与玩家的菜单是同一个对象，携带物都是 `minecraft:air`，而屏幕上确实有物品跟着鼠标）。集成终端在页对象 `TerminalStorageTabIngredientComponentClient` 里记一个"激活条目"（`activeSlotId`、`activeSlotQuantity`），由 `ContainerScreenTerminalStorage.drawActiveStorageSlotItem` 在鼠标处画，点进格子才发 `TerminalStorageIngredientSlotClickPacket`。（早先的判断"集成终端的拿起也是标准的菜单携带物"错了：源码里的 `setCarried` 只在物品/流体处理器的放下与取物路径里。）

**决定**：① 客户端接口 `NetworkTerminalClient` 加 `held(screen)`（读公开 getter）与 `release(screen)`（`resetActiveSlot()`），面板在光标为空时多画一份、点面板正文时把它当作一次"取出"；② 请求包加 `amount`（0 = 一整组），服务端夹到至多一组；③ 不去改集成终端的绘制层级（没有 mixin，宪法 VII），而是在面板之上再画同一件物品。

**否决**：把集成终端的激活条目转成菜单携带物（要改它的内部状态，且与服务端的状态不一致）；在面板里用 z 值压过它（面板本来就高于它，问题是面板不知道有东西要画）。
