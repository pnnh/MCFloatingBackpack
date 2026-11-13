# Implementation Plan: 灯笼光源（接 LambDynamicLights）

**Branch**: `052-lantern-light` | **Date**: 2026-11-13 | **Spec**: [spec.md](./spec.md)

## Summary

下方工具区里的光源物品（灯笼等 10 种）通过 LambDynamicLights 变成"跟着玩家走的光"，做法与 Traveler's Backpack 相同（依据见 [research.md](./research.md)）：

1. **标签与亮度表**：`mcfloatingbackpack:tool_lights`（10 个原版物品）决定"哪些算光源"；`ToolLights`（纯 Java）是固定的亮度表，数值等于原版同一方块（灯笼等 15、末地烛 14、灵魂灯笼 10），标签里别的成员取自己方块的发光值（非方块物品 8）。
2. **互斥**：`ToolLightRules`（纯逻辑，仿 `TankPlacement` 的"重复种类"判定）：下方区至多 1 个光源；服务端（`BackpackClickPayload`）与客户端预览（`FloatingPanelInput`）各接一处，被拒时提示；与"一个标签管理器"的规则互相独立。
3. **读背包**：`PlayerLights.brightnessOf(Player)`（MC 层，不含任何别的模组的类型）：`BackpackRef.resolve` → 下方区第一个光源 → 亮度；只读。
4. **LDL 接入**（`compat/lambdynlights/`）：`BackpackDynamicLight implements DynamicLightsInitializer`（入口，注册 `EntityLuminance.Type` 与对 `EntityType.PLAYER` 的亮度）、`BackpackEntityLuminance`（只对玩家、受 `compat.modLevels` 的 `lambdynlights` 开关控制，返回 `PlayerLights.brightnessOf`）。入口由 `neoforge.mods.toml` 的 `[modproperties."${mod_id}"."yumi:entrypoints"]` 声明，LDL 装了才会加载。
5. **提醒**：没装 LDL 时，玩家第一次把光源放进下方区（被接受），客户端聊天提示一次（`FloatingPanelInput`，每会话一次）。
6. **构建**：`compileOnly` LDL API（mojmap 分类，`transitive = false`）与 Yumi 事件库；`localRuntime` LDL（仅客户端的开发运行，与 Sodium、Iris 同一处）；仓库加 maven.gegy.dev（只限 `dev.lambdaurora.lambdynamiclights` 组）。
7. **测试**：纯逻辑（亮度表与原版一致、关系不反；互斥）、资源（标签、语言键）、源码文本检查（入口声明、可选依赖、只在 compat 包引用 LDL、没有 mixin 与网络包与世界方块操作）；游戏里验证带 LDL 与不带 LDL 两种运行。

**不做**：不放方块、不做光照、不做 mixin、不做服务端代码、不加网络包与配置项、不做颜色。（最初的"服务端光源方块"方案已整个删除，原因见 spec 的 Clarifications。）

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；**可选**：LambDynamicLights 4.8.11+1.21.1（只编译期与开发运行时）

**Storage**: 无新增

**Testing**: JUnit 5；纯逻辑 + 资源 + 源码文本检查；LDL 的实际效果只能在客户端会话里验证

**Target Platform**: 客户端（LDL 是客户端模组）；服务端不涉及

**Constraints**: 宪法 II（物品规则仍在服务端；光不属于服务端状态）、III（一个 feature）、V（客户端会话验证）、VII（可选依赖、只在 `compat/<modid>/` 引用第三方类型、编译期与开发运行时依赖、模组专属适配可关）；协议 19、修订 3 不变

**Scale/Scope**: 3 个纯 Java 类 + 1 个 MC 层类（`PlayerLights`、`LightTags`）、2 个 compat 类、1 个标签 JSON、2 条语言键 ×2 语言、约 4 个测试类；改 `BackpackClickPayload`、`FloatingPanelInput`、`build.gradle`、`neoforge.mods.toml`

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面结构 | PASS |
| II. Server Owns Items | 互斥规则服务端校验；光完全在客户端（LDL），不是服务端状态 | PASS |
| III. One Feature At A Time | 只做光源物品对 LDL 的接入 | PASS |
| IV. Vanilla Path Before Compat | 这是对第三方模组的主动适配：放在 `compat/lambdynlights/`，没有它时一切照常，**不接 LDL 就不发光是明确的取舍**（用户决定） | PASS（取舍已记录） |
| V. Verify In A Client Session | 容器里带 LDL 与不带 LDL 两次运行；观感待真机 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 无新贴图、模型 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | LDL 可选、`compileOnly`/`localRuntime`、类型只在 compat 包、`compat.modLevels` 的 `lambdynlights` 可关、`DependencyRulesTest` 覆盖 | PASS |

设计后复核：无违规项。

## Project Structure

### Documentation (this feature)

```text
specs/052-lantern-light/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rules.md          # 亮度表、互斥、LDL 接入
│   └── resources.md      # 标签、语言键
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── light/ToolLights.java               # 纯：亮度表
├── light/ToolLightRules.java           # 纯：下方区至多 1 个光源、第一个光源
├── light/LightTags.java                # MC：标签键
├── light/PlayerLights.java             # MC：读背包、算亮度（不含别的模组的类型）
├── compat/lambdynlights/BackpackDynamicLight.java     # LDL 入口
├── compat/lambdynlights/BackpackEntityLuminance.java  # 对玩家的亮度
├── network/BackpackClickPayload.java   # 改：服务端互斥检查
└── client/panel/FloatingPanelInput.java # 改：客户端互斥预览与"需要 LDL"的提示

src/main/resources/data/mcfloatingbackpack/tags/item/tool_lights.json   # 新
src/main/resources/assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json     # 改
src/main/templates/META-INF/neoforge.mods.toml                           # 改：可选依赖与入口
build.gradle                                                              # 改：compileOnly / localRuntime / 仓库
```

## Complexity Tracking

无宪法违规项。
