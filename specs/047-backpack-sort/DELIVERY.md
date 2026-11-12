# 047 背包整理：交付说明

**状态**：完成（2026-11-12）。代码、自动化测试、文档完成；游戏内验证做了 AI 自动验证（容器，见下）；**用户已确认 T019（没有问题）**。

## 做了什么

标题栏（放大镜左边）加一个整理图标；点击后服务端整理背包的**主区域**：同种物品合并堆叠，按注册名顺序排，能放进分类扩展行的物品优先进该行，通用扩展行最后兜底。

- **纯逻辑** `BackpackSorter<T>`（泛型，无 Minecraft 类型）：按"同物品同组件"归并累计 → 按种类排序 → 每种拆成"满堆在前、余量在后" → 逐堆用 035 已有的 `ExtensionRules.targetSegments`（匹配的可见分类行 → 档位格子 → 通用扩展行）放入各段的第一个空格 → 校验（每种总数相等、不超堆叠上限、行格子里只有该行收的物品、每堆都找到位置），任一不成立返回 null（整体放弃）。
- **排序键** `ItemSortOrder`：`minecraft` 命名空间最前 → 其余命名空间字母序 → 路径字母序 → 组件的稳定写法（按组件类型注册名排序后拼接，不依赖补丁的遍历顺序）。
- **服务端**：`BackpackInventory.sortMain()`（只写主区域；不碰工具区、隐藏行、光标、标签、垃圾桶、黑名单）；`BackpackSortPayload`（客户端到服务端，无字段）：解析玩家的有效背包、`stillValid`、携带栈为空、`BackpackGate.acquireForPanel`、面板自己的光标为空、整理、成功才 `saveInventory`、无论成败都 `sendFullSync`；放弃时记一条 WARN。
- **客户端**：`TitleBarLayout` 加 `sortLeft`（常驻，让位顺序：快照键先走）；`FloatingPanelState.hitsSortButton`；`FloatingPanelInput`（放大镜之后、快照键之前；搜索框打开时不判断；光标有物品时不发，仍吞掉这次按下）；`FloatingPanelRenderer.drawSortButton`（`fill` 画的紫色按钮加三条递减横杠，悬停 tooltip）；清空选区；不做乐观更新。
- **协议号 17 → 18**，只新增一个客户端到服务端的包；语言键 `panel.mcfloatingbackpack.sort.tooltip`（中英文）；README 两份、`docs/plan/category-extensions.md` 各一处。

## 基线与结果

- 基线（T001）：171 个类、1271 个用例，全部通过。
- 完成后：**173 个类、1298 个用例，全部通过**（新增 `BackpackSorterTest` 18 个、`BackpackSortWiringTest` 8 个；`TitleBarLayoutTest` 扩展）。
- 变异检查（T005）：把 `targetSegments` 的段顺序反过来，5 个分类行用例失败，还原后通过。
- 随机守恒：1000 组随机背包与行配置（无行、单行、同类多行、通用行、食物加通用行等），整理后每种总数不变、无格子超上限、行内只有匹配物品、再整理不变——全部成立。

## 与 plan/tasks 的偏离

- **协议号 17 → 18 带动 9 个既有测试**：`SearchResourcesTest`、`SearchPrefixResourcesTest`、`ItemTooltipWiringTest`、`GreyCellWiringTest`、`SearchTermsContractTest`、`WorldMarkerWiringTest`、`CategoryExtensionUiResourcesTest`、`VersionCheckResourcesTest`、`DowngradeProtectionResourcesTest` 里"协议号仍是 17"的断言改成 18（它们断言的是"这个 feature 没改协议"，现在协议被本 feature 合法地升了）；方法名 `theProtocolIsStill17…` 改为 `theProtocolIs18…`。除此之外没改任何既有测试。
- 整理放弃时，plan 写"仍发完整同步"，实现也如此（让两边一致）；`BackpackSortWiringTest` 断言保存只在成功分支、同步在最后。
- 整理按钮没有新增贴图，用 `fill` 画；颜色取紫色 `0xFF7B5FB5`（与绿色放大镜、蓝色快照键、红色关闭键区分），等用户看过再定。

## 游戏内验证（AI 自动验证，2026-11-12，容器 `mcfb-c1`，创造模式世界）

| quickstart | 结果 |
|---|---|
| D1 一键整理 | **通过**：原本散落且夹着空格的 14 种物品，点整理后从第一格起连续、按注册名顺序排（圆石、钻石、钻石镐、钻石剑、绿宝石、金斧、铁斧、铁镐、下界合金锭，下一行是原木与木板）；空格在末尾 |
| D2 幂等 | **通过**：再点一次，面板截图与上一次**逐像素一致** |
| D1 合并 | **通过**：另给 30 个圆石放在背包很远的一格，原有圆石 16：整理后合并成一堆 **46**，在第一格 |
| D3 分类行优先 | **通过**：食物扩展行里原有面包 24，另在主区域放 5 个苹果：整理后苹果和面包都在食物行里（苹果在前、面包在后，按注册名序），主区域里没有食物 |
| D5 不动不该动的 | **部分通过**：整理前后左区的食物扩展件、工具区物品不变；整理前隐藏行里有面包（黄点），整理时它不受影响（黄点仍在，后来放回扩展件后面包完好）；**背包标签与垃圾桶内容没有专门核对**（由 `sortMain` 的源码文本检查保证不碰） |
| D6 光标拿着物品 | **通过**：光标拿着钻石，点整理图标：整理没有发生（格子保持原样），放回后与整理后的状态逐像素一致 |
| D7 标题栏 | **部分通过**：9 列下按钮显示正常（紫色，放大镜左边），悬停 tooltip "Sort backpack"（在容器的小窗口里被屏幕顶边截掉一部分）；**3、4、6 列的截图没拍成**（缩小窗口后标题栏被容器里的新手提示条遮住），由 `TitleBarLayoutTest` 的算术断言覆盖 |
| D8 搜索框 | **通过**：点放大镜打开搜索框后整理图标不显示 |
| D4 通用扩展行兜底 | **未在游戏里验证**：只有 `BackpackSorterTest` 的单元测试（主格子满后才用通用行） |
| 放弃整理（无合法位置） | **未在游戏里验证**：只有单元测试（`whenThereIsNoPlaceTheWholeTidyingIsGivenUp`）与源码文本检查 |
| D9 面板挂在物品栏界面上 | **通过（间接）**：点整理在创造物品栏界面里同样可用（放物品用的是同一个界面） |

## 度量

- **纯逻辑部分（单元测试，开发机）**：整理 126 格（108 格加两个扩展行）约 **96 微秒**/次（`BackpackSorterTest`）。真实的 `ItemStack` 复制与组件比较要再加一点，**未度量**；点击到面板更新在容器里无可见停顿（软件渲染，不可信）。

## 已知限制

- 排序只按注册名：同类东西不一定挨着（如各种镐按字母排）；没有按类别分组、按数量等其它依据，也没有设置项（Out of Scope）。
- 整理时行里已不匹配的旧物品被当作普通物品挪到主格子；主格子放不下时整个整理放弃，只记一条服务端 WARN，玩家看不到提示（背包保持原样）。
- 同一物品不同数据组件各自成堆，不合并。
- 通用扩展行兜底、放弃整理、3/4/6 列标题栏截图、真机、Windows/Linux：未在游戏里验证。

## 宪法复查

I Overlay：图标画在同一条面板渲染路径里。II Server Owns Items：**所有改动在服务端**：客户端只发无参数请求、不乐观更新；守恒校验，放不下整体放弃；与快速转移同一套校验。III One Feature：只做整理。IV Vanilla：用原版 `isSameItemSameComponents`。V Verify：已在客户端会话里验证（未验证项如实列出）。VI Budget：无贴图，一次整理 O(n²) 可忽略。VII：无新依赖。**结论：全部通过，宪法不需要修订。**
