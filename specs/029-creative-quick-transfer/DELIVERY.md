# Delivery: 029-creative-quick-transfer

**日期**: 2026-11-09

**状态**: 代码与自动检查完成；**用户已验证 T009～T012，都没有问题**（2026-11-09）。

## 自动化结果

| 项 | 结果 |
|---|---|
| 改动前基线（T001） | 028 之后：87 个测试类、629 个用例、0 失败 |
| 最终 | **88 个测试类、634 个用例、0 失败**（新增 `CreativeInventorySyncTest` 1 个类；`QuickTransferRulesTest` 改写并新增下标换算用例，共多 5 个用例） |
| 图集度量（T014） | 2026-11-09，无游戏在跑时执行：**2048×1024，通过**（本 feature 不新增贴图） |
| 静态检查（T015） | 只改了 `QuickTransferRules`、`QuickTransferInput`、`BackpackQuickTransferPayload`，新增 `CreativeInventorySync` 与两个测试类；`isCreative` 的命中范围是 027 的桥接与客户端类和本 feature 的 `CreativeInventorySync`；没有新增网络包、设置、贴图 |

## 实现摘要

- **下标换算**（`QuickTransferRules.creativeInventoryIndex`，纯）：创造界面物品栏标签页的格子是 `SlotWrapper`，`getContainerSlot()` 是菜单序号；客户端用 `player.inventoryMenu.getSlot(k).getContainerSlot()` 换算成物品栏下标（快捷栏 36～44 → 0～8），物品标签页的快捷栏是真格子，直接用；只有下标 0～35 算来源。
- **解除排除**：`isInterceptableScreenClass` 不再排除创造界面；`QuickTransferInput.screenSlotAt` 对创造界面：自己物品栏的格子按上面换算，**创造物品列表的格子返回 null**（不是来源，Ctrl+点击保持原版）。
- **转移后同步**：`BackpackQuickTransferPayload` 末尾对创造模式、打开自己物品栏菜单的玩家调 `inventoryMenu.sendAllDataToRemote()`（`CreativeInventorySync`）：整份同步客户端无条件应用，物品标签页下被忽略的单格更新由此补上。
- 协议不变（仍是 "15"）；没有新增设置，没有对游戏模式或界面的限制。

## 与 plan 的偏离

- 无。027 研究 D8 估计要"自家同步包"，读源码（F6、F7）发现整份同步客户端无条件应用，服务端一个调用就够。

## 客户端验证（未验证）

| 任务 | 内容 | 状态 |
|---|---|---|
| T009 | 物品栏 → 面板，尤其 A2 下标换算（快捷栏第 0 格与第 8 格） | **用户已验证并勾选**（2026-11-09）：没有问题 |
    ctrl转移功能正常
| T010 | 面板 → 物品栏，尤其 B2 物品标签页下转进不可见格子后切到物品栏标签页 | **用户已验证并勾选**（2026-11-09）：没有问题 |
    功能正常
| T011 | Ctrl+拖动与选区 + Ctrl | **用户已验证并勾选**（2026-11-09）：没有问题 |
    功能正常
| T012 | 转移后不覆盖、创造物品列表的 Ctrl+点击保持原版、011/012/014 回归 | **用户已验证并勾选**（2026-11-09）：没有问题 |
    功能正常

## 已知限制与未验证项

- "`SlotWrapper.getContainerSlot()` 是菜单序号"原来只是读源码的推断；**A2 已由用户验证**，换算正确。
- 创造物品列表（虚拟物品）不是转移来源；创造界面里的 Delete 键删除到垃圾桶（022）仍未支持。
- 整份物品栏同步携带服务端光标；转移的前提是服务端光标为空（027 之后与客户端一致），所以不会影响光标。联机延迟下未验证。
- Windows/Linux 未验证（只有 macOS 本机）。
