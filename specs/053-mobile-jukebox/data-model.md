# 数据模型：移动唱片机

没有存储数据。只有内存里的三样东西。

## 播放列表（`PlaylistReader`，纯逻辑；每次需要时现读）

输入：可见行的列表（每行 `category` 与格子列表）、"这个格子里的东西是不是唱片、键是什么"的函数。输出：`List<Track>`，`Track(key, source)`：按行序、格子序，只取类别 `disc` 的行，跳过空格、非唱片、别的类别的行。隐藏行不在输入里。

## 播放状态（`JukeboxPlayer`，纯逻辑，客户端会话内）

| 字段 | 含义 |
|---|---|
| `playing` | 是否在播放 |
| `current` | 当前曲目（键 + 在列表里的位置），无则空 |
| `loopOne` / `shuffle` | 两个开关，默认关 |
| `queue` | 随机模式下本轮还没放的位置（含当前） |
| `history` | 随机模式下放过的位置栈（给"上一曲"） |

操作（都带"当前列表"参数，返回命令 `Start(track)` / `Stop` / `None`）：`play`、`stop`、`rewind`、`next`、`previous`、`finished`（一首放完）、`setLoopOne`、`setShuffle`、`reset`。

## 客户端播放器（`JukeboxPlayback`，MC 层）

持有 `JukeboxPlayer`、当前声音实例、本首已计时的 tick 与总 tick（`lengthInSeconds * 20 + 20`）、"曾经 active"标记、当前背包的键。每个客户端 tick 驱动（暂停界面不计时）。

## 页（客户端，会话内）

`JukeboxPanelState`：展开与否。不存储。
