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
| Network | Payload version `3`, lists up to 108. **[011 revision]** protocol version is now `4` (added the quick-transfer payload) |

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
