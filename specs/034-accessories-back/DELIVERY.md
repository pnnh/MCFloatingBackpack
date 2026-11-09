# Delivery: Accessories 后背槽兼容

**日期**: 2026-11-09 | **状态**: 代码、自动化检查完成；T012～T014 用户已验证，都没有问题。

## 这个 feature 是什么

允许把浮动背包放进 Accessories（Wisp Forest，1.21.1 的 beta 版）的 `back` 槽，放进去和穿在胸甲槽里一样算"背在身上"：背上画**我们的**模型，按 B 打开，面板所有功能照常。查找顺序（032 的来源列表）：主手 → 副手 → **胸甲 → Curios 后背槽 → Accessories 后背槽** → 物品栏，背着的只取第一只，不拦截。详见 [spec.md](./spec.md)、[plan.md](./plan.md)、[research.md](./research.md)。

## 做了什么

- **数据**：`data/accessories/tags/item/back.json`（`accessories:back` 标签，5 个档位，`replace: false`）。**没有**槽位分配文件：Accessories 默认就把 `back` 槽给了玩家。
- **来源**（`compat/accessories/AccessoriesWornSource`）：`id = Source.ACCESSORIES`（序号 5）、`order = 20`、`active()` 读 `modLevels` 的 `accessories`；`AccessoriesCapability.get(entity)`（可能为 `null`）→ `getContainers().get("back")`；只看真实栈；隐藏渲染 = `!shouldRender(slot)`；写回用 `getAccessories().setItem`（Accessories 每个 tick 自己发现变化并同步，不需要额外调用）；越界与任何异常都返回空且不抛。
- **渲染抑制**（`BackpackAccessoryRenderer` + `AccessoriesClientCompat`，仅客户端）：我们自己的"什么都不画"的渲染器，**不是** Accessories 的 `EmptyRenderer`（`forceNullRenderReplacement` 会把它换回默认渲染器）。`shouldRender` 与 `render` 每次读 `modLevels`：FULL 时不画（背上由 `BackpackBackLayer` 画），否则转给 `DefaultAccessoryRenderer.INSTANCE`。在 `FMLClientSetupEvent` 的 `enqueueWork` 里给 5 个档位各注册一个新实例（Accessories 只在资源重载时把注册表缓存起来，所以要早于首次重载；共用实例会被它警告并包一层）。
- `AccessoriesCompat.register()`（两端，主类只在 `ModList.isLoaded("accessories")` 时调用，签名里没有 Accessories 类型）。
- `BackpackRef.Source` 末尾追加 `ACCESSORIES(true)`（序号 5，已有序号不变）；协议版本仍是 16。
- **构建**：仓库 `maven.wispforest.io` 与 `compileOnly("io.wispforest:accessories-neoforge:1.1.0-beta.54+1.21.1") { transitive = false }`（**不带传递依赖，编译一次通过，不需要 owo-lib / endec 的类型**）；`neoforge.mods.toml` 加可选依赖 `accessories`；`DependencyRules` 的第三方坐标组加 `io.wispforest`。
- **没改**：任何网络包、语言键、贴图、配方、设置项；`FloatingBackpackItem`；032 的来源列表接口。

## 自动化结果

| 项 | 结果 |
|---|---|
| `./gradlew compileJava` | 通过 |
| `./gradlew test` | 104 个类、738 个用例，0 失败（基线 103 个类、734 个用例；新增 `AccessoriesBackResourcesTest` 3 个、`BackpackRefTest` 1 个，并更新了一个断言） |
| 静态检查（T010） | `io.wispforest` 只出现在 `compat/accessories/` 的三个文件；`ClientSetup` 是唯一引用 `AccessoriesClientCompat` 的地方（客户端代码）；协议仍是 `"16"`；没有新增语言键 |
| 启动：带全部模组（`FULL_MODS=1`） | Curios、Accessories、owo、精妙背包、旅行者背包、Sodium、Iris、JEI 与我们的模组一起加载，没有与我们有关的错误（日志里的堆栈是 Iris 对光影包的已知警告） |
| 启动：干净环境（没有 Accessories） | 只有 JEI；模组正常加载，**没有任何 `NoClassDefFoundError` 或与 Accessories 有关的错误**（"没装 Accessories 不加载任何 Accessories 类"的自动证据） |
| 图集度量（T016） | 2048×1024，通过（干净环境与带全部模组都是 2048×1024） |

## 验证状态

| 任务 | 内容 | 状态 |
|---|---|---|
| T012 | quickstart A、B：放入/取出/背着/**背上没有默认外观**/按 B/保存重进、顺序（含与 Curios 同时装） | 用户验证：没有问题 |
| T013 | quickstart C：隐藏渲染 | 用户验证：没有问题 |
| T014 | quickstart D：`modLevels` 的 GENERIC、没装 Accessories | 用户验证：没有问题（逐条细节未逐条记录） |

## 已知限制与要留意的点

- **要重点看的一点**：背上是不是**只有**我们的模型，没有一个 Accessories 默认外观叠在上面（渲染器注册时机与"不是 EmptyRenderer"的做法就是为它）；可选地打开 `forceNullRenderReplacement` 再看一次。
- **`modLevels` 在专用服务器上不生效**：客户端配置，服务器读不到、按 FULL 处理（用户已接受）。
- **写回的副作用**：背包内容每次变化，Accessories 会在下一个 tick 同步整只栈并触发回调，量级与原版装备槽相当。
- **Beta 版**：Accessories 1.1.0-beta.54 是 beta，以后版本可能改 API；我们只用 `AccessoriesCapability`、`AccessoriesContainer`、`AccessoryRenderer`、`AccessoriesRendererRegistry`、`DefaultAccessoryRenderer`、`SlotReference` 几个公开 API。
- **与 plan 的一处差别**：规格 FR-007 写"只在 FULL 时注册渲染器"，实现是"总是注册（Accessories 已加载时）、渲染器每帧读配置"，效果相同且支持配置热加载（见 research D5）。
- 不适配已停止维护的 Accessories Compatibility Layer（Accessories 自己把它 ≤0.1.9 声明为不兼容）。
