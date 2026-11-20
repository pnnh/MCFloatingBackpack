# Data Model: 按模组的服务端档位

无持久化数据（不改背包、快照、世界标记；`DataRevision` 6，协议 21）。运行时类型：

## `EffectiveLevel`（纯）

- `stricter(CompatLevel a, CompatLevel b)`：`FULL < GENERIC < OFF`，返回较严者（null 当 FULL）。
- `effective(CompatPolicy server, CompatPolicy client, String modId)`：`stricter(server.levelOf(modId), client.levelOf(modId))`；原版（`minecraft`、空串、null）恒 FULL（`CompatPolicy.levelOf` 已保证）。

## `ServerCompatGate`（纯，改）

- `allows(Action action, CompatLevel level)`：`level == OFF → false`，其余 `true`（四种动作相同；表写成测试）。
- `allows(Action action, String ownerModId)`：`allows(action, levels.apply(ownerModId))`；`levels` 默认全 FULL，`setLevelSource(Function<String, CompatLevel>)` 替换。

## `DenialThrottle`（纯）

每个玩家（UUID）一条记录 `(containerId, modId)`。

- `boolean shouldNotify(UUID player, int containerId, String modId)`：记录与参数相同 → false；否则写入并返回 true。
- `void forget(UUID player)`：清掉记录（界面关闭、登出）。线程安全（`ConcurrentHashMap`）。

## `ServerCompatLevels`（MC）

- 服务端 `modLevels` 的当前 `CompatPolicy`（缓存，原列表对象不变不重解析；解析的错误项 WARN 一次）。
- `effective(modId)`、`specificAdaptation(modId)`（有效档位 == FULL）。配置未载入：全 FULL。

## `ServerCompatDenials`（MC）

- `allows(ServerPlayer player, AbstractContainerMenu menu, Action action)`：`owner = MenuOwner.ownerOf(menu)`；`ServerCompatGate.allows(action, owner)` 为真 → true；否则 `DenialThrottle.shouldNotify(...)` 为真时给玩家发 `compat.server_off`（参数：模组 id），DEBUG 日志一条（玩家、模组、动作），返回 false。

## `BridgeDispatch`（058，改）

构造参数多 `Predicate<String> levelAllows`。`tryInsert` 对每个桥：`state.isActive(id)` 与 `levelAllows.test(id)` 都为真才继续；档位不允许时跳过，**不是失败**。

## `WornSource.active(P player)`（改）

`default boolean active(P player) { return active(); }`。`CuriosWornSource`/`AccessoriesWornSource` 覆盖：实体在客户端侧 → `CompatConfig.policy().specificAdaptation(id)`（原样）；服务端侧 → `ServerCompatLevels.specificAdaptation(id)`。
