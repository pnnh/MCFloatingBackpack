# DELIVERY: 灯笼光源（052，接 LambDynamicLights）

状态：已实现，自动化测试与容器里的游戏验证完成；**等用户确认**（T011：走路时光的平滑程度、与 Iris 光影包的搭配、真机放置）。未提交、未打 tag。

## 发布说明要写的两条

1. **需要另外安装 LambDynamicLights（1.21.1 的 4.x 版本）**，否则放进下方区的灯笼不会发光；本模组自己不处理光照。
2. 下方区里已经放着灯笼的老背包，装了 LDL 的玩家更新后会开始发光。

## 基线与结果

- `./gradlew test`（实现前，052 之前）：**187 个类、1447 个用例，0 失败**。
- `./gradlew compileJava test`（实现后）：**191 个类、1476 个用例，0 失败**（新增 `ToolLightsTest`、`ToolLightRulesTest`、`LightResourcesTest`、`LightWiringTest`；`DependencyRulesTest` 通过）。
- 无新贴图、模型、物品：共享图集不受影响（未度量）。

## 经过：方案改了一次

1. **最初做的是服务端光源方块**（`minecraft:light` 跟着玩家走，含世界数据文件、清理残留、跟随器等），并在游戏里验证了放置、跟随、水里、冰雪、清理、崩溃重启后的清理。用户试玩后反馈："走路时忽明忽暗，一格一格跳；拿在手上时手臂摆动加剧晃动感；关掉光影包仍有"。我量到跳格间隔曾因每 3 tick 采样而 150/300 毫秒交替（已修），但方块光按整格移动、服务端异步算光与客户端预测的差异是固有的，治不了。
2. **用户决定采用和 Traveler's Backpack 一样的方案：要求玩家另外安装 LambDynamicLights，不装则不发光，本模组自己不处理。**我读了 Traveler's Backpack 的字节码（开发运行里就有它的 jar），照同样的形状接入。
3. **整个服务端方案连同测试一并删除**（放方块、世界数据文件、清理残留、跟随器、落点、事件接线）；亮度同时按用户要求改成与原版一致（灯笼等 15、末地烛 14、灵魂灯笼 10，原版火把是 14）。

## 改了什么（最终）

- `light/`：`ToolLights`（亮度表，与原版一致）、`ToolLightRules`（下方区至多 1 个光源）、`LightTags`、`PlayerLights`（读背包下方区、算亮度，只读）。
- `compat/lambdynlights/`：`BackpackDynamicLight`（`DynamicLightsInitializer`，入口由 `neoforge.mods.toml` 的 `yumi:entrypoints` 声明）、`BackpackEntityLuminance`（对 `EntityType.PLAYER`，受 `compat.modLevels` 里 `lambdynlights` 的开关控制）。
- 标签 `mcfloatingbackpack:tool_lights`（10 个原版物品）；语言键 `light.duplicate`、`light.needs_mod`；`BackpackClickPayload`（服务端）与 `FloatingPanelInput`（客户端预览与"需要 LDL"的提示）。
- `build.gradle`：仓库 maven.gegy.dev（限 `dev.lambdaurora.lambdynamiclights`）；`compileOnly` LDL API（mojmap 分类，`transitive = false`）与 Yumi 事件库；`localRuntime` LDL（只客户端的开发运行）。`neoforge.mods.toml`：LDL 是 `optional`、`CLIENT`、`[4,)`。
- 不改协议（19）、数据修订（3）；没有新网络包、配置项、物品、世界数据、mixin、服务端代码。

## 游戏内验证（容器 `mcfb-c1`）

| 项 | 做了什么 | 结果 |
|---|---|---|
| 带 LDL 启动 | 默认开发运行（Sodium + Iris + LDL 4.8.11） | 日志里 LDL（含 API、Runtime）与本模组都加载，没有来自本模组的错误 |
| 发光（B2） | 夜里（`time set 18000`）站在平地上：先无背包，再带灯笼背包 | **带灯笼背包时地面与雪块区明显变亮，清掉背包后变暗**（截图对比 `n0`/`n1`） |
| 不带 LDL 启动（B3） | `-PcleanRuntime -PnoJei` | 游戏正常，没有来自本模组的错误（日志里只有旧测试数据包引用 PNC 的错误，与本功能无关），带灯笼背包时世界不变亮 |
| 提醒（B5） | 不带 LDL 时把灯笼放回下方区（同格替换，被接受） | 聊天出现一次"A light only shines if you also install LambDynamicLights…" |
| 互斥（B4） | 此前（服务端方案阶段）验证过：第二个被拒并有提示、同格替换成功、放进左区放得下；这部分代码未变 | 通过 |

**没有验证**：走路时的平滑程度（容器软件渲染，看不出）；与 Iris 光影包（Complementary 等）的搭配；别的玩家看到的光（只验了自己）；多人联机。**平滑程度、光影包下的观感：待用户确认。**

## 已知局限

- 需要玩家另外安装 LambDynamicLights（LDL 的 1.21 线已是最后更新，EOL）；没装则不发光。
- 别的玩家只有在背包**背着**（胸甲位、Curios、Accessories）时才被照亮；拿在手里或放在物品栏里的背包，别的客户端读不到（与 Traveler's Backpack 一样）。
- 没有颜色（LDL 只有 0～15 的亮度）。
- 光影包的手持光只认主手物品，不会认我们的背包；LDL 的光是烤进网格顶点光照里的，光影包读到的就是这个值，具体观感以真机为准。
- 与别的动态光源模组（RyoamicLights、Sodium Dynamic Lights）互不兼容，是它们自己的问题。

## 数据修订与协议号

不变（3 与 19）：没有新的存储数据、物品或网络包。

## 测试世界的残留

容器里的测试世界 `New World` 多了数据包 `c52`、`c51` 和一些平台方块，以及 052 旧方案留下的世界数据文件 `data/mcfloatingbackpack_lights.dat`（现在没有代码读它，无害）；备份在容器内 `/tmp/New World.bak052`、`/tmp/New World.bak051`。仓库里没有调试入口。

## 宪法复查

I、II（互斥规则在服务端；光不是服务端状态）、III、IV（对第三方模组的主动适配放 `compat/`，不接就不发光是用户明确的取舍）、V（容器里验证并记录了没验证的）、VI（无新贴图）、VII（LDL 可选、`compileOnly`/`localRuntime`、类型只在 compat 包、`compat.modLevels` 可关、`DependencyRulesTest` 通过）。无违规项。
