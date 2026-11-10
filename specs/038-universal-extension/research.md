# Research: 通用扩展

2026-11-10，读本仓库源码得出。"已核对"= 读过代码确认；"待游戏里验证"写进 quickstart。

## D1. `general` 放进 `ALL`，不放进 `IDS`

- **已核对**：`ExtensionCategories.IDS` 被这些地方遍历：`ModItems`（为每个 id 注册 `<id>_extension` 物品）、`ItemCategoryMatcher`（为每个 id 建数据标签键）、`ClientPlacement.categoryTagsBound`（十个标签是否都绑定到客户端）、`RowReconciler.categoriesOf`（类别处理顺序）、`ExtensionTagsResourcesTest`（每个 id 一个标签文件，期望的键集合 = IDS）、`CategoryExtensionResourcesTest`（每个 id 的模型/贴图/配方/语言键）、`CategoryStyleTest`、`RowSetTest`、`RowReconcilerTest`。
- 若 `general` 进 `IDS`：`ModItems` 会多注册一个 `general_extension`（我们要的是 5 个带档位的）；`ItemCategoryMatcher`/`ClientPlacement` 会要求一个不存在的数据标签（后者永远失败 → 预测永远放行，正是规格 FR-016 担心的）；多个资源测试要为 `general` 例外。
- **决定**：`IDS` 保持 10 个"分类类别"；新增 `GENERAL = "general"` 与 `ALL = IDS + GENERAL`。需要"全部类别"语义的地方改用 `ALL`：`orderOf`（`general` 排在最后，且是确定的位置而不是"未知"）、`isKnown`、`RowReconciler.categoriesOf`、`HiddenHint` 的排序（经由 `orderOf`）。需要"有标签文件的分类类别"的地方继续用 `IDS`。035 里"顺序一旦发布不能变"得到保持：10 个不动，`general` 在后。
- 035 的核对对"未知类别"本来就有处理（按出现顺序追加），所以即使不改 `categoriesOf`，`general` 也会被处理；改用 `ALL` 是为了让顺序明确且有测试。

## D2. 生效规则放在核对器之前：`PlacedPacks`

- **已核对**：`BackpackInventory.placedPacks()` 是全仓库唯一计数点：遍历三个工具区的全部格子加光标，用 `ExtensionPacks.countPlaced`（每个扩展件算 1，不分区不看档位）。它被 `reconciledRows()`（保存与载入时核对，调 `RowReconciler.reconcile(rows, placedPacks(), …)`）和 `ExtensionPackRules.canPlace`（行总数 35 的放入检查）使用；`RowReconciler` 只看 `Map<String,Integer>`。
- 通用扩展要的规则（同档、只数左右区、每区至多算 1、光标上的算一个但总数不超过 2）全是"怎样得到这张 Map"的问题。
- **决定**：新增纯函数 `PlacedPacks.count(List<Pack> packs, String backpackTier)`，`Pack(category, tier, place)`，`tier` 对分类扩展为 null，`place ∈ {LEFT, RIGHT, BOTTOM, CURSOR}`：
  - 分类扩展（`tier == null`）：每个算 1（任何位置），与现在完全一致；
  - 通用扩展：先丢掉 `tier != backpackTier` 的与 `place == BOTTOM` 的；剩下的：`left = 左区有任意个 ? 1 : 0`，`right` 同理；`cursor = 光标上有一个同档通用扩展 ? 1 : 0`；`general = min(2, left + right + cursor)`。
- **与顺序无关**（一个区里有任意个同档通用扩展，都只算一个），这满足规格"与它们在区里的先后无关"。
- `RowReconciler` 完全不动（逻辑不变），035 的核对测试继续有效；`RowReconcilerTest` 只加"通用扩展场景"的用例（该类别放着 0/1/2 个、升级换档后降到 0 时有物品的行隐藏、重新为 1 时恢复）。

## D3. 光标上的通用扩展

035 规定光标上拿着的扩展件仍算"放着的"，避免在功能区之间挪动时行跳到最后。通用扩展不知道会落到哪个区，所以：光标上的同档通用扩展算 1，但与左右区已有的合计不超过 2（`min(2, …)`）。例：左区有一个、光标上拿着一个 → 2（假定它将落到右区）；左右区都有、光标上再拿一个 → 仍是 2。若光标上的最后被放进左区第二格，核对后降回 1，那一行按 035 规则隐藏（有物品）或删除（空）。**待游戏里验证**：在左右区之间挪动通用扩展时行不跳、不丢物品。

## D4. 通用扩展永远允许放入

- **已核对**：`ExtensionPackRules.canPlace` 用 `placedPacks` 加上将放入的一个，经 `ExtensionRules.fits(rowSet, placedAfter, 35)` 检查是否超过 35 行；超过则拒绝。服务端 `BackpackClickPayload` 的工具区分支调它并给出 actionbar 提示。
- 通用扩展按规格"放哪里都允许、不生效就不生效"：**`canPlace` 对通用扩展（`tierOf(stack) != null`）恒为真**。若放了之后会超过 35 行，由核对器的"达到上限就不新增行"（`refused` 列表）处理，行不出现；这是规格 US6-3 的行为。
- 因此 `ToolAreaRules`（拒绝式的每区一个专属工具）**完全不动**，也没有新的限制提示语言键。

## D5. 目标顺序三段

- **已核对**：`ExtensionRules.targetSegments(...)` 返回 `[匹配的可见行格子, 基础容量格子]`；调用链：`BackpackInventory.mainTargetOrder → insertIntoMain → QuickTransferLogic.moveIntoSegments`；`AutoPickupEvents` 用 `PickupInsert.insert(..., mainTargetOrder(stack), ...)`；`BackpackTrashPayload` 的取回用 `TrashBin.restore(..., backpack::mainTargetOrder, ...)`；`BackpackTakeFromContainerPayload` 用 `ContainerToBackpack.take(..., backpack::mainTargetOrder, ...)`。**这些全部按 `List<int[]>` 段列表依次处理，不假设只有两段**（`PickupInsert`、`ContainerToBackpack`、`TrashBin` 的签名都是段列表）。
- **决定**：`targetSegments` 返回三段：`[类别行格子（不含通用行）, 基础容量格子, 通用行格子]`；`general` 行不在第一段。一个物品属于多个类别行时，第一段里仍按行的先后。`mainTargetOrder` 在没有可见行时原来返回 `[空, 全部]`，补成 `[空, 全部, 空]` 以保持三段的形状（消费方对空段无影响）。
- 点击、拖拽分配（玩家指定格子）不走这条路径，不受影响。
- 035 的 `ExtensionRulesTest` 里断言 `targetSegments` 返回"两段"与具体内容的用例要更新；每个更新写明原因是"规则变更：通用行是第三段"。没有通用行时第三段为空，所以其余断言只是多一个空段。

## D6. 物品：5 个，带档位

- **已核对**：`CategoryExtensionItem` 是 `final`，只有类别；提示一行用标签预设名。`ModItems` 按 `IDS` 循环注册 `<类别>_extension` 并放进 `CATEGORY_EXTENSIONS` 映射；创意栏按同样顺序列出。
- **决定**：`CategoryExtensionItem` 去掉 `final`，`UniversalExtensionItem(BackpackTier tier, Properties)` 继承它（类别固定 `"general"`），重写 `appendHoverText`（说明：增加一行可放任何物品的格子；只对 X 背包生效；放在左区或右区才生效，每个区只算一个）。已有的类别解析器 `stack.getItem() instanceof CategoryExtensionItem ext ? ext.category() : null` 对它自动给出 `"general"`，不用改。
- `ExtensionPacks` 增加档位解析器：`registerTier(Function<ItemStack,String>)`、`tierOf(stack)`；`MCFloatingBackpack` 注册 `stack.getItem() instanceof UniversalExtensionItem u ? u.tier().name().toLowerCase() : null`（两端都执行）。
- 注册：`ModItems` 对 `BackpackTier.values()` 循环注册 `<档位>_general_extension`（`leather_general_extension` … `netherite_general_extension`）到另一个映射 `UNIVERSAL_EXTENSIONS`；创意栏在 10 个分类扩展之后按档位顺序加。
- 背包档位：`BackpackTier.byCapacity(baseCapacity)`；为 null（异常容量）时通用扩展一律不生效。

## D7. 类别名与界面文字

- **已核对**：悬停说明与黄点提示里类别名用 `PresetTags.langKey(category)` → `tag.mcfloatingbackpack.preset.<id>`；`general` 没有这个键，直接用会显示键名。
- **决定**：纯函数 `CategoryNames.langKey(category)`：`general` → `category.mcfloatingbackpack.general`（"通用"/"General"），其余沿用 `PresetTags.langKey`。`FloatingPanelRenderer` 里两处取名（`drawRowTooltip`、`drawHiddenHint`）改用它。
- 行悬停说明：`general` 用 `panel.mcfloatingbackpack.row.tooltip.general`（"通用行：可以放任何物品"），不套"X行：只能放X类物品"；`ClientPlacement` 恒放行所以不会出现 `row.denied` 与红色高亮。
- `CategoryStyle`：加 `general` 的色值（见 contracts/items.md）；**`ghostItemId("general")` 返回 null → 不画幽灵图标**（`known("general")` 为真，但幽灵图标仅限 `IDS`）。`CategoryStyleTest` 现有遍历 `IDS` 的用例不变，加 `general` 的用例（边线色与 10 个都不同、不等于槽底色）。

## D8. 升级换档的行为

- **已核对**：`BackpackUpgradeRecipe` 升级后保留全部内容（035 已让它带 `rows`），工具区里的通用扩展也保留。新背包的档位变了，`placedPacks` 里旧档通用扩展不再计入 → 核对在载入/保存时把对应行隐藏（有物品）或删除（空）；放进新档通用扩展后恢复最早隐藏的一行。
- 这个后果是规格里确认的预期；物品提示说明"只对 X 背包生效"。**待游戏里验证**：升级后黄点出现、物品没丢、换新档扩展后物品回来。

## D9. 贴图

- 沿用 036 的手绘脚本思路（`design/demo/tools/make_category_extension_icons.py`）：同一个图案"一个开口的箱子，里面是格子网"，5 档各换主色（皮革棕、铁灰、金黄、钻石青、下界合金深紫黑），底板仍是铁灰描边圆角板，保证与 10 个分类扩展并排时能分得出（分类扩展的图案是各类别物品；通用扩展是箱子+格子）。
- 图集：5 张 16×16 的新增必须度量（宪法 VI，现状 2048×1024，预算 2048）。度量脚本 `check_atlas_budget.sh` 会 `rm -f run/logs/latest.log`；**度量前先把它备份成 `run/logs/latest.before-038.log`**，度量后保留，不覆盖。

## D10. 配方与成本

见 contracts/items.md 的成本对照表。**估算很粗**：按"铁锭当量"（铁锭 1、金锭 1.5、钻石 2.5、箱子 1、漏斗 6、皮革 0.5、线 0.2；末影箱、潜影壳、下界合金锭按"远贵于钻石"处理）。最低两档"两个通用扩展 < 同档升级（材料块 + 模板的复制成本）"只是略低（24 vs 27、28 vs 31），用户看过后可调：只改 `recipe/<档位>_general_extension.json` 与测试里的一张表。

## D11. 不做的事

- 不在面板上为"不生效的通用扩展"加提示（规格 Edge Cases：物品提示已写明条件）。
- 不引入"高档扩展在低档背包上生效"（用户已选"只对同档"）。
- 不改 `ToolAreaRules`、不加新的拒绝提示语。

## 尚未验证（写进 quickstart，验证前不得声称已满足）

1. 左右区之间挪动通用扩展时行不跳、不丢物品（光标算法 D3）。
2. 升级换档后旧档行隐藏、黄点、物品保留，换新档扩展后恢复（D8）。
3. 5 个图标并排时的可分辨度，与 10 个分类扩展的区别（D9）。
4. 三段顺序在转移、自动拾取、垃圾桶取回、从容器取物里的实际行为（D5）。
5. 通用行的底色与 10 个分类行、面板灰、悬停/选区叠加下的可分辨度。
6. 图集度量（D9）。
7. 配方成本的观感（D10）。
