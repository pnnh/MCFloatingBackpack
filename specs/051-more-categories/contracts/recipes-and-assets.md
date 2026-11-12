# 契约：配方、颜色、贴图、语言键

## 配方（有序，`minecraft:crafting_shaped`，`category: misc`，结果 `count 1`，不加进度）

图样 `IAI` / `BDB` / `IEI`，`I` = `minecraft:iron_ingot`。文件 `data/mcfloatingbackpack/recipe/<id>_extension.json`，格式同 `disc_extension.json`。

| 扩展件 | A（上） | B（左、右） | D（中） | E（下） |
|---|---|---|---|---|
| building | `minecraft:glass` | `minecraft:stone_bricks` | `minecraft:stonecutter` | `minecraft:terracotta` |
| natural | `minecraft:grass_block` | `minecraft:cobblestone` | `minecraft:iron_shovel` | `minecraft:sand` |
| plant | `minecraft:sunflower` | `#minecraft:leaves`（树叶，任意树种） | `minecraft:composter` | `minecraft:lily_pad` |
| decor | `minecraft:painting` | `minecraft:lantern` | `minecraft:loom` | `minecraft:candle` |
| mobdrop | `minecraft:bone` | `minecraft:string` | `minecraft:slime_block` | `minecraft:ender_pearl` |
| storage | `minecraft:chest` | `minecraft:barrel` | `minecraft:hopper` | `minecraft:trapped_chest` |
| transport | `minecraft:minecart` | `minecraft:rail` | `minecraft:oak_boat` | `minecraft:saddle` |
| enchant | `minecraft:book` | `minecraft:lapis_lazuli` | `minecraft:enchanting_table` | `minecraft:bookshelf` |
| meat | `minecraft:cooked_beef` | `minecraft:cooked_cod` | `minecraft:smoker` | `minecraft:cooked_chicken` |

- **蜡烛**：用 `minecraft:candle`（1.21.1 里的无色蜡烛，已查证存在），规格里的"蜡烛"就是它。
- 两两不同：9 个配方的图样与材料集合互不相同，也与现有 10 个、5 个通用扩展不重（测试里断言）。
- 同一原料出现在多个配方里不是问题（石砖、铁锹各一次）。

## 颜色（`CategoryStyle.EDGE`，见 research R4）

`building 0xFFC4552E`、`natural 0xFF4E7D2E`、`plant 0xFF14A06A`、`decor 0xFFE6D83A`、`mobdrop 0xFF6A2C7E`、`storage 0xFFD9A066`、`transport 0xFF4A4F5C`、`enchant 0xFF6D4BFF`、`meat 0xFFFF8F85`。`fill` 与 `ghostItemId` 沿用现有函数，不新增代码。

## 贴图与模型

- `assets/mcfloatingbackpack/models/item/<id>_extension.json`：`{"parent": "minecraft:item/generated", "textures": {"layer0": "mcfloatingbackpack:item/<id>_extension"}}`（同现有）。
- `assets/mcfloatingbackpack/textures/item/<id>_extension.png`：16×16 RGBA，由 `make_category_extension_icons.py` 生成；图案见 research R7。**占位手绘**，用户之后用同名文件替换即可，不用改代码。

## 语言键

| 键 | 中文 | 英文 |
|---|---|---|
| `item.mcfloatingbackpack.building_extension` | 建筑扩展 | Building Extension |
| `…natural_extension` | 自然扩展 | Natural Extension |
| `…plant_extension` | 植物扩展 | Plant Extension |
| `…decor_extension` | 装饰扩展 | Decor Extension |
| `…mobdrop_extension` | 怪物掉落扩展 | Mob Drops Extension |
| `…storage_extension` | 容器收纳扩展 | Storage Extension |
| `…transport_extension` | 交通运输扩展 | Transport Extension |
| `…enchant_extension` | 附魔与书扩展 | Enchanting Extension |
| `…meat_extension` | 肉与鱼扩展 | Meat & Fish Extension |
| `tag.mcfloatingbackpack.preset.building` 等 8 个（`plant` 已有） | 建筑方块、自然方块、装饰、怪物掉落、容器收纳、交通运输、附魔与书、肉与鱼 | Building, Natural, Decor, Mob Drops, Storage, Transport, Enchanting, Meat & Fish |

"放进功能区，增加一行只能存 %s 的格子"提示（`item.mcfloatingbackpack.category_extension.tooltip`）沿用，`%s` 取预设标签名。

## 资源测试要断言（`CategoryExtensionResourcesTest` 与 `ExtensionTagsResourcesTest` 扩成 19 个）

- 19 个类别各有：标签 JSON、模型、贴图（16×16）、配方、中英文物品名、中英文预设名；
- 每个新类别的标签 JSON：所有 `#` 条目 `required: false`；包含 contracts/categories.md 里的"抽查物品"（每类 ≥ 5 件在、≥ 3 件不在，见 quickstart 表）；
- `#minecraft:shulker_boxes` 与 `#c:dyed` 不出现；
- `ItemCategoryMatcher` 源码里 `enchant` 认 `STORED_ENCHANTMENTS` 且不认 `ENCHANTMENTS`。
