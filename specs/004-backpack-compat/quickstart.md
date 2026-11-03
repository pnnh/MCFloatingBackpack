# Quickstart: M4 backpack compat

## Default (no third-party jars)

```bash
./gradlew test compileJava
./gradlew runClient
```

Verify vanilla M3 path still works (E inventory / chest ↔ floating panel).

## With Sophisticated + Traveler's (dev only)

```bash
./gradlew runClientCompat
# equivalent to: ./gradlew runClient -PcompatRuntime
# dedicated-server side: ./gradlew runServerCompat (equivalent to runServer -PcompatRuntime)
```

1. Toggle floating panel (default `B`).
2. Open a Sophisticated backpack GUI → drag items to/from floating slots.
3. Open a Traveler's backpack GUI → drag via main storage slots (not fluid tanks).
4. Confirm game still launches with plain `./gradlew runClient` (no compat mods loaded).

## Known limits

- Fluid tanks, filter ghosts, disabled slots, upgrade settings pages: unsupported.
- Sort / bulk transfer buttons: out of scope.
