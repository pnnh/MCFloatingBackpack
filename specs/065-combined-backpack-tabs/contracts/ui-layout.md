# 契约：联合界面的几何与输入

> **066 更新（2026-11-24）**：联合标题栏已取消（用户判断作用不大）；标签栏直接贴在面板上沿（`BARS_H = TAB_H = 16`），最前加了"全部"标签，"+n"未连接徽标画在"全部"标签里。下文关于标题栏的几何描述作废，见 `specs/066-combined-all-tab/contracts/client-view.md`。


## 几何（GUI 像素；面板 = 现有浮动面板，位置 `posX, posY`，宽 `W`）

```text
y = posY - BARS_H            ┌ 联合标题栏  高 TITLE_H = 14 ┐   宽 = W，左对齐 posX
y = posY - TAB_H             ├ 标签栏      高 TAB_H   = 16 ┤
y = posY                     └ 现有面板（拖动条、标题栏……）┘
BARS_H = TITLE_H + TAB_H = 30
```

- 联合标题栏：左 12×12 联合背包图标（物品图标）、标题文字"联合背包"（档位名按需追加）、右侧小字显示未连接提示摘要（见下）。
- 标签栏：左右各一个 10 px 滚动箭头（只在内容溢出时显示）；中间标签区。标签宽 = `色块 6 + 图标 12 + 文字（铁砧名截断或 "#"+4 位编号）+ 内边距`，总宽超过标签区则横向滚动（滚轮在标签栏上滚动、箭头点击步进 = 一个标签宽）。当前标签加粗底色，加载中（`loading`）的标签栏整体变暗且不响应点击。
- 色块：**按成员在标签栏里的位置**从 16 色表取色（066 的"全部"标签共用同一张表）。颜色表放 `CombinedColors`（纯），**要在深色背景上可读，且与灰格、搜索高亮不冲突——由用户真机确认**。
- 悬停提示：位置（"胸甲" / "Curios 专用槽" / "Accessories 专用槽" / "副手" / "物品栏第 n 格"）、容量、是否 B 激活（"按 B 会打开这只"）。
- 未连接提示：任一计数 > 0 时，联合标题栏右侧显示"+n"徽标，悬停列出三类原因（档位过高 / 版本过新 / 超过 16 只）。

## 位置钳制与宿主

- `FloatingPanelState.clampToScreen` 加参数 `topReserve`：联合模式下 `= BARS_H`，保证 `posY >= BARS_H`；非联合模式 `= 0`（现有行为与测试不变）。进入联合模式、切换标签、分辨率变化后都钳制一次。
- 宿主界面（019）无需新的 Screen；`PanelHostScreen` 的按键路由通过 `HostKeyRouter.classify(names, toggleName)` 认 TOGGLE，**新增第二个参数 `combinedName`**（`HostKeyRouter.classify(names, toggle, combined)`：命中 `combined` 也返回 `KeyRole.TOGGLE`，由 `FloatingPanelEvents` 的键处理按键映射再区分 B/U）。
- JEI 排除区多一个矩形 `(posX, posY - BARS_H, W, BARS_H)`（只在联合模式可见时）。

## 输入（`CombinedBarInput`，在面板命中检测之前认领；先例：`TrashPanelInput`）

- 在联合标题栏/标签栏矩形内的鼠标按下、释放、滚轮**全部认领**，不会落到下层屏幕或面板手势：
  - 左键点标签 → 若光标非空（面板光标或菜单携带物）：不发包，动作栏 `…combined.empty_cursor`；否则 `loading = true` 并发 `CombinedSelectPayload(SELECT, i, identity)`。点当前标签：无操作。
  - 点箭头、滚轮 → 改 `tabScroll`，钳在 `[0, maxScroll]`。
  - 右键、中键、标题栏其它位置：认领但无操作。
- `loading` 期间**所有面板请求都不发**：`FloatingPanelInput.sendClick`、快速转移、拖动分摊、垃圾桶、整理、标签……统一问 `ClientCombinedState.blocksRequests()`（`active && loading`），为真就直接返回。
- 键：`FloatingBackpackKeys.COMBINED`（默认 U，`KeyConflictContext.UNIVERSAL`）。按下：面板可见且联合模式 → 关闭面板（`closePanel`）；否则若本机没穿联合背包（`WornCombined` 客户端同样可算）→ 动作栏 `need_worn`；否则发 `CombinedSelectPayload(OPEN)`。B 键的行为一字不改（面板可见时关闭；否则按 手→穿戴→物品栏 打开）。

## 唱片机 / 工作台 / 熔炉控件

- 右区三个移动工具的点击入口（`FloatingPanelInput` 里 053/054/055 三段）与唱片机页（`JukeboxPanelState.toggle`）：联合模式且当前标签成员**不是** B 激活的那只（`normalized(resolve) != 成员 ref`）时，不响应，改发动作栏 `tools_active_only`；工具图标在格子里画灰（沿用 `GreyCellRules` 的灰化风格）。
- 客户端每帧只在联合界面可见时比较（R7）。播放、自动拾取、服务端工具打开一律不变。
