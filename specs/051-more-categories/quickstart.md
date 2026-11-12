# Quickstart: 9 个新类别的验证

标签内容见 [contracts/categories.md](./contracts/categories.md)，配方、颜色、贴图见 [contracts/recipes-and-assets.md](./contracts/recipes-and-assets.md)，类型见 [data-model.md](./data-model.md)。

## A. 单元与资源测试（不需要游戏）

```bash
./gradlew test --tests 'pnnh.mcfloatingbackpack.inventory.extension.*' \
  --tests 'pnnh.mcfloatingbackpack.inventory.tags.*' \
  --tests 'pnnh.mcfloatingbackpack.client.panel.CategoryStyleTest' \
  --tests 'pnnh.mcfloatingbackpack.resources.*Extension*'
./gradlew compileJava test
```

覆盖点：`ExtensionCategoriesTest`（19 个 id、`ALL` 20 个、新 id ≤ 16 字符、旧 10 个的顺序不变）、`PresetTagsTest`（21 个预设，旧 13 个顺序不变）、`CategoryStyleTest`（19 个类别各有样式、边色互异、与槽位灰 ΔE ≥ 20、两两 ΔE ≥ 15）、`ExtensionTagsResourcesTest`（9 个标签 JSON、`#` 条目 `required: false`、抽查物品表、不含 `#minecraft:shulker_boxes` 与 `#c:dyed`）、`CategoryExtensionResourcesTest`（模型、贴图 16×16、配方图样、中英文名、图标互不相同）、`DataRevisionTest` 等（当前修订 3；`BackpackContentsRevisionTest` 里写死的 2 同步）、`ItemCategoryMatcher` 源码文本检查（`enchant` 认 `STORED_ENCHANTMENTS` 不认 `ENCHANTMENTS`）、`DependencyRulesTest`。011～050 的其它测试不改。

**抽查物品表**（每类至少 5 件应收、3 件不应收；用于资源测试断言"标签 JSON 里有/没有"这些条目或标签）：

| 类别 | 应收（展开后） | 不应收 |
|---|---|---|
| building | 石砖楼梯、橙色混凝土、玻璃板、陶瓦、橡木栅栏门、磨制安山岩 | 圆石、橡木原木、泥土 |
| natural | 石头、沙子、砂砾、黏土、雪块、灵魂沙 | 石砖、小麦、橡树树苗 |
| plant | 蒲公英、橡树树叶、红蘑菇、仙人掌、脑珊瑚扇、藤蔓 | 橡树树苗、小麦种子、南瓜 |
| decor | 红色染料、白色蜡烛、灯笼、画、红色床、旗帜 | 圆石、面包、钻石 |
| mobdrop | 骨头、线、火药、末影珍珠、黏液球、烈焰棒 | 圆石、铁锭、面包 |
| storage | 箱子、木桶、潜影盒、工作台、熔炉、讲台 | 铁轨、书、圆石 |
| transport | 铁轨、动力铁轨、矿车、橡木船、鞍、鞘翅 | 箱子、骨头、圆石 |
| enchant | 书、青金石、附魔台、书架、经验瓶、铁砧（附魔书靠组件，游戏里验） | 钻石镐、铁锭、面包 |
| meat | 熟牛肉、生鳕鱼、熟鸡肉、腐肉、熟羊肉、生三文鱼 | 面包、苹果、胡萝卜 |

## B. 游戏内（容器自动验证，宪法 V）

前提：`scripts/ai-gui` 的容器（先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`；`-PnoJei`）。造背包用数据包函数（聊天命令最长 256 字符）；每次验证读 `latest.log` 与 `/data get`，不靠 OCR；颜色与图标的主观观感请用户确认。

| 场景 | 步骤 | 预期 |
|---|---|---|
| B1 创造栏 | 创造物品栏 `main` 页 | 9 个新扩展件在现有分类扩展之后、通用扩展之前，顺序同类别表 |
| B2 合成 | 9 个配方各合成一次（`give` 材料） | 每个出 1 个；JEI 配方页（若开）图样一致 |
| B3 放置与新行 | 铁背包下区放 `building_extension` | 主区域多一行 9 格，底色与幽灵图标出现 |
| B4 匹配与拒绝 | 对每个新行：放 2 件应收 + 1 件不应收 | 应收进格；不应收被拒（零变化） |
| B5 多类别 | 放石砖楼梯到 wood 行与 building 行都有的背包 | 先进 wood 行，wood 行满了才进 building 行（035 规则） |
| B6 肉 | `meat` 与 `food` 行都有；放熟牛肉 | 先进 `food` 行；`food` 行满了进 `meat` |
| B7 附魔书 | 放附魔书到 `enchant` 行；放附魔钻石镐 | 书进格；镐被拒 |
| B8 手动标签 | 用标签管理器给圆石加 `building` | 圆石能进 building 行 |
| B9 预设下拉 | 标签面板输入 `bu` / `建` | 下拉里有"建筑方块"；空输入仍只显示前 5 个 |
| B10 黄点 | building 行里有物品时拿走 `building_extension` | 标题栏黄点；悬停与 `/hidden` 里有"建筑方块"与格数 |
| B11 快照 | 对带新行的背包做快照再恢复 | 行与内容一致 |
| B12 数据修订 | 旧背包（修订 2）打开保存 | `data_revision` = 3，物品无丢失 |
| B13 降级拦截 | `-Pmcfb.stampRevision=4` 造"更新版本"背包 | 040 保护提示（既有路径，抽查一次） |
| B14 图集 | `check_atlas_budget.sh` 前后各一次 | 尺寸 ≤ 2048，记录前后值 |

**不能自动判断的**：9 种底色是否好看、18 像素槽里贴图能否分辨、旧版本（降级）的真实表现——记为需用户确认。
