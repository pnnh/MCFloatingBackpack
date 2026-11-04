# Research: 011-ctrl-quick-transfer

本文件把 plan 阶段需要弄清的问题变成有依据的决定。"依据"一栏写的是读 NeoForge 21.1.251 源码（`build/moddev/artifacts/neoforge-21.1.251-sources.jar`）、读本仓库代码、或检查第三方 jar 得到的事实；不是凭经验。没有核实的地方明确写"推断"。

## Decision 1: 请求里用**玩家物品栏下标 0–35**，不用菜单槽位编号

**Decision**: 客户端告诉服务端"玩家 `Inventory` 的第 N 格"（0–35），服务端直接读写 `player.getInventory()`，不依赖当前打开的是哪个菜单，也不传 `containerId`。

**Rationale**:
- 依据（`InventoryMenu` 源码）：同一个玩家物品栏在不同菜单里的**菜单槽位编号不同**（背包界面里主物品栏是 9–35、快捷栏 36–44；箱子界面里又是另一套），但这些槽位的 `slot.container` 都是玩家 `Inventory`，`slot.getContainerSlot()`（公开方法）都是 0–35。客户端只需把悬停的 `Slot` 换算成 `getContainerSlot()`。
- 服务端不再需要把菜单槽位编号映射回物品栏，也就不会被伪造的编号欺骗；越界（<0 或 >35）直接拒绝（FR-012）。
- 服务端改的是 `Inventory` 本身，打开的菜单会在 `ServerPlayer` 每 tick 的 `containerMenu.broadcastChanges()` 里把变化同步给客户端（已读 `ServerPlayer`：多处调用）；转移后再显式调一次，保证立即同步。
- 盔甲槽（容器槽位 36–39）、副手（40）、合成格/输出槽的 `container` 不是玩家 `Inventory`（合成格、输出槽）或编号 ≥36（盔甲、副手），客户端按 `getContainerSlot() < 36 && container == 玩家 Inventory` 过滤即自然排除（FR-011）。

**Alternatives considered**:
- 传 `containerId + 菜单槽位编号`，服务端在菜单里找槽位：能支持外部容器和第三方界面，但第一版不需要（已澄清只管玩家 36 格），而且要面对每个菜单的槽位规则。留给后续版本。
- 用 `menu.clicked(slot, PICKUP)` 先拿起再放：会碰到光标同步和模组自定义槽位规则，比直接改 `Inventory` 复杂得多。

## Decision 2: 客户端自己做槽位命中测试，不依赖 `hoveredSlot`

**Decision**: 在 `ScreenEvent.MouseButtonPressed.Pre` 里，用事件给的鼠标坐标，对 `screen.getMenu().slots` 做命中测试：`slot.isActive()` 且鼠标落在 `(getGuiLeft()+slot.x, getGuiTop()+slot.y)` 起的 16×16 内；只接受 `slot.container == 玩家 Inventory && getContainerSlot() < 36`。

**Rationale**:
- 依据：`AbstractContainerScreen.hoveredSlot` 只在**渲染时**更新（源码第 144–153 行附近），按下事件到来时可能是上一帧的值；拖动时鼠标一帧内可以掠过好几个槽位（每格 18 像素）。`getGuiLeft()` 是 NeoForge 加的公开方法（源码 752 行）；`Slot.x/y` 是公开字段；`isHovering` 是私有方法，所以自己写一个等价的 16×16 判定。
- 创造模式物品栏界面（`CreativeModeInventoryScreen`）的槽位是客户端自己的假槽位，直接排除（FR-011）；判定用 `screen instanceof CreativeModeInventoryScreen`。

**Alternatives considered**: 用 `screen.getSlotUnderMouse()`：最简单，但会漏掉快速拖动掠过的槽位，且是上一帧的值。

## Decision 3: 拖动用"每帧轮询 + 线段采样"，不依赖拖动事件

**Decision**: 按下 Ctrl+左键时，把当前槽位当作"Ctrl+点击"立即处理，同时进入"拖动中"状态；之后每一帧（`ScreenEvent.Render.Post` 与 HUD 的 `RenderGuiEvent.Post`，二者已存在）读当前鼠标位置，在"上一帧位置 → 当前位置"的线段上每 ≤4 像素采样一次做命中测试，把新碰到的槽位加入本帧待发列表。每帧用 `glfwGetMouseButton` 和 `Screen.hasControlDown()` 检查是否仍按着，松开就结束。

**Rationale**:
- 依据：没打开界面（只有 HUD 上的面板）时没有 `ScreenEvent.MouseDragged`，现有代码也是在 `onClientTick` 里轮询鼠标位置来做面板拖动（`FloatingPanelEvents` 的 `updateDrag` 路径）。统一用轮询，屏幕界面和 HUD 两条路径只有"槽位解析函数"不同。
- 线段采样解决"一帧内掠过多个槽位"；每个槽位在一次拖动里只处理一次（`visited` 集合，FR-003）。
- 松开判定放在轮询里，**不依赖是否收到松开事件**——BUG-005-1 的教训：被吞掉或丢失的松开事件会让状态卡住。轮询到"已松开"就收尾，不会残留。

**Alternatives considered**: 用 `ScreenEvent.MouseDragged.Pre`：事件频率高、坐标准，但 HUD 路径没有，要写两套；也无法发现"拖动中途 Ctrl 松开"这类无事件的变化。

## Decision 4: 消费按下事件，并用现有的 `ConsumedPressTracker` 保证松开也被一并消费

**Decision**: Ctrl+左键命中有效槽位时，取消按下事件（否则原版会执行 `PICKUP`，把整堆拿到光标上）；同时在 `ConsumedPressTracker` 里登记这个按下，让对应的松开也被吞掉，其他松开一律放行。

**Rationale**:
- 依据：`AbstractContainerScreen.mouseClicked` 在光标为空且**按着 Shift** 时发 `QUICK_MOVE`，否则发 `PICKUP`（源码 391–414 行）；它**只看 Shift，不看 Ctrl**。所以如果不取消，Ctrl+点击会被原版当成普通拿起。
- 本仓库的 `ConsumedPressTracker`（BUG-005-1 修复引入）正是"只吞对应被吞按下的松开"。这里沿用，避免再出一次"松开被吞导致键位卡住"的问题。
- 光标上已有物品时**不拦截**（FR-007），原样放行给现有的拖放逻辑。

## Decision 5: "Ctrl"用原版的 `Screen.hasControlDown()`

**Decision**: 判定 Ctrl 手势一律用 `Screen.hasControlDown()`，不直接读 GLFW 键码。

**Rationale**:
- 依据（`Screen` 源码 425–431 行）：macOS 上它检测 343/347（Command 键），其他平台检测 341/345（Ctrl 键）。依据（`MouseHandler.onPress` 59–66 行）：macOS 上 Control 键 + 左键会被重映射成右键，所以 macOS 上物理 Ctrl+左键根本到不了我们这里。
- 用原版同一个函数，保证和原版对"Ctrl 手势"的平台约定一致，也让本机（macOS）能验证（SC-006）。
- 本机无法验证 Windows/Linux；那是按同一函数推断的，登记为未验证。

## Decision 6: 搬运算法抽成泛型纯逻辑，测试跑真正的实现

**Decision**: 新增 `QuickTransferLogic`，对"栈"抽象成一个小接口（是否空、数量、堆叠上限、是否同种、拆分/设置数量），算法只依赖这个接口；生产代码传 `ItemStack` 适配器，单元测试传 `FakeStack` 适配器，测的是**同一份算法**。

**Rationale**:
- 依据：现有 `BackpackClickLogicTest` 是"用 `FakeStack` 重写一遍逻辑"来测，生产代码"镜像"这份行为（测试文件头注释写明）。这样测试通过并不能证明生产代码正确。原因是 `ItemStack` 需要注册表引导，测试里不方便用。泛型接口能绕开这点，又不重复实现。
- 算法（FR-006）：给定来源槽位的栈、目标槽位序列：先对每个目标槽位，若同种且未满 → 并入；再对每个空槽位 → 放入（不超过堆叠上限）；返回剩余。目标顺序固定为下标升序（玩家侧 0→35，即先快捷栏后主物品栏；背包侧 0→size-1）。
- 总数守恒是核心不变量，单元测试对它做性质检查。

**Alternatives considered**: 用 `Inventory.add()`：依据（`Inventory.add(int, ItemStack)` 源码 252–283 行）创造模式下 `hasInfiniteMaterials()` 会直接把剩余物品丢弃，行为与"放不下的留在原处"（FR-006）冲突；且它会优先选当前手持槽位。自己写更可控。

## Decision 7: 网络包批量，一包多槽位，版本号升到 "4"

**Decision**: 新增客户端→服务端的 `BackpackQuickTransferPayload`：`direction`（到背包 / 到物品栏）加一个槽位下标列表（每帧至多一包），并带上客户端认为的背包来源（`sourceOrdinal`、`invIndex`，与 `BackpackClickPayload` 一致）。服务端校验、去重、按顺序执行，然后调 `sendFullSync`。网络注册版本从 `"3"` 升到 `"4"`。

**Rationale**:
- 依据（`BackpackNetwork`）：现有包注册在 `versioned("3")` 下，新增包会改变协议，必须升版本，否则新旧版本互连会出现未知包问题。
- 批量：拖动一帧可能掠过数个槽位，每槽一包会多出不必要的包；一包多槽位也让服务端可以一次性算出结果再同步一次（背包最大 108 格，`sendFullSync` 一次就够，不必像单格点击那样每格一包）。
- 请求频率（FR-013）：每包槽位数设上限（到背包 ≤36，到物品栏 ≤背包容量 ≤108），超限或含越界/重复下标整包拒绝，不改动任何物品。客户端每帧至多发一包。

**Alternatives considered**: 每个槽位一包（实现最简单，但拖动时包数多、没有整体校验点）；全量"请求转移整个物品栏"（不是本 feature 的手势）。

## Decision 8: 一次拖动的方向由起点决定

**Decision**: 起点是玩家物品栏槽位 → 方向"到背包"；起点是面板槽位 → 方向"到物品栏"。拖动途中碰到另一侧的槽位一律忽略。

**Rationale**: 对应 spec FR-004。否则光标从物品栏滑到面板上，会把刚存入的物品又取出来。起点的方向在按下时就确定，状态机很简单。

## Decision 9: 当前使用的背包，在物品栏里的那一格要跳过

**Decision**: 服务端按"当前打开的背包来源"算出要保护的物品栏下标：主手 → `inventory.selected`；来源是物品栏 → 那个 `invIndex`；胸甲、副手不在 0–35 内。到背包的转移跳过该下标。

**Rationale**:
- 依据（`BackpackRef.resolve`）：来源可以是主手、副手、胸甲、物品栏 0–35。FR-010 要求"正在使用的那只背包不能被转进自己"。其他背包（包括物品栏里的第二只）按普通物品处理，和现有手动点击规则一致（见 spec 假设；我没有在 `BackpackClickLogic` 里找到"背包不能放进背包"的限制）。
- 同时：来源是主手时，`BackpackClickPayload` 的 Shift 路径已有类似保护（"不要把背包自己换进槽位"）。

## Decision 10: 不动 Shift；只在**原版界面**上命中；箱子等外部容器的格子不拦截

**Decision**:
- 事件处理里只在 `Screen.hasControlDown()` 为真时介入；Shift 判断一律不碰（FR-009）。
- 命中测试只认两类槽位：`container == 玩家 Inventory && getContainerSlot() < 36`；箱子等外部容器自己的 `Slot`（`container != 玩家 Inventory`）不命中，事件原样放行（FR-017）。
- **只在原版界面上生效**：界面类的全限定名以 `net.minecraft.` 开头才处理；第三方界面（精妙背包、旅行者背包、以及任何继承原版类的模组界面）一律不处理，事件原样放行（FR-016）。

**Rationale**:
- 依据（对 jar 的检查）：精妙背包 `StorageContainerMenuBase.addPlayerInventorySlots(Inventory, …)` 用玩家**真实** `Inventory` 创建槽位，旅行者背包的菜单也创建原版 `Slot`。所以只看"槽位的 `container` 是不是玩家 `Inventory`"**无法**把它们排除——它们的玩家槽位会被命中，Ctrl+点击会被我们抢走，违反 FR-016"第三方界面不得被改动"。必须在界面一层排除。
- 依据（`ContainerScreenCompat`）：现有代码只能识别精妙/旅行者两个已知模组的界面（按类名）；用"类名是否以 `net.minecraft.` 开头"做白名单更严格，也覆盖未来别的模组界面，符合"第一版只兼容原生"。
- 原版的 Shift 手势在 `mouseClicked` 里；我们的 `Pre` 事件只对 Ctrl 手势取消，Shift 不触碰，所以不会抢走原版的 Shift+点击或精妙背包的 Shift+拖动（`handleQuickMoveAll`，在它自己的界面类里）。

**范围**: 白名单只约束**物品栏一侧**的命中（Ctrl+点击界面里的槽位）。面板一侧不受限：目标是玩家真实 `Inventory`，不触碰任何菜单，所以第三方界面打开时也可以从面板转出（用户 2026-11-04 接受）。

**风险**: 白名单会把"继承原版界面类的模组界面"也排除，这是有意的保守选择；以后要支持某个模组界面，再单独加。

## Decision 11: 被否决的方案

- **用 Mixin 给每个菜单注入浮动背包槽位**，让原版 `QUICK_MOVE` 能找到它：会改变所有菜单的槽位编号，要逐个模组适配 `quickMoveStack`，客户端服务端必须同时装本模组；复杂度很高。
- **挂接服务端 `clicked(QUICK_MOVE)` 做"溢出到背包"**：没有现成事件，需要 Mixin；而且不是本 feature 要的手势。

## 仍然不确定、需要实测的

- 第三方界面（精妙背包、旅行者背包）下按 Ctrl+点击它们的槽位，**必须**保持原有行为（不被我们拦）：靠 Decision 10 的白名单保证，但这是推断，quickstart 里列为必测回归项。
- Windows/Linux 上 `Screen.hasControlDown()` 的实际行为：只能推断，未验证。
- 拖动时每帧一包在局域网高延迟下的表现：未测，quickstart 里列为手测项。
- 创造模式下 `Inventory` 的行为（玩家有无限材料时某些 API 会丢弃剩余）：我们自己写搬运，不用 `Inventory.add`，预期不受影响，需实测。
