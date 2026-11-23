# 契约：请求包、服务端算法、桥接口

对应 FR-002、FR-005～FR-011。

## 1. 请求包 `BackpackNetworkTakePayload`

`type = mcfloatingbackpack:network_take`（沿用其它包的命名风格），方向客户端 → 服务端，注册在 `BackpackNetwork.register` 里；`BackpackNetwork` 的协议版本从 `"21"` 改为 `"22"`（同改所有写死 `versioned("21")` 的测试断言）。字段见 data-model。

## 2. 服务端算法 `handleServer`

```
player = context.player() (ServerPlayer)
ref = BackpackRef.resolve(player);  无效或 !ref.stillValid(player) → BackpackNetwork.sendSession(player,false,null); return
menu = player.containerMenu
if !ServerCompatDenials.allows(player, menu, TAKE_FROM_CONTAINER): return          # 059：OFF 拒绝并提示
if !menu.getCarried().isEmpty() or menu == player.inventoryMenu or menu.containerId != payload.containerId(): return
if payload.what().isEmpty(): return
amount = payload.what().getMaxStackSize()                                         # 不信任客户端
backpack = BackpackGate.acquireForPanel(player, ref).orElse(null);  null → sendSession; return
taken = NetworkStorageBridges.tryTake(player, menu, payload.what().copyWithCount(amount), payload.handle())   // amount = NetworkTakeRules.amountFor(ops, what, payload.amount())：0 = 一整组，否则夹到至多一组
if taken.isEmpty(): 保存/同步不需要；return                                        # 没有桥处理或失败：原样
stack = taken.get()                                                               # 可能为空（取不到）
if stack.isEmpty(): return
leftover = backpack.insertIntoMain(stack)
if !leftover.isEmpty():
    r = NetworkStorageBridges.tryGiveBack(player, menu, leftover, payload.handle()).orElse(leftover)
    if !r.isEmpty(): r = ContainerMenuAccess(player).fallback()  → toInventory(r) 然后 drop(剩余)；WARN
ref.saveInventory(player, backpack); player.getInventory().setChanged();
menu.broadcastChanges(); BackpackNetwork.sendFullSync(player, ref)
```

- 取到的物品栈必须与请求的是同一种物品（`ItemStack.isSameItemSameComponents`）且数量 ≤ `amount`，否则 `BridgeDispatch` 把它当作失败（058 起的返回值检查，R8）。
- 守恒：`taken = 放进背包 + 退回网络 + 兜底`（`NetworkTakeRules.distribute`，R9）。
- 没有额外的频率限制（每个请求只取一个条目的一组）。

## 3. 桥接口

```java
interface Bridge<P, M, S> {
    String modId();
    boolean handles(M menu);                               // 058：存入
    S insert(P player, M menu, S stack);                   // 058
    default boolean handlesTake(M menu) { return false; }  // 060：取出
    default S take(P player, M menu, S what, long handle) { throw new UnsupportedOperationException(); }
    default S giveBack(P player, M menu, S stack, long handle) { throw new UnsupportedOperationException(); }
}
```

- `take`：返回实际取到的物品栈（空 = 没取到），数量 ≤ `what.getMaxStackSize()`，是同一种物品；
- `giveBack`：返回**仍没放回去**的部分（空 = 全放回了）。

`BridgeDispatch.tryTake` / `tryGiveBack`（规则同 `tryInsert`）：跳过未启用的桥（`state.isActive` 与 `levelAllows` 都要满足）；`handlesTake`、`take`、`giveBack` 都在 `try` 里；失败（`LinkageError`/`RuntimeException`/不合法返回值）→ `state.fail(modId)`，第一次才调回调，返回空 `Optional`。不合法的返回：`take` 返回 null、与 `what` 不是同一种、数量 > 请求的数量；`giveBack` 返回 null、与给出的不是同一种、数量 > 给出的数量。测试表：

| 场景 | 期望 |
|---|---|
| 无桥 / 桥未启用 / `handlesTake` 为假 | 空 |
| `take` 返回 64 个 | 有值，64 个 |
| `take` 返回空 | 有值（空）：桥处理了，没取到 |
| `take` 返回别的物品 / 更多 / null | 失败：空，状态 `FAILED`，回调一次 |
| `take` 抛 `RuntimeException`/`NoSuchMethodError` | 失败 |
| `giveBack` 返回仍没放回去的 | 有值 |
| 失败之后再调 | 不再调用桥、回调不再触发 |
| 档位不允许 | 跳过，不是失败 |
| 传给桥的是拷贝 | 桥里改不影响调用方 |

## 4. 各桥的实现

- **集成终端**（`IntegratedTerminalsBridge`）：`handlesTake(menu)` = 058 的 `handles`；`take` = `tab.getIngredientNetwork().getChannel(selectedChannel).extract(what.copyWithCount(min(count, maxStack)), IngredientComponent.ITEMSTACK.getMatcher().getExactMatchNoQuantityCondition(), false)`（`what` 的数量 = 要取的数量，至多一组）；`giveBack` = 058 的 `insert`。
- **AE2**（`Ae2Bridge`）：`handles` 恒假；`handlesTake(menu)` = `menu instanceof MEStorageMenu`；`take`：`menu.getCarried()` 非空 → 返回空；`menu.handleInteraction(handle, InventoryAction.PICKUP_OR_SET_DOWN)`；`extracted = menu.getCarried().copy()`；`menu.setCarried(ItemStack.EMPTY)`；返回 `extracted`。`giveBack`：`menu.setCarried(stack.copy())`；`menu.handleInteraction(handle, InventoryAction.PICKUP_OR_SET_DOWN)`；`rest = menu.getCarried().copy()`；`menu.setCarried(ItemStack.EMPTY)`；返回 `rest`。
