# Quickstart: 验证 058

## 前置

- 开发依赖已在 `build.gradle` 的 `localRuntime`（集成终端 1.11.2 等）；第一次编译/运行会下载 `integrated-terminals`、`integrated-dynamics`、`cyclops-core` 的 jar（研究 R4）。
- 游戏内验证用容器 `mcfb-c1`（Docker arm64 + Xvfb + xdotool，见 [docs/plan/ai-gui-verification.md](../../docs/plan/ai-gui-verification.md)），带 `-PnoJei`；用户的游戏开着时先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`，不替用户关游戏；先备份世界。

## 一、自动测试（不启动游戏）

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.compat.*' --tests 'pnnh.mcfloatingbackpack.rules.*'
./gradlew build -PcleanRuntime -PnoJei      # CI 同款：确认不带开发依赖也能编译、测试、打包
```

期望：版本解析（表里的真实串）、五种结论、多段与要求、状态机与配置、桥分派与出错、`mods.toml` 宽松、README 对齐、`build.gradle` 版本落在段内、配置注释、语言键、接线（桥在 `backpackOut` 里被问到、第三方类型只在 `compat/integratedterminals/`）全部通过。

## 二、游戏内（容器，`-PnoJei`，spec SC-007 六种情形）

先建网络：创造模式，放集成动力学的**逻辑线缆**，旁边放一个**箱子**，线缆上装**物品接口**（连箱子），再装**终端存储部件**（Terminal Storage）；右键终端部件打开界面，切到物品存储页；按 `B` 让面板叠在上面。

| # | 情形 | 操作 | 期望 | 记入 DELIVERY |
|---|---|---|---|---|
| 1 | 存入 | 面板里放一组圆石（32），Ctrl+点击 | 面板里的圆石消失，箱子里有，终端网格里出现"圆石 32"；日志有"floating panel is shown on a screen of mod 'integratedterminals'" | ✓/✗ |
| 2 | 部分存入 | 箱子预先填到只剩 10 个空位的容量（或用小容量），Ctrl+点击 64 个 | 面板里剩下放不进的那部分，数量守恒（扣掉 + 剩余 = 64） | ✓/✗ |
| 3 | 离线 | 拆掉物品接口（或让网络没有存储）后 Ctrl+点击 | 物品留在面板里，没报错，玩家物品栏不动 | ✓/✗ |
| 4 | 合成页 | 终端切到合成页，Ctrl+点击 | 不存入网络（沿用通用路径，无效果、无害）；面板里的物品不动 | ✓/✗ |
| 5 | 末影箱页 | 终端切到末影箱页，Ctrl+点击 | 不由桥处理；沿用通用路径（末影箱页实现了自己的快速移动，可能存入末影箱——记录实际行为） | ✓/✗ |
| 6 | 范围外 | 用 `-Pmcfb.fakeModVersion=integratedterminals=2.0.0` 重启 | 启动日志出现一条 WARN（过高、未启用）；op 登录聊天出现一条；非 op 没有；情形 1 的 Ctrl+点击存不进（回到通用路径）；再把 `serverconfig/mcfloatingbackpack-server.toml` 的 `allowUntestedVersions` 改成 `true` 重开世界：WARN 与聊天改成"强制启用"，Ctrl+点击存得进 | ✓/✗ |

另外验证：

- **非集成终端的界面行为不变**：箱子、精妙存储、AE2 终端上 Ctrl+点击与 058 之前一致（SC-005）。
- **集成终端存储页服务端不处理 Shift+点击物品栏**（规格 Assumptions）：在终端界面里 Shift+点击玩家物品栏里的物品（不开面板），预期不移动（或移动由客户端包驱动）；记录。这一条只是坐实前提，不是新功能。
- **出错兜底**：在代码里没有现成办法让真实的集成终端出错，纯逻辑测试覆盖；游戏内不做。

验证 1～6 时用日志优先（`logs/latest.log`），截图为辅；每一步的结果如实写进 DELIVERY，没验证到的写"没验证"。

## 三、文档核对

- `README.md`、`README_CN.md` 的兼容表与 [contracts/config-and-texts.md](./contracts/config-and-texts.md) §7 一致；表里集成终端一行含 `[1.11.0, 1.12.0)`。
- 配置文件生成后 `[compat]` 一节注释含"备份"与风险说明。
