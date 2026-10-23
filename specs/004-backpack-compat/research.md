# Research: 004-backpack-compat

## Decision 1: Rely on AbstractContainerScreen inheritance

**Decision**: Treat all `AbstractContainerScreen` as the primary cross-GUI path; soft-tag known backpack class names for docs/logging only.

**Rationale**: Sophisticated Core `StorageScreenBase` and Traveler's `AbstractBackpackScreen` both extend `AbstractContainerScreen` and use standard menu carried. M3 already implements the carry/merge path.

**Alternatives considered**: Per-mod reflection adapters for slot clicks — unnecessary while menus expose `getCarried()`.

## Decision 2: Soft class-name detection

**Decision**: Match screen class names (and package prefixes) for `sophisticated` / `travelersbackpack` without compile-time types.

**Rationale**: Optional integrations must not hard-depend; packmakers may omit either mod.

## Decision 3: Optional localRuntime behind Gradle property

**Decision**: `./gradlew runClient -PcompatRuntime` pulls CurseMaven jars (core + backpacks + travelers). Default builds stay lean.

**Rationale**: Avoids CI/network failures and keeps publish classpath clean.

## Known limitations

- Fluid tanks, filter ghost slots, disabled/upgrade config UIs: not guaranteed.
- Bulk sort / transfer buttons on third-party screens: out of scope; only floating-slot ↔ menu carried.
