# Contract: 存储、网络编码与上限

## 1. 常量

| 名字 | 值 | 位置 | 用途 |
|---|---|---|---|
| `MAX_TIER_CAPACITY` | 108 | `BackpackInventory`（原 `MAX_SIZE`，改名） | 构造夹容量；`BackpackContents.items` 的编码与 `normalize/fit`；快照预览 |
| `ROW_CELLS` | 9 | `ExtensionCategories` | 一行格数 |
| `MAX_ROWS` | 35 | `ExtensionCategories` | 可见 + 隐藏的行总数上限 |
| `MAX_MAIN_SLOTS` | 423 = 108 + 35 × 9 | `BackpackInventory` | 会话/整份同步的格子列表编码；所有请求的格子数校验；点击的格子范围 |
| `MAX_TOOL_SLOTS` | 64（不变） | `ToolAreaContents` | 工具区最多 35 格，不受影响 |
| 同步大小阈值 | 900,000 字节 | `SnapshotRequestRules.MAX_PREVIEW_BYTES`（沿用） | 会话/整份同步的发送保护 |
| 协议版本 | 17（原 16） | `BackpackNetwork` | `registrar.versioned("17")` |

`BackpackInventory.MAX_SIZE` 删除；引用它的地方改成上面两个中合适的那个（编译器会指出全部）：

- 用 `MAX_TIER_CAPACITY`：`BackpackInventory` 构造、`BackpackContents` 的 `items` 编码与 `normalize/fit`。
- 用 `MAX_MAIN_SLOTS`：`BackpackSessionPayload`、`BackpackFullSyncPayload` 的格子列表；`BackpackClickPayload` 的格子范围；`QuickTransferRequest.MAX_BACKPACK_SLOTS`（被快速转移、光标手势、垃圾桶发送、`PanelCursorRequest`、`TrashSendRequest` 引用）；`TagRequest.MAX_SLOTS`。
- 不改：`SnapshotRequestRules.MAX_PREVIEW_SLOTS`（预览只取 `items`，即档位容量部分）。

## 2. 磁盘（`BackpackContents.CODEC`）

在原来 6 个字段后加：

```text
"extension_rows": {                      # optionalFieldOf，缺省 = 空；旧背包读成空
  "visible": [ { "category": "food", "cells": [ <ItemStack.OPTIONAL_CODEC> × 9 ] }, … ],
  "hidden":  [ …同上… ]
}
```

解码后 `sanitize`：每行补齐/截断到 9 格；类别字符串截到 16 字符；`visible + hidden` 超过 35 的部分截掉（先截 `hidden` 末尾，再截 `visible` 末尾）并记日志；`hidden` 里的空行删除。未知类别保留。

## 3. 网络

### 3.1 `BackpackContents.STREAM_CODEC`（手写）

```text
encode: items(list ≤108) | cursor | tools | tags | trash | pickupBlacklist | rows
rows  : visible(list ≤35 of Row) | hidden(list ≤35 of Row)
Row   : category(string ≤16) | 9 × ItemStack.OPTIONAL_STREAM_CODEC
```

前 6 段与原来一致。解码时对 `rows` 同样 `sanitize`。这条编码也是**数据组件同步**用的（`BACKPACK_CONTENTS` 是 `networkSynchronized`），所以隐藏行里的物品也会随背包栈到达客户端；客户端不显示它们。

### 3.2 会话与整份同步包（`BackpackSessionPayload`、`BackpackFullSyncPayload`）

在原有字段后加：

```text
baseCapacity       VAR_INT          档位容量
visibleCategories  list ≤35 string  可见行的类别（按行的顺序）
hidden             list ≤35 of (category string, nonEmptyCells VAR_INT)   隐藏行汇总，不含物品本身
```

格子列表 `slots` = 主区域含可见行的全部格子，上限 `MAX_MAIN_SLOTS`。

客户端 `ClientBackpackView.applyFull` 存下三项新字段，035 不用它们改布局；工具区容量回退改用 `baseCapacity`。

### 3.3 发送保护

`BackpackNetwork` 在发会话/整份同步之前，把要发的 payload 编码到一个临时缓冲区量字节数：

- ≤ 900,000：照发；
- \> 900,000：**不发**，记一条 `WARN`（带背包的来源与大小），改发 `BackpackSessionPayload.closed()`（面板按"没有背包"处理），客户端不会因超大包断线。

## 4. 已知限制（DELIVERY 里要如实写）

1. `BACKPACK_CONTENTS` 作为数据组件随每个背包栈同步，这条通路是原版的物品同步，上面的保护**管不到**。以前 108 格已有同样的暴露面，423 格把理论最坏值放大约 4 倍；只在玩家故意往行里塞大量带大数据的物品时才会碰到。035 不处理这条通路。
2. 最坏体积（423 格 + 工具区 + 光标，每格都是带大数据的物品）**没有实测**。
3. 带物品的可见行的线格式，035 里不会被走到（没有扩展件物品 ⇒ 可见行总被核对掉）；它在 036/037 的客户端会话里才有机会验证。035 验证的是：空可见行、带物品的**隐藏行**（`/give` 构造）的往返。

## 5. 兼容

- 旧背包：没有 `extension_rows` ⇒ 空；`items`、工具区、标签等不变。
- 旧协议（16）的客户端连不上新服务器，与以往升级一样；不做兼容。
- 快照：整个 `BackpackContents`（含 `rows`）经 `CODEC` 保存与恢复；预览只显示档位容量那部分。
- 升级配方：`BackpackUpgradeRecipe.assemble` 重建内容时要带上 `rows`。
