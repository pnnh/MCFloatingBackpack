# Feature Specification: 饰品模组里的专用槽位与通用槽位

**Feature Branch**: `061-worn-backpack-slots`

**Created**: 2026-11-23

**Status**: Draft

**Input**: 用户（2026-11-23）提出：033/034 让浮动背包能放进 Curios、Accessories 的 `back` 槽，但别的背包和别的模组也抢这个槽位；希望浮动背包能放进别的槽位，最好有自己的专用槽位，并且"同一种槽位类型里只能放一只、不论等级"。经过两轮讨论，决策已与用户确认、**不重新讨论**（记在 memory `project_worn_slots_061`）：①"一只"的范围是每个槽位类型内最多一只；②`back` 继续允许；③第一版做**专用槽位 `floating_backpack`** 并**同时支持通用的 `charm` 槽**，两个模组都做；④专用槽位不强制注册：FULL/GENERIC/OFF 管的是"我们的适配代码做不做"，不适用于"向第三方模组注册内容"，所以注册由一个**启动时读取的布尔开关**控制（每个模组一项，默认开），而不是按档位条件注册；⑤槽位数据在服务端，客户端档位只影响客户端的视觉与识别；⑥不处理与别的背包模型的重复渲染。相关：033（Curios `back` 槽）、034（Accessories `back` 槽）、036（Accessories 的右键装备）、059（有效档位）。

## Clarifications

### Session 2026-11-23（方案阶段，用户已确认）

- Q: "每个槽位类型只能放一只"是哪个范围？ → A: 每个槽位类型内最多一只（例如 `charm` 有多格也只能放一只；`floating_backpack` 与 `back` 各放一只仍然允许，此时 `B` 键、背部渲染取查找顺序里第一只，其余原样不动）。
- Q: `back` 还允许放浮动背包吗？ → A: 继续允许，tag 不变，老存档不受影响。
- Q: 第一版放哪些槽位？ → A: 专用槽位 `floating_backpack` 和通用槽位 `charm`，Curios、Accessories 各一套；`curio`、`accessories:any` 等别的槽位不做。
- Q: 怎么避免"强制新增槽位"？ → A: 不按 FULL/GENERIC/OFF 条件注册（SERVER 类型配置晚于数据包加载，且槽位消失的后果取决于对方模组）；改用启动时读取的布尔开关，每个模组一项、默认开，改动需重启；整合包作者也可用数据包覆盖槽位文件。
- Q: 客户端把 Curios 设成 OFF/GENERIC 会怎样？ → A: 只影响客户端的视觉与识别（不画、不当作背着的）；槽位仍在，数据都在服务端，改回 FULL 就能重新看到并取下背包。联机时客户端无法隐藏槽位（槽位数据由服务端同步）。

## 背景

现状（已核对源码与两个模组的 jar）：

- **只认 `back` 槽**：`CuriosWornSource`、`AccessoriesWornSource` 里槽位类型写死为 `"back"`；背包进槽靠 `curios:back`、`accessories:back` 两个 item tag；`curios/entities` 数据文件把 `back` 分配给玩家（Curios 自己不给任何实体分配槽位）。
- **数据驱动的槽位**：Curios 9.5.1 的槽位是 `data/<命名空间>/curios/slots/<id>.json`，实体分配在 `curios/entities/*.json`；Accessories 1.1.0-beta.54 是 `data/<命名空间>/accessories/slot/<id>.json` 与 `accessories/entity/*.json`。两个模组的加载器都支持 `neoforge:conditions`。槽位的物品靠 `curios:<槽位 id>`、`accessories:<槽位 id>` 这类 tag 决定（`tag` 校验器）。
- **Accessories 默认已给玩家 `charm`**（`default.json` 列了它）；**Curios 不给任何默认槽位**，`charm` 由整合包提供。
- **一个槽位类型可以有多格**（整合包可改数量）；两个模组在槽位类型消失时都有"无效物品"处理（Curios 的 `invalidStacks`、Accessories 的 `InvalidStacksAttribute`；读字节码，推断是还给玩家，**未在游戏里验证**）。
- **来源枚举**：`BackpackRef.Source` 末尾追加新值不会改变已有序号；`WornBackpacks` 按 `order()` 排的来源表依次找，"先找到的生效"。
- **服务端档位（059）**：来源是否生效问 `ServerCompatLevels.specificAdaptation(modId)`，只有 FULL 才生效；服务端 OFF 不会删除或移动槽里的物品，只是不再被当作背着的。
- **配置**：客户端配置（`compat.modLevels`）与服务端配置（`[compat]`）都在；还没有"启动时就读取"的配置（客户端配置在启动时加载，服务端配置在数据包之后才加载）。

本 feature 做四件事：**专用槽位**（不再抢别人的槽）、**通用 `charm` 槽**、**每类一只**（FULL 时由两个模组的"能否装备"判断拦住第二只）、**可关闭**（启动时的布尔开关，关了就不注册专用槽位）。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 把浮动背包放进专用槽位 (Priority: P1)

装了 Curios 的玩家打开饰品界面，看到一个"浮动背包"槽位（有自己的图标和名字），只有浮动背包能放进去；放进去后与放进 `back` 槽一样：背在背上被画出来，`B` 键从它打开面板。装了 Accessories 的玩家同样。别的模组的背包、别的物品都放不进这个槽，所以不再需要和它们抢 `back`。

**Why this priority**: 这是整个 feature 的目的：给浮动背包一个不会被别人占用的位置。

**Independent Test**: 只装 Curios：从饰品界面把浮动背包放进"浮动背包"槽，按 `B` 打开的是它；换成别的背包模组的背包，放不进；Accessories 同样做一遍。

**Acceptance Scenarios**:

1. **Given** 装了 Curios 且开关为默认，**When** 玩家把一只浮动背包拖进"浮动背包"槽，**Then** 放得进去，`B` 键打开它，背部渲染画出它。
2. **Given** 同上，**When** 玩家把别的物品（含别的模组的背包）拖进该槽，**Then** 放不进。
3. **Given** 装了 Accessories 且开关为默认，**When** 做同样的事，**Then** 结果相同。
4. **Given** 浮动背包放在 `back` 槽，**When** 玩家按 `B`，**Then** 与改动前完全一样（`back` 继续可用）。
5. **Given** 专用槽与 `back` 槽各有一只浮动背包，**When** 玩家按 `B`，**Then** 打开查找顺序里第一只，另一只原样不动、没有丢失。

---

### User Story 2 - 每个槽位类型最多一只 (Priority: P1)

玩家（或整合包）把某个槽位类型调成多格时，同一个类型里仍然只能放一只浮动背包，不论是哪一级；第二只放不进去（该槽位的"能否放入"为否）。不同类型各放一只仍然允许。

**Why this priority**: 没有这条限制，`charm` 之类多格槽位里会放进多只背包，`B` 键只认第一只，其余成为摆设，容易让人误以为丢了。

**Independent Test**: 把 `charm` 调成 3 格，放一只皮革背包后再放一只钻石背包，第二只放不进；在专用槽、`back` 槽各放一只仍然成功。

**Acceptance Scenarios**:

1. **Given** 一个多格的槽位类型里已有一只浮动背包，**When** 玩家把另一只（任意等级）放进同类型的另一格，**Then** 放不进。
2. **Given** 同上，**When** 玩家把背包放进另一种槽位类型，**Then** 放得进（各类型各一只）。
3. **Given** 该模组的档位不是 FULL，**When** 做同样的事，**Then** 不拦（我们的适配没有启用，不限制）。
4. **Given** 已经放了两只（命令、创造模式等绕过了限制），**Then** 第一只生效，第二只原样保留、没有丢失。

---

### User Story 3 - 通用 `charm` 槽也能放 (Priority: P2)

玩家不想用专用槽位，或者整合包关掉了专用槽位，仍然可以把浮动背包放进 `charm` 槽（Curios、Accessories 各自的 `charm`）。放进后与 `back` 槽一样工作；同样每类一只。

**Why this priority**: 它是专用槽位关闭时的落点，也是"不强制注册"的另一半；工作量小（tag 与来源）。

**Independent Test**: 开关关闭（不注册专用槽），Accessories 里把浮动背包放进 `charm`，`B` 键打开它；Curios 在有 `charm` 槽的整合包里同样。

**Acceptance Scenarios**:

1. **Given** 玩家有 `charm` 槽，**When** 放入一只浮动背包，**Then** 放得进，`B` 键与背部渲染按它工作。
2. **Given** 整合包没有给 Curios 玩家提供 `charm` 槽，**When** 玩家打开饰品界面，**Then** 没有 `charm` 槽，本 feature 不强行添加（背着仍可用 `back`、专用槽）。

---

### User Story 4 - 管理员可以关掉专用槽位的注册 (Priority: P2)

不想要多一个槽位的服主或整合包作者，在配置里把对应开关设为 `false` 并重启，Curios 或 Accessories 里就不再出现"浮动背包"槽位；`back`、`charm` 的支持不受影响。开关默认开启。

**Why this priority**: 这是"不引入强制、不可关闭的改动"的体现；优先级低于功能本身，因为多数玩家不会关。

**Independent Test**: 开关设为 false 重启，饰品界面里没有"浮动背包"槽；设回 true 重启，槽位回来；两个模组的开关互不影响。

**Acceptance Scenarios**:

1. **Given** Curios 的开关为 false，**When** 进入世界，**Then** Curios 里没有"浮动背包"槽位，`back` 与 `charm` 照常。
2. **Given** Accessories 的开关为 false 而 Curios 的为 true，**Then** 只有 Accessories 里没有这个槽。
3. **Given** 开关从 true 改成 false 前，玩家的背包放在专用槽里，**When** 重启进入世界，**Then** 背包不被删除（由饰品模组把它还给玩家，具体结果在游戏里验证后写入文档）。

---

### User Story 5 - 档位、文档与测试 (Priority: P3)

README 的兼容表说明三类位置（`back`、专用、`charm`）、每类一只、开关与重启、客户端档位在联机时只影响视觉；`docs/plan/third-party-compat.md` 记下设计原则：FULL/GENERIC/OFF 管适配代码，"向第三方注册内容"另有按模组的启动时配置。有自动测试守住这些。

**Independent Test**: 阅读文档与运行测试。

**Acceptance Scenarios**:

1. **Given** 新增或删除一种来源、tag 或槽位文件，**Then** 接线测试与漂移测试失败（来源枚举只追加、新 tag 与槽位文件齐全、`mods.toml` 依赖范围不变）。

---

### Edge Cases

- **装了两个饰品模组**：两个模组各有自己的专用槽与 `charm`，各自每类一只；一个玩家最多可能同时背着多只，`B` 键与背部渲染按固定顺序取第一只，其余原样不动。
- **整合包改了槽位数量**：限制是"每类一只"，与格数无关；格数为 0 的类型没有槽，什么都不发生。
- **槽位类型被关闭（开关 false）而槽里有背包**：由饰品模组处理（读字节码判断会还给玩家）；本 feature 不替它做，也不删除；游戏里验证结果写入文档，若发现会丢失，README 必须警告"改开关前先取下背包"。
- **客户端把模组设为 OFF/GENERIC**：客户端不画、不当作背着的；槽位仍在界面里（联机时由服务端同步，客户端无法隐藏），放进去的背包在服务端仍按服务端有效档位生效。
- **服务端档位为 GENERIC/OFF**：槽位仍在，放进去的背包不被当作背着的（与 `back` 现状一致），也不拦第二只；物品不删除、不移动。
- **绕过"能否放入"的途径**（命令、创造模式、发射器）：不拦，也不数数；多出的背包保持原样，第一只生效。
- **Accessories 的右键装备（036）**：浮动背包仍不被右键装备到任何槽位（`canEquipFromUse` 为否，不变）。
- **死亡掉落、跨维度**：沿用两个饰品模组自己的处理，不改。
- **Curios 没有给玩家 `charm` 槽的整合包**：没有 `charm` 槽就不能用，不强行添加（那是整合包的决定）。
- **与别的背包模型的重复渲染**：不处理；Curios、Accessories 各自"隐藏渲染"的开关继续只作用于它自己的槽位类型。

## Requirements *(mandatory)*

### Functional Requirements

**专用槽位（US1、US4）**

- **FR-001**: 系统 MUST 为 Curios 提供一个专用槽位类型 `floating_backpack`（1 格，有自己的图标与中英文名字），并把它分配给玩家；MUST 为 Accessories 提供同名的专用槽位（1 格，有自己的图标与中英文名字），并分配给玩家。
- **FR-002**: 专用槽位 MUST 只接受浮动背包（五个等级，靠各自模组的 item tag 实现）；别的物品、别的模组的背包 MUST NOT 能放进去。
- **FR-003**: 放进专用槽位的浮动背包 MUST 与放在 `back` 槽里的一样工作：被 `B` 键找到并打开、被背部渲染画出、遵守该槽位类型的"隐藏渲染"开关。
- **FR-004**: 专用槽位的注册 MUST 由一个**启动时读取**的布尔开关控制，**每个模组一项**（Curios、Accessories），默认开（`true`）；为 `false` 时该模组里 MUST NOT 出现这个槽位（槽位文件与实体分配文件都不生效）。开关 MUST 放在进程启动时就加载的配置里，使它在数据包加载前已可读；改动 MUST 需要重启，配置注释 MUST 写明这一点以及"关闭后放在专用槽里的背包由饰品模组处理，请先取下"的警告。开关 MUST 与 FULL/GENERIC/OFF 的档位相互独立（不读、不写档位配置）。
- **FR-005**: 系统 MUST 注册一个按开关判断的数据条件（`neoforge:conditions` 的自定义条件类型），专用槽位的槽位文件与实体分配文件都用它；这个条件只在安装了对应饰品模组时才被用到，不引入对它们的强依赖。

**通用 `charm` 槽（US3）**

- **FR-006**: 浮动背包 MUST 能放进 Curios 的 `charm` 槽与 Accessories 的 `charm` 槽（两个模组各加一个 tag 文件）；这条支持 MUST NOT 受专用槽位开关影响。
- **FR-007**: 系统 MUST NOT 在 Curios 里强行给玩家分配 `charm` 槽（整合包没有提供就没有）；Accessories 的玩家默认就有 `charm`，不需要额外分配。

**查找顺序与来源（US1、US3）**

- **FR-008**: `back`、专用、`charm` 三种槽位类型 MUST 各是一个独立的背着来源，在 `BackpackRef.Source` 末尾追加枚举（不改变已有序号），各自带固定顺序：胸甲 0，Curios 的 `back` 10、专用 11、`charm` 12，Accessories 的 `back` 20、专用 21、`charm` 22。"先找到的生效"的规则 MUST 不变。
- **FR-009**: 三种来源的"是否生效"MUST 都问**所属模组**的有效档位（只有 FULL 才生效），在客户端与服务端各自沿用 033/034/059 的做法；"隐藏渲染"MUST 按该来源的槽位类型读取；已有的 `back` 来源的行为 MUST 不变。
- **FR-010**: 只读真正的槽位，不读装饰（cosmetic）槽；槽位类型不存在、数据缺失、下标越界 MUST 一律当作空槽，不抛异常（沿用 033/034）。

**每类一只（US2）**

- **FR-011**: 该模组的有效档位为 FULL 时，向某个槽位类型（`back`、专用、`charm`）放入浮动背包 MUST 在该类型里已有另一只浮动背包（任意等级）时被拒绝（Curios 用其"能否装备"判断，Accessories 沿用现有的 `BackpackAccessory` 并加上"能否装备"）；放进同一格自己原有的那只、换位、取出 MUST 不受影响。
- **FR-012**: 有效档位不是 FULL 时 MUST NOT 做这个限制；绕过限制的途径（命令、创造模式等）MUST NOT 被拦，多出的背包 MUST 原样保留（第一只生效，其余不动、不删除）。
- **FR-013**: 限制 MUST 只数浮动背包，不数别的物品；跨槽位类型 MUST NOT 互相限制。

**档位与配置的关系**

- **FR-014**: 客户端档位 MUST 只影响客户端（是否绘制、是否识别背着）；服务端按自己的有效档位决定 `B` 键与限制；槽位本身 MUST 不随档位变化（由开关与饰品模组决定）。059 的行为在配置为空时 MUST 不变。

**文档与测试（US5）**

- **FR-015**: README.md 与 README_CN.md 的兼容说明 MUST 更新：三类位置（`back`、专用、`charm`）、每类一只、开关的位置/默认/重启要求/关闭后的警告、客户端档位在联机时只影响视觉；`docs/plan/third-party-compat.md` MUST 记下设计原则（档位管适配代码，注册内容走按模组的启动时配置）。
- **FR-016**: 系统 MUST 有自动测试：来源表的顺序与序号稳定；每类一只的判断（纯逻辑：已有一只则拒绝、同格换位不拒绝、非 FULL 不拦、绕过后第一只生效）；开关条件（默认真、读到 false 则假）；槽位文件、实体文件、tag 文件、语言键、图标齐全并彼此对得上；开发依赖与 `mods.toml` 范围不被改坏（沿用 058 的漂移测试）。
- **FR-017**: 本 feature MUST NOT 改变背包存储数据（`DataRevision` 保持 6）；MUST NOT 新增网络包（`BackpackRef` 里新来源的序号走已有的编码；是否需要改协议号由 plan 判断并说明）；MUST NOT 处理与别的背包模型的重复渲染；MUST NOT 做 `curio`、`accessories:any` 等别的槽位。

### Key Entities *(include if feature involves data)*

- **槽位类型**：饰品模组里的一类槽位（`back`、`floating_backpack`、`charm`），每个模组各自一套，每类至多放一只浮动背包。
- **背着来源**：一个模组的一个槽位类型对应的"背包放在哪里"，带固定顺序，决定 `B` 键与背部渲染取哪一只。
- **专用槽位开关**：每个饰品模组一个的启动时布尔值，决定是否注册专用槽位；与档位无关。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 装了 Curios（或 Accessories）、开关为默认时，浮动背包放进专用槽、`B` 键打开它、背部渲染画出它，**各在游戏里验证一次**；别的模组的背包放不进该槽。
- **SC-002**: 同一槽位类型里放第二只浮动背包 **0 次成功**（FULL 时）；不同类型各放一只 **100% 成功**；非 FULL 时不拦。
- **SC-003**: 开关设为 false 重启后，对应模组里**没有**专用槽位，另一个模组与 `back`、`charm` 不受影响；设回 true 后恢复。
- **SC-004**: `back` 槽里的背包、老存档、配置为默认时的全部现有自动测试 **100% 不变**。
- **SC-005**: 开关关闭时放在专用槽里的背包**不被删除**，结果（还给玩家或别的）在游戏里验证后记入文档。
- **SC-006**: `charm` 槽里的浮动背包在 Accessories 与（有 `charm` 槽的）Curios 里各验证一次。

## Assumptions

- Curios 与 Accessories 的槽位、实体分配加载器都支持 `neoforge:conditions`（已读字节码确认）；条件在数据包加载时求值，所以开关必须在启动时就可读（服务端配置晚于数据包，不能用）。
- 开关放在哪个配置里（预计是进程启动时就加载的 COMMON 类型配置，新建一个配置文件）、键名与自定义条件的细节由 plan 决定；本规格只要求"启动时可读、每个模组一项、默认开、与档位无关"。
- 槽位图标的资源路径（Curios 的 `slot/` 图集、Accessories 的 `gui/slot/`）能否用本模组命名空间下的贴图，由 plan 的第一步验证；若不行再用对方命名空间下的路径。
- 两个模组在槽位类型消失时对已有物品的处理（还给玩家）只有字节码证据，没有游戏内验证；SC-005 的游戏内验证决定文档措辞。
- `back` 的 tag 与现有的 `curios/entities` 文件保持不变（Curios 的 `back` 分配仍无条件）；这是 033 已确认的，本 feature 不改。
- 每个来源一个独立的 `WornSource`（而不是把槽位类型编进下标），避免背部渲染每帧遍历很多空下标；顺序数值见 FR-008，是默认值，不是接口承诺。
- 玩家同时背着多只（不同类型或不同模组）时，只有顺序里第一只被识别，这是现有规则的自然延伸，不做提示。
