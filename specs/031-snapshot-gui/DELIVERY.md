# Delivery: 快照管理界面（玩家与管理员面板）

**日期**: 2026-11-09 | **状态**: 代码与自动化检查完成；T028、T030、T031 用户已验证没有问题；**T029（非 op 视图与隔离）搁置未测，需要第二个账号**。

## 这个 feature 是什么

030 命令（`/mcfb snapshot …`）的图形界面版本：模组自己的独立 `Screen`，不依赖背包；三个入口（命令 `/mcfb snapshot gui`、默认不绑定的快捷键、浮动面板标题栏的小按钮）；列表 + 版本 + 只读预览；权限等级 2 另有"我的/全部"、所有者与不活跃天数过滤、恢复（给自己或在线玩家一只新背包）、删除一份、删除全部（确认）、清理（预览再确认）。**权限由服务端判断，与命令一致**，普通玩家的界面里没有 op 控件，别人的背包对他们等同"不存在"。详见 [spec.md](./spec.md)、[plan.md](./plan.md)、[research.md](./research.md)。

## 做了什么

- **服务端**：`SnapshotAccess`（谁能看什么）、`SnapshotRequestRules`（请求字段校验）、`SnapshotOps`（列表/版本/预览/恢复/删除/清理，不含文字，**命令与界面共用**）、`SnapshotServer`（4 个请求的处理，权限在每个处理的开头按 `player.hasPermissions(2)` 判断）；`SnapshotStore` 增加元数据缓存；`SnapshotCommands` 改用 `SnapshotOps`（输出文字不变）并新增 `gui` 子命令。
- **网络**：9 个 payload（4 个请求、5 个响应含"打开"通知），协议 15 → 16；预览超过 900,000 字节服务端不发。
- **客户端**：`SnapshotScreen`（原版 `Button`/`EditBox` + 自绘列表与预览）、`SnapshotScreenState`（纯状态机）、`SnapshotScreenLayout`（纯布局）、`SnapshotResultText`、`SnapshotClient`；标题栏按钮（`TitleButtonsLayout`、`FloatingPanelState`/`Renderer`/`Input` 小改，编号文字右界让到按钮左边）；快捷键 `SNAPSHOTS`（`InputConstants.UNKNOWN`、`KeyConflictContext.IN_GAME`）与 `FloatingPanelEvents` 的 tick 处理。
- **语言**：`gui.mcfloatingbackpack.snapshot.*`（含 14 个结果码文字、5 个档位名）、`key.mcfloatingbackpack.snapshots`、`panel.mcfloatingbackpack.snapshot.tooltip`、`command.mcfloatingbackpack.gui.console`（带英文后备），中英文一致。
- 实现时对计划的一处调整：列表请求多一个 `pageSize`（客户端按窗口放得下几行来要，范围 1～16），而不是固定 8 行；已写回 contracts/network.md。

## 自动化结果

| 项 | 结果 |
|---|---|
| `./gradlew compileJava` | 通过 |
| `./gradlew test` | 101 个类、711 个用例，0 失败（基线 93 个类、658 个用例；新增权限、请求校验、网络码、界面状态机、布局、标题栏几何、结果文字、语言键 8 组测试） |
| 图集度量（T033） | 2048×1024，通过（本 feature 不新增贴图） |
| 静态检查（T034） | 登记表相关名字无残留；9 个 payload 都在 `BackpackNetwork` 注册一次；`SnapshotScreen` 里没有任何权限判断，只用服务端回报的 `isOp` 决定显示什么；服务端每个动作处理的开头都先判断权限 |

## 验证状态

| 任务 | 内容 | 状态 |
|---|---|---|
| T028 | 三个入口、面板状态不变、手里没背包也能开（quickstart A） | 用户验证，没有问题 |
| T029 | 非 op 视图与隔离（quickstart B） | 2026-11-11 AI 自动验证：B1～B4 主要点通过（两个客户端连同一专用服务器）；翻页、悬停、点击预览、附魔物品未测，**未勾** |
| T030 | op 视图与动作、权限变化（quickstart C） | 用户验证，没有问题 |
| T031 | 布局与回归（quickstart D、E，含 030 命令与浮动面板回归） | 用户验证，没有问题 |

步骤见 [quickstart.md](./quickstart.md)；清单见 `docs/plan/manual-verification-checklist.md` 的 031-A～E（该清单已于 2026-11-11 删除，进度只看 tasks.md）。

### 2026-11-11 AI 自动验证：quickstart B（非 op 视图与隔离，部分）

环境：三个容器（一个专用服务器、两个客户端 P1/P2，见 `scripts/ai-gui/README.md` 的"联机"一节）。`ops.json` 只有 P1；P1 与 P2 各拿一只皮革背包，放入物品（P1 放 5 个石头、P2 放 3 个钻石），关面板后各出现 2 份快照。

| 步骤 | 结果 |
|---|---|
| B1 列表只含自己的背包，每行有编号、档位、版本数、所有者、距今 | **通过**：P2（非 op）打开 `/mcfb snapshot gui`，只有 `HPD7 leather ×2 P2 <1m ago`；P1 的 `GCDX` 不在列表里。翻页（超过一页）**没测** |
| B2 选背包列版本，选版本显示预览 | **通过**：版本 `#1 <1m ago 1 items`、`#2 2m ago 0 items`；预览显示 3 个钻石在左上格。悬停提示、点击预览无反应、带附魔的物品**没测** |
| B3 没有"全部"切换、过滤框、恢复、删除、清理 | **通过**：非 op 的界面里这些控件不存在（不是灰色）；同一时刻 op（P1）的界面有 Mine/Owner/Idle days/Restore/To: yourself/Delete this one/Delete all/Days/Clean up，对照明确 |
| B4 别的玩家的背包不出现 | **通过**：同上，P2 看不到 P1 的 `GCDX`，P1 的"Mine"视图也只有 `GCDX` |

没有勾 T029（翻页、悬停、点击预览、附魔物品没测）。截图在 `$TMPDIR/mcfb-ai-gui/shots/`（`nonop4.png`、`op1.png`，不进仓库）。

## 验证中确认的风险点（用户验证通过：快捷键与宿主界面、030 命令回归、布局）

- **快捷键与宿主界面**（research D7）：浮动面板打开着时按绑定的键能否打开界面。目前处理放在 `ClientTickEvent.Post`；不行就挪到 `Pre`，并保持在 `HostScreenManager.beforeTick` 之前。
- **抽取 `SnapshotOps` 后 030 命令不能回归**：命令输出文字由 `SnapshotCommandResourcesTest` 保护，行为需要在游戏里再过一遍 030 的 quickstart B。
- **布局**：只有纯逻辑测试，没有看过实际画面；颜色、对齐、文字截断需要你看一眼。
- **从面板的标题栏按钮打开时**，面板光标上若拿着物品，行为与打开任何其它非容器界面相同（物品留在面板光标里），没有额外处理。

## 已知限制

- 恢复目标只能选在线玩家（用玩家列表循环切换，人多时要多点几次）；没有下拉框。
- 预览只显示主区域格子，不显示工具区、标签、垃圾桶、黑名单。
- 没有搜索框；列表只靠分页与 op 的过滤。
- 单个版本的"第几份"以列表里的位置为准；两个窗口同时删除时，旧窗口的操作会得到"已经不在了"。
