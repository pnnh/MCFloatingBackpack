# Data Model: 搜索词提供者接口

全部在内存，不进存档、配置或网络包。通用类无 Minecraft 类型（泛型 `S`），生产里 `S = ItemStack`。

## SearchTermsProvider（公开）

```java
@FunctionalInterface
public interface SearchTermsProvider {
    /** 这个物品还可以被搜到的文字；null 或空表示没有。 */
    Collection<String> termsFor(ItemStack stack);
}
```

## BackpackSearchApi（公开）

```java
public final class BackpackSearchApi {
    public static int apiVersion();                                  // 1
    public static void registerSearchTermsProvider(String modId, SearchTermsProvider provider);
}
```

校验：`modId` 须匹配 `[a-z0-9_]+`（与 `CompatPolicy` 的 id 规则一致，才能被 `modLevels` 指到）；`provider` 非 null；否则 `IllegalArgumentException`。

## ProviderRegistry<S>（内部，通用）

| 成员 | 说明 |
|---|---|
| `entries: ConcurrentHashMap<String, Entry<S>>` | 模组 id → entry；同 id 再注册 = 新建 entry 替换（旧缓存随之丢弃） |
| `keyOf: Function<S,Object>` | 缓存键（生产：`StackKey`） |
| `prepare: UnaryOperator<S>` | 交给提供者前的准备（生产：`copyWithCount(1)`） |
| `enabled: Predicate<String>` | 模组 id 是否生效（生产：`CompatConfig.policy().specificAdaptation(id)`） |
| `sink: WarnSink` | `warn(String)`、`debug(String)`（生产：`MCFloatingBackpack.LOGGER`） |

方法：`register(modId, fn)`、`hasActive()`、`termsFor(S): List<String>`（合并去重）、`clearCaches()`。

## Entry<S>

| 字段 | 说明 |
|---|---|
| `modId` | 注册时的模组 id |
| `fn` | `Function<S, ? extends Collection<String>>` |
| `cache` | `TermsCache<Object, List<String>>`（LRU，上限 `CACHE_SIZE`），键是 `keyOf(stack)` |
| `health` | `ProviderHealth` |

## TermsCache<K,V>

有上限的 LRU（`LinkedHashMap` 访问序，超限淘汰最久未用）；`get`、`put`、`clear`、`size`。非线程安全，由 entry 同步。

## ProviderHealth

| 字段 | 说明 |
|---|---|
| `errors: int` | 累计异常次数 |
| `warnedClasses: Set<Class<?>>` | 已 WARN 过的异常类（同类只记一次） |
| `warnedViolations: Set<Violation>` | 已 WARN 过的违规类别（`TOO_MANY`、`TOO_LONG`） |
| `disabled: boolean` | `errors >= MAX_ERRORS` 后为 true，本会话不再恢复 |

## TermsNormalizer

`normalize(Collection<String> raw) → Result(List<String> terms, boolean tooMany, boolean tooLong)`：null → 空；丢弃 null/空白；`toLowerCase(Locale.ROOT)`；整词超过 `MAX_TERM_LENGTH` 的丢弃并标 `tooLong`；超过 `MAX_TERMS` 个只留前 `MAX_TERMS` 个并标 `tooMany`；去重并保持顺序。

## SearchTermsLimits（常量，与契约一致）

| 常量 | 值 |
|---|---|
| `MAX_TERMS` | 64 |
| `MAX_TERM_LENGTH` | 128 |
| `MAX_ERRORS` | 20 |
| `CACHE_SIZE` | 2048 |

## StackKey

`record StackKey(Item item, DataComponentPatch patch)`；`of(ItemStack)`：补丁为空时用只含物品的键（`patch = DataComponentPatch.EMPTY`）。

## 状态与生命周期

- **注册**：任意时刻；新 entry 缓存为空、健康为全新。
- **取词**：`hasActive()` 为假 → 调用方走 042 路径；真 → 对每个 `enabled(modId) && !disabled` 的 entry：查缓存 → 未命中则 `fn.apply(prepare(stack))`，try/catch，归一，存缓存。
- **出错**：`recordError(异常类)`（首次该类 WARN）；达到 `MAX_ERRORS` → `disabled=true` + WARN，之后 `hasActive()` 不再计入该 entry。
- **资源重载**：`clearCaches()` 清全部 entry 的缓存（健康记录保留）。
- **配置变化**：无动作；下一次 `enabled(modId)` 自然反映。
