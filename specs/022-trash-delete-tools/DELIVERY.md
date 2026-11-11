# Delivery: 022-trash-delete-tools

**日期**: 2026-11-05

**状态**: 代码、单元测试、编译、静态检查、文档与图集度量完成（T001～T028、T030～T034、T036～T037、T038～T043、T045～T051、T053～T055、T057、T059～T062）。**客户端验证（2026-11-07 更新）：T029、T044、T052、T056 已由用户逐项验证并勾选；T035（Q11～Q14 未验证）、T058（Q41 与统一回归未做）仍保持未勾选**；详见文末"2026-11-07（再续）"。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| 改动前基线（T001） | `./gradlew compileJava test` 通过；50 个测试类、409 个用例、0 失败 |
| Phase 2（T021） | `./gradlew compileJava test` 通过；新增 `TrashDeletePlanTest`、`TrashDumpTest`、`StackExtraDataTest`、`DeletableSlotsTest`、`TrashSendRequestTest`、`TagProtectionTest`、`TrashConfirmStateTest`、`NeverTrashResourcesTest`、`TrashToolsResourcesTest`；共 59 个测试类、447 个用例、0 失败。测试与实现同时写出，没有单独演示过"先失败" |

## 读源码的结论

- **`Slot.remove`（T053）**：`Slot.remove(amount)` 就是 `container.removeItem(slot, amount)`；箱子的槽 `mayPickup` 恒为真、`allowModification` 默认真，`tryRemove` 里多出来的只是"槽被取空时 `setByPlayer`"，普通槽没有副作用，所以服务端用 `slot.remove(count)` + `slot.setChanged()` 再 `menu.broadcastChanges()` 即可，不需要 `onTake`。（读 `build/moddev/artifacts/neoforge-21.1.251-sources.jar` 里的 `Slot`。）
- **`ScreenEvent.KeyPressed.Post`（T042）**：`KeyboardHandler` 里 `if (!handled) handled = Post(...)`，所以只有界面自己没处理这个键时才触发；`Post` 被取消就等于"已处理"。`ScreenEvent.KeyPressed.Pre` 被取消则界面的 `keyPressed` 与 `Post` 都不会发生（确认弹窗的 ESC 靠它）。

## 用户的部分验证与验证后发现的问题（2026-11-05）

用户进游戏验证过**删除选中物品、倾倒垃圾**，没有问题；**JEI 搜索栏里按 Backspace 可以正常输入**（对应 Q35 的一部分）。其余验证项仍未验证，T029、T035、T044 因此保持未勾选。

用户同时发现一个问题：**点击浮动面板不会让 JEI 搜索框失去焦点，只有点原版物品栏才会**（T063）。结论：

- **不是 JEI 专有的问题，也不需要 JEI 专门适配**。读 JEI 19.70 源码：它的搜索框焦点由它在运行时注册的 `ScreenEvent.MouseButtonPressed.Pre`/`MouseButtonReleased.Pre` 管理（`GuiInputController.mouseReleased` 里"松开点不在搜索框上就 `clearFocus`"）。我们面板消费的按下与松开在更早就被取消了，JEI 的监听收不到。对任何依赖这些事件、或依赖屏幕自己的鼠标处理来失焦的控件（原版配方书搜索框、其它模组的输入框）都一样。
- **修复是模组无关的**：面板消费一次按下时先调 `Screen.clearFocus()`（原版屏幕"点了别处就失去焦点"的那一步）。不引用任何模组类型，所以**不放进 `compat/jei/`、也不受 020 的档位控制**；它只在 `hostsPanel(screen)` 为真的界面上发生，020 配置为 OFF 的模组界面上面板本来就不处理点击，所以也不受影响（宪法 VII"不引用类型但触碰别的模组界面的行为仍受设置约束"这条，靠的就是这个既有的开关）。
- **用户已在游戏里验证，问题已解决**（T064，2026-11-05，口头确认）。

## 偏离说明

- 实现是按阶段顺序一口气写完的：US1～US5 的代码在同一轮完成，US3/US5 的服务端来源（`MAIN_SLOTS`、`MENU_SLOT`）与客户端的 Delete 事件在写请求流水线时一并写好，因此 tasks 里 T025/T040/T053 与 T042/T054 的先后只体现在勾选上。

## 阶段结果（续）

| 项 | 结果 |
|---|---|
| 全部阶段（T060） | `./gradlew test --rerun-tasks` 通过：**61 个测试类、468 个用例、0 失败**（基线 50 个类、409 个用例；新增 `TrashDeletePlanTest`、`TrashDumpTest`、`StackExtraDataTest`、`DeletableSlotsTest`、`TrashSendRequestTest`、`TagProtectionTest`、`TrashConfirmStateTest`、`TrashConfirmLayoutTest`、`DeleteKeyRulesTest`、`NeverTrashResourcesTest`、`TrashToolsResourcesTest`，扩展 `TrashPanelLayoutTest`、`TagPanelModelTest`） |
| 图集度量（T060） | 先 `pgrep` 确认没有客户端在运行，再跑 `design/demo/tools/check_atlas_budget.sh`：共用方块图集 **2048×1024**（mip 级别 4），估算显存约 11.2MB，预算 2048 → **通过**；脚本结束后确认没有残留的客户端进程。这次启动只到主菜单，加载世界时才读的数据包标签（`never_trash`）**没有被加载过**；启动日志里没有关于本模组的错误 |
| 静态检查（T061） | `PanelSelectionInput`、`PanelDragGesture`、`QuickTransferRules`、`TrashBin`、`BackpackTrashPayload`（021 的请求包）、`build.gradle`、`neoforge.mods.toml` 无改动；协议版本只在 `BackpackNetwork` 一处，为 `"10"`；`setScreen(` 与 021 一致（宿主管理器与宿主，没有新增）；`grep p3pp3rf1y\|tiviacz\|mezz` 只命中 `compat/jei/` 与 `ContainerScreenCompat` 里原有的类名字符串匹配；`DependencyRulesTest` 通过；`FloatingPanelEvents` 里 Delete 分支的 `setCanceled` 只在 `DeleteKeyInput.onKey` 返回真（目标不是 `NONE`）时调用 |

## 组件补丁的结论（T035 的前置，读源码）

`PatchedDataComponentMap.set`：新值等于原型默认值时会把补丁项**移除**，所以 `stack.getComponentsPatch()` 里只有与默认不同的组件；`asPatch()` 在补丁为空时返回 `EMPTY`。被"移除了默认组件"的栈（补丁里有值为 `Optional.empty` 的项）也会出现在补丁里，所以同样被判为带数据，符合 spec。因此"补丁里除了 `minecraft:damage` 没别的键"就是"栈只差耐久度"。**读源码的结论，没有用真实栈（附魔、命名、药水、旗帜、头颅、地图、书）实测。**

## 审阅（T057，读代码，没有改行为）

- ① `PanelHostScreen.boundMappings` 在 `minecraft.screen = null` 下问每个映射的冲突上下文是否活动；`DELETE_TO_TRASH` 是 `GUI` 上下文（`screen != null` 才活动），所以 Delete 在宿主上归为 `PASS`（`keyPressed` 返回 false），`Post` 照常触发，不会被当作让出重放的键。
- ② Delete 的 `Post` 处理与确认期间的 ESC 处理都先判断 `hostsPanel(screen)`，020 的 OFF 界面不生效。
- ③ `DeleteKeyInput.onKey` 在目标为 `NONE` 时返回 false，调用方不取消事件；其它路径都是先做事再返回 true。
- ④ 服务端三种来源的非法请求：背包不在/右侧区没有垃圾桶/`TrashSendRequest.validate` 失败 → 整个跳过；`MAIN_SLOTS` 越界或重复在校验里被拒；`MENU_SLOT` 逐项核对（容器 id、菜单类型、原版归属、`ServerCompatGate`、格子范围、`DeletableSlots`、`hasItem`、`mayPickup`）；销毁数超过 9 而没带 `confirmed` → 拒绝并提示；任一栈体积超限 → 整个拒绝；忽略路径都补发垃圾桶内容、整份同步与光标同步。**没有发现缺口。**
- ⑤ 菜单分支过 `ServerCompatGate`（沿用 `TAKE_FROM_CONTAINER`）；021 的 `BackpackTrashPayload` 没有改动。
- 一个值得知道的行为：确认被服务端拒绝时（背包已变）只给聊天提示，不自动重新弹确认。

## 客户端验证状态（宪法 V）

| 任务 | 内容 | 状态 |
|---|---|---|
| T029 | quickstart A、B（Q1～Q7） | **通过（2026-11-07）**，已勾选 |
| T035 | quickstart C（Q8～Q14，含栈级跳过的实测与 018 回归） | **部分（2026-11-07）**：Q8～Q10 通过；**Q11（栈级跳过实测）、Q12、Q13、Q14 未验证**；保持未勾选 |
| T044 | quickstart D、E（Q15～Q23） | **通过（2026-11-07）**，已勾选（只测了销毁数不超过 9 的删除） |
| T052 | quickstart F（Q24～Q30，含 ESC 与蒙版输入） | **通过（2026-11-07）**，已勾选 |
| T056 | quickstart G（Q31～Q38，含 JEI 搜索框与箱子同步） | **通过（2026-11-07）**，已勾选；**Q36（箱子里 Delete 后另一位玩家那边同步）没有条件，未验证** |
| T058 | quickstart H（Q39～Q42，含改键与 021/011～020 回归） | **部分（2026-11-07）**：Q39、Q40、Q42 通过；**Q41（OFF 的模组界面，S2）与 011～020 统一回归（K 组）未做**；保持未勾选 |

## 用户的部分验证与验证后发现的问题（2026-11-05）

用户进游戏验证过**删除选中物品、倾倒垃圾**，没有问题；**JEI 搜索栏里按 Backspace 可以正常输入**（对应 Q35 的一部分）。其余验证项仍未验证，T029、T035、T044 因此保持未勾选。

用户同时发现一个问题：**点击浮动面板不会让 JEI 搜索框失去焦点，只有点原版物品栏才会**（T063）。结论：

- **不是 JEI 专有的问题，也不需要 JEI 专门适配**。读 JEI 19.70 源码：它的搜索框焦点由它在运行时注册的 `ScreenEvent.MouseButtonPressed.Pre`/`MouseButtonReleased.Pre` 管理（`GuiInputController.mouseReleased` 里"松开点不在搜索框上就 `clearFocus`"）。我们面板消费的按下与松开在更早就被取消了，JEI 的监听收不到。对任何依赖这些事件、或依赖屏幕自己的鼠标处理来失焦的控件（原版配方书搜索框、其它模组的输入框）都一样。
- **修复是模组无关的**：面板消费一次按下时先调 `Screen.clearFocus()`（原版屏幕"点了别处就失去焦点"的那一步）。不引用任何模组类型，所以**不放进 `compat/jei/`、也不受 020 的档位控制**；它只在 `hostsPanel(screen)` 为真的界面上发生，020 配置为 OFF 的模组界面上面板本来就不处理点击，所以也不受影响（宪法 VII"不引用类型但触碰别的模组界面的行为仍受设置约束"这条，靠的就是这个既有的开关）。
- **用户已在游戏里验证，问题已解决**（T064，2026-11-05，口头确认）。

## 偏离说明

- 实现一口气写完了 US1～US5 的代码，服务端三种来源（`DUMP`、`MAIN_SLOTS`、`MENU_SLOT`）和客户端的 Delete 事件、确认弹窗都在同一轮完成，所以 tasks 里"US1 期间大批量倾倒只会被服务端拒绝"这条注意事项实际上不会出现；但验证仍建议按 tasks 的顺序做。
- 英文"确认"按钮文字由 "Confirm" 改为 "OK"：最小面板下按钮只有 38 像素宽，"Confirm" 要 37 像素，太挤。
- spec FR-008/US2 的"加号变灰"按 plan 的 Decision 4 实现为"'垃圾'预设变灰、添加被拒绝（加号仍可用）"，**用户尚未确认 spec 措辞**，spec 本身没有改。

## 已知限制

- Windows / Linux 未验证；联机与箱子里 Delete 的多人同步未验证。
- 把"删除到垃圾桶"改到一个同时绑着世界动作的键（如 W）时，019 的宿主界面会先吞掉它，Delete 功能不触发（改键冲突的代价，不修复）。
- 栈级跳过的覆盖面只有读源码的结论，没有用真实栈实测。
- 确认被服务端拒绝（背包内容已变）时只给聊天提示，不自动重新弹确认。
- `never_trash` 标签只在加载世界时读取，这次的启动只到主菜单，没有被加载过。
- 体积保护的上限（96 KiB）沿用 021 的初值，没有实测。

## 2026-11-07：二次确认框被物品图标盖住（缺陷，已修复，待用户重测）

用户在面板里 Shift 选中超过 9 组再按 Backspace 删除时，二次确认框出现在物品**下方**：物品图标与数量文字穿过了确认框。

- 原因：`FloatingPanelRenderer` 把整个面板（含确认框的遮罩和对话框）画在同一个基准深度 `OVERLAY_Z`（800）。原版 `GuiGraphics.renderItem` 把物品图标再抬高 150、数量文字抬高 200，而面板的深度测试对物品图标仍然有效，所以同一深度上画的遮罩被更近的物品盖住。
- 修复：确认框整体抬高 300（`CONFIRM_Z`），高于所有格子物品；光标上的物品抬到 400（`CURSOR_Z`），仍在确认框之上。只改了绘制深度，没有改任何输入、布局或协议逻辑。
- 验证：`./gradlew compileJava test` 通过（62 个测试类、0 失败）。渲染层没有单测，**视觉是否正确未验证**，需要用户在游戏里重做"选中超过 9 组 → Backspace"确认。

## 2026-11-07（续）：确认框遮罩修复已确认；取消删除时保留选区

- 用户确认二次确认框的遮罩层级问题已修复。
- 用户提出改动：玩家在确认框里**取消**批量删除时，**不要清空选区**。原因：`TrashActions.deleteSelection()` 在调用 `deleteMainSlots` 之后立即清选区，而此时确认框才刚弹出，所以不论点确认还是取消选区都没了。这与 spec 的"取消 → 什么都没变 / 数据零变化"（US 验收 3、SC-004）不一致，按缺陷修。
- 修复：`deleteMainSlots` 改为返回"是否已立即发送"；`deleteSelection` 只在已立即发送时清选区；需要确认时选区原样保留，点"确认"后由 `sendConfirmed()` 清除（原有逻辑）。取消、点蒙版其它位置、ESC、关面板都不动选区。不涉及协议、服务端和布局。
- 验证：`./gradlew compileJava test` 通过。选区是否保留**未在游戏里验证**（清单 J 组已加重测项）。

## 2026-11-07（再续）：J 组逐项验证结果

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选；没勾的仍是**未验证**。

- **通过**：Q1～Q10（图标与一键倾倒、保护的预设灰色/拦截/受保护物品被倾倒跳过）；Q15～Q23（删除图标、Delete 键，含宿主界面与开着物品栏/箱子）；Q24～Q30（二次确认：确认/取消/点蒙版/点面板外/ESC、弹窗期间输入无效、阈值边界、最小面板、内容变化时服务端拒绝）；Q31～Q35、Q37、Q38（原版玩家物品栏与箱子里的 Delete，含配方书搜索框 Backspace）；Q39、Q40、Q42（改键、改成 W 的表现、021/018 回归）。
- **未验证**：
  - **Q11**（钻石镐分类带"垃圾"，磨损的进垃圾桶、附魔/改名/药水/旗帜/头颅/地图/书/装了东西的潜影盒被跳过）——这是 research Decision 3 的组件补丁判断，只有读源码的结论，**仍没有实测**。
  - **Q12**（数据包增删 `never_trash` 条目）、**Q13**（日志没有 `bundle` 标签缺失的报错）、**Q14**（删除图标/Delete 键/手动拖动删除受保护物品与附魔镐都允许）。
  - **Q36**（箱子里 Delete 后另一位玩家那边同步，没有第二台机器）。
  - **Q41**（OFF 的模组界面上面板、垃圾桶面板、Delete 都不生效，会话 S2）。
  - 011～020 与 021 的统一回归（清单 K 组）。
- **取消批量删除时保留选区**：清单没有为它单独设勾选项；Q25（取消/点蒙版/点面板外/ESC 都只关弹窗、数据不变）已通过，但**"选区原样还在"没有被单独确认**，仍记为未单独验证。
- 确认框遮罩层级修复：用户此前已口头确认。
- T029、T044、T052、T056 已勾选；T035、T058 保持未勾选。

## 2026-11-07（三续）：统一回归

- **通过**：统一回归 REG-1～REG-9（含 REG-8 的垃圾桶丢入、拖放、拿回、Ctrl 恢复、倾倒、删除，REG-6 的标签面板，日志无新增报错）。
- **仍未验证**：Q41（OFF 的模组界面上面板、垃圾桶面板、Delete 都不生效，会话 S2）→ **T058 保持未勾选**。

## AI 自动验证（2026-11-11，Linux 容器内 `runClient`）：Q11～Q14

环境与方法见 `docs/plan/ai-gui-verification.md`。背包用数据包函数（聊天框只有 256 字符）一次性写好内容，垃圾桶放右区、标签管理器放下区。

- **Q11 部分通过**：给钻石镐的分类打"trash"标签，主区域有磨损的、附魔的、改名的钻石镐和一个装了东西的潜影盒，点倾倒图标：**只有磨损的那把进了垃圾桶**，其余三件留在主区域。药水、带图案的旗帜、玩家头颅、地图、书**没测**。
- **Q12 通过**：用数据包覆盖 `never_trash` 标签（去掉信标、加入圆石）并 `/reload`：给信标打 `trash` 成功（原来被拦）；给圆石打 `trash` 被拒，聊天 `These items cannot be marked as trash: Cobblestone`，文本框保持。
- **Q13 通过**：日志里没有关于 `bundle` 标签条目缺失的报错。
- **Q14 通过**：删除图标（附魔镐）、Delete 键（装了东西的潜影盒）、手动点击放入垃圾桶（改名镐）都允许；四件物品最后都在垃圾桶里。注意"删除图标"是把选区移进垃圾桶，不是直接销毁。

## 2026-11-11 第二轮 AI 自动验证：Q36（两个玩家看同一个箱子）

专用服务器 + 两个客户端；P1 的背包右区放垃圾桶；箱子在 (-2,100,0)，两个玩家都右键打开它；箱子第 9 格放 20 个圆石（两个客户端都看到）。P1 开面板，鼠标悬停在箱子第 9 格上按 Delete：**P2 屏幕上的箱子里那一格随即变空**（Delete 前 P2 看到 20 个圆石，Delete 后为空），**通过**。没有单独核对 P1 的垃圾桶里是否收到、没测 P2 在同一时刻点击同一格的冲突。
