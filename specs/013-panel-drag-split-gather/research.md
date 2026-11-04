# Research: 013-panel-drag-split-gather

"依据"一栏写的是读 NeoForge 21.1.251 源码（`build/moddev/artifacts/neoforge-21.1.251-sources.jar`）、读本仓库代码、或用 `javap` 检查精妙背包 / 旅行者背包 jar 得到的事实；没有核实的地方明确写"推断"。

## Decision 1: 面板里照搬原版"拖动分配"的结算时机——按下只开始，松开才结算

**Decision**: 光标上**有物品**时，在面板格子上按下鼠标（左或右键）不再立即放下，而是开始一次"拖动分配"；松开时才结算：滑过的有效格子 ≥ 2 个 → 按拖动规则分配；有效格子恰好 1 个 → 对那个格子做一次普通点击；没有有效格子 → 对松开位置的格子做一次普通点击（与现在的行为等价）。光标上**没有物品**时的按下仍然立即处理（拿起整组 / 右键拿起一半），与现在一致。

**Rationale**:
- 依据（`AbstractContainerScreen.mouseClicked`，源码 351–420 行）：光标为空时按下是立即 `PICKUP`；光标非空时进入 `isQuickCrafting`，不发包。依据（`mouseReleased`，519–600 行）：松开时 `isQuickCrafting && !quickCraftSlots.isEmpty()` 就发 `QUICK_CRAFT` 三段包；`quickCraftSlots` 为空时落到"光标非空 → 对松开位置做 `PICKUP`"。
- 依据（`AbstractContainerMenu.doClick`，314–376 行）：服务端在 `QUICK_CRAFT` 结束时如果 `quickcraftSlots.size() == 1`，就对那个槽位做一次 `PICKUP`（`doClick(i1, quickcraftType, ClickType.PICKUP, …)`）——这就是"只滑过一个格子等同普通点击"。
- 这是**行为变化**：面板里"拿着物品时放下"从按下时变为松开时（与原版一致）。风险与缓解见 Decision 9。

**Alternatives considered**: 按下时立即放下，拖动时再"撤销重分"——需要客户端预测与回滚，容易产生物品闪烁和服务端不一致，否决。

## Decision 2: 分配规则逐条照搬原版，包括"满堆叠也算有效格子"这个怪癖

**Decision**: 服务端与客户端预览共用同一份纯逻辑，规则与原版 `AbstractContainerMenu` 一致：
- 加入一个格子的条件（拖动过程中）：`canItemQuickReplace(slot, 光标, true)` 且该格接受该物品 且 `光标数量 > 已选格子数`。
- `canItemQuickReplace(slot, stack, true)`：格子为空 → 真；格子里是同种物品 → 真，只要 `格子数量 <= 最大堆叠`（正常情况恒真，**所以已满的同种堆叠也算有效格子**，只是放不进东西）。
- 结算时每格放 `placeCount`：左键 `floor(光标数量 ÷ 格子数)`，右键 `1`；放入后的数量 = `min(placeCount + 现有数量, min(最大堆叠, 该格上限))`；光标剩余 = 原数量 − Σ实际放入量；结算时对每个格子再检查一次条件，并要求 `光标数量 >= 格子数`（`quickcraftType == 2` 的中键不做）。
- 只有 1 个格子 → 退化为普通点击。

**Rationale**: 依据（源码 `AbstractContainerMenu` 314–376、728–745 行；`AbstractContainerScreen.mouseDragged` 495–504、`recalculateQuickCraftRemaining` 310–327 行）。照搬而不是"改进"，因为用户要的是"原版风格"，而且预览和服务端结果必须一致。满堆叠算有效格子这一点是原版的实际行为（预览里满格会以黄色显示上限），不是我们的推断，但和直觉不同，所以单列出来并在测试里钉住。

**精妙背包的做法**: 依据（`javap`）：它自己重新实现了"每格放几个"（`getQuickCraftPlaceCount(Slot, int, int, ItemStack)`，按槽位上限计算以支持超过 64 的堆叠）并检查槽位组合是否允许；面板里的格子是普通 64 上限，不需要这些，沿用原版公式即可。

## Decision 3: 拖动收集 = 拿起之后，滑过同种格子就收进光标

**Decision**: 光标为空时在有物品的面板格子上按下左键：照旧立即拿起整组；此后在左键保持按住期间，每滑到一个**新**的、与"刚拿起的物品"同种的面板格子，就向服务端请求把它收进光标：取 `min(该格数量, 最大堆叠 − 光标数量)`，格子里剩下的留在原格；光标已满、不同种、空格、已处理过的格子都忽略。松开左键即结束。

**Rationale**:
- 依据：这是用户描述的行为；原版没有（原版只有双击，见 Decision 4）。读了 `AbstractContainerScreen.mouseDragged`：光标非空且**不是** `isQuickCrafting` 时，拖动什么都不做——因为光标为空时按下已经 `PICKUP` 并设置了 `skipNextRelease`。所以这个手势在原版里是"空白"的，不会和原版行为冲突。
- 精妙背包、旅行者背包都没有这个手势（`javap` 看它们的 `mouseDragged`：精妙背包只处理 `isQuickCrafting` 的拖动分配，旅行者背包只处理界面控件）。
- "同种"的参照物是**按下时被拿起的物品**（客户端在发出拿起请求之前读取那个格子的内容），服务端用光标上实际的物品再核对一次，不信任客户端。

**与均分的冲突（已由用户裁决）**: 光标已经拿着物品时按住左键拖动一律是均分；收集只在"这一次按下刚刚拿起了一组物品"时触发（spec FR-017）。两者的起点条件互斥（按下时光标为空 vs 非空），不会出现同一个手势两种结果。

## Decision 4: 双击收集 = 照原版 `PICKUP_ALL`

**Decision**: 面板里左键在**同一个格子、同一个按键、250ms 内**第二次按下，并在松开时（光标非空且该格为空或不能被取出）触发"收集全部"：遍历面板所有格子，先收未满的同种堆叠，再收满的，每格取 `min(该格数量, 最大堆叠 − 光标数量)`，直到光标满。

**Rationale**: 依据（`AbstractContainerScreen.mouseClicked` 的 `doubleclick` 判定：`lastClickSlot == slot && 距上次点击 < 250ms && 同一按键`；`mouseReleased` 在 `doubleclick && slot != null && button == 0` 时发 `PICKUP_ALL`；`AbstractContainerMenu.doClick` 的 `PICKUP_ALL` 分支：条件 `!carried.isEmpty() && (!slot.hasItem() || !slot.mayPickup(player))`，两遍循环，第一遍跳过已满的堆叠）。第一次点击拿起整组，该格变空，所以第二次点击恰好满足条件。精妙背包与旅行者背包覆盖的 `canTakeItemForPickAll` 是"哪些槽位可以被收"的钩子，面板的格子没有这种限制。

**Shift+双击**: spec 已排除。

## Decision 5: 光标有两种来源，统一成一个读写接口

**Decision**: 服务端新增 `PanelCursor`（读、写光标栈），实现与现有点击完全一致：`screenOpen && 菜单存在` → 菜单的携带物（`menu.getCarried()/setCarried`）；否则 → 背包库存自己的光标（`BackpackInventory.getCursor()/setCursor`）。新的网络包沿用 `BackpackClickPayload` 的 `screenOpen` 标志。

**Rationale**: 依据（`BackpackClickPayload.handleServer`，约 99–115 行）：现有逻辑就是"`payload.screenOpen() && menu != null` 用 `BackpackMenuBridge.clickWithMenuCarried`，否则用 `BackpackClickLogic.applyTo`（面板自己的光标）"。依据（`FloatingPanelRenderer.cursorStack`）：客户端渲染光标时用同样的判断。保持一致，两种来源下行为就一致（spec FR-012）。
**已知限制**: `screenOpen` 是客户端自报的，服务端没有自己的"有没有打开界面"状态，这是现有设计的限制，不是本功能新增的；服务端在 `screenOpen` 为真时还要求菜单存在，不存在则回退到面板光标。

## Decision 6: 一个新的网络包，带"模式"，协议版本 "5" → "6"

**Decision**: 新增客户端→服务端的 `BackpackCursorGesturePayload(mode, slots[], screenOpen)`，`mode` ∈ {`DISTRIBUTE_LEFT`, `DISTRIBUTE_RIGHT`, `GATHER_DRAG`, `GATHER_ALL`}；`slots` 是面板格子下标：`DISTRIBUTE_*` 是按顺序滑过的有效格子；`GATHER_DRAG` 是本帧新滑过的格子；`GATHER_ALL` 只有被双击的那个格子。服务端校验：背包有效、`slots` 无重复且在 `[0, 背包大小)`、数量不超过背包大小、光标状态符合模式（分配和收集都要求光标非空）；通过后执行，之后 `sendFullSync`（含光标）；菜单模式下还要 `menu.broadcastChanges()`。

**Rationale**: 011/012 已经按"一个包带模式或方向"的做法做了；一个包也让校验集中。依据（`BackpackNetwork`）：新增包改变协议，必须升版本。`GATHER_DRAG` 按帧批量发送，沿用 011 的"每帧至多一个请求"。

**Alternatives considered**: 为每种手势单独一个包——校验和注册重复，否决。复用 `BackpackClickPayload` 加字段——会改变已验证的点击包格式，否决。

## Decision 7: 客户端的拖动状态机是纯逻辑，和游戏相连的部分单独成类

**Decision**: `PanelDragGesture`（纯逻辑）管理"模式、起点、已访问格子、已选有效格子、线段采样"；有效格子的判定通过注入的"读取格子内容 / 光标"接口完成，使用与服务端共用的 `PanelCursorLogic`；`PanelDragInput` 是与游戏相连的部分（命中测试、发包、每帧驱动、预览数据）。

**Rationale**: 与 011 的 `QuickTransferGesture`/`QuickTransferInput` 同一做法，状态机可单测；线段采样沿用 `SAMPLE_STEP = 4px`，快速拖动不漏格。每帧轮询松开与取消条件，**不依赖松开事件**（BUG-005-1 的教训）；按下事件被面板消费后用现有的 `ConsumedPressTracker` 保证松开也被一并吞。

## Decision 8: 预览只在客户端渲染，复用面板现有的槽位绘制

**Decision**: 拖动分配进行中，`FloatingPanelRenderer` 对已选格子画出"将放入后的数量"（超过上限时黄色上限，同原版），光标上显示"将剩余的数量"；预览数据来自 `PanelDragGesture`，不改变任何真实物品。拖动取消（松开前离开面板、切换界面、面板隐藏）时预览消失，没有任何改动。

**Rationale**: 依据（`AbstractContainerScreen.renderSlot`，243–275 行）：原版预览就是这样画的——被选槽位用 `copyWithCount(预计数量)` 渲染，超上限用黄字显示上限，不改槽位。只有 1 个格子时原版**不画预览**（`return`），照搬。

## Decision 9: 行为变化与回归风险的控制

- **变化**：拿着物品时在面板里"放下/交换"从按下变为松开时结算（与原版一致）。缓解：松开位置的格子即为点击位置；不移动时等价于现在的点击，只是晚了一个按键时长；quickstart 的回归项逐条覆盖单击放下、交换、右键放一个。
- **Ctrl/Shift 优先**：按下时 Ctrl 或 Shift 为真，一律走现有路径（Ctrl → 011/012，Shift → 现有 Shift 行为），不开始任何新手势（spec FR-011）。这里读取 Shift 是为了让路。
- **只作用于面板格子**：玩家物品栏、箱子、精妙背包、旅行者背包自己的拖动不改变（FR-018）；面板盖住的范围内按面板处理，沿用既有的命中规则。

## Decision 10: 被否决的方案

- **用原版 `QUICK_CRAFT` 包对面板格子做分配**：面板不是菜单，没有槽位下标可发。
- **客户端预测并立即改动画面，再等服务端纠正**：容易闪烁，服务端是权威（宪法 II）。
- **拖动收集也做在玩家物品栏/箱子里**：spec 已排除（菜单自己的行为不变）。

## 仍然不确定、需要实测的

- 拖动收集的手感：每帧一包在局域网高延迟下光标数量的更新是否可接受；是否需要客户端先行显示光标数量（目前不做预测）。
- 面板在"滚动"状态下（虚拟行重排、被滚出视野的格子）拖动是否正确命中：沿用 `PanelSlots.indexAt`，需实测。
- 双击与"点击后马上拖动"的区分：沿用原版 250ms 判定，需实测手感。
- 按下延后结算带来的体感变化：需要你在游戏里确认可以接受。
