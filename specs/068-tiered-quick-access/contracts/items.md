# Contract: 五档物品与放入

## 生效

打开网格和接受交换之前，下方功能区里要有一件与 `BackpackInventory.tier()` 相同的快捷访问。没有时不打开、不交换，快捷栏上方是 `quick.need_tool`，`%s` 为这一档的物品名。修订比本版本新时仍走 067 的 `revision.protected`，不打开确认窗口。

其余打开条件（游戏画面、不是主手、至少一组候选）与 067 的 [grid.md](../../067-quick-access-grid/contracts/grid.md) 相同。

## 放入

只在目标区域是下方、且手上是快捷访问时额外检查。顺序固定：

1. 手上这件的档与背包的档不同 → 整次拒绝，`quick.wrong_tier`。同一格替换也拒绝。
2. 档相同，但放入后下方区的快捷访问（任意档）会比原来多一件 → 整次拒绝，`limit.quick_access`。同一格把已有的一件换成同档的，允许。
3. 其它区域，或手上不是快捷访问 → 不因本规则拒绝。

客户端 `FloatingPanelInput` 与服务端 `BackpackClickPayload` 用同一条纯逻辑。客户端拒绝显示在聊天；服务端拒绝显示在快捷栏上方。拒绝时背包内容不变。

旧存档里更高档背包下方已有的皮革档：留着，不算生效，并占住那一个名额。

## 不在本契约里

交换落点、嵌套背包、整组不合并，仍是 067 的 [swap.md](../../067-quick-access-grid/contracts/swap.md)。其中「下方区有快捷访问」改成「下方区有同档快捷访问」，失败时的 `quick.need_tool` 带上档名。
