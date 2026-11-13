# 数据模型：移动工作台

没有存储数据。只有三样东西。

## 种类枚举（`MobileTool`，纯逻辑）

`CRAFTING_TABLE`（字节 0）；055 加 `FURNACE`（字节 1）。`fromByte(b)`：已知返回种类，未知返回 `null`；`id()` 返回字节。

## 决策表（`ToolOpenPlanner`，纯逻辑）

`decide(MobileTool tool, boolean inRightArea, MobileTool openMenuTool) → OPEN | CLOSE | IGNORE`，见 research R4。

## 包（`OpenToolPayload`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `tool` | byte | 哪种工具；服务端用 `MobileTool.fromByte` 解释，未知则忽略 |

方向：客户端 → 服务端。不带任何物品、槽位或背包数据。

## 服务端的菜单（`MobileCraftingMenu`）

`CraftingMenu` 的子类，实现 `MobileMenu`（`tool()` 返回 `CRAFTING_TABLE`）；构造用 `ContainerLevelAccess.create(世界, 玩家位置)`；`stillValid(player)` = `MobileTools.has(player, CRAFTING_TABLE)`。无字段。
