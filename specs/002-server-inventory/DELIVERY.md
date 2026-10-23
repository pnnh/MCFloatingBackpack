# M2 Delivery Note: 002-server-inventory

**Date**: 2026-10-23

## Automated

- `./gradlew test` — PASS (`PanelClampTest`, `ModIdTest`, `BackpackClickLogicTest` fake-stack rules)
- `./gradlew compileJava` — PASS

## Client / server playthrough (quickstart)

Not fully exercised interactively in this implement session. **待人工验收**:

1. `./gradlew runClient` → open panel (`B`)
2. Without E: hold item, **Shift+left-click** empty slot to deposit; Shift again to withdraw
3. Left-click pick/place/swap with panel cursor
4. Relog; contents persist
5. Optional: `runServer` with mod on both sides

## Notes

- Cross-GUI drag with open inventory/chest is **out of scope** (M3).
- NeoForge `unitTest { enable() }` was not left enabled (module-path conflict in this environment); click rules covered by pure fake-stack tests mirroring production logic.
