# Tasks: 网络终端 → 面板（集成终端与 AE2 的"取出"方向）

**Input**: `/specs/060-network-terminal-take/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 纯逻辑（`NetworkTakeRules`、`BridgeDispatch` 的取/还、请求包编解码）用 JUnit；README、`mods.toml`、`build.gradle`、接线用资源/源码文本检查。**单元测试类路径里没有 Minecraft 类，桥对真实终端的调用、客户端识别、请求的真实收发只能在游戏会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；改配置前备份、验证后还原；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增运行时依赖、mixin、反射；新增依赖只是 `compileOnly`；**协议号 21 → 22（新包）**；**`DataRevision` 仍为 6**；面板显示逻辑不改
- **没装集成终端/AE2、空配置时行为与 060 之前完全一致**：每个改动任务都要保住这一点
- 用户故事：US1 集成终端取出（P1）；US2 AE2 取出（P2）；US3 档位/版本/出错不出事（P1）；US4 README 与防漂移

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`；类数与用例数记在 `specs/060-network-terminal-take/DELIVERY.md`（新建，中文）
- [X] T002 `build.gradle`：在 058 的集成终端 `compileOnly` 之后加 `compileOnly("maven.modrinth:ae2:19.2.20") { transitive = false }`（注释：与 localRuntime 同版本）；`./gradlew compileJava` 确认能解析并下载；**写一个只 `import appeng.client.gui.me.common.MEStorageScreen`、`RepoSlot`、`appeng.api.stacks.AEItemKey`、`appeng.menu.me.common.MEStorageMenu`、`appeng.helpers.InventoryAction` 的临时探针类（放在 `src/main/java/pnnh/mcfloatingbackpack/compat/ae2/` 下，T016 起会被真实类取代并删除）验证编译**；若编译因缺 GuideME 的类失败，再加 `compileOnly("maven.modrinth:guideme:21.1.22") { transitive = false }`（research R11）并把结论记进 DELIVERY
- [X] T003 `src/main/templates/META-INF/neoforge.mods.toml`：加 `ae2` 可选依赖（`type="optional"`、`versionRange="[0,)"`、`ordering="NONE"`、`side="BOTH"`，注释同 058 那几条）

## Phase 2: Foundational

- [X] T004 [P] `…/compat/NetworkTakeRules.java`（纯，泛型 `StackOps<S>`）：`validate(what)`（非空；返回 `amount = maxStack`）、`distribute(taken, insertIntoBackpack, giveBack, fallback)` 返回 `Split(toBackpack, backToNetwork, fallback)` 并断言 `a + b + c == taken`、同种物品检查 `isSameKind`；`…test/compat/NetworkTakeRulesTest.java`（放得下、放不下一部分、完全放不下、退回部分失败走兜底、全部退回失败全走兜底、`taken == 0`、守恒断言对随机组合成立）
- [X] T005 `…/compat/BridgeDispatch.java`：`Bridge` 加默认方法 `handlesTake`（假）、`take`、`giveBack`（默认抛 `UnsupportedOperationException`）；加 `tryTake(player, menu, what, handle)`、`tryGiveBack(player, menu, stack, handle)`，均返回 `Optional<S>`，规则同 `tryInsert`（`state.isActive` 与 `levelAllows` 都要满足；`handlesTake`/`take`/`giveBack` 在 `try` 里；失败只报告一次；返回值检查：`take` 的结果必须空或与 `what` 同种且数量 ≤ `what` 的最大堆叠、`giveBack` 的结果必须空或与给出的同种且数量 ≤ 给出的；传给桥的是拷贝）；改 `…test/compat/BridgeDispatchTest.java`（contracts/request-and-server.md §3 的表全部）；类注释更新
- [X] T006 `…/compat/NetworkStorageBridges.java`：加 `tryTake`、`tryGiveBack`（桥列表为空直接返回空）
- [X] T007 `…/compat/ThirdPartyTable.java`：加 `ae2` 行（`Applied Energistics 2`，`[19.2.0, 19.3.0)`，`also` 空）；`ALL` 含两行；改 `…test/compat/ThirdPartyTableTest.java`（真实表通过；`ae2` 行的段与 `also`）；`CompatLogTextTest` 等若假设"表只有一行"的地方同步
- [X] T008 `…/compat/ClientCompatVersions.java`（客户端按自己装的版本判断）：`inRange(ThirdPartyCompat row)` = `CompatFinding.check(row, InstalledMods::versionOf)` 且 `IN_RANGE`，按 modId 缓存；`…test/compat/ClientCompatVersionsTest.java` 只测纯的部分（把 `installedVersion` 函数作为参数的静态方法：在范围、范围外、没装、要求的模组范围外）
- [X] T009 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 集成终端取出 (Priority: P1) 🎯 MVP

**Goal**: 面板可见时在集成终端物品存储页 Ctrl+点击条目，一步取一组进背包；放不下退回；合成页/面板不可见不拦截。

**Independent Test**: quickstart 情形 1～8。

- [X] T010 [US1] `…/network/BackpackNetworkTakePayload.java`：请求包 `(int containerId, ItemStack what, long handle)`（`ItemStack.STREAM_CODEC`、`VAR_INT`、`VAR_LONG`；空物品栈解码抛 `DecoderException`）+ `handleServer`（contracts/request-and-server.md §2 的算法：背包有效 → `ServerCompatDenials.allows(player, menu, TAKE_FROM_CONTAINER)` → 菜单编号与光标检查 → `what` 非空、`amount = maxStack` → `BackpackGate.acquireForPanel` → `NetworkStorageBridges.tryTake` → `backpack.insertIntoMain` → 放不下的 `tryGiveBack` → 还剩的走 `ContainerMenuAccess(player).fallback()`（先 `toInventory` 再 `drop`，WARN）→ 保存、`broadcastChanges`、`sendFullSync`）；`…/network/BackpackNetwork.java`：注册新包（客户端 → 服务端），`versioned("21")` → `versioned("22")`
- [X] T011 [US1] 把所有写死 `versioned(\"21\")` 的测试断言改成 `22`（18 个文件，`grep -rl 'versioned(\\"21\\")' src/test`）；运行确认
- [X] T012 [US1] `…/compat/integratedterminals/IntegratedTerminalsBridge.java`：`handlesTake(menu)` = 现有 `handles` 的同一份判断；`take`：`tab.getIngredientNetwork().getChannel(container.getSelectedChannel()).extract(what.copyWithCount(maxStack), IngredientComponent.ITEMSTACK.getMatcher().getExactMatchCondition(), false)`；`giveBack` = 现有 `insert`；更新类注释
- [X] T013 [US1] `…/client/compat/NetworkEntry.java`、`NetworkTerminalClient.java`（接口 `Optional<NetworkEntry> entryAt(AbstractContainerScreen<?> screen, Slot slot)`）、`NetworkTerminalClients.java`（注册表；`entryAt(screen, slot)`：按 `MenuOwner.ownerOf(menu)` 找识别器；启用条件：`ClientCompatVersions.inRange(表里该模组的行)` 且 `CompatConfig.policy().specificAdaptation(modId)`；不满足返回空）
- [X] T014 [US1] `…/compat/integratedterminals/IntegratedTerminalsClient.java`（第三方，`@OnlyIn(CLIENT)`）：`entryAt`（contracts/client-and-compat.md §3：`ContainerScreenTerminalStorage`、`getSelectedClientTab()` 是 `TerminalStorageTabIngredientComponentClient` 且不是 `…ItemStackCraftingClient`、`getIngredientComponent() == IngredientComponent.ITEMSTACK`、`menu.getTabSlots(menu.getSelectedTab())` 左元素含该格子、`slot.hasItem()` → `NetworkEntry(slot.getItem().copyWithCount(1), 0)`）；`IntegratedTerminalsCompat`：加 `registerClient()` 注册到 `NetworkTerminalClients`
- [X] T015 [US1] `…/client/panel/QuickTransferInput.java`：把 `screenSlotAt` 里"第一个在鼠标下面的 `isActive()` 格子"抽成 `slotAt(screen, x, y)`（`screenSlotAt` 行为不变）；`beginFromScreen` 在前置检查之后、`screenSlotAt` 之前：取 `slotAt`，问 `NetworkTerminalClients.entryAt`，有值就 `PacketDistributor.sendToServer(new BackpackNetworkTakePayload(menu.containerId, entry.what(), entry.handle()))` 并 `return true`（不调用 `start`）；`…/client/ClientSetup.java`：`ModList.get().isLoaded("integratedterminals")` 时调 `IntegratedTerminalsCompat.registerClient()`（放在 `enqueueWork` 里）
- [X] T016 [US1] 测试：请求包编解码 `…test/network/BackpackNetworkTakePayloadTest.java`（若测试类路径里没有 Minecraft 类无法跑真实 `StreamCodec`，则只写源码文本检查：字段、codec 名、空物品栈抛 `DecoderException`、`versioned("22")`）；接线测试 `…test/resources/NetworkTakeWiringTest.java`（源码文本）：`beginFromScreen` 里先 `NetworkTerminalClients.entryAt` 再 `screenSlotAt`、命中后 `return true` 且在那个分支里不出现 `start(`；`handleServer` 的顺序（`BackpackRef`、`ServerCompatDenials.allows`、携带物/菜单编号、`maxStack`、`acquireForPanel`、`tryTake`、`insertIntoMain`、`tryGiveBack`、`fallback`、`saveInventory`）；请求包与注册表里没有第三方类型；`org.cyclops.` 与 `appeng.` 只出现在 `compat/integratedterminals/`、`compat/ae2/`；`DependencyRulesTest` 补 AE2 的反例（`appeng.` 导入在 `compat/` 根目录失败、在 `compat/ae2/` 通过）
- [X] T017 运行 `./gradlew compileJava test`

**Checkpoint**: 集成终端取出可用，等游戏内验证

## Phase 4: User Story 2 - AE2 取出 (Priority: P2)

**Goal**: 同样的手势在 ME 终端网格上；AE2 自己判电力与权限；放不下退回。

**Independent Test**: quickstart 情形 13～18。

- [X] T018 [US2] `…/compat/ae2/Ae2Bridge.java`（第三方）：`handles` 恒假；`handlesTake(menu)` = `menu instanceof MEStorageMenu`；`take`（contracts §4：携带物非空返回空 → `handleInteraction(handle, PICKUP_OR_SET_DOWN)` → 读 `getCarried()` 并 `setCarried(EMPTY)` 返回）；`giveBack`（`setCarried(stack.copy())` → `handleInteraction(handle, PICKUP_OR_SET_DOWN)` → 读剩余并清空返回）；`…/compat/ae2/Ae2Client.java`（`@OnlyIn(CLIENT)`）：`entryAt`（`MEStorageScreen`、`RepoSlot`、`getEntry()`、`AEItemKey`、`getStoredAmount() > 0` → `NetworkEntry(key.toStack(1), entry.getSerial())`）；`…/compat/ae2/Ae2Compat.java`：`register()`（服务端桥）、`registerClient()`；删除 T002 的临时探针类；类注释写明"只有这三个类触碰 AE2 类型"
- [X] T019 [US2] `…/MCFloatingBackpack.java`：`ModList.get().isLoaded("ae2")` 时 `Ae2Compat.register()`（紧跟集成终端那一处）；`…/client/ClientSetup.java`：同样条件调 `Ae2Compat.registerClient()`
- [X] T020 [US2] 测试：补进 `NetworkTakeWiringTest`：`Ae2Bridge` 里 `handles` 恒假（存入仍走通用路径）、`take` 先查 `getCarried().isEmpty()`、用 `PICKUP_OR_SET_DOWN`、`setCarried(ItemStack.EMPTY)`；没有 `SHIFT_CLICK`；`Ae2Client` 要求 `RepoSlot`、`AEItemKey`、`getStoredAmount() > 0`；`Ae2Compat.register` 只在 `isLoaded("ae2")` 里被调用；第三方类型只在三个 `compat/ae2/` 类里
- [X] T021 运行 `./gradlew compileJava test`

## Phase 5: User Story 3 - 档位、版本、出错 (Priority: P1)

**Goal**: 服务端 OFF 拒绝并提示、GENERIC/范围外/出错关闭时不取；客户端范围外或客户端档位非 FULL 时不拦截。

- [X] T022 [US3] 测试（纯逻辑已在 T005 覆盖出错与档位）：补进 `NetworkTakeWiringTest`：`handleServer` 的第一道门是 `ServerCompatDenials.allows(player, menu, Action.TAKE_FROM_CONTAINER)`；`NetworkTerminalClients.entryAt` 的启用条件含 `ClientCompatVersions.inRange` 与 `CompatConfig.policy().specificAdaptation`；客户端代码里没有 `CompatRuntimeState`/`ThirdPartyCompatService`（客户端没有它们）；`ClientCompatVersions` 用 `CompatFinding.check`、不读 `allowUntested`
- [X] T023 运行 `./gradlew compileJava test`

## Phase 6: User Story 4 - README 与防漂移 (Priority: P2)

- [X] T024 [P] [US4] `README.md`：兼容表里集成终端与 AE2 两行（contracts/client-and-compat.md §4：AE2 行写出 `[19.2.0, 19.3.0)`，两行的"终端 → 面板"改成可用并说明手势）；删除/改写 058 里"反向暂不支持"类措辞与 AE2 "not verified, expected not to work"
- [X] T025 [P] [US4] `README_CN.md`：中文版同义
- [X] T026 [US4] `…test/resources/ThirdPartyDriftTest.java`：`SLUG` 加 `ae2 ↔ ae2`；`checkGradle` 要求 `ae2` 的 `compileOnly` 带 `transitive = false`、与 `localRuntime` 同版本、落在表的段内（`ae2` 的 `localRuntime` 行是 `maven.modrinth:ae2:19.2.20`，版本直接解析；若 T002 加了 guideme，`guideme` 不进表也不检查）；`modIdsInTable()` 自动含 `ae2`；README 对齐自动含 AE2；原来断言"反向暂不支持/未验证"的地方（`ThirdPartyDriftTest`、`IntegratedTerminalsWiringTest`、README 文字断言）同步成新说明；`docs/plan/third-party-compat.md` 状态行补一句"反向见 specs/060-network-terminal-take/"
- [X] T027 运行 `./gradlew compileJava test` 与 `./gradlew build -PcleanRuntime -PnoJei`

## Phase 7: 游戏内验证与交付

- [X] T028 游戏内验证——集成终端（容器，quickstart 第二节 A 的情形 1～12；面板键用 F9；用 `-Pmcfb.fakeModVersion` 与服务端配置热改验证 9～11；**不在 `src/main` 留调试入口**）：每项写实际验证了什么，做不了的写"未验证"
- [X] T029 游戏内验证——AE2（quickstart 第二节 B 的情形 13～18；ME 终端：`ae2:chest` + `ae2:creative_energy_cell` + 手放 `ae2:fluix_glass_cable` + 终端部件）：同上；**若发现 AE2 取出用公开 API 无法可靠实现，停下来按规格 Assumptions 降级（只写文档、不做代码、不进范围表），并回头收缩 T002/T003/T007/T018～T020/T024～T026**
- [X] T030 `specs/060-network-terminal-take/DELIVERY.md`（中文）：基线与完成后的类数与用例数、与 spec/plan 的偏离、每个游戏内验证项验证了什么 / 没验证什么、协议号 21 → 22 的理由、`DataRevision` 仍 6、已知局限（取出与放入背包之间没有事务，兜底方向是"不丢"；客户端范围外又被服务端强制启用时只能用两步法；AE2 的 compileOnly 是否需要 GuideME 的结论）、宪法复查
- [X] T031 最终检查：`./gradlew compileJava test` 与 `./gradlew build -PcleanRuntime -PnoJei` 全部通过；容器里的配置与 `options.txt` 已还原；无临时修改残留（包括 T002 的探针类）；**不提交、不打 tag**
- [X] T032 用户确认：真机上 Ctrl+点击条目取出的手感与时机、README 兼容表；**只有用户明确确认才勾**（2026-11-23 用户真机确认，含取出数量与拿起后放入面板两处修复）

## Dependencies

- T001 → T002 → T003；T004、T005 可并行；T006 依赖 T005；T007 独立；T008 依赖 T007；T009 在其后。
- US1：T010 依赖 T004、T006；T011 依赖 T010；T012 依赖 T005；T013 依赖 T008；T014 依赖 T012、T013；T015 依赖 T010、T013、T014；T016 依赖 T010～T015；T017 在其后。
- US2：T018 依赖 T002、T005、T013；T019 依赖 T018；T020 依赖 T018、T019；T021 在其后。
- US3：T022 依赖 T010、T013；T023 在其后。
- US4：T024、T025 可并行，依赖 T007；T026 依赖 T024、T025；T027 在其后。
- T028 依赖 US1 与 T027；T029 依赖 US2 与 T027；T030 在 T028、T029 之后；T031、T032 最后。

## Implementation Strategy

- **MVP**：Phase 1～3（集成终端取出）。
- US2（AE2）排在 US1 之后：若 T029 发现不可行就降级为文档，不影响 MVP。
- 每个 Phase 一次 `./gradlew compileJava test`，检查点都应是绿的。
- 协议号升级在 T010/T011，之前的任务不改协议。
- 不提交；做完、用户确认后由用户决定何时提交。
