# Data Model: 第三方模组兼容开关与客户端配置

本 feature 不新增任何持久化数据（除 NeoForge 自己生成的客户端配置文件），不改数据组件，不改网络包。

## 配置项

| 项 | 值 |
|---|---|
| 位置 | `config/mcfloatingbackpack-client.toml`，类型 `CLIENT` |
| 键 | `modLevels` |
| 类型 | 字符串列表，默认空 |
| 元素 | `modid=LEVEL`；`modid` 为 `[a-z0-9_]+`，`LEVEL` 为 `FULL`/`GENERIC`/`OFF`（不区分大小写） |
| 校验器 | 只检查元素是字符串；其余由 `CompatPolicy` 逐条处理 |

## CompatLevel

`FULL`、`GENERIC`、`OFF`。`parse(String)`：去空白、不区分大小写；认不出返回 null。

## CompatPolicy（纯逻辑）

`CompatPolicy.parse(List<String>)` → `(policy, warnings)`。内部 `Map<modId, CompatLevel>`（只存 GENERIC 与 OFF；FULL 等于没有记录）。

| 查询 | 规则 |
|---|---|
| `levelOf(modId)` | `minecraft` 恒 FULL；没有记录 FULL；否则记录的档位 |
| `allowsInteraction(ownerModId)` | `levelOf(ownerModId) != OFF`（`ownerModId` 为 null/空时为真） |
| `specificAdaptation(modId)` | `levelOf(modId) == FULL` |

解析规则与警告见 research Decision 2。

### 三档的含义与行为矩阵（2026-11-05 澄清）

- **FULL**：专用适配——开启我们为该模组写的专有代码（主动兼容，可视为功能增强）。
- **GENERIC**：没有专用适配——关闭我们为该模组写的任何专有代码；没写过的模组与 FULL 一样。**不决定**面板能否显示、能否交换物品（取决于该模组自己的实现）。
- **OFF**：面板不显示；面板与该模组的界面二选一。

| 模组类型 | FULL | GENERIC | OFF |
|---|---|---|---|
| 拥有**容器界面**的模组（精妙背包、旅行者背包、别的模组的机器界面） | 面板挂上去（现状；没有专用适配） | 与 FULL 相同（没有专用适配可关）；Ctrl 转移、光标合并仍按通用逻辑，结果取决于该模组的实现 | 该界面按"其它界面"：面板隐藏、不拦截、不转移、不合并光标；热键在聊天区给提示 |
| 只**叠加**在别的界面上的模组（JEI） | JEI 避让面板区域 | 不避让 | 等同 GENERIC |
| 拥有**非容器界面**的模组（JourneyMap 全屏地图、EMI 配方页） | 面板自动隐藏（019，不可配置） | 同 | 同 |
| 原版 | 永远 FULL | 不可配置 | 不可配置 |

## ConfigNotices / ConfigFileWatch（2026-11-05 新增）

| 类 | 职责 |
|---|---|
| `CompatPolicy.Warning(Problem, entry)` | 一条配置条目的问题：`MALFORMED`、`VANILLA`、`UNKNOWN_LEVEL`、`DUPLICATE`；`describe()` 给日志（英文），`problem()` 给聊天提示选语言键 |
| `ConfigNotices`（纯逻辑） | 线程安全的队列；`Notice(key, arg)`；`drain()`：相同的只留一条、最多 5 条加一行"还有 N 条"；`freshBackups(文件名→修改时间, 现在, 窗口)`：找出 10 秒内刚生成的 `mcfloatingbackpack-client*.toml.bak` |
| `ConfigFileWatch`（与游戏相连） | 订阅 `ModConfigEvent.Loading/Reloading`；扫描配置目录，有刚生成的 `.bak` 就加一条"文件有错误，已备份为 X" |

## MenuOwner（与游戏相连）

`ownerOf(AbstractContainerMenu)` → 模组 id（字符串）：菜单类型在 `BuiltInRegistries.MENU` 里的键的命名空间；没有类型/未注册/取不到 → `minecraft`。

## CompatConfig（与游戏相连）

`SPEC`、`MOD_LEVELS`；`policy()`：读 `MOD_LEVELS.get()`，列表引用与上次相同则返回缓存；不同则 `CompatPolicy.parse` 并把每条警告写 WARN 日志；配置未加载 → 默认策略。

## ServerCompatGate（纯逻辑）

`Action { DELIVER_TO_CONTAINER, TAKE_FROM_CONTAINER, MERGE_CURSOR, USE_MENU_CURSOR }`；`allows(Action, ownerModId)` 现阶段恒为 `true`。

## 依赖规则检查器（测试源码里）

| 检查 | 输入 | 失败条件 |
|---|---|---|
| `checkModsToml(text)` | `neoforge.mods.toml` 文本 | 除 `neoforge`、`minecraft` 外有依赖块不是 `type="optional"` |
| `checkImports(files)` | `(相对路径, 源码)` 列表 | 白名单前缀之外的 import 出现在路径不含 `/compat/<名字>/` 的文件里 |
| `checkBuildGradle(text)` | `build.gradle` 文本 | 第三方坐标的依赖声明不是 `compileOnly`/`localRuntime` |

每个检查返回违规列表（文件/块 + 原因）；空列表为通过。
