# Data Model: 垃圾桶（本体）

## 1. `TrashContents`（存储，`inventory/trash/TrashContents.java`）

| 字段 | 类型 | 规则 |
|---|---|---|
| `groups` | `List<ItemStack>` | 0～9 个，**非空**栈；下标 0 = 最新（显示在第 1 格），依次变旧；不自动合并 |

- `EMPTY`：空列表。
- `CODEC`：`ItemStack.OPTIONAL_CODEC.listOf()`；读入时丢弃空栈、超过 9 个只保留前 9 个（最新的）。
- `STREAM_CODEC`：`ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9))`；解码超限抛 `DecoderException`。
- 值语义：持有的栈都是副本；`BackpackContents` 里的记录从不共享可变栈。
- 作为 `BackpackContents` 的第五个字段 `trash`（可选，缺省 `EMPTY`）；`normalize/fit/copy/empty` 都带上它。

## 2. `TrashBin`（纯逻辑，`inventory/trash/TrashBin.java`）

`MAX_GROUPS = 9`。所有方法都是静态、泛型 `T`，用 `QuickTransferLogic.StackOps<T>` 判断空/同种/数量。

| 方法 | 输入 | 输出 | 规则 |
|---|---|---|---|
| `push(ops, groups, stack)` | 当前列表、要丢的整个栈 | `Push(newGroups, destroyed)` | 栈为空 → 原样、`destroyed` 空；否则 `stack` 放第 1 位，旧的后移；总数 > 9 时最后一个移出并作为 `destroyed` |
| `take(groups, index)` | 列表、下标 | `Take(newGroups, taken)` | 下标越界或该组为空 → 原样、`taken` 空；否则移出该组，后面的前移 |
| `restore(ops, groups, indices, targets, get, set)` | 列表、下标集合、主区域目标顺序、读写主区域的函数 | `newGroups` | 下标去重升序；每组用 `QuickTransferLogic.moveInto`；全部放下 → 移除；放下一部分 → 原位保留剩余数量；一点放不下 → 原样；越界下标忽略 |

不变量（单测断言）：`push` 之后组数 ≤ 9；`push` 的 `destroyed` 非空当且仅当之前已满 9 组且 `stack` 非空；`take` 之后组数少 1 或不变；`restore` 前后"垃圾桶 + 主区域"里每种物品的总数不变；未被处理的组的相对顺序不变。

## 3. `TrashRequest`（纯校验，`inventory/trash/TrashRequest.java`）

| 动作 | 字节 | `slots` | 额外条件（服务端）|
|---|---|---|---|
| `ADD` | 0 | 必须为空 | 光标非空 |
| `TAKE` | 1 | 恰好 1 个，∈ [0, 9) | 光标为空，且该下标有组 |
| `RESTORE` | 2 | 1～9 个，互不相同，∈ [0, 9) | 至少一个下标有组 |

`validate(action, slots)`：动作已知、下标规则满足；任何一项不满足返回 false。`Action.fromByte` 未知值返回 null。常量 `GROUPS = 9`。

## 4. `ToolAreaRules`（纯，`inventory/ToolAreaRules.java`，改）

```text
canPlace(area, incoming, slotIndex, areaSlots, isTrashCan, isTagManager) -> boolean
```

- 限制类别：`RIGHT` → 垃圾桶；`BOTTOM` → 标签管理器；其余区域不限制。常量 `LIMIT_PER_AREA = 1`。
- `incoming` 不属于该区域的限制类别 → `true`。
- 否则 `before` = `areaSlots` 里属于类别的个数；`replaced` = `slotIndex` 在范围内且该格属于类别 ? 1 : 0；`after = before - replaced + 1`；返回 `after <= LIMIT_PER_AREA || after <= before`。

| 场景 | before | replaced | after | 结果 |
|---|---|---|---|---|
| 右侧区空，放垃圾桶 | 0 | 0 | 1 | 放行 |
| 右侧区已有 1 个，放到别的格子 | 1 | 0 | 2 | **拒绝** |
| 右侧区已有 1 个，放在它那一格（交换） | 1 | 1 | 1 | 放行 |
| 右侧区已有 1 个，放在装着别的物品的格子 | 1 | 0 | 2 | **拒绝** |
| 下方区遗留 2 个标签管理器，放第 3 个 | 2 | 0 | 3 | **拒绝** |
| 下方区遗留 2 个，放在其中一个那一格 | 2 | 1 | 2 | 放行（不增加） |
| 主区域/左区放垃圾桶 | — | — | — | 放行 |
| 右侧区放别的物品 | — | — | — | 放行 |

## 5. `BackpackInventory`（改）

- `trash()` / `setTrash(TrashContents)`，`fromContents/toContents/fit` 读写；
- `hasTrashCan()`：扫描 `RIGHT` 区**整个列表**，任一格 `is(ModItems.TRASH_CAN)`；
- `tags()` 等既有访问不变。

## 6. 客户端镜像（`ClientBackpackView`，改）

- `trash()`：当前同步到的 `TrashContents`；`setTrash(...)`；`clear()` 里复位为 `EMPTY`；
- `hasTrashCan()`：同上，读 `tools.get(RIGHT)` 的整个列表。

## 7. `TrashPanelState`（客户端会话，`client/panel/trash/TrashPanelState.java`）

| 字段 | 说明 |
|---|---|
| `open` | 是否展开 |

`toggle()`、`collapse()`、`reset()`（退出世界）。不持久化，不跨关闭浮动面板保存。

## 8. `TrashPanelLayout`（纯几何，`client/panel/trash/TrashPanelLayout.java`）

输入：浮动面板左上角 `(panelX, panelY)` 与宽度 `panelWidth`（GUI 像素）。

| 量 | 值 |
|---|---|
| 外框左上角 | `x = panelX + panelWidth + 1`，`y = panelY` |
| `TITLE_H` | 14 |
| `PAD` | 4 |
| `COLS × ROWS` | 3 × 3，格子边长 `PanelLayout.SLOT = 18` |
| 外框宽 / 高 | `2*PAD + 3*18 = 62` / `TITLE_H + 3*18 + PAD = 72` |
| `titleRect()` | `(x, y, 62, 14)` |
| `cellRect(i)` | `i ∈ [0,9)`，从左到右、从上到下；原点 `(x + PAD, y + TITLE_H)` |
| `cellAt(px, py)` | 命中返回 0..8，否则 -1 |
| `contains(px, py)` | 命中整个外框（含标题栏、内边距、格子之间） |

## 9. `TrashDropTracker`（纯，`client/panel/trash/TrashDropTracker.java`）

记录"每个鼠标键的按下是否发生在垃圾桶外框之外"。

| 方法 | 说明 |
|---|---|
| `press(button, onTrash)` | `onTrash=false` 时记下该键"按下在外"；`onTrash=true` 时清除该键的记录 |
| `release(button, onTrash)` | 返回 **是否构成拖放**：该键记录为"按下在外" **且** `onTrash` **且** `button` 是左键；总是清除该键记录 |
| `clear()` | 清空 |

## 10. 请求/同步包（见 contracts）

- `BackpackTrashPayload(byte action, int[] slots, boolean screenOpen)`（客户端→服务端）
- `BackpackTrashContentsPayload(TrashContents trash)`（服务端→客户端）

## 11. 资源清单

| 文件 | 内容 |
|---|---|
| `data/mcfloatingbackpack/recipe/trash_can.json` | 十字形有序配方，`[" I ", "ICI", " I "]` |
| `assets/mcfloatingbackpack/models/item/trash_can.json` | `item/generated`，`layer0 = mcfloatingbackpack:item/trash_can` |
| `assets/mcfloatingbackpack/textures/item/trash_can.png` | 16×16 |
| `lang/zh_cn.json`、`lang/en_us.json` | `item.mcfloatingbackpack.trash_can`（垃圾桶 / Trash Can）、`trash.mcfloatingbackpack.title`（垃圾桶 / Trash Can）、`message.mcfloatingbackpack.limit.trash_can`、`message.mcfloatingbackpack.limit.tag_manager`、`message.mcfloatingbackpack.trash.too_large` |

## 12. 状态转移（一次"丢入"）

```text
光标 [stack]  --ADD-->  垃圾桶 [stack, g0, g1, ... g7]   （g8 若存在则被销毁并记日志）
                         光标 []
垃圾桶 [g0, g1, g2]  --TAKE 1-->  光标 [g1]，垃圾桶 [g0, g2]
垃圾桶 [g0, g1, g2]  --RESTORE {0,2}-->  主区域尽量放下 g0、g2，放不下的留原位
```
