# Implementation Plan: 背包配方难度调整

**Branch**: `023-recipe-difficulty` | **Date**: 2026-11-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-recipe-difficulty/spec.md`

## Summary

只改**配方数据**与**升级配方类**，不动任何玩法、界面、协议里的物品逻辑。研究（见 [research.md](./research.md)，读原版 1.21.1 数据与源码得出）的方案：

1. **升级配方加"模板"**：`BackpackUpgradeRecipe` 增加一个 `template` 成分（JSON 字段 `template`，**可选**，缺省 = 空 = 沿用 006 的"模板格必须为空"，保证没改过的数据包不被破坏）。`matches` 与 `isTemplateIngredient` 都按它判断；原版锻造台在取出结果时对模板、基础、材料各消耗 1 个，不需要额外代码。`isIncomplete` 不因模板为空而判为不完整。
2. **四条升级配方改数据**（`upgrade_*_floating_backpack.json`）：材料依次为铁块、金块、**钻石块、下界合金块**；模板分配见下表（决策见 research D2）：

   | 升级 | 模板（原版） | 材料 | 模板的生存来源（读自原版战利品表） | 复制所需（原版配方） |
   |---|---|---|---|---|
   | 皮革 → 铁 | `sentry_armor_trim_smithing_template`（哨兵） | 铁块 | 掠夺者前哨站箱子，每个箱子约 1/4 的概率出现，一次给 2 个 | 7 钻石 + 1 模板 + 1 圆石 |
   | 铁 → 金 | `dune_armor_trim_smithing_template`（沙丘） | 金块 | 沙漠神殿箱子，每个箱子约 1/7，一次给 2 个 | 7 钻石 + 1 模板 + 1 砂岩 |
   | 金 → 钻石 | `eye_armor_trim_smithing_template`（眼） | 钻石块 | 要塞图书馆箱子**必出**，要塞走廊箱子约 1/10 | 7 钻石 + 1 模板 + 1 末地石（要去末地） |
   | 钻石 → 下界合金 | `netherite_upgrade_smithing_template`（下界合金升级） | 下界合金块 | 堡垒遗迹的宝藏箱子**必出**，其它堡垒箱子约 1/10 | 7 钻石 + 1 模板 + 1 下界岩 |

3. **皮革背包配方**改为 `皮革 线 皮革 / 皮革 箱子 皮革 / 铁锭 皮革 铁锭`（5 皮革 + 2 铁锭 + 1 线 + 1 箱子）。
4. **JEI**：`FloatingBackpackJeiPlugin` 里 006-JEI 修复中新注册的锻造扩展，`setTemplate` 从"留空"改为添加 `recipe.template()`。
5. **配方同步格式变了**：`BackpackUpgradeRecipe.STREAM_CODEC` 多编码一个成分，配方通过原版配方同步包发给客户端，新旧版本互不兼容，所以**网络协议版本从 `"10"` 升到 `"11"`**，不匹配的客户端连不上，而不是静默解码错位（决策 D4）。
6. **测试与文档**：新增纯文本资源测试（项目测试类路径没有 Minecraft 类，沿用 `TrashCanResourcesTest` 的做法）校验五个配方 JSON；更新 006 spec（【023 修订】）与 `docs/MCFloatingBackpack.md`。

范围：只做配方与材料；不新增物品、贴图、模型；不改容量、界面、保存格式；不动垃圾桶、标签管理器配方；创造栏与升级时内容保留的逻辑一行不改。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（`SmithingRecipe`、`Ingredient`、`RecipeSerializer`、`SmithingMenu`）；JEI 仅在已有的可选插件里（`compileOnly`，宪法 VII，不新增依赖）

**Storage**: 无新增持久数据。配方是数据包文件；旧存档里的背包物品与内容不受影响

**Testing**: JUnit 5 纯文本资源测试（配方 JSON 的字段与值）；锻造台的真实匹配/消耗行为只能在客户端会话里验证（宪法 V）

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（数据 + 配方类 + 可选 JEI 插件）

**Performance Goals**: 无（配方匹配每次最多比较 3 个成分）

**Constraints**:
- 模板缺省保持 006 的"模板为空"语义，旧数据不报错（决策 D3）
- 内容保留逻辑（`assemble` 里的 `BackpackContents.fit`）不动
- 不新增任何贴图（宪法 VI）；协议版本升到 `"11"`

**Scale/Scope**: 改动 `BackpackUpgradeRecipe`、`FloatingBackpackJeiPlugin`、`BackpackNetwork`（只改版本号）、5 个配方 JSON、新增 1 个测试类、文档若干；约 5 个源文件 + 5 个数据文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面 | PASS |
| II. Server Owns Items | 配方匹配、产出、内容保留都在服务端（原版锻造菜单）；客户端只显示 | PASS |
| III. One Feature At A Time | 只做配方难度；006 的容量、升级内容保留不动；不借此夹带新物品或新玩法 | PASS |
| IV. Vanilla Path Before Compat | 只用原版材料与原版模板；JEI 展示沿用已有可选插件 | PASS |
| V. Verify In A Client Session | quickstart 逐项要求在锻造台与工作台里实际操作（16 个正反用例、内容保留、旧存档、JEI）；Windows/Linux 无法验证，登记为未验证 | PASS（已规划） |
| VI. Shared Resources Have A Budget | 不新增贴图、模型、物品；配方是数据文件，不进图集；实现完成时仍按惯例度量一次图集（先 `pgrep`，度量不了就写"未度量"） | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新增依赖；模板全是原版物品；JEI 代码仍只在 `compat/jei/`，由 JEI 自己的插件扫描进入 | PASS |

**Re-check after Phase 1 design**：没有违反项。实现阶段要盯住的风险见 research 的"风险"一节：①`Ingredient.EMPTY` 在 JSON 与网络编码里的行为要实测；②模板来源概率读自战利品表、没有游戏内验证。

## Project Structure

### Documentation (this feature)

```text
specs/023-recipe-difficulty/
├── plan.md              # 本文件
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── recipe-format.md # 升级配方的数据格式与五条配方的最终内容
└── tasks.md             # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/recipe/BackpackUpgradeRecipe.java     # + template 成分、matches/isTemplateIngredient/codec/网络编码
├── compat/jei/FloatingBackpackJeiPlugin.java       # setTemplate 显示模板
└── network/BackpackNetwork.java                    # 协议版本 "10" → "11"
src/main/resources/data/mcfloatingbackpack/recipe/
├── leather_floating_backpack.json                  # 5 皮革 + 2 铁锭
└── upgrade_{iron,gold,diamond,netherite}_floating_backpack.json   # + template，材料改块
src/test/java/pnnh/mcfloatingbackpack/resources/
└── BackpackRecipesResourcesTest.java               # 新增（纯文本）
docs/MCFloatingBackpack.md、specs/006-tiered-backpacks/spec.md    # 同步说明
```

**Structure Decision**: 沿用现有单模组结构；不新增包，不新增 Minecraft 无关的纯类（配方规则本身极小，直接放在配方类里）。

## Complexity Tracking

无违反项，不需要填写。
