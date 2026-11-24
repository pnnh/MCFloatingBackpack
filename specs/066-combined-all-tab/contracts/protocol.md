# 契约：协议 23 → 24

网络协议号 `versioned("23")` → `versioned("24")`。**全部为运行时包，不涉及存档**；`DataRevision` 不变。

## 改动的包

### `CombinedSelectPayload`（C→S，065）

`op` 增加 `SELECT_ALL = 2`（`index`、`identity` 忽略）。`OPEN`（U 键）改为**打开"全部"**（065 是打开 B 的那只）。服务端对 `SELECT_ALL`：联合背包仍穿着且启用 → 若光标规则允许（见下）→ 进入/重发"全部"；否则回退并重发成员表。

光标规则（服务端权威，FR-016～018）：
- 目前在"全部"：菜单携带物非空 → 拒绝（动作栏提示，与 065 一致）；内存光标非空 → **先冲洗再切**。
- 目前在具体标签：菜单携带物非空，或该背包自己的光标（存在背包数据里）非空 → 拒绝（065 不变）。

### `CombinedMembersPayload`（S→C，065）

新增布尔 `all`：当前是否"全部"。`all == true` 时 `selected` 无意义（编码为 0）。客户端处理：`active && all` 时让面板可见并宿主界面就位（"全部"不发 `BackpackSessionPayload`，所以由这个包承担 065 里 session 包做的那几件事：重置搜索状态、标签总览视图、`HostScreenManager.ensure()`）。

### `BackpackClickPayload`、`BackpackCursorGesturePayload`、`BackpackQuickTransferPayload`（C→S）

各新增 `int epoch`（varint；**0 = 具体标签/旧行为**）。

- 会话在"全部"：`epoch` 必须等于会话当前 `epoch`，否则拒绝 + 重发"全部"；`slotIndex`/`slots` 解释为**平铺号**（`AllAddressing`），范围上限放宽为 `MAX_MEMBERS × MAX_MAIN_SLOTS`（16 × 423）。点击包的 `sourceOrdinal/invIndex`（声称引用）在"全部"里忽略。
- 会话在具体标签（或无联合会话）：`epoch` 必须为 0，否则拒绝（防止"全部"的请求被当成单背包槽号）；其余行为逐字节不变。
- `BackpackQuickTransferPayload` 的 `TO_BACKPACK` 方向 `slots` 是**玩家物品栏下标**（与 epoch 无关），但仍带 `epoch` 以便服务端判断"这是给全部的"。

其余带槽号的 C→S 包（`BackpackTakeFromContainerPayload`、`BackpackNetworkTakePayload`、`MergePanelCursorPayload`）没有背包槽号，不加 `epoch`；在"全部"会话里服务端按"成员顺序放入"规则处理。

单背包专属的包（`BackpackTrashPayload`、`BackpackTrashSendPayload`、`BackpackSortPayload`、`BackpackTagPayload`、`BackpackTagOverviewPayload`、`BackpackPickupBlacklistOpPayload`）：服务端在"全部"会话里**直接忽略**（不关闭面板）。

## 新增的包

### `CombinedMemberSyncPayload`（S→C）

| 字段 | 说明 |
|---|---|
| `epoch` | 会话当前 epoch |
| `memberCount` | 本 epoch 的成员数（≤16） |
| `memberIndex` | 本包是第几个成员（与 `CombinedMembersPayload` 的成员表下标一致） |
| `slots` | 该成员主区域全部格子（`ItemStack.OPTIONAL_STREAM_CODEC`，≤ `MAX_MAIN_SLOTS`） |
| `rows` | `RowView`（可见行分类、隐藏摘要） |
| `tags` | `ItemTags`（分类行灰格判定要用） |
| `cursor` | 内存光标（每个包都带，客户端取最新） |

- 每个成员**一个包**，不超过现有整包（没有工具区、垃圾桶），仍用 `withinLimit` 检查 `MAX_SYNC_BYTES`；超限（极端情况）则该成员不发并 WARN，会话 `endAndTell` 结束并写 `DiagLog`，不让客户端停在半成品画面上。
- 进入"全部"/epoch 变化：按成员顺序发满 `memberCount` 个；之后只对变了的成员发（同 epoch）。
- 客户端：同一 epoch 收齐 `memberCount` 个之后才 `ready`；收到更高 epoch 的包就丢弃旧的、重新累计。

### `CombinedCellUpdatePayload`（S→C）

`epoch`、`flat`（平铺号）、`stack`、`cursor`。点击/自动拾取后只改一格时用，比整成员包省带宽。客户端：`epoch` 不等于已就绪的 `epoch` → 丢弃（等下一次整成员包）。

## 服务端发送入口（R7）

`BackpackNetwork.sendSlotUpdate / sendFullSync / sendPickupUpdate`：
- 无联合会话：不变。
- 具体标签会话：`ref` 不等于目标 → 不发。
- "全部"会话：把 `(ref, 成员内格号)` 翻成平铺号发 `CombinedCellUpdatePayload`；整包同步翻成该成员的 `CombinedMemberSyncPayload`；`ref` 不是成员 → 不发。

## 兼容

不兼容旧协议（号变了，连接时 NeoForge 的版本协商就会拒绝）；不需要读旧数据。
