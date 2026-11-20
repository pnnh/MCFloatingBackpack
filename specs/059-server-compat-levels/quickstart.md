# Quickstart: 验证 059

## 一、自动测试

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.compat.*' --tests 'pnnh.mcfloatingbackpack.inventory.worn.*' --tests 'pnnh.mcfloatingbackpack.resources.*'
./gradlew build -PcleanRuntime -PnoJei
```

期望：有效档位 9 格、门禁允许表、`DenialThrottle`、`BridgeDispatch` 新条件、`BackpackSearch.active(player)`、配置注释/语言键/README、接线（7 处调用都换成 `ServerCompatDenials`、门禁来源接到服务端有效档位、两个来源按侧选档位、`Close`/登出清限流）全部通过。

## 二、游戏内（容器；先 `pgrep` 确认宿主没有游戏，改配置前备份 `/work/run/config/mcfloatingbackpack-server.toml`，验证后还原）

**A. 单人（集成服务器）**，客户端 `compat.modLevels` 保持空，只改服务端配置（开发运行里服务端配置在 `run/config/mcfloatingbackpack-server.toml`）：

| # | 配置 | 操作 | 期望 |
|---|---|---|---|
| 1 | 空 | 精妙存储箱子上 Ctrl+点击面板物品 | 照常移动（SC-003） |
| 2 | `["sophisticatedstorage=OFF"]` | 同上 | 物品不动，聊天**恰好一条**提示；再点几次无新提示；关闭箱子再打开再点，再来一条 |
| 3 | 同上 | 原版箱子 | 照常移动（SC-001） |
| 4 | 同上 | 精妙存储箱子里 Ctrl+点击箱子格子想取出 | 不动、提示 |
| 5 | 在游戏运行中把配置改成空并保存，**不重开世界** | 精妙存储箱子 Ctrl+点击 | 照常移动（FR-004、R5）；若不成立回 research R5 改监听 |
| 6 | `["integratedterminals=GENERIC"]` | 集成终端存储页 Ctrl+点击面板物品（面板键用 F9，见 058 DELIVERY） | 不存入网络，物品留在面板；改回空后存得进（SC-006） |
| 7 | `["minecraft=OFF", "bad", "x=NOPE"]` | 启动 | 日志 WARN 三条（VANILLA、MALFORMED、UNKNOWN_LEVEL）；原版箱子仍工作 |
| 8 | 客户端 `modLevels=["curios=OFF"]`、服务端空 | 背包放 Curios 后背槽，按 `B` | 不再认它（与 059 之前一致，SC-003） |

**B. 专用服务器**（`multi-setup.sh` 建 `mcfb-srv` + `mcfb-c1`，见 `scripts/ai-gui/README.md`；服务端 `online-mode=false`）：

| # | 配置 | 操作 | 期望 |
|---|---|---|---|
| 9 | 服务端 `["curios=OFF"]`，客户端空 | 背包放 Curios 后背槽，按 `B` | 服务端不认 Curios 槽里的背包（SC-002）；改回空（不重开）又认 |
| 10 | 服务端 `["sophisticatedstorage=OFF"]` | 精妙存储箱子 Ctrl+点击 | 不动、提示（专用服务器上同样有效） |

做不了的写"未验证"；证据优先日志（`[CHAT]` 行、DEBUG 日志）与 `data get`。
