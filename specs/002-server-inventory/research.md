# Research: 服务端库存与面板内取放

## 玩家数据挂载方式

- **Decision**: 使用 NeoForge 1.21 **Attachment** 把 27 槽 `ItemStackHandler`（或等价容器）挂到 `Player`，并标记持久化。
- **Rationale**: 1.21 NeoForge 推荐替代旧 Capability 注册样板；随玩家存档读写，联机自然可用。
- **Alternatives considered**: 只写 `PersistentData` NBT——可行但要手写更多样板。世界级 SavedData——不适合按玩家隔离。

## 为何不用 ContainerMenu 打开背包

- **Decision**: 不注册「打开背包就 setScreen」的 Menu。点击全部走自定义 Payload。
- **Rationale**: 宪法 I；Menu 会抢占当前容器会话，破坏与物品栏共存的长期目标。
- **Alternatives considered**: 隐藏 Menu 仅用于点击协议——仍易与原版 `containerId` 冲突，M3 更难。

## 同步策略

- **Decision**: 玩家登录（及必要时面板首次打开）发送全量快照；每次成功点击后回推变更的槽位与面板光标。
- **Rationale**: 实现简单、易纠偏；27 槽全量很小。
- **Alternatives considered**: 纯客户端预测无确认——违反宪法 II。每 tick 全量——浪费带宽。

## 面板光标 vs 原版 Menu carried

- **Decision**: M2 使用**模组自己的面板光标** `ItemStack`，存在玩家 attachment 或会话状态中并同步到客户端。不读取/写入打开中的 `AbstractContainerMenu.getCarried()`。
- **Rationale**: 规格把跨 GUI 光标留给 M3；分离可避免半实现的双光标串扰。
- **Alternatives considered**: 立刻合并到当前 Menu carried——本质是 M3 的一部分。

## 无容器界面时的存取路径

- **Decision**: 左键面板槽位时，若「面板光标为空」且槽位有物，则拿起至面板光标；若要从身上放入：当面板光标为空、点空槽且**主手非空**时，将主手栈移入该槽（服务端执行）。从面板光标取回身上：在面板空白处以外提供「放回主手」——默认再点同一逻辑：若点槽外不操作；增加 **Shift+左键槽位** 将槽内物品与主手交换（无容器界面时）。更简单的默认：**面板光标与主手共用一条规则**——无 Screen 时，面板光标的渲染叠在鼠标上，取出时若关闭面板则尝试把面板光标塞回玩家库存空位。
- **精简落地（选定）**:
  1. 面板内点击只操作「背包槽 ↔ 面板光标」。
  2. **主手快捷存取**：无容器 Screen 时，对空槽 **Shift+左键** = 主手 → 槽；对非空槽 **Shift+左键** = 槽 → 主手（主手非空则交换）。
  3. 有容器 Screen 时，Shift 主手路径禁用，仅面板光标点击有效（避免与 Meltdown M3 抢戏）。
- **Rationale**: 满足 FR-006，同时边界清晰。
- **Alternatives considered**: 调试指令填物——仅开发可用，不够验收。

## 点击规则实现

- **Decision**: 将左键拿起/放下/交换/堆叠写成 `BackpackClickLogic`，输入为槽列表 + 光标栈 + 槽索引，输出新状态；服务端套用后同步。
- **Rationale**: 可单测，行为接近原版但不复制整个 Menu 点击状态机。
- **Alternatives considered**: 完整模拟 `AbstractContainerMenu.clicked`——过重，且依赖 Menu 实例。

## 客户端渲染

- **Decision**: 在现有灰槽上用 `GuiGraphics.renderItem` / `renderItemDecorations` 画 `ClientBackpackView` 中的栈；面板光标在鼠标处额外绘制（有 Screen 或无 Screen 均可）。
- **Rationale**: 复用原版物品图标与数量字。
- **Alternatives considered**: 只显示文字——体验差。
