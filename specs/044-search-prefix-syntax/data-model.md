# Data Model: 搜索前缀语法

全部在客户端内存里，不进背包数据、配置或网络包。除"客户端缓存"外都是不依赖 Minecraft 类的纯逻辑。

## SearchQuery（扩展 042）

```java
public final class SearchQuery {
    public enum Kind { WORD, MOD, TAG }

    /** 一个备选：种类、是否排除、小写文字。 */
    public record Alternative(Kind kind, boolean negated, String text) {
        public Alternative(boolean negated, String text) { this(Kind.WORD, negated, text); }   // 042 的写法
    }
    public record Term(List<Alternative> alternatives) { }

    /** 整条查询最开头的指令。 */
    public record Command(Type type, String raw) {
        public enum Type { NONE, HIDDEN, UNKNOWN }
        public static final Command NONE = new Command(Type.NONE, "");
    }

    List<Term> terms();
    Command command();
    boolean isEmpty();      // 没有 terms 且没有指令
}
```

- 042 的所有不变式照旧：文字已转小写（`Locale.ROOT`）；空备选、空项被丢弃；整条最多 100 个码点。
- `Command.raw`：用户输入的指令词原文（`/foo`），用于"未知指令"的提示（显示时按输入的写法，不转小写）。
- 指令查询的 `terms()` 是指令之后的那部分；`/hidden` 的 `terms()` 只用来过滤类别行。

### 解析规则（增量）

| 情形 | 结果 |
|---|---|
| `@minecraft` | 备选 `(MOD, +, "minecraft")` |
| `#logs` | 备选 `(TAG, +, "logs")` |
| `-@mod`、`-#tag` | 排除的 MOD / TAG 备选 |
| `@`、`#`、`-@`、`-#` | 文字为空，丢弃 |
| `@"a b"` | `(MOD, +, "a b")`（前缀之后的引号是整段） |
| `"@x"`、`a@b`、`a#b` | 普通词（引号内 / 不在备选开头） |
| `@a\|#b` | 一项两个备选：`(MOD,"a")`、`(TAG,"b")` |
| `/hidden`、`  /hidden`、`/HIDDEN` | `Command.HIDDEN`，`terms` 为空 |
| `/hidden fish` | `Command.HIDDEN`，`terms = [[WORD fish]]` |
| `/`、`/h`、`/foo`、`/hiddenx` | `Command.UNKNOWN`（`raw` 为 `/`、`/h`、`/foo`、`/hiddenx`） |
| `a /hidden`、`"/hidden"`、`a/b` | 没有指令，`/` 是普通字符 |

## TextMatcher（扩展）

```java
@FunctionalInterface
public interface TextMatcher {
    boolean contains(String needleLowerCase);
    default boolean inMod(String needleLowerCase) { return false; }
    default boolean hasTag(String needleLowerCase) { return false; }
    static TextMatcher ofTexts(List<String> lowerCaseTexts) { ... }   // 不变
}
```

`SearchMatcher.holds`：`WORD → contains`、`MOD → inMod`、`TAG → hasTag`；`negated ? !hit : hit` 不变。

## ModMatch / TagMatch（纯函数）

| 函数 | 规则 |
|---|---|
| `ModMatch.matches(needle, namespace, modNameLower)` | `namespace.contains(needle) \|\| modNameLower.contains(needle)` |
| `TagMatch.itemTag(needle, List<String> tagIdsLower)` | needle 含 `:` → 任一 id `startsWith(needle)`；否则任一 id 的 `:` 之后的路径 `contains(needle)` |
| `TagMatch.backpackTag(needle, List<Entry(id, labelLower)>)` | needle 含 `:` → false；否则 id 或 label `contains(needle)` |
| `hasTag(needle)`（客户端组合） | `itemTag(...) \|\| backpackTag(...)` |

`needle` 都是已小写的；标签 id 与 label 在缓存里已小写。

## ItemFacts（客户端缓存）

| 缓存 | 键 | 值 | 失效 |
|---|---|---|---|
| 物品属性 | `Item` | `Facts(namespace, modNameLower, List<String> tagIdsLower)` | `TagsUpdatedEvent`、资源重载 |
| 背包标签表 | `ClientBackpackView.tags()` 的对象身份 | `Map<ResourceLocation, List<Entry(id, labelLower)>>`（按物品懒填） | `ItemTags` 换对象、资源重载 |

## HiddenSummaryRows / HiddenSummaryFit

```java
public final class HiddenSummaryRows {
    public record Line(String categoryId, String displayName, int cells) { }   // displayName 为当前语言显示名
    public static List<Line> visible(List<Line> lines, SearchQuery rest);       // 复用 SearchMatcher，@/# 备选自然为假
    public static int totalCells(List<Line> visible);
}

public final class HiddenSummaryFit {
    public record Plan(int categoryLines, boolean showTitle, boolean showHint, boolean ellipsis) { }
    public static Plan fit(int categoryLinesNeeded, int titleLines, int hintLines, int availableLines);
}
```

`fit` 的优先顺序：类别行 > 标题 > 说明；类别行放不下就只显示前几行并 `ellipsis`；标题与说明只在剩余行数够时显示。

## SearchState（增量）

| 字段 / 方法 | 说明 |
|---|---|
| `Source.hiddenLines()` | `default` 返回空表；生产实现从 037 的数据取（类别、当前语言显示名、格数） |
| `command()` | 当前查询的指令类型 |
| `liveCount(source)` | `HIDDEN`：`totalCells(visible(source.hiddenLines(), query))`；`UNKNOWN`：0；否则 042 的规则 |
| 结果列表 | 指令查询：`new int[0]`（主区域没有格子）；`isFiltering()` 为真 |

## 状态转移

- 输入变化 → `textChanged`（042）：解析（含指令）→ 重算结果（指令查询为空数组）→ 清选区 → 回滚动。
- 进入/退出 `/hidden`、`/xxx`：都是"文字变化"，没有额外状态；Esc/`×` 清空即恢复完整显示。
- `TagsUpdatedEvent`、资源重载：只清客户端缓存；已显示的结果保持到下一次查询变化（042 的"结果只在查询变化时重算"）。
