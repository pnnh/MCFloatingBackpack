# Tasks: 被 Accessories 丢掉的背包（槽位消失时归还）

**Input**: `/specs/062-accessories-orphaned-backpacks/` 的 spec.md、plan.md、research.md、data-model.md、contracts/、quickstart.md；宪法

**Tests**: 纯逻辑（`OrphanRules`）用 JUnit；语言键、接线用资源/源码文本检查。**单元测试类路径里没有 Minecraft 与 Accessories 的类，NBT 读取、归还、下线事件的顺序只能在游戏会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`
- 不含提交 Git（等用户要求；也不打 tag）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；改配置前备份、验证后还原；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增依赖、mixin、反射；**协议号不改（22）**；**`DataRevision` 仍为 6**
- **没装 Accessories、档位非 FULL、没有槽位消失的玩家：行为与 062 之前完全一致**
- 用户故事：US1 归还（P1）；US2 不复制（P1）；US3 档位与兜底（P2）

## Phase 1: Setup

- [X] T001 基线：`./gradlew test`；类数与用例数记在 `specs/062-accessories-orphaned-backpacks/DELIVERY.md`（新建，中文）

## Phase 2: Foundational

- [X] T002 [P] `…/compat/OrphanRules.java`（纯）：`decide(entries, validSlotTypes, presentIdentities, fileUnidentified, liveUnidentified)` 返回要还的下标（data-model 的规则）；`…test/compat/OrphanRulesTest.java`：带编号在场/不在场、无编号缺几只还几只、不超过孤立数、槽位类型有效时不还、没有孤立的、同一只不重复、多种物品 id 分开计、空输入
- [X] T003 [P] 语言键 `message.mcfloatingbackpack.orphaned.returned`、`.dropped`（en_us、zh_cn）；`…test/resources/OrphanedBackpacksWiringTest.java` 第一部分：两个语言键中英文齐
- [X] T004 运行 `./gradlew compileJava test`

## Phase 3: User Story 1 - 归还 (Priority: P1) 🎯 MVP

**Goal**: 槽位类型消失后登录，躺在其中的浮动背包回到玩家物品栏（放不下掉脚边），有提示。

**Independent Test**: quickstart 情形 1、3、4、5、6。

- [X] T005 [US1] `…/compat/accessories/OrphanedBackpackScan.java`：读文件 NBT（`Scan`、`Found`；路径见 contracts；`readFile`、`read(CompoundTag)`；缺层/类型不对一律当没有，不抛）
- [X] T006 [US1] `…/compat/accessories/OrphanedBackpacks.java`：`Pending` 表、`onPlayerDataRead`、`onLogin`（20 刻倒计时）、`tick`、`onLogout`、`process`（档位门、现存槽位类型、收集在场、`OrphanRules.decide`、`ItemStack.parse`、`fallback()` 归还、提示与日志）、`clear`
- [X] T007 [US1] `…/MCFloatingBackpack.java`：在 Accessories 已加载的分支里挂 `PlayerEvent.LoadFromFile`、`PlayerLoggedInEvent`、`PlayerLoggedOutEvent`、`ServerTickEvent.Post`、`ServerStoppedEvent`；接线测试第二部分：只在 Accessories 分支、20 刻、下线事件、`fallback()`、没有 `io.wispforest` 引用（`OrphanRules`、`OrphanedBackpackScan`）、jar 里没有 Accessories 类
- [X] T008 [US1] 游戏内验证（quickstart 二、1、3～6）：复现 061 的丢失步骤，背包（含装了东西的、无编号的、装饰槽里的）回到物品栏；物品栏满时掉脚边；登录 1 秒内下线的竞态（结果决定 research R5 的措辞）；结果记在 DELIVERY.md

## Phase 4: User Story 2 - 不复制 (Priority: P1)

- [X] T009 [US2] 游戏内验证（quickstart 二、2）：改回开关再启动，物品总数不变、没有提示；再退出再进一次也没有；记在 DELIVERY.md（"玩家已拥有"的各种情形由 T002 的单元测试守）

## Phase 5: User Story 3 - 档位与兜底 (Priority: P2)

- [X] T010 [US3] 游戏内验证（quickstart 二、7～8）：`accessories=GENERIC` 不归还不提示；正常登录无提示、无多余日志

## Phase 6: Polish

- [X] T011 README.md 与 README_CN.md：把 061 写的"Accessories 不会归还、物品消失"改成"由本模组在登录时归还，限制是……"；`WornSlotsConfig` 的配置注释同步；061 的 DELIVERY 加一句指向 062；`docs/plan/worn-backpack-compat.md` 加一行
- [X] T012 `./gradlew build -PcleanRuntime -PnoJei`；DELIVERY.md 写完（已验证 / 没验证 / 取舍 / 已知局限 / 宪法复查）；更新 memory
- [X] T013 用户确认（2026-11-23，提示措辞没问题）：真机上的归还与提示措辞；**只有用户明确确认才勾**

## Dependencies & Order

- T002、T003 可并行；T004 收口。
- T005 → T006 → T007 → T008；T009、T010 依赖 T008；T011 依赖 T008、T009。
