# 契约：读取、核对、归还

对应 FR-001～FR-007。

## 读取（`OrphanedBackpackScan`，`compat/accessories/`，只用 NBT 与 `ItemStack`）

输入：玩家数据文件（压缩 NBT，`NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())`，与 057 同）。路径：`neoforge:attachments` → `accessories:inventory_holder` → `accessories_containers` → 各键 → `items` / `cosmetics`（`ListTag` of `CompoundTag`）。同时读根下的 `Inventory`（`ListTag`）统计无编号背包总数。判断"是浮动背包"：`id` 是五个物品 id 之一（`mcfloatingbackpack:{leather,iron,gold,diamond,netherite}_floating_backpack`）。编号：`components` → `mcfloatingbackpack:backpack_identity` → `uuid`（`int[4]`）。任何一层缺失或类型不对 = 当作没有，不抛异常。

## 时机

- 读：`PlayerEvent.LoadFromFile`（任意线程），仅当 `ModList.isLoaded("accessories")`；结果放 `ConcurrentHashMap<UUID, Pending>`。
- 核对：主线程，登录后 20 刻（`ServerTickEvent.Post` 里的倒计时表）或 `PlayerLoggedOutEvent`，先到先做，做完删 `Pending`；下线时若还有 `Pending` 且玩家在线，同样核对。`PlayerLoggedOutEvent` 之后清理表项。

## 核对（`OrphanedBackpacks.process(player)`）

1. 档位门：`ServerCompatLevels.specificAdaptation("accessories")` 为假 → 删 `Pending`，返回。
2. `validSlotTypes = AccessoriesCapability.get(player).getContainers().keySet()`；取不到（`null`、异常）→ 删 `Pending`，返回（不动）。
3. 收集 `Live`；调 `OrphanRules.decide`。
4. 对结果里每只：`ItemStack.parse(player.registryAccess(), stackTag)`（失败 → 日志一行，跳过）；`ContainerMenuAccess(player).fallback()`：`toInventory`，剩下的 `drop`。
5. 提示一行、日志 INFO 一行。

## 语言键（en_us、zh_cn）

- `message.mcfloatingbackpack.orphaned.returned`：`%1$s 只背包原来放在 Accessories 里已不存在的槽位中，已放回你的物品栏。`
- `message.mcfloatingbackpack.orphaned.dropped`：同上加"（物品栏放不下，%2$s 只掉在了你脚边）"。

## 不变量

- 只增加玩家的物品，不删除不移动任何现有东西。
- 异常一律不动；失败不影响登录。
- 不新增网络包；不用反射、mixin；`OrphanedBackpackScan`、`OrphanRules` 不引用 Accessories 的类型。
