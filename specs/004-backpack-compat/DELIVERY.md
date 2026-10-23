# M4 Delivery Note: 004-backpack-compat

**Date**: 2026-10-23

## Automated

- `./gradlew test` / `compileJava` — PASS

## Implemented

- `ContainerScreenCompat` soft class-name tagging (Sophisticated / Traveler's)
- Client `screenOpen` / cursor / opening-merge routed through compat helper (still covers all `AbstractContainerScreen`)
- Optional CurseMaven `localRuntime` via `-PcompatRuntime`
- `mods.toml` optional deps for sophisticatedcore / sophisticatedbackpacks / travelersbackpack
- Known limitations documented in quickstart (fluids, ghosts, upgrade pages)

## Manual check (待人工验收)

See [quickstart.md](quickstart.md): with `-PcompatRuntime`, panel↔Sophisticated and panel↔Traveler's main storage.
