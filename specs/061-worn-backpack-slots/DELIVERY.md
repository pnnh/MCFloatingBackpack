# 交付记录：饰品模组里的专用槽位与通用槽位（061）

日期：2026-11-23。状态：已实现并在容器里验证，用户确认功能没问题（T021，2026-11-23）；宪法 VII 的修订措辞**仍待用户确认（T018）**，随本次一并提交的是草案。

## 基线与结果

- 基线（060 提交后）：238 类 1832 例；现在 243 类 1857 例，`./gradlew build -PcleanRuntime -PnoJei` 通过，jar 里没有 `top/theillusivec4`、`io/wispforest` 的类。
- 协议号**不改**（22）：v0.1.0 是 21，22 没发布过。**若 060 先于 061 单独发布，必须把协议号升到 23**（旧 22 的客户端遇到序号 ≥ 6 的来源会退化，解码处有越界保护，但行为不对）。`DataRevision` 仍 6。

## 做了什么

- `BackpackRef.Source` 末尾追加 4 个值；每个槽位类型一个独立的背着来源（Curios `back`/专用/`charm` = 10/11/12，Accessories 20/21/22）；`CuriosWornSource`、`AccessoriesWornSource` 参数化。
- 数据文件：Curios `slots`、`entities`；Accessories `slot`、`entity`、`group/chest`，全部带条件 `mcfloatingbackpack:slot_enabled`；四个 tag（`floating_backpack`、`charm` × 两个模组）；两张 16×16 贴图；语言键中英文。
- 启动开关：FML `STARTUP` 配置 `config/mcfloatingbackpack-startup.toml`，`[slots] curios`、`accessories`，默认 `true`；`WornSlotsSwitches`（纯）+ `WornSlotsConfig` + `SlotEnabledCondition`。
- 每类一只：`SlotLimit`（纯）；Curios `BackpackCurioItem.canEquip`（`CuriosApi.registerCurio`）、Accessories `BackpackAccessory.canEquip`；档位非 FULL 放行，异常放行。
- **顺带修了一处诊断的误报**：`BackpackWatchService` 只看第一只背着的背包，另一只放在别的槽里会被报"gone"（只是日志，不影响数据）；加了 `BackpackSearch.allWorn`，诊断现在看全部。
- 宪法 VII 加了子节"Registering content in another mod's data"（2.3.0，**草案，待确认**）；`docs/plan/third-party-compat.md` 第 8 节；README 两份；配置注释。

## 游戏内验证（容器 `mcfb-c1`，Curios 9.5.1 与 Accessories 1.1.0-beta.54，单人）

| 项 | 结果 |
|---|---|
| 条件起作用 | 开关默认：日志 "Loaded 11 curio slots"；`curios=false` 重启：**"Loaded 10 curio slots"**，Curios 里没有专用槽；Accessories 开关为 false 时它的界面里少一个槽，**两个开关互不影响** ✓ |
| 槽位与名字 | Accessories 里"Floating Backpack"槽出现，与 `charm`、`back` 同在"胸部"组（合并成功，没有退到"未分类"）✓；Curios 的侧栏里同样出现 ✓。**图标**：Accessories 一侧显示了我们画的图（空槽里的灰色线稿）；Curios 一侧也显示；好不好看由用户确认 |
| 只有浮动背包放得进 | 物品提示里显示"Slots: Back, Floating Backpack, Charm"✓（别的物品放不进由 tag 保证，没有逐一试） |
| 放进去、持久、换位 | 铁、皮革、金、钻石、下界合金多种等级放进专用槽/`back`/`charm`，关闭界面再打开仍在；两只之间互换位置正常 ✓ |
| `B` 键 | 只有专用槽里有背包时，`B` 打开的就是它（金背包 WMFF）✓；`back` 里也有时先取 Curios 的 `back`（顺序 10 在 21 之前）✓ |
| 每类一只 | 把 Accessories `charm` 调成 3 格（数据包）：`charm` 里已有一只时，**第二只被服务端拒绝**（物品回到快捷栏）；放进 `back` 的第一格（别的类型）**成功** ✓。**客户端有一帧预测**：第二只会短暂显示在槽里，服务端拒绝后恢复（客户端侧的判断没起作用，原因没有深查；不丢物品） |
| 老存档 | `back` 里的老背包不受影响 ✓（本机世界里 `back` 的背包一直在） |
| 开关关闭后槽里的东西 | **Curios：还给玩家**（专用槽里的下界合金、金背包回到了物品栏）✓；**Accessories：物品消失**（专用槽里的两只背包，一只带内容，重启后既不在物品栏、地上也没有，重新打开开关也没回来；玩家数据里没有）⚠ |

## 补验证（2026-11-23，容器 `mcfb-c1`，其中专用服务器用 `mcfb-srv` 加 `mcfb-c1` 作客户端 P1）

| 项 | 结果 |
|---|---|
| **Curios 同类型最多一只** | 用 `curios-common.toml` 的 `slots = ["id=back;size=2;operation=SET","id=charm;size=3;operation=SET"]` 把 `back` 调成 2 格、`charm` 调成 3 格，再用一个数据包把 `charm` 分配给玩家。`back` 第一格有背包时，**第二个 `back` 格放不进**（物品留在光标上）；`charm` 里已有一只时，**另一个 `charm` 格放不进**；`back`、专用、`charm` 各放一只**可以同时放**（关闭界面再打开仍在）✓ |
| **Curios 的 `charm` 槽** | 同上，能放浮动背包 ✓（Curios 默认没有 `charm` 槽，是数据包分配的） |
| **`curios=GENERIC`** | 客户端设 `curios=GENERIC`：上面被拒的第二个 `back` **放得进去**（不限制），关闭再打开仍在 ✓ |
| **专用服务器：开关在启动时被读取** | `mcfb-srv` 上 `curios=true`：日志 "Loaded 11 curio slots"，客户端 P1 的 Curios 里有专用槽；改成 `curios=false` 重启：服务端日志 **"Loaded 10 curio slots"**（槽位数据在服务端线程出现之前就已加载，所以 SERVER 配置读不到，STARTUP 配置读得到），P1 的客户端（它自己的开关是 true）**没有**专用槽——联机时以服务端为准 ✓；槽里的背包由 Curios 还回了 P1 的快捷栏 ✓ |
| **专用服务器：`B` 键与档位（059 的情形 9）** | 背包放进专用服务器上的 Curios 专用槽，`B` 打开的就是它 ✓；服务端 `[compat].modLevels = ["curios=OFF"]`（热改，不重开世界）：`B` **不再认**它（面板不开）；改回空列表：又认了 ✓ |

## 仍没验证的

- 059 的情形 10（服务端 `sophisticatedstorage=OFF`，精妙存储箱子上 Ctrl+点击被拒并提示）：这次没做（要放置精妙存储箱子，容器里放置方块的按键绑定要先改），059 的 T033 因此仍未勾。
- 联机时客户端的 Accessories 一侧（只验证了 Curios）；`accessories=GENERIC` 在 Accessories 上的游戏内表现。
- 装饰（cosmetic）槽放入时的判断；Accessories 的"组"在别的整合包里的排布。

## 如实说明的取舍与风险

1. **Accessories 会丢槽位里的物品**（见上表）：是它自己的行为，不是我们的代码。**已由 062 处理**（`specs/062-accessories-orphaned-backpacks/`：登录时从玩家数据文件里找出并归还）；下面是 061 当时的记录。README 与配置注释都写了"先取下背包再关开关"，并指出背包内容可以从 030 的快照恢复（`/mcfloatingbackpack snapshot`、快照窗口）。若要更保险，另开 feature：在玩家数据读入时（`PlayerEvent.LoadFromFile`，057 已有）检测并保护 Accessories 数据里被孤立的背包。
2. 客户端对"第二只"的判断没起作用（只是视觉上的一帧）。
3. 宪法 VII 的修订是我起草的，**需要用户确认措辞**。
4. 容器环境改动已还原：启动配置两项都恢复为 true，测试用的数据包 `t61` 已删除，客户端停在标题界面；Accessories 的首次启动界面选了"Original"（`accessories.json5` 变了，只影响开发容器）。

## 宪法复查

II（槽位与限制由两个饰品模组在服务端判断）、III（只做槽位与限制）、V（容器里验证如上，没验证的如实写明）、VI（两张小贴图）、VII（第三方类型只在 `compat/curios/`、`compat/accessories/`；无新依赖、无反射、无 mixin；"注册内容"的开关见新子节，**待确认**）：通过。
