# Research: 标签总览

**Branch**: `025-tag-overview` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

依据都来自读本仓库源码（018 的标签面板、022 的保护规则、024 的黑名单页面）。**没有新的第三方调研**：本 feature 不涉及任何第三方模组。凡是没有在游戏里验证的，列在末尾"仍不确定项"。

## Decision 1：视图状态放在新的 `TagOverviewState`，不改 `TagPanelState` 的结构

**依据**：`TagPanelState`（018）是纯类单例：展开、编辑、下拉下标、待删标签、滚动、文本缓冲。它的所有使用者（输入、渲染、事件、Delete 键）都只认"选区视图"的语义。给它加视图字段会让每个使用点都要分叉。

**决定**：新增纯类单例 `client/panel/tags/TagOverviewState`：

| 字段 | 含义 |
|---|---|
| `view` | `SELECTION`（默认）/ `LIST` / `DETAIL` |
| `detailItem` | 明细页正在看的分类（`ResourceLocation`），仅 `DETAIL` 有意义 |
| `filter` | 列表页当前筛选的标签（或空） |
| `listScroll` | 列表页的滚动行数 |
| `filterInput`（`TagTextInput`）、`filterEditing`、`filterDropdownIndex` | 筛选框 |
| `confirm`（`ClearConfirm`） | 行内"确认清空？"状态（见 Decision 4） |

**复用 `TagPanelState` 已有的 `editing/input/dropdownIndex/pendingDelete/scroll` 给明细页的添加框**：明细页就是"一个分类的标签块 + 加号/文本框/下拉"，与 018 的选区视图是同一种界面，只是"分类的来源"不同。切换视图时 `TagPanelState.cancelTransient()` 并把 `scroll` 置 0。

**备选**：把视图字段塞进 `TagPanelState`——改动面大、回归风险高；为明细页再写一个状态类——和 `TagPanelState` 完全重复。

**重置规则**（spec FR-004）：
- 标签面板因"浮动面板不可见 / 功能区没有标签管理器"被每 tick 检查收起时，同时 `TagOverviewState.resetView()`（回到 `SELECTION`，清筛选、详情、确认；**不清**用户主动收起时的状态）；
- 用户再次左键标签管理器收起面板：只收起，视图状态保留；
- 退出世界/登录：`reset()`；
- **换背包**：服务端每次开会话（`BackpackSessionPayload`）客户端 `handleClient` 里 `resetView()`（`sendSession(open)` 只在 `BackpackOpenRequestPayload` 里发，即每次按 B 打开面板）。

## Decision 2：列表页是新的纯几何 `TagOverviewLayout`；明细页复用 `TagPanelLayout`

**依据**：`TagPanelLayout`（018）已经做了：顶部一行状态行（`ROW = 18`）、标签块自动换行、加号/文本框/下拉、按行滚动、命中检测，纯几何、有测试。明细页需要的完全一样。列表页是另一种结构（带图标的行 + 筛选行 + 每行的清空按钮），没有现成的。

**决定**：
- **明细页**：用 `TagPanelLayout.of(...)` 喂该分类的全部标签；用 `TagPanelModel.of(List.of(0), i -> item, book, hasTool && registered, isProtected)` 把"这个分类"当成"只选中了一个分类的选区"，直接得到 `commonTags`（全部标签）、`canAdd`（有标签管理器且没满）、`blocks(tag)`（022 的保护）。状态行里自己画返回按钮、物品图标与名称、菜单图标。
- **列表页**：新增 `TagOverviewLayout`（纯，不依赖 Minecraft）：状态行（标题 + 菜单图标位）、筛选行（占位/已选标签块 + 清除钮，编辑时是文本框）、下拉（编辑时盖在列表上，至多 5 条）、行（图标位、名称位、标签数位、"清空"按钮位）、按行滚动与最大滚动；命中返回 `MENU / FILTER / FILTER_CLEAR / DROPDOWN i / ROW i / CLEAR i / NONE`。

## Decision 3：行模型与排序是纯函数，渲染前缓存

`TagOverviewModel<K>`（纯，泛型 K 便于用字符串测试）：

```text
rows(book, nameOf, idOf, filter) -> List<Row<K>>   // Row = (key, name, tags)
  只含 book 里至少有一个标签的分类；filter 非空时只含 tags 里有该标签的分类
  排序：name（忽略大小写比较）→ idOf(key)
```

**成本**：最多 1024 个分类；每帧都排序会浪费。`TagOverviewContext`（客户端）缓存行列表，缓存键 = `(ItemTags 实例引用, filter, 语言代码)`；`ItemTags` 是不可变记录，同步一次换一个实例，所以引用比较就够。只画可见的行（按滚动算起止），悬停提示只对悬停那一行算。

## Decision 4：行内二次确认是一个纯状态机 `ClearConfirm`

```text
arm(key, now)                       // 点"清空"：该行进入确认；其它行的确认被取代
isArmed(key, now) -> boolean        // 超时（3000 ms）后视为未确认
confirm(key, now) -> boolean        // 第二次点同一行且未超时才为真，并复位
cancel()                            // 点别处、滚动、切换视图、收起面板
```

时间由调用方传入（`System.currentTimeMillis()`），所以纯类可测、没有真实延时。

## Decision 5：服务端新增一个按物品 id 寻址的请求，规则是纯函数 `TagOverviewRules`

**依据**：018 的请求（`BackpackTagPayload`）携带格子下标，服务端从真实内容解析分类；总览里的物品可能不在背包里，所以需要新的请求：`BackpackTagOverviewPayload(op, itemId, tag)`，`op` ∈ `ADD / REMOVE / CLEAR`，`itemId` 是字符串（`writeUtf(…, 256)`），`tag` 在 `CLEAR` 时为空串。

**服务端校验顺序**（任一步失败整个请求零变化并补发 `BackpackTagsPayload`）：

1. 请求者是 `ServerPlayer`；`BackpackRef.resolve` 得到背包且 `stillValid`（否则 `sendSession(false)`）；
2. **下方功能区有标签管理器**（新增 `BackpackInventory.hasTagManager()`，按整个列表；`BackpackTagPayload` 里原有的私有同名方法保持不动，本 feature 不改 018 的文件）；
3. `TagOverviewRequest.validate(op, itemIdString, tag)`（纯）：`op` 合法；`ResourceLocation.tryParse(itemId)` 不为空（`ResourceLocation.tryParse` 才不会因为坏字符串抛异常）；`ADD`/`REMOVE` 的 `tag` 满足 `TagRules.isValid`；`CLEAR` 的 `tag` 必须为空；
4. 纯规则 `TagOverviewRules.apply(book, op, key, tag, registered, isProtected)` 返回新的 `TagBook` 或 `null`：
   - `ADD`：该分类**已经有至少一个标签**；`registered`（物品仍在注册表里）；不超过 10 个（`TagBook.addOrNull`）；`TagProtection.blocks(tag, isProtected ? 1 : 0)` 为假；已有同名标签是幂等（原值）；
   - `REMOVE`：没有该分类或没有该标签 = 原值（不是错误）；最后一个被移除时分类消失（`TagBook.remove` 的既有行为）；
   - `CLEAR`：没有该分类 = 原值；否则移除该分类（新增 `TagBook.clear(keys)`，纯函数）；
5. 成功则 `setTags`、保存、发 `BackpackTagsPayload`；失败只发 `BackpackTagsPayload`。

**`isProtected`**：`BuiltInRegistries.ITEM.get(key).getDefaultInstance().is(ModTags.NEVER_TRASH)`（与客户端的 `TagPanelContext.isProtected` 同一判断；服务端自己算，不信客户端）；`registered` = `BuiltInRegistries.ITEM.containsKey(key)`。

**协议**：新增一个客户端→服务端的包；版本 `"12"` → `"13"`（`BackpackNetwork` 一处）。**不改**标签同步包、数据格式、`BackpackContents` 字段数（仍 6 个）。

**备选**：把 018 的请求扩成可选带物品 id——改 018 已验证的编码与处理，且"按下标"与"按 id"两种语义混在一个包里；每个操作一个包（增、删、清空各一个）——包多、重复校验。

## Decision 6："ADD 只允许对已有标签的分类"与明细页的行为

总览的定位是"管理已经打过标签的物品"，给**从未打过标签的**物品加标签仍是选区视图的事（spec Out of Scope）。服务端若允许对任意注册物品 ADD，等于把"选一个物品"的入口开放给了总览，范围扩大且不被需求要求；而按这条规则，**明细页删掉最后一个标签后该分类消失，页面就不能再停在一个"没有标签的分类"上添加**——所以明细页此时自动回到列表页（spec 已据此修订 FR-016、US4 场景 8、边界与假设）。

## Decision 7：输入与键盘的路由——尽量只加分支，不改 018 的行为

触点（读源码得到，逐条在 tasks 里落实）：

| 触点 | 做法 |
|---|---|
| `TagPanelRenderer.render`（选区视图） | 状态行右端画菜单图标；状态文字宽度让出图标 |
| `FloatingPanelRenderer` | `TagPanelState.isOpen()` 时：`view == SELECTION` 画 `TagPanelRenderer`，否则画 `TagOverviewRenderer`；两处"跳过主区域格子绘制"判断不变（面板展开即跳过） |
| `TagPanelInput.onMainPress` | 先判菜单图标命中（选区视图里点它进入列表）；`view != SELECTION` 时整个转给 `TagOverviewInput.onMainPress` |
| `TagPanelInput.onMainScroll` | `view != SELECTION` 时转给 `TagOverviewInput.onMainScroll` |
| `FloatingPanelInput`（编辑中点别处失焦） | 加 `TagOverviewState.get().stopFilterEditing()` |
| `FloatingPanelEvents` 键盘三事件 | 条件改为 `TagPanelState.isEditing() \|\| TagOverviewState.isFilterEditing()`；`view != SELECTION` 时转给 `TagOverviewInput`（列表页是筛选框，明细页是添加框，添加走**新的按 id 请求**）；`view == SELECTION` 仍走 `TagPanelInput` |
| 每 tick 收起检查 | 标签面板被收起（不可见/没有标签管理器）时同时 `resetView()` |
| `BackpackSessionPayload.handleClient` | `resetView()`（换背包） |
| `DeleteKeyInput` | `tagPanelEditing` 再 `\|\| TagOverviewState.get().isFilterEditing()`；`tagPanelOpen` 不变（总览也是标签面板展开） |
| 登录/登出 | `TagOverviewState.reset()` |

**备选**：把总览做成独立面板（与标签面板互斥，像 024 那样）：用户要求的是"标签管理器展开的面板里加一个菜单图标切换"，且 024 的复制代码已经带来重复，这次尽量复用。

## Decision 8：菜单图标只用填充与字体

三条横线（`fill` 三个 10×2 的矩形）；悬停提示用 `Component.translatable("overview.mcfloatingbackpack.icon.open"/"…close")`；图标矩形由 `TagOverviewLayout.menuIconRect(width)` 给出，选区视图与总览共用（选区视图里 `TagPanelInput` 用它命中）。**不新增贴图**。

## Decision 9：候选标签集中到纯类 `TagCandidates`

筛选框和明细页添加框的下拉候选 = "预设标签在前、本背包已用过的标签在后，去重，排除（明细页）该分类已有的标签"。024 里有一个同样逻辑的 `PickupPanelModel.candidates`，签名不同；**本 feature 新建 `inventory/tags/TagCandidates.of(presets, used, exclude)`，不改 024 的类**（整理项：以后可让 024 复用，不顺手做）。

## Decision 10：不做乐观更新

客户端发请求后什么都不改，等服务端的 `BackpackTagsPayload`（整份）到了再更新显示；明细页若收到的数据里该分类已不存在，自动回到列表页。理由：规则（上限、保护、标签管理器在不在）都在服务端，客户端猜结果容易与服务端不一致；一次同步的延迟对这种低频操作不可感。

## 仍不确定、需要在客户端会话里验证的项（登记，不得当作已知）

1. 列表在**大量分类**（比如 200～1000 个）下的滚动与悬停手感、缓存失效时的卡顿（造数据：用创造模式 + 标签管理器给大量不同物品各打一个标签，或直接改存档）。
2. 菜单图标、行、"清空"按钮在**窄面板**和不同 GUI 缩放下的布局与截断。
3. 筛选框、明细页添加框与 JEI 搜索框、宿主界面按键的**焦点交互**（沿用 018 的做法，但新增了两个文本框）。
4. 行内确认的**手感**：超时、点别处取消、同一时刻一行。
5. 语言切换后的**排序**与悬停提示。
6. 已不在注册表里的物品（需要移除一个模组后进旧存档）的显示。
7. Windows/Linux 本机无法验证。
