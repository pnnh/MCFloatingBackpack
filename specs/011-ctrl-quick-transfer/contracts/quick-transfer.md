# Contract: 快速转移（网络包 + 手势）

## 1. 网络包 `BackpackQuickTransferPayload`（客户端 → 服务端）

注册在 `BackpackNetwork`，协议版本 `"3"` → `"4"`（新增包必须升版本）。

| 字段 | 类型 | 说明 |
|---|---|---|
| direction | byte | 0 = `TO_BACKPACK`，1 = `TO_INVENTORY` |
| slots | VarInt 列表 | 来源下标，规则见 data-model；读取时数量超过 108 直接断开解码（防止恶意超大数组） |

（实现时去掉了原先设想的 `sourceOrdinal`/`invIndex` 字段：服务端本来就以 `BackpackRef.resolve` 的实时结果为准，声明的来源只有在与实时结果一致时才被采用，等于没有信息量，所以不带。）

### 服务端处理（`handleServer`，必须在服务端线程）

1. 玩家必须是 `ServerPlayer`；`BackpackRef.resolve(player)` 必须得到仍然有效的背包（注意：服务端没有"面板是否打开"的状态，现有 `BackpackClickPayload` 的 `panelOpen` 只是客户端自报，所以本包**不带也不检查**这个字段；能校验的是"背包仍然可用"）（沿用 `BackpackClickPayload` 的"实时解析优先，声明的来源只在一致时采用"），否则发 `sendSession(player, false, null)` 并返回，不改任何物品。
2. 光标必须为空：`player.containerMenu.getCarried().isEmpty()`，否则忽略（FR-007）。
3. 校验 `slots`：无重复、每项在范围内、数量不超过上限；任何一项不合法 → 整包忽略。
4. 加载背包库存（`ref.loadInventory`）。
5. 对每个来源下标（按包内顺序）执行搬运算法；`TO_BACKPACK` 跳过当前背包自己所在的物品栏下标。
6. 保存背包（`ref.saveInventory`）；对物品栏调用 `setChanged()`；`player.containerMenu.broadcastChanges()`。
7. `BackpackNetwork.sendFullSync(player, ref)`（一次，不按格发更新）。

### 不变量（必须有单元测试）

- 物品总数守恒；任何槽位不超过堆叠上限。
- 放不下的部分留在来源槽位。
- 校验失败时不修改任何槽位。
- 不会把当前背包物品本身转进它自己的存储。

## 2. 客户端手势契约

### 2.1 触发条件（全部满足才处理，否则事件原样放行）

| 条件 | 说明 |
|---|---|
| `Screen.hasControlDown()` | Windows/Linux 为 Ctrl，macOS 为 Command（见 research Decision 5） |
| 鼠标左键按下 | 右键、中键不处理 |
| 浮动面板可见 | 隐藏时 Ctrl 手势不生效（FR-008） |
| 光标为空 | 界面里 `menu.getCarried().isEmpty()`；HUD 下恒为空（FR-007） |
| 命中有效槽位 | 面板范围内 → 面板槽位（FR-015）；否则（需要打开了受支持的界面）→ 玩家物品栏槽位 |
| 界面类型（**只约束物品栏一侧的命中**） | 物品栏槽位的命中要求：全限定类名以 `net.minecraft.` 开头且不是 `CreativeModeInventoryScreen` 的容器界面（FR-011、FR-016）。**面板一侧的命中不受界面类型限制**：没有界面、原版界面、第三方界面打开时，只要面板可见都可用（FR-002；目标是玩家真实 `Inventory`，不触碰第三方菜单） |

### 2.2 命中规则

- **面板槽位**：沿用 `PanelSlots.indexAt(state, x, y)`，并检查 `< ClientBackpackView.slotCount()`（虚拟网格末行可能超出真实格数，现有代码同样处理）。
- **物品栏槽位**：遍历 `screen.getMenu().slots`，`slot.container == 玩家 Inventory && slot.getContainerSlot() < 36 && slot.isActive()`，且鼠标在 `(getGuiLeft()+slot.x, getGuiTop()+slot.y)` 起 16×16 内。
**空起点**：Ctrl+左键按在**空的有效槽位**上也算手势起点（方向由该槽位所在侧决定），该槽位本身没有东西可转、不发请求，之后滑过的槽位照常处理；这样玩家可以从一行的空位开始滑过去。按下事件同样被取消（保持一致，且原版对空槽位的拿起本来就什么都不做）。

### 2.3 事件消费

- 命中有效槽位的 Ctrl+左键**按下**事件：取消（防止原版 `PICKUP`），并在 `ConsumedPressTracker` 登记，保证其**松开**事件也被吞（FR 无冲突；沿用 BUG-005-1 的修复）。
- 其余事件原样放行；任何 Shift 判断不触碰。

### 2.4 拖动

见 data-model 的 `GestureState`。每帧至多一包（**界面打开时只由 `ScreenEvent.Render.Post` 驱动，HUD 下只由 `RenderGuiEvent.Post` 驱动**，与现有 `onRenderGui` 在界面打开时提前返回的做法一致，否则一帧会触发两次）；每个来源下标一次拖动只处理一次；方向固定为起点所在侧；结束条件 = Ctrl 松开 / 左键松开 / 面板隐藏 / 界面打开或关闭 / 光标拿起了物品。

## 3. 不在契约内（本阶段不做）

- 外部容器（箱子等）自身的槽位、第三方界面、Shift+双击、创造模式物品栏、盔甲/副手/合成槽。
