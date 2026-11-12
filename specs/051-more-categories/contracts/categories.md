# 契约：9 个类别的数据标签内容

文件：`src/main/resources/data/mcfloatingbackpack/tags/item/extension/<id>.json`，格式同现有的 `redstone.json`：`{"replace": false, "values": [...]}`；`#` 开头的标签条目写成 `{"id": "#…", "required": false}`，物品 id 写普通字符串。以下是**最终**内容（已对照 1.21.1 物品清单与标签逐条查证，见 [research.md](../research.md) R1、R2）。

## 11 `building` 建筑方块

标签：`#minecraft:stairs`、`slabs`、`walls`、`fences`、`fence_gates`、`doors`、`trapdoors`、`terracotta`、`wool`；`#c:bricks`、`glass_blocks`、`glass_panes`、`concretes`、`concrete_powders`、`glazed_terracottas`。

另加物品（逐个 id，约 89 个，从 1.21.1 物品清单按名字模式生成）：`*_bricks`（砖、石砖、深板岩砖、下界砖、红色下界砖、末地石砖、泥砖、苔石砖、裂纹、錾制）、`polished_*`（磨制的石头、花岗岩、闪长岩、安山岩、深板岩、玄武岩、黑石、凝灰岩）、`smooth_*`（平滑的石头、砂岩、红砂岩、石英、玄武岩）、`chiseled_*`、`cut_*`（切制砂岩、切制铜块）、`quartz_*`、`purpur_*`、`prismarine*`、`sandstone`/`red_sandstone` 家族、铜块家族（`copper_block`、`exposed/weathered/oxidized_*`、`waxed_*`、`copper_grate`、`copper_bulb`、`chiseled_copper`）。

## 12 `natural` 自然方块

标签：`#c:stones`、`cobblestones`、`sands`、`gravels`、`netherracks`、`end_stones`、`obsidians`；`#minecraft:dirt`、`#minecraft:sand`。

另加物品：`clay`、`mud`、`snow`、`snow_block`、`ice`、`packed_ice`、`blue_ice`、`soul_sand`、`soul_soil`、`mycelium`、`podzol`、`coarse_dirt`、`rooted_dirt`、`magma_block`、`calcite`、`tuff`、`dripstone_block`、`pointed_dripstone`、`amethyst_block`、`budding_amethyst`；R2 补充的地形杂物约 45 个（幽匿家族、紫水晶芽与簇、骨块、蘑菇方块与菌柄、燧石、黏土球、海绵、蜘蛛网、龟蛋、嗅探兽蛋、蛙卵、干海带块、干草块、基岩、下界疣块、荧石、菌光体、蛙明灯、土径、泥砖块、镀金黑石、强化深板岩、紫颂植株、爆裂紫颂果）。

## 13 `plant` 植物

标签：`#minecraft:flowers`（含小花、高花、樱花叶、开花杜鹃叶、红树胚芽、紫颂花）、`#minecraft:leaves`、`#c:mushrooms`。

另加物品：`short_grass`、`tall_grass`、`fern`、`large_fern`、`vine`、`glow_lichen`、`hanging_roots`、`lily_pad`、`moss_block`、`moss_carpet`、`cactus`、`sugar_cane`、`bamboo`、`kelp`、`seagrass`、`dead_bush`、`small_dripleaf`、`big_dripleaf`、`spore_blossom`、`azalea`、`flowering_azalea`、`pink_petals`、各色珊瑚/珊瑚扇/珊瑚块（含死的）。**树苗留在 `crop`，南瓜、西瓜留在 `crop`，不重复。**

## 14 `decor` 装饰

标签：`#minecraft:candles`、`banners`、`beds`、`wool_carpets`、`signs`、`hanging_signs`、`skulls`、`decorated_pot_sherds`；`#c:bookshelves`、`chains`、`dyes`。

另加物品：`painting`、`item_frame`、`glow_item_frame`、`lantern`、`soul_lantern`、`torch`、`soul_torch`、`flower_pot`、`armor_stand`、`decorated_pot`；R2 补充：`sea_lantern`、`jack_o_lantern`、`carved_pumpkin`、`iron_bars`、`ladder`、`scaffolding`、`bell`、`campfire`、`soul_campfire`、`end_crystal`、`lodestone`、`name_tag`。

## 15 `mobdrop` 怪物掉落

标签：`#c:bones`、`strings`、`gunpowders`、`leathers`、`feathers`、`slime_balls`、`ender_pearls`、`eggs`、`nether_stars`、`#c:rods/blaze`、`#c:rods/breeze`。

另加物品：`ghast_tear`、`phantom_membrane`、`rabbit_hide`、`rabbit_foot`、`ink_sac`、`glow_ink_sac`、`honeycomb`、`turtle_scute`、`armadillo_scute`、`shulker_shell`、`slime_block`、`honey_block`、`echo_shard`。

## 16 `storage` 容器收纳

标签：`#c:chests`、`barrels`、`shulker_boxes`（**不是** `#minecraft:shulker_boxes`）、`#c:player_workstations/crafting_tables`、`player_workstations/furnaces`、`villager_job_sites`。

另加物品：`bundle`。

## 17 `transport` 交通运输

标签：`#minecraft:rails`、`boats`、`chest_boats`。

另加物品：`minecart`、`chest_minecart`、`furnace_minecart`、`hopper_minecart`、`tnt_minecart`、`saddle`、`lead`、`carrot_on_a_stick`、`warped_fungus_on_a_stick`、`elytra`。

## 18 `enchant` 附魔与书

**组件**：带 `STORED_ENCHANTMENTS` 的物品（附魔书），写在 `ItemCategoryMatcher`；**不认 `ENCHANTMENTS`**。

标签文件里只有物品：`book`、`writable_book`、`written_book`、`enchanted_book`、`lapis_lazuli`、`enchanting_table`、`bookshelf`、`experience_bottle`、`anvil`、`chipped_anvil`、`damaged_anvil`。

## 19 `meat` 肉与鱼

标签：`#minecraft:meat`、`#minecraft:fishes`、`#c:foods/raw_meat`、`cooked_meat`、`raw_fish`、`cooked_fish`。这些物品仍带食物组件，所以也匹配 `food` 行；先后顺序见 research R3。

## 已知覆盖缺口

约 205 个物品不在任何类别：刷怪蛋、锻造模板与旗帜图案、按钮与压力板、桶、各种矿物整块、信标、刷怪笼等（见 research R2）。通用扩展可收；也可用 018 的标签管理器给它们打类别 id 的手动标签。
