# 契约：牛奶管、蜂蜜管的物品、配方、贴图、语言键

2 根管子，id `mcfloatingbackpack:milk_pipe`、`mcfloatingbackpack:honey_pipe`；与 049 的三根一起共五根。

## 配方（有序合成，3×3，`category: misc`，不加进度文件）

| 管子 | 形状 | 材料 | 合成后退回 |
|---|---|---|---|
| 牛奶管 | ` I ` / ` C ` / ` I `（`C` = 牛奶桶） | 2 铁锭 + 牛奶桶 | 空桶 |
| 蜂蜜管 | ` I ` / ` C ` / ` I `（`C` = 蜂蜜瓶） | 2 铁锭 + 蜂蜜瓶 | 玻璃瓶（**未验证**：原版 `craftRemainder`） |

## 物品

- 类 `PipeItem`（049 已有，`kind` 增加 MILK、HONEY）；`stacksTo(1)`、无耐久、无数据组件。
- 悬停说明：五根都加一句 `item.mcfloatingbackpack.pipe.discharge`（"潜行右键，用背包里的空桶装出一桶"；蜂蜜管用的是空玻璃瓶，所以说明按液体分两条键：`pipe.discharge`（桶）与 `pipe.discharge_bottle`（玻璃瓶））；牛奶管再加 `pipe.milk.tooltip`（"右键成年的牛或山羊，把牛奶挤进背包里的牛奶罐"）；蜂蜜管加 `pipe.honey.tooltip`（"右键装满蜂蜜的蜂巢，取蜜进背包里的蜂蜜罐"）。
- 创造栏：水、油、岩浆、牛奶、蜂蜜（`PipeKind` 的顺序），在罐子之后、类别扩展之前。
- 2 个 id 进 `never_trash.json`。

## 模型与贴图

- `models/item/<id>.json`：`minecraft:item/generated` + `layer0`。
- `textures/item/<id>.png`：16×16，由 `design/tank-textures/generate.py` 扩展生成（与 049 的管子同一个画法，管口颜色取牛奶白、蜂蜜琥珀）；占位图。

## 语言键（`en_us.json`、`zh_cn.json`）

| 键 | 英文 | 中文 |
|---|---|---|
| `item.mcfloatingbackpack.milk_pipe` / `honey_pipe` | Milk Pipe / Honey Pipe | 牛奶管 / 蜂蜜管 |
| `item.mcfloatingbackpack.pipe.discharge` | Sneak + right-click to fill an empty bucket from the tank | 潜行右键，用空桶从罐里装出一桶 |
| `item.mcfloatingbackpack.pipe.discharge_bottle` | Sneak + right-click to fill an empty glass bottle from the tank | 潜行右键，用空玻璃瓶从罐里装出一瓶 |
| `item.mcfloatingbackpack.pipe.milk.tooltip` | Right-click an adult cow or goat to milk it into the tank | 右键成年的牛或山羊，把奶挤进罐里 |
| `item.mcfloatingbackpack.pipe.honey.tooltip` | Right-click a full beehive to take its honey into the tank | 右键装满蜂蜜的蜂巢，把蜜取进罐里 |
| `message.mcfloatingbackpack.pipe.no_container` 等 | 见 discharge-rules.md | 见 discharge-rules.md |

水、油、岩浆管用 `pipe.discharge`；蜂蜜管用 `pipe.discharge_bottle`。

## 资源测试（`PipeResourcesTest`，扩展）要断言

- 5 个物品、5 个模型、5 张贴图（16×16）、5 个配方；牛奶管、蜂蜜管的形状与材料；每个结果 `count 1`；每根管子比对应的铁罐便宜；
- `never_trash.json` 含 5 个管子；
- 语言文件（中英）含 5 个物品名、新增的物品说明与 3 条新消息；
- 创造栏顺序：水、油、岩浆、牛奶、蜂蜜。
