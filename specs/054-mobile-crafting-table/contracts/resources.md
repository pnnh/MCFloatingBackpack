# 契约：物品、配方、贴图、语言键

## 物品与配方

- `mcfloatingbackpack:mobile_crafting_table`，类 `MobileCraftingTableItem`（`stacksTo(1)`，什么也不做，只有一条提示）；创造栏 `main` 页：移动唱片机之后、罐子之前；进 `never_trash`。
- `data/mcfloatingbackpack/recipe/mobile_crafting_table.json`：`minecraft:crafting_shaped`，`category: misc`，图样 `IDI` / `ATB` / `ICI`，`I` = `minecraft:iron_ingot`，`D` = `minecraft:diamond`，`A` = `minecraft:iron_pickaxe`，`B` = `minecraft:iron_axe`，`T` = `minecraft:crafting_table`，`C` = `minecraft:chest`；结果 `count 1`；不加进度。
- 模型 `models/item/mobile_crafting_table.json`（`minecraft:item/generated`）；贴图 `textures/item/mobile_crafting_table.png`，16×16 RGBA，手绘（`make_mobile_tool_icons.py`）。

## 语言键（`zh_cn.json`、`en_us.json`，两个文件键集合相同）

| 键 | 中文 | 英文 |
|---|---|---|
| `item.mcfloatingbackpack.mobile_crafting_table` | 移动工作台 | Mobile Crafting Table |
| `item.mcfloatingbackpack.mobile_crafting_table.tooltip` | 放进浮动背包的右区，左键点它打开工作台；再点一下关掉 | Put it in the right area of the floating backpack and left click it to open a crafting table; click again to close it |
| `message.mcfloatingbackpack.limit.mobile_crafting_table` | 右区已经有一个移动工作台了 | The right area already has a mobile crafting table |

## 测试

- `MobileCraftingTableResourcesTest`：物品、模型、贴图（16×16）、配方（图样与材料）、语言键、`never_trash`、创造栏顺序；配方与现有所有配方两两不同、图标与现有图标不同；
- `MobileToolTest`（字节映射：已知、未知、负数）、`ToolOpenPlannerTest`（决策表全部 8 行加未知种类）；
- `MobileCraftingWiringTest`（源码文本检查）：包在 `versioned("20")` 的 registrar 上 `playToServer`；处理器的校验与决策表用法；菜单子类 `extends CraftingMenu`、`ContainerLevelAccess.create`、**没有** `ContainerLevelAccess.NULL`、`stillValid` 重写且调用 `MobileTools.has`；`MobileTools.open` 用 `Component.translatable("container.crafting")`；两处右区限制；客户端点击发包并返回 true；没有 mixin、`setAccessible`、访问转换器；`DataRevision.CURRENT = 5`。
