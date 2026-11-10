# Contract: 扩展行的规则

纯逻辑，`inventory/extension/`。所有规则都有单元测试（假类型，见 [quickstart.md](../quickstart.md) C）。

## 1. 核对（reconcile）

输入：当前 `RowSet`、各类别"放着的数量" `placed`（三个功能区里的扩展件，每个按 1 个计，加面板光标上的 1 个）、上限 35。

```text
for category in ExtensionCategories.IDS:            # 固定顺序
    V = rows.visibleOf(category);  N = placed[category]
    while V > N:                                    # 拿走了
        if 该类别的可见行里有空行:  删除最后一个空行                       # 不产生隐藏行
        else:                       把该类别最后一个可见行移到 hidden 末尾  # 连同物品
        V -= 1
    while V < N:                                    # 放着了
        if hidden 里有该类别:       取最靠前的一行，追加到 visible 末尾      # "最早隐藏"
        elif rows.total() < 35:     新建空行追加到 visible 末尾
        else:                       refused += category; break              # 放不下，不产生行
        V += 1
未知类别的可见行（category 不在 IDS 里）：按 N = 0 处理：空的删、非空的移到 hidden
layoutChanged = 核对前后 visibleCategories() 不同
```

| # | 场景 | 结果 |
|---|---|---|
| R1 | 没有行，放着 1 个食物扩展 | 1 个可见空食物行 |
| R2 | 有食物行，再放着 1 个工具扩展 | 工具行排在食物行后面 |
| R3 | 食物行有物品，拿走食物扩展，没有空行 | 该行进 `hidden`，物品还在，`visible` 里没有它 |
| R4 | 两个食物行，第 1 行有物品、第 2 行是空的，拿走一个食物扩展 | 删第 2 行（空行），没有隐藏行 |
| R5 | 有 1 个隐藏食物行，再放着 1 个食物扩展 | 恢复它，追加到 `visible` 末尾，没有新建行 |
| R6 | 有 2 个隐藏食物行，放着 1 个 | 恢复 `hidden` 里排在前面的那一行 |
| R7 | 行总数 35，放着的扩展件会新增一行 | `refused` 含该类别，`rows` 不变 |
| R8 | 行总数 35，但 `hidden` 里有该类别 | 接回，不受上限限制 |
| R9 | 放着的扩展件全部消失（创造模式、别的模组） | 空行删除、非空行进 `hidden`；**物品总数不变** |
| R10 | 光标上拿着扩展件，工具区里没有 | 仍算放着（N 含光标），行不变 |
| R11 | 未知类别的非空可见行 | 进 `hidden`，不丢 |
| R12 | 任意放置/拿走序列（随机，固定种子） | 每一步之后所有格子里的物品总数不变 |

## 2. 放入

- `canPlace(slot, stack)`：`slot < baseCapacity` 恒为真；`baseCapacity ≤ slot < baseCapacity + 可见行 × 9`：看该格所在行，`matches(row.category, stack)`；其余（隐藏行、越界）为假。
- 不合格 = 拒绝、**零变化**（格子、光标都不动），服务端发 `sendSlotUpdate` 把客户端拉回。
- **取出永远允许**（点击拿起、Ctrl 转出、垃圾桶发送、聚拢、选区），不受类别影响。
- 类别判定只在放入时调用：事后标签变了，行里已有的物品不会被丢弃，仍可取出。

## 3. 类别判定（`CategoryMatcher.matches(category, stack)`）

```text
dataMatches(category, stack) || manualTagged(category, stack)
  dataMatches: stack ∈ 标签 mcfloatingbackpack:extension/<category>
               | category == food   && stack 有 FOOD 组件
               | category == potion && stack 有 POTION_CONTENTS 组件
  manualTagged: 这只背包的标签册里，stack 的物品注册 id 带有标签 <category>
```

- 两个来源是"或"；同时命中同一类别与只命中一个没有区别。
- 标签册是**每只背包各一份**：A 背包里打的标签不影响 B 背包。
- 一个物品匹配多个类别时（三叉戟 = 工具、武器；金苹果 = 食物）：由目标格顺序决定（见 4）。

## 4. 目标格顺序

`targetSegments(rows, baseCapacity, stack, matcher)` 返回两段下标：

1. **匹配行段**：该物品匹配的**可见**行，按行在 `visible` 里的顺序，每行 9 个下标；
2. **主段**：`0 .. baseCapacity-1`。

调用方对每段各调一次 `QuickTransferLogic.moveInto(…)`，剩余带到下一段 ⇒ 每一段里"先并进已有的没满的栈，再放空格"；匹配行都满了才进主段；主段也满了放不下的保持原状。**没有扩展行或没有匹配行时，第一段为空，整体与现状一致。**

使用方：快速转移（物品栏 → 面板、容器 → 面板）、自动拾取（`PickupInsert`）、垃圾桶取回、单格放入的"目标行"不需要（单格放入只检查 `canPlace`）。

多匹配的例子：有"工具行、武器行"（按这个顺序），放三叉戟：先看工具行里能不能并进或放进；工具行满了才看武器行；都满了进主段。

## 5. 聚拢、拖拽、选区、垃圾桶

- **双击聚拢**（原版 `PICKUP_ALL`）：范围是 `0 .. size()-1`（主区域 + 可见行），隐藏行不在其中；先没满的、再满的，规则不变。
- **拖拽分配**：不满足 `canPlace` 的格子不能加入拖拽。
- **选区、Ctrl 转移、倾倒垃圾**：对可见行与主区域一视同仁（取出允许；放回走第 4 条）。

## 6. 行总数上限与扩展件放入工具区

- 行总数 = `visible + hidden`，上限 35（`ExtensionCategories.MAX_ROWS`）。
- 往工具区放一个扩展件（`ToolAreaRules.canPlace` 之外再检查）：设 `placedAfter` = 现在放着的数量 + 这一个（如果这个扩展件目前已经在光标上算着，就不再加）；需要新建的行数 = Σ max(0, `placedAfter[c]` − (`visibleOf(c)` + `hiddenOf(c)`))；**不超过 `35 − total()` 才放行**，否则拒绝并提示。
- 扩展件**可以**放多个相同的（现有的"每区一个"只针对垃圾桶、标签管理器、自动拾取）。
- 一个扩展件的 `count > 1`：按 1 个计，记日志。

## 7. 核对的触发

- 保存（`BackpackInventory.toContents()`）与载入（`fromContents()`）各核对一次；结果与操作的先后无关。
- 保存后可见行结构变了（`layoutChanged`）⇒ `BackpackRef.saveInventory` 补发整份同步（主区域的格子编号变了）。
- 因 035 没有任何扩展件物品，`placed` 恒为 0：`/give` 或旧数据里的可见行在第一次载入时被删除（空）或隐藏（非空）。
