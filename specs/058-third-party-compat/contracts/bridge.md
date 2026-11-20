# 契约：网络存储桥与分派

对应 FR-009、FR-013～FR-021。

## 1. 接口

```java
// compat/BridgeDispatch.java（纯，泛型）
interface Bridge<P, M, S> {
    String modId();
    boolean handles(M menu);                  // 这个界面、当前页我是否处理；不改任何状态
    S insert(P player, M menu, S stack);      // 把 stack 的一份拷贝存入对方网络，返回没放进去的部分；不碰玩家物品栏
    // 060 预留：S extract(...)，058 不实现，不写进接口
}

// compat/NetworkStorageBridge.java（MC 层）
interface NetworkStorageBridge extends BridgeDispatch.Bridge<ServerPlayer, AbstractContainerMenu, ItemStack> {}
```

## 2. 分派算法 `BridgeDispatch.tryInsert(player, menu, stack) → Optional<S>`

返回**空**表示"没有桥处理，调用方走原来的通用路径"；返回**有值**表示"桥处理了，这是剩余"。

```
for bridge in bridges (注册顺序):
    if not state.isActive(bridge.modId()):  continue
    try:
        if not bridge.handles(menu):         continue
        rest = bridge.insert(player, menu, ops.copy(stack))
        if rest 不合法:                      # 非空且（不是同一种 或 数量 > 原数量）；null 视为不合法
            fail(bridge, new IllegalStateException("bridge returned an invalid remainder"))
            return empty                     # 背包原样，走通用路径
        return Optional.of(rest)
    catch (LinkageError | RuntimeException e):
        fail(bridge, e)                      # state.fail(modId)；第一次才触发失败回调
        return empty
return empty
```

- `fail(bridge, e)`：`state.fail(modId)` 返回 true（第一次）才调用失败回调（MC 层：写一条 ERROR 含异常；给在线 op 发聊天）；返回 false 什么也不做（FR-014）。
- 传给桥的是**拷贝**；调用方（`backpackOut`）拿到的有值结果直接 `backpack.setStackInSlot(index, rest)`，空表示不动背包。
- `handles` 也在 `try` 里（它引用第三方类型，类找不到也是 `NoClassDefFoundError`）。
- 不捕获 `Error` 的其它子类（`OutOfMemoryError` 等），只捕获 `LinkageError`（含 `NoSuchMethodError`、`NoClassDefFoundError`、`IncompatibleClassChangeError`）与 `RuntimeException`。
- 守恒：有值结果满足 `原数量 − 剩余数量 = 进入网络的数量`；分派层不能证明"进入网络的数量真的等于这个差"，这由桥（`insert` 的返回值语义）保证。出错时见 research R7。

## 3. 调用点

`BackpackQuickTransferPayload.backpackOut`，在 `ServerCompatGate.allows(...)` 之后、对每个物品调用 `OpenContainerDelivery.deliver` 之前：

```java
Optional<ItemStack> viaBridge = NetworkStorageBridges.tryInsert(player, player.containerMenu, stack);
if (viaBridge.isPresent()) {
    backpack.setStackInSlot(index, viaBridge.get());
    continue;
}
// 原有的 OpenContainerDelivery.deliver(...)
```

`NetworkStorageBridges.tryInsert` 在状态表还没建立（服务器未启动）时返回空（恒不启用）。

## 4. 集成终端的桥（`compat/integratedterminals/IntegratedTerminalsBridge`）

`modId()` = `"integratedterminals"`。

**`handles(AbstractContainerMenu menu)`**，全部满足才为真：

1. `menu instanceof ContainerTerminalStorageBase<?> container`；
2. `ITerminalStorageTabServer tab = container.getTabServer(container.getSelectedTab())`，`tab instanceof TerminalStorageTabIngredientComponentServer<?, ?>`；
3. **不是** `TerminalStorageTabIngredientComponentItemStackCraftingServer`（合成页）；
4. `ingredientTab.getIngredientNetwork().getComponent() == IngredientComponent.ITEMSTACK`（物品成分）。

末影箱页是别的类（`TerminalStorageTabEnderChestServer`），第 2 条已排除。

**`insert(ServerPlayer, AbstractContainerMenu, ItemStack stack)`**：

```java
var container = (ContainerTerminalStorageBase<?>) menu;
var tab = (TerminalStorageTabIngredientComponentServer<ItemStack, Integer>) container.getTabServer(container.getSelectedTab());
var channel = tab.getIngredientNetwork().getChannel(container.getSelectedChannel());
return channel.insert(stack, false);   // 返回 ItemStack：放不进去的部分（空栈表示全放进去了）
```

- 网络离线、没有存储、通道不可用：对方的 `insert` 原样返回 `stack`（不抛异常）；若它抛出了异常，由分派层按 §2 处理。
- 不读不写玩家物品栏；不发包（网络的变化由集成终端自己的观察者同步给终端界面）。

**`IntegratedTerminalsCompat.register()`**：`NetworkStorageBridges.register(new IntegratedTerminalsBridge())`；只在 `ModList.get().isLoaded("integratedterminals")` 为真时被调用（同 `CuriosCompat` 的写法）。注册无条件，是否启用在调用时看 `CompatRuntimeState`（版本要等服务器启动、配置加载后才能判）。

## 5. 测试（纯逻辑，用假菜单、假桥）

| 场景 | 期望 |
|---|---|
| 无桥 / 桥未启用 / 桥不认领 | 空 |
| 桥认领、全部放进 | 有值，剩余为空 |
| 桥认领、放进一部分 | 有值，剩余为差额，同种 |
| 桥返回别的物品、更大数量、null | 当出错：空，状态变 `FAILED`，失败回调 1 次 |
| 桥的 `insert` 抛 `RuntimeException` / `NoSuchMethodError` | 同上 |
| `handles` 抛 `NoClassDefFoundError` | 同上 |
| 出错之后再调用 | 不再调用桥，空，失败回调不再触发 |
| 两个桥，第一个不认领 | 第二个处理 |
| 状态 `FORCED` | 与 `ACTIVE` 一样调用 |
| 传给桥的是拷贝 | 桥里改它不影响调用方的原栈 |
