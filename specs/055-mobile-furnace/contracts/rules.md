# Contract: 规则、引擎、菜单、写回、点击

## 1. `MobileTool`

增加 `FURNACE((byte) 1)`；`fromByte` 对未知字节返回 null（不变）。线上字节：工作台 0、熔炉 1。

## 2. 打开决策

`ToolOpenPlanner.decide` 不改。熔炉的 `OPEN` 分支在 `MobileTools.open` 里多一步（见 §5）。

## 3. 烧制引擎（纯逻辑）

```text
FurnaceEngine.tickOnce(core, rules)           # 原版 serverTick 的逐刻移植（不含 setBlock）
FurnaceEngine.advance(core, ticks, rules)     # ticks ≥ 0；等价于 tickOnce 循环 ticks 次
```

契约：
- **等价**：对任意输入，`advance(core, n)` 的结果（三格、L、LD、P、T、`recipesUsed`）与 `tickOnce` 循环 `n` 次逐字段相等。
- **开销**：迭代次数 ≤ 原料件数 + 燃料件数 + 常数，与 `n` 无关；`n = 0` 什么也不做。
- **不丢不造**：除"烧一件（原料 −1、产物 +1）"与"燃料用掉一份（−1，留下剩余物）"外不改变任何物品；产物不超过堆叠上限。
- **逐刻的性质保持**：点燃的燃料即使原料没了也烧完；点着但不能烧时燃料白烧、进度归零；熄灭时进度每刻 −2 至 0（夹在 0 与 T 之间）；`T` 只在换原料（容器的 `setItem(0)`）与烧完一件时刷新。

## 4. 熔炉状态容器与菜单

`FurnaceStateContainer`（3 格）：
- `setItem(0, 新栈)`：新栈与旧栈不是"同物品同组件"时，`cookTotal = 配方时间或 200`、`cookProgress = 0`（原版 `AbstractFurnaceBlockEntity.setItem` 的行为）；每次内容变化调 `setChanged()`。
- `canPlaceItem(2, _) = false`；燃料格：燃料或（桶 且 燃料格里现在不是桶），与原版一致。
- 实现 `StackedContentsCompatible`（配方书转移要用）。

`MobileFurnaceMenu extends FurnaceMenu implements MobileMenu`：
- `tool()` = `FURNACE`。
- `stillValid(player)` = 按 B 解析出的背包存在，**身份等于打开时的身份**，且右区有 `mobile_furnace`。
- `broadcastChanges()`：`advance(core, now − lastAdvance)`，`lastAdvance = now`，再 `super.broadcastChanges()`；一刻内重复调用经过 0 刻。
- `removed(player)`：推进到现在，写回，`super.removed`（原版不清容器，三格留在物品里）。
- 产物槽：`slots[2]` 换成 `FurnaceResultSlot` 的子类，`checkTakeAchievements` 里发经验（§6）。
- `ContainerData`：`get(0)` = `litTime`，但 `litDuration > Short.MAX_VALUE` 时返回 `floor(litTime / litDuration × Short.MAX_VALUE)`；`get(1)` = `min(litDuration, Short.MAX_VALUE)`；`get(2)` = `cookProgress`；`get(3)` = `cookTotal`；`set` 写回对应字段；`getCount() = 4`（与 NeoForge 的原版实现一致，保证火苗与箭头显示一致）。

## 5. 打开（服务端）

`MobileTools.open(player, FURNACE)`：
1. `ref = BackpackRef.resolve(player)`；`BackpackGate.acquire(player, ref)`：
   - 为空（受保护的背包，或不再是背包）→ **不打开**；受保护时给玩家发 `message.mcfloatingbackpack.furnace.protected`；
2. 读 `ref` 的右区第一个 `mobile_furnace` 的 `FurnaceState`（无组件 = 空）；
3. 记下背包身份；`advance(core, max(0, 当前游戏时间 − stamp))`；
4. 写回一次（stamp = 当前游戏时间）；
5. `player.openMenu(SimpleMenuProvider(…, Component.translatable("container.furnace")))`。

## 6. 经验（取走产物时）

对 `recipesUsed` 的每一条 (配方 id, 个数 n)：配方存在才处理；`total = n × 配方经验`；`i = floor(total)`，`f = total − i`；`f ≠ 0 且 random < f` 则 `i++`；`ExperienceOrb.award(level, player.position(), i)`；收集配方后 `player.awardRecipes(...)`，对每个调 `player.triggerRecipeCrafted(配方, 三格内容)`；最后 `recipesUsed` 清零并写回。`FurnaceExperience`（纯）负责整数部分与"小数部分进位的概率"这一步（把随机数作为参数传入，便于测试）。

## 7. 写回

`MobileFurnaceStore.save(player, identity, core, stamp)`：
- `ref = BackpackRef.resolve(player)`；背包身份不等于 `identity` 或没有 → 什么也不写；
- `inv = BackpackGate.acquire(player, ref)`，空 → 什么也不写；
- 在 `inv.handler(RIGHT)` 里找第一个 `mobile_furnace`，把 `FurnaceState(core, stamp)` 设到该物品栈上；`ref.saveInventory(player, inv)`。
- 不发网络包（客户端的面板不需要这份数据）。

## 8. 右区"各一个"

与 054 同口径：服务端 `BackpackClickPayload` 与客户端预览 `FloatingPanelInput` 各加一条 `ToolSingleRules.check(RIGHT, …, stack -> stack.is(MOBILE_FURNACE))`；被拒时提示 `message.mcfloatingbackpack.limit.mobile_furnace`；与垃圾桶、唱片机、工作台的限制互相独立。

## 9. 点击（客户端）

`FloatingPanelInput` 的"点右区工具"：光标为空、左键、无修饰键、右区、物品是 `mobile_furnace` → `PacketDistributor.sendToServer(new OpenToolPayload(MobileTool.FURNACE.id()))`，返回 true（右键仍是拿起）。左区、下区没有这个分支。

## 10. 版本

- `DataRevision.CURRENT = 6`；文字说明加"6 added the mobile furnace…"。
- `BackpackNetwork` 协议号保持 `"20"`，不新增载荷。
