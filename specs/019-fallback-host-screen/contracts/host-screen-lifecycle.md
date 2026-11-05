# Contract: 宿主界面的生命周期、按键转发与事件路由

**Feature**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md) | **Research**: [research.md](../research.md)

没有新的网络包（协议保持 `"7"`）。这里约定的是客户端内部的行为契约，实现和验收都以它为准。

## 1. 宿主界面（`PanelHostScreen`）

| 方法 | 约定 |
|---|---|
| 构造 | 标题为空；不添加任何子控件 |
| `isPauseScreen()` | false（单人游戏不暂停，FR-002） |
| `shouldCloseOnEsc()` | false；ESC 在 `keyPressed` 里自己处理 |
| `renderBackground(...)` | 空操作（不变暗、不模糊，FR-001）。`render` 不画任何东西，面板由 `ScreenEvent.Render.Post` 画 |
| `keyPressed(key, scan, mods)` | 先查 ESC → `closePanel()`，返回 true；否则 `HostKeyRouter.classify`：`TOGGLE`/`PASS` 返回 false；`SWALLOW` 返回 true；`YIELD` 记 `pendingReplay`、返回 true |
| `keyReleased(...)` | 若释放的是 `pendingReplay` 的键，记为已松开；`SWALLOW` 的键返回 true；其余返回 false |
| `charTyped(...)` | 返回 false（不消费） |
| 鼠标事件 | 不覆盖；面板的鼠标处理由 `ScreenEvent.MouseButton*`/`MouseDragged`/`MouseScrolled` 的现有钩子完成。面板之外的点击落在宿主上什么都不做（世界交互失效是预期，FR-008） |
| `tick()` | ①面板不可见则关闭自己；②若有 `pendingReplay`：先 `setScreen(null)`，再 `minecraft.keyboardHandler.keyPress(PRESS)`（已松开则再补 `RELEASE`），清空 `pendingReplay` |
| `removed()` | 清空 `pendingReplay`；通知 `FloatingPanelInput.resetPressTracking()` |

## 2. 管理器（`HostScreenManager`）

| 方法 | 约定 |
|---|---|
| `ensure()` | 取当前 `screen` 的 `ScreenKind` 和玩家存活状态，调用 `HostScreenPolicy.decide`，执行动作：`OPEN_HOST` → `setScreen(new PanelHostScreen())`；`CLOSE_HOST` → `setScreen(null)`；`CLEAR_PANEL` → `FloatingPanelEvents.closePanel()` |
| 调用时机 | `RenderFrameEvent.Pre`（每帧）；`ClientTickEvent.Post`（最低优先级，先清被重放按键的残留点击再 `ensure`）；`BackpackSessionPayload` 客户端处理打开/关闭之后；`closePanel()` 之后 |
| 唯一性 | `setScreen(PanelHostScreen)` 只在 `HostScreenManager` 里出现；`setScreen(null)` 只在 `HostScreenManager` 和宿主的让出里出现（评审时 `grep` 这两处） |

## 3. 事件路由（`FloatingPanelEvents`）

| 事件 | 改动前 | 改动后 |
|---|---|---|
| `ScreenEvent.Render.Post` | 容器界面上画面板并驱动 011/013/014 的每帧逻辑 | 同，条件改为 `hostsPanel(screen)`（宿主或容器界面） |
| `ScreenEvent.MouseButtonPressed/Released.Pre`、`MouseDragged.Pre`、`MouseScrolled.Pre` | 仅容器界面 | 条件改为 `hostsPanel(screen)` |
| `RenderGuiEvent.Post` | 无界面时画面板并处理输入 | 输入部分**删除**；仅保留"宿主让出的那一个 tick 里只画不交互"的桥接绘制（`HostScreenManager.isAway()` 为真时），避免面板闪一下 |
| `InputEvent.MouseButton.Pre`、`InputEvent.MouseScrollingEvent` | 无界面时处理面板的点击与滚轮 | **删除** |
| `ScreenEvent.Opening` | 容器界面 → 发 `MergePanelCursorPayload`；可见时暂停菜单被取消并关面板；可见时其它非容器界面 → 关面板 | 只保留容器界面 → 发 `MergePanelCursorPayload`；其余两条**删除** |
| `InputEvent.Key`（`onKey`） | ESC 关面板；开关热键开/关面板 | 删除 ESC 分支；开关热键逻辑不变（`allowsPanel` 判定加入宿主） |
| `ClientTickEvent.Post`（`onClientTick`） | 含 `hideIfDisallowed`（关面板）与 `syncMouseToPanelVisibility` | 删除这两处；新增：非托管界面时结束面板拖动/缩放；`hostsPanel` 之外不更新拖动 |
| `ClientPlayerNetworkEvent.LoggingOut` | 无 | **新增**：清除可见状态与会话视图 |
| `RenderFrameEvent.Pre` | 无 | **新增**：`HostScreenManager.ensure()` |

`allowsPanel(screen)` = `screen == null || 宿主 || 容器界面`（用于"能否用热键打开面板"）；`hostsPanel(screen)` = `宿主 || 容器界面`（用于渲染与鼠标输入）。

## 4. 行为保证（验收依据）

1. **让位**：任何一帧里，`minecraft.screen` 不会是"宿主盖在别的界面之上"——宿主只在 `screen == null` 时被打开（策略不变量 1）。
2. **恢复**：容器/非容器界面关闭后，下一帧渲染之前宿主已回来（`RenderFrameEvent.Pre`）；验收标准放宽到 0.3 秒（SC-002）。
3. **按键**：`YIELD` 类按键在**同一 tick** 内被原版或模组消费；按 E 一次就打开物品栏（SC-001）。
4. **不留残留**：重放之后宿主离开到**下一个 tick 的 `Pre`（最低优先级）**；此时被重放按键的未消费点击被清除，宿主回来。这样在 tick 开头才读按键、或只在无界面时才读按键的模组都能读到点击，同时不会在宿主关闭后"幽灵触发"。
5. **不介入**：面板不可见时，宿主不存在，`HostKeyRouter` 不会被调用，`ensure()` 只做一次 O(1) 判断。
6. **只有一条输入/渲染路径**：`grep` 确认 `RenderGuiEvent`、`InputEvent.MouseButton`、`isMouseGrabbed/grabMouse/releaseMouse` 不再出现在 `src/main`（SC 之外的静态检查，写入 quickstart）。
