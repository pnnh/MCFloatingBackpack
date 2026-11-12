# Research: 背包搜索核心

**Feature**: [spec.md](./spec.md) | **Date**: 2026-11-12 | 全部结论来自读本仓库源码（客户端 `client/panel/` 与 `FloatingPanelEvents`），没有启动游戏；标"待实测"的在 quickstart 里验证。

## D1. 主区域"第几行第几列 → 背包格子编号"的映射只有一处，搜索就改这一处

**事实**：

- `PanelLayout`（纯算术）是几何的唯一来源：`slotIndex(MAIN, ordinal) = (ordinal / cols + scroll) * cols + ordinal % cols`，`hit(x, y)` 用它把鼠标位置换成 `Hit(area, 背包格子编号)`，`visibleCount`、`mainTotalRows`、`maxScroll`、`canBack/canForward` 都读它。
- 渲染 `drawSlotsWithItems` 逐个 ordinal 调 `layout.slotIndex/cellX/cellY`；滚动箭头 `drawScrollArrows` 读 `layout.canBack/canForward`。
- 点击、选区、Ctrl 转移、拖拽分配、Delete 键走的 `PanelSlots.indexAt(state, x, y)` 用 `state.layoutModel(...)` 建一个"虚拟网格"的 `PanelLayout` 再 `hit`；`ClientBackpackView.layout()` 同样走 `state.layoutModel(...)`。**没有任何代码从"格子编号"反推"画在哪里"**（`cellX/cellY` 只接受 ordinal；全仓库 grep 无 `ordinalOf`/`scrollTo`/`reveal`）。
- 发往服务端的全是背包格子编号（`sendClick(slot…)`、`BackpackQuickTransferPayload.of(…, slots)`、光标手势的 `slots`）。

**决定**：给 `PanelLayout` 增加一个可选的"主区域映射"`int[] mainMap`（命中格子编号的有序列表，null = 恒等映射）。有映射时：`slotCount(MAIN)` 视为 `mainMap.length`，`slotIndex(MAIN, ordinal)` 返回 `mainMap[(ordinal / cols + scroll) * cols + ordinal % cols]`（越界 -1），`mainTotalRows = ceil(len / cols)`。`FloatingPanelState.layoutModel(counts)` 把当前映射传进去——`PanelSlots.indexAt` 与 `ClientBackpackView.layout()` 都经过它，所以**渲染、命中、选区、转移、拖拽、Delete、滚动箭头全部自动按映射工作，不需要逐个改**。服务端、网络包、协议号零改动。

**备选（否决）**：在每个消费者里各自过滤（改十来处，必漏）；在服务端做搜索（违背"只改客户端映射"，要新网络包）。

## D2. 滚动与面板尺寸：搜索只影响滚动的"总行数"，不影响面板大小

**事实**：`FloatingPanelState.totalRows` 同时决定两件事：面板尺寸上限（`layout()` 里 `rowsUpper = min(totalRows, fitRows)`、缩放时 `updateResize`）与滚动夹取（`clampScroll` 用 `totalRows`）。搜索时结果行数很少，如果直接改 `totalRows`，面板尺寸会随搜索跳变。

**决定**：`FloatingPanelState` 新增 `searchSlots`（同 D1 的映射，null = 无搜索）。`clampScroll` 的主区域滚动夹取用 `searchSlots == null ? totalRows : ceil(searchSlots.length / visibleCols)`；`totalRows`、尺寸逻辑、`PanelShape` 的合法尺寸判断**保持不变**（搜索时面板大小不变，结果少时多出的格子画成与现有"填充格"相同的纯背景）。设置映射、改变列数（缩放）时都要重新夹取滚动；查询变化时滚动回到第 0 行。

## D3. 双击聚拢、拖拽聚拢的范围——与平时一致，不因搜索缩小（待实测确认）

**事实**：

- 拖拽聚拢 `GATHER_DRAG` 发的是鼠标路径上经过的格子编号（`PanelDragInput.slotAt` → `PanelSlots.indexAt`），所以只会含**当前显示**的格子。
- 双击聚拢 `GATHER_ALL` 只带一个格子编号，服务端 `gatherAll` 在**整个背包**里找同类物品（`BackpackCursorGesturePayload`）。平时它本来就会收走滚出可视范围、以及主区域之外行里的同类物品。

**决定**：不改。双击聚拢保持"整个背包里的同类"，写进已知限制与 quickstart（搜索结果里双击，光标收到的可能比屏幕上看到的多）。这符合规格"与平时一致"，且不需要新的网络动作。

## D4. 标题栏：放大镜用代码绘制，**不新增贴图**

**事实**：关闭键与快照键都是 `graphics.fill` 画的（`drawCloseButton` 一个红底加字母 x，`drawSnapshotButton` 蓝底加几条白线拼的"箱子"），仓库里没有 gui 贴图目录（`textures/` 下只有 `item/`）。

**决定**：放大镜同样用 `fill` 画在 10×10 的按钮底上（圆圈加手柄），**不新增贴图、不进任何图集**。规格 FR-023 写的是"新贴图要度量图集"，这里据实修订为"用代码绘制、不新增贴图；仍在完成后度量一次图集作为回归确认"（已同步改 spec）。

**布局算术**（新增 `TitleBarLayout`，替换并扩展现有 `TitleButtonsLayout`，纯算术）：

- 常量沿用：按钮 10、右边距 3、间隔 2、编号起点 `x+23`（图标 4+16+3）、`MIN_TEXT = 24`、编号后留 2。
- 输入：面板左边 `panelX`、宽 `panelWidth`、`showClose`（主区域列数 > 3）、`searching`。
- 输出：关闭键位置（无则不画不点）、放大镜与快照键位置（可能不显示）、编号文字右限、搜索框的矩形（搜索时）。
- 让位优先级：关闭 > 放大镜 > 快照 > 编号。先放关闭键（有则），再放快照与放大镜，若编号不足 `MIN_TEXT` 先去掉快照，放大镜任何宽度都保留。

| 主区域列数 | 面板宽 | 关闭键 | 快照键 | 放大镜 | 编号可用宽 | 搜索框（x 范围，宽） |
|---|---|---|---|---|---|---|
| 3 | 104 | 无 | 91～101 | 79～89 | 23→77（54） | 23～101（78） |
| 4 | 122 | 109～119 | 97～107 | 85～95 | 23→83（60） | 23～107（84） |
| 9 | 212 | 199～209 | 187～197 | 175～185 | 23→173（150） | 23～197（174） |

（x 都相对面板左边；搜索框右端留到关闭键左边 2 像素，无关闭键时到右边缘内 3 像素。）

**搜索框内部**（新增纯算术 `SearchBoxLayout`，输入框的左右与"命中数的文字宽度"）：左内边距 3；右端 10 像素是 `×`；命中数右对齐在 `×` 左边、间隔 2；**文字区宽度 < 36 像素时命中数不显示**（悬停整个搜索框可看到），`×` 与文字区不让位。3 列时：框 78，文字区 26→69（43 ≥ 36），命中数（≤ 3 位）显示。

**拖动**：`hitsDragBar` 仍覆盖整个标题栏，但按下的判断顺序改为：放大镜 → `×` → 搜索框内部（定位光标，不拖动）→ 关闭键 → 缩放把手 → 拖动把手；所以搜索时背包图标那一块、以及框与右边元素之间的缝，仍是拖动把手。

## D5. 键盘与焦点：照 018/025 的三个事件，字符走自己的输入模型

**事实**：标签框、过滤框、黑名单框都靠 `FloatingPanelEvents` 的三个事件接管键盘：`onScreenKeyPressed(KeyPressed.Pre)`、`onScreenKeyReleased(KeyReleased.Pre)`、`onScreenCharTyped(CharacterTyped.Pre)`；有焦点时处理并 `setCanceled(true)`，没焦点时什么都不拦。鼠标按下时若点在框以外就 `stopEditing`（`FloatingPanelInput.dispatchMouseButton` 开头）。`DeleteKeyInput` 的"正在编辑"判断列出了三个框。`Screen.isPaste(keyCode)` + `keyboardHandler.getClipboard()` 做粘贴。

**决定**：

- 新增 `SearchState`（客户端会话单例：是否打开、焦点、文字、结果映射、全选标记）与 `SearchInput`（键盘/字符/鼠标处理），在上面三个事件和鼠标按下里各加一个分支，`DeleteKeyInput` 的编辑判断加上搜索焦点。
- **不复用 `TagTextInput`**：它只接受标签字符且长度 `TagRules.MAX_LENGTH`、`type(char)`。搜索需要任意字符、按码点处理、上限 100 个码点，所以新写 `SearchTextInput`（纯类：文字、光标、插入/退格/删除/左右/Home/End/清空/粘贴，以及最小的"全选"：Ctrl+A 全选后，输入或退格会替换/清空，其它移动键取消全选）。
- 字符事件给的是码点（`event.getCodePoint()`）；`SearchInput.onCharTyped(int codePoint)` 过滤控制字符后按码点插入，中文输入法上屏的字走这条路。**待实测**：容器里 xdotool 输入中文是否可行；不行就写"未验证"，由用户真机确认。
- **Esc**：搜索框有焦点时 `onKeyPressed` 里 Esc = 清空并关闭搜索框；该事件被取消，宿主屏与容器屏看不到，所以不会关面板。没有焦点时不拦截，沿用现有行为（`TrashConfirmInput.onEscape` 的先后顺序保持：弹窗优先）。
- **B 键**：搜索框有焦点时 `b` 作为字符进入（`KeyPressed.Pre` 被取消）；没焦点时照旧由开关键处理器关闭面板。

## D6. 查询语法的精确定义

按字符从左到右扫描，**引号**切换"整段模式"（整段里的空格、`|`、`-` 都是普通字符；引号本身不进文字）；引号未闭合则到末尾都在整段里。

1. 整段模式以外的空白把查询切成若干**项**（连续空白忽略）。
2. 每一项再按整段模式以外的 `|` 切成若干**备选**。
3. 备选若以 `-` 开头（`-` 在整段模式以外、是该备选的第一个字符）则为**排除备选**，其后文字是要排除的文字；文字为空的备选被忽略。
4. **项的成立条件**：任一备选成立。正备选成立 = 某个"可搜索文字"包含该文字；排除备选成立 = **没有任何**可搜索文字包含该文字。（`iron|-gold` 即"含 iron，或不含 gold"——按规格 FR-020 的字面定义，不做更多解释。）
5. 整条查询成立 = 所有项都成立；没有有效项 = 空查询（不过滤、不高亮）。
6. 大小写：双方 `toLowerCase(Locale.ROOT)` 后做子串比较。`@`、`#`、开头的 `/` 是普通字符。

**可搜索文字**（本 feature）：该格物品的**当前语言显示名**（`stack.getHoverName().getString()`，含玩家改过名的物品）与**注册名的路径部分**（`BuiltInRegistries.ITEM.getKey(item).getPath()`）。匹配接口是一个小函数式接口 `TextMatcher { boolean contains(String needleLowerCase); }`——043 要加拼音时只换实现，不改解析与结果计算；本 feature 里只有"子串"这一种实现。

## D7. 结果集：什么时候算、算什么、怎么留位

- **算**（`SearchResults.compute`，纯函数）：输入查询、格子数、`IntFunction<TextMatcher>`（某格的可搜索文字，空格返回 null）；输出按编号升序的命中格子编号数组。空格永不命中；只有排除项的查询，结果是"有物品且不被排除的"。
- **何时重算**：查询文字变化（含清空）；面板（重新）打开/新会话（此时状态已被清空）。**不**在背包内容变化时重算。
- **留位**：结果集里的格子编号即使其物品被拿走（格子变空）也留在数组里，所以仍显示在原位置、点它与平时点那个空格完全一样（D1：点击发的是同一个格子编号）；不画高亮、不计入命中数。
- **命中数**：每帧 `Σ 结果集中当前有物品的格子`，≤ 400 次空判断，可忽略。
- **选区**：`SearchState.setQuery` 里 `PanelSelection.get().clear()`；Esc/`×` 清空也走它。
- **工具区高亮**：对三个工具区**当前可见**的格子（≤ `rows*2 + cols + 2`，一般不到 30 格）每帧判断名字是否命中（带一个按"物品 + 自定义名"的小缓存避免每帧新建字符串），不参与映射与过滤。

## D8. 高亮与选框的画法

- 搜索命中边框：1 像素外圈（与 `drawSelectionBorder` 同形），颜色待游戏里对照后定，候选**深蓝 `0xFF1E3FD0`**（Shift 选框是黄 `0xFFFFD34D`；格底有灰、蓝、绿、米黄四种；分类扩展行的 1 像素边线是橙、蓝、红、紫、青、绿、棕、粉、深红、深蓝、白）；搜索框的边框同色。
- 同一格既命中又被选中：命中外圈在最外，**选框画成内缩 1 像素的内圈**（与物品图标 16×16 的外缘重叠 1 像素，可接受，截图确认；不行就退成选框盖过命中边框）。
- 扩展行的底色 `drawRowDecor` 里"与上方/左方不同组时画 1 像素线、行首画角标"依赖格子在网格里的相邻关系，搜索结果里相邻关系变了，所以搜索时 `drawRowDecor` **只画底色填充**（不画分隔线与行首角标）；留位的空格仍画该类别的淡图标（`drawGhost`）。行悬停提示（`drawRowTooltip`）按命中格子编号查行，照常工作。
- 搜索结果比格子少时，多出的 ordinal 返回 -1，沿用现有"填充格不画"的路径。

## D9. 状态的生命周期

清空点（`SearchState.reset()`）：`closePanel()`（关闭键、B、背包被拿走都走它）、`onLoggingIn/Out`（退出/进入世界）、`BackpackSessionPayload` 里新会话（切换背包、重新打开；与 `TagOverviewState.resetView()` 同处）。面板仅被隐藏（019 宿主让位、020 的 OFF 界面）不调用任何重置，所以保留——这一条不需要额外代码，只要不在隐藏路径上清空。

**与整页覆盖主区域的面板**：标签面板（018/025）、黑名单页（024）展开时覆盖主区域，渲染与点击都被它们接管，搜索结果此时看不到也点不到；它们收起后结果还在。搜索不改变这几处的互斥规则。标签面板的"选区视图"读选区，选区因查询变化清空，所以显示"请先选择物品"。

## D10. 不动的东西

服务端代码、网络包、协议号（17）、配置、命令、`BackpackContents` 数据格式、`DependencyRulesTest` 规则、运行时依赖：全部不动。新增的语言键只有四个（搜索按钮提示、清除按钮提示、占位提示、命中数提示），中英文齐全。

## D11. 性能

400 格 × 每次按键：读每格 `getHoverName().getString()` 与注册名并转小写，约 800 个短字符串，微秒级到不足 1 毫秒；每帧只多做"命中数"与工具区 ≤ 30 格的判断。宪法 VI（对所有玩家的显存/渲染成本）：只在搜索打开时才有额外工作，没打开搜索时每帧只多一次 `searchSlots == null` 判断；不新增贴图。完成后用游戏内帧时间粗测（容器是软件渲染，只作定性），写进 DELIVERY，测不了就写"未测量"。
