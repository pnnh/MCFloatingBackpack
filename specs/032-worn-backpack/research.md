# Research: 统一的"背着的背包"查找

读本仓库源码（2026-11-09）得出的结论。每条：**决定 / 理由 / 备选**。

## D1 把"查找"写成一个纯逻辑类，真实的玩家与物品栈由适配层提供

**决定**：`BackpackSearch<P, S>`（`P` = 玩家，`S` = 物品栈，都是不透明类型）放进 `inventory/worn/`，不引用任何 Minecraft 类。它持有一个按 `order` 排好序的**背着的来源列表**，提供：

- `firstWorn(P)`：按顺序、跳过 `active()` 为假的来源，取第一个有背包的格子 → `Hit(kind=WORN, sourceId, slot, hidden)`；
- `resolve(P)`：主手 → 副手 → `firstWorn` → 物品栏 0～35，返回 `Hit`（`kind` 区分四种）。

**理由**：规格要求"顺序规则用假来源单测"（FR-010）。当前 `BackpackRef` 的类里直接调用 `Player.getItemBySlot`，没法在只有 JUnit 的测试类路径里测。抽成泛型后，测试用 `String` 当物品栈、用假玩家对象，真实代码用 `Player`、`ItemStack`。

**备选**：在 `BackpackRef` 里加 `if` 分支——否决，来源一多就散落各处，也测不了。

## D2 来源接口：四件事 + 序号与顺序分开

`WornSource<P, S>`：

| 方法 | 含义 |
|---|---|
| `int id()` | 这个来源在线路上的序号，等于 `BackpackRef.Source` 的 `ordinal()`；**一旦发布不改** |
| `int order()` | 查找顺序，小的在前（胸甲 0、Curios 10、Accessories 20）；**与 `id` 无关**，所以 033/034 无论谁先注册，顺序都固定 |
| `boolean active()` | **查询时**判断（以后看 `modLevels`），不是注册时 |
| `int slotCount(P)` | 这个来源里可能放背包的格子数（胸甲 1） |
| `S get(P, int slot)` | 读；**越界返回空栈**（客户端发来的下标不可信） |
| `void set(P, int slot, S)` | 写回 |
| `boolean hiddenRender(P, int slot)` | 该槽是否被设为不渲染；默认 `false`（胸甲恒为 `false`） |

**理由**：规格 FR-008 要求接口里留出"隐藏渲染"这个问题；`id` 与 `order` 分开是因为以后要保证"加新来源不改变已有序号"（FR-009），而顺序要允许在中间插入。

## D3 BackpackRef 与网络：线路格式不变，下标变成"来源内的格子号"

- `BackpackRef.Source` 保持 `MAIN_HAND, OFF_HAND, CHEST, INVENTORY`（序号 0～3），033/034 在末尾追加 `CURIOS, ACCESSORIES`；给每个常量加一个 `worn` 标志（`CHEST` 为真）。
- 引用仍是 `(Source, invIndex)`，仍用 `ordinal()` 与 `invIndex` 传输 → **不新增网络包字段、协议版本仍是 16**。
- 对 `worn` 来源，`invIndex` 解释为"来源内的格子号"。**胸甲的引用从 `(CHEST, -1)` 改成 `(CHEST, 0)`**：这是线路上唯一的取值变化，所有生产者与消费者都是同一份新代码，客户端声称的引用与服务端 `resolve` 的结果都按新值比较（`BackpackClickPayload` 里的相等比较）。没有任何地方把 `-1` 当作胸甲的标志（已 grep 确认）。
- `BackpackRef.getStack/setStack/stillValid` 对 `worn` 来源转给 `WornBackpacks.byId(source.ordinal())`；来源找不到（未加载的模组）、格子越界 → `ItemStack.EMPTY`，`stillValid` 为假，调用方按现状关闭会话。
- **服务端不信任客户端**：与现在一样，`BackpackClickPayload` 只在声称的引用与 `resolve` 结果的（来源，下标）**完全一致**时采用声称的，否则用 `resolve` 的结果。

**备选**：把来源序号改成字符串 id——否决，会改所有包的编码、要升协议。

## D4 注册与两端一致

- `WornBackpacks`（`inventory/worn/`，用 Minecraft 类）持有**一个** `BackpackSearch<Player, ItemStack>` 单例，里面有 `register(WornSource)`；胸甲来源在类初始化时注册。
- 033/034 在**模组构造函数**里（客户端与服务端都会跑）按 `ModList.isLoaded` 注册各自的来源；`order` 固定，所以与注册先后无关；两端用同一份代码、同一个 `order`，因此**各自独立算出同一个顺序**。
- 一端装了某个模组、另一端没装（例如客户端没装 Curios 连上装了的服务器）：没装的一端该来源不在列表里，只是看不到那只背包，**不会崩溃**；引用里的序号用的是 `BackpackRef.Source` 枚举（我们自己的），客户端照样能解码。
- **`active()` 在两端各自判断**：客户端读 `modLevels`（客户端配置），专用服务器没有客户端配置，按"生效"处理（与 020 的服务端门禁恒允许一致）。所以"GENERIC 时槽位里的背包不算背着的"**在专用服务器上不成立**，只对单人游戏与局域网房主成立——这是 033 要明确写下的限制，不在本 feature 里解决。

## D5 渲染层的使用

`BackpackBackLayer` 改为 `WornBackpacks.first(LivingEntity)`：返回 `Found(stack, hidden)` 或空；空或 `hidden` 为真就不画，否则画 `stack`（与现在画 `getItemBySlot(CHEST)` 的栈相同）。`first` 不分配对象之外的东西，没有第三方来源时就是读一次胸甲槽加一次判断，开销与现状相当。

## D6 其它调用点

| 调用点 | 要不要改 |
|---|---|
| 服务端 11 个包处理、客户端 3 处、自动拾取 | **不改**（它们只调 `resolve`、`getStack` 等） |
| `BackpackQuickTransferPayload.holderOf` | 改：`worn` 来源一律映射到 `Holder.CHEST`（"背包不在物品栏 0～35 里，没有要保护的下标"），`Holder` 枚举不动 |
| `FloatingPanelEvents.syncSelectionContext` 的引用键 | 不改（`ordinal + ":" + 下标` 对新来源同样唯一） |
| `BackpackSessionPayload`/`BackpackFullSyncPayload` 的解码 | 不改（序号越界回退到 `MAIN_HAND` 的现有处理保留） |
| `BackpackRef.saveInventory` 的过时注释 | 改（FR-012） |
| `FloatingBackpackItem`（Shift+右键换到胸甲槽） | **不改** |

## D7 测试

- `BackpackSearchTest`（纯）：假来源按 `order` 排序、跳过不生效、第一只、越界、隐藏渲染仍算背着的但渲染取不到、手里优先、无背包、物品栏下标最小、同样的结果对"服务端视角"和"客户端视角"（两个不同的假玩家）成立。
- `BackpackRefTest`（已有）：保留；加"胸甲引用的下标是 0"、`Source` 的 `worn` 标志、序号不变。
- 不为 `WornBackpacks`（用 Minecraft 类）写单元测试；它的行为由 quickstart 的客户端验证。
