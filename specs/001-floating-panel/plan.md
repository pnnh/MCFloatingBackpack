# Implementation Plan: 可见可拖动空面板

**Branch**: `001-floating-panel` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-floating-panel/spec.md`

## Summary

实现 M1：客户端浮动空面板。用 Overlay（非 `setScreen`）绘制可开关、可拖动的占位窗口；位置写入客户端本地配置；打开原版物品栏或容器时面板不关闭。本阶段不接入真实物品与服务端库存。

## Technical Context

**Language/Version**: Java 21，Minecraft 1.21.1，NeoForge 21.1.251

**Primary Dependencies**: NeoForge 客户端事件（按键注册、输入、Screen/HUD 渲染）。不引入第三方 UI 库。

**Storage**: 客户端本地配置保存面板坐标与可见性偏好（若需要）；无世界 NBT、无网络包。

**Testing**: 坐标钳制等纯逻辑用 JUnit。可见行为用 `./gradlew runClient`，步骤见 [quickstart.md](quickstart.md)。

**Target Platform**: Minecraft 1.21.1 客户端（单人 `runClient` 为主）

**Project Type**: NeoForge 模组（双端入口保留；本特征仅客户端行为）

**Performance Goals**: 每帧仅在面板可见时绘制一次简单矩形/标题栏；拖动时更新坐标，无每帧磁盘写入（松开或关闭时落盘）。

**Constraints**: 不得用 `setScreen` 打开面板。不得实现物品槽交互。快捷键在其它 Screen 打开时仍可用。

**Scale/Scope**: 一块空面板 + 拖动 + 本地位置；不含 M2–M5。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结果 |
| --- | --- |
| I. Overlay Not Screen | 通过。面板走 Overlay/渲染与输入事件，不替换 `Minecraft.screen`。 |
| II. Server Owns Items | 通过。本阶段无真实物品，不伪造库存。 |
| III. One Feature At A Time | 通过。范围锁定 M1；存储与互拖不进入本计划。 |
| IV. Vanilla Path Before Compat | 通过。只验证原版物品栏/箱子共存，无第三方背包适配。 |
| V. Verify In A Client Session | 通过。quickstart 规定 `runClient` 检查；钳制逻辑可单测。 |

Phase 1 之后复检：仍通过。无 Complexity Tracking 例外。

## Project Structure

### Documentation (this feature)

```text
specs/001-floating-panel/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── floating-panel.md
├── checklists/
│   └── requirements.md
└── tasks.md                 # 由 /speckit-tasks 生成，本阶段不创建
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── MCFloatingBackpack.java
└── client/
    ├── ClientSetup.java
    ├── FloatingBackpackKeys.java
    ├── panel/
    │   ├── FloatingPanelState.java      # 可见性、位置、拖动中状态
    │   ├── FloatingPanelConfig.java     # 本地读写坐标
    │   ├── FloatingPanelRenderer.java   # Overlay 绘制
    │   └── FloatingPanelInput.java      # 拖动与点击消费
    └── FloatingPanelEvents.java         # 订阅渲染/输入/tick

src/test/java/pnnh/mcfloatingbackpack/client/panel/
└── PanelClampTest.java                  # 屏幕边界钳制
```

**Structure Decision**: 单模块 NeoForge 模组。面板状态与渲染全部放在 `client/panel`，与后续库存包隔离。现有快捷键类保留并接上真正的 toggle。

## Complexity Tracking

无。
