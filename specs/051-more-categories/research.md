# Research: 更多分类扩展（9 个新类别）

方法：用 Python 读 1.21.1 原版客户端 jar 里的 `data/minecraft/tags/item/**`、NeoForge 21.1.251 jar 里的 `data/c/tags/item/**` 与 `assets/minecraft/models/item/*.json`（作为"物品清单"，去掉 `_01` 之类的模型变体、`*_trim`、`*_pulling_*`、调试用物品后得到 **1319** 个真实物品），把规格里每个标签展开成物品集合，数覆盖与重叠。分析脚本在会话临时目录里（不进仓库），结论如下。

## R1 规格里的标签逐条查证

结论：**除两处外全部存在**，两处要改：

| 规格里的写法 | 查证结果 | 决定 |
|---|---|---|
| `#minecraft:shulker_boxes` | 原版没有这个标签 | 改用 NeoForge 的 `#c:shulker_boxes` |
| `#c:dyed` | 存在但有 192 个物品（所有染色方块），涌进装饰行、与建筑严重重叠 | **不用**；装饰只收 `#c:dyes`（染料）与下面单列的物品 |

其余（`#minecraft:flowers`、`leaves`、`stairs`、`slabs`、`walls`、`fences`、`candles`、`banners`、`beds`、`rails`、`boats`、`meat`、`fishes` 等；`#c:stones`、`cobblestones`、`bricks`、`glass_blocks`、`concretes`、`chests`、`barrels`、`bones`、`strings`、`gunpowders`、`dyes`、`mushrooms` 等）在 1.21.1 / NeoForge 21.1.251 里都存在。

所有 `#` 条目写成 `{"id": "#…", "required": false}`（数据包或整合包删了标签不报错）；逐个列出的**原版物品 id** 写成普通字符串（与现有 `redstone.json` 同风格）；逐条对照物品清单，**没有写错的 id**。

## R2 覆盖率

只用 10 个旧类别时覆盖 **259 / 1745**（按含变体的模型数，旧口径）；加 9 个新类别后是 **963 / 1745**。按去重后的 1319 个真实物品，再把下面"补充家族"加进来（R3），覆盖 **1114 / 1319 ≈ 84 %**。

各类别展开后的物品数（含 R3 补充前）：`building` 322、`natural` 48、`plant` 94、`decor` 146、`mobdrop` 31、`storage` 35、`transport` 36、`meat` 17；`enchant` 按组件与单列物品约 11 件加所有附魔书。

**补充家族（本 feature 加入，写进 contracts/categories.md）**：

- `building` ＋ 89 个"砖、磨制、錾制、切制、平滑、石英、紫珀、海晶石、砂岩、铜块（含涂蜡、氧化）"家族的方块（按名字模式从物品清单生成，写成逐个 id）；
- `natural` ＋ 约 45 个地形杂物：幽匿家族、紫水晶芽与簇、骨块、蘑菇方块与菌柄、燧石、黏土球、海绵（含湿）、蜘蛛网、龟蛋、嗅探兽蛋、蛙卵、干海带块、干草块、基岩、下界疣块、荧石、菌光体、蛙明灯、土径、泥砖块、镀金黑石、强化深板岩、紫颂植株与爆裂紫颂果；
- `decor` ＋ 海晶灯、南瓜灯与雕刻南瓜、铁栏杆、梯子、脚手架、钟、营火与灵魂营火、末地水晶、磁石、命名牌；
- `mobdrop` ＋ 黏液块、蜂蜜块、回响碎片；`storage` ＋ 收纳袋。

**仍然不覆盖（记为已知缺口，约 205 个，都有 `general` 通用扩展兜底，也可用 018 的手动标签加进任意类别）**：80 个刷怪蛋、27 个锻造模板与旗帜图案、28 个按钮与压力板、11 种桶、煤/铁/金/钻石/青金石/下界合金/粗矿的整块、各种信标/下界传送门框/刷怪笼/试炼钥匙、烟花、地图与指南针、TNT、音符盒、点唱机、纸、糖、骨粉等杂项。**按钮与压力板更像红石，但改旧类别的数据不在本 feature 范围**（只加不改），留给以后。

## R3 重叠与行的先后（035 规则：放进第一个有空位且匹配的行，047 整理复用）

`ALL` 的顺序是 food, tool, weapon, armor, ore, crop, wood, potion, redstone, disc, **building, natural, plant, decor, mobdrop, storage, transport, enchant, meat**, general。新类别排在旧类别之后，所以重叠物品**先进旧类别的行**，旧行满了才进新行：

| 重叠 | 数量 | 行为 |
|---|---|---|
| food ∩ meat | 17 | 熟牛肉等先进 `food` 行；`food` 满了才进 `meat` 行。`meat` 行单独存在的价值：只有肉与鱼时不占食物行 |
| weapon ∩ tool | 6 | 旧的既有重叠，不动 |
| crop ∩ plant | 5 | 小麦种子之类先进 `crop` |
| wood ∩ building | 木板楼梯、栅栏、门等 | 先进 `wood`，`wood` 行满了进 `building` |
| redstone ∩ decor/storage | 红石灯、漏斗 | 先进 `redstone` |

不新增机制。验证项：`RowReconciler` 的行为测试只用字符串类别，不需要改；只在游戏里看一次"放木楼梯进 wood 行满的背包会进 building 行"。

## R4 底色草案（第一稿，要在游戏里看；与 037 同一做法）

现有 11 个（10 类别 + 通用）里最近的一对是 weapon/redstone，Lab ΔE76 = 21。9 个新颜色用下表，与全部 20 色最近的距离 ≥ 18（最近的一对是 building/weapon = 18），与槽位灰 `#8B8B8B` 的距离 ≥ 26：

| 类别 | `edge` | 说明 |
|---|---|---|
| building | `0xFFC4552E` | 砖红橙 |
| natural | `0xFF4E7D2E` | 深橄榄绿 |
| plant | `0xFF14A06A` | 翠绿 |
| decor | `0xFFE6D83A` | 金黄 |
| mobdrop | `0xFF6A2C7E` | 深紫 |
| storage | `0xFFD9A066` | 浅木色 |
| transport | `0xFF4A4F5C` | 深灰蓝 |
| enchant | `0xFF6D4BFF` | 靛紫 |
| meat | `0xFFFF8F85` | 鲑鱼粉 |

不可能 20 色都彼此很远；测试里的区分度阈值取 **ΔE ≥ 15**（现有 21 的一对要过；新草案最小 18 也过），与槽位灰 ≥ 20。颜色好不好看是主观项，留给用户在游戏里确认，不满意只改 `CategoryStyle` 一张表。

## R5 标签面板的预设下拉

`TagPanelLayout.DROPDOWN_MAX = 5`、`TagOverviewLayout.DROPDOWN_MAX = 5`：下拉**一次最多显示 5 项**，输入文字用模糊匹配缩小，没有滚动；13 → 21 个预设不改变这个行为，只是空输入时仍显示前 5 个。**不需要改界面代码**。新预设名要在 `tag.mcfloatingbackpack.preset.<id>` 里有中英文，模糊匹配既认 id 也认显示名。

## R6 数据修订与协议号

- `docs/plan/release-process.md` 的规则：存储数据"新增只能存在背包里的物品种类"要递增。新扩展件是只在背包功能区里生效的新物品，旧版本打开带新扩展件的背包会把它读成未知物品；`extension_rows` 里新的类别 id 在旧版本按"不认识的类别"保留（035 已有路径）。所以 **`DataRevision.CURRENT` 2 → 3**；040 的保护会把"更新版本保存的背包"拦下来。
- **协议号不变（19）**：类别 id 在既有网络包里是字符串；客户端用 `ExtensionCategories.IDS` 自己的顺序渲染，版本不同的两端本来就被协议号握手分开。
- 要同改的测试：`DataRevisionTest`、`BackpackContentsRevisionTest` 里若有"当前是 2"的断言（实现时逐个核对）；`TankWiringTest`、`PipeWiringTest` 里对 `DataRevision` 的引用（grep 到的 8 个文件）逐个看是否写死 2。

## R7 贴图

沿用 036 R4：Pillow 脚本逐像素画 16×16 RGBA，1 像素深色描边的铁灰色圆角底板、中间 10×10 的类别图案、左上亮右下暗。**只在 `design/demo/tools/make_category_extension_icons.py` 里加 9 个图案和 `order` 列表项**，不新建脚本。图案：建筑=砖墙、自然=草方块、植物=小花、装饰=画框、怪物掉落=骨头、容器收纳=箱子、交通运输=铁轨、附魔与书=书、肉与鱼=牛排。图标两两不同由 `CategoryExtensionResourcesTest` 已有的断言保证（"两张图一样"会失败）。是否在 18 像素槽里看得清只能在游戏里看。

## R8 匹配：`enchant` 与组件

`ItemCategoryMatcher.matches` 现在对 `food` 认 `DataComponents.FOOD`、对 `potion` 认 `POTION_CONTENTS`。给 `enchant` 加一行：`stack.has(DataComponents.STORED_ENCHANTMENTS)`，**不认 `ENCHANTMENTS`**（否则每件附魔工具都涌进来）。`STORED_ENCHANTMENTS` 在 1.21.1 的 `DataComponents` 里存在（附魔书用它）；空的附魔书（没有附魔）`has` 也为真，这是可以接受的（附魔书都进该行）。实现时用一个**纯测试**无法覆盖（需要 Minecraft 类），所以靠源码文本检查与游戏里验证。

## R9 要改的现有测试（写死"10"或"13"的断言）

`ExtensionCategoriesTest`（`IDS`、`ALL.size()` 11→20、`subList`、`ALL.get`）、`RowSetTest`（`IDS` 的列表、一处 `i < 10`）、`PresetTagsTest`（13 个列表）、`ExtensionTagsResourcesTest`（`PresetTags.ids().size()` 13、各类别的标签核对表）、`CategoryExtensionResourcesTest`（`CORE` 表、10 个）、`CategoryStyleTest`（"theTenEdgeColoursAreAllDifferent"改名与断言）、`CategoryNamesTest`、`UniversalExtensionResourcesTest`（若数 IDS）、`HiddenHintTest`、`RowReconcilerTest`、`ExtensionRulesCategoriesTest`、`RowLayoutTest`（只用字符串，**预计不用改**，实现时 `grep` 确认）。其余 011～050 的测试不改。

## R10 `never_trash`

`never_trash.json` 现在**没有**任何扩展件（`grep -c extension` = 0），所以新扩展件同样不加（FR-006 的括号条件：不处理）。

## R11 开放问题（不阻塞 plan）

- **9 还是 10 个新类别**：见 spec"数量说明"；默认 9。若要第 10 个，在 `IDS` 末尾再追加一行，其余随之。
- **用户自己建的手动标签叫 `building` 等**：升级后变成类别标签；在 DELIVERY 的"已知变化"里写明，不迁移。
- **旧版本打开带新类别的背包**：不支持降级；040/041 已拦。游戏里验证一次（standard route：`-Pmcfb.stampRevision`）。
