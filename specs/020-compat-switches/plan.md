# Implementation Plan: 第三方模组兼容开关与客户端配置，并把依赖规则变成自动检查

**Branch**: `020-compat-switches` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-compat-switches/spec.md`

## Summary

宪法 2.1.0 原则 VII 要求：最低依赖只有 Minecraft 与 NeoForge；对其它模组的主动互动必须有"模组 id → 档位（FULL/GENERIC/OFF）"的设置；依赖规则要有自动检查。研究阶段（见 [research.md](./research.md)）读 NeoForge 21.1.251 与本仓库源码得出的方案：

1. **一个客户端配置项 `modLevels`**：字符串列表，每条 `modid=LEVEL`，默认空；用 NeoForge 的 `ModConfigSpec`（`CLIENT` 类型），在 `MCFloatingBackpack` 构造里注册；元素校验器**接受任何字符串**，坏条目由我们自己逐条解析、记日志、忽略（否则 `ModConfigSpec` 会因为一个坏元素把整个列表纠正回默认）。
2. **纯逻辑 `CompatPolicy`**：把配置字符串列表解析成"模组 id → 档位"，给出 `levelOf(modId)`、`allowsInteraction(ownerModId)`（OFF 为假）、`specificAdaptation(modId)`（只有 FULL 为真）；原版（`minecraft`）永远 FULL；解析同时产出警告列表（非法档位、非法 id、重复、`minecraft`）。无 Minecraft 依赖，可单测。
3. **实时生效靠"按列表引用缓存"**：`CompatConfig.policy()` 每次取 `modLevels.get()`（`ModConfigSpec` 在配置重载前返回同一个列表对象），引用变了才重新解析并记警告；配置未加载时返回默认（全部 FULL）。不需要订阅重载事件，也不需要重启。
4. **界面归属 `MenuOwner`**：容器界面的菜单类型在注册表里的**命名空间**就是所属模组 id；没有菜单类型（玩家物品栏菜单、创造物品栏的菜单）或取不到时视为 `minecraft`（原版）；其它模组在原版菜单类型上开的界面也归原版。客户端和服务端共用同一个函数。
5. **OFF 的落点只有一处**（2026-11-05 修订：现在有两个层次，见下面的"修订"一节）：`ContainerScreenCompat.isOpenContainerScreen`（它已经被 `hostsPanel`、`HostScreenManager.kindOf`、渲染的光标来源、`screenOpen` 标志、`ScreenEvent.Opening` 的合并光标请求共用）在"所属模组为 OFF"时返回 false——于是该界面自动成为 019 的"其它界面"：面板隐藏、不画、不拦截、不发合并请求，关闭后恢复。另外两处需要补：`QuickTransferInput.supports`（Ctrl+点击容器槽位的入口，自己判断界面类型）也要过同一个判断；三个手势类的 `invalid` 增加"界面已不再承载面板"的取消条件（运行中改配置时安全取消）。
6. **GENERIC 与 JEI 适配**：JEI 插件在每次被问到区域时问 `CompatConfig.policy().specificAdaptation("jei")`，为假返回空列表；除此之外没有别的专用适配，所以容器类模组的 GENERIC 与 FULL 行为相同——GENERIC 只关闭"我们为该模组写的专有代码"，不决定面板能否显示或能否交换物品。
7. **热键提示与首次挂载日志**：在 OFF 的容器界面上按面板热键，`onKey` 里给一条 action bar 提示（带模组 id）；面板首次挂在某个非原版模组的容器界面上时（每个模组每次启动一次），日志记一行"若有冲突可设 `<modid>=OFF`"。
8. **服务端恒真门禁**：纯类 `ServerCompatGate.allows(Action, ownerModId)` 现阶段恒返回 true，带单元测试；接在服务端碰到别的模组菜单的 5 个入口（见 research Decision 7）。不改协议，不加服务端配置。
9. **配置界面**：`registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)` 一行注册，模组列表里出现配置按钮；用 `translation(...)` 提供中英文标题与说明（三档含义写进说明）。
10. **依赖规则的自动检查**：测试里的纯 Java 检查器 `DependencyRules`（三类检查：`mods.toml` 的依赖都是 optional；白名单之外的 import 只能在 `compat/<modid>/`；`build.gradle` 里第三方坐标只能出现在 `compileOnly`/`localRuntime`），用内联夹具做每类至少一个反例，再对真实仓库文件各跑一遍。

范围：配置与策略、客户端接入、JEI 适配开关、服务端恒真门禁、配置界面、依赖检查、文档。服务端配置、新适配、mixin 决定、`CompatTarget` 清理、按键让出开关都不做。

## 修订（2026-11-05，第一轮客户端验证之后）

用户验证后提出四点，结论如下：

1. **GENERIC 的含义（先误解后澄清）**：用户先问"为什么 GENERIC 下 Ctrl+点击仍能转移"，我据此一度把 GENERIC 改成"显示但不交换物品"并改了代码；用户随后补充说明：FULL 是专用适配（我们为该模组写的专有代码，代表主动兼容，可视为功能增强）；GENERIC 是没有专用适配（关闭这些专有代码，没写过的模组与 FULL 一样），**它并不决定**面板能否显示、能否与菜单交换物品，那取决于该模组自己的实现；OFF 是面板不显示，二选一。**已撤销**"不交换物品"的改动，代码回到原设计（`allowsInteraction` 只管 OFF，没有 `exchangesItems`/`usesMenuCursor`/`blocksItemExchange`），宪法改成 2.1.1（只补充说明，不改规则）。
2. **提示改到聊天区**：`FloatingPanelEvents.chat(...)` 用 `Minecraft.gui.getChat().addMessage` 把消息写进玩家自己的聊天（只有本人可见，不联网，按 T 可再看）；OFF 的界面上按热键的提示改用它。
3. **配置错误提示**：`CompatPolicy.Warning(Problem, entry)` 结构化警告；`ConfigNotices`（纯逻辑队列：每条提示是语言键 + 一个参数，相同的只留一条，一次最多 5 条加一行"还有 N 条"）；`CompatConfig.policy()` 重新解析时把警告放进队列；`ConfigFileWatch` 监听 `ModConfigEvent.Loading/Reloading`，发现配置目录里有刚生成（10 秒内）的 `mcfloatingbackpack-client*.toml.bak` 就加一条"文件有错误，已备份为 X"；`FloatingPanelEvents.onClientTick` 在玩家进入世界后把队列写入聊天。
4. **配置"被重置"**：读 FML `ConfigTracker.loadConfig` 确认——读文件出现解析异常时它先 `backUpConfig`（改名为 `*.toml.bak`）、删除原文件、写回默认配置再加载；日志里正是 `Failed to load config … Attempting to recreate`，`.bak` 里能看到被存下来的写了一半的内容（少了引号）。这是 NeoForge 的行为，不是本模组清空了设置（见 research Decision 12）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`ModConfigSpec`、`ConfigurationScreen`、`IConfigScreenFactory`、`BuiltInRegistries.MENU`）；无新增依赖，不引入任何第三方模组类型

**Storage**: 客户端配置文件 `config/mcfloatingbackpack-client.toml`（NeoForge 生成）；无服务端数据、不改数据组件

**Testing**: JUnit 5 纯逻辑测试：`CompatPolicyTest`（解析、大小写、非法档位/id、重复、`minecraft`、默认、三档查询、原版固定）、`ServerCompatGateTest`（所有动作 × 各种归属都放行）、`DependencyRulesTest`（检查器的三类反例与真实仓库）；加客户端会话手测（quickstart，宪法 V），用 `runClientCompat` 验证三档

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组

**Performance Goals**: 策略查询每帧若干次，必须是 O(1)（按列表引用缓存，菜单类型到命名空间是一次注册表查找）；不新增渲染

**Constraints**:
- 不得引入新的必需依赖、不得新增 mixin；第三方类型只能在 `compat/<modid>/`（宪法 VII）
- 原版容器的支持不可配置；不得改变 011～019 在原版界面上的任何行为
- 不改网络协议（保持 `"8"`）；服务端门禁现阶段恒为真
- 配置里一个坏条目不得影响其它条目，也不得让游戏崩溃

**Scale/Scope**: 约 6 个新类（`CompatLevel`、`CompatPolicy`、`CompatConfig`、`MenuOwner`、`ServerCompatGate`、首次挂载日志的小类）；改动 `MCFloatingBackpack`、`ClientSetup`、`ContainerScreenCompat`、`QuickTransferInput`、三个手势输入类的 `invalid`、`FloatingPanelEvents`、JEI 插件、5 个服务端入口、语言文件；3 个新测试类；文档

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

对照宪法 **2.1.0**：

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不新增任何 Screen；OFF 复用 019 的"其它界面"语义（宿主让位、面板隐藏），不替换、不拦截任何其它界面 | PASS |
| II. Server Owns Items | 新增服务端门禁，所有碰到别的模组菜单的服务端入口都先问它（现阶段恒允许）；客户端配置明确不是安全边界；不改任何物品规则 | PASS |
| III. One Feature At A Time | 范围限定在兼容开关、客户端配置、恒真门禁、依赖检查；服务端配置、新适配、mixin、`CompatTarget` 清理明确不做 | PASS |
| IV. Vanilla Path Before Compat | 原版容器不可配置、行为不变；不引用任何第三方类型（除既有的 `compat/jei`） | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证，用 `runClientCompat` 测三档；菜单命名空间归属在真实背包模组界面上的表现、配置实时生效、列表项编辑登记为未验证直到做过 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增任何资源 | PASS（不适用） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 本 feature 就是它的落地：三档设置、原版不可配置、`compat/<modid>/` 边界、依赖规则的自动检查；不新增必需依赖 | PASS |

**Re-check after Phase 1 design**：设计阶段没有新增违反项。实现阶段要盯住的风险：

1. **`ModConfigSpec` 对列表元素的纠正**：元素校验器必须接受任何字符串，否则一个坏条目会让整个列表被纠正成默认值（research Decision 1，未单独验证，quickstart 里实测）。
2. **归属判断的准确性**：个别模组可能复用原版菜单类型或没有注册表键，此时归属为原版、不受配置影响——这是规格里写明的边界；真实精妙背包/旅行者背包界面的命名空间要在游戏里确认。
3. **运行中改配置**：当前正显示的界面刚好被改成 OFF 时，进行中的手势要安全取消、物品守恒（三个 `invalid` 的新条件）。
4. **JEI 的刷新时机**：JEI 何时重新询问"额外区域"决定 GENERIC 切换多久生效，需要实测。

## Project Structure

### Documentation (this feature)

```text
specs/020-compat-switches/
├── plan.md              # 本文件
├── research.md          # Phase 0：有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：配置项、档位、策略与归属、门禁、检查器
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   └── compat-config.md # Phase 1：配置文件格式、三档行为矩阵、客户端接入点、服务端门禁入口
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── MCFloatingBackpack.java            # 修改：构造函数接收 ModContainer，注册 CLIENT 配置
├── compat/
│   ├── CompatLevel.java               # 新增：FULL / GENERIC / OFF（纯，含 parse）
│   ├── CompatPolicy.java              # 新增：纯逻辑，解析 "modid=LEVEL" 列表并回答三个查询
│   ├── CompatConfig.java              # 新增：ModConfigSpec（CLIENT），policy() 按列表引用缓存并记警告
│   ├── MenuOwner.java                 # 新增：菜单类型注册表命名空间 → 模组 id（客户端/服务端共用）
│   ├── ServerCompatGate.java          # 新增：纯逻辑，恒允许的门禁 + Action 枚举
│   ├── AttachNotice.java              # 新增：首次挂在非原版模组界面上时记一行日志（每模组一次）
│   ├── ContainerScreenCompat.java     # 修改：isOpenContainerScreen 在所属模组为 OFF 时返回 false
│   └── jei/FloatingBackpackJeiPlugin.java  # 修改：每次询问时读 specificAdaptation("jei")
├── client/
│   ├── ClientSetup.java               # 修改：注册 IConfigScreenFactory
│   ├── FloatingPanelEvents.java       # 修改：onKey 在 OFF 的容器界面上给提示；Opening 时调用 AttachNotice
│   └── panel/
│       ├── QuickTransferInput.java    # 修改：supports 过同一判断；invalid 加"界面已不承载面板"
│       ├── PanelDragInput.java        # 修改：invalid 加同一条件
│       └── PanelSelectionInput.java   # 修改：invalid 加同一条件
└── network/
    ├── BackpackTakeFromContainerPayload.java   # 修改：取出前问门禁
    ├── BackpackQuickTransferPayload.java       # 修改：向打开的容器投递前问门禁
    ├── MergePanelCursorPayload.java            # 修改：合并前问门禁
    ├── BackpackClickPayload.java               # 修改：使用菜单携带物的分支前问门禁
    └── BackpackCursorGesturePayload.java       # 修改：同上

src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json   # 修改：配置界面文字与热键提示

src/test/java/pnnh/mcfloatingbackpack/
├── compat/CompatPolicyTest.java
├── compat/ServerCompatGateTest.java
└── rules/DependencyRulesTest.java     # 含纯 Java 检查器（测试源码里）与夹具

specs/008-…/、docs/MCFloatingBackpack.md   # 修改：加 020 说明（配置项与三档含义）
```

**Structure Decision**: 沿用单模块。策略、档位、门禁是不依赖 Minecraft 的纯类；与游戏相连的部分（配置、归属、日志）单独成类，同 011～019 的做法。**不改** `ContainerScreenCompat` 里没人用的 `CompatTarget`/`identify`（登记为整理项）；**不改**网络协议；OFF 的判断收敛在 `isOpenContainerScreen` 一处加 `QuickTransferInput.supports` 一处。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违反项，不需要填写。
