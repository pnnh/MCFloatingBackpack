# Data Model: 与原版物品栏/箱子互拖

## UnifiedCursor（运行时概念）

| 模式 | 光标来源 |
| --- | --- |
| 无容器 Screen | `BackpackInventory.panelCursor` |
| 有 AbstractContainerScreen | `AbstractContainerMenu.carried` |

## MergeOnOpen

| 条件 | 效果 |
| --- | --- |
| 开屏且 panelCursor 非空且 menu.carried 为空 | carried ← panelCursor；panelCursor ← empty |
| 两者皆非空 | 保持 menu.carried；panelCursor 仍保留直到有空 carried（下次点击再尝试合并） |

## HoverSlot（客户端）

| 字段 | 规则 |
| --- | --- |
| index | `PanelSlots.indexAt` 结果；-1 无高亮 |
