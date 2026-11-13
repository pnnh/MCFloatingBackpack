# 契约：标签与语言键

## `data/mcfloatingbackpack/tags/item/tool_lights.json`

`{"replace": false, "values": [...]}`，值为 10 个原版物品 id 的普通字符串：`minecraft:lantern`、`soul_lantern`、`sea_lantern`、`glowstone`、`shroomlight`、`ochre_froglight`、`verdant_froglight`、`pearlescent_froglight`、`end_rod`、`jack_o_lantern`。**不含**火把、灵魂火把、红石火把、蜡烛、营火、岩浆。

## 语言键（`zh_cn.json`、`en_us.json`，两个文件键集合相同）

`message.mcfloatingbackpack.light.duplicate`、`message.mcfloatingbackpack.light.needs_mod`（内容见 rules.md）。

## 测试

- `LightResourcesTest`：标签文件恰好 10 个物品且与亮度表同一批、不含火把类；两个语言文件都有 `light.duplicate` 且键集合相同；
- `LightWiringTest`（源码文本检查）：入口声明与可选依赖（`optional`、`CLIENT`）；LDL 只在 `compileOnly`/`localRuntime`；只有 `compat/lambdynlights/` 引用 LDL；`light/` 包没有 `setBlock` 与别的模组类型；`EntityLuminance` 只对玩家、受 `specificAdaptation` 控制；读背包用 `BackpackRef.resolve` 且只读；两处互斥检查与"需要 LDL"的提示；协议 19、修订 3、没有新网络包、配置项、mixin；
- `ToolLightsTest`：10 个默认物品的亮度等于原版、关系不反、取自方块发光值的换算；`ToolLightRulesTest`：互斥规则；`DependencyRulesTest`：依赖规则。
