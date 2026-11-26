# Data Model: 快捷访问网格

无新的背包字段、无新的数据组件。标签 id `quick` 写进现有的标签书（物品注册名 → 标签字符串列表）。新物品是普通物品，堆叠 1，没有自己的组件。

## 实体

### 快捷标签

- 存储 id：`quick`。显示：`tag.mcfloatingbackpack.preset.quick`（中文「快捷」，英文 “Quick”）。
- 位置：`BackpackContents` 已有的 `tags`。每类最多 10 个，全书最多 1024 类，规则不变。
- 不是 `ExtensionCategories` 的类别，不写入 `extension_rows`。

### 快捷访问物品

- 注册名 `mcfloatingbackpack:quick_access`。类 `QuickAccessItem`，`stacksTo(1)`，与 `TagManagerItem` 相同。
- 下方功能区至多 1 个才生效；判定用 `ToolSingleRules`，不新写限放算法。
- 列入 `mcfloatingbackpack:never_trash`。
- 背包保存时 `data_revision` 盖 `DataRevision.CURRENT`（7）。没有新字段。

### 候选名单 `QuickCandidates`

纯逻辑。输入：

| 字段 | 含义 |
|---|---|
| `baseCapacity` | 档位格数（36/54/72/90/108） |
| `visibleRows` | 可见扩展行数；隐藏行不传入 |
| `occupied` | 主区域下标 → 物品 id（空格不出现） |
| `tagged` | 物品 id 是否带 `quick` |

下标范围是 `[0, baseCapacity + visibleRows × 9)`。输出：有序的格子下标，长度 ≤ `baseCapacity / 2`。

关系：每一项是背包主区域的一个格子，不是物品 id。同一种多组会占多项。

### 视口 `QuickViewport`

纯逻辑，不存档。打开时和窗口大小变化时重算。

| 字段 | 含义 |
|---|---|
| `columns` / `rows` | 这一屏实际画出的列、行（已按内容和窗口缩小） |
| `scroll` | 当前跳过的行数 |
| `totalRows` | 候选按列数换行后的总行数 |
| `originX` / `originY` | 视口左上角，使矩形在画面中央 |

常量：物品 32，格子 40，边距 18，目标 9×4。滚动夹取 `0 .. max(0, totalRows - 可见行数)`。可见行数是窗口算出来的上限，画出来的行数不超过内容。

状态：无持久状态。关闭即丢。死亡、断线、被别的界面替换，都不留下待交换。

### 交换请求

见 [contracts/protocol.md](./contracts/protocol.md)。不新增会话，不新增光标。

## 状态转移

```text
游戏画面、面板关着
  按键 → 打不开（背包没有 / 下方区没有物品 / 背包在主手 / 名单空 / 修订过新）
  按键 → 打开 QuickAccessScreen
打开中
  滚轮 → scroll ± 1（夹取）
  窗口大小变化 → 重算视口，夹取 scroll
  Z / Esc / 右键 / 点在视口外 → 关闭，不发包
  左键点在候选上 → 发 quick_swap，网格先留着，忽略后续连点
结果包 ok=true → 关闭
结果包 ok=false → 留着；拒绝的句子由服务端发到快捷栏上方
丢包 → 网格留着，再点一次或按 Esc
```

不乐观关闭。规格要求失败时网格仍开着，所以成功与否必须有回包。

## 语言键

| 键 | 用途 |
|---|---|
| `item.mcfloatingbackpack.quick_access` | 物品名 |
| `tag.mcfloatingbackpack.preset.quick` | 预设显示名 |
| `key.mcfloatingbackpack.quick_access` | 控制设置里的按键名 |
| `message.mcfloatingbackpack.quick.need_tool` | 下方区没有这个物品 |
| `message.mcfloatingbackpack.quick.in_hand` | 这只背包正在主手上 |
| `message.mcfloatingbackpack.quick.none` | 没有可列出的物品 |
| `message.mcfloatingbackpack.quick.no_room` | 没有能放下手上物品的空位，交换不发生 |
| `message.mcfloatingbackpack.quick.refused` | 其它拒绝 |
| `message.mcfloatingbackpack.limit.quick_access` | 下方区再放第二个 |
| `message.mcfloatingbackpack.need_backpack` | 复用 |
| `message.mcfloatingbackpack.revision.protected` | 复用；不新做确认流程 |
