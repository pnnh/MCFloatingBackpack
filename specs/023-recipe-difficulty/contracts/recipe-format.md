# Contract: 升级配方的数据格式

数据包作者可以直接写这种配方（路径 `data/<命名空间>/recipe/*.json`，`type` 为 `mcfloatingbackpack:backpack_upgrade`）：

```json
{
  "type": "mcfloatingbackpack:backpack_upgrade",
  "template": { "item": "minecraft:dune_armor_trim_smithing_template" },
  "base": { "item": "mcfloatingbackpack:iron_floating_backpack" },
  "addition": { "item": "minecraft:gold_block" },
  "result": { "id": "mcfloatingbackpack:gold_floating_backpack", "count": 1 }
}
```

- `template`：**可选**。省略或写成空成分，表示锻造台的模板格必须为空（006 的旧格式，行为不变）。写了就必须放入匹配的物品，升级时消耗 1 个。
- `base`、`addition`、`result`：与 006 相同。
- 升级时背包内的全部内容保留，不受 `template` 影响。

## 本模组自带的五条配方

见 [data-model.md](../data-model.md) 的表。实现完成后，`BackpackRecipesResourcesTest` 校验这五个文件的字段与值。

## 兼容性

- **旧数据包**（没有 `template`）：继续可用，行为与 006 一致。
- **客户端与服务端**：配方同步格式变了，协议版本 `"10"` → `"11"`，版本不一致的客户端连不上。
