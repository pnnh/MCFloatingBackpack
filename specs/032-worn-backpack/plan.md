# Implementation Plan: 统一的"背着的背包"查找

**Branch**: `032-worn-backpack` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

## Summary

把"背着的背包"从"胸甲槽一处写死"改成**一个有顺序的来源列表**，渲染与"按 B / 面板服务端操作 / 自动拾取"共用一个结果。读源码（见 [research.md](./research.md)）得到的设计：

1. **纯逻辑核心** `BackpackSearch<P, S>`（不引用 Minecraft 类）：来源按 `order` 排序；`firstWorn` 取第一只，`resolve` 给出"主手 → 副手 → 背着的第一只 → 物品栏"。用假来源单元测试（D1、D7）。
2. **来源接口** `WornSource<P, S>`：`id`（= `BackpackRef.Source` 序号，发布后不改）、`order`（顺序，与 id 无关）、`active()`（查询时判断，给以后的 `modLevels` 用）、`slotCount/get/set`（越界返回空）、`hiddenRender`（胸甲恒假，给 033/034 留的口子）（D2）。
3. **线路不变**：引用仍是 `(Source, invIndex)`，协议版本仍是 16；`worn` 来源里 `invIndex` 是"来源内的格子号"，胸甲从 `-1` 改成 `0`（唯一的取值变化）；新来源序号将来加在枚举末尾（D3）。
4. **渲染层**改用 `WornBackpacks.first(LivingEntity)`；隐藏渲染的槽仍算背着的，但不画、也不改画后面的（D5）。
5. **调用点**：服务端 11 处、客户端 3 处、自动拾取都只调 `resolve` / `getStack`，**不用改**；只改 `BackpackQuickTransferPayload.holderOf`（`worn` 来源一律映射到 `Holder.CHEST`）和 `BackpackRef.saveInventory` 的过时注释（D6）。

本 feature 只有一个来源（胸甲）。不新增网络包、语言键、贴图、配方、设置项、依赖。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；无新增依赖，不涉及第三方模组

**Storage**: 无

**Testing**: JUnit 5 纯逻辑：`BackpackSearchTest`（来源排序、跳过不生效、第一只、越界、隐藏渲染、手里优先、物品栏下标最小、两个不同的假玩家得同样结果）；`BackpackRefTest` 增补（胸甲引用下标为 0、`Source` 的 `worn` 标志与序号不变）。`WornBackpacks`、渲染层、网络路径只能在客户端会话里验证

**Target Platform**: 客户端 + 服务端；开发机 macOS

**Constraints**: 行为与 031 之后完全一致；协议版本 16 不变；渲染层每玩家每帧只查询一次且没有第三方来源时开销≈读一次胸甲槽；服务端不信任客户端的引用（现状）

**Scale/Scope**: 新增 `inventory/worn/`（`WornSource`、`BackpackSearch`、`WornBackpacks`、`ChestWornSource`，纯逻辑 2 个 + 适配 2 个）；改 `BackpackRef`、`BackpackBackLayer`、`BackpackQuickTransferPayload`；新增 1 个测试类、改 1 个

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面；渲染层仍是玩家实体上的一层 | PASS |
| II. Server Owns Items | "按 B 打开哪一只"仍由服务端 `resolve` 决定，对客户端引用的处理不变（只在与解析结果完全一致时才采用） | PASS |
| III. One Feature At A Time | 只做查找的抽象，不引入任何第三方模组（033、034 才做） | PASS |
| IV. Vanilla Path Before Compat | 唯一的来源是原版胸甲槽；接口为第二档模组留口子但不引用它们 | PASS |
| V. Verify In A Client Session | quickstart 要求 8 个场景与面板回归都在客户端对照 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图；渲染查询开销≈现状 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖；来源的 `active()` 在查询时判断，为 `modLevels` 留好位置 | PASS |

## Project Structure

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/worn/
│   ├── WornSource.java          # 新（纯）：来源接口
│   ├── BackpackSearch.java      # 新（纯）：顺序、第一只、resolve
│   ├── WornBackpacks.java       # 新（MC）：单例、注册、first(LivingEntity)、byId
│   └── ChestWornSource.java     # 新（MC）：胸甲槽来源
├── inventory/BackpackRef.java   # 改：Source.worn 标志；resolve/getStack/setStack/stillValid 走 WornBackpacks；注释
├── client/render/BackpackBackLayer.java   # 改：用 WornBackpacks.first
└── network/BackpackQuickTransferPayload.java   # 改：holderOf 对 worn 来源返回 CHEST

src/test/java/pnnh/mcfloatingbackpack/inventory/
├── worn/BackpackSearchTest.java   # 新
└── BackpackRefTest.java           # 改
```

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。

## Risks

- **胸甲引用从 `-1` 改成 `0`**：所有生产者与消费者是同一份新代码；服务端的相等比较、客户端的引用键都不依赖具体值。已 grep 确认没有地方把 `-1` 当胸甲的标志；仍要在 quickstart A 里实际操作一遍（尤其面板打开后摘下胸甲背包）。
- **专用服务器没有客户端配置**：以后的 `modLevels` 只在单人/局域网生效（research D4），记在这里，033 里明确写出来。
- **渲染层是每帧热路径**：只做一次列表遍历、不分配临时集合。
