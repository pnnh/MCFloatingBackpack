# Delivery: 016-panel-tool-areas

**日期**: 2026-11-05

**状态**: 代码、单元测试、编译完成；用户随后在游戏里测试，"3 个区域正常绘制，功能没有什么问题"（T019、T026、T031，没有逐项记录）。**T033（对 013/014/011/012 的逐项回归、`runClientCompat`、JEI 避让、守恒与日志）没有被明确确认**，保持未验证；Windows/Linux 无法验证。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| 基线（改动前） | `./gradlew compileJava test` 退出码 0，22 个测试类；图集 2048×1024（估算显存 11.2MB）退出码 0 |
| `./gradlew compileJava test` | 退出码 0；25 个测试类全部通过（原 22 个 + 新增 `ToolAreaCapacityTest`、`PanelLayoutGeometryTest`、`AxisScrollTest`；另外改写了 `PanelLayoutTest`、`PanelSlotsResizeTest`、`FloatingPanelStateTitleIconTest`） |
| `ToolAreaCapacityTest`（13） | 左/右 = 等级默认行数（4/6/8/10/12，非整除向上取整），下 = 默认列数 + 2 = 11；`fit` 补空、保持前缀顺序、**缩容不丢物品**、随机 100 组守恒；`PanelArea` 字节往返；`ToolAreaRules.canPlace` 恒为真 |
| `PanelLayoutGeometryTest`（15） | 宽 = `2·PAD_X + (列+2)·18`、高 = `PAD_TOP + (行+1)·18 + PAD_BOTTOM`；最小 5×4 即 104×96；四区铺满且没有空角；各区可见格数；命中与各区滚动偏移；下区延伸到左右区正下方；填充格不可命中；各区最大偏移；箭头可见性；格子位置 |
| `AxisScrollTest`（5） | 夹取、放得下不滚、`by`、两侧箭头条件、总数缩小后无空白 |
| 改写的布局测试 | 新增：最小 5×4、缩放只改主区域行列（工具区厚度不变）、左右列与下行从屏幕预算里扣除、`PanelSlots.indexAt` 只答主区域 |
| 图集预算（改动后） | 2048×1024、估算显存 11.2MB、退出码 0（度量时没有客户端在运行；本 feature 不新增资源） |

## 静态检查（T032）

- `git diff --stat`：`QuickTransfer*`、`PanelDragInput`、`PanelDragGesture`、`PanelSelectionInput`、`SelectionGesture`、`PanelSelection`、`BackpackQuickTransferPayload`、`BackpackTakeFromContainerPayload`、`BackpackCursorGesturePayload`、`PanelCursor*` **一行未改**。
- `indexAt` 的使用者仍是 `FloatingPanelInput`、`PanelDragInput`、`PanelSelectionInput`、`QuickTransferInput`，都经只返回主区域下标的 `PanelSlots.indexAt`，所以 011～014 的手势看不到工具区。
- 新增文件没有第三方类型（宪法 IV）。
- `FloatingBackpackJeiPlugin` 读 `FloatingPanelState` 的位置与宽高，面板变大后自动包含工具区，没有改。

## 与 plan/tasks 的偏离

- `ToolAreaCapacity.fit` 另有一个带 `Predicate` 的重载（`ItemStack` 没有可用的 `equals`）。
- `FloatingPanelState.layoutModel(counts)` 由调用方传入各区格子数，状态类保持纯类；`ClientBackpackView.layout()` 负责拼装。
- `ClientBackpackView` 把各工具区格子数报告给 `FloatingPanelState`，用于夹取滚动，格子数变化时该区复位。
- 填充格（容量之外的可见位置、主区域最后一行的空位）**不绘制**，保持面板底色。

## 协议与存储

- 协议版本 `"6"` → `"7"`：全量同步、会话、槽位更新、点击包与物品组件编码都变了；011～014 的包未变。
- 数据组件新增可选字段 `tools`（`left/right/bottom` 均可选）；旧存档读成空，主格子不变。
- 升级配方用三参数 `fit`，工具区内容保留。

## 验证情况

- 已由用户确认：T019、T026、T031。
- 仍未确认：T033（下面列出）。

## 尚未验证（需要你在游戏里做；T019/T026/T031 已确认，保留作记录）

按 `quickstart.md`，HUD（没开界面）与打开着界面各做一遍：

- **T019 布局与外观（US1，最重要）**：quickstart 一（1–7）——四块区域的位置、没有空角、四种底色互相可辨、没有滚动条、缩放只改主区域、缩到最小是 5×4。**重点看缩放手柄落在下区最后一格上的手感。**
- **T026 滚动与箭头（US2）**：quickstart 二（8–14）——各区滚轮方向、箭头的出现与消失、箭头的大小/颜色/位置是否醒目且不挡视线、下区滚轮方向的直觉、主区域缩列时左右区的填充格。
- **T031 存放与保存（US3）**：quickstart 三（15–20）——放入/取出/合并/交换、Alt 主手交换（要用副手打开面板才能构造）、关闭重开、退出重进、放进箱子再取出、锻造台升级、旧存档。
- **T033 不干扰现有操作（US4）**：quickstart 四、五、七（21–28、31）——重跑 013/014/011/012、Ctrl 转移只进出主区域、工具区里 Shift/Ctrl/拖动没有手势、JEI 避让、守恒与日志、`runClientCompat`。

## 已知限制 / 不确定

- 小扁箭头的形状与位置（`drawTriangle` 的 1 像素行叠法）和四种底色的可辨性，我**没有看过实际画面**，常量在 `FloatingPanelRenderer` 顶部，调起来很容易。
- 填充格是 spec 没有明确覆盖的边界处理（主区域缩列后左右区的可见格数可以超过它的容量），建议补进 spec 的 Edge Cases。
- 旧配置文件里的行列偏好现在对应更大的面板（多 2 列 1 行），小屏幕下靠 `layout()` 与 `clampToScreen` 兜底，没有实测。
- `ToolAreaRules.canPlace` 本阶段恒为真，只是留给将来的钩子。
- Windows/Linux 无法验证。

## 行为变化（需要你确认能否接受）

- 面板不再有滚动条：改为滚轮 + 小扁箭头；**不能再用鼠标拖动滚动条**。
- 面板最小尺寸 3×3 → 5 列 × 4 行；旧的行列偏好会让面板变大。
- 工具区里 Shift/Ctrl 点击被忽略，拖动没有手势，只做单击/右键/Alt。

## 2026-11-06：用户人工验证（清单 E–H 组）

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选并备注；清单只记录通过与否和少量备注，没有逐项数据。没勾的项仍是**未验证**。

- 用户确认通过：016-19（锻造台升级后工具区保留、容量变为新等级的）、016-22（Command 快速转移只进出主区域、工具区无变化）、016-23（工具区里 Shift/Command 点击与拖过多格没有手势）、016-24（主区域有选区时点工具区格子选区不变）、016-25（标题栏、关闭按钮、整体拖动、ESC 可用）、016-26（JEI 物品列表避开整个面板）、016-27（20 次以上工具区操作后同种物品总数守恒）。
- **T033 仍保持未勾选**：对 013/014/011/012 的统一回归（K 组）、`runClientCompat`（016-31）尚未做。

## 2026-11-07：统一回归与守恒压力

- **通过**：REG-1～REG-5（011/012/013/014 重跑与 016/017 的布局、滚动、拖动缩放只停在合法尺寸）；CONS-1、CONS-2（含工具区放入取出，前后物品总数相等，日志无新增报错）。
- **仍未验证**：016-31（`runClientCompat`：三个工具区在精妙/旅行者背包界面下的布局、滚动、存放，转移仍只进出主区域）→ **T033 保持未勾选**。
