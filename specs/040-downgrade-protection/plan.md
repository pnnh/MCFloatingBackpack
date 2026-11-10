# Implementation Plan: 降级保护

**Branch**: `040-downgrade-protection` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

## Summary

给背包数据加一个可选的**数据修订**，更新版本保存的背包在当前版本里被保护（设计依据见 [research.md](./research.md)，其中降级会发生什么已经读过 Minecraft 源码，但**仍必须由实验确认**）：

1. **存储**：`BackpackContents` 加第 8 个字段 `data_revision`（`optionalFieldOf`，缺省 0）；**只改磁盘编码（`CODEC`），不改手写的 `STREAM_CODEC`**——组件的网络编码变了就要升协议号（035 就因此升过一次），而本功能要求协议号仍是 17。客户端因此看不到修订；保护是服务器侧的，客户端不需要它（已知局限：创造模式下客户端把背包栈写回服务器会"洗掉"修订，见 research D2）。
2. **常量与纯逻辑（新，无 Minecraft 类型）**：`DataRevision`（`CURRENT = 1`、`isNewer(stored)`）；`RevisionGuard`（给定"背包的修订、当前修订、这次是不是明确的打开、这位玩家对这只背包是否有未过期的确认窗口"，决定 `ALLOW` / `REFUSE_FIRST` / `CONFIRMED` / `REFUSE`）；`ProtectionWindows`（确认窗口表：玩家加背包键 → 过期时间，时间由调用方传入，60 秒）。有完整单元测试。
3. **统一入口**：`BackpackGate` 在做任何会写背包的事**之前**读背包栈的修订（**先于分配身份与快照记录**，否则保护本身就改写了背包）；两个入口：
   - `acquireForOpen`（只给 `BackpackOpenRequestPayload` 用）：被保护 → 第一次拒绝并给玩家提示、开窗口；窗口内再次打开 → **放行并立即把栈上的修订改成当前**（确认即降级盖章，所以之后锻造台等路径也恢复正常）；
   - `acquire` / `acquireForPanel`（其它所有路径）：被保护 → 静默返回空（不提示、**不算确认**，否则自动拾取会在窗口内把保护悄悄确认掉）。
4. **自动拾取**：`acquire` 返回空就跳过（已经是这样）；加一条**限频的 WARN 日志**（每位玩家每分钟至多一条），不发聊天。
5. **锻造台升级**：`BackpackUpgradeRecipe.matches` 对被保护的背包返回假（只读基础背包的修订，不动它）。
6. **保存**：`BackpackInventory.toContents` 盖上 `DataRevision.CURRENT`；被保护的背包不会走到保存（前面都被拒绝），所以不会把高修订盖低，除非玩家确认。
7. **提示与日志**：聊天一条只有本人的提示（中英文语言键，写明两个修订、建议升级模组或先备份存档、60 秒内再次打开表示接受风险）；服务器日志 WARN：被保护、被确认放行。
8. **开发验证**：`-Pmcfb.stampRevision=99` 让保存时盖成 99（造出"更新版本保存的背包"，一个客户端就能验证），沿用 039 的调试开关写法。
9. **实验与文档**：实验手册与辅助脚本（降级到 035 之前的构建，在备份过的世界副本上观察）；`docs/plan/release-process.md` 写明"何时递增修订"与本版本为 1；README 加降级提醒。

不新增配置项、命令；不改协议号 17 与现有网络包；不改快照。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 背包栈上 `backpack_contents` 组件里多一个可选整数字段；确认窗口在服务器内存里

**Testing**: JUnit 5：修订比较与保护决策（`RevisionGuard`）、窗口过期与隔离（`ProtectionWindows`，时间由调用方传入）、提示文字的选择、语言键、源码接线文本。游戏里的行为（面板被拒、两步确认、自动拾取跳过、锻造台不匹配）与**降级实验**只能在客户端会话里做（宪法 V）

**Target Platform**: 服务器侧（含单人与局域网）；开发机 macOS；**不运行专用服务器**

**Constraints**: 宪法 II（服务器权威）、III（只做降级保护；解码层面的"保留"留给可能的 041）、V、VII；协议 17 不变；组件的 `STREAM_CODEC` 不变

**Scale/Scope**: 新增约 4 个类（`DataRevision`、`RevisionGuard`、`ProtectionWindows`、一个小的调试开关读取）+ 小改 `BackpackContents`、`BackpackInventory`、`BackpackGate`、`BackpackOpenRequestPayload`、`AutoPickupEvents`、`BackpackUpgradeRecipe`、`VersionDebug`/`build.gradle`、语言文件（约 2 条 × 2）、约 5 个测试类

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及面板绘制；被拒时只是面板不打开并有聊天提示 | PASS |
| II. Server Owns Items | 保护与确认全在服务器；客户端不能越过它（客户端看不到修订） | PASS |
| III. One Feature At A Time | 只做修订标记与保护；不改快照；解码层面的丢失留给 041 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方模组 | PASS |
| V. Verify In A Client Session | quickstart 用调试开关造"更新版本保存的背包"验证大部分，再按实验手册做一次真实降级；**实验未做之前不声称保护有效范围** | PASS（已规划，限制已记录） |
| VI. Shared Resources Have A Budget | 无新贴图、无图集变化 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖 | PASS |

## 对规格的说明

1. **修订放在 `BackpackContents` 里，而不是单独的数据组件**：单独的组件对 040 之前的版本是"不认识的组件类型"，物品栈解码会整个失败（`DataComponentPatch` 对未知组件报错，`Inventory.load` 把失败的栈读成空）——旧版本玩家会**整只背包消失**，比丢字段严重得多。放进记录里的可选字段，旧版本会忽略它。
2. **不改 `STREAM_CODEC`**：组件网络编码变了要升协议号，规格要求 17 不变。代价见上：客户端看不到修订，创造模式下客户端写回的背包栈没有修订（读作 0）。这是已知局限，写进交付说明；要彻底解决得升协议，另议。
3. **确认由"明确的打开"触发，不由任何路径触发**：规格里写"再次打开"；实现里只有 `BackpackOpenRequestPayload` 能确认，自动拾取、锻造台等路径即使在窗口内也不会确认。
4. **确认时立即盖章**（把栈上的修订改成当前）而不是"等下一次保存"：否则确认之后背包仍然是高修订，锻造台与自动拾取仍被挡住，直到碰巧保存一次。规格里"放行后按当前修订保存"的效果相同，只是更早。
5. **自动拾取只写限频日志，不发聊天**（规格只要求"不刷屏"）。

## Project Structure

### Documentation (this feature)

```text
specs/040-downgrade-protection/
├── plan.md
├── research.md        # 降级会发生什么（源码核对 + 假设）、网络编码取舍、入口核对
├── data-model.md
├── quickstart.md      # 调试开关验证 + 真实降级实验手册
├── contracts/
│   ├── protection.md  # 入口、决策表、确认窗口、哪些路径受保护
│   └── texts.md       # 提示文字、日志格式
├── checklists/requirements.md
└── tasks.md           # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/revision/
│   ├── DataRevision.java        # 新：CURRENT、isNewer（纯逻辑）
│   ├── RevisionGuard.java       # 新：决策表（纯逻辑）
│   └── ProtectionWindows.java   # 新：确认窗口（纯逻辑，时间由调用方传入）
├── inventory/BackpackContents.java   # 改：第 8 个字段 dataRevision（CODEC），STREAM_CODEC 不动
├── inventory/BackpackInventory.java  # 改：toContents 盖章
├── inventory/identity/BackpackGate.java  # 改：acquireForOpen；acquire/acquireForPanel 先查修订
├── network/BackpackOpenRequestPayload.java # 改：改用 acquireForOpen
├── inventory/pickup/AutoPickupEvents.java  # 改：被保护时限频日志
├── inventory/recipe/BackpackUpgradeRecipe.java # 改：matches 对被保护的背包为假
└── version/VersionDebug.java    # 改：stampRevision 开关
build.gradle                      # 已有 -Pmcfb.* 转发，不用改
design/demo/tools/downgrade_experiment.sh  # 新：实验辅助脚本（建 worktree、拷世界副本、打印步骤）
src/main/resources/assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json
README.md / README_CN.md / docs/plan/release-process.md
src/test/java/pnnh/mcfloatingbackpack/inventory/revision/{RevisionGuardTest, ProtectionWindowsTest}.java
src/test/java/pnnh/mcfloatingbackpack/resources/DowngradeProtectionResourcesTest.java
```

**Structure Decision**: 决策与窗口是无 Minecraft 类型的纯逻辑；对现有代码只在统一入口、保存、锻造台与自动拾取四处接线。

## Complexity Tracking

无需要说明的违规。
