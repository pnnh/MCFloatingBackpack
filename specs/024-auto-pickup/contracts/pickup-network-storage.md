# Contract: 自动拾取的存储、网络与服务端流程

## 1. 数据组件

`BackpackContents` 的存盘编码增加：

```text
{ "items": [...], "cursor": {...}?, "tools": {...}?, "tags": {...}?, "trash": [...]?, "pickup_blacklist": ["stone", "trash", ...]? }
```

- `pickup_blacklist` 可选，缺省为空；024 之前保存的背包读成空，其余字段不变。
- 读入时清洗：丢弃非法（不满足 `TagRules`）与重复的标签，超过 64 个只保留前 64 个。
- `fit(capacity)`、升级配方（`BackpackUpgradeRecipe`）、`copy()` 都保留它（与 `tags`、`trash` 同路）。
- 自动拾取**物品**不持有内容；物品被取走、被销毁，黑名单都留在背包里。

`BackpackContents.STREAM_CODEC`（物品栈同步）增加 `PickupBlacklist`：字段数 5 → **6（`composite` 的上限）**。

## 2. 协议版本

`BackpackNetwork`：`versioned("11")` → `versioned("12")`。

| 包 | 方向 | 变化 |
|---|---|---|
| `BackpackPickupBlacklistOpPayload(op, tag)` | 客户端 → 服务端 | **新增**：黑名单的添加 / 删除 |
| `BackpackPickupBlacklistPayload(PickupBlacklist)` | 服务端 → 客户端 | **新增**：整份黑名单 |
| `BackpackContents` 的物品栏同步编码 | 双向 | 增加 `PickupBlacklist` |
| 其余全部包 | — | 不变 |

## 3. 请求 `BackpackPickupBlacklistOpPayload`

字段：`op`（字节：ADD=0、REMOVE=1）、`tag`（`writeUtf(tag, TagRules.MAX_LENGTH)`）。

服务端（`handleServer`），按顺序，任一步失败则**整个请求忽略、数据零变化**（忽略时补发一次黑名单，让客户端显示追上真相）：

1. 请求者是 `ServerPlayer`；`BackpackRef.resolve` 得到背包且 `stillValid`，否则 `sendSession(false)`。
2. `PickupBlacklistRequest.validate(op, tag)`（纯）：`op` 合法、`TagRules.isValid(tag)`。
3. **该背包左侧区里有自动拾取**（`BackpackInventory.hasAutoPickup()`，按左侧区整个列表）；没有则忽略。
4. ADD：`blacklist.add(tag)`，返回 `null`（已满 64 个）→ 忽略；已存在 → 原值，不报错。REMOVE：`blacklist.remove(tag)`。
5. `backpack.setPickupBlacklist(next)`；`ref.saveInventory(player, backpack)`。
6. 发送 `BackpackPickupBlacklistPayload(整份)`。

客户端发出请求前也会做检查（满 64 个时加号灰掉等），但那只是体验，服务端才是权威。

## 4. 同步 `BackpackPickupBlacklistPayload`

整份 `PickupBlacklist`（≤ 64 个标签，每个 ≤ 16 字符，< 2 KiB）。发送时机：每次 `sendSession(open)` 之后（紧跟标签包与垃圾桶包）；每次黑名单变化之后；忽略请求时的补发。客户端 `ClientBackpackView.setPickupBlacklist(...)`，页面下一帧按新数据重画。`STREAM_CODEC` 解码超过 64 个抛 `DecoderException`。

## 5. （已取消）避让开关

随"取消主动避让"取消（research Decision 6）：没有避让开关包，也没有按玩家记录的服务端表。

## 6. 服务端拾取流程（`ItemEntityPickupEvent.Pre`，`EventPriority.LOW`）

见 [data-model.md](../data-model.md) 第 8 节。契约要点：

- **激活背包**每次拾取现算（`BackpackRef.resolve`），不存会话状态（FR-012a）；预检用背包物品上的数据组件，不建 `BackpackInventory`。
- `canPickup()` 已是 `FALSE`（别的模组已处理）、`stack` 为空、`hasPickUpDelay()` → 什么都不做。这是本模组对精妙背包、旅行者背包的全部礼让，另外就是优先级 `LOW`；**不检测、不引用它们**。
- 三种结果（`AutoPickupDecision.decide(toolActive, blacklisted)`）：`VANILLA`（什么都不做）、`BLOCK`（`setCanPickup(FALSE)`）、`TAKE`（搬运）。
- `TAKE`：物主检查与原版一致；用 `QuickTransferLogic.moveInto` 塞进主区域（目标顺序 `0..size-1`）；`moved == 0` 不动；否则**先保存背包，再**缩小实体栈，同步改动的主区域格子，`take` / `awardStat` / `onItemPickup`；实体栈空 → `discard` + `setCanPickup(FALSE)`；否则保持默认，让原版把剩余放进物品栏。
- 守恒：每次执行后 `进背包的数量 + 实体剩余数量 = 事件开始时的实体栈数量`；回落部分由原版 `Inventory.add` 负责，放不下就留在实体上。
- 异常安全：搬运过程中任何异常都不得让物品消失——保存背包失败（`ref.stillValid` 不再成立等）→ 不缩小实体栈、直接返回，交还原版。

## 7. 不变的服务端逻辑

011～023 的转入/转出、点击（除放入规则外）、拖动手势、选区、标签请求、垃圾桶请求、兼容门禁**一行不改**；自动拾取作为普通物品在任何区域、任何转移途径下都照常移动（只有"左侧区里放第二个"被点击放入规则拒绝）。服务端 `ServerCompatGate` 不变（恒真）；拾取不操作别的模组的菜单，不走这个门禁。
