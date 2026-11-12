# Research: 液体罐

读本仓库源码与 NeoForge 21.1.251 源码（`neoforge-21.1.251-sources.jar`）得出；每条标明**已查证**或**未验证**。

## R1 液量放在哪（D1）

- **Decision**: 放在背包里：`BackpackContents` 新增可选字段 `fluids`；罐物品无数据组件、`stacksTo(1)`。
- **Rationale**: spec 已确认"取下罐子，液体留在背包里，装回去就回来"。如果液量放在罐物品上，取下罐等于带走液体，与已确认的玩法相反。无状态的罐也让锻造台升级（金→下界合金）、合成、创造物品栏克隆都不涉及数据搬运。
- **Alternatives**: 液量放罐的数据组件（原版容器的惯常做法，更简单，但违背已确认玩法）；背包记"每种液体一个罐"的完整记录（过度）。**已查证**：背包的其它可选数据（标签、垃圾桶、黑名单）都是同样的做法。

## R2 档次比较（D2）

- **已查证**：`BackpackTier` 五档（皮 36、铁 54、金 72、钻石 90、下界合金 108），`ordinal()` 就是先后；`BackpackTier.byCapacity` 可能返回 null；服务端 `BackpackInventory.baseCapacity()` 是背包档次的格数。
- **Decision**: 罐的档次类型直接用 `BackpackTier`，只取其中皮、铁、金、下界合金四个值；`allowed = tank.ordinal() <= backpack.ordinal()`；背包档次判不出（null）时按皮处理（只放皮罐，保守）。
- **Alternatives**: 单独的 `TankTier` 枚举（多一个类、多一套转换，没有收益）。

## R3 往工具区放东西的路径（D3）

- **已查证**（`grep setStackInSlot/isTool/toolStacks`）：
  - `BackpackClickPayload.handleServer` 里 `if (area.isTool())` 求出 `held`（面板光标、Alt 点击时的主手、菜单携带栈三种），依次过 `ExtensionPackRules.canPlace` 与 `ToolAreaRules.canPlace`，被拒时 `sendSlotUpdate` 回滚。**罐的检查接在这里**。
  - `BackpackQuickTransferPayload`、`BackpackCursorGesturePayload`、`BackpackTakeFromContainerPayload`、`BackpackTrashPayload`、`BackpackTrashSendPayload`、`AutoPickupEvents`、`BackpackMenuBridge` 的写入都是 `backpack.setStackInSlot(int, …)`，只写主区域。
  - 客户端预测：`FloatingPanelInput.refusedByLimit`（聊天提示）与 `ClientPlacement`（主区域的行）。
- **已知局限**：创造模式下物品栏的客户端写回会整只覆盖背包栈（原版创造模式由客户端授权），这是 `ExtensionPackRules` 等现有规则同样绕不过的；不在本 feature 处理，DELIVERY 里写明。

## R4 数据修订与协议（D4）

- **已查证**：`DataRevision.CURRENT` 现为 1；`docs/plan/release-process.md` 规定"新增存储字段、新增只能存在背包里的物品种类"要递增，并在 DELIVERY 里写明、更新对应测试、发布说明提醒降级先备份。本 feature 两条都满足 → **CURRENT = 2**。
- **已查证**：`BackpackContents.STREAM_CODEC` 是手写的、按顺序编码，组件 `networkSynchronized` 用它；在末尾追加 `fluids` 会改变所有带背包栈的同步格式 → 协议号必须升（18→19）。会话打开时额外发一个 S→C 包 `BackpackFluidsPayload`（与 `BackpackTagsPayload`、`sendPickupBlacklist` 同一模式：客户端面板不持有背包栈，需要自己的镜像）。
- **已知**：不带保护的旧版本载入玩家数据时会丢不认识的字段与嵌套物品（040 的降级实验），所以降级后液量与工具区里的罐会丢；口径不变：**不支持降级，降级前请备份**。
- 历史测试里断言协议号 18 的（`grep 'versioned("18")'`）、断言修订为 1 的，逐个改为 19 与 2。

## R5 快照与降级拦截（D5）

- **已查证**：`SnapshotOps.readContents` 用 `BackpackContents.CODEC.parse`；快照写入同一 `CODEC` → 新字段自动包含，无需改代码，只加往返测试。
- 041 的存档标记比较 `DataRevision.CURRENT`；升到 2 后，写过 1 的存档照常启动并把标记写成 2；写过 2 的存档不能被只认识 1 的版本打开（正是预期）。

## R6 牛奶与蜂蜜的流体（对应"尚未验证"第 2 条）

- **已查证**（NeoForge 21.1.251 源码 `NeoForgeMod`、`Tags`）：
  - 牛奶：NeoForge **有**牛奶流体（`neoforge:milk`、`neoforge:flowing_milk`，桶为原版牛奶桶），但**默认不启用**：只有某个模组在构造器里调用 `NeoForgeMod.enableMilkFluid()` 才注册。标签 `c:milk`（流体）存在。
  - 蜂蜜：原版与 NeoForge 都**没有**蜂蜜流体；标签 `c:honey`（流体）存在，源码注释写明"蜂蜜瓶标准是 250 mB"，与 spec 的"一次 250 mB"一致。
  - 原油：`Tags.Fluids` 里没有 `crude_oil`（`grep -i crude` 无结果）——`c:crude_oil` 只由模组定义。
- **Decision**: 048 的 `FluidKind` 是**本模组自己的液体种类标识**，不要求对应某个已注册流体：水、岩浆有原版流体，牛奶、蜂蜜没有（或默认没有）也能存；只有原油需要记"锁定的流体 id"（字符串 `ResourceLocation`），因为它可能是不同模组的不同原油。049、050 接入真实流体时再用 `c:water`、`c:lava`、`c:milk`、`c:honey`、`c:crude_oil` 流体标签做归类，048 不碰。
- **原油在开发环境怎么验证**：单元测试里用假 id 字符串（`modern_industrialization:crude_oil` 与 `pneumaticcraft:oil`）验证锁定与解锁；游戏里只验证罐子、放入规则、量条（用 `/item replace` 写入带原油锁定的液量）。**真实的原油模组联调不做、写"未验证"**，留给 049。

## R7 配方（D7）

- **已查证**：原版 1.21.1 里水桶、岩浆桶、牛奶桶的 `craftRemainder` 是空桶，蜂蜜瓶的是玻璃瓶（原版蛋糕的配方就靠水桶、牛奶桶退回空桶），所以有序合成里用它们当材料时**自动退回空容器**，不写额外规则。**未验证**：蜂蜜瓶作为有序合成材料时退回玻璃瓶——实现时在游戏里合成一次核对。
- 项目现有 23 个配方都**没有**对应的进度文件（`data/mcfloatingbackpack` 下只有 `curios`、`recipe`、`tags`），本 feature 沿用。
- 下界合金罐：`minecraft:smithing_transform`，`template = minecraft:netherite_upgrade_smithing_template`，`base` = 同液体金罐，`addition` = `minecraft:netherite_ingot`；罐无状态，无需自定义配方类（背包本身的升级需要自定义是因为要搬内容）。
- 原油罐的"空桶加煤炭块"：有序合成同时放两样，所以空桶在中间、煤炭块在下中（取代环形的一格材料），见 contracts。

## R8 占位贴图与图集预算（D8，宪法 VI）

- **已查证**：`design/demo/tools/check_atlas_budget.sh` 存在，是 010 规定的度量办法。物品贴图在 1.21 里进入共享的 blocks 图集，所以新增 19 张 16×16 要加前后各量一次。
- **未验证**：加 19 张后图集尺寸是否变化——预计不变（现有图集远小于 2048），**实现时必须实测并记录**，不得声称达标。
- 生成脚本放 `design/tank-textures/`（开发期资源，不进 jar）；生成出的 PNG 在 `src/main/resources`，**不 LFS**（宪法：随 jar 发布的文件不能是 LFS）。

## R9 面板显示（D6）

- **已查证**：`FloatingPanelRenderer.drawSlotsWithItems` 对每个区的格子先 `drawSlotWell` 再 `renderItem` + `renderItemDecorations`；`drawItemTooltip`（045）调用 `graphics.renderTooltip(font, stack, x, y)`。
- **Decision**: 量条在 `renderItemDecorations` 之后画（抬到 z 200，与原版耐久条同层），仅当 `area == BOTTOM` 且该罐是 `TankItem`；悬停提示改用 `Screen.getTooltipFromItem` 取原版行再追加一行（只对底部区的罐）。物品本身的静态提示（容量、可放入的最低背包档次）走 `Item.appendHoverText`，在任何界面里都显示。
- 量条几何：竖条，格内左缘 2 像素宽、14 像素高，深色底；填充高度 = `round(14 × 液量 / 容量)`，液量 > 0 时至少 1 像素；颜色取 `FluidKind.color()`。纯逻辑放 `TankBar`，单元测试断言像素。

## R10 带液量的背包怎么在游戏里造出来（D9）

- 组件的持久 Codec 同时是命令里组件语法的解析来源（`/give`、`/item replace` 的方括号写法），因此新字段可以直接用命令写进背包，无需调试命令或调试开关。**未验证**：`fluids` 的 SNBT 写法在 1.21.1 命令里能否被解析（实现时第一步验证，不行就加一个仅开发用的 `-Pmcfb` 开关）。
