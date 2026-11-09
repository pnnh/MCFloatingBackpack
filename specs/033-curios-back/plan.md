# Implementation Plan: Curios 后背槽兼容

**Branch**: `033-curios-back` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

## Summary

在 032 的"背着的来源列表"里加第二个来源：Curios 官方 9.x 的 `back` 槽（顺序号 10，排在胸甲后面）。读 Curios 9.5.1 的 jar 与源码得到的设计（见 [research.md](./research.md)）：

1. **数据（零代码依赖）**：`data/curios/tags/item/back.json` 把 5 个档位加进 `curios:back` 标签；`data/mcfloatingbackpack/curios/entities/player_back.json` 无条件给玩家一个 `back` 槽（官方包不给任何实体分配槽位）。
2. **代码只在 `compat/curios/`**：`CuriosCompat.register()`（主类只在 `ModList.isLoaded("curios")` 时调用）与 `CuriosWornSource`（实现 032 的 `WornSource`）；Curios 类型只出现在这两个文件里，Curios 没装时不被加载。
3. **读写**：`CuriosApi.getCuriosInventory(entity)` → `getStacksHandler("back")`；只看真实栈，未激活的格子当空，隐藏渲染照抄 Curios 渲染层的判断；写回用 `getStacks().setStackInSlot`。**不需要显式同步**：Curios 每个 tick 自己比较前后栈并同步给客户端、发变化事件（D4）。
4. **`modLevels`**：`active()` 在每次查找时读 `CompatConfig.policy().specificAdaptation("curios")`。
5. **线路不变**：`BackpackRef.Source` 末尾追加 `CURIOS(true)`（序号 4），协议版本仍是 16。
6. **构建与规则**：`compileOnly` Curios 的 `:api` jar（`maven.theillusivec4.top`，已确认存在）；`DependencyRules` 的第三方坐标组加上 `top.theillusivec4.curios`；`neoforge.mods.toml` 加一个可选依赖 `curios`。

不新增网络包、语言键、贴图、配方、设置项。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；**Curios 9.5.1+1.21.1 仅作 `compileOnly`（API jar）与 `localRuntime`（开发运行时，已有）**；不进发布 jar，不是必需依赖

**Storage**: 无新增（背包栈存在 Curios 的槽里，随 Curios 的玩家数据保存）

**Testing**: JUnit 5 纯文本/资源检查：`CuriosBackResourcesTest`（标签与实体文件的内容、5 个档位、路径）；`BackpackRefTest` 增补（`CURIOS` 序号 4、`worn`、已有序号不变）；`DependencyRulesTest` 已有检查覆盖"Curios 类型只在 `compat/curios/`""构建里只 `compileOnly`/`localRuntime`""mods.toml 依赖都是可选"，常量更新后自动覆盖本 feature。`CuriosWornSource` 与 Curios 的交互只能在游戏里验证

**Target Platform**: 客户端 + 服务端；开发机 macOS

**Constraints**: 宪法 VII（第三方类型只在 `compat/<modid>/`、构建里只 `compileOnly`/`localRuntime`、可选依赖、`modLevels` 控制）；协议版本 16 不变；Curios 没装时模组零影响；来源查询每玩家每帧一次，必须便宜

**Scale/Scope**: 新增 `compat/curios/`（2 个类）、2 个数据文件、1 个测试类；改 `BackpackRef`（一个枚举常量）、`MCFloatingBackpack`（一行注册）、`build.gradle`、`neoforge.mods.toml`、`DependencyRules`

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面；渲染仍是玩家实体上我们自己的一层 | PASS |
| II. Server Owns Items | "按 B 打开哪一只"仍由服务端 `resolve` 决定，客户端引用只在与解析结果一致时才采用 | PASS |
| III. One Feature At A Time | 只做 Curios 的 `back` 槽；Accessories（034）、其它槽位都不做 | PASS |
| IV. Vanilla Path Before Compat | 原版（胸甲）仍是第一个来源，顺序在前；Curios 是第二档的兼容 | PASS |
| V. Verify In A Client Session | quickstart 要求放入/取出/顺序/隐藏渲染/`modLevels`/没装 Curios/保存重进 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图；来源查询是一次能力查找；写回只在背包内容变化时发生 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | Curios 仅 `compileOnly` + `localRuntime`；类型只在 `compat/curios/`；mods.toml 可选依赖；`modLevels` 的 `curios` 在查询时生效；Curios 属第二档（知名模组，积极支持 FULL） | PASS |

## Project Structure

```text
src/main/java/pnnh/mcfloatingbackpack/
├── compat/curios/
│   ├── CuriosCompat.java          # 新：register()，只在 Curios 已加载时被调用
│   └── CuriosWornSource.java      # 新：WornSource<LivingEntity, ItemStack>，顺序 10
├── inventory/BackpackRef.java     # 改：Source 末尾追加 CURIOS(true)
└── MCFloatingBackpack.java        # 改：构造函数里 isLoaded("curios") 时注册

src/main/resources/
├── data/curios/tags/item/back.json                              # 新
├── data/mcfloatingbackpack/curios/entities/player_back.json     # 新
src/main/templates/META-INF/neoforge.mods.toml                   # 改：可选依赖 curios
build.gradle                                                      # 改：仓库 + compileOnly :api

src/test/java/pnnh/mcfloatingbackpack/
├── resources/CuriosBackResourcesTest.java   # 新
├── rules/DependencyRules.java               # 改：第三方坐标组加 top.theillusivec4.curios
└── inventory/BackpackRefTest.java           # 改
```

## Complexity Tracking

> 无需填写：Constitution Check 没有违反项。

## Risks

- **写回触发的 Curios 副作用**（research D4）：背包内容每次变化都会让 Curios 同步整只栈并发一次 `CurioChangeEvent`；量级与原版装备槽相当，要在游戏里看一眼没有卡顿或刷屏日志。
- **Curios 在专用服务器上的 `modLevels`**：不生效（已知限制，用户已接受）。
- **整合包重复分配后背槽**：Curios 只保留一个，不叠加；没配置过的整合包玩家多出一个后背槽（已接受）。
- **Adorned**：声称同 API，但不单独验证。
- **`./gradlew runClient` 启动被占用的 `run/`**：验证前先 `pgrep`。
