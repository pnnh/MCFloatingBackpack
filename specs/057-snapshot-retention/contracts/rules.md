# Contract: 保留、淘汰、定时、版本变化、提示

## 1. 写快照（两条路径）

**变更路径**（现有防抖）：`SnapshotScheduler.due`/`drainWhere`/`drainAll` 取出的内容 → 编码 → 写线程 `writeOne(meta(kind=CHANGE), tag)`。

**定时路径**（新）：`SnapshotService.tick()`（约每秒）里 `timed.due(now)` 取出的脏背包 → 编码 → `writeOne(meta(kind=TIMED), tag)`。

**两条路径共用的 `writeOne`**：
1. 哈希 `tag.hashCode()`；与 `lastHash[(uuid, kind)]` 相同 → 更新哈希、返回（不写）。`lastHash` 没有记录时，从磁盘读该背包**该类别**最新一份的 `contents` 算哈希。
2. `store.write(meta, tag)`：写文件、对该背包**该类别**做保留清理（定时 60 / 变更 20 / 升级前 5）。
3. 更新 `lastHash`、`totalBytes`；然后检查容量。

`mark`（有变化）同时喂 `scheduler`（变更防抖）和 `timed`（定时窗口）。`noteUse` 的"首次使用基线"仍只走变更路径。

## 2. 保留清理

`SnapshotRetention.toDelete(List<Entry(time, kind)>, SnapshotKind kind, int keep)`：取该类别的条目，按 `time` 降序，保留前 `keep` 个，其余返回。**只在写完该类别的一份后对该背包调用一次**。输入只含"认得且版本已知"的文件。

## 3. 容量淘汰

触发：写完一份后 `totalBytes > maxBytes`（开关开且 `maxTotalMb` 已夹取）。
`SnapshotCapacity.victims(files, maxBytes)`（纯）：
1. `over = totalBytes - maxBytes`；
2. 候选 = 非受保护文件（受保护 = `UPGRADE`，或该背包文件夹里 `time` 最大的那一份）；
3. 排序：`TIMED` 先于 `CHANGE`；同类 `time` 升序（最旧先）；
4. 逐个累计 `bytes` 直到 ≥ `over`；
5. 返回 `(victims, stillOver)`；`stillOver` 为真表示候选用尽仍超限。
执行：对每个 victim 先读头确认版本已知（未知跳过、不计入释放量），再删；`stillOver` 时 `Throttle`（每小时一次）记 WARN。

## 4. 版本变化（启动时，`SnapshotService.start`）

1. `settings = SnapshotSettings.from(...)`；读 `state`（R8）。
2. `current = ModVersion.current()`（空则当 `"unknown"`）。
3. 若 `state` 缺失/损坏 → 写 `state(current, changeId=1, ...)`，结束。
4. 若 `state.lastModVersion.strip().equals(current.strip())` → 什么都不做（名单与编号不变）。
5. 否则：`newId = state.changeId + 1`；
   - 若 `settings.enabled()`：对每只背包（`store.backpacks()`）取认得的快照列表 → `UpgradePlan` → `store.pin(...)`；`pinned = 成功钉住或更新的背包数`；
   - 否则 `pinned = 0`；
   - 写 `state(current, newId, from = 旧版本, pinned, notified = ∅)`；
   - 创建 `SnapshotNotices.Pending(newId, from, current, pinned, direction)`。

## 5. 提示

- **升级/降级提示**：`PlayerLoggedInEvent` → `SnapshotNotices.onLogin(player)`：`Pending` 存在且 `player.uuid ∉ state.notified` → 发消息 → 名单加入 → 写 `state`（主线程）。`Pending` 只在本次运行里有；重启之后同一个 `changeId` 没有 `Pending`，但名单已在文件里——**因此重启后未被提示的玩家也要补发**：启动时若 `state.changeId > 0` 且当前运行没有新的版本变化，则也创建 `Pending(changeId, from_version, current, pinned, …)`（名单里已有的玩家不再提示）。（`changeId` 为 0 或没有记录则没有 Pending。）
- **丢失提示**：见 [config-and-texts.md](./config-and-texts.md) 的键；`LoadFromFile` → 把 `<uuid>.dat` 里 `Inventory` 的背包再解码一次，在当前线程上 `LostCells.reset()` / `take()`，`ConcurrentHashMap.put(uuid, n)`；`PlayerLoggedInEvent` 里 `remove(uuid)`，大于 0 才发；`PlayerLoggedOutEvent` 里也 `remove`（防泄漏）。
- 恢复提示：`SnapshotOps.restore` → `RestoreResult.lostCells`。

## 6. 命令与窗口

- `restore`/`delete` 序号：1..`MAX_VERSIONS`（100），最新在前、按全部类别。
- 窗口：版本页请求 `(backpack, page, pageSize, kind)`；服务端按 `kind` 筛选后分页；`total` 是筛选后的总数，`pages = max(1, ceil(total/pageSize))`；越界页回最后一页。

## 7. 开关关闭

`!settings.enabled()`：`noteUse`、`noteSaved`、`tick` 的写、`onLogout` 的写、`close` 的写、版本钉住、容量淘汰全部不做；状态文件版本照常更新；查看/预览/恢复/删除照常。
