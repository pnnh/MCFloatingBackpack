# Contract: 界面（几何、色值、文字）

## 1. 几何

| 项 | 规则 |
|---|---|
| 格子定位 | 不变，沿用 `PanelLayout`（`SLOT = 18`） |
| 底色 | 在原有 `drawSlotWell` 之后，对 `groupOf(index) >= 0` 的格子 `fill(sx+1, sy+1, sx+17, sy+17, fill)`（留出槽的亮暗边） |
| 分隔线 | `topEdge`：`fill(sx, sy, sx+18, sy+1, edge)`；`leftEdge`：`fill(sx, sy, sx+1, sy+18, edge)`；1 像素；画在底色之后、物品之前 |
| 行首角标 | `isRowStart`：`fill(sx+1, sy+1, sx+4, sy+4, edge)`，3×3 |
| 幽灵图标 | 格子是空的（且没有拖拽预览要画）时，在 `(sx+1, sy+1)` 画 16×16、透明度 0.16 的类别物品图（初稿 0.30，用户看过后嫌醒目，2026-11-10 调淡） |
| 放不进的高亮 | 光标上有物品、悬停格是扩展行格子且 `!canPlace` → 悬停高亮 `COLOR_SLOT_HOVER` 换成 `COLOR_SLOT_DENIED = 0x60FF3030` |
| 绘制顺序 | 槽底 → 类别底色 → 分隔线 → 角标 → （空则）幽灵图标 → 物品与数量 → 拖拽预览 → 选区边框 → 悬停高亮 |
| 没有扩展行 | 以上新分支一个都不进入，画面与 036 完成时逐像素一致 |

标签面板或拾取黑名单页打开时，主区域整体不画（现有 `continue`），新增内容随之不画。

## 2. 色值（初稿，游戏里看过可调；只改 `CategoryStyle` 一张表）

`fill` 是 `edge` 色加 `0x2A`（约 16%）透明度（初稿 0x59 约 35%，用户看过后嫌深，2026-11-10 调淡）；`edge` 不透明。

| 类别 | `edge`（RGB） | 说明 |
|---|---|---|
| food 食物 | `#F0A030` | 琥珀 |
| tool 工具 | `#4F8FE0` | 蓝 |
| weapon 武器 | `#D04040` | 红 |
| armor 盔甲 | `#9A6AD0` | 紫 |
| ore 矿物 | `#2FC4C0` | 青 |
| crop 农作物 | `#6FBF3F` | 绿 |
| wood 木材 | `#9C6A3A` | 棕 |
| potion 药水 | `#E060B0` | 粉 |
| redstone 红石 | `#8B1A1A` | 暗红（与 weapon 的红区分：更暗、更饱和） |
| disc 唱片 | `#2F3A8F` | 靛蓝（与 tool 的蓝区分：更暗） |
| 未知类别 | `#808080` | 中性灰，不画幽灵图标 |

相近的两对（weapon/redstone、tool/disc）靠角标、幽灵图标与悬停说明区分，不靠色相差。

## 3. 黄点与隐藏提示

- 条件：`HiddenHint.lines(view.rowView().hidden())` 非空。
- 位置：`x ∈ [posX+15, posX+20)`，`y ∈ [posY+1, posY+6)`；5×5，去掉四个角的像素，颜色 `0xFFFFD34D`，外圈 1 像素深色描边 `0xFF3A2E00`（在浅色图标与灰色标题栏上都看得见）。画在档位图标之后。
- 命中区：`x ∈ [posX+14, posX+21)`，`y ∈ [posY, posY+7)`；悬停黄点显示隐藏提示，**不**同时显示图标的档位名字（黄点在图标之上，优先）；悬停图标其余部分仍显示档位名字。
- 隐藏提示（多行）：
  1. 标题：`panel.mcfloatingbackpack.hidden.title`
  2. 每个有隐藏内容的类别一行：`panel.mcfloatingbackpack.hidden.line`（参数：类别名、格数）
  3. 提示：`panel.mcfloatingbackpack.hidden.hint`

## 4. 扩展行格子的悬停说明

悬停在 `groupOf(index) >= 0` 的格子上（标签面板与拾取页没盖住主区域时），在鼠标旁画一行：`panel.mcfloatingbackpack.row.tooltip`（参数：类别名）。类别名取 `tag.mcfloatingbackpack.preset.<类别>`（035 已有的预设标签语言键）。光标上有放不进的物品时，追加一行红字 `panel.mcfloatingbackpack.row.denied`。

## 5. 语言键（中英文，两个文件键集合保持一致）

| 键 | zh_cn | en_us |
|---|---|---|
| `panel.mcfloatingbackpack.row.tooltip` | `%1$s行：只能放%1$s类物品` | `%1$s row: only takes %1$s items` |
| `panel.mcfloatingbackpack.row.denied` | `这个物品放不进这一行` | `This item does not fit this row` |
| `panel.mcfloatingbackpack.hidden.title` | `有物品被隐藏了` | `Some items are hidden` |
| `panel.mcfloatingbackpack.hidden.line` | `%s：%s 格有物品` | `%s: %s slots hold items` |
| `panel.mcfloatingbackpack.hidden.hint` | `把对应的分类扩展放回功能区，它们就会回来` | `Put the matching category extension back into a tool area to get them back` |

（`row.tooltip` 只有一个参数，类别名，用 `%1$s` 两次引用。）

## 6. 不变的东西（回归）

36 的档位图标与背包编号的位置、大小、悬停；关闭、快照按钮；滚动箭头；选区、拖拽预览的画法；工具区的底色；标签面板、拾取页、垃圾桶面板。
