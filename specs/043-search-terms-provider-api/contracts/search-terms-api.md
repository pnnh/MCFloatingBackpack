# 契约：搜索词提供者接口（API 版本 1）

这是本模组（模组 id `mcfloatingbackpack`）**第一个、目前唯一的公开接口**。别的模组用它向背包面板的搜索框增加"某个物品还可以被搜到的文字"（别的语言的名字、拼音等）。本模组**不读语言文件、不做拼音、不引用任何其它模组**；提供什么词、怎样产生，完全由提供者决定。

## 包与类型

包：`pnnh.mcfloatingbackpack.api`。公开签名只含 JDK 与 Minecraft 的类型；不引用客户端专用类，所以专用服务器上加载它也安全。

```java
@FunctionalInterface
public interface SearchTermsProvider {
    Collection<String> termsFor(ItemStack stack);
}

public final class BackpackSearchApi {
    public static int apiVersion();   // 本契约为 1
    public static void registerSearchTermsProvider(String modId, SearchTermsProvider provider);
}
```

## 依赖写法（调用方一侧）

- 编译期：`compileOnly` 本模组的 jar（不要 `implementation`、不要打包进自己的 jar）。
- `neoforge.mods.toml`：把 `mcfloatingbackpack` 声明为 **`type="optional"`** 的依赖。
- 运行时：先 `ModList.get().isLoaded("mcfloatingbackpack")`，**只在为真时**才加载引用本 API 的类（同类里不要有别处会提前触发的引用，否则缺本模组时会 `NoClassDefFoundError`）。
- 再检查 `BackpackSearchApi.apiVersion() >= 1`，不满足就不注册。（`apiVersion()` 是方法而不是常量，所以检查发生在运行时，不会被你的编译器内联。）

## 注册

`registerSearchTermsProvider(modId, provider)`

- `modId`：调用方自己的模组 id，须匹配 `[a-z0-9_]+`；玩家用它在本模组配置 `[compat] modLevels` 里开关你的提供者（见下）。不合法或 `provider` 为 null → `IllegalArgumentException`。
- 时机：模组构造阶段、`FMLClientSetupEvent`、或之后任意时刻；线程任意、线程安全；在专用服务器上调用也安全（不会有任何调用）。
- 同一 `modId` 再次注册 → 替换旧的（旧的缓存丢弃）。
- 注册后会记一条 INFO 日志（含你的模组 id）。

## 调用约定

| 项 | 约定 |
|---|---|
| 何时调用 | 只在**客户端渲染线程**；只在搜索框里有非空查询、需要判断某个格子时；空查询不调用 |
| 入参 | 一个**非空**的 `ItemStack`，**数量为 1**，是一份拷贝；请不要修改它，也不要依赖数量 |
| 同一物品 | 按"物品 + 数据组件（不含数量）"缓存：同一个物品、同样的组件，在一代缓存内最多调用一次；**资源重载**（切换语言、F3+T、换资源包）与**重新注册**时缓存清空 |
| 耗时 | 必须**很快**：不阻塞、不做 I/O、不访问世界、不加锁等待别的线程。建议直接从自己预先建好的表里取 |
| 返回值 | 一组字符串，`null` 或空表示没有额外的词 |

## 返回值的规则（本模组会做的归一与防御）

- 每个词统一 `toLowerCase(Locale.ROOT)` 后与查询做**子串包含**比较（查询也已转小写）。所以不必自己转小写，也不必产出大小写变体。
- 丢弃 `null`、空串与纯空白串；重复的词合并。
- **单个词最长 128 字符**：更长的整词丢弃（不截断），并记一条 WARN（每个提供者每类一次）。
- **一次最多 64 个词**：多出的丢弃，并记一条 WARN（同上）。
- 查询按空格拆成多个词分别匹配（空格 = 并且），所以**含空格的词只能被整段 `"…"` 查询命中**；建议不返回含空格的词。
- 提供者**只增加**可被搜到的文字：不能改变显示名、结果顺序、语法含义（`@`、`#`、`/` 的含义归本模组）或高亮方式。

## 容错

- 提供者抛异常：该物品没有额外词，搜索继续；同一提供者的**同一种异常只记一条 WARN**（含你的模组 id 与异常类型）。
- 同一提供者累计**20 次**异常后，本会话起被停用并再记一条 WARN；重启游戏后恢复。
- 任何提供者的故障都不会使搜索崩溃，也不影响本模组自己的匹配与别的提供者。

## 玩家的控制

玩家在客户端配置 `config/mcfloatingbackpack-client.toml` 的 `[compat] modLevels` 里用你的模组 id 控制：

| 级别 | 对提供者的含义 |
|---|---|
| 未列出 / `FULL` | 提供者生效 |
| `GENERIC` | 提供者**不被调用**，它的词不生效 |
| `OFF` | 与 `GENERIC` 相同（本接口没有"在你的界面上显示面板"的含义） |

配置改动不需要重启，从下一次搜索起生效。

## 稳定性承诺

- **API 版本 1** 的类型、方法签名与语义**不会改变**。
- 新能力以**新增方法**或**新的版本号**提供；不移除已发布的版本；破坏性变更需要新的包版本与迁移说明。
- 本契约里的数值（上限、次数）属于版本 1：放宽可以（不破坏调用方），收紧要升版本。

## 最小示例

```java
// 在只有 mcfloatingbackpack 存在时才会被加载的类里：
static void register() {
    if (BackpackSearchApi.apiVersion() < 1) {
        return;
    }
    BackpackSearchApi.registerSearchTermsProvider("yourmodid", stack -> {
        // 从自己预先建好的表里按物品取词；没有就返回空
        return MyTable.termsOf(stack.getItem());
    });
}
```

## 数值（供测试对照，与代码常量保持一致）

| 名称 | 值 |
|---|---|
| MAX_TERMS | 64 |
| MAX_TERM_LENGTH | 128 |
| MAX_ERRORS | 20 |
| CACHE_SIZE | 2048 |
