# Contract: 标签总览的网络请求与服务端流程

## 1. 数据组件与同步

**不变**。标签数据仍是 `BackpackContents` 里 018 的 `tags`（`ResourceLocation → 标签列表`，≤ 1024 个分类，每个分类 ≤ 10 个标签）；`BackpackContents` 的字段数不变（6 个）；服务端把整份标签同步给客户端的方式不变（`BackpackTagsPayload`：开会话时、每次变化后、忽略请求时的补发）。总览所需的数据客户端已经有。

## 2. 协议版本

`BackpackNetwork`：`versioned("12")` → `versioned("13")`（只在这一处）。

| 包 | 方向 | 变化 |
|---|---|---|
| `BackpackTagOverviewPayload(op, itemId, tag)` | 客户端 → 服务端 | **新增** |
| 其余全部包 | — | 不变 |

## 3. 请求 `BackpackTagOverviewPayload`

字段：`op`（字节：ADD=0、REMOVE=1、CLEAR=2）、`itemId`（`writeUtf(…, 256)`，物品注册 id 的字符串，如 `minecraft:oak_log`）、`tag`（`writeUtf(…, TagRules.MAX_LENGTH)`；`CLEAR` 时为空串）。解码不做业务判断，越界的字符串长度由 `writeUtf/readUtf` 限制（超限抛 `DecoderException`）。

服务端（`handleServer`），按顺序，任一步失败则**整个请求忽略、背包数据零变化**，并补发一次 `BackpackTagsPayload`：

1. 请求者是 `ServerPlayer`；`BackpackRef.resolve` 得到背包且 `stillValid`，否则 `sendSession(false)` 并结束。
2. **下方功能区里有标签管理器**（`BackpackInventory.hasTagManager()`，按整个列表）；没有则忽略。
3. `TagOverviewRequest.validate(op, itemId, tag)`；通过后 `ResourceLocation.tryParse(itemId)`，为空则忽略。
4. `registered = BuiltInRegistries.ITEM.containsKey(key)`；`isProtected = registered && BuiltInRegistries.ITEM.get(key).getDefaultInstance().is(ModTags.NEVER_TRASH)`（服务端自己算，不信客户端）。
5. `next = TagOverviewRules.apply(book, op, key, tag, registered, isProtected)`：`null` → 忽略；`next` 与原书相等 → 不保存；否则 `ItemTags.of(next)`（并核对分类数不超过 `ItemTags.MAX_CATEGORIES`），`backpack.setTags`，`ref.saveInventory`。
6. 发送 `BackpackTagsPayload(backpack.tags())`（成功与失败都发，失败时让客户端追上真相）。

客户端发出请求前也会做大部分检查（加号灰、"垃圾"变灰等），但那只是体验，服务端才是权威。

## 4. 服务端流程的不变量

- **只有三种写入**：给"已有标签的分类"加一个标签（幂等、≤ 10、保护规则）；从分类里删一个标签；清空一个分类。不会创建新分类、不会改别的分类、不会改变背包里的物品。
- 原有的 018 请求（按格子下标）、022 的倾倒与保护、024 的拾取与黑名单**一行不改**；它们读的是同一份标签数据，总览改完后立即生效（例如清空后 022 的"一键倾倒"不再倾倒它；024 的黑名单下一次拾取就按新标签判断）。
- 服务端日志：被拒绝的请求不记日志（避免被刷）；异常（保存失败）用错误级别记录。

## 5. 与 011～024 的关系

唯一的协议变化是新增一个客户端→服务端的包；`"12"` → `"13"`。其它服务端逻辑不变。
