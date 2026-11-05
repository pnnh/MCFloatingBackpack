# Data Model: 标签管理器

## TagRules（纯逻辑）

| 项 | 规则 |
|---|---|
| `MAX_LENGTH` / `MIN_LENGTH` | 16 / 1 |
| `MAX_TAGS_PER_ITEM` | 10 |
| `acceptsChar(c)` | `'!' ≤ c ≤ '~'` 且 `c` 不在 `'A'..'Z'`（小写字母、数字、`_`、`-` 及其他可见符号；不含空格、大写、非 ASCII） |
| `isValid(tag)` | 非空、长度 1..16、每个字符 `acceptsChar` |
| `filter(text)` | 逐字符保留 `acceptsChar` 的字符并截到 16（用于粘贴） |

## PresetTags（纯逻辑）

`ids()` = `stone, wood, food, ore, trash, tool, weapon, armor, plant, redstone`（10 个，唯一，全部满足 `TagRules.isValid`）；`langKey(id)` = `tag.mcfloatingbackpack.preset.<id>`；`isPreset(tag)`。

## TagMatcher（纯逻辑）

`match(query, entries)`：`entries` = [(id, 显示名)]；返回按相关度排序的 id 列表。相关度：id 或显示名**前缀**匹配 > **子串**匹配 > **子序列**匹配；不区分大小写；同档保持预设顺序；空查询返回全部。

## TagBook<K>（纯逻辑，泛型键；运行时 K = `ResourceLocation`）

内部 `Map<K, List<String>>`（保持添加顺序）。

| 操作 | 语义 |
|---|---|
| `tagsOf(k)` | 该分类的标签（拷贝） |
| `common(Collection<K>)` | 所有给定分类共同拥有的标签（交集，保持第一个分类里的顺序）；空集合返回空 |
| `full(Collection<K>)` | 已有 ≥ 10 个标签的分类 |
| `canAdd(Collection<K>, tag)` | 没有任何分类"尚无该标签且已满" |
| `add(Collection<K>, tag)` | `canAdd` 为假则**什么都不改并返回失败**；否则给每个分类添加（已有则跳过）；返回新的 `TagBook` |
| `remove(Collection<K>, tag)` | 从每个分类移除（没有则跳过） |

不变量：任何分类标签数 ≤ 10；`add` 要么全成要么零变化；列表无重复。

## ItemTags（记录，MC 类型）

`ItemTags(Map<ResourceLocation, List<String>> map)`；`EMPTY`；对 `TagBook<ResourceLocation>` 的薄封装。

- `CODEC`：`Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING.listOf())`，读入时丢弃不合法标签并把每个分类截到 10 个（防止外来数据破坏上限）。
- `STREAM_CODEC`：`ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, list(STRING_UTF8(16)).apply(list(10)), 1024)`，分类数或列表过长时解码抛 `DecoderException`。
- 服务端额外限制：一只背包最多 1024 个分类。

## BackpackContents（扩展）

`BackpackContents(items, cursor, tools, tags)`；`tags` 缺省为 `ItemTags.EMPTY`（`optionalFieldOf`）；`fit(...)`、`copy()`、`normalize(...)` 保留标签；`BackpackInventory` 增加 `tags()`/`setTags(...)`，`fromContents`/`toContents` 读写。

## TagRequest（纯逻辑，请求校验）

`enum Op { ADD, REMOVE }`（线上一个字节，`fromByte` 未知返回 null）；`validate(op, slots, tag, mainSize)`：`op` 合法；`slots` 非空、无重复、每个在 `[0, mainSize)`、数量 ≤ `min(mainSize, 108)`；`tag` 满足 `TagRules.isValid`。（解析成物品 id、去重、忽略空格子、核对工具存在、检查上限是服务端处理里用真实数据做的。）

## TagPanelModel（客户端纯逻辑）

**输入**：选区下标集合、`stackAt(i)` 给出的物品分类 id（空格子为 null）、`ItemTags`、工具是否存在。

**输出**：

| 字段 | 含义 |
|---|---|
| `status` | `NO_ITEMS`（没有任何有物品的选中格子）/ `ITEMS` |
| `categories` | 去重后的分类 id 列表（按首次出现顺序） |
| `commonTags` | 所有分类共同拥有的标签（单个分类时就是它的全部标签） |
| `fullItems` | 已有 ≥ 10 个标签的分类（用于灰色加号的提示） |
| `canAdd` | `status == ITEMS` 且 `fullItems` 为空 |

## TagPanelState（客户端单例，界面状态）

| 字段 | 含义 |
|---|---|
| `open` | 标签面板是否展开 |
| `editing` | 文本框是否有焦点（只有 `open` 时才可能为真） |
| `text`、`caret` | 文本框内容与光标（由 `TagTextInput` 管理） |
| `dropdownIndex` | 下拉里高亮的项（-1 表示没有选中项） |
| `pendingDelete` | 正显示 ✕ 的标签（或 null） |
| `scroll` | 纵向滚动（行） |

生命周期：收起不清空 `text`/`scroll`；工具离开功能区、浮动面板关闭 → `open = false`、`editing = false`（`text`/`scroll` 保留）；登出 → 全部复位。状态机：`收起 ⇄ 展开`；展开里：`普通 → 编辑（点加号）→ 普通（回车/ESC/点别处）`；`普通 → 某标签待删除（点标签）→ 普通（点 ✕ / 点别处）`。

## TagTextInput（客户端纯逻辑）

缓冲与光标；`type(c)`（`TagRules.acceptsChar` 且长度 < 16 才插入）；`paste(text)`（`TagRules.filter` 后插入）；`backspace/delete/left/right/home/end`；`clear()`；`text()`、`caret()`。

## TagPanelLayout（客户端纯逻辑）

输入：面板像素宽 `W`（= 主区域列数 × 18）、高 `H`（= 主区域行数 × 18）、标签文字宽度测量函数、标签列表、是否编辑中、下拉项数、滚动、`pendingDelete`。输出：状态行矩形；每个标签块的矩形（宽 = 文字宽 + 内边距，`pendingDelete` 的标签右侧多一个 ✕ 区域，✕ 区域的矩形单独给出）；加号矩形（或文本框矩形，独占一行）；下拉项矩形（≤ 5，叠在文本框下方）；总内容行数与最大滚动；`hit(x, y)` → `{TAG(i), DELETE(i), PLUS, TEXTBOX, DROPDOWN(i), STATUS, NONE}`。行高 = 18，宽度方向按块宽流式换行，超出宽度换到下一行。

## 配方与资源

| 资源 | 位置 |
|---|---|
| 配方 | `data/mcfloatingbackpack/recipe/tag_manager.json`（3×3，见 research Decision 7） |
| 物品模型 | `assets/mcfloatingbackpack/models/item/tag_manager.json`（`minecraft:item/generated`） |
| 贴图 | `assets/mcfloatingbackpack/textures/item/tag_manager.png`（16×16 RGBA） |
| 语言 | `lang/zh_cn.json`、`lang/en_us.json`：`item.mcfloatingbackpack.tag_manager`、10 个 `tag.mcfloatingbackpack.preset.*`、`tag.mcfloatingbackpack.none`、`tag.mcfloatingbackpack.hint.select`、`tag.mcfloatingbackpack.full`、`tag.mcfloatingbackpack.full.batch`、`tag.mcfloatingbackpack.add.placeholder`、`tag.mcfloatingbackpack.status` 等 |
