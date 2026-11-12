# DELIVERY: 牛奶管、蜂蜜管与排放到容器（050）

状态：已完成，用户已在真机确认（2026-11-12，T025）。

## 基线与结果

- `./gradlew test`（实现前）：**186 个类、1418 个用例，0 失败**。
- `./gradlew test`（实现后）：**187 个类、1444 个用例，0 失败**（新增 `ContainerPickerTest`，扩展 `PipePlannerTest`、`PipeKindTest`、`PipeResourcesTest`、`PipeWiringTest`）。
- 共享方块图集（`design/demo/tools/check_atlas_budget.sh`，干净客户端）：加两张管子贴图**之前 2048x1024（049 收尾时测得），之后 2048x1024**，通过预算。
- 发布的 jar：不含 PNC（`unzip -l` 搜 `pneumaticcraft`、`me/desht` 为 0），管子相关文件 15 个（5×模型、贴图、配方）。

## 与 spec/plan 的偏离

1. **改了 049 的一条随机测试**：`PipePlannerTest.randomActionsKeepTheLiquidInRangeAndOnlyChangeItWhenDone` 里写死的 ±1000，改为 `pipe.unitMb()`（现在管子有五种，蜂蜜一份是 250）。`PipeWiringTest` 里一条断言"`PipeItem` 里没有 `useOn`"改成注释（050 为蜂蜜管加了 `useOn`，其余情况仍 `pass`）。
2. **`READY_HONEY_LEVEL = 5` 常量**：蜂巢没有公开的最大蜂蜜级别常量，放在 `PipeActions` 里。
3. **提示措辞**：`cannot_absorb`、`cannot_place` 的文字改为与单位无关（"装不下了"/"罐里不够了"），键名与逻辑不变（FR-013）。
4. **提示参数**：`NO_EMPTY_CONTAINER` 的参数是容器物品的名字，`NO_BUCKET_FOR_FLUID` 的参数是油的流体 id（与 spec 一致，plan 没写细）。

## 协议号与数据修订

- **协议号仍是 19，数据修订仍是 2**：没有新增网络包（排放走 `use()`，牛奶走 `interactLivingEntity`，蜂蜜走 `useOn`；面板同步用已有的 `sendFluids` 与 `sendFullSync`）、没有新增存储字段、配置项与依赖。`PipeWiringTest` 有断言。

## 游戏内验证（容器 mcfb-c1，带 PNC 8.2.24，`-PnoJei`，AI 自动验证）

试验台同 049：空中石头平台，"使用/攻击"改绑到键盘 `u`/`y`，潜行用 `xdotool keydown shift`。背包放在快捷栏第 9 格（不在主手），主区域放 4 个空桶、3 个空玻璃瓶，底部区放五种铁罐；用 `data get` 读液量与物品。

**验证了**（生存模式）：
- **B1/B2/B3 五种排放**：水管、岩浆管、牛奶管、油管（PNC）、蜂蜜管各潜行右键一次：水 3000→2000、岩浆 2000→1000、牛奶 3000→2000、油 2000→1000、蜂蜜 1000→750；得到水桶、岩浆桶、牛奶桶、**`pneumaticcraft:oil_bucket`**、蜂蜜瓶；用掉的是背包主区域的 4 个空桶与 1 个玻璃瓶（蜂蜜用瓶）。
- **B4/B5 容器的顺序**：背包主区域的空桶先用；主区域没有时才用物品栏里的（物品栏里 2 个桶→1 个）。
- **B6 没有空容器**：`No empty Bucket in your backpack or inventory`，液量不变。
- **B7 物品栏满**：成品（水桶）掉在脚边（附近出现 `minecraft:water_bucket` 的物品实体），水 3000→2000。
- **B8 不够一份与扣空**：蜂蜜 750 → 连续三次扣空，第 3 次 `The Honey tank is empty`，第 4 次 `The Honey tank holds too little`，共得到 3 个蜂蜜瓶。
- **B9 油没有桶**：油罐锁定为假流体 `test:crude` 时 `There is no bucket for this oil (test:crude)`，油 2000 不变、4 个空桶不变。
- **B10 潜行对着牛**（小牛）：牛奶管潜行右键是排放（牛奶 5000→4000，得到牛奶桶），不是吸收。
- **B11 挤奶**：牛奶管右键成年牛 3000→4000；成年山羊 4000→5000；小牛：不变。
- **B13 取蜜**：蜂蜜管右键 `honey_level=5` 的蜂巢：蜂蜜 1000→1250，蜂巢 `honey_level` 变 0；`honey_level=4` 的蜂巢：不变。
- **B15 左键**：牛奶管、蜂蜜管左键石头：不挖、不放，提示 `Milk cannot be poured out as a block; sneak and right-click to fill a container`（蜂蜜同）。
- **B16 面板同步**：面板开着时排放，主区域的空桶数立即从 4 变为 3（放大截图看到的角标，读数不是百分之百清晰）。
- 图集前后一致；jar 里没有 PNC。

**没验证的（如实记录）**：
- B12 罐满时挤奶的提示（只有单元测试）。
- B14 有无篝火烟熏的区别、蜜蜂被激怒与放出：没有造出蜜蜂场景，只有源码断言（用了原版公开的 `releaseBeesAndResetHoneyLevel`/`resetHoneyLevel`/`isSmokeyPos` 与照抄的 `angerNearbyBees`）。
- B17 工作台真实合成（牛奶管、蜂蜜管）与牛奶桶、蜂蜜瓶作材料时空桶、空瓶是否退回：没做，只有资源测试验证了配方形状与材料。
- 尖叫山羊的声音、哞菇、蜂窝（`bee_nest`）：没单独试。
- 扩展行格子里的空桶被取用：没试（按"主区域的格子"一视同仁，代码层面）。
- 声音是否真的播放（容器里无声卡，`Error starting SoundSystem`），只验证了逻辑路径。
- 中文界面与真机；多玩家。

## 已知局限

- 排放不会把容器里的液体装回罐（本 feature 范围外）。
- 空容器只认原版的空桶与空玻璃瓶（别的模组的空桶不认）。
- 蜂巢的取蜜重写了两小段原版 `private` 逻辑（激怒附近蜜蜂、判断有无蜜蜂），原版以后改了这两段，行为可能不同步。
- 牛奶管、蜂蜜管的客户端预测（`sidedSuccess(true)`）只决定挥手；服务端拒绝时（如罐满）玩家会看到挥手但没有效果，有聊天提示说明。

## 宪法复查

I 不涉及界面；II 容器的消耗、成品、液量、世界效果与提示都在服务端；III 只做牛奶管、蜂蜜管与排放；IV 牛、山羊、蜂巢、桶、瓶都是原版，油的桶用流体自己的 `getBucket()`；V 游戏内验证已做并如实记录；VI 图集前后都是 2048x1024；VII PNC 仍只是 `localRuntime`，主代码不引用，jar 里没有。
