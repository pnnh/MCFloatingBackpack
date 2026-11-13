# 契约：亮度、互斥、落点、跟随、清理

## 互斥（`ToolLightRules.check`，纯逻辑）

`check(area, incoming, slotIndex, areaSlots, isLight) → OK | DUPLICATE`：

- 不是下方区 → `OK`；`incoming` 不是光源 → `OK`。
- 下方区：`before` = `areaSlots` 里光源的个数；`replacesOne` = 目标格里现在是光源；`after = before − (replacesOne ? 1 : 0) + 1`；`after > 1 && after > before` → `DUPLICATE`，否则 `OK`（旧背包已有多个光源时保留，不能再加）。
- 与 `ToolAreaRules` 的标签管理器限制互相独立；光源与标签管理器可以同时放。

## 起作用的光源

当前背包（`resolvePlayer`）的下方区里**按格子顺序的第一个**标签成员；左、右区不看。

## 落点（`LightPlacement`，纯逻辑）

格子类型：`AIR`、`WATER_SOURCE`、`OURS`（仍是我们放的那个光源）、`OTHER`。`choose(eye, feet)`：眼睛格可放（`AIR`/`WATER_SOURCE`/`OURS`）→ 眼睛格；否则脚格可放 → 脚格；否则无。别人放的 `minecraft:light` 是 `OTHER`。

## 方块

`minecraft:light[level = N, waterlogged = 水源格]`；放置与删除用标志位 `2 | 16`；删除时 `waterlogged` 的格子还原成水源，其余还原成空气。

## 跟随器与清理

见 [research.md](../research.md) R6、R8：先放后删；最小间隔 2 tick；光源与目标格只差上下一格时不动；原地不动零动作；原地改亮度不受间隔限制；想要的亮度为 0（无光源、旁观、死亡、下线、换维度）则删除；记录只在"仍是我们放的同亮度光源"时才删。

## 提示

`message.mcfloatingbackpack.light.duplicate`（动作栏）："这个背包的下方区已经有一个光源了" / "The bottom area of this backpack already has a light source"。
