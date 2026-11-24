# Quickstart：验证 066

## A. 自动测试（`./gradlew test -PcleanRuntime -PnoJei`，构建用 `./gradlew build -PcleanRuntime -PnoJei`）

新增（纯逻辑，无 Minecraft 类型）：
- `AllAddressingTest`：前缀和、`locate`/`flatOf` 往返、边界（0、1、16 个成员、成员行数不同、越界）；
- `AllGridTest`：格子首尾相连、没有空位、总长度；
- `AllRequestRulesTest`：会话不在 ALL / epoch 不等 / 身份对不上 / 档位过高 / 保护中 → 拒绝；
- `AllContentDiffTest`：引用相同 → 未变；列表内容相同但引用不同 → 未变；一格不同 / 行分类变 / 标签变 → 变；
- `CursorFlushTest`：成员 → 物品栏 → 丢弃的顺序、总数守恒、成员背包都满、物品栏也满；
- `AllSortPermutationTest`：是置换、稳定、非空在前、分类行优先（对照 047 `BackpackSorter` 的规则）、同一输入同一输出；
- `AllSearchMapTest`：命中紧凑排列、整理后按显示顺序、命令（`/hidden`）不显示格子；
- `SlotAccessTest`：`distribute/gather` 经适配后与原单背包行为一致（对照现有 `PanelCursorLogicTest`）；
- 接线测试 `AllWiringTest`（延伸 `CombinedWiringTest`）：见 contracts/server-requests.md 的 4 条；
- 协议接线：三个带 `epoch` 的包的编解码往返；`CombinedMemberSyncPayload`/`CombinedCellUpdatePayload` 往返；`CombinedMembersPayload.all` 往返；协议号 `versioned("24")`。

更新：17+ 个断言 `versioned("23")` 的旧接线测试（改 `"24"`）；065 里断言"默认选中 B 的那只"/"标签栏无全部"的测试；`CombinedBarLayoutTest`（加"全部"标签）；`SearchTermsContractTest` 期望协议号。

## B. 容器自动验证（沿用 `scripts/ai-gui`，容器 `mcfb-c1`，见记忆"AI GUI 验证容器"）

准备：穿联合背包（钻石或下界合金档，才连得上多档背包）、物品栏放 3～4 只背包（含一只有分类行的、一只从未打开过的），各放不同物品。

1. U 打开：默认选中"全部"，网格按成员顺序、每只从新的一行开始；各成员染色与标签色块一致。
2. 悬停：提示末尾有"来自 ■编号（位置）"；对应标签高亮。
3. 取出一组 A 的物品 → 放进 B 的空格 → 切到 A、B 标签核对（总数守恒）。
4. 光标上有 A 的物品：点具体标签 → 冲洗（回到成员背包）后切换，不拒绝；再测按 U 关闭；再测把 A 丢掉（成员表变）。
5. 背包与物品栏都满时冲洗：物品落在脚下（不消失）。
6. 拖动分摊跨两只背包的格子；双击收集；Shift 点击取出到物品栏；物品栏 Shift 点击放入"全部"（按成员顺序，分类行优先）。
7. 搜索 `@minecraft`、`#`、`/hidden`：压紧显示，点击命中格落在真实 (背包, 格)。
8. 整理：显示重排，切到具体标签核对物品位置**没变**；放入物品后不自动重排；拾起新背包（epoch 变）后重算；切标签再回保留。
9. 自动拾取（B 的那只带自动拾取）：开着"全部"，捡东西 → 立即出现在网格；开着非 B 的具体标签 → 画面**不错位**（065 隐患的修复验证）。
10. 请求错位：成员变化瞬间点击 → 不落到别的背包（看日志 `all request refused`）。
11. 16 只满载：打开不卡、日志无"包过大"。
12. `[combined].enabled=false`（热加载）→ 一秒内结束并冲洗光标；`/hidden`、新增 `epoch` 日志。
13. 叠在箱子界面上：光标是携带物；Shift 点击容器 → "全部"；网络终端取出 → "全部"（若有集成终端/AE2）。
14. 不存档字段：保存退出，存档里成员背包的光标为空（读存档或 `/data`）。

## C. 要你真机看的（主观项，我不替你勾）

"全部"网格的外观（面板外框、填充格、淡染强度与深色可辨度）、标签栏加"全部"后的位置与滚动、面板标题栏上的搜索/整理在"全部"里的手感、取消联合标题栏后标签栏的观感（"+n"徽标与档位提示的位置）、冲洗时的提示措辞、默认选中"全部"是否顺手。
