# Quickstart: 水管、油管、岩浆管的验证

规则见 [contracts/pipe-rules.md](./contracts/pipe-rules.md)，物品与配方见 [contracts/recipes-and-assets.md](./contracts/recipes-and-assets.md)，类型见 [data-model.md](./data-model.md)。

## A. 单元测试（不需要游戏）

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.pipe.*' --tests 'pnnh.mcfloatingbackpack.resources.Pipe*'
./gradlew compileJava test
```

覆盖点：`PipePlannerTest`（判定表逐行、档次无关、原油锁定、同进退、1000 组随机）、`PipeKindTest`、`CooldownTest`、`PipeResourcesTest`、`PipeWiringTest`（右键的 `use` 里潜行 `pass`、射线用 `SOURCE_ONLY`；`LeftClickBlock` 取消与只在服务端 `START` 放置；改世界先于提交液量；`sendFluids`；无新网络包、协议号仍 19、数据修订仍 2；PNC 只在 `localRuntime`；主代码不 import `pneumaticcraft`）。既有测试一概不改。

## B. 游戏内（容器自动验证，宪法 V）

前提：`scripts/ai-gui` 的容器（先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei` 起，避免 JEI 干扰界面）；`build.gradle` 已加 PNC（`-PcompatRuntime` 组，默认开）；世界用数据包函数造背包（聊天命令最长 256 字符）：

```text
item replace entity @s hotbar.8 with mcfloatingbackpack:iron_floating_backpack[mcfloatingbackpack:backpack_contents={items:[],tools:{bottom:[{id:"mcfloatingbackpack:iron_water_tank",count:1},{id:"mcfloatingbackpack:iron_oil_tank",count:1},{id:"mcfloatingbackpack:iron_lava_tank",count:1}]}}]
give @s mcfloatingbackpack:water_pipe
give @s mcfloatingbackpack:oil_pipe
give @s mcfloatingbackpack:lava_pipe
setblock ~2 ~ ~ minecraft:water
setblock ~3 ~ ~ minecraft:lava
setblock ~4 ~ ~ pneumaticcraft:oil
```

背包放在物品栏里（不在主手）；管子拿在主手。每次验证读聊天日志（`latest.log` 的 `[CHAT]`）与 `/data get entity @s Inventory`（背包的 `fluids`），不靠 OCR。

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 吸水 | 水管右键水源 | 水源消失；`fluids.water` = 1000；面板（B）里水罐的量条出现 |
| B2 吸岩浆、吸油 | 岩浆管右键岩浆；油管右键 PNC 的石油 | 各 +1000；`oil_fluid` = `pneumaticcraft:oil` |
| B3 认液体 | 水管右键岩浆、岩浆管右键水、油管右键 PNC 的植物油（`c:plantoil`） | 什么也不发生 |
| B4 流动的不吸 | 水管右键流动的水 | 什么也不发生 |
| B5 水面下 | 水管对着水面看（水下有方块）右键 | 吸到水面的水源 |
| B6 放置 | 水管对地面左键 | 在相邻格出现水源；`fluids.water` −1000；地面方块没被挖（生存与创造各一次） |
| B7 按住左键 | 生存里按住左键扫过地面 | 不是每 tick 放置；最多约每 0.2 秒一次；方块不被挖 |
| B8 满与空 | 皮水罐（8 桶）吸满；再右键；放空；再左键 | 第 8 次后"水罐满了"；再吸"装不下一桶了"；放空后"水罐空了"；再放"不足一桶" |
| B9 冷却 | 0.5 秒内连点同一失败操作 | 只出现一条提示 |
| B10 没有背包 | 把背包丢掉再右键 | "没有找到浮动背包" |
| B11 没有罐 | 把水罐拿到左区再右键水源 | "背包底部区里没有水罐" |
| B12 油的锁定 | 吸 PNC 的石油后，再设法放一种别的 `c:crude_oil` 流体（没有第二种时用单元测试覆盖）；排空后再吸 | 另一种被拒（单元测试）；排空后锁定清除（`oil_fluid` 消失） |
| B13 潜行 | 潜行右键水源 | 什么也不发生 |
| B14 保护区域 | 出生点保护范围内（非 op）右键 | 不吸收 |
| B15 面板同步 | 面板开着时吸收 | 量条与 048 的"隐藏液体"（把罐拿走时）即时更新 |
| B16 下界放水 | 下界里水管左键 | 与桶一样蒸发（有烟、有声音），液量照扣 |
| B17 配方 | 工作台合成三根管子 | 与契约一致；水桶、岩浆桶退回空桶 |

记录：每个场景实际验证了什么；没验证的写"未验证"（真实多种原油并存、真机、Windows/Linux、PNC 在容器外的行为）。

## C. 文档核对

- `README.md` / `README_CN.md`：管子一节（用法、一次一桶、提示、潜行右键留给以后）；`docs/plan/fluid-tanks.md` 状态；`DELIVERY.md`（中文）：协议号与数据修订不变的理由、PNC 作为开发依赖的说明、未验证项。

## D. 度量（宪法 VI，DELIVERY 记录）

- 加管子贴图前后各跑一次 `design/demo/tools/check_atlas_budget.sh`（048 之后为 2048×1024），都 ≤ 2048；没量写"未度量"。
