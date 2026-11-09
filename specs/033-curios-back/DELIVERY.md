# Delivery: Curios 后背槽兼容

**日期**: 2026-11-09 | **状态**: 代码、自动化检查完成；T011～T013 用户已验证功能没问题。

## 这个 feature 是什么

允许把浮动背包放进官方 Curios（9.x）的 `back` 槽，放进去和穿在胸甲槽里一样算"背在身上"：背上画模型，按 B 打开，面板所有功能照常。查找顺序（032 的来源列表）：主手 → 副手 → **胸甲 → Curios 后背槽** → 物品栏，背着的只取第一只，不拦截第二只。详见 [spec.md](./spec.md)、[plan.md](./plan.md)、[research.md](./research.md)。

## 做了什么

- **数据**：`data/curios/tags/item/back.json`（`curios:back` 标签，5 个档位，`replace: false`）；`data/mcfloatingbackpack/curios/entities/player_back.json`（无条件给玩家一个 `back` 槽，官方 Curios 自己不给任何实体分配槽位）。
- **代码**（只在 `compat/curios/`）：`CuriosWornSource`（032 的 `WornSource`：`id = Source.CURIOS`、`order = 10`、`active()` 读 `modLevels` 的 `curios`；读真实栈、未激活的格子当空、隐藏渲染照抄 Curios 渲染层的判断、写回用 `setStackInSlot`，越界与任何异常都返回空且不抛）与 `CuriosCompat.register()`（主类只在 `ModList.isLoaded("curios")` 时调用，签名里没有 Curios 类型）。
- `BackpackRef.Source` 末尾追加 `CURIOS(true)`（序号 4，已有序号不变）；协议版本仍是 16。
- **构建**：仓库 `maven.theillusivec4.top` 与 `compileOnly "top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1:api"`（解析成功）；`neoforge.mods.toml` 加可选依赖 `curios`；`DependencyRules` 的第三方坐标组加 `top.theillusivec4.curios`。
- **没改**：任何网络包、语言键、贴图、配方、设置项；`FloatingBackpackItem`；032 的来源列表接口。

## 自动化结果

| 项 | 结果 |
|---|---|
| `./gradlew compileJava` | 通过 |
| `./gradlew test` | 103 个类、734 个用例，0 失败（基线 102 个类、729 个用例；新增 `CuriosBackResourcesTest` 4 个、`BackpackRefTest` 1 个） |
| 静态检查（T009） | `top.theillusivec4` 只出现在 `compat/curios/CuriosWornSource.java`；`DependencyRulesTest` 通过；协议仍是 `"16"`；没有新增语言键 |
| 启动：带全部模组（`FULL_MODS=1`） | Curios 9.5.1、Accessories、owo、精妙背包、旅行者背包、**Sodium、Iris**、JEI 与我们的模组一起加载，没有与我们有关的错误；Curios 的 mixin 对其它模组类的警告是 Curios 自己的、无害（这也确认了合并后的 `runClient` 里 Sodium 与 Iris 确实被加载） |
| 启动：干净环境（没有 Curios） | 只有 JEI；模组正常加载，**没有任何 `NoClassDefFoundError` 或与 Curios 有关的错误**（"没装 Curios 不加载任何 Curios 类"的自动证据） |
| 图集度量（T015） | 2048×1024，通过（干净环境与带全部模组都是 2048×1024） |

## 验证状态

| 任务 | 内容 | 状态 |
|---|---|---|
| T011 | quickstart A、B：放入/取出/背着/按 B/面板功能/保存重进、顺序 | 用户验证：功能没问题 |
| T012 | quickstart C：隐藏渲染 | 用户验证：功能没问题 |
| T013 | quickstart D：`modLevels` 的 GENERIC、没装 Curios | 用户验证：功能没问题（逐条细节未逐条记录） |

## 已知限制与要留意的点

- **`modLevels` 在专用服务器上不生效**：它是客户端配置，服务器读不到、按 FULL 处理；只对单人游戏与局域网房主成立（用户已接受）。
- **写回的副作用**：背包内容每次变化（面板每次点击）Curios 会在下一个 tick 同步整只栈并发一次 `CurioChangeEvent`，量级与原版装备槽相当；验证时请留意有没有卡顿或刷屏日志。
- 没配置过后背槽的整合包，玩家会多出一个 `back` 槽；整合包已经配置过的，Curios 只保留一个，不叠加（用户已接受）。
- Adorned 声称保持同样的 API，但没有单独验证。
