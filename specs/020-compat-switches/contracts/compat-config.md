# Contract: 兼容配置、客户端接入点与服务端门禁

**Feature**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md) | **Research**: [research.md](../research.md)

没有新的网络包（协议保持 `"8"`）。

## 1. 配置文件

```toml
[compat]
    # 每条 "modid=LEVEL"。没有列出的模组按 FULL。
    # FULL    : 专用适配：开启我们为该模组写的专有代码（主动兼容，可视为功能增强）。默认
    # GENERIC : 没有专用适配：关闭我们为该模组写的任何专有代码（没写过的模组与 FULL 一样）；不决定面板能否显示、能否交换物品，那取决于该模组自己的实现
    # OFF     : 面板不显示（面板与该模组的界面二选一；对只叠加界面的模组如 jei 等同 GENERIC）
    # minecraft（原版）不可配置。示例: modLevels = ["sophisticatedbackpacks=OFF", "jei=GENERIC"]
    modLevels = []
```

坏条目的处理（逐条，不影响其它条目）：

| 条目 | 结果 | 日志 |
|---|---|---|
| `x=off` / `x=Off` / `x=OFF` | OFF | — |
| `x=HALF` | 按 FULL | WARN |
| `Bad Id=OFF`、`=OFF`、`abc` | 忽略 | WARN |
| `minecraft=OFF` | 忽略 | WARN |
| 同一 id 多次 | 取最后一条 | WARN |
| 配置文件整体损坏 | NeoForge 回退默认 | NeoForge 自己的日志 |

## 1b. 聊天提示（2026-11-05 新增，FR-006、FR-019）

提示只写进玩家自己的聊天（不联网），前缀 `[浮动背包]`，进入世界后才显示：

| 情况 | 提示（语言键） |
|---|---|
| 条目格式不对被忽略 | `message.mcfloatingbackpack.config.malformed`（带该条） |
| 档位无法识别按 FULL | `….config.unknown_level` |
| 列了 `minecraft` | `….config.vanilla` |
| 同一模组重复 | `….config.duplicate` |
| 文件有语法/格式错误被 NeoForge 备份并恢复默认 | `….config.file`（带备份文件名） |
| 超过 5 条 | `….config.more`（带剩余条数） |
| OFF 的界面上按面板热键 | `message.mcfloatingbackpack.panel_off`（带模组 id） |

## 2. 客户端接入点

| 位置 | 改动 | 对应需求 |
|---|---|---|
| `ContainerScreenCompat.isOpenContainerScreen(screen)` | 容器界面且所属模组被 OFF → 返回 false（光标来源、`screenOpen`、合并光标、`hostsPanel`、019 的界面分类都用它） | FR-005、FR-009 |
| `QuickTransferInput.supports(screen)` | 同一判断；OFF 时不触发 Ctrl 转移 | FR-005 |
| `QuickTransferInput/PanelDragInput/PanelSelectionInput.invalid` | 增加 `!FloatingPanelEvents.hostsPanel(minecraft.screen)` | FR-009 |
| `FloatingPanelEvents.onKey` | OFF 的容器界面上按热键 → 玩家聊天里提示 | FR-006 |
| `FloatingPanelEvents.onClientTick` | 玩家进入世界后排空 `ConfigNotices` 写进聊天 | FR-019 |
| `ConfigFileWatch`（`ModConfigEvent.Loading/Reloading`） | 发现刚生成的 `.bak` → 加一条"文件有错误" | FR-019 |
| `FloatingPanelEvents.onScreenOpening` | 容器界面可承载且非原版 → `AttachNotice.onAttach(owner)` | FR-018 |
| `FloatingBackpackJeiPlugin.getGuiExtraAreas` | `!specificAdaptation("jei")` → 返回空列表 | FR-008 |
| `ClientSetup.register` | 注册 `IConfigScreenFactory`（`ConfigurationScreen`） | FR-010 |

## 3. 服务端门禁入口

见 research Decision 7 的表：5 个入口都在业务逻辑之前先 `ServerCompatGate.allows(action, MenuOwner.ownerOf(player.containerMenu))`，为假则直接 `return`（不改数据、不回包）。现阶段恒为真，所以 011/012/013 的行为一字不变。

## 4. 行为保证（验收依据）

1. 未配置时，所有行为与改动前**逐项一致**（011～019、018）。
2. `sophisticatedbackpacks=OFF` 后：精妙背包界面上面板隐藏、不拦截输入、Ctrl+点击槽位不转移、不发合并光标请求；关闭界面后面板回来；原版容器与其它模组的界面不受影响。`=GENERIC` 对精妙背包没有可关的专有代码，表现与 FULL 相同。
3. 面板热键在 OFF 的界面上不打开面板并给提示；在其它界面上照常。
4. `jei=GENERIC` 后 JEI 不再避让面板；面板功能不受影响；来回切换不需重启。
5. 配置里任何一种坏写法都不崩溃，坏条目只影响自己。
6. 面板首次挂在非原版模组界面上，日志出现 1 行带模组 id 的提示，同一会话不重复。
7. 依赖规则的检查在当前代码库通过，三类违规的反例被判为失败。
