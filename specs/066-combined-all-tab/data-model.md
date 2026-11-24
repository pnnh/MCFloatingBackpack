# Data Model: 联合背包 · "全部"标签

全部是**运行时**类型，不存盘、不进存档；`DataRevision` 不变，联合背包物品仍无数据。

## 服务端

### `CombinedSessions.Session`（扩展 065 的 record → 小型可变类，访问器名保持）

| 字段 | 类型 | 说明 |
|---|---|---|
| `target` / `identity` / `combinedTier` / `lastSent` | 同 065 | 具体标签的目标与成员表摘要；"全部"模式下 `target` 保留为最近一次具体标签（切回去用） |
| `mode` | `TAB` \| `ALL` | 当前是具体标签还是"全部" |
| `all` | `AllState` 或 null | 仅 `mode == ALL` 时有 |

### `AllState`（服务端，会话级，内存）

| 字段 | 说明 |
|---|---|
| `epoch` | 递增整数，每次"全部"重同步 +1；请求必须带对 |
| `members` | `List<AllMember>`：本 epoch 的成员快照（顺序 = 065 成员表） |
| `cursor` | `ItemStack`：内存光标，不存盘 |

### `AllMember`

`ref`（`BackpackRef`）、`identity`（`UUID`，非空：进入"全部"时已 `acquire`）、`mainSize`（该成员主区域格数 = `inventory.size()`，含分类行）、`sent`（上次发出的主区域列表副本 + `RowView` + `ItemTags` + 上次看到的 `BACKPACK_CONTENTS` 引用，供 R1 的比较）。

### 纯逻辑（泛型、无 Minecraft 类型，可单测，放 `inventory/combined/`）

- `AllAddressing`：由成员主区域长度表 → 平铺号前缀和；`locate(flat)` → `(memberIndex, local)` 或无；`flatOf(member, local)`；`total()`。
- `AllRequestRules.check(...)`：判定一个"全部"请求是否放行（会话在 ALL、`epoch` 相等、各涉及成员的身份/档位/保护通过）→ `ALLOW` / `REFUSE_RESYNC`。
- `AllContentDiff.changed(last, now)`：R1 的两级比较（引用相同 → 未变；否则比主区域列表 + 行分类 + 标签）。
- `CursorFlush.plan(cursor, memberAcceptors, inventoryAcceptor)`：冲洗顺序（成员依次 → 物品栏 → 丢弃），返回每一步放了多少；守恒（总数不变）是可单测的不变量。
- `SlotAccess`：`size() / get(i) / set(i, stack) / canPlace(i, stack)`；`BackpackInventory` 适配与"全部"适配（经 `AllContext`）。

### 服务端适配（碰 Minecraft 类型）

- `AllContext`：一个请求里的"成员按需解码并缓存、记录被动过的成员、结束时只保存动过的"；`begin(player, epoch)` 校验并返回 `AllContext` 或 null（同时重发）；`inventory(memberIndex)`、`markTouched(memberIndex)`、`finish()`（逐个 `ref.saveInventory`，再推受影响成员的同步）。
- `CursorLoan`：`borrow(inventory, sessionCursor)` / `settle(inventory)`；`settle` 把成员背包里的光标取回会话并清空成员背包的 `cursor`。
- `AllSync`：给客户端发"全部"的成员同步包；`AllContentDiff` 的调用者（每秒钩子）。

## 客户端

### `ClientCombinedState`（065 已有，扩展）

新增：`allSelected`（当前是否"全部"）、`epoch`（客户端已完整收到的那一版）、`ready`（本 epoch 的所有成员同步包是否收齐；未齐 = 加载中）、`hoverMember`（悬停格所属成员，供标签高亮）、`sortPermutation`（整理的排列表，可空）、`searchActive`。

### `ClientCombinedView`（新）

`memberCount`、每个成员一份 `MemberView(slots, RowView, ItemTags)`、`AllGrid`（平铺号 ↔ 成员；按当前列数生成带 -1 的显示映射）。收齐一个 epoch 的全部成员后，把拼接后的平铺槽位 + 光标装进 `ClientBackpackView`（R4）；之后同 epoch 的成员同步包只替换对应切片。

### 纯逻辑（客户端包 `client/panel/combined/`，无 Minecraft 类型）

- `AllGrid.displayMap(memberSizes)`：各背包的格子首尾相连的显示映射（2026-11-24 改：中间没有空位，只有最后一行的尾部可能不满）。
- `AllSortPermutation.of(cells, order)`：把平铺格按 047 的排序规则排成置换（非空在前、空在后；稳定；仅依赖入参）。
- `AllSearchMap.of(query results, permutation)`：搜索结果在显示顺序上紧凑排列，输出 `mainMap`。

## 状态转移（会话）

```
（无会话）--U-->  ALL(epoch 1)  --点具体标签-->  TAB(target)   --点"全部"-->  ALL(epoch n+1)
ALL --成员表/大小/可见行变化--> ALL(epoch+1)  （先冲洗光标）
ALL --关闭/B 键/下线/停服/enabled=false--> 无会话（先冲洗光标）
```

## 语言键（新增，中英文各一份）

`gui.mcfloatingbackpack.combined.all`（标签"全部"）、`...all.empty`（没有成员）、`...all.from`（"来自 %s（%s）"）、`...all.search`（搜索框提示）、`...all.sort`（整理按钮提示：只改变显示）、`message.mcfloatingbackpack.combined.flushed`（光标物品已放回/落到脚下的提示）。
