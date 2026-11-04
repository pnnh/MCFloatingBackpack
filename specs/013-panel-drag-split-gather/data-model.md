# Data Model: 013-panel-drag-split-gather

不新增持久化数据，背包内容仍保存在物品实例的数据组件上（005/006）。这里只定义**请求**、**服务端光标访问**与**客户端拖动状态**。

## 手势模式 CursorMode

| 值 | 含义 | 起点条件 | `slots` 的含义 |
|---|---|---|---|
| `DISTRIBUTE_LEFT` | 左键拖动均分 | 按下时光标**非空**，左键 | 按顺序滑过的有效格子 |
| `DISTRIBUTE_RIGHT` | 右键拖动逐格放 1 个 | 按下时光标**非空**，右键 | 按顺序滑过的有效格子 |
| `GATHER_DRAG` | 拖动收集 | 按下时光标**为空**、该格有物品、左键，且这次按下拿起了该组 | 本帧新滑过的候选格子 |
| `GATHER_ALL` | 双击收集 | 250ms 内同一格同一键第二次点击，光标非空 | 只有被双击的那个格子 |

## CursorGestureRequest（客户端 → 服务端）

| 字段 | 规则 |
|---|---|
| mode | `CursorMode` 之一，未知值整包拒绝 |
| slots | 面板格子下标，**无重复**，每项 ∈ [0, 背包容量)，数量 ≤ 背包容量；`GATHER_ALL` 恰好 1 项 |
| screenOpen | 同 `BackpackClickPayload`：真 → 用菜单携带物，假 → 用面板光标（客户端自报） |

校验失败（未知模式、越界、重复、超限、背包不可用、光标为空）→ **整包拒绝，不改任何物品**。

## PanelCursor（服务端，读写光标栈的统一接口）

| 来源 | 条件 | 读 | 写 |
|---|---|---|---|
| 菜单携带物 | `screenOpen` 且 `player.containerMenu` 存在 | `menu.getCarried()` | `menu.setCarried(stack)`（之后 `broadcastChanges`） |
| 面板光标 | 其他情况 | `backpack.getCursor()` | `backpack.setCursor(stack)` |

## 分配规则（纯逻辑 `PanelCursorLogic`，客户端预览与服务端共用）

| 名称 | 定义（与原版一致） |
|---|---|
| `canQuickReplace(slot, cursor)` | 槽位为空 → 真；槽位是同种物品 → 真（只要 `槽位数量 ≤ 最大堆叠`，所以**满堆叠也算**）；否则假 |
| `isValidDragSlot(slot, cursor, selectedCount, mode)` | `canQuickReplace` 且接受该物品，且 `光标数量 > selectedCount`（均分与右键都要求） |
| `placeCount(mode, cursorCount, slotsCount)` | 左键 `floor(cursorCount ÷ slotsCount)`；右键 `1` |
| 结算每格 | `new = min(placeCount + 现有, min(最大堆叠, 格子上限))`；光标剩余 `= 原数量 − Σ(new − 现有)`；结算时对每格再检查一次条件并要求 `光标数量 ≥ slotsCount` |
| 单格退化 | 有效格子恰好 1 个 → 对该格做一次普通点击（等价于现有 `BackpackClickLogic.click`） |
| 收集一格 | 同种且光标未满 → 取 `min(格子数量, 最大堆叠 − 光标数量)`，剩余留在原格 |
| 收集全部 | 两遍遍历面板所有格子：第一遍只收未满的同种堆叠，第二遍收满的；直到光标满 |

## PanelDragGesture（客户端状态，不持久化）

```text
IDLE
  ├─ 左/右键按下面板格子，Ctrl/Shift 未按，且光标非空 → DISTRIBUTING(mode, selected=[起点若有效], visited={起点})；不发包
  └─ 左键按下面板格子，Ctrl/Shift 未按，光标为空且该格有物品 → 立即发"拿起"点击 + GATHERING(kind=按下时该格的物品, visited={起点})

DISTRIBUTING
  ├─ 每帧：线段采样；新滑到的格子若 isValidDragSlot(…, selected.size(), mode) 则加入 selected 并更新预览
  ├─ 松开按键：selected ≥ 2 → 发 DISTRIBUTE_*(selected)；selected == 1 → 对该格发一次普通点击；selected == 0 → 对松开位置的格子发一次普通点击
  └─ 取消（面板隐藏 / 界面切换 / 光标变空或变物品 / 按下另一个键 / Ctrl 或 Shift 按下）→ 丢弃，不发任何包

GATHERING
  ├─ 每帧：线段采样；新滑到的格子，客户端视图里与 kind 同种 → 本帧批量发 GATHER_DRAG(新格子们)
  └─ 松开左键 / 取消 → IDLE（已收进光标的保留）

另有：记录"上一次左键点击的格子与时间"；250ms 内同一格同一键的第二次点击，在松开时（光标非空且该格为空）发 GATHER_ALL(该格)，代替普通点击与分配。
```

| 字段 | 说明 |
|---|---|
| mode | DISTRIBUTING 时的左/右；GATHERING 时为收集 |
| selected | 已选的有效格子（有序） |
| visited | 已处理过的格子下标，保证同一格一次拖动只处理一次 |
| kind | GATHERING 的参照物品（按下时被拿起的那种） |
| lastX / lastY | 上一帧鼠标位置，用于线段采样 |
