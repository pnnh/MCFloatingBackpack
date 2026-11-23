# 契约：客户端挂接、识别、启用条件、依赖与文档

对应 FR-001、FR-012～FR-014。

## 1. 挂接点 `QuickTransferInput.beginFromScreen`

```java
// 现有的前置检查不变：左键、Ctrl、不按 Shift、面板可见、屏幕支持、光标为空
Optional<NetworkEntry> entry = NetworkTerminalClients.entryAt(screen, mouseX, mouseY);
if (entry.isPresent()) {
    PacketDistributor.sendToServer(new BackpackNetworkTakePayload(menu.containerId, entry.get().what(), entry.get().handle()));
    return true;                      // 吞掉这次按下；不调用 start(...)，所以没有拖动
}
// 原来的 screenSlotAt / start 路径
```

`ScreenSlots.slotAt` 是 `screenSlotAt` 里同一个命中测试，AE2 的识别器用它；`screenSlotAt` 自己不变。

## 2. 注册表与启用条件 `NetworkTerminalClients`

- `register(String modId, NetworkTerminalClient client)`；`entryAt(screen, slot)`：`MenuOwner.ownerOf(menu)` 找到该模组的识别器；**启用条件**（全部满足才调用识别器）：`ClientCompatVersions.inRange(表里该模组的行)`；`CompatConfig.policy().specificAdaptation(modId)`。不满足 → 空。
- 注册在客户端初始化里（`ClientSetup`），只在 `ModList.get().isLoaded(modId)` 时调用对应模组的 `registerClient`。

## 3. 识别器

接口 `NetworkTerminalClient.entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY)`（鼠标是 GUI 缩放后的坐标，与 `beginFromScreen` 收到的相同）。

**集成终端**（`IntegratedTerminalsClient`，`@OnlyIn(CLIENT)`；条目不是菜单格子，见 research R14）：

```
screen instanceof ContainerScreenTerminalStorage<?, ?> terminal
tab = terminal.getSelectedClientTab() → TerminalStorageTabIngredientComponentClient（非 ...ItemStackCraftingClient），getIngredientComponent() == ITEMSTACK
index = terminal.getStorageSlotIndexAtPosition(mouseX, mouseY) >= 0
cell = tab.getSlot(terminal.getMenu().getSelectedChannel(), index)  # Optional<TerminalStorageSlotIngredient>
cell.getInstance() instanceof ItemStack stack, 非空
→ NetworkEntry(stack.copyWithCount(1), 0)
```

**AE2**（`Ae2Client`，`@OnlyIn(CLIENT)`）：

```
screen instanceof MEStorageScreen<?>
slot = ScreenSlots.slotAt(screen, mouseX, mouseY) instanceof RepoSlot repo ; entry = repo.getEntry() != null
entry.getWhat() instanceof AEItemKey key ; entry.getStoredAmount() > 0
→ NetworkEntry(key.toStack(1), entry.getSerial())
```

## 4. 范围表、加载声明、依赖、README

- `ThirdPartyTable`：新行 `ae2`（显示名 `Applied Energistics 2`，段 `[19.2.0, 19.3.0)`，`also` 为空）；`ALL` 含两行；`validate` 照旧通过。
- `neoforge.mods.toml`：`ae2` 可选依赖，`type="optional"`、`versionRange="[0,)"`、`ordering="NONE"`、`side="BOTH"`（注释同 058 的那几条）。
- `build.gradle`：`compileOnly("maven.modrinth:ae2:19.2.20") { transitive = false }`（与已有 `localRuntime "maven.modrinth:ae2:19.2.20"` 同版本）；若编译需要 GuideME，再加同样的 `compileOnly("maven.modrinth:guideme:21.1.22") { transitive = false }`。
- 防漂移测试：`SLUG` 表加 `ae2 ↔ ae2`；`checkGradle` 要求 `ae2` 的 `compileOnly` 带 `transitive = false`、与 `localRuntime` 同版本且落在 `ae2` 行的段内；`checkModsToml` 的模组集合自动包含 `ae2`；`checkReadme` 自动要求 README 含 `Applied Energistics 2` 与 `[19.2.0, 19.3.0)`。
- README 兼容表（中英文）：
  - Applied Energistics 2：`Panel → its container`：yes, into an ME terminal（通用路径）；`Its container → panel`：**yes, Ctrl+click an entry in the terminal's grid while the panel is visible**；`How`：存入走通用路径（19.2.20 验证），取出是本模组的主动兼容；`Verified versions`：`[19.2.0, 19.3.0)`。
  - Integrated Terminals：`Its container → panel`：**yes, Ctrl+click an entry on the item storage page**；其余不变。
  - 删除/改写 058 的"反向暂不支持"类措辞（EN/CN 各一处，`ThirdPartyDriftTest` 的断言同步）。

## 修订（真机反馈后）：手里拿着的条目（`held` / `release`）

`NetworkTerminalClient` 另有两个默认方法：`Optional<NetworkHeld> held(screen)`（玩家用普通左键拿起、而该终端不把它存成菜单携带物时，手里的物品与数量；默认空）与 `void release(screen)`（放开它）。只有集成终端实现：页对象的 `getActiveSlotId() >= 0`、`getActiveSlotQuantity() > 0`、`getSlot(channel, activeSlotId)` 的物品 → `NetworkHeld(物品×1, 数量)`；`release` = `resetActiveSlot()`。`NetworkTerminalClients.held/release` 与 `entryAt` 同样受版本范围、FULL 级别与"出错即关闭"约束。

`NetworkHeldDrop`（`client/panel/`）：`heldOn(minecraft, screen)`（面板可见、屏幕是开着的容器界面、菜单携带物为空、有 `held`）供渲染多画一份；`tryDrop(screen, button, x, y)`：左键、面板正文（不含拖动条、关闭键、缩放角、没有确认框）、有 `held` → 发 `BackpackNetworkTakePayload(containerId, what, 0, amount)` 并 `release`，返回 true（事件取消，按下记为已吞，松开也被吞）。渲染：光标为空时用 `held` 的物品在 `CURSOR_Z` 画一份。
