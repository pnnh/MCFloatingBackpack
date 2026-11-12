# Implementation Plan: 更多分类扩展（9 个新类别）

**Branch**: `051-more-categories` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

## Summary

给 035～038 的"分类扩展"再加 9 个类别（建筑方块、自然方块、植物、装饰、怪物掉落、容器收纳、交通运输、附魔与书、肉与鱼），总数 10 → 19（另有不分类的通用扩展）。035 的设计让它**主要是数据**：类别 id 同时是行的类别、018 的手动标签预设、数据标签 `mcfloatingbackpack:extension/<id>` 的最后一段、扩展件物品 `<id>_extension` 的前缀。所以：

1. **一处追加**：`ExtensionCategories.IDS` 末尾追加 9 个 id（顺序已发布后固定，只能追加）。`ModItems`、`ItemCategoryMatcher`、`ClientPlacement.categoryTagsBound`、`RowReconciler` 都是遍历 `IDS` / `ALL` 的，**不用改循环**。
2. **一处新规则**：`ItemCategoryMatcher` 为 `enchant` 多认一个数据组件 `STORED_ENCHANTMENTS`（附魔书），与 `food` 认食物组件、`potion` 认药水组件同一种写法，只多一个分支。
3. **纯数据**：9 个数据标签 JSON（内容见 [contracts/categories.md](./contracts/categories.md)，已对照 1.21.1 逐条查证）、9 个模型、9 张手绘贴图、9 个配方、中英文语言键。
4. **界面表**：`CategoryStyle.EDGE` 加 9 行颜色；`PresetTags.IDS` 追加 8 个新预设（`plant` 已有）；`CategoryNames` 走 `PresetTags.langKey`，只要语言键齐全。
5. **数据修订 2 → 3**（理由见 [research.md](./research.md) R6），协议号不变（19）。
6. **测试**：更新写死"10 个类别"的断言；新增 9 类的匹配抽查（纯逻辑用"展开后的物品集合"在资源测试里核对标签 JSON 的条目）、颜色区分度、资源逐项断言。
7. **游戏里验证**（宪法 V）：容器自动验证，每类放进一行、收对、拒错、颜色与幽灵图标、黄点；记录没验证的。

不改协议、不加网络包、不加配置、不加依赖、不改 035 的任何规则（`enchant` 的组件分支是新类别的匹配依据，不是规则变化）。

## Technical Context

**Language/Version**: Java 21；Python 3（开发期脚本，Pillow 已装）

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 无新增字段；行数据 `extension_rows` 里的类别 id 是字符串，新 id 直接存；`DataRevision` 2 → 3

**Testing**: JUnit 5；标签/配方/语言键/贴图用资源测试（沿用 `ExtensionTagsResourcesTest`、`CategoryExtensionResourcesTest` 的正则与字符串做法，没有 JSON 库）；颜色区分度用纯逻辑测试（`CategoryStyleTest`）；游戏行为在客户端会话里验证

**Target Platform**: 客户端 + 服务端；开发机 macOS，AI 验证用 Docker 容器 `mcfb-c1`

**Constraints**: 宪法 III（一个 feature，只做这 9 个类别）、V（客户端会话验证）、VI（图集度量：9 张 16×16 进共享图集，前后各量一次）、VII（无新依赖；标签条目只用原版与 NeoForge 的 `c:` 标签，全部 `required: false` 的写法见 contracts）

**Scale/Scope**: 9 个标签 JSON、9 个模型、9 张贴图、9 个配方、语言键 9×2（物品名）+ 8×2（预设名，`plant` 已有）；改 `ExtensionCategories`、`PresetTags`、`CategoryStyle`、`ItemCategoryMatcher`、`DataRevision` 与约 12 个测试里写死的数字；新增 1 个手绘脚本、1 个测试类

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面结构，只是新增行的底色 | PASS |
| II. Server Owns Items | 放入规则仍在服务端（035）；匹配用服务端能读到的标签与数据组件 | PASS |
| III. One Feature At A Time | 只做类别数据、物品、配方、颜色、预设；不做新机制 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方模组；标签里只放原版与 NeoForge 约定标签 | PASS |
| V. Verify In A Client Session | quickstart 要求容器里逐类验证；颜色是否好看只能人看，记为需用户确认 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 9 张 16×16 进共享图集：**前后各量一次**（`check_atlas_budget.sh`），量不了写"未度量" | PASS（待度量） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖、无第三方类型 | PASS |

设计后复核：无变化，无违规项。

## Project Structure

### Documentation (this feature)

```text
specs/051-more-categories/
├── plan.md
├── research.md          # 标签查证、覆盖率、重叠、颜色、预设容量、修订、贴图
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── categories.md            # 9 个类别的标签内容（逐条）与已知覆盖缺口
│   └── recipes-and-assets.md    # 配方、颜色、贴图图案、语言键
├── checklists/requirements.md
└── tasks.md                     # /speckit-tasks 产出
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/extension/ExtensionCategories.java   # 改：IDS 追加 9 个；类注释"ten"改数量
├── inventory/extension/ItemCategoryMatcher.java   # 改：enchant 认 STORED_ENCHANTMENTS
├── inventory/tags/PresetTags.java                 # 改：IDS 追加 8 个预设
├── inventory/revision/DataRevision.java           # 改：CURRENT 2 → 3，类注释加一句
└── client/panel/CategoryStyle.java                # 改：EDGE 加 9 行

src/main/resources/
├── data/mcfloatingbackpack/tags/item/extension/{building,natural,plant,decor,mobdrop,storage,transport,enchant,meat}.json   # 新
├── data/mcfloatingbackpack/recipe/{…}_extension.json                                                                          # 新
├── data/mcfloatingbackpack/tags/item/never_trash.json                                                                         # 改（若现有扩展件在里面则同样加）
├── assets/mcfloatingbackpack/models/item/{…}_extension.json                                                                   # 新
├── assets/mcfloatingbackpack/textures/item/{…}_extension.png                                                                  # 新
└── assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json                                                                          # 改

design/demo/tools/make_category_extension_icons.py   # 改：order 与图案表加 9 个（开发期，不进发行包）
src/test/java/…                                      # 改约 12 个；见 research R9
```

**Structure Decision**: 不新增类，不新增包；沿用 035～038 的目录与命名。

## Complexity Tracking

无宪法违规项。
