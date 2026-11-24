# Data Model: 联合背包 · 单背包标签

没有持久化数据：联合背包物品**无数据组件**，`DataRevision` 仍 6，背包存储不动。下面是运行时类型。纯逻辑的类不引用 Minecraft 类型（泛型），以便单元测试。

## 物品

`CombinedBackpackItem extends Item implements Equipable`：构造参数 `BackpackTier tier`；`getEquipmentSlot()` = `CHEST`；`canFitInsideContainerItems()` = false；`static boolean isCombined(ItemStack)`、`static BackpackTier tierOf(ItemStack)`（非联合背包返回 null）。注册：`ModItems.COMBINED_BACKPACKS: Map<BackpackTier, DeferredHolder<Item, CombinedBackpackItem>>`，id `<tier.name().toLowerCase()>_combined_backpack`，`stacksTo(1)`，进入创造栏标签。

## 成员（纯，泛型引用）

```text
Member<R>(R ref, Optional<UUID> identity, int tierOrdinal, String customName /* "" = 无 */, Place place)
Place = (kind: WORN | OFF_HAND | INVENTORY, sourceId, slot)      // 位置说明由它在客户端翻译
```

`ref` 在游戏里是 `BackpackRef`（`Source` ∈ 穿戴来源、`OFF_HAND`、`INVENTORY`；**永远不是 `MAIN_HAND`**，见 R4）。

## 成员表（纯）

`CombinedMembers.compute(Candidates<R> candidates, int combinedTierOrdinal, int cap)`：

- 输入 `Candidates`：按顺序给出 `List<Candidate<R>>`（穿戴来源所有背包 → 副手 → 物品栏 0..35），每项含 `ref, identity?, tierOrdinal, protectedNewer, customName`。
- 规则（按序，每个候选只落入一类）：`protectedNewer` → 计入 `skippedNewer`；`tierOrdinal > combinedTierOrdinal` → 计入 `skippedTier`；已有 `cap`(=16) 项 → 计入 `skippedOverCap`；否则进 `members`。
- 输出 `Result<R>(List<Member<R>> members, int skippedTier, int skippedNewer, int skippedOverCap)`。同一身份可出现多次，不去重（去重只按引用：同一 `ref` 不会重复，因为候选来源互斥）。
- 签名 `signature()`：`(ref, identity, tier, customName)` 列表 + 三个计数的哈希；用于"变了才推"。

## 联合背包选择（纯）

`CombinedWorn.pick(List<(BackpackTier tier, int order)> worn)` → 档位最高者；同档位取 `order` 小者；空则 null。

## 服务端会话（内存，每玩家）

`CombinedSession(BackpackRef target, UUID targetIdentity, int combinedTierOrdinal, int lastSignature)`；`CombinedSessions`（静态，`Map<UUID, CombinedSession>`）：`open/select/end/forget(UUID)`、`current(player)`。清除时机：`BackpackGate.noteClosed`（面板关）、下线、`ServerStoppingEvent`、联合背包不再穿戴/`enabled=false` 的 1 秒检查。

`PanelTargets.resolve(player)` 的判定（纯逻辑 `PanelTargetRules.decide(...)` + 游戏适配）：

| 条件 | 结果 |
|---|---|
| 无联合会话 | `BackpackRef.resolve(player)` |
| 有会话且目标栈仍是浮动背包、身份 = 会话身份、档位 ≤ 联合档位、不在保护中、联合背包仍穿着且启用 | 会话目标 |
| 有会话但上面任一条不满足 | `BackpackRef.resolve(player)`（随后既有的过期保护 `acquireForPanel` 会拒绝并触发 `resync`，`resync` 先问 `CombinedSessions.recover`） |

注意：**"目标在前 16 位"只在选标签时与每秒检查里强制**，请求内校验是 O(1)（FR-015）。

`CombinedSessions.recover(player)`：重算成员表；会话身份在表里**唯一**匹配 → 改目标；否则取第一个成员；表为空 → 结束会话（面板按单背包规则回到 `resolve`，或关闭）。

## 网络载荷

见 [contracts/protocol.md](./contracts/protocol.md)：`CombinedSelectPayload`（C→S）、`CombinedMembersPayload`（S→C）。

## 客户端状态

`ClientCombinedState`（单例）：`boolean active`、`List<MemberView> members`、`int selected`（成员下标，-1 无）、`int skippedTier/skippedNewer/skippedOverCap`、`int combinedTier`、`boolean loading`、`int tabScroll`（标签栏横向滚动，像素）。`MemberView(ref, identity?, tier, customName)`。`ClientBackpackView` 单例**不动**。

纯逻辑 `CombinedBarLayout`：输入（面板 `posX/posY/width`、成员数、各标签宽、`tabScroll`、计数提示文字宽度）→ 输出：标题栏矩形、标签栏矩形、每个标签的矩形（已按滚动裁剪）、左右滚动箭头矩形、`hitTab(x,y)`、`hitArrow(x,y)`、`maxScroll()`。

## 配置

`CombinedConfig`（服务端配置 `[combined]`）：`enabled`（布尔，默认 true）。成员上限 16 是常量 `CombinedMembers.MAX_MEMBERS`。

## 语言键

`item.mcfloatingbackpack.<tier>_combined_backpack`（5）；`key.mcfloatingbackpack.combined`（U）；`message.mcfloatingbackpack.combined.need_worn` / `.disabled` / `.empty_cursor` / `.no_members`；`gui.mcfloatingbackpack.combined.title` / `.skipped_tier` / `.skipped_newer` / `.skipped_cap` / `.place.chest` / `.place.curios` / `.place.accessories` / `.place.offhand` / `.place.inventory` / `.capacity`；`gui.mcfloatingbackpack.tools_active_only`（"唱片机/工作台/熔炉只对按 B 打开的背包生效"）。中英文各一份。
