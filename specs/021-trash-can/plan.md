# Implementation Plan: 第二个扩展工具——垃圾桶（本体）

**Branch**: `021-trash-can` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/021-trash-can/spec.md`

## Summary

新增物品"垃圾桶"：放进**右侧区**后左键向右展开一个小面板（标题栏 + 3×3 格），把光标上的物品**丢**进去，需要时**拿回来**；只记住最近丢掉的 9 组，更早的永久销毁。同时把"右侧区限放 1 个垃圾桶、下方区限放 1 个标签管理器"做进服务端放入规则。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **普通物品 + "在右侧区里"才有功能**：`TrashCanItem`（堆叠 1）进创意栏，配方十字形（4 铁锭 + 炼药锅）。展开/收起是纯客户端状态切换（左键、光标空、无 Shift/Ctrl），做法与 018 完全平行。
2. **垃圾桶内容 = `BackpackContents` 的第五个字段 `trash`**（`TrashContents`：至多 9 个 `ItemStack`，最新在前，不合并）。`StreamCodec.composite` 5 个字段，没超 6 的上限；旧背包读成空；`fit/copy/升级` 自动保留。
3. **纯逻辑** `TrashBin<T>`（追加并在第 10 组时挤掉最旧的、拿起、按目标顺序恢复且放不下的留原位）建在已有的 `QuickTransferLogic.StackOps<T>` 上，和真实游戏跑同一份代码，可单测。
4. **服务端权威**：一个客户端→服务端的请求 `BackpackTrashPayload(action, slots, screenOpen)`（`ADD`/`TAKE`/`RESTORE`），一个服务端→客户端的同步 `BackpackTrashContentsPayload`（整份 ≤ 9 组，像 018 的标签包那样单独发，因为会话包的字段已满）。服务端核对"右侧区确实有垃圾桶"、光标状态与动作一致、下标范围；不合法则整个丢弃且零变化。协议 `"8"` → `"9"`。每次溢出销毁记一行调试日志；追加前对单个物品栈做**体积保护**（防止编码后超出网络包上限踢人）。
5. **限放 1 个**：把 016 的恒真钩子 `ToolAreaRules.canPlace(area, stack)` 换成能看到"该区域当前内容 + 被替换格子"的纯函数；规则是"放入后该类物品的数量 ≤ 1，或不比放入前多"（所以旧背包里已有的多个不被删、同格交换允许）。服务端在 `BackpackClickPayload` 里已经有一处调用点，**只改这一处**；客户端用同一个纯函数提前判断并在聊天区提示。
6. **垃圾桶面板画在浮动面板矩形之外**：`TrashPanelLayout`（纯几何，吃浮动面板的位置与宽度）+ `TrashPanelState`（展开状态单例）+ 渲染/输入两个类。命中与按下消费、滚轮消费、JEI 避让矩形都读同一个布局。展开时与标签面板互不影响。
7. **拖放（松开在垃圾桶上）**：用一个纯的"按下/松开"记录器 `TrashDropTracker`，记下每个鼠标键的按下是否在垃圾桶之外；左键在垃圾桶范围上松开、且按下在外、无 Shift/Ctrl → 取消 013 手势、发 `ADD`，并在客户端预测清空光标。对原版容器界面的特殊处理（不拦截松开，让原版自己收尾）见 research Decision 8。
8. **Ctrl 恢复**：`QuickTransferGesture` 增加 `Side.TRASH`（自成一组，只接受同组的格子），`QuickTransferInput` 增加垃圾格的命中与发送，发 `RESTORE`。服务端按下标升序、先叠后填地放进主区域，放不下的留原位。
9. **013/014 手势只在主区域**：它们本来就只认主区域格子（`PanelSlots.indexAt`）；本 feature 只补一条——"松开在垃圾桶上时取消手势而不结算"。
10. **图标**：一张 16×16 平面贴图（ludo.ai 生成 → 脚本缩成 16×16），模型 `item/generated`，和标签管理器一样。

范围：只做垃圾桶本体；一键倾倒、标题栏图标、保护规则、确认弹窗、Delete 键都留给 022。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（数据组件与编码、`ScreenEvent`、`GuiGraphics`、`PacketDistributor`、数据驱动配方）；无新增依赖，不涉及第三方模组（JEI 的避让矩形沿用 008/020 的既有插件）

**Storage**: 物品数据组件 `BACKPACK_CONTENTS` 新增可选字段 `trash`；界面状态（面板展开、按下/松开记录）只在客户端会话里，不持久化

**Testing**: JUnit 5 纯逻辑测试：`ToolAreaRules`、`TrashBin`、`TrashRequest`、`TrashPanelLayout`、`TrashDropTracker`、扩展 `QuickTransferGestureTest`；资源测试：配方 JSON、模型、两份语言文件的键（文本方式）；加客户端会话手测（quickstart，宪法 V），宿主界面与容器界面都要测，重点是拖放松开与服务端守恒

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（物品 + 存储 + 网络 + 客户端界面与输入）

**Performance Goals**: 垃圾桶面板每帧只画 9 个格子；一次操作一个请求、一次整份（≤ 9 组）同步

**Constraints**:
- 所有进出垃圾桶的物品移动由服务端校验并执行；整个请求要么全成要么零变化；物品守恒（除溢出销毁）（宪法 II）
- 011～020 的行为不变；唯一的有意变化是 FR-020（限放 1 个）与"松开在垃圾桶上取消 013 手势"
- 只新增一张 16×16 贴图与语言条目（宪法 VI）；协议版本升到 `"9"`
- 没有新的键盘处理（022 才有）

**Scale/Scope**: 约 12 个新类（物品、`TrashContents`、`TrashBin`、`TrashRequest`、两个网络包、`TrashPanelState/Layout/Renderer/Input`、`TrashDropTracker`）；改动 `BackpackContents`、`BackpackInventory`、`BackpackNetwork`、`BackpackClickPayload`、`ToolAreaRules`、`ClientBackpackView`、`FloatingPanelInput`、`FloatingPanelRenderer`、`FloatingPanelEvents`、`QuickTransferGesture`、`QuickTransferInput`、`PanelDragInput`、`FloatingBackpackJeiPlugin`、`ModItems`；约 7 个新测试类；资源：配方、模型、贴图、语言

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 垃圾桶面板和浮动面板一样是叠层，画在同一条渲染路径里，输入走同一组 `ScreenEvent`；**不新增 Screen**。松开事件在原版容器界面上**不被拦截**（让原版自己收尾），只是先把光标清空（research Decision 8），所以不替换、不包装、不拦截原版界面的逻辑 | PASS |
| II. Server Owns Items | 追加/拿起/恢复全部在服务端算：客户端只发"动作 + 格子下标 + 界面是否打开"；服务端从背包与光标的真实内容算结果、核对垃圾桶在右侧区；"限放 1 个"也在服务端判定，客户端判断只是体验 | PASS |
| III. One Feature At A Time | 只做垃圾桶本体与两条限放规则；倾倒、图标、保护、确认弹窗、Delete 键明确留给 022；改动 018 的既有行为（下方区限放 1 个标签管理器）与 013/014 的手势边界是 spec 明确要求的 | PASS |
| IV. Vanilla Path Before Compat | 配方只用原版铁锭与炼药锅；JEI 只是多报一个避让矩形（既有插件，受 020 控制）；不引用任何第三方模组类型 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证（宿主界面/容器界面、拖放、Ctrl 恢复、限放、持久化、升级、旧存档、窗口缩窄、OFF 界面）；Windows/Linux 无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 只新增一张 16×16 物品贴图，面板全是 `fill`；实现完成时按 018 的做法度量一次图集（先 `pgrep` 确认没有游戏在跑，度量不了就如实写"未度量"） | PASS（已规划） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新增依赖；JEI 避让矩形仍受 `CompatConfig.policy().specificAdaptation("jei")` 控制；OFF 的模组界面上面板（含垃圾桶面板）整体不画、不响应，因为它们都挂在 `hostsPanel(screen)` 之后；使用菜单光标的分支过服务端 `ServerCompatGate` | PASS |

**Re-check after Phase 1 design**：没有违反项。实现阶段要盯住的风险：
1. **原版容器界面上的拖放松开**（research Decision 8，最不确定）：原版在"带着东西按下"时会进入"拖动分配"状态，松开若被拦截会留下状态，若放行则可能把物品扔出窗口或放进被垃圾桶遮住的格子。方案是**先在服务端发 `ADD`、客户端预测清空菜单携带物，再放行松开**，让原版带着空光标收尾；必须在真实客户端里用箱子和玩家物品栏各测一次，失败的退路写在 research。
2. **限放规则的钩子参数**：`canPlace` 签名变化会碰到所有调用点（只有 `BackpackClickPayload` 一处），要同时核对 Alt 交换主手与菜单携带物两种"手里的物品"来源。
3. **体积保护**：单个物品栈编码后过大会让同步包超限；上限值要用真实的满载潜影盒、满载嵌套背包测量后确定（research Decision 5）。
4. **`BackpackContents` 编码改变**：字段数 4 → 5、协议升到 9；旧背包读成空；`BackpackUpgradeRecipe` 与 `fit` 必须保留 `trash`（和 018 的 `tags` 一样走 `fit`/`copy`，要核对）。

## Project Structure

### Documentation (this feature)

```text
specs/021-trash-can/
├── plan.md              # 本文件
├── research.md          # Phase 0：有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：TrashContents、TrashBin、请求、限放规则、面板状态/布局、资源清单
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   ├── trash-network-storage.md   # Phase 1：数据组件、协议 9、请求校验与服务端流程、同步
│   └── trash-panel-ui.md          # Phase 1：展开/收起、命中与消费、拖放松开、Ctrl 恢复、渲染
├── checklists/requirements.md
└── tasks.md             # Phase 2（/speckit-tasks 生成，本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── item/
│   ├── TrashCanItem.java                        # 新：普通物品，堆叠 1
│   └── ModItems.java                            # 改：注册 trash_can，创意栏
├── inventory/
│   ├── ToolAreaRules.java                       # 改：限放 1 个（纯函数，吃区域内容与被替换格子）
│   ├── BackpackContents.java                    # 改：第 5 个字段 trash
│   ├── BackpackInventory.java                   # 改：trash()/setTrash/hasTrashCan，fromContents/toContents
│   └── trash/
│       ├── TrashContents.java                   # 新：≤ 9 个 ItemStack 的记录 + CODEC / STREAM_CODEC
│       ├── TrashBin.java                        # 新：纯逻辑（追加/拿起/恢复），泛型
│       └── TrashRequest.java                    # 新：纯校验（动作 + 下标）
├── network/
│   ├── BackpackTrashPayload.java                # 新：客户端→服务端 ADD / TAKE / RESTORE
│   ├── BackpackTrashContentsPayload.java        # 新：服务端→客户端 整份内容
│   ├── BackpackNetwork.java                     # 改：协议 "9"，注册，sendSession 后补发垃圾桶内容
│   └── BackpackClickPayload.java                # 改：放入规则调用点换成新签名
└── client/panel/
    ├── ClientBackpackView.java                  # 改：trash 镜像 + hasTrashCan()
    ├── FloatingPanelInput.java                  # 改：垃圾桶按下/松开分派、限放提示、松开取消手势
    ├── FloatingPanelRenderer.java               # 改：画垃圾桶面板（矩形之外）
    ├── QuickTransferGesture.java / QuickTransferInput.java   # 改：Side.TRASH
    ├── PanelDragInput.java                      # 改：公开的 cancel()
    ├── FloatingPanelEvents.java（在 client/ 下）   # 改：每 tick 收起检查、退出登录重置、滚轮消费、记录器清理
    └── trash/
        ├── TrashPanelState.java                 # 新：展开状态单例
        ├── TrashPanelLayout.java                # 新：纯几何（标题栏、9 格、命中）
        ├── TrashPanelRenderer.java              # 新：fill + 字体 + 物品
        ├── TrashPanelInput.java                 # 新：按下/松开分派，发请求，客户端预测
        └── TrashDropTracker.java                # 新：纯，记录"按下是否在垃圾桶外"
src/main/java/pnnh/mcfloatingbackpack/compat/jei/FloatingBackpackJeiPlugin.java   # 改：展开时多报一个矩形

src/main/resources/
├── assets/mcfloatingbackpack/models/item/trash_can.json      # 新：item/generated
├── assets/mcfloatingbackpack/textures/item/trash_can.png     # 新：16×16
├── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json         # 改：物品名、面板标题、限放提示、体积提示
└── data/mcfloatingbackpack/recipe/trash_can.json             # 新：十字形

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/ToolAreaRulesTest.java
├── inventory/trash/{TrashBinTest,TrashRequestTest}.java
├── client/panel/QuickTransferGestureTest.java                # 扩展：Side.TRASH
├── client/panel/trash/{TrashPanelLayoutTest,TrashDropTrackerTest}.java
└── resources/TrashCanResourcesTest.java

design/demo/tools/make_trash_can_icon.py                      # 新：ludo.ai 原图 → 16×16（开发期素材，不随包）
```

**Structure Decision**: 沿用 018 的分包：纯规则在 `inventory/trash/`，客户端界面在 `client/panel/trash/`，与标签管理器的 `inventory/tags/`、`client/panel/tags/` 并排，方便对照和以后合并公共部分。"限放 1 个"留在 016 已有的 `ToolAreaRules`，因为那里就是"服务端问放入规则"的唯一入口。

## Complexity Tracking

无违反项，不需要登记。
