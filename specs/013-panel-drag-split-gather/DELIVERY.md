# Delivery: 013-panel-drag-split-gather

**日期**: 2026-11-05

**状态**: 代码、单元测试、编译完成；用户随后在游戏里验证了拖动均分、拖动收集、右键拖动、双击收集（T016、T023、T027、T031，结论是"功能没有问题"，没有逐项记录）。**T033（不干扰现有操作与 011/012 回归）、T034（中途取消、守恒、日志）、T038（启动检查）没有被明确确认**，保持未验证。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| `./gradlew compileJava test` | 退出码 0；20 个测试类全部通过（原 17 个 + 新增 `PanelCursorRequestTest`、`PanelCursorLogicTest`、`PanelDragGestureTest`） |
| `PanelCursorRequestTest`（6 个用例） | 请求校验：未知模式、空/null、越界、负数、重复、超过背包容量、`GATHER_ALL` 必须恰好 1 项 |
| `PanelCursorLogicTest`（18 个用例） | 用假栈跑真实实现：原版均分的各条规则（左键 floor、右键 1、单格、已满的同种堆叠也算有效格子、结算要求 光标数量 ≥ 格子数、每格不超过上限、黄色上限标记）、单格收集、`pickupAll`（先未满后满），以及随机布局下总数守恒 |
| `PanelDragGestureTest`（16 个用例） | 状态机：按下延后结算、空光标按下不接管并进入收集、结局（分配/单格点击/无有效格子点击/无）、右键、已访问/无效格子不重复选、快速移动不漏格（线段采样）、数量不足时不再加格子、预览（单格无预览）、取消、收集只收新的同种格、双击窗口（250 ms 边界、换格、换键） |

## 静态检查（T032）

- `grep -n "hasShiftDown|KEY_LEFT_SHIFT|KEY_RIGHT_SHIFT"` 在 013 新增文件上：只有 `PanelDragInput.java` 的两处（开始前让路、拖动中取消），没有任何地方用 Shift 触发行为。
- `grep -n "p3pp3rf1y|tiviacz"` 在 013 新增文件上无命中（宪法 IV）。
- `git diff --stat` 对 `QuickTransferInput.java`、`QuickTransferGesture.java`、`QuickTransferRules.java` 为空：011/012 的客户端手势文件没有被改动。013 对现有文件的改动：`FloatingPanelInput`（接入入口与松开、`sendClick` 改为包内可见）、`FloatingPanelEvents`（每帧驱动、重置、取消检查）、`FloatingPanelRenderer`（预览、`cursorStack` 改为包内可见）、`BackpackNetwork`（注册新包，协议 `"5"` → `"6"`）。

## 与 plan/tasks 的偏离

- `PanelCursorRequest` 单列成纯逻辑类（见 T003 的说明）。
- 服务端 `DISTRIBUTE_*` 只按 `canQuickReplace` 过滤格子；"光标数量 > 已选格子数"由结算时的 `光标数量 ≥ 格子数` 保证（与原版结算一致），客户端选格子时才用 `isValidDragSlot` 的完整条件。
- 服务端 `GATHER_ALL` 不检查被双击的格子是否为空（客户端只在"第二次按下时光标非空"才发它）。
- `PanelDragInput` 的入口命名为 `beginFromPanel`（与 `QuickTransferInput` 一致）；让路与取消条件在 `PanelDragInput` 里，**没有单元测试**（依赖按键与界面状态）。
- T001（改动前基线）没有单独跑。

## 行为变化（需要你确认能否接受）

- **拿着物品在面板里单击放下/交换，现在是松开鼠标时才生效**（原来是按下就生效）。这是拖动均分的前提：按下时还不知道是单击还是拖动。没有拿着物品时的单击拿起仍然是按下即生效。

## 验证情况

- 已由用户确认：T016、T023、T027、T031。
- 仍未确认：T033、T034、T038（下面列出）。

## 尚未验证（需要你在游戏里做；T016/T023/T027/T031 已确认，保留作记录）

按 `quickstart.md`，HUD（没开界面）与打开着界面（`runClient`；精妙/旅行者背包用 `runClientCompat`）各做一遍：

- **T016 拖动均分（US1，最重要）**：quickstart 一（1–8）——60 个圆石滑过 4 个空格各 15；10 个滑过 3 格各 3 余 1；已有 60 的格子只多 4；拖动中预览（每格数量、光标剩余、到上限黄色）；只滑过一个格子等于普通点击；**重点确认"松开才放下"的手感**。
- **T023 拖动收集（US2）**：quickstart 三（10–14）——20/30/40 三格圆石，拿起 20 后滑过后两格，光标 20→50→64，第三格剩 26；不同种/空格被忽略；光标满后不再收。
- **T027 右键拖动（US3）**：quickstart 二（9）——拿起 10 个，按住右键滑过五个空格，每格 1 个，光标剩 5。
- **T031 双击收集（US4）**：quickstart 四（15），并留意双击与"点击后马上拖动"的区分手感。
- **T033 不干扰现有操作（US5）**：quickstart 五（16–22）——单击放下/交换/拿起整组/拿起一半/右键放一个；按着 Command 或 Shift 不触发；精妙背包、旅行者背包、箱子、玩家物品栏自己的格子里的拖动不受影响；重跑 011/012 的 quickstart。
- **T034**：quickstart 六（23–24）——拖动中途按 E/ESC 或隐藏面板；50 次以上拖动后物品总数不变；`run/logs/latest.log` 没有新增报错。
- **T038 游戏启动**：协议版本 6 下能进入世界、日志无报错、图集仍 2048×1024（`check_atlas_budget.sh`，跑之前先确认没有正在运行的客户端）。

## 已知限制

- `screenOpen` 是客户端自报（沿用 `BackpackClickPayload` 的设计）：谎报只会让光标落在另一个来源上，服务端仍会校验背包和每个格子。
- 没有客户端预测：拖动结算要等服务端回包后光标/格子才更新。
- 不做：玩家物品栏与箱子等菜单自己的格子（原版已有）、跨面板与菜单格子的混合拖动、创造模式中键拖动、触摸屏模式、Shift+双击。
- 双击的第二次按下如果因延迟在客户端光标还没同步时就到了，会被当作普通点击（放回去）。
- Windows/Linux 只能按原版的 Ctrl 判定推断，**未验证**。
