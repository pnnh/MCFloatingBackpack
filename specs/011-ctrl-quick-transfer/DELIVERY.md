# Delivery: 011-ctrl-quick-transfer

**日期**: 2026-11-04

**状态**: 实现完成，**验证大部分完成**（用户 2026-11-04 在游戏里验证了 Ctrl+点击、Ctrl+拖动、Shift 回归、第三方界面共存）。未验证的项见下面"仍未验证"，没有被当作通过。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| `./gradlew compileJava test` | 退出码 0；15 个测试类全部通过（原 11 个 + 新增 4 个） |
| 新增测试 | `QuickTransferLogicTest`（9）、`QuickTransferRequestTest`（6）、`QuickTransferRulesTest`（4）、`QuickTransferGestureTest`（7） |
| 搬运算法的总数守恒 | 随机 100 组布局，转移前后同种物品总数相等、没有槽位超过上限（`QuickTransferLogicTest.totalIsConservedAndLimitsHoldOnRandomLayouts`）；测的是生产代码本身（泛型 `StackOps`），不是镜像 |
| 客户端启动 | 协议版本 4、新网络包注册后能启动到创建图集，日志无报错；图集仍 2048×1024（`check_atlas_budget.sh`） |
| Shift 没有被读取（T026） | `grep -n "hasShiftDown\|KEY_LEFT_SHIFT\|KEY_RIGHT_SHIFT"` 在 011 新增文件里**没有任何命中**；整个源码树里只剩 `FloatingPanelInput.sendClick` 里**原有**的一处 Shift 检测，没有改动 |
| 面板隐藏时 Ctrl 手势不生效（T027） | 读代码确认：面板侧入口在 `FloatingPanelInput.onMouseButton` 之后（它先检查面板可见）；屏幕侧入口 `beginFromScreen` 先检查面板可见；拖动在 `invalid()` 里检查面板可见。**没有单独的测试**，只靠读代码和后续人工验证 |

## 与计划的偏差

- 请求包里**去掉了** `sourceOrdinal`/`invIndex`：服务端本来就以 `BackpackRef.resolve` 的实时结果为准，声明的来源没有信息量。contract、data-model、tasks 已同步。
- 测试和实现是**同时写的**，没有先看到测试失败；单元测试都通过，但"先红后绿"这一步没有做。
- US1/US2 的单击没有先做成"直接发包"再改成手势，而是一开始就走 `QuickTransferGesture`（T022 的目标形态），所以没有出现 T022 要处理的返工；但 T025 仍要求重跑 quickstart 一，因为单击和拖动共用一条路径。

## 人工验证结果（用户 2026-11-04，macOS Command 键，`runClient` / `runClientCompat`）

> 来源：用户在本文件里逐项写"没问题"。只记录通过与否，**没有记录具体观察或数据**。

- **T014** quickstart 一.1、2、5、7 与三.3～三.6：物品栏 → 背包的 Ctrl+点击，以及箱子格子、创造模式界面、盔甲/副手/合成槽不触发。
    quickstart 一.1 没问题
    quickstart 一.2 没问题
    quickstart 一.5 没问题
    quickstart 一.7 没问题

- **T019** quickstart 一.3、4、6：背包 → 物品栏，含没开界面的 HUD。
    quickstart 一.3 没问题
    quickstart 一.4 没问题
    quickstart 一.6 没问题
- **T025** quickstart 二：Ctrl+拖动，含松开 Ctrl/鼠标即停、方向锁定、36 格来回快速滑动无断线；以及重跑一。
    quickstart 二 没问题

- **T028** quickstart 三.1、2、4、7、7a、8：Shift 手势不变、面板隐藏时一致、`runClientCompat` 下精妙/旅行者界面不被拦截且面板侧仍可转出、重叠命中规则。
    quickstart 三.1 没问题
    quickstart 三.2 没问题
    quickstart 三.4 没问题
    quickstart 三.7 没问题
    quickstart 三.7a 没问题
    quickstart 三.8 没问题


- **T032** quickstart 四与五：50 次以上转移前后总数相等、重进存档一致、macOS Command 键两种手势可用。
    测试成本偏高未完整测试，不过多次转移操作后数量没有问题

## 仍未验证（如实记录）

- **quickstart 三.3、三.5、三.6**：Command+点击箱子自己的格子、创造模式物品栏、盔甲/副手/合成槽不触发转移。T014 把这几项列在内，但用户只记录了一.1、2、5、7，所以这几项**没有验证记录**。这些排除规则有单元测试（`QuickTransferRulesTest`）和读代码为证，但没有在游戏里看过。
- **quickstart 四（T032）**：50 次以上转移前后总数精确相等的计数、转移后重进存档内容一致，没有完整做（用户：测试成本偏高，只做了多次转移，数量没有问题）。SC-003 因此只算"部分验证"。
- **quickstart 一.7a / 三.7a** 用户记为通过，但没有说明是在 `runClientCompat` 的精妙背包还是旅行者背包里试的，也没有分别记录。

## 无法验证（如实记录）

- **Windows/Linux 的 Ctrl 键**：本机是 macOS，只能按 `Screen.hasControlDown()` 的原版判定推断。
- **局域网高延迟下的拖动表现**：没有测。

## 已知限制（第一版范围）

- 只涉及玩家自己的 36 格；箱子等外部容器自身的格子不参与转移。
- 只在原版界面上识别"物品栏一侧"的点击（类名白名单）；继承原版界面类的模组界面也被排除。
- 不做 Shift+双击；不改任何 Shift 手势。
- 服务端没有"面板是否打开"的状态，所以不检查它（现有 `BackpackClickPayload` 的 `panelOpen` 也只是客户端自报）；能校验的是"背包仍然可用"。
