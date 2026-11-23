# Quickstart: 验证 060

## 一、自动测试

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.compat.*' --tests 'pnnh.mcfloatingbackpack.network.*' --tests 'pnnh.mcfloatingbackpack.resources.*' --tests 'pnnh.mcfloatingbackpack.client.*'
./gradlew build -PcleanRuntime -PnoJei
```

期望：`BridgeDispatch` 的取/还、`NetworkTakeRules` 的校验与守恒、请求包编解码、接线（第三方类型只在 `compat/<modid>/`、协议 22、`beginFromScreen` 先问注册表且不调用 `start`、启用条件）、README 与范围表/`mods.toml`/`build.gradle` 对齐（含 AE2）全部通过。

## 二、游戏内（容器 `mcfb-c1`，`-PnoJei`；先 `pgrep`，改配置前备份，验证后还原；面板键临时改 F9——集成终端界面里 B 被吞）

**A. 集成终端**（沿用 058 的试验台：`/function s58:idnet` 的逻辑线缆 + 物品接口 + 终端存储部件 + 箱子；箱子里放物品用 `item replace block 101 120 99 container.N with …`）：

| # | 情形 | 操作 | 期望 |
|---|---|---|---|
| 1 | 取一组 | 箱子里 128 个圆石；面板可见；Ctrl+点击"圆石 128" | 网络剩 64、背包 +64，终端列表更新 |
| 2 | 不足一组 | 网络里 10 个 | 取 10，条目消失 |
| 3 | 最大堆叠小于 64 | 末影珍珠 40 个 | 取 16 |
| 4 | 背包放不下一部分 | 背包主区域只剩 10 格空位给该物品 | 放得下的进背包，其余退回网络；总数守恒 |
| 5 | 背包完全放不下 | 背包主区域塞满 | 全部退回网络，网络与背包都不变 |
| 6 | 面板不可见 | Ctrl+点击条目 | 与 060 之前一致（IT 自己的行为） |
| 7 | 合成页 | 切到合成页再 Ctrl+点击 | 不拦截、不取 |
| 8 | 网络离线 | 拆掉物品接口 | 列表空，没有条目可点 |
| 9 | 服务端 OFF | 配置 `modLevels=["integratedterminals=OFF"]`（热改） | 不取，聊天一条提示（每次打开一条） |
| 10 | 服务端 GENERIC | `=GENERIC` | 不取、不提示 |
| 11 | 范围外 | `-Pmcfb.fakeModVersion=integratedterminals=2.0.0` | 客户端不拦截（Ctrl+点击照旧）；服务端也不取 |
| 12 | 客户端 OFF | 客户端 `modLevels=["integratedterminals=OFF"]` | 面板不在该界面显示（020），手势不适用 |

**B. AE2**（058 搭过的 ME 终端：`ae2:chest` + `ae2:creative_energy_cell` + 手放 `ae2:fluix_glass_cable` + 终端部件；`ae2` 箱子里放圆石 128 个——用 `item replace block … container.N`）：

| # | 情形 | 期望 |
|---|---|---|
| 13 | Ctrl+点击"圆石 128" | 网络剩 64、背包 +64 |
| 14 | 背包放不下一部分 / 完全放不下 | 退回网络，守恒 |
| 15 | 断电（拆掉能量元件） | 取不到，什么也不变，不报错 |
| 16 | 服务端 OFF / GENERIC / 范围外伪造版本 | 同 9～11 |
| 17 | 只显示可合成的条目（数量 0） | 不取、不触发合成 |
| 18 | 面板 → ME 终端（存入）仍走通用路径 | 与 058 一致 |

证据优先日志与 `data get`；做不了的写"未验证"。
