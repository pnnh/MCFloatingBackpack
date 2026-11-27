# Contract: 配方

## 皮革档

沿用 `data/mcfloatingbackpack/recipe/quick_access.json`。工作台有序合成 `RIR` / `LCL` / `RIR`：红石粉、铁锭、皮革、指南针。产物 1 个 `quick_access`。

## 升级

四份都是 `minecraft:smithing_transform`。模板、加成与同档背包升级相同。底是低一档的快捷访问，不是背包。不使用 `mcfloatingbackpack:backpack_upgrade`。

| 升到 | 模板 | 底 | 加成 |
|---|---|---|---|
| 铁 | `minecraft:sentry_armor_trim_smithing_template` | `quick_access` | `minecraft:iron_block` |
| 金 | `minecraft:dune_armor_trim_smithing_template` | `iron_quick_access` | `minecraft:gold_block` |
| 钻石 | `minecraft:eye_armor_trim_smithing_template` | `gold_quick_access` | `minecraft:diamond_block` |
| 下界合金 | `minecraft:netherite_upgrade_smithing_template` | `diamond_quick_access` | `minecraft:netherite_block` |

每步消耗三样各一个，得到下一档 1 件。少一样、或跳级，没有产物。JEI 从这些数据配方列出，不改 JEI 插件。

背包升档不消耗、也不替换背包里的快捷访问。
