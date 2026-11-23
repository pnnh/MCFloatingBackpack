# Implementation Plan: 网络终端 → 面板（集成终端与 AE2 的"取出"方向）

**Branch**: `060-network-terminal-take` | **Date**: 2026-11-20 | **Spec**: [spec.md](./spec.md)

## Summary

在面板可见时，Ctrl+点击集成终端或 AE2 终端网格里的条目，一步把该物品的一组取进背包（设计依据与对集成终端 1.11.2、AE2 19.2.20 的 jar 查证见 [research.md](./research.md)）：

1. **客户端挂在现有手势上**：`QuickTransferInput.beginFromScreen` 已经负责面板可见时 Ctrl+左键点屏幕里的格子（点中集成终端/AE2 的网格格子，今天会发一个对服务端空格子的无效"取出"请求）。在它找格子之前先问一个新的客户端注册表 `NetworkTerminalClients.entryAt(screen, x, y)`：认得就把点击变成"取网络条目"请求并**吞掉这次按下**（不进入拖动手势，所以只有点击、没有拖动，满足 FR-004）；不认得就走原来的路。
2. **两个终端各一个客户端识别器**（第三方类型只在各自的 `compat/<modid>/`）：集成终端——悬停的菜单格子属于当前页的 `getTabSlots` 且当前页是物品存储页（客户端的页类不是合成页的子类，成分是物品），条目 = 该格子显示的物品栈；AE2——屏幕是 `MEStorageScreen`、格子是 `RepoSlot`，条目 = `getEntry()` 里的物品键（`AEItemKey`）与序号 `serial`，要求网络内数量 > 0。识别只用公开方法，无 mixin、无反射。
3. **新请求包 `BackpackNetworkTakePayload`（客户端 → 服务端）**：`containerId`、`ItemStack what`（单个、带数据）、`long handle`（AE2 的序号，集成终端为 0）。**不带数量**，服务端自己定为 `what.getMaxStackSize()`。协议号 **21 → 22**。
4. **服务端处理**（`handleServer`，沿用 `BackpackTakeFromContainerPayload` 的前置条件）：背包有效 → `ServerCompatDenials.allows(..., TAKE_FROM_CONTAINER)`（059：OFF 拒绝并提示）→ 菜单就是请求的菜单且光标为空 → 校验请求 → `BackpackGate.acquireForPanel` → `NetworkStorageBridges.tryTake(...)`（桥只在版本门、有效档位 FULL、未出错关闭时被问）取到物品栈 → `backpack.insertIntoMain` 放进背包 → 放不下的部分 `tryGiveBack` 退回网络 → 还退不回的部分走 012 已有的兜底（玩家物品栏，再不行掉在脚下，WARN）→ 保存背包、同步。
5. **桥接口扩展**：`NetworkStorageBridge` 加三个有默认值的方法 `handlesTake(menu)`（默认 false）、`take(player, menu, what, handle, amount)`、`giveBack(player, menu, stack, handle)`；`BridgeDispatch` 加 `tryTake`、`tryGiveBack`，出错、不合法返回值、档位、版本门的规则与 058/059 的 `tryInsert` 完全一样。
6. **服务端的取与还**：集成终端——取：对当前页所选通道 `extract(what 带数量, 精确匹配条件, false)`；还：沿用 058 的 `insert`。AE2——取：把菜单的"携带物"当作中转：`menu.handleInteraction(serial, PICKUP_OR_SET_DOWN)`（携带物为空时，AE2 自己做权限、能量检查并从网络提取至多一个满堆叠放到携带物里），读走携带物、清空；还：把剩余放回携带物再 `handleInteraction(serial, PICKUP_OR_SET_DOWN)`（携带物非空时 AE2 把它放回网络），返回仍留在携带物里的。**只用公开 API**，不碰 AE2 的能量与权限类型。
7. **范围表、加载声明、依赖**：AE2 进 `ThirdPartyTable`（`ae2` `[19.2.0, 19.3.0)`，不要求别的模组）；`neoforge.mods.toml` 加 `ae2` 可选依赖 `[0,)`；`build.gradle` 加 `compileOnly("maven.modrinth:ae2:19.2.20") { transitive = false }`（与已有 `localRuntime` 同版本）；防漂移测试扩到 AE2；README 兼容表两行改成"终端 → 面板可用"。
8. **客户端启用条件**（`NetworkTerminalClients`）：模组已装、**客户端自己的**版本在范围表内（`CompatFinding.check`，无警告）、客户端档位为 FULL（`CompatConfig.policy().specificAdaptation`）；不满足就不认条目，Ctrl+点击照旧。服务端不信任客户端，所有检查在服务端重做。
9. **测试**：纯逻辑（`NetworkTakeRules`：请求校验、数量；`BridgeDispatch` 的 `tryTake`/`tryGiveBack`；守恒的"放进背包/退回/兜底"计算；请求包编解码）、接线（第三方类型只在 `compat/<modid>/`、协议号 22、请求只从客户端手势下发、客户端启用条件、`QuickTransferInput` 先问注册表）、README 与范围表/`mods.toml`/`build.gradle` 对齐；**游戏内**：集成终端网络试验台与 AE2 的 ME 终端各一轮（见 [quickstart.md](./quickstart.md)）。

不做：Ctrl+拖动取出、右键取一半/单个、集成终端的合成页与末影箱页、别的成分（流体、能量）、AE2 的合成/样板编码这些终端的**别的**功能、让客户端知道服务端档位、面板显示逻辑改动、别的 Cyclops/AE2 附属模组。`DataRevision` 仍 6。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；新增**仅编译**依赖 `ae2:19.2.20`（`maven.modrinth`，`transitive = false`）；集成终端的四个已有

**Storage**: 无新增存储（`DataRevision` 仍 6）

**Testing**: JUnit 5；纯逻辑 + 资源与接线文本检查；游戏内：容器 `mcfb-c1`（058 的集成动力学试验台；058 搭过的 ME 终端）

**Target Platform**: 客户端（识别与发包）+ 服务端（校验与提取）

**Constraints**: 宪法 II（取出由服务端完成，客户端只发"我点了哪个条目"）、III（一个 feature）、V（游戏内验证）、VII（第三方类型只在 `compat/<modid>/`；可选依赖 `[0,)`；无反射/mixin）；**协议号 21 → 22**；`DataRevision` 仍 6；**空配置、没装这两个模组时行为与 060 之前完全一致**

**Scale/Scope**: 新增约 12 个类（纯：`NetworkTakeRules`；通用：`BackpackNetworkTakePayload`、`NetworkTerminalClients`、`NetworkTerminalClient`、`NetworkEntry`；第三方：`IntegratedTerminalsClient`、`Ae2Bridge`、`Ae2Client`、`Ae2Compat`；扩展：`ClientCompatVersions`、`NetworkTakeServer`），改约 10 个（`NetworkStorageBridge`、`BridgeDispatch`、`NetworkStorageBridges`、`IntegratedTerminalsBridge`、`IntegratedTerminalsCompat`、`QuickTransferInput`、`BackpackNetwork`、`ThirdPartyTable`、`ClientSetup`/`MCFloatingBackpack`、`neoforge.mods.toml`、`build.gradle`、README ×2、八处写死 `versioned("21")` 的测试），语言键 0～1 个

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不碰界面；只在终端屏幕上拦截一次 Ctrl+点击（面板本来就叠在上面） | PASS |
| II. Server Owns Items | 取出、放进背包、退回、兜底全在服务端；客户端只报"条目的物品种类与标识"，不报数量，不被信任 | PASS |
| III. One Feature At A Time | 只做反向取出（点击）；拖动、半组、别的页都不做 | PASS |
| IV. Vanilla Path Before Compat | 原版与现有通用路径不变；手势只在认得终端条目时才拦截 | PASS |
| V. Verify In A Client Session | 容器里两个终端各一轮；手感待用户 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 无新资源 | PASS（不适用） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 新依赖仅编译、可选、`[0,)`；第三方类型只在 `compat/ae2/` 与 `compat/integratedterminals/`；客户端档位与版本控制手势，服务端用 059 的档位；无反射/mixin | PASS |

设计后复核：无违规项。三个要写进交付说明的事实：(1) 请求带 AE2 条目序号这一"不透明标识"，服务端由 AE2 自己解析与校验（过期序号什么也不做）；(2) 集成终端的取出中途抛异常时可能出现"网络已减少但背包没收到"——取出与放入背包之间没有事务，退路是兜底把物品放进玩家物品栏/脚下，方向仍是"不丢"；(3) 客户端按自己安装的版本判断，范围外又被服务端强制启用时，玩家只能用 013 的两步法（规格已写明）。

## Project Structure

### Documentation (this feature)

```text
specs/060-network-terminal-take/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── request-and-server.md      # 请求包、服务端算法、校验、守恒与兜底、桥接口扩展
│   └── client-and-compat.md       # 客户端挂接、两个终端的识别与取/还、启用条件、范围表/mods.toml/build.gradle/README
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── compat/NetworkTakeRules.java              # 新（纯）：请求校验、amount = 最大堆叠、守恒分配（放进背包/退回/兜底）
├── compat/BridgeDispatch.java                # 改：tryTake、tryGiveBack；Bridge 加 handlesTake/take/giveBack（默认方法）
├── compat/NetworkStorageBridge.java          # 改：继承扩展后的 Bridge（无第三方类型）
├── compat/NetworkStorageBridges.java         # 改：tryTake、tryGiveBack
├── compat/ThirdPartyTable.java               # 改：加 ae2 一行
├── compat/ClientCompatVersions.java          # 新：客户端按自己装的版本判断某行是否在范围内（缓存）
├── compat/integratedterminals/IntegratedTerminalsBridge.java   # 改：handlesTake/take（extract）、giveBack（insert）
├── compat/integratedterminals/IntegratedTerminalsClient.java   # 新（第三方，仅客户端）：entryAt
├── compat/integratedterminals/IntegratedTerminalsCompat.java   # 改：客户端侧也注册识别器
├── compat/ae2/Ae2Bridge.java                 # 新（第三方）：handles=false（存入仍走通用路径）、handlesTake、take、giveBack
├── compat/ae2/Ae2Client.java                 # 新（第三方，仅客户端）：entryAt（MEStorageScreen/RepoSlot）
├── compat/ae2/Ae2Compat.java                 # 新（第三方）：register / registerClient
├── network/BackpackNetworkTakePayload.java   # 新：请求包 + handleServer（服务端算法）
├── network/BackpackNetwork.java              # 改：注册新包，versioned("22")
├── client/compat/NetworkTerminalClient.java  # 新（客户端，无第三方类型）：接口 entryAt(screen, slot) → Optional<NetworkEntry>
├── client/compat/NetworkTerminalClients.java # 新（客户端）：注册表 + 启用条件（版本、客户端档位）
├── client/panel/QuickTransferInput.java      # 改：beginFromScreen 先问注册表
├── client/ClientSetup.java                   # 改：模组已装时注册识别器
├── MCFloatingBackpack.java                   # 改：Ae2Compat.register()（isLoaded("ae2")）
src/main/templates/META-INF/neoforge.mods.toml   # 改：ae2 可选依赖
build.gradle                                     # 改：compileOnly ae2
README.md, README_CN.md                          # 改：兼容表
src/test/java/…                                  # 见 tasks
```

**Structure Decision**: 与 058/059 一致：规则（校验、数量、守恒）是纯类；通用代码（请求包、客户端注册表）不含第三方类型；两个终端各有 `Bridge`（服务端）与 `Client`（客户端）两个只在对应模组加载时才被触碰的类。

## Complexity Tracking

无违规项。
