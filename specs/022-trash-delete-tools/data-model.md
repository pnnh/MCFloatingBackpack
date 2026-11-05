# Data Model: 垃圾桶的删除手段与保护

## 1. `TrashDeletePlan`（纯，`inventory/trash/TrashDeletePlan.java`）

| 成员 | 规则 |
|---|---|
| `THRESHOLD = 9` | 会销毁的组数 **超过**它才需要确认 |
| `destroyed(existing, incoming)` | `max(0, existing + incoming − TrashBin.MAX_GROUPS)`；`existing`、`incoming` 为负数按 0 |
| `needsConfirm(destroyed)` | `destroyed > THRESHOLD` |

| existing | incoming | destroyed | 确认？ |
|---|---|---|---|
| 0 | 5 | 0 | 否 |
| 4 | 3 | 0 | 否 |
| 9 | 1 | 1 | 否 |
| 9 | 9 | 9 | 否（恰好 9） |
| 9 | 10 | 10 | **是** |
| 0 | 20 | 11 | **是** |
| 5 | 12 | 8 | 否 |
| 5 | 14 | 10 | **是** |

## 2. `TrashDump`（纯泛型，`inventory/trash/TrashDump.java`）

```text
select(size, stackAt, isEmpty, hasTrashTag, isProtected, onlyDefaultData) -> int[]
```

返回升序下标：对 `[0, size)` 的每个格子，`stack = stackAt(i)`，`!isEmpty(stack) && hasTrashTag(stack) && !isProtected(stack) && onlyDefaultData(stack)`。四个谓词都是调用方给的，所以单测用假栈即可。

## 3. `StackExtraData`（纯，`inventory/trash/StackExtraData.java`）

`onlyDamage(Collection<String> patchedComponentIds)`：集合为空，或只含 `minecraft:damage` → 真；含其它任何 id → 假。常量 `DAMAGE = "minecraft:damage"`。适配器把 `stack.getComponentsPatch()` 里每个组件类型转成注册名字符串后调用。

## 4. `DeletableSlots`（纯，`inventory/trash/DeletableSlots.java`）

```text
allowed(boolean ownInventory, int containerSlot, boolean chestOwnSlot) -> boolean
```

- `ownInventory`（`slot.container` 是玩家物品栏）：仅当 `containerSlot ∈ [9, 35]`（主物品栏，不含快捷栏 0～8、装备 36～39、副手 40）。
- 否则：仅当 `chestOwnSlot`（`slot.container` 是这个箱子菜单自己的容器）。
- 其它一律 `false`。常量 `OWN_FIRST = 9`、`OWN_LAST = 35`。

## 5. `TrashSendRequest`（纯，`inventory/trash/TrashSendRequest.java`）

| 来源 | 字节 | `slots` | `containerId` |
|---|---|---|---|
| `DUMP` | 0 | 必须为空 | 忽略（客户端发 0） |
| `MAIN_SLOTS` | 1 | 1～`min(mainSize, 108)` 个，互不相同，∈ [0, mainSize) | 忽略 |
| `MENU_SLOT` | 2 | 恰好 1 个，∈ [0, 256) | ≥ 0 |

`Source.fromByte` 未知 → null；`validate(source, slots, containerId, mainSize)` 任一不满足返回 false。常量 `MAX_MENU_SLOT = 256`（与 `QuickTransferRequest.MAX_MENU_SLOTS` 同值）。

## 6. `TagProtection`（纯，`inventory/trash/TagProtection.java`）

`blocks(String tag, int protectedCategories) = "trash".equals(tag) && protectedCategories > 0`。常量 `TRASH = "trash"`（与 `PresetTags` 里的预设 id 相同，测试里断言它是预设）。

## 7. `TagPanelModel`（改，018）

增加：`protectedItems()`（选中分类里受保护的）、构造参数 `protectedAt`（`Predicate<K>` 或 `Set<K>`）；`canAdd()` 语义**不变**（只看工具存在与"已满"）；新方法 `blocks(tag)` = `TagProtection.blocks(tag, protectedItems().size())`。

## 8. 保护标签 `mcfloatingbackpack:never_trash`（数据，`item/ModTags.java`）

| 类别 | 条目 |
|---|---|
| 本模组 | 五档浮动背包、`tag_manager`、`trash_can` |
| 潜影盒 | `shulker_box` 与 16 色 |
| 原版贵重物 | `dragon_egg`、`nether_star`、`elytra`、`totem_of_undying`、`beacon`、`heart_of_the_sea`、`enchanted_golden_apple` |
| 收纳袋（可选条目） | `bundle` 与 16 色（`required: false`） |

## 9. `TrashPanelLayout`（改，021 的几何）

新增 `enum Icon { DUMP, DELETE }`、`ICON = 9`、`ICON_GAP = 2`、`ICON_PAD = 4`：

| 量 | 值 |
|---|---|
| `iconRect(DELETE)` | 右对齐：`x = 外框右 − ICON_PAD − ICON`，`y = 标题栏上 + (TITLE_H − ICON) / 2` |
| `iconRect(DUMP)` | 在 DELETE 左边隔 `ICON_GAP` |
| `iconAt(px, py)` | 命中某图标返回它，否则 null |
| `titleTextWidth()` | 标题栏里留给文字的宽度 = `DUMP` 图标左边 − 文字起点 − 2 |

021 已有的 `rect/titleRect/cellRect/cellAt/contains` 不变，测试保持通过。

## 10. `TrashConfirmState`（会话，`client/panel/trash/TrashConfirmState.java`）

| 字段 | 说明 |
|---|---|
| `pending` | 待确认操作：`DUMP` 或 `MAIN_SLOTS(int[] 快照)`；空表示没有弹窗 |
| `destroyed` | 弹窗里显示的 N |
| `screen` | 打开弹窗时的界面（`Object`，纯类里不依赖 Minecraft 类型） |

方法：`open(Pending, destroyed, screen)`、`cancel()`、`isPending()`、`shouldDiscard(boolean panelVisible, boolean hasTrashCan, Object currentScreen)`（任一不满足 → 真）。

## 11. `TrashConfirmLayout`（纯几何，`client/panel/trash/TrashConfirmLayout.java`）

`of(panelX, panelY, panelW, panelH, textLines, lineHeight)`：

| 量 | 值 |
|---|---|
| `mask()` | `(panelX, panelY, panelW, panelH)` |
| `dialog()` | 宽 `min(panelW − 12, 120)`、高 `8 + textLines*lineHeight + 6 + 14 + 8`，居中于面板 |
| `confirmButton()` / `cancelButton()` | 在弹窗底部并排，各宽 `(弹窗宽 − 24) / 2`、高 14，左右各 8 内边距、中间 8 间隔 |
| `hit(x, y)` | `CONFIRM` / `CANCEL` / `DIALOG`（弹窗内其它位置）/ `MASK`（蒙版内弹窗外）/ `OUTSIDE`（面板外） |

不变量（单测）：弹窗完全在蒙版内；两个按钮完全在弹窗内且互不相交；最小面板 `104 × 96` 下 `textLines ≤ 3` 时弹窗高 ≤ 面板高。

## 12. `DeleteKeyRules`（纯，`client/panel/DeleteKeyRules.java`）

```text
decide(Inputs) -> Target
Inputs { panelVisible, hasTrashCan, confirmPending, tagPanelOpen, tagEditing,
         ScreenKind kind (HOST, INVENTORY, CHEST, OTHER),
         selectionHasItem, hoverMainHasItem, hoverMenuAllowedWithItem }
Target { NONE, MAIN_SELECTION, MAIN_HOVER, MENU_SLOT }
```

| 条件（依次） | 结果 |
|---|---|
| `!panelVisible \|\| !hasTrashCan \|\| confirmPending \|\| tagPanelOpen \|\| tagEditing` | `NONE` |
| `selectionHasItem` | `MAIN_SELECTION` |
| `hoverMainHasItem` | `MAIN_HOVER` |
| `(kind == INVENTORY \|\| kind == CHEST) && hoverMenuAllowedWithItem` | `MENU_SLOT` |
| 其它 | `NONE` |

`kind == OTHER`（创造物品栏、潜影盒、漏斗、熔炉、模组容器……）只是不处理**菜单格子**；主区域（选区/悬停）在面板所在的任何界面上都照常可删。`NONE` 时调用方 **不取消事件**。

## 13. 请求/资源清单

- 新包 `BackpackTrashSendPayload`（见 contracts）；`BackpackTagPayload` 增加一条拒绝（见 contracts）。
- `data/mcfloatingbackpack/tags/item/never_trash.json`；
- 语言键（中英文各一份）：`key.mcfloatingbackpack.delete_to_trash`、`trash.mcfloatingbackpack.icon.dump`（名称）、`.dump.tooltip`、`.icon.delete`、`.delete.tooltip.disabled`、`.delete.tooltip.enabled`、`trash.mcfloatingbackpack.confirm.text`（含 `%s` = N）、`.confirm.yes`、`.confirm.no`、`message.mcfloatingbackpack.trash.nothing_to_dump`、`message.mcfloatingbackpack.trash.changed`、`tag.mcfloatingbackpack.protected`（悬停/聊天提示，含物品列表）；`trash.mcfloatingbackpack.title` 的英文改为 "Trash"。

## 14. 状态转移（一次倾倒）

```text
主区域 [圆石×64, 腐肉×12, 钻石×3(没标)]  --DUMP-->  垃圾桶 [腐肉, 圆石, ...原有]
                                                         主区域 [空, 空, 钻石]
需销毁 > 9 且无 confirmed → 拒绝，零变化
需销毁 > 9 且 confirmed   → 执行：后放入的留着，更早的（含刚放入又被挤掉的）销毁并记日志
```
