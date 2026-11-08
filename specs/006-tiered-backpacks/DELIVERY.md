# DELIVERY: 006-tiered-backpacks

**Date**: 2026-10-25  
**Status**: Code complete. `./gradlew compileJava test` **BUILD SUCCESSFUL**. Manual `runClient` (quickstart) still pending.

## Delivered

| Area | Notes |
| --- | --- |
| Tiers | Leather 36 / Iron 54 / Gold 72 / Diamond 90 / Netherite 108 |
| Upgrade | Smithing `backpack_upgrade`: empty template + iron/gold block / diamond / netherite ingot |
| Panel | Rows follow synced capacity; wheel scroll when taller than screen |
| Visuals | Shared element model + five 32×32 item textures; chest equip via `Equipable` (no ArmorItem leather plate); back layer uses tier texture. **[009 revision: delivered state at the time; visuals now replaced by sculpted-mesh models, see specs/009]** |
| Network | Payload version `3`, lists up to 108. **[011 revision]** protocol version is now `4` (added the quick-transfer payload), and `5` after 012 added the take-from-container payload |

## Verify

```bash
./gradlew test compileJava
./gradlew runClient
```

Follow [quickstart.md](quickstart.md) for in-game checks.

## 人工验证（2026-11-04，runClient）

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选（2026-11-04）。清单只记录了通过与否，**没有记录每项的细节数据**；没勾的项都是**未验证**，不是通过。

| quickstart 部分 | 结果 |
| --- | --- |
| 五档格子（行数 4/6/8/10/12，每行 9 格）、无背包提示、ESC/B 关闭 | 通过 |
| 小窗口（下界合金 12 行，滚轮滚到首尾、每格可点） | 通过 |
| 升级保留物品（四条升级路径）、模板槽放东西/不配套无结果、没有降级配方 | **未验证** |
| 外观（图标五色可分、手持是包身、F5 背部是对应配色的背包） | 通过（2026-11-04 用户确认五档图标/手持/背上渲染没有问题；肩带部分已由 009 取代） |
| 回归：金背包丢出再捡内容仍在；主手皮革 + 背包格下界合金时按 B 打开皮革（36 格） | 通过 |
| SC-003 五名观察者区分五档 | **未做** |

T027 仍未完成：缺升级路径的验证和五人观察者测试。

## 2026-11-06：用户人工验证（清单 A–D 组）

来源：用户在 `docs/plan/manual-verification-checklist.md` 里逐项勾选并备注；清单只记录通过与否和少量备注，没有逐项数据。没勾的项仍是**未验证**。

| 项 | 结果 |
| --- | --- |
| D3 升级保留物品（皮革→铁→金→钻石→下界合金四条路径，模板槽留空） | 通过 |
| D4 模板槽放东西或基底与材料不配套无结果；没有降级配方 | 通过 |

T027 现在**只剩五名观察者区分五档（SC-003/SC-005）**，保持未勾选。

## 2026-11-07：JEI 里看不到升级配方（已修复，用户已确认）

用户发现 JEI 里只能看到皮革背包的配方，看不到铁/金/钻石/下界合金的升级配方，但在锻造台里升级功能正常。

- 原因：JEI 的锻造台分类**支持**锻造配方，但只显示"已注册扩展"的配方类（原版只注册了 `SmithingTransformRecipe` 和 `SmithingTrimRecipe`）。本模组的升级配方是自己的类 `BackpackUpgradeRecipe`，JEI 判定"不处理"，所以不显示。与 JEI 版本（19.70）无关，升级本身不受影响。
- 修复：在已有的 JEI 插件（008）里实现 `registerVanillaCategoryExtensions`，给 `BackpackUpgradeRecipe` 注册锻造分类扩展（基础物品、材料、结果；模板格留空，与配方"模板为空"一致）。
- 验证：`./gradlew compileJava test` 通过。**用户 2026-11-07 在游戏里确认**（清单 006-JEI 与 023-10/11/12）：JEI 里铁、金、钻石、下界合金各有锻造台配方，023 之后模板格显示对应模板。
