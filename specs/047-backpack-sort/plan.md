# Implementation Plan: 背包整理

**Branch**: `047-backpack-sort` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

## Summary

标题栏加整理图标，点击发一个无参数请求，服务端把主区域"合并堆叠 → 按注册名排序 → 按分类行优先的规则放回"，守恒校验通过才写入，再发完整同步。**服务端权威（宪法 II）、纯逻辑泛型可测**。核心思路：

1. **整理的纯逻辑写成泛型类 `BackpackSorter<T>`**，沿用 `QuickTransferLogic.StackOps<T>` 与 `CategoryMatcher<T>`，放置顺序**直接复用** `ExtensionRules.targetSegments`（匹配的可见分类行 → 档位格子 → 通用扩展行），所以"优先进分类行"与 035/038 的放入规则天然一致（D1）。
2. **守恒与放不下的处理在纯逻辑里**：算出新布局后校验每种物品总数、格子上限、行内匹配；任一不成立返回 null（整体放弃），调用方不写入（D2）。
3. **排序键**：`ItemSortOrder`（生产）= `minecraft` 命名空间最前 → 其它命名空间字母序 → 路径字母序 → 组件的稳定写法（按组件类型 id 排序后拼接）；测试里用字符串比较器（D3）。
4. **服务端入口**与快速转移同一套校验（`BackpackRef.resolve`/`stillValid`/`BackpackGate.acquireForPanel`），光标非空拒绝，成功后存回并 `sendFullSync`；协议号 17 → 18，只新增一个客户端到服务端的包（D4）。
5. **标题栏**：`TitleBarLayout` 加 `hasSort`/`sortLeft`，让位顺序 快照键 → 编号空间，放大镜与整理图标常驻；渲染与命中沿用放大镜的写法（搜索框打开时不画不命中）（D5）。

## Technical Context

Java 21 · NeoForge 21.1.251 / Minecraft 1.21.1 · 无新增依赖 · Testing: JUnit 5 `BackpackSorterTest`（含随机守恒 1000 组、幂等、行规则）、`TitleBarLayoutTest` 扩展、`BackpackSortWiringTest`（源码文本检查）；容器里游戏内验证 · Constraints: 无新配置项、无新贴图（图标用 `fill` 画）、协议号 17→18（只加一个 C→S 包）、011～046 的测试除协议号断言外不改。

## Constitution Check

| 原则 | 评估 |
|---|---|
| I. Overlay Not Screen | 图标画在同一条面板渲染路径里，不新增 Screen PASS |
| II. Server Owns Items | **所有改动在服务端**：客户端只发无参数请求，不乐观更新；守恒校验，放不下整体放弃；与其它改背包的请求同一套校验 PASS |
| III. One Feature At A Time | 只做整理；其它排序依据、设置、快捷键、自动整理明确不做 PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方；可堆叠与组件判断用原版 `isSameItemSameComponents` PASS |
| V. Verify In A Client Session | quickstart 要求容器里验证（含 `/data get` 核对格子）PASS（实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 无贴图；整理一次 O(n²)（n ≤ 约 120 种堆）可忽略 PASS |
| VII. Minimal Dependencies | 无新依赖 PASS |

## Research

- **D1 复用放入规则**：`ExtensionRules.targetSegments(visibleCategories, baseCapacity, stack, matcher)` 已返回三段格子编号（匹配的分类行格子、档位格子、通用扩展行格子）。整理把"逐堆按顺序放入第一个空格"套在这三段上：每一段内取第一个空格；上一段没空格再下一段。多个同类行自然是"一行一行填"。**Decision**: 不另写放置规则。
- **D2 守恒**：整理前把每个非空堆归并到"种类"（`sameKind`）累计总数；整理后重新累计，要求逐种相等、没有超 `maxStack`、行格子的堆满足 `matcher`；还要求放置时每一堆都找到空位，否则返回 null。**Decision**: 全部在 `BackpackSorter` 里，生产与测试跑同一份代码。
- **D3 排序键**：生产里 `ItemSortOrder.compare(a, b)`：命名空间 `minecraft` 先；命名空间 `compareTo`；路径 `compareTo`；再比 `componentKey`（组件补丁的条目按组件类型的注册名排序，拼成 `类型=值.toString()`；已被移除的组件写 `类型=removed`）。同种堆的"满堆在前、余量在后"在整理逻辑里处理，不靠比较器。**待实现时核对**：`DataComponentPatch` 的遍历与 `toString` 在两个相等补丁之间是否一致（所以 `componentKey` 自己按键排序，不依赖补丁的迭代顺序）。
- **D4 服务端**：`BackpackSortPayload`（无字段，`StreamCodec.unit`）+ `handleServer`：沿用 `BackpackQuickTransferPayload.handleServer` 的前半段（`BackpackRef.resolve`、`stillValid`、携带栈为空、`BackpackGate.acquireForPanel`），再检查 `backpack.getCursor().isEmpty()`，调用 `backpack.sortMain()`（内部取 `rowSet`、`tags`、`ItemCategoryMatcher`，成功才写回主区域），`ref.saveInventory`、`sendFullSync`；`DiagLog.record` 记一行。**Decision**: 不需要 `CreativeInventorySync`（玩家物品栏没变）。
- **D5 标题栏**：`TitleBarLayout.of` 现在是 `close → snapshot → search`；加 `sort` 在 search 左边：`sortLeft = searchLeft - GAP - BUTTON_SIZE`。让位：先 `hasSnapshot`（现规则），整理图标常驻，所以编号的文字空间 = `sortLeft - MARGIN - textStart`。3 列（宽 104）实算：snapshot 在 91、search 在 79、sort 在 67（相对面板左），编号可用 ≥ 24 仍成立。`boxLeft/boxRight` 的搜索框范围不变（框打开时占整段）。
- **D6 客户端**：`FloatingPanelState.hitsSortButton(x,y)`；`FloatingPanelInput` 在放大镜之后、快照键之前判断（搜索框打开时不判断）；`FloatingPanelRenderer.drawSortButton`（`fill` 画三条递减的横杠，悬停 tooltip `panel.mcfloatingbackpack.sort.tooltip`）；点击时若光标有物品则不发；发请求后 `PanelSelection.get().clear()`。发包用与其它请求相同的 `PacketDistributor.sendToServer`。
- **D7 协议号**：`versioned("17")` → `versioned("18")`；断言 17 的历史测试（`SearchResourcesTest`、`SearchPrefixResourcesTest`、`ItemTooltipWiringTest`、`GreyCellWiringTest`、`SearchTermsContractTest`、`WorldMarkerWiringTest`、`CategoryExtensionUiResourcesTest`、`VersionCheckResourcesTest`、`DowngradeProtectionResourcesTest` 等）逐个改成 18（它们断言的是"这个 feature 没改协议"，现在协议被本 feature 合法地改了）。
- **风险**：`getComponentsPatch` 的值 `toString` 对带大数据的物品（装满的潜影盒）开销；整理只在点击时做一次，可接受，实现后度量。

## Project Structure

```text
specs/047-backpack-sort/{spec.md,plan.md,tasks.md,DELIVERY.md,checklists/}
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/BackpackSorter.java            # ★ 泛型纯逻辑
├── inventory/ItemSortOrder.java             # ★ 生产排序键（ItemStack）
├── inventory/BackpackInventory.java         # 改：sortMain()
├── network/BackpackSortPayload.java         # ★ C→S 无参数请求
├── network/BackpackNetwork.java             # 改：注册、协议号 18
└── client/panel/{TitleBarLayout,FloatingPanelState,FloatingPanelInput,FloatingPanelRenderer}.java   # 改
src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json   # 改：sort.tooltip
src/test/java/pnnh/mcfloatingbackpack/inventory/BackpackSorterTest.java
src/test/java/pnnh/mcfloatingbackpack/client/panel/TitleBarLayoutTest.java   # 扩展
src/test/java/pnnh/mcfloatingbackpack/resources/BackpackSortWiringTest.java
README.md / README_CN.md
```

## 验证（quickstart，容器）

D1 散堆圆石 10/20/40 与原木、镐、面包，空格夹中间：整理后圆石 64+6、物品连续按序、空格在末尾（`/data get entity @s` 或截图核对）；D2 再点一次不变；D3 有食物行与工具行：食物进食物行、镐进工具行、圆石在主格子；食物多于 9 堆时余量进主格子；D4 通用扩展行只在主格子满后才用（造满主格子）；D5 工具区、隐藏行、背包标签不变；D6 光标拿着物品点图标：无变化；D7 标题栏 9/6/4/3 列截图；D8 搜索框打开时图标消失；D9 面板挂在物品栏界面上同样可用。

## Complexity Tracking

无需要论证的违例（协议号升级是新增请求的既有流程）。
