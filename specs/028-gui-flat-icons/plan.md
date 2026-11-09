# Implementation Plan: 背包在 GUI 里用平面图标

**Branch**: `028-gui-flat-icons` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

## Summary

快捷栏里的 8 只背包把帧率从约 101 压到约 22 fps，原因是每帧要画约 15 万个面。让背包在 **GUI** 场景里用平面图标，手持、第三人称、背在背上（`FIXED`）、展示框、掉落物（`GROUND`）仍用现在的 3D 模型。做法：

1. **每档物品模型改成 `neoforge:separate_transforms`**：`base` 指向原来的 OBJ 模型（文件改名为 `*_3d.json`，内容一个字不改），`perspectives` 只有 `gui`，指向 `item/generated` 平面模型。**不放 `fixed`**，因为背在背上的渲染用 `FIXED`。
2. **图标由开发期脚本从 OBJ 与皮肤贴图渲出**（`design/demo/tools/render_backpack_icons.py`，纯 Python + PIL，没有 numpy）：按原来的 GUI 视角（旋转 `[30, 225, 0]`）正交投影，Z 缓冲、柔和方向光、4× 超采样后缩到 32×32，五档共用同一个缩放比例以保持相对大小。
3. **验证分三层**：自动检查（模型 JSON 结构与图标文件）、启动客户端看资源重载有没有模型报错（图集度量脚本会启动客户端）、玩家在游戏里做帧率对照与外观对照（宪法 V）。

范围：只改显示。OBJ、皮肤贴图、手持与背在背上的变换、配方、功能一律不动；不新增设置，不限制任何模式或界面。

## Technical Context

**Language/Version**: Java 21（本 feature 没有 Java 改动）；Python 3 + PIL（开发期脚本）

**Primary Dependencies**: NeoForge 21.1.251 的 `neoforge:separate_transforms` 与 `neoforge:obj` 加载器；无新增依赖，不涉及第三方模组

**Storage**: 资源文件：5 个 PNG 图标、15 个模型 JSON（每档：入口、3D、图标）

**Testing**: JUnit 5 文本检查（`BackpackIconResourcesTest`：JSON 结构与图标文件）；图集度量脚本；启动客户端看日志里的模型错误；客户端会话里的帧率与外观对照

**Target Platform**: Minecraft 1.21.1 桌面客户端；开发机 macOS

**Performance Goals**: 快捷栏 8 只背包 vs 空快捷栏，帧率下降不超过约 10%（SC-001）

**Constraints**: 3D 模型与手持/背在背上的变换不变；共用方块图集宽高都不超过 2048（宪法 VI）；不新增设置、不限制任何场景（宪法 VII）

**Scale/Scale**: 5 张 32×32 图标（约 5,120 像素的图集增量）；脚本一个；JSON 15 个；测试类一个

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面 | PASS |
| II. Server Owns Items | 只改客户端显示，不涉及物品存储 | PASS |
| III. One Feature At A Time | 只做 GUI 图标；ESC 掉帧、减面、Ctrl 创造转移都不在此 | PASS |
| IV. Vanilla Path Before Compat | 平面图标与原版物品一致；不引用第三方 | PASS |
| V. Verify In A Client Session | quickstart 要求帧率对照、五档外观、手持、背在背上与掉落物不变、JEI | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 新增 5 张 32×32 贴图进共用图集：改动前后各测一次；**这正是该原则要管的情形** | PASS（已规划） |
| VII. Minimal Dependencies, Opt-Out Compatibility | 这是第一档（原版）的性能修复：不可配置、不新增设置、不限制模式或界面 | PASS |

## Project Structure

```text
design/demo/tools/render_backpack_icons.py                       # 新增，开发期脚本
src/main/resources/assets/mcfloatingbackpack/
├── textures/item/<tier>_floating_backpack_icon.png              # 新增 ×5
└── models/item/
    ├── <tier>_floating_backpack.json                            # 改：separate_transforms 入口
    ├── <tier>_floating_backpack_3d.json                         # 改名：原来的 OBJ 模型，内容不变
    └── <tier>_floating_backpack_icon.json                       # 新增：item/generated 平面模型
src/test/java/pnnh/mcfloatingbackpack/resources/BackpackIconResourcesTest.java   # 新增
```

**Structure Decision**: 入口 JSON 保持原来的文件名（所有引用不变）；原来的内容搬到 `_3d`；图标模型单独一个文件。

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。
