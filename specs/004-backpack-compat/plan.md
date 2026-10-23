# Implementation Plan: 精妙/旅行者背包兼容

**Branch**: `004-backpack-compat` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

## Summary

在 M3 统一 `AbstractContainerMenu.getCarried()` 通路之上，增加无硬依赖的软检测兼容层，把精妙/旅行者等已知背包界面显式纳入「容器打开」判定与文档；`build.gradle` 用属性开关的 `localRuntime` 便于联调；`mods.toml` 声明 optional。

## Technical Context

**Language/Version**: Java 21，MC 1.21.1，NeoForge 21.1.251  
**Primary Dependencies**: 既有 Overlay + MenuBridge；无 compile 第三方  
**Storage**: 不变（玩家 Attachment + 打开菜单 carried）  
**Testing**: 软检测单测 + `./gradlew test`；有 jar 时 `runClient -PcompatRuntime`  
**Constraints**: Overlay Not Screen；Vanilla Path Already Done；optional only  

## Constitution Check

| 原则 | 结果 |
| --- | --- |
| I. Overlay Not Screen | 通过 |
| II. Server Owns Items | 通过（沿用 M3） |
| III. One Feature At A Time | 通过（仅热门背包兼容） |
| IV. Vanilla Path Before Compat | 通过（M3 已完成） |
| V. Verify In A Client Session | 通过（quickstart；无 jar 时单测 + 编译） |

## Project Structure

```text
specs/004-backpack-compat/ ...
src/main/java/pnnh/mcfloatingbackpack/
  compat/ContainerScreenCompat.java   # 软检测 + isOpenContainerScreen
  client/FloatingPanelEvents.java      # Opening merge 用兼容判定
  client/panel/FloatingPanelInput.java # screenOpen 用兼容判定
  client/panel/FloatingPanelRenderer.java # cursor 用兼容判定
build.gradle                           # -PcompatRuntime localRuntime
src/main/templates/META-INF/neoforge.mods.toml  # optional deps
```

## Complexity Tracking

无额外架构复杂度；不引入 per-mod 适配器类硬引用。
