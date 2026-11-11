# Delivery: 分类扩展：数据与规则

**状态**：代码与自动化已完成（2026-11-10）；**游戏里的验证（quickstart B1～B4 / 任务 T039～T041）等用户确认，尚未做**。没有提交 Git。

## 基线与结果

| | 类数 | 用例数 | 失败 |
|---|---|---|---|
| 开始前（034 之后） | 104 | 738 | 0 |
| 完成后 | 109 | 784 | 0 |

新增测试类：`RowSetTest`、`RowReconcilerTest`、`ExtensionRulesTest`、`CategoryMatcherTest`（都在 `inventory/extension/`）、`ExtensionTagsResourcesTest`；更新了 `PresetTagsTest`、`PanelCursorRequestTest`、`TagRequestTest`、`TrashSendRequestTest`（上限从 108 改为 423）、`ToolAreaRulesTest`（加一条扩展件不受"每区一个"限制）。

图集：`design/demo/tools/check_atlas_budget.sh` 干净环境 **2048×1024，通过**（035 不新增贴图）。

## 做了什么

- **纯逻辑**（`inventory/extension/`，不引用 Minecraft 类）：`ExtensionCategories`（10 个类别 id、9 格、35 行、108/423 常量）、`Row`、`RowSet`（可见与隐藏两个有序列表、`sanitize`、隐藏汇总）、`RowReconciler`（按放着的数量核对）、`ExtensionRules`（`canPlace`、`targetSegments`、`fits`、`rowOfSlot`）、`CategoryMatcher`。
- **游戏一侧的适配**：`ItemCategoryMatcher`（数据标签 + `food`/`potion_contents` 组件 + 这只背包的标签册）、`ExtensionPacks`（"扩展件解析器"，035 里没有注册任何扩展件，所以什么都认不出）、`ExtensionPackRules`（放扩展件进工具区时检查行总数）、`ExtensionRowsCodecs`、`RowView`。
- **数据**：`BackpackContents` 加第 7 个字段 `rows`（磁盘 `extension_rows` 是可选字段；网络编码改成手写）；`BackpackInventory` 的主区域 = 档位容量 + 可见行的格子，载入与保存时核对；`BackpackUpgradeRecipe` 升级时带上 `rows`；10 个数据标签文件 `data/mcfloatingbackpack/tags/item/extension/*.json`；预设标签增加 `crop`、`potion`、`disc`（中英文语言键各 3 条）。
- **放入规则**：往主区域写入的地方：序列型（快速转移、容器 → 面板、垃圾桶取回、自动拾取）都改成"匹配的可见行 → 档位容量的格子"（`QuickTransferLogic.moveIntoSegments`，`moveInto` 本身没改）；单格型（点击、菜单光标、主手交换、拖拽分配）放入前检查 `canPlaceMain`；取出永远允许。
- **常量与协议**：`MAX_SIZE` 删除，拆成 `MAX_TIER_CAPACITY = 108`（档位容量、`items` 的编码）与 `MAX_MAIN_SLOTS = 423`（会话/整份同步的格子列表、所有请求的格子数校验）；协议版本 16 → 17；会话与整份同步包手写编码并带上 `RowView`（档位容量、可见行类别、隐藏汇总）；发送前量编码大小，超过 900,000 字节不发、记警告并按"没有背包"处理；`BackpackRef.saveInventory` 发现可见行结构变了就补发整份同步；`ClientBackpackView` 存下 `RowView`（035 不用它改布局）。

## T035 写入路径核对结果

`grep -rn "setStackInSlot\|moveInto\|PickupInsert\|insertItem"`（`src/main/java`，不含客户端与 Curios 的来源）逐个看过：

| 路径 | 结论 |
|---|---|
| `BackpackQuickTransferPayload.inventoryToBackpack` | 改为 `backpack.insertIntoMain` |
| `BackpackTakeFromContainerPayload` → `ContainerToBackpack.take` | 改为每个栈取 `mainTargetOrder` 的分段 |
| `BackpackTrashPayload.restore` → `TrashBin.restore` | 同上 |
| `AutoPickupEvents` → `PickupInsert.insert` | 改为分段重载 |
| `BackpackClickPayload`（点击、菜单光标、主手交换） | 放入前统一检查 `canPlaceMain`，不合格拒绝并 `sendSlotUpdate` |
| `BackpackCursorGesturePayload` | 拖拽分配过滤掉 `canPlaceMain` 为假的格子；聚拢只取出，不检查；`pickupAll` 范围是 `0..size()-1`（主区域 + 可见行） |
| `BackpackClickLogic`、`BackpackMenuBridge` | 只有 `BackpackClickPayload` 调用，已在它那里检查 |
| `BackpackQuickTransferPayload` 的 backpack → 物品栏/容器、`BackpackTrashSendPayload` | 只从背包取出，不受影响（倾倒垃圾的范围是 `size()`，含可见行、不含隐藏行） |
| `CreativeCursorPush`、`MergePanelCursorPayload`、`OpenContainerDelivery`、`ContainerMenuAccess` | 只动光标或往容器里放，不写背包格子 |

## 用户验证记录与一次背包丢失事件（2026-11-10）

- **B2**（标签预设）：用户在下拉里只看到 `stone wood food ore trash` 五项，直接输入搜索能看到新加的标签。**原因**：018 起下拉最多显示 5 项（`TagPanelLayout.DROPDOWN_MAX = 5`），超出的只能靠输入搜索；13 个预设里新增的 3 个排在最后，所以看不到。035 没有改这个上限，**不是 035 的缺陷**，但 13 个预设下新增项只能靠搜索，是体验问题，留给以后（放宽上限或做滚动）。
- **B3**（`/give` 带 `extension_rows`）：用户拿到的"都是普通背包"——这是预期的（035 没有扩展件物品，载入时被核对掉，面板上看不出区别）；用 `/data get entity @s Inventory` 输出太长无法检查。**搁置**，以后再测。
- **B4**：未测。
- **B1**：用户没有给出明确的通过结论，**仍是未验证**。
- **一次严重事件（背包丢失）**：创造模式、"生存物品栏"页签、钻石背包**拿在主手**、面板与物品栏同时打开。点击面板主区域里有物品的格子，物品消失（物品栏里也没有）；再点几次，整只钻石背包消失。不总能复现；用户用 030 的备份恢复得到了背包副本，备份之后放入的物品没有找回。用户不确定 035 之前是否出现过。
  - **查证**：`latest.log` 里唯一的异常是 Iris（光影）的 `Unknown variable: BIOME_PALE_GARDEN` 等，与背包无关；我们的模组在整个会话里没有一行 ERROR/WARN。服务端发现背包不在时只是静默关闭面板，不会写日志。快照时间线：钻石背包 `218ffba0` 在 09:50:16 有一份快照，用户恢复出来的就是它。
  - **读 035 的改动**：载入/保存、放入检查、常量与编码，没有找到会清空或替换背包栈的路径；这只是读代码，**不是证明**。
  - **推测（没有证据）**：创造模式的玩家物品栏以客户端为准（客户端发 `SetCreativeModeSlot`，服务端照收），再加上 027 的"创造光标镜像"（服务端菜单光标由客户端报告覆盖）：如果客户端那一刻报告的光标是空的，而服务端菜单光标里刚好有从面板拿起的物品，物品就会消失且不留日志；背包本身怎么消失没有想出机制。
  - **结论**：**原因未确定**。按用户的决定，先加诊断日志后搁置，以后再出现时靠日志排查（见下）。

## 为追查这次事件加的诊断

- **日志级别设置**：客户端配置（`config/mcfloatingbackpack-client.toml`）和服务端配置（世界的 `serverconfig/mcfloatingbackpack-server.toml`）各有 `[logging] level`，取值 `OFF / ERROR / WARN / INFO / DEBUG`，默认客户端 `WARN`、服务端 `INFO`；游戏的"模组"列表里的设置界面也能改。DEBUG 会把每次面板点击与创造光标消息写进 `latest.log`（行数很多）。
- **面板操作环形记录**：服务端对每个玩家**始终**在内存里保存最近 40 条面板操作（点击、创造光标报告与回写、合并面板光标、光标手势、快速转移，含点击前后格子、面板光标、菜单光标、手上物品的简述，以及"没有背包可解析 / 闸门拒绝"这类被静默忽略的情形），与日志级别无关。
- **背包失踪检测**（`diag/BackpackWatchService`）：每秒看每个玩家的背包在哪（物品栏、护甲、副手、光标、打开的容器、面板解析出的穿戴/手持背包）；上一秒有、这一秒没有的背包，若附近 16 格内没有掉落物，就写一条 `WARN`（背包简号、最后所在位置、游戏模式、打开的菜单），并跟着列出该玩家最近的面板操作；只是被丢在地上的写一条 `INFO`。这是线索而不是定论：放进末影箱之类自己没在看的容器也会被报为"不见了"。
- 自动化：`DiagLogicTest`（日志级别、环形记录、失踪检测的逻辑）；全部测试 110 个类、789 个用例、0 失败。**这些诊断本身没有在游戏里验证过。**

## 未验证（如实记录，不声称已满足）

1. **游戏里的验证 B1～B4 / T039～T041**：旧背包与现有功能不回归、标签预设多 3 项、`/give` 构造的行数据往返、大背包没有回归——都在等用户确认。
2. **标签数据文件的加载**：T038 只读了启动到主菜单的日志（0 个 ERROR），**但物品标签是进世界后才加载**，所以"`required: false` 的标签引用缺失时不报错"要在 B1 里进世界后看日志才算数。
3. **带物品的可见扩展行**在会话/整份同步、组件同步里的往返：035 没有扩展件物品，可见行总会在载入时被核对掉，只能等 036/037。
4. **扩展行里放物品的各条路径**（点击、拖拽、聚拢、快速转移、自动拾取、垃圾桶取回）：只有纯逻辑的单元测试（`ExtensionRulesTest` 里用假栈验证目标顺序与 `moveIntoSegments`），没有游戏验证；`BackpackInventory`、`ItemCategoryMatcher`、`ExtensionPackRules` 这些依赖 Minecraft 类的适配没有单元测试。
5. **保存时可见行结构变化后补发整份同步**（`BackpackRef.saveInventory`）：没有扩展件物品就走不到这条通路。
6. **`/give` 命令的 SNBT 写法**：字段名已对照源码，但命令本身没有试过。

## 已知限制

1. `BACKPACK_CONTENTS` 作为数据组件随每个背包栈走原版的物品同步，我们的 900,000 字节保护管不到这条通路。以前 108 格已有同样的暴露面，423 格把理论最坏值放大约 4 倍；只在玩家故意往扩展行里塞大量带大数据的物品时才会碰到。035 不处理，**最坏体积没有实测**。
2. 客户端布局还在用 `slots.size()`（含可见行的主区域格数）算合法尺寸：只要有可见行，布局就会不对。035 里可见行造不出来（没有扩展件物品），布局归 036。`ClientBackpackView.rowView()` 已经带着 `baseCapacity` 给 036 用。
3. 预览（031 界面）仍只显示档位容量那部分，扩展行不在预览里。
4. 标签面板只显示手动标签，不显示按数据标签推断出的类别（面包不会显示 `food`）。
5. `food` 同时是类别标签和自动拾取黑名单可填的标签名（既有行为：标签就是名字）。

## AI 自动验证（2026-11-11，Linux 容器内 `runClient`）

环境与方法见 `docs/plan/ai-gui-verification.md`、`scripts/ai-gui/README.md`。这一节补上此前"搁置/未验证"的 B3、B4。

- **B3 通过**（`/give` 带 `extension_rows` 的皮革背包，组件字段名与 quickstart 里的命令一次就解析成功）：
  1. 给自己：没有断线，日志没有新增错误。
  2. 按 B 打开：主区域仍是 36 格，标题图标右上角出现黄点（有物品的隐藏行）。
  3. 放一件物品触发保存、Save and Quit 后的 `playerdata` NBT：`extension_rows` 只剩 `hidden`，里面是 `category:"food"` 的一行，`bread × 3` 还在；可见行已被移走。
  4. 重新进入世界：同一只背包（编号 WE2Y）黄点仍在，内容不变。
  5. 空的可见行：载入后被删除（保存后的 NBT 里没有 `extension_rows`）。
  6. 锻造台升级（皮革 → 铁）：升级后的铁背包 NBT 里 `extension_rows.hidden` 仍有那一行，`bread × 3` 还在。
- **B4 通过**：用数据包函数生成下界合金背包，装 108 个各含 12 组圆石的潜影盒（命令约 7.6 万字符）；打开面板：正常，日志里没有解码异常，也没有 900,000 字节的保护警告。**这不是 423 格的最坏情况**，只是确认协议 17 下大背包没有回归。
- 顺带：进世界后用 `/clear @s` 清掉旧背包会触发"Backpack … is gone"的诊断 WARN，是预期的。
