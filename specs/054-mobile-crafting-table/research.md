# Research: 移动工作台

方法：读 1.21.1 原版源码（NeoForge 21.1.251 的 sources jar）、本仓库代码、旅行者背包的 jar。可行性与做法选择见 `docs/plan/mobile-tools.md`，这里只记 plan 阶段的查证。

## R1 为什么是子类化 `CraftingMenu`，且不能用 `ContainerLevelAccess.NULL`

- `CraftingMenu(int, Inventory, ContainerLevelAccess)` 公开；`slotsChanged` 里用 `access.execute((level, pos) -> slotChangedCraftingGrid(...))` 算结果：**`ContainerLevelAccess.NULL.execute` 什么也不做**，所以结果格永远是空的。要用 `ContainerLevelAccess.create(level, pos)`。
- `stillValid` 是 `stillValid(access, player, Blocks.CRAFTING_TABLE)`，会检查 `pos` 处的方块是不是工作台——我们没有方块，所以**子类化并重写 `stillValid`**，不依赖 `access`。
- 菜单类型仍是原版 `MenuType.CRAFTING`（`getType()` 来自父类），所以服务端发的 `ClientboundOpenScreenPacket` 让**原版客户端**构造 `CraftingMenu(id, inventory)`（客户端用 `ContainerLevelAccess.NULL`，只用来显示与发点击）。客户端不需要认识我们的子类，JEI / EMI 的配方转移也按原版 `CraftingMenu` 工作。
- 先例：别的模组做"便携工作台"大多是这个形状。

## R2 `stillValid` 的时机

`ServerPlayer.tick` 每 tick 询问 `containerMenu.stillValid(this)`，返回假就 `closeContainer()` 并把 `containerMenu` 换回 `inventoryMenu`。所以"工具离开右区就关"只需要 `stillValid` 里查一次：`MobileTools.has(player, CRAFTING_TABLE)`（`BackpackRef.resolve` 扫手、背、物品栏 36 格，只在界面开着时才有，开销可忽略）。**不走 `BackpackGate`**（只读，不要求保护窗口确认）。

## R3 面板与新菜单共存

- 面板本来就叠在箱子等容器界面上：`ContainerScreenCompat.isOpenContainerScreen` 认得所有 `AbstractContainerScreen`，`HostScreenPolicy` 的 `ScreenKind.CONTAINER` 时宿主界面不出现、面板照常；`CraftingScreen` 是 `AbstractContainerScreen`，所以与箱子界面同样处理。
- 没有界面（宿主界面）时服务端 `openMenu`：客户端收到 `ClientboundOpenScreenPacket` 会 `setScreen(CraftingScreen)` 取代宿主界面；关掉合成界面后回到"没有界面"，`HostScreenManager.ensure()` 在面板仍可见时重新开宿主界面。这与"面板开着去右键一个工作台方块"是同一条路径，原本就能工作。
- 光标：`ServerPlayer.openMenu` 先 `closeContainer`（旧菜单 `removed` 会把光标上的东西退还给玩家物品栏），所以光标上拿着东西、或开着箱子，打开后东西不丢。面板自己的光标（`PanelCursor`，存在背包里的"面板光标"）不属于菜单，不受影响；游戏里验证"打开前光标上拿着东西"一条。
- 关闭：玩家关界面时客户端发 `ServerboundContainerClosePacket`，原版 `CraftingMenu.removed` 把合成格退还。

## R4 决策表

| 种类已知 | 右区有该工具 | 开着的是同种菜单 | 结果 |
|---|---|---|---|
| 否 | — | — | IGNORE |
| 是 | 否 | — | IGNORE（`stillValid` 本来就会关掉同种菜单） |
| 是 | 是 | 是 | CLOSE |
| 是 | 是 | 否（没有菜单、别的菜单、别种的我们的菜单） | OPEN |

"开着别的菜单"属于"否"：`openMenu` 会关掉旧的再开新的（R3）。

## R5 包

- `OpenToolPayload(byte tool)`，`StreamCodec` 手写：写一个字节；读一个字节，不抛异常（未知的值留给 `MobileTool.fromByte` 返回空，处理器忽略）。符合"服务端不信任客户端"的惯例（`BackpackSortPayload` 无字段、`BackpackTrashPayload` 只带槽号与是否开着界面）。
- 处理器 `handleServer`：`context.enqueueWork`；`context.player()` 不是 `ServerPlayer` 就返回；`MobileTool tool = fromByte(...)`；`has = tool != null && MobileTools.has(player, tool)`；`open = player.containerMenu instanceof MobileMenu m ? m.tool() : null`；按决策表执行。没有限频（连点 = 开关开关，无害）。
- 注册：`registrar.playToServer(OpenToolPayload.TYPE, OpenToolPayload.STREAM_CODEC, OpenToolPayload::handleServer)`，在 `versioned("19")` 那个 registrar 上，改成 `"20"`。

## R6 要改的历史测试

`grep -rn 'versioned(\\"19\\")'` 命中约 14 个文件（`ItemTooltipWiringTest`、`VersionCheckResourcesTest`、`DowngradeProtectionResourcesTest`、`SearchResourcesTest`、`JukeboxWiringTest`、`LightWiringTest`、`GreyCellWiringTest`、`SearchPrefixResourcesTest`、`BackpackSortWiringTest`、`CategoryExtensionUiResourcesTest`、`PipeWiringTest`、`TankWiringTest`、`WorldMarkerWiringTest`），实现时逐个把 19 改成 20；写死 `CURRENT = 4;` 的（`DataRevisionTest`、`JukeboxWiringTest`、`LightWiringTest`、`PipeWiringTest`、`TankWiringTest`）改成 5。其余测试不改。

## R7 配方与贴图

- 配方 `IDI / ATB / ICI`（`I` 铁锭、`D` 钻石、`A` 铁镐、`B` 铁斧、`T` 工作台、`C` 箱子），与现有所有配方两两不同（资源测试里沿用 053 的"两两不同"断言）。
- 贴图沿用 036 与 053 的手绘做法：`make_mobile_tool_icons.py` 里加一张小工作台（木桌面、3×3 格子线、一把小锯 / 锤）；占位，用户可用同名文件替换。共享图集前后各量一次。

## R8 开放问题（不阻塞 plan）

- **创造模式**：原版创造模式能开工作台界面，`CraftingMenu` 不受限；游戏里验证一次。
- **面板光标与新菜单**：R3 的推理需要游戏里验证"点工具前面板光标上拿着东西"（`PanelCursor` 有东西时点工具会怎样；本 feature 的客户端点击要求光标为空，所以不会触发）。
- **点击时服务端菜单是别人的**（别的模组的容器）：`openMenu` 照常关掉旧菜单，别的模组的 `removed` 会照常运行；若该模组的菜单不希望被这样关掉，那是它的事，本 feature 不特殊处理。
