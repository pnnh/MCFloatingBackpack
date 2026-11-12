# 契约：罐子的放入规则与消息

规则本体见 [data-model.md](../data-model.md) 的 `TankPlacement`；这里是"玩家看到什么、测试要覆盖什么"。

## 谁判定

- **服务端**：`BackpackClickPayload` 里，`ToolAreaRules.canPlace` 通过之后、改动之前，对 `held`（面板光标 / Alt 点击的主手 / 菜单携带栈）调用 `TankPlacement.check`；结果不是 `OK` 就 `sendSlotUpdate` 回滚并给动作栏提示（`serverPlayer.displayClientMessage(..., true)`），不改任何东西。
- **客户端**：`FloatingPanelInput.refusedByLimit` 用同一个函数预测（数据来自 `ClientBackpackView`：背包档次、底部区存储列表、`fluids()`），被拒时发聊天提示（沿用 021/024 的做法）并**不发请求**。客户端预测只为省一次必败的请求；以服务端为准。

## 判定表

`T` = 罐档次，`B` = 背包档次，档次先后 皮 < 铁 < 金 < 钻石 < 下界合金。

| 目标区 | 检查 | 结果 |
|---|---|---|
| 主区域 | 不检查 | OK |
| 左、右区 | `T ≤ B`？ | 否 → `TIER_TOO_HIGH`；是 → OK（不生效） |
| 底部区 | `T ≤ B`？ | 否 → `TIER_TOO_HIGH` |
| 底部区 | 放入后同液体的罐会多于一个（替换同格同液体的罐不算多） | `DUPLICATE_KIND` |
| 底部区 | 背包里该液体的现量 > 罐容量 | `STORE_EXCEEDS_CAPACITY` |
| 底部区 | 以上都通过 | OK |

## 档次 × 背包的预期（单元测试穷举）

| 背包 \ 罐 | 皮 | 铁 | 金 | 下界合金 |
|---|---|---|---|---|
| 皮 | ✓ | ✗ | ✗ | ✗ |
| 铁 | ✓ | ✓ | ✗ | ✗ |
| 金 | ✓ | ✓ | ✓ | ✗ |
| 钻石 | ✓ | ✓ | ✓ | ✗ |
| 下界合金 | ✓ | ✓ | ✓ | ✓ |

## 消息（动作栏，服务端；聊天，客户端预测）

| 结果 | 键 | 参数 |
|---|---|---|
| `TIER_TOO_HIGH` | `message.mcfloatingbackpack.tank.tier` | 罐需要的背包档次显示名 |
| `DUPLICATE_KIND` | `message.mcfloatingbackpack.tank.duplicate` | — |
| `STORE_EXCEEDS_CAPACITY` | `message.mcfloatingbackpack.tank.too_small` | — |

## 单元测试要覆盖

1. 上面的 5 × 4 表，对 19 种罐逐一断言（5 档背包 × 19 种 = 95 项；皮岩浆罐不存在）。
2. 互斥：空区放罐 OK；已有水罐，别的格放另一档水罐 → `DUPLICATE_KIND`；放到水罐所在格（替换）OK；放油罐 OK；五种液体各一个共存。
3. 容量：现量 10 000 mB 水，放皮水罐（8 000）→ `STORE_EXCEEDS_CAPACITY`，放铁水罐（16 000）OK；现量刚好等于容量 OK。
4. 左、右区：同液体放两个都 OK；高档罐放进低档背包 → `TIER_TOO_HIGH`；不查容量。
5. 非罐物品与空栈：恒 OK；背包档次为 null：只放皮罐。
6. 旧数据已有两个同液体的罐：再放第三个被拒，替换其中一个 OK（与 `ToolAreaRules` 同样宽容）。
7. 1000 组随机（区、格、已有列表、档次、现量）：结果为 `OK` 时，放入后底部区同液体最多一个（或不比放入前多），且档次不高于背包。
