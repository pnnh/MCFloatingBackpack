# Data Model: 063

无持久化数据（`DataRevision` 6）。运行时：

`OpenContainerDelivery.Result<T>(T leftover, boolean conserved, int absorbed, RuntimeException error, boolean skipped, boolean swapped)`

- `conserved`：没有复制也没有多丢（总数相等，或减少量在取走范围内）。**含义较旧版放宽**；调用方只有 `BackpackQuickTransferPayload`。
- `absorbed`：被菜单格子之外吸收的数量；`conserved == false` 时为 0；相等时为 0。
- `absorbedOrInconsistent(before, after, taken)`：纯函数，返回吸收量，不一致返回 -1。
