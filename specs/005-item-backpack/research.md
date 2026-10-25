# Research: 005 背包道具化（A/C/E）

## 库存从玩家 Attachment 迁到物品实例

- **Decision**: 使用 Minecraft 1.21.1 **Data Component**（自定义 `DataComponentType`）把主仓 `ItemStack` 列表（及面板光标，若仍需要）挂在浮动背包 `ItemStack` 上；删除 `BackpackAttachments` 及所有 `player.getData(BACKPACK)` 路径，**不迁移**旧数据。
- **Rationale**: 与「背包是道具」一致；掉落/箱存/交易自然携带内容；符合宪法 II（仍由服务端改组件）。
- **Alternatives considered**: 自定义 Capability 挂 ItemStack——1.21 更推荐 component。继续 Attachment 再「指向 UUID」——多一层间接且易悬空。

## 打开目标解析（多背包取第一）

- **Decision**: 共享工具 `BackpackRef.resolve(player)`，顺序固定：**主手 → 副手 → 胸甲（EquipmentSlot.CHEST）→ 物品栏下标 0–8 → 9–35**。客户端与服务端同一算法；点击包携带 `BackpackRef`（枚举 source + slotIndex），服务端重解析并校验仍为浮动背包物品。
- **Rationale**: 评审要求「取第一个」；显式顺序可测、可文档化。
- **Alternatives considered**: 按物品自定义名/UUID 排序——本期无命名 UI。记住「上次打开」——延后。

## 右键使用 vs 方块交互

- **Decision**: `FloatingBackpackItem.use` / `useOn`：仅当未对可交互方块成功使用时切换面板（或 `use` 在未瞄准方块时）；潜行与否跟原版习惯——默认不强制潜行。
- **Rationale**: 规格 Assumptions；避免右键无法放方块。
- **Alternatives considered**: 永远打开面板——破坏挖掘/放置手感。

## 胸甲装备与背负渲染

- **Decision**: 物品声明可装备 `CHEST`（`ArmorItem` 或 1.21 `Equippable` 组件，防护可极低/零）。客户端用 `RenderPlayerEvent` / 装备层 / 简单 `HumanoidModel` 附加或物品 `BlockEntityWithoutLevelRenderer` 思路——优先 **玩家后背平移的简易模型**（立方体白盒 + 皮革色材质）。
- **Rationale**: 无 Curios 时的可见性；规格允许占位模。
- **Alternatives considered**: 仅改第一人称手臂模型——看不到背。Curios——明确 Out of Scope。

## 面板容量 36 与同步

- **Decision**: `BackpackInventory`（或改名的 contents helper）容量常量 **36**；`BackpackFullSync` / 客户端镜像改为长度 36；`PanelSlots` 布局 **4 行 × 9 列**。
- **Rationale**: 规格皮革档；Overlay 仍可读。
- **Alternatives considered**: 滚动视口——延后到更大等级。

## 面板光标存放位置

- **Decision**: 面板光标与主仓一并存在**当前打开的那只背包**的 Data Component 中（或仅会话级存在服务端 Map 以 ref 为键）。选定：**写入该物品 component**，避免换背包丢光标却占玩家 Attachment。
- **Rationale**: 去掉 Attachment 后需有归属；绑物品最简单。
- **Alternatives considered**: 纯服务端会话光标不落盘——掉线可能丢光标上的物品，差。

## JEI 仅开发运行时

- **Decision**: 在 `build.gradle` 用已有 `localRuntime` 引入 JEI（固定 1.21.1 NeoForge 坐标，可用 `-PjeiRuntime` 或默认开启开发 JEI）；`neoforge.mods.toml` **不** `required` JEI。发布 jar 的 pom/依赖图不声明 JEI。
- **Rationale**: 与 004 `compatRuntime` 模式一致；满足 E。
- **Alternatives considered**: 永远 runtime JEI——污染发布预期。手动创造栏 only——可作补充但仍要 JEI 提效。

## 基础合成

- **Decision**: 一条 shapeless/shaped 配方产出皮革浮动背包（如皮革 + 箱子 + 线）；创造 `CreativeModeTabs` 注册。
- **Rationale**: 无 JEI 也能验收；不算分级升级链。
- **Alternatives considered**: 仅创造获取——规格要求至少一条生存配方。

## 与 003/004 互拖

- **Decision**: 保留 `BackpackMenuBridge` / carried 合并逻辑，但读写改为「当前 BackpackRef 指向的物品 contents」。
- **Rationale**: 宪法与 SC-005；改绑而非重做 Overlay。
