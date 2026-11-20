# Data Model: 第三方模组版本管理与集成终端主动兼容

本 feature **不新增任何持久化数据**：不改背包存储（`DataRevision` 6）、不改快照格式、不新增网络包（协议号 21）。下面的类型都是运行时的、纯逻辑的，除配置文件里多一个布尔项外不落盘。

## 实体

### `ThirdPartyVersion`（纯）

解析后的版本：`long[]` 数字段。

- `parse(String) → Optional<ThirdPartyVersion>`：规则见 [contracts/version-table.md](./contracts/version-table.md)；没有数字或空/null → 空。
- `compareTo`：逐段比较，缺的段补 0。

### `VersionRange`（纯）

`record VersionRange(ThirdPartyVersion minInclusive, ThirdPartyVersion maxExclusive)`

- 不变式：`min < max`（构造时检查，违反抛 `IllegalArgumentException`）。
- `contains(v)`：`min ≤ v < max`。
- `toString()`：`[1.11.0, 1.12.0)`（每段至少三位数字补零显示；README 对齐测试用它）。

### `Requirement`（纯）

`record Requirement(String modId, String displayName, List<VersionRange> supported)`：同时要求的另一个模组及它的版本段。

### `ThirdPartyCompat`（纯，表的一行）

`record ThirdPartyCompat(String modId, String displayName, List<VersionRange> supported, List<Requirement> also, String variant)`

- 不变式（`ThirdPartyTable` 的完整性测试检查）：`supported` 非空、按下限升序、互不重叠（前一段的上限 ≤ 后一段的下限）；`also` 里每个 `Requirement` 同样；`modId` 在表里唯一；`variant` 本期恒为 `null`（预留，FR-003）。

### `VersionVerdict`（纯，枚举）

`IN_RANGE`、`BELOW_ALL`、`ABOVE_ALL`、`IN_GAP`、`UNKNOWN`。

### `CompatFinding`（纯）

一个行一次检查的结论。

| 字段 | 含义 |
|---|---|
| `entry` | 表里的行 |
| `verdict` | `VersionVerdict`；`IN_RANGE` 表示整行通过 |
| `subjectModId` / `subjectName` | 判不过的是哪个模组（自己或某个要求的模组）；通过时为 `entry` 自己 |
| `installedVersion` | `subject` 的已装版本原文（日志与聊天里原样显示） |
| `ranges` | `subject` 的全部受支持版本段（用于文字） |

### `CompatStatus`（纯，枚举）

| 值 | 含义 | `isActive` |
|---|---|---|
| `ACTIVE` | 已装且在范围内 | 是 |
| `OUT_OF_RANGE` | 已装、范围外、没有被配置强制启用 | 否 |
| `FORCED` | 已装、范围外、`allowUntestedVersions=true` | 是 |
| `FAILED` | 本次运行里适配器出错被关闭 | 否 |

没装的行**不在**状态表里（`status(modId)` 返回空）。

### `CompatRuntimeState`（纯）

一次服务器运行里的状态表。线程安全（`ConcurrentHashMap`），因为出错可能在服务端主线程以外的路径被报告（实际上递送在主线程，这只是防御）。

- `static CompatRuntimeState evaluate(List<ThirdPartyCompat> table, Function<String, Optional<String>> installedVersion, boolean allowUntested)`：对每行：自己没装 → 跳过；有要求的模组没装 → 跳过（R6）；否则 `VersionChecker` 得 `CompatFinding`；`IN_RANGE` → `ACTIVE`；否则 `allowUntested ? FORCED : OUT_OF_RANGE`，同时记下 `finding`。
- `isActive(modId)`：状态为 `ACTIVE` 或 `FORCED`。
- `fail(modId) → boolean`：状态为 `ACTIVE`/`FORCED` 时置 `FAILED` 并返回 true（第一次）；已是 `FAILED` 或无此行返回 false。
- `findingsOutOfRange()`：状态为 `OUT_OF_RANGE` 或 `FORCED` 的 `CompatFinding`（保持表顺序）。`FAILED` 的行不在内。

状态转移：

```
(没装) ──────────────────────────────▶ 不在表中
已装 ──在范围内──▶ ACTIVE ──fail──▶ FAILED
    └─范围外─┬─allow=false─▶ OUT_OF_RANGE
              └─allow=true──▶ FORCED ──fail──▶ FAILED
```

`FAILED` 只在本次运行有效；下次服务器启动重新 `evaluate`。

### `BridgeDispatch<P, M, S>`（纯，泛型）

- `interface Bridge<P, M, S> { String modId(); boolean handles(M menu); S insert(P player, M menu, S stack); }`
- `Optional<S> tryInsert(P player, M menu, S stack)`：见 [contracts/bridge.md](./contracts/bridge.md)。构造参数：桥列表、`CompatRuntimeState`、`QuickTransferLogic.StackOps<S>`、失败回调（`(modId, Throwable)`，MC 层用它写 ERROR 与发聊天）。

### `NetworkStorageBridge`（MC 层接口）

`Bridge<ServerPlayer, AbstractContainerMenu, ItemStack>` 的别名接口；唯一实现是 `IntegratedTerminalsBridge`。`NetworkStorageBridges` 持有注册表（`CopyOnWriteArrayList`）与一个 `BridgeDispatch` 实例。

## 配置（服务端，`serverconfig/mcfloatingbackpack-server.toml`）

```toml
[compat]
    # 注释见 contracts/config-and-texts.md
    allowUntestedVersions = false
```

## 与现有数据的关系

- 不读、不写背包、快照、世界标记。
- `MenuOwner.ownerOf(menu)` 返回菜单类型的命名空间（集成终端是 `integratedterminals`），与桥的 `modId()` 相同；`ServerCompatGate.allows` 用它，桥的状态用同一个 id。
