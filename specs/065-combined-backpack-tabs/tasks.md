# Tasks: 联合背包 · 单背包标签

**Tests**: 纯逻辑（成员表、取最高档、解析判定、找回、条形布局、颜色表、空背包规则）用 JUnit；接线与资源用源码文本检查。**单元测试类路径里没有真实的物品栏与屏幕，U 键、标签点击、切标签后的落点、灰掉的控件、真实配方都只能在游戏会话里验证**（宪法 V）；界面观感（颜色、图标、高度、占位贴图）由用户真机确认，我不替你勾。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`，`res/` = `src/main/resources/`
- 不含提交 Git（等用户要求）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；测试用的数据包函数与改过的配置用完还原；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增依赖、mixin、反射；**协议号 22 → 23**；**`DataRevision` 仍为 6**；没穿联合背包时所有面板行为与 064 之后逐字节一致
- 设计依据：[plan.md](./plan.md)、[research.md](./research.md)、[contracts/](./contracts/)

## Phase 1: 基线、物品与资源

- [X] T001 基线（测试基线 249 / 1907 已记；图集"加入前"尺寸改在 T028 用容器里上一次的日志核对，不在宿主机另起客户端）：`./gradlew test -PcleanRuntime -PnoJei`，记下测试类/项数（064 之后 249 / 1907）；`design/demo/tools/check_atlas_budget.sh` 记下**加入前**的图集尺寸（宪法 VI）
- [X] T002 `…/item/CombinedBackpackItem.java`（`Item` + `Equipable`，胸甲槽；`isCombined`、`tierOf`；`canFitInsideContainerItems` 返回 false；堆叠 1）；`…/item/ModItems.java` 注册 `COMBINED_BACKPACKS`（`<tier>_combined_backpack`）并放进创造栏
- [X] T003 资源：`res/assets/mcfloatingbackpack/models/item/<tier>_combined_backpack.json` ×5（`item/generated`）；占位贴图 `textures/item/<tier>_combined_backpack.png` ×5（用脚本从各档 `*_floating_backpack_icon.png` 派生，右下角叠紫色连线徽章，32×32；2026-11-24 按用户意见重做，脚本放 `design/demo/tools/`，**标注占位待用户确认**）；`lang/en_us.json`、`zh_cn.json` 的 5 个物品名
- [X] T004 [P] `…/inventory/BackpackEmptiness.java`（纯规则：主区域、工具区、垃圾桶、光标、隐藏行与行内物品、液体存量全空才算空；标签配置与拾取黑名单不算内容）+ `…test/inventory/BackpackEmptinessTest.java`
- [X] T005 配方：`…/inventory/recipe/CombinedCraftingRecipe.java`（`ShapedRecipe` 子类，只覆盖 `matches`：中间那格的背包必须空；序列化器/流编解码同 `BackpackCraftingRecipe` 的写法）；`ModRecipes` 登记 `combined_crafting`；`res/data/mcfloatingbackpack/recipe/combined_<tier>_backpack.json` ×5（同档位背包居中、四角末影珍珠、四边档位材料；数值先按草案）、`upgrade_<tier>_combined_backpack.json` ×4（原版 `smithing_transform`，模板与材料块同背包升级）
- [X] T006 标签：`res/data/curios/tags/item/floating_backpack.json`、`res/data/accessories/tags/item/floating_backpack.json` 加五个联合背包；**不**加进 `back`、`charm`
- [X] T007 `…test/resources/CombinedResourcesTest.java`：五档物品的 lang（中英）、模型、贴图、两个配方与升档配方 JSON 的结构、两个标签含五个联合背包且 `back`/`charm` 不含、物品 id 与 `BackpackTier` 对应

## Phase 2: 纯逻辑与测试（无 Minecraft 类型）

- [X] T008 [P] `…/inventory/combined/CombinedMembers.java`：`Candidate`/`Member`/`Result`、`compute`（保护 → 档位 → 超过 16 的顺序，三类计数）、`signature`；`…test/inventory/combined/CombinedMembersTest.java`（顺序、同身份多次、档位过滤、保护排除、截断与计数、签名只在变化时变）
- [X] T009 [P] `…/inventory/combined/CombinedWorn.java`：`pick`（档位最高；同档位取 `order` 小者）；`…test/inventory/combined/CombinedWornTest.java`
- [X] T010 [P] `…/inventory/combined/PanelTargetRules.java`：`decide`（无会话 / 目标有效 / 身份对不上 / 档位超 / 保护 / 联合背包没了 / 未启用）；`BackpackRef.normalized(source, invIndex, selected)`（`MAIN_HAND` → `INVENTORY(selected)`，其余不变）；`…test/inventory/combined/PanelTargetRulesTest.java`
- [X] T011 [P] `…/inventory/combined/CombinedRecovery.java`：`recover`（身份唯一匹配 / 多个相同 / 找不到 → 第一个 / 空表 → 结束）；`…test/inventory/combined/CombinedRecoveryTest.java`
- [X] T012 [P] `…/client/panel/combined/CombinedBarLayout.java`（标题栏/标签栏矩形、标签宽与滚动裁剪、箭头、`hitTab`、`maxScroll`）、`CombinedColors.java`（16 色表，按位置取）；`FloatingPanelState.clampToScreen` 加 `topReserve`（默认 0）；`…test/client/panel/combined/CombinedBarLayoutTest.java`、`FloatingPanelStateTest` 补用例（`topReserve` 不变量，旧用例不改）

## Phase 3: 服务端

- [X] T013 `…/inventory/worn/WornBackpacks.java`：第二个 `BackpackSearch`（探针换成 `CombinedBackpackItem.isCombined`），`register` 同时登记到两个实例；新增 `firstCombined/allCombined(Player)`；`…/inventory/combined/WornCombined.java`（适配：取玩家穿着的最高档联合背包）；`…test/inventory/worn/BackpackSearchTest` 补"同一来源登记到两个实例"用例
- [X] T014 `…/inventory/combined/CombinedCandidates.java`（适配：穿戴所有背包 → 副手 → 物品栏 0..35，读 `BACKPACK_IDENTITY`、`BACKPACK_CONTENTS` 修订号、自定义名；不 acquire、不改栈）、`CombinedSessions.java`（会话 map；`open/select/end/forget/recover/tick`）、`PanelTargets.java`（`resolve(player)`）
- [X] T015 `…/network/CombinedSelectPayload.java`（C→S，`op/index/identity`，解码范围检查）、`CombinedMembersPayload.java`（S→C）；`BackpackNetwork` 协议 `"22"` → `"23"` 并注册；`handleServer` 按 [contracts/protocol.md](./contracts/protocol.md) 的 OPEN/SELECT 步骤
- [X] T016 `…/inventory/combined/CombinedConfig.java` + `…/diag/DiagConfig.java`：`[combined].enabled`（默认 true，注释写明"关了物品仍在，U 不打开，下一次检查结束既有会话"）
- [X] T017 `…/inventory/identity/BackpackGate.java`：`noteClosed`/`forget` 联动 `CombinedSessions.end/forget`；`resync` 先 `CombinedSessions.recover`；`tickOpenSession` 里联合会话的每秒检查（成员签名变了才推；联合背包没了/`enabled=false` 则 `end` 并发 `active=false`、面板退回单背包规则）
- [X] T018 面板类处理器统一解析（见 [contracts/panel-targets.md](./contracts/panel-targets.md) 清单）：`BackpackClickPayload`、`BackpackCursorGesturePayload`、`BackpackQuickTransferPayload`、`BackpackTakeFromContainerPayload`、`BackpackNetworkTakePayload`、`MergePanelCursorPayload`、`BackpackTrashPayload`、`BackpackTrashSendPayload`、`BackpackSortPayload`、`BackpackTagPayload`、`BackpackTagOverviewPayload`、`BackpackPickupBlacklistOpPayload` 改用 `PanelTargets.resolve`；点击包在联合会话内声称引用与目标不等则拒绝并 `resync`；逐项读"手里/主手"假设（清单里 8 条）；读 `snapshot/net/*` 与 `CreativeCursor*` 归类并把结论写进 DELIVERY
- [X] T019 `…/MCFloatingBackpack.java`：下线、停服清 `CombinedSessions`；语言键 `message.mcfloatingbackpack.combined.need_worn/.disabled/.empty_cursor/.no_members`（中英）
- [X] T020 064 的嵌套规则认联合背包：`NestingRules`/`BackpackNesting` 的"是背包"判断与深度扫描 id 集合含五个联合背包；`…test/inventory/nesting/` 补用例
- [X] T021 `…test/resources/CombinedWiringTest.java`（[contracts/panel-targets.md](./contracts/panel-targets.md) 的接线断言、协议 23 与两包注册、`DiagConfig` 含 `[combined]`、`HostKeyRouter`/`FloatingBackpackKeys` 含 U、`canFitInsideContainerItems`、`BackpackGate` 关闭路径联动、"B 激活"语义的文件仍用 `BackpackRef.resolve`）
- [X] T022 `./gradlew test -PcleanRuntime -PnoJei`：全部通过

## Phase 4: 客户端

- [X] T023 `…/client/FloatingBackpackKeys.java` 加 `COMBINED`（U，`UNIVERSAL`）并注册；`…/client/panel/HostKeyRouter.java`（`classify` 认第二个 toggle 键，`…test/client/panel/HostKeyRouterTest` 补用例）；`FloatingPanelEvents` 处理 U：面板可见且联合模式 → 关闭；本机没穿联合背包 → 动作栏提示；否则发 OPEN；B 键行为不变
- [X] T024 `…/client/panel/combined/ClientCombinedState.java`（成员表、选中、计数、`loading`、`tabScroll`、`blocksRequests()`）；`CombinedMembersPayload.handleClient`；`BackpackSessionPayload.handleClient` 在 `activeRef` 对上选中成员时清 `loading`；`active=false` 时清状态
- [X] T025 `…/client/panel/combined/CombinedBarInput.java`（点标签发 SELECT，光标非空只提示；箭头与滚轮；条形区域内按下/释放/滚轮全部认领；`FloatingPanelInput` 与 `FloatingPanelEvents` 的滚轮在面板命中之前先问它）、`CombinedBarRenderer.java`（标题栏、标签栏、色块、悬停提示、"+n"徽标与原因）；联合模式下 `clampToScreen(topReserve = BARS_H)`；`FloatingBackpackJeiPlugin` 多一个排除区矩形
- [X] T026 `loading` 期间所有面板请求不发（`sendClick`、快速转移、拖动分摊、垃圾桶、整理、标签……统一问 `blocksRequests()`）；三个移动工具入口（唱片机页、工作台、熔炉）与唱片机页在联合模式且当前标签成员不是"B 激活的那只"时不响应、动作栏提示、格子里画灰；比较用客户端 `BackpackRef.resolve` 归一化，仅联合界面可见时每帧做
- [X] T027 语言键 `gui.mcfloatingbackpack.combined.*`、`key.mcfloatingbackpack.combined`（U）、`gui.mcfloatingbackpack.tools_active_only`（中英）；`…test/resources/` 的语言键对称测试沿用

## Phase 5: 验证（容器 `mcfb-c1`，见 [quickstart.md](./quickstart.md)）

- [X] T028 `./gradlew build -PcleanRuntime -PnoJei`；加入后再跑一次 `check_atlas_budget.sh` 并把**前后尺寸**记入 DELIVERY（宪法 VI）
- [X] T029 quickstart B 的 15 项逐条验证（AI 容器能做的），证据记入 DELIVERY；**13（锻造台）、15（Accessories 与真正放进 Curios 槽）未验证**，如实写在 DELIVERY
- [ ] T030 用户真机确认：标签栏与标题栏观感、颜色表、图标与占位贴图、面板高度与小分辨率钳制（主观项，由用户勾）

## Phase 6: 文档

- [X] T031 README 两份（联合背包五档与连接规则、U 键、穿戴位置与专用槽位限制、移动工具/唱片机/自动拾取只对 B 打开的那只、协议号 22 → 23 两端都要更新）；`docs/plan/combined-backpack.md` 状态；`specs/065-combined-backpack-tabs/DELIVERY.md`（已验证 / 没验证 / 已知限制：专用槽位大小 1、062 不找回联合背包、EMI 排除区未适配）
- [X] T032 最终 `./gradlew build -PcleanRuntime -PnoJei`
