# Tasks: 标题栏图标化 + JEI 悬浮遮挡修复

**Input**: Design documents from `/specs/008-panel-title-icon-and-jei-safe-zone/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md（无 `contracts/`，见 plan.md 说明）

**Tests**: 延续本项目既有的"纯逻辑 JUnit + `runClient`/`runClientCompat` 手动验证"模式；JEI 相关行为无法用纯逻辑测试覆盖（依赖 JEI 真实渲染），只能靠 quickstart.md 的客户端会话验证。

**Organization**: 按用户故事分组。US1（标题图标）与 US2（JEI 视觉冲突修复）改动的文件不重叠，互不依赖，可任意顺序实现。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖同一故事内未完成的任务）
- **[Story]**: 仅用户故事阶段使用（US1–US2）

---

## Phase 1: Setup

本功能所有依赖/配置改动都只被 US2 使用，没有跨故事共享的 Setup 项；相关任务放进 US2 阶段的第一个任务。

---

## Phase 2: Foundational

US1、US2 改动的文件完全不重叠（US1：`FloatingPanelState.java` 新增一个方法 + `FloatingPanelRenderer.java` 的标题绘制部分；US2：`build.gradle`、新文件 `compat/jei/FloatingBackpackJeiPlugin.java`、`FloatingPanelRenderer.java` 的 render() 开头一行），没有需要互相等待的前置条件；无 Foundational 任务。

---

## Phase 3: User Story 1 - 标题栏改为图标，不再溢出 (Priority: P1) 🎯 MVP

**Goal**: 标题栏不再显示可能溢出的文字，改为显示背包等级图标；悬停图标弹出原版风格 tooltip 显示等级名称。

**Independent Test**: 打开任意等级背包，缩到最窄（3 列），确认标题栏只有图标、不溢出；悬停图标看到名称提示；切换等级后图标和提示同步更新。

### Tests for User Story 1

- [X] T001 [P] [US1] 新建 `src/test/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelStateTitleIconTest.java`：覆盖 `hitsTitleIcon` 的命中边界（比照 `PanelSlotsResizeTest.java` 对 `hitsCloseButton`/`hitsResizeHandle` 的测试写法）

### Implementation for User Story 1

- [X] T002 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelState.java`：新增 `hitsTitleIcon(double mouseX, double mouseY)`（标题栏左侧固定像素区域的命中判断，写法比照既有 `hitsCloseButton`）
- [X] T003 [US1] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：把 `render()` 里原来的 `graphics.drawString(minecraft.font, title, x + 8, y + 6, ...)` 替换成——用 `ModItems.stackFor(BackpackTier.byCapacity(ClientBackpackView.get().slotCount()))` 取图标用的 `ItemStack`（`byCapacity` 返回 `null` 时跳过图标绘制，不弹 tooltip），在标题栏左侧用 `graphics.renderItem(...)` 画出来；命中 `state.hitsTitleIcon(mouseX, mouseY)` 时额外调用 `graphics.renderTooltip(minecraft.font, titleFor(...), mouseX, mouseY)`（`titleFor` 这个既有私有方法直接复用，不用改）。原来只被这行用到的 `COLOR_TITLE_TEXT` 常量一并删除。

**Checkpoint**: 标题栏图标在任意面板尺寸下不溢出，悬停提示正确；US1 可独立验收。

---

## Phase 4: User Story 2 - 浮动面板不与 JEI 的提示框产生视觉冲突 (Priority: P2)

**Goal**: 安装了 JEI 时，浮动面板覆盖区域内 JEI 不再摆放物品/弹出提示框；面板未盖住物品但 JEI 提示框范围延伸到面板区域时，不再出现背景被挡、文字穿透的撕裂画面。未安装 JEI 时不受任何影响。

**Independent Test**: 按 `quickstart.md` 第二部分，在 `./gradlew runClientCompat` 下分别验证排除区域生效（面板盖住物品）与撕裂修复（面板未盖住物品、但提示框延伸过来）两种场景；再在 `./gradlew runClient`（不带 compat）下确认默认启动流程和日志无 JEI 相关异常。

### Implementation for User Story 2

- [X] T004 [US2] 修改 `build.gradle`：新增一条**无条件**（不受现有 `compatRequested` 开关门控）的 `compileOnly "mezz.jei:jei-1.21.1-common-api:19.70.0.469"`，并加注释说明这是编译期可选依赖、不随成品 jar 发布、和下方 `-PcompatRuntime` 门控的运行期 JEI 依赖是两回事
- [X] T005 [US2] 新建 `src/main/java/pnnh/mcfloatingbackpack/compat/jei/FloatingBackpackJeiPlugin.java`：`@JeiPlugin` 注解，实现 `IModPlugin`（`getPluginUid()` 返回 `ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "jei_plugin")`），`registerGuiHandlers(IGuiHandlerRegistration registration)` 里调用 `registration.addGlobalGuiHandler(...)`，传入一个匿名类实现 `IGlobalGuiHandler.getGuiExtraAreas()`：`FloatingPanelState.get().isVisible()` 为 `false` 时返回空集合，为 `true` 时返回包含一个 `new Rect2i(state.getPosX(), state.getPosY(), state.getWidth(), state.getHeight())` 的集合
- [X] T006 [US2] 修改 `src/main/java/pnnh/mcfloatingbackpack/client/panel/FloatingPanelRenderer.java`：在 `render()` 方法最开头（`if (!state.isVisible()) return;` 判断之后、其它任何绘制调用之前）新增一次 `graphics.flush()`（配合既有结尾的 `graphics.flush()`，两头各一次）

**Checkpoint**: JEI 不存在时游戏启动和浮动背包全部功能不受影响；JEI 存在时按 quickstart.md 第二部分验证两类视觉冲突都已修复；US2 可独立验收。

---

## Phase 5: Polish

- [X] T007 [P] 更新 `docs/MCFloatingBackpack.md`，在相关段落补充标题图标化与 JEI 视觉冲突修复的说明
- [X] T008 运行 `./gradlew test compileJava`，确认全部通过（10 个测试类、0 失败；另确认 `compileOnly` JEI 依赖只出现在 `compileClasspath`，不出现在默认 `runtimeClasspath`，`runClientCompat` 的 curse.maven/JEI 依赖解析不受影响）
- [ ] T009（2026-11-04：第一部分标题图标、第二部分的排除区域/面板提示/004 回归已通过；撕裂修复场景与第三部分无 JEI 回归未验证，见 DELIVERY.md）按 `specs/008-panel-title-icon-and-jei-safe-zone/quickstart.md` 的三部分清单，分别在 `./gradlew runClient` 与 `./gradlew runClientCompat` 下验证，把结果记录到 `specs/008-panel-title-icon-and-jei-safe-zone/DELIVERY.md` —— **需要真实客户端会话，无法由我代为执行，留给你验证**

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup、Foundational 均为空，可直接从 Phase 3 开始
- US1（Phase 3）与 US2（Phase 4）互不依赖，可任意顺序或并行实现
- Polish（Phase 6）依赖两个故事都已完成

### User Story Dependencies

- **US1**：无依赖，可直接开始
- **US2**：无依赖，可直接开始；T006（在 `FloatingPanelRenderer.java` 开头加 `flush()`）和 US1 的 T003（同一个文件里改标题绘制）严格来说在同一个文件，但分别是方法开头和方法中段两处不重叠的改动，正常顺序完成即可，不存在真正的阻塞关系

### Parallel Opportunities

- T001（US1 测试）可与 T004/T005（US2 的 build.gradle + 新插件文件）完全并行，分属不同故事、不同文件
- US1 和 US2 整体可以交给两个人并行推进

## Parallel Example

```text
T001 FloatingPanelStateTitleIconTest.java：标题图标命中测试（US1）
T004 build.gradle：新增 compileOnly JEI API 依赖（US2）
T005 FloatingBackpackJeiPlugin.java：JEI 插件类（US2，依赖 T004 的编译期依赖先落地）
```

## Implementation Strategy

### MVP 优先（仅 US1）

1. 完成 Phase 3（US1）：标题图标化，解决已确认会出现的文字溢出缺陷。
2. **停下验证**：按 quickstart.md 第一部分手动验证。
3. US1 本身不依赖 JEI，任何玩家都能受益，可独立交付。

### 增量交付

1. US1（标题图标，MVP）→ 独立验证 → 可发布。
2. US2（JEI 视觉冲突修复，可选增强）→ 按 quickstart.md 第二、三部分验证 → 可发布。
3. Polish：跑完整测试 + quickstart 全量验证 + 写 `DELIVERY.md`。

## Notes

- US2 的 JEI 集成全程只在 JEI 存在时生效；T004 新增的 `compileOnly` 依赖不得写进现有 `compatRequested` 开关的 `if` 块里（那个开关控制的是运行期本地 jar，语义不同，见 research.md Decision 5）。
- `FloatingBackpackJeiPlugin` 用 `addGlobalGuiHandler`，不要改成按 Screen 类型枚举注册的 `addGuiContainerHandler`——research.md Decision 2 已经用 JEI 官方源码确认前者就是为"会和任意容器界面同屏出现的悬浮面板"设计的，不需要枚举。
- T006 的 `flush()` 修复是基于读 JEI 源码得出的最小改动假设，必须在 quickstart.md 第二部分实测确认；如果仍有残留撕裂，参考 research.md Decision 4 的 Alternatives considered 再评估，不要凭感觉加别的 flush 调用。
