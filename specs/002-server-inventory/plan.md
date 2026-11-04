# Implementation Plan: 服务端库存与面板内取放

> **【005 修订（2026-11-04 对齐）】** 本文描述的"挂在玩家 Attachment 上、固定 27 格的全局库存"已被 005 取代：库存现在保存在**物品实例**的数据组件上（`BackpackContents`），`BackpackAttachments` 已删除，且不迁移旧数据；容量由等级决定（36/54/72/90/108，见 006）。服务端权威、网络同步、点击取放规则（FR-003～FR-005、FR-009）的意图仍然成立。下文为 002 当时的原文，仅供追溯。

**Branch**: `002-server-inventory` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-server-inventory/spec.md`

## Summary

在 M1 浮动面板之上增加服务端权威的 27 格玩家背包、网络同步，以及面板内点击取放。无容器界面时通过主手与背包槽交换完成存取。不实现与已打开原版物品栏/箱子的光标互拖（M3）。

## Technical Context

**Language/Version**: Java 21，Minecraft 1.21.1，NeoForge 21.1.251

**Primary Dependencies**: NeoForge Attachments（或等价玩家持久化）、Payload 网络注册、既有客户端 Overlay。

**Storage**: 玩家 Attachment / 保存数据中的 `ItemStackHandler`（27 槽）+ 可选面板 carried 栈；写入玩家持久化。

**Testing**: 堆叠/交换纯逻辑可 JUnit；存盘与点击用 `./gradlew runClient`（及可选 `runServer`）按 quickstart。

**Target Platform**: 双端模组（专用服务器需安装）

**Project Type**: NeoForge 模组

**Performance Goals**: 全量同步仅在登录/大变更时；日常点击用单槽或差分包，避免每 tick 刷库存。

**Constraints**: 不 `setScreen` 开背包。不读/写原版 `AbstractContainerMenu` carried（M3）。服务端权威。

**Scale/Scope**: 每玩家 27 槽 + 面板光标；无升级、无第三方背包。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结果 |
| --- | --- |
| I. Overlay Not Screen | 通过。继续 M1 Overlay；点击走自定义包。 |
| II. Server Owns Items | 通过。Attachment + 服务端校验，禁止纯客户端定案。 |
| III. One Feature At A Time | 通过。明确排除 M3 跨 GUI 互拖。 |
| IV. Vanilla Path Before Compat | 通过。无第三方背包适配。 |
| V. Verify In A Client Session | 通过。quickstart 含存取与重进检查。 |

Phase 1 之后复检：仍通过。无 Complexity Tracking 例外。

## Project Structure

### Documentation (this feature)

```text
specs/002-server-inventory/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── backpack-sync.md
├── checklists/
│   └── requirements.md
└── tasks.md                 # 实现前由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── MCFloatingBackpack.java              # 注册 attachment、网络、共通
├── inventory/
│   ├── BackpackInventory.java           # 27 槽 handler + 序列化
│   ├── BackpackAttachments.java         # 玩家 attachment 注册
│   └── BackpackClickLogic.java          # 纯点击/堆叠规则（可单测）
├── network/
│   ├── OpenSyncPayload.java             # 登录/打开时全量同步
│   ├── SlotUpdatePayload.java           # 单槽/光标更新
│   └── ClickSlotPayload.java            # 客户端 → 服务端点击
└── client/
    ├── panel/
    │   ├── FloatingPanelRenderer.java   # 绘制真实 ItemStack
    │   ├── FloatingPanelInput.java      # 槽位点按 → 发 ClickSlotPayload
    │   └── ClientBackpackView.java      # 客户端镜像库存 + 光标
    └── FloatingPanelEvents.java         # 接线

src/test/java/pnnh/mcfloatingbackpack/inventory/
└── BackpackClickLogicTest.java
```

**Structure Decision**: 库存与网络放在共通包；客户端只持镜像视图。点击规则抽成无 Minecraft 强耦合的可测逻辑（ItemStack 比较可用测试夹具或薄封装）。

## Complexity Tracking

无。
