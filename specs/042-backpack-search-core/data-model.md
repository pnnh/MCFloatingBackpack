# Data Model: 背包搜索核心

**没有新的存储**：搜索状态只在客户端会话里，不进背包数据（`BackpackContents` 字段数不变）、不进配置、不进网络包。下面都是纯类（不依赖 Minecraft 类的标 ★，可单元测试）。

## 1. `SearchQuery` ★（`client/panel/search/`）

解析结果。语法见 [contracts/query-syntax.md](./contracts/query-syntax.md)。

```text
SearchQuery.parse(String text) -> SearchQuery
  terms(): List<Term>            // 每项都要成立（并且）
  isEmpty(): boolean             // 没有有效项
Term     = List<Alternative>      // 任一备选成立
Alternative = (boolean negated, String text)   // text 已转小写（Locale.ROOT），非空
```

## 2. `TextMatcher` ★ 与 `SearchMatcher` ★

```text
interface TextMatcher { boolean contains(String needleLowerCase); }    // 043 换实现（拼音）时不改下面的类
SearchMatcher.matches(SearchQuery q, TextMatcher haystack) -> boolean
   // 项成立 = 任一备选成立；正备选成立 = haystack.contains(text)；排除备选成立 = !haystack.contains(text)
   // 空查询 -> true
TextMatcher.ofTexts(List<String> lowerCaseTexts)  // 本 feature 唯一的实现：任一文字 contains(needle)
```

## 3. `SearchResults` ★

```text
SearchResults.compute(SearchQuery q, int slotCount, IntFunction<TextMatcher> matcherOf) -> int[]
   // matcherOf(i) 对空格子返回 null；空格永不命中；升序；空查询返回 null（= 不过滤）
SearchResults.liveCount(int[] slots, IntPredicate hasItem) -> int
```

## 4. `SearchTextInput` ★

```text
text(), caret()                       // UTF-16 下标；移动与删除按码点
insert(int codePoint)                 // 控制字符丢弃；超过 MAX_CODEPOINTS(100) 丢弃
paste(String)                         // 逐码点 insert，超出的截断
backspace(), delete(), left(), right(), home(), end(), clear()
selectAll(), hasSelection()           // 最小的"全选"：之后 insert/backspace/delete 先清掉全部文字；其它移动键取消全选
```

## 5. `SearchState`（客户端会话单例，`client/panel/search/`）

```text
open: boolean            // 搜索框是否显示（放大镜点击后 true，Esc/×/重置后 false）
focused: boolean         // 是否有键盘焦点
input: SearchTextInput
query: SearchQuery       // 随文字变化重新解析
slots: int[] | null      // 结果集（含留位）；null = 不过滤
reset()                  // closePanel / 登录登出 / 新会话时调用：全部清空
setText(...)             // 文字变化后：重新解析、compute、PanelSelection.clear()、滚动回到 0、同步到 FloatingPanelState
```

`FloatingPanelState` 新增一个 `searchSlots`（等于 `SearchState.slots`，由 `SearchState` 在变化时推过去），供 `layoutModel` 与 `clampScroll` 使用，避免 `FloatingPanelState` 依赖搜索包。

## 6. `PanelLayout` 的映射（D1）★

```text
PanelLayout.of(posX, posY, cols, rows, counts, scroll, int[] mainMap)   // mainMap 可为 null
  有 mainMap：slotCount(MAIN) = mainMap.length；
              slotIndex(MAIN, ordinal) = mainMap[(ordinal/cols + scroll)*cols + ordinal%cols]，越界 -1；
              mainTotalRows = max(1, ceil(len/cols))
  无 mainMap：与现在完全相同
```

## 7. `TitleBarLayout` ★ 与 `SearchBoxLayout` ★（D4）

```text
TitleBarLayout.of(int panelX, int panelWidth, boolean showClose, boolean searching) ->
   (closeLeft | none, searchLeft, hasSnapshot, snapshotLeft, textRightLimit, boxLeft, boxRight)
SearchBoxLayout.of(int boxLeft, int boxRight, int countTextWidth) ->
   (textLeft, textRight, countLeft | none, clearLeft)
```
