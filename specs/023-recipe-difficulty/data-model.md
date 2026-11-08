# Data Model: 背包配方难度调整

没有新的持久数据。只有配方的数据结构。

## 1. 升级配方 `mcfloatingbackpack:backpack_upgrade`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `template` | 成分（Ingredient） | **否**，缺省 = 空 | 模板格必须匹配它；缺省或为空表示"模板格必须为空"（006 行为）。**本次新增** |
| `base` | 成分 | 是 | 低一级的背包 |
| `addition` | 成分 | 是 | 升级材料 |
| `result` | 物品栈 | 是 | 高一级的背包 |

### 匹配规则（`matches`）

- 模板成分**非空**：输入的模板格物品必须匹配该成分（不能为空）。
- 模板成分**为空**：输入的模板格必须为空（006）。
- 基础格、材料格各自匹配 `base`、`addition`。
- 三项都满足才有产出；`assemble` 不变（把基础背包原样转成结果等级，内容按新容量 `fit`）。

### 菜单辅助（锻造台里能放什么）

| 方法 | 规则 |
|---|---|
| `isTemplateIngredient(stack)` | 模板成分非空 → `template.test(stack)`；为空 → `stack.isEmpty()`（006） |
| `isBaseIngredient(stack)` | `base.test(stack)`（不变） |
| `isAdditionIngredient(stack)` | `addition.test(stack)`（不变） |
| `isIncomplete()` | `base` 或 `addition` 没有任何物品时为真；**不**看模板 |

### 网络编码

`STREAM_CODEC`：依次 `template`、`base`、`addition`、`result`（先加了 `template`）。旧版本不能解码，所以协议版本升到 `"11"`。

## 2. 最终配方内容（对应 [contracts/recipe-format.md](./contracts/recipe-format.md)）

| 配方 id | 模板 | 基础 | 材料 | 结果 |
|---|---|---|---|---|
| `upgrade_iron_floating_backpack` | `minecraft:sentry_armor_trim_smithing_template` | 皮革背包 | `minecraft:iron_block` | 铁背包 |
| `upgrade_gold_floating_backpack` | `minecraft:dune_armor_trim_smithing_template` | 铁背包 | `minecraft:gold_block` | 金背包 |
| `upgrade_diamond_floating_backpack` | `minecraft:eye_armor_trim_smithing_template` | 金背包 | `minecraft:diamond_block` | 钻石背包 |
| `upgrade_netherite_floating_backpack` | `minecraft:netherite_upgrade_smithing_template` | 钻石背包 | `minecraft:netherite_block` | 下界合金背包 |

## 3. 皮革背包配方（工作台）

`minecraft:crafting_shaped`，`pattern = ["LSL", "LCL", "ILI"]`，`L` 皮革、`S` 线、`C` 箱子、`I` 铁锭。
