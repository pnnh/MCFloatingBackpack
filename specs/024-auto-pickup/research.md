# Research: 第三个扩展工具——自动拾取

**Branch**: `024-auto-pickup` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

本文件记录 Phase 0 的调研结果：每一条决定写**依据**与**备选**；凡是没有在游戏里验证过的，单独列在末尾的"仍不确定项"。依据来自读本仓库源码、读 NeoForge 21.1.251 的源码 jar、以及用 `javap` 反编译精妙背包 3.26.19、精妙背包核心 1.5.x、旅行者背包 10.1.40（开发期 `-PcompatRuntime` 用的那三个 jar）。**没有在游戏里运行过这两个模组**，凡是"读字节码得出"的结论都要在客户端会话里再验证。

## Decision 1：用 `ItemEntityPickupEvent.Pre` 接管拾取，自己完成"搬运 + 动画 + 回落"

**依据（读源码）**：

- NeoForge 21.1.251 的 `ItemEntity.playerTouch`：先 `fireItemPickupPre(this, player).canPickup()`；`FALSE` 直接返回；否则条件是 `(TRUE || pickupDelay == 0 && (target == null || target 是该玩家)) && player.getInventory().add(stack)`，成功后才触发 `Post`、`player.take(entity, 实际数量)`（拾取动画与声音由这个包带出）、空了就 `discard`、记统计。
- 重要：**`TRUE` 只是绕开"拾取延迟与物主"检查，`inventory.add(stack)` 仍会执行**；所以没有"让原版别加物品栏但继续播动画"的组合。`Inventory.add` 对空栈直接返回 `false`，所以"我们把整个栈搬空后返回默认"会让原版什么也不做（没有动画、没有声音，空实体下一 tick 被丢弃）。
- 精妙背包与旅行者背包都在这个事件（`Pre`，NORMAL 优先级）里工作：精妙在 `CommonEventHandler.onItemPickup` 里先看 `pickupDelay`，让升级吃掉一部分，剩余写回实体，再 `setCanPickup(FALSE)`；旅行者在 `NeoForgeEventHandler.onItemPickup` 里对"背着的那只"的自动拾取升级 `tryPickup`，成功就 `setCanPickup(FALSE)`。

**决定**：在 `Pre` 里用 **`EventPriority.LOW`** 注册监听器，流程：

1. 事件的 `canPickup()` 已经是 `FALSE`（别人已处理）→ 什么都不做；
2. 先做**便宜的预检**（Decision 2）：没有激活背包 / 左侧区没有自动拾取 → 直接返回，原版照常；
3. 向 `AutoPickupDecision`（纯逻辑，Decision 6）问（输入只有"工具是否激活"与"是否命中黑名单"）：`VANILLA`（不适用）/ `BLOCK`（命中黑名单）/ `TAKE`；
4. `BLOCK` → `setCanPickup(FALSE)`，不搬运，物品留在地上；
5. `TAKE` → 先复刻原版的前置条件（`!entity.hasPickUpDelay()` 且 `entity.getTarget()`（NeoForge 补的物主 getter）为空或就是该玩家；否则返回，让原版去拒绝；`pickupDelay` 字段本身是 private，不直接读），再用已有的 `QuickTransferLogic.moveInto` 把物品栈塞进主区域（先叠后填，目标顺序 0..N-1）；搬了 `moved` 个：实体栈 `shrink(moved)`、保存背包、同步客户端、`player.take(entity, moved)`、`awardStat(ITEM_PICKED_UP, moved)`、`player.onItemPickup(entity)`；
   - 实体栈空了 → `entity.discard()` 并 `setCanPickup(FALSE)`（我们已处理完，避免原版对空栈再走一遍）；
   - 没搬空（背包放不下）→ **保持默认**，原版用缩小后的实体栈继续做"进物品栏"；物品栏也放不下的部分按原版留在地上（决策 3 的回落）。
   - `moved == 0`（主区域满）→ 保持默认，整个交给原版。

**备选**：

- `Post` 事件：晚于"进物品栏"，物品已进了玩家物品栏，无法接管。
- 用 `setCanPickup(TRUE)` 让原版"强制拾取"：见上，`inventory.add` 仍会执行，达不到目的。
- 每 tick 扫描玩家附近的 `ItemEntity` 主动吸取：改变了"什么时候能拾取"（spec FR-012 禁止），也更贵。
- 优先级取 `NORMAL`：与第三方两个模组并列，先后取决于模组加载顺序，不可控。取 `LOW` 让它们（NORMAL）先跑，并保证"别人已经 `FALSE` 时我们不插手"——这就是本模组对它们的全部礼让（Decision 6）。

**取舍与登记**：我们**不触发 `ItemEntityPickupEvent.Post`**——它的文档语义是"已经加入玩家物品栏之后"，对"进了背包"的那部分不成立，且 `fireItemPickupPost` 还会连带触发依赖玩家物品栏的旧事件；监听 `Post` 的其它模组（任务、成就类）看不到这部分拾取。**登记为已知限制**；统计（`ITEM_PICKED_UP`）与 `onItemPickup`（飞出物品被拾取的触发器）由我们自己调用。

## Decision 2：激活背包与"先预检、后加载"

**依据**：`BackpackRef.resolve` 是服务端所有请求共用的解析（主手→副手→胸甲→物品栏 0～35），`BackpackOpenRequestPayload` 在服务端也是现找一次，没有任何"当前打开的是哪只"的记录（见 spec 的"已查证"）。`BackpackInventory.fromItem` 会把整份内容（主格子最多 `MAX_SIZE` 个栈、工具区、标签、垃圾桶）复制成可变的 `ItemStackHandler`——每次拾取事件都做这件事太贵（一个玩家站在一堆掉落物上，事件每 tick、每个实体都会触发；`pickupDelay > 0` 时也会触发）。

**决定**：

- 事件里**先判 `entity.hasPickUpDelay()` 就返回**（与精妙、旅行者同样的短路）；
- 用 `BackpackRef.resolve(player)`（遍历最多 36 格，很便宜）；没有背包 → 返回；
- **预检**：直接读该背包物品上的数据组件 `BackpackContents.tools().left()`（已是不可变记录，不用建 `BackpackInventory`），左侧区里没有自动拾取 → 返回；
- 预检通过之后才 `loadInventory`、算黑名单命中、搬运、保存。

**备选**：缓存"某玩家是否激活了自动拾取"：状态散落、失效条件多（换手持物、移动物品栏、放入/取走工具），不选；每次实时算（FR-012a）。

**宪法 VI 登记**：这是对**所有玩家**（含从不持有背包的玩家）每个拾取事件多出的一次判断。无背包时成本是遍历 36 个格子的 `instanceof` 检查，量级与原版自己拾取判断相当，**没有测量**；实现阶段 T 项里加一次粗测（开发机上用 `System.nanoTime` 对 10 万次 `resolve` 求平均，记入 DELIVERY），测不了就写"未测量"。

## Decision 3：命中判断——该背包的标签 ∩ 黑名单

**依据**：018 的 `ItemTags.tagsOf(ResourceLocation)` 就是"该背包给该物品分类打的标签"；物品分类 = `BuiltInRegistries.ITEM.getKey(stack.getItem())`（不是具体栈）。

**决定**：`PickupBlacklist.matches(List<String> tagsOfItem)`：任一标签在黑名单即命中；未打标签的物品不命中。标签用 018 的 `TagRules` 校验，所以全部小写，比较是普通字符串相等。

**备选**：把黑名单做成按物品 id（不用标签）——违背用户的"标签黑名单"设想；做成数据包物品标签（`#c:ores`）——超出范围。

## Decision 4：黑名单的存储——`BackpackContents` 的第六个字段 `pickup_blacklist`

**依据**：`BackpackContents` 现在有 5 个字段（items、cursor、tools、tags、trash），`StreamCodec.composite` 上限是 6，所以还能再加**一个**；`RecordCodecBuilder.group` 上限远高于此。`fit`/`normalize`/`copy`/`BackpackUpgradeRecipe`（升级用 `BackpackContents.fit(current.items(), ..., current.trash(), capacity)`）都要带上新字段，和 021 加 `trash` 时完全同构。

**决定**：新类 `inventory/pickup/PickupBlacklist`（不可变记录，`List<String> tags`，去重、每个标签满足 `TagRules`、至多 64 个；`CODEC` 读入时清洗，`STREAM_CODEC` 用 `ByteBufCodecs.stringUtf8(16)` 列表上限 64）。字段名 `"pickup_blacklist"`，`optionalFieldOf(..., PickupBlacklist.EMPTY)`，旧背包读成空。

**登记**：`BackpackContents` 的编码字段**已用满 6 个**；下一个要往背包数据里加字段的 feature 必须先把 `STREAM_CODEC` 改成手写（或把几个小字段合并成一个子记录）。写进 DELIVERY 的"给后来者的提醒"。

**同步**：像 018 的标签、021 的垃圾桶一样，黑名单**单独发一个包**（`BackpackPickupBlacklistPayload`），会话包的字段已满，不动它；会话打开时在 `sendSession` 里补发，每次修改后也发。

## Decision 5：黑名单页面——复用 018 的纯组件，新建平行的一套状态/输入/渲染，不改 018 的类

**依据**：`TagPanelLayout`（纯几何：状态行、标签块自动换行、加号/文本框、下拉、滚动）、`TagTextInput`（文本缓冲）、`TagMatcher`（模糊匹配）、`TagRules`、`PresetTags` 都不依赖选区，直接可用。`TagPanelState/Context/Input/Renderer` 则和"选区 → 共同标签"绑得很紧（`TagPanelModel` 吃选区）。

**决定**：新增 `client/panel/pickup/` 包，`PickupPanelState`（展开、编辑、下拉下标、待删标签、滚动、`TagTextInput` 缓冲，结构同 `TagPanelState`）、`PickupPanelContext`（模型 = 黑名单列表 + "是否有自动拾取"；标签显示名复用 `TagPanelContext.label`；下拉候选 = 预设 + 本背包已用过的标签，用 `TagMatcher.match`）、`PickupPanelInput`（鼠标/滚轮/键盘，发 `ADD`/`REMOVE`）、`PickupPanelRenderer`（画在主区域矩形上，标题行"自动拾取黑名单 3/64"）。**互斥**：`PickupPanelState.toggle()` 展开前调用 `TagPanelState.get().collapse()`，反向同理（在两个 `toggle` 调用点各加一行）；垃圾桶面板在矩形外，不互斥。

**要同步改的既有触点**（来自 grep，T 项逐一列出）：`FloatingPanelInput`（工具区点击分派、主区域按下消费、编辑中点击别处失焦）、`FloatingPanelEvents`（每 tick 收起检查、退出世界重置、键盘三事件、滚轮）、`FloatingPanelRenderer`（画页面；页面展开时跳过主区域格子的绘制，两处 `area == MAIN && TagPanelState.isOpen()`）、`DeleteKeyInput`/`DeleteKeyRules`（022 的 Delete 键在标签页打开/编辑时让位，黑名单页面同理，传"标签页或黑名单页打开/编辑"的并集）、`ClientBackpackView`（`hasAutoPickup()` 与黑名单镜像）。

**备选**：把 `TagPanel*` 参数化成通用"标签列表面板"：改动 018 已验证的代码，回归风险大，且 018 的模型含选区/保护逻辑，抽象成本高；先复制结构、复用纯组件，等第三处需要时再抽象（登记为整理项，不顺手做）。

## Decision 6：不检测其它背包模组，只做被动礼让（2026-11-08，用户裁决；取代最初的 Decision 6～8）

最初的方案是：纯决策 `AutoPickupDecision` + 可注册的探测器（读精妙背包、旅行者背包是否开了拾取升级）+ 档位由客户端同步给服务端 + 三个 `compileOnly` 第三方 jar。调研时已经看出它**必须依赖这两个模组的内部类**（`PlayerInventoryProvider`、`BackpackLinkedStorageResolver`、`AttachmentUtils`、`IPickupResponseUpgrade`、`AutoPickupUpgrade` 都不是它们声明的公开 API；精妙背包的升级数据在服务端 saved data 里，物品上只有 UUID，读不到），版本一变就可能 `NoSuchMethodError`；探测器虽然整体 `catch Throwable` 能退化，但"检测得到时才避让"的行为会随版本悄悄变化，很难测，也很难向玩家解释。用户据此决定**不做主动避让**。

**决定**：

- 不检测、不读、不引用任何第三方模组；不新增 `compileOnly`（`build.gradle` 回到 024 之前）、不新增 `compat/<modid>/` 代码、不新增"避让开关"网络包与服务端表、020 的配置不增加含义。
- `AutoPickupDecision.decide(toolActive, blacklisted)` 只剩两个输入（`VANILLA`/`BLOCK`/`TAKE`），不再有 `avoidMods`/`detects`。
- 被动礼让只靠 Decision 1 里已经有的两点：**低优先级**（`EventPriority.LOW`，晚于它们的普通优先级）；**事件已被别人设为 `FALSE` 时不插手**。

**多个背包同时拾取时的实际效果**（读字节码得出，**未在游戏里验证**；实测项见末尾的"仍不确定项"）：

| 路径 | 行为 |
|---|---|
| 精妙背包（`CommonEventHandler.onItemPickup`，普通优先级） | 让升级拿走一部分，把剩余写回实体；只要数量变了就 `setCanPickup(FALSE)` |
| 旅行者背包（`NeoForgeEventHandler.onItemPickup`，普通优先级） | 只处理"背在身上的那只"的拾取升级；`tryPickup` 成功才 `FALSE`；**不检查事件是否已被别人设为 `FALSE`**，所以它与精妙背包可能各拿一部分 |
| 本模组（`LOW`） | 看到 `FALSE` 就跳过；否则按 US1 拿 |
| 旅行者拾取升级的 `tick`、精妙背包的磁铁升级 | 每隔几 tick 按范围（`AABB.inflate`）扫描掉落物并吸取，**不经过这个事件**，往往比"碰到"更早，本模组拿不到这些物品 |

结果：同一个实体的数量被依次扣减，**不会复制**；各方拿多少取决于谁先出手（精妙与旅行者之间取决于模组注册顺序）；对方过滤器不放行的物品进本模组的背包而不是物品栏；对方放行的物品即使命中本模组的黑名单也被对方拿走。DELIVERY 的已知限制里如实写明。

**备选**：

- 主动检测并避让（原方案）：见上，依赖内部类，已否决。
- 让玩家在配置里手动写"哪些模组让路"：本模组在服务端运行拾取，客户端配置要同步过去；而且没有检测就无从"让路"，只能是"不接管"的整体开关，价值很低，不做。

## Decision 7 / Decision 8：（已取消）

原"两个探测器怎么读"与"避让档位由客户端同步给服务端"随 Decision 6 取消。保留编号是为了不改动其它文档里对 Decision 9～11 的引用。

## Decision 9：左侧区限放 1 个——给 `ToolAreaRules.canPlace` 加一个谓词参数

**依据**：021 已把 `canPlace` 做成"区域 + 该区域当前内容 + 被替换的格子"的纯函数，`switch (area)` 里 `LEFT` 现在走 `default -> null`（不限）。调用点两处：服务端 `BackpackClickPayload`（唯一权威）、客户端 `FloatingPanelInput.refusedByLimit`（只做体验）。

**决定**：签名加第三个谓词 `isAutoPickup`，`case LEFT -> isAutoPickup`；两处调用点各补一个 `stack -> stack.is(ModItems.AUTO_PICKUP.get())`；`ToolAreaRulesTest` 补左侧区的用例（旧背包已有 2 个不删、同格交换允许、其它区域不限）。客户端提示 key：`message.mcfloatingbackpack.limit.auto_pickup`。

## Decision 10：配方与贴图

**配方**（有序，`data/mcfloatingbackpack/recipe/auto_pickup.json`）：

```text
I E I        I = 铁锭 ×4   E = 末影珍珠 ×1
R H R        R = 红石粉 ×2 H = 漏斗 ×1
I D I        D = 钻石 ×1
```

**依据**：标签管理器（纸/书/墨囊/线/皮革，开局材料）< 垃圾桶（4 铁锭 + 炼药锅，早期）< **自动拾取（漏斗 + 末影珍珠 + 钻石，中期）**< 023 之后的钻石/下界合金背包升级（钻石块/下界合金块 + 锻造模板）。漏斗（5 铁锭 + 箱子）表达"收取"，末影珍珠要到下界找烈焰人，钻石要挖到深层，红石表达"自动"。只用原版物品，JEI 能显示。**这是建议值**，实现阶段若觉得难度不合适，调整须记入 DELIVERY。配方 JSON 的写法对照 `trash_can.json`（`item` 键直接引用原版物品；铁锭/红石也可以用 `c:` 标签，但垃圾桶用了 `item`，保持一致）。

**贴图**：一张 16×16 平面贴图 `textures/item/auto_pickup.png`，模型 `item/generated`（同 `tag_manager.json`/`trash_can.json`）。图案方向：一个漏斗，上方一颗蓝绿色末影珍珠正落入其中（与配方呼应）。生成方式沿用 021：用 ludo.ai 生成较大的原图 → `design/demo/tools/make_auto_pickup_icon.py` 缩成 16×16；ludo 不可用时退到 pixellab 或手工逐像素绘制。贴图预算（宪法 VI）：只新增一张 16×16 物品贴图，物品贴图进的是物品图集而不是共用方块图集；实现完成后跑 `design/demo/tools/check_atlas_budget.sh`（先 `pgrep` 确认没有游戏在跑），度量不了就如实写"未度量"。

## Decision 11：拾取后的同步，以及"每次拾取都重发整只背包"的成本

**依据**：`BackpackRef.saveInventory` 把新内容写回**物品栈的数据组件**；原版容器同步会因此把**整只背包物品栈（含全部格子）**重发给客户端——这是本模组所有写入路径（点击、转移、垃圾桶）已有的行为，每次操作一次。拾取的频率比手动点击高得多（刷怪塔、挖矿掉落一次几十个）。

**决定**：

- 面板打开时客户端镜像需要更新：对**被改动的主区域下标**逐个调用已有的 `BackpackNetwork.sendSlotUpdate`（`moveInto` 的 `setTarget` 回调里收集下标，通常 1～2 个），不发整份同步；面板关闭时镜像本来就不被使用（`applySlotUpdate` 对越界下标无害；会话打开时是整份重发）。
- **同一 tick 内同一玩家的多次拾取不合并**（本期不做）；成本登记为**已知风险**：大背包 + 高频拾取 → 容器同步的字节数。实现阶段加一次粗测（拾取 64 次的包字节数，开发机），写入 DELIVERY；如果超标，后续另开 feature 做"拾取批处理"（同 tick 合并、延迟一 tick 统一保存）。

**备选**：现在就做批处理——复杂度高（要处理"保存前玩家换了背包"等竞态），而用户并未提出性能要求；登记风险比盲目优化更合适。

## 仍不确定、需要在客户端会话里验证的项（登记，不得当作已知）

1. **与两个第三方模组同时拾取**（Decision 6 的表格是读字节码得出的）：用 `-PcompatRuntime` 的客户端，带拾取升级（有/无过滤器）的精妙背包与旅行者背包分别、同时开着时：不崩溃、物品守恒、本模组确实在对方设为"不可拾取"的事件里跳过、剩余物品下一次接触时被拿；各拿多少不作要求。
2. **按范围吸取的路径**（旅行者拾取升级的 `tick`、精妙磁铁）确实不经过拾取事件、本模组确实拿不到那部分物品。
3. **拾取的声音与动画**：`player.take(entity, moved)` 单独调用后的表现；部分搬运时我们调一次 `take`、原版（回落进物品栏）再调一次，是否出现两次拾取声。
4. **`onItemPickup`/统计/成就**：与原版的差异（不触发 `Post`，见 Decision 1）。
5. **`pickupDelay`/物主**的前置条件复刻是否与原版完全一致（含创造/旁观模式；`playerTouch` 本身在旁观模式下不会被调用，所以应无差别）。
6. **每个拾取事件的额外成本**与**高频拾取的同步字节数**（Decision 2、11）。
7. Windows/Linux 本机无法验证。
