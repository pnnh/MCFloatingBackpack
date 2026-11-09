# Contract: 网络（协议 15 → 16）

所有 payload 在 `BackpackNetwork.onRegisterPayloads` 里注册；`registrar.versioned("16")`。字段用 `StreamCodec`，数值用 VarInt/VarLong，字符串带长度上限（读取时超限抛 `DecoderException`，与已有 payload 一致）。

## 客户端 → 服务端：请求（服务端处理，主线程）

每个请求的**第一步**是：`SnapshotService.get()` 为空 → 回 `SnapshotResultPayload(UNAVAILABLE)`；然后 `flushAndWait()`；然后按 `player.hasPermissions(2)` 算出 `op`。

| payload | 字段 | 校验（失败 = 零变化，不回包，记调试日志） | 服务端行为 |
|---|---|---|---|
| `SnapshotListRequestPayload` | `scope: byte`, `owner: String(≤16)`, `inactiveDays: VarInt`, `page: VarInt`, `pageSize: VarInt` | `scope∈{0,1}`；`page∈[1,1000]`；`pageSize∈[1,16]`（客户端按窗口能放下几行来要，实现时从"固定 8 行"改成这样）；`inactiveDays∈[-1,36500]`；`owner` 匹配 `[A-Za-z0-9_]*` | 非 op：忽略 `scope/owner/inactiveDays`，当作 `MINE`、无过滤；回 `SnapshotListPayload` |
| `SnapshotVersionsRequestPayload` | `backpack: UUID` | — | 不可见（非 op 且不是自己的）→ 回空版本列表（与"不存在"同）；否则回 `SnapshotVersionsPayload` |
| `SnapshotPreviewRequestPayload` | `backpack: UUID`, `time: VarLong` | `time>0` | 不可见/没有这一份 → `SnapshotPreviewPayload` 失败（`NOT_FOUND`）；读不了 → `UNREADABLE`；编码超过 900,000 字节 → `TOO_LARGE` |
| `SnapshotActionPayload` | `kind: byte`, `backpack: UUID`, `time: VarLong`, `target: String(≤16)`, `days: VarInt`, | `kind` 是已知值；`days∈[0,36500]`；`target` 匹配 `[A-Za-z0-9_]*` | `!op` → `SnapshotResultPayload(NO_PERMISSION)`；其余见下 |

动作细则（都在服务端按快照文件当前状态重新判断，**不信任客户端的列表**）：

- `RESTORE(backpack, time, target)`：`target` 空 = 请求者自己；按名字找在线玩家，没有 → `TARGET_OFFLINE`；该背包没有 `time` 那一份 → `NOT_FOUND`/`BAD_VERSION`；档位对不上 → `NO_TIER`；读不了 → `UNREADABLE`；成功 → 给目标一只**无标识的新背包**（`placeItemBackInInventory`）→ `RESTORED(id, targetName)`。**不改任何已有背包与快照。**
- `DELETE_ONE(backpack, time)`：删这一份；成功后 `forget(uuid)` → `DELETED_ONE`。
- `DELETE_ALL(backpack)`：删这只背包全部快照；`forget` → `DELETED_ALL(count)`。
- `TRIM_PREVIEW(days)`：算出候选（`SnapshotListing.trimCandidates`）但**不删** → `TRIM_PREVIEWED(backpacks, files)`。
- `TRIM_CONFIRM(days)`：服务端**重新计算**候选再删（不信任预览时的数字）→ `TRIM_DONE(files, backpacks)`。

## 服务端 → 客户端

| payload | 字段 |
|---|---|
| `SnapshotListPayload` | `isOp: bool`, `scope: byte`, `page`, `pages`, `total`, `rows: List<Row>(≤ 16)` |
| `SnapshotVersionsPayload` | `backpack: UUID`, `versions: List<VersionRow>(≤ 16)`（最新的在前） |
| `SnapshotPreviewPayload` | `backpack: UUID`, `time`, `ok: bool`；`ok` 时 `capacity`, `slots`, `items: List<ItemStack>(≤ 108, OPTIONAL_STREAM_CODEC)`；否则 `reason: ResultCode` |
| `SnapshotResultPayload` | `code: ResultCode`, `a: String`, `b: String`, `n1: VarInt`, `n2: VarInt`（含义随码，见下） |
| `SnapshotOpenPayload` | 空；客户端在主线程打开 `SnapshotScreen`（已打开则忽略） |

`SnapshotResultPayload` 的参数：`RESTORED`: a=背包编号, b=目标名；`DELETED_ONE`: a=编号, n1=序号；`DELETED_ALL`: a=编号, n1=份数；`TRIM_PREVIEWED`: n1=背包数, n2=文件数, 另外客户端记下天数；`TRIM_DONE`: n1=文件数, n2=背包数；其余码无参数。

## 客户端行为

- 响应只在 `SnapshotScreen` 打开时处理；界面已关闭时到达的响应被丢弃。
- 客户端**从不**自己判断权限，只用 `SnapshotListPayload.isOp`（以及任何响应都可能随之更新它）来决定显示哪些控件。
- 请求的发送由界面状态机触发：打开、翻页、切换范围、过滤变更、选中背包、选中版本、动作之后（动作成功后自动重新请求列表与版本）。
