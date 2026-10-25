# Implementation Plan: 背包道具化（A/C/E）

**Branch**: `005-item-backpack` | **Date**: 2026-10-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-item-backpack/spec.md`

## Summary

将浮动背包从「玩家 Attachment 全局 27 格」改为「皮革浮动背包物品 + 36 格 Data Component」；无物品不可按 B / 右键打开；可装备胸甲栏并第三人称背负占位渲染；JEI 仅开发 `localRuntime`。不实现多等级升级链与 OP 指令。

## Technical Context

**Language/Version**: Java 21，Minecraft 1.21.1，NeoForge 21.1.251

**Primary Dependencies**: NeoForge 物品注册、Data Components、既有 Overlay / 网络点击、可选 JEI（dev only）

**Storage**: ItemStack Data Component（36 槽 + 面板光标）；删除玩家 Backpack Attachment

**Testing**: 解析顺序与 contents 编解码可 JUnit；门禁 / 胸甲 / JEI 用 `./gradlew runClient`（quickstart）

**Target Platform**: 双端模组

**Project Type**: NeoForge 模组

**Performance Goals**: 全量同步 36 槽仍很小；点击差分更新

**Constraints**: Overlay Not Screen；Server Owns Items；正式包无 JEI 硬依赖；不迁移旧 Attachment

**Scale/Scope**: 单物品、36 格、无升级侧栏、无 Curios、无 OP 指令

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结果 |
| --- | --- |
| I. Overlay Not Screen | 通过。右键/快捷键只切换 Overlay。 |
| II. Server Owns Items | 通过。服务端改 Item component 并回推。 |
| III. One Feature At A Time | 通过。B/D 与高等级明确排除。 |
| IV. Vanilla Path Before Compat | 通过。JEI 为可选开发依赖，非兼容阻塞。 |
| V. Verify In A Client Session | 通过。quickstart 含门禁/胸甲/JEI。 |

Phase 1 之后复检：仍通过。无 Complexity Tracking 例外。

## Project Structure

### Documentation (this feature)

```text
specs/005-item-backpack/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── item-backpack.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (expected touch set)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── MCFloatingBackpack.java                 # 注册物品/组件；去掉 Attachment
├── item/
│   ├── ModItems.java                       # DeferredRegister 物品
│   └── FloatingBackpackItem.java           # use / 胸甲可装备
├── inventory/
│   ├── BackpackInventory.java              # SIZE=36；读写 component
│   ├── BackpackContents.java               # component 记录类型（名可调）
│   ├── BackpackRef.java                    # 解析顺序
│   ├── BackpackClickLogic.java             # 容量感知
│   └── BackpackAttachments.java            # DELETE
├── network/                                # Payload 增加 ref；校验改绑
├── client/
│   ├── panel/                              # 4×9 布局；门禁后才 toggle
│   └── render/                             # 胸甲背负层
└── …

build.gradle                                # localRuntime JEI
src/main/resources/…/models|textures|recipes|lang
src/main/templates/META-INF/neoforge.mods.toml  # 无 required JEI
```

## Phase 0 / Phase 1

- Phase 0: [research.md](research.md)（已完成决策）
- Phase 1: [data-model.md](data-model.md)、[contracts/item-backpack.md](contracts/item-backpack.md)、[quickstart.md](quickstart.md)

下一步：`/speckit-tasks` 生成 `tasks.md`，再 `/speckit-implement`。
