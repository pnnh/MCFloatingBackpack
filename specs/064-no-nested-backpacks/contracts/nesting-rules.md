# Contract: 嵌套判定、入口、来源记录

## 判定

| 物品 | 结果 |
|---|---|
| 五档浮动背包 | `IS_BACKPACK` |
| 空潜影盒、装普通物品的潜影盒/收纳袋 | `OK` |
| 里面（任意深度）有浮动背包的潜影盒/收纳袋 | `CONTAINS_BACKPACK` |
| 带别的模组容器组件、编码后含浮动背包 id | `CONTAINS_BACKPACK` |
| 本模组其它物品（垃圾桶、标签管理器、自动拾取、分类扩展件、管子…） | `OK` |
| 嵌套超过 8 层 | `CONTAINS_BACKPACK`（保守） |

## 入口矩阵（谁、什么时候判定、拒绝后怎样）

| 入口 | 判定时机 | 来源记录能放行吗 | 拒绝后 |
|---|---|---|---|
| 点击主区域、工具区 | 点击处理，应用之前 | 能（同一背包、同一物品） | 槽位回发 + 操作栏提示，状态不变 |
| 手里换格子（swapHand） | 同上 | 否 | 同上（补提示） |
| 拖动分摊 | `distribute` 入口 | 能 | 整个手势不做 |
| 物品栏 → 面板 | 移动之前 | 否 | 物品留在物品栏 |
| 自动拾取 | 插入之前（`mainTargetOrder` 为空） | 否 | 交给原版拾取 |
| 容器 → 面板 | 取出之前 | 否 | 留在容器 |
| 网络终端 → 面板 | 提取之前 | 否 | 不提取 |
| 垃圾桶（光标、Delete 键、倾倒） | 推入之前 | **否**（一律拒绝） | 槽位回发 + 提示 |
| 垃圾桶 → 主区还原 | 内部版本，不判定 | — | — |
| 整理、快照还原 | 不经过判定 | — | — |

## 来源记录

- 写入：`BackpackClickPayload` 里，一次点击会把有嵌套风险的物品从面板格子拿起来时（含垃圾桶取出到光标）。
- 放行：`同一背包 && ItemStack.matches`，放行即清。
- 清除：下一次写入覆盖、`PlayerContainerEvent.Close`、下线。
- 失败方向：任何对不上都按外部处理（拒绝）。

## 拒绝提示

操作栏（`displayClientMessage(…, true)`）：`IS_BACKPACK` → `message.mcfloatingbackpack.nesting.refused`；`CONTAINS_BACKPACK` → `message.mcfloatingbackpack.nesting.contains`。批量路径（Ctrl+点击多格）一次操作至多提示一条。

## `canFitInsideContainerItems`

`FloatingBackpackItem` 重写无参 `Item.canFitInsideContainerItems()` 返回 false；NeoForge 的有参版本默认委托给它，TB 与 `ComponentItemHandler` 也都落到它。
