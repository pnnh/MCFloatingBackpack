# Implementation Plan: 第三个扩展工具——自动拾取（标签黑名单；与其它背包模组只做被动礼让）

**Branch**: `024-auto-pickup` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/024-auto-pickup/spec.md`

## Summary

新增物品"自动拾取"：放进**左侧区**后，玩家拾取地上物品时直接进浮动背包主区域，放不下的部分回落到玩家物品栏；左键点击展开一个覆盖主区域的**标签黑名单页面**（最多 64 个标签），命中黑名单的物品既不进背包也不进物品栏，留在地上；功能只对"按 B 会打开的那一只"背包生效；与精妙背包 / 旅行者背包同时拾取时**不检测、不依赖它们**，只比它们晚一步出手并尊重它们已处理的结果（2026-11-08 取消了最初的"主动检测并避让"，原因见 research Decision 6）。研究阶段（见 [research.md](./research.md)）读源码与字节码得出的方案：

1. **接管点是 `ItemEntityPickupEvent.Pre`（`EventPriority.LOW`）**：读 NeoForge 21.1.251 的 `ItemEntity.playerTouch` 得知，`setCanPickup(TRUE)` 只绕开延迟与物主检查、`inventory.add` 仍会执行，所以我们在事件里**自己完成搬运**：`QuickTransferLogic.moveInto` 塞进主区域 → 缩小实体栈 → `take` / 统计 → 实体空了 `discard` 并 `FALSE`；没搬空就保持默认让原版回落进物品栏。命中黑名单 → `FALSE`。精妙背包与旅行者背包也在这个事件里工作，所以这就是和它们"争抢"的地方。
2. **激活背包每次拾取实时算**（`BackpackRef.resolve`，与按 B 同一套）；**预检直接读物品上的 `BackpackContents.tools().left()`**，通过之后才建可变的 `BackpackInventory`；没有服务端会话状态（FR-012a）。
3. **黑名单 = `BackpackContents` 的第六个字段 `pickup_blacklist`**（`PickupBlacklist`：≤ 64 个、满足 018 `TagRules`、去重）；`composite` 编码字段因此**用满 6 个**（登记）。单独发 `BackpackPickupBlacklistPayload` 同步，修改走 `BackpackPickupBlacklistOpPayload`（服务端核对"该背包左侧区确有自动拾取"）。
4. **命中 = 该背包的标签（018 的 `ItemTags`）∩ 黑名单**，物品分类 = 物品注册 id。
5. **黑名单页面复用 018 的纯组件**（`TagPanelLayout`、`TagTextInput`、`TagMatcher`、`TagRules`、`PresetTags`），新建平行的 `client/panel/pickup/` 状态/上下文/输入/渲染，**不改 018 的类**；与标签页**互斥**、与垃圾桶面板可共存；触点逐条列在 research Decision 5 与 [contracts/pickup-ui-compat.md](./contracts/pickup-ui-compat.md)。
6. **左侧区限放 1 个**：`ToolAreaRules.canPlace` 增加 `isAutoPickup` 谓词，`LEFT` 分支生效；服务端唯一权威调用点在 `BackpackClickPayload`，客户端 `refusedByLimit` 只做体验。
7. **对其它背包模组只做被动礼让**：`EventPriority.LOW`（晚于它们的普通优先级）+ 事件已是 `FALSE` 时不插手；`AutoPickupDecision.decide(toolActive, blacklisted)` 只有 `VANILLA`/`BLOCK`/`TAKE` 三种结果。**不检测、不引用它们的任何类型、不新增依赖/网络包/配置含义**（它们的内部类不是公开 API，版本一变就失效）。多个背包同时拾取时各拿多少不保证，只保证不崩溃、不复制、不丢失（research Decision 6 的表格）。
8. **配方** `IEI / RHR / IDI`（4 铁锭、末影珍珠、2 红石、漏斗、钻石，中期难度）；**图标**一张 16×16 平面贴图（漏斗 + 珍珠），沿用 021 的"ludo.ai 生成 → 脚本缩成 16×16"。

范围：只做自动拾取本体、黑名单、限放、被动礼让；主动避让、磁力吸附、白名单、拾取批处理、第三方模组之间的冲突都不做（见 spec Out of Scope 与 research 的登记项）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`ItemEntityPickupEvent`、数据组件与编码、`PacketDistributor`、数据驱动配方、`ScreenEvent`、`GuiGraphics`）。**没有新增依赖，不涉及第三方模组**

**Storage**: 物品数据组件 `BACKPACK_CONTENTS` 新增可选字段 `pickup_blacklist`；界面状态（页面展开等）只在客户端会话里，不持久化

**Testing**: JUnit 5 纯逻辑测试：`PickupBlacklist`（上限、清洗、匹配、幂等）、`PickupBlacklistRequest`（校验）、`AutoPickupDecision`（决策表全部组合）、`ToolAreaRules`（左侧区）、`PickupPanelModel`、拾取搬运的守恒（用 `StackOps` 假栈跑 `moveInto` 的"全放下 / 部分放下 / 放不下"）；资源测试：配方 JSON、模型、两份语言文件的键；`DependencyRulesTest` 照常通过；加客户端会话手测（quickstart，宪法 V）

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（物品 + 存储 + 网络 + 服务端事件 + 客户端界面与输入 + 可选兼容）

**Performance Goals**: 每个拾取事件在"无背包/无自动拾取"时只做一次 36 格遍历与一次数据组件读取；通过预检才建可变背包；一次拾取至多 1～2 个格子同步（见 research Decision 2、11）

**Constraints**:
- 所有拾取的去向、黑名单的修改、左侧区放入规则由服务端判断；整个请求要么全成要么零变化；物品守恒（宪法 II）
- 不引用任何第三方模组的类型或数据（宪法 VII；`DependencyRulesTest` 照常通过）；与精妙背包/旅行者背包同时拾取时不崩溃、物品守恒
- 011～023 的行为不变；唯一的有意变化是"左侧区限放 1 个自动拾取"与"标签页和黑名单页互斥"
- 只新增一张 16×16 物品贴图（宪法 VI）；协议版本升到 `"12"`；`BackpackContents` 的 `composite` 字段用满 6 个

**Scale/Scope**: 约 10 个新类（物品、`PickupBlacklist`、`PickupBlacklistCodecs`、`PickupBlacklistRequest`、`AutoPickupDecision`、`PickupInsert`、`AutoPickupEvents`、两个网络包、`PickupPanelState/Context/Model/Input/Renderer`）；改动 `BackpackContents`、`BackpackInventory`、`BackpackUpgradeRecipe`、`BackpackNetwork`、`BackpackClickPayload`、`ToolAreaRules`、`ClientBackpackView`、`FloatingPanelInput`、`FloatingPanelRenderer`、`FloatingPanelEvents`、`DeleteKeyInput`、`TagPanelState`（仅互斥）、`ModItems`、`MCFloatingBackpack`；约 7 个新测试类；资源：配方、模型、贴图、语言

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 黑名单页面和标签页一样是叠层，画在同一条渲染路径里，输入走同一组 `ScreenEvent`；**不新增 Screen**，不替换、不包装、不拦截任何界面 | PASS |
| II. Server Owns Items | 拾取去向（含回落）、黑名单增删、左侧区放入规则全部在服务端算；客户端只发"增删 + 标签名"；背包保存成功后才缩小实体栈，每次执行后"进背包 + 实体剩余 = 原数量" | PASS |
| III. One Feature At A Time | 只做自动拾取本体、黑名单、左侧区限放、被动礼让；主动避让、磁力吸附、白名单、拾取批处理明确不做 | PASS |
| IV. Vanilla Path Before Compat | 拾取、黑名单、页面、配方全部只用原版；不引用任何第三方模组；原版路径（原版拾取 → 物品栏）在不满足条件时原样保留 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证（A～H 组，含 `runClientCompat` 的同时拾取、激活背包、背包满回落、互斥、持久化、旧存档）；Windows/Linux 无法验证，登记为未验证；关于精妙背包/旅行者背包的"读字节码得出"的行为登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 只新增一张 16×16 物品贴图（物品图集，不是共用方块图集），页面全是 `fill` 与字体；实现完成时按 021 的做法度量一次图集（先 `pgrep`，度量不了就写"未度量"）。**对所有玩家新增的每个拾取事件判断**，实现阶段做一次粗测并记入 DELIVERY，测不了就写"未测量"（research Decision 2、Q38） | PASS（已规划） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 必需依赖不变；**本 feature 不引用、不检测任何第三方模组，不新增依赖、`compat/` 代码、网络协议里的避让开关或 020 配置的新含义**；对其它背包的礼让只是用 `LOW` 优先级与"事件已处理就跳过"，不属于"对另一个模组的主动互动" | PASS |

**Re-check after Phase 1 design**：没有违反项。实现阶段要盯住的风险：

1. **与第三方背包同时拾取的表现是读字节码得出的，未验证**：精妙背包与旅行者背包都在 `Pre` 事件里拿，旅行者不检查别人是否已设置 `FALSE`；另有按范围吸取、不经过事件的路径。只保证不崩溃、守恒；各拿多少不保证；已登记为已知限制（research Decision 6）。
2. **拾取接管的细节**：不触发 `ItemEntityPickupEvent.Post`、部分搬运时 `take` 可能响两次、统计/成就与原版的差异（research Decision 1、仍不确定项 3/4）。
3. **高频拾取的同步成本**：每次拾取会让背包物品栈重发一次（本模组所有写路径已有的行为，但拾取比点击频繁得多）；实现阶段粗测，超标则另开"拾取批处理"（research Decision 11）。
4. **`BackpackContents` 编码字段用满 6 个**：之后再加字段必须先改手写编码；写进 DELIVERY 给后来者的提醒。
5. **互斥与既有触点多**：黑名单页面要在输入、渲染、键盘、滚轮、Delete 键、每 tick 收起检查等约十处接入；漏一处就会出现穿透或键盘冲突（research Decision 5 列了清单，tasks 逐条落实）。

## Project Structure

### Documentation (this feature)

```text
specs/024-auto-pickup/
├── plan.md              # 本文件
├── research.md          # Phase 0：有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：PickupBlacklist、背包数据字段、决策表、会话状态、拾取流程、资源清单
├── quickstart.md        # Phase 1：客户端验收步骤（A～H）
├── contracts/
│   ├── pickup-network-storage.md   # Phase 1：数据组件、协议 12、两个包、服务端拾取流程
│   └── pickup-ui-compat.md         # Phase 1：展开/收起与分派、页面渲染与输入（原"避让开关/compat"两节已取消）
├── checklists/requirements.md
└── tasks.md             # Phase 2（/speckit-tasks 生成，本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── MCFloatingBackpack.java                      # 改：注册服务端拾取事件（LOW 优先级）
├── item/
│   ├── AutoPickupItem.java                      # 新：普通物品，堆叠 1
│   └── ModItems.java                            # 改：注册 auto_pickup，创意栏
├── inventory/
│   ├── ToolAreaRules.java                       # 改：LEFT 限放 1（新谓词）
│   ├── BackpackContents.java                    # 改：第 6 个字段 pickupBlacklist，hasAutoPickup()
│   ├── BackpackInventory.java                   # 改：pickupBlacklist()/set，hasAutoPickup()，fromContents/toContents
│   ├── recipe/BackpackUpgradeRecipe.java        # 改：升级保留 pickupBlacklist
│   └── pickup/
│       ├── PickupBlacklist.java                 # 新：≤ 64 标签的记录 + CODEC / STREAM_CODEC + 纯函数
│       ├── PickupBlacklistRequest.java          # 新：纯校验（op + tag）
│       ├── AutoPickupDecision.java              # 新：纯决策表
│       ├── PickupInsert.java                    # 新：纯搬运（复用 QuickTransferLogic.moveInto，收集被改的格子下标）
│       └── AutoPickupEvents.java                # 新：ItemEntityPickupEvent.Pre（LOW）、登出清除
├── network/
│   ├── BackpackPickupBlacklistOpPayload.java    # 新：客户端→服务端 ADD / REMOVE
│   ├── BackpackPickupBlacklistPayload.java      # 新：服务端→客户端 整份黑名单
│   ├── BackpackNetwork.java                     # 改：协议 "12"，注册，sendSession 后补发黑名单
│   └── BackpackClickPayload.java                # 改：放入规则调用点补谓词
└── client/
    ├── FloatingPanelEvents.java                 # 改：每 tick 收起、登出重置、键盘三事件、滚轮
    └── panel/
        ├── ClientBackpackView.java              # 改：黑名单镜像 + hasAutoPickup()
        ├── FloatingPanelInput.java              # 改：左区点击分派、限放提示（谓词）、主区按下消费、失焦
        ├── FloatingPanelRenderer.java           # 改：画页面；页面展开时跳过主区格子
        ├── DeleteKeyInput.java                  # 改：Delete 键对黑名单页让位
        ├── tags/TagPanelState.java              # 改：toggle 展开前收起黑名单页（互斥，仅此一行）
        └── pickup/
            ├── PickupPanelState.java            # 新：展开/编辑/下拉/待删/滚动/文本缓冲单例
            ├── PickupPanelModel.java            # 新：纯，canAdd / 计数
            ├── PickupPanelContext.java          # 新：标签显示名、下拉候选、布局（复用 TagPanelLayout）
            ├── PickupPanelInput.java            # 新：鼠标/滚轮/键盘，发 ADD/REMOVE
            └── PickupPanelRenderer.java         # 新：fill + 字体

src/main/resources/
├── assets/mcfloatingbackpack/models/item/auto_pickup.json      # 新：item/generated
├── assets/mcfloatingbackpack/textures/item/auto_pickup.png     # 新：16×16
├── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json           # 改：物品名、页面文字、限放提示、配置说明
└── data/mcfloatingbackpack/recipe/auto_pickup.json             # 新


src/test/java/pnnh/mcfloatingbackpack/
├── inventory/ToolAreaRulesTest.java                            # 扩展：LEFT
├── inventory/pickup/{PickupBlacklistTest,PickupBlacklistRequestTest,AutoPickupDecisionTest,PickupInsertTest}.java
├── client/panel/pickup/PickupPanelModelTest.java
└── resources/AutoPickupResourcesTest.java

design/demo/tools/make_auto_pickup_icon.py                      # 新：ludo.ai 原图 → 16×16（开发期素材，不随包）
```

**Structure Decision**: 沿用 018/021 的分包：纯规则在 `inventory/pickup/`，客户端界面在 `client/panel/pickup/`，与 `tags/`、`trash/` 并排；本 feature 不碰 `compat/`。服务端拾取事件放在 `inventory/pickup/AutoPickupEvents`（它用到 `BackpackInventory` 与网络，不属于 `compat/`）。**不改 018 的标签页类**（只加一行互斥），先复制结构、复用纯组件，等第三处需要同类面板时再抽象（整理项，不顺手做）。

## Complexity Tracking

无违反项，不需要登记。
