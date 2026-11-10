# Contract: 规则

## 1. 生效（谁算"放着的"）

见 data-model.md 的 `PlacedPacks` 规则。要点：

- 放哪里都允许，**没有拒绝，没有新的提示**；
- 算数的只有：与背包同档、在左区或右区、每区至多一个（任意个同档的都只算一个）、光标上的同档算一个，合计最多 2；
- 别档、下区、主区域、同区第二个及以后：普通物品，不产生行；
- 分类扩展的计数不变（每个都算，任何位置）。

## 2. 匹配

- `ItemCategoryMatcher.matches("general", stack)`：`stack` 非空即真；不查标签、不看手动标签。
- 其它类别不变。
- `ExtensionRules.canPlace(visibleCategories, base, slot, stack, matcher)`：通用行的格子对任何非空物品返回真；隐藏行与不存在的格子仍返回假（035）。

## 3. 目标顺序

`targetSegments` 三段：类别行 → 基础容量 → 通用行（data-model.md）。适用于：Ctrl 快速转移（转入）、自动拾取、垃圾桶取回、从打开的容器取物（它们都通过 `BackpackInventory.mainTargetOrder` 取段列表）。不适用于点击与拖拽分配（玩家指定格子）。每段内部由 `QuickTransferLogic.moveInto` 先并进已有的没满的栈、再放空格。

**这是 035 规则的变更。** 对没有通用行的背包只是多一个空段，行为不变。

## 4. 放入检查

`ExtensionPackRules.canPlace(inventory, incoming, incomingCounted)`：`ExtensionPacks.tierOf(incoming) != null`（通用扩展）→ 恒为真。分类扩展的行总数 35 检查不变。

## 5. 升级与保存

- 保存与载入都重算 `placedPacks` 再核对；升级换档后旧档通用扩展不计入，对应行隐藏（有物品）或删除（空）。
- 光标上同档通用扩展算一个，总数不超过 2。

## 6. 客户端预测

不新增判断：通用行恒放行（匹配规则同上，同一份代码）；`ClientPlacement.categoryTagsBound` 仍遍历 `IDS`（10 个），`general` 不参与。
