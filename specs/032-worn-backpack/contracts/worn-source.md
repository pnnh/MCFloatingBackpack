# Contract: 背着的来源（给 033、034 用）

## 加一个来源要做的事

1. 在 `BackpackRef.Source` **末尾**追加一个常量，`worn = true`（序号一旦发布不改）。
2. 实现 `WornSource<LivingEntity, ItemStack>`：`id()` 返回该常量的序号，`order()` 选一个数（胸甲 0；Curios 10；Accessories 20），`active()` 在查询时读 `modLevels`，`slotCount/get/set` 读写那个槽，`hiddenRender` 读该槽的渲染开关，**越界返回 `ItemStack.EMPTY`**。
3. 在模组构造函数里（两端都跑）按 `ModList.isLoaded` 调 `WornBackpacks.register(...)`。兼容类放在 `compat/<模组id>/`（宪法 VII）。

不需要改：已有的网络包、服务端 11 处与客户端 3 处调用、渲染层、自动拾取。

## 对来源实现的要求

- `get` 在任何异常情况下都**不抛**（模组被移除、槽不存在、下标越界）→ 返回空栈。
- `active()` 便宜（每个玩家每帧调一次）。
- 只看**真实栈**，不看装饰栈。
- 在客户端要对**任意玩家实体**可用（渲染别的玩家）。

## 引用的语义

`BackpackRef(source, invIndex)`：`worn` 来源里 `invIndex` 是来源内的格子号（胸甲为 0）；`MAIN_HAND`/`OFF_HAND` 为 -1；`INVENTORY` 为 0～35。服务端对客户端声称的引用只在与 `resolve` 的结果完全一致时采用。

## 查找顺序

`主手 → 副手 → firstWorn（来源按 order，来源内按格子号）→ 物品栏 0～35`。手里的永远优先于背着的。
