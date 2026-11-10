# Contract: 10 个配方

全部 `minecraft:crafting_shaped`，`category: "misc"`，结果 `{"id": "mcfloatingbackpack:<类别>_extension", "count": 1}`。版型统一：

```text
I T I
L C R
I B I
```

`I` = 铁锭（`minecraft:iron_ingot`，四个角）；`T` 上、`L` 左、`C` 中、`R` 右、`B` 下。

| 扩展 | T（上） | L（左） | C（中） | R（右） | B（下） |
|---|---|---|---|---|---|
| 食物 `food` | 木桶 `barrel` | 面包 `bread` | 烟熏炉 `smoker` | 面包 `bread` | 金胡萝卜 `golden_carrot` |
| 工具 `tool` | 铁镐 `iron_pickaxe` | 铁斧 `iron_axe` | 砂轮 `grindstone` | 铁锹 `iron_shovel` | 铁锄 `iron_hoe` |
| 武器 `weapon` | 铁剑 `iron_sword` | 箭 `arrow` | 盾牌 `shield` | 箭 `arrow` | 弓 `bow` |
| 盔甲 `armor` | 铁头盔 `iron_helmet` | 皮革 `leather` | 锻造台 `smithing_table` | 皮革 `leather` | 铁靴子 `iron_boots` |
| 矿物 `ore` | 钻石 `diamond` | 铜锭 `copper_ingot` | 高炉 `blast_furnace` | 金锭 `gold_ingot` | 青金石 `lapis_lazuli` |
| 农作物 `crop` | 铁锄 `iron_hoe` | 骨粉 `bone_meal` | 干草块 `hay_block` | 骨粉 `bone_meal` | 水桶 `water_bucket`（会还回一个空桶） |
| 木材 `wood` | 铁斧 `iron_axe` | `#minecraft:logs` | 切石机 `stonecutter` | `#minecraft:logs` | `#minecraft:logs` |
| 药水 `potion` | 下界疣 `nether_wart` | 玻璃瓶 `glass_bottle` | 酿造台 `brewing_stand` | 玻璃瓶 `glass_bottle` | 萤石粉 `glowstone_dust` |
| 红石 `redstone` | 红石比较器 `comparator` | 红石中继器 `repeater` | 红石块 `redstone_block` | 活塞 `piston` | 红石粉 `redstone`（第 5 件，草案） |
| 唱片 `disc` | 钻石 `diamond` | 音符盒 `note_block` | 唱片机 `jukebox` | 青金石 `lapis_lazuli`（第 5 件，草案） | `#c:music_discs` |

说明：

- 盔甲的第 5 件（第二块皮革）、红石的红石粉、唱片的青金石是为补满 5 格而加的草案，**用户待确认**。
- 键的写法：物品 `{"item": "minecraft:..."}`，标签 `{"tag": "minecraft:logs"}` / `{"tag": "c:music_discs"}`。
- 10 个配方的（版型 + 键）两两不同；每个都有 4 个铁锭角。
