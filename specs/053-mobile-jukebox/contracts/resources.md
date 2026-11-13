# 契约：物品、配方、贴图、语言键

## 物品与配方

- `mcfloatingbackpack:mobile_jukebox`，类 `MobileJukeboxItem`（`stacksTo(1)`，什么也不做，只有一条提示）；创造栏 `main` 页：自动拾取之后、罐子之前；进 `never_trash`。
- `data/mcfloatingbackpack/recipe/mobile_jukebox.json`：`minecraft:crafting_shaped`，`category: misc`，图样 `IDI` / `NJN` / `IEI`，`I` = `minecraft:iron_ingot`，`D` = `minecraft:diamond`，`N` = `minecraft:note_block`，`J` = `minecraft:jukebox`，`E` = `minecraft:emerald`；结果 `count 1`；不加进度。
- 模型 `models/item/mobile_jukebox.json`（`minecraft:item/generated`，`layer0`）；贴图 `textures/item/mobile_jukebox.png`，16×16 RGBA，手绘（`design/demo/tools/make_mobile_tool_icons.py`）。

## 语言键（`zh_cn.json`、`en_us.json`，两个文件键集合相同）

| 键 | 中文 | 英文 |
|---|---|---|
| `item.mcfloatingbackpack.mobile_jukebox` | 移动唱片机 | Mobile Jukebox |
| `item.mcfloatingbackpack.mobile_jukebox.tooltip` | 放进浮动背包的右区，左键点它打开播放控制；播放"唱片扩展"行里的唱片 | Put it in the right area of the floating backpack and left click it to open the player; it plays the discs in the disc extension rows |
| `jukebox.mcfloatingbackpack.title` | 播放控制 | Player |
| `jukebox.mcfloatingbackpack.empty` | 唱片扩展行里没有唱片 | No discs in the disc extension rows |
| `jukebox.mcfloatingbackpack.track` | 第 %s / %s 首 | Track %s of %s |
| `jukebox.mcfloatingbackpack.previous` | 上一曲 | Previous |
| `jukebox.mcfloatingbackpack.play` | 播放 | Play |
| `jukebox.mcfloatingbackpack.stop` | 停止 | Stop |
| `jukebox.mcfloatingbackpack.rewind` | 倒带（从头重播） | Rewind (play again from the start) |
| `jukebox.mcfloatingbackpack.next` | 下一曲 | Next |
| `jukebox.mcfloatingbackpack.loop_one` | 单曲循环 | Repeat one |
| `jukebox.mcfloatingbackpack.loop_one.label` | 单曲 | Loop 1 |
| `jukebox.mcfloatingbackpack.shuffle` | 列表随机 | Shuffle |
| `jukebox.mcfloatingbackpack.shuffle.label` | 随机 | Shuffle |
| `jukebox.mcfloatingbackpack.on` | 开 | On |
| `jukebox.mcfloatingbackpack.off` | 关 | Off |
| `message.mcfloatingbackpack.limit.mobile_jukebox` | 右区已经有一个移动唱片机了 | The right area already has a mobile jukebox |

## 测试

- `MobileJukeboxResourcesTest`：物品、模型、贴图（16×16）、配方（图样与材料）、语言键（两种语言、键集合相同）、`never_trash` 含它；与现有所有配方两两不同、图标与现有图标不同；
- `JukeboxWiringTest`（源码文本检查）：页的挂接点（点击互斥、`onPress` 先于面板命中测试、渲染、滚轮、关闭 / 登录 / 登出重置、客户端 tick 收起与播放器驱动）；声音实例是 `RECORDS` + `relative` + `Attenuation.NONE`、开始前 `stopPlaying()`、`setNowPlaying`；**没有** `setAccessible`、`getDeclaredField`、访问转换器、mixin；播放器读 `BackpackRef.resolve` 与 `rows().visible()`，不读 `ClientBackpackView`；服务端与客户端各接一处 `ToolSingleRules`；协议仍 19、没有新 `CustomPacketPayload`、`DataRevision.CURRENT = 4`。
