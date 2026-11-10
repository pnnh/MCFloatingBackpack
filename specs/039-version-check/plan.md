# Implementation Plan: 版本一致性提示

**Branch**: `039-version-check` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

## Summary

玩家登录服务器时，两端的**本模组版本** MAJOR.MINOR 不同就只给他自己一条聊天警告；老客户端（没有这个功能）由服务器发纯文本提示；服务器记日志；客户端对未知类别的预测放行（设计依据见 [research.md](./research.md)，其中的 NeoForge 行为已经在字节码里核对过）：

1. **纯逻辑（无 Minecraft 类型，新）**：`ModVersions`（解析语义化版本、按"MAJOR.MINOR 加预发布优先级"比较，PATCH 与构建元数据不参与，给出 `MATCH / CLIENT_OLDER / CLIENT_NEWER / UNCOMPARABLE`）与 `VersionNotices`（按结果选择提示类型；老客户端的双语纯文本）。有完整单元测试，覆盖规格要求的全部版本字符串边界。
2. **握手（新，可选网络包，不改协议号 17）**：`VersionHelloPayload`（服务器 → 客户端：服务器版本）与 `VersionReportPayload`（客户端 → 服务器：客户端版本），在**单独的注册器** `event.registrar(MOD_ID).versioned("1").optional()` 里注册，与现有 `versioned("17")` 的注册器互不影响；这个版本号 `"1"` **永远不改**（以后要演进就新增另一个包类型）。
3. **服务器登录钩子**：`PlayerLoggedInEvent`：房主本人（`server.isSingleplayerOwner(profile)`，覆盖单人游戏与局域网房主）→ 什么都不做；其余玩家 → 若 `connection.hasChannel(VersionHelloPayload.TYPE)` 则发 hello，否则（老客户端）发双语纯文本并记 WARN。**向没有通道的客户端发包会抛 `UnsupportedOperationException`（已在 NeoForge 字节码里核对），所以必须先 `hasChannel`。**
4. **客户端**：收到 hello → 用 `ModVersions` 比较 → 不一致就在**下一个客户端 tick** 用现有的 `FloatingPanelEvents.chat` 输出警告（中英文语言键）；一致不输出；无论是否一致都回一个 report（让服务器知道版本）。
5. **服务器记录**：`PlayerVersions`（玩家 UUID → 客户端版本 / "无版本检测" / "尚未报告"）；report 到达时比较并记日志（不一致 WARN，一致 DEBUG）；`BackpackWatchService` 的"不见了"WARN 头部带该玩家的版本；退出时清除。
6. **预测宽容**：`RowLayout` 加 `isUnknownRow(index)`（该格所在行的类别不在 `ExtensionCategories.ALL` 里）；`ClientPlacement.canPlace` 对未知类别的行恒返回放行（`ItemCategoryMatcher` 与服务端规则不动：服务端对未知类别行仍然拒绝）。
7. **验证用的调试开关**（默认关）：几个 `-Dmcfloatingbackpack.debug.*` 系统属性，让一个客户端就能在开发里验证大部分场景（伪造两端版本、不跳过房主、把客户端当作老客户端），见 research D7；`build.gradle` 加一个把 `-Pmcfb.<名字>` 转成系统属性的转发。
8. **文档**：README 与发布说明页脚的"多人游戏"写明版本规则；`docs/plan/release-process.md` 更新 039 状态。

不新增配置项、命令；不改现有网络包与协议号；服务器**只提醒，不拒绝、不踢人**。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1；不新增依赖

**Storage**: 无（版本信息只在内存里：`PlayerVersions`）

**Testing**: JUnit 5。纯逻辑（版本解析与比较、提示选择、老客户端文本、`RowLayout.isUnknownRow`）与文本检查（语言键、接线）不加载 Minecraft；网络协商、聊天显示、老客户端能否登录只能在客户端会话里验证（宪法 V）

**Target Platform**: 客户端 + 服务器（含局域网）；**不运行专用服务器**（项目约定）。老客户端的真实协商需要用户用 038 的构建当第二个客户端手动验证

**Constraints**: 宪法 II（服务器仍是权威；本功能只提醒）、III（只做版本提示，降级保护是 040）、V、VII；协议 17 不变；对现有包零影响

**Scale/Scope**: 新增约 8 个类（`ModVersions`、`VersionNotices`、`VersionHello/ReportPayload`、`VersionNetwork`、`PlayerVersions`、`VersionDebug`）+ 小改 `BackpackNetwork`、`MCFloatingBackpack`、`BackpackWatchService`、`RowLayout`、`ClientPlacement`、`build.gradle`、语言文件（约 3 条 × 2）、约 5 个测试类

## Constitution Check

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 不涉及面板与 Screen；只往聊天写一条文字 | PASS |
| II. Server Owns Items | 不碰物品存储；版本信息只用于提示，服务器不据此改变任何规则 | PASS |
| III. One Feature At A Time | 只做版本提示（含一个与之相关的小兜底：预测宽容）；降级保护留给 040 | PASS |
| IV. Vanilla Path Before Compat | 不涉及第三方模组 | PASS |
| V. Verify In A Client Session | quickstart 用调试开关在一个客户端里验证提示与日志，再用 038 的构建当第二个客户端验证老客户端真实协商；**专用服务器不跑，记为未验证** | PASS（已规划，限制已记录） |
| VI. Shared Resources Have A Budget | 无新贴图、无图集变化 | PASS |
| VII. Minimal Dependencies, Opt-Out Compatibility | 无新依赖；只用 NeoForge 自带的可选包机制 | PASS |

## 对规格的说明

1. **"可选包放进单独的注册器、不影响协议号 17"已核对**：`PayloadRegistrar.versioned()/optional()` 每次返回一个新的注册器副本（字节码里是复制构造），所以 `event.registrar(MOD_ID).versioned("1").optional()` 与现有的 `versioned("17")` 互不影响；`NetworkComponentNegotiator` 对"一方有、另一方没有"的**可选**组件按"禁用"处理而不是拒绝连接。**但这仍是字节码层面的结论，没有跑过两个真实版本的连接**，所以 quickstart 里留了"老客户端真实登录"一项由用户验证，这一项失败就要重新设计机制（违反 FR-006）。
2. **局域网房主的判断用 `server.isSingleplayerOwner(profile)`**（单人游戏与局域网房主都为真，局域网里别的玩家为假）；比规格里设想的"`isSingleplayer()`加所有者"更简单，一个判断就够。

## Project Structure

### Documentation (this feature)

```text
specs/039-version-check/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── handshake.md   # 两个包、时序、谁发谁收、何时不发
│   └── texts.md       # 提示文字（中英文语言键、老客户端纯文本）、日志格式
├── checklists/requirements.md
└── tasks.md           # /speckit-tasks 生成
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── version/
│   ├── ModVersions.java         # 新：解析与比较（纯逻辑）
│   ├── VersionNotices.java      # 新：结果 → 提示类型；老客户端双语纯文本（纯逻辑）
│   ├── PlayerVersions.java      # 新：服务器侧每位玩家的版本记录（内存）
│   ├── VersionDebug.java        # 新：调试系统属性（默认关）
│   └── VersionNetwork.java      # 新：注册两个可选包、服务器登录钩子、客户端处理
├── network/{VersionHelloPayload, VersionReportPayload}.java   # 新
├── network/BackpackNetwork.java # 改：在注册事件里再调 VersionNetwork.register
├── MCFloatingBackpack.java      # 改：登录/退出钩子
├── diag/BackpackWatchService.java # 改：WARN 头部带玩家版本
├── inventory/extension/RowLayout.java # 改：isUnknownRow
└── client/panel/ClientPlacement.java  # 改：未知类别行放行
build.gradle                      # 改：-Pmcfb.* 转发为系统属性
src/main/resources/assets/mcfloatingbackpack/lang/{zh_cn,en_us}.json
README.md / README_CN.md / .github/release-notes/_footer.md / docs/plan/release-process.md
src/test/java/pnnh/mcfloatingbackpack/
├── version/{ModVersionsTest, VersionNoticesTest}.java
├── inventory/extension/RowLayoutTest.java（加用例）
└── resources/VersionCheckResourcesTest.java
```

**Structure Decision**: 比较与提示选择是纯逻辑（可测）；网络与登录钩子集中在 `VersionNetwork`；对现有代码只做最小接线。

## Complexity Tracking

无需要说明的违规。
