# Data Model: 兜底透明宿主界面

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

本 feature 不新增任何持久化数据，也不改网络包。下面是客户端运行时的概念、纯逻辑的决策表和状态转移。

## 概念

| 名称 | 含义 | 所在 |
|---|---|---|
| 面板可见（`FloatingPanelState.isVisible()`） | 面板处于"打开"状态，对应服务端有一个背包会话。**不等于**此刻画在屏幕上：被聊天等界面隐藏时仍为可见 | 现有，不改 |
| 界面种类 `ScreenKind` | 当前 `Minecraft.screen` 的分类：`NONE`（null）、`HOST`（`PanelHostScreen`）、`CONTAINER`（`ContainerScreenCompat.isOpenContainerScreen` 为真）、`OTHER`（其余一切：聊天、暂停、选项、死亡、过场、其它模组的非容器界面） | 新增，纯逻辑用的枚举 |
| 托管界面 `hostsPanel(screen)` | `HOST` 或 `CONTAINER`；面板的渲染、鼠标输入、滚轮只在托管界面上挂钩 | `FloatingPanelEvents` |
| 待重放按键 `pendingReplay` | 宿主收到的、需要让出再重放的按键（键值、扫描码、是否已松开） | `PanelHostScreen` 的实例字段 |

## 策略表 `HostScreenPolicy.decide(panelVisible, kind, playerAlive)`

| panelVisible | playerAlive | kind | 动作 | 说明 |
|---|---|---|---|---|
| true | false | 任意 | `CLEAR_PANEL` | 死亡：清除可见状态（FR-015）。优先于其它行 |
| true | true | `NONE` | `OPEN_HOST` | 核心规则：可见且没有任何界面 → 显示宿主（FR-001） |
| true | true | `HOST` | `NONE` | 已经在了 |
| true | true | `CONTAINER` | `NONE` | 面板挂在容器界面上（FR-009） |
| true | true | `OTHER` | `NONE` | 宿主让位、面板隐藏、可见状态保留（FR-003/FR-012） |
| false | 任意 | `HOST` | `CLOSE_HOST` | 面板关了，宿主必须消失，不再介入（FR-005） |
| false | 任意 | `NONE` / `CONTAINER` / `OTHER` | `NONE` | 完全不介入（FR-005） |

不变量（单元测试钉住）：
1. `OPEN_HOST` 只在 `kind == NONE` 时出现——宿主从不覆盖任何其它界面（原则 I）。
2. `CLOSE_HOST` 只在 `kind == HOST` 时出现——从不关别人的界面。
3. `panelVisible == false` 时除 `CLOSE_HOST` 外没有任何动作。

## 按键分类表 `HostKeyRouter.classify`

输入：按键绑定到的**映射名**集合（只含冲突上下文不是 `GUI` 的映射，见 research D4）（`KeyMapping.getName()`），以及其中是否含面板自己的开关热键。

| 条件（自上而下，先到先得） | 结果 | 宿主的动作 |
|---|---|---|
| 集合含面板开关热键 | `TOGGLE` | 返回 false，交给 `onKey` |
| 集合为空 | `PASS` | 返回 false，不拦截 |
| 集合里每一个都在世界动作清单里 | `SWALLOW` | 返回 true，吞掉 |
| 其它 | `YIELD` | 返回 true，记入 `pendingReplay`，tick 里让出再重放 |

世界动作清单：`key.forward`、`key.left`、`key.back`、`key.right`、`key.jump`、`key.sneak`、`key.sprint`、`key.attack`、`key.use`、`key.pickItem`、`key.drop`、`key.swapOffhand`、`key.hotbar.1`～`key.hotbar.9`、`key.saveToolbarActivator`、`key.loadToolbarActivator`。

边界：一个键同时绑了世界动作和别的功能（用户在 Controls 里造成冲突）→ `YIELD`，重放会同时点击所有绑定它的映射，包括世界动作那个；这是用户自己制造的冲突，登记即可。

## 状态与转移

```text
            面板变为可见 / 其它界面关闭
   (无宿主) ───────────────────────────────▶ (宿主在场)
      ▲   ensure() 发现 可见 && kind==NONE      │  │
      │                                          │  │ 收到 YIELD 按键 → 记 pendingReplay
      │ 面板关闭（ESC / 热键 / 关闭按钮 /         │  ▼
      │ 会话关闭 / 死亡 / 登出）                  │ tick(): setScreen(null) 后把按键交给 keyboardHandler.keyPress
      │   ensure() 发现 !可见 && kind==HOST       │   同一 tick 的 handleKeybinds 消费点击
      └──────────────────────────────────────────┘   ├─ 打开了新界面 → 宿主让位（面板挂上去或隐藏）
                                                      └─ 没有 → Post(最低优先级) 清残留点击，ensure() 放回宿主
```

宿主的每一次出现、消失、被替换，都伴随一次 `ensure()`；`ensure()` 每帧至少调用一次（`RenderFrameEvent.Pre`），所以任何路径上的遗漏都最多存在一帧。

## 进行中的手势

| 手势 | 取消条件 | 现有机制 |
|---|---|---|
| 011 Ctrl 转出 | 界面变了（`minecraft.screen != activeScreen`） | `QuickTransferInput.stopIfInvalid`，不改 |
| 013 拖动均分/收集 | 同上 | `PanelDragInput.stopIfInvalid`，不改 |
| 014 选区拖选 / Ctrl 批量转出待定 | 同上 | `PanelSelectionInput.stopIfInvalid`，不改 |
| 面板拖动、面板缩放 | 界面切到非托管界面，或面板不可见 | **新增**：每 tick 检查 `!hostsPanel(screen)` 时调用 `state.endDrag()/endResize()` |

宿主 ↔ 容器界面互相切换时 `minecraft.screen` 必变，所以上面三类手势一律会被取消；取消时不发任何请求，物品总数不变。

## 光标物品

| 事件 | 面板光标上的物品 |
|---|---|
| 宿主 → 容器界面 | `onScreenOpening` 发 `MergePanelCursorPayload`，服务端按现有规则合并进携带物（不改） |
| 宿主 → 非容器界面 | 不动，仍在面板光标（背包数据里） |
| 面板关闭 | 按现有规则退还（`closePanel` → 服务端 `BackpackOpenRequestPayload.close()`，不改） |
| 容器界面 → 宿主 | 携带物按原版规则在容器关闭时回到玩家物品栏；面板光标为空（与 HUD 时代一致） |
