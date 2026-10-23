# Feature Specification: 精妙/旅行者背包兼容

**Feature Branch**: `004-backpack-compat`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "M4：精妙背包/旅行者背包等热门背包 GUI 兼容；浮动面板与其 AbstractContainerScreen 路径互拖；软检测适配层；可选开发依赖；文档说明已知限制（流体槽等）"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 精妙背包 GUI 与浮动面板互拖 (Priority: P1)

玩家打开精妙背包（Sophisticated Backpacks）界面时，浮动背包仍可见；可用同一光标在精妙背包格子与浮动槽之间取放物品。

**Why this priority**: 热门背包之一；其 `StorageScreenBase` 继承 `AbstractContainerScreen`，是 M3 通路的首要回归目标。

**Independent Test**: 开发环境启用兼容 runtime 依赖后 `runClient`，开浮动面板 → 开精妙背包 → 往返拖动至少一组物品。

**Acceptance Scenarios**:

1. **Given** 浮动面板与精妙背包界面均打开，**When** 从精妙背包拿起物品再左键浮动空槽，**Then** 物品进入浮动槽，菜单 carried 清空。
2. **Given** 同上，**When** 从浮动槽拿起再点精妙背包空槽，**Then** 物品进入精妙背包，浮动槽空。
3. **Given** 未安装精妙背包，**When** 启动游戏，**Then** 本模组正常加载，无硬依赖崩溃。

---

### User Story 2 - 旅行者背包 GUI 与浮动面板互拖 (Priority: P1)

玩家打开旅行者背包（Traveler's Backpack）界面时，浮动背包仍可见，并能与其标准物品槽用同一光标互拖。

**Why this priority**: 与精妙同为 M4 文档目标；`AbstractBackpackScreen` 同样继承 `AbstractContainerScreen`。

**Independent Test**: 启用兼容 runtime 后开浮动面板 → 开旅行者背包 → 主存储槽往返拖动。

**Acceptance Scenarios**:

1. **Given** 浮动面板与旅行者背包界面打开，**When** 在主存储槽与浮动槽之间互拖，**Then** 两边数量正确。
2. **Given** 未安装旅行者背包，**When** 启动，**Then** 无硬依赖崩溃。

---

### User Story 3 - 软检测与可选开发依赖 (Priority: P2)

模组通过类名软检测识别已知背包界面（不硬引用第三方类型）；`build.gradle` 提供可选 `localRuntime`（或属性开关）以便开发者联调；`mods.toml` 声明 optional 依赖。文档写明流体槽、升级假槽等非标准交互的已知限制。

**Why this priority**: 保证可测、可文档化，且不把第三方打进发布依赖。

**Independent Test**: 单元测试覆盖类名匹配；默认 `./gradlew test` 不下载第三方 jar；开启兼容属性后能解析依赖坐标。

**Acceptance Scenarios**:

1. **Given** 屏幕类名含已知背包标识，**When** 查询兼容层，**Then** 报告为兼容容器且打上对应模组标签。
2. **Given** 任意 `AbstractContainerScreen`，**When** 查询兼容层，**Then** 视为可用菜单光标路径（与 M3 一致）。
3. **Given** 阅读 quickstart，**When** 按说明启用兼容 runtime，**Then** 能在开发客户端加载精妙/旅行者。

---

### Edge Cases

- 流体罐、过滤器幽灵槽、禁用槽、升级配置页：不保证与浮动背包互拖；物品主存储槽必须可用。
- 打开第三方背包前若浮动面板光标仍有物品：并入菜单 carried（沿用 M3 merge）。
- 本功能 MUST NOT 引入 compile/runtime 硬依赖。
- JEI/EMI 专项共存属 M5，本里程碑仅要求不因兼容层崩溃。

## Requirements *(mandatory)*

- **FR-001**: 精妙背包标准存储界面打开时，MUST 能用统一菜单 carried 与浮动背包互拖。
- **FR-002**: 旅行者背包标准存储界面打开时，MUST 同样支持与浮动背包互拖。
- **FR-003**: 兼容识别 MUST 使用软检测（类名/模组 id），MUST NOT 编译期引用第三方类型。
- **FR-004**: 发布产物 MUST NOT 将精妙/旅行者作为硬依赖；`mods.toml` MAY 声明 optional。
- **FR-005**: 客户端判断「容器打开」MUST 覆盖已知背包界面，且至少包含所有 `AbstractContainerScreen`。
- **FR-006**: MUST 文档化已知限制（流体、假槽、升级页）。
- **FR-007**: 所有物品移动 MUST 仍服务端权威；MUST NOT 用 `setScreen` 打开浮动背包。

## Success Criteria *(mandatory)*

- **SC-001**: 有兼容 jar 的开发会话中，精妙背包↔浮动背包往返拖动成功。
- **SC-002**: 有兼容 jar 的开发会话中，旅行者背包主存储↔浮动背包往返拖动成功。
- **SC-003**: 无第三方 jar 时 `./gradlew test compileJava` 通过，模组可单独加载。
- **SC-004**: 软检测单测覆盖已知类名样本。

## Assumptions

- 精妙：`StorageScreenBase` → `AbstractContainerScreen`（Sophisticated Core）。
- 旅行者：`AbstractBackpackScreen` / `BackpackScreen` → `AbstractContainerScreen`。
- 因此 M3 的 menu-carried 通路是主路径；M4 主要是显式兼容层、可选联调依赖与文档/回归。
