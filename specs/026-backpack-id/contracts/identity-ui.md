# Contract: 标题栏编号与物品提示

## 1. 标题栏

- `FloatingPanelRenderer.drawTitleIcon` 画完 16×16 的等级图标后，在其右侧 `3` 像素处画编号文字（`panelX + 4 + 16 + 3`，垂直与图标居中），颜色同面板文字。
- 编号来自 `ClientBackpackView.getActiveRef()` 对应的客户端栈上的标识算出的 `BackpackIdentity.id()`；**不信任也不使用客户端自己生成的任何编号**。栈没有标识（还没被使用过）不画。
- 可用宽度 = 关闭按钮左缘 − 起点 − 2（`TitleIdLayout`，纯几何）；文字超出则 `font.plainSubstrByWidth` 截断；**不得遮挡关闭按钮**。
- 悬停该文字区域（`FloatingPanelState.hitsTitleId(x, y)`，几何来自 `TitleIdLayout`）显示完整编号的提示（`panel.mcfloatingbackpack.id.tooltip`："背包编号 %s" / "Backpack ID %s"）。
- 图标悬停显示等级名称的行为（008）不变；两个悬停区互不重叠。

## 2. 物品提示

`FloatingBackpackItem.appendHoverText`：

- 有标识：灰色一行 `tooltip.mcfloatingbackpack.id`（"编号：%s" / "ID: %s"）；
- 没有标识：灰色一行 `tooltip.mcfloatingbackpack.id.none`（"未登记（首次使用时登记）" / "Unregistered (registered on first use)"）。

## 3. 其它界面

不改任何其它界面；本 feature 没有命令、没有新面板、没有新贴图。
