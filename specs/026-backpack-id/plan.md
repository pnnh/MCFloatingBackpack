# Implementation Plan: 背包编号与登记表（精简版）

**Branch**: `026-backpack-id` | **Date**: 2026-11-08（2026-11-09 精简） | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/026-backpack-id/spec.md`

## Summary

给每只浮动背包一个**玩家可见的三位编号**（如 `K7M`），并在服务端世界存档里维护一份**尽力而为的登记表**。**不限制数量**。研究（见 [research.md](./research.md)）与 2026-11-09 的精简决定得出：

1. **身份 = 数据组件 `backpack_identity`（只有一个 UUID），默认不带**：`/give 1000` 不占任何东西。复制的栈共用标识与编号。
2. **编号由 UUID 算出**（`BackpackIds.of`，两半各自混合后对 24,576 取模）：不查登记表、不避重、不会用完。
3. **合成时在配方的 `assemble` 里盖标识**（自家 `ShapedRecipe` 子类 `BackpackCraftingRecipe`），Shift 点击一次合成多只时每只各一个；**不再需要 `onCraftedBy` 登记**。
4. **登记表是世界存档数据（`SavedData`）**：每条记录 = 内部标识、所有者（最后使用者）、档位、状态、时间、影子副本；**没有修订号、没有复制检测、没有数量计数**；单条损坏加载时跳过。
5. **一道统一的可用性检查 `BackpackGate.acquire`**：12 个读写背包的服务端入口（含自动拾取）都经过它；没有标识的分配标识、登记表记一笔或更新；**唯一的拒绝是墓碑**。`BackpackRef.saveInventory` 里调 `afterSave` 同步影子等。
6. **显示**：标题栏图标右侧画编号（纯几何 `TitleIdLayout`）；物品提示加一行。协议版本与 027 一起是 `"15"`，不新增网络包、不新增贴图、**没有服务端配置**。

范围：只做身份、编号、登记表、墓碑拒绝与显示。数量限制、所有权转移、复制检测、销毁、备份、管理员命令与面板都不做；**本 feature 没有任何命令**。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（数据组件、`SavedData`、`ShapedRecipe`）；**无新增依赖，不涉及第三方模组**

**Storage**: 世界存档数据 `mcfloatingbackpack_registry`（`SavedData`，每条记录含影子内容）；物品数据组件 `backpack_identity`

**Testing**: JUnit 5 纯逻辑测试：`BackpackIds`（字符集、穷举 24,576、`of` 稳定且均匀）、`BackpackIdentity`、`RegistryIndex`、`NoticeLimiter`、`TitleIdLayout`、语言键与配方类型（文本检查）

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（单人游戏用内置服务端）；开发机 macOS

**Project Type**: 单一 NeoForge 模组

**Performance Goals**: 常规请求通过 gate 只做一次 `HashMap` 查找；保存时多出一次记录更新与影子引用替换；世界保存时只重编码改动过的条目

**Constraints**:
- 登记表所有读写只在服务端主线程；客户端不被信任提供任何标识
- 011～025 的行为不变（除"先过可用性检查"）；不新增命令、依赖、贴图、设置项；`BackpackContents` 编码仍是 6 个字段
- 单条记录损坏不能阻止世界加载

**Scale/Scope**: 约 10 个类（`BackpackIdentity(+Codecs)`、`BackpackIds`、`RegistryRecord`、`RegistryIndex`、`BackpackRegistry`、`BackpackGate`、`BackpackMessages`、`NoticeLimiter`、`BackpackCraftingRecipe`、`TitleIdLayout`）；修改 12 个服务端入口、`BackpackRef`、`FloatingBackpackItem`、`ModDataComponents`、`ModRecipes`、`FloatingPanelRenderer/State`、语言文件、配方 JSON

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 只在标题栏画一段文字，不新增 Screen | PASS |
| II. Server Owns Items | 标识的分配、登记表的所有读写都在服务端主线程；客户端只读栈上同步来的标识用于显示 | PASS |
| III. One Feature At A Time | 只做身份与登记表；销毁、备份、命令留给之后的 feature；数量限制明确不做 | PASS |
| IV. Vanilla Path Before Compat | 不引用任何第三方 | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证；多人场景没有条件登记"未验证" | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图；新增成本是 gate 的一次查找与世界保存时登记表的编码——实现阶段粗测写进 DELIVERY，测不了写"未测量" | PASS（已规划） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新增依赖，不新增设置，不引用第三方类型 | PASS |

**要盯住的风险**：影子副本让存档体积约翻倍（已登记为已知限制，保底是之后的管理员命令）；自家配方类型在 JEI、配方书、合成器里的表现（U2，退化路径是"首次使用时分配标识"）。

## Project Structure

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/identity/        BackpackIds, BackpackIdentity(+Codecs), RegistryRecord, RegistryIndex, BackpackRegistry,
│                              BackpackGate, BackpackMessages, NoticeLimiter
├── inventory/recipe/BackpackCraftingRecipe.java
├── client/panel/TitleIdLayout.java（+ FloatingPanelState.hitsTitleId、FloatingPanelRenderer.drawTitleId）
└── network/*（12 个入口先过 gate）

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/identity/        BackpackIdsTest, BackpackIdentityTest, RegistryIndexTest, NoticeLimiterTest
├── client/panel/TitleIdLayoutTest.java
└── resources/BackpackIdResourcesTest.java
```

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。
