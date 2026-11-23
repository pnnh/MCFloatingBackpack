# Implementation Plan: 被 Accessories 丢掉的背包（槽位消失时归还）

**Branch**: `062-accessories-orphaned-backpacks` | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

## Summary

玩家数据读入时，把他保存的 Accessories 数据里"所在槽位类型已不存在"的浮动背包记下来；登录后约 1 秒（或玩家在此之前下线的那一刻）核对并归还（设计依据与字节码查证见 [research.md](./research.md)）：

1. **读（任意线程，`PlayerEvent.LoadFromFile`，057 同款）**：`OrphanedBackpackScan`（`compat/accessories/`，只用 NBT 与 `ItemStack`，不引用 Accessories 类型）读文件的 `neoforge:attachments/accessories:inventory_holder/accessories_containers`：对每个容器条目的 `items`、`cosmetics`，把其中的浮动背包（五个等级的物品 id）连同**所在槽位名**记成 `Entry(slotName, cosmetic, itemId, identity|null, stackTag)`；同时记下文件里**所有**这类背包按"有编号/无编号 × 物品 id"的计数（`Counts`）。结果放进 `Pending`，按玩家 UUID 存。装了 Accessories 才做。
2. **核对并归还（主线程，登录后 20 刻，或玩家下线事件，先到先做；做完删 `Pending`）**：Accessories 在第一个 tick 就归还它自己的"无效物品"（字节码查证），所以 20 刻足够晚。`compat/accessories/OrphanedBackpacks`（第三方类型只在这里）：问 Accessories 现在给该玩家的槽位类型（`AccessoriesCapability.get(player).getContainers().keySet()`，033/034 的来源同一套 API），孤立 = 条目的槽位名不在其中；再收集玩家**现在**拥有的背包：物品栏（含盔甲、副手）、光标、Accessories 现有容器里的（真实与装饰）、地上 16 格内的掉落物，得到"在场编号集"与"按物品 id 的无编号计数"。把这些交给纯逻辑 `OrphanRules.decide(...)`，得到要还的条目。
3. **纯逻辑 `OrphanRules`**（`compat/`，可单元测试）：带编号的孤立背包，编号在"在场编号集"里则不还；无编号的，按物品 id：`缺 = max(0, 文件里无编号的数 − 现在无编号的数)`，还 `min(缺, 该物品 id 的无编号孤立数)` 只。输出还几只、各是哪只。
4. **归还**：对每只，用 `ItemStack.parse` 还原成物品栈（组件原样），走 `ContainerMenuAccess(player).fallback()`（060 已用：先放物品栏，放不下掉在脚边）；聊天提示一行（语言键，中英文，区分"放不下掉在脚边"），服务器日志 INFO 一行（玩家、槽位类型、物品、编号、去向）。
5. **门**：只在 Accessories 已加载、且服务端对 `accessories` 的有效档位是 FULL（`ServerCompatLevels.specificAdaptation`）时做；任何异常（`RuntimeException`、`LinkageError`）被捕获，日志一行，不动、不影响登录。
6. **文档与测试**：README 两份（把 061 写的"Accessories 不会归还"改成"由本模组归还"以及限制）、配置注释、061 的 DELIVERY 加一句指向；纯逻辑测试（`OrphanRules`）、资源与接线测试（语言键、只在 Accessories 加载时、20 刻与下线事件、无第三方类型进入非 `compat/accessories/` 的类）；**游戏内验证**见 [quickstart.md](./quickstart.md)。

不做：Curios（它自己归还）、槽位还在而格数变少（Accessories 自己在第一个 tick 归还，字节码查证）、别的物品、新网络包。协议号不改（22），`DataRevision` 仍 6。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；Accessories 1.1.0-beta.54（已有的 `compileOnly`，不新增）

**Storage**: 无新存储；读玩家数据文件（只读）

**Testing**: JUnit 5；纯逻辑 + 资源与接线文本检查；游戏内：容器 `mcfb-c1`（复现 061 的丢失步骤，再验证归还与不复制）

**Target Platform**: 服务端

**Constraints**: 宪法 II（归还在服务端）、III、V、VII（读取只用普通 NBT，第三方类型只在 `compat/accessories/`，档位门，无反射/mixin，出错不动）；协议号不改；`DataRevision` 仍 6；没装 Accessories 或档位非 FULL 时与 062 之前完全一致

**Scale/Scope**: 新增 3 个类（纯 `compat/OrphanRules`；`compat/accessories/OrphanedBackpackScan`、`OrphanedBackpacks`），改 2 个（`MCFloatingBackpack` 接事件与 tick、`AccessoriesCompat` 暴露入口），语言键 2 个（中英），文档 4 处

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不碰界面 | PASS |
| II. Server Owns Items | 找出、核对、归还全在服务端；没有客户端参与 | PASS |
| III. One Feature At A Time | 只做"整个槽位类型不存在"的归还 | PASS |
| IV. Vanilla Path Before Compat | 与原版容器无关 | PASS（不适用） |
| V. Verify In A Client Session | 容器里复现丢失并验证归还、不复制、下线竞态；没验证的如实写明 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 登录时多读一次已经要读的文件（057 已读，这里再读一次，只在装了 Accessories 时），无运行时循环 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 这是对 Accessories 的专门适配：档位 FULL 才做，读数据只用 NBT、第三方类型（`AccessoriesCapability`）只在 `compat/accessories/`，出错不动，无新依赖/反射/mixin | PASS |

设计后复核：无违规项。

## Project Structure

### Documentation (this feature)

```text
specs/062-accessories-orphaned-backpacks/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── scan-and-return.md   # 读哪些 NBT 路径、Pending、核对规则、归还与提示
└── tasks.md                 # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── compat/OrphanRules.java                              # 纯逻辑：该还哪些
├── compat/accessories/OrphanedBackpackScan.java         # 读文件 NBT（无第三方类型）
├── compat/accessories/OrphanedBackpacks.java            # Pending、核对、归还、提示（用 Accessories 的 API）
├── compat/accessories/AccessoriesCompat.java            # 暴露给主类的入口
└── MCFloatingBackpack.java                              # LoadFromFile、登录后 20 刻、下线事件

src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json

src/test/java/pnnh/mcfloatingbackpack/
├── compat/OrphanRulesTest.java
└── resources/OrphanedBackpacksWiringTest.java
```

**Structure Decision**: 单项目；与 058～061 一致，第三方类型只在 `compat/accessories/`。

## Complexity Tracking

无违规项，无需填写。
