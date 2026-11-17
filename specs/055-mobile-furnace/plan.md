# Implementation Plan: 移动熔炉

**Branch**: `055-mobile-furnace` | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

## Summary

新物品 `mobile_furnace` 放进右区、左键点它，服务端打开**原版熔炉界面**（`FurnaceMenu`），容器与进度来自这个工具物品自己带的"熔炉状态"；关掉界面后它"像在继续烧"，靠**补算**实现（设计依据与查证见 [research.md](./research.md)）：

1. **物品与规则**：`MobileFurnaceItem`（仿 `MobileCraftingTableItem`，什么也不做，只有提示）、模型、手绘贴图、配方（岩浆桶退回空桶）、语言键、`never_trash`、创造栏（移动工作台之后）；右区"各至多一个"复用 `ToolSingleRules`（服务端 `BackpackClickPayload` 与客户端预览 `FloatingPanelInput` 各加一条，提示键 `limit.mobile_furnace`）。
2. **复用 054 的"打开工具"请求**：`MobileTool` 加 `FURNACE = 1`；**不新增包，协议号保持 20**；`ToolOpenPlanner` 不改。`MobileTools.itemOf`、`open` 各加一个分支；`FloatingPanelInput` 点击处加一个分支。
3. **熔炉状态（物品上的数据组件）**：新组件 `mobile_furnace_state`（`FurnaceState`：三格、燃烧剩余与总时长、烧制进度与总时长、已烧出的配方计数、**该状态对应的游戏时间**）。存在**工具物品自己**身上，随物品走；没有这个组件 = 空熔炉。组件随背包内容一起经过物品的编码（磁盘、网络、快照、背包升级）——不需要给背包的保存路径加任何东西（research R3）。
4. **烧制引擎（纯逻辑，本 feature 的核心）**：`FurnaceEngine.advance(state, ticks, rules)`——对 `AbstractFurnaceBlockEntity.serverTick` 逐刻状态机的**原样移植**（去掉 `level.setBlock`），外加一个"跳过没有事件的一段刻"的快进：稳态每刻只是 `litTime--`、`cookProgress++` 之类的线性变化，直接一次跳 `k` 刻；事件刻（烧完一件、燃料用尽、重新点燃）仍逐刻处理。开销 = 事件个数，与经过多少刻无关（R2）。**界面开着时每刻调它（ticks=1），打开时补算也调它（ticks=经过的刻）**，所以只有一套烧制逻辑；用"逐刻 vs 快进"的随机对照测试保证一致（SC-001）。
5. **服务端（MC 层）**：
   - `MobileFurnaceMenu extends FurnaceMenu implements MobileMenu`：容器是 `FurnaceStateContainer`（3 格，复现 `AbstractFurnaceBlockEntity.setItem` 的"换了原料就重置进度"），`ContainerData` 是引擎状态的视图（复现 NeoForge 对 `litDuration > Short.MAX_VALUE` 的比例处理）；**把产物格换成自己的 `FurnaceResultSlot` 子类**，因为原版槽只对方块实体发经验（R5）；重写 `broadcastChanges`（先"推进到现在"再广播；一刻内被调多次时经过时间为 0，是空操作）、`stillValid`（按 B 的背包**仍是打开时的那只**（身份）且右区仍有移动熔炉）、`removed`（最后写回一次）。
   - `MobileFurnaceStore`：从背包右区找到第一个移动熔炉读状态；写回时 `BackpackGate.acquire` + 工作副本改工具物品 + `ref.saveInventory`（盖修订章、通知快照）。
   - **何时写回**：打开、关闭、**每次三格内容变化**（容器的 `setChanged`）、取走产物发完经验之后。**不做定期写回**：进度可由"写回的状态 + 时间戳"推出（R4），所以无需。
6. **经验**：容器记着"配方 id → 烧出个数"（在状态里）；自己的产物槽在取走时按原版公式发经验（含小数概率进位）、授予配方与触发 `triggerRecipeCrafted`，然后清零并写回。
7. **受保护背包（040）**：打开前 `BackpackGate.acquire`，被拒（更新版本保存的）就**不打开**并发一条提示（新键）；不写任何东西。
8. **版本**：**数据修订 5 → 6**（新的只能存在背包里的物品种类 + 工具物品上的新组件）；**协议号 20 不变**；同改写死 5 的 7 处历史断言（见 R7）。
9. **测试**：纯逻辑（引擎：逐刻对照随机测试、事件边界、状态编解码、`MobileTool` 字节映射、经验取整）、资源（物品、模型、贴图、配方、语言键、组件编码）、源码文本检查（接线、菜单子类的形状、两处右区限制、客户端点击、无 mixin 与反射、写回触发点）；游戏里（容器）验证打开、烧制、关闭后补算、拿走工具时关闭、退出再进入。

不做：不画熔炉格、不做高炉与烟熏炉、不 tick 背包里的熔炉、不在物品图标上画火苗、不对外自动化、不做每档限制。

## Technical Context

**Language/Version**: Java 21；Python 3（手绘脚本，Pillow）

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 工具物品上新增一个数据组件（随物品的编码存盘与同步）；不新增文件

**Testing**: JUnit 5；纯逻辑（引擎用一个假的"世界"：铁矿 200 刻、木板 300 刻、岩浆桶 20000 刻留下空桶……）+ 资源 + 源码文本检查；菜单与界面只能在客户端会话里验证

**Target Platform**: 客户端 + 服务端；开发机 macOS，AI 验证用 Docker 容器 `mcfb-c1`

**Constraints**: 宪法 I（打开的是原版界面，面板仍叠在上面）、II（打开、校验、烧制、写回都在服务端；客户端只说"我想开哪种"）、III（一个 feature）、V（客户端会话验证）、VI（1 张 16×16 进共享图集，前后各量一次）、VII（无新依赖、无 mixin、无反射）；**协议号不变（20）、修订 5 → 6**

**Scale/Scope**: 新增约 10 个类（纯逻辑：`FurnaceEngine`、`FurnaceRules`、`FurnaceCore`（可变状态）、`FurnaceState`（编码用的不可变记录，放 MC 层）；MC：`MobileFurnaceItem`、`MobileFurnaceMenu`、`FurnaceStateContainer`、`FurnaceResultSlot` 子类、`MobileFurnaceStore`、`McFurnaceRules`）、1 个数据组件、1 个模型、1 张贴图、1 个配方、语言键约 4 条 × 2 语言、约 6 个测试类；改 `MobileTool`、`MobileTools`、`ModItems`、`ModDataComponents`、`BackpackClickPayload`、`FloatingPanelInput`、`DataRevision`、`never_trash.json` 与 7 处写死修订 5 的测试

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 点工具打开的是**原版**熔炉界面（`MenuType.FURNACE`，客户端是原版 `FurnaceScreen`），面板不替换也不包装它 | PASS |
| II. Server Owns Items | 校验、打开、烧制、写回、经验都在服务端；请求只带一个字节；工具状态改的是服务端的物品 | PASS |
| III. One Feature At A Time | 只做移动熔炉 | PASS |
| IV. Vanilla Path Before Compat | 用原版 `FurnaceMenu`、原版熔炉配方（`RecipeType.SMELTING`，别的模组加的配方自动可用）、原版燃料表（`getBurnTime`，含 NeoForge 的燃料数据映射与事件） | PASS |
| V. Verify In A Client Session | 容器里验证打开、烧制、关闭后补算、拿走工具、退出再进入；手感待用户 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 新增 1 张 16×16：前后各量一次 | PASS（待度量） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖、无 mixin、无反射；不碰别的模组 | PASS |

设计后复核：无违规项。

## Project Structure

### Documentation (this feature)

```text
specs/055-mobile-furnace/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rules.md        # 引擎、状态、菜单、写回、经验、点击
│   └── resources.md    # 物品、配方、贴图、语言键
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── tool/MobileTool.java                         # 改：FURNACE = 1
├── tool/MobileTools.java                        # 改：itemOf、open 各加熔炉分支（open 先过 BackpackGate）
├── tool/furnace/FurnaceCore.java                # 新（纯）：可变的熔炉状态（三格 + 四个计数 + 配方计数）
├── tool/furnace/FurnaceRules.java               # 新（纯）：配方、燃料、剩余物、堆叠的查询接口
├── tool/furnace/FurnaceEngine.java              # 新（纯）：advance(core, ticks, rules)；逐刻移植 + 快进
├── tool/furnace/FurnaceExperience.java          # 新（纯）：个数 × 经验 → 整数部分与小数概率
├── tool/furnace/FurnaceState.java               # 新：物品上的组件记录与编码
├── tool/furnace/McFurnaceRules.java             # 新（MC）：FurnaceRules 的 ItemStack 实现（RecipeManager + getBurnTime）
├── tool/furnace/FurnaceStateContainer.java      # 新（MC）：3 格容器；setItem 的重置；变化回调
├── tool/furnace/MobileFurnaceMenu.java          # 新（MC）：FurnaceMenu 子类
├── tool/furnace/MobileFurnaceStore.java         # 新（MC）：读右区的状态、经 BackpackGate 写回
├── item/MobileFurnaceItem.java                  # 新
├── item/ModItems.java                           # 改：注册、创造栏
├── inventory/ModDataComponents.java             # 改：MOBILE_FURNACE_STATE
├── network/BackpackClickPayload.java            # 改：右区"各一个"再加一条
├── client/panel/FloatingPanelInput.java         # 改：点工具发请求；客户端预览的限制
└── inventory/revision/DataRevision.java         # 改：CURRENT = 6

src/main/resources/
├── assets/mcfloatingbackpack/models/item/mobile_furnace.json            # 新
├── assets/mcfloatingbackpack/textures/item/mobile_furnace.png           # 新（手绘）
├── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json                    # 改
├── data/mcfloatingbackpack/recipe/mobile_furnace.json                   # 新
└── data/mcfloatingbackpack/tags/item/never_trash.json                   # 改

design/demo/tools/make_mobile_tool_icons.py     # 改：加熔炉图标
src/test/java/…                                  # 新约 6 个；改 7 处写死修订 5 的断言与 MobileToolTest
```

**Structure Decision**: 熔炉比工作台大得多，放进 `tool/furnace/` 子包；纯逻辑（`FurnaceCore`、`FurnaceRules`、`FurnaceEngine`、`FurnaceExperience`）不碰 Minecraft 类型，可单测；MC 层只做接线与适配。

## Complexity Tracking

无宪法违规项。
