# Research: 分类扩展：数据与规则

下面每条决定都标了依据：**已查证**是读了本仓库源码或本机游戏数据；**尚未验证**要等实现或游戏里才知道，验证前不得声称已满足。

## R1. 存储：扩展行另存，`items` 仍是档位容量

- **决定**：`BackpackContents` 新增第 7 个字段 `rows`（`RowSet<ItemStack>`：可见行列表 + 隐藏行列表）；`items` 只放档位容量那么多格，工具区、光标、标签、垃圾桶、黑名单都不变。
- **已查证**：`BackpackContents.fit(capacity)` 把 `items` 补齐或裁到容量，并用同一个 `capacity` 去 `tools.fit(size)`；`BackpackInventory.capacityOf(stack)` 取档位容量，被 `BackpackGate`（快照登记）、`BackpackUpgradeRecipe`、`BackpackInventory.fromItem` 使用；`SnapshotOps` 取 `contents.items()` 的前 108 格做预览、恢复时按容量判断档位。如果把扩展行的格子放进 `items`，这些地方都会把"容量"当成 108+，要全部改；另存则**一个都不用改**。
- **备选**：把行格子接在 `items` 后面——否决，理由同上，且 `fit` 会把它们当成"超出容量"裁掉。

## R2. 运行时视图：主区域 = 档位容量格 + 可见行的格子

- **决定**：`BackpackInventory` 的 `items` 处理器长度 = `baseCapacity + 可见行数 × 9`；新增 `baseCapacity()`；工具区容量用 `ToolAreaCapacity.of(area, baseCapacity)`（现在构造函数里用的是 `size`，会算错）；`hiddenRows` 单独保存、不进处理器。`toContents()` 把处理器的前 `baseCapacity` 格放回 `items`，后面按行拆回 `rows.visible`，再加上 `rows.hidden`。
- **已查证**：`BackpackInventory(int capacity)` 里 `ToolAreaCapacity.of(area, size)` 的 `size` 就是主列表长度；`fromContents(contents, capacity)` 用 `contents.fit(inventory.size())`；`toContents()` 用 `size()` 作容量。所以这三处必须区分"基础容量"与"含行的总数"。其余调用 `size()`/`getStackInSlot(i)`/`setStackInSlot(i, …)` 的地方（点击、转移、选区、拖拽）把"主区域"当作 `0..size()-1`，换成含行的总数后不用改。
- **尚未验证**：`size()` 的所有调用点里有没有"其实想要基础容量"的（实施时全文 grep `\.size()` 在 `BackpackInventory` 相关调用处逐个核对；已知需要改的三处见上）。

## R3. 逻辑行列表与核对（reconcile）

- **决定**：纯逻辑 `RowReconciler.reconcile(rows, placed, isEmpty, emptyCell, maxRows)`：
  1. 对每个类别（按固定的类别顺序）：可见行数 `V`、放着的数量 `N`；
  2. `V > N`：每少一个，若该类别可见行里有空行就**删最后一个空行**，否则把**该类别最后一个可见行**移到 `hidden` 末尾（`hidden` 的顺序就是隐藏的先后）；
  3. `V < N`：每多一个，若 `hidden` 里有该类别就取**最靠前的一行**追加到 `visible` 末尾，否则（可见 + 隐藏总数 < 35）新建一个空行追加到 `visible` 末尾；总数已达 35 就不产生行，把这个类别记入 `refused`；
  4. 返回新的 `RowSet`、`layoutChanged`（可见行的类别序列是否变了）、`refused`。
- **放着的数量**：三个功能区里每个物品栈按 `ExtensionPacks.categoryOf` 取类别、**每个栈按 1 个计**（`count > 1` 防御性地按 1 个并记日志），再加面板光标上的那一个。
- **为什么用"两个列表"而不是给每行一个隐藏序号**：隐藏的先后顺序就是列表顺序，保存与载入不需要额外字段；"最早隐藏"就是列表里该类别的第一个。
- **未知类别**（旧数据或将来版本留下的 id）：不属于任何已知类别，`N` 恒为 0，所以非空的会被移到 `hidden`、空的被删；永远不丢物品。
- **已查证**：行为取自 `docs/plan/category-extensions.md` 的已确认决策与 spec 的 US1 场景。
- **尚未验证**：任意放置/拿走序列下物品总数不变（SC-003）——用随机序列的单元测试验证，实现后才能声称。

## R4. 核对的触发点

- **决定**：**保存时**在 `BackpackInventory.toContents()`，**载入时**在 `BackpackInventory.fromContents()`；都用同一个静态入口 `reconcileFor(contents/inventory)`，放着的数量从当时的工具区与光标现算。
- **已查证**：所有保存都经过 `BackpackRef.saveInventory → BackpackInventory.saveToItem → toContents()`；所有载入都经过 `BackpackInventory.fromItem → fromContents()`；升级配方在 `BackpackUpgradeRecipe.assemble` 里直接用 `BackpackContents.fit(…)`（没有经过 `BackpackInventory`），所以那里也要带上 `rows`，但**不需要核对**（升级不改工具区与光标）；快照恢复用的是 `BackpackContents`，恢复之后下一次载入会核对。
- **一个必须处理的后果**：保存时核对可能让可见行的结构变了（例如一个扩展件被取走），此时服务端的 `BackpackInventory` 对象还是旧的格子数，而主区域的格子编号已经变了。所以 `BackpackRef.saveInventory` 比较"保存前可见行类别序列"与"保存后"，**不同就补发一次整份同步**（`BackpackNetwork.sendFullSync`）。已查证 `sendSlotUpdate` 会重新 `loadInventory` 并对越界下标返回空栈，所以补发整份同步之后再发的单格更新是安全的。
- **尚未验证**：补发整份同步的时机与"同一次请求里随后的 `sendSlotUpdate`"在客户端的先后（TCP 保序，整份同步先到）；需要在 036 有界面时才看得见。035 里没有扩展件物品，这条通路不会被触发。

## R5. 类别判定：两个来源

- **决定**：`CategoryMatcher<T>`：`matches(String categoryId, T stack)` = `dataMatches(categoryId, stack) || manualTags(stack).contains(categoryId)`。游戏一侧 `ItemCategoryMatcher`：
  - `dataMatches`：`stack.is(TagKey<Item> mcfloatingbackpack:extension/<id>)`；食物另加 `stack.has(DataComponents.FOOD)`；药水另加 `stack.has(DataComponents.POTION_CONTENTS)`；
  - `manualTags`：这只背包的 `ItemTags`（按物品注册 id 查）。
- **已查证**：标签文件放 `data/mcfloatingbackpack/tags/item/extension/<id>.json`（1.21 起目录名是单数 `item`；仓库已有 `tags/item/` 下的文件）；`ItemTags` 与 `PresetTags` 的现状见 spec "已查证的事实"；类别 id 与现有预设 id 重合 7 个，需要新增 `crop`、`potion`、`disc`。已用本机 1.21.1 / NeoForge 21.1.251 的数据核对过标签并入关系（见 spec 决策 4）。
- **标签文件的写法**：`{"replace": false, "values": [{"id": "#c:crops", "required": false}, …]}`；显式物品用普通字符串。
- **尚未验证**：服务端在 `ServerStartingEvent`/世界载入之后才有物品标签，**但判定只在玩家操作时调用**，不会在启动阶段调用；`required: false` 的标签不存在时整个文件仍可加载（原版标签加载器的行为，实施时用游戏日志确认没有报错）。

## R6. 放入规则：全部走"目标格顺序"和一个 `canPlaceMain`

- **已查证（读调用点）**：往主区域写入的地方都是"算目标下标序列再 `moveInto`/`setStackInSlot(i, …)`"：
  - 序列型：`BackpackQuickTransferPayload`（物品栏 → 面板，两处）、`BackpackTakeFromContainerPayload` → `ContainerToBackpack`（容器 → 面板）、`BackpackTrashPayload`（垃圾桶取回，`TrashBin.moveInto`）、`AutoPickupEvents` → `PickupInsert.insert`（目前 `order = 0..size-1`）；
  - 单格型：`BackpackClickLogic.click/applyTo`、`BackpackMenuBridge.clickWithMenuCarried`、`BackpackClickPayload` 里的"主手物品换进格子"、`BackpackCursorGesturePayload`（放入、拖拽分配、聚拢、`pickupAll`）。
- **决定**：
  - `BackpackInventory.mainTargetOrder(stack, matcher)`：先是"该物品匹配的可见行"的格子（按行的顺序，每行 9 个下标），再是 `0..baseCapacity-1`；序列型调用点分两段各调一次 `moveInto`（第一段的剩余作为第二段的输入）——这正好得到"每一段先并进已有的没满的栈，再放空格"。**没有扩展行或没有匹配行时序列就是 `0..size-1`，与现状一致。**
  - `BackpackInventory.canPlaceMain(slot, stack, matcher)`：`slot < baseCapacity` 恒为真；否则取该格所在行，隐藏行或越界 = 假；可见行 = `matcher.matches(row.category, stack)`。单格型调用点在放入前检查，不合格 = 拒绝、零变化（光标不变、格子不变），并发 `sendSlotUpdate` 把客户端拉回。
  - 拖拽：不合格的格子不能加入拖拽（`isValidDragSlot` 的调用处过滤）。聚拢：**取**永远允许，范围是主区域 + 可见行，隐藏行本来就不在 `0..size()-1` 里。
- **`QuickTransferLogic.moveInto` 不改**（只收"目标下标序列 + 取/存函数"，与行无关）。
- **垃圾桶、选区、Ctrl 转移**：从背包"拿出"的一律允许；往背包"放入"的走上面两条。
- **尚未验证**：调用点的清单是读代码得到的，实施时要再用 `grep -rn "setStackInSlot\|moveInto\|PickupInsert" src/main/java` 逐个核对，漏掉一个就会让物品进错行（任务里有一条专门的核对）。

## R7. 上限常量

- **已查证**：写死 108 的地方：`BackpackInventory.MAX_SIZE`（构造函数夹容量、`BackpackContents` 的两个编码与 `normalize/fit`、`BackpackSessionPayload`、`BackpackFullSyncPayload`、`BackpackClickPayload` 的格子范围）、`QuickTransferRequest.MAX_BACKPACK_SLOTS`（被 `BackpackQuickTransferPayload`、`BackpackCursorGesturePayload`、`BackpackTrashSendPayload`、`PanelCursorRequest`、`TrashSendRequest` 引用）、`TagRequest.MAX_SLOTS`（被 `BackpackTagPayload` 引用）、`SnapshotRequestRules.MAX_PREVIEW_SLOTS`。`ToolAreaContents.MAX_TOOL_SLOTS = 64` 不受影响（工具区最多 35 格）。
- **决定**：拆成两个常量：`MAX_TIER_CAPACITY = 108`（`BackpackInventory` 的构造夹、`BackpackContents.items` 的编码与 `normalize/fit`、快照预览）与 `MAX_MAIN_SLOTS = 108 + 35 × 9 = 423`（会话/整份同步的格子列表编码、所有请求的格子数校验、点击的格子范围、`TagRequest.MAX_SLOTS`）。`MAX_SIZE` 删除，让编译器指出所有用到的地方，不留歧义。
- **理由**：`items` 仍然只是档位容量那么长（R1），所以它的编码上限仍是 108；而会话与整份同步发的是"含行的主区域"，上限要用 423。

## R8. `BackpackContents` 的编码

- **已查证**：`BackpackContents.STREAM_CODEC` 是 `StreamCodec.composite` 6 个字段，这是它的上限；磁盘 `CODEC` 是 `RecordCodecBuilder`（`.group(…)` 最多 16 个字段），能直接加第 7 个 `optionalFieldOf("extension_rows", RowSet.empty())`。
- **决定**：磁盘编码加 `optionalFieldOf`（**旧背包读成空**）；网络编码改成 `StreamCodec.of(encode, decode)`：先按原顺序写 6 个字段（保持字节布局的前 6 段不变），再写 `rows`。这是协议升级（17），不需要兼容旧线格式。
- **行的编码**：`rows` = `visible` 列表与 `hidden` 列表各一个"行列表"；一行 = 类别字符串（≤16 字符，沿用 `TagRules.MAX_LENGTH`）+ 9 个 `ItemStack.OPTIONAL_STREAM_CODEC`；行列表上限 35，且 `visible + hidden ≤ 35`（解码时超出就截断并记日志——对应 `TagBook.sanitize` 的做法）。
- **尚未验证**：`ItemStack` 的磁盘与网络编码需要游戏注册表，单元测试里没有，**往返只能在游戏里验证**（quickstart B：`/give` 带 `extension_rows` 的背包 → 载入 → 保存 → 重进世界 → `/data get`）。035 里可见行永远为空（没有扩展件物品时核对会把可见行清掉），所以"带物品的可见行"的线格式在 036/037 才会走到，**035 不声称它已验证**。

## R9. 快照与升级

- **已查证**：`SnapshotOps` 预览取 `contents.items()` 的前 108 格，恢复按容量选档位——`items` 仍是档位容量，所以**不改**。快照存的是整个 `BackpackContents`（经 `CODEC`），所以 `rows` 随它一起被保存与恢复。预览不展示扩展行（031 的界面是 036 之外的另一块，预览显示扩展行属于以后）。
- **升级**：`BackpackUpgradeRecipe.assemble` 用 `BackpackContents.fit(…)` 重建内容，**要把 `current.rows()` 一并带上**（否则升级会丢掉所有扩展行与隐藏行）。这是 035 最容易漏掉的一处，任务里有专门的一条和一个检查。
- **尚未验证**：快照恢复后的 `rows` 与工具区一致性——恢复后第一次载入会核对，已有的行不会丢（隐藏行保留）。

## R10. 协议与同步

- **已查证**：`BackpackNetwork` 里 `registrar.versioned("16")`；`BackpackSessionPayload` 与 `BackpackFullSyncPayload` 发的是 `inventory.items()` 的全部（现在 = 档位容量格）。
- **决定**：协议 17。会话与整份同步包：格子列表改为"含可见行的主区域"（上限 `MAX_MAIN_SLOTS`），**新增** `baseCapacity`（VAR_INT）、`visibleCategories`（≤35 个字符串）、`hidden`（≤35 项：类别 + 其中物品总件数，**不发隐藏行的物品本身**）。客户端 `ClientBackpackView` 存下这三项，035 **不**用它们改布局（没有可见行时布局与现在一样；`applyFull` 里算工具区容量的回退改用 `baseCapacity`）。
- **大小保护**：服务端在发会话/整份同步之前，把要发的内容编码到临时缓冲区量大小，超过 900,000 字节就**不发、记日志**（沿用快照预览的 `SnapshotRequestRules.MAX_PREVIEW_BYTES`（900,000）与做法），面板按"打不开"处理（发 `BackpackSessionPayload.closed()`）。
- **已知限制（要在 DELIVERY 里如实写）**：`BACKPACK_CONTENTS` 作为数据组件随每个背包栈同步，这条通路是原版的物品同步，**我们的保护管不到**。以前 108 格已有同样的暴露面，423 格把理论最坏值放大约 4 倍（只在玩家故意往扩展行塞大量带大数据的物品时才会碰到）。035 不处理这条通路，记为已知限制，并在 quickstart 里留一个"用 423 格的大背包看日志"的检查项。
- **尚未验证**：最坏情况的体积（423 格 + 工具区 + 光标，每格都是带大量数据的物品）没有实测，只能按"原版单包 2 MiB 帧上限、我们的阈值 900,000"来保护。

## R11. 工具区放置规则

- **已查证**：`ToolAreaRules.canPlace(area, held, slot, stacks, isTrash, isTagManager, isAutoPickup)` 只对垃圾桶、标签管理器、自动拾取做每区一个的限制，其余恒放行；调用点在 `BackpackClickPayload`（已有 `held` 的来源判断）。
- **决定**：扩展件不受"每区一个"限制（天然满足，不用改）；新增一个检查：**会新增行但行总数已达 35**（`ExtensionRules.fits`）时拒绝放入，沿用"拒绝 = 零变化 + `sendSlotUpdate`"。判定只在"这个扩展件目前不算在放着的数量里"时才需要（光标上的已经算了）：调用方告诉规则 `heldAlreadyCounted`。
- **尚未验证**：035 里没有扩展件物品，这个检查只能用假解析器的单元测试验证。

## R12. 预设标签

- **已查证**：`PresetTags.IDS` = `stone wood food ore trash tool weapon armor plant redstone`（10 个，顺序固定）；中英文 lang 里有对应键 `tag.mcfloatingbackpack.preset.<id>`；`TagManagerResourcesTest` 检查两种语言的标签键一致。
- **决定**：追加 `crop`、`potion`、`disc`（排在最后，保持现有 10 个的顺序）；中文：农作物、药水、唱片；英文：crop、potion、disc。`plant` 保留，不连任何类别。
- **已查证**：`PresetTagsTest` 逐个断言完整的 id 列表，要同步更新；`TagMatcherTest`、`TagManagerResourcesTest` 是遍历 `PresetTags.ids()`，不用改；`TagProtectionTest` 只检查 `trash` 是预设，不受影响。

## R13. 不做的事（留给后续）

界面（036）、物品与配方与贴图（037）、搜索与整理（`hidden` 条件）、标签面板只读显示"按数据标签推断的类别"、快照预览显示扩展行。
