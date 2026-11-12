# 契约：管子的物品、配方、贴图、语言键

3 根管子，id `mcfloatingbackpack:<液体>_pipe`：`water_pipe`、`oil_pipe`、`lava_pipe`。

## 配方（有序合成，3×3，`category: misc`，不加进度文件）

`I` 铁锭、`C` 该液体的桶、`B` 空桶、`K` 煤炭块。

| 管子 | 形状 | 材料 | 合成后退回 |
|---|---|---|---|
| 水管 | ` I ` / ` C ` / ` I `（`C` = 水桶） | 2 铁锭 + 水桶 | 空桶 |
| 岩浆管 | ` I ` / ` C ` / ` I `（`C` = 岩浆桶） | 2 铁锭 + 岩浆桶 | 空桶 |
| 油管 | ` K ` / ` B ` / ` I ` | 1 铁锭 + 空桶 + 煤炭块 | — |

每根的材料成本低于对应的铁罐（7～8 铁锭 + 容器）。结果各 1 根。

## 物品

- 类 `PipeItem`；`stacksTo(1)`；无耐久、无数据组件；`appendHoverText`：`item.mcfloatingbackpack.pipe.tooltip`（右键源方块吸收、左键方块放一桶）与 `item.mcfloatingbackpack.pipe.needs_tank`（需要背包底部区的对应罐）；潜行右键的用途不在说明里提前承诺。
- 创造物品栏 `main` 标签页里，放在罐子之后、类别扩展之前，顺序水、油、岩浆。
- 3 个 id 进 `never_trash.json`。

## 模型与贴图

- `models/item/<id>.json`：`minecraft:item/generated` + `layer0 = mcfloatingbackpack:item/<id>`。
- `textures/item/<id>.png`：16×16，由 `design/tank-textures/generate.py`（扩展）生成：斜放的铁灰色管身，管口一端是液体色；**占位图**，用户之后同名替换。

## 语言键

| 键 | 英文 | 中文 |
|---|---|---|
| `item.mcfloatingbackpack.water_pipe` / `oil_pipe` / `lava_pipe` | Water Pipe / Oil Pipe / Lava Pipe | 水管 / 油管 / 岩浆管 |
| `item.mcfloatingbackpack.pipe.tooltip` | Right-click a source block to take it into the matching tank of your backpack; left-click a block to pour one bucket out | 右键源方块，吸进背包里对应的罐；左键方块，倒出一桶 |
| `item.mcfloatingbackpack.pipe.needs_tank` | The backpack you would open with B needs that tank in its bottom area | 按 B 会打开的那只背包，底部区里要有对应的罐 |
| `message.mcfloatingbackpack.pipe.*`（8 条） | 见 pipe-rules.md | 见 pipe-rules.md |

## 资源测试（`PipeResourcesTest`）要断言

- 3 个物品、3 个模型、3 张贴图（16×16）、3 个配方；形状与材料与上表一致；每个结果 `count 1`；
- `never_trash.json` 含 3 个管子；
- 语言文件（中英）含 3 个物品名、2 条物品说明、8 条消息；
- 创造栏里管子在罐子之后、类别扩展之前。
