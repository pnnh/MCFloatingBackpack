# Contract: 数据文件

## `data/curios/tags/item/back.json`

```json
{
  "replace": false,
  "values": [
    "mcfloatingbackpack:leather_floating_backpack",
    "mcfloatingbackpack:iron_floating_backpack",
    "mcfloatingbackpack:gold_floating_backpack",
    "mcfloatingbackpack:diamond_floating_backpack",
    "mcfloatingbackpack:netherite_floating_backpack"
  ]
}
```

要求：`replace` 必须是 `false`（不能抹掉别的模组往 `curios:back` 里加的东西）；5 个档位的 id 与 `BackpackTier.id()` 一致（测试检查）；没有多余项。Curios 没装时这个文件被忽略。

## `data/mcfloatingbackpack/curios/entities/player_back.json`

```json
{
  "entities": ["minecraft:player"],
  "slots": ["back"]
}
```

要求：文件名不以 `_` 开头（Curios 会跳过）；`entities` 含 `minecraft:player`；`slots` 含 `back`；**不带 `neoforge:conditions`**（无条件提供）。同一个槽 id 在别处也被分配时 Curios 只保留一个，不叠加。

## `neoforge.mods.toml`

```toml
[[dependencies.mcfloatingbackpack]]
modId = "curios"
type = "optional"
versionRange = "[9,)"
ordering = "NONE"
side = "BOTH"
```

（格式以文件里已有的 JEI 依赖为准。）
