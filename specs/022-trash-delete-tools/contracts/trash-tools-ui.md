# Contract: 垃圾桶工具的界面与输入

## 1. 标题栏图标

- 位置与几何见 [data-model.md](../data-model.md) 第 9 节：两个 9×9 图标右对齐，倾倒在左、删除在右；文字"垃圾桶"在它们左边，宽度不够时截断。
- 绘制只用 `fill` 与字体：倾倒 = 向下的小三角 + 托盘线；删除 = `×`。可用时用深色，置灰时用灰色；悬停时图标底色高亮。
- **倾倒图标**：始终可点（光标为空时）。悬停说明：把主区域里带"垃圾"标签的物品移进垃圾桶；受保护的物品和带附魔等数据的物品会被跳过。
- **删除图标**：选区里至少有一个有物品的格子时可点，否则置灰。悬停说明：置灰时"先在主区域用 Shift 选择物品，再点这里删除"；可用时"删除选中的物品（可从垃圾桶拿回）"。置灰时也显示说明。

## 2. 标题栏上的按下（`TrashPanelInput.onPress`，在 021 的分派之后增加）

垃圾桶面板展开且按下点在标题栏上时（021 已经把整个外框内的按下消费）：

| 条件 | 行为 |
|---|---|
| 右键 / Shift / 013 拖动在途 | 吞掉，不做事（021 规则不变） |
| 光标非空 | 按 021：追加（**不管点在哪个图标上**） |
| 光标为空，点在倾倒图标上 | `TrashActions.dump()` |
| 光标为空，点在删除图标上且删除可用 | `TrashActions.deleteSelection()` |
| 光标为空，点在删除图标上但置灰 | 吞掉，不做事 |
| 其它（标题文字等） | 吞掉，不做事 |

## 3. 确认弹窗

- 触发：`TrashActions` 算出 `needsConfirm(destroyed)` 为真时 `TrashConfirmState.open(...)`，不发请求。
- 绘制（`FloatingPanelRenderer.render` 里，垃圾桶面板之后、光标物品之前）：半透明蒙版盖住整个浮动面板矩形；弹窗居中，文字 `trash.mcfloatingbackpack.confirm.text`（"将永久销毁 %s 组物品，确认吗？"）按弹窗宽折行；底部"确认""取消"两个按钮，悬停高亮。
- **输入（待确认期间）**：
  - 所有左右键按下先被 `TrashConfirmInput.onPress` 消费（`FloatingPanelInput.dispatchMouseButton` 的 PRESS 最前）；点"确认" → 发带 `confirmed` 的请求、清选区、关弹窗；点"取消"或任何其它位置 → 关弹窗，不发请求。
  - 滚轮一律消费。
  - ESC：`ScreenEvent.KeyPressed.Pre`，`hostsPanel(screen)` 且待确认 → 关弹窗并取消事件（宿主和容器界面都不再看到这个 ESC）。
  - Delete 键：`DeleteKeyRules` 因 `confirmPending` 返回 `NONE`，不吞键。
  - 其它键不受影响（E、聊天等照旧；换界面会触发丢弃）。
- **丢弃**：客户端 tick 检查：面板不可见 / 垃圾桶不在右侧区 / `minecraft.screen` 与记录的不一致 → `cancel()`。

## 4. Delete 键

- 映射：`key.mcfloatingbackpack.delete_to_trash`，`KeyConflictContext.GUI`，默认 macOS Backspace、其它 Delete，分类沿用模组的按键分类。
- 事件：`ScreenEvent.KeyPressed.Post`（界面自己没处理这个键时才触发）。`hostsPanel(screen)` 为假（020 的 OFF 界面、其它不托管面板的界面）直接不处理。
- 流程：组装 `DeleteKeyRules.Inputs`（界面种类、选区是否有物品、悬停的主区域格子是否有物品、悬停的菜单格子是否允许且有物品）→ `decide` → 按 `Target` 调 `TrashActions`；`NONE` 不取消事件。目标为 `MENU_SLOT` 时直接发请求（单格，永远不需要确认）。
- 取消事件的时机：只有 `decide` 返回非 `NONE` 并且请求已发出（或弹窗已打开）才 `event.setCanceled(true)`。

## 5. 三种界面上的 Delete

| 界面 | 判定 | 可删 |
|---|---|---|
| 019 宿主（无其它界面） | `PanelHostScreen` | 只有主区域（选区或悬停） |
| 玩家物品栏 | `InventoryScreen` | 主区域；悬停的玩家物品栏第 9～35 格 |
| 箱子 | 菜单是原版 `ChestMenu` | 主区域；悬停的箱子格子与玩家物品栏第 9～35 格 |
| 其它（创造物品栏、潜影盒、漏斗、熔炉、模组容器、020 的 OFF 界面） | — | 主区域**仍可**（面板在托管的界面上才有主区域），菜单格子不处理、不吞键 |

快捷栏、装备栏、副手、合成格：悬停时 `NONE`，不吞键。

## 6. 标签面板里的"垃圾"拦截

- `TagPanelModel.protectedItems()`：选中分类里受保护的物品；`blocks("trash")` 为真时：
  - 下拉里的"垃圾"预设**画灰**，悬停 `renderComponentTooltip` 列出这些物品名（`tag.mcfloatingbackpack.protected`）；
  - 点击它、方向键选中后回车、文本框里手打 `trash` 回车 → `TagPanelInput.add` 直接返回（**不停止编辑**），并在聊天区给一句提示（列出物品名）；
  - 其它标签照常；加号的可用性不变（只看工具存在与"已满"）。
- 服务端：见 [trash-send-network.md](./trash-send-network.md) 第 4 节。
