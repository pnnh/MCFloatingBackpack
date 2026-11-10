# Contract: 物品、配方、资源、界面文字

## 1. 物品与资源

每个档位 `t ∈ {leather, iron, gold, diamond, netherite}`：

| 资源 | 路径 |
|---|---|
| 模型 | `assets/mcfloatingbackpack/models/item/<t>_general_extension.json`（`minecraft:item/generated`，`layer0` → `item/<t>_general_extension`） |
| 贴图 | `assets/mcfloatingbackpack/textures/item/<t>_general_extension.png`（16×16 RGBA，手绘） |
| 配方 | `data/mcfloatingbackpack/recipe/<t>_general_extension.json`（`minecraft:crafting_shaped`，`category: misc`） |
| 名字 | `item.mcfloatingbackpack.<t>_general_extension` |

物品提示键：`item.mcfloatingbackpack.universal_extension.tooltip`（参数：档位名）。档位名键：`tier.mcfloatingbackpack.<t>`（"皮革"/"铁"/"金"/"钻石"/"下界合金"）。

## 2. 配方草案（版型 `ITI / LCR / IBI`，`I` = 铁锭；**待用户确认**）

| 档位 | T（上） | L（左） | C（中） | R（右） | B（下） |
|---|---|---|---|---|---|
| 皮革 | 箱子 | 皮革 | 漏斗 | 皮革 | 线 |
| 铁 | 箱子 | 铁锭 | 漏斗 | 铁锭 | 铁锭 |
| 金 | 箱子 | 金锭 | 漏斗 | 金锭 | 金锭 |
| 钻石 | 钻石 | 箱子 | 漏斗 | 箱子 | 钻石 |
| 下界合金 | 下界合金锭 | 末影箱 | 潜影壳 | 末影箱 | 钻石 |

（物品 id：`minecraft:chest`、`leather`、`hopper`、`string`、`iron_ingot`、`gold_ingot`、`diamond`、`netherite_ingot`、`ender_chest`、`shulker_shell`；plan 阶段已知这些在 1.21.1 都存在，实现时在测试里核对。）

## 3. 成本对照（**粗估**，"铁锭当量"：铁锭 1、金锭 1.5、钻石 2.5、箱子 1、漏斗 6（5 铁锭 + 箱子）、皮革 0.5、线 0.2）

| | 估算 | 说明 |
|---|---|---|
| 分类扩展（现有 10 个） | 约 8～16 | 4 铁锭 + 一个功能方块 + 几件材料（036 的配方，音符盒、唱片机、高炉等较贵） |
| 皮革通用扩展 | 约 12 | 4 + 漏斗 6 + 箱子 1 + 皮革 1 + 线 0.2 |
| 铁通用扩展 | 约 14 | 4 + 6 + 1 + 3 |
| 金通用扩展 | 约 16 | 4 + 6 + 1 + 4.5 |
| 钻石通用扩展 | 约 21 | 4 + 6 + 2 + 5 + … |
| 下界合金通用扩展 | 远高于钻石 | 下界合金锭 + 2 个末影箱 + 潜影壳 + 钻石：顶级背包的额外空间，高价材料消耗 |

升级成本（材料块 + 模板的复制成本，模板复制按 7 钻石 ≈ 17.5 估）：

| 升级 | 估算 | 两个同档通用扩展 | 比较 |
|---|---|---|---|
| 皮革→铁 | 铁块 9 + 17.5 ≈ 27（还要先找到一个模板） | 皮革 ×2 ≈ 24 | 略低 |
| 铁→金 | 金块 13.5 + 17.5 ≈ 31 | 铁 ×2 ≈ 28 | 略低 |
| 金→钻石 | 钻石块 22.5 + 17.5 ≈ 40 | 金 ×2 ≈ 32 | 低 |
| 钻石→下界合金 | 下界合金块（9 锭）+ 模板，远高于以上 | 钻石 ×2 ≈ 42 | 远低 |

结论：每一档都比分类扩展贵或相当，且两个通用扩展都低于同档升级，下界合金档是额外空间的材料消耗；最低两档只是略低，用户看过后可调（改配方 JSON 与测试里的一张表）。

## 4. 贴图风格

铁灰描边圆角底板（同 036）+ 10×10 的"开口箱子，里面是格子网"；各档主色：皮革棕、铁灰、金黄、钻石青、下界合金深紫黑。与 10 个分类扩展的图案（面包、镐、剑……）明显不同；5 档之间靠主色区分。脚本放大预览目视检查。

## 5. 语言键（中英文，两个文件键集合一致）

| 键 | zh_cn | en_us |
|---|---|---|
| `item.mcfloatingbackpack.leather_general_extension` | 皮革通用扩展 | Leather Universal Extension |
| `item.mcfloatingbackpack.iron_general_extension` | 铁通用扩展 | Iron Universal Extension |
| `item.mcfloatingbackpack.gold_general_extension` | 金通用扩展 | Gold Universal Extension |
| `item.mcfloatingbackpack.diamond_general_extension` | 钻石通用扩展 | Diamond Universal Extension |
| `item.mcfloatingbackpack.netherite_general_extension` | 下界合金通用扩展 | Netherite Universal Extension |
| `item.mcfloatingbackpack.universal_extension.tooltip` | 增加一行可以放任何物品的格子。只对%s背包生效；放在左区或右区才生效，每个区只算一个。 | Adds a row that takes any item. Only works in a %s backpack, in the left or right area, one per area. |
| `tier.mcfloatingbackpack.leather` … `netherite` | 皮革、铁、金、钻石、下界合金 | Leather, Iron, Gold, Diamond, Netherite |
| `category.mcfloatingbackpack.general` | 通用 | General |
| `panel.mcfloatingbackpack.row.tooltip.general` | 通用行：可以放任何物品 | General row: takes any item |

## 6. 界面

- `CategoryStyle`：`general` 的 `edge`（不透明）初稿取**近白 `#F2F2F2`**：10 个分类的色相（琥珀、蓝、红、紫、青、绿、棕、粉、暗红、靛蓝）都不是白色，白色在灰色槽底上又清楚，也符合"通用"的直觉；缺点是与悬停高亮（半透明白）有点像，悬停是瞬时的，可接受。游戏里看过再调（只改 `CategoryStyle` 一处）。
- 底色 `fill`：`edge` + 透明度 **0x4A（约 29%）**（十个分类是 0x2A；近白在灰色槽底上 16% 看不见，2026-11-10 用户看过后加深）；分隔线与行首角标沿用 037。
- 空格画淡图标（2026-11-10 用户看过后加）：**当前背包档位的通用扩展**的物品图，与分类行的幽灵图标同样的透明度。
- 悬停说明：`panel.mcfloatingbackpack.row.tooltip.general`。
- 黄点悬停：`panel.mcfloatingbackpack.hidden.line` 的类别名取 `CategoryNames.langKey("general")`（"通用"）。
