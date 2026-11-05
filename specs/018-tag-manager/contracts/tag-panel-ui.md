# Contract: 标签面板、点击分派与键盘接管

## 1. 展开/收起

- 触发：命中**功能区（下区）**的一格，该格是标签管理器，按下左键，光标为空，没有 Shift/Ctrl → 切换 `TagPanelState.open`（纯客户端，不发包）并消费这次按下。右键、光标非空、带修饰键、以及其他区域里的标签管理器，全部按 016 的普通点击处理。
- 自动收起（同时结束编辑，保留 `text`/`scroll`）：功能区里没有标签管理器了（每 tick 检查）、浮动面板关闭、登出（登出时界面状态也复位）。
- 展开面板的范围：恰好是 `PanelLayout.rect(MAIN)`（主区域当前的 `cols × rows` 格），随缩放跟随。

## 2. 展开期间的输入分派

命中 `MAIN` 区域或在其上滚轮时（面板展开）：

| 事件 | 处理 |
|---|---|
| 左键点击标签块 | 该标签进入"待删除"：旁边显示 ✕；再点别处取消 |
| 左键点击 ✕ | 发 `BackpackTagPayload(REMOVE, 选区下标, tag)`，`pendingDelete` 清空 |
| 左键点击加号 | 可用：进入编辑（文本框获得焦点，见 §4）；灰色：无效 |
| 左键点击文本框 | 保持编辑，移动光标 |
| 左键点击下拉项 | 选中该项（`dropdownIndex`），不立即添加；回车才添加 |
| 左键点击其他空白 | 取消编辑与待删除 |
| 其他按键/右键 | 消费并忽略 |
| 滚轮 | 面板内容纵向滚动（每档一行） |

这些事件**不进入** 014 选区、011 Ctrl、013 拖动；`PanelSelection` 不变。工具区、标题栏、关闭按钮、缩放手柄、整体拖动不受影响。

## 3. 面板内容（`TagPanelLayout` / `TagPanelModel`）

- 状态行（第 1 行，18 像素）：`status == NO_ITEMS` 时显示提示"请先在主区域用 Shift 选择物品"；否则显示"选中 N 种物品"。
- 标签块：每个共同标签一块（预设显示本地化名称，自定义原样显示），按行流式换行；没有共同标签时显示"无"。
- 最后一个元素是**加号**：`canAdd` 为真时正常，否则灰色；悬停在灰色加号上弹出提示（`renderTooltip`）：列出 `fullItems` 里每种物品的名称，并说明"标签已满的物品不支持批量新增"；`NO_ITEMS` 时悬停提示"请先选择物品"。
- 编辑中：加号变成文本框（独占一行，宽 = 面板宽）；其下方叠一个下拉（≤ 5 项，来自 `TagMatcher.match(text, 预设)`）；回车：`dropdownIndex ≥ 0` 则添加该预设，否则 `text` 合法则添加 `text`，空或不合法则什么都不做；添加后文本框变回加号。
- 内容超出面板高度时用滚轮滚动；不画滚动条与箭头（面板内容少，滚轮即可）。
- 所有颜色、尺寸用 `fill` 绘制，常量集中在渲染类顶部。

## 4. 键盘焦点模型（FR-016）

```text
没有焦点（默认）            →  不拦截任何键盘事件；HUD 下 WASD 照常移动
        │ 点击可用的加号
        ▼
文本框有焦点（editing）     →  键盘字符与按键只作用于文本框
        │ ESC / 回车 / 点击文本框以外 / 面板收起 / 工具离开 / 浮动面板关闭
        ▼
没有焦点
```

- **容器界面打开时**：编辑期间，`ScreenEvent.KeyPressed.Pre`、`KeyReleased.Pre`、`CharacterTyped.Pre` 把事件交给 `TagTextInput`/面板处理并**取消**，原版收不到。
- **HUD（没有界面）时**：编辑开始时 `minecraft.setScreen(new TagInputScreen())`（透明、`isPauseScreen() == false`、不绘制任何内容、`shouldCloseOnEsc() == false`），之后键盘事件同样走上面的 `ScreenEvent` 钩子；原版因为有界面打开而释放全部按键状态，WASD 不再移动角色。编辑结束时 `setScreen(null)`。
- 编辑期间的按键：可见字符 → `type`；Backspace/Delete/左右/Home/End → 编辑；上下 → 移动 `dropdownIndex`；回车 → 添加；**ESC → 取消编辑**（不关浮动面板、不弹暂停菜单）；粘贴（`Screen.isPaste`）→ `paste`（逐字符过滤，大写被过滤）。
- `FloatingPanelEvents.onKey`（`InputEvent.Key`）在编辑中直接返回，避免热键或 ESC 同时触发面板切换/关闭。
- 没有焦点时，ESC 与热键的行为保持原样（ESC 关闭浮动面板）。

## 5. `TagInputScreen` 与现有逻辑的衔接

新增 `FloatingPanelEvents.hostsPanel(screen)` = 容器界面（`ContainerScreenCompat.isOpenContainerScreen`）**或** `TagInputScreen`，用于：

| 位置 | 用途 |
|---|---|
| `allowsPanel` / `hideIfDisallowed` | 输入屏幕打开时不关闭面板 |
| `onScreenOpening` | 输入屏幕打开时不因"非容器界面"而关闭面板 |
| `onScreenRender` | 输入屏幕打开时由 `ScreenEvent.Render.Post` 绘制面板（`RenderGuiEvent.Post` 在有界面时不画） |
| 鼠标按下/松开/拖动/滚轮钩子 | 输入屏幕打开时点击与滚轮仍然交给面板 |

不变：`screenOpen`/光标来源仍只认容器界面，所以输入屏幕打开时用面板自己的光标；输入屏幕关闭后 `syncMouseToPanelVisibility` 在下一 tick 重新释放鼠标。

## 6. 本地化

所有文字用语言键，`zh_cn` 与 `en_us` 各一份（物品名、10 个预设、"无"、状态行、提示、占位文字）。预设标签的**数据**是英文 id，只有**显示**随语言变化。
