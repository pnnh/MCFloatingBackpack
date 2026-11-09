# Delivery: 统一的"背着的背包"查找

**日期**: 2026-11-09 | **状态**: 代码、自动化检查完成；T011 用户已验证功能没有问题；没有提交 Git。

## 这个 feature 是什么

一次重构，玩家可见行为与 031 之后完全一致：把"背着的背包"从"胸甲槽一处写死"改成**一个有固定顺序的来源列表**，背上模型的渲染与按 B / 面板的服务端操作 / 自动拾取都走同一条规则——**主手 → 副手 → 背着的第一只 → 物品栏 0～35**。目前只有胸甲一个来源；033（Curios）、034（Accessories）把各自的后背槽作为新来源加在胸甲后面。详见 [spec.md](./spec.md)、[plan.md](./plan.md)、[research.md](./research.md)。

## 做了什么

- `inventory/worn/WornSource`（来源接口）与 `BackpackSearch`（查找核心）：**纯逻辑，不引用 Minecraft 类**。来源按 `order` 排序（与线路上的 `id` 无关），`active()` 在每次查找时判断，`get` 越界返回空，`hiddenRender` 留给 033/034（胸甲恒为"不隐藏"）。
- `WornBackpacks`（单例 `BackpackSearch<LivingEntity, ItemStack>`）与 `ChestWornSource`：真实的玩家与物品栈一侧；`first(LivingEntity)` 给渲染层，`resolvePlayer/get/set` 给 `BackpackRef`。
- `BackpackRef`：`Source` 每个常量带 `worn` 标志（序号不变）；`resolve` 与 `getStack/setStack` 对 worn 来源走 `WornBackpacks`；**胸甲的引用下标从 `-1` 改成 `0`**（线路上唯一的取值变化，协议版本仍是 16）；修正了 `saveInventory` 里提到登记表的过时注释。
- `BackpackBackLayer`：画 `WornBackpacks.first` 的那一只；该槽被设为隐藏渲染时不画（不改画后面的）。
- `BackpackQuickTransferPayload.holderOf`：worn 来源一律映射到 `Holder.CHEST`（不在物品栏 0～35，没有要保护的下标）。
- **没有改**：服务端 11 处与客户端 3 处调用、自动拾取、`FloatingBackpackItem`、任何网络包与语言键。

## 自动化结果

| 项 | 结果 |
|---|---|
| `./gradlew compileJava` | 通过 |
| `./gradlew test` | 102 个类、729 个用例，0 失败（基线 101 个类、711 个用例；新增 `BackpackSearchTest` 15 个用例、`BackpackRefTest` 增补 3 个） |
| 静态检查（T009） | `EquipmentSlot.CHEST` 在 `main` 里只剩 `ChestWornSource` 与 `FloatingBackpackItem`（`Equipable`）；`BackpackRef.resolve` 的调用点没有变化；协议版本仍是 `"16"`；没有新增语言键 |
| 图集度量（T013） | 2048×1024，通过（本 feature 不新增贴图） |

实现中遇到的一件事：`BackpackRef` 的代码里一旦出现 `LivingEntity`，JVM 的字节码校验会为了检查类型兼容而加载它，纯单元测试类路径里没有 Minecraft 类，`BackpackRefTest` 就加载失败。所以 `WornBackpacks` 给 `BackpackRef` 的入口参数都用 `Player`（`resolvePlayer`、`get`、`set`），`LivingEntity` 只留在 `WornBackpacks` 与渲染层。

## 验证状态

| 任务 | 内容 | 状态 |
|---|---|---|
| T011 | quickstart A（8 个场景）与 B（面板各功能不回归） | 用户验证：功能没有问题（逐个场景的细节未逐条记录） |

## 已知限制

- **`modLevels` 在专用服务器上不生效**（plan 风险）：它是客户端配置，专用服务器读不到，按"生效"处理；以后"GENERIC 时槽位里的背包不算背着的"只对单人游戏与局域网房主成立。这是 033 要明确写出的限制。
- 本 feature 只有胸甲一个来源，所以顺序规则只在单元测试里用假来源验证过；真实的多来源行为要等 033/034。
