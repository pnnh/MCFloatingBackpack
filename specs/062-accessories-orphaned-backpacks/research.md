# 研究：被 Accessories 丢掉的背包

读了 Accessories 1.1.0-beta.54 的字节码，用容器里 061 留下的玩家数据文件核对了 NBT 结构。**除标注"未验证"的，都是字节码或数据文件的证据，不是游戏内结果**。

## R1　Accessories 怎么处理"槽位类型已不存在"的条目（解释了 061 的实测）

- `AccessoriesHolderImpl` 读玩家数据时（`read`），对 `accessories_containers` 里每个键：该键在 `EntitySlotLoader.getEntitySlots(entity)` 里（槽位类型对这个实体有效）才按容器读入；**不在的分支没有把物品放进 `invalidStacks`**，物品只是不被读入。所以槽位类型消失 = 条目被丢弃；下一次保存时文件里就没有它了。这与 061 的实测一致（物品没回来，改回开关也没回来）。
- `invalidStacks` 是另一件事：`ExpandedSimpleContainer.fromTag`（槽位下标超过现有格数）与 `AccessoriesContainerImpl.update` 往里放；`AccessoriesEventHandler.onLivingEntityTick` 在第一个 tick 对非空的 `invalidStacks` 调 `giveItemToPlayer`（服务端玩家）或 `spawnAtLocation`。**所以"槽位还在、格数变少"的情形 Accessories 自己归还**，不在本 feature 范围，规格里"plan 要查的第三件事"有了答案。
- 同一个 tick 里 `handleInvalidStacks` 还会对每个格子的物品再问一次 `canInsertIntoSlot`，不通过就丢出并还给玩家（061 的 `canEquip` 因此也会被问到，物品是还给玩家而不是丢失）。

## R2　玩家数据文件里的结构（容器 `mcfb-c1` 的真实文件）

`neoforge:attachments` → `accessories:inventory_holder` → `accessories_containers` → 每个槽位类型一个条目：`slot_name`、`base_size`、`current_size`、`render_options`、`items`（列表）、`cosmetics`（列表）；列表元素是普通物品 NBT：`id`、`count`、`Slot`、`components`（带编号的背包有 `mcfloatingbackpack:backpack_identity: {uuid: [I;4]}`）。键名就是槽位类型名（`back`、`charm`、`floating_backpack`、`accessories:head` 等）。**不需要 Accessories 的任何类就能读。**

## R3　文件在登录时还保留着丢失的物品

玩家数据只在保存时写出（自动保存、下线、关服）；读入之后、第一次保存之前文件不变。061 的实测里关掉开关后启动，被丢的物品直到保存前仍在文件里；057 的 `SnapshotNotices.onPlayerDataRead` 已经依赖同一个事实（`PlayerEvent.LoadFromFile` 读文件）。

## R4　"现在存在的槽位类型"怎么读

`AccessoriesCapability.get(player).getContainers()`（033/034 的来源已用，`Map<String, AccessoriesContainer>`）的键集合 = 对该玩家现在有效的槽位类型（读入时用 `EntitySlotLoader.getEntitySlots` 建立 `validTypes`）。**未验证**：该 map 是否包含大小为 0 的类型（不影响判断：大小为 0 的类型文件里不会有物品）。

## R5　什么时候核对

- Accessories 的归还发生在第一个 living tick（R1）；所以登录后 20 刻（1 秒）核对，在它之后、玩家来得及乱动之前。
- 玩家在 20 刻之内下线：下线时文件会被保存，丢失就成定局。`PlayerEvent.PlayerLoggedOutEvent` 在保存之前触发（NeoForge 在 `PlayerList.remove` 开头触发它；**读的是记忆，没有从字节码核对，游戏内验证**——quickstart 情形 5），所以在那个事件里同样核对并归还，物品会随这次保存写进文件。若事件在保存之后触发，这个窄窗口就是已知局限（spec 的 Edge Cases 已写）。
- 事件线程：`LoadFromFile` 可能在别的线程（057 注释），`Pending` 用 `ConcurrentHashMap`；核对与归还只在主线程（`ServerTickEvent.Post`、`PlayerLoggedOutEvent` 都是主线程）。

## R6　不复制的规则

- 有编号（`backpack_identity.uuid`）的背包：**编号是唯一的**（040 的身份），玩家身上已有同编号就一定不是丢了的那只。"身上"= 物品栏（含盔甲、副手）、光标、Accessories 现有容器（真实+装饰）、地上 16 格内的掉落物（与 `BackpackWatchService` 的"lying"同一个半径）。
- 无编号的（从未打开过）：没法区分，按数量：文件里（物品栏 + 所有 Accessories 容器）的无编号数 − 现在的无编号数 = 缺几只；不超过"在孤立槽位里的无编号数"。已知小缺口：登录后 20 刻内玩家打开过某只无编号背包使它得到编号，会让计数少算一只（偏向少还，不会多还）。
- 比较的"域"两边必须相同：只数物品栏、光标、Accessories 容器、地面；不数末影箱、不数别的模组的槽位（Curios 等）。

## R7　归还的做法

沿用 060 的 `ContainerMenuAccess(player).fallback()`（先放进物品栏，放不下的掉在玩家脚边，不丢）。物品栏的"放"不改变已有物品。

## 否决的方案

- **在 Accessories 的数据读入之前改 NBT**：`LoadFromFile` 触发时数据已被应用，没有更早的钩子，且要改第三方的数据。
- **不管计数，总是归还**：若 Accessories 将来改成自己归还，会复制。
- **只用编号去重**：无编号的背包没法去重。
- **按档位以外再加一个配置开关**：它是对 Accessories 的专门适配，已有档位可关（宪法 VII）。
- **用反射/mixin 拦 Accessories 的丢弃**：宪法 VII 禁止。
