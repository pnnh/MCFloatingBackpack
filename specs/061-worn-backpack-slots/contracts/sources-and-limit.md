# 契约：背着来源的参数化与"每类一只"

对应 FR-008～FR-014。

## `BackpackRef.Source`

末尾追加（序号不变）：`CURIOS_DEDICATED`（6，worn）、`CURIOS_CHARM`（7）、`ACCESSORIES_DEDICATED`（8）、`ACCESSORIES_CHARM`（9），都 `worn = true`。测试断言：`values()` 的前 6 项与现在一致、新 4 项的顺序。

## `WornSource` 的实现

`CuriosWornSource(String slot, BackpackRef.Source source, int order)`，`AccessoriesWornSource` 同形。现有两个类里写死的 `SLOT = "back"`、`Source.CURIOS`/`ACCESSORIES`、顺序 10/20 变成构造函数参数；其余方法（`slotCount`、`get`、`set`、`hiddenRender`、两个 `active`）按 `slot` 读，行为不变。

`CuriosCompat.register()` 注册三个：`("back", CURIOS, 10)`、`("floating_backpack", CURIOS_DEDICATED, 11)`、`("charm", CURIOS_CHARM, 12)`；`AccessoriesCompat.register()` 同理（20、21、22）。**不论开关**都注册三个来源（槽位不存在时 `slotCount` 为 0，什么都不找）；开关只管数据文件。`active(entity)` 的两侧语义不变（059）。

## 每类一只

纯逻辑 `inventory/worn/SlotLimit`：

```java
static boolean allows(int othersInType, boolean enforced)   // !enforced || othersInType == 0
```

### Curios：`BackpackCurioItem implements ICurioItem`

```java
@Override public boolean canEquip(SlotContext context, ItemStack stack)
    // enforced = ServerCompatLevels / CompatConfig 的 FULL（与 WornSource.active(entity) 同一个判断，按 context.entity() 所在侧）
    // othersInType = 该实体 context.identifier() 类型里，下标 != context.index() 的真实格子里的浮动背包数（读 CuriosApi 的 stacks handler）
    // 装饰格（context.cosmetic()）直接放行
    // 任何异常 → 放行
```

注册：`CuriosCompat.registerCurios()` 在通用 setup 里（物品已存在）对五个等级各 `CuriosApi.registerCurio(item, new BackpackCurioItem())`；`canEquipFromUse` 不覆盖（默认假），所以不会引入右键装备。

### Accessories：`BackpackAccessory`

增加 `canEquip(ItemStack stack, SlotReference reference)`：同样的计数（`reference.slotContainer().getAccessories()` 的格子，下标 != `reference.slot()`，是浮动背包的个数），非 FULL 放行，异常放行。`canEquipFromUse` 保持为否（036）。

## 不变量

- 限制只在**放入**时判断；不在 tick 里清理，不删除、不移动任何已有物品。
- 不限制取出、换位、跨槽位类型。
- 不数装饰格；不数别的物品。
- `ServerCompatLevels`、`CompatConfig` 的读取不新增任何配置项。
