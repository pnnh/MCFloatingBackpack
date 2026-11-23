# 数据模型：饰品模组里的专用槽位与通用槽位

本 feature 不改背包存储数据（`DataRevision` 仍 6）；下面是新增的概念与它们的规则。

## 槽位类型与来源

| 模组 | 槽位类型 | `BackpackRef.Source` | 顺序 | 来源 | 注册 |
|---|---|---|---|---|---|
| 胸甲 | — | `CHEST` | 0 | 原有 | — |
| Curios | `back` | `CURIOS`（序号 4，已有） | 10 | 原有（033） | `player_back.json`，无条件 |
| Curios | `floating_backpack` | `CURIOS_DEDICATED`（6） | 11 | 新 | 开关 `[slots] curios` |
| Curios | `charm` | `CURIOS_CHARM`（7） | 12 | 新 | 整合包提供（我们不分配） |
| Accessories | `back` | `ACCESSORIES`（序号 5，已有） | 20 | 原有（034） | 模组默认给玩家 |
| Accessories | `floating_backpack` | `ACCESSORIES_DEDICATED`（8） | 21 | 新 | 开关 `[slots] accessories` |
| Accessories | `charm` | `ACCESSORIES_CHARM`（9） | 22 | 新 | 模组默认给玩家 |

规则：同一个来源里的"第几格"仍是 `BackpackRef.invIndex`；来源表按顺序找，先找到的生效；其余背包不动。

## 启动开关（`WornSlotsConfig`）

- 配置类型 `STARTUP`，文件 `config/mcfloatingbackpack-startup.toml`，节 `[slots]`，两项布尔：`curios`、`accessories`，默认 `true`。
- 读取：`enabled(modId)`；配置尚未载入（或键不存在）时返回 `true`（默认开）。只有 `curios`、`accessories` 两个 id 认识；别的 id 返回 `true`。
- 条件 `mcfloatingbackpack:slot_enabled {"mod": "<id>"}`：`test(context) = WornSlotsConfig.enabled(mod)`。

## 每类一只（`SlotLimit`，纯逻辑）

`allows(int othersInType, boolean enforced)`：`!enforced || othersInType == 0`。

- `othersInType`：该实体该槽位类型里、**不是**正在放入的那一格、且是浮动背包的**真实**（非装饰）格子数。
- `enforced`：该模组的有效档位是 FULL（服务端侧问 `ServerCompatLevels`，客户端侧问 `CompatConfig.policy()`，与 `WornSource.active(entity)` 同一个判断）。
- 只拦"放入"；取出、换位（先取出再放）、命令与创造模式不受它管。

## 状态转换

- 开关 true→false（重启后）：槽位类型消失，槽里的物品由饰品模组的"无效物品"处理（结果待游戏内验证）。
- 开关 false→true：槽位回来，是空的（旧物品已被还给玩家或已被取走）。
- 档位 FULL→GENERIC/OFF：槽位仍在，来源不生效，不限制放入；物品原样不动。改回 FULL 立即恢复。
