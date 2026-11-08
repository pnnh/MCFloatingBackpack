# Contract: 标签总览的界面与输入

## 1. 状态行与菜单图标

标签面板（018，覆盖主区域）的**顶部状态行**（高 `TagPanelLayout.ROW = 18`）右端有一个菜单图标，矩形由 `TagOverviewLayout.menuIconRect(width)` 给出（`14×14`，距右 2、距上 2）；用 `fill` 画三条横线，悬停时底色变亮；**不新增贴图**。

| 视图 | 图标 | 悬停提示 | 点击 |
|---|---|---|---|
| 选区 | 菜单 | "标签总览" | 进入 `LIST`（`TagPanelState.cancelTransient()`，`setScroll(0)`） |
| 列表 / 明细 | 菜单（高亮） | "返回选区" | 回到 `SELECTION`（`TagOverviewState` 视图复位，**选区不改动**） |

选区视图里，状态行文字的可用宽度让出图标位（`font.plainSubstrByWidth(text, width − 8 − 18)`）。

## 2. 列表页（`LIST`）

- **状态行**：左 `overview.mcfloatingbackpack.title`（"标签总览 %s"，参数为**符合当前筛选的**分类数），右图标。
- **筛选行**：未筛选且未编辑 = 占位字"按标签筛选"；已筛选 = 一个标签块（预设按语言显示）+ 右端 `×`（点 = 清除筛选）；编辑 = 文本框。点占位/标签块进入编辑（`filterEditing`）。
- **下拉**（编辑时）：`TagCandidates.of(预设, 本背包已用标签, ∅)` 经 `TagMatcher.match` 过滤，至多 5 条；点一条 = 选定为筛选并结束编辑；Enter = 选定高亮候选，或输入的文本若是合法标签则选定；ESC = 取消（不改变已有筛选）；上下键移动高亮。
- **行**：图标（`graphics.renderItem(stack, …)`，已不在注册表的物品画占位图标）+ 名称（超宽截断）+ 标签数 + "清空"。悬停行显示提示：物品名 + 该分类的全部标签（每行一个，预设按语言显示）。
- **点击**：点行（不含"清空"）→ `DETAIL`；点"清空"见第 4 节。
- **空状态**：没有任何标签 → "还没有给任何物品打过标签"；筛选无结果 → "没有符合的物品"（筛选框与清除仍可用）。
- **滚动**：滚轮按行滚（上滚向列表开头），夹在 `[0, maxScroll]`；滚动取消"确认清空？"。

## 3. 明细页（`DETAIL`）

- **状态行**：左端返回按钮（`‹`，点 = 回 `LIST`，保留 `listScroll` 与 `filter`），其后物品图标 + 名称（已不在注册表的显示原始 id），右端菜单图标。
- **内容**：`TagPanelLayout.of(...)`（标签块 + 加号/文本框 + 下拉），数据来自 `TagPanelModel.of(List.of(0), i -> item, book, hasTool && registered, isProtected)`：
  - 点标签 → `TagPanelState.setPendingDelete`；点删除 → 发 `REMOVE`；
  - 点加号 → `canAdd` 才 `startEditing`；已满 10 个时加号灰、悬停提示 `tag.mcfloatingbackpack.full`（018 的键）；**已不在注册表** → 加号灰、悬停提示 `overview.mcfloatingbackpack.detail.missing`；
  - 文本框 + 下拉（`TagCandidates.of(预设, 已用标签, 该分类已有标签)`，`TagMatcher` 过滤）；`model.blocks(id)` 为真的候选（`trash` 对受保护物品）变灰，悬停提示 018 的 `tag.mcfloatingbackpack.protected`；Enter/点候选 = 发 `ADD`；ESC = 取消。
- **同步后检查**：每帧若 `book.tagsOf(detailItem)` 为空 → 回 `LIST`（最后一个标签被删光 / 被别处清空）。
- 发出 `ADD/REMOVE` 后客户端**不**改本地数据，等 `BackpackTagsPayload`（Decision 10）。

## 4. 行内二次确认

- 第一次点某行的"清空" → `ClearConfirm.arm(key, now)`，该行"清空"按钮变成"确认清空？"（红底）；
- 同一行第二次点（未超时 `3000 ms`）→ 发 `CLEAR`，复位；
- 取消：点别处（含面板外）、滚动、切换视图、收起面板、超时（渲染时 `isArmed` 为假即显示回"清空"）、点另一行的"清空"（取代）。

## 5. 输入路由（沿用 018 的消费方式）

- 展开期间主区域的按下、滚轮**全部被标签面板消费**（`TagPanelInput.onMainPress/onMainScroll`），`view != SELECTION` 时转给 `TagOverviewInput`；右键与非主区域的按下不处理（照 018）。
- 键盘：**只在文本框有焦点时拦截**（`TagPanelState.isEditing()` 或 `TagOverviewState.isFilterEditing()`）；`view == DETAIL` 的添加走 `TagOverviewInput`（发按 id 的请求），`view == SELECTION` 照旧走 `TagPanelInput`；`KeyReleased/CharacterTyped` 同。
- 点面板外：编辑中的文本框失焦（`FloatingPanelInput` 的既有分支多加一条）。
- 022 的 Delete 键：`DeleteKeyInput` 传 `tagPanelEditing = TagPanelState.isEditing() || TagOverviewState.isFilterEditing()`；`tagPanelOpen` 不变（总览也是标签面板展开）。
- 020 的 OFF 界面：`hostsPanel(screen)` 为假时整个面板（含总览）不画不响应，状态保留（既有机制，无需新代码）。

## 6. 与 011～024 的关系

唯一可见变化：标签面板状态行右侧多一个菜单图标。选区视图的全部行为、022、024 不变。
