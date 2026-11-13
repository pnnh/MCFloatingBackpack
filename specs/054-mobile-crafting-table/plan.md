# Implementation Plan: 移动工作台

**Branch**: `054-mobile-crafting-table` | **Date**: 2026-11-13 | **Spec**: [spec.md](./spec.md)

## Summary

新物品 `mobile_crafting_table` 放进右区、左键点它，服务端打开一个**原版的合成界面**；工具不在右区了就由服务端关掉（设计依据见 [research.md](./research.md)）：

1. **物品与规则**：`MobileCraftingTableItem`（仿 `TrashCanItem`，什么也不做，只有提示）、模型、手绘贴图、配方、语言键、`never_trash`、创造栏（移动唱片机之后）；右区"各至多一个"复用 053 的 `ToolSingleRules`（服务端 `BackpackClickPayload` 与客户端预览 `FloatingPanelInput` 各加一条，提示键 `limit.mobile_crafting_table`）。
2. **通用的"打开工具"请求**：新的客户端到服务端的包 `OpenToolPayload(byte tool)`（只带"哪种工具"，不带任何物品或背包数据；协议 19 → 20）。种类枚举 `MobileTool`（本 feature 只有 `CRAFTING_TABLE = 0`，055 加 `FURNACE = 1`），未知的字节解码为空、服务端忽略。
3. **决策表（纯逻辑）**：`ToolOpenPlanner.decide(tool, toolInRightArea, openMenuTool) → OPEN | CLOSE | IGNORE`：种类未知或右区没有 → `IGNORE`；本种类的菜单已开着 → `CLOSE`；否则 `OPEN`（包括关掉别的菜单、换一种我们的菜单）。
4. **服务端（MC 层）**：`MobileTools`（`has(player, tool)`：按 B 的背包的右区有没有这个物品，只读，不走 `BackpackGate`；`open(player, tool)`：`player.openMenu(SimpleMenuProvider)`）；`MobileCraftingMenu extends CraftingMenu`：用 `ContainerLevelAccess.create(世界, 玩家位置)`（**不能用 `NULL`**）、**重写 `stillValid` 为"按 B 的背包的右区仍有移动工作台"**（服务端每 tick 询问，不满足就关）、实现一个小接口 `MobileMenu { MobileTool tool(); }` 让处理器认得"这是我们的菜单"。
5. **客户端**：`FloatingPanelInput` 的"点右区工具"那一段加一个分支：光标为空、左键、右区、`MOBILE_CRAFTING_TABLE` → `PacketDistributor.sendToServer(new OpenToolPayload(...))` 并返回 true；**没有页、没有新状态**。客户端界面是原版 `CraftingScreen`（原版菜单类型 `MenuType.CRAFTING`，客户端自己构造），面板与宿主界面按原有机制叠上与接回。
6. **版本**：协议 19 → 20；数据修订 4 → 5；同改写死 19 / 4 的历史测试（见 research R6）。
7. **测试**：纯逻辑（决策表、`MobileTool` 字节映射、`ToolSingleRules` 已有）、资源（物品、模型、贴图、配方、语言键）、源码文本检查（包的注册与协议 20、处理器的校验、菜单子类的形状、两处右区限制、客户端点击、没有 mixin 与反射）；游戏里（容器）验证打开、合成、再点关闭、拿走工具时关闭。

不做：不画合成格、不实现合成逻辑、不自动从背包取料、不做熔炉（055）、不做每档限制。

## Technical Context

**Language/Version**: Java 21；Python 3（开发期手绘脚本，Pillow）

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 无新增

**Testing**: JUnit 5；纯逻辑 + 资源 + 源码文本检查；菜单与界面只能在客户端会话里验证

**Target Platform**: 客户端 + 服务端（新包）；开发机 macOS，AI 验证用 Docker 容器 `mcfb-c1`

**Constraints**: 宪法 I（打开的是原版界面，面板仍是叠在上面的浮层，不替换、不包装）、II（打开与关闭、校验都在服务端；客户端只说"我想开哪种"）、III（一个 feature）、V（客户端会话验证）、VI（1 张 16×16 进共享图集，前后各量一次）、VII（无新依赖、无 mixin、无反射）；协议 19 → 20、修订 4 → 5

**Scale/Scope**: 新增约 8 个类（纯逻辑：`MobileTool`、`ToolOpenPlanner`；MC：`MobileTools`、`MobileMenu`、`MobileCraftingMenu`、`OpenToolPayload`、`MobileCraftingTableItem`）、1 个模型、1 张贴图、1 个配方、语言键约 3 条 × 2 语言、约 5 个测试类；改 `ModItems`、`BackpackNetwork`（注册）、`BackpackClickPayload`、`FloatingPanelInput`、`DataRevision`、`never_trash.json`、约 14 个写死协议号 19 的历史测试与几个写死修订 4 的测试

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 点工具打开的是**原版**合成界面，面板不替换也不包装它；面板仍叠在上面（与箱子界面一样） | PASS |
| II. Server Owns Items | 服务端校验"右区确有这个工具"并打开菜单；`stillValid` 在服务端；客户端的请求不带任何物品或背包数据 | PASS |
| III. One Feature At A Time | 只做移动工作台；熔炉是 055 | PASS |
| IV. Vanilla Path Before Compat | 纯原版 `CraftingMenu`；别的模组的配方与合成事件自动可用 | PASS |
| V. Verify In A Client Session | 容器里验证打开、合成、关闭；手感待用户 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 新增 1 张 16×16：前后各量一次 | PASS（待度量） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖、无 mixin、无反射 | PASS |

设计后复核：无违规项。

## Project Structure

### Documentation (this feature)

```text
specs/054-mobile-crafting-table/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rules.md        # 决策表、包、菜单、点击
│   └── resources.md    # 物品、配方、贴图、语言键
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── tool/MobileTool.java                    # 新（纯）：种类枚举与字节映射
├── tool/ToolOpenPlanner.java               # 新（纯）：OPEN / CLOSE / IGNORE 决策表
├── tool/MobileTools.java                   # 新（MC）：has、open
├── tool/MobileMenu.java                    # 新：我们的菜单的小接口
├── tool/MobileCraftingMenu.java            # 新（MC）：CraftingMenu 子类
├── network/OpenToolPayload.java            # 新：客户端到服务端的包与处理器
├── network/BackpackNetwork.java            # 改：注册、协议 "20"
├── network/BackpackClickPayload.java       # 改：右区"各一个"再加一条
├── item/MobileCraftingTableItem.java       # 新
├── item/ModItems.java                      # 改：注册、创造栏
├── client/panel/FloatingPanelInput.java    # 改：点工具发请求；客户端预览的限制
└── inventory/revision/DataRevision.java    # 改：CURRENT = 5

src/main/resources/
├── assets/mcfloatingbackpack/models/item/mobile_crafting_table.json            # 新
├── assets/mcfloatingbackpack/textures/item/mobile_crafting_table.png           # 新（手绘）
├── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json                           # 改
├── data/mcfloatingbackpack/recipe/mobile_crafting_table.json                   # 新
└── data/mcfloatingbackpack/tags/item/never_trash.json                          # 改

design/demo/tools/make_mobile_tool_icons.py     # 改：加工作台图标
src/test/java/…                                  # 新约 5 个；改约 14 个写死协议号 19 的、几个写死修订 4 的
```

**Structure Decision**: 新建 `tool/` 包放"移动工具"的服务端部分（055 的熔炉也进这里）；纯逻辑与 MC 层分开；包放进已有的 `network/` 包，与别的 payload 并列。

## Complexity Tracking

无宪法违规项。
