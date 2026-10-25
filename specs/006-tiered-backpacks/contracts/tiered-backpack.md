# Contract: Tiered Backpack Sync & Upgrade (006)

沿用 005 的打开规则与 Overlay。本契约只写等级带来的变化。

## 物品

| ID | 容量 |
| --- | --- |
| `mcfloatingbackpack:leather_floating_backpack` | 36 |
| `mcfloatingbackpack:iron_floating_backpack` | 54 |
| `mcfloatingbackpack:gold_floating_backpack` | 72 |
| `mcfloatingbackpack:diamond_floating_backpack` | 90 |
| `mcfloatingbackpack:netherite_floating_backpack` | 108 |

任一 ID 都算「浮动背包」。扫描顺序与 005 相同。

## 协议版本

`3`。旧的固定 36 格包不再兼容。

### 会话 / 全量同步（服务器 → 客户端）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| open | bool | 会话包才有；false 时其余字段可空 |
| source | enum | 手 / 胸甲 / 物品栏 |
| invIndex | int | 物品栏下标，否则 -1 |
| slots | ItemStack 列表 | 长度 = 该背包容量，最大 108 |
| cursor | ItemStack | 面板光标 |

客户端按 `slots.length` 排版，不得假定 36。

### 点击（客户端 → 服务器）

槽位下标必须落在 **该物品容量** 内。容量不符、物品已不是浮动背包、或位置对不上：关闭会话，不改物品。

单格更新的下标同样按该容量校验。`-1` 只更新光标（与现有合并光标路径一致）。

## 升级配方

类型：`mcfloatingbackpack:backpack_upgrade`（锻造台可见）。

| 字段 | 说明 |
| --- | --- |
| base | 低一级背包物品 ID |
| addition | 一个物品 ID（铁块 / 金块 / 钻石 / 下界合金锭） |
| result | 高一级背包物品 ID |

匹配条件：

- 模板槽为空
- 基底物品 ID 等于 `base`
- 附加槽物品 ID 等于 `addition` 且数量 ≥ 1

结果堆叠：

- 新物品 ID 为 `result`
- 拷贝基底的背包组件，并把格子列表补到新容量
- 取出时基底与附加各消耗 1（原版锻造台行为）

不匹配时结果槽为空，不消耗。

## 外观资源

| 资源 | 要求 |
| --- | --- |
| `models/item/floating_backpack.json` | 共用模型：包身、翻盖、肩带、扣具。不是单个立方体，也不是 `item/generated` 平面。 |
| `models/item/<tier>_floating_backpack.json` | 父模型 + 该等级贴图 |
| `textures/item/<tier>_floating_backpack.png` | 宽和高都 ≥ 32 |
| 背部图层 | 使用该等级贴图，轮廓与物品模型一致 |

手持 display 使用同一 JSON，保证第一人称不是纸片或紫黑缺材质。
