# Data Model: 网络终端 → 面板

无持久化数据（`DataRevision` 6）。运行时类型：

## `NetworkEntry`（客户端，无第三方类型）

`record NetworkEntry(ItemStack what, long handle)`：被点的网络条目。`what` 是单个、带数据的物品栈；`handle` 是各终端自己解释的不透明标识（AE2：`GridInventoryEntry.getSerial()`；集成终端：0）。

## `NetworkTerminalClient`（客户端接口）

`Optional<NetworkEntry> entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY)`：光标下是不是我认得的终端的网络条目。第三方类型只在实现里。

## `BackpackNetworkTakePayload`（客户端 → 服务端）

| 字段 | 类型 | 编码 |
|---|---|---|
| `containerId` | int | `ByteBufCodecs.VAR_INT` |
| `what` | ItemStack | `ItemStack.STREAM_CODEC`（非空） |
| `handle` | long | `ByteBufCodecs.VAR_LONG` |
| `amount` | int | `ByteBufCodecs.VAR_INT`（0 = 一整组；服务端夹到至多一组，真机反馈后新增） |

解码规则：`what` 为空 → `DecoderException`；`containerId` 不限（服务端与当前菜单比对）。

## `NetworkTakeRules`（纯）

- `validate(ItemStack-like what)`：非空；`amount = maxStackSize(what)`（≥ 1）。
- `distribute(int taken, IntUnaryOperator insertIntoBackpack, IntUnaryOperator giveBack, IntUnaryOperator fallback)`：返回 `Split(toBackpack, backToNetwork, fallback)`，断言 `toBackpack + backToNetwork + fallback == taken`；回调各返回"没放进去的数量"。
- 泛型版本用 `QuickTransferLogic.StackOps<S>` 抽象物品栈，纯逻辑测试用假栈。

## `BridgeDispatch`（058/059，改）

`Bridge` 多 `handlesTake`、`take`、`giveBack`（默认方法）；多 `tryTake(player, menu, what, handle)`、`tryGiveBack(player, menu, stack, handle)`，均返回 `Optional<S>`（空 = 没有桥处理，调用方走自己的退路）。

## `ClientCompatVersions`（客户端）

`boolean inRange(ThirdPartyCompat row)`：`CompatFinding.check(row, InstalledMods::versionOf)` 且结论 `IN_RANGE`；按 modId 缓存。
