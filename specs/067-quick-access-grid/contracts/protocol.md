# Contract: 协议

协议号 `versioned("24")` → `versioned("25")`（`BackpackNetwork` 的主 registrar）。版本检查 registrar 仍是 `versioned("1").optional()`。

两端协议不同时，玩家进不了服务器（现有行为）。

## C→S `mcfloatingbackpack:quick_swap`

| 字段 | 类型 | 含义 |
|---|---|---|
| `sourceOrdinal` | VAR_INT | `BackpackRef.Source` 的序号 |
| `invIndex` | VAR_INT | 来源内的格号；主手、副手为 -1 |
| `slot` | VAR_INT | 主区域格子下标 |

客户端在玩家左键点中一格时发送。格子下标来自当时的候选名单，不是视口上的第几格（滚动已经折算回去）。

## S→C `mcfloatingbackpack:quick_swap_result`

| 字段 | 类型 | 含义 |
|---|---|---|
| `ok` | BOOL | 这一次对调是否写入了 |

- `ok = true`：客户端关掉网格。
- `ok = false`：网格保持打开。句子由服务端在拒绝时直接发给该玩家（快捷栏上方），结果包不再带文案。

没有「打开网格」的服务端包。名单完全由客户端从物品组件算出；服务端只在交换时重算。

## 不改的包

`BackpackClickPayload` 的线格式不变。下方区限放只是处理器里多一次判断。标签的增删仍走 `BackpackTagPayload` / `BackpackTagOverviewPayload`。
