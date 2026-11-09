# Research: Curios 后背槽兼容

读 Curios 9.5.1 的 jar、`-sources.jar`（2026-11-09 下载到临时目录）与本仓库源码得出的结论。每条：**决定 / 理由 / 备选**。

## D1 编译依赖：Curios 的 `-api` jar，`compileOnly`

**决定**：`build.gradle` 加仓库 `https://maven.theillusivec4.top/`（`content { includeGroup "top.theillusivec4.curios" }`）与 `compileOnly "top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1:api"`。开发运行时继续用已有的 `localRuntime "maven.modrinth:curios:9.5.1+1.21.1"`（完整 jar）。**已确认**这个 `-api.jar` 在该仓库上存在（HTTP 200）。

**理由**：Curios 官方 README 就是这样写的；`:api` 只含 API 类，编译时不会误用内部类。

**要同步改的规则**：`DependencyRules.THIRD_PARTY_GROUPS` 加 `top.theillusivec4.curios`（构建检查要求第三方坐标只能是 `compileOnly` / `localRuntime`）；`neoforge.mods.toml` 里加一个**可选**依赖 `curios`（`type="optional"`、`versionRange="[9,)"`、`ordering="NONE"`），`checkModsToml` 要求除 neoforge、minecraft 之外都是可选。

**备选**：用 Modrinth 的完整 jar 当 `compileOnly`——否决，会让内部类也可见。

## D2 类加载安全：只有 `compat/curios/` 里的类出现 Curios 类型

- `MCFloatingBackpack` 构造函数里 `if (ModList.get().isLoaded("curios")) CuriosCompat.register();`。`CuriosCompat.register()` 的签名里没有 Curios 类型，所以主类不会因为它去加载 Curios。
- `CuriosCompat`、`CuriosWornSource` 都放在 `compat/curios/`，是仅有的引用 `top.theillusivec4.*` 的文件；这由已有的 `DependencyRulesTest.theRealSourcesTouchThirdPartyTypesOnlyUnderCompatModId` 自动检查（只要 `compat/<modid>/` 目录名下的文件才允许第三方 import）。
- `CuriosWornSource` 的方法签名只用我们自己的类型（`LivingEntity`、`ItemStack`）；Curios 类型只出现在方法体里，Curios 没装时这个类根本不会被加载。

**备选**：`@Mod` 的可选子类——否决，沿用 JEI 插件的先例（`compat/jei/`）。

## D3 Curios 来源的读写（对照 9.5.1 源码）

| 事 | 做法 |
|---|---|
| 取处理器 | `CuriosApi.getCuriosInventory(LivingEntity)` → `Optional<ICuriosItemHandler>` → `getStacksHandler("back")` → `Optional<ICurioStacksHandler>`。Curios 自己的 `CuriosLayer` 在**客户端**对任意实体这样读，所以别的玩家也可用 |
| 格子数 | `handler.getSlots()`（= `getStacks().getSlots()`） |
| 读真实栈 | `handler.getStacks().getStackInSlot(slot)`（`getCosmeticStacks()` 是装饰栈，**不看**） |
| 未激活的格子 | `handler.getActiveStates()`：`size > slot && !get(slot)` 则当作空。（Curios 的 `findCurios` 用的同一个判断：`activeStates.size() > i && !activeStates.get(i)` 才跳过）|
| 隐藏渲染 | Curios 渲染层的写法是 `renderable = renders.size() > i && renders.get(i)`；我们照抄：`hidden = !(renders.size() > slot && renders.get(slot))` |
| 写回 | `handler.getStacks().setStackInSlot(slot, stack)`（`DynamicStackHandler extends ItemStackHandler`，`setStackInSlot` 不做校验，只触发 `onContentsChanged`） |
| 越界/异常 | 格子下标不在 `[0, slots)` → 空栈；任何 `RuntimeException` → 空栈（并且不抛） |

## D4 写回之后 Curios 怎么保存与同步（已读源码，不需要额外调用）

`CuriosEventHandler.tick`（`EntityTickEvent.Post`）每个 tick 对每个格子：`if (!ItemStack.matches(stack, previousStack))` → `syncCurios(...)`（发 `SPacketSyncStack` 给跟踪这个玩家的客户端）、发 `CurioChangeEvent`、重新计算属性修饰，然后 `setPreviousStackInSlot(i, stack.copy())`。所以我们在服务端 `setStackInSlot` 之后，**下一个 tick Curios 自己发现变化并同步**，保存则靠 Curios 的能力数据随玩家存档。**不需要显式同步调用**。

**副作用要写进验证**：背包内容每次变化（每次点击）都会让 Curios 同步整只栈（带内容）给跟踪的客户端、发一次 `CurioChangeEvent`；原版装备槽对胸甲里的物品也是同样的整栈同步，所以量级相同。

## D5 数据文件

- **标签** `data/curios/tags/item/back.json`：`{"replace": false, "values": ["mcfloatingbackpack:leather_floating_backpack", …五个]}`。标签键是 `curios:back`（`CuriosTags.createItemTag(id)` = `curios:<id>`），物品标签目录是 1.21 通用的 `tags/item/`。
- **槽位分配** `data/mcfloatingbackpack/curios/entities/player_back.json`：`{"entities": ["minecraft:player"], "slots": ["back"]}`。`CuriosEntityManager` 读 `curios/entities`（所有命名空间合并，文件名以 `_` 开头的被跳过），字段 `entities`（实体类型 id 或 `#标签`）与 `slots`；同一个槽 id 重复时 `buildKeepingLast`，**不叠加**。

## D5b 槽位验证器

`back` 槽的验证器是 `curios:tag`；`DynamicStackHandler.isItemValid` 还会 `CuriosApi.isStackValid(ctx, stack)` 并发 `CurioCanEquipEvent`。我们的背包在 `curios:back` 标签里，所以放得进去；背包没有实现 `ICurio`，走 Curios 的默认行为（`curio.canEquip` 缺省为真）。

## D6 来源接口的实现要点

`CuriosWornSource implements WornSource<LivingEntity, ItemStack>`：

- `id()` = `BackpackRef.Source.CURIOS.ordinal()`（在 `Source` 末尾追加 `CURIOS(true)`，序号 4）；`order()` = 10；
- `active()` = `CompatConfig.policy().specificAdaptation("curios")`（客户端读 `modLevels`；专用服务器配置没加载，`policy()` 返回 `DEFAULT`，即 FULL——已知限制）；
- `slotCount(entity)`、`get`、`set`、`hiddenRender` 如 D3；
- 注册：`CuriosCompat.register()` → `WornBackpacks.register(new CuriosWornSource())`。

`BackpackQuickTransferPayload.holderOf` 已对 worn 来源一律返回 `Holder.CHEST`（032），对 Curios 格子同样成立（背包不在物品栏 0～35）。

## D7 测试

- **单元测试（纯文本/资源检查）**：`CuriosBackResourcesTest`：标签文件存在、格式对、**5 个档位都在**、没有多余项；实体文件存在、`entities` 含 `minecraft:player`、`slots` 含 `back`；`BackpackRefTest` 增补 `CURIOS` 序号 4 且 `worn`、已有序号不变；`DependencyRulesTest` 现有的检查覆盖"Curios 类型只在 `compat/curios/`"与"构建里只 `compileOnly`/`localRuntime`"，并为新坐标组更新常量。
- **游戏内验证**：Curios 界面出现后背槽；放进去/取出/交换；背上模型；按 B；顺序；隐藏渲染；GENERIC；没装 Curios（`-PcleanRuntime`）；保存重进。
