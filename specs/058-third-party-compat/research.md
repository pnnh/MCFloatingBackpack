# Research: 第三方模组版本管理与集成终端主动兼容

方案阶段的取舍已在 [docs/plan/third-party-compat.md](../../docs/plan/third-party-compat.md) 里与用户确认；本文只记 plan 阶段**新查证**的事实与由此做出的设计决定。查证对象：集成终端 1.11.2、集成动力学 1.39.1、Cyclops Core 1.30.2（Modrinth 版本 id `mbABgx56`）的 jar（`javap` 读类与方法签名）、FML 4.0.44 的 `ModSorter`（前一阶段读过）、本仓库现有代码。

## R1　FML 对"已装的可选依赖版本不在 versionRange 里"的处理（前阶段已查，复述结论）

`ModSorter.verifyDependencyVersions` 对已安装的可选依赖也核对版本范围，不满足会让加载中止。**结论**：新兼容的 `neoforge.mods.toml` 一律 `versionRange="[0,)"`；范围只在我们自己的表里。现有的下限（Curios `[9,)` 等）保持不动，058 不改它们。

## R2　版本字符串的解析：不复用 `VersionOrder`

`snapshot/VersionOrder`（057）取字符串里**所有**数字段，用来给"升级/降级"选措辞。对范围判断不合适：`1.1.0-beta.54+1.21.1` 会得到 `1,1,0,54,1,21,1`，`9.5.1+1.21.1` 得到 `9,5,1,1,21,1`，与 `9.5.1` 的比较会因多出来的段而错。

**决定**：新写 `ThirdPartyVersion`：找到第一个数字，往后取"数字（`.` 数字）*"，遇到第一个不满足的字符就停（`+`、`-`、字母、`..`、末尾的 `.` 都停），其余忽略；没有数字返回 null（无法判断）。比较时缺的段补 0（`1.11` 等于 `1.11.0`）。`VersionOrder` 不动（它对"措辞"够用）。

实测的串（来自本仓库与已装的 jar）：`1.11.2`→[1,11,2]；`1.39.1`→[1,39,1]；`2.11.6`→[2,11,6]；`1.30.2`→[1,30,2]；`9.5.1+1.21.1`→[9,5,1]；`1.1.0-beta.54+1.21.1`→[1,1,0]；`21.1.22`→[21,1,22]；`1.12.0-beta`→[1,12,0]（与 `1.12.0` 相同，不在 `[1.11.0, 1.12.0)` 内，与规格边界情形一致）；`v2.0`→[2,0]；`snapshot`、`""`、`null`→无法判断。

## R3　集成终端 1.11.2 的类与方法（`javap` 查证，覆盖规格里的"源码证据"）

- 模组 id：`integratedterminals`（依赖 `cyclopscore`、`integrateddynamics`、`commoncapabilities`，都是 NeoForge 常规写法）；集成动力学 `integrateddynamics`；Cyclops Core `cyclopscore`（1.30.2）；公共能力 `commoncapabilities`。**这四个 id 就是 `neoforge.mods.toml` 里要写的 `modId`**。
- `ContainerTerminalStorageBase<L>` 有 `getSelectedTab()`（String）、`getSelectedChannel()`（int）、`getTabServer(String)`（返回原始类型 `ITerminalStorageTabServer`）、`getTabsServer()`、`quickMoveStack(Player, int)`。
- `TerminalStorageTabIngredientComponentServer<T, M>`：`getIngredientNetwork()` 返回 `IPositionedAddonsNetworkIngredients<T, M>`；后者有 `getComponent()` 与 `getChannel(int)`（返回 `INetworkIngredientsChannel<T, M>`，它**继承** `IIngredientComponentStorage<T, M>`，有 `insert(T, boolean)`）。
- `TerminalStorageTabIngredientComponentItemStackCraftingServer extends TerminalStorageTabIngredientComponentServer<ItemStack, Integer>`：**合成页是存储页类的子类**，所以只判 `instanceof TerminalStorageTabIngredientComponentServer` 会把合成页也认进来；桥要明确排除这个子类（`FR-017`）。
- 末影箱页是独立的类 `TerminalStorageTabEnderChestServer`，与上面无继承关系，`instanceof` 天然排除。
- `IngredientComponent.ITEMSTACK` 是 `IngredientComponent<ItemStack, Integer>` 的静态字段，用 `tab.getIngredientNetwork().getComponent() == IngredientComponent.ITEMSTACK` 判"物品成分"。
- `TerminalClickType` 有 `PLAYER_QUICK_MOVE`、`PLAYER_QUICK_MOVE_INCREMENTAL`（客户端驱动的"玩家物品栏 → 终端"）；`handleStorageSlotClick` 里的取通道调用就是 `IPositionedAddonsNetworkIngredients.getChannel(int)`。我们的 `insert` 与它做的是同一件事，只是由服务端直接发起。
- `quickMoveStack` 对存储页返回空（前阶段读源码的结论）本阶段没有再核对一遍，游戏内验证（SC-007）会坐实。

**设计后果**：`handles`/`insert` 用到的第三方类型：`ContainerTerminalStorageBase`、`TerminalStorageTabIngredientComponentServer`、`TerminalStorageTabIngredientComponentItemStackCraftingServer`、`IPositionedAddonsNetworkIngredients`（来自集成动力学）、`IngredientComponent`（来自公共能力）；Cyclops Core 只因类的继承链（`InventoryContainer`）需要出现在编译类路径里，代码不直接引用。泛型用 `@SuppressWarnings("unchecked")` 处理，`getTabServer` 是原始类型，必须强转。

## R4　这些 jar 是不是已经在本机缓存里

Gradle 缓存里 `integrated-terminals 1.11.2`、`integrated-dynamics 1.39.1`、`cyclops-core mbABgx56` 此刻只有 `.pom`（`dependencies` 任务解析了图，没下载 jar），1.10.0 / 1.37.1 才有 jar。**不影响实现**：第一次 `compileJava` 或 `runClient` 会下载；本阶段为查证直接从 Modrinth 的 maven 下载了三个 jar 放在会话临时目录里 `javap`（未进仓库）。任务里第一条是"让 `compileOnly` 生效后编译通过"，用它确认坐标可解析。

## R5　`DependencyRules` 不需要新增组

规格 FR-023 原本写"加入这几个组"。读 `DependencyRules`：`THIRD_PARTY_GROUPS` 已含 `maven.modrinth`，四个模组都在这个组下；检查导入的规则是"白名单之外的包前缀都算第三方，只允许在 `compat/<modid>/<文件>` 下出现"，新包 `org.cyclops.*` 自然被归为第三方。**结论**：规则代码不改；加一条测试用"桥类放在 `compat/` 根目录就失败"的反例证明它真在检查（同现有 `aThirdPartyImportDirectlyInCompatRootFails` 的写法）。FR-023 已据此改写。

## R6　版本门的位置与"要求的模组"

- 表行 `integratedterminals`：自己的段 `[1.11.0, 1.12.0)`；要求 `integrateddynamics` `[1.39.0, 1.40.0)`、`commoncapabilities` `[2.11.0, 2.12.0)`。这三个是开发验证过的版本（1.11.2 / 1.39.1 / 2.11.6）的主.次窄段。
- Cyclops Core（1.30.2）**不进要求列表**：它不是我们直接引用的类型来源，而且它的版本与集成终端强绑定（集成终端自己的 `mods.toml` 已要求）；只在我们的 `mods.toml` 里作为可选依赖声明。
- **要求的模组没装**：集成终端本身没有集成动力学就起不来，所以不会出现；若真出现，该行视为"不适用"（不启用、不警告），因为"没装的模组不检查不提示"（FR-006）。
- 判断顺序：先自己，再按声明顺序逐个要求；第一个不合格的写进结论（`CompatFinding` 里有"是哪个模组"）。

## R7　出错兜底的语义：保证不丢，不保证不重复

桥的 `insert` 对集成动力学网络调用 `insert(copy, false)`。若调用抛异常：

- 抛在网络真正接收物品**之前**（`NoSuchMethodError`、找不到类、空指针）：网络没动，背包里的原件还在，不丢不重复；
- 抛在网络已经接收**之后**（极少见，例如通知客户端时出错）：物品已在网络里，背包里的原件还在，**重复**。

分派层拿不到"接收了多少"，没法补救。方向选"宁可重复，不可丢"（宪法 II 与 012 的守恒口径：012 出错时也是"物品在槽里取回"）。**记入 DELIVERY 的已知限制**，不为此加反射或事务。出错后该次操作转回通用路径（在终端界面上它什么也不移，无害），之后桥被关闭。

## R8　返回值检查

桥返回的"剩余"必须是：空，或与原物品同一种且数量 ≤ 原数量。违反则当作出错处理（日志 + 关闭桥，背包保持原样，不信任返回值）。这挡住"桥把剩余写成别的物品或更大数量"的 bug；用 `QuickTransferLogic.StackOps<T>`（012 已有的抽象）判同种与数量，纯逻辑可测。

## R9　通知谁、什么时候

- **启动 WARN**：`ServerStartingEvent`（与快照服务同一处），每个已安装且范围外的行一条；"主动兼容是未启用还是被配置强制启用"写进同一条。
- **登录聊天**：`PlayerLoggedInEvent`，`hasPermissions(2)`，对当前状态里每个范围外（`OUT_OF_RANGE` 或 `FORCED`）的行一条；**不记名单**（持续存在的状态，每次登录都发；与 057 的一次性升级提示不同）。`FAILED` 状态**不**在登录时补发（规格 SC-004：错误通知每次运行至多一次，发给出错当时在线的 op；之后日志里有）。
- **出错通知**：`CompatRuntimeState.fail(modId)` 返回"是不是第一次"，只有第一次写 ERROR 并发聊天；发给 `server.getPlayerList().getPlayers()` 里 `hasPermissions(2)` 的人。

## R10　配置的读取方式

`ThirdPartyConfig` 照 057 的 `SnapshotConfig`：在 `DiagConfig` 的服务端配置里 `define`，`ServerStartingEvent` 里读一次；服务端配置还没加载（`IllegalStateException`）时取默认 `false`。配置节叫 `[compat]`，与客户端配置里的 `compat.modLevels` 不冲突（不同文件）。

## R11　调试开关

`-Pmcfb.fakeModVersion=integratedterminals=2.0.0`：`build.gradle` 现有的 `-Pmcfb.<name>` → `-Dmcfloatingbackpack.debug.<name>` 转发已通用（057 查过），`VersionDebug.fakeModVersions()` 解析逗号分隔的 `modid=版本`。**只对已安装的模组生效**：伪造不会让没装的模组"变成已装"（那样桥类根本不存在）。

## R12　通用路径与桥的先后、与 `ServerCompatGate` 的关系

`backpackOut` 现有顺序：`hasOpenContainer`（菜单有玩家物品栏之外的格子）→ `ServerCompatGate.allows(DELIVER_TO_CONTAINER, owner)` → 对每个物品 `OpenContainerDelivery.deliver`。桥插在最后一步前面：**对每个物品先问桥**，桥不认领才走 `deliver`。集成终端的菜单有玩家物品栏之外的格子（它自己的合成槽等），`hasOpenContainer` 为真；如果某个菜单没有这类格子而桥认领了，也应该由桥处理——但 058 不改 `hasOpenContainer` 的判断（集成终端满足现有条件，已验证 `ContainerTerminalStorageBase` 继承 `InventoryContainer`，有自己的格子）；这一点用游戏内验证确认。

## R13　没有选用的做法

| 做法 | 为什么不选 |
|---|---|
| 让桥直接模拟终端客户端发的 `PLAYER_QUICK_MOVE` 点击 | 要构造集成终端的点击包与客户端状态；直接对网络通道 `insert` 更小、更不依赖其内部点击协议 |
| 复用 `VersionOrder` | 见 R2 |
| 表放进数据文件（JSON）由玩家改 | 范围是"我们验证过什么"，是代码的一部分；玩家要放宽用 `allowUntestedVersions` |
| 把 Cyclops Core 也放进"要求"列表 | 见 R6 |
| 登录时补发 `FAILED` 状态 | 与 SC-004 冲突；日志已有 |
| 桥注册时就看版本，范围外干脆不注册 | 版本要在服务器启动时（配置已加载）才能判；注册在模组加载阶段，所以注册无条件做、调用时看状态 |
