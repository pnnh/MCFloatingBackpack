# Implementation Plan: 分类扩展：数据与规则

**Branch**: `035-category-extension-rules` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

## Summary

给背包加一份"扩展行"数据与规则：背包里有一个有序的**逻辑行列表**（每行 9 格、一个类别、可见或隐藏），行数由"放着的扩展件数"（三个功能区加面板光标）决定；往扩展行放物品要通过"类别判定"。035 **没有界面、没有扩展件物品**，所以玩家可见行为基本不变；规则靠单元测试验证，数据通路靠一次游戏里的回归加 `/give` 构造的数据检查。设计要点（依据见 [research.md](./research.md)）：

1. **存储分开**：`BackpackContents.items` 仍然只是**档位容量**那么长；扩展行另存在新字段 `rows`（可见行列表 + 隐藏行列表）。所以 `capacityOf`、升级配方、快照恢复判断档位、`ToolAreaCapacity`、合法尺寸全部**不用改**。
2. **运行时视图**：`BackpackInventory` 的主区域 = 档位容量格 + 各可见行的格子；加 `baseCapacity()`。`toContents()` 再拆回去，并在**保存**时核对一次（reconcile）；`fromContents()` 在**载入**时核对一次。
3. **纯逻辑**（新包 `inventory/extension/`，不引用 Minecraft 类）：`RowSet`（可见/隐藏两个有序列表）、`RowReconciler`（核对）、`ExtensionRules`（某格能不能放某物品、一个物品的目标格顺序、会不会超出行总数 35）、`CategoryMatcher`（类别判定的两个来源）。游戏一侧只写薄薄的适配：`ItemCategoryMatcher`（物品标签、`food`/`potion_contents` 组件、背包标签册）与 `ExtensionPacks`（"扩展件解析器"，默认什么都认不出，037 来注册）。
4. **放入规则统一走"目标格顺序"**：现有所有往主区域写的地方本来就是"算一个目标下标序列，再 `moveInto`"（快速转移、取容器物品、垃圾桶取回、自动拾取、光标拖拽），所以只要把"0..size-1"换成 `inventory.mainTargetOrder(stack)`；单格点击与光标放入加一个 `canPlaceMain(slot, stack)` 检查。`QuickTransferLogic.moveInto` 本身**不改**。
5. **类别判定两个来源，满足任一个**：数据标签 `mcfloatingbackpack:extension/<类别>`（加 `food`/`potion_contents` 组件）或这只背包的标签册里物品带有类别 id（`food tool weapon armor ore crop wood potion redstone disc`）。预设标签新增 `crop`、`potion`、`disc`。
6. **上限与协议**：新增 `MAX_MAIN_SLOTS = 108 + 35×9 = 423`（线路与请求校验用），`MAX_TIER_CAPACITY = 108`（`items` 列表与构造用）；`BackpackContents` 的网络编码改成手写（已用满 6 字段）；会话与整份同步包带上 `baseCapacity`、可见行类别、隐藏物品汇总；发送前量大小，超过 900,000 字节拒发并记日志；协议版本 16 → 17。
7. **布局变了要整份同步**：`BackpackRef.saveInventory` 发现可见行的结构变了（行数或类别序列），自动补发一次整份同步，因为主区域的格子编号变了。

不新增设置项、命令、配方、贴图、物品；唯一新增的语言键是 3 个预设标签名。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增任何依赖；不引用第三方类型

**Storage**: `BackpackContents` 新增可选字段 `extension_rows`（`optionalFieldOf`，旧背包读成空）；新增 10 个数据文件 `data/mcfloatingbackpack/tags/item/extension/<类别>.json`

**Testing**: JUnit 5。纯逻辑（`RowReconcilerTest`、`ExtensionRulesTest`、`CategoryMatcherTest`、`RowSetTest`，用泛型与假类型，沿用 032 的做法）；资源文本检查（`ExtensionTagsResourcesTest`：10 个标签文件的内容与 `required: false`、`PresetTags` 13 个 id 与中英文语言键）；现有的 018/025 相关测试按预设数量更新。**编码器的往返**用到 `ItemStack` 的编解码，需要游戏注册表，单元测试做不了，靠游戏里的检查（quickstart B）

**Target Platform**: 客户端 + 服务端；开发机 macOS

**Constraints**: 宪法 II（规则在服务端判定）、III（只做数据与规则，不做界面与物品）、VII（`c:`、`minecraft:` 标签只是数据引用，不是模组依赖；不引用第三方类型）；协议 17；图集不变（不新增贴图）

**Scale/Scope**: 新增包 `inventory/extension/`（约 7 个类）、10 个数据文件、4 个测试类；改动约 20 个已有文件（见 research.md R6、R7 的清单）。主区域最多 423 格；工具区、其它数据不变

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及界面 | PASS |
| II. Server Owns Items | 行的增删、类别判定、放入规则全部在服务端；客户端只收结果。判定要用到的标签册是服务端数据 | PASS |
| III. One Feature At A Time | 只做数据与规则；界面（036）、物品与配方（037）、搜索与整理都不做；预设标签新增 3 个是"手动标签联动"这一决策的一部分，属于 035 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方模组；原版标签与 `c:` 标签只是数据 | PASS |
| V. Verify In A Client Session | 玩家可见变化只有标签预设下拉多 3 项；quickstart 要求回归现有功能、旧背包载入、`/give` 构造的行数据往返与隐藏行保留。**扩展行在界面里的样子无法在 035 验证（没有扩展件物品、界面是 036）**，记为未验证，不声称已满足 | PASS（已规划，限制已记录） |
| VI. Shared Resources Have A Budget | 不新增贴图、模型；数据体积：每个背包多存 ≤35 行，大小保护见 contracts/wire.md；标签文件 10 个，只在资源重载时读取 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖；`#c:` 引用都带 `required: false` | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/035-category-extension-rules/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rules.md          # 行列表核对、类别判定、放入与目标顺序、上限
│   └── wire.md           # 存储与网络编码、限制常量、大小保护、协议 17
├── checklists/requirements.md
└── tasks.md              # /speckit-tasks 产出
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/extension/                 # 新：纯逻辑，不引用 Minecraft 类（除标明的适配类）
│   ├── ExtensionCategories.java         # 10 个类别 id 与顺序、行格数 9、行总数上限 35、常量
│   ├── Row.java                         # record Row<T>(String category, List<T> cells)
│   ├── RowSet.java                      # record RowSet<T>(List<Row<T>> visible, List<Row<T>> hidden)
│   ├── RowReconciler.java               # 核对：放着的数量 → 新的 RowSet
│   ├── ExtensionRules.java              # canPlace / targetOrder / fits（会不会超出 35）/ 隐藏汇总
│   ├── CategoryMatcher.java             # 类别判定接口（两个来源）
│   ├── ItemCategoryMatcher.java         # 适配：标签、组件、标签册（引用 Minecraft 类）
│   ├── ExtensionPacks.java              # 适配："扩展件解析器"，默认认不出任何物品（引用 Minecraft 类）
│   └── ExtensionRowsCodecs.java         # 适配：磁盘与网络编码（引用 Minecraft 类）
├── inventory/BackpackContents.java      # 改：新增 rows 字段，手写网络编码
├── inventory/BackpackInventory.java     # 改：MAX_TIER_CAPACITY/MAX_MAIN_SLOTS、baseCapacity、行的载入/保存/核对、mainTargetOrder、canPlaceMain
├── inventory/BackpackRef.java           # 改：saveInventory 在可见行结构变化时补发整份同步
├── inventory/BackpackClickLogic.java    # 改：放入前检查 canPlaceMain
├── inventory/BackpackMenuBridge.java    # 改：同上
├── inventory/ToolAreaRules.java         # 改：扩展件可放多个；会新增行但已满 35 时拒绝
├── inventory/QuickTransferRequest.java  # 改：MAX_BACKPACK_SLOTS → MAX_MAIN_SLOTS
├── inventory/PanelCursorRequest.java    # 改：同上
├── inventory/ContainerToBackpack.java   # 改：目标格顺序
├── inventory/pickup/PickupInsert.java   # 改：接受目标格顺序（保留旧签名）
├── inventory/pickup/AutoPickupEvents.java # 改：用 mainTargetOrder
├── inventory/tags/PresetTags.java       # 改：新增 crop、potion、disc
├── inventory/tags/TagRequest.java       # 改：MAX_SLOTS → MAX_MAIN_SLOTS
├── inventory/trash/TrashSendRequest.java # 改：同上
├── inventory/recipe/BackpackUpgradeRecipe.java # 改：fit 时带上 rows
├── network/                             # 改：BackpackNetwork（协议 17、整份同步入口、大小保护）、
│                                        #    BackpackSessionPayload、BackpackFullSyncPayload（新字段与上限）、
│                                        #    BackpackClickPayload / BackpackQuickTransferPayload / BackpackCursorGesturePayload /
│                                        #    BackpackTakeFromContainerPayload / BackpackTrashPayload / BackpackTrashSendPayload / BackpackTagPayload（上限与目标格顺序）
├── client/panel/ClientBackpackView.java # 改：保存 baseCapacity、可见行类别、隐藏汇总（035 不用于布局）
└── snapshot/                            # 不改（预览仍只取档位容量那部分，见 research R9）

src/main/resources/data/mcfloatingbackpack/tags/item/extension/{food,tool,weapon,armor,ore,crop,wood,potion,redstone,disc}.json   # 新
src/main/resources/assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json   # 改：3 个预设标签名

src/test/java/pnnh/mcfloatingbackpack/inventory/extension/{RowReconcilerTest,ExtensionRulesTest,CategoryMatcherTest,RowSetTest}.java   # 新
src/test/java/pnnh/mcfloatingbackpack/resources/ExtensionTagsResourcesTest.java   # 新
```

**Structure Decision**: 纯逻辑放进新包 `inventory/extension/`，泛型 + 假类型测试（沿用 `inventory/worn/BackpackSearch` 的做法）；适配类很薄，且集中在这个包里，其余已有文件只做"换常量、换目标格顺序、加一次检查"的小改动。

## Complexity Tracking

无宪法违规项。两个需要说明的取舍：

| 取舍 | 原因 | 放弃的更简单做法 |
|---|---|---|
| `BackpackContents` 的网络编码改成手写 | 现有 `StreamCodec.composite` 已用满 6 个字段，第 7 个字段放不下 | 把几个小字段折成子记录会改动 3 个已有字段的访问方式，影响面更大 |
| 隐藏行里的物品也随背包组件同步到客户端 | `BACKPACK_CONTENTS` 是 `networkSynchronized`，原版每次同步背包栈都带全部内容，不能只发可见部分 | 单独为隐藏行建一个不同步的组件：会让一份背包数据分裂成两个组件，保存与快照都要处理两份 |
