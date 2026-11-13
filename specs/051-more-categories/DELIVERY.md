# DELIVERY: 更多分类扩展（9 个新类别，051）

状态：已完成，用户已确认（2026-11-13，T025）：底色、贴图观感、9 个类别。已提交推送（2625410）。

## 基线与结果

- `./gradlew test`（实现前）：**187 个类、1444 个用例，0 失败**。
- `./gradlew compileJava test`（实现后）：**187 个类、1447 个用例，0 失败**（新增：`CategoryStyleTest` 的色差测试、`ExtensionTagsResourcesTest` 两条；`ExtensionCategoriesTest`、`RowSetTest`、`PresetTagsTest`、`DataRevisionTest`、`CategoryExtensionResourcesTest` 等改为新数量）。`DependencyRulesTest` 通过。
- 变异检查：把 `building.json` 里的 `#minecraft:stairs` 删掉，`ExtensionTagsResourcesTest` 失败；还原后通过。
- 共享方块图集（`design/demo/tools/check_atlas_budget.sh`，干净客户端）：**之前 2048x1024，之后 2048x1024**，通过预算（9 张 16×16 没有让图集变大）。

## 改了什么

- `ExtensionCategories.IDS` 追加 9 个：`building natural plant decor mobdrop storage transport enchant meat`（`ALL` 变 20 个）；`PresetTags` 追加 8 个预设（`plant` 已有，共 21 个）；`CategoryStyle.EDGE` 加 9 种底色；`ItemCategoryMatcher` 让 `enchant` 认 `STORED_ENCHANTMENTS`（不认 `ENCHANTMENTS`）。
- 数据：9 个标签 JSON（`#` 条目都 `required: false`；逐个列出的物品 id 全部对照 1.21.1 物品清单核对过：building 95 个、natural 58、plant 52、decor 22、mobdrop 13、storage 1、transport 10、enchant 11、meat 0）、9 个配方（`IAI/BDB/IEI`，材料同 spec）、9 个模型、9 张手绘贴图（`make_category_extension_icons.py` 追加 9 个图案，旧 10 张逐字节不变）、中英文物品名与 8 个预设名。
- 没有新类、新网络包、配置项、依赖；协议号仍 19。

## 与 spec/plan 的偏离

1. 标签清单按 research 修正：`#minecraft:shulker_boxes` 不存在，用 `#c:shulker_boxes`；`#c:dyed` 太宽没用。
2. `never_trash`：现有扩展件都不在里面，所以新扩展件同样没加（FR-006 的括号条件）。
3. 英文预设名按现有风格用小写短语（`building blocks`、`natural blocks`、`mob drops`、`meat & fish` 等），所以行的悬停提示读作 "building blocks row: only takes building blocks items"。
4. 历史测试里"数据修订是 2"的断言（`TankWiringTest`、`PipeWiringTest`）改为 3 并加注释；`RowSetTest`、`ExtensionCategoriesTest` 里拿 `plant` 当"未知类别"的例子改成 `zzz`（`plant` 现在是类别）。

## 数据修订与协议号

- **`DataRevision.CURRENT` 2 → 3**：新扩展件是只在背包功能区里生效的新物品种类，新类别 id 存进 `extension_rows`；旧版本读到带它们的背包会丢失物品，所以要让 040/041 的降级保护生效。游戏里验证：新版本保存的背包 `data_revision` = 3。发布说明要提醒"降级前先备份存档"。
- **协议号不变（19）**：类别 id 在既有网络包里是字符串，没有新包；客户端按自己的 `IDS` 渲染。

## 已知变化与缺口

- **用户自建的手动标签**如果恰好叫 `building`、`natural`、`decor`、`mobdrop`、`storage`、`transport`、`enchant`、`meat`，升级后它们成为"类别标签"（带这个标签的物品能进对应的行）；影响面很小，不做迁移。
- **覆盖缺口**（约 205 个，约 16 %）：80 个刷怪蛋、27 个锻造模板与旗帜图案、28 个按钮与压力板、11 种桶、各种矿物整块、信标、刷怪笼、烟花、地图、TNT 等；通用扩展可收，也可用标签管理器给它们打类别 id 的手动标签。按钮与压力板更像红石，但改旧类别不在本 feature 范围。
- **重叠**：新类别排在旧类别之后，重叠物品先进旧行（035 规则）：熟牛肉先进 `food` 行，木楼梯先进 `wood` 行。

## 游戏内验证（容器 `mcfb-c1`，创造模式取物 + 生存模式面板；证据来自聊天日志、截图与存档转储）

| 项 | 做了什么 | 结果 |
|---|---|---|
| 标签内容（B4 的数据部分） | 数据包函数 `c51:tags`：把 81 件物品（9 类 × 6 件应收 + 3 件不应收）放进容器格，用 `execute if items … #mcfloatingbackpack:extension/<类>` 判定 | **81 项全部符合**（54 应收、27 不应收），聊天日志逐条 `T51 PASS`，没有 `FAIL`；加载没有标签或配方解析错误 |
| 配方（B2 的数据部分） | `recipe take` 对 plant、meat 成功，对不存在的 id 报 Unknown recipe | 9 个配方由资源测试逐项断言；游戏里只抽查了 2 个能被识别，**没有逐个在工作台合成** |
| 物品与行（B3） | 给一只下界合金背包，三个工具区放 9 个新扩展件，`extension_rows` 带 9 行 | 面板里 9 个扩展件图标都在，9 行各有底色、分隔线与空格幽灵图标；悬停提示 "storage row: only takes storage items" 等 |
| 放入与拒绝（B4、B6、B7） | 点击拾起再放下：附魔书→附魔行 ✔、**带附魔的钻石镐→附魔行 ✘**；蒲公英→植物行 ✔、橡树树苗→植物行 ✘；熟牛肉→肉与鱼行 ✔、面包→肉与鱼行 ✘；石砖楼梯→建筑行 ✔、圆石→建筑行 ✘ | 与预期一致；被拒时提示 "This item does not fit this row"（红字）；这也验证了 `enchant` 的组件分支（附魔书进、附魔镐不进） |
| 黄点（B10） | 把 building 扩展件拿出工具区（行里有楼梯） | 标题栏出现黄点，行被隐藏；物品没丢（存档里 `extension_rows` 仍含 `stone_brick_stairs`）。`/hidden` 里的类别名**未再验证** |
| 数据修订（B12） | Save and Quit 后用 `bp_dump.py` 转储玩家数据 | 新保存的背包 `data_revision` = **3**，8 个扩展件、牛肉、蒲公英、附魔书、楼梯都在，没有丢物品 |
| 图集（B14） | `check_atlas_budget.sh` 前后各一次 | 都是 2048x1024 |

**没有验证**：B1 创造栏里 9 个扩展件的排列顺序（只靠 `ModItems` 按 `IDS` 循环加源码测试）；B2 逐个合成；B5 木楼梯进 wood 行与 building 行的先后；B6 里 food 行已存在时熟牛肉先进 food 行（只验了没有 food 行时进 meat 行）；B8 手动标签；B9 预设下拉的实际显示；B11 快照；B13 降级拦截的抽查；旧版本真实打开新背包的表现（不支持降级，口径不变）。以上都不影响数据或安全，用户真机若发现问题再补。**颜色是否好看、贴图在 18 像素格里能否分辨：待用户确认**（目视：9 种底色在面板里能互相区分，贴图在槽位大小下也能认出砖墙、草方块、花、画框、骨头、箱子、铁轨、书、牛排，但这是我的观感，不是用户的）。

## 测试世界的残留

容器里的测试世界 `New World` 多了数据包 `c51`（`tags`、`bp`、`items`、`rec` 四个函数）与一只测试背包；改动前的世界已备份在容器内 `/tmp/New World.bak051`。仓库里没有调试入口。

## 宪法复查

I（只是新行底色）、II（放入规则仍在服务端）、III（一个 feature、一次做完 9 类）、IV、VII（无新依赖）通过；V：容器里验证并记录了没验证的；VI：图集前后都 2048x1024。无违规项。
