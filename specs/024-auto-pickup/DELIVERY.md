# Delivery: 024-auto-pickup

**日期**: 2026-11-08

**状态**: 代码与自动化已完成（T001～T033、T044～T046、T048、T050、T053；T002、T035～T043 因取消主动避让而取消/回退）。**用户已口头确认 US1～US3 手动验证通过、功能正常（2026-11-08，具体逐项结果未记录）**。**T047（左侧区限放）用户已手动验证，没有问题（2026-11-08）**。图集度量 T051 已完成（2048×1024，通过）。还剩：T034（与精妙背包/旅行者背包同时拾取，`runClientCompat`）、T049（持久化与 011～023 回归）、T052（成本粗测，**未测量**）、T054（本文最终定稿）。

## 自动化结果

| 项 | 结果 |
|---|---|
| 改动前基线（T001） | `./gradlew compileJava test` 通过；62 个测试类、473 个用例、0 失败 |
| T002 | 原计划加三条 `compileOnly` 第三方 jar，**2026-11-08 随"取消主动避让"回退**，`build.gradle` 与 024 之前一致 |
| Phase 2（T013） | `./gradlew compileJava test` 通过；新增 `PickupBlacklistTest`、`PickupBlacklistRequestTest`、`AutoPickupDecisionTest`、`PickupInsertTest`；共 66 个测试类、500 个用例、0 失败 |

## 偏离说明

- **取消主动避让（2026-11-08，用户裁决）**：最初的 US4 是"检测玩家是否背着开了自动拾取的精妙背包/旅行者背包并避让（档位按 020，由客户端同步给服务端）"。读字节码后发现这必须依赖它们的内部类（`PlayerInventoryProvider`、`BackpackLinkedStorageResolver`、`AttachmentUtils`、`AutoPickupUpgrade` 等，不是公开 API；精妙背包的升级数据在服务端存储里，物品上只有 UUID），版本一变就可能失效，所以取消。已回退 `build.gradle`，`AutoPickupDecision` 简化为 `decide(toolActive, blacklisted)`，删掉原 T033～T043；US4 改为"被动礼让"（`LOW` 优先级 + 事件已是 `FALSE` 就跳过，不引用任何第三方）。spec/plan/research/data-model/contracts/quickstart/tasks 已同步。
- **多个背包同时拾取时的实际效果**（读字节码得出，**未验证**）：同一个实体的数量被依次扣减，不复制；精妙背包拿走一部分后设为"不可拾取"，本模组此次跳过，剩余下一次接触时被拿；旅行者背包不检查别人是否已设置，可能与精妙背包各拿一部分；对方过滤器不放行的物品进本模组背包；对方放行的物品即使命中本模组黑名单也被对方拿走；旅行者拾取升级的 `tick` 与精妙磁铁按范围吸取、不经过拾取事件，本模组拿不到这些物品。

- **`PickupBlacklistCodecs`**：plan/data-model 把 `CODEC`/`STREAM_CODEC` 写在 `PickupBlacklist` 里；但测试类路径没有 Minecraft/Netty，带 `Codec` 静态字段的记录无法在测试里加载，所以编解码器拆到同包的 `PickupBlacklistCodecs`（与 018 的 `TagBook`/`ItemTags` 分法一致）。记录本身的构造函数照常做清洗。
- 编解码层（往返、超限抛异常）无法单测，登记为未单测，改在客户端会话里验证。
| Phase 3（T018） | `./gradlew compileJava test` 通过；新增 `AutoPickupResourcesTest`（配方、模型、16×16 贴图、两份语言文件键） |
| Phase 4/5（T026、T031） | `./gradlew test --rerun-tasks` 通过：**69 个测试类、515 个用例、0 失败**（基线 62/473；新增 `PickupBlacklistTest`、`PickupBlacklistRequestTest`、`AutoPickupDecisionTest`、`PickupInsertTest`、`AutoPickupResourcesTest`、`PickupPanelModelTest`、`PickupPanelStateTest`） |

## 已实现（代码）

- **服务端拾取**（`inventory/pickup/AutoPickupEvents`）：`ItemEntityPickupEvent.Pre`，`EventPriority.LOW`；激活背包 = `BackpackRef.resolve`（每次现算）；先读物品上的 `BackpackContents.hasAutoPickup()` 预检；`VANILLA`/`BLOCK`/`TAKE`；`TAKE` 先保存背包再缩小实体栈，实体空了 `discard` + `FALSE`，否则保持默认让原版回落进物品栏。对其它背包模组只做被动礼让（`LOW` 优先级、事件已是 `FALSE` 就跳过）。
- **黑名单**：`BackpackContents` 第 6 个字段 `pickup_blacklist`；`BackpackPickupBlacklistOpPayload`（客户端→服务端，服务端核对左侧区有自动拾取、标签合法、不超 64 个）与 `BackpackPickupBlacklistPayload`（服务端→客户端）；协议版本 `"11"` → `"12"`；`sendSession` 后补发黑名单。
- **页面**：`client/panel/pickup/`（状态、模型、上下文、渲染、输入），复用 018 的 `TagPanelLayout`/`TagTextInput`/`TagMatcher`/`TagRules`/`PresetTags`；与标签页互斥（`TagPanelState.toggle()` 仅加一行）；接入 `FloatingPanelInput`、`FloatingPanelRenderer`、`FloatingPanelEvents`（滚轮、每 tick 收起、登录/登出/关闭重置、键盘三事件）与 `DeleteKeyInput`。
- **物品**：`AutoPickupItem`、配方 `IEI/RHR/IDI`、`item/generated` 模型、中英文语言键。
- **图标（T016）**：**手绘**——`design/demo/tools/make_auto_pickup_icon.py` 逐像素画出"铁漏斗 + 末影珍珠"（16×16，RGBA）；**没有用 ludo.ai/pixellab**（tasks 允许的退路；没必要为一张 16×16 图消耗额度）。源图不存在，脚本即来源。已用 `Read` 目视检查放大图，形状可辨认。**游戏里的外观未验证。**

## 还没有做 / 未验证

- **T019、T027、T032 需要你在客户端验证**（quickstart A～E）；本轮没有启动客户端，也没有启动服务端（没有 `run/eula.txt`，我不替用户接受 EULA），**启动、配方解析、网络注册在真实游戏里都还没有跑过**。
- 编解码层（`PickupBlacklistCodecs`、`BackpackContents` 的第 6 个字段）无法单测，只靠编译与客户端会话验证。
- US4 的审阅与验证（T033、T034）、US5（左侧区限放 1 个）、US6、Polish（T044～T054）未开始。

## 阶段结果（续）

| 项 | 结果 |
|---|---|
| US5（T046） | `./gradlew test --rerun-tasks` 通过：**69 个测试类、515 个用例、0 失败**（`ToolAreaRulesTest` 新增左侧区用例；`AutoPickupDecisionTest` 随避让取消简化为 3 个用例） |
| 静态检查（T053） | `src/main` 里第三方引用只在 `compat/jei/` 与 `ContainerScreenCompat` 原有位置；`setScreen(` 仍为 4 处（与 HEAD 相同）；`build.gradle`、`neoforge.mods.toml`、`compat/` 无改动；协议版本只在 `BackpackNetwork` 一处为 `"12"`；`ItemEntityPickupEvent` 只出现在 `AutoPickupEvents` 与注册处 `MCFloatingBackpack` |
| 图集度量（T051） | 2026-11-08 用户退出游戏后，先 `pgrep` 确认没有客户端在运行，再跑 `design/demo/tools/check_atlas_budget.sh`：共用方块图集 **2048×1024**（mip 级别 4），估算显存约 11.2MB（由尺寸推算，非 GPU 实测），预算 2048 → **通过**；脚本结束后确认没有残留的客户端进程。这次启动只到主菜单，没有加载世界，所以配方等数据包内容没有在这次启动里被读到；启动日志里没有关于本模组的错误 |
| 成本粗测（T052） | **未测量**（需要在游戏外或专门的会话里做） |

## 用户已确认 / 仍未验证

- **已确认**（2026-11-08，用户口头）：T019（拾取去向、回落、激活背包）、T027（页面展开/收起/互斥）、T032（黑名单过滤）手动验证通过、功能正常。**具体验证了 quickstart 的哪几项没有逐项记录**，下列"未验证"以此为准。
- **已确认**（2026-11-08，用户口头）：T047 左侧区限放 1 个，没有问题。
- **未验证**：T034（与精妙背包/旅行者背包同时拾取，research Decision 6 的表格是读字节码得出的）；T049（黑名单持久化、升级、换背包、旧存档、011～023 回归）；多人/局域网；Windows/Linux；拾取声是否响两次、统计/成就与原版的差异；高频拾取的同步成本。

## 已知限制

- 不触发 `ItemEntityPickupEvent.Post`：监听它的其它模组看不到进了背包的那部分拾取（统计与 `onItemPickup` 由本模组自己调用）。
- 与精妙背包/旅行者背包同时拾取时各拿多少不保证（只保证不崩溃、不复制、不丢失）；旅行者拾取升级的 `tick` 与精妙磁铁按范围吸取、不经过拾取事件，本模组拿不到那部分物品；对方放行的物品即使命中本模组黑名单也被对方拿走。
- 高频拾取会让背包物品栏频繁重发（本模组所有写路径已有的行为，拾取更频繁），**未测量**。
- 编解码层（`PickupBlacklistCodecs`、`BackpackContents` 第 6 个字段）无法单测，只靠编译与客户端会话。

## 给后来者的提醒

- `BackpackContents` 的 `StreamCodec.composite` **已用满 6 个字段**；再加字段必须先改成手写编码，或把几个小字段合并成一个子记录。
- `TagPanel*` 与 `PickupPanel*` 结构重复（复用了纯布局/输入/匹配组件，状态/输入/渲染各写了一套）；第三处需要同类面板时再抽象，本 feature 不顺手做。
- 两个第三方背包模组的内部类不是公开 API；如果将来要做主动避让，必须接受"随它们的版本失效"的代价（本 feature 评估后放弃，见 research Decision 6）。

## AI 自动验证（2026-11-11，Linux 容器内 `runClient`）：Q19、Q35 的一部分

环境与方法见 `docs/plan/ai-gui-verification.md`。

- **Q19（左侧区限放 1 个）**：左区已有一个自动拾取时，用光标往左区空格放第二个：被拒，聊天 `Only one Auto Pickup fits in this area`，光标上的物品不变；与自己的格子交换：没有提示；往右区放：允许。"别的物品的格子"被拒、Alt 交换主手被拒**没测**。
- **Q35（黑名单持久）部分通过**：给黑名单加 `stone`（页面显示 `1/64`），Save and Quit 后的 `playerdata` NBT 里该背包有 `pickup_blacklist: ['stone']`；重新进入世界，页面仍显示 `stone 1/64`；右键取走自动拾取、放回（换了左区的另一格），页面仍是 `stone 1/64`。**没测**：锻造台升级保留、两只背包黑名单互不共享、024 之前的旧背包黑名单为空无报错、Q36 多人、Q37 重跑 011～023。

## 2026-11-11 第二轮 AI 自动验证：Q36（多人）

专用服务器 + 两个客户端。P1 的背包左区放自动拾取，P2 的背包没有。在 P1 脚下掉 16 个圆石：全部进了 P1 的背包（面板里 16 个圆石）；在 P2 脚下掉 7 个钻石：进了 P2 的物品栏，P1 的背包没有钻石、P2 的背包没有圆石。**一名玩家的自动拾取只影响他自己，通过**。没测两人站在同一点同时抢同一堆掉落物。
