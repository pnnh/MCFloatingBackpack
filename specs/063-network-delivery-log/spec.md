# Feature Specification: 网络终端存入的误导 ERROR 日志

**Feature Branch**: `063-network-delivery-log`

**Created**: 2026-11-23

**Status**: Draft

**Input**: 用户（2026-11-23）从待办里选了两项：3. 集成终端背包重画（060）的残留问题，5. AE2 通用路径存入的误导 ERROR 日志。核对后（用户同意）**3 没有缺陷，关闭**：当初列的两项（IT 拖动分摊松手落在面板上；手里拿的数量超过一组只存一组）不是缺陷——前者只会让 IT 自己按拖过的格子分摊，物品不会丢；后者与 IT 自己往物品栏放的行为一致，且用户真机确认过。本 feature 只做 5。相关：012（向打开的容器投递，`OpenContainerDelivery`）、058（网络存储桥）、060 的 DELIVERY（记下了这条日志，"012 起就有、物品实际正确进入网络"）。

## 背景

现状（已核对源码）：

- 面板向打开的容器投递物品（Ctrl+点击、拖动），在没有专用桥的菜单上走通用路径 `OpenContainerDelivery.deliver`：把物品放进菜单里一个"停放格"，对它调菜单自己的 `quickMoveStack`，再把停放格里剩下的取回。
- 它为了防止"物品凭空多出或消失"，投递前后各数一遍**菜单所有格子里同种物品的总数**，再加上手里这一叠与剩下的那一叠，两边必须相等；不相等就由 `BackpackQuickTransferPayload` 写一条 **ERROR**（"Quick transfer into open container changed the item total"）。
- AE2 的 ME 终端、以及别的"物品存进网络、而不是留在菜单格子里"的终端上，`quickMoveStack` 把物品放进了网络——它们离开了菜单的所有格子，数出来的总数就变少了，于是每次存入都写一条 ERROR（060 里一次测试见到 5 条）。物品实际是对的（进了网络），这些 ERROR 是**误报**。
- 误报的害处：服主看到 ERROR 会以为模组在丢物品；真正的丢失/复制出现时，这类 ERROR 又被淹没。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 往 ME 终端存东西不再写 ERROR (Priority: P1)

玩家在 AE2 的 ME 终端界面上，Ctrl+点击把面板里的物品存进网络。物品进了网络，服务器日志里**没有 ERROR**（至多一条 DEBUG，说明这次存入是"放到了菜单格子之外"）。

**Why this priority**: 这是这个 feature 要修的东西。

**Independent Test**: 容器里（060 的 AE2 环境）在 ME 终端上存入一叠物品：背包里少一叠、网络里多一叠、日志里没有 "changed the item total" 的 ERROR。

**Acceptance Scenarios**:

1. **Given** 打开的是网络终端（物品进网络、不留在菜单格子里），**When** 存入一叠物品，**Then** 没有 ERROR；DEBUG 日志里有一行说明（菜单、物品、存入的数量）。
2. **Given** 同上但网络只收下一部分，**Then** 剩下的仍回到背包，没有 ERROR。
3. **Given** 一个普通容器（箱子）存入，**Then** 与之前完全一样（没有 ERROR，也没有那条 DEBUG）。

---

### User Story 2 - 真的出了问题仍然报 ERROR (Priority: P1)

"物品凭空多出"（投递后菜单里的总数比取走的还多）或"丢了比取走的更多"仍然写 ERROR；这个检查不能因为修误报而失效。

**Why this priority**: 这条检查是防复制/丢物品的最后防线，不能为了日志好看把它弱化到失效。

**Independent Test**: 单元测试用假菜单构造四种情形：总数不变、比取走的少（吸收到菜单之外）、比取走的多（复制）、少得比取走的还多（丢了别的东西）；前两种不报，后两种报。

**Acceptance Scenarios**:

1. **Given** 投递后菜单里同种物品的总数**增加**了（不论取走多少），**Then** 仍是 ERROR（可能是复制）。
2. **Given** 总数**减少**了，且减少的数量**超过**取走的数量（别的物品也没了），**Then** 仍是 ERROR。
3. **Given** 总数不变，**Then** 照旧无任何日志。

---

### Edge Cases

- **取走 0 个**（菜单什么也没收）：总数必须不变，否则 ERROR；与之前一样。
- **投递抛异常**：沿用现有的"threw"错误日志，不受本 feature 影响。
- **一次存入多叠**：每叠各自判断、各自（最多）一条 DEBUG。
- **日志量**：DEBUG 每叠一条，不是每帧；ERROR 的数量只会变少。
- **别的调用方**：`conserved` 只有 `BackpackQuickTransferPayload` 一处在用，别处不受影响。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `OpenContainerDelivery.deliver` 的结果 MUST 区分三种情形：**守恒**（投递前后总数不变）、**被吸收**（总数减少，且减少量不超过这次取走的数量，即物品进了菜单格子看不到的地方：ME 网络、别的存储）、**不一致**（总数增加，或减少量超过取走的数量）。取走的数量 = 投递的数量 − 剩下的数量。
- **FR-002**: 调用方 MUST 对"不一致"写 ERROR（措辞、内容与现在一致），对"被吸收"只写一条 DEBUG（菜单类、物品、存入数量、被吸收数量），对"守恒"什么都不写。
- **FR-003**: "守恒"的判定 MUST 与现在完全一样；对箱子等普通容器的行为 MUST 不变（没有新日志）。
- **FR-004**: MUST NOT 新增网络包、改协议号、改背包存储数据（`DataRevision` 仍 6）；MUST NOT 对任何第三方类型有引用（这是通用路径）。
- **FR-005**: 系统 MUST 有自动测试：四种情形的分类（总数不变、被吸收、复制、丢了别的）、取走 0 个、部分吸收部分回到背包、既有的 `OpenContainerDeliveryTest` 全部仍通过；接线测试：`BackpackQuickTransferPayload` 里的 ERROR 只在"不一致"时、DEBUG 在"被吸收"时。**游戏内验证**：AE2 的 ME 终端上存入，日志没有 ERROR。
- **FR-006**: 文档：060 的 DELIVERY 里"AE2 通用路径存入的……ERROR 仍在"一条 MUST 改成"已由 063 处理"；060 的"已知局限"里关于待办 3 的两项 MUST 补一句核对结论（不是缺陷）。

### Key Entities *(include if feature involves data)*

- **投递结果**：已有的 `Result`，新增"被吸收的数量"（0 表示没有被吸收）；"守恒"与"被吸收"都算物品没有多出也没有丢失。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在 ME 终端上存入物品，服务器日志里 "changed the item total" 的 ERROR **0 条**（之前每次存入约 1 条）。
- **SC-002**: 四种分类情形各有单元测试；复制与"丢了别的"两种仍然是 ERROR。
- **SC-003**: 箱子等普通容器上的存入，日志与之前**完全一致**。

## Assumptions

- ME 终端的存入走的是通用路径（`Ae2Bridge` 的 `handles` 恒为假，存入留给通用路径，060 已定）；本 feature 不改它的存入方式，只改判定与日志。
- "被吸收"的判定足够安全：一个菜单若把取走的物品"吃掉"又不放进任何格子，与"进了网络"在这里无法区分，这是通用路径固有的；检查的是更危险的两种（增加、比取走的还多地减少）。
- 060 的 DELIVERY 里提到的"5 条 ERROR"是这类误报的数量，不是每次存入固定 5 条。
- 待办 3 关闭，不改任何代码（用户已同意）。
