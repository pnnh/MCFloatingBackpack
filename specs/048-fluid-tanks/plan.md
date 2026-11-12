# Implementation Plan: 液体罐

**Branch**: `048-fluid-tanks` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

## Summary

19 种"没有状态"的罐物品 + 背包级的**液量记录**（`FluidStore`）+ 放入规则（档次、同液体互斥、容量）+ 面板量条与悬停提示。**服务端权威（宪法 II）、纯逻辑可测**。核心思路：

1. **罐子物品不带液量**：液量存在**背包里**（`BackpackContents` 的新可选字段 `fluids`），按液体种类各一个数；罐子只决定"这种液体有没有、容量多大"。这样"取下罐、液体留在背包"（spec US4）自然成立，罐子可以随便换、堆叠上限 1 的物品不需要任何数据组件（D1）。
2. **放入规则是一个纯函数 `TankPlacement.check`**，接在服务端 `BackpackClickPayload` 里 `ToolAreaRules` 之后（所有往工具区放东西的服务端路径只有点击这一条，见 research D3），客户端 `FloatingPanelInput.refusedByLimit` 同规则预测；被拒时给一条动作栏提示说明原因（D2、D3）。
3. **数据**：`FluidStore`（不可变，五种液体各一个整数 mB，加原油的锁定流体 id）进 `BackpackContents`（磁盘可选字段、网络编码追加）、030 快照（走同一个 `CODEC`，不用改）、**数据修订 1 → 2**（新增存储字段，040/041 的规则要求）、协议号 18 → **19**（组件网络编码变了，另加一个 S→C 包 `BackpackFluidsPayload`）（D4、D5）。
4. **面板**：底部区的生效罐在格子上画一条竖向量条（代码画，不加贴图），悬停提示在原版物品提示后追加一行"液体名：当前 / 容量 mB"；左、右区的罐不画（D6）。
5. **物品、配方、占位贴图**：19 个物品、19 个配方（皮 4、铁 5、金 5 为有序合成，下界合金 5 为锻造台）、19 个物品模型与占位贴图（Python 脚本按"档次色 × 液体色"生成，放 `design/`），语言键中英文（D7、D8）。
6. **048 只提供"改液量"的接口**（`FluidStoreOps`：存入、取出、原油锁定），不接任何玩家操作；游戏里用 `/item replace … [mcfloatingbackpack:backpack_contents={…,fluids:{water:8000}}]` 造出带液量的背包来验证显示（D9）。

## Technical Context

**Language/Version**: Java 21 · NeoForge 21.1.251 / Minecraft 1.21.1

**Primary Dependencies**: 无新增；`DependencyRulesTest` 照常

**Storage**: 背包数据组件 `backpack_contents` 的新可选字段 `fluids`（磁盘 + 网络）；数据修订 CURRENT 1→2

**Testing**: JUnit 5：`TankPlacementTest`（100 种组合穷举 + 1000 组随机不变式）、`FluidStoreTest`（运算、边界、原油锁定、守恒）、`FluidStoreCodecTest`（磁盘与网络往返、旧数据缺字段）、`TankCatalogTest`（档次、容量、19 种清单、没有皮岩浆）、`TankBarTest`（量条像素）、`TankResourcesTest`（19 个物品/模型/贴图/配方/语言键/never_trash 都在、配方形状与规格一致）、`TankWiringTest`（源码文本：放入规则接线、协议号 19、修订 2、载荷注册）；容器里游戏内验证

**Target Platform**: Minecraft 客户端 + 服务端（NeoForge）

**Project Type**: 游戏模组（单工程，服务端权威）

**Performance Goals**: 放入检查 O(底部区格数)（≤ 约 20）；面板每帧最多多画 11 条量条；无可见影响

**Constraints**: 无新增配置项与依赖；协议号 18→19；数据修订 1→2；011～047 的测试除协议号与修订断言外不改；共享方块图集不超过 2048（宪法 VI，加贴图前后各量一次，见 quickstart D）

**Scale/Scope**: 19 个物品、19 个配方、19 张 16×16 贴图、约 8 个新类、改动约 10 个既有文件

## Constitution Check

| 原则 | 评估 |
|---|---|
| I. Overlay Not Screen | 量条与提示画在同一条面板渲染路径里，不新增 Screen PASS |
| II. Server Owns Items | 放入判定与液量都在服务端；客户端只做预测与显示、不乐观更新；液量由服务端同步给客户端 PASS |
| III. One Feature At A Time | 只做罐子本身；管子、吸收、放置、排放、聊天提示明确不做（049、050）PASS |
| IV. Vanilla Path Before Compat | 完全用原版物品与配方；原油只定义"带 `c:crude_oil` 标签的流体"这一规则，不依赖任何原油模组 PASS |
| V. Verify In A Client Session | quickstart 要求容器里验证放入规则、量条、悬停提示、配方，未验证项如实记录 PASS（实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 新增 19 张 16×16 物品贴图进共享图集：**加之前与之后各跑一次 `design/demo/tools/check_atlas_budget.sh` 并记录**；未量不声称达标 PASS（待实现时度量） |
| VII. Minimal Dependencies | 无新依赖；不引用其它模组的类型 PASS |

## Research

完整记录见 [research.md](./research.md)。要点：

- **D1 液量放哪**：背包级 `FluidStore`，不放罐子的数据组件。理由：spec 已定"取下罐子液体留在背包"；罐子无状态则合成、锻造台升级（金→下界合金）都不用搬数据。代价是改 `BackpackContents`（记录加一个分量，约 10 处调用点）。
- **D2 档次比较**：直接用 `BackpackTier` 的先后（皮 < 铁 < 金 < 钻石 < 下界合金）；罐只有四档，比较 `罐档次.ordinal() ≤ 背包档次.ordinal()`。无法从容量判出背包档次（`byCapacity` 返回 null）时只放皮罐。
- **D3 放入路径**：读源码确认，往工具区放东西的服务端路径只有 `BackpackClickPayload`（点击、Alt 点击、菜单携带栈都在同一处求 `held`）；快速转移、光标手势、取出容器、垃圾桶、自动拾取都只写主区域。创造模式的客户端整包写回是既有的绕过（`ExtensionPackRules` 同样），列为已知局限。
- **D4 数据修订与协议**：新增存储字段 + 新增只能存在背包里的物品种类，按 `release-process.md` 的规则 `DataRevision.CURRENT` 1→2；组件网络编码追加 `fluids` + 新 S→C 包，协议号 18→19。
- **D5 快照与降级**：030 快照读写都走 `BackpackContents.CODEC`，新字段自动包含，只需测试往返；040 的"保护"按修订生效；041 的存档标记随 CURRENT 升到 2。
- **D6 面板**：量条用 `graphics.fill`，z 抬到物品装饰层（200）；悬停在 `drawItemTooltip` 里对底部区的罐追加一行。
- **D7 配方**：见 [contracts/recipes-and-assets.md](./contracts/recipes-and-assets.md)；容器的空桶/空瓶用原版 `craftRemainder`（水桶、岩浆桶、牛奶桶、蜂蜜瓶都已带），不写额外规则；下界合金罐用原版 `smithing_transform`（罐无状态，不需要自定义配方类）。**项目里现有配方都没有解锁进度**，本 feature 沿用（spec 的"配方书解锁"改为沿用现状，见下"对 spec 的更正"）。
- **D8 占位贴图**：`design/tank-textures/generate.py` 生成 19 张 16×16 PNG 到 `src/main/resources/assets/.../textures/item/`；贴图是 `design/` 之外的运行时资源，**不进 LFS**（宪法）。
- **D9 怎么在游戏里造出带液量的背包**：`/item replace entity @s weapon.mainhand with mcfloatingbackpack:iron_floating_backpack[mcfloatingbackpack:backpack_contents={items:[],fluids:{water:8000}}]`（组件的持久编码就是命令的语法）；048 不加调试命令。

## 补充：隐藏液体（US6，2026-11-12）

- **D10 判定**：`tank/HiddenLiquids.lines(FluidStore, Predicate<FluidKind> hasTank)` → 按 `FluidKind` 顺序、量 > 0 且没有生效罐的液体（`Line(kind, amountMb)`）。客户端 `hasTank = view.tankTier(kind) != null`（只看底部区整张存储列表）。无网络变化。
- **D11 黄点**：`FloatingPanelRenderer` 里"有隐藏物品"的条件改为"有隐藏物品或有隐藏液体"；悬停黄点的提示 `drawHiddenHint` 在物品段之后加液体段（标题 `panel.….hidden.liquid.title`、每行 `hidden.liquid.line`、提示 `hidden.liquid.hint`）。只有液体时不出现"有物品被隐藏"的标题。
- **D12 /hidden**：`HiddenSummaryRows.Line` 增加 `liquidMb`（类别行为 0；用 3 参数构造保持 044 的既有测试不变）；`SearchContext.hiddenLines()` 在类别行之后追加液体行（`categoryId = "liquid_<id>"`，`displayName` 取 `panel.….hidden.liquid.name`，如"水（液体）"/"Water (liquid)"，`cells = 1`，`liquidMb = 量`）；`HiddenSummaryRows.visible` 沿用同一套匹配（名字或 id 含词），所以 `/hidden 液体` 能筛出全部液体行；`SearchRenderer.drawHiddenSummary` 对 `liquidMb > 0` 的行用 `hidden.liquid.line`；标题与提示按"有物品行/有液体行"选键（只物品：037 的原文；只液体：液体的；两者：合并标题 `hidden.mixed.title` 与两段提示）。
- **D13 测试**：`HiddenLiquidsTest`（纯逻辑）、`HiddenSummaryRowsTest` 扩展（液体行筛选、计数）、`TankWiringTest` 源码断言（黄点条件、悬停段、`/hidden` 追加）；游戏里：取下罐后黄点、悬停、`/hidden`、放回后消失。

## 对 spec 的更正（plan 阶段查证后）

1. **档次规则对三个工具区都生效**（不只底部区）：用户原话"铁罐只能放在铁背包及以上"；左、右区若不检查，下界合金罐就能塞进皮背包。互斥与液量显示仍只看底部区。**主区域不限制**（它是普通存储，限制要改动所有转移路径，代价大、收益小）——**请用户确认**。
2. **被拒时给动作栏提示**（三种原因各一条语言键）：沿用 036 对扩展行上限的做法，不然玩家只看到"放不进去"。
3. **"配方书解锁"撤销**：现有配方都没有进度文件，本 feature 沿用，配方靠 JEI 与配方书的现有呈现。
4. 协议号确定为 19，数据修订确定为 2。

## Project Structure

```text
specs/048-fluid-tanks/{spec.md,plan.md,research.md,data-model.md,quickstart.md,contracts/,tasks.md,DELIVERY.md,checklists/}
src/main/java/pnnh/mcfloatingbackpack/
├── tank/FluidKind.java               # ★ 五种液体：id、一次的量、颜色、有无锁定（纯逻辑）
├── tank/TankCatalog.java             # ★ 19 种清单、容量、档次先后（纯逻辑）
├── tank/FluidStore.java              # ★ 不可变液量记录 + 运算（纯逻辑）
├── tank/FluidStoreCodecs.java        # ★ 磁盘 Codec 与网络 StreamCodec
├── tank/TankPlacement.java           # ★ 放入判定（纯函数）
├── tank/TankBar.java                 # ★ 量条像素与文字（纯逻辑）
├── item/TankItem.java                # ★ 无状态物品，带 kind 与 tier，悬停提示静态部分
├── item/ModItems.java                # 改：19 个罐、创造标签页
├── inventory/BackpackContents.java   # 改：新分量 fluids（codec 磁盘可选 + 网络追加）
├── inventory/BackpackInventory.java  # 改：fluids()/setFluids、tankTier(kind)，toContents/fromContents
├── inventory/revision/DataRevision.java   # 改：CURRENT 2
├── network/BackpackClickPayload.java # 改：放入规则 + 提示
├── network/BackpackFluidsPayload.java     # ★ S→C
├── network/BackpackNetwork.java      # 改：注册、协议号 19、sendFluids、会话打开时发
└── client/panel/{ClientBackpackView,FloatingPanelInput,FloatingPanelRenderer}.java   # 改
src/main/resources/
├── assets/mcfloatingbackpack/{models/item,textures/item}/<tier>_<kind>_tank.*   # 19 + 19
├── assets/mcfloatingbackpack/lang/{en_us,zh_cn}.json
├── data/mcfloatingbackpack/recipe/<tier>_<kind>_tank.json   # 19
└── data/mcfloatingbackpack/tags/item/never_trash.json       # 改
design/tank-textures/generate.py
src/test/java/pnnh/mcfloatingbackpack/{tank/*,resources/Tank*Test.java}
README.md / README_CN.md / docs/plan/fluid-tanks.md
```

## 验证（quickstart，见 [quickstart.md](./quickstart.md)）

单元测试 → 容器里：19 种罐都能取到与合成；5 档背包 × 4 档罐的放入结果；同液体互斥与同格替换；左/右区放入但无量条；带液量背包的量条与悬停；换更小的罐被拒；重进世界、快照恢复后液量一致；旧存档背包照常打开；图集尺寸前后对比。

## Complexity Tracking

无需要论证的违例。`BackpackContents` 记录再加一个分量（8→9）是既有做法（035 加 `rows`、040 加 `data_revision` 都这么做），不另立结构。
