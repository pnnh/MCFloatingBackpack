# Implementation Plan: 标题栏图标化 + JEI 悬浮遮挡修复

**Branch**: `008-panel-title-icon-and-jei-safe-zone` | **Date**: 2026-11-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-panel-title-icon-and-jei-safe-zone/spec.md`

## Summary

007 交付后玩家实测发现两处问题：(1) 面板缩到最窄（3 列）时标题文字溢出面板边界；(2) 安装了 JEI 时，浮动面板会和 JEI 自己的提示框产生两类视觉冲突——面板盖住物品格子导致提示误触发，以及面板没盖住物品、但提示框范围延伸到面板区域导致背景/文字各自不同叠放顺序的画面撕裂。

本阶段方案：(1) 标题栏改成按背包等级显示的小图标，悬停时弹出原版风格 tooltip 显示名称，图标尺寸固定、不随文字长度变化，从根上不会溢出。(2) 新增一个只有 JEI 存在时才生效的 `@JeiPlugin` 插件类（`compileOnly` 编译期依赖，不随成品发布、不是运行时硬依赖），用 JEI 官方 `IGlobalGuiHandler.getGuiExtraAreas()` 机制告诉 JEI"这块屏幕区域已被占用"，解决面板盖住物品格子的情况；同时在 `FloatingPanelRenderer.render()` 开头补一次 `graphics.flush()`，让任何此前尚未刷新到屏幕的缓冲内容（包括 JEI 可能已调用但未刷新的提示框）先行定型，解决提示框撕裂的情况。这两处 JEI 相关修复完全是可选增强：没装 JEI 的玩家不受任何影响。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251；Minecraft 1.21.1 客户端 GUI API（`GuiGraphics`）；**新增**：JEI 1.21.1 API（`mezz.jei:jei-1.21.1-common-api:19.70.0.469`，`compileOnly`，编译期可选依赖，不打进成品 jar、不是运行时硬依赖）

**Storage**: 无新增存储；不涉及任何持久化数据

**Testing**: JUnit 5 纯逻辑单元测试覆盖标题栏图标命中测试、背包等级反查等不依赖 Minecraft 启动的部分；JEI 排除区域生效与否、提示框撕裂是否消失，必须在 `./gradlew runClientCompat` 真实客户端会话里对照 quickstart.md 验证（宪法 V 条），纯 Java 层面无法模拟 JEI 的实际渲染行为

**Target Platform**: Minecraft 1.21.1 桌面客户端（Windows/macOS/Linux）

**Project Type**: 单一 NeoForge 模组；标题栏图标化是纯客户端渲染改动，JEI 相关修复是新增的"编译期可选依赖 + 运行时软检测"集成，模式上类似 004 的第三方背包兼容，但这是本项目第一次需要在源码里直接 `import` 第三方模组的 API 类型（此前的 `ContainerScreenCompat` 是零编译依赖的纯反射/字符串匹配）

**Performance Goals**: 标题栏图标渲染与悬停判定是每帧 O(1) 开销；JEI 排除区域查询（`getGuiExtraAreas()`）只在 JEI 自己的布局更新时机被调用，不由本模组驱动频率

**Constraints**: JEI 不存在时，新增的 `@JeiPlugin` 类不得被加载或产生任何错误/警告；默认构建（`./gradlew runClient`，不带 `-PcompatRuntime`）的运行时依赖树不应该出现 JEI

**Scale/Scope**: 改动集中在 `client/panel/FloatingPanelRenderer.java`、`client/panel/FloatingPanelState.java`（图标命中测试）；新增 `compat/jei/` 包下的一个 `@JeiPlugin` 类；`build.gradle` 新增一条无条件的 `compileOnly` JEI API 依赖

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 本功能只改标题栏渲染内容和新增一个 JEI 排除区域上报，不引入 `setScreen`，面板仍是 Overlay | PASS |
| II. Server Owns Items | 不涉及任何物品数据或网络协议改动 | PASS（不适用） |
| III. One Feature At A Time | spec.md 已限定范围为"标题图标化 + JEI 视觉冲突修复"两项，不涉及 007 已交付的缩放/关闭等其它交互 | PASS |
| IV. Vanilla Path Before Compat | JEI 集成通过 `compileOnly` + `@JeiPlugin` 软检测实现，JEI 不存在时完全不生效，不是硬依赖；原版路径（标题图标）不依赖任何第三方模组 | PASS |
| V. Verify In A Client Session | quickstart.md 要求在 `./gradlew runClientCompat` 真实会话里逐条验证 JEI 排除区域与撕裂修复是否生效，标题图标部分也要求在 `runClient` 下观察窄面板表现 | PASS（已规划，实现阶段需真正执行） |

无违反项，`Complexity Tracking` 留空。

## Project Structure

### Documentation (this feature)

```text
specs/008-panel-title-icon-and-jei-safe-zone/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

本功能不新增/修改任何 `CustomPacketPayload`，不生成 `contracts/`——唯一的"外部接口"是对 JEI 公开 API 的调用（`IModPlugin`/`IGlobalGuiHandler`），这是消费第三方模组的 API 而不是本模组自己暴露的接口，不属于需要在 `/contracts/` 里文档化的范畴。

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── client/panel/
│   ├── FloatingPanelState.java     # 新增：hitsTitleIcon(mouseX, mouseY) 命中测试（比照既有 hitsCloseButton）
│   └── FloatingPanelRenderer.java  # 修改：标题栏改画图标 + 悬停 tooltip；render() 开头新增一次 graphics.flush()
└── compat/
    └── jei/                        # 新增包：本项目首次需要编译期 import 第三方模组 API 的代码，和零依赖的
        └── FloatingBackpackJeiPlugin.java  #   ContainerScreenCompat 分开存放；@JeiPlugin，实现 IModPlugin + 匿名 IGlobalGuiHandler

src/test/java/pnnh/mcfloatingbackpack/client/panel/
└── FloatingPanelStateTitleIconTest.java  # 新增：标题栏图标命中区域的纯逻辑测试
```

**Structure Decision**: 单项目结构，不引入新模块；`compat/jei/` 是一个新的子包而非散落在既有 `compat` 包里，因为它和 `ContainerScreenCompat` 的依赖模式本质不同（前者编译期直接依赖 JEI API 类型，后者零编译依赖），分开存放能让"这段代码需要 JEI 在 classpath 上才能编译"这件事一眼可见，不会被误当成和 `ContainerScreenCompat` 一样的零依赖代码去修改。

## Complexity Tracking

*无违反项，本节留空。*
