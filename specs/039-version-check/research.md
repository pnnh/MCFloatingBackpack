# Research: 版本一致性提示

2026-11-10。"已核对"= 读 NeoForge 21.1.251 字节码或本仓库源码确认；"待验证"写进 quickstart，验证前不得声称已满足。

## D1. 可选网络包：NeoForge 的行为（已核对字节码）

- **注册器是不可变副本**：`PayloadRegistrar.versioned(String)` 与 `optional()` 都是 `new PayloadRegistrar(this)` 复制后改一个字段再返回。所以 `event.registrar(MOD_ID).versioned("1").optional()` 得到的是一个独立的注册器，不会改动现有 `event.registrar(MOD_ID).versioned("17")` 那个。（`event.registrar(String)` 的参数是初始版本号而不是模组 id；现有代码先写 `registrar(MOD_ID)` 再 `versioned("17")` 覆盖它，本功能同样写法。）
- **协商**：`NetworkComponentNegotiator.negotiate` 对"一方有、另一方没有"的组件：**可选**的被归入"禁用的可选组件"（`buildDisabledOptionalComponents`），连接继续；**非可选**的缺失才失败（`neoforge.network.negotiation.failure.missing...`）。同一个 id 两端都有但版本字符串不同则失败（`failure.version.mismatch`）——所以本功能的包版本 `"1"` **不能再改**，以后要演进就新增另一个包类型。
- **向没有通道的客户端发包会抛异常**：`NetworkRegistry.checkPacket` 对服务器发往客户端的 `ClientboundCustomPayloadPacket`，若客户端没有这个通道（`hasChannel(listener, id)` 为假）就 `throw new UnsupportedOperationException("Payload %s may not be sent to the client!")`。**不是静默丢弃**。所以服务器必须先 `connection.hasChannel(VersionHelloPayload.TYPE)`。
- `ICommonPacketListener.hasChannel(CustomPacketPayload.Type)` 存在（反编译核对过）。通道集合在配置阶段结束时设置（`NetworkRegistry.onConfigurationFinished`），而 `PlayerLoggedInEvent` 在玩家进入游戏阶段之后才触发，所以此时 `hasChannel` 应当可用。**待验证**：两个真实版本的连接（quickstart G）。

## D2. 版本字符串

- 取法：`ModList.get().getModContainerById(MOD_ID)` → `getModContainer().getModInfo().getVersion()`（`ArtifactVersion`，`toString()` 是原始字符串）。`getModContainerById` 已核对存在；`IModInfo.getVersion` 在 `neoforgespi` 里，实现时编译确认。
- 开发运行里 `neoforge.mods.toml` 由 `generateModMetadata` 展开，`version="${mod_version}"`，所以是 `gradle.properties` 的 `0.1.0`（发布 jar 里核对过是 `0.1.0`）。取不到时（异常、空）按"未知"处理，比较结果 `UNCOMPARABLE`。

## D3. 比较规则（`ModVersions`）

解析：`MAJOR.MINOR[.PATCH][-预发布][+构建元数据]`，三段整数；PATCH 缺省按 0；允许前导 `v`；其余不规范的（含空、`null`、非数字）→ 解析失败 → `UNCOMPARABLE`。

比较（客户端 `c` 与服务器 `s`）：
1. 先比 `(MAJOR, MINOR)`：`c < s` → `CLIENT_OLDER`，`c > s` → `CLIENT_NEWER`；
2. 相等则比预发布：按语义化版本的优先级（**没有预发布 > 有预发布**；都有时逐个标识符比，数字标识符按数值、字母标识符按字典序，数字 < 字母，前缀更短的更小）；`c < s` → `CLIENT_OLDER`，`c > s` → `CLIENT_NEWER`；
3. 都相同 → `MATCH`（**PATCH 与构建元数据不参与**）。

由此：`0.2.1` vs `0.2.0` → `MATCH`；`0.2.0-rc1` vs `0.2.0` → `CLIENT_OLDER`；`0.2.0` vs `0.2.0-rc1` → `CLIENT_NEWER`；`0.2.0-rc1` vs `0.2.1`（服务器没有预发布）→ `CLIENT_OLDER`；`0.2.0-rc1` vs `0.2.1-rc1` → `MATCH`。0.x 阶段 MINOR 就是规则变化的级别，PATCH 不警告对 `0.x.y` 同样成立（release-process.md）。

## D4. 谁不被提示：`isSingleplayerOwner`

- **已核对**：`MinecraftServer.isSingleplayerOwner(GameProfile)`、`isSingleplayer()`、`isDedicatedServer()`、`isPublished()` 都存在；`isSingleplayerOwner` 在整合服务器上比较玩家名与房主，在专用服务器上恒为假。
- **决定**：服务器登录钩子里只用 `server.isSingleplayerOwner(player.getGameProfile())`：为真（单人游戏的玩家、局域网房主本人）→ 不发 hello、不发纯文本、不记日志；为假（专用服务器上的所有人、局域网里别的玩家）→ 照常。局域网里别的玩家会被提示，依据的是房主客户端的版本。

## D5. 客户端什么时候显示

- hello 到达时在客户端主线程（默认 `HandlerThread.MAIN`）；`PlayerLoggedInEvent` 在服务器 `placeNewPlayer` 的最后触发（登录包、"某某加入了游戏"的广播都已发出），所以客户端此时已进入世界、聊天可用。
- 仍然稳妥一点：收到后把"待显示的提示"存起来，在**下一个客户端 tick**（`ClientTickEvent.Post`，`minecraft.player != null && minecraft.gui != null`）显示一次，沿用现有的 `FloatingPanelEvents.chat`（带模组名前缀、只给本人）。避免登录瞬间的刷屏把提示冲到上面，也避免聊天未就绪时丢失。**待验证**：实际看到的位置（quickstart）。
- 每次登录最多一次：待显示的提示是一个单值，显示后清空；断线时（`ClientPlayerNetworkEvent.LoggingOut`）也清空。

## D6. 服务器侧：记录与诊断头部

- `PlayerVersions`（`ConcurrentHashMap<UUID, String>`）：hello 发出时记 `"尚未报告"`；老客户端记 `"无版本检测（版本过旧）"`；report 到达记客户端版本；退出清除。
- report 到达：服务器用同一个 `ModVersions` 比较（客户端与服务器反过来看），不一致记 WARN（玩家名、客户端版本、服务器版本、结果），一致记 DEBUG；日志经 `DiagLog.log(SERVER, …)`，遵守已有的 `logging.level`。
- `BackpackWatchService` 的"不见了"WARN 头部加 `client mod version X`（`PlayerVersions.describe(uuid)`）。

## D7. 验证用的调试开关（默认关，只读系统属性）

只有一个开发者能用，`VersionDebug` 读：
- `mcfloatingbackpack.debug.checkOwner=true`：服务器不再跳过房主（这样单人游戏的整合服务器也走一遍握手）；
- `mcfloatingbackpack.debug.serverVersion=X`：服务器发给客户端的版本改成 X；
- `mcfloatingbackpack.debug.clientVersion=X`：客户端用 X 当自己的版本（比较与 report 都用它）；
- `mcfloatingbackpack.debug.legacyClient=true`：服务器把所有玩家当作没有这个通道（走纯文本分支），用来验证文字与日志。

`build.gradle` 的 `runs.client` 把 `-Pmcfb.<名字>=<值>` 转成 `-Dmcfloatingbackpack.debug.<名字>=<值>`（没传就什么都不加）。这样一个客户端就能验证：不一致两种方向、PATCH 不同、预发布不同、无法比较、老客户端文字与日志、单人游戏不提示；**真实的老客户端登录协商仍需要两个客户端**（局域网：新构建当房主，038 的构建当加入者），由用户手动验证。

## D8. 预测宽容

- **已核对**：`ItemCategoryMatcher.matches` 在 `KEYS.get(category) == null`（不认识的类别）时返回假；服务器对未知类别的行也据此拒绝放入（035：未知类别的行保留数据但不收东西）。客户端 `ClientPlacement.canPlace` 用同一个匹配器，所以版本落后的客户端会对它不认识的类别行误显示"放不进"。
- **决定**：只改客户端：`RowLayout.isUnknownRow(index)`（该格属于某个可见行且该行类别不在 `ExtensionCategories.ALL`）为真时，`ClientPlacement.canPlace` 返回放行；服务器与 `ExtensionRules` 不动。已知类别（10 个与通用）的预测完全不变。
- 局限：不认识类别的行的悬停说明会显示一个找不到的语言键（`CategoryNames.langKey` 对未知 id 给预设键）；这一版不处理，记为已知限制。

## D9. 已知局限（写进交付说明）

- 开发版之间版本都是 `0.1.0`，代码不同也检测不出来（"发布前才改版本号"的约定）。
- 本功能只提醒，不阻止登录、不踢人；玩家可以无视。
- 老客户端的提示是纯文本，不随语言切换。
- 局域网里别的玩家被提示依据的是房主客户端的版本。
- 专用服务器没有跑过（项目约定不运行专用服务器）；用整合服务器与局域网代替。

## 尚未验证（写进 quickstart）

1. 新构建当局域网房主、038 的构建当加入者：老客户端**仍能登录**，并收到纯文本提示（D1、D6 的核心假设）。
2. 两个新构建版本不同（用调试开关）：提示的位置、文字、只给本人。
3. `hasChannel` 在 `PlayerLoggedInEvent` 里的真实取值。
4. 客户端回的 report 在服务器日志里出现。
5. 对现有网络包没有任何影响（协议 17 的两端连接照常）。
