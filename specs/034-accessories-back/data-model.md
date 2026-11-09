# Data Model: Accessories 后背槽兼容

没有新增持久化数据（背包的栈存在 Accessories 自己的槽里，随它的玩家数据保存）。

## 数据文件（随模组发布）

| 文件 | 内容 |
|---|---|
| `data/accessories/tags/item/back.json` | `accessories:back` 物品标签：5 个档位的浮动背包（`replace: false`） |

## 类型

- `BackpackRef.Source.ACCESSORIES(true)`：追加在末尾，序号 5；`isWorn() == true`。
- `AccessoriesWornSource`（`compat/accessories/`）：`id = ACCESSORIES.ordinal()`、`order = 20`；读写 `back` 槽（research D3）。
- `AccessoriesCompat`（`compat/accessories/`）：`register()`，只在 Accessories 已加载时被调用（两端）。
- `AccessoriesClientCompat`（`compat/accessories/`，仅客户端）：`registerRenderers()`，给 5 个档位注册 `BackpackAccessoryRenderer`（每个档位一个新实例）。
- `BackpackAccessoryRenderer implements AccessoryRenderer`：`shouldRender` 与 `render` 每次读 `modLevels`（FULL：不画；否则转给 `DefaultAccessoryRenderer.INSTANCE`）。

## 不变式

- 来源序号 0～4 不变；`ACCESSORIES` 是 5。
- 来源顺序：胸甲（0）→ Curios（10）→ Accessories（20）。
- Accessories 没装、或 `modLevels` 里 `accessories` 不是 FULL：来源不生效，`WornBackpacks.first/resolve` 不会返回它的格子，`BackpackRef` 指向它时读到空栈。
- 渲染器不是 `EmptyRenderer`，所以不会被 `forceNullRenderReplacement` 换掉；每个物品一个新实例，所以不会被 Accessories 当成"共用"而包一层。
