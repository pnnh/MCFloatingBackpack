# Implementation Plan: 牛奶管、蜂蜜管与排放到容器

**Branch**: `050-milk-honey-containers` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

## Summary

在 049 的基础上加两根管子，并给五根管子都加上"潜行右键 = 排放到容器"。**不新增网络包、存储字段、配置项与依赖，协议号仍 19，数据修订仍 2。** 四条入口，全部走物品自己的钩子，不用事件：

1. **潜行右键 → 排放**：`PipeItem.use()`（049 里潜行时 `pass`，现在改成排放）。方块的 `useOn` 与实体的 `interactLivingEntity` 在潜行时都返回 `pass`，原版随后会调 `use`（读源码确认，D1）。
2. **牛奶管右键牛、山羊 → 吸收**：`PipeItem.interactLivingEntity()`（实体自己的 `mobInteract` 对管子返回 `pass` 之后才轮到它）。
3. **蜂蜜管右键蜂巢 → 吸收**：`PipeItem.useOn()`（蜂巢的 `useItemOn` 对管子返回 `PASS_TO_DEFAULT_BLOCK_INTERACTION`，之后轮到物品的 `useOn`）。蜂巢的世界效果用原版**公开**的 `releaseBeesAndResetHoneyLevel` / `resetHoneyLevel` 与 `CampfireBlock.isSmokeyPos`；只有"激怒附近蜜蜂"那一小段原版是 `private`，照原版的写法重写（D2）。
4. **左键牛奶管、蜂蜜管**：沿用 049 的取消事件（不挖方块），不放置，给一条经冷却的提示。

判定继续写成纯逻辑 `PipePlanner`：单位改为"这种液体的一份"（`FluidKind.perUseMb()`），新增 `discharge` 与三条提示；世界（容器、牛、蜂巢）改成功才提交液量。空容器的选择顺序（先背包主区域、后物品栏）也是纯逻辑 `ContainerPicker`，可单元测试（D3、D4）。

## Technical Context

**Language/Version**: Java 21 · NeoForge 21.1.251 / Minecraft 1.21.1

**Primary Dependencies**: 无新增；PNC 仍只是开发运行时依赖

**Storage**: 无新增（液量沿用 048 的 `fluids`；背包主区域的空容器被取走用既有的 `saveInventory`）

**Testing**: JUnit 5：`PipePlannerTest` 扩展（单位、排放判定、牛奶/蜂蜜、左键提示）、`ContainerPickerTest`（先主区域后物品栏）、`PipeKindTest` 扩展、`PipeResourcesTest` 扩展（5 根）、`PipeWiringTest` 扩展（四条入口的顺序、潜行、成品容器、`sendFullSync`、无新包）；容器里游戏内验证（牛、山羊、小牛、蜂巢、篝火、PNC 的桶）

**Target Platform**: Minecraft 客户端 + 服务端（NeoForge）

**Project Type**: 游戏模组（单工程，服务端权威）

**Performance Goals**: 一次操作 O(背包主区域格数 + 36)；无逐帧开销

**Constraints**: 无新增配置项、网络包、依赖；协议号 19、数据修订 2 不变；011～049 的测试除提示文字相关的断言外不改

**Scale/Scope**: 2 个物品、1 个新纯逻辑类、改动约 6 个既有文件

## Constitution Check

| 原则 | 评估 |
|---|---|
| I. Overlay Not Screen | 不涉及界面 PASS |
| II. Server Owns Items | 容器的消耗、成品的发放、液量、世界效果全在服务端；客户端只对牛、蜂巢的"可能成功"返回成功以便挥手（不改任何东西）PASS |
| III. One Feature At A Time | 只做牛奶管、蜂蜜管与排放到容器；把容器液体装回罐不做 PASS |
| IV. Vanilla Path Before Compat | 牛、山羊、蜂巢、桶、瓶全是原版；原油的桶用流体自己的 `getBucket()`，不引用任何模组的类 PASS |
| V. Verify In A Client Session | quickstart 要求容器里验证（含 PNC 的桶），未验证项如实记录 PASS（实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 新增 2 张 16×16 物品贴图进共享图集：前后各量一次 `check_atlas_budget.sh` PASS（待实现时度量） |
| VII. Minimal Dependencies | 无新依赖；PNC 仍只作 `localRuntime` PASS |

## Research

完整记录见 [research.md](./research.md)。要点：

- **D1 入口顺序**（读 `Player.interactOn`、`BeehiveBlock.useItemOn`、`ServerPlayerGameMode`）：实体先 `entity.interact`（牛的 `mobInteract` 只认桶，对管子返回 `pass`）再 `itemstack.interactLivingEntity`；方块先 `useItemOn`（蜂巢只认剪刀与玻璃瓶，其余走默认）再物品的 `useOn`；两者都 `pass` 时客户端再发 `use`。**潜行时**原版跳过方块的 `useItemOn` 但仍调物品的 `useOn`，实体交互不看潜行——所以管子在 `useOn` 与 `interactLivingEntity` 里**自己判断潜行并返回 `pass`**，排放统一落在 `use()`。
- **D2 蜂巢**：`releaseBeesAndResetHoneyLevel`、`resetHoneyLevel`、`CampfireBlock.isSmokeyPos` 都是公开的；`angerNearbyBees`、`hiveContainsBees` 是 `private`，重写它们（各几行，AABB 膨胀 (8,6,8)，对没有目标的蜜蜂随机指定附近一名玩家，`BeehiveBlockEntity.isEmpty()` 判断有没有蜜蜂）。蜂蜜瓶取蜜的声音是 `BOTTLE_FILL`、游戏事件是 `FLUID_PICKUP`。
- **D3 纯逻辑**：`PipePlanner.discharge(pipe, situation, supply)`，`Supply(hasEmptyContainer, productExists)`；顺序：背包、罐、液量不够一份、成品容器不存在、没有空容器、做。`absorb` 的单位从 `pipe.fluid().perUseMb()` 取（蜂蜜 250）。`place` 对牛奶、蜂蜜返回 `NO_BLOCK_FORM`。`ContainerPicker`：给一串"位置"（背包主区域的各格，之后是物品栏的各格）与谓词，返回第一个匹配的位置。
- **D4 成品容器**：水桶 `Items.WATER_BUCKET`、岩浆桶、牛奶桶、蜂蜜瓶 `Items.HONEY_BOTTLE`；油 = 锁定流体 `BuiltInRegistries.FLUID.get(id).getBucket()`，是 `Items.AIR` 就算"没有桶"。空容器：`Items.BUCKET`（蜂蜜 `Items.GLASS_BOTTLE`）。
- **D5 发放**：`player.getInventory().add(stack)`，失败 `player.drop(stack, false)`（与原版取蜜一致）；背包主区域的空容器被取走后用 `inventory.setStackInSlot` + `saveInventory`，并 `sendFullSync` 让打开着的面板更新。
- **D6 客户端预测**：牛、蜂巢的交互在客户端也会跑一遍 `interactLivingEntity`/`useOn` 来决定要不要挥手；客户端对"成年牛/羊 + 牛奶管 + 未潜行"、"蜂巢 + 蜂蜜管 + `honey_level>=5` + 未潜行"返回 `sidedSuccess(true)`（只决定挥手，不改任何东西），其余 `pass`。
- **D7 提示措辞**：`cannot_absorb`、`cannot_place` 去掉"一桶"（蜂蜜一份只有 250 mB）：英文 "The %s tank has no room for more" / "The %s tank holds too little"，中文"%s罐装不下了" / "%s罐里不够了"；键名与逻辑不变。

## Project Structure

```text
specs/050-milk-honey-containers/{spec.md,plan.md,research.md,data-model.md,quickstart.md,contracts/,tasks.md,DELIVERY.md,checklists/}
src/main/java/pnnh/mcfloatingbackpack/
├── pipe/PipeKind.java          # 改：加 MILK、HONEY；`unitMb()`、`hasBlockForm()`、容器与成品的"种类"
├── pipe/PipePlanner.java       # 改：单位、`discharge`、`NO_EMPTY_CONTAINER`/`NO_BUCKET`/`NO_BLOCK_FORM`
├── pipe/ContainerPicker.java   # ★ 纯逻辑：从有序的位置里挑第一个空容器
├── pipe/PipeActions.java       # 改：`discharge`、`absorbEntity`、`absorbHive`；`place` 对牛奶/蜂蜜
├── pipe/PipeNotices.java       # 改：三条新提示的键与参数
├── pipe/PipeEvents.java        # 改：牛奶管、蜂蜜管左键
└── item/PipeItem.java          # 改：`use`（排放）、`interactLivingEntity`、`useOn`
src/main/resources/
├── assets/mcfloatingbackpack/{models/item,textures/item}/{milk,honey}_pipe.*
├── assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json
├── data/mcfloatingbackpack/recipe/{milk,honey}_pipe.json
└── data/mcfloatingbackpack/tags/item/never_trash.json
design/tank-textures/generate.py   # 改：生成牛奶管、蜂蜜管占位图
src/test/java/pnnh/mcfloatingbackpack/{pipe/*,resources/Pipe*Test.java}
README.md / README_CN.md / docs/plan/fluid-tanks.md
```

## 验证

见 [quickstart.md](./quickstart.md)：单元测试 → 容器里（带 PNC）：五种液体各排放一次；牛、山羊、小牛、蜂巢（有无篝火）；没有空容器、物品栏满、油没有桶；面板同步；潜行对着牛、方块、空气。

## Complexity Tracking

无需要论证的违例。蜂巢里重写两小段 `private` 的原版逻辑（激怒蜜蜂、判断有无蜜蜂）是为了不复制整段取蜜；它们各几行，且有源码注释指向原版出处。
