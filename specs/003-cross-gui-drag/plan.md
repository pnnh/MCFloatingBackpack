# Implementation Plan: 与原版物品栏/箱子互拖

**Branch**: `003-cross-gui-drag` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

## Summary

容器 GUI 打开时，浮动背包槽位点击改用 `AbstractContainerMenu.getCarried()` 作为统一光标；服务端写回 carried 并 `broadcastChanges`。客户端悬停高亮；打开容器时把残留面板光标并入菜单 carried。

## Technical Context

**Language/Version**: Java 21，MC 1.21.1，NeoForge 21.1.251  
**Primary Dependencies**: 既有 Overlay、Attachment、Click payload；`AbstractContainerMenu` carried API  
**Storage**: 仍用 M2 Attachment；carried 用当前打开的 containerMenu  
**Testing**: `./gradlew test` + `runClient` quickstart  
**Constraints**: 不 setScreen；不接第三方背包；服务端权威  

## Constitution Check

| 原则 | 结果 |
| --- | --- |
| I. Overlay Not Screen | 通过 |
| II. Server Owns Items | 通过（菜单 carried + backpack attachment） |
| III. One Feature At A Time | 通过（仅原版标准容器） |
| IV. Vanilla Path Before Compat | 通过 |
| V. Verify In A Client Session | 通过（quickstart） |

## Project Structure

```text
specs/003-cross-gui-drag/ ...
src/main/java/pnnh/mcfloatingbackpack/
  network/BackpackClickPayload.java     # screenOpen 时用 menu carried
  client/panel/FloatingPanelRenderer.java  # hover + cursor from menu
  client/panel/FloatingPanelInput.java     # 可选：开屏时 merge 请求
  network/MergePanelCursorPayload.java     # 可选开屏合并
```

## Complexity Tracking

无。
