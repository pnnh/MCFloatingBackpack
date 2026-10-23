<!--
Sync Impact Report
- Version change: template → 1.0.0
- Modified principles:
  - Overlay Not Screen
  - Server Owns Items
  - One Feature At A Time
  - Vanilla Path Before Compat
  - Verify In A Client Session
- Added sections: Additional Constraints, Development Workflow, Governance
- Removed sections: none
- Follow-up TODOs: none
-->

# MCFloatingBackpack Constitution

## Core Principles

### I. Overlay Not Screen

The floating backpack MUST NOT take over `Minecraft.screen` as its primary
open path. Opening the panel MUST leave the current container GUI (or lack of
one) intact. The panel MUST behave as a draggable overlay that can stay visible
while the player inventory, chests, or other container screens are open.

Rationale: the product goal is MMO-style bags that float above other UIs. A
normal Screen replaces the previous one and cannot meet that goal.

### II. Server Owns Items

Any real item storage MUST be authoritative on the server and synced to the
client. Client-only fake inventories MUST NOT be treated as finished item
features. Cosmetic empty panels MAY be client-local until a storage feature
adds inventory.

Rationale: overlay rendering is client work; item movement is multiplayer-safe
game state.

### III. One Feature At A Time

A feature MUST stay inside its specification. Background milestones in
`docs/MCFloatingBackpack.md` (M2 storage, M3 cross-GUI drag, M4 third-party
backpacks, M5 polish) MUST NOT be implemented under an earlier feature's
tasks.

Rationale: Spec Kit delivers one player-visible slice at a time.

### IV. Vanilla Path Before Compat

Cross-mod compatibility with Sophisticated Backpacks, Traveler's Backpack, JEI,
EMI, and similar mods MUST wait until the vanilla inventory and chest path
works. Optional integrations MUST NOT be hard dependencies.

Rationale: third-party GUIs vary; the overlay contract must be proven on
vanilla first.

### V. Verify In A Client Session

A feature that changes what the player sees is not complete until that flow
has been exercised in a client session. `./gradlew runClient` is the default
check. Pure logic MAY have automated tests that do not start the game.
Player-visible claims MUST NOT be marked done from a compile alone.

## Additional Constraints

- Minecraft 1.21.1, NeoForge 21.1.251, Java 21.
- Mod id `mcfloatingbackpack`, package `pnnh.mcfloatingbackpack`.
- Dual-sided mod once storage exists; M1 empty panel MAY be client-visible only
  while still leaving the common entry point in place.
- `thirdpart/` is local reference only and MUST stay gitignored.
- Hotkeys that toggle the panel MUST remain usable while another Screen is
  open (universal conflict context or equivalent).

## Development Workflow

Work follows Spec Kit, one feature at a time:

1. Specify the player-visible behavior.
2. Plan the design (`research.md`, `data-model.md`, `contracts/`,
   `quickstart.md`).
3. Break the plan into tasks only when implementation is requested.
4. Implement only the active feature's tasks.

`docs/MCFloatingBackpack.md` is background and feasibility. A feature
specification overrides it when they disagree.

## Governance

This constitution overrides conflicting local practice. Feature plans MUST
pass the Constitution Check before implementation. A violation needs an entry
in that plan's Complexity Tracking.

Amendments change this file and bump the version:

- MAJOR: remove or redefine a principle.
- MINOR: add a principle or a constraint.
- PATCH: wording that does not change a rule.

**Version**: 1.0.0 | **Ratified**: 2026-10-23 | **Last Amended**: 2026-10-23
