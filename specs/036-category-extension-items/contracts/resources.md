# Contract: 物品 id、资源与图标风格

## 物品

| 类别 id | 物品 id | 中文名 | 英文名 |
|---|---|---|---|
| food | `mcfloatingbackpack:food_extension` | 食物扩展 | Food Extension |
| tool | `…:tool_extension` | 工具扩展 | Tool Extension |
| weapon | `…:weapon_extension` | 武器扩展 | Weapon Extension |
| armor | `…:armor_extension` | 盔甲扩展 | Armor Extension |
| ore | `…:ore_extension` | 矿物扩展 | Ore Extension |
| crop | `…:crop_extension` | 农作物扩展 | Crop Extension |
| wood | `…:wood_extension` | 木材扩展 | Wood Extension |
| potion | `…:potion_extension` | 药水扩展 | Potion Extension |
| redstone | `…:redstone_extension` | 红石扩展 | Redstone Extension |
| disc | `…:disc_extension` | 唱片扩展 | Disc Extension |

顺序固定（创意栏与代码里一致）。

## 语言键（两个语言文件的键集合必须一致）

- `item.mcfloatingbackpack.<类别>_extension` × 10
- `item.mcfloatingbackpack.category_extension.tooltip`：中文"放进浮动背包的功能区，增加一行只能存 %s 的格子"；英文 "Put it in a function area of the floating backpack to add a row that only stores %s"
- `message.mcfloatingbackpack.limit.extension_rows`：中文"这只背包的扩展行已达上限（35）"；英文 "This backpack already has the most extension rows (35)"

## 贴图与图标风格

- 每张：`textures/item/<id>.png`，**16×16，8 位 RGBA**，由 `design/demo/tools/make_category_extension_icons.py` 生成（`python3 make_category_extension_icons.py <输出目录> [--preview <放大图目录>]`）。
- **统一的底板**：1 像素深色（`0x24 0x24 0x28`）描边的铁灰色圆角板（浅 `CF CF D3`、中 `9A 9A A0`、暗 `62 62 68`，左上亮右下暗）；**中间 10×10 是类别图案**，每个类别有自己的主色，保证在 18 像素的格子里分得出来：

| 类别 | 图案 | 主色 |
|---|---|---|
| 食物 | 面包 | 暖黄褐 |
| 工具 | 镐 | 棕（柄）+ 灰（头） |
| 武器 | 剑 | 浅灰蓝 |
| 盔甲 | 头盔 | 钢灰 + 红缨 |
| 矿物 | 带矿点的石块 | 灰 + 青蓝矿点 |
| 农作物 | 麦穗 | 金黄 |
| 木材 | 原木截面（年轮） | 棕 |
| 药水 | 瓶子 | 紫红液体 |
| 红石 | 粉末堆 | 红 |
| 唱片 | 黑胶盘 | 黑 + 红标签 |

- 测试保证：16×16、RGBA、至少 120 个不透明像素（不是空图）、10 张两两不同、与现有三个功能件的图标也不同。形状是否好看只能目视（放大预览）与游戏里看，**不由测试保证**。
