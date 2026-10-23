# Research: 可见可拖动空面板

## 为何不用 Screen 打开面板

- **Decision**: 开关面板只改客户端 `FloatingPanelState.visible`，绝不调用 `Minecraft.setScreen` 来「打开背包」。
- **Rationale**: 宪法 I 与规格 FR-002/003。`setScreen` 会关掉当前容器 UI，无法与按 E 共存。
- **Alternatives considered**: 自定义 `Screen` 并尝试保留下层——原版单 Screen 模型不支持可靠叠两层容器交互；Windowed GUIs 式全盘改造超出 M1。

## 渲染挂载点

- **Decision**: 在客户端用 Screen 渲染后置事件（或等价的 GUI 层事件）绘制面板，使面板出现在已打开的容器界面之上；世界内无 Screen 时同样绘制。
- **Rationale**: 规格要求与物品栏同时可见。挂在容器渲染之后比纯 HUD 层更接近「浮在界面上」。
- **Alternatives considered**: 只挂 `RenderGuiEvent`——打开 Screen 时可能被盖住或坐标空间不一致。第三方面板库（Panels Not Screens）——M1 不引入新依赖。

## 输入与拖动

- **Decision**: 订阅鼠标按下/释放/拖动（或 Screen 鼠标事件 Pre）。指针落在标题栏（拖动区）时开始拖动并消费事件；落在面板正文时消费点击但不做物品逻辑；落在面板外则不拦截。
- **Rationale**: 避免点穿到下层容器造成误操作，同时允许玩家操作未被遮挡的下层 UI。
- **Alternatives considered**: 打开面板时强制暂停下层输入——会破坏「同时使用物品栏」的体验。

## 快捷键在 Screen 打开时仍可用

- **Decision**: 继续使用 `KeyConflictContext.UNIVERSAL` 的 `KeyMapping`，并在客户端 `InputEvent.Key`（及必要时鼠标）里在 `matches` 时 toggle；聊天屏打开时忽略 toggle。
- **Rationale**: 规格 FR-010 与边界情况。骨架已注册 UNIVERSAL 键。
- **Alternatives considered**: 仅 `IN_GAME`——物品栏打开时无法开关。聊天中也响应——可能干扰打字。

## 位置持久化

- **Decision**: 使用 NeoForge 客户端配置（或模组本地 config 文件）保存 `posX`/`posY`；在拖动结束或退出世界时写入；加载模组/进入客户端时读出。
- **Rationale**: 规格 FR-005；M1 不要求按世界分配置。
- **Alternatives considered**: 存玩家 NBT——需要服务端，违反本阶段无物品/无服务端状态的边界。每次拖动都写盘——多余 IO。

## 屏幕外钳制

- **Decision**: 在拖动结束与加载配置后，将面板矩形钳制到窗口 GUI 坐标的可见范围（至少保留标题栏可点区域）。纯函数便于单测。
- **Rationale**: 规格 FR-006 / SC-004。
- **Alternatives considered**: 允许完全拖出——面板会「丢失」。自动重置到默认——丢掉用户布局。

## 与后续里程碑的接缝

- **Decision**: `FloatingPanelState` 只持有 UX 状态。M2 增加库存与网络时新增包/附件，不把物品数据塞进本阶段配置文件。
- **Rationale**: 宪法 III。
- **Alternatives considered**: M1 就建空 `ItemStackHandler`——易滑向未同步的假库存，违反宪法 II。
