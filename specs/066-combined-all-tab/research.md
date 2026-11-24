# Research: 联合背包 · "全部"标签

读码得出的结论与决定（2026-11-24）。每条：**发现 → 决定 → 备选**。规格 [spec.md](./spec.md) 末尾"Plan 阶段需读码确认"的五件事在 R1～R5。

## R1 服务端"上次发出的内容"能否用物品上的 `BACKPACK_CONTENTS` 做引用比较？

**发现**：`BackpackRef.saveInventory` 每次都 `inventory.saveToItem(stack)` → `toContents()` 生成**新的** `BackpackContents` 对象；它是 record，字段含 `List<ItemStack>`，而 `ItemStack` 没有 `equals`（按引用），所以 record 的 `equals` 对"内容相同的两个对象"也返回 false。`ItemStack.copy()` 共享组件值的引用，所以搬动/复制栈不改引用。

**决定**：两级比较。①**引用相同** → 一定没变（没有保存过），跳过；②引用不同 → 比较**主区域列表**（`ItemStack.listMatches`）、`RowView` 的可见行分类与 `ItemTags`，三者都一样也**不发**。每个成员每秒一次，16 个成员约几千次 `ItemStack.matches`，微秒～毫秒级（估算，未实测）。比较逻辑写成纯函数 `AllContentDiff`（泛型，可单测）。

**备选**：给 `BackpackContents` 加版本号字段 → 要升 `DataRevision`，不值得。

## R2 `BackpackClickPayload` 处理体拆出可复用函数的边界

**发现**：处理体 92～317 行可分三段：
1. **外层（留在处理器里）**：坏请求检查（区域、`panelOpen`、槽号范围）、`claimed`/`PanelTargets.resolve`、065 的"声称引用与目标不一致则拒绝"、`BackpackGate.acquireForPanel`、槽号对 `inventory.size(area)` 的检查。
2. **内层（拆成 `applyClick`）**：从 `AbstractContainerMenu menu = ...`（149 行）到末尾：菜单携带物、064 嵌套检查、工具区各规则、`canPlaceMain`、`BackpackMenuBridge.clickWithMenuCarried` / 换手 / `BackpackClickLogic.applyTo`、嵌套记录、`saveInventory`、`sendSlotUpdate`。
3. 内层里有 **2 处 `ref.saveInventory`**（换手分支 292 行、末尾 314 行）和 **9 处 `BackpackNetwork.sendSlotUpdate`**。

**决定**：`applyClick(player, payload, ref, inventory, area, slot, loan)`，`loan` 是可空的"光标借用"（见 R6）：两处保存前调用 `loan.settle(inventory)`。9 处 `sendSlotUpdate` **不改**，改为让 `BackpackNetwork.sendSlotUpdate` 自己认得"全部"会话（见 R7）。单背包标签走 `loan == null`，行为逐字节不变。

**备选**：复制一份"全部"专用的点击逻辑 → 规则会两份，漏改风险高（规格 FR-006 明确不复制）。

## R3 聚合网格能否复用 `PanelLayout.mainMap` / 042 搜索？

**发现**：
- `PanelLayout.of(..., mainMap)` 已经是"显示位置 → 槽号"的映射，**映射值为 -1 时就是填充格**（`slotIndex` 返回 -1，`hit` 返回 null）。所以"各背包的格子首尾相连（不再每只从新的一行开始）、块末尾留空位"可以直接用带 -1 的 `mainMap` 表达，几何、滚动、命中检测都不用改。
- `FloatingPanelState.searchSlots` 就是这张映射的存放处（`layoutModel` 传给 `PanelLayout`，`mainScrollRows` 按它的长度算滚动行数）。
- 搜索：`SearchState.Source`（`slotCount / matcherOf / hasItem / hiddenLines`）是个接口，`SearchResults.compute` 只认"槽号 → 匹配器"。换一个"全部"版 Source 即可，**搜索语法、`TermsCache`、`@mod`/`#tag` 一字不改**。
- 面板的列数不是 9 而是玩家设置的 `visibleCols`（007）；"各背包的格子首尾相连（不再每只从新的一行开始）"依赖列数，所以块起始行只能在**客户端**按当前列数算（服务端不管）。

**决定**：
- **全局格号用"平铺号"**：成员主区域长度的前缀和（不含空位，与列数无关）。服务端寻址只认平铺号；客户端的 `mainMap` 才含空位，由纯逻辑 `AllGrid`（平铺号 ↔ 成员 + 成员内格号；按列数排出带 -1 的映射）生成。
- 搜索 / 整理的结果都是 `mainMap`：搜索 = 命中的平铺号紧凑排列；整理 = 排列表（显示位置 → 平铺号，不含空位）；两者叠加时搜索作用在整理后的显示顺序上。

## R4 客户端直接读 `ClientBackpackView` 主区域的位置有多少？

**发现**：`ClientBackpackView` 引用：渲染器 21、输入 19、事件 15、拖动 5、选择 2、快速转移 3、`ClientPlacement`、`SearchContext`、各面板上下文共约 100 处。读主区域格子内容的主要在渲染器（画格子、悬停提示）、`PanelDragInput`/`PanelSelectionInput`/`QuickTransferInput`（取格子、`slotCount`）、`ClientPlacement.canPlace`（灰格）、`SearchContext`。

**决定**：**"全部"的内容铺进同一个单例**：进入"全部"时 `ClientBackpackView` 装的是**平铺后的合成视图**（槽 = 各成员主区域依次拼接、光标 = 内存光标、三个工具区长度 0），而不是另起一套读取路径。渲染、拖动、选择、快速转移几乎不用改。单例的含义由"镜像当前标签那一只"放宽为"**面板此刻显示的东西**"：具体标签时不变，"全部"时是合成视图。必须改的只有：
1. `ClientPlacement.canPlace`：平铺号 → 成员的 `RowView` 与 `ItemTags`（`ClientCombinedView` 提供）；
2. 请求的发送处（点击、拖动、快速转移）附带 `epoch`；
3. 渲染：染色、悬停来源行、标签高亮、"全部"下不画的东西；
4. 搜索与整理的 `Source`/排列表；
5. 读 `getActiveRef()` 的 3 处（输入、渲染、事件）在"全部"下按"无单个引用"处理。

**备选**：每处读取都分支到 `ClientCombinedView` → 改动面几乎是整个面板代码，回归风险高。

## R5 快速转移放入"全部"最坏依次试 16 只的开销

**发现**：`insertIntoMain` 已是"先分类行、再通用格、同种叠加优先"，对一组栈的开销与成员数线性。放入"全部"= 对成员依次 `acquire`（解码）→ `insertIntoMain` → 有改动才 `saveInventory`。最坏（前 15 只都满）每个成员一次解码 + 一次格子扫描，不保存。

**决定**：一个请求内**成员按需解码、缓存、只保存改动的**（`AllContext`，见 data-model）；解码是现状已有的成本（每次请求本来就解码一只背包），最坏 16 倍，仍是毫秒级（估算，未实测）。一次放入快速转移的槽数有上限（现有 `MAX_BACKPACK_SLOTS`），所以不会无界。

## R6 内存光标怎么复用现有逻辑？

**发现**：现有点击逻辑（`BackpackClickLogic.applyTo`、`BackpackMenuBridge`、`PanelCursor`）把光标当成 `BackpackInventory.cursor`。拖动手势用 `PanelCursor.of(screenOpen, menu, backpack)`（有菜单用菜单携带物，否则用背包里的光标）。

**决定**：
- **借用法**：处理"全部"请求时，把会话里的内存光标**临时放进**被操作的成员背包的 `cursor` 字段，跑现有逻辑，**保存前**取回到会话并把成员背包的 `cursor` 清空（`CursorLoan.settle`）。成员背包的存档里永远不会出现这个光标。
- 跨多成员的手势（拖动分摊、收集）：用一个**空的占位 `BackpackInventory`**（只当光标容器）配 `PanelCursor`，操作槽位走一个小接口 `SlotAccess`（`size/get/set/canPlace`），`BackpackInventory` 与"全部"各给一个适配。`PanelCursorLogic.distribute/gather/pickupAll` 本来就是泛型纯函数，直接复用。
- 打开了容器界面时光标是菜单携带物（`PanelCursor.usesMenu`），内存光标不参与；`MergePanelCursorPayload`（容器界面一打开就发）在"全部"里把**内存光标**并入携带物（同现有语义）。

**备选**：把光标写进某个成员背包 → 违反"联合背包无数据、不动存档"，且崩溃时会污染该背包。

## R7 服务端发给客户端的单背包更新，在"全部"里会错位

**发现**：`BackpackNetwork.sendSlotUpdate / sendFullSync / sendSession` 以及 `AutoPickupEvents`（**直接** `PacketDistributor` 发 `BackpackSlotUpdatePayload`）、`PipeActions`（`sendFullSync(found.ref())`）、`BackpackRef.saveInventory`（布局变了会 `sendFullSyncIfServer`）都把"某只背包的更新"发给客户端，客户端把它**套到当前显示的那一只上**。065 里这已是一个隐患：联合面板开在非 B 激活的标签时，自动拾取的槽位更新会被客户端当成当前标签的槽位更新，**画面错位**（只是画面，下次整包同步纠正；服务端数据没错）。"全部"里则会直接弄乱合成视图。

**决定**（066 一并修，并回补 065 的隐患）：
- `BackpackNetwork` 的 `sendSlotUpdate / sendFullSync` 先问会话：**具体标签会话**只在 `ref` 等于目标时才发，否则丢弃（那只背包没在面板上）；**"全部"会话**把 (ref, 成员内格号) 翻成平铺号，发"全部"的更新包（见协议）。
- `AutoPickupEvents` 改走同一个发送入口（新增 `BackpackNetwork.sendPickupUpdate`），不再自己 `PacketDistributor`。
- 这样"外部变化 1 秒内出现在全部里"（US5-1）在自动拾取时**立即**生效；其他改动（例如管子）靠每秒的内容比较兜底。

## R8 `acquireForPanel` 的过期保护在"全部"里怎么办？

**发现**：`acquireForPanel` 用 `OpenSessions` 里记着的"面板开在哪个身份上"检查请求落点背包的身份，不对就 `resync`。"全部"的请求落在**多个**成员上，身份各不相同，会被误判为过期。

**决定**："全部"请求**不走** `acquireForPanel`；改由 `AllContext.begin` 校验：①会话在"全部"模式；②请求的 `epoch` 等于会话当前 `epoch`；③涉及的每个成员，引用处仍是浮动背包且身份等于该 `epoch` 快照里记的身份；④档位不超、不在保护中。任何一条不满足 → 拒绝，成员表与"全部"内容重发（`epoch` 加一）。通过后用 `BackpackGate.acquire` 取成员（它会给没身份的补身份、通知快照服务，与现有一致）。

## R9 `epoch`：为什么需要它

**发现**：平铺号依赖成员表（成员数、顺序、各自主区域长度）。客户端发出请求时的成员表可能已和服务端不一致（成员增减、行数变化、切了标签）。若一个平铺号 20 在具体标签会话里被解释成"那只背包的 20 号格"，就会**落错位置**（SC-002 的头号风险）。

**决定**：服务端会话里有一个递增整数 `epoch`，在以下时刻 +1：进入"全部"、成员列表或某成员主区域长度/可见行变化、请求被拒后重同步。每个"全部"同步包带 `epoch`；客户端所有主区域请求（点击、拖动、快速转移）带它所见的 `epoch`（具体标签与旧行为用 0）。服务端：会话在"全部"且 `epoch` 相等才处理；**其余一律拒绝并重发**。切到具体标签后，旧 `epoch` 的"全部"请求因为会话不在"全部"而被拒，不会被当成单背包的槽号。

## R10 冲洗的触发点在代码里的位置

**发现**：`CombinedSessions.end / endAndTell / forget / clear` 分别由 `BackpackGate.noteClosed`、`BackpackOpenRequestPayload`（B 键打开）、`MCFloatingBackpack`（下线 `BackpackGate.forget(UUID)`，172 行）与服务器停止（141 行）调用。其中下线只有 UUID（玩家对象当时仍可取），停止时只有会话表。

**决定**：把"冲洗"做成 `CombinedSessions.flush(ServerPlayer)`，在会话结束的**所有**路径之前调用：关闭、B 键打开、切标签、成员表变化、`enabled=false`、下线（改为先 `flush(player)` 再 `forget`）、停服（遍历在线玩家先 `flush` 再 `clear`）。冲洗时成员按**会话里记的快照**逐个校验身份，对不上的成员跳过；剩余走物品栏，再剩余丢在脚下（`ContainerMenuAccess.fallback` 已有）。

## R11 搜索与整理放在哪（2026-11-24 用户改为：取消联合标题栏）

**发现**：搜索框与整理按钮本来就在**面板自己的标题栏**里（`TitleBarLayout`、`FloatingPanelState` 的命中函数、`SearchRenderer`、`SearchInput`），而该标题栏在 065 的标签栏**正下方**。原先想把它们搬到联合标题栏要替换一整套基准坐标；用户判断联合标题栏作用不大，建议取消。

**决定**：**取消 065 的联合标题栏**（联合图标 + 标题 + 档位 + "+n" 那一条）。标签栏直接贴在面板上沿，`BARS_H` 由 30 变为 `TAB_H`（16）。在"全部"里**直接复用面板自己的标题栏**，不画"假标题栏"：
- 搜索框、放大镜、关闭、拖动：原样；搜索对象换成"全部"的数据源（R3，`SearchState.Source`）。
- 整理按钮：保留位置与外观，点击在"全部"里改为客户端的**显示整理**（不发 `BackpackSortPayload`）；具体标签里仍是真整理。
- 背包编号（026）与快照按钮（031）在"全部"里不画不响应（它们属于某一只背包）。
- 联合标题栏原有的信息移位：档位 → "全部"标签的悬停提示；"+n"未连接徽标 → 标签栏右端（沿用其悬停提示，三类计数）；联合图标 → 不再显示（标签栏的"全部"标签可用联合背包图标）。

**代价**：改 065 已提交的代码：`CombinedBarLayout`（去掉 `TITLE_H`/`titleRect`，`BARS_H = TAB_H`）、`CombinedBarRenderer`（去掉 `drawTitle`，徽标挪位）、`CombinedBarInput`、JEI 排除区与 `topReserve`（随 `BARS_H` 自动变）、对应单元测试与 065 的 `contracts/ui-layout.md`。都是小改，且比"把整套搜索基准换到联合标题栏"简单得多；没有新的基准替换风险，原先的"退路"不再需要。

**对"标题栏融合"的影响**：联合标题栏取消后，"把单背包标题栏与联合标题栏融合"这一待办**自动消失**；面板标题栏留在标签栏下面。用户若仍想把它移到标签栏上方，另开 feature。

## R12 其他发现与决定

- **`MAX_MAIN_SLOTS` = 108 + 35×9 = 423**：一个成员的同步包与现有整包同量级（主区域 ≤ 423 格，没有工具区与垃圾桶，比整包小）；平铺号最大约 16×423 = 6768，所以点击包的槽号范围、拖动与快速转移的槽数上限在"全部"下要放宽为 `16 × MAX_MAIN_SLOTS`（只在"全部"请求里；具体标签的边界不变）。
- **`CombinedMembersPayload.selected` 用 `selected+1` 编码**：不方便再塞"全部"，新增一个布尔 `all`；协议号反正要升。
- **标签**：`ItemTags`（`BackpackTagsPayload` 的内容）影响分类行的灰格判定（`ItemCategoryMatcher`），所以每个成员的同步包带上自己的 `ItemTags`，客户端才能在"全部"里画对灰格。
- **颜色与成员对应**：沿用 065 的 `CombinedColors`，"全部"里的染色按成员在标签栏的位置取色；"全部"标签本身不占颜色。
- **默认标签**：`CombinedSelectPayload.OPEN` 改为打开"全部"；服务端 `open()` 不再需要"B 激活的那只"的偏好（`preferred`）。
- **065 的既有测试**里断言默认选中 B 的那只的、断言标签栏无"全部"的，随本期更新。
- **宪法**：不新增依赖、不碰 mixin/反射、不新增贴图（图集预算无新增）；"全部"不引入新的 `compat/` 类型。
