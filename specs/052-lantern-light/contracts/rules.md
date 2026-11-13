# 契约：亮度、互斥、LDL 接入

## 互斥（`ToolLightRules.check`，纯逻辑）

`check(area, incoming, slotIndex, areaSlots, isLight) → OK | DUPLICATE`：

- 不是下方区 → `OK`；`incoming` 不是光源 → `OK`。
- 下方区：`before` = `areaSlots` 里光源的个数；`replacesOne` = 目标格里现在是光源；`after = before − (replacesOne ? 1 : 0) + 1`；`after > 1 && after > before` → `DUPLICATE`，否则 `OK`（旧背包已有多个光源时保留，不能再加）。
- 与 `ToolAreaRules` 的标签管理器限制互相独立。

## 起作用的光源

当前背包（`BackpackRef.resolve`，手 → 背 → 物品栏）的下方区里**按格子顺序的第一个**标签成员（`ToolLightRules.firstLight`）；左、右区不看。

## LDL 接入

- 入口类 `pnnh.mcfloatingbackpack.compat.lambdynlights.BackpackDynamicLight implements DynamicLightsInitializer`，由 `neoforge.mods.toml` 的 `[modproperties."${mod_id}"."yumi:entrypoints"]` 的 `"lambdynlights:initializer"` 声明；
- `onInitializeDynamicLights(DynamicLightsContext)`：`entityLightSourceManager().onRegisterEvent().register(c -> c.register(EntityType.PLAYER, new EntityLuminance[]{BackpackEntityLuminance.INSTANCE}))`；
- `BackpackEntityLuminance.getLuminance(itemLightSourceManager, entity)`：不是 `Player` 或 `compat.modLevels` 里 `lambdynlights` 不是 FULL → 0；否则 `PlayerLights.brightnessOf(player)`；
- `EntityLuminance.Type` 用 `registerSimple(mcfloatingbackpack:tool_light, INSTANCE)` 注册。

## 提示

- `message.mcfloatingbackpack.light.duplicate`（动作栏/聊天）："这个背包的下方区已经有一个光源了" / "The bottom area of this backpack already has a light source"；
- `message.mcfloatingbackpack.light.needs_mod`（聊天，每会话一次，没装 LDL 时）："光源需要你另外安装 LambDynamicLights（动态光源模组）才会发光；没有安装时，放进去也不会亮" / "A light only shines if you also install LambDynamicLights (the dynamic lights mod); without it nothing lights up"。
