# Research: 垃圾桶（本体）

结论来自读本仓库源码（`ModItems`、`TagManagerItem`、`BackpackContents`、`BackpackInventory`、`ToolAreaRules`、`BackpackClickPayload`、`BackpackCursorGesturePayload`、`BackpackQuickTransferPayload`、`BackpackMenuBridge`、`PanelCursor`、`QuickTransferLogic`、`QuickTransferGesture`、`QuickTransferInput`、`PanelDragGesture`/`PanelDragInput`、`PanelLayout`、`FloatingPanelInput`/`FloatingPanelRenderer`/`FloatingPanelEvents`、`TagPanelState`、`FloatingBackpackJeiPlugin`、018 的 research 与 contracts）与对原版 `AbstractContainerScreen` 鼠标流程的记忆。**没有在游戏里验证**的地方在"仍不确定"里列出，原版鼠标流程那一条尤其要在实现时读 `build/moddev/artifacts/neoforge-21.1.251-sources.jar` 里的源码再确认一遍。

## Decision 1: 垃圾桶是一个普通 `Item`，配方是十字形

**Decision**: 新增 `TrashCanItem extends Item`（`stacksTo(1)`），注册名 `trash_can`，进创意栏（在标签管理器之后）。配方 `data/mcfloatingbackpack/recipe/trash_can.json`：`minecraft:crafting_shaped`，`category: misc`，

```text
  I        I = minecraft:iron_ingot
I C I      C = minecraft:cauldron
  I
```

pattern `[" I ", "ICI", " I "]` → 1 个 `mcfloatingbackpack:trash_can`。物品模型 `item/generated`，`layer0 = mcfloatingbackpack:item/trash_can`。

**Rationale**: 与 018 的标签管理器同一形态；配方已被用户接受，只用原版材料（宪法 IV）。铁锭用具体物品而不是 `c:ingots/iron` 标签，JEI 里显示最直白（配方被用户确认为"4 铁锭 + 炼药锅"）。

## Decision 2: 内容存在 `BackpackContents` 的第五个字段 `trash`

**Decision**: 新增 `TrashContents`（`List<ItemStack> groups`，**至多 9 个、非空、最新在前**）：
- `CODEC`：`ItemStack.OPTIONAL_CODEC.listOf()`，读入时丢弃空栈并截到 9 个；
- `STREAM_CODEC`：`ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9))`，解码超限抛 `DecoderException`（原版的 list codec 自带）；
- `EMPTY` 常量。

`BackpackContents` 增加 `trash`（`optionalFieldOf("trash", TrashContents.EMPTY)`），`CODEC` 的 `group` 从 4 个字段到 5 个，`STREAM_CODEC` 的 `composite` 从 4 到 5 个字段（上限 6，足够）。`normalize/fit/copy/empty` 全部带上 `trash`，`BackpackInventory` 增加 `trash()/setTrash(...)` 并在 `fromContents/toContents` 里读写（照 `tags` 的写法）。

**Rationale**: 用户已定存在背包数据里、随背包保存、升级保留、旧背包读成空。018 的 `tags` 已经走通同样的路（`fit` 把 `tags` 原样带上，升级配方走 `fit`），这里照做，实现阶段要核对 `BackpackUpgradeRecipe` 确实经过 `fit`/`copy`。垃圾桶**物品**不持有内容：把它取走，内容仍在背包里，放回右侧区又出现（spec FR-012）。

**Alternatives considered**: 把内容存在垃圾桶物品自己的组件里（用户已否决，取走即带走会让"右侧区还在不在"变得复杂）；存在玩家数据里（已否决）。

## Decision 3: 追加/拿起/恢复是泛型纯逻辑 `TrashBin<T>`

**Decision**: 新增无 Minecraft 依赖的 `TrashBin`，建在已有的 `QuickTransferLogic.StackOps<T>` 上（生产适配器 `ItemStackTransferOps` 已经存在）：

- `MAX_GROUPS = 9`。
- `push(ops, groups, stack)`：空栈 → 原样；否则把 `stack` 放在第 1 个位置，原有的依次后移；结果超过 9 个时**最后一个被挤出**，作为 `destroyed` 返回。**不合并**同种物品。
- `take(groups, index)`：返回被拿起的那一组和去掉它之后的新列表；空下标/越界 → 空结果。
- `restore(ops, groups, indices, targetOrder, getTarget, setTarget)`：下标先**去重并升序**；对每一组调用已有的 `QuickTransferLogic.moveInto(...)`（先叠进同种堆叠，再填空格）；该组完全放下 → 从列表里移除；放下一部分 → 该组留在**原位**，只是数量变成剩余的；一点也放不下 → 原样。返回新列表。

**Rationale**: `moveInto` 已被 011/012 验证过，"先叠后填"的规则就是它，不再重写。三个操作都是列表变换，容易做穷尽单测（空、满 9、溢出、拿起中间一组后前移、部分恢复、主区域满）。

## Decision 4: 一个请求包 + 一个同步包；协议 `"8"` → `"9"`

**Decision**:
- 客户端→服务端 `BackpackTrashPayload(byte action, int[] slots, boolean screenOpen)`：`ADD`（无下标）、`TAKE`（恰好一个下标）、`RESTORE`（1～9 个互不相同的下标）。下标是**垃圾格下标 0..8**，不是物品 id。解码时数量超 9 抛 `DecoderException`。
- 服务端→客户端 `BackpackTrashContentsPayload(TrashContents)`：整份内容。发送时机：`BackpackNetwork.sendSession(open)` 里紧跟标签包之后；每次垃圾桶内容变化之后；服务端因为"垃圾桶不在右侧区"等原因忽略请求时也补发一次（让客户端的显示追上真相，数据本身零变化）。
- `BackpackNetwork`：`versioned("8")` → `versioned("9")`，注册两个新包。

**服务端流程**（`BackpackTrashPayload.handleServer`，细节见 contracts）：解析并核对背包 → 加载 → **右侧区里确实有垃圾桶**（按区域整体内容）→ `TrashRequest.validate` → ADD/TAKE：确定光标来源（`PanelCursor.of(screenOpen, menu, backpack)`，菜单分支先过 `ServerCompatGate.allows(USE_MENU_CURSOR, owner)` 再 `mergePanelCursorIntoCarried`）→ 执行 → 守恒核对 → 保存 → 发同步。RESTORE 不碰菜单光标，目标是主区域。

**Rationale**: 一个请求包装三个动作，和 018 的 `BackpackTagPayload(op, ...)` 同构，注册和测试都少一套；整份同步最多 9 组，简单可靠；会话包字段已满（6 个），所以像 018 一样另发包。`screenOpen` 沿用 `BackpackClickPayload`/`BackpackCursorGesturePayload` 的"由客户端报告、服务端据此选光标来源"。

## Decision 5: 单个物品栈的体积保护

**Decision**: ADD 前，服务端把要丢进去的这个物品栈用 `ItemStack.OPTIONAL_STREAM_CODEC` 试编码到一个临时 `RegistryFriendlyByteBuf`，字节数超过 `TrashBin`/`TrashContents` 旁的常量 `MAX_GROUP_ENCODED_BYTES` 就**整个拒绝**（零变化，聊天提示"这件物品的数据太大，放不进垃圾桶"，中英文各一份）。上限初值取 **96 KiB**（9 × 96 KiB = 864 KiB，低于服务端→客户端自定义包 1 MiB 的上限，并留出余量），**实现时用真实的满载潜影盒、满载嵌套背包测量后再定**。

**Rationale**: 垃圾桶最多 9 组，每组原样保存带全部组件；如果一组是装满的网络背包，整份同步包可能超限，客户端会被踢。这是新引入的风险（光标上的物品原来最多随菜单同步，不会 ×9）。拒绝只发生在极端物品上，和"放进去永远成功"的承诺冲突很小，要在 DELIVERY 里如实写明。**这条是 spec 没写的内部保护**，登记在"仍不确定"，建议补进 spec 的 Edge Cases 与 FR-018。

**Alternatives considered**: 超限时静默丢弃（会丢物品，违反守恒，否决）；同步时只发缩略（客户端要显示物品图标与数量，需要完整栈，否决）。

## Decision 6: "限放 1 个"= 一个纯函数，规则是"不比放入前多"

**Decision**: `ToolAreaRules.canPlace(area, stack)` 改为

```java
static <T> boolean canPlace(PanelArea area, T incoming, int slotIndex, List<T> areaSlots,
                            Predicate<T> isTrashCan, Predicate<T> isTagManager)
```

- 只有 `RIGHT` 区限制垃圾桶、`BOTTOM` 区限制标签管理器，其余区域和其余物品一律放行。
- 设 `kind` 为该区域的限制类别，`incoming` 不属于它 → 放行。
- `before` = 区域内属于 `kind` 的格子数；`replaced` = 被点击的格子里当前就是 `kind` 则为 1；`after = before - replaced + 1`。
- **放行当且仅当 `after <= 1`（上限）或 `after <= before`（不增加）**。

后半句让 018 遗留的"下方区已有 2 个标签管理器"不被删、同格交换仍允许，但第三个放不进去。

调用点：`BackpackClickPayload.handleServer` 里已有的那一处（`area.isTool()` 且手里有东西时）。`held` 的来源（菜单携带物 / 主手 / 面板光标）已经算好，直接传入；`areaSlots` 用 `inventory.handler(area)` 取出的整个区域内容（**不是可见格子**）。被拒绝时沿用已有的"重发该格更新并返回"。客户端在 `FloatingPanelInput` 的工具区分支里用**同一个纯函数**预判（读 `ClientBackpackView`），拒绝时发聊天提示且不发请求。

**Rationale**: 这是 016 留给"以后限制某工具只能放某区域"的唯一入口；工具区格子只有点击一条放入路径（含 Alt 交换），一处就够。规则写成"不比放入前多"，同时满足用户确认的三条：新放入被拒、同格交换允许、已有的多个不删。

## Decision 7: 垃圾桶面板画在浮动面板矩形之外，几何是纯类

**Decision**: `TrashPanelLayout.of(panelX, panelY, panelWidth)` 纯算：
- 外框左上角 `x = panelX + panelWidth + 1`（它的 1 像素黑色外框与浮动面板右侧外框重合，视觉上贴在一起），`y = panelY`（顶端对齐）。
- 内容：标题栏高 `TITLE_H = 14`，下面 3×3 个 `PanelLayout.SLOT = 18` 的格子，四周内边距 `PAD = 4`。外框宽 `2*PAD + 3*SLOT = 62`，高 `TITLE_H + 3*SLOT + PAD = 72`。
- `rect()`、`titleRect()`、`cellRect(i)`、`cellAt(x, y)`（0..8 或 -1）、`contains(x, y)`（整个外框，含标题栏与空隙）。

`TrashPanelState` 是单例，只有一个 `open` 布尔和 `toggle()/collapse()/reset()`，不保存别的（spec 假设：展开状态不跨关闭浮动面板保存，所以关闭面板或离开世界就 `collapse/reset`）。渲染用 `fill` 与字体：外框和底色同 `drawVanillaPanel` 的配色，标题栏文字"垃圾桶"（`trash.mcfloatingbackpack.title`），格子用 `COLOR_SLOT_RIGHT`（和右侧区同色，暗示它属于右侧区）的凹槽，悬停高亮与物品绘制沿用 `drawSlotWell`/`COLOR_SLOT_HOVER`。**画在 `OVERLAY_Z` 的同一个 `pushPose` 里**，在浮动面板内容之后、光标物品之前。

窗口缩窄时它本来就会被画到屏幕外，OpenGL 自然裁剪；鼠标不可能在窗口外，所以命中也自然只在可见部分，**不做特殊处理**。

**Rationale**: 与 018 的 `TagPanelLayout` 同构（纯几何 + 状态 + 渲染分离）；固定尺寸、不随面板缩放，是因为用户要求它是"小标题栏 + 3×3"，格子尺寸跟浮动面板的格子一致即可。

## Decision 8: 拖放松开——先清光标，再让原版收尾（最不确定的一条）

**问题**：spec FR-016 要求"光标非空、按下在垃圾桶外、在垃圾桶上松开 = 丢进去"。松开事件有两种来源：

(a) **按下由浮动面板消费**（主区域、工具区、或者面板光标）。这是我们自己的手势：主区域按下且光标非空时 `PanelDragInput` 进入 `DISTRIBUTING`（点击推迟到松开）；松开时 `settle` 会按选中的格子数结算（1 格 → `CLICK`）。
(b) **按下落在原版容器界面的格子上**（面板没消费）。原版 `AbstractContainerScreen` 在"光标为空"时按下就立即拿起并置 `skipNextRelease`；"光标非空"时进入快速分配（`isQuickCrafting`），**松开时**再做放置：若松开点在 GUI 外就是 `-999` 扔出物品，在某个格子上就点那个格子（可能是被垃圾桶遮住的格子）。

**Decision**:
- 在 `FloatingPanelInput.onMouseButton` 的 `GLFW_RELEASE` 分支**最前面**（早于 `PanelSelectionInput.onRelease`/`PanelDragInput.onRelease`）调用 `TrashPanelInput.onRelease(...)`。它用 `TrashDropTracker` 判断"左键、按下在垃圾桶外、松开在垃圾桶外框内、无 Shift/Ctrl"。满足则：
  1. `PanelDragInput.cancel()`（(a) 的手势不结算，不发 `CLICK`/`DISTRIBUTE`）；
  2. 发 `BackpackTrashPayload(ADD, [], screenOpen)`；
  3. **客户端预测**：把当前光标清空（菜单携带物 `menu.setCarried(ItemStack.EMPTY)`，或面板光标 `applySlotUpdate(MAIN, -1, EMPTY, EMPTY)`），服务端的回执会校正；
  4. 返回值：**若这个松开的按下被面板消费过**（`PRESSES` 里有）→ 吞掉（(a)）；**否则放行**（(b)），让原版带着已清空的光标收尾——原版此时走"光标为空"的分支，不再扔出、不再点格子，并且正常复位 `isQuickCrafting` 等状态。服务端先收到我们的 `ADD`，再收到原版随后发的点击/分配包，那时服务端的携带物已空，原版的包对它是空操作。
- 客户端不管光标是否看起来为空都发 `ADD`（Shift/Ctrl 按住时不发）：面板光标的"拿起"要等服务端回执，一次快速的"按下—拖—松开"可能早于回执，此时客户端看到的光标仍是空的；服务端按真实内容决定，光标为空就什么也不做。
- 无论如何，服务端处理完 `ADD`（含被忽略）都补发一次光标同步。

**Rationale**: 吞掉 (b) 的松开会让原版停在 `isQuickCrafting = true`，之后它的点击都被忽略；放行但不清光标又会扔物品。"先清光标再放行"让两边都收尾干净，且不需要访问原版的受保护字段，也不需要反射。

**仍不确定**：原版 `mouseReleased` 在"携带物被我们清空 + 已经拖过若干格子"时的实际行为（它会照 `quickCraftSlots` 发分配包，但服务端携带物已空）。**实现时先读源码确认，再在客户端用箱子和玩家物品栏各测一次**（quickstart 的 Q16～Q19）；如果仍有残留状态，退路是：对容器界面改成"吞掉松开 + 通过 `Screen#mouseReleased` 在安全坐标补发一次松开"，或者把"拖放"在容器界面上降级为只支持点击（需要用户同意，因为会改 spec）。

**Alternatives considered**: 只在 (a) 里支持（容器界面里只能点击垃圾桶）——违反用户明确要求的"原版容器本来就支持按住拖到另一个格子松开"；在 `mouseDragged` 里一路拦截（原版拿不到完整拖动，状态更乱）。

## Decision 9: Ctrl 恢复——`Side.TRASH` 自成一组

**Decision**: `QuickTransferGesture.Side` 增加 `TRASH`。把 `intoBackpack(Side)` 的二值判断换成"组号"比较：`INVENTORY`、`CONTAINER` 同组（进背包），`PANEL`（出背包），`TRASH`（出垃圾桶，进主区域）各自一组；一次拖动只接受与起点同组的格子。`QuickTransferInput` 增加：
- `beginFromTrash(index, x, y)`：Ctrl 按住、Shift 没按、**光标为空**、垃圾桶展开且该点在格子上。
- `resolveAt` 先判断垃圾桶外框，命中某格就返回 `Hit(Side.TRASH, i, hasItem)`；落在外框的非格子部分返回 null。
- `send` 把 `TRASH` 的下标发成 `BackpackTrashPayload.of(RESTORE, ...)`（`screenOpen = false`，目标是主区域，不涉及菜单）。
- 光标非空时 Ctrl+点击垃圾桶，按 FR-017 当作**追加**（不是恢复）。

服务端 `RESTORE`：下标升序，目标顺序是主区域 0..size-1，调用 `TrashBin.restore`，保存，发整份垃圾桶内容与 `sendFullSync`（主区域变了很多格）。

**Rationale**: 011/012 的手势骨架已经通用（按组过滤、按键防重、沿路径采样），只需要加一个"组"。不引入反向（主区域→垃圾桶）手势，所以 `PANEL` 组与 `TRASH` 组方向不同、互不接纳。

## Decision 10: 013/014 手势已经只认主区域，只补"松开在垃圾桶上"

**Decision**: `PanelDragInput.slotAt`/`PanelSelectionInput.slotAt` 都走 `PanelSlots.indexAt`（只认主区域格子），所以垃圾桶、其它工具区本来就不会被它们当作格子。本 feature 只补：松开在垃圾桶上的 `GLFW_RELEASE`（Decision 8 的第 1 步取消 `PanelDragInput`）。离开主区域后再回来继续手势的现有行为**不改**（011～020 验证过）；spec FR-016 已按用户确认改成"手势只作用于主区域格子；在垃圾桶上松开不结算"（2026-11-05），与这里的实现一致。

**Rationale**: 完全按字面做"一出主区域就取消"会改变 013 已验证的行为，也会让"按下后稍微滑出边缘再松开"失去点击效果；用户的目的是"松开在垃圾桶不要被当作 013 结算"，这条已经满足。

## Decision 11: JEI 避让矩形多报一个

**Decision**: `FloatingBackpackJeiPlugin.getGuiExtraAreas` 在已有的浮动面板矩形之外，当 `TrashPanelState.get().isOpen()` 时再追加一个 `Rect2i`，位置/大小取自 `TrashPanelLayout.of(state.getPosX(), state.getPosY(), state.getWidth())`。仍受 `CompatConfig.policy().specificAdaptation("jei")` 控制（020）：GENERIC/OFF 下两个矩形都不报。

**Rationale**: 008 的适配点就是这个方法；该类已经在 `compat/jei/` 下，不改变宪法 VII 对第三方类型的位置要求。

## Decision 12: 面板生命周期与点击消费

**Decision**:
- `FloatingPanelEvents.onClientTick`：垃圾桶面板展开但（浮动面板不可见 或 `!ClientBackpackView.get().hasTrashCan()`）→ `collapse()`。`hasTrashCan()` 扫描**右侧区整个列表**（不是可见格子），所以滚动/缩小不会误收起。`onLoggingIn/onLoggingOut` 里 `TrashPanelState.get().reset()`，`FloatingPanelInput.resetPressTracking()` 同时清 `TrashDropTracker`。关闭浮动面板（`closePanel`/会话关闭）时也收起。
- 按下：`FloatingPanelInput.dispatchMouseButton` 的 PRESS 分支最前（在关闭按钮/缩放手柄判断之前，因为垃圾桶外框与它们不重叠）调用 `TrashPanelInput.onPress`，**垃圾桶外框内的左右键按下一律消费**（Shift 按住 → 只吞掉；Ctrl+光标空+有物品的格子 → Ctrl 恢复；光标非空 → `ADD`；光标空且点在有物品的格子 → `TAKE`；右键 → 吞掉不做事）。
- 滚轮：`scrollPanel` 里 `TrashPanelLayout.contains` 为真时消费但不做事。
- 右侧区点击分派：`FloatingPanelInput` 的工具区分支里，`RIGHT` 区、左键、光标空、无 Shift/Ctrl、点中的是垃圾桶 → `TrashPanelState.toggle()`，与 018 的 `BOTTOM + 标签管理器` 并排。

**Rationale**: 全部沿用 018 已经走通的挂接点，不新增事件订阅。

## Decision 13: 图标——ludo.ai 生成后缩成 16×16

**Decision**: 与 018 相同的流程：用 `mcp__ludo-ai__createImage` 生成一张铁灰色垃圾桶的像素风原图，`design/demo/tools/make_trash_can_icon.py` 缩成 16×16（最近邻/调色板限制），放到 `textures/item/trash_can.png`；原图与脚本在 `design/` 下（不随包、不被运行时代码引用，宪法 Additional Constraints）。生成方式写进 DELIVERY；若生成不理想则自绘并如实记录。

## Decision 14: 销毁日志

**Decision**: `TrashBin.push` 返回被挤出的那一组；服务端处理 ADD 时在 `MCFloatingBackpack.LOGGER.debug("Trash can destroyed {} x{}", itemId, count)` 记一行。是否带玩家名：带 `player.getGameProfile().getName()`（调试级别，便于服务器管理员排查"我的东西去哪了"）。

## 仍不确定 / 需要在实现时验证

1. **原版容器界面的松开流程**（Decision 8）：源码确认 + 箱子/玩家物品栏各一次实测；有残留状态则走退路。
2. **体积保护的上限值**（Decision 5）：用真实满载潜影盒与嵌套背包测量；以及它是否该写进 spec。
3. ~~spec FR-016 措辞的收窄~~：用户已确认并改入 spec（2026-11-05）。
4. **`BackpackUpgradeRecipe` 是否经过 `fit`**：核对 `trash` 在升级后保留（和 018 的 `tags` 同路，预期没问题）。
5. **Alt 交换主手时的客户端预判**：Alt 在容器界面外才是"和主手交换"（`PanelDragInput` 的注释），预判要用与服务端 `held` 相同的来源；实现时对照 `BackpackClickPayload`。
6. **图集度量**：沿用 018 的做法，度量不了就写"未度量"。
7. **Windows/Linux**：无法验证；Ctrl 在 macOS 上是 Command（`Screen.hasControlDown()`），行为与 011 一致。
