<!--
Sync Impact Report
- Version change: 1.0.0 -> 1.1.0
- Modified principles:
  - V. Verify In A Client Session (PATCH-level clarification only: what a completion
    claim must state, scripts/compiles do not substitute, fixes are checked under the
    reported conditions; the rule itself is unchanged)
- Added principles:
  - VI. Shared Resources Have A Budget (from specs 009 and 010)
- Added sections: none (new bullets under Additional Constraints and Development Workflow)
- Expanded sections:
  - Additional Constraints: design/ directory, Git LFS boundary, dev-only runtime deps
  - Development Workflow: backfilling work that landed outside Spec Kit
- Removed sections: none
- Templates reviewed, no change needed: .specify/templates/plan-template.md (Constitution
  Check gates are read from this file at plan time), spec-template.md, tasks-template.md,
  AGENTS.md, CLAUDE.md (no principle numbers or names are quoted there)
- Follow-up TODOs: none. The mesh triangle-count ceiling is intentionally left undefined
  until frame-rate data exists (spec 009 known limitation 3, task T024).
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

A completion claim MUST state what was actually verified. Anything not exercised
stays recorded as unverified; a passing compile, unit test, or measurement script
does not stand in for checking player-visible behavior. A fix for a reported visual
defect MUST be checked under the conditions of the report (for example the real
first-person hand transform), not only under an approximation of them.

### VI. Shared Resources Have A Budget

An asset that enters a resource shared with the rest of the game, such as the
block texture atlas, MUST be measured before and after it is added. The shared
block atlas MUST NOT exceed 2048 pixels in width or height (spec 010). Measure it
with `design/demo/tools/check_atlas_budget.sh`.

A change that adds video-memory or rendering cost for every player, including
players who never hold a backpack, MUST record its measured cost in the feature's
plan. A cost that was not measured MUST be registered as a known limitation and
MUST NOT be claimed as met.

Adding or replacing a model MUST record its triangle count. No per-model triangle
ceiling is set yet (spec 009 known limitation 3); one MUST be proposed only after
frame-rate data exists, not before.

Rationale: a bundled texture inflated the shared atlas from 1024x1024 to
4096x4096 and cost every player memory (specs 009 and 010). The effect was
invisible to the feature's own screenshots and was found only from a game log.

## Additional Constraints

- Minecraft 1.21.1, NeoForge 21.1.251, Java 21.
- Mod id `mcfloatingbackpack`, package `pnnh.mcfloatingbackpack`.
- Dual-sided mod once storage exists; M1 empty panel MAY be client-visible only
  while still leaving the common entry point in place.
- `thirdpart/` is local reference only and MUST stay gitignored.
- Hotkeys that toggle the panel MUST remain usable while another Screen is
  open (universal conflict context or equivalent).
- `design/` holds development-time source assets and processing scripts (GLB, OBJ,
  Blender projects, Python and Blender scripts). It MUST NOT be shipped in the mod
  and runtime code MUST NOT reference it.
- Binary source assets under `design/` (`.obj`, `.blend`) MUST be tracked with
  Git LFS (see `.gitattributes`). Files under `src/main/resources` that ship in the
  jar MUST NOT be LFS-tracked: a build without git-lfs would pack pointer text into
  the jar (spec 009 known limitation 9). Because each re-export of a shipped large
  file grows repository history, the commit message MUST say when one was replaced.
- Development-only runtime dependencies (JEI, compatibility-test mods, Iris and
  Sodium) MUST be `localRuntime` and enabled by a Gradle switch (`-PcompatRuntime`,
  `-PshaderRuntime`, `-PnoJei`). They MUST NOT reach the published artifact.

## Development Workflow

Work follows Spec Kit, one feature at a time:

1. Specify the player-visible behavior.
2. Plan the design (`research.md`, `data-model.md`, `contracts/`,
   `quickstart.md`).
3. Break the plan into tasks only when implementation is requested.
4. Implement only the active feature's tasks.

Work that landed outside Spec Kit (a prototype or a hotfix) MUST be backfilled
into a specification before the next feature starts. That specification MUST say it
is retrospective, list its known gaps, and follow the current source where the two
disagree (as spec 009 does).

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

**Version**: 1.1.0 | **Ratified**: 2026-10-23 | **Last Amended**: 2026-11-04
