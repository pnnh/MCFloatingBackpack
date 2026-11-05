# Research: 第三方模组兼容开关与客户端配置

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-05

读的是 NeoForge 21.1.251 源码、本仓库源码与构建文件、精妙背包/旅行者背包/JEI 的字节码（只作设计参考）。标"已查证"的是读源码得到的；标"未验证"的必须在客户端会话里确认（宪法 V）。

## Decision 1：配置 = 一个 CLIENT 类型的字符串列表 `modLevels`，元素校验器接受任何字符串

**Decision**: `ModConfigSpec.Builder.defineListAllowEmpty("modLevels", List.of(), () -> "modid=FULL", element -> element instanceof String)`，类型 `CLIENT`，文件 `config/mcfloatingbackpack-client.toml`；在 `MCFloatingBackpack` 构造里通过 `ModContainer.registerConfig(ModConfig.Type.CLIENT, SPEC)` 注册（构造函数改为接收 `ModContainer`）。每条形如 `modid=LEVEL`。

**Rationale**:
- NeoForge 的配置没有现成的"映射"类型，"字符串列表 + 自己解析"是最直接的键值对写法，也能被自带的配置界面编辑。
- 元素校验器**只检查是不是字符串**，把"档位写错、id 写错"留给我们自己的解析：`ModConfigSpec` 对列表的校验是"逐个元素用 `elementValidator` 测试"，只要有一个元素不通过，整个值会被判为不合法并被纠正回默认——那会让一个手误清掉玩家所有设置，违反 FR-002（"坏条目只影响自己"）。（元素纠正的具体行为由 `ListValueSpec.test` 的结构推断，**未单独验证**；quickstart 里实测。）
- `CLIENT` 类型只在客户端加载；在专用服务端注册它不会生效也不会加载，所以注册可以放在通用构造里。

**Alternatives considered**: 每个已知模组一个布尔/枚举配置项（被迫逐个维护，且对没测过的模组没有逃生口）；`COMMON` 或 `SERVER` 类型（本阶段只做客户端，服务端名单留给以后）。

## Decision 2：纯逻辑 `CompatPolicy` 与 `CompatLevel`

**Decision**: `CompatLevel`：`FULL`、`GENERIC`、`OFF`，`parse(String)`（不区分大小写，认不出返回 null）。`CompatPolicy.parse(List<String> entries)` 返回策略与警告列表：

| 输入 | 处理 |
|---|---|
| `modid=LEVEL`，id 合法、档位认得 | 记入 |
| 档位认不出 | 该条按 FULL（等于不记入）并产生警告 |
| id 不合法（不是 `[a-z0-9_]+`）或没有 `=` | 忽略并产生警告 |
| `minecraft=…` | 忽略并产生警告（原版不可配置） |
| 同一 id 多次出现 | 取最后一条并产生警告 |
| 两侧空白 | 去掉后再解析 |

查询：`levelOf(modId)`（没列出 = FULL，`minecraft` 恒 FULL）、`allowsInteraction(ownerModId)`（`levelOf == OFF` 为假）、`specificAdaptation(modId)`（只有 FULL 为真）。

**Rationale**: 无 Minecraft 依赖，可在只有 JUnit 的测试类路径里单测；"原版恒 FULL"收敛在 `levelOf` 一处，上层不用再判断。

## Decision 3：实时生效 = 按列表引用缓存，不订阅重载事件

**Decision**: `CompatConfig.policy()` 每次调用取 `MOD_LEVELS.get()`；与上次解析所用的列表**引用相同**就直接返回缓存的 `CompatPolicy`，不同才重新解析（并把警告写日志）。配置尚未加载时（`get()` 抛 `IllegalStateException`）返回默认策略（全部 FULL）。

**Rationale（已查证）**: `ModConfigSpec.ConfigValue.get()` 把读到的值缓存在 `cachedValue` 里，配置重载（文件被修改、配置界面保存）时对所有值调用 `clearCache()`，下一次 `get()` 才重新读取——所以"引用变了"恰好等价于"配置变了"，而没变时每帧的开销只是一次引用比较。这样不需要处理 `ModConfigEvent`，也不需要重启。

**Alternatives considered**: 每次都重新解析（每帧多次，浪费）；订阅 `ModConfigEvent.Reloading` 重建快照（多一处与事件总线的耦合，且要处理加载前后的时序）。

## Decision 4：界面归属 = 菜单类型的注册命名空间

**Decision**: `MenuOwner.ownerOf(AbstractContainerMenu)`：取 `menu.getType()`，在 `BuiltInRegistries.MENU` 里查键，键的命名空间就是模组 id；`getType()` 抛 `UnsupportedOperationException`（玩家物品栏菜单、创造物品栏的菜单没有类型）、类型未注册、或取不到时视为 `minecraft`。客户端用 `screen.getMenu()`；非 `AbstractContainerScreen` 的界面没有归属（不在本判断范围内，它们本来就是 019 的"其它界面"）。服务端用 `player.containerMenu`。

**Rationale**: 命名空间是模组自己注册菜单时用的，天然就是"这是谁的界面"，不需要认任何模组的类名；其它模组在原版菜单类型上打开的界面归原版（规格里写明的边界）。

**未验证**: 真实的精妙背包/旅行者背包界面的菜单类型命名空间是不是 `sophisticatedbackpacks`/`travelersbackpack`；精妙背包的"存储"类界面是否用 `sophisticatedcore` 的命名空间（若是，玩家要把 `sophisticatedcore` 也列上才能完全关闭）。quickstart 里用首次挂载的日志行直接读出。

**Alternatives considered**: 界面类的包名（`net.p3pp3rf1y…`，要认每个模组，且重定位/混淆后不可靠）；`ModList` 反查类所属模组文件（更重、跨加载器行为不明）。

## Decision 5：OFF 的落点收敛在 `ContainerScreenCompat.isOpenContainerScreen`

**Decision**: 该方法已被这些地方共用：`FloatingPanelEvents.hostsPanel`（面板是否挂载/渲染/收鼠标）、`HostScreenManager.kindOf`（019 的策略把它当 `CONTAINER` 还是 `OTHER`）、`FloatingPanelRenderer.cursorStack`、`FloatingPanelInput.sendClick` 与 `PanelDragInput` 里的 `screenOpen` 标志、`FloatingPanelEvents.onScreenOpening` 里发 `MergePanelCursorPayload`。让它在"所属模组 OFF"时返回 false，这些地方**全部自动**把该界面当作"其它界面"：面板隐藏（019 已有的语义：可见状态保留、关闭后宿主恢复）、不画、不拦截、不发合并请求。

另外两处需要补：
- `QuickTransferInput.supports(screen)`（Ctrl+点击容器槽位）自己判断 `AbstractContainerScreen`，不经 `isOpenContainerScreen`，要加同一个判断，否则 OFF 的界面上 Ctrl+点击仍会把物品转进（已隐藏的）面板。
- 三个手势输入类（011/013/014）的 `invalid(...)`：运行中把某模组改成 OFF 时，同一个界面对象还在，"界面变了"的取消条件不会触发，需要加"`!hostsPanel(screen)`"这一条（`hostsPanel` 对宿主与承载面板的容器界面为真）。

**Rationale**: 改一处影响全部，回归面最小；与 019 的界面分类完全吻合。

## Decision 6：GENERIC 与 JEI 适配

**Decision**: `FloatingBackpackJeiPlugin.getGuiExtraAreas()` 每次被 JEI 调用时问 `CompatConfig.policy().specificAdaptation("jei")`：为真照旧返回面板区域，为假返回空列表。JEI 的 OFF 与 GENERIC 相同（`specificAdaptation` 只有 FULL 为真）。除 JEI 之外没有别的专用适配，所以容器类模组的 GENERIC 与 FULL 行为相同——GENERIC 只关闭"我们为该模组写的专有代码"，不决定面板能否显示、能否交换物品（用户补充说明，见 Decision 12）。

**Rationale**: JEI 本来就在每次布局时调用 `getGuiExtraAreas`（`IGlobalGuiHandler` 的约定），读配置是常数开销，改配置后下一次刷新布局即生效，不需要重启（具体多久生效取决于 JEI 何时重新询问，**未验证**）。

## Decision 7：服务端恒真门禁 + 5 个入口

**Decision**: 纯类 `ServerCompatGate`：`enum Action { DELIVER_TO_CONTAINER, TAKE_FROM_CONTAINER, MERGE_CURSOR, USE_MENU_CURSOR }`，`static boolean allows(Action action, String ownerModId)` 现阶段恒返回 true。在服务端碰到"玩家当前打开的别的模组菜单"的入口先问门禁，不通过就直接 `return`（不改任何数据、不回包）：

| 入口 | 动作 |
|---|---|
| `BackpackTakeFromContainerPayload.handleServer`（从打开的容器取出） | `TAKE_FROM_CONTAINER` |
| `BackpackQuickTransferPayload.handleServer`（Ctrl 转出时投递给打开的容器，`hasOpenContainer` 分支） | `DELIVER_TO_CONTAINER` |
| `MergePanelCursorPayload.handleServer`（合并面板光标进菜单携带物） | `MERGE_CURSOR` |
| `BackpackClickPayload.handleServer`（`screenOpen` 时用菜单携带物做光标） | `USE_MENU_CURSOR` |
| `BackpackCursorGesturePayload.handleServer`（同上，拖动/收集手势） | `USE_MENU_CURSOR` |

归属用 `MenuOwner.ownerOf(player.containerMenu)`；玩家自己的物品栏菜单（`player.inventoryMenu`）归原版，门禁对原版也恒允许。

**Rationale**: 宪法 II 要求影响物品移动的判断在服务端；先把入口统一起来，以后有服务端名单时只改 `allows` 一处。不改协议、不加服务端配置。

**Alternatives considered**: 现在就做服务端配置（用户已决定先不做）；在每个入口里散写判断（以后要改 5 处）。

## Decision 8：配置界面与文字

**Decision**: `ClientSetup.register` 里 `modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`；配置项用 `.translation("config.mcfloatingbackpack.modLevels")`，语言文件里提供 `config.mcfloatingbackpack.modLevels` 与 `.tooltip`（三档含义、容器类模组 FULL=GENERIC、JEI 的 OFF 等同 GENERIC、`minecraft` 不可配置、示例 `sophisticatedbackpacks=OFF`）。配置文件里同样写 `.comment(...)`（英文，配置文件不本地化）。**（已查证）** `IConfigScreenFactory` 注册后配置界面出现在模组列表里；`ConfigurationScreen(ModContainer, Screen)` 构造函数存在。字符串列表项在配置界面里能否新增/编辑**未验证**。

## Decision 9：热键提示与首次挂载日志

**Decision**:
- `FloatingPanelEvents.onKey`：现有逻辑在 `!allowsPanel(screen)` 时直接返回；在返回前，若 `screen` 是容器界面但被 OFF（`AbstractContainerScreen` 且 `!isOpenContainerScreen`），给 `minecraft.player.displayClientMessage(..., true)`（action bar），文字带模组 id（语言键 `message.mcfloatingbackpack.panel_off`）。
- `AttachNotice.onAttach(ownerModId)`：在 `onScreenOpening` 里，容器界面被判定可承载且归属不是 `minecraft` 时调用；用一个静态 `Set<String>` 保证每个模组每次启动只记一行 INFO："面板挂在 `<modid>` 的界面上；若有冲突，可在配置 `modLevels` 里加入 `<modid>=OFF`"。

**Rationale**: 玩家不用猜模组 id；只记一次不刷屏（SC-009）。

## Decision 10：依赖规则的自动检查

**Decision**: 在测试源码里写一个纯 Java 检查器 `DependencyRules`（无外部库），三个检查：
1. **`mods.toml`**：按 `[[dependencies.…]]` 块切分，读 `modId` 与 `type`；除 `neoforge`、`minecraft` 之外的块必须 `type="optional"`。
2. **import**：遍历 `src/main/java` 的 `.java`，对每条 `import`（含 `import static`）取包名；白名单前缀 `java.`、`javax.`、`com.mojang.`、`net.minecraft.`、`net.neoforged.`、`io.netty.`、`com.google.`、`org.slf4j.`、`org.lwjgl.`、`org.apache.`、`it.unimi.`、`pnnh.mcfloatingbackpack.`；不在白名单的 import 只允许出现在路径含 `/compat/<名字>/` 的文件里。（**已查证**：当前代码里非白名单的只有 4 条 `mezz.jei`，都在 `compat/jei`。）
3. **`build.gradle`**：只看依赖声明行（形如 `<配置名> "<group>:<name>:<version>"`），坐标 group 为 `curse.maven`、`maven.modrinth`、`mezz.jei` 的，配置名只能是 `compileOnly` 或 `localRuntime`；`includeGroup`、仓库 `url` 这类行不是依赖声明，不检查。

每个检查器用内联夹具文本做反例（违规必须被判为失败并指出位置），并对真实仓库文件各跑一遍，当前必须通过。

**Rationale**: 测试类路径里只有 JUnit，所以用纯 Java 读文件；约定变成会失败的检查，满足宪法 VII 的"必须由自动化测试检查"。

## Decision 11：不改的东西

- 网络协议保持 `"8"`；不新增网络包。
- 019 的按键"让出再重放"与模组无关，不进配置（FR-013）。
- `ContainerScreenCompat.CompatTarget/identify` 保留不动（整理项）。
- 不新增 mixin、不引入新的必需依赖。

## 仍不确定、必须在客户端确认的点

1. 真实精妙背包/旅行者背包界面的菜单类型命名空间是否就是它们的模组 id（以及 `sophisticatedcore`）。
2. `ModConfigSpec` 对列表里"形如 `modid=LEVEL` 的任意字符串"都接受，一个坏条目不影响其它条目。
3. 配置界面里字符串列表项能否新增/编辑；保存后是否立即生效。
4. JEI 切到 GENERIC 后，多久之后不再避让（取决于 JEI 何时重新询问）。
5. 把正显示的界面的模组改成 OFF 时，面板与进行中的手势的表现。
6. JourneyMap 小地图这类画在游戏画面上的 HUD 与面板的重叠（只登记，不在本 feature 处理）。

## Decision 12（2026-11-05 修订）：GENERIC 的含义（澄清）；聊天提示；"配置被重置"的原因

**GENERIC 的含义（用户补充说明，已澄清）**: FULL = 专用适配（我们为该模组写的专有代码，代表主动兼容，可视为功能增强）；GENERIC = 没有专用适配（关闭我们为该模组写的任何专有代码；没写过的模组与 FULL 一样）；OFF = 面板不显示（面板与该模组的界面二选一）。GENERIC **不决定**面板能否显示在该模组的界面上、能否与它的菜单做 Ctrl+点击转移——这取决于该模组自己的实现，实现得越接近原版容器，我们理论上能给的兼容就越好；我们不根据是不是 GENERIC 来判断是否支持 Ctrl+点击转移。（第一轮验证时我把它误读成"GENERIC 应关闭物品交换"并改过一版代码，用户补充说明后已撤销。）

**聊天提示**: 用 `Minecraft.gui.getChat().addMessage(Component)`：只写进本机的聊天记录，不发给服务器或其他玩家，玩家按 T 能再次看到；**未验证**这行调用在所有时机（例如刚进入世界的第一个 tick）都能显示，所以队列在玩家存在之后才排空。

**配置被重置（已查证）**: FML 4.0.44 的 `ConfigTracker.loadConfig`：读取现有文件时抛 `IOException`/`ParsingException`，就记 WARN "Failed to load config … Attempting to recreate"，`backUpConfig(path)`（改名为 `*-N.toml.bak`）、`Files.delete(path)`、`setupConfigFile`（写默认配置）再读入；如果能读但 `!isCorrect(config)`，则记 "Configuration file … is not correct. Correcting"，同样先备份再纠正。第一轮验证的日志里有两次 `Failed to load config mcfloatingbackpack-client.toml … Invalid newline in basic string`，对应的两个 `.bak` 文件里一个有 `"mcfloatingbackpack` 另一个有单独一个 `"`——都是编辑器在游戏运行时自动保存下的写了一半的引号字符串，文件监视器马上重新读取并触发了恢复。所以"没改完就被重置成空数组"是 NeoForge 的行为。本模组无法关闭文件监视，能做的是用 `ModConfigEvent.Loading/Reloading` 发现刚生成的 `.bak` 并在聊天里提醒（见 `ConfigFileWatch`）。
