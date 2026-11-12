# 契约：物品、配方、贴图、语言键

19 个罐，id 为 `mcfloatingbackpack:<档次>_<液体>_tank`；档次 `leather|iron|gold|netherite`，液体 `water|oil|lava|milk|honey`；**没有 `leather_lava_tank`**。

## 配方

记号：`L` 皮革（`minecraft:leather`）、`G` 玻璃（`minecraft:glass`）、`I` 铁锭、`K` 煤炭块（`minecraft:coal_block`）、`B` 空桶（`minecraft:bucket`）、`C` 该液体的容器（见下表）、`T` 同液体的铁罐、`A` 金锭。

| 液体 | 容器 `C` | 合成后退回 |
|---|---|---|
| water | `minecraft:water_bucket` | 空桶 |
| lava | `minecraft:lava_bucket` | 空桶 |
| milk | `minecraft:milk_bucket` | 空桶 |
| honey | `minecraft:honey_bottle` | 玻璃瓶 |

退回由原版物品自己的 `craftRemainder` 负责（**蜂蜜瓶退回玻璃瓶要在游戏里合成一次核对**）。

### 有序合成（3×3）

| 档次 | 水、岩浆、牛奶、蜂蜜 | 原油 |
|---|---|---|
| 皮（无岩浆） | `LGL` / `LCL` / `LLL` | `LGL` / `LBL` / `LKL` |
| 铁 | `III` / `ICI` / `III` | `III` / `IBI` / `IKI` |
| 金 | `AAA` / `ATA` / `AAA`（`T` = 同液体铁罐） | 同左，`T` = 铁原油罐 |

- 皮罐：7 皮革 + 上中 1 玻璃 + 中间容器；原油皮罐：6 皮革 + 上中玻璃 + 中间空桶 + 下中煤炭块。
- 铁原油罐：7 铁锭 + 中间空桶 + 下中煤炭块。
- 金罐 4 种液体共 5 个配方，都是"铁罐 + 8 金锭"。

### 锻造台（下界合金罐，5 个）

`minecraft:smithing_transform`：`template = minecraft:netherite_upgrade_smithing_template`，`base = 同液体金罐`，`addition = minecraft:netherite_ingot`，`result = 同液体下界合金罐`。

### 配方清单

| 档次 | 个数 | 种类 |
|---|---|---|
| 皮 | 4 | 水、原油、牛奶、蜂蜜 |
| 铁 | 5 | 水、原油、岩浆、牛奶、蜂蜜 |
| 金 | 5 | 同上 |
| 下界合金 | 5 | 同上（锻造台） |
| **合计** | **19** | |

`category` 沿用现有配方的 `misc`；**不加进度文件**（现有配方都没有）。

## 物品

- 类 `TankItem`；`stacksTo(1)`；没有数据组件；`appendHoverText`：`item.mcfloatingbackpack.tank.capacity`（"容量 %s mB"）与 `item.mcfloatingbackpack.tank.min_tier`（"放入 %s 及以上的背包"，档次名用 `item.mcfloatingbackpack.<tier>_floating_backpack` 的显示名）。
- 创造物品栏 `main` 标签页里，放在 `AUTO_PICKUP` 之后、类别扩展之前，按"档次先、液体后"的顺序。
- 19 个 id 全部进 `data/mcfloatingbackpack/tags/item/never_trash.json`。

## 模型与贴图

- 模型：`models/item/<id>.json`，`parent = minecraft:item/generated`，`layer0 = mcfloatingbackpack:item/<id>`。
- 贴图：`textures/item/<id>.png`，16×16，由 `design/tank-textures/generate.py` 生成：一个罐的轮廓（上口、腰身、底）+ 档次色描边 + 液体色填充；**占位图**，用户之后用同名文件替换即可，不用改代码。

| 档次 | 描边色 | 液体 | 填充色 |
|---|---|---|---|
| leather | `#8B5A2B` | water | `#3F76E4` |
| iron | `#C8C8C8` | oil | `#2B2B2B` |
| gold | `#F2C94C` | lava | `#E8651A` |
| netherite | `#4A3F47` | milk | `#F2F2F2` |
| | | honey | `#E3A82B` |

## 语言键（`en_us.json`、`zh_cn.json`）

| 键 | 英文 | 中文 |
|---|---|---|
| `item.mcfloatingbackpack.<tier>_<kind>_tank`（19） | `Leather Water Tank`、`Iron Oil Tank`… | `皮水罐`、`铁油罐`、`金岩浆罐`、`下界合金牛奶罐`、`皮蜂蜜罐`… |
| `fluid.mcfloatingbackpack.<kind>`（5） | Water / Crude Oil / Lava / Milk / Honey | 水 / 原油 / 岩浆 / 牛奶 / 蜂蜜 |
| `tier.mcfloatingbackpack.<tier>`（5，含钻石） | Leather / Iron / Gold / Diamond / Netherite | 皮 / 铁 / 金 / 钻石 / 下界合金 |
| `item.mcfloatingbackpack.tank.capacity` | `Capacity: %s mB` | `容量：%s mB` |
| `item.mcfloatingbackpack.tank.min_tier` | `Fits %s backpacks and above` | `放入%s及以上的背包` |
| `tooltip.mcfloatingbackpack.tank.amount` | `%s: %s / %s mB` | `%s：%s / %s mB` |
| `tooltip.mcfloatingbackpack.tank.empty` | `%s: empty` | `%s：空` |
| `message.mcfloatingbackpack.tank.tier` | `This tank needs a %s backpack or better` | `这个罐子需要%s及以上的背包` |
| `message.mcfloatingbackpack.tank.duplicate` | `This backpack already has a tank for that liquid` | `这个背包已经有这种液体的罐了` |
| `message.mcfloatingbackpack.tank.too_small` | `This tank is too small for the liquid already stored` | `这个罐子装不下背包里已有的液体` |

## 资源测试（`TankResourcesTest`）要断言

- 19 个物品、19 个模型、19 张贴图、19 个配方、38 条物品语言键（中英各 19）、5 条液体名、其余键都在两个语言文件里；没有 `leather_lava_tank` 的任何文件；
- 每个配方：有序合成的形状与上表一致（用 JSON 解析比对），锻造台的模板、基底、材料与结果一致，结果都是 `count 1`；
- `never_trash.json` 含全部 19 个；
- 贴图都是 16×16（读 PNG 头）。
