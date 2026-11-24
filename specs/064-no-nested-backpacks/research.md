# Research: 064 禁止浮动背包嵌套

## R1 为什么要禁（数据量）

背包内容是物品上的数据组件，嵌套即按值复制。皮革背包 36 格、下界合金 108 格（`BackpackTier`），再加扩展行、工具区、垃圾桶。三层各塞满皮革背包是 36³ ≈ 4.7 万只；每只空背包按几百字节估算（**未实测**，实现时用 `/give` 造一只测一次写进 DELIVERY）。载荷 1 MiB 的上限、玩家数据登录同步、057 快照（把嵌套整份再存）都会撞到。已有的先例：`SnapshotRequestRules.MAX_PREVIEW_BYTES = 900_000`、`BackpackTrashPayload.tooLarge`（垃圾桶按编码字节数拒收）。

## R2 各模组有没有可用的钩子（反编译已核对）

| 对象 | 结论 | 依据 |
|---|---|---|
| NeoForge `ComponentItemHandler.isItemValid` | 调 `stack.canFitInsideContainerItems()` | NeoForge 21.1.251 源码 |
| `IItemExtension.canFitInsideContainerItems(ItemStack)` | 默认委托给 `Item.canFitInsideContainerItems()`（无参，原版） | 源码 808～810 行 |
| 旅行者背包（TB）格子 `BackpackSlotItemHandler.isItemValid` | 依次看内置黑名单列表、标签 `travelersbackpack:blacklisted_items`、一个布尔配置、`Item.canFitInsideContainerItems()` | javap：`invokevirtual Item.canFitInsideContainerItems:()Z`；标签文件默认是空的 |
| 精妙背包（SB）`BackpackInventoryHandler.isAllowed` | 先问 `Config.SERVER.disallowedItems.isItemDisallowed(item)`（注册名列表），再看是不是自己的背包；`containerItemsDisallowed` 默认 **false**（`iconst_0`），即默认不认 `canFit` | javap |
| 精妙存储 | 有 `shulkerBoxDisallowedItems` 和同名布尔，只管它自己的潜影盒；箱子没有对应钩子 | javap |
| AE2 19.2.20 | 只有 `ae2:blacklisted/annihilation_plane` 一个无关标签；类里没有 `canFitInsideContainerItems` | 解包 |
| 集成终端 | 没有相关钩子 | 解包 |

结论：重写**无参** `Item.canFitInsideContainerItems()`（TB 和 NeoForge 的有参版本都落到它）。SB 只能靠管理员配置；AE2、集成终端、精妙存储箱子没有钩子，拦截要 mixin，违反宪法 VII。TB 的标签是冗余的，不加。

## R3 来源（同包内重摆）的前提核对——用户选了"允许"，但前提要修订

用户选了"允许同一只背包内部重摆"，我当时的判据是"面板光标上的是内部来源"。读代码后发现不成立：

- 没开容器界面（"M2-only"）时，从面板拿起的物品放在面板光标 `inventory.getCursor()`（存进背包内容）。
- 开着容器界面（含物品栏界面 E）时，`BackpackClickLogic.click` 的结果交给 `menu.setCarried`，也就是菜单携带物；`mergePanelCursorIntoCarried` 还会把面板光标并进去。创造模式则是客户端上报、服务端镜像到 `inventoryMenu` 的携带物（`CreativeCursorBridge.onReport`）。

所以光标本身看不出来源。**做法**：每个玩家一条临时记录 `(背包引用, 物品副本)`，在 `BackpackClickPayload` 里"这一击会把有嵌套风险的物品拿起来"时写入；放回时 `同一只背包 && ItemStack.matches` 才放行，用掉即清，`PlayerContainerEvent.Close`（`MCFloatingBackpack` 已有这个监听）与下线时清。

可接受的漏洞：玩家拿起后放进物品栏、过一会儿又拿着放回**同一只**背包——结果和拿起前一样，没有新增嵌套。记录不会让物品进入任何别的背包。若验证中发现记录不可靠，退路是"一律只能取出"，只需让放行条件恒为假。

## R4 深度扫描

- 触发条件（只对这些物品扫描，其余不付代价）：物品本身是浮动背包；或其组件补丁里有 `minecraft:container`、`minecraft:bundle_contents`、`minecraft:custom_data`，或任何命名空间不是 `minecraft` 的组件（别的模组的容器数据）。判断沿用 `StackExtraData` 的思路（补丁里的组件 id 列表）。
- 扫描：原版两个组件**结构化递归**（`ItemContainerContents.nonEmptyItems()`、`BundleContents.items()`），深度上限 8（超过按"含有"拒收，保守）。别的组件用**通用兜底**：把物品编码成 NBT 后查找五个浮动背包的完整物品 id 字符串（`mcfloatingbackpack:<tier>_floating_backpack`）。
- 误伤：名字里恰好写了完整 id 的物品会被拒绝放入——只是拒绝，不丢物品，可接受。

## R5 所有入口（读代码核对）与处理

| 入口 | 位置 | 处理 |
|---|---|---|
| 点击放入主区域 | `BackpackClickPayload`（`canPlaceMain` 前） | 加嵌套判定（带来源记录），拒绝时槽位回发 + 操作栏提示 |
| 点击放入工具区 | 同上，`area.isTool()` 分支（`ToolAreaRules` 现在"其它一律放行"） | 同上 |
| 手里与格子互换 | 同上 `swapHand`（已拒绝换出手里的背包，但没有提示） | 补提示 |
| 拖动分摊、集合 | `BackpackCursorGesturePayload.distribute` | 入口处判定一次 |
| 物品栏 → 面板（Ctrl/Shift 点击） | `BackpackQuickTransferPayload.inventoryToBackpack` → `insertIntoMain` | 有风险的物品留在原处 |
| 自动拾取 | `AutoPickupEvents.take` → `mainTargetOrder` | 有风险的不吸，交给原版 |
| 容器 → 面板 | `BackpackTakeFromContainerPayload.takeIntoBackpack` | 取之前判定，有风险的留在容器 |
| 网络终端 → 面板 | `BackpackNetworkTakePayload`（提取之前） | 有风险的不提取 |
| 垃圾桶 | `BackpackTrashPayload.add`、`BackpackTrashSendPayload`（`TrashBin.push`） | 一律拒绝（不看来源） |
| 创造模式 | 经 `CreativeCursorBridge` 镜像到携带物，走上面的点击路径 | 无额外处理，游戏内验证 |
| 整理（047）、垃圾桶还原到主区、快照还原 | 内部搬动或整体覆盖 | **不拦**（`BackpackTrashPayload` 的还原调的是 `mainTargetOrder`，要改成内部版本） |

**默认安全**：把嵌套判定放进 `BackpackInventory.mainTargetOrder(stack)`（有风险的物品返回三段全空），所有"外来物品进主区域"的路径（`insertIntoMain`、拾取、容器 → 面板、终端 → 面板）自动继承；垃圾桶还原要用的内部版本 `mainTargetOrderInternal` 不过滤。以后新增的入口默认也被拦住。

## R6 垃圾桶的旧决定

021 FR-018 允许丢入任何物品（含本模组背包）。垃圾桶的内容存在背包里，属于嵌套入口，所以修订为"浮动背包除外"；021 spec 第 101 行的验收场景同步更新。已在垃圾桶里的旧背包仍可取出（`TrashBin.take`），取出到光标时同样写来源记录。

## R7 备选与放弃的理由

- 用 mixin 拦 AE2、集成终端、精妙存储的格子：宪法 VII 禁止。
- 禁止所有容器进浮动背包：扇出本来有界，会伤到潜影盒收纳这个主流用法。
- 只靠"含有"的字符串扫描、不做结构化：慢且依赖字符串，原版两个组件结构化更准。
- 启动时自动搬出旧嵌套：会改动旧数据，风险大于收益，用户也没有要求。
