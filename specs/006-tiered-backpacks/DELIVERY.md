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
| Network | Payload version `3`, lists up to 108 |

## Verify

```bash
./gradlew test compileJava
./gradlew runClient
```

Follow [quickstart.md](quickstart.md) for in-game checks.
