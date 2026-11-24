# Implementation Plan: 禁止浮动背包嵌套

**Branch**: `064-no-nested-backpacks` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

## Summary

浮动背包（及装着浮动背包的物品）进不了任何浮动背包的任何区域；潜影盒、收纳袋、旅行者背包也拒收浮动背包；旧嵌套只能取出，同一只背包内部可以放回（设计依据与反编译核对见 [research.md](./research.md)）：

1. **纯逻辑 `NestingRules`**（`inventory/nesting/`，可单元测试，不依赖 Minecraft）：对一个物品给出判定 `OK` / `IS_BACKPACK` / `CONTAINS_BACKPACK`。通过一个小接口 `Probe<T>`（`isBackpack(T)`、`children(T)`——结构化取出容器里的物品、`mentionsBackpack(T)`——通用兜底、`needsScan(T)`——是否带容器类数据）抽象物品，深度上限 8，超过按"含有"处理。
2. **纯逻辑 `NestedOrigins`**（同包）：每个玩家一条"刚从哪只背包拿起了哪个物品"的临时记录；`remember(player, backpackKey, stack)`、`allowsReturn(player, backpackKey, stack)`（同一背包且物品相同才放行，放行即清）、`clear(player)`。键与物品类型用泛型，测试用假类型。
3. **Minecraft 适配 `BackpackNesting`**（`inventory/nesting/`）：`Probe<ItemStack>` 的实现——`isBackpack` 用 `FloatingBackpackItem.isBackpack`；`children` 读 `DataComponents.CONTAINER`（`ItemContainerContents.nonEmptyItems()`）与 `BUNDLE_CONTENTS`（`BundleContents.items()`）；`needsScan` 看组件补丁里有没有容器类或非 `minecraft` 命名空间的组件；`mentionsBackpack` 把物品编码成 NBT 查五个浮动背包的物品 id 字符串。来源记录的静态存取、清理钩子（`PlayerContainerEvent.Close`、下线，`MCFloatingBackpack` 里已有这两个监听的位置）。
4. **入口**（逐个核对过，见 research R5）：
   - `BackpackInventory.mainTargetOrder(stack)` 对有风险的物品返回三段全空——**默认安全**，`insertIntoMain`、自动拾取、容器 → 面板、终端 → 面板、物品栏 → 面板自动继承；新增内部版本 `mainTargetOrderInternal` 给垃圾桶还原等内部搬动用。
   - `BackpackClickPayload`：主区域和工具区两个分支各加判定（带来源记录与操作栏提示），`swapHand` 补提示；拿起有风险的物品时写来源记录。
   - `BackpackCursorGesturePayload.distribute`：入口处判定一次。
   - `BackpackTakeFromContainerPayload`、`BackpackNetworkTakePayload`、`BackpackQuickTransferPayload.inventoryToBackpack`：在取出/移动**之前**判定，有风险的原地不动（终端里的不提取）。
   - `BackpackTrashPayload.add`、`BackpackTrashSendPayload`：一律拒绝（不看来源）；`TrashBin.take` 取出旧的到光标时写来源记录。
5. **`FloatingBackpackItem` 重写无参 `canFitInsideContainerItems()` 返回 false**：原版潜影盒、收纳袋、TB 与别的遵守该接口的容器拒收。精妙背包只能靠管理员配置，写进 README 与配置说明。
6. **语言键**：`message.mcfloatingbackpack.nesting.refused`（"浮动背包不能放进浮动背包"）、`message.mcfloatingbackpack.nesting.contains`（"里面含有浮动背包"），中英文。
7. **旧数据**：不动；打开背包时（`BackpackOpenRequestPayload` 处理里）对主区域、工具区、垃圾桶做一遍扫描，发现嵌套就在服务器日志写至多一条 INFO（同一只背包本次运行只写一次）。
8. **文档与测试**：README 两份（嵌套规则、精妙背包的配置做法、网络存储不限制）、021 spec 修订（FR-018 与第 101 行验收）、`docs/plan/no-nested-backpacks.md`、DELIVERY；单元测试（`NestingRules`、`NestedOrigins`）与接线/资源测试（各入口调用了判定、`canFitInsideContainerItems` 重写、语言键齐全）；游戏内验证见 [quickstart.md](./quickstart.md)。

不做：AE2、集成终端、精妙存储的拦截（无钩子，要 mixin）、禁止容器进浮动背包、自动搬出旧嵌套、新网络包。协议号不改（22），`DataRevision` 仍 6。

## Technical Context

**Language/Version**: Java 21 · NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖、mixin、反射
**Storage**: 无新存储（来源记录只在内存）
**Testing**: JUnit 5（纯逻辑 + 源码接线检查）；游戏内：容器 `mcfb-c1`（旅行者背包、精妙背包已在开发运行里）
**Constraints**: 宪法 II（服务端决定）、III（一次一件）、V（游戏内验证）、VII（只用原版/NeoForge 接口，不引用第三方类型）；协议号 22 不变；`DataRevision` 6 不变
**Scale/Scope**: 新增 3 个类（`NestingRules`、`NestedOrigins` 纯逻辑；`BackpackNesting` 适配），改约 9 个入口类、`FloatingBackpackItem`、`MCFloatingBackpack`（清理钩子），语言键 2 个（中英），文档 5 处

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不碰界面（客户端先不改，被拒由服务端回发槽位） | PASS |
| II. Server Owns Items | 判定、来源记录、拒绝全在服务端 | PASS |
| III. One Feature At A Time | 只做嵌套限制；网络存储实测另行 | PASS |
| IV. Vanilla Path Before Compat | 原版潜影盒、收纳袋靠原版接口；创造模式经同一路径 | PASS |
| V. Verify In A Client Session | 各入口、容器拒收、旧嵌套取出/放回、创造模式都在游戏里验证；验证不到的如实写明 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 深度扫描只对带容器类数据的物品，深度上限 8；开背包时的旧嵌套扫描一次、日志一次 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 只用 NeoForge/原版接口；精妙背包只写文档、不碰它的配置；不引用第三方类型；没装这些模组时无差别 | PASS |

设计后复核：无违规项。**需要如实说明的修订**：spec 里"面板光标即内部来源"的前提被代码推翻（开着容器界面时拿起的物品在菜单携带物上），改成"来源记录"，已写回 spec（Edge Cases、FR-003、Assumptions）与 research R3；退路是一律只能取出。

## Project Structure

```text
specs/064-no-nested-backpacks/
├── spec.md · plan.md · research.md · data-model.md · quickstart.md · tasks.md
└── contracts/nesting-rules.md   # 判定、入口矩阵、来源记录
src/main/java/pnnh/mcfloatingbackpack/inventory/nesting/{NestingRules,NestedOrigins,BackpackNesting}.java   # 新
src/main/java/pnnh/mcfloatingbackpack/{inventory/BackpackInventory, item/FloatingBackpackItem, MCFloatingBackpack}.java
src/main/java/pnnh/mcfloatingbackpack/network/{BackpackClickPayload,BackpackCursorGesturePayload,BackpackQuickTransferPayload,
    BackpackTakeFromContainerPayload,BackpackNetworkTakePayload,BackpackTrashPayload,BackpackTrashSendPayload,BackpackOpenRequestPayload}.java
src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json
src/test/java/pnnh/mcfloatingbackpack/inventory/nesting/{NestingRulesTest,NestedOriginsTest}.java
src/test/java/pnnh/mcfloatingbackpack/resources/NestingWiringTest.java
```
