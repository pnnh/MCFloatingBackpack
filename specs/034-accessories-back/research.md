# Research: Accessories 后背槽兼容

读 Accessories 1.1.0-beta.54+1.21.1 的 jar 与 `-sources.jar`（2026-11-09 下载到临时目录）与本仓库源码得出的结论。每条：**决定 / 理由 / 备选**。

## D1 编译依赖

**决定**：`build.gradle` 加仓库 `https://maven.wispforest.io/releases`（`content { includeGroup "io.wispforest" }`），`compileOnly("io.wispforest:accessories-neoforge:1.1.0-beta.54+1.21.1") { transitive = false }`。开发运行时继续用已有的 `localRuntime`（Modrinth 的 Accessories 与 owo-lib）。**已确认**这个 jar 与 POM 在该仓库上存在（HTTP 200）。

**理由**：POM 里 `owo-lib-neoforge`、`forgified-fabric-api-base`（compile 范围）、`endec` 等（runtime 范围）会被传递进来，我们既不需要它们参与编译，也不想为它们再加仓库；`transitive = false` 只取 Accessories 自己的 API。如果 javac 抱怨缺少 owo / endec 的类型（API 的某个签名引用了它们），再按需单独 `compileOnly` 那几个坐标——这是实现时验证的点。

**要同步改的规则**：`DependencyRules.THIRD_PARTY_GROUPS` 加 `io.wispforest`；`neoforge.mods.toml` 加一个**可选**依赖 `accessories`（`versionRange="[1,)"`，`ordering="NONE"`）。

## D2 类加载安全

与 033 相同：`MCFloatingBackpack` 构造函数里 `if (ModList.get().isLoaded("accessories")) AccessoriesCompat.register();`；`AccessoriesCompat.register()` 的签名里没有 Accessories 类型。Accessories 类型只出现在 `compat/accessories/` 的文件里（`DependencyRulesTest` 自动检查）。客户端渲染器的注册放在一个**单独的、只在客户端加载的类** `AccessoriesClientCompat` 里（`@OnlyIn(Dist.CLIENT)`），由 `ClientSetup` 在 Accessories 已加载时调用，这样专用服务器永远不会加载它。

## D3 来源的读写（对照 beta.54 源码）

| 事 | 做法 |
|---|---|
| 取容器 | `AccessoriesCapability.get(LivingEntity)`（**可能为 `null`**）→ `getContainers().get("back")`（`Map<String, AccessoriesContainer>`，没有这个槽就是 `null`）。Accessories 自己的渲染层在**客户端**对任意实体这样读 |
| 格子数 | `container.getAccessories().getContainerSize()` |
| 读真实栈 | `container.getAccessories().getItem(slot)`（`getCosmeticAccessories()` 是装饰栈，**不看**） |
| 隐藏渲染 | `!container.shouldRender(slot)`（`shouldRender` 对超出列表的下标返回 `true`） |
| 写回 | `container.getAccessories().setItem(slot, stack)`（`ExpandedSimpleContainer.setItem`：越界直接返回，不抛；它取消旧的变化订阅并为新栈重订，然后 `SimpleContainer.setItem`） |
| 越界/异常 | 下标不在 `[0, size)` → 空栈；任何 `RuntimeException` → 空栈且不抛 |

## D4 写回之后怎么保存与同步（已读源码，不需要额外调用）

`AccessoriesEventHandler.onLivingEntityTick` 每个 tick 对每个格子：`if (!ItemStack.matches(currentStack, lastStack))`（仅服务端、实体没死）→ `setPreviousItem(i, current.copy())`、放进 `dirtyStacks`（随后同步给客户端）、重算属性修饰、触发 `AccessoryChangeCallback`。所以服务端 `setItem` 之后，**下一个 tick Accessories 自己发现变化并同步**，保存靠 Accessories 的玩家数据。**不需要显式的"已变化"调用**。

副作用与 033 相同：背包内容每次变化都会让 Accessories 同步整只栈并发一次变化回调；量级与原版装备槽相当。每次 `setItem` 还会为非空栈重建一个"栈变化订阅"，量小。

## D5 渲染抑制：我们自己的渲染器，不是 `EmptyRenderer`

- `AccessoriesRenderLayer` 对每个非空栈取 `AccessoriesRendererRegistry.getRenderer(stack)`，然后 `if (renderer.isEmpty() || !renderer.shouldRender(container.shouldRender(i))) continue;`。
- `AccessoryRenderer.isEmpty()` 是 `@NonExtendable` 的 default：**`this instanceof EmptyRenderer`**；`getRenderer(Item)` 在 `forceNullRenderReplacement` 打开时会把 `EmptyRenderer` 换成默认渲染器。所以我们的渲染器**不能是 `EmptyRenderer`**，要自己实现 `AccessoryRenderer`。
- **做法**：`BackpackAccessoryRenderer implements AccessoryRenderer`：
  - `shouldRender(boolean isRendering)`：`modLevels` 里 `accessories` 是 FULL 时返回 `false`（渲染层直接跳过；背上由我们自己的 `BackpackBackLayer` 画）；不是 FULL 时返回 `isRendering`；
  - `render(...)`：FULL 时什么都不做；不是 FULL 时转给 `DefaultAccessoryRenderer.INSTANCE.render(...)`（`public static final`，已确认）——这样 GENERIC / OFF 时背包在 Accessories 槽里就是"和别的物品一样"，保持规格的含义（我们的代码不介入）。
  - 因为渲染器**注册一次、每帧读配置**，改了 `modLevels` 立刻生效，不需要重新注册。
- **注册**：`AccessoriesRendererRegistry.registerRenderer(item, () -> new BackpackAccessoryRenderer())`，**每个档位一个新实例**（`onReload` 发现同一个实例被两个物品共用会打警告并包一层）。`onReload()`（资源重载时把 `RENDERERS` 缓存进 `CACHED_RENDERERS`）在客户端资源重载时执行，所以必须在**第一次资源重载之前**注册：放在 `FMLClientSetupEvent` 的 `enqueueWork` 里（模组加载期间，早于首次资源加载）。
- 订正规格里的一句话：规格 FR-007 写"只在 Accessories 为 FULL 时生效"，实现是"总是注册、由渲染器每帧读 `modLevels` 决定做不做事"，效果相同且支持配置热加载。

## D6 数据文件

- **标签** `data/accessories/tags/item/back.json`：`{"replace": false, "values": [五个档位]}`；标签键是 `accessories:back`（`AccessoriesAPI` 用 `TagKey.create(Registries.ITEM, accessories:<槽名>)`，已读源码确认），物品标签目录是 1.21 通用的 `tags/item/`。
- **不需要槽位分配**：`accessories:entity/default.json` 的目标是 `#accessories:defaulted_targets`（含 `minecraft:player`），列出了 `back`。
- `back` 槽的验证器是 `accessories:tag` 与 `accessories:component`：物品在 `accessories:back` 标签里就放得进去。

## D7 来源接口的实现要点

`AccessoriesWornSource implements WornSource<LivingEntity, ItemStack>`：`id() = BackpackRef.Source.ACCESSORIES.ordinal()`（在 `Source` 末尾追加 `ACCESSORIES(true)`，序号 5）；`order() = 20`；`active() = CompatConfig.policy().specificAdaptation("accessories")`；其余如 D3。`BackpackQuickTransferPayload.holderOf` 对 worn 来源一律返回 `Holder.CHEST`（032），对 Accessories 格子同样成立。

## D8 与 Curios、兼容层同时存在

- 顺序是 `order`：胸甲 0、Curios 10、Accessories 20，与注册先后无关。两个来源互不依赖。
- Accessories Compatibility Layer：Accessories 的 `mods.toml` 把它 ≤0.1.9 声明为不兼容；更新的版本可能把 Curios 物品镜像进 Accessories。因为只取第一只，镜像不影响我们；不适配，不验证。

## D9 测试

- **单元测试**：`AccessoriesBackResourcesTest`（标签文件存在、`replace=false`、5 个档位 id 与 `BackpackTier.id()` 一致且无多余项、路径正确；**没有**槽位分配文件）；`BackpackRefTest` 增补（`ACCESSORIES` 序号 5 且 `worn`，已有序号不变，`values().length == 6`）；`DependencyRulesTest`（已有）覆盖"Accessories 类型只在 `compat/accessories/`""构建里只 `compileOnly`/`localRuntime`""mods.toml 依赖都是可选"，常量更新后自动覆盖。
- **游戏内验证**：Accessories 界面里后背槽；放进去/取出；背上只有我们的模型（没有默认外观）；按 B；顺序（与胸甲、Curios）；隐藏渲染；GENERIC；没装 Accessories（`-PcleanRuntime`）；保存重进；`forceNullRenderReplacement` 打开时仍然正常。
