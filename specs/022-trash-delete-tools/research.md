# Research: 垃圾桶的删除手段与保护

结论来自读本仓库源码（021 的 `BackpackTrashPayload`/`TrashBin`/`TrashPanelLayout`/`TrashPanelInput`、018 的 `TagPanelModel`/`TagPanelInput`/`BackpackTagPayload`/`TagBook`/`PresetTags`、014 的 `PanelSelection`、019 的 `PanelHostScreen`/`HostKeyRouter`、`FloatingPanelEvents`/`FloatingPanelInput`/`FloatingPanelRenderer`、`FloatingBackpackKeys`、`ContainerMenuAccess`、`MenuOwner`/`ServerCompatGate`）与 NeoForge 21.1.251 / 原版 1.21.1 的源码（`ScreenEvent.KeyPressed`、`KeyboardHandler`、`AbstractContainerScreen`、`Slot`、`ChestMenu`，从 `build/moddev/artifacts/neoforge-21.1.251-sources.jar` 读取）。**没有在游戏里验证**的地方在"仍不确定"里列出。

## Decision 1: 新增一个"放进垃圾桶"请求，021 的请求包不动

**Decision**: 新增客户端→服务端 `BackpackTrashSendPayload(byte source, boolean confirmed, int containerId, int[] slots)`：
- `DUMP`(0)：无下标；服务端自己筛选（Decision 3）。
- `MAIN_SLOTS`(1)：1～108 个互不相同的主区域格子下标；服务端按下标升序取**非空**格子的整个栈。
- `MENU_SLOT`(2)：恰好 1 个菜单格子号（< 256）与 `containerId`；服务端按 Decision 8 核对后取走整栈。

021 的 `BackpackTrashPayload`（ADD/TAKE/RESTORE，下标上限 9）保持原样。`BackpackNetwork`：`versioned("9")` → `versioned("10")`，注册新包。

**服务端流程**（`handleServer`，细节见 contracts）：解析并核对背包 → `hasTrashCan()` → `TrashSendRequest.validate` → 确定要放入的栈列表（Decision 3/8）→ 列表为空则忽略 → `TrashDeletePlan.destroyed(现有组数, 放入组数)`，超过 9 且没带 `confirmed` → 整个拒绝 → 每一组的体积保护（复用 021 的 `MAX_GROUP_ENCODED_BYTES`，任何一组超限整个拒绝）→ 依次 `TrashBin.push` 并从来源取走 → 守恒核对（"垃圾桶 + 主区域 + 来源菜单"的总数，除 `destroyed` 外必须相等）→ 保存 → 同步。

**Rationale**: 021 的包是"一个光标栈 / 一个下标"的小动作，下标上限和解码校验都按 9 写的；批量放入的语义（来源、已确认、菜单 id）完全不同，另开一个包比给旧包硬塞字段清楚，也不会动 021 已被用户验证过的路径。一个包覆盖三种来源，因为它们的后半段（算销毁数、确认、体积保护、push、同步）完全一样。

**Alternatives considered**: 给 `BackpackTrashPayload` 增加动作（要放宽 9 的上限，动到已验证的校验，否决）；三个包各一种来源（后半段重复三份，否决）。

## Decision 2: 销毁数与确认阈值是一个纯函数，两端共用

**Decision**: `TrashDeletePlan`：`THRESHOLD = 9`；`destroyed(existingGroups, incomingGroups) = max(0, existing + incoming − 9)`；`needsConfirm(destroyed) = destroyed > THRESHOLD`。客户端在发请求前用它决定要不要弹确认，服务端用它决定要不要拒绝——**同一个类**。

**Rationale**: spec 里"销毁数包括这次自己放进去又被挤掉的"就是这个公式；单次 Delete 悬停一格（incoming = 1）最多销毁 1 组，永远不弹。

## Decision 3: 倾倒筛选 = 主区域 + trash 标签 + 保护列表 + 只差耐久度

**Decision**: 纯泛型 `TrashDump.select(size, stackAt, isEmpty, hasTrashTag, isProtected, onlyDefaultData)` 返回要倾倒的主区域下标（升序）。一个格子被选中当且仅当：非空、该物品分类在背包标签里带 `trash`（`ItemTags.tagsOf(id).contains("trash")`）、物品**不在**保护标签里、栈的数据**只差耐久度**。适配器在服务端用真实栈计算：
- `hasTrashTag`：`backpack.tags().tagsOf(registryId).contains("trash")`。
- `isProtected`：`stack.is(ModTags.NEVER_TRASH)`。
- `onlyDefaultData`：`StackExtraData.onlyDamage(ids)`，`ids` = `stack.getComponentsPatch()` 里每个组件类型的注册名（`BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)`）。`DataComponentPatch` 只记录与物品原型**不同**的组件（`PatchedDataComponentMap.set` 在值等于原型时会去掉补丁项），所以"补丁里除了 `minecraft:damage` 没别的键"就是"栈与同种默认栈只差耐久度"。

客户端用同一个 `TrashDump.select`（读 `ClientBackpackView`）预先数出要倾倒几组，决定要不要确认；物品标签（`ModTags.NEVER_TRASH`）会同步给客户端，所以 `stack.is(tag)` 在客户端也成立。

**Rationale**: spec 要求"任何会让栈与同种默认栈不同的数据"，补丁是原版自己对"与默认不同"的表示，不需要枚举组件类型（附魔、名称、容器内容、药水、旗帜、头颅、地图、书内容……都自动覆盖，将来新增的组件也一样）。耐久损耗的组件 id 是 `minecraft:damage`。

**仍不确定**：某些物品的原型本身带组件、补丁里是否会出现与默认值相同的"显式项"——实现时读 `PatchedDataComponentMap` 源码确认，并用真实栈测一次（Q9）。

## Decision 4: 标签面板只拦截"垃圾"，用"灰色预设 + 拒绝添加"而不是灰色加号（对 spec 措辞的收窄）

**问题**：spec FR-008/US2 写"加号变灰……添加别的标签不受影响"。但 018 的加号是**与标签无关**的入口（点加号才出文本框，之后才选/输入标签），它灰了就什么标签都加不了，和"只拦截垃圾"矛盾。

**Decision**:
- `TagPanelModel` 增加 `protectedItems`（选中的分类里在保护标签中的），`TagProtection.blocks(tag, protectedCount) = tag.equals("trash") && protectedCount > 0`。
- **加号保持可用**（除非 018 原有的"已满"）。
- 下拉里的"垃圾"预设：选区含受保护物品时**画成灰色**，悬停弹出提示"这些物品不能标记为垃圾：……"；点击它、用方向键选中后回车、或在文本框里手打 `trash` 回车，都**不添加**：文本框保持编辑状态，聊天区给一句提示（列出物品名）。
- 服务端 `BackpackTagPayload`：解析出分类后，若 `op == ADD` 且 `TagProtection.blocks(tag, 受保护分类数)`，整个请求拒绝、零变化（再发一次整份标签，让客户端追上）。
- 其它标签（含"食物"）照常。

**需要用户确认**：这与 spec 里"加号变灰"的字面不同；目的一致（受保护物品不能被标成垃圾、有悬停提示、服务端拒绝）。建议把 FR-008 与 US2 场景 1、3 的"加号变灰"改成"'垃圾'预设变灰、添加被拒绝"。

**Alternatives considered**: 受保护物品被选中时整个加号变灰（连"食物"都不能加，违背"只拦截垃圾"，否决）；点加号后再检查（文本框里输什么都先放行，最后才报错，体验差，否决）。

## Decision 5: 标题栏图标——纯几何 + `fill` 绘制，英文标题缩短

**Decision**: `TrashPanelLayout` 增加 `iconRect(Icon)` 与 `iconAt(x, y)`：两个 9×9 图标右对齐在标题栏里（右内边距 4，两图标间隔 2：删除在最右，倾倒在它左边），`Icon { DUMP, DELETE }`；`TITLE_H`、`PAD` 不变，所以 021 的几何测试仍成立。文字"垃圾桶"在图标左边按剩余宽度截断（`font.plainSubstrByWidth`）；英文标题键 `trash.mcfloatingbackpack.title` 由 "Trash Can" 改成 "Trash"（物品名仍是 "Trash Can"），否则 62 像素宽的标题栏放不下。

图标的绘制全部 `fill`：倾倒 = 一个向下的小三角加一条托盘线；删除 = 一个 `×`（两条对角像素线）。置灰 = 同形状换灰色。悬停时底色高亮并 `renderComponentTooltip`。

**Rationale**: spec 要求不新增贴图；图标小，像素画用 `fill` 即可；沿用 018 的做法（`TagPanelRenderer` 的加号、✕ 也是 `fill`+字符）。

## Decision 6: Delete 键——`KeyPressed.Post` + GUI 上下文映射 + 纯判定类

**Decision**:
- `FloatingBackpackKeys.DELETE_TO_TRASH = new KeyMapping("key.mcfloatingbackpack.delete_to_trash", KeyConflictContext.GUI, InputConstants.Type.KEYSYM.getOrCreate(Minecraft.ON_OSX ? GLFW_KEY_BACKSPACE : GLFW_KEY_DELETE), CATEGORY)`，在 `registerKeys` 里注册；语言键中英文各一份。
- 事件：`ScreenEvent.KeyPressed.Post`（`FloatingPanelEvents` 新增订阅）。读 `KeyboardHandler` 确认：`Post` 只在 `screen.keyPressed(...)` 返回 false 时才触发（`if (!handled) handled = Post(...)`），且 `Post` 被取消即视为"已处理"。所以 JEI/配方书搜索框有焦点时 Backspace 被搜索框消费，到不了这里；条件不满足时我们**不取消**事件，不吞键。
- 判定：纯类 `DeleteKeyRules.decide(Inputs)`，输入 = 面板可见、右侧区有垃圾桶、确认待定、标签面板展开、标签文本框编辑中、界面种类（HOST/INVENTORY/CHEST/OTHER）、选区是否有物品、悬停的主区域格子是否有物品、悬停的菜单格子是否允许且有物品；输出 `Target { NONE, MAIN_SELECTION, MAIN_HOVER, MENU_SLOT }`。优先级：选区非空（且至少一格有物品）→ `MAIN_SELECTION`（只在宿主/容器界面上都生效，因为面板在两者上都画）；否则主区域悬停有物品 → `MAIN_HOVER`；否则界面是物品栏/箱子且悬停格子允许 → `MENU_SLOT`；否则 `NONE`。
- 019 的宿主：`PanelHostScreen.keyPressed` 用 `boundMappings`（只算"在无界面上下文里也活动"的映射）分类；`DELETE_TO_TRASH` 是 GUI 上下文，无界面时不活动，所以 Delete 在宿主上是 `PASS`（返回 false），`Post` 照常触发，也不会被当成"让出再重放"。玩家把它改成一个同时绑着世界动作的键（如 W）时，宿主会先吞掉，Delete 不触发——这是改键冲突的代价，写进已知限制。
- 悬停格子：主区域用 `PanelSlots.indexAt(state, guiMouseX, guiMouseY)` 并检查真实格子数；菜单用 `AbstractContainerScreen.getSlotUnderMouse()`（NeoForge 提供）。

**Rationale**: 用户已确认这个做法；`Post` 事件天然解决"文本框抢键"，GUI 上下文天然避开 019 的让出机制。

## Decision 7: 确认弹窗——整面板蒙版，所有输入先过它，ESC 在 `Pre` 里取消

**Decision**:
- 状态：`TrashConfirmState`（单例）：`pending`（来源 + 主区域下标快照）、`destroyed`、`screen`（打开时的 `Minecraft.screen`）。`open(...)`、`cancel()`、`isPending()`。
- 几何：`TrashConfirmLayout.of(panelX, panelY, panelW, panelH, textLines, lineHeight)`：蒙版 = 整个浮动面板矩形；弹窗宽 = `min(panelW − 12, 120)`、居中；高 = `8 + 行数×lineHeight + 6 + 14 + 8`；两个按钮并排在弹窗底部（各 `(宽 − 24) / 2`、高 14）。`hit(x, y)` 返回 `CONFIRM`/`CANCEL`/`DIALOG`/`MASK`/`OUTSIDE`。最小面板（3×3 主格 + 工具区 = 104 × 96）下，文字按 `font.split(text, 弹窗宽 − 12)` 折行（≤ 3 行）后弹窗高 ≤ 63，放得下。
- 按下：`FloatingPanelInput.dispatchMouseButton` 的 PRESS 分支**最前面**先问 `TrashConfirmInput.onPress`：待确认时**所有左右键按下都被消费**；点"确认"→ 发请求（带 `confirmed = true`）、清选区、关弹窗；点"取消"或任何其它位置（弹窗内空白、蒙版、面板外、垃圾桶面板）→ 只关弹窗。因为按下被消费，`PRESSES` 会吞掉对应的松开，`TrashDropTracker` 的记录在 `notePress` 里照常，但松开不会触发拖放（`TrashPanelInput.onRelease` 看到的按下记录是"在外"，需要在确认期间跳过——见下）。
- 滚轮：`scrollPanel` 里待确认时一律消费。
- ESC：`ScreenEvent.KeyPressed.Pre` 里，`hostsPanel(screen)` 且待确认且 `keyCode == ESCAPE` → `cancel()` 并取消事件。宿主与容器界面都看不到这个 ESC，所以既不关面板，也不弹暂停菜单、不关容器。
- 丢弃：每个客户端 tick：待确认但（面板不可见 或 `!hasTrashCan()` 或 `minecraft.screen != 记录的界面`）→ `cancel()`。
- 渲染：`FloatingPanelRenderer.render` 在垃圾桶面板之后、光标物品之前画 `TrashConfirmRenderer`（蒙版 `fill(0xA0000000)`、弹窗底色同面板、两个按钮用 `fill` + 字体）。
- 确认期间松开的处理：`TrashPanelInput.onRelease` 在待确认时直接返回 false（弹窗打开前的拖动按下仍可能在途，不当作丢入）。

**Rationale**: 把"所有输入先过弹窗"放在分派入口最前面，比在每个手势里各加一个"待确认就别动"更不容易漏；ESC 用已有的 `Pre` 事件（018 已经用它接管键盘），不需要碰宿主的 `keyPressed`。

## Decision 8: 原版界面里的 Delete——客户端认两类界面，服务端按菜单核对

**客户端**（`DeleteKeyRules` 的"界面种类"与"格子允许"）:
- `InventoryScreen`（生存模式玩家物品栏，菜单 = 玩家的 `inventoryMenu`，`containerId == 0`）：悬停的格子 `slot.container == player.getInventory()` 且 `slot.getContainerSlot()` ∈ [9, 35]。
- 箱子界面：`screen.getMenu() instanceof ChestMenu` 且 `MenuOwner.ownerOf(menu).equals("minecraft")`：悬停格子要么是箱子自己的格子（`slot.container == chestMenu.getContainer()`），要么玩家物品栏 9～35。
- 创造模式物品栏（`CreativeModeInventoryScreen`）、潜影盒/漏斗/熔炉/其它模组的菜单 → `OTHER`，不处理、不吞键。
- 这个允许规则写成纯函数 `DeletableSlots.allowed(ownInventory, containerSlot, chestOwnSlot)`，客户端与服务端共用。

**服务端**（`MENU_SLOT`）按顺序核对，任一不满足整个丢弃、零变化：`containerId == player.containerMenu.containerId`；菜单是 `player.inventoryMenu` 或 `ChestMenu`；`MenuOwner.ownerOf(menu)` 是 `minecraft`；`ServerCompatGate.allows(TAKE_FROM_CONTAINER, owner)`；菜单格子号在 `[0, menu.slots.size())`；`slot.container` 判断出 `ownInventory`/`chestOwnSlot` 后过 `DeletableSlots.allowed`；`slot.hasItem()` 且 `slot.mayPickup(player)`。通过后 `ItemStack removed = slot.remove(slot.getItem().getCount())`，放进垃圾桶，`slot.setChanged()`，最后 `menu.broadcastChanges()`（正在看同一个箱子的玩家随后收到同步）。光标（菜单携带物）不动。

**Rationale**: 用 `slot.container` 而不是裸的菜单格子号判断"玩家物品栏第 9～35 / 箱子自己的格子"，菜单布局变化（箱子行数不同、玩家物品栏接在后面）也不会错；创造模式界面用的是别的菜单，天然排除。

**仍不确定**：`slot.remove` 对 `ChestMenu` 里的 `Slot` 是否需要 `onTake`——普通槽没有副作用，实现时读 `Slot` 源码确认；多人同步要两个客户端才能验证（没有就写"未验证"）。

## Decision 9: 倾倒/删除后清选区，倾倒无物可倾时聊天提示

**Decision**: 删除图标与 Delete（`MAIN_SELECTION`）发出（或确认后发出）请求时客户端 `PanelSelection.get().clear()`。倾倒时没有候选 → 不发请求，聊天区 `message.mcfloatingbackpack.trash.nothing_to_dump`。请求被服务端拒绝（背包已变）→ 服务端给玩家一条 `message.mcfloatingbackpack.trash.changed`（"背包内容已变化，操作已取消，请重试"），客户端随同步更新显示，**不自动重新弹确认**。

**Rationale**: 选区里的格子已空，留着只会让图标继续可点；"自动重新弹"需要服务端回传销毁数，复杂度不值得，一条提示足够（spec 的"客户端重新给出提示"按这个理解）。

## Decision 10: 保护标签 JSON 与 `ModTags`

**Decision**: `data/mcfloatingbackpack/tags/item/never_trash.json`：`replace: false`，`values` 里——本模组 7 个物品 id（五档背包、`tag_manager`、`trash_can`）、17 个潜影盒（`shulker_box` 与 16 色）、`dragon_egg`、`nether_star`、`elytra`、`totem_of_undying`、`beacon`、`heart_of_the_sea`、`enchanted_golden_apple`，以及 `bundle` 与 16 色收纳袋——后两者写成 `{"id": "...", "required": false}`，这样没启用该特性或该版本没有这些物品时不报错。`item/ModTags.java`：`TagKey<Item> NEVER_TRASH = ItemTags.create(ResourceLocation.fromNamespaceAndPath(MOD_ID, "never_trash"))`。标签数据由服务端随数据包同步给客户端，所以客户端 `stack.is(NEVER_TRASH)` 成立。

**Rationale**: 数据包可编辑正是用户要求（整合包作者加别的模组物品）；`required: false` 是原版标签对可选条目的标准写法。

## Decision 11: 图标与弹窗不新增贴图，度量沿用做法

**Decision**: 两个图标、蒙版、确认框、按钮全部 `fill` 与字体绘制；保护标签是数据文件。实现完成时按 018/021 的做法度量图集：先 `pgrep` 确认没有游戏在跑，再跑 `check_atlas_budget.sh`，度量不了就写"未度量"。

## 仍不确定 / 需要在实现时验证

1. **Spec FR-008/US2 的"加号变灰"**（Decision 4）：建议改成"'垃圾'预设变灰、添加被拒绝"，等用户确认。
2. **`getComponentsPatch()` 判断"只差耐久度"的覆盖面**（Decision 3）：读 `PatchedDataComponentMap` 并实测附魔、命名、药水、旗帜、潜影盒、头颅、地图、书的栈。
3. **`Post` 事件在宿主/物品栏/箱子三种界面的实际流向**（Decision 6）：读过源码，实测确认；以及玩家把键改到与世界动作冲突的键时的表现。
4. **箱子里取走整栈的同步**（Decision 8）：`Slot.remove` 的副作用；多人同步。
5. **确认期间按下与拖放松开的交互**（Decision 7）：确认弹窗打开时若有一个拖放正在进行，松开不能触发丢入。
6. **Windows/Linux**：无法验证；默认键 Delete 仅推断。
