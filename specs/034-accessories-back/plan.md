# Implementation Plan: Accessories 后背槽兼容

**Branch**: `034-accessories-back` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

## Summary

在 032/033 的"背着的来源列表"里加第三个来源：Accessories 的 `back` 槽（顺序号 20，排在胸甲和 Curios 后面）。读 Accessories 1.1.0-beta.54 的 jar 与源码得到的设计（见 [research.md](./research.md)）：

1. **数据**：只加 `data/accessories/tags/item/back.json`（`accessories:back` 标签，5 个档位）；**不需要槽位分配**（Accessories 默认就给玩家 `back` 槽）。
2. **代码只在 `compat/accessories/`**：`AccessoriesCompat.register()`（主类只在 `ModList.isLoaded("accessories")` 时调用）、`AccessoriesWornSource`（实现 `WornSource`）、仅客户端的 `AccessoriesClientCompat` 与 `BackpackAccessoryRenderer`。
3. **读写**：`AccessoriesCapability.get(entity)`（可能为 `null`）→ `getContainers().get("back")`；只看真实栈；隐藏渲染 = `!container.shouldRender(slot)`；写回用 `getAccessories().setItem`。**不需要显式同步**：Accessories 每个 tick 自己比较前后栈并同步、触发回调（D4）。
4. **渲染抑制（和 033 的关键区别）**：注册我们自己的 `BackpackAccessoryRenderer`（**不是** `EmptyRenderer`，因为 `forceNullRenderReplacement` 会把它换回默认渲染器）。它每帧读 `modLevels`：FULL 时 `shouldRender` 返回 `false`（背上由我们的 `BackpackBackLayer` 画），否则转给 `DefaultAccessoryRenderer.INSTANCE`。在 `FMLClientSetupEvent` 里**每个档位注册一个新实例**，早于首次资源重载（D5）。
5. **`modLevels`**：`active()` 在每次查找时读 `CompatConfig.policy().specificAdaptation("accessories")`。
6. **线路不变**：`BackpackRef.Source` 末尾追加 `ACCESSORIES(true)`（序号 5），协议版本仍是 16。
7. **构建与规则**：`compileOnly("io.wispforest:accessories-neoforge:1.1.0-beta.54+1.21.1") { transitive = false }`（仓库 `maven.wispforest.io`，已确认存在）；`DependencyRules` 的第三方坐标组加 `io.wispforest`；`neoforge.mods.toml` 加可选依赖 `accessories`。

不新增网络包、语言键、贴图、配方、设置项。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；**Accessories 1.1.0-beta.54+1.21.1 仅作 `compileOnly` 与 `localRuntime`（开发运行时，已有，含 owo-lib）**；不进发布 jar，不是必需依赖

**Storage**: 无新增

**Testing**: JUnit 5 纯文本/资源检查：`AccessoriesBackResourcesTest`（标签文件内容、5 个档位、路径；没有槽位分配文件）；`BackpackRefTest` 增补（`ACCESSORIES` 序号 5、`worn`、已有序号不变）；`DependencyRulesTest` 已有检查覆盖依赖规则。来源、渲染器与 Accessories 的交互只能在游戏里验证

**Target Platform**: 客户端 + 服务端；开发机 macOS

**Constraints**: 宪法 VII（第三方类型只在 `compat/<modid>/`、构建里只 `compileOnly`/`localRuntime`、可选依赖、`modLevels` 在查询时生效）；协议 16 不变；Accessories 没装时模组零影响；渲染器类只在客户端加载

**Scale/Scope**: 新增 `compat/accessories/`（4 个类）、1 个数据文件、1 个测试类；改 `BackpackRef`（一个枚举常量）、`MCFloatingBackpack`（一行注册）、`ClientSetup`（一个客户端初始化监听）、`build.gradle`、`neoforge.mods.toml`、`DependencyRules`

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面；渲染仍是我们自己的一层 | PASS |
| II. Server Owns Items | "按 B 打开哪一只"仍由服务端 `resolve` 决定，客户端引用只在与解析结果一致时才采用 | PASS |
| III. One Feature At A Time | 只做 Accessories 的 `back` 槽；其它槽位都不做 | PASS |
| IV. Vanilla Path Before Compat | 胸甲（原版）仍是第一个来源，顺序在前 | PASS |
| V. Verify In A Client Session | quickstart 要求放入/取出/背上没有默认外观/顺序/隐藏渲染/`modLevels`/没装 Accessories/保存重进/`forceNullRenderReplacement` | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图；来源查询是一次能力查找；渲染器 `shouldRender` 只读一次配置 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | Accessories 仅 `compileOnly` + `localRuntime`；类型只在 `compat/accessories/`；mods.toml 可选依赖；`modLevels` 的 `accessories` 在查询时生效；属第二档（知名模组，积极支持 FULL） | PASS |

## Project Structure

```text
src/main/java/pnnh/mcfloatingbackpack/
├── compat/accessories/
│   ├── AccessoriesCompat.java          # 新：register()，两端，只在 Accessories 已加载时被调用
│   ├── AccessoriesWornSource.java      # 新：WornSource<LivingEntity, ItemStack>，顺序 20
│   ├── AccessoriesClientCompat.java    # 新（仅客户端）：registerRenderers()，在 FMLClientSetupEvent 里
│   └── BackpackAccessoryRenderer.java  # 新（仅客户端）：每帧读 modLevels 的"什么都不画"的渲染器
├── client/ClientSetup.java             # 改：FMLClientSetupEvent 里（Accessories 已加载时）注册渲染器
├── inventory/BackpackRef.java          # 改：Source 末尾追加 ACCESSORIES(true)
└── MCFloatingBackpack.java             # 改：构造函数里 isLoaded("accessories") 时注册

src/main/resources/data/accessories/tags/item/back.json     # 新
src/main/templates/META-INF/neoforge.mods.toml              # 改：可选依赖 accessories
build.gradle                                                 # 改：仓库 + compileOnly（transitive = false）

src/test/java/pnnh/mcfloatingbackpack/
├── resources/AccessoriesBackResourcesTest.java   # 新
├── rules/DependencyRules.java                    # 改：第三方坐标组加 io.wispforest
└── inventory/BackpackRefTest.java                # 改
```

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。

## Risks

- **编译时缺 owo / endec 类型**：`transitive = false` 之后，API 类的签名若引用了它们，javac 会报；按需单独 `compileOnly`（实现时验证）。
- **渲染器注册时机**：必须早于 Accessories 的首次资源重载（`FMLClientSetupEvent` 的 `enqueueWork`）；晚了会回落到默认渲染器——quickstart A2 直接看得出来。
- **同一个渲染器实例被多个物品共用**会被 Accessories 警告并包一层：每个档位 `new` 一个。
- **Beta 版 API**：Accessories 1.1.0-beta.54 是 beta，以后版本可能改 API；我们只用 `AccessoriesCapability`、`AccessoriesContainer`、`AccessoryRenderer`、`AccessoriesRendererRegistry`、`DefaultAccessoryRenderer`、`SlotReference` 几个公开 API。
- **`modLevels` 在专用服务器上不生效**（已知限制，用户已接受）。
- **写回的副作用**：与 033 相同（每次变化 Accessories 在下一个 tick 同步整只栈并触发回调）。
