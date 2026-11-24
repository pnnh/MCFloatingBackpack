# Research: 联合背包 · 单背包标签

读码确认（2026-11-24）。每条给出 Decision / Rationale / Alternatives。规格里"Plan 阶段要读代码确认的事实"四条放在最前。

## R1. `OpenSessions.check` 对没有身份的背包

- **事实**：`OpenSessions.opened(player, null)` 是**移除**记录；`check` 在没有记录时返回 `NO_SESSION`，所以没有身份的背包**没有过期保护**（`BackpackGate.acquireForPanel` 只在 `STALE` 时拒绝）。`acquire` 会在第一次使用时给背包身份并 `ref.setStack`。
- **Decision**：联合会话选标签时一律先 `acquire`（给身份），再 `noteOpened`；因此联合会话里的目标**一定有身份**，过期保护全程有效。成员表本身只读（不 acquire）。
- **Rationale**：直接复用既有保护，不新增判断。
- **Alternatives**：对无身份背包用"引用 + 内容哈希"做保护——多一套机制，被否。

## R2. `OpenToolPayload` / 移动工具找背包的方式

- **事实**：`OpenToolPayload` → `MobileTools.has(player, tool)` → `BackpackRef.resolve(player)`，即 **B 会打开的那只**；`MobileFurnaceStore`（3 处）、`MobileFurnaceMenu`、`PipeActions`、`PlayerLights`、`AutoPickupEvents`、`JukeboxPlayback`（客户端 2 处）、`BackpackWatchService`（诊断）都是同样的"B 的那只"。唱片机播放读物品栏里的栈，不读面板视图。
- **Decision**：这些**全部保持 `BackpackRef.resolve`，不改**。规格里"面板类请求"清单去掉"移动工具打开"；联合界面里，**移动工具（唱片机、工作台、熔炉）三个控件**在非 B 激活的标签里灰掉（规格原先只写唱片机，这里补全）。
- **Rationale**：移动熔炉的状态存在工具物品上、按 B 的那只找；在别的标签里点它会开到 B 那只的熔炉，必须灰掉；统一处理最简单。
- **Alternatives**：让工具跟着标签走——要改 5 处服务端解析、熔炉状态与菜单，且与"自动拾取/唱片机只对 B 的那只"的既定原则不一致，被否。

## R3. JEI 排除区的实现

- **事实**：`FloatingBackpackJeiPlugin` 用 `IGlobalGuiHandler.getGuiExtraAreas()`，返回面板矩形 `(posX, posY, width, height)`，垃圾桶页展开时再加一个挂在面板右侧的矩形（`TrashPanelInput.layout().rect()`）。EMI 没有单独适配代码。
- **Decision**：联合标题栏 + 标签栏**像垃圾桶页一样挂在面板外沿**（面板上沿之上），在 JEI 排除区里**多加一个矩形**，不改面板自己的矩形。
- **Rationale**：既有先例，零改动现有几何。

## R4. 主手去重与 `BackpackRef.resolve` 返回 `MAIN_HAND`

- **事实**：`BackpackRef.getStack(MAIN_HAND)` 是 `player.getMainHandItem()`，对 `Player` 就是物品栏里被选中的那一格；所以同一只背包可以有两种引用：`MAIN_HAND` 和 `INVENTORY(selected)`。`resolve` 在手里有背包时返回 `MAIN_HAND`。
- **Decision**：成员表**只用 `INVENTORY(0..35)`、`OFF_HAND`、穿戴来源**三类引用，不出现 `MAIN_HAND`；会话目标引用同样如此。比较"某成员是不是 B 激活的那只"时先把 `resolve` 的结果**归一化**（`MAIN_HAND` → `INVENTORY(selected)`），新增纯函数 `BackpackRef.normalized(source, invIndex, selected)`。
- **Rationale**：点击包的"声称引用"与服务端会话目标都是同一种引用，比较不出错。
- **Alternatives**：成员表里留 `MAIN_HAND`——滚动快捷栏会让同一只背包的引用变化，目标失效，被否。

## R5. 面板类请求的全部入口（要换成统一解析的）

服务端 13 处（按文件）：`BackpackClickPayload`、`BackpackCursorGesturePayload`、`BackpackQuickTransferPayload`、`BackpackTakeFromContainerPayload`、`BackpackNetworkTakePayload`、`MergePanelCursorPayload`、`BackpackTrashPayload`、`BackpackTrashSendPayload`、`BackpackSortPayload`、`BackpackTagPayload`、`BackpackTagOverviewPayload`、`BackpackPickupBlacklistOpPayload`、`BackpackGate.tickOpenSession`（以及 `BackpackGate.resync` 内部）。

**不改**：`BackpackOpenRequestPayload`（B 键）、`BackpackWatchService`、`PipeActions`、`PlayerLights`、`AutoPickupEvents`、`MobileTools`/`MobileFurnace*`、客户端 `JukeboxPlayback`。

另外要查的**非 `resolve` 入口**：`snapshot/net/*`（快照窗口的请求是否按"当前面板背包"）、`CreativeCursor*`、`BackpackFluidsPayload`（S→C）。这些在 tasks 的核对项里逐个读，若按面板背包走就同样换。

- **Decision**：新增 `PanelTargets.resolve(player)`；13 处一次性替换；`NestingWiringTest` 风格的 `CombinedWiringTest` 断言这 13 个文件含 `PanelTargets.resolve` 且不含 `BackpackRef.resolve(`，并断言上面"不改"的文件仍含 `BackpackRef.resolve(`。

## R6. 复用现有 session 包做"切标签"的整包同步

- **事实**：`BackpackNetwork.sendSession(player, true, ref)` 一次发 `BackpackSessionPayload`（主区域、工具区、光标、行）+ 标签、垃圾桶、拾取黑名单、液体四个小包，并 `noteOpened`；客户端 `BackpackSessionPayload.handleClient` 重置标签总览、搜索框，设置 `activeRef`，`setVisible(true)`。这正是"换一只背包显示"需要的全部。
- **Decision**：切标签 = 改会话目标 + `sendSession(player, true, targetRef)`；**不新增同步包**。`BackpackGate.resync`（过期自愈）在联合会话里先问 `CombinedSessions.recover` 找回目标，再走同一路径。
- **注意**：session 包含 `MAX_SYNC_BYTES` 检查（1 MiB），已有。

## R7. 客户端"B 激活的是哪只"不能靠服务端推

- **事实**：B 激活的那只随主手/副手/快捷栏滚动变化，成员表并不变，服务端按"成员表变化才推"的节奏不会通知。
- **Decision**：客户端用自己已有的 `BackpackRef.resolve(player)` + 归一化，**每帧只在联合界面可见时**与当前标签成员的引用比较（几个整数比较）；不靠服务端字段。
- **Rationale**：客户端本来就这样决定"B 会开哪只"（`FloatingPanelEvents` 里发 open 请求前也是 `BackpackRef.resolve`）。

## R8. 面板怎么叠标题栏/标签栏而不改布局

- **事实**：`FloatingPanelState` 的几何（拖动条、拖动、`contains`、缩放把手、位置钳制）全从 `posX/posY/width/height` 派生；`PanelLayout.PAD_TOP = 17` 是写死的静态常量，`width()/height()` 是静态方法，被渲染、命中、滚动、JEI 多处读。垃圾桶页是先例：挂在面板外，自己的 `TrashPanelInput.onPress` 在面板命中检测**之前**认领点击、滚轮（`FloatingPanelEvents:575`）。
- **Decision（偏离规格 FR-020）**：**不给 `PanelLayout` 加 `topExtra`**。联合标题栏 + 标签栏画在面板上沿**之上**（矩形 `(posX, posY - BARS_H, width, BARS_H)`，`BARS_H = TITLE_H + TAB_H`），由新的 `CombinedBarInput` 在面板命中检测之前认领点击与滚轮；面板位置钳制在联合模式下给 `posY` 加下限 `BARS_H`；JEI 排除区多加一个矩形。规格 FR-020 改写为行为描述。
- **Rationale**：现有面板的所有几何、测试、命中全部不动，风险最小；视觉上就是"叠在现有标题栏上方"。
- **Alternatives**：`PanelLayout.topExtra` 贯穿——要改 4 个文件的几何且影响所有面板测试，收益只是"几何上归一"，被否。

## R9. Curios/Accessories 专用槽位与标签

- **事实**：061 的专用槽位 `floating_backpack`（`size: 1`，`validators: ["curios:tag"]`；Accessories 同理）靠物品标签 `curios:floating_backpack` / `accessories:floating_backpack` 决定放什么；`CuriosWornSource`/`AccessoriesWornSource` 每个槽位类型一个来源（`BackpackRef.Source` 序号已定）。
- **Decision**：联合背包五个物品**加进这两个 `floating_backpack` 标签**，**不加** `back`、`charm`。来源与槽位数据不动。第二个 `BackpackSearch` 实例（探针换成"是不是联合背包"）与第一个**共用同一批来源对象**：`WornBackpacks.register` 同时登记到两个实例（来源的 `id()` 在各自实例内唯一即可）。
- **已知限制**：专用槽位大小为 1，同一时刻只能放一只背包**或**一只联合背包；联合背包还可以穿胸甲槽。联合背包放进专用槽位后，背包仍可放 `back`/`charm`/胸甲槽。写进 README 与 DELIVERY。
- **已知缺口（本期不做）**：062 的"被 Accessories 丢掉的背包"只认浮动背包；槽位类型消失时槽内的联合背包不会被找回。记入 DELIVERY 的已知限制，后续可扩展。

## R10. 配方与升档

- **Decision**：
  - **合成**：新配方类型 `mcfloatingbackpack:combined_crafting`，类 `CombinedCraftingRecipe extends ShapedRecipe`，只覆盖 `matches`：在 `super.matches` 之上要求中间那格的背包"空"（纯函数 `BackpackEmptiness.isEmpty(stack)`：主区域、工具区、垃圾桶、光标、隐藏行与行内物品、液体存量全空；标签配置与拾取黑名单是设置，不算内容）。**不盖身份章**（联合背包无数据）。JEI 把它当普通有序合成显示。
  - **升档**：直接用原版 `minecraft:smithing_transform`（模板 + 低一档联合背包 + 材料块 → 高一档）；联合背包无数据，不需要自定义类，JEI 原生显示。
  - 末影之眼的用法留 tasks 阶段按"≥ 2 倍难度"核对数值后定，**数值只改 JSON**。
- **Rationale**：自定义的只有"必须空"这一条语义。

## R11. 服务端配置位置

- `[combined].enabled` 放进现有服务端配置（`DiagConfig.SERVER_SPEC`，与 `[snapshots]`、`[compat]` 同处，新增 `CombinedConfig.define(builder)`），启动读取；改动需重开世界。**客户端不读它**：U 键永远发 OPEN，服务端在关闭时回提示。

## R12. 共享资源预算（宪法 VI）

- 五个新物品各一张 16×16 平面贴图进共享图集；联合背包无背部模型、无新方块，不增加每个玩家的渲染/显存成本之外的东西。**任务里要用 `design/demo/tools/check_atlas_budget.sh` 在加入前后各测一次并记录**；三角形数：无模型，为 0。服务端每秒钩子对没有联合会话的玩家是一次 map 查找。

## R13. 贴图

- 美术资源没有现成的。**占位方案**：用 `design/demo/tools` 里的脚本风格，从各档 `*_floating_backpack_icon.png` 派生（同档位配色 + 紫色传送门/末影点缀），生成 5 张 16×16 占位图，标注"占位，待你替换"；贴图观感是主观项，由你确认。
