# Contract: 背包贴图预算与图集过滤

本功能不暴露网络或代码接口；这里的"契约"是随模组打包的资源约定，以及维护者度量预算的约定。

## 资源文件

| 路径 | 要求 |
|---|---|
| `assets/minecraft/atlases/blocks.json` | 一个 `minecraft:filter` 源，`pattern.namespace` = `mcfloatingbackpack`，`pattern.path` = `item/.*_n`。**必须放在 `assets/minecraft/` 下**（原版图集定义的位置；各资源包同名文件的 `sources` 会叠加，我们的排在原版 `directory` 源之后才能生效） |
| `assets/mcfloatingbackpack/textures/item/<tier>_floating_backpack.png` | 512×512，RGB，不带透明通道 |
| `assets/mcfloatingbackpack/textures/item/<tier>_floating_backpack_n.png` | 512×512，RGBA；LabPBR 法线；文件名必须是基础色文件名加 `_n` |

图集过滤文件的内容：

```json
{
  "sources": [
    {
      "type": "minecraft:filter",
      "pattern": { "namespace": "mcfloatingbackpack", "path": "item/.*_n" }
    }
  ]
}
```

## 预算

| 项 | 值 |
|---|---|
| 共用方块图集（`minecraft:textures/atlas/blocks.png-atlas`）宽 | ≤ 2048 |
| 共用方块图集高 | ≤ 2048 |
| 当前方案的实测目标 | 2048×1024（mip 级别 4） |

## 度量方法

1. 干净客户端：只有原版 + 本模组。
2. `rm -f run/logs/latest.log`，起 `./gradlew runClient`，等图集创建完成（日志里出现 `blocks.png-atlas` 那一行，约 20~30 秒）后关闭。
3. 读 `Created: <W>x<H>x<N> minecraft:textures/atlas/blocks.png-atlas`。
4. W、H 都 ≤ 2048 则通过。
5. `design/demo/tools/check_atlas_budget.sh` 封装了以上步骤，超标时非零退出。

## 变更规则

- 新增或替换背包贴图后必须重新度量。
- 新增一个等级的贴图，或把任何一张贴图放大，都要先看度量结果是否仍在预算内，再合入。
- `_n` 命名必须保持 `<基础色名>_n.png`，否则既无法被图集过滤规则匹配，光影包也找不到。

## 未验证（实现后必须确认）

- 图集过滤是否影响光影包读取法线（推断不影响，因为光影包按资源名读取，未验证）。
- 512×512 在游戏里（UV 正确）是否达到"近看略软但不糊"（离线对比的数据支持，游戏内未验证）。
