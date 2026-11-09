# Data Model: Curios 后背槽兼容

没有新增持久化数据（背包的栈存在 Curios 自己的槽里，随 Curios 的玩家数据保存）。

## 数据文件（随模组发布）

| 文件 | 内容 |
|---|---|
| `data/curios/tags/item/back.json` | `curios:back` 物品标签：5 个档位的浮动背包（`replace: false`） |
| `data/mcfloatingbackpack/curios/entities/player_back.json` | 玩家的槽位分配：`entities: [minecraft:player]`、`slots: [back]` |

## 类型

- `BackpackRef.Source.CURIOS(true)`：追加在末尾，序号 4；`isWorn() == true`。
- `CuriosWornSource`（`compat/curios/`）：`id = CURIOS.ordinal()`、`order = 10`；读写 `back` 槽（见 research D3）。
- `CuriosCompat`（`compat/curios/`）：`register()`，只在 Curios 已加载时被调用。

## 不变式

- 来源序号 0～3 不变；`CURIOS` 是 4；034 的 `ACCESSORIES` 将是 5。
- 来源顺序：胸甲（0）→ Curios（10）→（Accessories 20）。
- Curios 没装、或 `modLevels` 里 `curios` 不是 FULL：来源不生效，`WornBackpacks.first/resolve` 不会返回它的格子，`BackpackRef` 指向它时读到空栈。
