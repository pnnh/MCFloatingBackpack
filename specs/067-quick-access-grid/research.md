# Research: 快捷访问网格

读码日期 2026-11-26。规格 [spec.md](./spec.md)，规划 [docs/plan/quick-access.md](../../docs/plan/quick-access.md)。没有未决的 NEEDS CLARIFICATION。

## R1. 网格必须是自己的透明界面，不能借用面板宿主

**Decision**: 新的 `QuickAccessScreen`。只在 `screen == null` 且浮动面板不可见时 `setScreen`。`isPauseScreen` 为假，`renderBackground` 留空。它不存放物品。任何别的界面替换它即结束（暂停、死亡、按 E）。面板宿主 `PanelHostScreen` 继续只托面板。

**Rationale**: 宪法 I 允许「面板可见且没有别的界面」时用宿主，并写明宿主不得挪作他用。游戏画面里鼠标被抓住，悬停和点击只能发生在某个 Screen 上。自己开一块、并且面板关着时才开，两条都满足。

**Alternatives considered**: 在 `screen == null` 上用 HUD 画格子——鼠标不会放开，点不到。借用 `PanelHostScreen`——违反「宿主只托面板」。先按 B 打开面板再过滤——规格明确要求面板开着时按键无效。

## R2. 按键只在游戏画面，改成 F 时不能顺带换副手

**Decision**: 按键 `FloatingBackpackKeys` 新增一项，默认 Z，`KeyConflictContext.IN_GAME`。在现有 `InputEvent.Key`（`FloatingPanelEvents.onKey`）里处理按下：有 overlay、有任意 Screen、或 `FloatingPanelState.isVisible()`（含宿主让开的那一帧）则直接返回。处理成功或给出提示后 `setCanceled(true)`。若这一下的物理键同时也是原版「与副手交换」（`options.keySwapOffhand`），再 `consumeClick()` 把这一下吃掉。网格开着时的「再按一次关闭」写在 `QuickAccessScreen.keyPressed` 里，因为这时 `IN_GAME` 不会再记点击。

**Rationale**: B/U 是 `UNIVERSAL`，在容器上也会触发；本键相反。`IN_GAME` 在宿主和容器上本来就不会记点击，再加 `isVisible()` 挡住让开的那一帧。NeoForge 的 `InputEvent.Key` 可取消（本仓库的 Screen 事件已经在用 `setCanceled`）。只取消事件仍可能在同一帧留下原版按键的 click，所以改成 F 时要主动吃掉副手交换。

**Alternatives considered**: 默认仍用 F——规格已否决。只依赖冲突上下文、不吃副手 click——改键后可能一次按键做两件事。

## R3. 关掉面板之后，格子和标签读物品组件，不读会话视图

**Decision**: 客户端用 `BackpackRef.resolve` 拿到背包物品，读 `ModDataComponents.BACKPACK_CONTENTS`（主格子、可见扩展行、标签、下方区）。不读 `ClientBackpackView`。服务端用 `BackpackGate.acquire` 再算一遍。

**Rationale**: 会话结束时 `ClientBackpackView.clear()` 会把格子和标签清掉（`BackpackSessionPayload`）。唱片机和光源在面板关掉后已经改读物品上的组件。组件随物品同步；自动拾取等保存走 `BackpackRef.saveInventory`，会写回物品。

**Alternatives considered**: 为了网格专门开一次面板会话——会把面板显示出来，和规格相反，也多一次整包同步。

## R4. 候选名单与视口是纯逻辑

**Decision**: 两个无 Minecraft 类型的类。

- `QuickCandidates`：输入是默认容量、可见扩展行数、每个主区域格子的「空不空 + 物品 id 字符串」、以及「这个 id 是否带 `quick`」。顺序：下标 0 到默认容量-1，再是可见行（每行 9 格，`RowLayout` 的下标）。跳过空格。取前 `baseCapacity/2` 个。返回背包格子下标列表。工具区、隐藏行、光标根本不传入。
- `QuickViewport`：格子 18（与 `PanelLayout.SLOT` 相同），边距 18（给三角留出画面内的位置）。目标 9 列 × 4 行。可见列 = 把 1..9 夹进 `(画面宽 - 2×边距) / 18`，行同理，至少 1。内容行数更少时视口缩到内容；不足一行时宽度缩到个数。滚动一次一行，向上减小滚动，夹在 `0 .. 总行数-可见行数`。命中：鼠标在视口内则换成「滚动后的候选下标」，否则是外面。三角：滚动 > 0 画上，后面还有行画下。

**Rationale**: 规格要求服务端用同一套名单，并且窗口缩放要能单测。`RowLayout.groupOf` 已经规定扩展行的下标，不必再发明一套。

**Alternatives considered**: 视口固定 9×4、超出画面就裁切——规格要求完整留在画面内。左右滚动——规格禁止。

## R5. 交换是一条新请求，语义抄现有的整组对调

**Decision**: `QuickSwapPayload`（C→S）：背包来源序号、格号、主区域格子下标。频道 `mcfloatingbackpack:quick_swap`。服务端不相信客户端的名单，只把来源用来核对「是不是现在按 B 的那一只」（`BackpackRef.normalized` 后比较，主手和「物品栏当前格」算同一只）。然后：

1. `containerMenu == inventoryMenu`（没有别的容器）。
2. `BackpackGate.acquire`。受 040 保护的背包在这里得到 empty：不写、不交换、不启动也不消耗「60 秒内再打开」的确认窗口。客户端在打开前若发现 `dataRevision` 比本版本新，不打开网格，聊天里用现有的 `message.mcfloatingbackpack.revision.protected`（确认仍然只能靠按 B 打开面板）。
3. 下方区有快捷访问（扫整列，与 `hasTagManager` 一样）。
4. 格子下标在此刻重算的 `QuickCandidates` 里。
5. 主手是浮动背包，或 `BackpackNesting.refusal` 不是 OK：拒绝。
6. 主手非空且 `canPlaceMain` 失败：拒绝。
7. 整组对调，与 `BackpackClickPayload` 里 `swapHand` 分支相同（复制两份再写入），`saveInventory`。面板会话若正开着这只背包，再走现有的格子同步，避免面板画面落后；正常情况面板是关的，物品组件同步就够。

失败时快捷栏上方一句，网格保持打开（客户端收到拒绝才留着；成功则本地先关掉，服务端不要求再关）。请求到达时网格已经关了也照样执行或拒绝，不依赖界面还在。

**Rationale**: 现有点击包在 `panelOpen == false` 时整包丢弃，而且要求会话。规格要求面板关掉时交换。`acquire` 会登记身份并通知快照「开始使用」，`saveInventory` → `afterSave` 通知「已保存」，变更快照不用另做。

**Alternatives considered**: 复用 `BackpackClickPayload` 并假装会话开着——会把面板打开或写错会话目标。只发物品 id——同一种有多组，对不上格子。

## R6. 下方区限放沿用 ToolSingleRules，不改它的签名

**Decision**: `FloatingPanelInput.refusedByLimit` 与 `BackpackClickPayload` 各加一处 `ToolSingleRules.check(BOTTOM, …, stack -> stack.is(QUICK_ACCESS))`，提示键 `message.mcfloatingbackpack.limit.quick_access`。与标签管理器、光源、罐子的判断并列，不塞进 `ToolAreaRules`。快捷访问在下方区没有左键展开；不在 `FloatingPanelInput` 里为它加点击分支，拿起走普通点击。

**Rationale**: 053 起新的「某区至多一个」都是这样接的，测试 `ToolSingleRulesTest` 不用改。`ToolAreaRules` 的下方区已经专指标签管理器。

**Alternatives considered**: 改 `ToolAreaRules` 的谓词列表——053 的研究明确说不改那个签名。

## R7. 数据修订 6→7，协议 24→25，预设追加在末尾

**Decision**:

- `DataRevision.CURRENT = 7`，注释加上「7 added the quick access item」。理由与 053/054/055 相同：新的、会放进背包的物品种类，旧版本保存时会丢掉。只加字符串 `quick` 不够成升修订的理由，但物品和标签同一 feature 交付，修订只升一次。
- `BackpackNetwork` 的 `versioned("24")` 改为 `"25"`。版本检查那条 `versioned("1").optional()` 不动。
- `PresetTags.IDS` 在 `meat` 之后追加 `"quick"`。`PresetTagsTest.theTwentyOnePresetsInOrder` 改成 22 个，前面 21 个顺序不变。
- 历史测试里写死 `versioned("24")` 的改为 `"25"`，写死 `CURRENT = 6` 的改为 `7`。已知文件：协议 `AllWiringTest`、`TankWiringTest`、`CategoryExtensionUiResourcesTest`、`IntegratedTerminalsWiringTest`、`NetworkTakeWiringTest`、`CombinedWiringTest`、`MobileFurnaceWiringTest`、`GreyCellWiringTest`、`PipeWiringTest`、`SearchPrefixResourcesTest`、`JukeboxWiringTest`、`MobileCraftingWiringTest`、`ItemTooltipWiringTest`、`BackpackSortWiringTest`、`SearchResourcesTest`、`LightWiringTest`、`SnapshotCapacityWiringTest`、`DowngradeProtectionResourcesTest`、`VersionCheckResourcesTest`、`WorldMarkerWiringTest`、`ServerCompatWiringTest`；修订 `DataRevisionTest`、`TankWiringTest`、`PipeWiringTest`、`MobileFurnaceWiringTest`、`LightWiringTest`、`JukeboxWiringTest`、`MobileCraftingWiringTest`、`SnapshotCapacityWiringTest`。实现时再 grep 一遍，漏网的一并改。注释里过时的「协议仍是 19」之类随断言改掉。

**Rationale**: `release-process.md`：新的只能放在背包里的物品种类要递增修订。新的运行时包要递增协议，否则旧客户端能进服却不认识请求。

**Alternatives considered**: 协议不升、把字段塞进旧点击包——旧客户端会按旧格式解码失败或语义错误。修订不升——旧版本加载含这个物品的背包会丢东西且没有 040/041 的拦截。

## R8. 提示、贴图、配方

**Decision**:

- 打不开网格的四句用快捷栏上方（`displayClientMessage(..., true)`），与现有「需要背包」相同。`need_backpack` 复用。另外三句新键：`quick.need_tool`、`quick.in_hand`、`quick.none`。交换失败另两句：`quick.wrong_row`（分类行拒收）、`quick.refused`（其余拒绝，含名单已变、嵌套、容器开着）。同一句 20 tick（1 秒）内不重复，客户端与服务端各做一个小冷却，不复用管子的 `PipeCooldowns`（那个枚举是管子专用的）。
- 主手正是 `BackpackRef.resolve` 的那只（来源是主手）时，客户端直接 `quick.in_hand`，不发包。服务端仍拒绝「主手是任意浮动背包」。
- 贴图 16×16 平面物品图，模型与 `tag_manager.json` 相同（一层 `layer0`）。不新增方块，不进方块图集，不跑 010 的图集预算。创造栏插在标签管理器之后。`never_trash.json` 加上 `mcfloatingbackpack:quick_access`。
- 配方按规格：`RIR` / `LCL` / `RIR`（红石粉、铁锭、皮革、指南针）。

**Rationale**: 规格把「快捷栏上方」和「1 秒」写死了。物品图不是宪法 VI 的方块图集；一张 16×16 也不会给每个玩家加可测量的常驻开销以外的新模型。

**Alternatives considered**: 提示走聊天——和「需要背包」不一致，也更容易挡住画面。复用管子冷却——会把快捷访问的句子算进管子的「同一种提示」。

## R9. 三角不从面板渲染器里抽公共 API

**Decision**: 快捷网格自己画三角，尺寸和颜色与 `FloatingPanelRenderer` 的 `ARROW_SIZE = 3`、深色 `0xFF3A3A3A`、浅色描边 `0xFFF2F2F2` 相同。不把那里的私有方法改成公共的。

**Rationale**: 画法大约十五行。抽公共方法会让面板渲染器为了一个无关界面改签名，收益很小。

**Alternatives considered**: 把 `drawArrow` 提成公共工具——可以，但不是本 feature 必须的重构。

## R10. 实机修订（2026-11-26）：按住显示、放大、落点改放空位

上面 R3、R4、R8 里「按一下弹出、再按一下关闭」「格子 18 像素」「分类行整次拒绝并说 `quick.wrong_row`」已被这次修订替换。数据修订仍是 7，协议仍是 25：没有新的存档字段，也没有新的网络包。

**Decision**:

- 按下（`GLFW_PRESS`）且当时在游戏画面、网格还没开，才打开。重复（`GLFW_REPEAT`）忽略。松开这个物理键（`getKey()` 相等，不用 `matches`，因为界面开着时 `IN_GAME` 为假）关闭。Esc 仍关闭。右键和点在网格外不关闭，否则按住时点空会把网格关掉，而重复按下不会再打开。
- 物品 32 像素，格子 40 像素。边距仍是 18。目标仍是 9×4；400×300 的窗口仍放得下 9 列。
- `HandSwapPlace` 决定落点，`HandSwapSlots` 用 `mainTargetOrder` 列出空的、`canPlaceMain` 为真的格子。网格交换和主区域的 Alt+左键都调用它。没有落点时 `quick.no_room`，不改任何格子。`quick.wrong_row` 留在语言文件里，不再发出。

**Rationale**: 按住时原版把重复键交给已经打开的界面，界面里「再按一次关闭」就会闪。分类行里的快捷物品换箱子时，箱子进不了那一行，但背包别处常常有空位。

**Alternatives considered**: 1.5 倍（物品 24、格子 28）——世界画面里仍偏小，2 倍在 854 宽的窗口里 9 列仍放得下（9×40+边距）。点外面关闭——和按住显示冲突。
