# Delivery: 通用扩展

**状态**：代码与自动化完成（2026-11-10）；**游戏里的验证：quickstart A、B（任务 T029、T030）用户 2026-11-10 确认功能没有问题；C、D、E（T031～T033：三段顺序、外观与文字、升级换档与回归）用户说之后再测，尚未做**。配方草案与成本观感用户没有单独表态，仍按草案。绘制与交互没有任何自动化测试能代替游戏里看一眼（宪法 V）。

## 基线与结果

| | 类数 | 用例数 | 失败 |
|---|---|---|---|
| 开始前（037 完成后） | 121 | 851 | 0 |
| 完成后 | 125 | 890 | 0 |

新增测试类：`ExtensionCategoriesTest`（`IDS` 不变、`ALL` = 10 + `general`、顺序）、`PlacedPacksTest`（分类扩展不变；通用扩展的左/右/下区、每区算一个、别档、光标、最多 2、与顺序无关）、`CategoryNamesTest`、`UniversalExtensionResourcesTest`（5 个模型/贴图/配方/语言键、配方两两不同且与分类扩展不同、接线文本）。修改的测试：`ExtensionRulesTest`（三段顺序，**唯一一处旧断言改动**：`withoutRowsTheSegmentsAreTheOldOrder` 里"两段"改为三段，注释写明"规则变更：通用行是第三段"；新增 5 个用例）、`RowReconcilerTest`（6 个通用扩展场景）、`CategoryStyleTest`（`general` 的样式）。035/036/037 的其它测试一行未改并通过。

## 做了什么

- **行数核对器 `RowReconciler` 的逻辑一行没改**（只把类别处理顺序的起始列表换成 `ALL`）；通用扩展的生效规则放在它之前的纯函数 **`PlacedPacks`**：分类扩展每个算一个任何位置；通用扩展只数同档、左/右区、每区至多一个、光标上的算一个，合计不超过 2。`BackpackInventory.placedPacks()`（全仓库唯一计数点）改为收集观测并调它。
- **`ExtensionCategories.GENERAL` 与 `ALL`**：`IDS` 保持 10 个（有数据标签、`<id>_extension` 物品、标签预设名），`general` 放在 `ALL` 末尾；`orderOf`、`isKnown`、核对顺序用 `ALL`。**与规格"IDS 末尾加 general"的字面有出入**（见 plan 的说明）：放进 `IDS` 会让 `ModItems` 多注册一个错的物品、让客户端"标签已绑定"检查永远失败。
- **匹配**：`ItemCategoryMatcher` 对 `general` 恒匹配非空物品（在标签查找之前）；客户端预测复用同一份规则，所以通用行自动恒放行，且 `ClientPlacement.categoryTagsBound` 仍遍历 `IDS`（10 个），不受 `general` 影响（`ClientPlacement` 一行没改）。
- **不拒绝**：`ExtensionPackRules.canPlace` 对通用扩展恒为真；`ToolAreaRules` 没改；超过行总数 35 时核对器不新增行（不拒绝）。
- **目标顺序三段**（对 035 规则的唯一变更）：`ExtensionRules.targetSegments` 返回 类别行 → 基础容量 → 通用行；`mainTargetOrder` 没有行时返回 `[空, 全部, 空]`。自动拾取、垃圾桶取回、从容器取物、快速转移都按"段列表"处理（核对过 `PickupInsert`、`ContainerToBackpack`、`TrashBin`、`moveIntoSegments`），调用点没改。
- **物品**：`UniversalExtensionItem`（带档位，类别 `general`，提示说明只对 X 背包生效、放左区或右区、每个区只算一个）；`CategoryExtensionItem` 去掉了 `final`；`ModItems.UNIVERSAL_EXTENSIONS` 注册 5 个 `<档位>_general_extension`，创意栏排在 10 个分类扩展之后；`ExtensionPacks.registerTier`/`tierOf`（两端都在 `MCFloatingBackpack` 里注册）。
- **资源**：5 个平面模型、5 张手绘贴图（`design/demo/tools/make_universal_extension_icons.py`，复用 036 脚本的铁灰底板与辅助函数；图案是"一个框里的 3×3 格子"，各档换主色，脚本即来源，没有用 ludo.ai / pixellab）、5 个配方（草案）、中英文语言键（5 个名字、物品提示、5 个档位名、类别名"通用"、通用行悬停说明）。
- **界面**：`CategoryStyle` 加 `general`（近白 `#F2F2F2`）；**用户第一次在游戏里看到后反馈"只有边框没有底色、和分类行差异大"**（近白 16% 叠在灰色槽底上几乎看不见，也没有空格图标）：通用行的底色不透明度加到约 29%（`FILL_ALPHA_GENERAL = 0x4A`，十个分类仍是 16%），空格里也画淡图标——**当前背包档位的那个通用扩展**（皮革背包画皮革通用扩展）。**调整后的样子还没看过。**`FloatingPanelRenderer` 的行悬停说明与黄点提示里的类别名改用新的 `CategoryNames`；通用行悬停写"通用行：可以放任何物品"。
- 协议仍是 17；没有新增网络包、设置项、命令、数据组件。

## 图标目视检查（T016）

看过 5 个图标的放大预览与"5 个并排在 10 个分类扩展旁边"的 18 像素格子大小预览：5 档靠主色分得出（皮革棕、铁白、金黄、钻石青、下界合金紫黑），与 10 个分类扩展的图案（面包、镐……）明显不同；一次出图，没有重画。**游戏里的观感未验证。**

## 图集（T027）

先把 `run/logs/latest.log` 备份为 `run/logs/latest.before-038.log`（度量脚本会删它），再度量：干净环境 **2048×1024，通过**（5 张新贴图没有改变图集尺寸）。启动到主菜单的日志 0 个 ERROR，没有与本 feature 的模型、贴图有关的警告（配方与标签要进世界后才加载，未验证）。

## 配方与成本（草案，等用户确认）

见 `contracts/items.md`。粗估：每一档都不低于分类扩展，两个同档通用扩展都低于同档升级（材料块 + 模板）；**最低两档只是略低**（24 vs 27、28 vs 31）。要调只改 `data/.../recipe/<档位>_general_extension.json` 与 `UniversalExtensionResourcesTest` 里的 `CORE` 表。

## 未验证（游戏里，等用户）

- 全部外观与手感：5 个物品的合成、创意栏、JEI；5 个图标在游戏里的观感；通用行近白底色与分类行、悬停高亮的可分辨度。
- 生效规则：左/右区、下区、同区第二个、别档、混放；**光标上的通用扩展在左右区之间挪动时行不跳、不丢物品**（`min(2, …)` 的算法只有单元测试）。
- 升级换档：旧档通用行隐藏、黄点、物品保留，换新档扩展后恢复。
- 三段顺序在转移、自动拾取、垃圾桶取回、从容器取物里的实际表现。
- 行总数 35 到上限时通用扩展不拒绝但不出行；快照、Curios/Accessories、创造模式、主机屏、旧背包回归。
- 配方成本的观感。

## 已知限制

- 升级后旧档通用扩展失效是设计的后果（"过渡"）；不生效时面板上没有额外提示，只有物品提示写明条件。
- 配方是草案；成本是粗估。
- 底色近白与悬停高亮（半透明白）有点像，悬停是瞬时的；通用行的底色强度与分类行不同（29% 对 16%），是为了在灰色上看得见。
