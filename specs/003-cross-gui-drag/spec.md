# Feature Specification: 与原版物品栏/箱子互拖

**Feature Branch**: `003-cross-gui-drag`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "M3：浮动背包与原版玩家物品栏、原版箱子等标准容器之间互相拖动物品；统一光标；并补齐浮动格子悬停高亮。不含精妙/旅行者等第三方背包。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 物品栏打开时与浮动背包互拖 (Priority: P1)

玩家打开浮动面板后按 E。从物品栏格子拿起物品，再点到浮动背包空槽，物品进入浮动背包；反之从浮动背包拿到物品栏空槽。鼠标上的物品在两边操作中是同一份「拿在手上」的状态。

**Why this priority**: 这是 M3 最常用路径，也是当前可测性的关键缺口。

**Independent Test**: `runClient`，开面板 → E → 互拖至少一组物品往返。

**Acceptance Scenarios**:

1. **Given** 面板与物品栏都打开，**When** 从物品栏拿起物品再左键浮动空槽，**Then** 物品进入该浮动槽，光标清空。
2. **Given** 同上，**When** 从浮动槽拿起再点物品栏空槽，**Then** 物品进入物品栏，浮动槽空。
3. **Given** 光标持物，**When** 在浮动槽与物品栏槽之间交换不同物品，**Then** 两边结果符合交换预期。

---

### User Story 2 - 箱子等标准容器互拖 (Priority: P2)

玩家打开箱子（或潜影盒等使用标准容器界面的方块）时，浮动背包仍可见，并能与箱子格子用同一光标互拖。

**Why this priority**: 规格明确要求原版箱子；与物品栏同属 `AbstractContainerMenu` 路径。

**Independent Test**: 放一个箱子，开面板后开箱，完成往返拖动。

**Acceptance Scenarios**:

1. **Given** 面板与箱子界面打开，**When** 从箱子拿物放入浮动槽，**Then** 箱子减少、浮动槽增加。
2. **Given** 同上，**When** 从浮动槽放入箱子，**Then** 反向成立。

---

### User Story 3 - 悬停高亮与光标一致 (Priority: P3)

鼠标移到浮动背包格子上时有半透明高亮（类似原版）。打开容器时，浮动面板上显示的光标物品与容器菜单光标为同一份，不出现两套光标。

**Why this priority**: 可用性与防串扰；高亮便于瞄准测试。

**Independent Test**: 开面板移动鼠标见高亮；开 E 后从物品栏拿物，光标在浮动面板上方仍显示同一物品。

**Acceptance Scenarios**:

1. **Given** 面板可见，**When** 指针进入某浮动槽，**Then** 该槽出现半透明高亮；移出后消失。
2. **Given** 物品栏打开且光标持物，**When** 指针移到浮动面板上，**Then** 仍显示同一持物，无第二套独立光标残留。

---

### Edge Cases

- 面板隐藏时不能点浮动槽，也不拦截容器点击。
- 打开容器前若浮动面板光标仍有物品：应并入容器光标（若容器光标为空），避免物品卡在面板光标里。
- 不支持精妙背包/旅行者背包专项（M4）。
- Shift 在容器打开时走原版容器逻辑或面板↔菜单光标点击，不强制主手 Shift 路径。

## Requirements *(mandatory)*

- **FR-001**: 打开原版玩家物品栏时，MUST 能用同一光标在浮动背包与物品栏槽之间取放。
- **FR-002**: 打开原版箱子类标准容器时，MUST 同样支持与浮动背包互拖。
- **FR-003**: 容器打开期间，浮动面板 MUST 使用（或同步为）当前容器菜单的 carried，MUST NOT 长期维持第二套冲突光标。
- **FR-004**: 浮动槽悬停 MUST 显示类似原版的半透明高亮。
- **FR-005**: 所有移动 MUST 服务端权威校验。
- **FR-006**: MUST NOT 用 `setScreen` 替换来打开浮动背包。
- **FR-007**: 本功能 MUST NOT 要求第三方背包模组。

## Success Criteria *(mandatory)*

- **SC-001**: 一次会话内完成物品栏↔浮动背包往返拖动，耗时 &lt; 1 分钟。
- **SC-002**: 箱子↔浮动背包往返拖动成功，两边数量正确。
- **SC-003**: 悬停高亮在指针进入/离开槽时即时切换。
- **SC-004**: 20 次交叉点击后无复制刷物、无物品消失（相对服务端状态）。

## Assumptions

- 「标准容器」= 使用 `AbstractContainerScreen` / `AbstractContainerMenu` 的原版界面（物品栏、箱子、潜影盒等）。
- JEI/EMI 共存仅要求不崩溃；不专项适配。
- M2 的无容器时主手 Shift 路径保留。
