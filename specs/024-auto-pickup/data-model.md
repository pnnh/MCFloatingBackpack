# Data Model: 第三个扩展工具——自动拾取

**Branch**: `024-auto-pickup` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

没有新的数据库或文件；全部是物品数据组件里的一个新字段、一个服务端的内存表、几个客户端会话状态，以及一组不依赖 Minecraft 类型的纯逻辑类。

## 1. 标签黑名单 `PickupBlacklist`（`inventory/pickup/`）

不可变记录，随背包保存。

| 字段 | 类型 | 规则 |
|---|---|---|
| `tags` | `List<String>` | 每个标签满足 `TagRules.isValid`（1～16 个可见 ASCII、无空格、无大写）；不重复；至多 `MAX_TAGS = 64`；保持添加顺序 |

- `EMPTY`；（实现时拆出 `PickupBlacklistCodecs`，见 DELIVERY"偏离说明"）`CODEC` = `Codec.STRING.listOf()` 的 `xmap`，读入时**清洗**：丢掉非法与重复的标签、截到 64 个（外来数据不能突破上限，和 `ItemTags.sanitize` 同理）。
- `STREAM_CODEC` = `ByteBufCodecs.stringUtf8(TagRules.MAX_LENGTH).apply(ByteBufCodecs.list(MAX_TAGS))`；解码超界由 NeoForge 抛 `DecoderException`。
- 纯函数（返回新值，被拒绝的修改返回 `null`，调用方当作"零变化"）：
  - `add(tag)`：非法 → `null`；已存在 → 原值（幂等，不是错误）；已满 64 个 → `null`；否则追加。
  - `remove(tag)`：不存在 → 原值。
  - `contains(tag)`、`size()`、`isFull()`。
  - `matches(List<String> tagsOfItem)`：任一标签在黑名单中。
- 它**不属于**自动拾取物品：物品被取走、丢掉、销毁，黑名单都在背包里；放回左侧区后重新生效（FR-016）。

## 2. 背包数据 `BackpackContents` 的第六个字段

```text
BackpackContents(items, cursor, tools, tags, trash, pickupBlacklist)
```

- `CODEC`：`PickupBlacklist.CODEC.optionalFieldOf("pickup_blacklist", PickupBlacklist.EMPTY)`；旧背包读成空。
- `STREAM_CODEC`：`StreamCodec.composite` 现在是 5 个字段，加上它是 **6 个（上限）**；之后再加字段必须先改手写编码（见 research Decision 4）。
- `empty/normalize/fit/copy` 全部带上新字段（带 `null` 保护，同 `trash`）；`BackpackUpgradeRecipe` 调 `BackpackContents.fit(...)` 时补传 `current.pickupBlacklist()`（升级保留）；`BackpackInventory` 增 `pickupBlacklist()`/`setPickupBlacklist()`、`fromContents`/`toContents` 同步（模式同 `trash`）。
- 新增只读便利方法（预检用，不建 `BackpackInventory`）：`BackpackContents.hasAutoPickup()` = 左侧区列表里有 `ModItems.AUTO_PICKUP` 的栈。`BackpackInventory.hasAutoPickup()` 同义（服务端点击/黑名单请求校验用，扫描 `size(PanelArea.LEFT)`）。

## 3. 左侧区限放规则（`inventory/ToolAreaRules`，纯）

`canPlace(area, incoming, slotIndex, areaSlots, isTrashCan, isTagManager, isAutoPickup)`：

- `case LEFT -> isAutoPickup`（其余不变：`RIGHT -> isTrashCan`、`BOTTOM -> isTagManager`、`MAIN -> 不限`）。
- 规则不变：放入后该类物品数量 ≤ 1，**或不比放入前多**（所以旧背包里已有 2 个不删、同格交换允许、被"别的物品"占着的格子换进第二个会被拒）。

## 4. 拾取决策 `AutoPickupDecision`（`inventory/pickup/`，纯）

```text
enum Outcome { VANILLA, BLOCK, TAKE }

decide(boolean toolActive, boolean blacklisted)
```

| toolActive | blacklisted | 结果 | 含义 |
|---|---|---|---|
| 假 | — | `VANILLA` | 没有激活的自动拾取：原版 |
| 真 | 真 | `BLOCK` | 命中黑名单：留在地上，不进背包也不进物品栏 |
| 真 | 假 | `TAKE` | 搬进背包主区域，放不下的回落物品栏 |

- **没有"避让其它背包模组"这一栏**（2026-11-08 取消，见 research Decision 6）：对其它背包的礼让只靠事件优先级 `LOW` 与"事件已是 `FALSE` 就不插手"，两者都在 `AutoPickupEvents` 里，不进决策表。
- 原来的避让开关表（`PickupAvoidRegistry`）与探测器（`compat/OtherBackpackDetector(s)`）**不存在**。

## 5. （已取消）避让开关表

## 6. （已取消）探测器

两节随"取消主动避让"取消（research Decision 6），保留编号是为了不改动其它文档的引用。

## 7. 客户端会话状态

| 状态 | 位置 | 说明 |
|---|---|---|
| 黑名单页面展开/编辑/下拉下标/待删标签/滚动/文本缓冲 | `PickupPanelState`（单例，结构同 `TagPanelState`） | 不持久；离开世界清空；收起保留文本与滚动；与 `TagPanelState` **互斥**（展开时收起对方） |
| 黑名单镜像 | `ClientBackpackView.pickupBlacklist()`/`setPickupBlacklist()` | 由 `BackpackPickupBlacklistPayload` 整份覆盖；`clear()` 时置空 |
| `hasAutoPickup()` | `ClientBackpackView` | 左侧区**整个列表**里有自动拾取（不看可见格子），同 `hasTrashCan` |

页面的"模型"：`PickupPanelModel`（纯）= 黑名单列表 + `hasTool` + 上限 → 提供 `canAdd`（`hasTool && !full`）、状态行文字所需的 `count/max`。

## 8. 服务端拾取流程（不是数据，但决定了读写顺序）

```text
onItemPickup(Pre):                       // EventPriority.LOW
  if event.canPickup().isFalse()          return
  entity = event.getItemEntity(); stack = entity.getItem()
  if stack.isEmpty() || entity.hasPickUpDelay()   return
  ref = BackpackRef.resolve(player);  if ref == null  return
  contents = ref.getStack(player).get(BACKPACK_CONTENTS);  if contents == null || !contents.hasAutoPickup()  return
  // —— 通过预检后才做下面的事 ——
  blacklisted = contents.pickupBlacklist().matches(contents.tags().tagsOf(itemId(stack)))
  switch AutoPickupDecision.decide(true, blacklisted):
    VANILLA : return
    BLOCK   : event.setCanPickup(FALSE); return
    TAKE    :
      if entity.getTarget() != null && !entity.getTarget().equals(player.getUUID())  return   // 与原版一致
      inventory = ref.loadInventory(player)
      changed = []
      rest = QuickTransferLogic.moveInto(ops, stack, [0..size-1], inventory::get, (s,i)->{ inventory.set(i,s); changed.add(i) })
      moved = stack.count - rest.count;  if moved == 0  return
      ref.saveInventory(player, inventory)
      stack.shrink(moved)
      for i in changed: BackpackNetwork.sendSlotUpdate(player, ref, i)
      player.take(entity, moved); player.awardStat(ITEM_PICKED_UP, moved); player.onItemPickup(entity)
      if stack.isEmpty(): entity.discard(); event.setCanPickup(FALSE)
      // 否则保持默认：原版把剩余部分放进物品栏（回落）
```

守恒不变量：每次执行后，`进背包 + 实体剩余 = 原实体栈`；回落由原版 `inventory.add` 保证不丢失；`moved` 只在背包保存成功后才从实体栈扣除（同一服务端 tick 内，无并发）。

## 9. 资源清单

| 资源 | 路径 |
|---|---|
| 物品 | `item/AutoPickupItem`（普通物品，堆叠 1；同 `TrashCanItem`），`ModItems.AUTO_PICKUP` = `auto_pickup`，创意栏 |
| 模型 | `assets/mcfloatingbackpack/models/item/auto_pickup.json`（`item/generated`） |
| 贴图 | `assets/mcfloatingbackpack/textures/item/auto_pickup.png`（16×16） |
| 配方 | `data/mcfloatingbackpack/recipe/auto_pickup.json`（research Decision 10） |
| 语言 | `zh_cn.json`、`en_us.json`：物品名"自动拾取"/"Auto Pickup"；页面标题；状态行；"无"；加号/上限悬停提示；限放提示 `message.mcfloatingbackpack.limit.auto_pickup` |
| 构建 | 无变化（不新增依赖） |
