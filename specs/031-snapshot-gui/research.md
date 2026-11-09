# Research: 快照管理界面

读本仓库源码（2026-11-09）得出的结论。每条列出：**决定 / 理由 / 备选**。

## D1 独立 Screen 与浮动面板的关系：不需要特殊处理

`FloatingPanelEvents.hostsPanel(screen)` 只认 `PanelHostScreen` 与容器界面；"在其它任何界面上面板只是被隐藏，可见状态保留，界面关闭后恢复"（`onScreenOpening` 的注释）。所以：

- 快照界面不是容器界面，打开时面板自动隐藏、不画、不抢鼠标；
- 从标题栏按钮打开时，当前界面是宿主（`PanelHostScreen`），`setScreen(快照界面)` 把它换掉；快照界面关闭 `setScreen(null)` 后，`HostScreenManager.ensure()` 每帧检查，面板可见且无界面就重开宿主——**面板的位置、大小、滚动、选区都在 `FloatingPanelState` 里，原样保留**（规格 FR-001）；
- 面板光标上拿着物品时打开快照界面：与打开暂停菜单等任何其它界面一样的既有行为，物品留在面板的光标里；**不额外处理**（plan 不改 019 的规则），按钮在光标有物品时仍可用。

备选：把快照界面也当成"面板宿主"让面板叠在它上面——否决，会抢鼠标，且违背"独立"。

## D2 网络：沿用 `BackpackNetwork` 注册，协议 15 → 16

- 所有 payload 在 `BackpackNetwork.onRegisterPayloads` 里用同一个 `registrar.versioned("15")` 注册；新增 payload 后升为 `"16"`（客户端与服务端版本必须一致，旧客户端连新服务器会被 NeoForge 拒绝）。
- 服务端处理：`handleServer(payload, context)` 里 `context.enqueueWork(...)`，主线程；客户端处理：`handleClient`。
- **大小限制（原版）**：服务端→客户端的自定义负载上限约 1 MiB（`ClientboundCustomPayloadPacket`），客户端→服务端约 32 KiB。请求都是几十字节；列表一页 ≤ 8 行、每行约 100 字节；**预览**是最大的：最多 108 个物品栈，带数据组件（附魔书、潜影盒内容）可能很大。`BackpackFullSyncPayload` 已经用同样的编码给客户端发整份背包，所以常规情况没有问题；仍然加一道**服务端大小保护**：先用 `RegistryFriendlyByteBuf` 编码到临时缓冲区，超过 900,000 字节就不发预览、回报"太大，无法预览"（避免超限导致断线）。

## D3 一个动作 payload + 三个查询 payload + 四个响应 payload

查询（服务端→请求者）都是"请求/响应"一对一：

| 方向 | payload | 用途 |
|---|---|---|
| →服务端 | `SnapshotListRequestPayload` | 列表（范围、所有者名字、不活跃天数、页码） |
| →服务端 | `SnapshotVersionsRequestPayload` | 某只背包的各个版本 |
| →服务端 | `SnapshotPreviewRequestPayload` | 某一份快照的主区域物品 |
| →服务端 | `SnapshotActionPayload` | 恢复、删除一份、删除全部、清理预览、清理确认 |
| →客户端 | `SnapshotListPayload` | 一页列表 + 权限标志 + 总数/页数 |
| →客户端 | `SnapshotVersionsPayload` | 版本列表 |
| →客户端 | `SnapshotPreviewPayload` | 物品栈列表或失败原因 |
| →客户端 | `SnapshotResultPayload` | 动作结果码与数字参数（客户端翻译成状态文字） |
| →客户端 | `SnapshotOpenPayload` | 命令 `gui` 通知打开（空） |

结果用**码 + 数字/字符串参数**而不是服务端拼好的文本：客户端有语言文件，文字可以本地化；服务端不需要为此加语言键。详见 [contracts/network.md](./contracts/network.md)。

备选：每个动作一个 payload——否决，注册样板多，且五个动作的字段高度重合。

## D4 权限判断的唯一入口，界面与命令共用

规格 SC-004 要求界面与命令"同一玩家同一时刻看到和能做的完全一样"。做法：把 `SnapshotCommands` 里"谁能看什么、怎样删、怎样恢复"的服务端逻辑抽到 `SnapshotOps`（服务端类，不含任何消息文字），命令与 payload 处理都调用它：

- `SnapshotAccess`（**纯逻辑**）：`isOp(permissionLevel)`、`canSee(op, self, group.newest().owner())`、`filterFor(...)`；
- `SnapshotOps`：`visibleGroups`、`page`、`versions`、`preview`、`restore`、`deleteOne`、`deleteAll`、`trimPreview`、`trimConfirm`，返回结果记录（含失败原因码），**不发消息**；
- `SnapshotCommands` 保持输出文字不变（030 已验证，不能回归），只是内部改用 `SnapshotOps`；payload 处理把结果码转成 `SnapshotResultPayload`。

权限等级 2 的判断：命令是 `source.hasPermission(2)`；payload 里是 `player.hasPermissions(2)`——同一个 `PermissionLevel`，单人游戏关作弊没有 op，开作弊的房主是 op。**权限检查放在每个 payload 的服务端处理开头**，客户端发来的"我是 op"没有任何作用（客户端根本不发这个）。

## D5 请求校验是纯逻辑

`SnapshotRequestRules`（纯）：页码 1..1,000、页大小固定（服务端定，客户端不能指定）、版本序号 1..16、天数 0..36,500、所有者名字 ≤16 字符且只含 `[A-Za-z0-9_]`、范围/动作字节必须是已知值。违规一律"零变化 + 一条调试日志"，不回包（避免成为探测手段），例外：客户端界面需要知道请求失败时才回 `SnapshotResultPayload`（用于合法但失败的情形，如"找不到"）。

## D6 服务端对元数据加缓存（性能）

030 的 `SnapshotStore.groups()` 每次都读**每个背包的最新快照文件**（压缩 NBT，含全部内容）来拿元数据。命令一次一页没关系，但界面每次翻页、每次选中、每次刷新都会请求，背包有几百只时代价明显。

- 快照文件**写入后不再修改**，文件名含时间；所以"文件路径 → 元数据"的缓存**永远正确**，不存在过期问题。
- `SnapshotStore` 内加一个 `ConcurrentHashMap<Path, SnapshotMeta>`：列目录（便宜），已缓存的文件直接用，新文件才读；目录里已不存在的从缓存里清掉。手动删除/移动目录（030 特意让目录名可读，就是为了这种操作）立即生效，因为每次都重新列目录。
- 这是**对文件的缓存，不是第二份真相**。不新增任何持久化。

备选：把元数据另存一个小文件——否决，会重新引入"索引与文件不一致"。

## D7 快捷键：`KeyMapping` 默认不绑定

- `FloatingBackpackKeys` 里加 `SNAPSHOTS = new KeyMapping("key.mcfloatingbackpack.snapshots", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY)`，在 `registerKeys` 里注册；控制设置里的"未绑定"状态是原版 `InputConstants.UNKNOWN`。
- 处理放在 `ClientTickEvent.Post`：`while (SNAPSHOTS.consumeClick()) open()`。原版只在**没有界面**时把按键转成 `KeyMapping` 点击，所以在聊天框、告示牌编辑等界面里天然不触发；
- 面板可见（宿主界面在场）时：`PanelHostScreen` 的按键转发会把"绑定到这个键的映射"交还给游戏（`boundMappings`）；`HostKeyRouter.classify` 把它归为"其它按键"，宿主让位一个 tick，让这个映射的点击被消费——本 feature 的处理器在 `ClientTickEvent.Post` 里 `consumeClick`，与 Traveler's Backpack 读键的方式不同（它在 Pre）。**这一点需要在游戏里验证**（quickstart A2），不对就把处理挪到 `ClientTickEvent.Pre`，并保持 `HostScreenManager.beforeTick` 之前的顺序。

## D8 标题栏按钮的几何

现状（`FloatingPanelState`）：等级图标在 x+4，边长 16；关闭按钮 10×10，右边距 3，y+2；背包编号文字从图标右边 +3 开始，一直到"关闭按钮左边 −2"（`TitleIdLayout.of(posX, closeButtonLeft(), textWidth)`）。

新按钮 10×10，放在关闭按钮左边 2 px 处：`snapshotButtonLeft = closeButtonLeft − 2 − 10`。编号文字的右界改为这个按钮的左边（`TitleIdLayout` 的 `closeLeft` 参数换成新的右界，`FloatingPanelRenderer.drawTitleId` 与 `FloatingPanelState.hitsTitleId` 同步改）。

- 纯逻辑 `TitleButtonsLayout`：给定面板位置与宽度，返回关闭按钮左边、快照按钮的矩形、"文字右界"；当面板窄到按钮会压到等级图标时**不显示**快照按钮（返回 empty）。可单测，并保证与关闭按钮、编号不重叠。
- 点击顺序：`FloatingPanelInput.onPress` 里在 `hitsCloseButton` **之前**判断 `hitsSnapshotButton`（两个按钮不重叠，顺序无所谓，但放在一起便于读）；只响应左键。
- 绘制：代码画——沿用 025 菜单图标做法：`graphics.fill` 的底色 + 三条横线或一个小"盒子"图案；悬停提示"背包快照"（`graphics.renderTooltip`）。**不新增贴图。**

## D9 独立界面用原版控件，不自绘一套输入

快照界面是真正的 `Screen`，有了控件系统：`Button`（翻页、范围切换、恢复、删除、清理、关闭）、`EditBox`（所有者名字、天数、清理天数）。这样焦点、键盘、IME、光标闪烁、Tab 切换都免费且与原版一致。列表行与预览格子自绘（行的点击在 `mouseClicked` 里处理）。

- 复用：`FloatingPanelRenderer.drawVanillaPanel`（外框）、`drawSlotWell`（格底色）、`COLOR_SLOT_HOVER`；物品用 `graphics.renderItem`/`renderItemDecorations`；悬停提示用 `graphics.renderTooltip(font, stack, mx, my)`（原版提示）。**不复制绘制代码**。
- 布局：纯逻辑 `SnapshotScreenLayout`（输入：GUI 宽高、预览行数；输出：各区矩形）。目标：宽 ≤ 400、高 ≤ 260，居中；GUI 缩放 4（常见 1920×1080 → 480×270）也放得下；更小时预览区改为纵向滚动（鼠标滚轮），列表每页行数随高度减少（最少 4 行）。
- 状态机：纯逻辑 `SnapshotScreenState`（范围、过滤、页码、选中、待确认操作、按钮可用性、状态文字码），Screen 只负责把它画出来、把输入喂给它。`isOp` 只来自服务端响应。

## D10 恢复目标的选择

客户端从 `Minecraft.getConnection().getOnlinePlayers()`（玩家列表）取在线玩家名，op 用一个"目标：自己 ▸"循环按钮在名字间切换（人数多时按字母序，点一下下一个；不做下拉）。服务端按名字找在线玩家，找不到（刚下线）回"目标不在线"。普通玩家没有这个按钮。

## D11 `/mcfb snapshot gui` 的实现

`SnapshotCommands` 增加 `gui` 子命令，**不要求权限**：执行者是玩家 → `PacketDistributor.sendToPlayer(player, new SnapshotOpenPayload())`；控制台 → `sendFailure`（`command.mcfloatingbackpack.gui.console`，带英文后备）。客户端收到后在主线程 `Minecraft.setScreen(new SnapshotScreen())`（已有同类 `handleClient` 先例）。若已有快照界面打开就忽略。

## D12 单人游戏与权限刷新

每个 `SnapshotListPayload` 都带当时的 `isOp`。客户端据此显示/隐藏 op 控件；玩家在界面打开期间被取消 op 时，下一次任何请求的响应里 `isOp` 变 false，控件消失，已选的 op 动作作废（待确认状态清空）；动作本身仍由服务端按当时权限再判一次。

## D13 不做的东西（与规格一致）

不改共用方块图集（没有新贴图）；不新增服务端配置；预览只读；不做搜索框。
