# Data Model: 005 背包道具化

## FloatingBackpackItem（注册物品）

| 字段 / 行为 | 规则 |
| --- | --- |
| id | `mcfloatingbackpack:leather_floating_backpack`（名称可本地化「皮革浮动背包」） |
| 装备 | 可进入 `EquipmentSlot.CHEST` |
| 堆叠 | `maxStackSize = 1`（避免合并丢失内容） |
| 本期容量 | 主仓 36 格 |

## BackpackContents（Data Component，挂在 ItemStack 上）

| 字段 | 规则 |
| --- | --- |
| slots | 长度 36 的物品栈列表；空为 empty |
| cursor | 面板光标栈；可 empty；与 slots 一同序列化以防掉线丢物 |

校验：单槽数量 ≤ 该物品 `maxStackSize`；服务端写入前校验。

## BackpackRef（解析句柄，非持久化）

| 字段 | 规则 |
| --- | --- |
| source | `MAIN_HAND` \| `OFF_HAND` \| `CHEST` \| `INVENTORY` |
| invIndex | 当 source=`INVENTORY` 时为 0–35；其它为 -1 |

解析顺序（第一匹配）：

1. 主手是浮动背包  
2. 副手是浮动背包  
3. 胸甲是浮动背包  
4. `inventory.getItem(i)` i=0..35 第一只浮动背包  

## ClientBackpackView（客户端镜像）

| 字段 | 规则 |
| --- | --- |
| slots[36] | 最近同步 |
| cursor | 面板光标 |
| activeRef | 可选；当前打开的 BackpackRef，用于 UI |

## 状态转换

```text
toggle_open(player):
  ref = resolve(player)
  if ref == null → deny + hint
  else → bind session to ref, sync full contents, show overlay

click(slot, …, claimedRef):
  serverRef = resolve(player)  // or validate claimedRef still matches stack
  if invalid → close + deny
  apply click to contents on that ItemStack
  write component back to the stack in that slot/hand/chest
  persist player inventory
  reply slot/cursor updates
```

## 删除的实体

- **Player Attachment `backpack`**：移除类型注册与读写；旧 NBT 忽略。

## 预留（不实现）

| 等级 | 主仓格数 |
| --- | --- |
| 皮革 | 36 |
| 铁 | 54 |
| 金 | 72 |
| 钻石 | 90 |
| 下界合金 | 108 |
