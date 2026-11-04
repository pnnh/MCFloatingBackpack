# Delivery: 012-ctrl-transfer-to-open-container

**日期**: 2026-11-05

**状态**: 正向（面板 → 容器）实现完成，用户初步验证可用；**反向（容器 → 面板，US5）2026-11-05 补入 spec/plan/tasks，尚未实现**；验证未完成。以下是正向的记录：代码、单元测试、编译和启动都通过；玩家可见的行为（面板 Ctrl+点击/拖动存入打开着的容器）**没有在游戏里验证过**（宪法 V 条），在这些做完之前不得标记为完成。

## 已完成并实测（自动化）

| 项 | 结果 |
|---|---|
| `./gradlew compileJava test` | 退出码 0；16 个测试类全部通过（原 15 个 + 新增 `OpenContainerDeliveryTest`） |
| `OpenContainerDeliveryTest`（16 个用例） | 用假菜单（箱子式、工作台式、拒收式、抛异常式、永不收敛式）测真实的 `OpenContainerDelivery`：存入容器、先并入再占空格、容器满/部分放下时剩余留在背包且不进玩家槽位、拒收、优先用空槽位、物品栏满时换出主物品栏槽位并放回**同一个对象**、永不选手持的快捷栏槽位与 `mayPickup`/`mayPlace` 为假的槽位、没有可用槽位时不转移、工作台式在玩家区内部挪动后总数仍守恒、抛异常时不外泄且取回临时槽位内容、永不收敛时在上限处终止、选择结果确定、盔甲/副手/非玩家槽位不能当临时槽位、"有没有打开的容器"的判定、100 组随机布局下总数守恒 |
| 客户端启动 | 能启动到创建图集，日志无本功能相关报错（只有原有的联网取皮肤超时）；图集仍 2048×1024 |
| 客户端没有被改动（T010） | `git status` / `git diff` 对 `src/main/java/pnnh/mcfloatingbackpack/client` 无任何改动；协议版本仍为 `"4"`，网络包字段不变，011 客户端与 012 服务端互相兼容 |
| 没有读取 Shift、没有引用第三方类型（T012） | `grep -n "hasShiftDown\|KEY_LEFT_SHIFT\|KEY_RIGHT_SHIFT"` 与 `grep -n "net.p3pp3rf1y\|com.tiviacz"` 在 `OpenContainerDelivery.java`、`ContainerMenuAccess.java`、`BackpackQuickTransferPayload.java` 上**均无命中** |

## 与计划的偏差

- 测试和实现是**同时写的**，没有先看到测试失败；16 个用例一次通过，但"先红后绿"这一步没有做。
- 011 的 `SlotView` 字段在 plan 阶段叫 `isPlayerInventorySlot`，实现时改成 `belongsToPlayerInventory`（"容器是玩家 `Inventory`"，不含 0–35 的限制），因为盔甲和副手槽也属于玩家 `Inventory`，必须能和"可做临时槽位的槽位"区分开；data-model 已同步。

## 用户初步验证（2026-11-05）

用户反馈：按 Ctrl 能够实现从浮动背包到**精妙背包、旅行者背包和箱子**的快速转移；"物品栏满 + 手持模组背包"、拖动、熔炉/工作台等非存储界面、日志检查等**没有说明**，按未验证记。用户同时指出**反向（容器 → 浮动背包）不支持**，并要求补上：已作为 User Story 5 写入 spec（FR-017～FR-023、SC-008～SC-010）、plan、research（Decision 10～15）、contracts、quickstart 八与 tasks（Phase 8，T020～T033），**尚未实现**。注意：反向在 012 起草时是被明确列为 Out of Scope 的取舍（因为需要在客户端命中容器自己的槽位），不是遗漏。

## 反向（容器 → 浮动面板，US5）的实现与自动化结果（2026-11-05）

| 项 | 结果 |
|---|---|
| `./gradlew compileJava test` | 退出码 0；17 个测试类全部通过（再增 `ContainerToBackpackTest`，并更新了 `QuickTransferRulesTest`、`QuickTransferGestureTest`、`QuickTransferRequestTest`） |
| `ContainerToBackpackTest`（10 个用例） | 用假槽位测真实的 `ContainerToBackpack`：整堆取出放进背包、先并入再占空格、背包满时全部放回、部分放下时剩余放回、堆叠 1000 的槽位只转走背包放得下的 128、不合格的输出类槽位与空槽位被跳过且不被改动、槽位拒绝放回时回退（`set` → 玩家物品栏 → 丢弃）不丢物品、100 组随机布局总数守恒 |
| 客户端启动 | 协议版本 5、新包注册后能启动到创建图集，日志无新增报错；图集仍 2048×1024 |
| 没有引用第三方类型 | `grep "net.p3pp3rf1y\|com.tiviacz"` 在 `ContainerToBackpack`、`ContainerMenuAccess`、`BackpackTakeFromContainerPayload`、`QuickTransferInput`、`QuickTransferRules` 上无命中 |
| Shift | `QuickTransferInput` 里读取 `hasShiftDown()` 共 3 处，全部是"按着 Shift 就让路"（开始手势、拖动中、面板侧），没有任何一处用 Shift 触发行为 |

### 与计划的偏差

- 测试和实现仍是同时写的（没有先红后绿）。
- **来源槽位判据改用原版的 `slot.allowModification(player)`**（= `mayPickup && mayPlace(getItem())`，原版 `tryRemove` 就是靠它拒绝输出槽的部分取出），不再自造判据；客户端规则 `isContainerSourceSlot` 因此只有 4 个参数（没有 `hasItem`），空的容器槽位也能作为拖动的起点。
- 客户端的"面板侧"手势（Ctrl+点击面板槽位）也改为 Shift 按下时让路，与其他两侧一致（011 里面板侧不看 Shift）。
- `OpenContainerDelivery` 这一侧的 `ContainerMenuAccess` 增加了 `isExternalSlot`、`slotAccess`、`fallback` 三个方法。

### 反向尚未验证（需要你在游戏里做）

- **T030** quickstart 八（21–28）`runClient`：箱子取入、背包满时留在容器、熔炉/工作台/铁砧/交易输出槽不参与、拖动一行 9 格、起点方向、Shift 让路、面板隐藏。
- **T031** quickstart 八（29–32）`runClientCompat`：精妙背包（存储槽、大堆叠、被滚动隐藏的槽位、升级槽/锁定槽，以及**它自己的 Ctrl 用法**有没有被破坏）、旅行者背包（存储槽、工具槽、记忆槽）、两个界面里玩家物品栏槽位的 011 方向。
- 用户 2026-11-05 曾反馈"在这些容器里 Ctrl+左键都没有效果"，那是在反向**还没实现**时的观察（用户随后确认忘记了功能还没做），不构成对反向的验证结果。

## 用户第二次验证（2026-11-05，正向 + 反向）

用户反馈：**箱子、精妙背包、旅行者背包的双向 Ctrl+点击或拖动功能正常**（面板 → 容器、容器 → 面板，点击与拖动）；**没有验证**：背包满（浮动背包满或玩家物品栏满，用户没有区分）、熔炉、工作台等其他容器。用户只给了总体结论，**没有逐项数据**，所以只勾选明确被覆盖的子项：

- 已验证（`tasks.md` 里对应子项已勾选）：一.1、三.10（普通物品）、三.11（普通物品）、四.14（箱子里的拖动）、八.21、八.25、八.29 前半、八.31 前半。
- **未验证，保持不动**：容器满/背包满的部分转移（一.3、一.4、八.23、四.16）、熔炉/工作台/铁砧/酿造台/村民交易等其他容器（二.7～9、八.24）、物品栏满 + 手持模组背包（三.12）、大堆叠 >64（八.29 后半）、升级物品/工具槽/记忆槽/被滚动隐藏的槽位/精妙背包自己的 Ctrl 用法（八.30、三.10、三.11 的后半）、第三方界面里玩家物品栏槽位的 011 方向（八.32）、拖动中途关闭容器（四.15）、Ctrl+Shift 让路、面板隐藏时的行为、30 次守恒计数与日志检查、重跑 011 的 quickstart。
- 因为每个任务（T007、T008、T011、T030、T031）都至少有一个未验证的子项，**这五个任务都没有勾选**。

## 尚未验证（需要你在游戏里做）

- **T007** quickstart 一（1–6）与二（7–9）：箱子、木桶、末影箱、潜影盒、漏斗、发射器；物品栏满；容器满；熔炉、工作台、铁砧、酿造台、村民交易与 Shift+点击一致。
- **T008** quickstart 三（10–13）`runClientCompat`：精妙背包与旅行者背包；**重点是第 12 条"物品栏已满 + 手持模组背包"**（临时槽位换出再换回对两个模组是否安全，是对它们保护手持槽位的做法的推断，没有实测）。
- **T009** 条件任务：只有 T008 发现问题才需要。
- **T011** quickstart 四（14–16）：拖动。
- **T013** quickstart 五（17–20）：没有容器时的行为、Shift、重跑 011 的 quickstart。
- **T017** quickstart 六与七：30 次以上转移的总数守恒；搜日志里的守恒/异常记录。

## 无法验证（如实记录）

- **Windows/Linux 的 Ctrl 键**：本机是 macOS。
- **局域网高延迟下的拖动表现**：没有测。

## 已知限制

- **第三方菜单在 `quickMoveStack` 内部抛异常时无法回滚其内部状态**：只保证我们放进临时槽位的部分被取回；守恒检查只记日志（含菜单类名），不尝试回滚。
- **工作台、铁砧等菜单放不下时**，物品会留在玩家物品栏内部（这是原版 Shift+点击的行为），总数守恒但不在容器里。
- 物品栏满且没有任何满足 `mayPickup`/`mayPlace` 的主物品栏槽位时，本次不转移，物品留在背包。
- 反向（容器 → 浮动背包）2026-11-05 已补入并实现（US5），011 的 FR-016/FR-017 因此被修订；输出类槽位（`allowModification` 为假）不作为来源。
