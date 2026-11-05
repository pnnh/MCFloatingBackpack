# Research: 兜底透明宿主界面

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-05

读的是 NeoForge 21.1.251 的源码（`neoforge-21.1.251-sources.jar`）和本仓库源码。标"已查证"的是读源码得到的；标"未验证"的必须在客户端会话里确认（宪法 V）。

## Decision 1：宿主是一个普通的 `Screen` 子类

**Decision**: 新增 `PanelHostScreen extends Screen`：标题为空；`isPauseScreen()` 返回 false；`shouldCloseOnEsc()` 返回 false（ESC 自己处理）；覆盖 `renderBackground` 为空操作；不加任何子控件；不是 `AbstractContainerScreen`，没有菜单。

**Rationale（已查证）**: `Screen.render` 先调用 `renderBackground`，而 `renderBackground` 会画模糊背景和菜单背景（`renderBlurredBackground`、`renderMenuBackground`）——不覆盖它，世界画面会变暗/模糊，违反 FR-001。把 `isPauseScreen` 置 false，单人游戏不会暂停（FR-002）。面板仍由现有的 `ScreenEvent.Render.Post` 画，宿主不需要自己画任何东西。1.21.1 的 `Screen` 里没有 `isAllowedInPortal`（grep 无命中），`LocalPlayer` 也没有"站在传送门里关闭界面"的逻辑，所以宿主在传送门里不会被反复关闭。

**Alternatives considered**:
- 直接打开原版 `InventoryScreen`（用户已否决的方案 B）。
- 用 `Overlay` 或 `GuiLayer`：不是 `Screen`，接管不了键鼠，等于回到 HUD 模式。

## Decision 2：生命周期靠"策略检查"，不靠事件

**Decision**: 纯逻辑 `HostScreenPolicy.decide(panelVisible, kind, playerAlive)`，`kind ∈ {NONE, HOST, CONTAINER, OTHER}`，返回 `OPEN_HOST / CLOSE_HOST / CLEAR_PANEL / NONE`（表见 [data-model.md](./data-model.md)）。`HostScreenManager.ensure()` 执行它，调用时机：`RenderFrameEvent.Pre`、`ClientTickEvent.Post`（最低优先级）、服务端会话打开/关闭（`BackpackSessionPayload` 客户端处理）之后。

**Rationale（已查证）**: `Minecraft.setScreen` 只在 `guiScreen != null` 时才发 `ScreenEvent.Opening`；"回到游戏"（`setScreen(null)`）只发 `ScreenEvent.Closing(旧界面)`，并且在发出 `Closing` 之后才把 `this.screen` 改掉、并调用旧界面的 `removed()`，所以在 `Closing` 回调里再 `setScreen(宿主)` 会重入（旧界面 `removed()` 被调两次，外层随后又把 `screen` 覆盖成 null）。因此"恢复宿主"必须在事件之外做。
- 放在 `RenderFrameEvent.Pre`：它在 `Minecraft.runTick` 里**紧挨着 `GameRenderer.render` 之前**触发（`ClientHooks.fireRenderFramePre`），界面在输入回调里关闭（按 E 关物品栏就是在按键回调里 `setScreen(null)`）后，这一帧渲染之前宿主就已经回来，不会有"面板消失的一帧"。
- 放在 `ClientTickEvent.Post` 且最低优先级：让其它模组的 tick 处理（可能因按键打开界面）先跑完，再决定要不要放回宿主；保证 tick 内的最终状态正确。
- 放在会话打开/关闭之后：面板刚变为可见/不可见的那一刻立即生效，不等下一帧。

**Alternatives considered**:
- 在 `ScreenEvent.Closing` 里恢复：重入，已排除。
- 只在 tick 里检查：界面在输入回调里关闭时，渲染帧会先于 tick Post 出现一次无面板的画面，并且鼠标被抓取（`grabMouse`），产生可见闪动；加上 `RenderFrameEvent.Pre` 可以消除。

## Decision 3：让出发生在宿主的 `tick()` 里，同一 tick 内完成

**Decision**: 宿主收到"需要让出"的按键时，只记录下来（返回 true 表示已消费）；在宿主自己的 `tick()` 里才真正让出：**先** `minecraft.setScreen(null)`，**再**调用 `minecraft.keyboardHandler.keyPress(window, key, scanCode, GLFW_PRESS, modifiers)`（若按键在 tick 之前已松开，再补一次 `GLFW_RELEASE`）。

> **2026-11-05 修订（客户端验证后）**：最初的实现是在让出**之前**直接调 `KeyMapping.set/click`，客户端验证发现 E 可用、T（聊天）和精妙背包快捷键无效。原因有二：①此时界面还是宿主，`KeyMapping.click` 只会点击"冲突上下文当前活跃"的映射，只在游戏中生效的映射被忽略；②部分模组（精妙背包）靠 `InputEvent.Key` 读按键，手动 `set/click` 不会触发它。现在改成让出之后走游戏自己的 `keyPress`：它按"没有界面"的完整路径处理——映射在上下文活跃时被设置和点击、发出 `InputEvent.Key`、F1/F3 之类的特殊处理也一并走原路。

**Rationale（已查证）**: `Minecraft.tick()` 的顺序是：`if (screen != null) screen.tick()` → `if (overlay == null && screen == null) handleKeybinds()` → … → `ClientTickEvent.Post`。宿主在 `screen.tick()` 里把 `screen` 设为 null，紧接着同一个 tick 的 `handleKeybinds` 就会执行（条件 `screen == null` 现在成立），消费刚点击的按键映射：`keyInventory` 会 `setScreen(new InventoryScreen(player))`，`keyChat` 会打开聊天……。到 `Post` 时：若已经有新界面则策略什么都不做，否则把宿主放回来。**整个过程没有渲染帧**。
- 若在按键回调里直接让出，则按键回调与下一个 tick 之间可能插进渲染帧（高帧率时一个 tick 可以对应多帧），那一帧会是无界面状态。

**Alternatives considered**:
- 在按键回调里直接 `setScreen(null)` 并点击：可能产生可见的一帧闪动，已排除。
- 用访问转换器调用私有的 `handleKeybinds`：只能覆盖原版的按键，覆盖不了其它模组在自己 tick 事件里消费按键的做法，且需要新增 AT 条目，已排除。
- 只给 E 写特例：不满足 FR-007（其它模组的按键必须也能用）。

## Decision 4：按键分类与"让出再重放"的细节

**Decision**: `HostKeyRouter.classify(boundMappingNames)`，对宿主收到的每个按键（`InputConstants.getKey(key, scanCode)`）：

| 结果 | 条件 | 宿主的动作 |
|---|---|---|
| `TOGGLE` | 绑定里含面板自己的开关热键 | 返回 false，交给现有的 `InputEvent.Key` → `onKey` 处理（开关面板） |
| `PASS` | 没有任何按键映射绑定它（普通字母、方向键、F2/F11 等） | 返回 false，不拦截 |
| `SWALLOW` | 绑定的全部映射都在"世界动作"清单里 | 返回 true，吞掉 |
| `YIELD` | 其余（至少有一个不在清单里的映射） | 返回 true，记录，tick 里让出再重放 |

"世界动作"清单（映射名）：`key.forward`、`key.left`、`key.back`、`key.right`、`key.jump`、`key.sneak`、`key.sprint`、`key.attack`、`key.use`、`key.pickItem`、`key.drop`、`key.swapOffhand`、`key.hotbar.1`～`key.hotbar.9`、`key.saveToolbarActivator`、`key.loadToolbarActivator`。

重放的细节：
1. 查找绑定：遍历 `options.keyMappings`，用 `KeyMapping.matches(keysym, scancode)`，**但只保留"没有界面时它的冲突上下文是活跃的"的映射**（实现：检查期间临时把 `Minecraft.screen` 置空、问每个映射自己的 `getKeyConflictContext().isActive()`，检查完立即还原；上下文只读取它，不需要认识任何模组）。第一版只排除了原版的 `KeyConflictContext.GUI`，实测无效——JEI 用的是它自己的上下文（`JeiForgeKeyConflictContexts`，`isActive()` 以 `GUI.isActive()` 为前提）。原因：它们只在有界面时才起作用，没有东西可以"交还给游戏"，而且会误把世界动作键判成"要让出"（客户端验证发现 JEI 的"添加书签" `key.jei.bookmark` 默认也是 A，与 `key.left` 同键，A 被判成 `YIELD`，每按一次 A 宿主就让出、玩家向左走一步）。其余上下文（含 `IN_GAME`，此时界面是宿主，它们报告"不活跃"，所以不能用 `isActive` 过滤）都保留。
2. 让出之后用 `keyboardHandler.keyPress(PRESS)` 重放：游戏自己会在没有界面时 `KeyMapping.set(true)` + `click`（对所有绑定了该键、含修饰键匹配且冲突上下文活跃的映射各加一次点击计数），并发出 `InputEvent.Key`。
3. 若在 tick 之前这个键已经松开（宿主收到了 `keyReleased`），按下之后再补一次 `keyPress(RELEASE)`；否则松开事件到来时界面已是 null，由 `KeyboardHandler` 自己 `set(false)`，不会卡住。
4. **宿主在重放之后离开到下一个 tick 开头**（`awayUntilNextTick`）：重放发生在宿主自己的 `tick()` 里，即这个 tick 的 `ClientTickEvent.Pre` 之后；如果这时立刻把宿主放回来，在 tick 开头（`Pre`）才轮询按键的模组（实测 Traveler's Backpack 的"打开背包"就是 `ClientTickEvent.Pre` + `consumeClick`）要等到下一个 tick 才读到点击，那时宿主已在场，凡是带 `screen == null` 判断的都会放弃。所以重放之后宿主**不回来**，一直等到下一个 tick 的 `Pre`（最低优先级，排在所有模组的 `Pre` 处理之后）才把被重放映射的残留点击清掉并放回宿主。这样三类读法都能覆盖：`InputEvent.Key`（重放时由游戏发出）、同一 tick 里的 `handleKeybinds`/`Post`、下一个 tick 的 `Pre`。代价：没有打开界面的按键（如 F5）会让面板消失约一个 tick（约 50 毫秒）。其它模组若根本不通过按键映射读按键，仍然无效（已知限制）。
5. 修饰键、`charTyped`：让出之后同一个按键事件的字符事件到达时 `screen` 已是 null，`KeyboardHandler.charTyped` 在无界面时直接忽略，所以聊天键 `t` 不会把 "t" 打进聊天框。

**Rationale（已查证）**: 有界面时 `KeyboardHandler.keyPress` 的 `flag4 = (screen == null)` 为 false，既不 `KeyMapping.set(true)` 也不 `KeyMapping.click`；`handleKeybinds` 只在 `screen == null` 时运行。所以必须由宿主补上。把"吞掉"定义成世界动作清单、其余全部让出，使得 E、聊天、命令、成就、视角切换以及任何模组的"打开界面"按键走同一条路径，不需要逐个适配。

**面板的"桥接"绘制**：宿主在重放之后离开的那一个 tick（实测约 30～60 毫秒，约 2～4 帧）里没有任何界面，面板会闪一下。为此在这段窗口里，面板由 `RenderGuiEvent.Post` 只画不交互地画出来（宿主回来之前没有人能点它）。这是 HUD 时代那条渲染路径的一个很小的残留，只在 `HostScreenManager.isAway()` 为真时生效。

**未验证**: 其它模组如果不通过 `KeyMapping.consumeClick` 而是在 `InputEvent.Key` 里直接看键码（NeoForge 在界面没有消费该键时仍会发 `InputEvent.Key`，见 `KeyboardHandler.keyPress` 末尾的 `ClientHooks.onKeyInput`），宿主返回 true 会让这个事件不再发出。折中：`YIELD` 类按键由宿主消费；对确有此类需求的模组再具体处理，当前登记为已知限制。

**Alternatives considered**:
- 宿主对所有未知按键都返回 false（不消费），让 `InputEvent.Key` 照发：模组的 `InputEvent.Key` 处理者能看到按键，但原版 `handleKeybinds` 依然不会运行，E 打不开物品栏；不满足 FR-007。
- 只让出、不重放，让玩家再按一次：违反 SC-001（必须第一次按键就生效）。

## Decision 5：ESC 由宿主处理，删除两处旧的 ESC 逻辑

**Decision**: `PanelHostScreen.keyPressed(ESC)` → 调用 `FloatingPanelEvents.closePanel()` 并返回 true。删除 `onScreenOpening` 里"面板可见且新界面是暂停菜单则取消并关面板"，以及 `onKey` 里的 ESC 分支。

**Rationale（已查证）**: 屏幕接管后 ESC 走 `Screen.keyPressed`（`shouldCloseOnEsc` 为 true 时会 `onClose()`），原版的 `pauseGame` 只在 `screen == null` 时被 ESC 触发，所以宿主在场时 ESC 本来就不会开暂停菜单；容器界面上 ESC 由原版关闭容器，之后回到宿主，再按一次才进入宿主的 ESC 逻辑（FR-013）。旧的两处逻辑是为"无界面时 ESC 会开暂停菜单"而写的，现在不再可达。

## Decision 6：非容器界面把面板隐藏而不是关闭

**Decision**: 面板的渲染、鼠标输入、滚轮已经只在 `isOpenContainerScreen(screen)` 的界面上挂钩；改成 `hostsPanel(screen)`（宿主或容器界面）。其它界面（聊天、选项、暂停菜单、过场、死亡界面……）上这些钩子不触发，面板自然隐藏。去掉 `hideIfDisallowed` 里的 `closePanel()` 和 `onScreenOpening` 里的"非容器界面则关面板"。进行中的面板拖动/缩放（`state.isDragging/isResizing`）在界面切到非托管界面时由每 tick 的检查结束；011/013/014 的手势仍靠 `minecraft.screen != activeScreen` 取消。

**Rationale（已查证）**: 这是用户的决策 5（"面板隐藏但可见状态保留"）。`syncSelectionContext()` 以 `FloatingPanelState.isVisible()` 为清空条件，隐藏期间选区保留，符合 FR-012。会话（服务端）保持打开，由现有的会话校验在背包不可访问时关闭。

**未验证**: 隐藏期间背包被别的途径取走或服务器端校验失败时，客户端的 `visible` 状态如何收敛，沿用现有的 `BackpackSessionPayload(open=false)` 路径，需在 quickstart 里实测一次（把背包扔出再关聊天）。

## Decision 7：死亡与登出清除"可见"状态

**Decision**: 策略在 `playerAlive == false` 且面板可见时返回 `CLEAR_PANEL`，管理器据此调用 `closePanel()`；另外订阅 `ClientPlayerNetworkEvent.LoggingOut`，清除可见状态（`LoggingIn` 现有逻辑保留）。

**Rationale**: 现状里死亡/登出是靠"死亡界面不是允许的界面 → 关面板"间接实现的；改成隐藏语义后必须显式处理，否则重生后面板会带着旧会话回来。`isDeadOrDying()` 在死亡界面打开之前就为真，所以不会出现"死亡界面下面板被隐藏但一直留着"。

## Decision 8：鼠标位置不特别保持

**Decision**: 每次让出/恢复宿主，鼠标回到窗口中心，与原版开关界面一致。不额外保存和恢复鼠标位置。

**Rationale（已查证）**: `MouseHandler.releaseMouse()` 和 `grabMouse()` 都把 `xpos/ypos` 重置为窗口中心（`getScreenWidth()/2`）。用户在 spec 里已接受。若手感不佳，可以在后续用 `glfwSetCursorPos` 在恢复宿主后放回原位，但这需要更多验证，不在本阶段做。

## Decision 9：删除 HUD 驱动，不留兼容开关

**Decision**: 删除 `onRenderGui`、`onWorldMouseButton`、`onWorldMouseScroll`、`syncMouseToPanelVisibility`；`BackpackSessionPayload.handleClient` 与 `ClientBackpackActions.toggleFromUse` 里的 `syncMouseToPanelVisibility()` 改为 `HostScreenManager.ensure()`。`FloatingPanelInput.resetPressTracking()` 原本由 `onWorldMouseButton` 在面板不可见时调用，改为由 `ensure()` 在宿主被关闭时调用，保证没有残留的"已吞按下"记录。

**Rationale**: 宪法 III 与用户的明确目标——只保留一条输入路径、一条渲染路径。`QuickTransferInput`、`PanelDragInput`、`PanelSelectionInput` 的 `tick` 现在只会被 `ScreenEvent.Render.Post` 驱动，原先 `onRenderGui` 里的那份调用随之删除；文件里"onRenderGui returns early"的注释同步更新。

## Decision 10：`screenOpen` 与光标来源不变

**Decision**: `screenOpen = isOpenContainerScreen(minecraft.screen)` 不改。宿主不是容器界面，所以宿主在场时仍为 false，沿用面板自己的光标；容器界面在场时为 true，沿用菜单携带物。`FloatingPanelRenderer.cursorStack`、`PanelDragInput`、`QuickTransferInput.cursorIsEmpty` 里对 `minecraft.screen` 的判断对宿主与对 `null` 等价（都不是容器界面），不需要改。`Alt 换主手`在 `PanelDragInput` 里的让路条件是"不是容器界面且按着 Alt"，对宿主同样成立。

## Decision 11：创造模式沿用现状

**Decision**: 创造物品栏 `CreativeModeInventoryScreen` 是 `AbstractContainerScreen` 的子类，现有的 `isOpenContainerScreen` 对它返回 true；019 不改变它。宿主是自己的界面，所以创造模式下没有界面时面板照常可用（FR-018 第一部分）。创造物品栏界面上面板的携带物语义**未验证**，quickstart 里记录观察结果，不在本 feature 里修改。

## Decision 12：不改协议

**Decision**: 不新增、不修改任何网络包，协议版本保持 `"7"`；`MergePanelCursorPayload` 与 `BackpackOpenRequestPayload` 原样使用。

## 受影响的文档

`grep` 到的"HUD / 没有打开任何界面"描述分布在：003、007、011、012、013、014、016 的 spec/plan/quickstart 等，以及 `docs/MCFloatingBackpack.md`。实现完成后逐处把"无界面 HUD"改写成"宿主界面（没有容器界面时）"并注明 019；历史任务清单里已勾选的验证项不改。018 的 spec/plan 记录对 019 的依赖，其简化留到 019 验证后再做。

## 仍不确定、必须在客户端确认的点

1. 关闭其它界面后，宿主出现时鼠标回到中心是否可接受（Decision 8）。
2. 让出再重放对精妙背包"打开背包"快捷键是否有效（`runClientCompat`）；对不经 `consumeClick` 的模组按键无效，已登记。
3. JEI/EMI 在宿主界面上是否出现、008 的"JEI 安全区"是否仍然合理。
4. 宿主在场时窗口失焦：原版的"失焦自动暂停"只在没有界面时触发，宿主在场时不会弹暂停菜单。
5. 隐藏期间背包被取走时的收敛（Decision 6）。
6. 切换维度（`ReceivingLevelScreen`）期间与结束后面板的表现。
7. 创造物品栏界面上面板的携带物语义（Decision 11）。
8. F1、F3 及其组合键在宿主下不可用：已登记为已知限制。
