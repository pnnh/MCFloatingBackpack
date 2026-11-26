# Contract: 网格界面

## 打开条件（客户端，全部满足才 `setScreen`）

1. 没有 overlay，当前没有 Screen，`FloatingPanelState` 不可见。
2. `BackpackRef.resolve` 非空，且来源不是主手。
3. 该物品的 `BACKPACK_CONTENTS.dataRevision` 不大于本版本（大于则聊天提示 `revision.protected`，不打开）。
4. 下方区至少有一个 `quick_access`。
5. `QuickCandidates` 至少一项。

不满足 2–5 时快捷栏上方对应的一句，20 tick 内同一句不重复。1 不满足则无提示。

## 视口

- 画在 `QuickAccessScreen.render`。不调用会压暗的背景。
- 只画候选物品。格子 40 像素，物品 32 像素（16 像素图标放大 2 倍，堆叠数一起放大），四周各留 4 像素。没有格子底。悬停时先填整格 `0x80FFFFFF`，再画物品。
- 提示用 `graphics.renderTooltip`，在物品之后画。
- 三角贴视口上下边的中点，尖端朝外；`scroll > 0` 画上，`scroll + 画出的行数 < totalRows` 画下。不参与命中。
- 滚轮：`scrollDeltaY > 0` 则滚动减 1（回到开头），与 `FloatingPanelEvents.scrollPanel` 相同。鼠标是否在视口内都滚动，并取消事件。
- 左键落在格内：发送 `quick_swap`，在结果包到来前不再发第二下（忽略连点）。落在视口外、或最后一行空出来的位置：不关闭，不发送。
- 松开快捷键，或 Esc：关闭，不发送。按住期间的重复按下不关闭。右键不关闭。
- `init`（含窗口大小变化）重算视口并夹住滚动。

## 关闭

`onClose` 只丢掉界面。不改背包、不发关闭包。玩家死亡或另一个界面打开时，原版会换掉 Screen，网格随之消失。
