# Contract: 协议、窗口、命令

协议号 **20 → 21**（`BackpackNetwork`：`registrar(...).versioned("21")`）。不新增载荷类型，只改下面三个。

## 载荷

### VersionRow（记录，在 `SnapshotCodecs.writeVersions/readVersions` 里编码）

`(long time, int slots, String ownerName, int capacity, byte kind, String toVersion)`

编码顺序：`varlong time`、`varint slots`、`utf ownerName(≤16)`、`varint capacity`、**`byte kind`（1/2/3）**、**`utf toVersion(≤32)`**。`kind` 不是 1..3 → `DecoderException`。`toVersion` 非升级前为空串。

### SnapshotVersionsRequestPayload（窗口 → 服务端）

`(UUID backpack, int page, int pageSize, byte kind)`
- 编码：`uuid`、`varint page`、`varint pageSize`、`byte kind`。
- 校验（`SnapshotRequestRules`）：`page` 1..`MAX_PAGE`（1000）；`pageSize` 1..`MAX_PAGE_SIZE`（16）；`kind` 0..3。不合法：不处理、不回应（同现有 `refuse`）。

### SnapshotVersionsPayload（服务端 → 窗口）

`(UUID backpack, byte kind, int page, int pages, int total, List<VersionRow> rows)`
- 解码时 `rows` 个数 ≤ `MAX_PAGE_SIZE`，`total` ≤ `MAX_VERSIONS`，否则 `DecoderException`。
- `kind` 是服务端实际用的筛选（回显）；`total` 是**筛选后**的总数；`pages ≥ 1`。
- 背包不存在或请求者看不到：`total = 0`、`rows` 空（同现有"像不存在一样"）。

### 不变

`SnapshotPreviewRequestPayload(backpack, time)`、`SnapshotActionPayload(kind, backpack, time, target, days)`、`SnapshotResultPayload(code, a, b, n1, n2)` 的**结构不变**。`RESTORED` 的 `n1` 新用作"无法恢复的格数"（原来没用）。

## 常量（`SnapshotRequestRules`）

`MAX_VERSIONS = 100`（原 16）；其余不变。新增 `validKind(byte)`、版本页大小用现有 `validPageSize`。

## 服务端（`SnapshotServer.onVersions`）

1. 校验；取该背包全部认得的快照（新到旧）；
2. 按 `kind` 筛选（0 = 全部）；
3. `pages = max(1, ceil(total / pageSize))`；`page > pages` → 回最后一页；
4. 取该页的行，类别与升级前的新版本来自 `SnapshotMeta`；
5. 发 `SnapshotVersionsPayload`。

预览、恢复、删除仍按 `time` 在服务端重新查找；找不到（已被清理）→ `NOT_FOUND`/`BAD_VERSION`（现有），**不作用于别的快照**。

## 窗口（客户端）

- 状态（`SnapshotScreenState`）：`versionPage`、`versionPages`、`versionTotal`、`versionKind`（0..3）；`applyVersions(payload)` 带上这些；换背包 → 回第 1 页、类别"全部"、选择清空。
- 版本区（`SnapshotScreenLayout`）：头部一行放"页码 + 上一页/下一页 + 类别按钮"（按钮宽度沿用列表区翻页条的做法，`half = max(12, min(20, w/4))`）；一页的行数 = 现有 `versionRows`（≤ 5），**页大小 = `versionRows`**（至少 1）。
- 类别按钮：点击循环 全部 → 定时 → 变更 → 升级前 → 全部；换类别 → 第 1 页、清选择、重新请求。
- 每行：`[类别短标记] <相对时间> <格数>`；升级前再追加 `→ <toVersion>`。
- 翻页/换类别/删除之后：重新请求当前页；预览清掉。
- 滚轮：在版本区上滚动翻页（同列表区现有的 `goToPage` 做法）。

## 命令（`SnapshotCommands`）

- `restore`、`delete` 的 `<版本>` 参数：`IntegerArgumentType.integer(1, SnapshotRequestRules.MAX_VERSIONS)`，语义不变（最新在前、按全部类别编号）。
- `info`：每份带类别短标记（`T` / `C` / `U`，升级前再带新版本）：见 [config-and-texts.md](./config-and-texts.md) 的键。
- `restore` 成功文字：`lostCells > 0` 时用带提示的键。
