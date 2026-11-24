# Quickstart：065 验证指南

前置：`./gradlew build -PcleanRuntime -PnoJei` 通过（单元 + 接线测试）。游戏内验证用 AI 容器（`scripts/ai-gui`）或 `./gradlew runClient`；**界面观感类（标签栏、颜色、图标、高度）由用户真机确认**。

## A. 自动测试（不启动游戏）

1. `CombinedMembersTest`：顺序（穿戴 → 副手 → 物品栏下标升序）、同身份多次、档位过滤、保护排除、超过 16 截断与三类计数、签名只在变化时变。
2. `CombinedWornTest`：多个联合背包取档位最高；同档位取 `order` 靠前。
3. `PanelTargetRulesTest`：无会话 → 退回；目标有效 → 目标；身份对不上/档位过高/保护/联合背包没了 → 退回；`MAIN_HAND` 归一化为 `INVENTORY(selected)`。
4. `CombinedSessionsTest`：`recover`（唯一匹配 / 多个相同 / 找不到 → 第一个 / 空表 → 结束）、`forget`。
5. `CombinedBarLayoutTest`：标签矩形、滚动与裁剪、命中、`maxScroll`、`FloatingPanelState.clampToScreen(topReserve)` 不变量。
6. `BackpackEmptinessTest`：主区域、工具区、垃圾桶、光标、隐藏行、液体存量各自非空即"非空"；标签配置与黑名单不算内容。
7. `CombinedResourcesTest`：五档物品的 lang、模型、贴图、配方 JSON（合成类型、锻造 `smithing_transform`）、两个槽位标签含五个联合背包、不含 `back`/`charm`；物品 id 与 `BackpackTier` 对应。
8. `CombinedWiringTest`：见 contracts/panel-targets.md；协议号 23；`FloatingBackpackKeys` 有 U；`HostKeyRouter` 认 U；`DiagConfig` 含 `[combined]`；`FloatingBackpackItem` / `CombinedBackpackItem` 的 `canFitInsideContainerItems` 为 false；064 的 `NestingRules` 的 id 集合含联合背包。
9. 语言键：新增键中英文各一份（沿用现有的 lang 对称测试）。

## B. 游戏内（AI 容器可做的）

| # | 步骤 | 预期 |
|---|---|---|
| 1 | `/give` 铁档联合背包，Shift+右键穿上；物品栏放皮革、铁、金档背包各一只 | 按 U 打开；标签栏 2 个标签（皮革、铁）；"+1"徽标提示 1 只档位过高 |
| 2 | 点铁档标签 → Ctrl+点击物品栏里一格物品 | 物品进铁档背包；皮革背包不变 |
| 3 | 按 B 打开（面板先关）| B 打开的仍是 手→穿戴→物品栏 顺序的那只，不受联合影响 |
| 4 | 联合界面开着，丢出一只背包 | 1 秒内标签减一；若正看的就是它，回到第一个标签，无物品落到别的背包 |
| 5 | 拿起一只新背包 | 1 秒内标签加一 |
| 6 | 光标上拿着物品点另一个标签 | 不切换；动作栏"先放下手里的物品" |
| 7 | 放 17 只皮革背包 | 只连前 16，"+1"徽标提示超过 16 只 |
| 8 | 联合背包放物品栏里按 U | 动作栏"需要穿戴联合背包" |
| 9 | 服务端配置 `[combined].enabled=false` 重开世界，U | 提示已被关闭 |
| 10 | 选中非 B 激活的标签，该背包右区有移动唱片机/工作台/熔炉 | 控件灰掉，点击只出提示；换成 B 激活的那只则正常 |
| 11 | 选中的背包被挪到物品栏另一格 | 面板不闪、不错位，仍是同一只 |
| 12 | 合成：中间放非空同档位背包 | 不匹配；放空背包 → 得到联合背包 |
| 13 | 锻造台：模板 + 低一档联合背包 + 材料块 | 得到高一档联合背包 |
| 14 | 把联合背包点向浮动背包面板 | 被拒（064 规则） |
| 15 | 装 Curios/Accessories（开专用槽）：联合背包放进专用槽 | 能放；放不进 `back`/`charm` |

## C. 要你真机看的（不替你勾）

标签栏与标题栏的观感、颜色表、图标、面板高度与小分辨率下的钳制；占位贴图是否满意。

## D. 不会验证的（如实写进 DELIVERY）

多人服务器下的真实延迟表现；062 式"槽位类型消失"时槽内联合背包不被找回（已知缺口）；EMI 排除区（未适配 EMI，沿用现状）。
