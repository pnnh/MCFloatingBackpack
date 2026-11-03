# MCFloatingBackpack — 项目目标与可行性分析（初版）

## 1. 项目概述

### 背景
原版 Minecraft 在同一时间通常只允许一个激活的 `Screen`（例如按 E 打开的玩家物品栏，或打开的箱子 / 旅行者背包 / 精妙背包界面）。打开新容器界面时，前一个界面会被关闭。这使得「像魔兽世界那样同时打开多个可拖动背包窗口，并在窗口之间拖动物品」在默认机制下不可用。

### 目标
开发一个面向 **Minecraft 1.21.1 + NeoForge** 的**浮动背包模组**：

- 通过**可自定义快捷键 / 组合键**开关浮动背包面板。
- 面板类似 MMO 背包：可拖动、始终浮在游戏画面之上。
- 即使同时打开原版物品栏、旅行者背包、精妙背包或箱子等容器 GUI，浮动背包仍保持可见并可交互（类似可拖动 HUD / 叠加层）。
- 支持在浮动背包与玩家物品栏、第三方背包 GUI、原版容器之间**互相拖放物品**。

### 核心原则
- **不要用「替换当前 Screen」作为主路径**：否则无法与其它容器界面共存。
- **显示层与存储层分离**：客户端负责面板渲染、拖动与输入抢占；服务端负责真实物品存储与校验。
- **优先 Overlay / Panel，而非多 Screen 栈**：参考 JEI/EMI 叠加在容器 GUI 上的做法。
- **双端模组**：只要背包有独立格子且可联机同步，就需要服务端逻辑；不能做成纯客户端伪造物品。
- **分阶段交付**：先打通「可开关、可拖动、可与原版物品栏互拖」，再做第三方背包专项兼容。

## 2. 核心功能需求（初版范围）

### 2.1 浮动面板 UX
- 快捷键切换显示 / 隐藏（默认建议 `B`，可在控制设置中改键与修饰键）。
- 面板可拖动到屏幕任意位置，位置可持久化（本地配置）。
- 打开其它容器 GUI 时不自动关闭；关闭其它 GUI 时也不强制关闭浮动背包。
- 渲染层级高于当前容器界面内容（至少在物品栏 / 容器内容之上可点中）。

### 2.2 物品交互
- 面板内格子支持点击、Shift 点击、拖拽堆叠等接近原版槽位的操作。
- 当原版物品栏或其它 `AbstractContainerScreen` 打开时，可在浮动背包槽位与当前打开容器槽位之间移动物品。
- 无打开容器时，仍可独立管理浮动背包内物品（打开面板本身即足够）。

### 2.3 存储与联机
- 背包内容绑定玩家（或绑定可配置的背包物品 / 附件），由服务端权威保存。
- 客户端预测点击反馈，服务端通过网络包确认，避免「看起来移动了但实际没同步」。

### 2.4 兼容目标（优先级）
1. 原版玩家物品栏（E）
2. 原版箱子 / 潜影盒等标准容器
3. 精妙背包（Sophisticated Backpacks）类界面
4. 旅行者背包（Traveler's Backpack）类界面
5. JEI/EMI 叠加层共存（避免抢鼠标冲突）

## 3. 原版限制：为什么「同时只能开一个背包」

| 机制 | 表现 | 对本需求的影响 |
|------|------|----------------|
| `Minecraft.screen` 单例 | 客户端同一时刻只有一个活动 Screen | 用 `setScreen(new InventoryScreen)` 会关掉上一个界面 |
| `AbstractContainerMenu` | 服务端玩家通常对应一个打开的容器菜单 | 多容器同时打开需要额外菜单会话或自定义交互协议 |
| 槽位点击 | `ServerboundContainerClickPacket` 针对当前 `containerId` | 跨界面拖放不能只靠「两个 Screen 同时存在」 |
| 鼠标捕获 | Screen 打开时游戏进入 GUI 输入模式 | Overlay 必须在 Screen 事件链中抢先处理点击 |

结论：**需求无法通过「再做一个普通 Screen」直接满足**；必须绕开单 Screen 假设，或改造容器窗口系统。

## 4. 可行性结论

**总体判断：可行，但工程重点在「叠加层输入 + 跨菜单物品同步」，不是简单 UI 贴图。**

| 能力 | 可行性 | 说明 |
|------|--------|------|
| 快捷键开关浮动面板 | 高 | NeoForge `RegisterKeyMappingsEvent` + 客户端 tick / 输入事件即可 |
| 面板拖动与常驻显示 | 高 | HUD / `ScreenEvent.Render` 叠加绘制已有大量先例 |
| 浮在原版物品栏之上并可点击 | 高 | JEI/EMI、Inventory HUD+、Panels Not Screens 等证明 Overlay 可拦截鼠标 |
| 与箱子等标准容器互拖物品 | 中高 | 需把 Overlay 点击翻译成对「当前打开 Menu」或「自有 Menu」的服务端操作 |
| 与精妙 / 旅行者背包互拖 | 中 | 对方若走标准 `AbstractContainerScreen`/`Menu`，路径类似；若自定义渲染/输入则需专项适配 |
| 完全像 WoW 多窗口自由共存且无兼容问题 | 中低（长期） | 第三方 GUI 差异大，需黑名单 / 适配层与持续回归 |

### 4.1 推荐技术方案（主路径）

采用 **「自有背包库存 + 客户端浮动 Overlay」**：

1. **服务端**：为玩家注册背包库存（Attachment / Capability / 保存到玩家 NBT），提供点击与移动的网络协议。
2. **客户端**：不调用 `setScreen` 打开背包；用 Overlay / Panel 绘制格子。
3. **输入**：在 `ScreenEvent.MouseButtonPressed.Pre`（及拖拽相关事件）中，若指针落在浮动面板上则消费事件，避免穿透到下层容器。
4. **跨 GUI 拖放**：
   - 浮动背包 ↔ 当前打开的 `AbstractContainerMenu`：通过自定义包或模拟槽位点击，在服务端两个库存之间转移。
   - 浮动背包自身：始终有明确的服务端库存句柄，不依赖「当前是否打开了 Screen」。
5. **可选增强**：当需要原版手持光标物品（carried stack）体验时，复用 / 同步玩家当前 Menu 的 carried 槽，或自建 carried 状态并在打开原版 GUI 时合并。

这条路径与「再做一个 InventoryScreen」相比，能直接满足「按 E 后背包仍浮在上面」。

### 4.2 备选 / 参考路径

| 路径 | 优点 | 缺点 | 适用 |
|------|------|------|------|
| Overlay + 自有库存（推荐） | 不抢占 `Minecraft.screen`；可控性强 | 要自研槽位交互与同步 | 本模组主目标 |
| 改造多窗口容器（如 Windowed GUIs 思路） | 可同时开多个原版容器 | 侵入大、兼容面极广 | 可作为远期研究方向，非 MVP |
| 仅 HUD 展示玩家背包（InvHUD 类） | 实现简单 | 通常只是「看」或弱交互，不是独立背包 | 不满足独立存储目标 |
| 第三方面板库（如 Panels Not Screens） | 拖动 / 停靠 / z-order 现成 | 引入依赖；库存逻辑仍要自写 | 可评估是否降低 UI 成本 |

### 4.3 关键先例（说明并非空中楼阁）

- **JEI / EMI**：在容器 Screen 之上绘制可交互区域并处理鼠标，证明 Overlay 可行。
- **Inventory HUD+ / InvHUD**：背包格子 HUD 化、可拖动定位。
- **Windowed GUIs**：把容器改成可同时存在的窗口（目标更激进，兼容成本更高）。
- **Panels Not Screens**：NeoForge 1.21.1 上的可拖动面板框架思路，可参考输入路由与 z-order。
- **HoloGUI**：浮动菜单同时保留世界交互的另一类尝试。

本模组与「纯 HUD 显示」的差异在于：**独立（或半独立）库存 + 与其它打开容器双向拖放**；与 Windowed GUIs 的差异在于：**不试图重做全游戏多窗口系统，只做一个常驻浮动背包**。

## 5. 主要风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 鼠标事件与 JEI/EMI/其它 Overlay 抢占顺序 | 点击失灵或点穿 | 明确事件优先级；提供「面板锁定 / 穿透」配置；联调 JEI |
| 第三方背包非标准 Screen | 无法互拖 | 检测模组后走适配器；做不到则降级为「仅与原版容器互拖」并文档说明 |
| carried 物品（鼠标上挂着的物品）状态分裂 | 拖到一半错乱 | 尽量接入当前打开 Menu 的 carried；无 Menu 时用自有 carried 并在打开 GUI 时同步 |
| 仅客户端改画面、不同步服务端 | 联机刷物 / 回滚 | 服务端权威；所有移动走网络校验 |
| 打开 Screen 时快捷键被吞 | 无法开关面板 | 使用 `KeyConflictContext.UNIVERSAL` 或额外在 Screen 输入钩子中处理 |
| GUI 缩放 / 宽屏坐标 | 拖动与点击偏移 | 统一用 GUI 坐标换算（`guiLeft`/`guiTop` 同思路） |

## 6. 建议里程碑

1. **M0 — 工程骨架（本仓库当前状态）**  
   NeoForge 1.21.1 工程、模组入口、快捷键注册、文档。
2. **M1 — 可见可拖动空面板**  
   Overlay 渲染、拖动、位置持久化；尚无真实物品。
3. **M2 — 服务端库存 + 面板内取放**  
   玩家绑定背包、网络同步、面板内点击。
4. **M3 — 与原版物品栏 / 箱子互拖**  
   有打开容器时的跨库存转移与 carried 行为。
5. **M4 — 精妙 / 旅行者等热门背包兼容**  
   二者主界面均继承 `AbstractContainerScreen`，走 M3 菜单 carried 通路；软检测 + 可选 `./gradlew runClientCompat` / `runServerCompat`（等价于 `-PcompatRuntime`）联调。流体罐/假槽/升级页不保证。详见 `specs/004-backpack-compat/`。
6. **M5 — 打磨**  
   配置项、键位冲突、JEI 共存、多语言、性能与联机测试。面板可拖拽缩放行列（独立调整，3 格下限，列数变化时按虚拟总行数重新排布，仅纵向滚动条）、标题栏关闭按钮、标题栏按背包等级动态命名，详见 `specs/007-floating-window-chrome/`。标题栏改为背包等级图标（悬停显示名称提示，窄面板下不再溢出）；可选的 JEI 兼容：通过 `IGlobalGuiHandler` 让 JEI 避让浮动面板区域，并修正与 JEI 自身提示框的渲染顺序冲突，仅在玩家安装 JEI 时生效，详见 `specs/008-panel-title-icon-and-jei-safe-zone/`。五档背包（皮革/铁/金/钻石/下界合金）均已换成重新建模的外观（阶梯收腰轮廓、多色调贴图，含背带/卡扣细节），几何体五档共用、仅配色不同，模型与贴图设计过程见 `design/backpack-redesign/`。可拆卸小包（左/右/后）的几何体已预备好，但尚未接入实际"升级组件"玩法逻辑，目前所有档位都按不含小包的基础款渲染。

## 7. 技术栈与工程约定

与 `thirdpart/MCBilingual` 对齐，便于同一开发环境维护：

| 项 | 取值 |
|----|------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251 |
| 映射 | Parchment 1.21.1 / 2024.11.17 |
| Java | 21 |
| 构建 | ModDevGradle 2.0.148 + Gradle Wrapper 9.2.1 |
| 模组 ID | `mcfloatingbackpack` |
| 根包名 | `pnnh.mcfloatingbackpack` |
| 开发启动 | `./gradlew runClient` |

目录结构要点：

```text
MCFloatingBackpack/
  build.gradle / settings.gradle / gradle.properties
  gradle/wrapper/
  docs/MCFloatingBackpack.md
  src/main/java/pnnh/mcfloatingbackpack/
    MCFloatingBackpack.java          # 双端入口
    client/                          # Overlay、快捷键、面板
  src/main/resources/
    assets/mcfloatingbackpack/lang/
    mcfloatingbackpack.mixins.json
    META-INF/accesstransformer.cfg
  src/main/templates/META-INF/neoforge.mods.toml
  thirdpart/MCBilingual/             # 参考工程（gitignore，不提交）
```

## 8. 建议配置项（示例，非最终格式）

```yaml
panel:
  visible_on_join: false
  pos_x: 20
  pos_y: 20
  scale: 1.0
  slots: 27                # 初版可固定，后续可升级

hotkey:
  toggle: "key.keyboard.b" # 实际以游戏 Controls 为准

compat:
  consume_mouse_over_panel: true
  jei_safe_zone: true
```

## 9. 初版 conclusión

- **需求在 NeoForge 1.21.1 上可行**，关键不是「再做一个背包 Screen」，而是 **Overlay 常驻 UI + 服务端库存 + 跨当前容器的物品操作协议**。
- 原版「同时只能有一个激活面板」的限制真实存在，但可被 Overlay 方案绕过；JEI 类模组已验证这条技术路线。
- 与旅行者 / 精妙背包的无缝互拖属于**兼容性工作**，应在原版通路打通后再做，不宜作为 MVP 阻塞项。

---

*文档版本：0.1.0（项目初始化）*
