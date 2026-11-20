# Research: 按模组的服务端档位

方案取舍已与用户确认（spec Clarifications）。本文记 plan 阶段新查证的事实与由此做的设计决定。

## R1　6 个服务端入口与门禁的位置（逐个核对）

| 入口（`network/`） | 门禁调用 | 在状态改变之前？ |
|---|---|---|
| `BackpackQuickTransferPayload.backpackOut` | `allows(DELIVER_TO_CONTAINER, owner)`，在 `hasOpenContainer` 之后、逐个物品投递之前（058 的桥在它之后） | 是 |
| `BackpackTakeFromContainerPayload` | `allows(TAKE_FROM_CONTAINER, owner)`，在取出之前 | 是 |
| `MergePanelCursorPayload` | `allows(MERGE_CURSOR, owner)` | 是 |
| `BackpackCursorGesturePayload` | `screenOpen && menu != null && !allows(USE_MENU_CURSOR, owner)` | 是 |
| `BackpackClickPayload` | `viaMenu && !allows(USE_MENU_CURSOR, owner)`，在 `DiagLog.record` 之后、状态改变之前 | 是 |
| `BackpackTrashPayload`（`add`、`take` 两处） | `viaMenu && !allows(USE_MENU_CURSOR, owner)` | 是 |
| `BackpackTrashSendPayload` | `"minecraft".equals(owner) && allows(...)`：只对原版菜单工作，本来就拒绝其它模组的菜单 | 不需要改 |

共 7 处调用（6 个入口，`BackpackTrashPayload` 两处）。`MenuOwner.ownerOf` 在两侧都能用，菜单没有类型/没有注册名时给 `minecraft`（恒 FULL）。

## R2　服务端有没有按模组 id 的专用分支

`grep` 全部 `src/main/java`：服务端侧按模组 id 分支的只有 `compat/curios`、`compat/accessories`（背着来源）与 058 的 `compat/integratedterminals`（桥）。精妙背包、旅行者背包的字符串只出现在客户端的 `ContainerScreenCompat`（按类名判断）与 `CompatConfig` 的注释例子里，**服务端没有**。所以服务端 GENERIC 对它们与 FULL 无区别，OFF 才有作用（拒绝交换）。

## R3　`WornSource.active()` 在哪一侧被问

`active()` 无参。`BackpackSearch.firstWorn`/`activeSource`/`resolve` 在两侧都被用：服务端（`BackpackRef.resolve(player)`，决定 `B` 键与请求校验）和客户端（背部层画别人的背包；客户端自己 `B` 键前的 `resolve`）。要让服务端问服务端档位、客户端保持问客户端档位，必须把"是哪一侧"带进来。**决定**：在 `WornSource` 加默认方法 `active(P player)`（默认回 `active()`），`BackpackSearch` 全部改问它（`activeSource(int id)` 加玩家参数）；两个实现用 `entity.level().isClientSide()` 选侧。旧的无参 `active()` 保留（`ChestWornSource` 与现有测试里的假来源不用改）。

## R4　客户端档位在服务端的读取：不用判断专用服务器

`CompatConfig.policy()` 在配置没加载时捕获 `IllegalStateException` 返回 `CompatPolicy.DEFAULT`（全 FULL）。专用服务器上客户端配置不加载（来源注释已写），所以返回 DEFAULT；集成服务器（单人、局域网）同进程，配置已加载，返回玩家的真实设置。"取更严"时 DEFAULT 是中性元素，因此 `effective = stricter(serverPolicy.levelOf(m), CompatConfig.policy().levelOf(m))` 在两种环境里都对，不用任何 `isDedicatedServer` 判断。

## R5　服务端配置的热读

`ModConfigSpec.ConfigValue.get()` 读的是配置数据的当前副本；NeoForge 监视配置文件并在变化时重新加载（`ModConfigEvent.Reloading`），客户端的 `ConfigFileWatch` 正是靠这一点。所以改文件后下一次 `get()` 就是新值。**但这一点没有在服务端配置上亲眼验证过**，quickstart 里有一步专门验证（改文件、不重开世界、再操作）；若不成立，回来改成监听 `Reloading` 重建缓存，规格不变。缓存沿用客户端的"`get()` 返回的列表对象没变就不重新解析"。

## R6　限流键：用"界面编号"，不用时间

提示的限流要求"同一个玩家对同一次打开的界面至多一条"（FR-013）。服务端菜单有 `containerId`（每次打开递增，1～100 循环）。`DenialThrottle` 记每个玩家最近一次提示过的 `(containerId, modId)`；相同则不提示。再加两个清除点：`PlayerContainerEvent.Close`（界面关了，下次打开即使编号相同也要提示）与登出。**不用定时器**：行为确定、可测。一个玩家同时被两个不同的 OFF 模组拒绝只可能发生在两个不同的界面，所以只记一条也不会漏提示。

## R7　桥的第四个条件放哪里

`BridgeDispatch` 已有 `Supplier<CompatRuntimeState>`（版本门/强制/出错）。档位是另一类来源（配置、可变、与版本无关），放进 `CompatRuntimeState` 会让它依赖配置读取，破坏它的纯度。**决定**：`BridgeDispatch` 多一个 `Predicate<String> levelAllows` 构造参数，在 `state.isActive` 之后问；不满足就跳过这个桥，**不算失败**（不调用失败回调、不改状态）。

## R8　`ServerCompatGate` 保持静态纯逻辑

6 个入口与测试都用静态 `ServerCompatGate.allows(Action, owner)`。保持这个签名；内部多一个可替换的"档位来源"（`Function<String, CompatLevel>`，默认全 FULL）和一个纯的重载 `allows(Action, CompatLevel)`。启动时（模组构造器）把来源设成 `ServerCompatLevels::effective`，所以单元测试里门禁默认恒允许（现有 `ServerCompatGateTest` 不变），新测试用自己的来源。

## R9　没有选用的做法

| 做法 | 为什么不选 |
|---|---|
| 判断 `FMLEnvironment.dist` 决定读不读客户端配置 | 不需要：DEFAULT 是中性元素（R4） |
| 把档位放进 `CompatRuntimeState` | 破坏它的纯度（R7） |
| 用定时器限流提示 | 不确定、难测；用界面编号（R6） |
| 给 `WornSource.active()` 改签名为必带玩家 | 要改所有假来源与测试；默认方法更小（R3） |
| 服务端 OFF 时通知客户端隐藏面板 | 需要新包/协议，规格明确不做 |
