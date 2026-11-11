# Research: 降级保护

2026-11-10。"已核对"= 读 Minecraft 1.21.1 与 NeoForge 的源码或本仓库源码确认；"假设"= 读源码推出的预期，**必须由实验确认**；"待验证"写进 quickstart。

## D1. 降级到底会发生什么：源码核对与三个假设

**已核对（Minecraft 1.21.1 源码，`ItemStack`、`Inventory`、`DataComponentPatch`）**

- `ItemStack.CODEC` 是记录编码：`id`（`ITEM_NON_AIR_CODEC`，物品不存在就是错误）、`count`、可选的 `components`（`DataComponentPatch.CODEC`）。
- `DataComponentPatch` 解码时，**不认识的组件类型直接报错**（`No component with type: '…'`），所以带着旧版本没有的组件的物品栈整个解码失败。
- `Inventory.load` 对玩家物品栏的每个槽用 `ItemStack.parse(…).orElse(ItemStack.EMPTY)`；`parse` 是 `CODEC.parse(…).resultOrPartial(记 "Tried to load invalid item")`：**解码失败（且没有部分结果）的栈在玩家物品栏里读成空**，只留一条 ERROR 日志。
- `BackpackContents.CODEC` 是 `RecordCodecBuilder`：对**不认识的字段忽略**（Mojang 的记录编码一般行为），保存时只写自己认识的字段。
- 背包里的每一格用 `ItemStack.OPTIONAL_CODEC`（`optionalEmptyMap(CODEC)`）：格子里的物品 id 旧版本没有，这一格解码失败。

**由此得到三个假设（待实验）**

- **H1（字段丢失）**：旧版本打开新版本保存的背包，不认识的字段（035 起的 `extension_rows`，含隐藏行里的物品；以后的新字段）在**下一次保存时丢失**。——这是 040 要保护的情形（解码成功但字段会丢）。
- **H2（嵌套物品只丢一格）**：背包里嵌套的某一格物品 id 旧版本没有（036 的分类扩展、038 的通用扩展放在工具区里）：DFU 的列表编码对失败的元素返回**部分结果**，`resultOrPartial` 会取它，于是背包解码成功、**只丢那几格**，下一次保存时它们永久消失。——这也属于"保存时丢"，保护能拦住（背包被保护，旧版本不改写它）**只要玩家没确认**；但**读入时已经丢在内存里**，所以保护的是"别保存"。
- **H3（整只背包被读成空）**：如果上述失败**不**带部分结果，整个 `backpack_contents` 解码失败 → 背包栈（它的组件解码失败）在玩家物品栏里读成**空**，背包整只消失，退出时覆盖存档。——这是解码层面的丢失，**040 保护不了**（背包栈根本没有活到被使用的时候），要靠另一个 feature（041：让编码对不认识的物品宽容、把解码不了的物品原样保存）。

**H2 与 H3 哪个成立决定 040 的价值范围**，必须实验。无论哪个成立，"修订标记"本身是安全的：它是记录里的可选字段，旧版本会忽略，而不是整只背包的组件解码失败（见 D3）。

## D2. 为什么不改网络编码（`STREAM_CODEC`）

- **已核对**：`backpack_contents` 注册为 `persistent` 加 `networkSynchronized`，`STREAM_CODEC` 是手写的，原版每次同步背包栈都用它。035 为了在组件里加行，改了这个编码并把协议号从 16 升到 17。
- 规格要求协议号仍是 17：给 `STREAM_CODEC` 加字段会让新旧版本互相解码出错（解码器异常会断开连接）。所以**只给磁盘编码加字段**。
- 代价（已知局限，写进交付说明）：
  1. 客户端的背包栈没有修订（读作 0）。保护是服务器侧的，客户端不需要它；
  2. **创造模式下，客户端把背包栈写回服务器（`SetCreativeModeSlot`）时修订会丢**（读作 0），等于"洗掉"保护。创造模式的玩家本来就是在自己的世界里，且需要先有一只被保护的背包并在创造栏里搬动它，可接受；
  3. 服务器把背包栈同步给客户端时，客户端看不到修订，也就不能在界面上标出"这只背包是被保护的"。
- 如果以后要彻底解决，要升协议号并在 `STREAM_CODEC` 里带修订，另议。

## D3. 为什么修订放在 `BackpackContents` 里，而不是单独的数据组件

- 单独的组件（比如 `backpack_revision`）对 040 之前的版本是"不认识的组件类型"：见 D1，整个物品栈解码失败，玩家物品栏里的背包**整只读成空**。比丢字段严重得多，也会让 040 反过来伤害降级到 035～039 的玩家。
- 放进记录里的**可选字段** `data_revision`：旧版本忽略，背包照常读取；记录编码最多 16 个字段，现在 7 个，加到 8 个没有问题。
- 磁盘往返：新字段缺省 0；旧背包（没有这个字段）读作 0，`toContents` 保存时盖上当前修订。`optionalFieldOf("data_revision", 0)`：写出时修订为 0 也会写出字段（为了往返一致可以接受；也可以在 0 时省略，实现时选"总是写出当前修订"，因为保存时总是盖当前值，不会是 0）。

## D4. 统一入口的核对

**已核对（`BackpackGate`、调用点）**

- `BackpackGate.acquire(player, ref)`：读 `ref.getStack(player)`；没有身份时**生成身份并 `ref.setStack(player, updated)` 写回栈**（改写！）；通知快照服务 `noteUse`；返回 `Optional<BackpackInventory>`（`BackpackInventory.fromItem` 读组件、核对行）。
- `acquireForPanel`：先查面板打开时的身份记录（`OpenSessions`）。
- 调用点：面板的十个请求处理器（点击、转移、标签、垃圾桶、光标手势、合并光标、黑名单、取容器物品、标签总览、垃圾桶发送）用 `acquireForPanel`；`BackpackOpenRequestPayload` 与 `AutoPickupEvents` 用 `acquire`。它们都把"返回空"当作拒绝（关面板或跳过）。
- 所以**保护必须在 `acquire` 里、在"分配身份"之前**做：读栈上的 `BACKPACK_CONTENTS` 修订（`stack.get(...)` 只读），被保护就直接返回空，不碰身份、不碰快照。

**确认只能由"明确的打开"触发**：如果任何 `acquire` 都能确认，自动拾取在 60 秒窗口里的下一次调用会静默地把保护确认掉。所以新增 `acquireForOpen`（仅 `BackpackOpenRequestPayload` 用）；其余路径 `acquire`/`acquireForPanel` 只检查、不确认、不提示。

## D5. 确认窗口

- 键：玩家 UUID + 背包键。背包键：有身份时用身份 UUID；**没有身份时用引用**（`BackpackRef` 的来源与下标，加上栈上的修订，防止同一个位置换了别的背包）。被保护的背包不分配身份，所以"没有身份"的情形是可能的（比如旧版本保存过、从没打开过的背包不会有身份，但那样它也没有高修订；有高修订的背包几乎总是有身份的，除非手工编辑）。
- 值：过期时间（服务器毫秒时钟，调用方传入，纯逻辑便于测试）；60 秒；确认时清除；玩家退出与服务器停止时清除（沿用 `BackpackGate.forget` 与 `OpenSessions` 的写法，内存表）。
- 同一个玩家同时有多只被保护的背包：各自一个窗口；第一只的窗口不能确认第二只。

## D6. 确认时立即盖章

确认放行时，把栈上的修订改成当前（`ref.setStack` 写回一份修订为当前的副本，内容不变）。原因：否则背包仍是高修订，锻造台（`matches` 看修订）与自动拾取仍被挡，直到碰巧保存一次。盖章之后背包就是当前版本能完整保存的样子；新数据（不认识的字段）在内存里早已丢了（解码时被忽略），盖章只是把这个事实写下来。

## D7. 锻造台与 JEI

- **已核对**：`BackpackUpgradeRecipe.matches` 现在只看 `template`、`base`、`addition` 三个材料（`Ingredient.test`）；`assemble` 读基础背包的内容、带上行重新组装。
- **决定**：`matches` 加一个条件：基础背包的 `BACKPACK_CONTENTS` 修订不大于当前（只读栈）。JEI 显示的是配方定义（材料），不是某个具体背包，所以**不受影响**；只在把被保护的背包放进锻造台时配方才不匹配（槽位里没有结果）。

## D8. 开发验证：造出"更新版本保存的背包"

- 沿用 039 的调试开关：`-Pmcfb.stampRevision=99`（系统属性 `mcfloatingbackpack.debug.stampRevision`）：`toContents` 盖章时用它代替 `CURRENT`。启动一次带开关的客户端，用背包保存一次（点一下格子），再退出，不带开关重启：这只背包的修订是 99 > 1，被保护。一个客户端、一个世界就能验证大部分场景。
- 真实降级（实验）：用旧构建在**备份过的世界副本**上做，见 quickstart。

## D9. 为什么保护只拦"读写内容的路径"

丢弃、放进箱子、交易、放进工具区或主区域、创造复制都不会解码再保存背包内容（物品栈原样移动，组件作为整体复制），所以不需要拦。**例外**：创造模式的客户端写回（D2）。

## D10. 已知局限（写进交付说明）

- 保护从第一个带标记的版本开始；早于它的版本不认识 `data_revision`，也不会保留它（记录编码忽略并在保存时丢弃它），所以那些版本之间的降级无法保护。
- 创造模式下客户端写回的背包栈没有修订（读作 0）。
- 客户端界面上看不出背包是被保护的（只有被拒绝时的聊天提示）。
- 保护的是"别用面板改写"，不是"别读取"：读入时旧版本已经忽略了不认识的字段（H1，实验已证实）或丢了不认识的嵌套物品（H2，实验已证实）；这份已经变短的内容会随玩家数据一起写回（推断，待对照实验确认），**保护拦不住这种读入时的丢失**。
- 解码层面整只背包读成空（H3）：实验里**没有出现**，不需要为它开 041；但"解码时逐格丢弃"的问题需要另议（见实验分析）。

## 实验设计（plan 阶段先做；步骤见 quickstart）

要弄清：(a) `extension_rows` 是否在旧版本的下一次保存时丢失；(b) 嵌套的物品 id 旧版本没有时是丢一格还是整只背包；(c) 失败时玩家物品栏里那只背包会怎样；(d) 世界存档里的数据是否在退出时被覆盖。

做法：**先备份世界 → 在一个独立的 `git worktree` 里检出 035 之前、仍能编译的提交（`e1207ea`，030；031～034 引用的 `snapshot/net` 包被 `.gitignore` 误忽略、从没进过 git，检出后编译不过）构建 → 把世界副本放进那个 worktree 的 `run/saves/` → 用旧构建打开 → 观察 → 退出 → 对比存档**；同一时刻只开一个客户端（先 `pgrep`）；不运行专用服务器。世界里要有的内容：一只放着工具区扩展件（分类扩展与通用扩展）、有隐藏行物品的背包（用户的 "New World" 里已经有 038 的内容）。观察：日志里的 `Tried to load invalid item`、旧版本里背包是否还在、面板里的内容、退出后用 `nbt` 读 playerdata 对比。

实验结果：

我手动执行design/demo/tools/downgrade_experiment.sh run/saves/"New World"
输出如下：
世界：run/saves/New World
旧提交：e1207ea（030：背包快照——去掉世界里的登记表，改为世界之外的单个背包快照；编号改 4 位）

+ cp -R run/saves/New World /Users/Larry/Desktop/New World-backup-20261110-214152
+ git -C /Users/Larry/Projects/pnnh/MCFloatingBackpack worktree add /Users/Larry/Projects/pnnh/MCFloatingBackpack/../MCFloatingBackpack-old e1207ea
Preparing worktree (detached HEAD e1207ea)
HEAD is now at e1207ea 030：背包快照——去掉世界里的登记表，改为世界之外的单个背包快照；编号改 4 位
+ mkdir -p /Users/Larry/Projects/pnnh/MCFloatingBackpack/../MCFloatingBackpack-old/run/saves
+ cp -R run/saves/New World /Users/Larry/Projects/pnnh/MCFloatingBackpack/../MCFloatingBackpack-old/run/saves/New World

准备好了。接下来由你手动做（我不会替你启动游戏或改世界）：

执行结果记录：
 
      a. 玩家物品栏里那只背包还在吗？还在 
      b. 日志（run/logs/latest.log）里有没有 "Tried to load invalid item"？有几条？没有搜索及看到类似错误
      c. 打开面板：背包里的内容少了什么？好像是都没少，至少直观上看是这样
      d. 随便点一个格子，触发一次保存，然后退出世界。已操作
 4. 对比退出后的存档（用 NBT 工具看 /Users/Larry/Projects/pnnh/MCFloatingBackpack/../MCFloatingBackpack-old/run/saves/New World/playerdata/<uuid>.dat 与备份里同名文件）：
      隐藏行物品、extension_rows 还在吗？data_revision（旧版本没有这个字段）呢？

    在MCFloatingBackpack-old世界里的<uuid>.dat文件解析出来后没有extension_rows\hidden相关内容，备份的New World-backup-20261110-214152世界里有这两个内容，两个好像都没有看到data_revision内容。

### 实验分析（对比两份 playerdata 的 NBT 后得出；上面是用户的原始记录）

做法：用只读脚本把备份与旧版本退出后的 `playerdata/<uuid>.dat` 解开，逐只背包比较 `backpack_contents` 的字段与嵌套的物品 id（只统计"少了什么"，旧版本运行时自动拾取新增的物品不计）。

| 槽位 | 背包 | 字段（备份 → 旧版本保存后） | 少了什么 |
|---|---|---|---|
| 0 | 皮革 | `extension_rows`、`items`、`tools` → `items`、`tools` | 隐藏行里的 2 个面包；工具区 4 个扩展件（作物、唱片、红石、皮革通用） |
| 1 | 钻石 | 多了 `extension_rows` → 没了 | 工具区 1 个食物扩展 |
| 6 | 金 | `extension_rows`、`items`、`tools` → `items`、`tools` | 工具区 8 个分类扩展件（只剩 2 根线） |
| 5/7/8/20/102 | 没有 `extension_rows` 与新扩展件的背包 | 不变 | 无 |

结论：

- **H1 成立（实测）**：旧版本保存后，3 只背包的 `extension_rows` 字段整个没了，隐藏行里的物品随之永久消失。旧版本自己就认识的工具（自动拾取、标签管理器、垃圾桶）与普通物品都保留了。
- **H2 成立（实测，与上面"好像都没少"的直观印象不同）**：旧版本没有的物品 id（036 的分类扩展、038 的通用扩展）在工具区里**被逐格丢掉**，同一只背包里认识的物品原样保留（槽位 1：只丢了 1 个食物扩展，其余 18 个都在）。面板里看不出来，因为丢掉的格子旧版本根本没读进来。
- **H3 未出现（实测）**：背包没有整只消失、没有被读成空。所以 **不需要为"整只背包丢失"另开 041**。
- **日志无 `Tried to load invalid item`（0 条）**：但存档证明确实丢了，所以不能用"日志里有没有报错"判断是否丢数据。
- `data_revision` 两边都没有是预期的：备份由 040 之前的构建保存，旧版本不认识这个字段。

**对 040 的影响（重要，推断，尚未实测）**：丢失发生在**读入时**——旧版本解码背包栈就已经把不认识的字段和物品扔掉了，之后玩家数据（退出、自动存档）按内存里这份已经变短的内容整包写回。这与是否打开过面板无关；本次实验里你点过一个格子，所以**不能区分**"只读就会丢"还是"点了才丢"。按 Minecraft 的实现（`Inventory.save` 从内存里的组件重新编码），前者几乎肯定成立。如果成立：

- 040 的保护（`BackpackGate` 拦面板、自动拾取、锻造台）拦住的是**面板对背包内容的进一步改写**，**拦不住这种读入时的丢失**；降级后只要登录一次，丢失就已经落进玩家数据，而背包上的 `data_revision` 还留着较高的值，升级回新版本时不会被识别成"丢过东西"。
- 所以 040 目前的价值是：①在玩家用面板之前给出警告并让其有机会先备份；②防止确认前基于残缺内容继续改写；**不是**"防止丢数据"。D10 里"保护的是别保存"的说法需要收窄（见下）。
- 真正防止丢失要靠**解码层面的保留**（记录编码把不认识的字段与解码不了的物品原样存下来再原样写回），这就是原先设想的 041，只是动机从"防整只背包消失"变成"防字段/物品被逐格丢"。它同样只对有这个能力的版本之间生效，更早的版本（含当前已发布的，如果有）无法保护。

**建议的追加验证（便宜，推荐）**：对备份再做一次对照——在旧版本里**不点任何格子、不打开面板**，进世界后直接退出，再比较 NBT。如果仍然丢了，上面的推断就是实测。

**T026/040-J 状态**：实验已做、数据已记录；按约定**不由 Claude 勾选**，等你确认（含是否做上面的对照实验）。