# Implementation Plan: 创造物品栏界面里的 Ctrl 快速转移

**Branch**: `029-creative-quick-transfer` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

## Summary

011 的 Ctrl 快速转移一直把创造物品栏界面排除在外。027 解决了光标；这个 feature 解决 027 研究 D8 里剩下的两个障碍。读源码（见 [research.md](./research.md)）发现它比预想的小：

1. **格子下标换算**：创造界面物品栏标签页的格子是 `SlotWrapper`，`getContainerSlot()` 是菜单序号而不是物品栏下标。客户端用 `player.inventoryMenu.getSlot(k).getContainerSlot()` 换算成真实下标（纯规则 `QuickTransferRules.creativeInventoryIndex`，可测）；物品标签页的快捷栏是真格子，直接用。创造物品列表的格子不是来源。
2. **转移后的客户端显示**：服务端 `BackpackQuickTransferPayload` 处理完后，对创造模式且打开自己物品栏菜单的玩家调一次 `inventoryMenu.sendAllDataToRemote()`（整份物品栏同步，客户端**无条件应用**）。纯决策 `CreativeInventorySync.shouldSync` 可测。
3. 解除 `isInterceptableScreenClass` 对创造界面的排除（011 的测试随之更新）。

不新增网络包（协议不变，仍是 "15"）、不新增贴图与设置、没有对游戏模式或界面的限制。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；无新增依赖，不涉及第三方模组

**Storage**: 无

**Testing**: JUnit 5 纯逻辑测试：`QuickTransferRulesTest`（创造界面可拦截、下标换算：物品栏标签页的快捷栏 36～44 → 0～8、主物品栏不变、护甲/合成/副手 → -1、物品标签页不调用换算）、`CreativeInventorySyncTest`；其余在客户端会话验证

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端；开发机 macOS

**Constraints**: 011/012/014 在其它界面与协议不变；不限制任何模式或界面（宪法 VII）；协议不变

**Scale/Scope**: 1 个新类（`CreativeInventorySync`），改 3 个文件（`QuickTransferRules`、`QuickTransferInput`、`BackpackQuickTransferPayload`）与 1 个测试类，新增 1 个测试类

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不替换、不包装创造界面；只读它的格子与标签页状态 | PASS |
| II. Server Owns Items | 转移仍由服务端按真实下标执行；客户端只换算下标；服务端转移后同步整份物品栏 | PASS |
| III. One Feature At A Time | 只做创造界面里的 Ctrl 转移；创造物品列表作为来源、Delete 键都不做 | PASS |
| IV. Vanilla Path Before Compat | 修的是原版界面里的缺口；不引用第三方 | PASS |
| V. Verify In A Client Session | quickstart 要求两个标签页、下标换算、转移后显示与覆盖、拖动与选区 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图；非创造玩家零成本；创造玩家每次转移多一次整份物品栏同步（一个包） | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 这是第一档（原版）的缺口修复：不可配置、不限制、不新增设置 | PASS |

## Project Structure

```text
src/main/java/pnnh/mcfloatingbackpack/
├── client/panel/QuickTransferRules.java        # 改：不再排除创造界面；新增 creativeInventoryIndex
├── client/panel/QuickTransferInput.java        # 改：创造界面的格子换算；创造物品列表不是来源
├── inventory/creative/CreativeInventorySync.java   # 新增
└── network/BackpackQuickTransferPayload.java   # 改：转移后同步整份物品栏

src/test/java/pnnh/mcfloatingbackpack/
├── client/panel/QuickTransferRulesTest.java    # 改
└── inventory/creative/CreativeInventorySyncTest.java   # 新增
```

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。
