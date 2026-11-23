# Feature Specification: 被 Accessories 丢掉的背包（槽位消失时归还）

**Feature Branch**: `062-accessories-orphaned-backpacks`

**Created**: 2026-11-23

**Status**: Draft

**Input**: 061 的游戏内验证发现：专用槽位的开关从开改成关、重启后，**Accessories 不会归还槽里的物品，物品消失**（Curios 会归还）；用户（2026-11-23）选了这个作为下一个 feature（"保护被 Accessories 孤立的背包"）。相关：061（专用槽位与开关，DELIVERY 里的实测表）、057（读玩家数据文件的做法 `SnapshotNotices.onPlayerDataRead`）、030（背包快照）、059（有效档位）。实测的事实（容器 `mcfb-c1`，Accessories 1.1.0-beta.54）：槽位类型消失后，玩家数据里该槽的内容在读入时被丢掉，物品栏里没有、地上没有、改回开关也没有回来；而磁盘上的玩家数据文件在下一次保存之前**仍然保留着**这些物品。

## 背景

现状（已核对）：

- 玩家数据里，Accessories 的数据在 `neoforge:attachments` → `accessories:inventory_holder` → `accessories_containers`，每个槽位类型一个条目（`slot_name`、`items`、`cosmetics`），`items` 与 `cosmetics` 是普通的物品 NBT（`id`、`count`、`Slot`、`components`）。读它不需要 Accessories 的任何类。
- 槽位类型消失的原因不止 061 的开关：整合包改了数据包、去掉了一个模组、改了配置。Accessories 对这些条目的处理是丢弃（本版本实测）；Curios 的是归还（`invalidStacks`）。
- 玩家数据文件在读入之后、下一次保存之前不变（保存发生在自动保存、下线、关服时），所以**登录时文件里还有被丢掉的物品**；057 已有"读入时再读一遍文件"的做法（`PlayerEvent.LoadFromFile`）。
- 浮动背包的内容在物品组件里（另有 030 的快照），所以保住背包物品就保住了内容。

本 feature 做一件事：**玩家登录时，如果他保存的数据里有浮动背包躺在一个现在已经不存在的 Accessories 槽位里，把它还给玩家**，并告诉玩家与服主。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 关掉专用槽位后，槽里的背包回到玩家手里 (Priority: P1)

服主把 `[slots] accessories` 改成 `false` 并重启。一个玩家下线前把一只装着东西的背包放在 Accessories 的"浮动背包"槽里。他再登录时，那只背包出现在他的物品栏里（满了就掉在脚边），里面的东西完整，聊天里有一行说明"某只背包原来在 Accessories 已不存在的槽位里，已还给你"。

**Why this priority**: 这是 061 实测发现的会丢物品的路径，也是这个 feature 存在的理由。

**Independent Test**: 容器里复现 061 的步骤：背包放进 Accessories 专用槽 → 退出 → 开关改 false → 启动进世界：背包在物品栏里，内容完整，有聊天提示；再退出、改回 true 启动：背包仍是一只（没有复制）。

**Acceptance Scenarios**:

1. **Given** 玩家数据里有一只浮动背包在已不存在的 Accessories 槽位类型里，**When** 他登录，**Then** 这只背包被放进他的物品栏（背包原样，组件不变）；放不下的掉在他脚边；聊天里有一行提示；服务器日志有一行记录（玩家、槽位类型、物品）。
2. **Given** 同上但背包在该槽位的装饰（cosmetics）列表里，**Then** 同样归还。
3. **Given** 同一个已不存在的槽位里有两只背包，**Then** 两只都归还，聊天提示一行说明数量。
4. **Given** 已不存在的槽位里除了背包还有别的物品，**Then** 本 feature **只**归还浮动背包，别的物品不动（它们是 Accessories 的事）。
5. **Given** 重启一次后再登录（上次登录之后玩家数据已保存），**Then** 不再有东西可还，没有提示。

---

### User Story 2 - 不会复制物品 (Priority: P1)

如果 Accessories 的某个版本自己归还了物品（或者玩家已经拿着它），本 feature 不会再还一次。

**Why this priority**: 归还类的功能最危险的失败就是复制；没有这一条，这个功能本身就成了漏洞。

**Independent Test**: 用单元测试覆盖"玩家已拥有"的各种情形；容器里在 Accessories 保持现状（会丢）的版本上验证归还一次、不多不少。

**Acceptance Scenarios**:

1. **Given** 要还的背包带有编号（身份）而玩家身上（物品栏、光标、所有背着的槽位、地上 16 格内）已经有同一编号，**Then** 不还。
2. **Given** 要还的背包没有编号（从未打开过），**When** 玩家身上这种物品的数量已经不少于保存时的数量，**Then** 不还；少几只，就还几只（不超过在已不存在的槽位里的数量）。
3. **Given** 登录后的短暂时间里 Accessories 或别的模组把物品还了，**Then** 本 feature 在那之后才核对，所以不会重复。

---

### User Story 3 - 服主可以控制它，出问题时不添乱 (Priority: P2)

这是"针对 Accessories 的专门适配"，服务端的 Accessories 档位不是 FULL（`GENERIC` 或 `OFF`）时不做；任何读取错误（数据格式变了、文件读不了）都只写一条日志，什么都不动、不影响登录。

**Why this priority**: 它依赖第三方数据格式，必须有关闭办法和失败兜底（宪法 VII）；但相比功能本身是次要的。

**Independent Test**: 服务端 `accessories=GENERIC` 时登录：不归还、不提示；把文件里的 Accessories 数据弄坏：不归还、日志一条、登录正常。

**Acceptance Scenarios**:

1. **Given** 服务端对 `accessories` 的有效档位不是 FULL，**Then** 本 feature 什么都不做。
2. **Given** 玩家数据里没有 Accessories 的数据，或结构不是预期的，**Then** 什么都不做，不报错给玩家，日志至多一行 DEBUG。
3. **Given** 没有装 Accessories，**Then** 什么都不做（不去读文件）。

---

### Edge Cases

- **背包带着内容**：归还的是整只物品（含内容、编号等组件），一个字节都不改。
- **物品栏满了**：沿用原版的"放不下就掉在脚边"，并在聊天提示里说明。
- **玩家登录时已经死亡/在别的维度**：归还到他当前所在的位置（登录后的玩家实体）。
- **登录很快又下线**：核对在登录后的延迟之后才做；若玩家在此之前下线，什么都不做，下次登录时文件里的东西仍在（因为没保存过就不会被覆盖）——但若期间已发生保存，物品已经丢了，这种极端情况不处理，写进已知局限。
- **两个来源都读到同一只**（容错）：按上面的数量/编号规则只还一次。
- **槽位还在但格数变少，放不下的物品**：不在本 feature 范围（本版本只处理整个槽位类型不存在的情形）；若实测发现 Accessories 同样会丢，另议。
- **Curios**：Curios 自己归还，不处理。
- **别的物品、别的模组的背包**：不处理。
- **单人游戏与专用服务器**：行为相同（读的是服务端的玩家数据文件）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 服务端 MUST 在玩家登录后（延迟一小段时间，使 Accessories 与别的模组先完成它们自己的处理），读取该玩家**登录时保存的数据**里 Accessories 的各个槽位容器（`accessories_containers` 下每个条目的 `items` 与 `cosmetics`），找出其中的浮动背包（五个等级的物品 id）。
- **FR-002**: 其中，所在槽位类型**在这个玩家现在不存在**的背包（Accessories 现在给这个玩家的槽位类型里没有它的 `slot_name`）MUST 被归还：放进玩家的物品栏，放不下的掉在玩家脚边；物品的全部组件原样保留。归还只增加玩家的物品，MUST NOT 删除或移动玩家现有的任何东西。
- **FR-003**: 系统 MUST NOT 复制物品：已归还或玩家已拥有的不再还。判断：背包带编号（`backpack_identity`）时，玩家身上（物品栏、光标、所有背着的槽位类型、地上 16 格内的掉落物）已有同编号则不还；没有编号时，按该物品 id 在保存数据里的总数与玩家现在的总数比较，缺几只才还几只，且不超过"在已不存在的槽位里的数量"。
- **FR-004**: 归还后 MUST 给该玩家发一条聊天提示（语言文件，中英文）：说明有几只背包原来在 Accessories 已不存在的槽位里、已还给他；放不下掉在脚边时要说明。MUST 写一条服务器日志（玩家、槽位类型、物品、数量、去了哪里）。
- **FR-005**: 本 feature 是对 Accessories 的专门适配：服务端对 `accessories` 的有效档位不是 FULL 时 MUST 什么都不做；没有装 Accessories 时 MUST 什么都不做；读取与解析 MUST 只用普通 NBT，MUST NOT 引用 Accessories 的类型（宪法 VII），且 MUST NOT 使用反射或 mixin。
- **FR-006**: 任何异常（文件读不了、结构不对、字段缺失）MUST 被捕获：不归还、不影响登录、日志至多一行（WARN 只用于确认丢了东西而没能还的情形）。
- **FR-007**: 归还 MUST 在服务端做（宪法 II）；MUST NOT 新增网络包、MUST NOT 改协议号、MUST NOT 改背包存储数据（`DataRevision` 仍 6）。
- **FR-008**: 文档：README 与 README_CN 中 061 写的"Accessories 不会归还、物品消失"MUST 改成新的说明（由本模组归还，以及它的限制）；`mcfloatingbackpack-startup.toml` 的配置注释 MUST 同步；061 的 DELIVERY 里的记录 MUST 加一个指向本 feature 的说明。
- **FR-009**: 系统 MUST 有自动测试：从 NBT 里找出孤立的背包（槽位类型存在与不存在、`items` 与 `cosmetics`、带编号与不带编号、别的物品不动、结构缺失）、不复制的各种情形、档位门、日志与提示的文字、接线（只在 Accessories 已加载时、延迟后做、没有 Accessories 类型的引用）。**游戏内验证**：复现 061 的步骤。

### Key Entities *(include if feature involves data)*

- **孤立的背包**：保存的玩家数据里躺在一个现在不存在的 Accessories 槽位类型中的浮动背包。
- **归还**：把孤立的背包放进玩家物品栏（放不下掉在脚边），并告诉玩家与服主。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 复现 061 的步骤（Accessories 专用槽里有一只装着东西的背包 → 开关改 false → 重启 → 登录），背包**100% 回到玩家手里**，内容完整，聊天里有提示。
- **SC-002**: 同一种情形再登录一次，物品总数**不增加**（0 次复制）；"玩家已经拥有"的各种情形都有测试。
- **SC-003**: 服务端 `accessories=GENERIC`、没有 Accessories、数据损坏时，**0 次**归还、登录**不受影响**。
- **SC-004**: 对没有槽位消失的玩家，登录的行为、耗时与之前**没有可感知的差别**（只读一次已有的玩家数据文件，且只在装了 Accessories 时）。

## Assumptions

- 玩家数据文件在登录后到第一次保存之前保持不变（vanilla 与 NeoForge 的行为；057 同样依赖它）；本 feature 靠它找到被 Accessories 丢掉的东西。
- Accessories 的数据格式（`accessories_containers`、`items`、`cosmetics`、`slot_name`）是普通 NBT，本版本（1.1.0-beta.54）已核对；Accessories 是 beta，格式可能变，所以读不懂时一律不动（FR-006），且档位可以关掉它。
- "现在不存在"的判断用 Accessories 当前给这个玩家的槽位类型（通过它在服务端的公开能力读到的容器名）；具体用哪个入口由 plan 决定（若需要第三方类型，只放在 `compat/accessories/`，且受宪法 VII 约束）。
- 延迟多久由 plan 决定（几秒），原则是晚于 Accessories 自己的归还、早于玩家可能丢弃或移动东西的窗口。
- 只处理浮动背包；整个槽位类型不存在的情形；Curios 不处理（它自己归还）；槽位还在、格数变少的情形不处理。
- 极端情况（登录后延迟之前就发生了一次保存，或服务器在这期间崩溃）下物品仍可能丢：写进已知局限；背包内容还可以从 030 的快照恢复。
