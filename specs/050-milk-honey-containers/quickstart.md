# Quickstart: 牛奶管、蜂蜜管与排放到容器的验证

规则见 [contracts/discharge-rules.md](./contracts/discharge-rules.md)，物品与配方见 [contracts/recipes-and-assets.md](./contracts/recipes-and-assets.md)，类型见 [data-model.md](./data-model.md)。

## A. 单元测试（不需要游戏）

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.pipe.*' --tests 'pnnh.mcfloatingbackpack.resources.Pipe*'
./gradlew compileJava test
```

覆盖点：`PipePlannerTest`（扩展：排放判定表、单位 250/1000、五种液体、`NO_BLOCK_FORM`、随机同进退）、`ContainerPickerTest`、`PipeKindTest`（扩展）、`PipeResourcesTest`（扩展到 5 根）、`PipeWiringTest`（扩展：`use` 里潜行排放、`useOn` 与 `interactLivingEntity` 里潜行 `pass`；牛奶的成年判断与挤奶声；蜂巢的 `honey_level` 判断、篝火判断、公开方法；排放里"判定 → 取容器 → 发成品 → 提交"的顺序、成品放不下掉落；空容器取自主区域时整包同步；无新包、协议号仍 19、修订仍 2、`neoforge.mods.toml` 无 PNC）。049 的测试只因提示文字有关的断言才可能要改。

## B. 游戏内（容器自动验证，宪法 V）

前提同 049：`scripts/ai-gui` 的容器（先 `pgrep …`；`-PnoJei`）；"使用/攻击"改绑到键盘 `u`/`y`（见 `scripts/ai-gui/README.md`）；平台试验台（`plat`、`pw`…）；世界里用数据包函数。造背包（五种罐都在底部区，各装一些液体）：

```text
item replace entity @s hotbar.8 with mcfloatingbackpack:netherite_floating_backpack[mcfloatingbackpack:backpack_contents={items:[{id:"minecraft:bucket",count:4},{id:"minecraft:glass_bottle",count:3}],tools:{bottom:[{id:"mcfloatingbackpack:iron_water_tank",count:1},{id:"mcfloatingbackpack:iron_oil_tank",count:1},{id:"mcfloatingbackpack:iron_lava_tank",count:1},{id:"mcfloatingbackpack:iron_milk_tank",count:1},{id:"mcfloatingbackpack:iron_honey_tank",count:1}]},fluids:{water:3000,oil:2000,oil_fluid:"pneumaticcraft:oil",lava:2000,milk:3000,honey:1000}}]
give @s mcfloatingbackpack:water_pipe  （及油、岩浆、牛奶、蜂蜜管）
summon cow ~ ~ ~3     summon goat ~ ~ ~4     summon cow ~ ~ ~5 {Age:-24000}
setblock ~ ~ ~6 minecraft:beehive[honey_level=5]
```

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 水排放 | 水管潜行右键 | 主区域的一个空桶被消耗，得到水桶，`fluids.water` −1000；空桶在背包主区域里先用（`/data` 看背包 `items` 里的桶数 4→3） |
| B2 其它排放 | 岩浆、牛奶、油（PNC）各一次 | 得到岩浆桶、牛奶桶、`pneumaticcraft:oil_bucket`；各 −1000 |
| B3 蜂蜜排放 | 蜂蜜管潜行右键 | 消耗一个玻璃瓶，得到蜂蜜瓶，`fluids.honey` −250（1000→750） |
| B4 优先背包主区域 | 背包主区域与物品栏里都有空桶 | 先用主区域的（`items` 里的桶数减，物品栏里的桶数不变） |
| B5 只有物品栏有空桶 | 把背包主区域的桶清空，物品栏放桶 | 用物品栏的 |
| B6 没有空容器 | 都没有 | "No empty Bucket…"；液量不变 |
| B7 物品栏满 | 塞满物品栏再排放 | 成品掉在脚边 |
| B8 不够一份 | 蜂蜜罐 200 mB、水罐 900 mB | "罐里不够了"，不消耗 |
| B9 油没有桶 | 锁定为假流体 `test:crude` 再排放 | "这种油没有对应的桶"，不消耗空桶 |
| B10 潜行对着牛 | 牛奶管对着牛潜行右键 | 排放（得到牛奶桶），牛不挤奶 |
| B11 挤奶 | 牛奶管（未潜行）右键成年牛、山羊 | 各 +1000，有声音；小牛没反应 |
| B12 满了 | 牛奶罐剩 500 mB 空位再挤 | "装不下了"，牛不受影响 |
| B13 取蜜 | 蜂蜜管右键 `honey_level=5` 的蜂巢 | +250，`honey_level` 变 0；`honey_level=4` 没反应 |
| B14 篝火 | 蜂巢下放一堆点燃的篝火再取蜜 | 不激怒蜜蜂（和没篝火时对比：放几只蜜蜂 `summon bee`） |
| B15 左键 | 牛奶管、蜂蜜管左键方块 | 方块不被挖、不放置，出现"倒不出方块"的提示 |
| B16 面板 | 面板开着时排放（空桶取自主区域） | 主区域的桶数与量条即时更新 |
| B17 配方 | 工作台合成牛奶管、蜂蜜管 | 牛奶桶退回空桶、蜂蜜瓶退回玻璃瓶（**未验证项**，记录实际结果） |

记录：每个场景实际验证了什么；没验证的写"未验证"（真机、中文界面、多玩家、模组的牛或蜂巢）。

## C. 文档核对

- `README.md` / `README_CN.md`：管子一节补牛奶管、蜂蜜管与"潜行右键装进容器"；`docs/plan/fluid-tanks.md` 状态；`DELIVERY.md`（中文）：协议号与数据修订不变的理由、提示措辞的改动、未验证项。

## D. 度量（宪法 VI，DELIVERY 记录）

- 加两张管子贴图前后各跑一次 `design/demo/tools/check_atlas_budget.sh`（049 之后为 2048×1024），都 ≤ 2048；没量写"未度量"。
