# 数据模型：灯笼光源

没有存储数据。只有三样东西：

## 亮度表（`ToolLights`，纯逻辑）

| 物品 id | 亮度（= 原版同一方块） |
|---|---|
| `minecraft:lantern`、三种蛙明灯、`glowstone`、`sea_lantern`、`shroomlight`、`jack_o_lantern` | 15 |
| `minecraft:end_rod` | 14 |
| `minecraft:soul_lantern` | 10 |

`level(itemId, inTag, blockLuminance, isBlockItem)`：不在标签 → 0；在表 → 表值；否则方块物品 → `clamp(blockLuminance, 1, 15)`，非方块物品 → 8。

## 标签

`data/mcfloatingbackpack/tags/item/tool_lights.json`：10 个默认物品（见 contracts/resources.md）。

## 对 LDL 的回答（`PlayerLights` → `BackpackEntityLuminance`）

`brightnessOf(Player)`：`BackpackRef.resolve(player)` → 该背包的 `tools().bottom()` → 按格子顺序第一个标签成员 → `ToolLights.level(...)`；没有则 0。LDL 在客户端每 tick 对每个玩家询问一次。
