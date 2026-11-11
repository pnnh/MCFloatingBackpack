# Delivery: 020-compat-switches

**日期**: 2026-11-05

**状态**: 代码、单元测试、编译、静态检查与文档完成；用户做了第一轮客户端验证（见下）并提出 4 点，**已修订，修订后的重测（T036）还没有做**；其余客户端验证（T011、T014、T017、T025、T028）保持"未验证"。在你进游戏确认之前，不要把这个 feature 当作已验证。注意：开始实现时有一个客户端进程在运行（pid 33478），我**没有启动也没有终止任何客户端**；做客户端验证前请先退出它。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| 改动前基线（T001） | `./gradlew compileJava test` 通过；38 个测试类 |
| `./gradlew compileJava test`（T004、T010、T013、T016、T020、T024 及最后一次） | 全部通过：42 个测试类、349 个用例、0 失败（新增 4 个测试类） |
| `CompatPolicyTest`（12 个用例） | 档位解析（大小写、空白、认不出）；空列表全部 FULL；`x=off/Off/OFF`；非法档位按 FULL 并警告；非法 id（大写、空格、`=OFF`、无 `=`、连字符、点）忽略并警告；`minecraft=OFF` 忽略并警告；重复取最后一条并警告；一条坏条目不影响其它；null/空白条目忽略；只有 OFF 拒绝互动；只有 FULL 保留专用适配；`jei=OFF` 与 `jei=GENERIC` 都不做专用适配 |
| `ServerCompatGateTest`（2 个用例） | 所有动作 × 各种归属（含 null、空串）都返回允许 |
| `DependencyRulesTest`（13 个用例） | 检查器本身（`mods.toml` 的 required/缺 type、import 白名单、`compat/<modid>/` 内外、`compat/` 根目录下不允许、`import static`、`compileOnly`/`localRuntime` 通过与 `implementation`/`runtimeOnly`/`api`/`jarJar`/`compileOnlyApi` 失败、`includeGroup`/`url` 不误判）与三个对**真实仓库**的用例（当前全部通过） |
| `CompatConfigResourcesTest`（2 个用例） | 配置界面与热键提示的语言键中英文都有且键集合相同；配置项引用的 `translation` 键存在 |

## 负向验证（T020，自己做，已还原）

对依赖检查做了三次**临时**破坏，每次都确认测试失败，再用 `git checkout` 还原：

1. 把 `neoforge.mods.toml` 里 `jei` 的 `type="optional"` 临时改成 `"required"` → `DependencyRulesTest` 失败（13 个中 1 个失败）。
2. 在 `client/panel/FloatingPanelState.java`（不在 `compat/<modid>/` 下）临时加 `import mezz.jei.api.IModPlugin;` → 失败（1 个失败）。（第一次用 BSD `sed` 没有插入成功，测试没有失败，所以改用 Python 插入后重做。）
3. 把 `build.gradle` 里 `localRuntime "mezz.jei:jei-1.21.1-neoforge:…"` 临时改成 `runtimeOnly` → 失败（1 个失败）。

三次都还原后重新跑，全部通过；`git status` 确认这三个文件没有残留改动。

## 静态检查（T027）

- `neoforge.mods.toml` 与 `build.gradle` 没有任何改动；没有 mixin；协议版本仍为 `"8"`；没有新增必需依赖。
- `git diff --stat`：`QuickTransferInput`、`PanelDragInput`、`PanelSelectionInput` 各只有 7 行改动（`supports`/`invalid` 的新条件与 import），`QuickTransferGesture/Rules` 无改动。
- `grep -rln "mezz.jei" src/main/java` 只命中 `compat/jei/FloatingBackpackJeiPlugin.java`。
- `grep -rn "ServerCompatGate.allows" src/main/java` 命中 5 个服务端入口。
- `grep -rn "setScreen(" src/main/java` 与 019 一致（宿主管理器与宿主的让出，没有新增）。

## 与 plan/tasks 的偏离

- **`MenuOwner` 只有 `ownerOf(AbstractContainerMenu)`**：没有 `Screen` 版本，`ContainerScreenCompat.isOpenContainerScreen` 直接用 `AbstractContainerScreen.getMenu()`（服务端安全，只用 `net.minecraft` 类型）。
- **服务端入口 `BackpackQuickTransferPayload`** 的门禁放在 `backpackOut(...)` 里 `hasOpenContainer` 为真之后；被拒绝时直接返回（该次转出不生效），不回退到玩家物品栏。现阶段恒允许，所以不影响行为。
- **`CompatConfig` 把配置放在 `compat` 子段**（`[compat]` → `modLevels`），所以配置界面会多一级"模组兼容"分区；相应加了 `mcfloatingbackpack.configuration.*` 语言键（键名读 `ConfigurationScreen` 源码确认）。
- **测试编译有 2 条 `Dist.CLIENT` 注解的警告**（"class file for Dist not found"），来自测试类路径里没有 NeoForge；只是警告，不影响结果。

## 行为变化（需要你确认能否接受）

- 新增客户端配置 `compat.modLevels`；**默认空，所有行为与改动前一致**。
- 把某个模组设为 `OFF` 后，它的容器界面上面板隐藏、不拦截、不转移、不发合并光标请求，关闭后恢复；在那里按面板热键给一条 action bar 提示。
- 面板第一次挂在某个非原版模组的界面上时，日志记一行"若有冲突，可加入 `<modid>=OFF`"（每个模组每次启动一次）。
- JEI 的避让受 `jei` 档位控制（FULL 才避让）。

## 第一轮客户端验证（用户，2026-11-05）与修订

| 项 | 用户的结果 | 状态 |
|---|---|---|
| 运行中把精妙背包改成 OFF | 无法在它之上打开面板 | 通过（用户确认） |
| **改成 GENERIC 仍能 Ctrl+点击转移物品，问为什么** | 询问后用户补充说明了三档的含义 | **已澄清并写进文档/配置说明**（见下 1）；代码回到原设计 |
| **OFF 时按 B 的提示一闪而过** | 看不清 | **已改到聊天区，待重测**（见下 2） |
| **配置写错应在聊天区提示** | 需要 | **已实现，待重测**（见下 3） |
| **配置文件被重置成空数组** | 不知道什么时候发生 | **已查明原因**（见下 4） |

### 1. GENERIC 的含义（先误解后澄清）

你问"为什么 GENERIC 下 Ctrl+点击仍能转移物品"，我一度理解成"GENERIC 应当关掉物品交换"，改了一版代码和宪法（2.2.0，未提交）。你随后补充的说明纠正了这个理解：

- **FULL**：专用适配——开启我们为这些模组写的专有代码，代表我们对它们做了主动兼容，也可以看作功能增强。
- **GENERIC**：没有专用适配——关闭我们为这些模组写的任何专有代码（许多模组我们本来就没写，此时 GENERIC 与 FULL 一样）。它**不决定**面板能不能显示在该模组的界面上、能不能与它的菜单 Ctrl+点击互相转移物品：这取决于该模组自己的实现，实现得越符合原版容器与面板的行为，兼容得越好；即使关闭了所有专有代码，Ctrl+点击仍然可能互相转移，也可能不行，我们不根据是不是 GENERIC 来判断。
- **OFF**：面板不显示；面板与该模组的界面二选一。

所以**那一版已完全撤销**：代码回到原设计（只有 `isOpenContainerScreen` 一个显示判断，`grep` 确认没有 `usesMenuCursor`/`blocksItemExchange`/`exchangesItems`），宪法改为 **2.1.1**（只补充说明三档，规则没变），这三档的解释也写进了配置文件注释、配置界面提示（中英文）、规格和文档。对精妙背包、旅行者背包，`GENERIC` 目前与 `FULL` 表现相同——这正是你说的"没写专有代码时两者一样"。

### 2 与 3. 聊天提示

OFF 的界面上按面板热键，以及配置里的问题，都写进**玩家自己的聊天**（前缀 `[浮动背包]`，只有本人可见，按 T 可再查看）。配置问题包括：条目格式不对被忽略、档位无法识别按 FULL、列了 `minecraft`、同一模组重复、整个文件有语法或格式错误；一次最多 5 条，多的合并成一行；游戏未进入世界时的提示等进入世界后再显示。

### 4. 配置为什么被重置成空数组

读了 FML 的 `ConfigTracker.loadConfig`，并对照你这次的日志和 `run/config/` 下的备份文件：
- 日志里有两次 `Failed to load config mcfloatingbackpack-client.toml … Invalid newline in basic string … Attempting to recreate`（14:44 和 14:51）。
- 对应的两个备份 `mcfloatingbackpack-client-2.toml.bak`、`mcfloatingbackpack-client-1.toml.bak` 里，一个写着 `"mcfloatingbackpack`、另一个只有单独一个 `"`——是**游戏运行时编辑器自动保存下的写了一半的内容**（引号没写完）。
- NeoForge 的文件监视器马上重新读取，读不出来就把这份文件**备份成 `.bak`、删除、写回默认配置再加载**（`modLevels` 变回空数组）。

所以这是 NeoForge 的恢复行为，**不是本模组清空了设置**，你改的内容都在 `.bak` 里。我们无法关闭 NeoForge 的文件监视，所以做了两件事：发现刚生成的 `.bak` 时在聊天里提示；建议在游戏关闭时修改配置文件，或使用游戏里的配置界面。

## 2026-11-11 AI 自动验证（容器里的 runClient，开发环境带精妙背包、旅行者背包、JEI；配置用改文件的方式，游戏不重启）

环境见 `scripts/ai-gui/README.md`。为了让面板热键 B 不与其它两个模组撞键，把精妙背包的"打开背包"和旅行者背包的"打开"键解绑（`options.txt`）——**这一点本身是个发现**：三个模组的默认键都是 B，面板的宿主界面（019）会把 B 重放给别的模组，所以开发环境里按 B 会同时打开面板和对方的界面；这是 019 的设计，但玩家同时装这几个模组时会遇到，值得在 README 里提一句。

| quickstart | 结果 |
|---|---|
| 1 默认挂载 | **部分通过**：面板挂在精妙背包界面上、旅行者背包界面上；箱子、工作台没测；Ctrl 转移回归没测 |
| 2 日志里的模组 id | **通过（要记的事实）**：精妙背包界面写的是 `sophisticatedbackpacks`，旅行者背包界面写的是 `travelersbackpack`；各一行 INFO，开关同一界面不重复（`The floating panel is shown on a screen of mod '…'. If it conflicts with that mod, add "…=OFF" to compat.modLevels`）。精妙背包"存储"类界面是否是 `sophisticatedcore`：没遇到，**未验证** |
| 3 原版箱子日志里没有这类提示 | 没测 |
| 4 运行中加 `sophisticatedbackpacks=OFF`，不重启 | **通过**：面板开着时改文件，约 5 秒内面板从精妙背包界面上消失（没重启） |
| 5 OFF 后：面板隐藏、关闭界面后恢复 | **部分通过**：改回 `[]` 后面板约 4 秒内回到界面上，位置不变；Ctrl+点击不转移没测 |
| 6 OFF 界面上按热键出聊天提示 | **通过**：先按一次是关面板（面板此时算"已打开"），再按一次聊天出现 `[浮动背包] 面板在 sophisticatedbackpacks 界面上已在配置里关闭`，关界面后聊天里仍在（T 可再看）；原版物品栏上按热键照常打开面板 |
| 7 其它界面不受影响 | **部分通过**：OFF 时原版物品栏上面板照常；旅行者背包、箱子、熔炉、工作台没测 |
| 8 运行中改成 OFF：面板立即隐藏；手势取消、物品守恒 | **部分通过**：面板隐藏通过；手势中途取消没测 |
| GENERIC（二b） | 没测精妙背包的 GENERIC（只测了 jei） |
| 9 默认 JEI 避让 | **通过**：把面板位置设在 JEI 物品列表上方（`mcfloatingbackpack-panel.properties` 写 posX=240），JEI 的列表与搜索框都不画 |
| 10 `jei=GENERIC` 不重启，多久生效 | **通过**：改文件后约 1～2 秒（截图的时间分辨率）JEI 的搜索框就回来了，**不需要重新打开界面** |
| 11 `jei=OFF` 与 GENERIC 相同；改回 FULL 又避让；来回 5 次 | **通过**：OFF 与 GENERIC 都是"不避让"，改回 `[]`/`jei=FULL` 都又避让；OFF↔默认来回 5 次每次都正确，无需重启 |
| 12 坏配置不崩溃、日志 WARN 清楚 | **部分通过**：运行中改文件逐个写入，都没崩，WARN 与聊天提示都有且文字清楚：`sophisticatedbackpacks=HALF`（档位无法识别，按 FULL）、`Bad Id=OFF`、`=OFF`、`abc`、`bad`（格式不对，被忽略）、`minecraft=OFF`（原版不能配置，被忽略）、`jei=OFF`+`jei=GENERIC`（重复，取最后一条）；`jei=off` 小写按 OFF 处理、`notinstalledmod=OFF`（没装的模组）都没有提示（符合规格：不区分大小写；没装的模组不报）。"一条合法加一条非法，合法那条仍生效"：`sophisticatedbackpacks=OFF` 加 `bad` 时 `bad` 被提示，OFF 本身是否生效没单独看。**启动时**读到坏配置只测了"整个文件乱码"：游戏照常启动、进世界，WARN `The client config could not be used as saved; … mcfloatingbackpack-client-1.toml.bak … defaults were restored`，进世界后聊天里出现 `[浮动背包] 配置文件有语法或格式错误：已备份为 …bak 并恢复默认` |
| 12b 聊天提示 | **部分通过**：见上；"一次最多 5 条，多的合并"没测（一次只写一种）。**观察**：运行中存一份写了一半的文件时，这条"备份并恢复默认"的聊天提示出现了**两次**（相隔 0.5 秒，各来自一次重新读取：先读到坏文件，再读到恢复出的默认文件）——玩家会看到重复的一行，影响不大，要不要去重由你定 |
| 12c 游戏运行时存写了一半的文件 | **通过**：把 `modLevels = ["abc` 存进文件，聊天提示备份为 `mcfloatingbackpack-client-1.toml.bak`，配置回到默认，`.bak` 里能找到我写的 `modLevels = ["abc` |
| 13 配置界面（三档说明、列表增删改） | **部分通过**（英文）：模组列表 → 浮动背包 → Config → Client settings → Mod compatibility → "Compatibility level per mod…" → Edit… 里能用 `+` 新增条目（默认文字 `modid=FULL`）、点进文本框改成 `jei=GENERIC`（Ctrl+A 在这个框里无效，要先 End 再退格）；一路点 Done 退出，退出"Client settings"这一层后 `mcfloatingbackpack-client.toml` 里就是 `modLevels = ["jei=GENERIC"]`。悬停提示里有三档说明与"错误会在聊天里报告"。**没测**：条目删除（×）、中文界面、空列表时的三档说明（我测时已经有一条）。所以**字符串列表项在界面里能新增与修改**，限制写"Ctrl+A 无效"即可 |
| 14～15 JourneyMap、小地图 | **没测**（开发环境没装 JourneyMap） |
| 16～20 依赖规则、服务端门禁手测、协议版本、文档 | **没测**（16/17 之前已有自动化与负向验证记录；18 的 011～013 手测回归没做） |

**没勾任务**：T011（缺 1 的箱子/工作台与 Ctrl 转移、5 的 Ctrl+点击、7、8 的手势、GENERIC）、T014（9～11 已齐，**可以由你确认后勾**）、T017（缺 13 的删除与中文、启动时的各种坏配置）、T036（12b/12c 的主要点已过，缺"一次最多 5 条"与启动时各种坏写法的聊天提示）、T025、T028 没做。

### 2026-11-11 补充（第二轮 AI 自动验证）

| 项 | 结果 |
|---|---|
| 1/3 默认挂载与原版界面无提示 | **部分通过**：工作台界面上面板正常显示、挂在上面，日志里没有"shown on a screen of mod"提示（整个会话这条提示只出现在精妙背包与旅行者背包各一次）；箱子界面没打开成功（放置位置的问题），没测 |
| 5/7 Ctrl 转移回归 | **通过（工作台）**：面板挂在工作台界面上，Ctrl+点击物品栏里的 64 个圆石，面板里多出一格 64 个圆石、物品栏里那格变空；OFF 配置（精妙背包）下原版物品栏上面板照常（上一轮）。精妙背包界面的 Ctrl 转移、旅行者背包界面没测 |
| 12 启动时 10 条坏配置 | **通过**：一次写入 `a=HALF`、`Bad Id=OFF`、`=OFF`、`abc`、`minecraft=OFF`、`jei=OFF`、`jei=GENERIC`、`sophisticatedbackpacks=OFF`、`bad2`、`x y=FULL`，游戏启动、进世界没有问题 |
| 12b "一次最多 5 条，多的合并" | **通过**：进世界后聊天里出现 5 条（HALF 档位未知、`Bad Id=OFF`、`=OFF`、`abc`、`minecraft=OFF`），第 6 行是 `... and 3 more config problems, see the log` |
| 8 运行中改成 OFF 时手势取消、物品守恒 | **没测**（光标拖动手势需要鼠标按住，容器里的 xdotool 拖拽在本项目界面不灵） |
| 精妙背包 GENERIC 与 FULL 一致 | **没测**（尝试时没有成功打开精妙背包界面） |

**还没做的 020 项**：T011 的箱子、精妙背包界面里的 Ctrl 转移与手势取消、GENERIC；T017 的配置界面删除与中文；T025（011～013 回归）；T028（JourneyMap、小地图）。

## 客户端验证情况

**全部未验证**（宪法 V：编译与单元测试不能代替玩家可见行为的验证）。需要你在游戏里做的项，按 `quickstart.md`（先 `pgrep`，**不要有运行中的客户端**）：

- **T011（US1，最重要）**：一（1–3）、二（4–8）：默认挂载与 Ctrl 转移回归；**日志里读到的真实菜单命名空间**（精妙背包、旅行者背包，以及精妙背包"存储"类界面是否是 `sophisticatedcore`）；`sophisticatedbackpacks=OFF` 后面板隐藏/不转移/关闭后恢复；热键提示；原版容器与旅行者背包不受影响；运行中改成 OFF 时手势取消且物品守恒。
- **T014（US2）**：三（9–11）：JEI 三档，记录 GENERIC 多久后不再避让。
- **T017（US3）**：四（12–13）：10 种坏配置不崩溃；配置界面里三档说明（中英文）、列表项能否新增/修改/删除、保存后是否立即生效。
- **T025（US5）**：六（18）：011/012/013 的转入、取出、合并、光标手势手测与改动前一致。
- **T028**：五（14–15）、六（19–20）：JourneyMap 全屏地图（没装就记"未验证"）、小地图与面板的重叠、`latest.log` 无新增报错。

## 已知限制与未验证项

- 按菜单类型注册命名空间判断界面归属，在真实精妙背包/旅行者背包界面上的表现：**未验证**。
- `ModConfigSpec` 对"任意字符串"元素的接受与一个坏条目不影响其它条目：按结构设计，**未单独验证**。
- 配置界面里字符串列表项能否新增/编辑：**未验证**。
- JEI 切档的生效时机：2026-11-11 AI 自动验证：约 1～2 秒、无需重开界面（见上）。
- EMI/JourneyMap 小地图不适配。
- Windows/Linux：只能按原版行为推断，**未验证**。
