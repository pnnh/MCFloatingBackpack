# Data Model: 创造物品栏界面兼容

没有新增持久数据。下面是会话级的状态与纯规则。

## 1. 创造光标报告的判定 `CreativeCursorRules`（纯，无 Minecraft 类型）

```text
enum Verdict { ACCEPT, NOT_CREATIVE, OTHER_MENU, BAD_COUNT, DISABLED_ITEM }

check(boolean creative, boolean inventoryMenuOpen,
      boolean empty, int count, int maxStack, boolean itemEnabled) -> Verdict
```

顺序与含义见 research D5。`empty` 为真时直接 `ACCEPT`（空光标永远合法）；`count > maxStack` 或 `count < 0` 为 `BAD_COUNT`。

## 2. 光标记账 `CursorMirror<T>`（纯、泛型，两端各一个实例）

记录"对端已知的光标"，用来决定要不要发包，避免回声。

```text
CursorMirror<T>(BiPredicate<T,T> same, UnaryOperator<T> copy, T empty)
  T known()                       // 对端已知的光标（初始为 empty）
  boolean shouldSend(T current)   // !same(known, current)
  void sent(T current)            // known = copy(current)；本端发出后调用
  void received(T value)          // known = copy(value)；收到对端的包后调用（回写或报告）
  void reset()                    // known = empty；界面关闭、断线、模式变化时调用
```

- 客户端实例：`known` = 服务端已知的创造光标。输入事件里 `shouldSend(carried)` 为真才发报告；收到回写时 `received`。
- 服务端实例（每个创造玩家一个，存在 `CreativeCursorBridge` 的 `Map<UUID, CursorMirror<ItemStack>>` 里）：`known` = 客户端已知的光标。`pushIfChanged`：`shouldSend(menu.getCarried())` 为真才发回写；收到报告时 `received`。
- 判定"相同"：`ItemStack.matches`（数量与组件都相同，空等于空）。

## 3. 网络包（`contracts/creative-cursor.md`）

| 包 | 方向 | 内容 |
|---|---|---|
| `CreativeCursorReportPayload` | 客户端 → 服务端 | `ItemStack cursor`（可空） |
| `CreativeCursorPushPayload` | 服务端 → 客户端 | `ItemStack cursor`（可空） |
| `BackpackStackSyncPayload`（**条件，Phase B**） | 服务端 → 客户端 | `int inventorySlot`、`ItemStack stack` |

## 4. 生命周期与状态转移

```text
创造界面打开 + 面板可见        客户端 mirror.reset()
  输入事件 / 发请求前          current != known ? 发 Report; mirror.sent(current)
  服务端收到 Report            Verdict==ACCEPT ? inventoryMenu.setCarried(copy); mirror.received(copy) : 忽略
  服务端处理面板操作           ... menu 光标被现有代码改动 ...
  BackpackNetwork 发送辅助     menu.carried != known ? 发 Push; mirror.sent(menu.carried)
  客户端收到 Push              创造界面仍打开 ? inventoryMenu.setCarried(copy); mirror.received(copy)
关闭界面（E / ESC）            客户端 KeyPressed.Pre 先发最后一次 Report（早于关闭包）
                               服务端 doCloseContainer → removed(): 光标放回物品栏并清空
                               PlayerContainerEvent.Close → 服务端 mirror.reset()
                               客户端 Screen 关闭 → mirror.reset()
玩家退出 / 断线                服务端 mirror 丢弃；去向同生存模式（U7）
```

## 5. 与既有结构的关系

- 服务端：`BackpackClickPayload` 等 7 处读 `player.containerMenu.getCarried()`，镜像之后天然正确，**不改**。
- 客户端：`FloatingPanelRenderer.cursorStack` 已经读 `player.containerMenu.getCarried()`（创造界面时就是 `inventoryMenu` 的光标），**不改**。
- 026：`BackpackGate`、`BackpackRef.saveInventory` 不改。
