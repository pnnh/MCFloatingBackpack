# Implementation Plan: 饰品模组里的专用槽位与通用槽位

**Branch**: `061-worn-backpack-slots` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

## Summary

让浮动背包能放进 Curios、Accessories 的**专用槽位 `floating_backpack`** 和**通用 `charm` 槽**，每个槽位类型最多一只；专用槽位的注册由一个**启动时读取的布尔开关**控制（设计依据与源码查证见 [research.md](./research.md)）：

1. **每个槽位类型一个独立的背着来源**：`BackpackRef.Source` 末尾追加 `CURIOS_DEDICATED`、`CURIOS_CHARM`、`ACCESSORIES_DEDICATED`、`ACCESSORIES_CHARM`（序号 6～9，已有序号不变）。`CuriosWornSource`、`AccessoriesWornSource` 改成带参数的类（槽位类型 id、来源枚举、顺序）；现有的 `back` 是同一个类的一个实例，行为不变。顺序：Curios `back` 10、专用 11、`charm` 12；Accessories 20、21、22。`BackpackSearch` 的"先找到的生效"规则不动。
2. **槽位数据**（全是数据包文件，模组构造时不写任何槽位代码）：Curios 的 `slots/floating_backpack.json`、`entities/player_floating_backpack.json`；Accessories 的 `slot/floating_backpack.json`、`entity/player_floating_backpack.json`、`group/chest.json`（把专用槽放进"胸部"组，找不到合并语义时退回 `unsorted`）。这四个文件都带 `neoforge:conditions`。四个 tag 文件：`curios:charm`、`curios:floating_backpack`、`accessories:charm`、`accessories:floating_backpack`（tag 文件无条件：tag 里有物品而槽位不存在是无害的）。图标两张 16×16 贴图，语言键中英文。
3. **启动开关**：新的配置类型 `ModConfig.Type.STARTUP`（FML 4.0.44 提供：注册的瞬间就读取，文件 `config/mcfloatingbackpack-startup.toml`），两项布尔 `[slots] curios = true`、`accessories = true`（键名见 contracts）；注释写明"改动需重启、关闭后放在专用槽里的背包由饰品模组处理，请先取下"。一个自定义的 NeoForge 条件类型 `mcfloatingbackpack:slot_enabled`（`{"type": "mcfloatingbackpack:slot_enabled", "mod": "curios"}`），`test(context)` 读开关；用 `DeferredRegister` 注册到 `NeoForgeRegistries.CONDITION_SERIALIZERS`（不依赖任何饰品模组的类，常驻注册）。两个模组的加载器都会对槽位与实体文件调用 `ICondition.conditionsMatched`（已读字节码确认），传入的是普通 `RegistryOps`，上下文为 `IContext.EMPTY`，我们的条件不用上下文，所以可用。
4. **每类一只**：纯逻辑 `SlotLimit.allows(othersInType, enforced)`（`enforced` 即有效档位为 FULL）；Curios 用 `CuriosApi.registerCurio(item, ICurioItem)` 给五个等级注册一个只覆盖 `canEquip` 的 `ICurioItem`（`ICurioItem.canEquipFromUse` 默认为否，不会引入右键装备；注册可以在通用 setup 里做，Curios 是在第一次查询物品能力时才读登记表），`canEquip(SlotContext, stack)` 数该实体该槽位类型里**别的**格子里的浮动背包；Accessories 沿用现有的 `BackpackAccessory`，加 `canEquip(stack, SlotReference)`。两个模组都只在**放入**时问（Curios 的 `DynamicStackHandler.isItemValid`、Accessories 的 `AccessoriesAPI.canEquip`），不会在 tick 里把已有的背包踢出去，所以绕过限制放进去的多出的背包不会被动过。
5. **档位**：三种来源的 `active(entity)` 沿用 059（服务端侧问 `ServerCompatLevels.specificAdaptation(modId)`，客户端侧问 `CompatConfig.policy()`）；`canEquip` 里用同一个判断，非 FULL 放行。
6. **文档**：README 两份（兼容表加三类位置、每类一只、开关、关闭后的警告、联机时客户端档位只影响视觉）；`docs/plan/third-party-compat.md` 记设计原则；`docs/plan/worn-backpack-compat.md` 的状态；**宪法 VII 的一处修订**（见下）；配置注释、语言键。
7. **测试**：纯逻辑（`SlotLimit`、来源表的顺序与序号稳定）、资源（槽位、实体、tag、语言键、图标逐个对得上；条件 JSON 的类型名与注册名一致；配置键与条件里读的键一致）、接线（来源枚举只追加、`CuriosApi.registerCurio` 只在 Curios 已加载时调用、`STARTUP` 配置在构造函数里注册、条件类型用 `NeoForgeRegistries.CONDITION_SERIALIZERS`）、漂移（沿用 058）；**游戏内验证**见 [quickstart.md](./quickstart.md)。

不做：`curio`、`accessories:any`、Curios 里强行分配 `charm`、重复渲染、新网络包、改背包存储数据。协议号**不改**：`BackpackRef` 的来源序号走原有的整数编码；v0.1.0 是 21，当前 22 还没发布过，所以 061 与 060 可以同用 22（若 060 在 061 之前单独发布，必须先把 061 并入或把协议号升到 23；写进交付说明）。`DataRevision` 仍 6。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；Curios 9.5.1、Accessories 1.1.0-beta.54（已有的 `compileOnly`，不新增）

**Storage**: 无新存储；数据包文件与一份启动配置

**Testing**: JUnit 5；纯逻辑 + 资源与接线文本检查；游戏内：容器 `mcfb-c1`（单人 / 集成服务器，Curios 与 Accessories 各一轮）、必要时 `mcfb-srv`（专用服务器上开关的启动时读取）

**Target Platform**: 客户端与服务端；槽位数据在服务端，客户端只同步

**Constraints**: 宪法 II、III、V、VII（见下）；`DataRevision` 仍 6；协议号不改；两个饰品模组缺席时一切不加载（来源在 `ModList.isLoaded` 后才注册，条件类型不引用它们）；`back` 行为与老存档不变

**Scale/Scope**: 新增约 4 个类（纯逻辑 `SlotLimit`；条件与开关 `WornSlotsConfig`、`SlotEnabledCondition`；Curios 的 `BackpackCurioItem`），改约 8 个（两个 `WornSource`、两个 `Compat` 入口、`BackpackAccessory`、`BackpackRef.Source`、`MCFloatingBackpack`、`ModItems` 不动），数据文件 9 个、贴图 2 张、语言键约 4 个（中英）

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不碰界面 | PASS |
| II. Server Owns Items | 槽位与限制由两个饰品模组在服务端判断；我们只提供"能否放入"的回答 | PASS |
| III. One Feature At A Time | 只做槽位与限制；重复渲染、别的槽位类型不做 | PASS |
| IV. Vanilla Path Before Compat | 与原版容器无关 | PASS（不适用） |
| V. Verify In A Client Session | 容器里验证放入、`B` 键、渲染、每类一只、开关、槽位消失时的物品去向；手感不涉及 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 两张 16×16 贴图，无运行时开销；来源表多 4 项，每项只在背部层查找时多一次槽位查询（按实体的槽位类型取，不遍历下标） | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖、无反射、无 mixin；第三方类型只在 `compat/curios/`、`compat/accessories/`。**需要修订宪法的一处措辞**：VII 写着"每个对其它模组的主动交互 MUST 由模组 id → 档位的设置控制"，而"向第三方模组的数据注册内容"（槽位、条件）在数据包加载时就已决定，而服务端档位晚于它才可用；本 feature 用**启动时读取的按模组布尔开关**满足"可关闭"的本意（用户已确认）。修订方案：在 VII 里加一个子节，规定"向另一个模组注册内容"这一类主动交互用 STARTUP 配置里的按模组开关控制（默认开、重启生效），档位仍管适配代码 | **PASS，条件：随本 feature 修订宪法 VII（MINOR，2.3.0）** |

设计后复核：同上，无其它违规项。宪法修订作为 tasks 里的一项，按治理规则写 Sync Impact Report 并更新版本号；**需用户在那一步确认措辞**。

## Project Structure

### Documentation (this feature)

```text
specs/061-worn-backpack-slots/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── data-files.md          # 9 个数据文件、2 张贴图、语言键、条件类型、启动配置
│   └── sources-and-limit.md   # 来源表、WornSource 的参数化、每类一只的两个实现、纯逻辑
└── tasks.md                   # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/BackpackRef.java                      # Source 末尾追加 4 个值
├── inventory/worn/SlotLimit.java                   # 纯逻辑：每类一只
├── compat/WornSlotsConfig.java                     # STARTUP 配置：两个开关；条件读它
├── compat/SlotEnabledCondition.java                # ICondition：mcfloatingbackpack:slot_enabled（只用 NeoForge 类型）
├── compat/curios/CuriosWornSource.java             # 参数化：槽位类型、来源、顺序
├── compat/curios/CuriosCompat.java                 # 注册 4 个实例中的 3 个（back、专用、charm）与 registerCurio
├── compat/curios/BackpackCurioItem.java            # ICurioItem：canEquip（每类一只）
├── compat/accessories/AccessoriesWornSource.java   # 同上
├── compat/accessories/AccessoriesCompat.java
├── compat/accessories/BackpackAccessory.java       # 加 canEquip
└── MCFloatingBackpack.java                         # 注册 STARTUP 配置与条件类型

src/main/resources/
├── data/mcfloatingbackpack/curios/slots/floating_backpack.json
├── data/mcfloatingbackpack/curios/entities/player_floating_backpack.json
├── data/mcfloatingbackpack/accessories/slot/floating_backpack.json
├── data/mcfloatingbackpack/accessories/entity/player_floating_backpack.json
├── data/mcfloatingbackpack/accessories/group/chest.json
├── data/curios/tags/item/{charm,floating_backpack}.json
├── data/accessories/tags/item/{charm,floating_backpack}.json
├── assets/mcfloatingbackpack/textures/slot/floating_backpack.png          # Curios（blocks 图集的 slot/ 目录）
├── assets/mcfloatingbackpack/textures/gui/slot/floating_backpack.png      # Accessories（gui 图集）
└── assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/worn/SlotLimitTest.java
├── compat/WornSlotsConfigTest.java（键名与默认值）
└── resources/WornSlotsResourcesTest.java、WornSlotsWiringTest.java、ThirdPartyDriftTest（扩展）
```

**Structure Decision**: 单项目；第三方类型只在 `compat/curios/`、`compat/accessories/`；条件与配置只用 NeoForge/FML 类型，放在 `compat/` 根下（与 058 的 `ThirdPartyConfig` 同层）。

## Complexity Tracking

| 偏离 | 为什么需要 | 否决的更简单做法 |
|---|---|---|
| 宪法 VII 的一处修订 | "注册内容"的开关必须在数据包加载前可读，而档位配置（SERVER）晚于数据包 | 按档位条件注册（首次加载读不到，要自动 reload；槽位消失的后果取决于对方模组）；无条件注册（强制、不可关闭，违背用户要求） |
| 新配置文件 `mcfloatingbackpack-startup.toml` | 唯一在注册瞬间就读取的配置类型 | 复用客户端配置（专用服务器上不加载）；复用服务端配置（太晚） |
