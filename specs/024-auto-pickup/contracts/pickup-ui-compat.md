# Contract: 黑名单页面的界面与输入

## 1. 展开 / 收起与点击分派（客户端）

`FloatingPanelInput` 的工具区分支（016）里，与标签管理器、垃圾桶平行增加一条：

```text
左键 + 光标为空 + 命中 LEFT 区 + 该格是自动拾取  →  PickupPanelState.toggle()，消费这次按下
```

- 无 Shift/Ctrl（工具区本来就吞掉 Shift/Ctrl）、没有主区域拖动在进行；右键与"光标非空"走 016 的普通点击（拿起 / 交换）。
- `toggle()` 展开时先 `TagPanelState.get().collapse()`；标签管理器的 `TagPanelState.toggle()` 展开前先 `PickupPanelState.get().collapse()`（**互斥**）。垃圾桶面板不受影响（在矩形之外）。
- 其它区域里的自动拾取不分派，它只是普通物品（FR-005）。
- 左侧区限放：`refusedByLimit` 的谓词补 `isAutoPickup`，提示键 `message.mcfloatingbackpack.limit.auto_pickup`。

收起条件（`FloatingPanelEvents` 每 tick，同标签页的写法）：浮动面板不可见，或 `ClientBackpackView.hasAutoPickup()` 为假（按左侧区整个列表）。退出世界 / 登出：`reset()`（清文本、滚动、展开）。

## 2. 页面的几何与渲染

- 页面画在**主区域矩形**上（与 018 的标签页相同的位置与尺寸，宽高与格子网格对齐），展开期间主区域的格子**不画**（`FloatingPanelRenderer` 里两处 `area == MAIN && TagPanelState.isOpen()` 判断补上"或黑名单页面展开"）。
- 布局用 018 的纯类 `TagPanelLayout.of(...)`：状态行（顶部一行）、标签块从左到右自动换行、末尾加号；编辑时加号换成文本框，下面是下拉（至多 5 条）；按行滚动。
- 状态行文字：`自动拾取黑名单  3/64`（`pickup.mcfloatingbackpack.status`，参数为已用/上限）；黑名单为空时标签区显示"无"（`pickup.mcfloatingbackpack.none`）。
- 标签块显示名：预设标签按语言显示（`TagPanelContext.label`），自定义标签原样；点标签出现删除标记，点删除 = `REMOVE`。
- 加号：`hasTool && !isFull` 才可点；已满（64）时灰色，悬停提示"黑名单已满（64）"（`pickup.mcfloatingbackpack.full`）。
- 文本框与下拉：下拉候选 = `PresetTags.ids()`（带显示名）并上"本背包已用过的标签"（`ClientBackpackView.tags()` 里出现过的全部标签，去重，预设在前），用 `TagMatcher.match` 过滤；点候选 = 立即添加；Enter = 添加高亮候选或输入的文本；ESC = 取消。输入字符限制 `TagRules.acceptsChar`、长度 ≤ 16；粘贴走 `TagRules.filter`。
- 已在黑名单里的标签再添加：客户端直接当成功（不发请求或发了也无害，服务端幂等），文本框关闭。

## 3. 输入的消费

- 页面展开时，主区域上的按下、滚轮**全部被它消费**（不穿透到选区、Ctrl 转移、拖动手势，也不穿透到下面的界面）：`FloatingPanelInput` 里 `TagPanelState.isOpen()` 的分支旁加一条同构的 `PickupPanelState.isOpen()` 分支；`FloatingPanelEvents` 的滚轮分支同。
- 键盘**只在文本框有焦点时**拦截（`PickupPanelState.isEditing()`）：`FloatingPanelEvents` 的 `KeyPressed.Pre/KeyReleased.Pre/CharacterTyped.Pre` 三处判断补上 `|| PickupPanelState.get().isEditing()`；没有焦点时不拦截任何键盘事件（沿用 018）。
- 编辑中点页面以外的地方 → 失焦（`FloatingPanelInput` 里现有的 `TagPanelState.isEditing()` 失焦分支补上对应一条）。
- 022 的 Delete 键：`DeleteKeyInput` 传给 `DeleteKeyRules.Inputs` 的 `tagPanelOpen`/`tagPanelEditing` 取**标签页与黑名单页的并集**（页面覆盖主区域时 Delete 不删主区域物品；编辑时 Delete 属于文本框）。
- 020 的 OFF 界面：`hostsPanel(screen)` 为假时整个面板（含黑名单页面）不画、不响应，展开状态保留，沿用既有机制，无需新代码。

## 4. （已取消）避让开关的客户端发送时机

## 5. （已取消）第三方兼容（`compat/`）

两节随"取消主动避让"取消（research Decision 6）：不新增 `compat/` 代码、不引用精妙背包/旅行者背包的任何类型、不新增 `compileOnly`、不新增网络包、020 的配置不增加含义。`DependencyRulesTest` 照常守住"第三方 `import` 只在 `compat/<modid>/`"，本 feature 没有新增任何第三方 `import`。

## 6. 与 011～023 的关系

唯一的有意变化：**左侧区限放 1 个自动拾取**；**标签页与黑名单页互斥**。点击、拖动均分/收集、选区、Ctrl 转移、缩放、滚动、标签、垃圾桶、兼容开关、宿主界面、配方全部保持。
