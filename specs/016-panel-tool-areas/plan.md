# Implementation Plan: 浮动面板的三个工具区（左、右、下）与无滚动条的区域滚动

**Branch**: `016-panel-tool-areas` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-panel-tool-areas/spec.md`

## Summary

在主区域外侧增加左、右、下三个工具区（独立存储、各自滚动），去掉滚动条，整个面板仍是一个矩形；缩放只改变主区域的行列，三个工具区厚度恒为 1，面板最小 5×4。研究阶段（见 [research.md](./research.md)）读源码得出的方案：

1. **独立存储**：`BackpackContents` 新增 `ToolAreaContents(left, right, bottom)`（`optionalFieldOf`，旧存档读成空）；`BackpackInventory` 为三个区各持有一个 `ItemStackHandler`，**主格子完全不动**，所以 011/012/013/014 的服务端逻辑天然只面对主格子（FR-013）。
2. **动态容量**：纯函数 `ToolAreaCapacity.of(area, mainCapacity)`：左、右 = 等级默认行数，下 = 默认列数 + 2；列表长度 = `max(容量, 最后一个非空格子 + 1)`，容量变小不丢物品。
3. **点击寻址 `(PanelArea, 下标)`**：`BackpackClickPayload` 与 `BackpackSlotUpdatePayload` 各加一个 `area` 字节；服务端点击逻辑用 `getSlot/setSlot` 访问器泛化，放物品前过一个本阶段恒为真的 `ToolAreaRules.canPlace` 钩子（FR-011）。
4. **协议 `"6"` → `"7"`**：全量同步、会话、物品组件的编码都加 `ToolAreaContents`。
5. **纯类 `PanelLayout`** 统一几何与命中（`PanelSlots.indexAt` 保留签名、只返回主区域下标，所以 011/013/014 的输入类一行不用改）：面板宽 = `2·PAD_X + (列+2)·18`，高 = `PAD_TOP + (行+1)·18 + PAD_BOTTOM`；下区横跨左右区正下方；每个区域有自己的滚动偏移；左、右区可见格数 = 主区域可见行数，下区 = 可见列数 + 2；容量不足的位置画填充格。`PanelSlots` 变薄，滚动条代码整块删除。
6. **滚动与提示**：滚轮作用于悬停区域（下区横向）；有隐藏内容的一侧画小扁箭头（4 条 `fill` 叠成，不用贴图）。
7. **输入**：主区域流程不变；工具区只做立即点击与右键（含 Alt 主手交换），Shift/Ctrl 按下被吞掉，不清空主区域选区。
8. **四种底色**：主区域不变，左浅蓝、右浅绿、下浅琥珀。

范围：只做区域、存储、滚动与提示；扩展工具本身、放物品的限制、工具区里的 Shift/Ctrl/013 手势、跨区拖动均不做。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: NeoForge 21.1.251 / Minecraft 1.21.1（数据组件与编码、`ItemStackHandler`、`GuiGraphics`、`PacketDistributor`）；无新增依赖，不涉及第三方模组

**Storage**: 物品数据组件 `BACKPACK_CONTENTS`（`BackpackContents` 增加工具区列表）；客户端面板大小偏好仍在 `config/mcfloatingbackpack-panel.properties`（存主区域行列，不改格式）

**Testing**: JUnit 5 纯逻辑测试：`PanelLayout`、滚动夹取、`ToolAreaCapacity`/`fit`、`ToolAreaRules`；改写 4 个现有布局测试；加客户端会话手测（quickstart，宪法 V），HUD 与打开界面都要测

**Target Platform**: Minecraft 1.21.1 桌面客户端 + 服务端（双端模组）；开发机 macOS

**Project Type**: 单一 NeoForge 模组（存储 + 网络 + 客户端布局/输入/渲染）

**Performance Goals**: 面板每帧只多画 3 块区域（≤ 12+12+11 个格子）与至多 8 个箭头；点击仍是每次一个槽位更新

**Constraints**:
- 物品存放与点击由服务端校验并执行（宪法 II）；客户端只发 `(area, slot, 按键)`
- 主区域的所有现有操作与 015 完成时一致；011/012/013/014 只作用于主区域
- 不新增贴图/资源（宪法 VI 不适用）；协议版本升到 `"7"`
- 容量必须由等级默认尺寸推出（动态），与面板当前缩放无关

**Scale/Scope**: 约 7 个新类（`PanelArea`、`ToolAreaContents`、`ToolAreaCapacity`、`ToolAreaRules`、`PanelLayout`、`AxisScroll`、渲染里的箭头/底色常量）；改动 `BackpackContents`、`BackpackInventory`、`BackpackClickPayload`、`BackpackSlotUpdatePayload`、`BackpackFullSyncPayload`、`BackpackSessionPayload`、`BackpackNetwork`、`BackpackMenuBridge`、`BackpackUpgradeRecipe`、`ClientBackpackView`、`PanelSlots`、`FloatingPanelState`、`FloatingPanelInput`、`FloatingPanelEvents`、`FloatingPanelRenderer`；4 个现有测试改写 + 约 4 个新测试类；007/011/013/014 文档加说明

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|---|---|---|
| I. Overlay Not Screen | 仍是叠层；不打开或替换任何 `Screen`；整个面板仍是一个可拖动的矩形 | PASS |
| II. Server Owns Items | 工具区的物品由服务端存放、校验、保存；客户端只镜像并发送点击；放物品前有服务端规则钩子 | PASS |
| III. One Feature At A Time | 范围限定在三个工具区、滚动与提示、最小尺寸；扩展工具本身与放物品限制明确留给后续 feature；去掉滚动条与最小尺寸变化已写进 spec | PASS |
| IV. Vanilla Path Before Compat | 不引用精妙背包、旅行者背包的类型（只是参考它们的"升级组件"概念） | PASS |
| V. Verify In A Client Session | quickstart 逐场景要求客户端验证（布局、缩放、滚动与箭头、存放与保存、升级、旧存档、联机/重进）；Windows/Linux 无法验证，登记为未验证 | PASS（已规划，实现阶段必须执行） |
| VI. Shared Resources Have A Budget | 不新增贴图或模型，箭头与底色全是 `fill`；图集预算不变，仍在收尾时跑一次度量作为回归 | PASS（不适用） |

**Re-check after Phase 1 design**：设计阶段没有新增违反项。实现阶段要盯住的风险：
1. **布局重写的回归面**：`PanelSlots`、缩放、命中、选区/013 的命中函数、JEI 避让、`FloatingPanelConfig` 都依赖旧几何，必须重跑 005～015 的面板验证项；
2. **存储与同步**：旧存档能读、升级保留、全量同步/会话/物品组件三处编码一致、协议升级（research Decision 1/2/5）；
3. **滚动状态**：四份滚动偏移在缩放、容量变化、关闭面板时要夹取/复位，不能出现空白行列（FR-015/FR-016）。

## Project Structure

### Documentation (this feature)

```text
specs/016-panel-tool-areas/
├── plan.md              # 本文件
├── research.md          # Phase 0：13 条有依据的决定 + 仍不确定项
├── data-model.md        # Phase 1：PanelArea、ToolAreaContents、容量规则、PanelLayout、滚动状态
├── quickstart.md        # Phase 1：客户端验收步骤
├── contracts/
│   ├── panel-layout.md           # Phase 1：几何公式、命中、滚动、箭头、颜色、输入分派
│   └── storage-and-network.md    # Phase 1：数据组件、协议 7、点击寻址、服务端规则
├── checklists/requirements.md
└── tasks.md             # Phase 2：由 /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
src/main/java/pnnh/mcfloatingbackpack/
├── inventory/
│   ├── PanelArea.java                 # 新增：enum MAIN/LEFT/RIGHT/BOTTOM（线上一个字节），无 Minecraft 依赖
│   ├── ToolAreaCapacity.java          # 新增：纯函数，容量规则 + 不丢物品的 fit
│   ├── ToolAreaRules.java             # 新增：canPlace(area, stack) 钩子，本阶段恒为真
│   ├── ToolAreaContents.java          # 新增：三个列表的记录 + CODEC/STREAM_CODEC
│   ├── BackpackContents.java          # 修改：增加 tools 字段（optionalFieldOf）、fit 同时补齐工具区
│   ├── BackpackInventory.java         # 修改：三个工具区 handler、fromContents/toContents、访问器
│   ├── BackpackMenuBridge.java        # 修改：clickWithMenuCarried 增加访问器重载
│   └── recipe/BackpackUpgradeRecipe.java  # 修改：升级时同时 fit 工具区
├── network/
│   ├── BackpackClickPayload.java      # 修改：增加 area 字段；按区域选访问器
│   ├── BackpackSlotUpdatePayload.java # 修改：增加 area 字段
│   ├── BackpackFullSyncPayload.java   # 修改：增加 ToolAreaContents
│   ├── BackpackSessionPayload.java    # 修改：增加 ToolAreaContents
│   └── BackpackNetwork.java           # 修改：versioned "6" → "7"；sendSlotUpdate 带 area
└── client/
    ├── FloatingPanelEvents.java       # 修改：滚轮按区域分派；每 tick 夹取滚动
    └── panel/
        ├── PanelLayout.java           # 新增：纯类，几何/命中/滚动偏移/填充格/箭头可见性
        ├── AxisScroll.java            # 新增：纯类，单轴滚动的夹取与可滚动方向
        ├── PanelSlots.java            # 修改：保留常量与 panelWidth/Height，委托 PanelLayout，删除滚动条方法
        ├── FloatingPanelState.java    # 修改：四份滚动偏移、layout() 扣除左右列与下行、删除滚动条拖动
        ├── ClientBackpackView.java    # 修改：保存三个工具区的镜像与槽位更新
        ├── FloatingPanelInput.java    # 修改：按区域分派点击；删除滚动条命中；sendClick 带 area
        └── FloatingPanelRenderer.java # 修改：四种底色、填充格、箭头；删除滚动条绘制

src/test/java/pnnh/mcfloatingbackpack/
├── inventory/ToolAreaCapacityTest.java    # 容量规则、fit 补空/缩容保留、ToolAreaRules 钩子
└── client/panel/
    ├── PanelLayoutGeometryTest.java       # 尺寸公式、最小 5×4、矩形、命中、滚动偏移、填充格
    ├── AxisScrollTest.java                # 夹取、可滚动方向、总数变化
    ├── PanelLayoutTest.java / PanelSlotsResizeTest.java / PanelClampTest.java / FloatingPanelStateTitleIconTest.java  # 改写

specs/003、007、011、013、014 与 docs/MCFloatingBackpack.md   # 修改：加 016 说明
```

**Structure Decision**: 沿用单模块。几何、容量、滚动都是不依赖 Minecraft 的纯类，渲染与输入只读它们的结果；存储与网络按"主格子不动、工具区独立"扩展，最大限度减少对 011～014 的影响。

## Complexity Tracking

无违反项，不需要填写。
