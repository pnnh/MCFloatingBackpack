# Research: 背包快照保留策略调整

2026-11-17。"已核对"= 读 Minecraft 1.21.1 / NeoForge 21.1.251 源码或本仓库源码确认；"假设"= 读源码推出的预期，**由游戏里的验证确认**（写进 quickstart）。

## R1. 定时快照怎么调度

**已核对**：现有 `SnapshotScheduler` 是"变更 → 防抖"：`mark` 记最新内容，`due(now)` 取走"安静 ≥ 10 秒或首次未写 ≥ 5 分钟"的；`SnapshotService.tick()` 约每秒一次（`ServerTickEvent.Post` 里 `tickCount % 20 == 0`）。

**决定**：加一个独立的纯类 `TimedWindow<T>`：`mark(uuid, value, now)` 只记"自上次定时写以来脏了的背包"和它的最新内容（替换式，不累积）；`due(now)` 在 `now ≥ nextAt` 时取走全部脏的并把 `nextAt = now + 间隔`；间隔默认 900 秒，`VersionDebug.timedSnapshotSeconds()` 可改。`SnapshotService.mark` 同时喂给两个调度器（变更的防抖与定时的窗口）。用墙钟（`System.currentTimeMillis`，同现有）：服务器暂停时 `tick()` 不跑，恢复后至多晚一个窗口，不补发。

**依据**：与现有防抖互不影响（可独立测试）；"只写变过的"天然成立（脏标记）；内存只持有脏背包的最新内容（与背包物品共享同一个不可变对象，不是复制）。

**否决**：把定时并进防抖调度器（耦合；两种"到点"语义不同，测试更难）；用游戏刻计时（暂停时刻不前进，但与现有墙钟不一致，调试开关也不好用）。

## R2. 类别记在哪里：文件名 + 文件头

**已核对**：`SnapshotFiles.fileName(time)` = `<time>.dat`，`parseTime` 只认纯数字加 `.dat`；`SnapshotStore.times(dir)` 靠文件名列出；`write` 靠文件名集合做清理；列表（`versions(...)`）靠读每个文件的头（`readTag` 整个读出压缩 NBT，再按 `time` 缓存）。

**决定**：类别写进**文件名后缀**：`<time>.dat`（变更，也是版本 1 的旧文件）、`<time>-t.dat`（定时）、`<time>-u.dat`（升级前）；同时写进文件头（`kind`、`from_mod_version`、`to_mod_version`）。**文件名是清理与淘汰的依据，文件头是显示的依据**；二者不一致时以文件名为准（只可能是手工改过）。无法识别的后缀（`-x`、`.tmp`、别的）`parseTime` 返回 -1，**列表、清理、容量淘汰都看不见它**，这自然满足"更高版本的文件绝不被删"的一部分；另外对"文件名是我们认得的、但头里 `version` 更高"的文件（以后的版本可能沿用我们的命名）：**列表不显示，也不参与保留与淘汰**（保留与淘汰只对"成功读出头且版本已知"的文件动手；容量淘汰选中候选后先读头确认再删）。

**依据**：容量淘汰要"全局最旧的定时、最旧的变更"，只靠名字就能选，不必为淘汰解开几千个文件；钉住只需改名（外加改头）。

**否决**：只在文件头里记类别（淘汰要读所有文件，慢）；每类放一个子文件夹（`folderOf`、`folders()` 与现有布局都要改，收益不大）。

## R3. "升级前"是钉住旧的，不是新写

**已核对（040 的降级实验与 056 的 DFU 模拟）**：新版本读到不认识的物品 id，该格被留空（056 起保位）；此后写出的快照已缺那些物品。旧版本写下的快照文件头与内容是原始 NBT，里面还有那些物品的 id。

**决定**：钉住 = 对每只背包取**最新一份**（按时间）快照，把它变成 `-u` 文件：读出压缩 NBT 复合标签（`NbtIo.readCompressed`），**只改头部字段**（`kind`、`from_mod_version`、`to_mod_version`），原样保留 `contents` 子标签，`NbtIo.writeCompressed` 到临时文件，改名为 `<time>-u.dat`；**然后重新读出新文件，把它的 `contents` 与原来读到的 `contents` 比较（`Tag.equals`）**，相等才删旧文件，不等就删新文件、保留旧文件、记 ERROR。每只背包最多 5 份 `-u`（第 6 份进来删最旧的 `-u`）。最新一份已是 `-u` 时只改它的 `to_mod_version`（同样先写后校验）。

**为什么不能保证"逐字节"**：`CompoundTag` 底层是哈希表，读出再写入时键的顺序理论上可能不同，所以保证的是**语义相等**（读出后相等），不是文件字节相同。这对恢复足够（恢复本来就是解码内容）。plan 把 spec 的"逐字节"改成"完全一致（重读比较相等）"。

**否决**：升级时新写一份（新版本里读到的内容已有损）；复制一份而不是改名（占双倍空间，且还要决定删谁）。

## R4. 保留与淘汰按类别、只对"读得出的"文件动手

**决定**：`SnapshotRetention.toDelete(metas, keep)`：对每个类别按时间排序，取超过保留数的最旧的；输入只含成功读出头且版本已知的快照。保留数：定时 60、变更 20、升级前 5。**写完一份新快照后只对该背包该类别做一次清理**（不是对三类都做）。`SnapshotPolicy.KEEP = 5` 常量与它的测试（`SnapshotPolicyTest`）随之改成三个常量与新的测试。

## R5. 去重按 (背包, 类别)

**已核对**：`SnapshotService.lastHash` 是 `Map<UUID,Integer>`，只记每只背包最新一份的哈希；`writeOne` 第一次读不到哈希时从磁盘最新一份读 `contents` 算哈希。

**决定**：键改成 `(UUID, SnapshotKind)`；首次从磁盘初始化时读**该类别**最新一份。哈希用 `contents.hashCode()`（同现在）。`forget(backpack)`（命令删光之后）清掉该背包所有类别的哈希。

## R6. 服务端配置：读取时机与夹取

**已核对**：`ServerLifecycleHooks.handleServerAboutToStart` 里先 `ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, …)`（源码第 96 行），再 `post(ServerAboutToStartEvent)`；本模组的 `SnapshotService.start` 挂在 **`ServerStartingEvent`**（更晚）。开发运行的日志里服务端配置在 15:16:43.498 载入，快照服务在 15:16:45.556 启动，与源码一致。所以 `start` 时配置值已可读，**专用服务器与单人游戏相同**。`DiagConfig` 已经用 `modContainer.registerConfig(ModConfig.Type.SERVER, DiagConfig.SERVER_SPEC)` 注册了服务端配置（读到的文件在开发运行里是 `run/config/mcfloatingbackpack-server.toml`）。

**决定**：在 `DiagConfig` 的 `BUILDER` 里再 `push("snapshots")` 一节（由新类 `SnapshotConfig.define(builder)` 定义并保存 `ENABLED`、`MAX_TOTAL_MB` 两个值），不新增配置文件。`MAX_TOTAL_MB` 用**不限范围**的整数定义（`defineInRange(..., Integer.MIN_VALUE, Integer.MAX_VALUE)`）而不是 128～4096：`ModConfigSpec` 的范围校验对越界值的做法是**纠正为默认值**（不是夹取），而规格要的是夹到范围内并记 WARN。夹取由纯类 `SnapshotSettings.from(enabled, mb, debugSeconds)` 做，返回值与"是否被夹过"。读配置值包在 try/catch（`IllegalStateException`：配置未载入）里，失败当默认值。

**配置在服务器启动时读取一次**；运行中改文件不生效（FR-020）。

## R7. 容量统计与淘汰

**决定**：写线程里持有 `totalBytes`（`AtomicLong`，只有写线程用，命令删除后通过 `invalidateSize()` 让下一次重算）。首次或失效后遍历快照目录的所有认得的文件（`Files.size`）求和；之后每写一份加、每删一份减。每写完一份后若 `totalBytes > maxBytes`，调用纯类 `SnapshotCapacity.victims(candidates, overBytes)`：候选 = 所有认得的文件（时间、类别、大小、是否某背包最新一份），先排除受保护的（每只背包的最新一份、所有升级前类），再按"定时最旧优先，然后变更最旧"排序，累计到释放足够为止。删除前对每个候选**读头确认版本已知**（未知版本跳过）。只剩受保护的仍超限：`Throttle`（每小时一次）记 WARN，之后不再删。

**依据**：目录遍历只做一次；"最新一份"由文件名里的时间判断（每个背包文件夹内最大的 `time`）。

**否决**：每次写都遍历目录（背包多时慢）；按背包配额（用户要的是总上限）。

## R8. 版本变化检测的时机与来源

**已核对**：`SnapshotService.start(server)` 在 `ServerStartingEvent` 里调用，早于任何玩家登录；模组版本在 `WorldMarkerEvents.modVersion()`（私有）里取：`ModList.get().getModContainerById(MOD_ID)`…。

**决定**：把 `modVersion()` 提成一个共用的小方法（放在 `version/` 包的新类 `ModVersion.current()`，`WorldMarkerEvents` 改调它，行为不变）。`SnapshotService.start` 里：读 `state.properties` → 与当前版本比较 → `UpgradePlan` → `SnapshotStore.pin(...)` → 写新的 `state.properties`（版本、新的版本变化编号 = 旧编号 + 1、旧版本、已钉住的背包数、空名单）。**开关关闭时**只写状态（版本照常更新），不钉住。状态文件缺失或损坏：当"没有记录"：只写入当前版本（编号 1），不钉住、不提示。

**版本比较**：字符串不相等即算变化（升级或降级都算；"1.2.0" 与 "1.2.0 " 先 `strip`）；不解析语义版本号。

## R9. 提示的发送与"已提示名单"

**已核对**：`MCFloatingBackpack` 已有 `PlayerEvent.PlayerLoggedInEvent` 监听（039 的版本握手）和 `PlayerLoggedOutEvent`。

**决定**：`SnapshotNotices.onLogin(player)`：若本次运行存在"版本变化提示"（`state.changeId` 与 `notified` 名单可得）且玩家不在名单里 → 发聊天（前缀 + 文字，同 `BackpackGate.tell` 的写法）→ 把 UUID 加进名单 → 写 `state.properties`（原子替换）。名单上限 10000，超过丢最早的并 WARN。开关关闭时提示改为"备份已关闭"版本，同样记名单。提示只发在线玩家。

**注意**：`state.properties` 在运行中被登录事件改写，要与 `SnapshotService` 的写线程不冲突——状态文件**只由主线程读写**（服务器启动与登录事件都在主线程；原子"写临时文件再改名"）。

## R10. 丢失计数：线程与时机（实现时按游戏里的实测改了做法）

**已核对（NeoForge 源码）**：`PlayerDataStorage.load(Player)` 里先 `player.load(tag)`（背包物品栈在这里解码），**之后**才 `EventHooks.firePlayerLoadingEvent(...)` 发 `PlayerEvent.LoadFromFile`；两者在同一线程。`getPlayerFile("dat")` 被保留，抛异常，要自己用 `getPlayerDirectory()` 与 `getPlayerUUID()` 拼 `<uuid>.dat`。

**游戏里的实测（2026-11-17，容器）推翻了原来的设想**：原设想是"用主线程的线程本地计数，`LoadFromFile` 里读出并清零，每个 tick 开始时清零"。实测玩家数据里的物品栈**不是在服务端主线程上解码的**——`LenientStacks` 的 WARN 全部出在 `Worker-Main-N` 线程，主线程的计数一直是 0，登录时没有任何提示。所以线程本地计数 + 主线程读取行不通。

**决定（改后）**：`LoadFromFile` 的处理里**自己再解码一次**：读 `<uuid>.dat`，取 `Inventory` 里每个物品的 `components."mcfloatingbackpack:backpack_contents"`，用 `BackpackContents.CODEC` 解码，在**当前线程**上先 `LostCells.reset()`、解码后 `LostCells.take()`，结果放进 `ConcurrentHashMap<UUID,Integer>`，登录事件里取出并提示。这样计数只来自这次解码，不受别的线程（区块加载等）影响，也与是哪个线程读的数据无关。代价：登录时多读一次玩家文件并解码其中的背包（毫秒级）；`LenientStacks` 的 WARN 会因此在日志里重复一次。只看 `Inventory`（背包放在 Curios/Accessories 的槽里的丢失不计，尽力而为）。`ServerTickEvent.Pre` 的清零监听取消了。

**快照恢复的计数**（R11）仍用线程本地计数：`SnapshotOps.restore` 在主线程上同步解码，解码前 `reset`、解码后 `take`。

## R11. 恢复结果带"无法恢复的格数"

**已核对**：`SnapshotResultPayload(code, a, b, n1, n2)`，`RESTORED` 现在只用 a（背包 id）、b（目标玩家名），**n1、n2 没用**；`SnapshotResultText.args` 的 `RESTORED` 是 `{b, a}`；命令的 `restore.ok` 文字在 `SnapshotCommands`。`SnapshotOps.restore` 只返回 `ResultCode`。

**决定**：`SnapshotOps.restore` 改返回 `RestoreResult(ResultCode code, int lostCells)`；窗口路径把 `lostCells` 放进 `n1`（不新增包字段、不新增载荷）；客户端 `SnapshotResultText` 对 `RESTORED` 且 `n1 > 0` 用另一个语言键（多一句"其中 N 格因物品已不存在无法恢复……"）。命令路径同样追加一句。预览（`preview`）也读快照：同样的计数，预览的结果提示暂不带（预览载荷没有这个字段，不为它改协议；恢复时才提示，见 spec FR-014）。

## R12. 协议：版本列表的分页与类别

**已核对**：预览、恢复、删除请求在 031 就已经带 `time`（`SnapshotPreviewRequestPayload(backpack, time)`、`SnapshotActionPayload(kind, backpack, time, target, days)`），客户端 `SnapshotScreenState.versionTime()` 取所选行的 `time`。所以 FR-025（以时间标识）**现在就是这样做的，不需要改**；规格里写成需求是为了不退化。需要改的只有版本列表：`SnapshotVersionsRequestPayload(backpack)` 没有页，`SnapshotVersionsPayload(backpack, versions)` 一次发全部且 `SnapshotCodecs` 里上限 `MAX_VERSIONS`（16）；窗口的版本区最多 5 行（`SnapshotScreenLayout.MAX_VERSION_ROWS`）。

**决定**：请求加 `page`（1 起）、`pageSize`（1..16）、`kind`（0 全部、1 定时、2 变更、3 升级前）；应答加 `kind`、`page`、`pages`、`total`。服务端对越界页回最后一页（同 `onList` 的做法）。`MAX_VERSIONS` 改 100，只用于命令序号和 `total` 的上界（解码时校验行数 ≤ `MAX_PAGE_SIZE`）。`VersionRow` 加 `kind`（byte）与 `toVersion`（≤ 32 字符，非升级前为空）。**协议号 20 → 21**；八处写死 `versioned("20")` 的测试断言同改（`JukeboxWiringTest`、`BackpackSortWiringTest`、`MobileFurnaceWiringTest`、`LightWiringTest`、`PipeWiringTest`、`CategoryExtensionUiResourcesTest`、`WorldMarkerWiringTest`、`MobileCraftingWiringTest`）。

## R13. 窗口里的选择与翻页

**已核对**：窗口选择版本用页内序号（`selectedVersion` 1 起，指向 `versions` 列表），`versionTime()` 取时间；换背包时清空。

**决定**：翻页或换类别时选择清空（预览清掉），页内序号继续指向当前页；"删除"之后重新请求当前页。类别按钮循环 全部→定时→变更→升级前，换类别回第 1 页。版本区放不下翻页条与按钮时：布局把"页码文字 + 上一页/下一页 + 类别按钮"放在版本区头部那一行（头部本来有两行文字，`VERSION_HEADER_H = 22`），具体像素在实现时看截图定。

## R14. 命令的序号

**已核对**：`restore`/`delete` 的 `version` 参数 `IntegerArgumentType.integer(1)`（没有上限），再与 `versions.size()` 比较；GUI 不用序号。

**决定**：序号仍是"最新在前、按全部类别编号"的位置，范围校验为 1..`MAX_VERSIONS`（100）；`info` 每份显示类别（短标记：`T`/`C`/`U`，升级前再显示新版本）。

## R15. 读元数据的成本

**已核对**：`SnapshotStore.groups()` 对每个背包文件夹调用 `versions(...)`，对没缓存的文件 `readTag` 整个读出压缩 NBT 再取头；缓存在 `metaCache`（按文件路径）。每只背包最多 5 份时成本不显著；85 份时第一次列出所有背包要读 85 × 背包数个文件。

**决定**：先不改，**实现后实测**（quickstart 里有一项：造 20 只各 85 份的背包，量第一次 `/mcfb snapshot list all` 的耗时）；超过约 2 秒再优化（只读每个文件夹的最新一份的头，份数从文件名数）。`Group.versions` 的含义保持"认得的文件数"。

## R16. 开关关闭时

**决定**：`SnapshotService` 照常启动（查看、预览、恢复、命令都要它）；`noteUse`、`noteSaved`、定时、`onLogout`、`close` 里的写、版本钉住全部在 `!settings.enabled()` 时直接返回；已有文件不碰；容量淘汰也不跑（不写就不会超）。登录提示里"备份已关闭"。

## R17. 不做的事

- 迁移旧文件：版本 1 的文件按变更类读，不改名、不删除（它们没有后缀，本来就被当成变更类）。
- 在快照里保留不认识的物品（解码层保留）：用户已决定不做。
- 预览结果带"无法读取格数"：预览载荷没有这个字段，不为它改协议。
