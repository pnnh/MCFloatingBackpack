# DELIVERY: 移动熔炉（055）

状态：已实现，游戏内验证已做（见下表），用户已确认手感（T024）。已提交，未打 tag。

## 基线与结果

- `./gradlew test`（实现前）：**201 个类、1552 个用例，0 失败**。
- `./gradlew compileJava test`（实现后）：**205 个类、1583 个用例，0 失败**（新增 `FurnaceEngineTest`、`FurnaceExperienceTest`、`MobileFurnaceResourcesTest`、`MobileFurnaceWiringTest`；`MobileToolTest` 加熔炉字节；写死数据修订 5 的 7 处断言改为 6：`DataRevisionTest`、`MobileCraftingWiringTest`、`JukeboxWiringTest`、`LightWiringTest`、`TankWiringTest`、`PipeWiringTest`（两处））。`DependencyRulesTest` 通过。
- 共享方块图集（`check_atlas_budget.sh`）：**之前 2048x1024，之后 2048x1024**，通过预算（新增 1 张 16×16）。

## 改了什么

- 物品 `mobile_furnace`（`MobileFurnaceItem`，堆叠 1，右区才生效）、模型、手绘贴图（`make_mobile_tool_icons.py`）、配方 `IRI/CFC/ILI`（岩浆桶退回空桶，原版的剩余物）、中英文语言键、`never_trash`、创造栏（移动工作台之后）。
- **纯逻辑**（`tool/furnace/`，无 Minecraft 类型）：`FurnaceEngine`（`tickOnce` 是 1.21.1 `serverTick` 的逐刻移植，去掉 `setBlock`；`advance` 在"只有计数在走"的一段里一次跳过，事件刻仍逐刻处理，开销与经过的刻数无关）、`FurnaceRules`、`FurnaceCore`、`FurnaceExperience`。
- **MC 层**：`FurnaceState`（工具物品上的数据组件 `mobile_furnace_state`）、`McFurnaceRules`（`RecipeType.SMELTING` + `getBurnTime`）、`FurnaceStateContainer`（三格容器，换了原料就重置进度，同原版）、`MobileFurnaceSession`（一次打开：补算到现在、每次看菜单推进到现在、变化时写回、取走产物发经验）、`MobileFurnaceMenu`（`FurnaceMenu` 子类，有效性 = 背包身份仍是打开时的那只 + 右区仍有移动熔炉；产物槽换成发经验的子类）、`MobileFurnaceStore`（读右区、经 `BackpackGate` 写回、受保护背包拒绝并提示）。
- `MobileTool.FURNACE = 1`；`MobileTools` 加分支；`FloatingPanelInput` 点击处与两处右区限制各加一条。
- **没有新的网络包，协议号保持 20**；**数据修订 5 → 6**（新的只能存在背包里的物品种类，工具物品上有新的数据组件）。没有新配置项、依赖、mixin，没有反射与访问转换器（`MobileFurnaceWiringTest` 断言）。

## 与 spec / plan 的偏离与更正

- **写回策略比 spec 原来的更强**：spec 草稿写"进度最多落后 1 秒"。plan 查证后改为**只在玩家动格子、打开、关闭时写回，不定期写**：进度可由"写回的状态 + 时间戳"用同一个引擎推出，所以崩溃或下线后没有进度落后，且不会每秒改动背包物品。spec 的 FR-012、US3#8、崩溃一条已改。
- **更正 spec 的一个错误假设**：睡觉与 `/time` 只改一天里的时刻、不改游戏时间（`getGameTime`），熔炉不会因此多烧；spec 的 Assumptions 已改。
- 1.21.1 没有 `FuelValues`，燃料用 `ItemStack.getBurnTime(RecipeType.SMELTING)`（含燃料数据映射与 NeoForge 的事件）。
- 原版产物槽只对方块实体发经验，所以把 `slots[2]` 换成自己的 `FurnaceResultSlot` 子类。
- 容器最大堆叠按原版的 99（`Container.getMaxStackSize()` 默认），而不是 64。

## 游戏内验证（容器 `mcfb-c1`）

测试数据：数据包 `c54` 里新增函数 `f1`（造一只右区放着移动熔炉与垃圾桶的铁背包，并给铁矿、煤、沙子、木棍）、`f2`（再给一个移动熔炉）；测试世界备份在容器内 `/tmp/New World.bak055`。

| 项 | 做了什么 | 结果 |
|---|---|---|
| 打开 | 物品栏里按 B，左键点右区的移动熔炉 | 原版熔炉界面打开（标题 "Furnace"，配方书、原料 / 燃料 / 产物格），浮动面板仍叠在上面 |
| 烧制 | shift 点击放入 16 铁矿 + 煤 | 火苗亮、箭头走；约 25 秒出 2 个铁锭（约 10 秒一个，与原版一致） |
| 经验与解锁 | 取走产物 | 玩家经验总量 2（3 个铁锭 × 0.7 按概率进位）；出现配方解锁提示 |
| 再点关闭 | 再点一次工具 | 界面关闭，面板接回 |
| 状态在物品上 | `data get` 背包右区的工具 | 有 `mobile_furnace_state`：输入 13、燃料 7 个煤、`lit_time` 812、`cook_progress` 189、`stamp` |
| **补算** | 关着时 `/tick sprint 2000`（连同等待共过了 3224 刻），再打开 | 产物 13 个、原料 0、燃料 6 个煤、熄灭、进度 0；**与我用同一份规则在 Python 里逐刻模拟的结果（0, 6, 13, 0, 0）完全一致** |
| 工具离开 | 界面开着，右键把工具从右区拿起 | 界面立刻关闭；工具回到物品栏，上面带着 8 沙子 + 6 煤的状态 |
| 状态随物品走 | 把它放回右区再打开 | 8 沙子已烧成 8 玻璃（离开期间也在"烧"，按补算），煤剩 5 |
| 退出再进入 | Save and Quit，再进世界打开 | 燃料（5 煤）仍在；转储玩家数据：背包 `data_revision` = **6**，工具与状态都在 |
| 右区只能一个 | 光标上第二个移动熔炉点右区空格 | 被拒，聊天"The right area already has a mobile furnace"，物品留在光标上 |
| 左区无作用 | 放进左区再点它 | 只是拿起，没有界面、没有请求 |
| 日志 | 搜 ERROR / Exception | 没有来自本模组的错误（只有容器环境的 OpenAL、GL 噪声） |
| 图集 | `check_atlas_budget.sh` 前后各一次 | 都是 2048x1024 |

**没有验证**：手感与界面衔接（容器软件渲染，**用户已确认**）；受保护的背包（更新版本保存的）点熔炉时的提示（只有源码文本检查）；换到另一只也带熔炉的背包时界面关闭、不串状态（只有源码文本检查）；服务端对"第二个移动熔炉"的校验（只在客户端预览被拦住后看到提示）；JEI / EMI 的配方转移与配方书的"放置配方"（容器 `-PnoJei`，配方书按钮可见但没点）；燃料中途烧完、产物放满、岩浆桶剩余物、湿海绵这些边界只在单元测试（含 6000 个随机炉子的"快进 = 逐刻"对照）里验证，没有在游戏里逐个试；多人联机；创造模式取走产物是否发经验；100 万刻的卡顿只在单元测试里量了（10 亿刻、64 个原料，远小于 500 毫秒）。

## 已知局限

- 以按 B 的那只背包为准；右区取"第一个"移动熔炉（右区有"各一个"的限制，只有命令之类绕过手段才会有多个）。
- 界面标题用原版的"熔炉"，不显示"移动熔炉"；工具图标上不画火苗与进度。
- 极端情况：某个模组的燃料"用完后剩余物还是自己"（永远烧不完），补算的迭代次数与 经过时间 / 燃烧时间 成正比；正常内容不受影响。
- 玩家下线时 `PlayerList.remove` 不会先关菜单，所以最后一次写回是"最近一次动格子"时的状态；之后的进度由补算得出（与没有中断一致）。

## 数据修订与协议号

- 数据修订 **5 → 6**：新的只能存在背包里的物品种类 + 工具物品上的新数据组件；旧版本读到会丢（040/041 的降级保护生效，发布说明要提醒"降级前先备份存档"）。
- 协议号 **20 不变**：复用 054 的 `open_tool` 包（`MobileTool.FURNACE = 1`），没有新增包。

## 测试世界的残留

容器里的测试世界 `New World` 的数据包 `c54` 多了 `f1`、`f2`；世界里背包与工具有测试用的状态；改动前的世界备份在容器内 `/tmp/New World.bak055`。仓库里没有调试入口（`grep -rn TMP055 src` 为空）。

## 宪法复查

I（原版熔炉界面，面板仍是叠在上面的浮层）、II（打开、校验、烧制、写回、经验都在服务端；请求只带一个字节）、III（一个 feature）、IV（原版菜单、原版配方与燃料表）、V（容器里验证并记录了没验证的）、VI（图集前后都 2048x1024）、VII（无新依赖，不用反射、访问转换器与 mixin）。无违规项。
