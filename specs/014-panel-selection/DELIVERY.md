# Delivery: 014-panel-selection

**日期**: 2026-11-05

**状态**: 代码、单元测试、编译完成；用户随后在游戏里验证了选区功能（T010、T015、T019，结论是"运行正常"，没有逐项记录）和 Alt+点击切换主手（T025 的一部分，"没有问题"）。**T025 的其余项（Shift+点击不交换、Shift+Alt、Ctrl+Shift/Shift+右键被忽略、重跑 013 与 011/012、守恒与日志、`runClientCompat`）和 T028（启动检查）没有被明确确认**，保持未验证。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| `./gradlew compileJava test` | 退出码 0；22 个测试类全部通过（原 20 个 + 新增 `PanelSelectionTest`、`SelectionGestureTest`）；改动前基线同样退出码 0、20 个测试类 |
| `PanelSelectionTest`（11 个用例） | 切换、`addAll` 只加不减、越界下标不加入、空格子可选、`snapshot` 升序且是拷贝、`clear`、`syncContext`（面板隐藏/背包变化/容量变化清空，不变则保留，隐藏后重新开始） |
| `SelectionGestureTest`（19 个用例） | 选择：按下不改选区；停在起点内松开=切换；起点内的小幅移动仍是点击；拖动加入起点与滑过的格子；拖动不会切掉已选的起点、只加不减；空格子也加入；一帧快速移动不漏格；来回滑过只算一次；面板外不加入；取消保留已加入的、丢弃未结算的点击。批量：只在"已选格子 + 光标为空"时开始；松开时返回升序且有物品的选中格子；全空则不发；Ctrl 已松/有 Shift/面板隐藏/换了界面/光标非空任一成立都取消；批量期间移动不改任何东西；选区不被批量改变；视野外的下标仍参与 |

## 静态检查

- **T017**：选区只被输入类、手势类、渲染器和 `FloatingPanelEvents` 引用；选区相关三个类里没有滚动/缩放/`isRowVisible` 的调用；批量转出的格子来自 `PanelSelection.snapshot()`，不按可见行过滤。
- **T023**：Shift 只出现在 `PanelSelectionInput`（触发选择、吞掉其余 Shift 组合、批量提交时检查）与 011/013 既有的让路判断里；Alt 只出现在 `FloatingPanelInput.sendClick` 和 `PanelDragInput` 的让路判断里；新增文件没有第三方类型；`QuickTransferInput/Gesture/Rules`（011/012 客户端手势）`git diff` 为空。
- 协议版本仍为 `"6"`：`BackpackClickPayload` 只把字段 `shiftDown` 改名为 `swapHand`，写入/读取的位不变；没有新网络包。

## 对现有文件的改动

`BackpackClickPayload`（字段改名）、`FloatingPanelInput`（选区入口与松开；`sendClick` 的标志位改读 Alt，去掉不再用的导入）、`FloatingPanelEvents`（每帧驱动、重置、`syncSelectionContext` 每 tick 同步上下文）、`FloatingPanelRenderer`（金色内边框 `COLOR_SELECTED`）、`PanelDragInput`（没有打开界面且按着 Alt 时让路）。

## 与 plan/tasks 的偏离

- `PanelSelection` 的构造函数是包内可见（测试用），运行时用 `PanelSelection.get()` 单例。
- 选区手势与 013 各自有一份线段采样和命中函数（未抽取，plan 已登记为后续整理项）。
- 没有为 `PanelSelectionInput`、渲染、事件钩子写单元测试（依赖游戏状态），只能在游戏里验证。

## 行为变化（需要你确认能否接受）

- **Shift 不再触发 HUD 下的主手交换**，改为 **Alt（macOS Option）+点击**；打开着界面时 Alt+点击与普通点击一致。
- **Shift+右键、Ctrl+Shift 点击被忽略**（不拿起、不放下、不转移）。
- **不按 Shift/Ctrl 的面板格子点击会清空选区**（包括右键和 Alt+点击）。
- **Ctrl 批量转出是松开鼠标时才发送**；松开前先放开 Ctrl 则取消。

## 验证情况

- 已由用户确认：T010、T015、T019；T025 中的 Alt+点击切换主手。
- 仍未确认：T025 的其余项、T028（下面列出）。

## 尚未验证（需要你在游戏里做；T010/T015/T019 已确认，保留作记录）

按 `quickstart.md`，HUD（没开界面）与打开着界面各做一遍：

- **T010 选择（US1）**：quickstart 一（1–7）——Shift 单击切换、拖过一行（含空格）、从已选格拖动不取消、不按 Shift 点击清空、光标拿着物品时 Shift 单击只改选区；**重点确认金色内边框的辨识度**（与悬停、013 预览的区别）。
- **T015 批量转出（US2）**：quickstart 二（8–15）——批量转到玩家物品栏/箱子、目标只能放下一部分时按下标顺序、放不下时选区不变、Ctrl 点击未选格子行为不变、松开前先放开 Ctrl 取消、移动不额外转移、光标非空不触发；**留意"松开才发送"的手感**。
- **T019 生命周期（US3）**：quickstart 三（16–21）——滚动/缩放后保持、关闭重开清空、HUD↔界面保持、换背包清空。
- **T025 Alt 改键与回归（US4）**：quickstart 四、五、六（22–33）——Alt+点击主手交换（需要用副手打开面板才能构造）、Shift+点击不交换、Shift+Alt、Ctrl+Shift/Shift+右键被忽略、重跑 013 与 011/012、守恒与日志、`runClientCompat` 下的两个模组。
- **T028 游戏启动**：协议版本 6 下能进入世界、日志无报错。

## 已知限制

- 选区只在客户端；同一栏位里把背包换成另一只同容量背包时选区不会被清空（`syncContext` 只比较背包栏位与容量）。
- Alt+点击在 Windows/Linux 可能被窗口管理器占用（拖动窗口），本机（macOS）无法验证。
- Windows/Linux 的 Ctrl 判定只能按原版规则推断，未验证。
- Option+点击在 macOS 上是否稳定报告 Alt 键状态，未验证。
- 不做：选区的其他操作、全选/反选、持久化、服务端选区、容器 → 面板的批量转移。

## 2026-11-06：Claude 代跑的自动项

- **T028 的自动部分**：`./gradlew compileJava test --rerun-tasks` 退出码 0（BUILD SUCCESSFUL）：61 个测试类、468 个用例、0 失败、0 跳过（测试编译只有 3 条 `Dist.CLIENT` 注解警告，测试类路径没有 NeoForge 所致）。
- **T028 的启动部分**：这次启动只到创建图集（主菜单之前），日志 65 行，`ERROR` / `Exception` 0 行；共 10 条 WARN，全部来自原版或开发环境（命令歧义 5 条、`Assets URL … uses unexpected schema` 2 条、缺少山羊角音效 2 条、`rendertype_entity_translucent_emissive` 着色器 1 条），没有本模组的 WARN 或 ERROR。**没有进入世界**，所以"进入世界无报错"与"游戏内日志无新增报错"仍未验证。因此 T028 **保持未勾选**。

## 2026-11-06：用户人工验证（清单 A–D 组）

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选并备注；清单只记录通过与否和少量备注，没有逐项数据。没勾的项仍是**未验证**。

- 启动检查（T028）：用户确认能进入世界且无报错（BOOT-1）。**T028 勾选**，spec Status 已改为 Implemented，并如实写明未验证的部分。
- 014 的 E 组（014-23～27）及其余回归本次**未做**，T025 保持未勾选。

## 2026-11-06：用户人工验证（清单 E–H 组）

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选并备注；清单只记录通过与否和少量备注，没有逐项数据。没勾的项仍是**未验证**。

- 四（22–27）：Alt+点击切换主手（014-22，上一轮已记）之外，014-23（Shift+点击不换主手）、014-24（光标拿着物品时 Alt+点击立即交换）、014-25（打开着界面时 Alt+点击与普通点击一致）、014-26（Shift+Alt 按选区处理）、014-27（Ctrl+Shift、Shift+右键被忽略、选区不变）**用户确认通过**。macOS 上 Alt 即 Option；Windows/Linux 的 Alt 是否被窗口管理器占用**仍未验证**。
- **T025 仍保持未勾选**：重跑 013 与 011/012（统一回归 K）、五（30–31）守恒与日志（L 组）、六（32–33）`runClientCompat` 下精妙背包与旅行者背包（会话 S2）尚未做。

## 2026-11-07：统一回归与守恒压力

- **通过**：REG-1～REG-4（011/012/013 与 014 的 Shift 选区、Ctrl 批量转出、Alt+点击，宿主界面与打开着界面各扫一遍）；CONS-1、CONS-2（守恒与日志）。
- **仍未验证**：六（32–33）即 014-32（精妙/旅行者背包界面里重做批量转出，选区不变）、014-33（它们自己格子里的 Shift/Ctrl 点击行为不变）→ **T025 保持未勾选**。
