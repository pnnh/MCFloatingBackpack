# Research: 标签管理器

结论来自读本仓库源码（`ModItems`、`BackpackContents`、`BackpackInventory`、`BackpackClickPayload`、`FloatingPanelEvents`/`FloatingPanelInput`/`FloatingPanelRenderer`、`PanelLayout`、`PanelSelection`、`ToolAreaRules`、现有配方 JSON）、NeoForge 21.1.251 / 原版 1.21.1 的源码（`KeyboardHandler`、`InputEvent`、`ScreenEvent`）以及两个对照模组的配方数据（精妙背包 3.26.12、旅行者背包 10.1.38，只读取配方 JSON 作设计参考，没有读它们的代码）。**没有在游戏里验证**的地方在"仍不确定"里列出。

## Decision 1: 标签管理器是一个普通 `Item`，功能由"它在功能区里"触发

**Decision**: 新增 `TagManagerItem extends Item`（最大堆叠 1），注册名 `tag_manager`，进创意栏；**不改任何放入规则**（`ToolAreaRules` 保持恒为真，主区域本来就不询问）。展开面板的触发条件是客户端看到"功能区（下区）里有标签管理器"，并且玩家左键点的就是那一格。

**Rationale**: 用户决定它可以放进任何区域、只有放在功能区才有功能，这等于把"功能区里有它"当作功能开关，不需要服务端规则。服务端只在处理标签请求时再核对一次"这只背包的功能区里确实有标签管理器"，保证权威（Decision 5）。

## Decision 2: 标签数据 = `BackpackContents` 里的新可选字段，按物品 id 为键

**Decision**: 新增 `ItemTags` 记录（`Map<ResourceLocation, List<String>>`，列表保持添加顺序），作为 `BackpackContents` 的第四个字段 `tags`（`optionalFieldOf("tags", ItemTags.EMPTY)`；旧存档读成空）；`BackpackInventory` 增加 `tags()` 访问并在 `fromContents/toContents` 里读写；升级配方的 `fit` 同步保留。

**Rationale**: 与 016 的工具区一样放在同一个数据组件里，随背包物品保存、升级、复制，互不共享（用户已定"每只背包各自保存"）；键用物品注册 id，所以与具体堆叠无关、物品移走后标签仍在。

**安全上限**: 每个分类最多 10 个标签（用户要求）；每个标签 1～16 个字符；**另外**服务端把一只背包的分类数封顶在 1024（远大于实际需要，只为防止异常请求把组件撑到无限大，因为组件会随物品栈同步）。这条是 spec 没写的内部保护，登记在"仍不确定"，建议补进 spec 的 Edge Cases。

**Alternatives considered**: 存在玩家数据里（用户已否决）；存在每个物品堆叠上（用户明确要求挂在分类上）。

## Decision 3: 标签规则与预设是纯函数，客户端与服务端共用

**Decision**: 新增无 Minecraft 依赖的 `TagRules`：合法标签 = 1～16 个字符，每个字符在 `'!'..'~'` 内且**不是** `'A'..'Z'`（即小写字母、数字、`_`、`-` 和其他可见符号，不含空格、不含大写、不含非 ASCII）；`acceptsChar(c)` 给文本框过滤用。`PresetTags` 内置 10 个稳定 id：`stone`、`wood`、`food`、`ore`、`trash`、`tool`、`weapon`、`armor`、`plant`、`redstone`（本身都满足 `TagRules`），显示名走语言键 `tag.mcfloatingbackpack.preset.<id>`。

**Rationale**: 规则与预设会被文本框、请求校验、模糊匹配、测试四处使用，放在一个纯类里最不容易漂移。预设只存 id，换语言不影响数据（用户已定）。

## Decision 4: 模糊匹配 = 前缀 > 子串 > 子序列，不区分大小写

**Decision**: 纯类 `TagMatcher.match(query, entries)`，`entries` 是（id，当前语言的显示名）对；对 id 与显示名分别做不区分大小写的匹配，按"前缀匹配 → 子串匹配 → 子序列匹配"排序，同档按预设原有顺序；空查询返回全部预设。

**Rationale**: 满足"带一定的关键词模糊匹配"；显示名参与匹配是因为界面语言可能是中文（用户输不了中文，但英文界面下显示名就是英文，可匹配）。

## Decision 5: 增删请求带"选中的主格子下标"，服务端解析并校验

**Decision**: 新增客户端→服务端的 `BackpackTagPayload(op, slots, tag)`：`op` = ADD(0)/REMOVE(1)；`slots` = 选区里的主格子下标（上限 108，解码超限抛 `DecoderException`）；`tag` = 标签字符串。服务端 `handleServer`：解析并核对背包（`BackpackRef.resolve` + `stillValid`）→ **核对功能区里确实有标签管理器** → `TagRequest.validate`（纯类：操作合法、下标非空无重复且在 `[0, size)`、标签合法）→ 把下标解析成**非空格子**的物品 id 并去重 → 空集则忽略 → ADD：对每个分类，若还没有该标签且已有 10 个标签则**整个请求拒绝**（数据零变化），否则逐个添加（已有则跳过）；REMOVE：逐个移除（没有则跳过）→ 保存背包 → 把整份标签发回客户端。

**Rationale**: 选区只在客户端（014），所以必须带下标；用下标而不是物品 id，服务端才不用信任客户端说的物品种类。核对工具存在，防止没有标签管理器的客户端发请求。整个请求要么全成功要么零变化，符合 spec 的 FR-012/SC-007。

## Decision 6: 新增 `BackpackTagsPayload` 把标签同步给客户端；协议 `"7"` → `"8"`

**Decision**: 服务端→客户端 `BackpackTagsPayload(ItemTags)`；在会话打开（`sendSession(open)`）之后、以及每次标签变化之后发送。`BackpackSessionPayload` 已经有 6 个字段（`StreamCodec.composite` 上限），所以不往里加，单独发这个包。`ItemTags.STREAM_CODEC` 用 `ByteBufCodecs.map`（键 `ResourceLocation.STREAM_CODEC`，值 `list(UTF8 16)` 再套 `list(10)`），分类数解码上限 1024，超限抛 `DecoderException`。`BackpackContents.STREAM_CODEC` 也加一个 `tags` 字段（3 → 4 个字段）。

**Rationale**: 标签变化不频繁，整份重发最简单可靠；会话包字段已满，单独的包避免改动现有 5 个包的编码。物品栈同步里多带一份标签，体积随标签数增长（典型几百字节），可接受。

## Decision 7: 配方——一段式 3×3，参考两个模组的"线 + 皮革 + 主题材料"，不用它们的物品

**Decision**: `data/mcfloatingbackpack/recipe/tag_manager.json`，`minecraft:crafting_shaped`，`category: misc`：

```text
S P S      S = c:strings（线）      P = minecraft:paper（纸）
L B L      L = c:leathers（皮革）   B = minecraft:book（书）
P I P      I = minecraft:ink_sac 或 minecraft:black_dye（墨）
```

→ 1 个 `mcfloatingbackpack:tag_manager`。

**对照**: 精妙背包的升级组件是"升级基础件（铁锭 + 皮革 + 线）+ 主题材料"（过滤升级 = 基础件 + 红石粉 + 线），旅行者背包是"空白升级（线 + 皮革）+ 主题材料"（合成升级 = 空白升级 + 工作台 + 箱子）；我们的背包本身是皮革 + 线 + 箱子。标签管理器沿用"线 + 皮革"这套基础材料的味道，主题材料换成"标签"：纸 + 线（吊牌）、墨水、书，全部是原版早期材料，总成本（2 线、2 皮革、1 书、2 纸、1 墨）相当于一个中等升级组件。

**Rationale**: 不引入新的"基础件"物品（那需要再做一张贴图和一套配方，超出本 feature）；不依赖任何第三方模组的物品（宪法 IV）；`c:strings`/`c:leathers` 是 NeoForge 的通用标签，与对照模组一致。墨用"墨囊或黑色染料"两种都行。

## Decision 8: 图标——先用 ludo.ai 生成，整理成 16×16，失败则自绘；平面模型

**Decision**: `models/item/tag_manager.json` 用 `minecraft:item/generated`，纹理 `mcfloatingbackpack:item/tag_manager`（`textures/item/tag_manager.png`，16×16 RGBA）。生成流程：用 ludo.ai MCP 的 `createImage` 生成一张大图（提示词：像素风、原版 Minecraft 物品图标、一只带线的吊牌/标签、透明背景），必要时 `removeBackground`，再用一个放在 `design/` 下的小脚本缩成 16×16（盒式缩放 + 调色板量化，保留透明）；如果效果不好或额度不够，改用同一个脚本里的手绘 16×16 像素网格（字符画 + 调色板）一次性生成。生成方式、提示词、来源文件和脚本记录在 DELIVERY；`design/` 下的源图与脚本不随模组发布。

**Rationale**: 用户要求"一次性生成、仅一张贴图"；`item/generated` 让物品栏、手持、掉落物、展示框都是同一张平面贴图（像面包）；16×16 不影响共用图集的预算（宪法 VI 仍度量一次）。

## Decision 9: 标签面板是一个覆盖主区域的叠层；布局、模型、文本编辑都是纯类

**Decision**: 
- `TagPanelState`（客户端会话单例）：`open`、`editing`（文本框是否有焦点）、`text`、`caret`、`dropdownIndex`、`pendingDelete`（正显示 ✕ 的标签）、`scroll`；收起不清空（FR-005）。
- `TagPanelModel`（纯类）：由"选中的主格子下标 + 各格物品 id + `ItemTags`"算出显示内容——分类集合（去重、忽略空格子）、共同标签（交集）、加号是否可用、已满的物品列表、无物品选中的提示。
- `TagPanelLayout`（纯类）：在主区域矩形（整数个格子）里排版：第 1 行（18 像素）是状态行（"选中 n 种物品"或提示）；其余按行流式排布标签块（块宽 = 文字宽度 + 内边距，宽度测量由调用方注入，测试里用假的 6 像素/字符），最后是加号（或文本框，独占一行，下拉列表叠在它下面，最多 5 项）；行高 18 像素，纵向滚动；提供 `hit(x, y)`（标签、标签的 ✕、加号、文本框、下拉项、空白）。
- `TagTextInput`（纯类）：文本缓冲、光标、插入（经 `TagRules.acceptsChar` 过滤，≤ 16 字符）、退格/删除/左右/Home/End、粘贴（逐字符过滤）。

**Rationale**: 把规则、排版、编辑都做成不依赖 Minecraft 的纯类，才能用单元测试钉住（多选交集、10 个上限、换行、命中、过滤），渲染与事件只读它们的结果，同 011～017 的做法。

## Decision 10: 展开期间主区域被面板消费；选区不动

**Decision**: `FloatingPanelInput` 的格子分支里，**面板展开时**命中 `MAIN` 区域（以及滚轮）的事件先交给标签面板处理（点击标签/✕/加号/文本框/下拉项，其余空白点击只用来取消编辑或取消 ✕），**不进入** 014 选区、011 Ctrl、013 拖动的任何路径；`PanelSelection` 不被触碰。工具区、标题栏、缩放手柄照常。渲染时在主区域位置画面板背景和内容，隐藏主区域的格子、选区边框与滚动箭头。

**Rationale**: 满足 FR-004，并且让 011/013/014 的输入类一行不用改（它们只是收不到被面板消费的事件）。

## Decision 11（关键）: 键盘接管用"事件拦截 + 仅在编辑时存在的透明输入屏幕"

**Decision**:
- **有界面打开**（容器界面）：文本框有焦点时，用 NeoForge 的 `ScreenEvent.KeyPressed.Pre`、`KeyReleased.Pre`、`CharacterTyped.Pre`（都可取消）把键盘事件喂给 `TagTextInput` 并取消，原版因此收不到。
- **HUD 模式（没有任何界面）**：原版在没有界面时，按键直接进 `KeyMapping`，NeoForge 的 `InputEvent.Key` **不可取消**且没有字符事件，所以无法拦截。做法是文本框获得焦点的那一刻 `minecraft.setScreen(new TagInputScreen())`——一个**透明、不暂停、不画任何东西**的 `Screen`——打开它会让原版自动释放所有按键状态（WASD 不再移动角色），并且之后的按键与字符都走上面同样的 `ScreenEvent` 事件；文本框失去焦点（ESC、回车、点击别处、面板收起、工具离开、浮动面板关闭）时 `setScreen(null)`，键盘立刻还给游戏。
- **没有焦点时不创建这个屏幕、不拦截任何事件**（FR-016）：HUD 下 WASD 照常。
- 这个屏幕只是键盘承载体，不是面板的打开路径：浮动面板仍然是叠层。因此需要让现有逻辑把它当作"可以承载面板的界面"：`allowsPanel`、`onScreenOpening`（不要因为它打开而关闭面板）、`onScreenRender`（它打开时面板由 `ScreenEvent.Render.Post` 绘制，因为 `RenderGuiEvent.Post` 在有界面时不画）、鼠标事件钩子（它打开时点击仍交给面板）都新增 `FloatingPanelEvents.hostsPanel(screen)`（容器界面或 `TagInputScreen`）；而"光标来源/`screenOpen`"仍只认容器界面（`ContainerScreenCompat.isOpenContainerScreen`），所以这个屏幕下仍然用面板自己的光标。
- `FloatingPanelEvents.onKey`（`InputEvent.Key`，现有的热键切换与 ESC 关面板）在编辑中直接返回，避免文本框里的 `Esc`/热键字符同时触发面板切换。

**Rationale**: 这是唯一不改原版代码（不用 mixin）就能在 HUD 下可靠地独占键盘并让角色停止移动的办法；它只在编辑时存在，满足用户"没有焦点时不拦截、HUD 下 WASD 照常"。**这是对宪法 I 的一个窄例外**（宪法说不得把 `Minecraft.screen` 当作面板的主要打开路径；这里它只是编辑期的键盘承载体），记录在 Complexity Tracking，并在 quickstart 里专门验证（不弹暂停菜单、不遮挡画面、鼠标抓取恢复）。

**Alternatives considered**: 在 `InputEvent.Key` 里读按键并自己转字符、每 tick 清 `KeyMapping`（键盘布局相关、按键状态闪烁，不可靠）；mixin `KeyboardHandler`（侵入大，维护成本高）；始终打开一个屏幕（违背宪法 I，且没有焦点时就会拦截）。

## Decision 12: 展开/收起的点击分派

**Decision**: `FloatingPanelInput` 的工具区分支：命中 `BOTTOM` 且那一格是标签管理器、按下的是左键、光标为空、没有 Shift/Ctrl 时 → `TagPanelState.toggle()`（纯客户端，不发包）并消费；其余一律按 016 的点击规则发 `BackpackClickPayload`（右键拿起、光标有物品时交换等不变）。每 tick 用 `ClientBackpackView` 检查功能区里是否还有标签管理器，没有就收起面板并结束编辑；浮动面板关闭或登出时同样收起并结束编辑（界面状态保留，登出时清空）。主区域/左区/右区里的标签管理器走原有点击，没有任何特殊处理。

## Decision 13: 文字与提示全部走语言键，按钮用 `fill` 绘制

**Decision**: 所有文字用 `Component.translatable`（`zh_cn` 与 `en_us` 各一份：物品名、10 个预设、"无"、"请先在主区域用 Shift 选择物品"、已满提示及"不支持批量新增"、占位文字）；标签块、加号、✕、文本框边框、下拉都用 `GuiGraphics.fill` 与字体绘制，不新增贴图；灰色加号悬停提示用 `renderTooltip` 列出已满物品的名称（`Item.getDescription()`）。

**Rationale**: FR-018、FR-019；不新增资源，图集预算不受影响。

## Decision 14: 测试策略

**Decision**: 纯逻辑单元测试：`TagRules`、`PresetTags`（10 个、唯一、均合法）、`TagMatcher`、`TagBook`（泛型：增删、10 个上限、共同标签、已满列表、整请求要么全成要么零变化）、`TagRequest.validate`、`TagTextInput`、`TagPanelModel`、`TagPanelLayout`；资源测试：解析 `tag_manager.json` 配方（图案、键、结果）与两份语言文件（所有标签相关键齐全）。存储编码、网络、键盘接管、渲染与手感只能在客户端会话里验证（宪法 V）。

## 仍不确定 / 需要游戏内确认

- **透明输入屏幕**：打开时是否有一帧闪烁、鼠标抓取/释放是否正常、`ScreenEvent.Opening` 是否会因为我们的屏幕而误关面板、`F3`/截图等系统热键是否仍可用、焦点丢失后移动键是否立刻恢复（宪法 I 的例外，必须在 HUD 与容器界面两种情况下验证）。
- `InputEvent.Key` 在被 `ScreenEvent` 取消的按键上是否真的不再触发（源码里看起来是 `return` 在前，需要实测）。
- 标签管理器图标的最终效果与 ludo.ai 额度；是否需要退回自绘。
- 分类数封顶 1024 是 spec 没写的内部保护，建议补进 spec。
- 面板在最小主区域（3×3 = 54×54 像素）里的排版是否仍可用（状态行 + 一行标签 + 加号，靠滚动查看更多）。
- 配方成本是否合适（只是设计初值）。
