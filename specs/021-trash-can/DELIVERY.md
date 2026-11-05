# Delivery: 021-trash-can

**日期**: 2026-11-05

**状态**: 代码、单元测试、编译、静态检查、文档与图集度量完成；用户进游戏验证过**基本功能（US1/US2 的点击与拖放），没有发现问题，但没有逐项对照 quickstart 做全面验证**。其中 T020、T028 因为没有逐项验证**保持未勾选**；T032、T038、T043 用户后来验证过已勾选；T045 未验证，保持未勾选。US3（拿回）、US4（Ctrl 恢复）、US5（限放 1 个）的代码写在那次基本验证之后，**用户后来在游戏里实际验证过这三项，没有问题**（口头确认）。仍未验证的是 T045（持久化、兼容、窗口缩窄、回归、联机）以及 T020/T028 的未逐项部分。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| 改动前基线（T001） | `./gradlew compileJava test` 通过；43 个测试类、355 个用例、0 失败 |
| Phase 2（T008） | `./gradlew compileJava test` 通过；新增 `TrashBinTest`（15 个用例）、`TrashRequestTest`（5 个用例）；测试与实现同时写出，没有单独演示过"先失败" |

## 与 plan/tasks 的偏离

- `TrashContents` 的 `CODEC`/`STREAM_CODEC` 无法单测（测试类路径没有 Minecraft），编码层往返与超限抛异常改在客户端会话里验证（未单测）。

## 图标（T011）

`mcp__ludo-ai__createImage`（`item-icon`、`16-Bit Pixel`、n=2，共 1 点数）生成两张 768×768 候选（提示词见下），都是可辨认的铁灰色垃圾桶；选了第 2 张（更亮、条纹更清楚）。源图放在 `design/demo/tools/trash_can_icon/`（`src1/2.png`、`.webp` 原件，**不随模组发布**），用 `design/demo/tools/make_trash_can_icon.py`（由 `make_tag_manager_icon.py` 复制：去背景、裁切、按最长边缩放到 16、alpha 二值化、12 色量化、底部对齐）生成 `textures/item/trash_can.png`（16×16，实际内容 13×16）。目视检查过放大预览，**最终效果由用户在游戏里看**，不满意可换第 1 张或改手绘。

提示词（原文）：`A small iron trash can, Minecraft-style item icon: a gray metal bucket-shaped bin with a flat lid with a small handle on top and a few vertical ridges, dark outline, simple shading, front view, centered, plain white background, no text, no shadow`

## 阶段结果

| 项 | 结果 |
|---|---|
| Phase 3（T019） | `./gradlew compileJava test` 通过；新增 `TrashCanResourcesTest`（4 个用例）、`TrashPanelLayoutTest`（7 个）、`TrashPanelStateTest`（2 个）；共 379 个用例（含 Phase 2 的 20 个），0 失败。中途 `TrashCanResourcesTest.recipeIsTheDesignedCross` 失败过一次：测试先把空白全去掉再比较含空格的配方图案，是测试写错，已改成先用正则检查图案再去空白 |

## 原版容器界面的鼠标流程（T026，读 `AbstractContainerScreen` 源码的结论）

读了 `neoforge-21.1.251-sources.jar` 里的 `mouseClicked`/`mouseDragged`/`mouseReleased`，research Decision 8 的假设**全部成立**，没有不一致处：

- 光标为空时在格子上按下：立即 `PICKUP`，并置 `skipNextRelease = true`；松开时除"双击 + 松开处有格子"这一支（松开处是垃圾桶，不会命中同一个格子）外，直接 `return`，什么都不发。
- 光标非空时按下：进入 `isQuickCrafting`（快速分配）；拖动时把经过的格子加进 `quickCraftSlots`；松开时：`quickCraftSlots` 非空 → 发一串 `QUICK_CRAFT` 包；为空且携带物非空 → 在**松开位置**做 `PICKUP`（松开点在 GUI 之外会变成 `-999` 扔出物品，松开点在某个格子上就点那个格子）；最后无论如何 `isQuickCrafting = false`。
- 所以：吞掉松开会让 `isQuickCrafting` 停在 true，之后原版的点击都被忽略；放行但携带物仍在会扔出/点到被垃圾桶遮住的格子。**先把客户端的携带物清空、发 `ADD`、再放行松开**可以避免两者：携带物为空时松开走不到 `PICKUP`，`QUICK_CRAFT` 包到服务端时服务端携带物已空（`ADD` 先到），服务端对空携带物的快速分配是空操作。
- 采用的做法与 contracts 第 4 节一致：`TrashPanelInput.onRelease` 取消 013 手势、发 `ADD`、客户端预测清空光标；按下是面板消费的（`PRESSES` 里有）才吞掉松开，否则放行。

**这是读源码的结论，没有在游戏里验证**；T028 的 Q18/Q19 要用箱子和玩家物品栏实测。

## 阶段结果（续）

| 项 | 结果 |
|---|---|
| Phase 4（T027） | `./gradlew compileJava test` 通过；新增 `TrashDropTrackerTest`（8 个用例）；`grep -rn "setScreen(" client/panel/trash` 无命中。编译提示 `BackpackTrashPayload` "uses or overrides a deprecated API"（没有定位是哪一处，不影响行为，Polish 阶段处理或记入已知问题） |
| Phase 5～7（T031、T037、T042） | `./gradlew compileJava test` 通过；新增 `ToolAreaRulesTest`（8 个用例）、`QuickTransferGestureTest` 增加 5 个 `Side.TRASH` 用例；旧的 `ToolAreaCapacityTest` 里"放入规则恒为真"的用例随规则改变而改成"普通物品在各区域都放行"。最终：50 个测试类、409 个用例、0 失败 |
| 图集度量（T047） | 先 `pgrep` 确认没有客户端在运行，再跑 `design/demo/tools/check_atlas_budget.sh`：共用方块图集 **2048×1024**（mip 级别 4），估算显存约 11.2MB，预算 2048 → **通过**；脚本结束后确认没有残留的客户端进程 |
| 静态检查（T037、T048） | `build.gradle`、`neoforge.mods.toml`、`PanelSelectionInput`、`PanelDragGesture`、`QuickTransferRules` 无改动；`PanelDragInput` 只加了 `cancel()`；`QuickTransferInput/Gesture` 只加了 `Side.TRASH` 相关；`canPlace(` 只在 `BackpackClickPayload`（服务端）与 `FloatingPanelInput`（客户端预判）两处调用；`setScreen(` 与 020 一致（宿主管理器与宿主，没有新增）；协议版本只在 `BackpackNetwork` 一处，为 `"9"`；`grep p3pp3rf1y\|tiviacz\|mezz` 只命中 `compat/jei/` 与 `ContainerScreenCompat` 里原有的类名字符串匹配；`DependencyRulesTest` 通过 |

## 审阅（T044，读代码，没有改行为）

- ① `BackpackUpgradeRecipe` 升级时把 `current.trash()` 一起传给 `BackpackContents.fit`，保留。
- ② `BackpackContents.normalize/fit/copy` 都带 `trash`，容量变化不丢；`BackpackInventory.fromContents/toContents` 读写。
- ③ 读入旧存档：`trash` 用 `optionalFieldOf(…, TrashContents.EMPTY)`，缺字段为空。
- ④ 服务端非法请求路径：背包不在/不是右侧区有垃圾桶/校验失败 → 什么都不做，只补发垃圾桶内容与光标同步；`ADD` 光标为空、`TAKE` 光标非空或该格无组、`RESTORE` 下标全空或越界 → 忽略（`TrashBin` 对越界下标本身就跳过）；未知动作 → `validate` 为假。`ADD` 的体积保护拒绝时零变化并提示。**没有发现缺口。**
- ⑤ 使用菜单光标的 `ADD`/`TAKE` 都过 `ServerCompatGate.allows(USE_MENU_CURSOR, …)`；`RESTORE` 不碰菜单。
- 一处值得知道的行为：`ADD`/`TAKE` 走菜单分支时，合并面板光标进菜单会修改背包，所以即使后面被忽略也会先保存一次，避免物品复制。

## 客户端验证状态（宪法 V）

| 任务 | 内容 | 状态 |
|---|---|---|
| T020 | quickstart A、B（Q1～Q11） | **部分验证**：用户进游戏验证过基本功能没有问题，但没有逐项对照，**其余按未验证** |
| T028 | quickstart C、D、E、H 部分（Q12～Q14、Q16～Q23、Q41～Q42） | **部分验证**：用户验证过基本的点击/拖放；Q18/Q19（箱子、玩家物品栏上的拖放松开）、Q22/Q23（满 9 组与销毁日志）、Q41/Q42（嵌套数据与体积保护的实测上限）**未逐项验证** |
| T032 | Q15（拿回） | **用户已确认没有问题**（2026-11-05，口头确认，没有逐项明细） |
| T038 | Q24～Q29（Ctrl 恢复） | **用户已确认没有问题**（2026-11-05，口头确认，没有逐项明细） |
| T043 | Q30～Q35（限放 1 个） | **用户已确认没有问题**（2026-11-05，口头确认，没有逐项明细） |
| T045 | Q36～Q40、Q43～Q49（持久化、兼容、窗口缩窄、回归、联机） | **未验证** |

## 已知限制与未验证

- Windows / Linux 未验证；联机未验证。
- 体积保护的上限（`TrashContents.MAX_GROUP_ENCODED_BYTES` = 96 KiB）是按"9 组仍低于 1 MiB 的包上限"推算的初值，**没有用真实满载潜影盒或嵌套背包实测**。
- `TrashContents` 的编码层（往返、超限抛异常）无法单测，只能在客户端会话里验证，**未验证**。
- 原版容器界面上的拖放松开靠"先清光标、再放行"（见上文），读源码得出，**只有用户的基本验证，没有逐项实测**。
- `BackpackTrashPayload` 编译时有一条 "uses or overrides a deprecated API" 提示，没有定位到具体调用（不影响行为）。
- spec 里"体积保护"没有写成需求，只在 plan/research 里；拒绝时的聊天提示是新增行为。
