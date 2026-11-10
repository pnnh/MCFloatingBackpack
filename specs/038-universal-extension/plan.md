# Implementation Plan: 通用扩展

**Branch**: `038-universal-extension` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

## Summary

在分类扩展之上加"通用扩展"：5 个档位各一个物品，类别 `general`，一行 9 格放任何物品，只对同档背包的左区/右区生效（设计依据见 [research.md](./research.md)）。**核心发现：行数核对器 `RowReconciler` 一行不用改**——它只吃"每个类别放着几个"的数字（`Map<String,Integer>`），所以"在哪个区、哪一档、每区算一个、最多 2、光标"这些规则全部放进它**之前**的一个新纯函数里：

1. **`PlacedPacks`（纯逻辑，新）**：输入一组"扩展件观测"（类别、档位或 null、位置 = 左/右/下/光标）和背包档位，输出 `Map<类别,个数>`。分类扩展照旧（每个都算，任何位置）；通用扩展只数"同档、在左区或右区、每区至多算一个"，光标上的同档通用扩展算一个但总数不超过 2。`BackpackInventory.placedPacks()` 改成收集观测并调它——**这是全仓库唯一计数的地方**（核对器与 `ExtensionPackRules` 都从它取数）。
2. **`ExtensionCategories.GENERAL` 与 `ALL`（改）**：`IDS` **仍是 10 个分类类别**（数据标签、物品注册、预测里的"标签已绑定"检查、几个资源测试都遍历它，并假设每个 id 有标签文件/物品/贴图）；新增 `GENERAL = "general"` 与 `ALL = IDS + GENERAL`，`orderOf`、`isKnown`、`RowReconciler` 的类别顺序改用 `ALL`。与规格"加在 IDS 末尾"的字面略有出入，意图相同（顺序固定、`general` 最后），见 research D1。
3. **匹配**：`ItemCategoryMatcher.matches("general", 非空物品)` 恒为真（不查标签）；`ExtensionRules.canPlace` 因此对通用行不加限制，客户端预测同一份代码自动对通用行恒放行，且 `ClientPlacement.categoryTagsBound` 遍历的是 `IDS`（10 个），不会因为 `general` 没有标签而失败。
4. **目标顺序三段**：`ExtensionRules.targetSegments` 返回 `[类别行的格子, 基础容量的格子, 通用行的格子]`；所有调用点（自动拾取、垃圾桶取回、从容器取物、快速转移）都按"段列表"处理（已核对），不用改；`mainTargetOrder` 在没有行时也补成三段。035 里断言"两段"的测试要更新并说明为什么。
5. **物品**：`UniversalExtensionItem`（带档位，继承 `CategoryExtensionItem`，类别 `general`）注册 5 个 `<档位>_general_extension`；`ExtensionPacks` 增加档位解析器 `tierOf(stack)`；创意栏排在 10 个分类扩展之后；5 个模型、5 张手绘贴图（沿用 036 的 Pillow 脚本，换色）、5 个配方、中英文语言键。
6. **不拒绝**：通用扩展放哪里都允许；`ExtensionPackRules.canPlace` 对通用扩展恒为真（超过行总数 35 时由核对器不新增行，不拒绝）；`ToolAreaRules` 不动。
7. **界面（沿用 037）**：`CategoryStyle` 加 `general` 的底色（与 10 个分开）、**不画幽灵图标**；悬停说明与黄点里的类别名由一个新的 `CategoryNames.langKey(category)` 取（分类类别沿用标签预设键，`general` 用自己的键）；通用行的悬停说明用自己的文字，不套"X行：只能放X类物品"。
8. **协议不改（仍是 17）**：行里存的是类别字符串，`general` 不超长。

不新增配置项、命令、网络包、数据组件；035/036/037 已验证的分类行行为不变，**唯一的规则变更是目标顺序三段化**。

## Technical Context

**Language/Version**: Java 21；Python 3（Pillow，开发期贴图脚本）

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 无新增（行数据存储是 035 的）

**Testing**: JUnit 5。纯逻辑：`PlacedPacks`（区、档位、每区算一个、光标、最多 2）、`ExtensionRules` 三段顺序与 `general` 匹配、`ExtensionCategories` 的顺序、`CategoryStyle`、`CategoryNames`、`RowReconciler` 加一组通用扩展场景（升级换档、移到下区、隐藏与恢复）；资源与源码文本检查：5 个模型/贴图/配方/语言键与档位一一对应。游戏里的验证见 quickstart（宪法 V）

**Target Platform**: 客户端 + 服务端；开发机 macOS

**Project Type**: Minecraft 模组（NeoForge）

**Performance Goals**: 没有通用扩展时零额外开销；`placedPacks` 每次保存与载入各算一次，与现在同量级

**Constraints**: 宪法 II（行的增删与放入规则在服务端）、III（只做通用扩展，035 的规则只改目标顺序）、V、VI（5 张新贴图要度量图集）、VII；协议 17

**Scale/Scope**: 新增约 5 个类（`PlacedPacks`、`CategoryNames`、`UniversalExtensionItem`、小改 `ExtensionPacks`/`ExtensionCategories`/`ExtensionRules`/`ItemCategoryMatcher`/`RowReconciler`/`BackpackInventory`/`ExtensionPackRules`/`CategoryStyle`/`FloatingPanelRenderer`/`ModItems`/`MCFloatingBackpack`）、5 个模型、5 张贴图、5 个配方、语言键约 20 条 × 2、约 6 个测试类

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 界面仍是现有叠加层，不新增 Screen | PASS |
| II. Server Owns Items | 行的增删、生效规则、目标顺序都在服务端；客户端只预测（通用行恒放行），服务端裁判 | PASS |
| III. One Feature At A Time | 只做通用扩展；对 035 的规则只改目标顺序（写进规格、更新测试并说明原因）；不做搜索整理、不加类别以外的东西 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方模组；配方只用原版物品 | PASS |
| V. Verify In A Client Session | quickstart 覆盖合成、创意栏、放左右区与下区、同区第二个、别档、升级换档、三段顺序、隐藏恢复、黄点；不会用编译通过代替验证 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 新增 5 张 16×16 进共用图集，**必须度量**（现状 2048×1024，预算 2048）。度量脚本会删 `run/logs/latest.log`，**度量前先把它备份成 `latest.before-038.log`** | PASS（待度量） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖 | PASS |

## 对规格的两处说明

1. **`general` 不放进 `IDS`，放进新增的 `ALL`**（规格写"IDS 末尾加 general"）：`IDS` 被好几处遍历并假设每个 id 有数据标签文件、`<id>_extension` 物品、标签预设名；放进去会让 `ModItems` 多注册一个错的物品、`ClientPlacement` 的"标签已绑定"检查永远失败、几个资源测试失败。`ALL` 保留同样的意图（顺序固定、末尾）。
2. **配方的相对成本**：见 [contracts/items.md](./contracts/items.md) 的成本对照表，**是粗估**；最低两档的"两个通用扩展 < 升级"只是略低，用户看过后可调，只改配方文件与测试里的一张表。

## Project Structure

### Documentation (this feature)

```text
specs/038-universal-extension/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rules.md      # 生效规则、匹配、三段顺序、不拒绝
│   └── items.md      # 5 个物品、配方草案与成本对照、语言键、贴图与界面文字
├── checklists/requirements.md
└── tasks.md          # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/extension/
│   ├── PlacedPacks.java            # 新：观测 → 每类别"放着几个"（含通用扩展的区/档位/每区一个/最多 2/光标）
│   ├── CategoryNames.java          # 新：类别 → 显示名语言键（分类沿用标签预设键，general 自己的键）
│   ├── ExtensionCategories.java    # 改：GENERAL、ALL；orderOf/isKnown 用 ALL
│   ├── ExtensionRules.java         # 改：targetSegments 三段
│   ├── ExtensionPacks.java         # 改：tierOf（档位解析器）
│   ├── ExtensionPackRules.java     # 改：通用扩展恒允许
│   ├── ItemCategoryMatcher.java    # 改：general 恒匹配非空物品
│   └── RowReconciler.java          # 改：类别顺序用 ALL（逻辑不变）
├── inventory/BackpackInventory.java# 改：placedPacks 用 PlacedPacks；mainTargetOrder 补三段
├── item/{CategoryExtensionItem, UniversalExtensionItem, ModItems}.java  # 改/新
├── MCFloatingBackpack.java         # 改：注册档位解析器
├── client/panel/{CategoryStyle, FloatingPanelRenderer}.java             # 改：general 样式、不画幽灵图标、悬停与黄点用 CategoryNames
design/demo/tools/make_universal_extension_icons.py                      # 新（或在 036 脚本里加）
src/main/resources/
├── assets/mcfloatingbackpack/{models/item,textures/item}/<档位>_general_extension.*  # 各 5
├── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json
└── data/mcfloatingbackpack/recipe/<档位>_general_extension.json                      # 5
src/test/java/pnnh/mcfloatingbackpack/
├── inventory/extension/{PlacedPacksTest, CategoryNamesTest}.java        # 新
├── inventory/extension/{ExtensionRulesTest, RowReconcilerTest, RowSetTest}.java       # 改（三段、general）
├── client/panel/CategoryStyleTest.java                                    # 改
└── resources/UniversalExtensionResourcesTest.java                         # 新
```

**Structure Decision**: 生效规则是一个纯函数（`PlacedPacks`），不碰 035 已验证的核对器；物品与资源沿用 036 的结构；界面只补 `general` 的样式与文字。

## Complexity Tracking

无需要说明的违规。
