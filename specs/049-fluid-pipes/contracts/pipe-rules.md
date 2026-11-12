# 契约：管子的操作与提示

规则本体见 [data-model.md](../data-model.md) 的 `PipePlanner`；这里是"玩家看到什么、测试要覆盖什么"。

## 入口

| 操作 | 入口 | 条件 |
|---|---|---|
| 右键吸收 | `PipeItem.use()` | 主手是管子、未潜行；射线（`SOURCE_ONLY`）命中源方块；`mayInteract`、`mayUseItemAt` 通过 |
| 左键放置 | `LeftClickBlock`（服务端，`START`） | 主手是管子；节流（4 tick）通过；目标位置按桶的取法确定；`mayInteract`、`mayUseItemAt` 通过 |
| 潜行右键 | — | 本 feature 什么也不做（`use` 返回 `pass`），留给 050 |
| 左键（任何动作、两端） | `LeftClickBlock` | 主手是管子就取消：不挖方块、不开始挖掘、创造不瞬间破坏 |
| 对实体左键（攻击） | — | 不受影响 |

## 判定表（`PipePlanner`）

| 序 | 条件 | 吸收 | 放置 |
|---|---|---|---|
| 1 | 没找到背包 | 提示 `NO_BACKPACK` | 同左 |
| 1' | 背包被保护 | `BACKPACK_PROTECTED` | 同左 |
| 2 | 底部区没有该液体的罐 | `NO_TANK` | 同左 |
| 3 | 油罐已锁定为另一种流体 | `OIL_LOCKED` | — |
| 4 | 吸收：空位 < 1000 mB；放置：液量 < 1000 mB（含油的锁定为空） | `CANNOT_ABSORB_FULL` | `CANNOT_PLACE_EMPTY` |
| 5 | 以上都通过 | 做；之后 `amount == capacity` 则 `TANK_FULL_NOW` | 做；之后 `amount == 0` 则 `TANK_EMPTY_NOW` |

世界没改成功（桶的机制拒绝：不是源方块、目标放不下、保护区域等）：不扣、不加、不提示满/空；吸收时世界里不是管子的液体：什么也不发生、不提示。

## 提示（聊天栏，个人，带前缀；冷却 20 tick，键 = 玩家 + 种类）

| 种类 | 键 | 英文 | 中文 | 参数 |
|---|---|---|---|---|
| `TANK_FULL_NOW` | `message.mcfloatingbackpack.pipe.full_now` | The %s tank is full | %s罐满了 | 液体名 |
| `TANK_EMPTY_NOW` | `…pipe.empty_now` | The %s tank is empty | %s罐空了 | 液体名 |
| `CANNOT_ABSORB_FULL` | `…pipe.cannot_absorb` | The %s tank has no room for another bucket | %s罐装不下一桶了 | 液体名 |
| `CANNOT_PLACE_EMPTY` | `…pipe.cannot_place` | The %s tank holds less than a bucket | %s罐里不足一桶 | 液体名 |
| `NO_BACKPACK` | `…pipe.no_backpack` | No floating backpack found | 没有找到浮动背包 | — |
| `BACKPACK_PROTECTED` | `…pipe.backpack_protected` | This backpack was saved by a newer version and cannot be used yet | 这个背包是更新版本保存的，暂时不能用 | — |
| `NO_TANK` | `…pipe.no_tank` | Your backpack has no %s tank in its bottom area | 背包底部区里没有%s罐 | 液体名 |
| `OIL_LOCKED` | `…pipe.oil_locked` | The oil tank already holds another oil (%s); empty it first | 油罐里已经是另一种原油（%s），排空后才能装别的 | 流体 id |

液体名沿用 048 的 `fluid.mcfloatingbackpack.<kind>`（水、原油、岩浆）。

## 单元测试要覆盖

1. 判定表逐行：有/没有背包、被保护、有/没有罐、油锁定、空位 999/1000/1001、液量 999/1000/1001、刚好满、刚好空。
2. 档次无关：四档罐的结果只由容量不同。
3. 原油：第一次吸收锁定；同一种再吸 OK；另一种 `OIL_LOCKED`；排空后可吸另一种；放置放的是锁定的流体。
4. 同进退：`REFUSE` 时 `next == store`；`DO` 时 `next` 与 `FluidStoreOps` 的结果一致；1000 组随机序列下液量始终在 `[0, 容量]` 内（旧数据超量除外）、总量守恒（吸收 +1000 对应世界 −1 个源方块的记账由 MC 层保证）。
5. `PipeKind.classify`：水、岩浆、油、同时在多个标签里、都不在；`accepts`。
6. `Cooldown`：第一次通过；冷却内不通过且不更新；冷却过后通过；不同键互不影响；`forget`。
