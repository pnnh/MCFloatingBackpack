# Implementation Plan: 按模组的服务端档位

**Branch**: `059-server-compat-levels` | **Date**: 2026-11-20 | **Spec**: [spec.md](./spec.md)

## Summary

给服务端加一份与客户端同语法的"模组 → 档位"配置，并让服务端所有按模组做的决定都问它（设计依据与源码查证见 [research.md](./research.md)）：

1. **配置**：服务端配置 `[compat].modLevels`（`defineListAllowEmpty`，与客户端同形），放在 058 已有的 `[compat]` 节里（`ThirdPartyConfig.define` 里加一项）。读取、解析、缓存沿用客户端 `CompatConfig` 的做法：列表对象不变就不重新解析，变了才解析并把错误项写 WARN；NeoForge 自己监视配置文件并触发 `Reloading`，所以改了不用重开世界（要在游戏里验证）。
2. **有效档位**（`ServerCompatLevels`，MC 层 + 纯规则）：`effective(modId)` = `max(服务端档位, 客户端档位)`（`CompatLevel` 的声明顺序 FULL < GENERIC < OFF，取更严就是取较大的序号）。客户端档位直接用 `CompatConfig.policy()`：它在专用服务器上读不到配置时返回 `CompatPolicy.DEFAULT`（全 FULL），对"取更严"是中性元素，所以**不需要判断是不是专用服务器**。原版恒 FULL。
3. **门禁**：`ServerCompatGate.allows(Action, owner)` 保持纯逻辑，改成按档位回答：`OFF → 否`，其余 → 是；档位从一个可替换的来源取（默认全 FULL，启动时设成 `ServerCompatLevels::effective`），所以现有测试（恒允许）不变。6 个入口都改成问一个 MC 层的包装 `ServerCompatDenials.allows(player, menu, action)`：先问门禁；被拒绝时发一条限流的聊天提示并写 DEBUG 日志。
4. **提示限流**（`DenialThrottle`，纯逻辑）：每个玩家只记"最近一次提示过的 (界面编号, 模组)"；同一个玩家对同一个已打开的界面不重复提示；玩家关闭界面（`PlayerContainerEvent.Close`）或下线时清掉。语言键 `message.mcfloatingbackpack.compat.server_off`。
5. **专用适配**：`BridgeDispatch` 多一个条件 `Predicate<String> levelAllows`（`ServerCompatLevels::specificAdaptation`，即有效档位是 FULL），与版本门、`allowUntestedVersions`、出错关闭并列，任一不满足桥不被问。Curios、Accessories 的背着来源：`WornSource` 加默认方法 `active(P player)`（默认回 `active()`），`BackpackSearch` 全部改问 `active(player)`（`activeSource` 加玩家参数）；两个实现按实体所在的一侧选档位——**服务端侧**问 `ServerCompatLevels.specificAdaptation`，**客户端侧**仍问 `CompatConfig.policy()`（渲染与客户端自己的查找不变）。
6. **文档**：README 两份把 058 写的"暂时不能单独关闭"改成新说明；配置注释；语言键中英文。
7. **测试**：纯逻辑（有效档位 3×3、门禁允许表、限流、`BridgeDispatch` 新条件、`WornSource` 默认方法与 `BackpackSearch` 的 `active(player)`）、资源（配置注释、语言键）、接线（6 个入口都经包装、包装问门禁、门禁来源接到服务端有效档位、来源按侧选档位、`Close` 事件清限流）、README 对齐；**游戏内验证**见 [quickstart.md](./quickstart.md)。

不做：按玩家/权限细分、白名单、客户端界面屏蔽、新网络包、面板显示逻辑改动、`BackpackTrashSendPayload` 的改动（它只对原版菜单工作）。协议号仍 21，`DataRevision` 仍 6。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；无新依赖

**Storage**: 无（只有服务端配置多一项；限流状态在内存）

**Testing**: JUnit 5；纯逻辑 + 资源与接线文本检查；游戏内：容器 `mcfb-c1`（单人 / 集成服务器）加 `mcfb-srv`（专用服务器，验证 Curios 来源）

**Target Platform**: 服务端为主；客户端只在 `WornSource` 的客户端侧保持原样

**Constraints**: 宪法 II（物品交换的拒绝在服务端）、III（一个 feature）、V（游戏内验证）、VII（服务端判断；原版不可配置；无新依赖/mixin/反射）；协议号仍 21；`DataRevision` 仍 6；**空配置时行为与 059 之前完全一致**（FR-006）

**Scale/Scope**: 新增约 5 个类（纯逻辑 `EffectiveLevel`、`DenialThrottle`；MC 层 `ServerCompatLevels`、`ServerCompatDenials`；`ServerCompatConfig` 视情况并入 `ThirdPartyConfig`），改约 12 个（`ServerCompatGate`、6 个载荷、`BridgeDispatch`、`NetworkStorageBridges`、`WornSource`、`BackpackSearch`、`WornBackpacks`、`CuriosWornSource`、`AccessoriesWornSource`、`ThirdPartyConfig`、`MCFloatingBackpack`、语言、README ×2），语言键 1 个（中英）

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不碰界面 | PASS |
| II. Server Owns Items | 拒绝与专用适配的开关都在服务端；客户端仍只发请求；提示是服务端发的聊天 | PASS |
| III. One Feature At A Time | 只做服务端档位；反向（060）、面板隐藏（需要协议）都不做 | PASS |
| IV. Vanilla Path Before Compat | 原版恒 FULL，不可配置；`minecraft=OFF` 被忽略 | PASS |
| V. Verify In A Client Session | 容器里验证 OFF 提示与物品不动、GENERIC 关桥、专用服务器上 curios=OFF、热改；手感不涉及 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 无新资源 | PASS（不适用） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 这正是 VII 要求的"依赖设置、关系到物品移动的判断在服务端做"；无新依赖、无反射 | PASS |

设计后复核：无违规项。两个要写进交付说明的事实：(1) 客户端不知道服务端档位，所以在服务端 OFF 的界面上面板仍显示、手势仍发出（规格已写明的代价）；(2) 客户端的背部层仍按客户端配置绘制，可能画出服务端不认的背包。

## Project Structure

### Documentation (this feature)

```text
specs/059-server-compat-levels/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── levels-and-gate.md       # 有效档位、门禁允许表、专用适配条件、WornSource 的侧
│   └── config-and-texts.md      # 配置项与注释、语言键、日志、README 文字
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── compat/EffectiveLevel.java          # 新（纯）：stricter(a, b)、effective(serverPolicy, clientPolicy, modId)
├── compat/DenialThrottle.java          # 新（纯）：每个玩家最近一次提示过的 (界面编号, 模组)；shouldNotify、forget
├── compat/ServerCompatLevels.java      # 新（MC）：服务端 modLevels 的读取、缓存、错误项日志；effective、specificAdaptation
├── compat/ServerCompatDenials.java     # 新（MC）：6 个入口共用的 allows(player, menu, action)：门禁 + 限流提示 + DEBUG 日志；onClose/onLogout 清限流
├── compat/ServerCompatGate.java        # 改：按档位回答（OFF 否），档位来源可替换，默认全 FULL
├── compat/ThirdPartyConfig.java        # 改：[compat] 节里加 modLevels 项（注释见 contracts）
├── compat/BridgeDispatch.java          # 改：多一个 Predicate<String> levelAllows
├── compat/NetworkStorageBridges.java   # 改：传入 ServerCompatLevels::specificAdaptation
├── compat/curios/CuriosWornSource.java       # 改：active(LivingEntity) 按侧选档位
├── compat/accessories/AccessoriesWornSource.java  # 改：同上
├── inventory/worn/WornSource.java      # 改：default boolean active(P player)
├── inventory/worn/BackpackSearch.java  # 改：firstWorn/activeSource 问 active(player)
├── inventory/worn/WornBackpacks.java   # 改：activeSource 带实体参数
├── network/*Payload.java               # 改（6 个入口）：ServerCompatGate.allows(...) → ServerCompatDenials.allows(player, menu, action)
├── MCFloatingBackpack.java             # 改：启动时设门禁的档位来源；Close/Logout 清限流
src/main/resources/assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json   # 加 1 键
README.md, README_CN.md                  # 改：兼容说明
src/test/java/…                          # 见 tasks
```

**Structure Decision**: 与 058 一致：规则（取更严、允许表、限流）是不含 Minecraft 类型的纯类；Minecraft 层只读配置、发消息、把真对象接到纯规则；入口处只换一行调用。

## Complexity Tracking

无违规项。
