# 契约：网络（协议号 22 → 23）

协议号常量在 `BackpackNetwork`（`registrar.versioned("22")` → `"23"`）；两个新包都注册在同一 registrar 上（**必需**，不是可选包：只装了一边的客户端与服务端不应进同一个世界）。版本检查的两个可选包（`versioned("1").optional()`）不动。

## `CombinedSelectPayload`（C → S）

| 字段 | 类型 | 说明 |
|---|---|---|
| `op` | byte | `0 = OPEN`（U 键，打开联合界面，目标取默认）；`1 = SELECT`（点标签） |
| `index` | varint | SELECT：成员下标（0..15）；OPEN：忽略（写 0） |
| `identity` | boolean + 16 字节 UUID（可无） | SELECT：客户端看到的该成员身份（无身份则 false）；OPEN：忽略 |

解码规则：`op` 不是 0/1、`index` 不在 0..15 → `DecoderException`。

**服务端处理**（`handleServer`，主线程）：

OPEN：
1. 不启用（`[combined].enabled=false`）→ 动作栏 `message.…combined.disabled`，`sendSession` 不变，返回。
2. 没穿联合背包（`CombinedWorn.pick` 为空）→ `message.…combined.need_worn`，返回。
3. 算成员表；为空 → `message.…combined.no_members`，返回。
4. 默认目标：B 会打开的那只（`BackpackRef.resolve` 归一化）若在表里取它，否则第一个成员。
5. `BackpackGate.acquire` 目标（给身份；失败则取下一个成员，全部失败则提示并返回）→ 建 `CombinedSession` → 先发 `CombinedMembersPayload(active=true, …)`，再 `BackpackNetwork.sendSession(player, true, target)`。

SELECT：
1. 没有联合会话、或联合背包不再穿戴/已关闭 → 发 `CombinedMembersPayload(active=false)`，返回。
2. 光标非空（面板光标或菜单携带物）→ 拒绝：动作栏 `message.…combined.empty_cursor`，**重发当前** `CombinedMembersPayload` 让客户端退出"加载中"，返回。
3. 重算成员表；`index` 越界 → 重发成员表，返回。
4. 校验身份：成员的身份 = `identity`，或该成员当前无身份且客户端也说无 → 通过；否则重发成员表，返回。
5. `acquire` 目标（无身份则此时赋予）→ 更新会话目标与身份 → 发 `CombinedMembersPayload`（`selected = index`）→ `sendSession(player, true, target)`。

**关闭**：沿用 `BackpackOpenRequestPayload.close()` → `sendSession(false)` → `BackpackGate.noteClosed` 同时 `CombinedSessions.end`。不新增关闭包。

## `CombinedMembersPayload`（S → C）

| 字段 | 类型 | 说明 |
|---|---|---|
| `active` | boolean | `false`：退出联合模式（会话结束、关闭、联合背包没了），其余字段忽略 |
| `combinedTier` | byte | 联合背包档位序号 |
| `selected` | varint | 当前目标在成员表里的下标，无则 -1 |
| `skippedTier` / `skippedNewer` / `skippedOverCap` | varint ×3 | 三类未连接计数 |
| `members` | list（≤ 16） | 每项：`sourceOrdinal`(varint)、`invIndex`(varint)、`hasIdentity`(boolean)+UUID、`tier`(byte)、`customName`(`writeUtf`, ≤ 64) |

**发送时机**：OPEN/SELECT 的处理里；每秒钩子里成员表签名变化时；目标因挪动被找回/被重置时（`selected` 变）；`active=false` 在会话结束时。**不会每帧、不会每个请求发。**

**客户端处理**：`active=false` → `ClientCombinedState.clear()`；否则存下表与 `selected`，并设 `loading = true` **直到**随后的 `BackpackSessionPayload`（`open=true`）到达且其 `(sourceOrdinal, invIndex)` 等于 `members[selected].ref` 才置 `loading = false`。若 `selected` 没变且是每秒推送，则不进入 loading。

**顺序**：服务端永远**先发 Members 再发 Session**；同一连接按序到达。

## 既有包的变化

- `BackpackClickPayload`：线上格式**不变**。服务端在联合会话里：`claimed != PanelTargets 目标` → 拒绝 + `resync`（不再静默改用解析结果）；单背包模式保持旧行为。
- 其它面板类包：线上格式不变。

## 版本兼容

旧客户端连新服务端、反之：协议号不同，NeoForge 在握手时拒绝（与以往每次升协议号相同）。README 写明"协议号 22 → 23，两端都要更新"。
