# 契约：数据文件、条件、配置、贴图与语言键

对应 FR-001～FR-007、FR-015。

## 条件

`type = "mcfloatingbackpack:slot_enabled"`，字段 `mod`（字符串，`curios` 或 `accessories`）。`MapCodec` 注册在 `NeoForgeRegistries.CONDITION_SERIALIZERS`（`DeferredRegister`，构造函数里挂到 mod 事件总线），名字 `slot_enabled`。`ICondition.test` 读 `WornSlotsConfig.enabled(mod)`；不使用 `IContext`。条件类只引用 NeoForge 类型。

## 启动配置

`ModConfig.Type.STARTUP`，在 `MCFloatingBackpack` 构造函数里 `modContainer.registerConfig(...)`（`WornSlotsConfig.SPEC`）；注册的瞬间 FML 读入。键：`slots.curios`、`slots.accessories`（布尔，默认 `true`）。配置注释（英文，沿用 058 的写法）必须写明：

- 控制是否在 Curios / Accessories 里注册"浮动背包"专用槽位；
- 改动需**重启**（读取发生在数据包加载之前）；
- 联机时以**服务端**的值为准（槽位由服务端同步给客户端）；
- 关闭后放在专用槽里的背包由饰品模组处理（结果见 README），**改之前先取下背包**；
- 与 `compat.modLevels` 的档位无关；`back`、`charm` 的支持不受它影响。

## 数据文件（全部在 `src/main/resources/`）

`C` = 带 `"neoforge:conditions": [{"type": "mcfloatingbackpack:slot_enabled", "mod": "<id>"}]`。

| 文件 | 内容 | 条件 |
|---|---|---|
| `data/mcfloatingbackpack/curios/slots/floating_backpack.json` | `size: 1`、`operation: SET`、`order: 85`（`back` 是 80）、`icon: "mcfloatingbackpack:slot/floating_backpack"`、`validators: ["curios:tag"]` | C(curios) |
| `data/mcfloatingbackpack/curios/entities/player_floating_backpack.json` | `entities: ["minecraft:player"]`、`slots: ["floating_backpack"]` | C(curios) |
| `data/mcfloatingbackpack/accessories/slot/floating_backpack.json` | `replace: false`、`amount: 1`、`operation: "set"`、`order: 810`（`back` 是 800）、`icon: "mcfloatingbackpack:gui/slot/floating_backpack"`、`validators: ["accessories:tag", "accessories:component"]` | C(accessories) |
| `data/mcfloatingbackpack/accessories/entity/player_floating_backpack.json` | `replace: false`、`entities: ["minecraft:player"]`、`slots: ["floating_backpack"]` | C(accessories) |
| `data/mcfloatingbackpack/accessories/group/chest.json` | `replace: false`、`order: 80`、`slots: ["floating_backpack"]`、`icon: "accessories:gui/group/chest"` | C(accessories) |
| `data/curios/tags/item/floating_backpack.json`、`charm.json` | 五个等级的浮动背包，`replace: false`（与现有 `back.json` 同格式） | 无 |
| `data/accessories/tags/item/floating_backpack.json`、`charm.json` | 同上 | 无 |

（组文件的 `order`、`icon` 与原版 `chest.json` 一致；组按名字合并还是被覆盖，是 plan 里的第一个游戏内验证点，不行退回 `unsorted`。）

## 贴图（16×16，PNG）

- `assets/mcfloatingbackpack/textures/slot/floating_backpack.png`（Curios：blocks 图集的 `slot/` 目录）
- `assets/mcfloatingbackpack/textures/gui/slot/floating_backpack.png`（Accessories：gui 图集）

风格同两个模组自带的空槽图标（灰色线稿背包轮廓）；两张可以是同一张图。

## 语言键（en_us、zh_cn 都要）

- `curios.identifier.floating_backpack`：Floating Backpack / 浮动背包
- `accessories.slot.floating_backpack`：Floating Backpack / 浮动背包

（两个模组的 `charm` 的名字由它们自己提供。）

## 依赖声明

`neoforge.mods.toml` 不变：Curios、Accessories 已是 `optional`；条件类型与配置不引用它们。`DependencyRulesTest` 的第三方坐标规则不变（没有新坐标）。
