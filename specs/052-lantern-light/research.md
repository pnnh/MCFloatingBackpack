# Research: 灯笼光源（接 LambDynamicLights）

## R1 为什么不再用服务端光源方块（已做过、已删除）

最初的方案是服务端把 `minecraft:light` 放在玩家身边、随玩家移动。做完并在游戏里验证了放置、跟随、水、冰雪、清理、崩溃重启后的清理，但用户试玩后反馈：走路时"忽明忽暗"、"一格一格跳"，拿在手上时手臂摆动加剧晃动感；关掉光影包后仍有。原因（我在容器里量过、也读了源码）：

- 方块光只能按整格移动，每跳一格周围亮度整体变一级；
- 服务端光照引擎异步，客户端自己也会预测光，两者的差异造成抖动；
- 玩家手臂与手持物的亮度取自玩家所在格的光照，跟着跳；
- 我修过一次"每 3 tick 看一次位置导致跳格间隔 150/300 毫秒交替"的问题，只治了一部分。

用户决定改用 LambDynamicLights 的方案。**整套服务端代码（放方块、世界数据文件、清理残留、跟随器、落点）连同测试一并删除**；留下的只有"哪些算光源、多亮、怎么互斥"。

## R2 LambDynamicLights 的接口（已核对 4.8.11+1.21.1）

- 客户端模组，NeoForge 1.21.1 支持；1.21 线的最后一个版本（End-of-Life），所以不追新。
- API 工件：`dev.lambdaurora.lambdynamiclights:lambdynamiclights-api:4.8.11+1.21.1`（maven.gegy.dev）。**普通 jar 里的名字是 intermediary（`class_1297`），NeoForge 要用 `:mojmap` 分类的 jar**。它的 POM 依赖 `dev.yumi.commons:yumi-commons-event`（Maven Central）与 jspecify；事件库的 POM 带来比游戏更新的 slf4j（版本冲突），所以事件库、核心库、集合库都 `transitive = false` 单独列出。
- `DynamicLightsInitializer`：`onInitializeDynamicLights(DynamicLightsContext)`（默认方法）与 `onInitializeDynamicLights(ItemLightSourceManager)`（抽象，已标记弃用待删，必须实现，空方法即可）。
- `context.entityLightSourceManager().onRegisterEvent().register(registerContext -> registerContext.register(EntityType.PLAYER, new EntityLuminance[]{…}))`。
- `EntityLuminance`：`type()` 与 `getLuminance(ItemLightSourceManager, Entity)`；`EntityLuminance.Type.registerSimple(ResourceLocation, EntityLuminance)` 注册类型。
- 入口声明（NeoForge）：`neoforge.mods.toml` 里 `[modproperties."${mod_id}"."yumi:entrypoints"]`，键 `"lambdynlights:initializer"`，值是入口类全名。

## R3 Traveler's Backpack 就是这个做法（读它的字节码，本仓库开发运行里有它的 jar）

`com.tiviacz.travelersbackpack.compat.lambdynamiclights.BackpackDynamicLight`（入口）与 `BackpackEntityLuminance`（对玩家：取穿着的背包，若背包有灯笼升级返回 15，否则 0），入口在它的 `neoforge.mods.toml` 里同样用 `yumi:entrypoints` 声明。本 feature 的形状完全相同，只是"有灯笼"换成"下方区有光源，亮度按表"。

## R4 LDL 与 Sodium、Iris

- LDL 改原版 `LevelRenderer.getLightColor` 与 `EntityRenderer.getBlockLightLevel`；读 Sodium 0.6.13 的 `LightDataAccess` 字节码，它也调用同一个 `LevelRenderer.getLightColor`，所以 LDL 在 Sodium 下可用（LDL 自己对 Sodium 只有设置界面的 mixin）。
- 容器里（Sodium + Iris，没有光影包）带 LDL 运行：夜里带灯笼背包画面明显变亮（截图对比），清掉背包后变暗。
- 光是烤进网格顶点的光照坐标里的，光影包读到的就是这个值；与 Complementary 等的搭配以真机为准（容器里跑不了光影包）。

## R5 读背包的两个来源

- **自己**：`BackpackRef.resolve(player)` 在客户端可用（手 → 背 → 物品栏），读栈上的 `BACKPACK_CONTENTS`（网络编码里含工具区）。
- **别人**：客户端只知道别人"穿着"的东西（胸甲位、Curios、Accessories 的背包），物品栏与手里的背包读不到；所以别人的光只在背包背着时才亮（与 Traveler's Backpack 同样的限制）。

## R6 可选依赖与宪法 VII

- `neoforge.mods.toml`：`modId="lambdynlights"`、`type="optional"`、`side="CLIENT"`、`versionRange="[4,)"`；
- `build.gradle`：API 只在 `compileOnly`，LDL 本体只在 `localRuntime`（与 Sodium、Iris 一起，只客户端的开发运行带它）；仓库 maven.gegy.dev 限定 `dev.lambdaurora.lambdynamiclights` 组；
- 第三方类型只出现在 `compat/lambdynlights/`；主代码不引用这两个类，所以没装 LDL 时不会加载它们；
- `compat.modLevels` 里 `lambdynlights=GENERIC/OFF` 让 `EntityLuminance` 返回 0；
- 容器里 `-PcleanRuntime`（没有 LDL）运行：游戏正常，没有来自本模组的错误，世界不变亮。

## R7 没装 LDL 的提醒

客户端、`FloatingPanelInput` 里：光源被放进下方区（互斥检查通过之后）、`ModList.get().isLoaded("lambdynlights")` 为假、本会话还没提示过 → 聊天一次 `message.mcfloatingbackpack.light.needs_mod`。已在容器里（没有 LDL）验证出现。

## R8 亮度

与原版一致（`Blocks.java` 1.21.1）：灯笼、荧石、海晶灯、菌光体、南瓜灯、三种蛙明灯 15；末地烛 14；灵魂灯笼 10；（火把 14、灵魂火把 10）。不再有"融冰融雪"的顾虑：光不改世界。LDL 自己的亮度计算（衰减、水敏感等）归 LDL 管。
