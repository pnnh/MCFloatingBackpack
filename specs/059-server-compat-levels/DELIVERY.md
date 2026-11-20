# 交付记录：059 按模组的服务端档位

状态：**代码、自动测试、单人（集成服务器）游戏内验证完成；专用服务器上的验证没做**（T033，见下）；等用户确认（T036）。未提交、未打 tag。

## 基线与完成后（`./gradlew test`）

| | 测试类数 | 用例数 | 失败 |
|---|---|---|---|
| 059 之前（058 提交 `1f63bfb`） | 232 | 1761 | 0 |
| 完成后 | 236 | 1795 | 0 |

`./gradlew build -PcleanRuntime -PnoJei`（CI 同款）通过。协议号仍 21（`BackpackNetwork` 没动，接线测试断言），`DataRevision` 仍 6（没碰存储）。

## 实现了什么

- 服务端配置 `[compat].modLevels`（与客户端同形、同一份解析规则；错误项 WARN 写服务器日志）；有效档位 = 服务端档位与 `CompatConfig.policy()` 中更严者（专用服务器读不到客户端配置时是全 FULL，中性，所以没有判断是不是专用服务器）。
- `ServerCompatGate` 按档位回答（OFF 否）；7 处调用（6 个入口，垃圾桶两处）全换成 `ServerCompatDenials.allows(player, menu, action)`：被拒时发一条聊天提示（每个玩家每次打开的界面一条，关闭界面/登出清除）+ DEBUG 日志。
- 058 的集成终端桥多了第四个条件（档位必须是 FULL）；Curios、Accessories 的背着来源在服务端侧问服务端有效档位，客户端侧仍问客户端配置（`WornSource.active(player)`）。
- README 两份改写；`ThirdPartyDriftTest` 的 README 断言改成新说明。

## 与 spec / plan 的偏离

- 无功能偏离。`WornBackpacks.activeSource` 的公共方法带上了实体参数（原来没有外部调用者）。
- DEBUG 日志的行在容器里没有看到（开发运行的日志级别设置下 `LOGGER.debug` 没有输出到 `latest.log`），所以 DEBUG 日志**没有游戏内证据**，只有接线测试（先问门禁、再写 DEBUG、再限流提示）。

## 游戏内验证（容器 `mcfb-c1`，单人创造世界；服务端配置在 `run/config/mcfloatingbackpack-server.toml`，客户端 `modLevels` 保持空；改配置全部是**游戏运行中直接改文件、不重开世界**）

| # | 配置 | 操作 | 结果 | 证据 |
|---|---|---|---|---|
| 1 | 空 | 精妙存储箱子上 Ctrl+点击面板里的 10 个圆石 | ✓ 照常存入（箱子 圆石 10） | `data get block … storageWrapper.contents` |
| 2 | `["sophisticatedstorage=OFF"]`（热改） | 同上连点 3 次 | ✓ 背包仍 10，聊天**恰好一条** "This server does not allow the floating backpack to exchange items with sophisticatedstorage screens (a server setting)." | `data get entity`、`[CHAT]` 计数 |
| 3 | 同上 | 关闭箱子再打开再点 | ✓ 再来一条（累计 2） | `[CHAT]` 计数 |
| 4 | 同上 | 原版箱子上 Ctrl+点击 | ✓ 照常存入，没有新提示 | `data get block 98 120 99 Items` |
| 5 | 同上 | 精妙存储箱子里 Ctrl+点击容器格子想取出 | ✓ 箱子内容不变（仍 10 个圆石），打开界面再记一条提示 | `data get block` |
| 6 | 改回 `[]`（热改，不重开世界） | 精妙存储箱子 Ctrl+点击 | ✓ 恢复存入（箱子圆石 20），没有新提示 —— **FR-004 的唯一证据：NeoForge 对服务端配置的文件监视确实让改动立即生效** | `data get block` |
| 7 | `["integratedterminals=GENERIC"]`（热改） | 集成终端存储页 Ctrl+点击面板里的圆石 | ✓ 不存入（背包仍 10，网络箱子空） | `data get`、截图 `u2` |
| 8 | 改回 `[]` | 同上 | ✓ 存入（网络箱子 圆石 10） | `data get block 101 120 99 Items` |
| 9 | `["minecraft=OFF", "bad", "x=NOPE", "sophisticatedstorage=GENERIC", "sophisticatedstorage=OFF"]`（热改） | 精妙存储箱子 Ctrl+点击 | ✓ 服务器日志四条 WARN：vanilla cannot be configured / expected 'modid=LEVEL' … / unknown level, treated as FULL / listed more than once, the last entry wins；最后一项 OFF 生效（被拒绝，再来一条提示，背包仍 10） | `latest.log` |
| 10 | 空 → `["curios=OFF"]`（热改） | 背包放在 Curios 后背槽（`/curios replace back 0 @s with …`），按面板键 | ✓ 空配置时面板打开；改成 `curios=OFF` 后面板不再打开，提示 "You need a floating backpack" —— 服务端不再把 Curios 槽当作背着的（这里是单人世界、客户端配置为空，所以起作用的是服务端配置） | 截图 `x1`、`x2` |

环境：面板键临时改成 F9（集成终端界面里 B 被"平衡合成网格"吞掉，见 058），"使用"键改绑到 U；验证后 `options.txt` 与服务端配置都已还原。

**没有验证到的**：
- **专用服务器**（T033，quickstart 的 B 部分，情形 9、10）：没有起 `mcfb-srv` + 客户端；服务端的判断代码与单人里是同一份（情形 10 用的就是只有服务端配置起作用的路径），区别只是专用服务器上客户端配置不加载（`CompatConfig.policy()` 返回全 FULL，是中性元素，有纯逻辑测试）。需要的话再单独做一轮。
- **单人里客户端 `curios=OFF` 与服务端取更严者**：由 `EffectiveLevelTest`（9 格）与接线测试覆盖，游戏里没有单独做"只改客户端配置"那一轮。
- **DEBUG 日志行**：见上。
- **手感**（提示的文字与出现时机）：待用户（T036）。

## 已知局限（如实）

- 客户端不知道服务端档位：在服务端 OFF 的界面上面板仍然显示、手势仍发出，服务端拒绝并发一条聊天提示（规格定的代价）。
- 服务端关了 Curios/Accessories 的来源后，客户端仍按自己的配置绘制背部层，可能画出服务端不认的背包。
- 限流状态只在内存里：下线或重启后清空。

## 宪法复查

II（拒绝与专用适配的开关在服务端）、III（只做服务端档位）、V（游戏内验证如上，没验证的如实写明）、VII（原版恒 FULL 不可配置；无新依赖、无反射）：通过。
