# Tasks: 禁止浮动背包嵌套

**Tests**: 纯逻辑（`NestingRules`、`NestedOrigins`）用 JUnit；各入口的接线用源码文本检查。**单元测试类路径里没有 Minecraft 的物品栈，原版容器的读取、`canFitInsideContainerItems` 的实际效果、各入口的真实点击只能在游戏会话里验证**（宪法 V）。

## Format

- `…/` = `src/main/java/pnnh/mcfloatingbackpack/`，`…test/` = `src/test/java/pnnh/mcfloatingbackpack/`
- 不含提交 Git（等用户要求）；启动客户端前先 `pgrep -f "[f]ml.modFolders=mcfloatingbackpack"`；容器里 `-PnoJei`；测试用的数据包函数用完删除；AI 自动验证的勾选规则同前（有明确证据才勾，主观项只由用户确认）
- 不新增依赖、mixin、反射；**协议号不改（22）**；**`DataRevision` 仍为 6**；没有嵌套时行为与 064 之前完全一致

## Phase 1: 基线与纯逻辑

- [X] T001 基线：246 个测试类 / 1881 项（063 之后）
- [X] T002 [P] `…/inventory/nesting/NestingRules.java`（纯）：`Verdict`、`Probe<T>`、`check`，深度上限 8；`…test/inventory/nesting/NestingRulesTest.java`（背包本身、普通物品、空容器、含背包、多层、空子项、通用兜底、兜底只在有不可读数据时问、超过深度）
- [X] T003 [P] `…/inventory/nesting/NestedOrigins.java`（纯、泛型）：`remember` / `matches`（不用掉）/ `forget` / `clear`；`…test/inventory/nesting/NestedOriginsTest.java`

## Phase 2: Minecraft 适配与入口

- [X] T004 `…/inventory/nesting/BackpackNesting.java`：`Probe<ItemStack>`（原版容器、收纳袋结构化读取，别的模组数据编码后查五个物品 id）、来源记录的静态存取、提示、旧嵌套 INFO
- [X] T005 `…/item/FloatingBackpackItem.java`：重写无参 `canFitInsideContainerItems()` 返回 false
- [X] T006 `…/inventory/BackpackInventory.java`：`mainTargetOrder` 对有风险的物品返回三段全空，新增 `mainTargetOrderInternal`
- [X] T007 入口：`BackpackClickPayload`（主区域、工具区、手里互换，拿起时写记录、放回时用掉）、`BackpackCursorGesturePayload`（分摊）、`BackpackQuickTransferPayload`（物品栏 → 面板）、`BackpackTakeFromContainerPayload`（容器 → 面板，取之前判定）、`BackpackNetworkTakePayload`（终端 → 面板，提取之前判定）、`BackpackTrashPayload`（推入一律拒绝，取出写记录，还原用内部版本）、`BackpackTrashSendPayload`（Delete/倾倒时过滤）、`BackpackOpenRequestPayload`（旧嵌套 INFO）；自动拾取经 `mainTargetOrder` 自动继承
- [X] T008 `MCFloatingBackpack.java`：关闭界面、下线、世界停止时清记录；语言键 `message.mcfloatingbackpack.nesting.refused`、`.contains`（en_us、zh_cn）
- [X] T009 `…test/resources/NestingWiringTest.java`：各入口调用了判定、`canFitInsideContainerItems` 重写、内部版本只被垃圾桶还原用、终端先判定再提取、记录的清理、不含第三方类型、语言键
- [X] T010 `./gradlew test -PcleanRuntime -PnoJei`：249 个测试类 / 1907 项 0 失败

## Phase 3: 游戏内验证（容器 `mcfb-c1`）

- [X] T011 按 [quickstart.md](./quickstart.md) 逐条验证并记入 DELIVERY（直接禁各入口、深度扫描、潜影盒/收纳袋/旅行者背包拒收、旧嵌套取出与放回、精妙背包按配置）；验证不到的如实写明

## Phase 4: 文档

- [X] T012 README 两份（嵌套规则、精妙背包的配置做法、网络存储不限制、旧数据）；021 spec 修订（FR-018、第 101 行的验收）；`docs/plan/no-nested-backpacks.md`；064 的 DELIVERY.md
- [X] T013 `./gradlew build -PcleanRuntime -PnoJei`
