# Implementation Plan: 分类扩展：10 个扩展件物品

**Branch**: `036-category-extension-items` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

## Summary

给 035 的数据与规则配上 10 个扩展件物品，并让它们真的起作用（设计依据见 [research.md](./research.md)）：

1. **一个物品类、十个实例**：`CategoryExtensionItem(String category)`（`stacksTo(1)`，不做任何事，只有一条物品提示）；`ModItems` 按 `ExtensionCategories.IDS` 的顺序循环注册 `<类别>_extension`，创意栏照同一顺序列出。
2. **一行代码接上规则**：构造函数里 `ExtensionPacks.register(stack -> stack.getItem() instanceof CategoryExtensionItem item ? item.category() : null)`（两端都执行）。035 的核对、放入检查、工具区上限检查从此有了真实的扩展件。**这是本 feature 唯一碰到规则层的地方，规则本身一行不改。**
3. **资源（纯数据）**：10 个 `models/item/<id>.json`（`minecraft:item/generated`）、10 张 `textures/item/<id>.png`（16×16，RGBA）、10 个 `data/mcfloatingbackpack/recipe/<id>.json`（有序配方，四角铁锭 + 5 格核心材料）、中英文语言键（10 个名字 + 1 条通用提示）。
4. **贴图手绘**：沿用 024 的做法——一个 Python 脚本逐像素画出 10 个图标（统一的铁灰底板 + 各自的类别图案），`design/demo/tools/make_category_extension_icons.py`；脚本是来源，没有源图；用 `Read` 看放大预览核对。
5. **补 035 的一个缺口**：放不进去（行总数已达 35）时服务端给玩家发一条 actionbar 提示 `message.mcfloatingbackpack.limit.extension_rows`（`BackpackClickPayload` 里加一行）。
6. **配方的第 5 件**（盔甲、红石、唱片）：皮革、红石粉、青金石（草案，待用户确认；改的话只动 3 个 JSON 与测试里的一张表）。
7. **测试**：`CategoryExtensionResourcesTest`（模型、贴图格式、配方、语言键与 `ExtensionCategories.IDS` 一一对应，配方两两不同，图标两两不同）；`ModItems` 与创意栏的注册靠源码文本检查；真正的游戏行为（放入、行出现、转移……）只能在客户端会话里验证。

不改协议（仍是 17）、不新增网络包、设置项、命令、数据组件；不改 035 的任何规则。

## Technical Context

**Language/Version**: Java 21；Python 3（开发期脚本，Pillow 13.0.0 已装）

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 无新增（行数据的存储是 035 的）

**Testing**: JUnit 5 资源/文本检查（没有 JSON 库，沿用正则与字符串检查，同 `TagManagerResourcesTest`、`ExtensionTagsResourcesTest`）；物品类依赖 Minecraft 注册表，单元测试不加载它；游戏里的验证见 quickstart

**Target Platform**: 客户端 + 服务端；开发机 macOS

**Constraints**: 宪法 III（只做物品，不做界面）、V（客户端会话验证）、VI（图集度量）、VII（不引用第三方类型；配方只用原版物品与 `#minecraft:logs`、`#c:music_discs`）；协议 17 不变

**Scale/Scope**: 新增 1 个物品类、10 个模型、10 张贴图、10 个配方、语言键 11 条 × 2 语言、1 个脚本、1 个测试类；改 `ModItems`、`MCFloatingBackpack`（一行注册）、`BackpackClickPayload`（一条提示）

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面 | PASS |
| II. Server Owns Items | 行的增删与放入规则仍在服务端（035）；物品本身只是数据 | PASS |
| III. One Feature At A Time | 只做物品、贴图、配方、语言键与解析器注册；界面（037）不做；035 的规则不改 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方模组；配方只用原版物品与标签 | PASS |
| V. Verify In A Client Session | quickstart 要求客户端会话验证创意栏、合成、JEI、外观、放入功能区、行的出现与消失、035 未验证的各条路径；**含扩展行的面板外观是过渡期**，只验证功能 | PASS（已规划，限制已记录） |
| VI. Shared Resources Have A Budget | 新增 10 张 16×16 进共用图集：**完成后必须度量**（现状 2048×1024），度量不了写"未度量" | PASS（待度量） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖；配方不引用任何模组物品 | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/036-category-extension-items/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── recipes.md        # 10 个配方的版型与材料
│   └── resources.md      # 物品 id、模型、贴图、语言键、图标风格
├── checklists/requirements.md
└── tasks.md              # /speckit-tasks 产出
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── item/CategoryExtensionItem.java      # 新：category() + stacksTo(1) + 物品提示
├── item/ModItems.java                   # 改：循环注册 10 个、创意栏列出
├── network/BackpackClickPayload.java    # 改：扩展件放不进去时给玩家一条提示（035 的缺口，见 research R9）
└── MCFloatingBackpack.java              # 改：构造函数里 ExtensionPacks.register(...)

src/main/resources/
├── assets/mcfloatingbackpack/models/item/{food,tool,weapon,armor,ore,crop,wood,potion,redstone,disc}_extension.json   # 新
├── assets/mcfloatingbackpack/textures/item/{…}_extension.png                                                           # 新
├── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json                                                                    # 改
└── data/mcfloatingbackpack/recipe/{…}_extension.json                                                                    # 新

design/demo/tools/make_category_extension_icons.py                                                                       # 新（开发期，不进发行包）
src/test/java/pnnh/mcfloatingbackpack/resources/CategoryExtensionResourcesTest.java                                      # 新
```

**Structure Decision**: 物品类放在已有的 `item/` 包里，与 `TagManagerItem`、`TrashCanItem`、`AutoPickupItem` 并列；资源按已有的目录与命名；图标脚本放在 `design/demo/tools/`（同 `make_auto_pickup_icon.py`）。

## Complexity Tracking

无宪法违规项。
