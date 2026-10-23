# Research: 与原版物品栏/箱子互拖

## 统一光标

- **Decision**: `screenOpen==true` 时，面板点击的光标读写 `player.containerMenu.getCarried()` / `setCarried`；面板 attachment 光标清空或仅作无屏模式使用。
- **Rationale**: 原版槽位点击已经操作 menu carried；共用后即可「拿起再点另一边」。
- **Alternatives considered**: 双光标手动桥接——易不同步。

## 打开容器时的残留面板光标

- **Decision**: 处理点击或收到开屏合并时：若 `menu.getCarried()` 为空且面板光标非空，则 `setCarried(panelCursor)` 并清空面板光标。
- **Rationale**: 避免物品卡在 M2 面板光标里无法放进物品栏。
- **Alternatives considered**: 丢弃面板光标——丢物不可接受。

## 客户端绘制

- **Decision**: 有 `AbstractContainerScreen` 时，浮动层光标绘制使用 `menu.getCarried()`；否则用 `ClientBackpackView.cursor`。悬停槽画 `0x80FFFFFF`。
- **Rationale**: 与 FR-003/004 一致。

## 同步

- **Decision**: 点击后 `menu.broadcastChanges()` + 既有 `BackpackSlotUpdatePayload`（cursor 字段在 screen 模式可传 EMPTY，客户端改读 menu）。
- **Rationale**: 容器槽位靠原版同步；背包槽靠我们的包。
