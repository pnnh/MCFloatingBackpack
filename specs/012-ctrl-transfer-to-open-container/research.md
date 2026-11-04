# Research: 012-ctrl-transfer-to-open-container

"依据"一栏写的是读 NeoForge 21.1.251 源码（`build/moddev/artifacts/neoforge-21.1.251-sources.jar`）、读本仓库代码、或用 `javap` 检查精妙背包 / 旅行者背包 jar 得到的事实；没有核实的地方明确写"推断"。

## Decision 1: "有没有打开容器"由服务端的 `containerMenu` 判定

**Decision**: 服务端处理"转出"请求时看 `player.containerMenu`：它不是 `player.inventoryMenu`，并且其中至少有一个 `slot.container != player.getInventory()` 的槽位，就算"有打开的容器"；否则按 011 转到玩家物品栏。

**Rationale**:
- 依据（`Player` 源码 161–162 行）：`inventoryMenu` 与 `containerMenu` 都是公开字段；没开界面或只开着玩家自己的物品栏界面（按 E）时 `containerMenu == inventoryMenu`，这正是 spec 假设里"不算容器"的两种情况。
- 依据（`ChestMenu`、`Slot` 源码）：箱子区的槽位 `container` 是箱子的 `Container`，玩家区槽位的 `container` 是玩家 `Inventory`；精妙背包 `addPlayerInventorySlots(Inventory, …)` 用真实 `Inventory` 创建玩家槽位（011 research 已查），所以"非玩家 `Inventory` 的槽位"可以通用地识别外部槽位，不需要认识任何模组的类。
- 容器在请求到达前被关闭，由于服务端按**处理那一刻**的 `containerMenu` 判定，自然落到"没有容器 → 物品栏"（spec FR-011），不需要额外处理。
- 011 的客户端手势和网络包完全不变（spec 假设"不改客户端手势"）。

## Decision 2: 复用菜单自己的 `quickMoveStack`，通过一个临时的玩家物品栏槽位

**Decision**: 要把一个背包栈存入容器时：把它放进一个**玩家物品栏槽位**（临时槽位），调用 `menu.quickMoveStack(player, 该槽位的菜单下标)`——这正是玩家在容器里 Shift+点击自己物品栏物品时走的那条路——之后把没有被搬走的部分取回背包。

**Rationale**:
- 依据（`ChestMenu.quickMoveStack` 源码）：对玩家区槽位是"移进容器区"（`moveItemStackTo(…, 0, containerRows*9, false)`）；`CraftingMenu.quickMoveStack` 对玩家区槽位是"先试合成格 1–10，不行就在玩家物品栏内部挪动"。这就是 spec 里"结果与 Shift+点击一致"的依据。
- 依据（`javap` 检查两个模组）：精妙背包的 `StorageContainerMenuBase.quickMoveStack` 带自己的规则（`getSlotUpgradeContainer`、`isUpgradeSlot`、`mergeSlotStack`、`IUpgradeItem.getCleanedUpgradeStack`、合成认领）；旅行者背包的 `BackpackBaseMenu.quickMoveStack` 带工具槽（`ToolSlotItemHandler.isValid`）、记忆槽（`checkMemorySlots`）、升级槽（`UpgradeSlotItemHandler.shiftClickToBackpack`）、合成升级的 Shift 合成。这些规则自己重写既费力又容易与模组不一致；复用等于零模组专属代码（宪法 IV：不引用第三方类型）。
- 依据（`AbstractContainerMenu.doClick`，QUICK_MOVE 分支）：原版自己就是"反复调用 `quickMoveStack`，直到返回空或槽位里剩下的不再是同一种物品"。我们照这个循环做。

**Alternatives considered**: 见 Decision 7。

## Decision 3: 临时槽位怎么选，物品栏满时怎么办

**Decision**: 在 `menu.slots` 里找"玩家槽位"（`container == 玩家 Inventory` 且 `getContainerSlot()` ∈ [0,35]），按这个顺序选：
1. **空槽位**，且 `slot.mayPlace(栈)` 为真；
2. 没有空槽位（物品栏满）时，选一个**有物品的主物品栏槽位**（容器槽位 9–35），要求 `mayPickup(player)` 和 `mayPlace(栈)` 都为真：先把原物品 X 取出暂存，放入我们的栈，调用后把 X 放回；**永远不选当前手持的快捷栏槽位**（`Inventory.selected`）；
3. 都没有 → 本次不转移，物品留在背包里。

**Rationale**:
- spec 要求"物品栏已满也能存入容器"（FR-008），所以必须有"换出再换回"。
- 依据（`javap`）：精妙背包用 `addStorageItemSafeSlot` 创建玩家槽位（"安全槽"，保护打开着的那只背包物品所在格）；旅行者背包的 `BackpackItemMenu.clicked` 拿 `getScreenID`、`getSelected` 做比较，保护"手持的那只背包"。所以**不选手持槽位**，并要求 `mayPickup`，是为了避开模组保护的那一格。这是对两个模组实现的**推断**，不是文档保证，所以在 quickstart 里专门列了"物品栏满 + 手持模组背包"的场景实测。
- 选主物品栏（9–35）而不是快捷栏，是因为玩家手持和快捷栏位置更常被模组特殊对待。

**Risk**: 换出的 X 若恰好是模组背包物品本身（打开着的那只放在主物品栏时），临时把它拿出再放回。调用只在服务端一个 tick 内完成，放回的是同一个 `ItemStack` 对象，所以模组持有的引用不失效（**推断**，要实测）。

## Decision 4: 照原版的循环，并加上限

**Decision**: 调用 `quickMoveStack` 的循环完全照 `doClick`：`res = quickMoveStack(...)`，`while (!res.isEmpty() && 槽位里的栈与 res 是同种物品) res = quickMoveStack(...)`；另外加迭代上限（取"栈数量+4"与一个硬上限的较小值），防止有缺陷的模组菜单让它死循环。

**Rationale**: 依据 `doClick` 的循环（源码 391–397 行）；上限是防御性的，原版自己没有。

## Decision 5: 异常与守恒的处理（FR-012）

**Decision**:
- 调用包在 `try { … } catch (RuntimeException e)`；无论是否抛异常，`finally` 里都执行"取回临时槽位里剩下的内容 → 放回背包 → 把 X（如果换出过）放回原槽位"。异常只记日志，不向上抛（不让一个模组的 bug 把玩家踢下线）。
- 每次转移后做一次**守恒检查**：转移前后，"该种物品在菜单所有槽位 + 光标 + 背包里的总数"必须相等；不相等就记一条错误日志（含菜单类名，便于定位是哪个模组）。**不尝试回滚**第三方菜单的内部状态。

**Rationale**:
- spec FR-012 要求模组容器异常时物品不丢不复制。抛异常发生在 `quickMoveStack` 内部时，第三方菜单自己的状态可能已部分改变，我们无法可靠回滚；能保证的是**我们自己放进临时槽位的那部分**被取回。这是已知限制，写进 DELIVERY。
- 守恒检查既是运行期防御，也是后续排错的依据。

## Decision 6: 算法做成泛型纯逻辑，用假菜单测试

**Decision**: 新增 `OpenContainerDelivery`（不引用 Minecraft 类型），对"菜单"的抽象接口 `MenuAccess<T>` 操作：列出玩家槽位的视图（菜单下标、是否空、`mayPickup`、`mayPlace`、是否手持槽）、读写槽位、`quickMove(下标)`。生产代码提供基于 `AbstractContainerMenu` 的适配器，单元测试提供**假菜单**：箱子式（把玩家槽位的物品搬进容器区）、工作台式（容器区放不下时在玩家区内部挪动）、拒收式（`quickMove` 什么都不做）、抛异常式、永不收敛式。

**Rationale**: 与 011 的 `QuickTransferLogic` 同一做法（测试跑真实实现，而不是镜像）。这些情形正是第三方菜单可能出问题的地方，用假菜单可以在不启动游戏的情况下覆盖：临时槽位选择、换出换回、循环上限、异常、守恒。

## Decision 7: 被否决的方案

- **B：自己遍历容器槽位，按 `mayPlace` 和堆叠上限放**：语义清晰、可单测，但不懂各模组的特殊路由（精妙背包升级槽、旅行者背包工具槽/记忆槽、熔炉原料与燃料的区分、工作台先试合成格），结果会与"Shift+点击"不一致，违反 SC-002。
- **混合（有空槽用 A，满了用 B）**：行为随物品栏满不满而变，玩家无法预期，也违反"结果与 Shift+点击一致"。
- **客户端判断容器并发送"目标"**：违反宪法 II，也会让客户端要认识每个模组的菜单。
- **Mixin 注入浮动背包槽位**：与 011 同样的理由，否决。

## Decision 8: 不改协议，不改客户端

**Decision**: `BackpackQuickTransferPayload` 的字段和协议版本（`"4"`）都不变；方向值 `TO_INVENTORY` 的含义从"转到玩家物品栏"扩展为"转出背包（目的地由服务端按当前打开的菜单决定）"。客户端手势、命中测试、每帧驱动一行不改。

**Rationale**: spec 已确定"客户端只发请求，不指定目标"。不改协议还有一个好处：011 的客户端与 012 的服务端互相兼容，不会出现版本不匹配。**命名问题**：`TO_INVENTORY` 这个名字之后不再准确。plan 选择**保留常量名**并在 `Direction` 的 Javadoc 里说明含义（避免为了改名而改协议常量、测试和 011 文档），以后如果引入第三个方向再统一重命名。

## Decision 9: 对 011 的修订

011 的 FR-018（"转出只进玩家物品栏"）和澄清第二条被本 feature 取代。实现阶段要在 011 的 `spec.md`、`contracts/quick-transfer.md`、`DELIVERY.md` 里加【012 修订】标注，并在 011 的"已知限制"中说明"外部容器自身的格子不参与转移"仍然成立——012 只改了"转出去哪"，没有改"能否从容器里转进来"。

## 仍然不确定、需要实测的

- 临时槽位在 `runClientCompat` 下精妙背包、旅行者背包里是否真的安全（尤其"物品栏满 + 手持模组背包"）：推断，必须实测。
- 第三方菜单的 `quickMoveStack` 对"不是由点击包触发的调用"是否有隐含假设：未知，实测。
- 精妙背包里堆叠数超过 64 的存储槽对守恒检查的影响：守恒检查按总数计，不应受影响，需实测。
- 工作台、熔炉、铁砧、酿造台、村民交易这类"非存储"界面的行为：与 Shift+点击一致，但没有逐一在游戏里看过；quickstart 里各列一项。

---

# 补充（2026-11-05）：反向——容器 → 浮动背包

用户验证正向可用后要求补上反向（spec User Story 5，FR-017～FR-023）。下面是对应的研究；依据同样来自源码或 `javap`。

## Decision 10: 反向不能复用 `quickMoveStack`，要直接取出

**Decision**: 把容器槽位里的物品取出，放进浮动背包，放不下的放回原槽位。

**Rationale**:
- 依据（`ChestMenu.quickMoveStack`）：对**容器区**槽位，原版 Shift+点击是把物品"移进玩家物品栏区"（`moveItemStackTo(…, containerRows*9, slots.size(), true)`），目标永远是玩家物品栏，不可能是浮动背包。所以正向能借用的"菜单自己的规则"在反向没有对应物。
- 所以反向是：`Slot.safeTake(count, count, player)`（会检查 `mayPickup`、调用 `onTake`）取出 → 用 011 的 `QuickTransferLogic.moveInto` 放进浮动背包 → 剩余用 `Slot.safeInsert` 放回原槽位；`safeInsert` 仍放不下时依次回退到 `slot.set`（原槽位刚被我们取空）、玩家物品栏、`player.drop`，保证不丢。
- 依据（`Slot` 源码）：`safeTake`、`safeInsert`、`tryRemove` 都是原版槽位的公开方法，第三方槽位（精妙背包的存储槽、旅行者背包的 `SlotItemHandler`）覆盖的是 `getItem/set/remove/mayPlace`，因此这些调用对它们同样有效（**推断**，要在 `runClientCompat` 下实测）。

## Decision 11: 用"能不能把这件物品放回去"排除输出类槽位

**Decision**: 一个槽位只有在 `slot.mayPickup(player)` 为真、且 `slot.mayPlace(它当前的物品)` 为真时才能作为来源。

**Rationale**: 依据（`Slot` 源码 202–204 行）：原版自己就有这个概念——`allowModification(player)` 定义为 `mayPickup(player) && mayPlace(getItem())`，而 `tryRemove` 在它为假时**拒绝部分取出**（只允许整堆取出）。所以实现直接用 `slot.allowModification(player)`，不另造判据。输出类槽位（合成输出、熔炉输出、铁砧/锻造台输出、村民交易输出）取走会触发合成消耗、交易或经验，它们的共同点是 `mayPlace` 对任何物品都返回假。普通存储槽位（箱子格、精妙背包的存储槽与升级槽、旅行者背包的存储槽）都接受自己里面的物品。这样不需要认识任何具体的槽位类型，对第三方通用。**推断**，需要实测：精妙背包的"锁定/过滤"槽位是否会误判；旅行者背包的工具槽（只接受工具）里取出工具是否正常。

## Decision 12: 新网络包，带菜单 id；协议版本 "4" → "5"

**Decision**: 新增客户端→服务端包 `BackpackTakeFromContainerPayload(containerId, menuSlots[])`，`menuSlots` 是**菜单槽位下标**（不是物品栏下标）。服务端要求 `containerId == player.containerMenu.containerId`，否则整个包作废；并校验下标合法、不重复、不是玩家物品栏槽位、数量不超过 `menu.slots.size()`、光标为空。协议版本升到 `"5"`。

**Rationale**: 011 里用玩家物品栏下标（0–35）是因为那一侧的槽位在每个菜单里编号不同但 `getContainerSlot()` 相同；容器自己的槽位没有这种统一编号，只能用菜单槽位下标，因此必须带 `containerId` 防止客户端和服务端的菜单已经不是同一个（容器在请求途中被关闭或换了）。新增包改变了协议，必须升版本（与 011 同样的理由）。

**Alternatives considered**: 在 011 的包里加一个方向值 2 和 containerId 字段：会改变已验证的包格式；新增独立的包更清晰，011 的路径完全不动。

## Decision 13: 客户端：拿掉界面类名白名单，改为"任意容器界面，排除创造模式"；Shift 让路

**Decision**:
- 命中测试扩展到容器自己的槽位：`slot.container != 玩家 Inventory`、`slot.isActive()`、`slot.hasItem()`、`mayPickup`、`mayPlace(slot.getItem())`（Decision 11），且菜单不是玩家自己的 `inventoryMenu`。
- 011 的"类名以 `net.minecraft.` 开头"白名单取消，改为"任意 `AbstractContainerScreen`，排除 `CreativeModeInventoryScreen`"，这样精妙背包、旅行者背包的界面里，容器槽位和其中的玩家物品栏槽位都能处理。
- 按着 Shift（`Screen.hasShiftDown()`）时一律不处理：这是**011 唯一一处读取 Shift**，目的是让路，而不是占用——保留 Ctrl+Shift 给原版和模组。

**Rationale**:
- 依据（`javap`）：精妙背包的 `StorageScreenBase.mouseClicked` 自己读取 `hasControlDown`，附近也读取 `hasShiftDown`，说明 Ctrl 与 Shift 的组合在它的界面里可能有含义（具体含义**未知**）；Shift 让路是对这种冲突的保守处理。单独的 Ctrl+点击在它的槽位上会被我们先处理（`Pre` 事件先于界面的 `mouseClicked`），可能与它自己的某个 Ctrl 用法冲突，需要实测。
- 依据（`StorageScreenBase`）：它有公开的 `findSlot`、重写了 `isHovering`，说明它自己的命中测试可能与 `getGuiLeft()+slot.x` 不完全一致；我们仍用通用的 `slot.x/y + guiLeft/Top + isActive()`，在 `runClientCompat` 下实测是否对得上（尤其是被滚动隐藏的槽位是否 `isActive()` 为假）。
- 取消白名单会让 011 的 FR-016 失效，用户已明确要求（Clarifications）。

## Decision 14: 手势的"侧"扩展为三种，转入背包的两侧合并成一组

**Decision**: `QuickTransferGesture.Side` 增加 `CONTAINER`；`INVENTORY` 与 `CONTAINER` 同属"转入背包"组，`PANEL` 是"转出"。起点所在的组决定整次拖动的方向（FR-018）；组内两种槽位混着滑过都处理，各自走自己的请求：玩家物品栏槽位走 011 的包（物品栏下标），容器槽位走新包（菜单槽位下标）。因此一帧最多发出两个包（每种来源一个）。

**Rationale**: 玩家的预期是"滑过的物品自动进浮动背包"，不关心来源是玩家物品栏还是容器。FR-013 的"每帧至多一个请求"相应改为"每帧每种来源至多一个请求"。

## Decision 15: 被否决的方案

- **反向也用临时槽位 + `quickMoveStack`**：`quickMoveStack` 的目标永远是玩家物品栏（Decision 10），无法指向浮动背包。
- **先让 `quickMoveStack` 把物品移进玩家物品栏，再从物品栏拿**：无法知道哪些物品是刚移进来的，会误拿玩家原有物品。
- **客户端判断来源类型并区分处理**：服务端是权威，客户端只上报"菜单 id + 槽位下标"。

## 反向仍然不确定、需要实测的

- 精妙背包、旅行者背包的槽位是否真的接受 `safeTake`/`safeInsert`（它们覆盖的是 `getItem/set/remove`）：推断。
- 它们的界面里被滚动隐藏的槽位 `isActive()` 是否为假：未知。
- 精妙背包自己的 Ctrl 用法是否与单独的 Ctrl+点击冲突：未知。
- 旅行者背包的工具槽、记忆槽（只接受特定物品）能否按 Decision 11 的判据正确取出：未知。
