# M3 Delivery Note: 003-cross-gui-drag

**Date**: 2026-10-23

## Automated

- `./gradlew test` / `compileJava` — PASS

## Implemented

- Floating slot hover highlight (`0x80FFFFFF`)
- Container open: merge leftover panel cursor → menu carried
- Panel clicks with inventory/chest open use `AbstractContainerMenu.getCarried()`
- Client cursor draw prefers menu carried while `AbstractContainerScreen` is open

## Manual check (待人工验收)

See [quickstart.md](quickstart.md): panel↔E inventory, panel↔chest, hover, Shift path without screen.
