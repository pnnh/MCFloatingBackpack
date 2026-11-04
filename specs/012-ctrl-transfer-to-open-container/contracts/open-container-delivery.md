# Contract: 转出到打开着的容器

011 的网络包（`BackpackQuickTransferPayload`）和客户端手势契约**不变**，见 `specs/011-ctrl-quick-transfer/contracts/quick-transfer.md`。本契约只描述服务端处理 `TO_INVENTORY`（"转出"）方向时多出来的步骤。

## 1. 服务端流程（`handleServer` 的 `TO_INVENTORY` 分支）

011 的公共校验不变（背包有效、光标为空、`QuickTransferRequest.validate`）。通过之后，对包里的每个背包下标（按包内顺序）：

1. 取该槽位的栈；为空则跳过。
2. 判断"有没有打开的容器"（data-model）。
   - **没有** → 011 原路径：`QuickTransferLogic.moveInto` 放进玩家物品栏 0–35，剩余写回背包槽位。
   - **有** → `OpenContainerDelivery.deliver(...)`：
     1. 选临时槽位（data-model 的优先级）；无可用 → 该栈原样留在背包。
     2. 如需换出：取出临时槽位里的 X 暂存。
     3. 把栈放进临时槽位。
     4. 循环 `menu.quickMoveStack(player, menuIndex)`：照 `doClick`——返回空或槽位里剩下的不再是同种物品就停；有迭代上限。
     5. `finally`：取回临时槽位里剩下的内容作为 leftover；若换出过，把 X 放回临时槽位；捕获到的异常只记日志。
     6. 守恒检查，不通过记一条错误日志（含菜单类名），不回滚。
     7. leftover 写回背包槽位。
3. 全部处理完后与 011 相同：`ref.saveInventory`、`player.getInventory().setChanged()`、`player.containerMenu.broadcastChanges()`、`BackpackNetwork.sendFullSync`。

## 2. 不变量（必须有单元测试，用假菜单）

- 物品总数守恒：转移前后，该种物品在（菜单所有槽位 + 光标 + 背包）里的总数相等。
- 换出的 X 一定被放回原槽位，且是**同一个 `ItemStack` 对象**。
- 容器全满或拒收时，栈原样留在背包，不回退到玩家物品栏。
- `quickMoveStack` 抛异常时，我们放进临时槽位的那部分被取回，X 被放回，没有异常外泄。
- `quickMoveStack` 永远返回同一个非空栈时，循环会在上限处终止。
- 物品栏满且没有任何符合条件的换出槽位（比如都是手持槽位或 `mayPickup` 为假）时，本次不转移。
- 临时槽位选择在同一输入下结果确定。

## 3. 客户端契约

无变化。客户端不判断容器，不指定目标；011 的手势、命中测试、界面白名单、每帧驱动都不动。

## 4. 对 011 契约的修订

011 `contracts/quick-transfer.md` 里关于 `TO_INVENTORY` 的描述"目标是玩家物品栏 0–35"，在 012 之后改为"没有打开的容器时目标是玩家物品栏 0–35；有打开的容器时目标是该容器（按容器自己的 Shift+点击规则）"。实现阶段给 011 的该文件加【012 修订】。

## 5. 已知限制

- 第三方菜单在 `quickMoveStack` 内部抛异常时，其内部状态可能已部分改变，我们只保证自己放进临时槽位的部分被取回，不回滚第三方状态。
- 物品栏满时的"换出再换回"对精妙背包/旅行者背包的安全性是推断，必须实测。
- 工作台等菜单放不下时会在玩家物品栏内部挪动，结果是物品留在玩家物品栏而不是容器（总数守恒）。

---

# 补充（2026-11-05）：反向——容器 → 浮动背包

## 5. 新网络包 `BackpackTakeFromContainerPayload`（客户端 → 服务端）

注册在 `BackpackNetwork`，协议版本 `"4"` → `"5"`。

| 字段 | 类型 | 说明 |
|---|---|---|
| containerId | VarInt | 客户端界面对应的菜单 id |
| menuSlots | VarInt 列表 | 菜单槽位下标；读取时数量超过 `QuickTransferRequest.MAX_MENU_SLOTS`（取 256）直接断开解码 |

### 服务端处理（`handleServer`）

1. 玩家必须是 `ServerPlayer`；`BackpackRef.resolve` 得到仍然有效的背包，否则 `sendSession(player, false, null)` 并返回。
2. 光标必须为空；`player.containerMenu != player.inventoryMenu`；`player.containerMenu.containerId == containerId`；否则忽略。
3. 校验 `menuSlots`：无重复、每项在 [0, menu.slots.size())、数量不超过上限；任一不合法 → 整包忽略。
4. 对每个下标（包内顺序）：槽位必须 `container != 玩家 Inventory`；`mayPickup` 与 `mayPlace(自己的物品)` 都为真，否则跳过；按 data-model 的状态转移取出、放进背包、放回剩余。
5. `ref.saveInventory`、`player.getInventory().setChanged()`、`player.containerMenu.broadcastChanges()`、`BackpackNetwork.sendFullSync`。
6. 守恒检查失败或回退路径被触发时，记日志（含菜单类名与物品）。

### 不变量（必须有单元测试，用假槽位）

- 物品总数守恒。
- 背包放不下的部分留在原槽位；原槽位拒绝放回时依次回退，不丢物品。
- 输出类槽位（`mayPlace` 恒假）与 `mayPickup` 为假的槽位被跳过且不被改动。
- 堆叠 >64 的槽位只转走背包放得下的数量。
- 校验失败（id 不符、越界、重复、超限、玩家物品栏槽位）整包不改任何物品。

## 6. 客户端手势契约补充

- 触发条件在 011 的基础上：`Screen.hasShiftDown()` 为假（Shift 让路）；界面是任意 `AbstractContainerScreen` 且不是 `CreativeModeInventoryScreen`；容器槽位命中要求 `slot.container != 玩家 Inventory`、`isActive()`、`hasItem()`、`mayPickup`、`mayPlace(自己的物品)`。
- 命中优先级不变：面板范围内按面板槽位（FR-015），其余才看界面里的槽位。
- 每帧每种来源至多发一个包（玩家物品栏槽位走 011 的包，容器槽位走新包）。
- 按下事件与松开事件的处理沿用 011：取消按下，登记 `ConsumedPressTracker`，拖动的结束判定靠每帧轮询。

## 7. 对 011 契约的修订

011 的"界面类型"一行（"类名以 `net.minecraft.` 开头…"）与"外部容器自身槽位不参与"被本节取代；实现阶段给 011 的相应文件加【012 修订】。
