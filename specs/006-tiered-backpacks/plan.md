# Implementation Plan: 分级浮动背包

**Branch**: `006-tiered-backpacks` | **Date**: 2026-10-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-tiered-backpacks/spec.md`

## Summary

在现有皮革浮动背包（36 格、物品组件、Overlay）上增加铁 / 金 / 钻石 / 下界合金四档。容量按物品等级决定，锻造台逐级升级并拷贝内容。面板行数随等级变化，放不下时在面板内滚动。五个等级共用一只低多边形背包物品模型，只换 32×32 贴图与配色；去掉胸甲皮革护甲外观，背部只画背包。

## Technical Context

**Language/Version**: Java 21，Minecraft 1.21.1，NeoForge 21.1.251

**Primary Dependencies**: 现有 Overlay、`BackpackContents` 数据组件、物品注册；原版锻造台配方接口；物品 JSON 模型。贴图用生图转像素，造型可用 Blender 定比例后再写成方块元素，不引入模型加载器。

**Storage**: 仍是物品上的 `BackpackContents`（格子列表 + 面板光标）。列表长度等于该等级容量，上限 108。

**Testing**: 等级容量与「升级后前缀格子保留」用 JUnit（不启动游戏）。五档外观、锻造、滚动与互拖用 `./gradlew runClient`（quickstart）。

**Target Platform**: 双端模组

**Project Type**: NeoForge 模组

**Performance Goals**: 打开时全量同步最多 108 格；之后仍按单格差分。面板滚动只改客户端偏移。

**Constraints**: Overlay 不占 Screen；服务端按该物品等级校验槽位下标；暂停菜单不画面板；无工具栏、无功能升级、无 Curios、无 OP 指令。

**Scale/Scope**: 5 个物品、4 条升级配方、1 个共用物品模型、5 套贴图、面板最多 12 行。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结果 |
| --- | --- |
| I. Overlay Not Screen | 通过。等级只改变 Overlay 行数，不新开 Screen。 |
| II. Server Owns Items | 通过。容量、升级结果与点击都由服务端按物品等级写入组件。 |
| III. One Feature At A Time | 通过。侧栏、升级效果、Curios、OP 指令不在本特征。 |
| IV. Vanilla Path Before Compat | 通过。锻造与容器互拖走原版；不接旅行者背包数据。 |
| V. Verify In A Client Session | 通过。quickstart 覆盖五档格子、锻造保留、图标/背负、滚动。 |

Phase 1 之后复检：仍通过。无 Constitution 例外。

## Project Structure

### Documentation (this feature)

```text
specs/006-tiered-backpacks/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── tiered-backpack.md
├── checklists/
│   └── requirements.md
└── tasks.md                 # /speckit-tasks 再写
```

### Source Code (expected touch set)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── item/
│   ├── BackpackTier.java              # 五档容量、下一档、贴图名
│   ├── FloatingBackpackItem.java      # 不再当皮革胸甲画；潜行装备、否则打开
│   └── ModItems.java                  # 五个物品 + 创造栏
├── inventory/
│   ├── BackpackContents.java          # 按等级长度规范化，升级时补空格
│   ├── BackpackInventory.java         # 实例容量，MAX=108
│   └── BackpackUpgrade.java           # 拷贝内容到下一档（配方与测试共用）
├── inventory/recipe/
│   └── BackpackUpgradeRecipe.java     # 锻造台：空模板槽 + 1 个材料
├── network/                           # 列表上限 108；槽位按该背包容量校验；协议版本 3
├── client/panel/                      # 行数来自当前视图；超出屏幕则滚轮滚动
└── client/render/BackpackBackLayer.java # 按等级贴图画包身/盖/肩带，不再画单位立方体

src/main/resources/
├── assets/mcfloatingbackpack/models/item/floating_backpack.json
├── assets/mcfloatingbackpack/models/item/<tier>_floating_backpack.json
├── assets/mcfloatingbackpack/textures/item/<tier>_floating_backpack.png   # ≥32×32
├── assets/mcfloatingbackpack/textures/entity/backpack_<tier>.png
├── data/mcfloatingbackpack/recipe/upgrade_*_floating_backpack.json
└── assets/.../lang/en_us.json、zh_cn.json
```

**Structure Decision**: 继续单模组源码树。等级是物品差异，不新开子项目。皮革物品 ID `leather_floating_backpack` 保持不变。

## Phase 0 / Phase 1

- Phase 0: [research.md](research.md)
- Phase 1: [data-model.md](data-model.md)、[contracts/tiered-backpack.md](contracts/tiered-backpack.md)、[quickstart.md](quickstart.md)

下一步：`/speckit-tasks` 生成 `tasks.md`，再实现。
