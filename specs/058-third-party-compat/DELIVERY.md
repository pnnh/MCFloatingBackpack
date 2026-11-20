# 交付记录：058 第三方模组版本管理与集成终端主动兼容（面板 → 终端）

状态：**代码、自动测试、游戏内验证（AI，容器）都已完成，等用户确认真机手感**（T041）。未提交、未打 tag。

## 基线与完成后（`./gradlew test`）

| | 测试类数 | 用例数 | 失败 |
|---|---|---|---|
| 058 之前（T001） | 221 | 1665 | 0 |
| 代码与测试完成后（T037） | 232 | 1761 | 0 |

`./gradlew build -PcleanRuntime -PnoJei`（CI 同款）通过；发布的 jar 里没有 `org/cyclops` 的类，`neoforge.mods.toml` 里四条新依赖都是 `optional`、`versionRange="[0,)"`。

## 与 spec / plan 的偏离

- **`DependencyRules` 没有新增第三方组**：四个模组的坐标都在 `maven.modrinth` 下，规则已覆盖；FR-023 的说法据此改了，补了两条反例测试。
- **`-Pmcfb.<name>` 的转发原先只有客户端运行配置有**；058 的警告要在专用服务器上看，所以 `runs.server` 也加了同样的转发（`build.gradle`）。
- plan 里写"`CompatStatus` 与 `VersionVerdict` 在 `VersionChecker.java`"，实现时各自一个文件。
- 另外新增 `CompatLogText`（日志与聊天里的范围文字，纯逻辑，可测）。

## 已知局限（如实）

- 桥的 `insert` 若在集成动力学网络**已经接收**物品之后才抛异常，背包里的原件还在，物品会重复一份（research R7）；方向是"宁可重复、不可丢"。出错路径的"不丢"由 `BridgeDispatchTest` 覆盖，真实集成终端没有现成办法让它出错，游戏里没测。
- **客户端把 `integratedterminals` 设为 `GENERIC` 关不掉服务端的存入**（FR-030）；按模组的服务端档位计划单开 059，README 里已如实写明。
- 范围表只有一行（集成终端）；LambDynamicLights、Curios、Accessories 仍沿用 `mods.toml` 里的下限，058 没动。
- 集成终端存储页"服务端不处理 Shift+点击物品栏"一条：游戏里没有单独验证（没有在不开面板的情况下 Shift+点击玩家物品栏）；但在范围外（桥关闭、走通用路径）时 Ctrl+点击存入终端**没有任何效果**（情形 6a），这间接支持了"通用路径在集成终端存储页无效"的判断。
- **`B` 键在集成终端界面上打不开面板**（本次游戏里发现，未在计划内）：集成终端的"平衡合成网格"键默认也是 `B`（GUI 上下文，`javap` 读到默认键码 66），它的界面在本模组之前处理按键并吞掉，所以那里按 `B` 无反应；把本模组的键改成 F9 后面板正常显示、Ctrl+点击正常存入。这是两个模组默认键冲突，不是 058 新引入的（058 之前面板在这个界面上同样打不开）；README 已写明"改掉任意一个键"，没有改代码。
- **AE2 终端的通用路径会在日志里留下一条误导性的 ERROR**（本次翻日志时看到，012 起就有）："Quick transfer into open container changed the item total (menu appeng.menu.me.common.MEStorageMenu …)"：守恒检查数的是菜单格子里的物品，而 ME 网络不是格子，物品进了网络就"少了"。实际上物品是正确进入网络的（容器里之前验证过）；058 不动它，可以单独修（对"物品进了网络而不是格子"的菜单放宽这条检查）。

## 游戏内验证

环境：容器 `mcfb-c1`（arm64 Linux，软件渲染），单人创造世界（房主有作弊权限），`-PnoJei`，现有 `New World` 世界（AE2 验证时建的，试验台：石头平台 + 逻辑线缆 + 物品接口（Integrated Tunnels）+ 终端存储部件 + 箱子）；"使用/攻击"键改绑到 U/Y 以便 xdotool 操作，面板键临时改成 F9（原因见上），验证后 `options.txt` 与服务端配置都已还原。证据优先用 `latest.log`、`data get` 的聊天输出与截图。已验证的版本：集成终端 1.11.2、集成动力学 1.39.1、公共能力 2.11.6（即范围内），伪造版本只用 `-Pmcfb.fakeModVersion=integratedterminals=2.0.0`。

| # | 情形 | 结果 | 证据 |
|---|---|---|---|
| 0 | 范围内启动 | ✓ 没有任何第三方版本警告 | 启动日志里没有 "Third-party mod" 行 |
| 1 | 存入 | ✓ 面板里 32 个圆石 Ctrl+点击 → 终端网格出现"圆石 32"，容量显示 `37 / 2,673`（5 铁锭 + 32 圆石），面板格子清空；日志有 "floating panel is shown on a screen of mod 'integratedterminals'" | 截图 `f2`～`f4` |
| 2 | 部分存入 | ✓ 箱子只剩 4 个空位，背包 10 个圆石 → 箱子第 26 格圆石 64（+4），背包剩 6，守恒 | `data get block … Items[26]` = `{count: 64, …}`；`data get entity … items` 圆石 6、泥土 7 |
| 3 | 网络没有存储 | ✓ 拆掉箱子后 Ctrl+点击 → 背包仍是 6；终端提示"存储不可用，请用接口连接网络"，没有报错 | 截图 `i1`；日志里没有本模组的 ERROR |
| 4 | 合成页 | ✓ 切到合成页后 Ctrl+点击（箱子是空的、有空位）→ 背包仍是 6，箱子仍是空 | 截图 `j1`；`data get` |
| 5 | 末影箱页 | ✓ 给终端用末影之眼升级出第三个页（末影箱页）后 Ctrl+点击 → 10 个圆石进了**玩家的末影箱**（`EnderItems`），没进网络（箱子里仍是之前的 10）：桥不处理，由集成终端自己的快速移动处理 | `data get entity @s EnderItems` |
| 6a | 范围外 | ✓ `-Pmcfb.fakeModVersion=integratedterminals=2.0.0`：服务端日志一条 WARN（"…newer than every version this mod was verified with ([1.11.0, 1.12.0)); active compatibility is switched off…"），房主（op）进游戏聊天一条同义提示；Ctrl+点击存不进（背包仍 10，箱子空） | 启动日志、`[CHAT]` 行、`data get` |
| 6b | 强制启用 | ✓ 服务端配置 `[compat] allowUntestedVersions = true` 重开世界：WARN 与聊天都改成"FORCED ON … at your own risk（back up the world …）"，Ctrl+点击存入成功（箱子 圆石 10，背包第一格空） | 启动日志、`[CHAT]` 行、`data get` |
| 7 | 配置文件 | ✓ 新生成的 `[compat]` 节默认 `false`，注释含 WARNING / crash of the server / backup of the world / set it back to false / reopened | `config/mcfloatingbackpack-server.toml` |
| 8 | 普通箱子上通用路径不变 | ✓ 原版箱子上 Ctrl+点击 → 6 个圆石进箱子（桥不认领，走 012 的通用路径） | `data get block 98 120 99 Items` |

**没有验证到的**：
- 非 op 玩家看不到提示：单人游戏里房主的作弊权限无法取消，没有开第二个客户端；只有 `ThirdPartyCompatWiringTest` 读源码确认只对 `hasPermissions(2)` 发。
- 专用服务器的启动日志：启动逻辑在集成服务器里跑过（同一段 `ThirdPartyCompatService.start`），没有单独起 `runServer`。
- 出错兜底（`FAILED`）：真实的集成终端没有现成办法让它出错；`BridgeDispatchTest` 覆盖了抛 `RuntimeException`、`NoSuchMethodError`、`NoClassDefFoundError`、不合法的返回值、出错后不再调用且只报告一次。
- 精妙存储、AE2 的通用路径：沿用 spec 阶段在容器里的验证（本次没有重做）；AE2 终端 → 面板（反向）仍未验证。
- 多段范围、`IN_GAP`：只有纯逻辑测试，真实模组没有两段范围的行。
- 手感（Ctrl+点击、拖动存入的节奏与提示）：容器是软件渲染，只能验证"存没存进去"，手感待用户（T041）。
