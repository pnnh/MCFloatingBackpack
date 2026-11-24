# Tasks: 联合背包 · "全部"标签

**Tests**: 纯逻辑（寻址、显示映射、请求规则、内容比较、冲洗、槽位接口、整理排列、搜索映射）用 JUnit；接线、协议、资源用源码文本检查。**单元测试类路径里没有真实的物品栏与屏幕，"全部"里的点击/拖动/快速转移落点、光标冲洗各时机、染色与悬停、搜索与整理、自动拾取同步都只能在游戏会话里验证**（宪法 V）；界面观感由用户真机确认，我不替你勾。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`res/` = `src/main/resources/`
- 不含提交 Git（等用户要求）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；测试用的数据包与改过的配置用完还原；AI 自动验证的勾选规则同前
- 不新增依赖、mixin、反射、贴图；**协议号 23 → 24**；**`DataRevision` 仍为 6**；没穿联合背包、或在具体标签里时，所有面板行为与 065 逐字节一致（除 T004 取消联合标题栏、T020 的同步修复）
- 设计依据：[plan.md](./plan.md)、[research.md](./research.md)、[contracts/](./contracts/)

## Phase 1: 基线与 065 瘦身

- [X] T001 基线：`./gradlew test -PcleanRuntime -PnoJei`，记下测试类/项数（065 之后 259 / 1974）
- [X] T002 取消联合标题栏（R11）：`…/client/panel/combined/CombinedBarLayout.java` 去掉 `TITLE_H`/`titleRect`，`BARS_H = TAB_H`；`CombinedBarRenderer` 去掉 `drawTitle`，"+n"未连接徽标画在标签栏右端（悬停提示沿用）；`CombinedBarInput`、`ClientCombinedState`（`topReserve`）、JEI 排除区随 `BARS_H` 变；更新 `…test/client/panel/combined/CombinedBarLayoutTest.java` 与 065 `contracts/ui-layout.md`
- [X] T003 语言键（中英）：`gui.mcfloatingbackpack.combined.all`、`.all.empty`、`.all.from`、`.all.tooltip_tier`、`message.mcfloatingbackpack.combined.flushed`

## Phase 2: 纯逻辑与测试（无 Minecraft 类型）

- [X] T004 [P] `…/inventory/combined/AllAddressing.java`（成员主区域长度 → 前缀和；`locate`、`flatOf`、`total`、`MAX_MEMBERS`）+ `…test/inventory/combined/AllAddressingTest.java`
- [X] T005 [P] `…/client/panel/combined/AllGrid.java`（`displayMap(sizes, cols)`：各背包的格子首尾相连（不再每只从新的一行开始）、块末尾 -1）+ `…test/client/panel/combined/AllGridTest.java`
- [X] T006 [P] `…/inventory/combined/AllRequestRules.java`（会话不在 ALL / `epoch` 不等 / 身份对不上 / 档位过高 / 保护 → 拒绝；`epoch` 0 与具体标签的互斥）+ 测试
- [X] T007 [P] `…/inventory/combined/AllContentDiff.java`（引用相同 → 未变；主区域列表/行分类/标签任一不同 → 变）+ 测试
- [X] T008 [P] `…/inventory/combined/CursorFlush.java`（成员 → 物品栏 → 丢弃；守恒）+ 测试
- [X] T009 [P] `…/inventory/combined/SlotAccess.java`（`size/get/set/canPlace`）；`BackpackCursorGesturePayload` 的 `distribute/gatherSlots/gatherAll` 抽成 `SlotAccess` 泛型静态方法（`BackpackInventory` 适配），既有测试不变 + `…test/inventory/combined/SlotAccessTest.java`
- [X] T010 [P] `…/client/panel/combined/AllSortPermutation.java`（置换、稳定、非空在前；按 047 规则）、`AllSearchMap.java`（命中紧凑排列，作用在显示顺序上）+ 测试

## Phase 3: 服务端

- [X] T011 `…/inventory/combined/AllState.java`、`AllMember`、`CombinedSessions.Session`（record → 可变类，加 `mode/all`）、`AllContext.java`（`begin` 校验、按需解码缓存、`markTouched`、`finish`）、`CursorLoan.java`、`AllSink.java`（成员顺序 `insertIntoMain`）、`AllSync.java`
- [X] T012 协议 24：`…/network/CombinedMemberSyncPayload.java`、`CombinedCellUpdatePayload.java`（S→C）；`CombinedSelectPayload` 加 `SELECT_ALL`；`CombinedMembersPayload` 加 `all`；`BackpackClickPayload`/`BackpackCursorGesturePayload`/`BackpackQuickTransferPayload` 加 `epoch`；`BackpackNetwork` `versioned("24")` 与注册；更新所有断言 `"23"` 的旧测试
- [X] T013 `CombinedSessions`：`open` 改为进"全部"；`activateAll`（逐成员 `acquire`、建 `AllState`、发成员表 + 同步包）；`select(SELECT_ALL)` 与"全部"→ 具体标签的冲洗；`flush(player)`；`tick` 的 ALL 分支（成员/大小/可见行变 → 冲洗 + epoch+1 + 重发；否则 `AllContentDiff` 发变了的成员）
- [X] T014 `BackpackClickPayload`：拆 `applyClick(... loan)`（两处保存前 `loan.settle`）；"全部"分支（只接受 MAIN、`begin`、`locate`、借用光标）
- [X] T015 `BackpackCursorGesturePayload`、`BackpackQuickTransferPayload`（放入 `AllSink`、取出 `SlotAccess`）、`BackpackTakeFromContainerPayload`、`BackpackNetworkTakePayload`、`MergePanelCursorPayload`："全部"分支；`Trash/TrashSend/Sort/Tag/TagOverview/PickupBlacklistOp` 六个在"全部"会话里直接忽略；`PanelTargets.inAll`
- [X] T016 `BackpackNetwork.sendSlotUpdate/sendFullSync` 认会话（具体标签只发目标；"全部"翻成平铺号发 `CombinedCellUpdatePayload`/`CombinedMemberSyncPayload`）；新增 `sendPickupUpdate`；`AutoPickupEvents` 改走它；`PipeActions` 的同步经同一入口
- [X] T017 冲洗联动：`BackpackGate.noteClosed`、`BackpackOpenRequestPayload`（B 键）、`MCFloatingBackpack` 下线与停服（先 `flush` 再 `forget`/`clear`）、`enabled=false`（tick 里）
- [X] T018 `…test/resources/AllWiringTest.java`（[contracts/server-requests.md](./contracts/server-requests.md) 的 4 条接线断言、协议 24 与新包注册、`epoch` 字段）

## Phase 4: 客户端

- [X] T019 `…/client/panel/combined/ClientCombinedView.java`（成员切片、`ready`、`epoch`、收齐后 `ClientBackpackView.applyCombined`）；`CombinedMemberSyncPayload`/`CombinedCellUpdatePayload` 的客户端处理；`CombinedMembersPayload.all` 的处理（让面板可见、重置搜索与标签总览）
- [X] T020 `ClientBackpackView.applyCombined`、`ClientPlacement.canPlace`（"全部"按成员的 `RowView`/`ItemTags`）、`FloatingPanelState` 显示映射来源（`AllGrid.displayMap` / 整理排列表 / 搜索）、`SearchContext.source()` 的"全部"版
- [X] T021 请求发送：点击、拖动、快速转移带 `epoch`；加载中不发；`FloatingPanelInput` 整理按钮在"全部"里改为显示整理；"全部"下编号与快照按钮不画不响应、工具区/左右区/底部画成填充格
- [X] T022 `FloatingPanelRenderer`：格子按成员染色（淡染，高亮与灰格之下）、悬停提示追加"来自 ■编号（位置）"、`hoverMember` 置位；`CombinedBarRenderer`/`CombinedBarInput`/`CombinedBarLayout`：最前"全部"标签（选中、悬停高亮、点击发 `SELECT_ALL`、悬停提示含档位）
- [X] T023 关闭/切换清理：`ClientCombinedState`/`ClientCombinedView`/排列表在关闭联合界面时清空，切标签再回保留

## Phase 5: 文档与验证

- [X] T024 README 中英文（"全部"标签、搜索与整理只改显示、光标冲洗规则、崩溃会丢光标、默认选中"全部"）；`docs/plan/combined-backpack.md` 状态；旧测试里断言 065 默认标签/无"全部"的更新
- [X] T025 `./gradlew build -PcleanRuntime -PnoJei` 全绿；记下测试类/项数
- [ ] T026 容器自动验证（[quickstart.md](./quickstart.md) B 的 14 项，有明确证据才勾）；`DELIVERY.md` 写已验证与没验证的 —— **部分完成**：取出/放入、跨背包移动、光标冲洗（切标签、关闭）、Ctrl+点击放入、整理、搜索、滚动、B 键不变已有截图证据；拖动分摊、自动拾取同步、容器叠加、epoch 拒绝、成员实时增减、`enabled=false`、16 只满载未验证，见 DELIVERY
- [ ] T027 用户真机确认（C）："全部"网格外观、淡染强度与深色可辨度、标签栏加"全部"与徽标后的观感、搜索/整理手感、冲洗提示措辞、默认选中"全部"是否顺手。**只由用户勾选**

## Dependencies

T001 → T002/T003 → Phase 2（可并行）→ Phase 3（T011 → T012 → T013～T017）→ T018；Phase 4 依赖 T012；T024～T027 最后。
