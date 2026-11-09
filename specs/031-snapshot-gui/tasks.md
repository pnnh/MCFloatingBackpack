# Tasks: 快照管理界面（玩家与管理员面板）

**Input**: `/specs/031-snapshot-gui/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法 2.2.0

**Tests**: 权限判断、请求校验、界面状态机、布局、标题栏几何、结果码文字、语言键是纯逻辑，用单元测试覆盖；网络、界面、命令、权限、面板状态保持只能在客户端/服务端会话里验证（宪法 V）。

## Format: `[ID] [P?] [Story] Description`

- 路径相对于仓库根目录；`…/` 表示 `src/main/java/pnnh/mcfloatingbackpack/`；**不包含提交 Git 的任务**（等用户要求再提交）；**启动客户端或跑图集度量前先 `pgrep -f "fml.modFolders=mcfloatingbackpack"`**
- 勾选规则：客户端验证任务**只有用户明确确认**才打勾；没做的写"未验证"
- **不新增贴图、配方、设置项、依赖**；030 命令的输出文字与行为不变

---

## Phase 1: Setup

- [x] T001 基线（030 提交后：93 个类、658 个用例），记在 DELIVERY.md

## Phase 2: Foundational（纯逻辑、服务端抽取、缓存）

- [x] T002 [P] `…/snapshot/SnapshotAccess.java`（`isOp(level)`、`canSee(op, self, owner)`、`filterFor`）与 `SnapshotRequestRules.java`（页码 1..1000、版本序号 1..16、天数 0..36500 / 过滤 -1..36500、名字 ≤16 且 `[A-Za-z0-9_]*`、已知的范围/动作字节），测试 `SnapshotAccessTest`、`SnapshotRequestRulesTest`
- [x] T003 [P] `…/snapshot/net/` 的纯数据：`Row`、`VersionRow`、`Scope`、`ActionKind`、`ResultCode`（字节 ↔ 枚举，未知值 → 拒绝），测试 `SnapshotNetTypesTest`
- [x] T004 [P] `…/client/snapshot/SnapshotScreenState.java`（data-model 的状态与不变式：非 op 强制 MINE/无过滤/无目标/无待确认；按钮可用性；待确认流程；选中、翻页、过滤、权限变化时清理），测试 `SnapshotScreenStateTest`
- [x] T005 [P] `…/client/snapshot/SnapshotScreenLayout.java`（宽 `min(W−16,400)`、高 `min(H−16,260)`、居中；列表行数最少 4；预览区高度不够时给出可滚动行数），测试 `SnapshotScreenLayoutTest`（缩放 1～4 与很小的窗口都不溢出）
- [x] T006 [P] `…/client/panel/TitleButtonsLayout.java`（关闭按钮、快照按钮 10×10 在其左 2 px、编号文字右界；太窄时快照按钮隐藏），测试 `TitleButtonsLayoutTest`（与关闭按钮、编号、等级图标不重叠）
- [x] T007 [P] `…/client/snapshot/SnapshotResultText.java`（结果码 → 语言键与参数），测试 `SnapshotResultTextTest`（每个码都有键、成功与失败分开）
- [x] T008 `…/snapshot/SnapshotStore.java`：元数据缓存（`ConcurrentHashMap<Path, SnapshotMeta>`；列目录后已缓存的文件直接用，新文件才读，目录里不存在的清掉）；`groups()`、`versions()` 用它；写出与删除保持正确
- [x] T009 `…/snapshot/SnapshotOps.java`（服务端，不含文字）：`visibleGroups`、`page`、`versions`、`preview`（含 900,000 字节大小保护）、`restore`、`deleteOne`、`deleteAll`、`trimPreview`、`trimConfirm`，返回结果记录与失败原因码；`SnapshotCommands` 改为调用它，**输出文字与行为不变**
- [x] T010 运行 `./gradlew compileJava test`（`SnapshotCommandResourcesTest` 仍通过）

## Phase 3: User Story 1 + 6 - 打开界面、看自己的快照、权限隔离 (P1)

- [x] T011 [US1][US6] `…/snapshot/net/`：`SnapshotListRequestPayload`、`SnapshotListPayload`、`SnapshotVersionsRequestPayload`、`SnapshotVersionsPayload`、`SnapshotResultPayload`；服务端处理按 contracts/network.md（`flushAndWait` → 权限 → 校验 → `SnapshotOps`；非 op 忽略 scope/过滤；别人的背包 = 空/不存在）；`BackpackNetwork` 注册并把协议版本升为 `"16"`
- [x] T012 [US1] `…/client/snapshot/SnapshotClient.java` 与 `SnapshotScreen.java`：标题、列表（每页最多 8 行，行格式：编号、UUID 开头、档位、版本数、所有者、距今）、翻页、版本列表、状态行、关闭；打开时请求第一页；界面关闭后到达的响应丢弃；**普通玩家不创建任何 op 控件**
- [x] T013 [P] [US1] `/mcfb snapshot gui`：`SnapshotOpenPayload`（空，服务端 → 客户端，客户端主线程 `setScreen`，已打开则忽略）；`SnapshotCommands` 新增 `gui`（不要求权限；控制台 → `sendFailure`，带英文后备）
- [x] T014 [P] [US1] `…/client/FloatingBackpackKeys.java`：`SNAPSHOTS`（`InputConstants.UNKNOWN`、`KeyConflictContext.IN_GAME`，同类别），`registerKeys` 注册；`…/client/FloatingPanelEvents.java`：`ClientTickEvent.Post` 里 `while (SNAPSHOTS.consumeClick())` 打开（在 `FloatingPanelEvents` 已有 tick 处理里加；若 quickstart A2 失败改到 `Pre`）
- [x] T015 [P] [US1] 标题栏按钮：`FloatingPanelState.hitsSnapshotButton` 与编号右界改用 `TitleButtonsLayout`；`FloatingPanelRenderer` 画按钮（代码绘制，悬停提示）并让 `drawTitleId` 用新右界；`FloatingPanelInput.onPress` 里左键点击打开界面
- [x] T016 运行 `./gradlew compileJava test`

## Phase 4: User Story 2 - 只读预览 (P1)

- [x] T017 [US2] `…/snapshot/net/`：`SnapshotPreviewRequestPayload`、`SnapshotPreviewPayload`（`ok` 时容量、非空格数、≤108 个 `ItemStack`；失败时原因码）；服务端先量大小，超限回 `TOO_LARGE`
- [x] T018 [US2] `SnapshotScreen`：选中版本后请求并显示预览（9 列物品格，复用 `drawVanillaPanel`、`drawSlotWell`、`COLOR_SLOT_HOVER`，物品用 `renderItem`/`renderItemDecorations`，悬停原版提示；点击无反应；高度不够时鼠标滚轮纵向滚动）；快照被删等失败显示在状态行
- [x] T019 运行 `./gradlew compileJava test`

## Phase 5: User Story 3 - 管理员恢复 (P1)

- [x] T020 [US3] `…/snapshot/net/SnapshotActionPayload`（`kind/backpack/time/target/days`，校验按 contracts）与处理：`!op` → `NO_PERMISSION`；`RESTORE` 按 contracts（目标在线、档位、读取、`placeItemBackInInventory`、无标识）；结果 `SnapshotResultPayload`
- [x] T021 [US3] `SnapshotScreen`（仅 op 创建）：恢复按钮（选中版本才可用）、目标循环按钮（自己 + 在线玩家，取自 `getConnection().getOnlinePlayers()`）；成功后状态行显示并刷新
- [x] T022 运行 `./gradlew compileJava test`

## Phase 6: User Story 4 + 5 - 删除、全部/过滤、清理 (P2)

- [x] T023 [US4][US5] 服务端：`DELETE_ONE`、`DELETE_ALL`、`TRIM_PREVIEW`、`TRIM_CONFIRM`（确认时重新计算候选）；删除后 `forget`；`SnapshotListRequest` 里 op 的 `ALL`、所有者名字、不活跃天数过滤生效
- [x] T024 [US4] `SnapshotScreen`（仅 op）：删除这一份、删除全部（二次确认，显示份数）、取消
- [x] T025 [US5] `SnapshotScreen`（仅 op）：我的/全部切换、所有者与天数输入框（Enter 应用）、清理（天数 + 预览 + 确认删除 + 取消，状态行显示数量）
- [x] T026 运行 `./gradlew compileJava test`

## Phase 7: 语言、验证与收尾

- [x] T027 语言键（中英文）：`gui.mcfloatingbackpack.snapshot.*`（标题、按钮、列表行、状态码）、`key.mcfloatingbackpack.snapshots`、`panel.mcfloatingbackpack.snapshot.tooltip`、`command.mcfloatingbackpack.gui.console`（带英文后备）；测试 `SnapshotGuiResourcesTest`（两种语言键一致、结果码都有键、`gui` 命令消息的英文后备一致）；若 `SnapshotCommandResourcesTest` 的正则要容纳新键则一并更新
- [x] T028 [US1] 按 quickstart A 验证三个入口、面板状态不变、手里没背包也能开（先 `pgrep`；由用户确认后才打勾）。用户 2026-11-09 验证没有问题
- [ ] T029 [US1][US2][US6] 按 quickstart B 验证非 op 视图与隔离（没有第二个账号写"未验证"；由用户确认后才打勾）。**搁置，未测**：需要第二个账号（用户 2026-11-09）；非 op 的界面由纯逻辑测试与服务端权限检查覆盖，但没有在游戏里验证过
- [x] T030 [US3][US4][US5] 按 quickstart C 验证 op 视图与动作、权限变化（由用户确认后才打勾）。用户 2026-11-09 验证没有问题
- [x] T031 布局与回归：quickstart D、E（GUI 缩放 1～4、很小的窗口、108 格预览滚动；030 命令与浮动面板回归；由用户确认后才打勾）。用户 2026-11-09 验证没有问题
- [x] T032 [P] 更新文档：`docs/MCFloatingBackpack.md`（031 的说明）、`docs/plan/manual-verification-checklist.md`（031 验证项）、`docs/plan/backup-admin-roadmap.md`（"玩家可用的快照面板"从暂缓移到已做）、`specs/030-backpack-snapshots/spec.md` 的一句说明；**不要修改 `docs/ideas`**
- [x] T033 图集度量：`design/demo/tools/check_atlas_budget.sh`（先 `pgrep`；本 feature 不新增贴图，应仍是 2048×1024）
- [x] T034 静态检查：`grep -rn "BackpackRegistry\|RegistryRecord\|tombstone"` 无残留；新增 payload 在 `BackpackNetwork` 全部注册；`SnapshotScreen` 里没有任何权限判断（只用服务端回的 `isOp` 决定显示）
- [x] T035 `specs/031-snapshot-gui/DELIVERY.md`（中文）：自动化结果、每个验证项实际验证了什么 / 未验证什么、已知限制

---

## Dependencies & Execution Order

Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7。T002～T007 互相独立（`[P]`）；T008、T009 依赖 T002；T011 依赖 T003、T009；T012 依赖 T004、T005、T011；T013、T014、T015 彼此独立，依赖 T012（`SnapshotScreen` 的 `open()`）；T018 依赖 T017；T021 依赖 T020；T024、T025 依赖 T023。

## Implementation Strategy

MVP = Phase 2 + 3：玩家能用命令/快捷键/标题栏按钮打开界面，看到自己的快照与版本，权限隔离成立。预览（Phase 4）、恢复（Phase 5）、删除与清理（Phase 6）依次叠加，每个阶段末尾都能独立验证。

### 风险提示

- **快捷键与宿主界面**（T014、T028）：面板可见时能否打开要在游戏里验证；备选是把处理挪到 `ClientTickEvent.Pre`。
- **抽取 `SnapshotOps` 不能让命令回归**（T009）：命令输出文字由 `SnapshotCommandResourcesTest` 保护，行为需要用户在游戏里再过一遍 030 的命令（T031）。
- **预览体积**（T017）：阈值 900,000 字节，超限回 `TOO_LARGE`。
- **标题栏很窄**（T006、T015）：按钮隐藏，入口仍有命令与快捷键。
