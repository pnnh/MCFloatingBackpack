# Tasks: 背包整理

**Input**: `/specs/047-backpack-sort/` 的 spec.md、plan.md；宪法

**Tests**: 整理逻辑（合并拆分、排序、分类行优先、通用行兜底、守恒、整体放弃、幂等）用 JUnit（纯 Java）；标题栏算术用 `TitleBarLayoutTest`；接线用源码文本检查。**图标外观、点击、与服务端的完整同步、实际格子内容只能在客户端会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`…res/` = `src/main/resources/assets/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`（宿主机有用户的客户端在运行时不启动容器客户端）；容器见 `scripts/ai-gui/README.md`；AI 自动验证的勾选规则同前
- 不改配置项、依赖、贴图；协议号 17→18，只新增一个 C→S 包；011～046 的测试除协议号断言外不改
- 用户故事：US1 一键整理（堆叠与排序）；US2 优先进分类扩展行；US3 标题栏图标；US4 不丢东西、不动不该动的

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`（预期 171 个类、1271 个用例）；`git status` 干净（除 047 文档与 042 的勾选）；记在 `specs/047-backpack-sort/DELIVERY.md`（新建，中文）（基线 171 个类、1271 个用例）

## Phase 2: Foundational（纯 Java）

- [X] T002 `…/inventory/BackpackSorter.java`（泛型 `<T>`，无 Minecraft 类型）：`sort(List<T> slots, int baseCapacity, List<String> visibleCategories, CategoryMatcher<T> matcher, QuickTransferLogic.StackOps<T> ops, Comparator<T> kindOrder)` 返回新的槽位列表或 null：按 `sameKind` 归并累计 → 按 `kindOrder` 排种类 → 每种拆成"满堆在前、余量堆在后" → 逐堆用 `ExtensionRules.targetSegments` 放入各段的第一个空格（整堆一格）→ 校验（每种总数相等、不超 `maxStack`、行格子的堆满足 `matcher`）→ 任一失败或没位置返回 null；空背包与已整理好的背包返回与原内容相等的列表
- [X] T003 [P] `…/inventory/ItemSortOrder.java`（生产）：`compare(ItemStack, ItemStack)`：`minecraft` 命名空间先 → 命名空间 → 路径 → `componentKey`（组件补丁条目按组件类型注册名排序，拼 `类型=值.toString()`，移除的写 `类型=removed`）；`…/inventory/ItemStackTransferOps` 已是 `StackOps<ItemStack>`
- [X] T004 [P] 单元测试 `…test/inventory/BackpackSorterTest.java`（用字符串或小记录当 `T`，自带 `StackOps` 与行匹配器）：US1（散堆合并 64+余量、不同组件不合并、不可堆叠按序、空格在末尾、幂等、空背包）；US2（食物进食物行从行首起、行内排序、多于一行时余量进档位格子、两个同类行一行一行填、通用行只在档位格子满后兜底、物品属多个类别时进第一个匹配行、行里不匹配的旧物品移到档位格子）；US4（守恒：随机 1000 组内容与行配置，逐种总数相等、无超限、行内全匹配、再整理不变；放不下时返回 null 且输入未变；超上限旧堆被重新拆分）；排序键（`minecraft` 先、其余命名空间字母序、同命名空间路径序、同种满堆在前）
- [X] T005 运行 `./gradlew compileJava test`；故意把一处规则写错（如把 targetSegments 的段顺序反过来）确认 T004 会失败，再还原（变异检查：段顺序反过来，5 个用例失败，还原后通过）

**Checkpoint**: 整理逻辑就绪，可在不启动游戏的情况下测试

## Phase 3: User Story 1 + 2 + 4 - 服务端整理 (Priority: P1) 🎯 MVP

- [X] T006 [US1] `…/inventory/BackpackInventory.java`：`public boolean sortMain()`：取主区域各格、`visibleRowCategories()`、`baseCapacity()`、`new ItemCategoryMatcher(tags)`、`ItemStackTransferOps.INSTANCE`、`ItemSortOrder`；`BackpackSorter.sort` 非 null 才用 `setStackInSlot` 写回主区域并返回 true；null 时不动并返回 false；不碰工具区、隐藏行、光标、标签、垃圾桶、黑名单
- [X] T007 [US4] `…/network/BackpackSortPayload.java`（无字段，`StreamCodec.unit`，类型 id `sort`）与 `handleServer`：玩家有效背包校验（`BackpackRef.resolve`、`stillValid`，失败 `BackpackNetwork.sendSession(player, false, null)`）；`player.containerMenu.getCarried()` 非空 → 返回；`BackpackGate.acquireForPanel` 失败 → 会话失效路径；`backpack.getCursor()` 非空 → 返回；`DiagLog.record(...)`；`sortMain()`：true → `ref.saveInventory`、`BackpackNetwork.sendFullSync`；false → 服务端日志一行（"sort aborted: …"），仍发一次完整同步让客户端与服务端一致
- [X] T008 [US4] `…/network/BackpackNetwork.java`：`versioned("17")` → `versioned("18")`；`registrar.playToServer(BackpackSortPayload.TYPE, BackpackSortPayload.STREAM_CODEC, BackpackSortPayload::handleServer)`；把所有断言协议号 17 的历史测试改为 18（`SearchResourcesTest`、`SearchPrefixResourcesTest`、`ItemTooltipWiringTest`、`GreyCellWiringTest`、`SearchTermsContractTest`、`WorldMarkerWiringTest`、`CategoryExtensionUiResourcesTest`、`VersionCheckResourcesTest`、`DowngradeProtectionResourcesTest` 及 `grep` 到的其它），方法名里的 `Is17`/`Still17` 同步改（改了 9 个既有测试里的协议号断言，见 DELIVERY.md）
- [X] T009 运行 `./gradlew compileJava test`

**Checkpoint**: 服务端整理可用（客户端还没有入口）

## Phase 4: User Story 3 - 标题栏图标 (Priority: P1)

- [X] T010 [US3] `…/client/panel/TitleBarLayout.java`：加 `hasSort`、`sortLeft`（常驻，`sortLeft = searchLeft - GAP - BUTTON_SIZE`；`textRightLimit = sortLeft`）；更新 `of(...)` 与类注释；`…test/client/panel/TitleBarLayoutTest.java` 扩展：3、4、6、9 列（有/无关闭键）下整理图标存在、元素互不重叠、不越界、编号空间 ≥ `MIN_TEXT`；让位顺序仍是快照键先走
- [X] T011 [US3] `…/client/panel/FloatingPanelState.java`：`hitsSortButton(x, y)`；`…/client/panel/FloatingPanelInput.java`：在放大镜（`SearchInput.onPress`）之后、快照键之前：`!isRight && !SearchState.get().isOpen() && state.hitsSortButton(...)` → 若光标为空则 `PacketDistributor.sendToServer(new BackpackSortPayload())` 并 `PanelSelection.get().clear()`，返回 true（光标有物品也吞掉这次按下，不发请求）
- [X] T012 [US3] `…/client/panel/FloatingPanelRenderer.java`：`drawSortButton`（`fill`：按钮底色、三条递减的白色横杠；只在 `!SearchState.get().isOpen()` 时画；悬停 tooltip `panel.mcfloatingbackpack.sort.tooltip`）；`…res/lang/en_us.json`/`zh_cn.json` 各加 `panel.mcfloatingbackpack.sort.tooltip`（"Sort backpack" / "整理背包"）；两份键保持一致
- [X] T013 [P] [US3] 源码文本检查测试 `…test/resources/BackpackSortWiringTest.java`：`BackpackNetwork` 注册了 `BackpackSortPayload` 且只新增这一个包、协议号 `"18"`；`BackpackSortPayload.handleServer` 里有 `BackpackRef.resolve`、`stillValid`、`getCarried().isEmpty()`、`BackpackGate.acquireForPanel`、`getCursor().isEmpty()`、`sortMain()`、`sendFullSync`；`FloatingPanelInput` 里整理在放大镜之后、快照键之前，并检查搜索框未打开与光标为空；渲染里整理图标只在搜索框关闭时画；语言键中英文齐全；`BackpackSorter` 不含 `net.minecraft`；无新配置项、无新依赖
- [X] T014 运行 `./gradlew compileJava test`

**Checkpoint**: 整理可从面板使用

## Phase 5: 游戏内验证与收尾

- [X] T015 [P] `README.md` 与 `README_CN.md`：加整理功能的一条（标题栏整理图标；相同物品堆叠、按注册名排序、优先进分类扩展行、通用行兜底；工具区与隐藏行不动）；`docs/plan/category-extensions.md` 里"搜索与整理单独做"的整理部分标注"整理已做（047）"
- [X] T016 游戏内验证（AI 自动验证，容器，plan 的 D1～D9；`/data get entity @s` 或截图核对格子内容）：每项写实际验证了什么，做不了的写"未验证"；**不在 `src/main` 留任何调试入口**——**AI 自动验证已做**（D1、D2、D3、D6、D8 通过；D4 通用行兜底与放弃整理只有单元测试；3/4/6 列截图未拍成，详见 DELIVERY.md）
- [X] T017 `specs/047-backpack-sort/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 plan/tasks 的偏离、每个游戏内验证项验证了什么/没验证什么、已知限制、服务端整理耗时（粗测）、宪法复查
- [X] T018 最终检查：`./gradlew compileJava test` 全部通过；`git status` 确认无临时修改残留、没有新增依赖与配置项；协议号 18 与只新增一个包；**不提交、不打 tag**（173 个类、1298 个用例通过；无临时修改残留；协议号 18、只新增一个包）
- [X] T019 用户确认：用户在真机上点整理图标（散堆、分类行、满背包、窄面板）；**只有用户明确确认才勾**——**用户已确认（2026-11-12）：没有问题**
