# 契约：排放、牛奶、蜂蜜与提示

规则本体见 [data-model.md](../data-model.md) 的 `PipePlanner`；这里是"玩家看到什么、测试要覆盖什么"。

## 入口

| 操作 | 入口 | 条件 |
|---|---|---|
| 潜行右键（任何目标） | `PipeItem.use()` | 主手是任意一根管子；潜行；服务端 → 排放 |
| 右键成年牛、哞菇、山羊 | `PipeItem.interactLivingEntity()` | 牛奶管；未潜行；目标是 `Cow`/`Goat` 且不是幼体 → 吸收 1000 mB 牛奶 |
| 右键蜂巢、蜂窝 | `PipeItem.useOn()` | 蜂蜜管；未潜行；`BeehiveBlock` 且 `honey_level >= 5` → 吸收 250 mB 蜂蜜 |
| 右键水、岩浆、石油源方块 | `PipeItem.use()` | 049，不变 |
| 左键方块 | `LeftClickBlock` | 049：水、油、岩浆管放置；牛奶管、蜂蜜管：取消事件 + 提示 `NO_BLOCK_FORM`，不放置 |

潜行时 `useOn`、`interactLivingEntity` 都返回 `pass`，所以牛奶管对着牛潜行右键是排放。

## 排放判定表（`PipePlanner.discharge`）

| 序 | 条件 | 提示 |
|---|---|---|
| 1 | 没找到背包 / 被保护 / 底部区没有该液体的罐 | `NO_BACKPACK` / `BACKPACK_PROTECTED` / `NO_TANK` |
| 2 | 液量 < 一份（油：锁定为空也算） | `CANNOT_PLACE_EMPTY` |
| 3 | 成品容器不存在（油的流体没有桶物品） | `NO_BUCKET_FOR_FLUID` |
| 4 | 背包主区域与物品栏里都没有空容器 | `NO_EMPTY_CONTAINER` |
| 5 | 通过 | 做；扣完为 0 则 `TANK_EMPTY_NOW` |

"做"= 取走一个空容器（先背包主区域，后物品栏）、发放成品（进物品栏，满了掉在脚边）、扣一份液量、保存、给打开着的面板同步（空容器取自背包主区域时整包同步）、`BUCKET_FILL` / `BOTTLE_FILL` 声音与 `FLUID_PICKUP` 游戏事件。

## 吸收（扩展）

| 管子 | 目标 | 一份 | 世界效果 |
|---|---|---|---|
| 牛奶管 | 成年牛、哞菇、山羊 | 1000 | 挤奶声（牛 `COW_MILK`；山羊 `GOAT_MILK`，尖叫山羊 `GOAT_SCREAMING_MILK`），牛不受影响 |
| 蜂蜜管 | `honey_level` = 5 的蜂巢/蜂窝 | 250 | `BOTTLE_FILL` 声、`FLUID_PICKUP` 事件、级别归零；无篝火烟熏：激怒附近蜜蜂并放出蜂巢里的蜜蜂；有烟熏：只归零 |

其余判定同 049（没有背包/罐、空位 < 一份 → `CANNOT_ABSORB_FULL`、满了 → `TANK_FULL_NOW`）；世界没改成功不加液量。小牛、小羊、`honey_level < 5`：什么也不发生、不提示。

## 提示（聊天栏，个人，带前缀；冷却 20 tick）

| 种类 | 键 | 英文 | 中文 | 参数 |
|---|---|---|---|---|
| `NO_EMPTY_CONTAINER`（新） | `message.mcfloatingbackpack.pipe.no_container` | No empty %s in your backpack or inventory | 背包和物品栏里没有空的%s | 容器名（桶/玻璃瓶） |
| `NO_BUCKET_FOR_FLUID`（新） | `…pipe.no_bucket` | There is no bucket for this oil (%s) | 这种油没有对应的桶（%s） | 流体 id |
| `NO_BLOCK_FORM`（新） | `…pipe.no_block_form` | %s cannot be poured out as a block; sneak and right-click to fill a container | %s倒不出方块，潜行右键装进容器 | 液体名 |
| `CANNOT_ABSORB_FULL`（改文字） | `…pipe.cannot_absorb` | The %s tank has no room for more | %s罐装不下了 | 液体名 |
| `CANNOT_PLACE_EMPTY`（改文字） | `…pipe.cannot_place` | The %s tank holds too little | %s罐里不够了 | 液体名 |

其余沿用 049。

## 单元测试要覆盖

1. 排放判定表逐行：有/没有背包、被保护、没有罐、液量 249/250/251 与 999/1000/1001（蜂蜜与其它）、成品不存在、没有空容器、刚好扣完。
2. 吸收的单位：蜂蜜空位 249/250/251；牛奶 999/1000/1001；刚好满。
3. 五种液体的单位、空容器种类、`hasBlockForm`；`place` 对牛奶、蜂蜜得 `NO_BLOCK_FORM`。
4. 原油：排放放的是锁定的流体（`plan.fluidId()`），没有锁定则不够一份。
5. `ContainerPicker`：主区域有就先用主区域；只有物品栏有就用物品栏；都没有 -1；位置的边界。
6. 同进退：`REFUSE` 时 `next == store`；1000 组随机排放序列下液量始终在 `[0, 容量]`、`成品数 × 一份 = 液量的减少`。
