# Research: 五档快捷访问

读的是 067 已落地的代码（`QuickAccessItem`、`QuickViewport`、`ToolSingleRules`、`BackpackInventory.tier()`、`DataRevision.CURRENT = 7`）和背包升级配方。规格里没有待澄清项。

## R1 — 五个注册名，皮革沿用 `quick_access`

**Decision**: `QuickAccessItem` 增加 `BackpackTier` 字段，与 `TankItem` 一样。`ModItems` 里现在的单个 `QUICK_ACCESS` 改成按档的表，另给 `quickAccess(BackpackTier)`。皮革档的注册名仍是 `quick_access`。其余是 `iron_quick_access`、`gold_quick_access`、`diamond_quick_access`、`netherite_quick_access`。按键 `FloatingBackpackKeys.QUICK_ACCESS` 不改名。

**Rationale**: 旧存档的下方区里已经是 `mcfloatingbackpack:quick_access`。改注册名会对不上。显示名改成「皮革快捷访问」只动语言文件。

**Alternatives considered**: 五件都用新 id，再写一份旧 id 到皮革的迁移。背包内容是物品 id 列表，迁移要在读档时改写，比留着旧 id 多一条会丢物品的路径。

## R2 — 档位看背包容量，不看联合背包

**Decision**: 比较 `QuickAccessItem.tier()` 与 `BackpackInventory.tier()`。后者已是 `BackpackTier.byCapacity(baseCapacity)`。`hasQuickAccess()` 改成只在下方区找到**同档**的一件时返回真，并改名为看得到这个含义的方法（调用处一起改，不留一个仍认皮革档的旧方法）。

**Rationale**: 五档容量互不相同（36、54、72、90、108），容量就是档。不用在背包上再存一个「快捷访问档」。

**Alternatives considered**: 按联合背包的档来认。按 B 打开的是 `FloatingBackpackItem`（`BackpackRef.stillValid`、`capacityOf` 都只认它）。联合背包是另一件物品，开着它的界面时 Z 本来就无效。成员背包仍按自己的档。

## R3 — 错档与「最多一件」分开判，不改 `ToolSingleRules`

**Decision**: 新的纯逻辑 `QuickAccessPlace`。只在目标是下方区、且放入的是快捷访问时检查：

1. 放入的档 ≠ 背包的档 → `WRONG_TIER`。同一格替换也不行。
2. 否则按「下方区里任意一档的快捷访问」数件数，规则与 `ToolSingleRules` 相同：会变成多一件才 `DUPLICATE`；同一格换成同档的允许。已经躺在更高档背包里的皮革档算这一件，不能在别的格再加，直到拿走或就在这一格换成同档的。
3. 主区域、左区、右区，以及放入的不是快捷访问：一律通过。

两处现有的 `ToolSingleRules.check(..., QUICK_ACCESS)`（`FloatingPanelInput.refusedByLimit`、`BackpackClickPayload`）换成这一条。`ToolSingleRules` 的签名不动。

**Rationale**: `ToolSingleRules` 只回答「会不会多一件」，没有「档不对」这一种结果。液体罐已经是「先看档、再看件数」的另一条规则，快捷访问照着做，不把档位塞进那个通用函数。

**Alternatives considered**: 放错档仍放进去、只是不生效。规格写的是整次拒绝。旧档已经在里面的，不删除，只是不生效。

## R4 — 提示点名这一档

**Decision**:

| 键 | 何时 | 文案 |
|---|---|---|
| `message.mcfloatingbackpack.quick.need_tool` | 下方没有同档的，按 Z 或交换时 | 中文「把%s放进这只背包的下方功能区」，`%s` 是这一档物品的名字 |
| `message.mcfloatingbackpack.quick.wrong_tier` | 往下方放入错档 | 「下方功能区只能放同档的快捷访问」 |
| `message.mcfloatingbackpack.limit.quick_access` | 下方已经有一件又要再放 | 沿用 067，不改 |

`QuickNotices.actionBar` 与 `QuickSwapPayload.refuse` 增加能带一个物品名的重载。冷却仍按翻译键，不按拼好的句子，所以换语言或档名不会每档各刷一次。放入时的错档和重复：客户端用聊天（与罐子、067 的限放相同），服务端用快捷栏上方（与 `BackpackClickPayload` 现有拒绝相同）。

**Rationale**: 规格要求点名这一档。五个几乎相同的键不如一个 `%s`。

**Alternatives considered**: 五个 `need_tool.leather` 之类的键。多五份文案，说的是同一句话。

## R5 — 先选格子尺寸，再排 6 行

**Decision**: `PREFERRED_ROWS = 6`。两档常量：大档物品 32、格子 40；小档物品 24、格子 32。边距仍是 18。

选择：`(screenHeight - 36) / 40 >= 6`（即高度至少 276）用大档，否则用小档。然后再用选定的格子算放得下的列和行，行不超过 6，至少 1。内容行更少时网格缩到实际行数。`Fit` 增加 `item` 与 `slot`。`hit` 和 `QuickAccessScreen` 的绘制、悬停都用这两个字段，不再读类上的单一 `ITEM` / `SLOT`。三角仍是 3 像素，不随格子缩放。两档的缝都是 8 像素。

因此：

- 高 300、宽 400、54 组：大档，9×6，没有三角，滚轮不动。
- 高 270 或 240、宽约 430、54 组：小档，9×6，一次看完。9 列小档宽 324。
- 高不到 228：小档也放不下 6 行，可见行减少并滚动。
- 宽不到 324：列数少于 9，54 组超过 6 行，滚动。

内容不超过可见行时滚动夹在 0，滚轮不改变看到的内容。

**Rationale**: 大档 6 行高 276。1080p 全屏自动缩放约 270，小窗口约 240，都差一截。先缩小格子而不是先丢掉一行，一般窗口才不用滚。断点按「6 行大格子放不放得下」，不按这一次实际有几组，避免只有两组时突然变大。

**Alternatives considered**: 按 GUI 缩放倍数再乘一次。原版缩放已经体现在 `Screen` 的宽高里，再乘会重复。按物品数量把格子挤紧。规格明确不要。

## R6 — 锻造用原版 `smithing_transform`

**Decision**: `recipe/quick_access.json` 保持有序合成，产物仍是 `quick_access`。四份新配方类型为 `minecraft:smithing_transform`，材料与对应的背包升级相同：

| 文件 | 模板 | 底 | 加成 | 产物 |
|---|---|---|---|---|
| `upgrade_iron_quick_access.json` | `sentry_armor_trim_smithing_template` | `quick_access` | `iron_block` | `iron_quick_access` |
| `upgrade_gold_quick_access.json` | `dune_armor_trim_smithing_template` | `iron_quick_access` | `gold_block` | `gold_quick_access` |
| `upgrade_diamond_quick_access.json` | `eye_armor_trim_smithing_template` | `gold_quick_access` | `diamond_block` | `diamond_quick_access` |
| `upgrade_netherite_quick_access.json` | `netherite_upgrade_smithing_template` | `diamond_quick_access` | `netherite_block` | `netherite_quick_access` |

不登记新的配方类型，不使用 `mcfloatingbackpack:backpack_upgrade`。

**Rationale**: 那条自定义配方会把背包内容抄到产物上，并拒绝修订更新的背包。快捷访问没有内容。罐子的下界合金升级已经用原版 `smithing_transform`。JEI 会自己列出数据配方，不用改 JEI 插件。

**Alternatives considered**: 复用 `BackpackUpgradeRecipe`。它的 `matches` 会去读 `BACKPACK_CONTENTS`，快捷访问没有这个组件，行为靠「读出来是 null 就不保护」碰巧通过，以后改那条配方会连累本物品。

## R7 — 图标只改外框，不进方块图集

**Decision**: 五张都是 16×16 物品平面图，模型是 `item/generated`。构图沿用现在的 `quick_access.png`：奶油底、红色指针、深色针座。只改外框颜色，对照五档背包图标：皮革棕色、铁浅灰、金保持现在的金边、钻石青色、下界合金深色并带一点红。现在那张金边图改成皮革档；金档另存一张金边。手持、掉落、展示框走同一张物品贴图。

**Rationale**: 067 已确认物品平面图不进方块图集，不占用 010 的 2048 预算。五张换色不改变这一点。

**Alternatives considered**: 把背包图标缩小嵌进指针图。16×16 里再塞一个背包会看不清指针，五档也就分不开。

## R8 — 数据修订 8，协议保持 25

**Decision**: `DataRevision.CURRENT = 8`，注释加上「8 added the iron, gold, diamond and netherite quick access items」。`BackpackNetwork` 仍是 `versioned("25")`，不注册新包。`versioned("1").optional()` 不动。

必须改字面量 `CURRENT = 7` 或 `assertEquals(7, DataRevision.CURRENT)` 的测试：

- `inventory/revision/DataRevisionTest.java`（当前是 7；`isNewer(7)` 改为假不再成立，`isNewer(8)` 改为假，`isNewer(9)` 为真）
- `resources/QuickAccessWiringTest.java`
- `resources/LightWiringTest.java`
- `resources/MobileFurnaceWiringTest.java`
- `resources/JukeboxWiringTest.java`
- `resources/MobileCraftingWiringTest.java`
- `resources/PipeWiringTest.java`（两处）
- `resources/SnapshotCapacityWiringTest.java`
- `resources/TankWiringTest.java`

**Rationale**: 与 053–055、067 相同：新的、只会放在背包里的物品，旧版本保存时会丢掉。只改网格行数或显示名不升修订。没有新包，协议不升；两端协议检查仍是 25。

**Alternatives considered**: 不升修订，因为皮革档的 id 没变。另外四档是新 id，旧版本写回背包时会丢掉它们。这正是 7 从 6 升上来的理由。
