# Quickstart: 分类扩展——数据与规则验证

035 没有界面也没有扩展件物品，玩家可见的变化只有一个：标签管理器的预设下拉多了 3 项。所以验证分三块：**单元测试**（规则）、**游戏里的回归**（现有功能不坏、数据往返）、**`/give` 构造的数据检查**。先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`：有客户端在运行就不要再启动；改了代码要重启客户端。跑完 Gradle 后 `./gradlew --stop`。

## A. 自动化

```bash
./gradlew compileJava test
design/demo/tools/check_atlas_budget.sh   # 先 pgrep；本 feature 不新增贴图，应仍是 2048×1024
```

新增与更新的测试：

| 测试 | 覆盖 |
|---|---|
| `RowReconcilerTest` | contracts/rules.md 第 1 节的 R1～R12（含随机序列的物品总数不变） |
| `ExtensionRulesTest` | `canPlace`、`targetSegments`（含多类别匹配的顺序、没有匹配行时与现状一致）、`fits`（上限 35、接回不受限、光标已计入）、`rowOfSlot` |
| `CategoryMatcherTest` | 两个来源的"或"、事后删标签不影响已放入的物品、标签册每只背包各一份 |
| `RowSetTest` | 构造的不变式（9 格、总数 ≤ 35、隐藏行非空）、`hiddenSummary`、`visibleCategories` |
| `ExtensionTagsResourcesTest` | 10 个标签文件存在、`replace: false`、`#c:`/`#minecraft:` 引用带 `required: false`、内容与 spec 的并入关系一致；`PresetTags` 13 个 id、中英文语言键齐全 |
| `PresetTagsTest`（更新） | 完整 id 列表增加 `crop potion disc`，前 10 个顺序不变 |
| `DependencyRulesTest`（已有） | 没有引入第三方类型 |

## B. 游戏里（`./gradlew runClient`，开发世界用 2026-11-09 之后新建的那个）

### B1. 回归：现有功能与旧背包

旧存档里的背包（035 之前保存的）打开：物品、工具区、标签、垃圾桶内容、自动拾取黑名单都在。然后过一遍：点击放入拿出、拖拽、双击聚拢、Ctrl 快速转移（物品栏 ↔ 面板）、选区、标签管理器增删标签、垃圾桶、自动拾取、升级（皮革 → 铁，内容保留）、关闭重进世界。

### B2. 标签预设

打开标签管理器的下拉：现有 10 个预设顺序不变，后面多了 3 个——中文"农作物、药水、唱片"，英文 `crop`、`potion`、`disc`。给一个物品选 `crop`，在标签总览里能看到；`plant` 仍在，没有被改。

### B3. `/give` 构造的行数据往返（核对、保存、网络）

```text
/give @s mcfloatingbackpack:leather_floating_backpack[mcfloatingbackpack:backpack_contents={items:[],extension_rows:{visible:[{category:"food",cells:[{id:"minecraft:bread",count:3},{},{},{},{},{},{},{},{}]}],hidden:[]}}]
```

（组件 id `mcfloatingbackpack:backpack_contents` 与字段名 `items`、`extension_rows`、`visible`、`hidden`、`category`、`cells` 已对照源码核对；**命令本身还没有在游戏里试过**，若解析报错，按报错调整 SNBT 的写法，不要改数据结构。）

1. 给自己：背包栈到达客户端没有断线、没有日志报错（**组件同步用到手写的网络编码**）。
2. 按 B 打开：035 里没有扩展件物品，`placed` 恒为 0，所以载入时那个**非空的可见食物行被移到隐藏行**；面板里**没有**多出任何格子（主区域仍是皮革档的 36 格），布局与平常一样。
3. 在面板里随便挪动一个物品（触发一次保存），再关闭面板，`/data get entity @s Inventory`（或看物品提示）确认背包数据里 `extension_rows.hidden` 有那一行、`bread × 3` 还在。
4. 重新进入世界，再检查一次：数据仍在。
5. 用一个空的可见行重做一遍：载入后**被删除**（隐藏行里没有它）。
6. 把带隐藏行的背包用锻造台升级：隐藏行与里面的面包都还在（升级配方带上了 `rows`）。

### B4. 大背包的日志检查

下界合金背包（108 格）装满带数据的物品（例如装满东西的潜影盒），打开面板：日志里没有网络解码错误，也没有 900,000 字节的保护警告；然后记下面板能否正常打开。**这不是 423 格的最坏情况**（035 里造不出扩展行），只是确认协议 17 下大背包没有回归。

## C. 035 里无法验证的（如实记录，不声称已满足）

- 带物品的**可见**扩展行在会话/整份同步里的往返，以及 `ClientBackpackView` 存下的新字段：035 里可见行总被核对掉，要等 036/037 有界面和扩展件物品。
- 扩展行里放物品的各条路径（点击、拖拽、聚拢、快速转移、自动拾取）：只有单元测试，没有游戏验证。
- 423 格的最坏体积：没有实测；组件同步通路没有我们的保护（见 contracts/wire.md 第 4 节）。
- 保存时可见行结构变化后补发整份同步：没有扩展件物品就走不到这条通路。

## D. 如果某项失败

- B1 失败（旧背包打不开、物品缺失）：先查 `BackpackContents.CODEC` 的 `optionalFieldOf` 与手写网络编码的字段顺序。
- B3 第 1 步断线：手写网络编码的 `encode`/`decode` 字段顺序不一致。
- B3 第 6 步行丢了：`BackpackUpgradeRecipe.assemble` 没有带 `rows`。
- B2 下拉里预设顺序变了：`PresetTags` 追加位置不对。
