# Contract: 资源

## 物品

- `mcfloatingbackpack:mobile_furnace`：`MobileFurnaceItem`，堆叠 1，什么也不做（点击由面板处理）；`appendHoverText` 显示提示。
- 创造物品栏：放在 `mobile_crafting_table` 之后。
- `data/mcfloatingbackpack/tags/item/never_trash.json` 加入 `mcfloatingbackpack:mobile_furnace`。

## 模型与贴图

- `assets/mcfloatingbackpack/models/item/mobile_furnace.json`：`item/generated`，`layer0 = mcfloatingbackpack:item/mobile_furnace`。
- `assets/mcfloatingbackpack/textures/item/mobile_furnace.png`：16×16 手绘，由 `design/demo/tools/make_mobile_tool_icons.py` 生成（加一个熔炉图标：灰色炉体 + 炉口 + 一点火光，与垃圾桶、唱片机、工作台的风格一致）。占位，用户之后可用同名文件替换。
- 共享图集预算（宪法 VI）：新增 1 张 16×16；实现时前后各量一次。

## 配方 `data/mcfloatingbackpack/recipe/mobile_furnace.json`

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": ["IRI", "CFC", "ILI"],
  "key": {
    "I": {"item": "minecraft:iron_ingot"},
    "R": {"item": "minecraft:blaze_rod"},
    "C": {"item": "minecraft:coal_block"},
    "F": {"item": "minecraft:furnace"},
    "L": {"item": "minecraft:lava_bucket"}
  },
  "result": {"id": "mcfloatingbackpack:mobile_furnace", "count": 1}
}
```

- 岩浆桶的剩余物：原版岩浆桶 `craftingRemainingItem` 是桶，有序配方合成后自动退回，不需要写；资源测试核对这一点（物品的 `hasCraftingRemainingItem`）。
- 不加解锁进度（`category: misc`，与 053、054 一致）。

## 数据组件

- `mcfloatingbackpack:mobile_furnace_state`（`ModDataComponents.MOBILE_FURNACE_STATE`）：见 [data-model.md](../data-model.md)。

## 语言键（中、英）

| 键 | 中文 | 英文 |
|---|---|---|
| `item.mcfloatingbackpack.mobile_furnace` | 移动熔炉 | Mobile Furnace |
| `item.mcfloatingbackpack.mobile_furnace.tooltip` | 放进浮动背包右侧工具区，左键点它打开熔炉；关掉界面后还会继续烧；再点一下关闭 | Put it in the right area of the floating backpack and left click it to open a furnace; it keeps smelting after you close it; click again to close it |
| `message.mcfloatingbackpack.limit.mobile_furnace` | 右区已经有一个移动熔炉了 | The right area already has a mobile furnace |
| `message.mcfloatingbackpack.furnace.protected` | 这只背包是更新版本保存的，为了不丢数据，移动熔炉暂时打不开 | This backpack was saved by a newer version; the mobile furnace cannot be opened so that no data is lost |

## 文档

- `README.md` / `README_CN.md`：移动熔炉一节（用法、补算的说明、配方）。
- `docs/plan/mobile-tools.md`：055 的状态改为已实现。
- `specs/055-mobile-furnace/DELIVERY.md`：交付说明（修订 5 → 6 的理由、协议号 20 不变、未验证项）。
