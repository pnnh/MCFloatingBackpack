# Data Model: 标签总览

**Branch**: `025-tag-overview` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

**没有新的存储**：标签数据格式不变（018 的 `ItemTags`：`ResourceLocation → 标签列表`），`BackpackContents` 字段数不变（仍是 6 个，composite 上限）。本 feature 只新增客户端会话状态、几个纯逻辑类、一个客户端→服务端请求。

## 1. `TagBook` 新增 `clear`（`inventory/tags/TagBook`，纯）

```text
clear(Collection<K> keys) -> TagBook<K>   // 移除这些分类的全部标签；不存在的分类忽略；返回新书，原书不变
```

## 2. 请求 `TagOverviewRequest`（`inventory/tags/`，纯）

| 方面 | 规则 |
|---|---|
| `Op` | `ADD`、`REMOVE`、`CLEAR`；`toByte/fromByte`（越界返回 `null`） |
| `validate(op, itemId, tag)` | `op != null`；`itemId` 非空且长度 ≤ 256；`ADD/REMOVE`：`TagRules.isValid(tag)`；`CLEAR`：`tag` 为空串 |
| 物品 id 的解析 | 在调用方（需要 `ResourceLocation`）做：`ResourceLocation.tryParse(itemId)`，为空则整个请求丢弃 |

## 3. 规则 `TagOverviewRules`（`inventory/tags/`，纯，泛型 K）

```text
apply(TagBook<K> book, Op op, K key, String tag, boolean registered, boolean isProtected) -> TagBook<K> | null
```

| op | 条件 | 结果 |
|---|---|---|
| `ADD` | `book.tagsOf(key)` 为空（分类没有标签） | `null`（拒绝） |
| `ADD` | `!registered` | `null` |
| `ADD` | `TagProtection.blocks(tag, isProtected ? 1 : 0)` | `null` |
| `ADD` | 已有该标签 | 原书（幂等） |
| `ADD` | 已有 10 个标签 | `null`（`addOrNull`） |
| `ADD` | 其它 | `book.addOrNull(List.of(key), tag)` |
| `REMOVE` | 分类或标签不存在 | 原书（不是错误） |
| `REMOVE` | 其它 | `book.remove(List.of(key), tag)`（最后一个被移除时分类消失） |
| `CLEAR` | 分类不存在 | 原书 |
| `CLEAR` | 其它 | `book.clear(List.of(key))` |

调用方：新增的 `BackpackTagOverviewPayload.handleServer`。`null` 表示"拒绝：数据零变化并补发同步"；与原书相等表示"没有变化"（不保存也不报错，仍补发同步）。

## 4. 候选 `TagCandidates`（`inventory/tags/`，纯）

```text
of(Collection<String> presetIds, Collection<String> usedTags, Collection<String> exclude) -> List<String>
```
预设在前、本背包已用标签在后，各自保持顺序，去重，排除 `exclude`（筛选框传空；明细页传该分类已有的标签）。

## 5. 列表行模型 `TagOverviewModel<K>`（`client/panel/tags/`，纯）

```text
record Row<K>(K key, String name, List<String> tags)
rows(TagBook<K> book, Function<K,String> nameOf, Function<K,String> idOf, String filter) -> List<Row<K>>
```
- 只含"至少有一个标签"的分类（`TagBook` 本来不存空分类）；`filter` 非空时只含 `tags` 里有该标签的分类；
- 排序：`name` 忽略大小写比较（`String.CASE_INSENSITIVE_ORDER`），相同按 `idOf(key)`；
- `nameOf` 对已不在注册表里的物品返回其原始注册 id 字符串。

## 6. 行内确认 `ClearConfirm`（`client/panel/tags/`，纯）

| 状态 | `(armedKey, armedAt)` 或空 |
|---|---|
| `arm(key, now)` | 记下 key 与时间，**取代**之前任何一行的确认 |
| `isArmed(key, now)` | key 相同且 `now - armedAt < TIMEOUT_MS (3000)` |
| `confirm(key, now)` | 若 `isArmed` 为真则返回 `true` 并复位；否则 `false`（调用方改为 `arm`） |
| `cancel()` | 复位 |

## 7. 会话状态 `TagOverviewState`（`client/panel/tags/`，纯单例）

见 research Decision 1 的表。状态转移：

```text
SELECTION --点菜单图标--> LIST --点行--> DETAIL --返回--> LIST --点菜单图标--> SELECTION
DETAIL --点菜单图标--> SELECTION
DETAIL --该分类的标签被删光 / 同步后分类不存在--> LIST
任一 --面板被每 tick 检查收起 / 新会话 / 登录登出--> SELECTION（resetView / reset）
```

- `LIST` 的进入/离开：`TagPanelState.cancelTransient()`，`TagPanelState.setScroll(0)`，`ClearConfirm.cancel()`。
- 从 `DETAIL` 返回 `LIST` 保留 `listScroll` 与 `filter`。

## 8. 列表页布局 `TagOverviewLayout`（`client/panel/tags/`，纯几何）

```text
常量：ROW_H = 18, FILTER_H = 14, PAD = 2, ICON = 14, DROPDOWN_MAX = 5
of(width, height, rowCount, scroll, filterEditing, dropdownCount, clearLabelWidths[...])
状态行 y∈[0,18)：标题位（左）+ menuIconRect（右）
筛选行 y∈[18,34)：filterRect；有筛选时右端 filterClearRect
下拉（编辑时）：盖在列表之上，每条 14 高，至多 5 条
行区 y≥34：第 i 行（滚动后）的 rowRect、iconRect(16×16)、countRect、clearRect
命中：hit(x,y) -> (MENU | FILTER | FILTER_CLEAR | DROPDOWN i | ROW i | CLEAR i | NONE, index)
maxScroll：max(0, rowCount - 可见行数)
```
行右端从右到左：`clearRect`（宽 = 文本宽 + 8，确认状态时是"确认清空？"的文本宽）、标签数位（固定宽 14）；名称位占其余宽度，按像素截断。

## 9. 资源清单

| 资源 | 说明 |
|---|---|
| 语言 | `zh_cn.json`、`en_us.json`：`overview.mcfloatingbackpack.icon.open`（"标签总览"）、`…icon.close`（"返回选区"）、`…title`（"标签总览 %s"）、`…empty`（"还没有给任何物品打过标签"）、`…filter.placeholder`（"按标签筛选"）、`…filter.none`（"没有符合的物品"）、`…filter.clear`、`…detail.back`、`…detail.missing`（"这个物品已不在游戏里，只能删除标签"）、`…clear`（"清空"）、`…clear.confirm`（"确认清空？"）、`…clear.tooltip`（"清空这个物品的所有标签"） |
| 贴图/模型/配方 | **无**（不新增贴图，宪法 VI） |
| 构建/依赖 | 无变化 |
