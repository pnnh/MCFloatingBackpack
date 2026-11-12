# 045 面板格子的物品提示（tooltip）：交付说明

**状态**：完成（2026-11-12）。代码、自动化测试、文档完成；游戏内验证做了 AI 自动验证（容器，见下）；**用户已确认 T012（没有问题）**。

## 结论：不是回归

查了源码和整个 git 历史（M1 至今）：面板的渲染从来没有画过格子里物品的 tooltip。原版界面只给它自己的 `Slot` 画 tooltip，面板的格子是自己画、自己命中判断的，所以"滑过原版物品栏有提示、滑过面板没有"一直如此。

## 基线与结果

- 基线（T001）：167 个类、1248 个用例，全部通过。
- 完成后：**169 个类、1260 个用例，全部通过**（新增 `ItemTooltipRulesTest` 7 个、`ItemTooltipWiringTest` 5 个）。011～044 的测试不改。
- 没有动服务端、网络包、协议号（仍 17，源码文本检查断言）、配置、语言键、依赖、贴图。

## 改动

- 新增 `ItemTooltipRules`（纯逻辑）：`shouldShow(area, mainCovered, cursorHasItem, dragPreview, dialogPending, cellHasItem)`。
- `FloatingPanelRenderer.drawItemTooltip`：`layout().hit(...)` 取鼠标下的真实槽位（搜索时按映射，填充格为 null）；主区域 `getStackInSlot`、工具区 `getToolStack`；规则通过后调用原版 `graphics.renderTooltip(font, stack, x, y)`。渲染里改为 `if (!drawItemTooltip(...)) drawRowTooltip(...)`：有物品弹物品 tooltip，空的扩展行格子与拿着东西时仍是 037 的行提示（`drawRowTooltip` 一行没改）。
- README 两份各加一句。

## 游戏内验证（AI 自动验证，2026-11-12，容器 `mcfb-c1`，创造模式世界）

| 场景 | 结果 |
|---|---|
| B1 主区域悬停 | **通过**：铁镐显示 "Iron Pickaxe" 与攻击属性；钻石镐同理；面板挂在透明宿主界面上，tooltip 画在面板之上、没有撕裂 |
| B2 三个工具区悬停 | **通过**：垃圾桶 "Trash Can"、标签管理器 "Tag Manager"、自动拾取 "Auto Pickup"、食物扩展 "Food Extension" 加 036 的类别说明行 |
| B3 拿着物品悬停 | **通过**：光标拿着镐悬停原木，不弹 |
| B4 标签面板盖住主区域 | **通过**：悬停被盖住的主区域不弹；悬停仍露出的工具区（食物扩展）照常弹 |
| B5 搜索时 | **通过**：搜索 `diamond` 后悬停结果格弹出 "Excalibur"（改过名的钻石剑）；悬停空格不弹 |
| B6 扩展行 | **通过**：食物行里有面包的格子弹 "Bread"（不弹行提示）；食物行的空格仍弹 "food row: only takes food items" |
| B7 F3+H | **通过**：tooltip 带注册名与 "9 component(s)" |
| B8 两种界面 | **通过**：透明宿主界面（B1）与创造物品栏界面（悬停原木，tooltip 在面板之上、完整可见） |
| B9 拖拽预览与确认弹窗 | **未在游戏里验证**：只有 `ItemTooltipRulesTest` 与源码文本检查证据 |

没做的：拖拽预览与确认弹窗的游戏内验证、物品栏界面里本模组之外的 tooltip 冲突、真机与 Windows/Linux。

## 已知限制

- 只覆盖主区域与三个工具区；垃圾桶面板、标签面板、标签总览、拾取黑名单页里的物品没有 tooltip（不在本 feature 范围）。
- 内容完全沿用原版 `renderTooltip(Font, ItemStack, …)`，别的模组经 `ItemTooltipEvent` 加的行会自然出现，本模组不控制。
- tooltip 立即弹出，没有延迟或开关（与原版一致）。

## 宪法复查

I Overlay：画在同一条面板渲染路径里，不新增 Screen。II：只读客户端已有的格子内容，不发请求。III：只做物品 tooltip，搜索灰格另做 046。IV：用原版入口，不引用第三方类型。V：已在客户端会话里验证（未验证项如实列出）。VI：无贴图、无缓存。VII：无新依赖。**结论：全部通过，宪法不需要修订。**
