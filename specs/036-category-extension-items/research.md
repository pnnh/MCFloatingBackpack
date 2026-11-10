# Research: 分类扩展：10 个扩展件物品

每条决定标了依据：**已查证**是读了本仓库源码、本机 1.21.1 / NeoForge 21.1.251 的 jar 与源码包；**尚未验证**要等实现或游戏里才知道，验证前不得声称已满足。

## R1. 物品类与注册

- **决定**：`item/CategoryExtensionItem extends Item`，构造 `(String category, Properties)`，`super(properties.stacksTo(1))`，`category()` 返回类别 id；重写 `appendHoverText` 加一行提示。`ModItems` 里 `LinkedHashMap<String, DeferredHolder<Item, CategoryExtensionItem>> CATEGORY_EXTENSIONS`，在静态初始化里按 `ExtensionCategories.IDS` 循环 `ITEMS.register(id + "_extension", () -> new CategoryExtensionItem(id, new Item.Properties()))`；创意栏 `displayItems` 在 `AUTO_PICKUP` 后面同样循环 `output.accept`。
- **已查证**：`TagManagerItem` 是 `super(properties.stacksTo(1))` 的空壳；`ModItems` 的注册与创意栏写法如此；`FloatingBackpackItem` 已重写 `appendHoverText(stack, context, tooltip, flag)`，签名与源码包里 `Item.appendHoverText(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)` 一致。
- **备选**：10 个子类——否决，除了类别 id 没有任何差别。

## R2. 接入 035 的解析器

- **决定**：`MCFloatingBackpack` 构造函数里一行 `ExtensionPacks.register(stack -> stack.getItem() instanceof CategoryExtensionItem item ? item.category() : null);`。
- **已查证**：`ExtensionPacks.register(Function<ItemStack,String>)` 存在，注册表是 `CopyOnWriteArrayList`；`categoryOf` 的调用点：`BackpackInventory.placedPacks`（载入与保存时的核对）、`ExtensionPackRules.canPlace`（放入工具区）。构造函数在客户端与服务端都会执行，所以两端的解析器一致。
- **理由**：不用 `FMLCommonSetupEvent`：解析器只依赖物品类，不依赖注册表是否已冻结；构造函数里注册最简单，且没有时序问题。
- **尚未验证**：专用服务器上构造函数也会执行（NeoForge 的模组构造函数在两端都跑，预期如此）；开发期只有客户端，专用服务器没有条件，写进交付说明为"未验证"。

## R3. 配方

- **版型**：有序配方，3×3，**四角铁锭**，上、左、中、右、下 5 格放核心材料（契约见 [contracts/recipes.md](./contracts/recipes.md)）。
- **已查证**（读源码包 `Items.java`）：`water_bucket` 带 `craftRemainder(BUCKET)`，所以**农作物扩展的配方会还回一个空桶**（和原版蛋糕配方一样）；这是正常行为，不是缺陷，写进 quickstart 的预期。
- **已查证**（读本机 jar）：配方里用到的 35 个原版物品（`iron_ingot`、`smoker`、`barrel`、`golden_carrot`、`bread`、`grindstone`、`iron_pickaxe`、`iron_axe`、`iron_shovel`、`iron_hoe`、`iron_sword`、`bow`、`shield`、`arrow`、`smithing_table`、`iron_helmet`、`iron_boots`、`leather`、`blast_furnace`、`diamond`、`copper_ingot`、`gold_ingot`、`lapis_lazuli`、`water_bucket`、`hay_block`、`bone_meal`、`stonecutter`、`brewing_stand`、`glass_bottle`、`nether_wart`、`glowstone_dust`、`redstone_block`、`comparator`、`repeater`、`piston`、`redstone`、`jukebox`、`note_block`）的物品模型都在 1.21.1 的资源里，id 都存在。
- **已查证**：标签 `#minecraft:logs` 在 `data/minecraft/tags/item/logs.json`；**`#minecraft:music_discs` 不在本机的资源 jar 里**（原版数据在服务端 jar，没有逐个核对），而 NeoForge 的 `#c:music_discs`（`data/c/tags/item/music_discs.json`）存在，035 的数据标签也用它。**所以唱片配方用 `#c:music_discs`**（spec 里写的 `#minecraft:music_discs` 以此为准修正）。
- **配方撞车**：10 个结果都是我们的新物品，且四角铁锭加这些材料的组合不会与原版或已有配方（`auto_pickup` 是 `IEI/RHR/IDI`、`trash_can`、`tag_manager`）相同；`CategoryExtensionResourcesTest` 检查 10 个配方两两不同。
- **盔甲、红石、唱片的第 5 件**：皮革、红石粉、青金石（草案，用户待确认）。

## R4. 贴图

- **决定**：**手绘**，一个 Python 脚本（Pillow）逐像素画出 10 个 16×16 RGBA 图标，统一风格：1 像素深色描边的铁灰色圆角底板（暗示"铁框的扩展件"，与四角铁锭的配方呼应），中间 10×10 的类别图案（食物：面包；工具：镐；武器：剑；盔甲：头盔；矿物：矿石块；农作物：麦穗；木材：原木截面；药水：药水瓶；红石：红石粉堆；唱片：唱片）。左上亮、右下暗的光照，与原版物品图标一致。
- **已查证**：024 的自动拾取图标就是这样做的（`design/demo/tools/make_auto_pickup_icon.py`，Pillow，没有用 ludo.ai / pixellab，脚本即来源）；现有图标都是 16×16、8 位 RGBA（PNG 头部颜色类型 6）；Pillow 13.0.0 可用。
- **理由**：10 张 16×16 不值得消耗生成额度；手绘可重复、风格一致、可在脚本里调整；用 `Read` 看放大预览核对形状。
- **备选**：用 pixellab 生成——保留为退路，风格一致性不好控制。
- **尚未验证**：10 个图标在 18 像素格子里是否分辨得出、在 JEI 与手持下的观感——只能在游戏里看，记为未验证。

## R5. 语言键

- **决定**：每个物品 `item.mcfloatingbackpack.<类别>_extension`（中文"食物扩展"……"唱片扩展"；英文 "Food Extension"、"Tool Extension"、"Weapon Extension"、"Armor Extension"、"Ore Extension"、"Crop Extension"、"Wood Extension"、"Potion Extension"、"Redstone Extension"、"Disc Extension"）；一条通用提示 `item.mcfloatingbackpack.category_extension.tooltip`：中文"放进浮动背包的功能区，增加一行只能存 %s 的格子"，英文 "Put it in a function area of the floating backpack to add a row that only stores %s"；`%s` 用 `Component.translatable(PresetTags.langKey(category))`（食物、工具、武器、盔甲、矿物、农作物、木材、药水、红石、唱片）。
- **已查证**：`tag.mcfloatingbackpack.preset.<id>` 对 10 个类别 id 都已存在（035 补齐了 `crop`、`potion`、`disc`），中文名与类别名一致（矿物 = `ore`）；两个语言文件的键集合有测试要求一致。
- **注意**：英文的 `%s` 是预设的英文名（`food`、`tool` 等小写词），句子读起来是 "…only stores food"，可接受。

## R6. 创意栏与 JEI

- **已查证**：`ModItems.TAB` 的 `displayItems` 逐个 `output.accept`；JEI 自动显示原版配方类型（有序配方），本仓库已有的三个功能件的配方就是这样出现在 JEI 的，所以不需要 JEI 插件改动。
- **尚未验证**：JEI 里 `#minecraft:logs`、`#c:music_discs` 显示为可替换的一组（预期，JEI 对标签原料的标准行为）。

## R7. 过渡期的含扩展行面板

- **已查证**（读客户端代码）：`ClientBackpackView.layout()` 把 `slots.size()`（含可见行的主区域）交给 `PanelShape`/布局；017 的合法尺寸要求列数（3～9）整除主格子数。035 的会话/整份同步已经发"含可见行"的格子列表，所以含扩展行的面板在客户端会显示为**更大的普通网格**，行的格子接在最后面。
- **预期的表现**（皮革 36 格加 1 行 = 45 格）：合法列数 3、5、9（`45 % 4 ≠ 0`、`45 % 6 ≠ 0`……），缩放可能与没放扩展件时不同；行数多时（例如 36 + 35×9 = 351，或下界合金的 108 + 315 = 423）合法列数可能很少，甚至只剩 3 与 9。这些都是**过渡期的限制，交给界面 feature（037）**，本 feature 只记录观察到的现象。
- **尚未验证**：含扩展行时面板是否仍能打开、滚动、缩放；是否有档位出现"没有任何合法尺寸"的情形。

## R8. 035 里验证不了的几项，在本 feature 里怎么验

有了扩展件物品，035 DELIVERY 里"未验证"的前四条可以在 quickstart 里逐条做：①带物品的可见行的同步往返（放扩展件、放物品、重进世界）；②扩展行里放物品的各条路径（点击、拖拽、聚拢、Ctrl 转移、自动拾取、垃圾桶取回）；③保存时可见行变化后的整份同步（在面板里拿走扩展件、放回）；④行总数 35 的拒绝与提示（下界合金背包放满）。**拒绝时的提示**：035 的 `ExtensionPackRules` 在放不下时只做"拒绝、零变化、`sendSlotUpdate`"，**没有提示信息**（spec 要求"给出提示"）——这是 035 留下的一个缺口，见下条。

## R9. 035 的缺口：放不进去时没有提示

- **已查证**：`BackpackClickPayload` 在 `ExtensionPackRules.canPlace` 为假时直接 `sendSlotUpdate` 并 `return`，没有给玩家任何消息；`ToolAreaRules` 的"这里只能放一个垃圾桶"提示是客户端按已有的语言键 `message.mcfloatingbackpack.limit.*` 显示的（客户端预判）。
- **决定**：本 feature **补一个最小的服务端提示**：拒绝时给玩家发一条 actionbar 消息 `message.mcfloatingbackpack.limit.extension_rows`（中文"这只背包的扩展行已达上限（35）"，英文 "This backpack already has the most extension rows (35)"）。这是对 035 的补全，不是新规则；记入交付说明。
- **备选**：留给界面 feature 统一做——否决：035 的 spec 已把提示列为要求，不应一直缺着。
- **尚未验证**：actionbar 消息在面板打开（创造/生存）时是否可见。

## R10. 不做的事

界面（037）、搜索与整理、新类别、协议改动、035 规则的修改。
