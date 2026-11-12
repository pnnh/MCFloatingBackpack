# DELIVERY: 水管、油管、岩浆管（049）

状态：已完成，用户已在真机确认（2026-11-12，T026）。

## 基线与结果

- `./gradlew test`（实现前）：**181 个类、1371 个用例，0 失败**。
- `./gradlew test`（实现后）：**186 个类、1418 个用例，0 失败**（新增 5 个测试类、47 个用例：`PipePlannerTest`、`PipeKindTest`、`CooldownTest`、`PipeResourcesTest`、`PipeWiringTest`）。
- 共享方块图集（`design/demo/tools/check_atlas_budget.sh`，干净客户端）：加管子贴图**之前 2048x1024，之后 2048x1024**，通过预算。
- 发布的 jar（`./gradlew jar`）：不含 PNC（`unzip -l` 搜 `pneumaticcraft`、`me/desht` 为 0），`neoforge.mods.toml` 里没有它；管子的 3 个模型、3 张贴图、3 个配方都在里面。

## 与 spec/plan 的偏离

1. **目标位置已是同种源方块时不放置、不扣量**（spec 的边缘情况里写了，plan 没落实到步骤，实现时加了：按住左键、对着刚倒出的液体再点不会白白用掉一桶）。
2. **新增一种提示 `NO_FLUID`**：油罐里锁定的流体已不在注册表里（那个模组被卸载）时提示"添加这种原油的模组没有安装"，不扣量（spec 与 plan 没有列，属于 MC 层才会遇到的情况，`PipePlanner` 本身不产生它）。语言键 `message.mcfloatingbackpack.pipe.no_fluid`。
3. `PipeCooldowns` 把两个冷却（提示、放置节流）放在一起，登出时一起清；`PipeNotices` 是提示的发送端。
4. `scripts/ai-gui/README.md` 补了"在世界里做右键与左键"的经验（改绑键盘、搭试验台、F5、查液量的命令）。

## 协议号与数据修订（release-process.md 要求写明）

- **协议号仍是 19，数据修订仍是 2**：没有新增网络包（右键走物品的 `use()`，左键走两端都有的 `LeftClickBlock`）、没有新增存储字段（只改 048 已有的液量数值）、没有新增配置项与依赖。`PipeWiringTest` 有断言。
- PNC 只在 `build.gradle` 的 `-PcompatRuntime` 那一组里作 `localRuntime`（`maven.modrinth:pneumaticcraft-repressurized:8.2.24`），主代码不引用它的任何类（只认标签 `#c:crude_oil`），`DependencyRulesTest` 通过。

## 游戏内验证（容器 mcfb-c1，带 PNC 8.2.24，`-PnoJei`，AI 自动验证）

试验台：数据包函数在空中搭一块石头平台，`100 119 102` 放一个源方块（周围是石头），准星正对它；用改绑到键盘的"使用"（`u`）与"攻击"（`y`）操作；每次读聊天日志与 `data get entity @s Inventory[{Slot:8b}]…fluids`、`execute if block …`。

**验证了**（生存模式，除非注明）：
- B1 吸水：水管右键水源，源方块消失，`fluids.water` +1000。
- B2 吸岩浆与吸油：岩浆管、油管各 +1000；PNC 的石油 → `oil_fluid: "pneumaticcraft:oil"`（**PNC 在容器里能起来，`setblock ~ ~ ~ pneumaticcraft:oil` 放出的是源方块**）。
- B3 只认自己的液体：岩浆管、油管对着水源，水源保持、液量不变。
- B6 放置：水管左键石头顶面，在上面一格出现水源，液量 −1000，石头没有被挖掉；创造模式同样（`gamemode creative`：水源出现、石头还在、液量变化一致）。
- B7/B9 按住左键与冷却：按住 2.5 秒，只倒出一次（液量 1000→0），随后每秒一条"水罐里不足一桶"（3 条/2.5 秒），不是每 tick 一条。
- B8 满与空：`The Water tank is full`（15000→16000）、`The Water tank has no room for another bucket`、`The Water tank is empty`（放空）、`The Water tank holds less than a bucket`。
- B10 没有背包：`No floating backpack found`；B11 没有罐（罐在左区）：`Your backpack has no Water tank in its bottom area`。
- B12 油的锁定：背包里锁定为假的 `test:crude` 时，油管对 PNC 的石油 → `The oil tank already holds another oil (test:crude); empty it first`，石油保持；锁定的流体不在注册表里时左键 → `The mod that adds that oil (test:crude) is not installed`，液量不变。
- PNC 石油全程：吸收 1000 → 放置放出 PNC 石油（`pneumaticcraft:oil` 方块）→ `The Crude Oil tank is empty`，`fluids` 字段消失（锁定清除）。
- B13 潜行右键：什么也不发生（水源保持，液量不变）。
- B15 面板同步：面板开着时吸收，水罐上的量条立即出现（1000/16000 的 1 像素）。

**没验证的（如实记录）**：
- B4 流动的水不吸、B5 水面下有方块时吸水面、B14 保护区域内不能吸/放、B16 下界里放水蒸发：没有试。
- B17 真实合成（工作台）：没试，只有资源测试验证了配方形状与材料。
- 岩浆的放置（只验证了岩浆的吸收）。
- 对没有桶物品的模组流体的吸收边界（只有源码推断）。
- 中文界面下的提示文字；真机（Windows/Linux、鼠标键位）；多个玩家同时用。
- 直接用 `xdotool click` 做世界里的左右键：没有得到可信的结果（见 `scripts/ai-gui/README.md`），验证用的是改绑到键盘的键。

## 已知局限

- 按住左键时每 4 tick 最多倒一次（原版每 tick 发开始挖掘的包，服务端分不清点一下和按住）；同一位置已有同种源方块时不再倒。
- 管子只在主手生效。
- `BackpackGate.acquire` 返回空时（被保护的背包，或已不是背包）提示"这个背包是更新版本保存的，暂时不能用"，对后一种原因措辞不准，很少见。
- 没有桶物品的模组流体（如果有）的吸收只由"返回非空或不再是源方块"判断，没有真实模组可测。

## 宪法复查

I 不涉及界面；II 吸收、放置、液量、提示都在服务端，客户端只取消挖掘；III 只做三根管子；IV 水与岩浆只用原版机制，油只认标签；V 游戏内验证已做并如实记录；VI 图集前后都是 2048x1024；VII PNC 只作开发运行时依赖，主代码不引用它，jar 里没有。
