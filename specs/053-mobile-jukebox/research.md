# Research: 移动唱片机

方法：读本仓库代码、NeoForge 21.1.251 的 sources jar（含 1.21.1 原版源码）、旅行者背包的 jar（本仓库开发运行里有）。

## R1 页的结构与挂接点（垃圾桶页是模板）

垃圾桶页的全部挂接点（`grep TrashPanel`）：

| 位置 | 作用 | 唱片机页怎么办 |
|---|---|---|
| `FloatingPanelInput`：点右区的垃圾桶（光标空、左键、无 Shift/Ctrl/拖拽）→ `TrashPanelState.toggle()` | 展开/收起 | 同一处加：点右区的移动唱片机 → 先 `TrashPanelState.collapse()` 再 `JukeboxPanelState.toggle()`；点垃圾桶时反过来收起唱片机页 |
| `FloatingPanelInput.dispatchMouseButton`：`TrashPanelInput.onPress` 先于面板自己的命中测试 | 页在面板矩形之外，按下要先认领 | 紧跟着加 `JukeboxPanelInput.onPress`（被消费的按下，原有的 `PRESSES` 机制会吞掉对应的松开） |
| `FloatingPanelRenderer`：`if (TrashPanelState.get().isOpen()) TrashPanelRenderer.render(...)` | 画 | 同处加 |
| `FloatingPanelEvents.scrollPanel`：页上的滚轮被吞 | 不让滚轮穿透 | 同处加 `JukeboxPanelInput.covers` |
| `closePanel` / `onLoggingIn` / `onLoggingOut`：`TrashPanelState.collapse()/reset()` | 收起与重置 | 同处加；**登出时还要停播放并清状态** |
| `onClientTick`：垃圾桶页需要右区有垃圾桶且面板可见，否则收起 | 条件收起 | 同处加 `hasMobileJukebox()`；**注意：页收起不等于停止播放**（面板关了音乐继续，见 R3） |

**不引入页注册表**：现在只有两个右区页，互斥写在两处点击里即可（YAGNI）；等 054 的工作台与熔炉（不需要页，直接开原版界面）之后再看要不要。

## R2 "只在面板打开时才有数据"的陷阱

`ClientBackpackView.clear()` 在 `closePanel()` 里被调用，所以面板关着时它是空的。播放器要在面板关着时继续放（自动接下一首），所以**不能读 `ClientBackpackView`**，要读玩家自己的背包栈：

- `BackpackRef.resolve(player)`（客户端可用：手 → 背 → 物品栏，不依赖面板）→ `getStack(player).get(ModDataComponents.BACKPACK_CONTENTS.get())`；
- `BACKPACK_CONTENTS` 是 `networkSynchronized(BackpackContents.STREAM_CODEC)`，而 `STREAM_CODEC`（035 起手写）**包含 `rows`**（最后几段），所以客户端栈上有可见行的全部格子与右区的工具栈；
- `contents.rows().visible()` 是 `List<Row<ItemStack>>`，`Row.category` 与 `cells`；`contents.tools().right()` 是右区的栈。
- 面板里画页时同样读这个，不读 `ClientBackpackView`，两处一致。

## R3 声音：为什么用"耳边的、可被计时的"声音实例

- 旅行者背包的做法（读 jar）：`JukeboxWidget$MovingSound extends AbstractTickableSoundInstance`（跟着实体）+ `queueTickingSound` + `SoundManager.stop(id, SoundSource)` + `Gui.setNowPlaying` + `JukeboxSong.fromStack`；只有播放/停止，没有暂停，没发现向别的玩家广播。
- 我们更简单：自己的声音只要在耳边，所以 `AbstractSoundInstance` 的子类设 `relative = true`、`attenuation = NONE`（相对听者，不需要每 tick 更新位置）、`SoundSource.RECORDS`（音量跟随原版"唱片机/音符盒"滑条）。`SimpleSoundInstance.forLocalAmbience` 不用：它的类别是 `AMBIENT`，滑条不对。
- **放完了怎么知道**：不能靠 `SoundManager.isActive`——`SoundEngine.play` 在音量为 0 时直接返回（日志 `Skipped playing sound …, volume was zero`），根本不建通道，`isActive` 一开始就是假。所以用**计时**：`JukeboxSong.lengthInSeconds * 20` tick（与原版唱片机方块 `JukeboxSong.hasFinished` 同一口径，多留 20 tick 余量），只在游戏没暂停时累加。额外：之前看到过它 active、后来不 active（`/stopsound` 之类）也当放完。
- 开始前 `Minecraft.getMusicManager().stopPlaying()`（原版唱片机方块 `LevelRenderer.playJukeboxSong` 也是这么做的），`Gui.setNowPlaying(song.description())`。
- 原版没有单个声音的暂停（`SoundEngine.pause()` 是全局的，`instanceToChannel` 是私有的），所以不做暂停；**倒带 = 停掉当前再重新开始**。
- 容器里 OpenAL 打不开，`SoundManager.play` 是空操作，但计时仍走：所以状态与自动接歌能在容器里验证，听觉不能。

## R4 播放列表

- `RowView.visibleCategories` 与 `rows().visible()` 都是"可见行的类别 / 行"，顺序就是行序；非连续的唱片行只要逐行过滤 `category.equals("disc")` 即可读全（`ExtensionCategories.IDS` 里 `disc` 是第 10 个类别，id 固定）。
- 单元格：空格 `ItemStack.isEmpty()` 跳过；`JukeboxSong.fromStack(registries, stack)` 返回 `Optional<Holder<JukeboxSong>>`，空表示不是唱片（手动标签放进唱片行的非唱片物品就是这样被跳过的）。
- 曲目的键：`holder.unwrapKey()` 的 id 字符串，没有就用物品 id；同一首歌的两张唱片键相同，列表允许重复（按位置各算一首）。
- 隐藏行不在 `visible()` 里，天然不算。

## R5 状态机的细节决定

- 列表每次操作都重新读（FR-008），状态机只记：当前曲目的键与位置、是否在播放、`loopOne`、`shuffle`、洗牌队列、历史。
- 位置找法：先看原位置的键是否还匹配；否则找第一个同键的位置；再否则（唱片被拿走）位置按原值夹到列表范围内；列表空 → 停止。
- 顺序模式：下一首 = 位置 + 1 取模；上一首 = 位置 − 1 取模；放完 = 下一首。
- 随机模式：一轮 = 对 `0..n-1` 的位置洗牌，当前这首排在队首（若在播放）；放完一轮后重新洗牌，若新队首与刚放完的最后一首同一位置且 n > 1，则与第二个交换（避免紧挨着重复）；列表长度或内容变了 → 下一次需要"下一首"时重建队列；"上一曲"从历史栈弹出，历史空则停在当前。
- 单曲循环：放完 = 重放当前；"下一曲 / 上一曲"仍按模式前进或后退。
- 播放 / 停止：播放 = 从当前曲目开头开始（第一次：顺序取第一首、随机取随机一首）；停止 = 停声音，记住当前曲目不记进度。倒带 = 停掉当前再从开头开始（没在播放时等同播放）。
- 开关切换时：随机开→建队列；随机关→丢队列；单曲循环不影响队列。
- 测试：固定种子的 1000 轮检查"每轮每首恰好一次"、"相邻两轮边界不重复（n > 1）"；上一曲回放历史；列表中途变化的各种情形。

## R6 右区"各至多一个"

`ToolAreaRules.canPlace` 现在对每个区只限一个"某种工具"（右区是垃圾桶，下区标签管理器，左区自动拾取），签名里有三个判定。**不改它的签名**（011～052 的调用点与测试不动）；新建纯逻辑 `ToolSingleRules.check(targetArea, area, incoming, slotIndex, areaSlots, isKind) → OK | DUPLICATE`，判定与 `ToolLightRules` 一致（只在 `targetArea` 里算、同格替换允许、旧背包已多时保留不能再加）。服务端与客户端预览各接一处，提示键 `message.mcfloatingbackpack.limit.mobile_jukebox`。054、055 的工作台与熔炉各加一个判定即可。`ToolLightRules` 保持原样（不为了复用去重构 052）。

## R7 数据修订与协议号

- 新物品只在背包里生效 → 按 `release-process.md`"新的只能存在背包里的物品种类"递增：`DataRevision.CURRENT` 3 → 4；要同改的测试：`DataRevisionTest`、`TankWiringTest`、`PipeWiringTest`、`LightWiringTest` 里的 `CURRENT = 3;`（grep 核对）。
- 协议号不变（19）：客户端播放，没有新的网络包；服务端只多一个放置检查。

## R8 贴图与资源

沿用 036 的手绘做法：新脚本 `design/demo/tools/make_mobile_tool_icons.py`（Pillow，16×16 RGBA），铁灰底板上画一个小唱片机（木箱、黑色唱片、音符）；之后 054、055 的图标加在同一个脚本里。占位，用户可用同名文件替换。共享图集前后各量一次（`check_atlas_budget.sh`）。

## R9 开放问题（不阻塞 plan）

- **背包换了的判定**：`BACKPACK_IDENTITY`（026）可能没有（栈从没被注册过），所以"换了"用"按 B 解析出的背包的身份 id，没有则用来源与槽位"的组合键；换了就停。实现时在游戏里验证手 → 物品栏之类的移动不会误停。
- **展开页与容器菜单的相互影响**：本 feature 不打开任何菜单，不涉及；054 再验证。
- **两个页互斥与现有的点击归属**：垃圾桶页的 `covers` 在页收起时为假，所以互斥只靠状态机，不需要改命中逻辑。
