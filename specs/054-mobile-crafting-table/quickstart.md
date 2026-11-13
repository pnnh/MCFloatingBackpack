# Quickstart: 移动工作台的验证

规则见 [contracts/rules.md](./contracts/rules.md)，资源见 [contracts/resources.md](./contracts/resources.md)。

## A. 单元与资源测试

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.tool.*' --tests '*MobileCraftingTableResourcesTest' --tests '*MobileCraftingWiringTest'
./gradlew compileJava test
```

覆盖点：`MobileToolTest`、`ToolOpenPlannerTest`（决策表）、`MobileCraftingTableResourcesTest`、`MobileCraftingWiringTest`；`ToolSingleRulesTest` 已有；既有测试只改写死协议号 19 与修订 4 的断言。

## B. 游戏内（容器自动验证，宪法 V）

前提：`scripts/ai-gui` 的容器（先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；`-PnoJei`）。造背包用数据包函数：

```text
item replace entity @s hotbar.8 with mcfloatingbackpack:iron_floating_backpack[mcfloatingbackpack:backpack_contents={items:[],tools:{right:[{id:"mcfloatingbackpack:mobile_crafting_table",count:1},{id:"mcfloatingbackpack:mobile_jukebox",count:1},{id:"mcfloatingbackpack:trash_can",count:1}]}}]
give @s minecraft:oak_planks 8
```

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 打开 | E 开物品栏，B 开面板，左键点右区第一格的移动工作台 | 原版合成界面打开（3×3 合成格、结果格、配方书），标题"合成"；面板仍叠在上面 |
| B2 合成 | 把 4 块橡木板放进合成格的 2×2，取走结果 | 结果是工作台，板被扣掉；关界面材料退还 |
| B3 再点关闭 | 界面开着，再点右区的移动工作台 | 界面关掉 |
| B4 工具离开 | 界面开着，右键拿起右区的移动工作台 | 界面在 1 秒内被关掉 |
| B5 光标 | 光标上拿着东西，点另一个工具后再点移动工作台（或先拿起再放回） | 物品不丢 |
| B6 别的菜单 | 开着箱子界面时点移动工作台 | 箱子界面被替换为合成界面，光标上的东西退还 |
| B7 限制 | 右区再放第二个移动工作台；放在左区、下区点它 | 第二个被拒并有提示；左区、下区的点了没有反应 |
| B8 创造模式 | `/gamemode creative`，点移动工作台 | 同样能打开 |
| B9 宿主界面 | 没有界面时（只有面板）点工具，再关合成界面 | 合成界面打开；关掉后面板接回（宿主界面） |
| B10 数据与图集 | 保存读取；`check_atlas_budget.sh` 前后各一次 | 背包内容零变化、修订 5、协议 20；图集 ≤ 2048 |

**不能自动判断的**：手感与界面衔接、JEI / EMI 的配方转移（容器里 `-PnoJei`）、别的模组的配方——记为需用户确认。
