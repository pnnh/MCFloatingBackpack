# 契约：右区限制、打开请求、菜单、点击

## 右区"各至多一个"

沿用 053 的 `ToolSingleRules.check(PanelArea.RIGHT, area, incoming, slotIndex, areaSlots, stack -> stack.is(MOBILE_CRAFTING_TABLE))`；服务端 `BackpackClickPayload` 与客户端预览 `FloatingPanelInput` 各加一条，提示键 `message.mcfloatingbackpack.limit.mobile_crafting_table`；与垃圾桶、移动唱片机的限制互相独立。

## 点击（客户端）

在 `FloatingPanelInput` 的"点工具"分支里：光标为空、左键（`!isRight`）、无 Shift / Ctrl / 拖拽、`toolHit.area() == PanelArea.RIGHT`、该格是 `MOBILE_CRAFTING_TABLE` → `PacketDistributor.sendToServer(new OpenToolPayload(MobileTool.CRAFTING_TABLE.id()))`，返回 true（吞掉这次点击）。右键仍是拿起（走原有路径）。

## 打开请求（服务端，`OpenToolPayload.handleServer`）

1. 不是 `ServerPlayer` → 返回。
2. `tool = MobileTool.fromByte(payload.tool())`；`inRight = tool != null && MobileTools.has(player, tool)`（`BackpackRef.resolve` → 栈 → `BACKPACK_CONTENTS.tools().right()` 里有该工具对应的物品）。
3. `open = player.containerMenu instanceof MobileMenu m ? m.tool() : null`。
4. `switch (ToolOpenPlanner.decide(tool, inRight, open))`：`OPEN` → `MobileTools.open(player, tool)`；`CLOSE` → `player.closeContainer()`；`IGNORE` → 什么也不做。
5. 不信任客户端：包里没有物品或槽位；校验只看服务端自己解析出的背包。

## 菜单（`MobileCraftingMenu`）

- `MobileTools.open(player, CRAFTING_TABLE)`：`player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MobileCraftingMenu(id, inventory, ContainerLevelAccess.create(p.level(), p.blockPosition())), Component.translatable("container.crafting")))`。
- `MobileCraftingMenu extends CraftingMenu implements MobileMenu`；`stillValid(Player)` 重写为 `MobileTools.has(player, MobileTool.CRAFTING_TABLE)`（不调用父类的方块检查）；`tool()` 返回 `CRAFTING_TABLE`。
- 其余（合成、取走、配方书、剩余物品、关闭时的退还、统计、进度）一律原版。

## 版本

协议号 `"20"`；数据修订 `CURRENT = 5`。
