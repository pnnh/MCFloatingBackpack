# Contract: 标签的存储与网络

## 1. 数据组件

`BackpackContents` 的存盘编码增加：

```text
{ "items": [...], "cursor": {...}?, "tools": {...}?, "tags": { "minecraft:oak_log": ["wood", "build"], ... }? }
```

- `tags` 可选，缺省为空；018 之前保存的背包读成空，其余字段不变。
- 读入时：丢弃不合法的标签（不满足 `TagRules.isValid`）与重复项，每个分类截到 10 个。
- 键是物品注册 id；该物品不再存在（模组被移除）时数据保留，不清理，不报错。
- `fit(capacity)`、升级配方、复制都保留 `tags`。

`BackpackContents.STREAM_CODEC`（物品栈同步）增加 `ItemTags`；字段数 3 → 4，不超过 `composite` 的 6 个上限。

## 2. 协议版本

`BackpackNetwork`：`versioned("7")` → `versioned("8")`。

| 包 | 方向 | 变化 |
|---|---|---|
| `BackpackTagPayload(op, slots, tag)` | 客户端 → 服务端 | **新增** |
| `BackpackTagsPayload(ItemTags)` | 服务端 → 客户端 | **新增** |
| `BackpackContents` 的物品栈同步编码 | 双向 | 增加 `ItemTags` |
| 其余全部包 | — | 不变 |

## 3. 请求 `BackpackTagPayload`

字段：`op`（字节：ADD=0、REMOVE=1）、`slots`（`int[]`，VarInt 列表，≤ 108，解码超限抛 `DecoderException`）、`tag`（UTF-8 字符串，≤ 16 字符，解码超长抛 `DecoderException`）。

服务端（`handleServer`），按顺序，任一步失败则**整个请求忽略、数据零变化**：

1. 请求者是 `ServerPlayer`；`BackpackRef.resolve` 得到背包且 `stillValid`，否则 `sendSession(false)`。
2. **功能区里有标签管理器**（`ItemStack.is(ModItems.TAG_MANAGER)`）；没有则忽略。
3. `TagRequest.validate(op, slots, tag, 主格子数)`：操作合法、下标非空无重复且在范围内、标签满足 `TagRules.isValid`。
4. 把 `slots` 解析成**非空主格子**的物品注册 id，去重；结果为空则忽略。
5. ADD：`TagBook.canAdd`——任何分类"尚无该标签且已有 10 个标签"则整个请求拒绝；否则给每个分类添加（已有则跳过）。一只背包的分类数超过 1024 也拒绝。
   REMOVE：从每个分类移除该标签（没有则跳过）。
6. `ref.saveInventory`；向请求者发送 `BackpackTagsPayload(整份 ItemTags)`。

客户端发出请求前也会做同样的检查（灰色加号等），但那只是体验，服务端才是权威。

## 4. 同步 `BackpackTagsPayload`

整份 `ItemTags`。发送时机：每次 `sendSession(open)` 之后；每次标签变化之后。客户端 `ClientBackpackView.setTags(...)`，面板在下一帧按新数据重新计算。`ItemTags.STREAM_CODEC`：分类数上限 1024，每个标签 ≤ 16 字符，每个分类 ≤ 10 个标签，超限解码抛 `DecoderException`。

## 5. 合法标签

`TagRules.isValid`：长度 1..16；每个字符在 `'!'..'~'` 内且不是大写字母（`'A'..'Z'`）。含大写、空格、非 ASCII 的标签一律不合法，**不会被自动转成小写**，服务端对整个请求拒绝。

## 6. 不变的服务端逻辑

011～017 的转入/转出、点击、工具区存放、放入规则（`ToolAreaRules` 恒为真）、选区相关请求**一行不改**；标签管理器作为普通物品在任何区域、任何转移途径下都照常移动。
