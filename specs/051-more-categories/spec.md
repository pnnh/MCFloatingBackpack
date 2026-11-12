# Feature Specification: 更多分类扩展（9 个新类别）

**Feature Branch**: `051-more-categories`

**Created**: 2026-11-12

**Status**: Draft

**Input**: 用户（2026-11-12）："关于分类扩展物品，我们目前做了 10 种，是否有更多的品类可以推荐？似乎 10 种无法覆盖我的世界原版游戏中常见的物品分类。"——讨论后确认：新增 8 个推荐类别并加上"植物""肉与鱼"，染料并进装饰；配方沿用现有风格；全部一起做成一个 feature。

## Clarifications

### Session 2026-11-12（方案阶段，用户已确认）

- Q: 要新增哪些类别？ → A: 建筑方块、自然方块、**植物**（花草足够多，独立成类，不并进自然方块）、装饰（**含染料**，不单独成类）、怪物掉落、容器收纳、交通运输、附魔与书、**肉与鱼**（已被"食物"覆盖，但占了原版食物的约 40%，独立成类）。共 **9 个**新类别，总数 10 + 9 = **19**（见下面"数量说明"）。
- Q: 配方？ → A: 沿用现有风格：4 个铁锭在四个角，上、下、左、右、中间 5 格放"代表性材料"，中间是该类别的"工作方块"；**用下表的材料，不换**。
- Q: 一次做完还是分批？ → A: 一个 feature 一次做完。

### 数量说明

用户先说"按你的建议做成 10 类"，后来说"染料并进 decor，然后 10 类一起做（包括肉类和花草）"。我的建议里有 10 个新类别（8 个推荐 + 植物 + 肉与鱼，其中染料是 8 个推荐之一）；染料并进装饰之后是 **9 个**。如果用户其实想要第 10 个，规格里只要再追加一行类别（一项数据、一个物品、一个配方），改动很小；**默认按 9 个做**，在 plan 阶段之前请用户确认。

## 背景

分类扩展（035～038）现在有 10 个类别：食物、工具、武器、盔甲、矿物、农作物、木材、药水、红石、唱片，加上不分类的通用扩展（038）。它们覆盖的都是"功能性物品"；背包里最占格子的**方块与杂物**（石头泥土、建筑方块、花草、怪物掉落、箱子、铁轨、附魔书……）没有对应的扩展件，只能放主格子。本 feature 补上 9 个类别，每个类别一个扩展件物品、一个数据标签、一个配方、一种底色、一张贴图。

035 的设计让这件事主要是**数据**：类别 id 同时是行的类别、018 的手动标签预设、数据标签 `mcfloatingbackpack:extension/<id>` 的最后一段、扩展件物品 `<id>_extension` 的前缀；`ExtensionCategories.IDS` 的顺序一旦发布就固定，所以**新类别只能追加在末尾**。

## 新类别（追加到 `ExtensionCategories.IDS` 末尾，按此顺序）

| 序 | id | 中文名 | 英文名 | 匹配依据（数据标签 `mcfloatingbackpack:extension/<id>` 的内容；plan 阶段逐条查证在 1.21.1 / NeoForge 21.1 里存在，不存在的标签改成逐个列出物品 id） |
|---|---|---|---|---|
| 11 | `building` | 建筑方块 | Building | `#minecraft:stairs`、`slabs`、`walls`、`fences`、`fence_gates`、`doors`、`trapdoors`、`terracotta`、`wool`；`#c:bricks`、`glass_blocks`、`glass_panes`、`concretes`、`concrete_powders`、`glazed_terracottas` |
| 12 | `natural` | 自然方块 | Natural | `#c:stones`、`cobblestones`、`sands`、`gravels`、`netherracks`、`end_stones`、`obsidians`；`#minecraft:dirt`、`sand`；另外逐个列出：黏土、泥巴、雪、冰（含浮冰、蓝冰）、灵魂沙、灵魂土、菌丝、砂岩等原版地形方块（plan 阶段列表） |
| 13 | `plant` | 植物 | Plant | `#minecraft:flowers`（26 种，含小花、高花、樱花叶、开花杜鹃叶、红树胚芽、紫颂花）、`#minecraft:leaves`（10 种）、`#c:mushrooms`；另外逐个列出：草、蕨、大型蕨、高草、藤蔓、睡莲、苔藓、仙人掌、甘蔗、竹子、海带、海草、枯灌木、滴水叶、荧光地衣、垂根、各色珊瑚/珊瑚扇/珊瑚块（含死的）。树苗留在"农作物"，南瓜、西瓜在"农作物"，不重复 |
| 14 | `decor` | 装饰 | Decor | `#minecraft:candles`、`banners`、`beds`、`wool_carpets`、`signs`、`hanging_signs`、`skulls`、`decorated_pot_sherds`；`#c:bookshelves`、`chains`、`dyes`；另外逐个列出：画、物品展示框（含发光）、灯笼（含灵魂灯笼）、火把（含灵魂、红石之外的）、花盆、盔甲架、装饰罐。**染料并在这里**（`#c:dyes`；`#c:dyed` 有 192 个物品太宽，不用） |
| 15 | `mobdrop` | 怪物掉落 | Mob drops | `#c:bones`、`strings`、`gunpowders`、`leathers`、`feathers`、`slime_balls`、`ender_pearls`、`eggs`、`nether_stars`、`rods/blaze`、`rods/breeze`；另外逐个列出：恶魂之泪、幻翼膜、兔子皮、兔子脚、墨囊、发光墨囊、鞘翅之外的"掉落材料"、蜂巢蜜脾、鳞甲、潜影壳 |
| 16 | `storage` | 容器收纳 | Storage | `#c:chests`、`barrels`、`#c:shulker_boxes`、`#c:player_workstations/crafting_tables`、`player_workstations/furnaces`、`#c:villager_job_sites` |
| 17 | `transport` | 交通运输 | Transport | `#minecraft:rails`、`boats`、`chest_boats`；另外逐个列出：各种矿车（普通、箱子、熔炉、漏斗、TNT）、鞍、拴绳、胡萝卜钓竿、诡异菌钓竿、鞘翅 |
| 18 | `enchant` | 附魔与书 | Enchanting | **按数据组件**：带 `STORED_ENCHANTMENTS` 的物品（附魔书）；**不按 `ENCHANTMENTS`**（否则每件附魔工具都会涌进来）；另外逐个列出：书、书与笔、青金石、附魔台、书架、经验瓶、铁砧 |
| 19 | `meat` | 肉与鱼 | Meat & Fish | `#minecraft:meat`、`#minecraft:fishes`、`#c:foods/raw_meat`、`cooked_meat`、`raw_fish`、`cooked_fish`；这些物品仍然带食物组件，所以**同时也匹配"食物"行**（放进哪一行取决于行的先后，035 的既有规则） |

- 每个新类别的 id 都 ≤ 16 个字符；`plant` 已经是 018 的预设标签。
- 同一件物品可能匹配多个类别（木板楼梯既是 `wood` 也是 `building`；红石灯既是 `redstone` 也是 `decor`……）：沿用 035 的规则"放进第一个有空位且匹配的行"，整理（047）也复用这条规则，**不需要新机制**。
- 其中"逐个列出的物品 id"是我按对原版的了解写的**草案**，具体清单与 `required: false` 的写法在 plan 阶段对照 1.21.1 的物品注册表核对。

## 扩展件配方（沿用现有风格：4 个铁锭在角，5 格放材料，中间是工作方块）

有序配方，图样 `I A I / B D B / I E I`（`I` = 铁锭）：

| 扩展件 | A（上） | B（左、右） | D（中间） | E（下） |
|---|---|---|---|---|
| `building_extension` 建筑扩展 | 玻璃 | 石砖 | 切石机 | 陶瓦 |
| `natural_extension` 自然扩展 | 草方块 | 圆石 | 铁锹 | 沙子 |
| `plant_extension` 植物扩展 | 向日葵 | 橡树树叶 | 堆肥桶 | 睡莲 |
| `decor_extension` 装饰扩展 | 画 | 灯笼 | 织布机 | 蜡烛 |
| `mobdrop_extension` 怪物掉落扩展 | 骨头 | 线 | 黏液块 | 末影珍珠 |
| `storage_extension` 容器收纳扩展 | 箱子 | 木桶 | 漏斗 | 陷阱箱 |
| `transport_extension` 交通运输扩展 | 矿车 | 铁轨 | 橡木船 | 鞍 |
| `enchant_extension` 附魔与书扩展 | 书 | 青金石 | 附魔台 | 书架 |
| `meat_extension` 肉与鱼扩展 | 熟牛肉 | 熟鳕鱼 | 烟熏炉 | 熟鸡肉 |

- "橡树树叶"不能作为原版物品标签的任意树叶，plan 阶段改用标签 `#minecraft:leaves`，其余尽量用具体物品；红石之类现有配方里已经用到的核心材料不重复限制。
- 不加解锁进度（现有配方都没有）；创造物品栏排在现有分类扩展之后、通用扩展之前，顺序同上表。

## 已查证的事实（读本仓库源码；规格要尊重）

- `ExtensionCategories.IDS` 是 10 个 id 的固定顺序，`ItemCategoryMatcher` 为每个 id 建一个物品标签键 `mcfloatingbackpack:extension/<id>`，`food` 另认食物组件、`potion` 另认药水内容组件；其余都只看标签与手动标签。
- 一个类别还需要的东西（以现有 10 个为准）：`ModItems` 里的 `<id>_extension` 物品（`CategoryExtensionItem`，堆叠 1）、一张 16×16 贴图与模型、一个配方、`CategoryStyle` 的底色（`edge`/`fill`）与空格里显示的"幽灵图标"（`ghostItemId` = `<id>_extension`）、`CategoryNames`/`PresetTags` 的语言键 `tag.mcfloatingbackpack.preset.<id>`、数据标签 JSON、中英文物品名。
- 018 的手动标签预设 `PresetTags.IDS` 现有 13 个（`stone wood food ore trash tool weapon armor plant redstone crop potion disc`），其中类别 id 同时是预设；新类别要把 8 个新 id（`plant` 已有）追加进去。
- 行数上限 `MAX_ROWS = 35` 不变；19 个类别不会挤爆。
- 数据修订 `DataRevision.CURRENT` 现为 2；新增"只能存在背包里的物品种类"与新的类别 id 存进 `extension_rows`，按 `release-process.md` 的规则要在 DELIVERY 里写明是否递增（预期 2→3）。

## 尚未验证（plan 阶段先查证；验证前不得声称已满足）

- ~~上表里每个标签是否存在~~ **已在 plan 阶段查证**（research R1）：只有 `#minecraft:shulker_boxes` 不存在，已改 `#c:shulker_boxes`；`#c:dyed` 太宽已去掉。
- ~~覆盖与重叠~~ **已查证**（research R2、R3）：补充家族后覆盖约 84 %，缺口（刷怪蛋、模板、按钮与压力板等）已记录。
- ~~标签面板的下拉容量~~ **已查证**（research R5）：下拉一次最多 5 项、靠模糊匹配缩小，无需改界面；游戏里仍抽查一次。
- 新增 9 个类别在 `CategoryStyleTest` 里的底色要与现有 10 个加通用共 11 个互相区分（看得出的色差），plan 阶段给出配色草案并在游戏里看一次。
- 用户自己建的手动标签里，如果恰好叫 `building`、`natural`、`decor`、`mobdrop`、`storage`、`transport`、`enchant`、`meat`，升级后它们会变成"类别标签"（带这个标签的物品能进对应的行）：影响面很小，写进 DELIVERY 的已知变化，不做迁移。
- 旧版本打开带新扩展件的背包：新物品在旧版本里是未知物品（040 的降级口径：不支持降级）；新类别 id 存在行数据里，旧版本按"不认识的类别"处理（035 已有该路径）——游戏里验证一次。
- 贴图：036 的 10 张是**手绘**的（Pillow 脚本逐像素画 16×16 RGBA：1 像素深色描边的铁灰色圆角底板，中间 10×10 的类别图案，左上亮、右下暗；`specs/036-category-extension-items/research.md` R4）；新 9 张沿用同一套画法与风格（建筑：砖墙；自然：草方块；植物：小花；装饰：画框；怪物掉落：骨头；容器收纳：箱子；交通运输：铁轨；附魔与书：书；肉与鱼：牛排），在 18 像素格子里能否分辨只能在游戏里看，记为未验证。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 9 个新扩展件：合成、放置、多出一行 (Priority: P1)

玩家用配方合成新的扩展件，放进背包的功能区，背包多出一行 9 格，只收该类别的物品。

**Why this priority**: 没有物品就没有新类别。

**Independent Test**: 开发环境里创造栏取到 9 个新扩展件；合成建筑扩展（铁锭、玻璃、石砖、切石机、陶瓦）；放进铁背包的下区，主区域多一行；楼梯、玻璃、羊毛放得进该行，圆石放不进（提示被拒）。

**Acceptance Scenarios**:

1. **Given** 创造栏，**Then** 9 个新扩展件在现有分类扩展之后、通用扩展之前，顺序同类别表。
2. **Given** 配方表，**Then** 每个扩展件有一个有序配方，图样与材料与上表一致，结果各 1 个；不加解锁进度。
3. **Given** 放进三个功能区之一，**Then** 背包多一行 9 格，该行只收该类别的物品；放在主区域则是普通物品。
4. **Given** 每个新扩展件，**Then** 有中英文名、"放进功能区，增加一行只能存 %s 的格子"的提示（`%s` 取预设标签名）。

---

### User Story 2 - 每个类别收对的东西 (Priority: P1)

每个新类别的数据标签把原版里"该属于它"的物品都包含进去：放得进去、放得进对的行；不属于它的放不进。

**Why this priority**: 类别的价值就在匹配得准。

**Independent Test**: 单元测试按原版标签展开后抽查：建筑行收石砖楼梯、玻璃板、橙色混凝土，不收圆石；植物行收蒲公英、橡树树叶、红蘑菇、脑珊瑚扇，不收橡树树苗（在农作物）；肉与鱼行收熟牛肉、生鳕鱼、腐肉，不收面包；附魔行收附魔书、青金石，不收一把带附魔的钻石镐；装饰行收红色染料、白色蜡烛、灯笼；游戏里各放几件验证。

**Acceptance Scenarios**:

1. **Given** 一件匹配的物品，**When** 放进对应的行，**Then** 成功；**Given** 不匹配的物品，**Then** 被拒、零变化（035 的规则）。
2. **Given** 一件同时匹配两个类别的物品（木板楼梯），**Then** 能放进任一匹配的行；整理（047）把它放进行里的先后顺序与 035 的规则一致。
3. **Given** 肉与鱼，**Then** 它们既能进 `meat` 行也能进 `food` 行（带食物组件）。
4. **Given** 带附魔的工具，**Then** 不匹配 `enchant`；附魔书匹配。
5. **Given** 玩家用 018 的标签管理器给某物品打上类别 id 的手动标签，**Then** 它也进该类别的行（035 的既有规则，对新 id 同样成立）。

---

### User Story 3 - 界面：底色、幽灵图标、预设标签 (Priority: P2)

新类别的行在面板里有自己的底色与分隔线、空格里有淡淡的扩展件图标；标签面板的预设下拉里有新类别的名字；悬停提示与隐藏行的黄点提示里的类别名有中英文。

**Why this priority**: 没有界面，新行和普通格子分不清；但先有规则才有意义。

**Independent Test**: 放 9 个新扩展件的背包截图：每行底色互相区分、与现有 10 个加通用也区分，空格里有对应扩展件的淡图标；悬停行头显示类别名；标签面板里能选到新类别；拿走一个扩展件（行里有东西）出现黄点，提示里有该类别的名字。

**Acceptance Scenarios**:

1. **Given** 新类别的行，**Then** 有该类别的底色（`CategoryStyle.edge/fill`）与分隔线，空格显示 `<id>_extension` 的淡图标。
2. **Given** 标签面板，**Then** 预设下拉里有 21 个预设（原 13 + 新 8，`plant` 不重复），模糊匹配与滚动正常。
3. **Given** 中英文界面，**Then** 类别名（行悬停、黄点提示、`/hidden`）都有对应的语言键。
4. **Given** 行里有东西而扩展件被拿走，**Then** 黄点与 `/hidden` 的汇总里列出该类别的名字与格数（037/044 的既有规则）。

---

### User Story 4 - 旧存档、旧数据不受影响 (Priority: P2)

没有新扩展件的旧背包照常载入；快照（030）能保存与恢复带新类别行的背包；新增的数据修订（预期 3）按 040/041 的规则生效。

**Why this priority**: 数据兼容是这个项目一贯的底线。

**Independent Test**: 旧背包（修订 2）打开后照常，保存后修订变 3；带新类别行的背包做快照再恢复，行与内容一致；旧版本读带新类别 id 的行时按"未知类别"处理、不丢物品（035 已有路径，游戏里验证一次）。

**Acceptance Scenarios**:

1. **Given** 旧背包，**Then** 照常打开，功能不变。
2. **Given** 带新类别行的背包，**When** 快照后恢复，**Then** 行的类别、顺序、格子内容一致。
3. **Given** 数据修订，**Then** 递增并在 DELIVERY 写明理由、发布说明提醒降级前备份。

---

### Edge Cases

- **物品匹配多个类别**：按 035 的规则（先后顺序），不新增机制；plan 阶段列出几个典型的重叠（楼梯、红石灯、蜂巢蜜脾）并确认行为符合预期。
- **标签里的物品在某个整合包里不存在**：所有标签条目都 `required: false`，缺了不报错。
- **别的模组往原版标签里加物品**（例如 `#c:ores`）：自然被新类别收进去，这是数据驱动的好处。
- **类别太多界面太挤**：行总数上限不变；扩展件在创造栏变多（10→19），属于预期。
- **自定义标签名与新 id 冲突**：见"尚未验证"，写进 DELIVERY。
- **行里原有物品在新规则下不再匹配**：数据标签升级后若某物品不再匹配所在行，沿用 035 的处理（保留在行里，不丢）。
- **降级**：不支持（口径不变）。

## Requirements *(mandatory)*

### Functional Requirements

**类别与数据**

- **FR-001**: `ExtensionCategories.IDS` MUST 追加 9 个类别（`building`、`natural`、`plant`、`decor`、`mobdrop`、`storage`、`transport`、`enchant`、`meat`），顺序固定在现有 10 个之后，id ≤ 16 个字符；`ALL` 随之变为 10 + 9 + `general`。
- **FR-002**: MUST 为每个新类别提供数据标签 `mcfloatingbackpack:extension/<id>`，内容按类别表；所有条目 MUST 为 `required: false`；`enchant` MUST 额外认 `STORED_ENCHANTMENTS` 数据组件（不认 `ENCHANTMENTS`）。
- **FR-003**: `PresetTags` MUST 追加新类别 id 中尚不是预设的 8 个（`building natural decor mobdrop storage transport enchant meat`），并有语言键（中英文），预设标签面板能选到。

**扩展件物品**

- **FR-004**: MUST 有 9 个扩展件物品 `<id>_extension`（`CategoryExtensionItem`，堆叠 1），注册为扩展件解析器认得的物品；放进三个功能区之一才有功能（与现有 10 个相同）。
- **FR-005**: MUST 有 9 个配方，图样与材料见"扩展件配方"表；不加解锁进度；MUST 有 9 张 16×16 贴图与模型、中英文物品名；提示沿用现有"放进功能区，增加一行只能存 %s 的格子"。
- **FR-006**: 9 个扩展件 MUST 在创造栏现有分类扩展之后、通用扩展之前，按类别表的顺序；MUST 受 `never_trash` 保护的做法同现有扩展件（若现有扩展件在 `never_trash` 里则同样处理，plan 阶段确认）。

**界面**

- **FR-007**: `CategoryStyle` MUST 为 9 个新类别各给一种底色（`edge`/`fill`），与现有 10 个类别和通用的白互相区分；空格里的幽灵图标取对应的 `<id>_extension`。
- **FR-008**: 类别名的语言键 MUST 中英文齐全（行悬停、黄点提示、`/hidden` 都用 `tag.mcfloatingbackpack.preset.<id>`）。

**数据与兼容**

- **FR-009**: 数据修订 MUST 在 DELIVERY 里写明是否递增及理由（预期 2→3：新的只能存在背包里的物品种类与新的类别 id）；递增时 MUST 同改 `DataRevision.CURRENT` 与相关测试；协议号是否变化由 plan 论证（预期不变，类别 id 在既有包里是字符串）。
- **FR-010**: 旧背包 MUST 照常载入；快照（030）MUST 保存与恢复带新类别行的背包；不支持降级的口径不变。
- **FR-011**: MUST NOT 新增配置项、依赖、网络包（除非 plan 论证必须）；011～050 的测试里写死"10 个类别"的断言按新数量更新（`ExtensionCategoriesTest`、`CategoryStyleTest`、`CategoryNamesTest`、`ExtensionTagsResourcesTest`、`CategoryExtensionResourcesTest`、`RowSetTest`、`RowReconcilerTest` 等），其余不改。

**测试与文档**

- **FR-012**: 纯逻辑（类别表、样式表的完整性与区分度）MUST 有单元测试；标签内容、配方、贴图、语言键、创造栏顺序用资源测试；接线用源码文本检查；游戏里验证放置、匹配、界面（宪法 V）。
- **FR-013**: 文档 MUST 更新：`README.md`/`README_CN.md`（19 个类别的说明）、`docs/plan/category-extensions.md`、本 feature 的 `DELIVERY.md`（中文）；共享图集前后各量一次（宪法 VI）。

### Key Entities *(include if feature involves data)*

- **类别（Category）**：id、中文名、英文名、数据标签、底色、扩展件物品；顺序固定，只追加。
- **扩展件物品（Category extension item）**：`<id>_extension`，无状态。
- **数据标签**：`mcfloatingbackpack:extension/<id>`，数据包与别的模组可以往里加物品。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 9 个新类别用原版标签展开后，抽查表里每类至少 5 件应收与 3 件不应收的物品，**全部符合**（单元测试或资源测试）。
- **SC-002**: 新旧类别（19 个）的底色两两可区分（单元测试断言任意两种颜色的色差大于阈值）；每个类别都有扩展件物品、贴图、模型、配方、语言键、预设标签（资源测试逐个断言）。
- **SC-003**: 游戏里（容器自动验证）：9 个新扩展件各合成一次（或创造栏取）、放进背包、多出一行；每行放进 2 件匹配的物品、1 件不匹配的被拒；底色与幽灵图标可见；拿走扩展件后黄点出现；记录没验证的。
- **SC-004**: 旧背包（修订 2）打开保存后修订为 3；带新类别行的背包快照恢复后一致；没有物品丢失。
- **SC-005**: `./gradlew compileJava test` 通过；`DependencyRulesTest` 通过；011～050 的测试除写死 10 的断言外不改而通过；共享图集尺寸前后都不超过 2048。

## Assumptions

- 类别的中文名与英文名按类别表；`plant` 沿用 018 的预设（中文"植物"）。
- 数据标签里"逐个列出"的物品 id 是草案，plan 阶段核对；允许少量遗漏，数据驱动（数据包与别的模组可补）。
- 新类别的扩展件放进功能区、多出一行，规则与现有 10 个完全一致（035～038），不新增行为。
- 贴图沿用 036 的手绘脚本做法，用户之后可以替换。
- 本 feature 不做用户自定义类别（工作量大得多，另议）。
