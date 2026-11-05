# Implementation Plan: 垃圾桶的删除手段与保护

**Branch**: `022-trash-delete-tools` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-trash-delete-tools/spec.md`

## Summary

在 021 的垃圾桶之上加三种**主动删除手段**（一键倾倒、删除图标/Shift 选区、Delete 键）、**两层保护**和**二次确认**。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **一个新的"放进垃圾桶"请求** `BackpackTrashSendPayload(source, confirmed, containerId, slots)`：`source` = `DUMP`（倾倒，无下标）/`MAIN_SLOTS`（主区域下标）/`MENU_SLOT`（原版界面的一个菜单格子）。021 的 `BackpackTrashPayload`（追加光标栈/拿起/恢复）**一行不改**。服务端流程：解析背包 → 垃圾桶在右侧区 → 算出这次要放入的栈（倾倒按筛选规则；主区域按下标取非空格子；菜单格子按原版菜单规则取）→ 算**会销毁多少组**，超过 9 且没带 `confirmed` 则整个拒绝 → 单组体积保护 → 逐组 `TrashBin.push` 并从来源取走 → 守恒核对、保存、同步。协议 `"9"` → `"10"`。
2. **纯规则全部不依赖 Minecraft**，建在 021 的 `TrashBin`/`StackOps` 之上：`TrashDeletePlan`（销毁数 = `max(0, 现有组数 + 放入组数 − 9)`、阈值 9）、`TrashDump`（倾倒筛选：主区域、分类带 `trash`、不在保护列表、栈只有耐久损耗）、`StackExtraData`（"只差耐久度"的比较）、`DeletableSlots`（原版玩家物品栏第 9～35 格、箱子格子）、`TrashSendRequest`（请求校验）、`TagProtection`（"垃圾"标签与受保护物品）。
3. **保护 = 数据包物品标签 `mcfloatingbackpack:never_trash`**（`data/mcfloatingbackpack/tags/item/never_trash.json`，条目可选，缺失不报错）。标签面板里**加号不整体变灰**（那会连"食物"也不让加）：选区含受保护物品时，下拉里的"垃圾"预设**画灰、悬停说明原因、点击和回车都不添加**，输入框里手打 `trash` 回车同样被拒并在聊天区说明；服务端 `BackpackTagPayload` 对"ADD `trash` 且选区含受保护物品"整个拒绝（research Decision 4，这是对 spec FR-008 措辞的一个收窄，需要用户确认）。
4. **垃圾桶标题栏图标**：`TrashPanelLayout` 增加两个 9×9 图标的几何与命中（右对齐），`TrashPanelRenderer` 用 `fill` 画；文字"垃圾桶"在窄栏里按宽度截断，英文标题改成 "Trash" 以免被图标挤掉。倾倒图标始终可点（光标为空时）；删除图标在选区里至少有一个有物品的格子时才可点。
5. **二次确认**：`TrashConfirmState`（会话状态：待确认的操作 + 销毁数 + 记录的界面）、`TrashConfirmLayout`（纯几何：蒙版 = 浮动面板矩形，居中弹窗，两个按钮；文字行数作参数以适配最小面板）、`TrashConfirmRenderer`、`TrashConfirmInput`。确认期间**所有按下**都先被它消费（只有两个按钮有效，其余等于取消）；滚轮被吞；ESC 在 `ScreenEvent.KeyPressed.Pre` 里先取消确认并取消事件（所以宿主和容器界面都看不到这个 ESC）；每 tick 检查面板可见、垃圾桶还在、界面没换，否则丢弃。
6. **Delete 键**：`FloatingBackpackKeys.DELETE_TO_TRASH`（`KeyConflictContext.GUI`，默认 Delete，macOS 默认 Backspace）。用 `ScreenEvent.KeyPressed.Post`——它只在界面自己没处理这个键时才触发，所以 JEI/配方书搜索框里的 Backspace 到不了这里。纯类 `DeleteKeyRules.decide(...)` 根据"面板可见、垃圾桶在、无确认、标签面板没展开且没在编辑、当前界面种类、选区/悬停"给出目标（`MAIN_SELECTION`/`MAIN_HOVER`/`MENU_SLOT`/`NONE`），`NONE` 时**不吞键**。019 的宿主界面对这个键判为 PASS（它的映射在"无界面"上下文里不活动），所以不会被当成让出的键。
7. **原版界面里的 Delete**：客户端认两类界面——玩家物品栏（`InventoryScreen`）和箱子（菜单是 `ChestMenu` 且 `MenuOwner` 为 `minecraft`）；悬停格子通过 `DeletableSlots`（玩家物品栏的 9～35、箱子自己的格子）判断。服务端再核对：菜单 id 等于玩家当前打开的、菜单类型、格子在范围、`slot.mayPickup`，整栈 `slot.remove` 取走，`broadcastChanges` 让看同一个箱子的玩家同步；菜单分支过 `ServerCompatGate`（沿用 `TAKE_FROM_CONTAINER`）。
8. **范围外**：021 的垃圾桶本体与手势一行不改（只在标题栏、渲染、`FloatingPanelInput` 分派层、`FloatingPanelEvents` 加挂接点）；不新增任何贴图。

范围：只做删除手段与保护；快捷栏/装备栏/其它容器的 Delete、其它模组容器、自动清理、垃圾桶搜索/排序都不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`ScreenEvent.KeyPressed.Pre/Post`、`KeyMapping` + `KeyConflictContext.GUI`、物品标签数据、`AbstractContainerMenu`/`Slot`、`DataComponentPatch`）；无新增依赖，不涉及第三方模组

**Storage**: 无新增持久数据。保护列表是数据包标签（数据文件）；确认弹窗状态、图标状态只在客户端会话里

**Testing**: JUnit 5 纯逻辑测试：`TrashDeletePlan`、`TrashDump`、`StackExtraData`、`DeletableSlots`、`TrashSendRequest`、`TagProtection`、`TagPanelModel`（受保护扩展）、`TrashPanelLayout`（图标）、`TrashConfirmLayout`、`TrashConfirmState`、`DeleteKeyRules`；资源测试：保护标签 JSON、语言键；加客户端会话手测（quickstart，宪法 V），宿主界面与容器界面都要测，重点是 ESC/Delete 的键盘流向、JEI 搜索框焦点、箱子里的同步

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（输入 + 渲染 + 网络 + 服务端规则 + 数据包标签）

**Performance Goals**: 倾倒筛选最多扫描 108 个主格子；一次操作一个请求、一次整份同步

**Constraints**:
- 所有进入垃圾桶的批量移动由服务端校验并执行，整个请求要么全成要么零变化；物品守恒（除溢出销毁）（宪法 II）
- 没有任何条件满足时 Delete **不拦截任何键**；确认弹窗期间的 ESC 在宿主界面与容器界面上都先于"ESC 关闭面板"
- 011～021 的行为不变；唯一的有意变化是标签面板对"垃圾"的拦截
- 不新增贴图（宪法 VI）；协议版本升到 `"10"`

**Scale/Scope**: 约 16 个新类（见下）；改动 `BackpackNetwork`、`BackpackTagPayload`、`FloatingBackpackKeys`、`FloatingPanelInput`、`FloatingPanelEvents`、`FloatingPanelRenderer`、`TrashPanelLayout/Renderer/Input`、`TagPanelModel/Context/Input/Renderer`、`ClientSetup`（注册按键）、`ModItems`（物品标签常量）；约 11 个新测试类；资源：标签 JSON、语言

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 确认蒙版与弹窗画在浮动面板同一条渲染路径里，**不新增 Screen**。唯一对别的界面按键的"介入"是确认期间取消 ESC（`ScreenEvent.KeyPressed.Pre`），目的是取消**我们自己的弹窗**，其余键一律放行；Delete 用 `Post` 事件，只处理界面自己没处理的键，且条件不满足时不吞键。不是为了打开或显示面板而拦截别的界面 | PASS |
| II. Server Owns Items | 倾倒筛选、主区域删除、原版菜单格子删除、销毁数计算、"已确认"校验、保护标签校验全部在服务端；客户端只发来源 + 下标 + 菜单 id + 已确认标志，不发物品；客户端的计算与确认只是体验 | PASS |
| III. One Feature At A Time | 只做 022 的删除手段与保护；021 的本体与手势不动；快捷栏/装备栏/其它容器、自动清理明确不做 | PASS |
| IV. Vanilla Path Before Compat | 只处理原版玩家物品栏与箱子菜单，其它模组容器一律不处理；保护标签对其它模组物品只是"允许数据包加条目"，不引用任何第三方类型 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证（三种来源的倾倒/删除、保护、确认框与 ESC、Delete 在三种界面生效与不吞键、JEI 搜索框、改键、同步）；Windows/Linux 无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增任何贴图：两个图标、确认框、蒙版全是 `fill` 与字体；保护标签是数据文件，不进图集；实现完成时按 018/021 的做法度量一次图集（先 `pgrep`，度量不了就如实写"未度量"） | PASS（已规划） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新增依赖；菜单分支用 `MenuOwner` + `ServerCompatGate`（沿用 `TAKE_FROM_CONTAINER`）；OFF 的模组界面上面板、垃圾桶面板、Delete 都挂在 `hostsPanel(screen)` 之后，不生效 | PASS |

**Re-check after Phase 1 design**：没有违反项。实现阶段要盯住的风险：
1. **Delete 的键盘流向**（research Decision 6）：`Post` 事件只在界面没处理时触发——必须在宿主界面、玩家物品栏、箱子三种界面实测；条件不满足时绝不能取消事件（否则吞掉玩家的其它按键）；玩家把键改成与世界动作冲突的键时宿主会先吞掉（这是改键的代价，写进已知限制）。
2. **确认期间的 ESC**（research Decision 7）：`Pre` 里取消后宿主与容器界面都不能再看到；同时标签文本框的 ESC 逻辑（018）与它的先后——确认期间没有编辑，二者不会同时发生，但要实测。
3. **服务端取出菜单格子**（research Decision 8）：箱子里取走整栈要同步给其他看箱子的玩家；`Slot.remove` + `setChanged` + `broadcastChanges`；必须在箱子里实测，另有第二个玩家时验证同步（没有就写"未验证"）。
4. **栈级跳过的判断**（research Decision 3）：用 `ItemStack.getComponentsPatch()` 判断"只差耐久度"；要实测附魔、命名、药水、旗帜、潜影盒、玩家头颅等各类栈都被跳过，普通耐久损耗的镐被倾倒。
5. **标签面板的拦截**不要破坏 018 已验证的流程：加号、预设下拉、回车添加、点击添加都要回归。

## Project Structure

### Documentation (this feature)

```text
specs/022-trash-delete-tools/
├── plan.md              # 本文件
├── research.md          # Phase 0：有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：纯规则、请求、保护标签、确认状态/几何、按键判定、资源清单
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   ├── trash-send-network.md    # Phase 1：请求包、服务端流程、协议 10、标签请求的新拒绝
│   └── trash-tools-ui.md        # Phase 1：图标、确认弹窗、Delete 键与三种界面、标签面板拦截
├── checklists/requirements.md
└── tasks.md             # Phase 2（/speckit-tasks 生成，本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/trash/
│   ├── TrashDeletePlan.java          # 新：纯。销毁数、阈值 9、是否需要确认
│   ├── TrashDump.java                # 新：纯泛型。倾倒筛选（主区域、trash 标签、保护、栈数据）
│   ├── StackExtraData.java           # 新：纯。"只差耐久度"的比较
│   ├── DeletableSlots.java           # 新：纯。原版界面里允许删除的格子
│   ├── TrashSendRequest.java         # 新：纯。请求校验（来源 + 下标 + 菜单 id）
│   └── TagProtection.java            # 新：纯。"垃圾"标签 × 受保护物品
├── network/
│   ├── BackpackTrashSendPayload.java # 新：客户端→服务端 DUMP / MAIN_SLOTS / MENU_SLOT
│   ├── BackpackNetwork.java          # 改：协议 "10"，注册新包
│   └── BackpackTagPayload.java       # 改：ADD trash + 受保护物品 → 整个拒绝
├── item/ModTags.java                 # 新：TagKey<Item> NEVER_TRASH
└── client/
    ├── FloatingBackpackKeys.java     # 改：DELETE_TO_TRASH
    ├── FloatingPanelEvents.java      # 改：KeyPressed.Pre 里确认期间的 ESC；KeyPressed.Post 里 Delete；tick 里丢弃待确认；滚轮
    └── panel/
        ├── FloatingPanelInput.java   # 改：按下先交给确认弹窗
        ├── FloatingPanelRenderer.java# 改：确认蒙版与弹窗
        ├── DeleteKeyRules.java       # 新：纯。Delete 键的可用性与目标
        ├── tags/{TagPanelModel,TagPanelContext,TagPanelInput,TagPanelRenderer}.java  # 改：受保护物品与"垃圾"
        └── trash/
            ├── TrashPanelLayout.java   # 改：两个图标的几何与命中
            ├── TrashPanelRenderer.java # 改：画图标与悬停说明
            ├── TrashPanelInput.java    # 改：标题栏点击 → 倾倒/删除
            ├── TrashActions.java       # 新：算需要多少确认、发请求、清选区
            ├── TrashConfirmState.java  # 新：待确认操作（会话状态）
            ├── TrashConfirmLayout.java # 新：纯几何（蒙版、弹窗、两个按钮）
            ├── TrashConfirmRenderer.java # 新
            └── TrashConfirmInput.java  # 新：按下/ESC/丢弃

src/main/resources/
├── data/mcfloatingbackpack/tags/item/never_trash.json        # 新：保护标签（条目可选）
└── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json         # 改：图标说明、确认框、聊天提示、按键名、保护提示

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/trash/{TrashDeletePlanTest,TrashDumpTest,StackExtraDataTest,DeletableSlotsTest,TrashSendRequestTest,TagProtectionTest}.java
├── client/panel/DeleteKeyRulesTest.java
├── client/panel/tags/TagPanelModelTest.java                  # 扩展：受保护
├── client/panel/trash/{TrashPanelLayoutTest(扩展),TrashConfirmLayoutTest,TrashConfirmStateTest}.java
└── resources/NeverTrashResourcesTest.java
```

**Structure Decision**: 沿用 018/021 的分包：纯规则在 `inventory/trash/`，客户端界面在 `client/panel/trash/`；保护标签常量放在 `item/ModTags.java`（`TagKey` 是 Minecraft 类型，不进纯类）。"销毁数/确认阈值"独立成 `TrashDeletePlan`，客户端与服务端共用，避免两边算法漂移。

## Complexity Tracking

无违反项，不需要登记。
