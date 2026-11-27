# Implementation Plan: 五档快捷访问

**Branch**: `068-tiered-quick-access` | **Date**: 2026-11-26 | **Spec**: [spec.md](./spec.md)

**Input**: [spec.md](./spec.md)；决策全文 [docs/plan/quick-access.md](../../docs/plan/quick-access.md) 第 11 节

## Summary

把 067 的一件快捷访问拆成五档，并让满 6 行的网格在一般窗口里一次看完。读码后的做法（详见 [research.md](./research.md)）：

1. **一件物品类、五个注册名**（R1）：`QuickAccessItem` 带上 `BackpackTier`。皮革档仍注册为 `quick_access`，另外四档是 `iron_quick_access` 等。
2. **同档才生效**（R2、R3）：背包档位用现成的 `BackpackInventory.tier()`（按默认容量）。下方区放错档整次拒绝；任意一档都占「下方最多一件」的名额。其它区域不限、不生效。
3. **6 行、两档格子**（R5）：画面高度够 276 时仍是物品 32、格子 40；更矮则物品 24、格子 32。`Fit` 带上这一次的格子尺寸，绘制和命中都用它。三角仍是 3 像素。
4. **锻造用原版配方**（R6）：皮革档工作台配方不动。四档升级是 `minecraft:smithing_transform`，不走会复制背包内容的自定义配方。
5. **修订 8，协议仍是 25**（R8）：四种新物品会进背包。没有新包。

## Technical Context

**Language/Version**: Java 21，Minecraft 1.21.1，NeoForge 21.1.251

**Primary Dependencies**: 仅原版与 NeoForge；不新增 `compat/` 代码（宪法 VII）

**Storage**: 无新字段。四种新物品使 `DataRevision.CURRENT` 7 → 8。协议 `versioned("25")` 不动

**Testing**: JUnit 5 纯逻辑（放入判定、视口两档）+ 源码文本接线。游戏内验证见 [quickstart.md](./quickstart.md)

**Target Platform**: 双端（服务端判定放入与是否生效；客户端只预判和画网格）

**Project Type**: NeoForge 模组（单项目）

**Performance Goals**: 没按键时零额外开销。打开时仍一次扫下方区和主区域。选格子尺寸是一次整数除法

**Constraints**: 不升协议；不用 mixin / 反射；不改 `ToolSingleRules` 的签名；网格不暂停；视口至少 1×1 且完整留在画面内；两档格子的缝都是 8 像素（40−32 与 32−24）

**Scale/Scope**: 1 个纯判定、视口改一处、4 个新物品注册名、4 张锻造配方、5 张 16×16 图标、修订字面量约 10 处测试

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结论 | 说明 |
|---|---|---|
| I. Overlay Not Screen | ✅ | 仍用 067 的 `QuickAccessScreen`。不借用面板宿主，不暂停，不存放物品 |
| II. Server Owns Items | ✅ | 档位不符与「下方最多一件」在 `BackpackClickPayload` 再判一次。交换仍只在 `acquire` 之后写入。客户端预判只为少发一次注定失败的请求 |
| III. One Feature At A Time | ✅ | 只做 068。不改交换落点、不改标签、不把背包升档和快捷访问升档合成一步 |
| IV. Vanilla Path Before Compat | ✅ | 不碰第三方类型。联合背包不是按 B 打开的那一只（R2） |
| V. Verify In A Client Session | ⏳ | [quickstart.md](./quickstart.md)；未进游戏的不记成完成 |
| VI. Shared Resources Have A Budget | ✅ | 五张 16×16 物品平面图，替换或新增物品贴图，不进方块图集，不测 010 的 2048 预算（R7） |
| VII. Minimal Dependencies | ✅ | 无新依赖、无新 `compat/`。`DependencyRulesTest` 不用改 |

**Phase 1 后复核**：契约没有新包、没有新存档字段、没有第三方类型。档位来自现成的容量，不另存一份。受保护背包仍不打开 040 的确认窗口。无 Complexity Tracking 条目。

## Project Structure

### Documentation (this feature)

```text
specs/068-tiered-quick-access/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── items.md             # 五档、放入、提示
│   ├── grid.md              # 两档格子、6 行
│   └── recipes.md           # 工作台与四步锻造
└── tasks.md                 # /speckit-tasks 生成，本命令不写
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/quick/
│   ├── QuickAccessPlace.java            # 新：错档 / 重复，无 Minecraft 类型
│   └── QuickViewport.java               # 改：默认 6 行，两档格子，Fit 带尺寸
├── inventory/BackpackInventory.java     # 改：hasQuickAccess 改为同档才算
├── inventory/revision/DataRevision.java # 改：CURRENT = 8
├── item/QuickAccessItem.java            # 改：带 BackpackTier
├── item/ModItems.java                   # 改：五档注册，创造栏按档排列
├── network/BackpackClickPayload.java    # 改：错档与重复两句
├── network/QuickSwapPayload.java        # 改：同档才放行，提示带上档名
├── client/quick/QuickAccessOpener.java  # 改：同上
├── client/quick/QuickNotices.java       # 改：提示可以带物品名
├── client/quick/QuickAccessScreen.java  # 改：绘制和命中用 Fit 上的格子尺寸
└── client/panel/FloatingPanelInput.java # 改：放入预判与服务端同一条

src/main/resources/
├── assets/mcfloatingbackpack/lang/zh_cn.json, en_us.json
├── assets/mcfloatingbackpack/models/item/   # 四份新模型；皮革沿用 quick_access.json
├── assets/mcfloatingbackpack/textures/item/ # 五张 16×16，皮革覆盖现有金边图
└── data/mcfloatingbackpack/
    ├── recipe/upgrade_*_quick_access.json   # 四份 smithing_transform
    └── tags/item/never_trash.json           # 加上四档

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/quick/QuickAccessPlaceTest.java  # 新
├── inventory/quick/QuickViewportTest.java     # 改：6 行与两档
├── inventory/revision/DataRevisionTest.java   # 改：8
└── resources/QuickAccessWiringTest.java       # 改
```

历史接线测试里写死 `CURRENT = 7` 的，按 [research.md](./research.md) R8 改成 8。协议断言仍是 `"25"`。

**Structure Decision**: 单项目。放入判定放在 `inventory/quick`，测试能直接加载。物品沿用 `item/`。不新加网络包，所以 `network/` 只改两处现有判定。

## Complexity Tracking

无宪法违反，无条目。

## 实现顺序（供 /speckit-tasks 参考）

1. 物品带档、五个注册名、文案、`never_trash`、创造栏、修订 8，以及 R8 列出的字面量。
2. `QuickAccessPlace` 与两处放入（预判 + 点击包），含错档和重复的提示。
3. `hasQuickAccess` 改为同档才算；打开与交换的提示点名这一档。
4. 视口两档、`Fit` 带尺寸、界面改用它；改 `QuickViewportTest`。
5. 四份锻造配方、五张图标与模型。
6. 游戏内走 [quickstart.md](./quickstart.md)，写 DELIVERY，改 README 与规划状态。
