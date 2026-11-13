# 契约：右区各一个、播放列表、播放状态机、页

## 右区各一个（`ToolSingleRules.check`，纯逻辑）

`check(targetArea, area, incoming, slotIndex, areaSlots, isKind) → OK | DUPLICATE`：`area != targetArea` 或 `incoming` 不是这种工具 → `OK`；否则 `before` = 区里这种工具的个数，`replacesOne` = 目标格里现在就是这种工具，`after = before − (replacesOne ? 1 : 0) + 1`，`after > 1 && after > before` → `DUPLICATE`，否则 `OK`（旧背包已有多个时保留、不能再加）。移动唱片机：`targetArea = RIGHT`，提示 `message.mcfloatingbackpack.limit.mobile_jukebox`。与垃圾桶的限制互相独立。

## 播放列表

见 data-model.md。读自按 B 的背包栈（R2）；类别 `disc`；可见行；行序 + 格子序；空格、非唱片、别的类别的行跳过；同一首歌的多张唱片按位置各算一首。

## 播放状态机（序列规则）

- 初始：`playing = false`，`current` 空，两个开关关。
- `play(list)`：列表空 → `Stop`；`current` 有效（键仍在列表里）→ 从它的开头开始；否则顺序取第一首、随机取随机一首 → `Start`。
- `stop()` → `Stop`，保留 `current`。
- `rewind(list)`：等同 `play`（在播放中也重新开始当前曲）。
- `next(list)`：顺序 → 位置 + 1 取模；随机 → 队列里的下一个（当前压入历史），队列空则洗牌新的一轮（避免与刚放完的最后一首相邻重复，n > 1）；→ `Start`；列表空 → `Stop`。
- `previous(list)`：顺序 → 位置 − 1 取模；随机 → 历史栈弹出，历史空则重放当前。
- `finished(list)`：`loopOne` → 重放当前；否则同 `next`；列表空 → `Stop`。
- `setShuffle(true)` → 以当前为队首建新队列；`setShuffle(false)` → 丢队列与历史。`setLoopOne` 不碰队列。
- 列表变化：位置重新按键解析；键找不到（唱片被拿走）→ 位置夹到范围内；队列在长度或内容变了之后下一次 `next` 前重建。

## 页的行为与几何

- 展开/收起：点右区的移动唱片机（光标空、左键、无 Shift/Ctrl/拖拽）切换；右键仍拿起；页与垃圾桶页同位置，展开一个收起另一个。
- 页挂在面板右边缘、顶部对齐（`JukeboxPanelLayout.of(panelX, panelY, panelWidth)`：`x = panelX + panelWidth + 1`，`y = panelY`）；宽 96、高约 76；从上到下：标题栏（14）；曲名一行（太长截断）；"第 n / 共 m 首"一行；一排四个按钮（上一曲、播放 / 停止、倒带、下一曲，各约 18×14，间隔 3）；一排两个开关（单曲循环、列表随机，带文字，开则高亮）；没有唱片时曲名行位置显示提示 `jukebox.mcfloatingbackpack.empty`，按钮灰色且不响应。
- 页上的左键：按钮与开关触发；页内其它位置、右键、Shift 点击：被吞（不穿透）；滚轮被吞。
- 页的收起条件：面板不可见 / 右区没有移动唱片机 / 登出 / 关闭面板。**页收起不停止播放**；播放的停止条件见 spec FR-012。
- 按钮悬停有提示（上一曲、播放、停止、倒带、下一曲、单曲循环：开/关、列表随机：开/关）。
