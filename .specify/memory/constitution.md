<!--
Sync Impact Report
- Version change: 2.1.0 -> 2.1.1 (PATCH: the three levels of principle VII are explained, no rule changes)
- Modified principles:
  - VII. Minimal Dependencies, Opt-Out Compatibility. The levels now say what they are about: FULL is
    the mod-specific adaptation (code written for that mod, active compatibility, an enhancement);
    GENERIC turns off any such code (for a mod nothing was written for it equals FULL) and does not decide
    whether the panel shows on that mod's screens or exchanges items with its menus, which depends on how
    the mod implements its containers; OFF is the only level that stops the panel being shown there.
    (An intermediate wording, "no item exchange at GENERIC", was tried during the first client session of
    spec 020 and withdrawn: it misread the intent.)
- Added principles: none
- Added sections: none
- Removed sections: none
- Templates reviewed, no change needed: .specify/templates/*, AGENTS.md, CLAUDE.md
- Follow-up TODOs: as in 2.1.0 (mixin question undecided; the meaning of OFF for a mod that only decorates
  other screens is defined by the feature that introduces its adaptation).

Previous report (2.0.0 -> 2.1.0):
- Version change: 2.0.0 -> 2.1.0 (MINOR: new principle VII)
- Added principles:
  - VII. Minimal Dependencies, Opt-Out Compatibility. Required dependencies are only
    Minecraft 1.21.1 and NeoForge 21.1.251; every other mod is optional, compile-time-only
    and dev-run-time-only; third-party types stay under compat/<modid>/; vanilla container
    support is not configurable; every proactive interaction with another mod is controlled
    by a mod-id -> level setting (FULL default / GENERIC / OFF); the dependency rules are
    checked by an automated test. Codifies what the project already did (mods.toml, build.gradle,
    the single JEI plugin) so a later change cannot break it unnoticed.
- Modified principles: none (IV, the dev-only dependency bullet and II are consistent with VII;
  the "mixins" question was raised but not decided, so VII says nothing about mixins)
- Added sections: none
- Removed sections: none
- Templates reviewed, no change needed: .specify/templates/* (Constitution Check gates are read
  from this file at plan time), AGENTS.md, CLAUDE.md
- Follow-up TODOs:
  - A feature (planned 020) implements the setting, its levels and the enforcement test.
  - Whether to forbid mixins (unless justified in a plan) is undecided.
  - What level OFF means for a mod that only decorates other screens (JEI) is to be defined by
    the feature that introduces the setting.

Previous report (1.1.0 -> 2.0.0):
- Version change: 1.1.0 -> 2.0.0 (MAJOR: principle I is redefined, per the Governance
  rule "remove or redefine a principle")
- Modified principles:
  - I. Overlay Not Screen (name kept so earlier specs' Constitution Check tables still
    match). Old rule: the panel MUST NOT take over `Minecraft.screen` as its primary
    open path, and "lack of" a current Screen was an allowed state. New rule: the panel
    is an overlay on whatever Screen is current and MUST NOT replace, wrap, subclass or
    intercept another Screen; when the panel is visible and no other Screen is open, the
    mod MAY show a transparent fallback host Screen that yields to any other Screen and
    returns when it closes. Reason: the screen-less HUD mode needed a second input and
    render path beside the Screen-event path (planned feature 019 replaces it).
- Added principles: none
- Added sections: none
- Expanded sections:
  - Additional Constraints: the panel toggle hotkey bullet now also names the host Screen
- Removed sections: none
- Templates reviewed, no change needed: .specify/templates/plan-template.md (Constitution
  Check gates are read from this file at plan time), spec-template.md, tasks-template.md,
  AGENTS.md, CLAUDE.md (no principle numbers or names are quoted there)
- Follow-up TODOs:
  - Feature 019 (replace HUD mode with the fallback host Screen) must run its
    Constitution Check against the new principle I and must be implemented before 018.
  - Earlier specs (003, 007, 011-014, ...) describe the screen-less HUD mode; they are
    historical and are updated by 019, not by this amendment.
  - The mesh triangle-count ceiling is intentionally left undefined until frame-rate
    data exists (spec 009 known limitation 3, task T024).

Previous report (1.0.0 -> 1.1.0): added principle VI (Shared Resources Have A Budget,
specs 009 and 010); PATCH-level clarification of V; new bullets under Additional
Constraints (design/, Git LFS boundary, dev-only runtime deps) and Development Workflow
(backfilling work that landed outside Spec Kit).
-->

# MCFloatingBackpack Constitution

## Core Principles

### I. Overlay Not Screen

The floating backpack MUST be an overlay drawn on top of whatever Screen is
current. It MUST NOT replace, wrap, subclass, or intercept the vanilla inventory,
chest, or another mod's Screen in order to open or show the panel. Opening the
panel MUST leave the current container GUI intact. The panel MUST behave as a
draggable overlay that can stay visible while the player inventory, chests, or
other container screens are open.

When the panel is visible and no other Screen is open, the mod MAY show its own
transparent host Screen so that the panel has a Screen to sit on. The host Screen
is a fallback only:

- It MUST give way the moment any other Screen opens, whether the player, a
  keybinding, or another mod opened it, and MUST come back when that Screen closes
  while the panel is still visible.
- It MUST NOT pause the game, MUST NOT hold item storage, and MUST keep every
  keybinding that opens another Screen working.
- It MUST NOT be used for anything but hosting the panel; any other use requires its
  own feature specification.

Rationale: the product goal is MMO-style bags that float above other UIs. A
normal Screen replaces the previous one and cannot meet that goal. A screen-less
HUD mode needed a second input and render path beside the Screen-event path; a
fallback host Screen removes that path without taking any other Screen's place.

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

### VII. Minimal Dependencies, Opt-Out Compatibility

The mod's only required dependencies are Minecraft 1.21.1 and NeoForge 21.1.251.
Every other mod MUST be optional: declared `type="optional"` in
`neoforge.mods.toml`, its API on the compile class path only (`compileOnly`), and
present at run time only through the development-only mechanism under Additional
Constraints. A feature MUST work with vanilla and NeoForge alone.

Code that references another mod's types MUST live under `compat/<modid>/` and MUST
be reached only through that mod's own discovery or registration point, so nothing
outside `compat/` imports it. Behaviour that touches another mod's UI without
referencing its types (for example working over any container screen) is not compat
code, but it is still an interaction with that mod and falls under the setting below.

Support for vanilla containers (the player inventory, chests, furnaces and the other
vanilla menus) MUST NOT be configurable. Every proactive interaction with another mod
MUST be controlled by a setting that maps that mod's id to a level; a mod that is not
listed is at FULL:

- FULL: the mod-specific adaptations: the code written for that mod, that is active
  compatibility with it, which can also be seen as an enhancement.
- GENERIC: no mod-specific adaptation; none of the code written for that mod runs (for a
  mod nothing was written for, the same as FULL). It does not decide whether the panel is
  shown on that mod's screens or exchanges items with its menus: that depends on how the
  mod implements its containers, and the closer to vanilla containers the better.
- OFF: the panel is not shown on that mod's UI: the panel and that UI are either/or. What OFF means for a
  mod that only decorates other screens is defined by the specification of the
  feature that introduces its adaptation.

A feature that adds a mod-specific adaptation MUST state in its specification what
each level changes. A decision that gates item movement and depends on this setting
MUST be made on the server; a client-side setting only resolves client-side
conflicts and is not a security boundary (principle II).

The rules on required dependencies, `compat/<modid>/` and `neoforge.mods.toml` MUST
be checked by an automated test, not by review alone.

Rationale: the mod's value is that it sits beside the game's own UIs. Interactions with
other mods cannot all be tested in advance, so the guarantee is a vanilla-only
minimum plus an escape hatch for the player, not a promise of adapting to every mod.

## Additional Constraints

- Minecraft 1.21.1, NeoForge 21.1.251, Java 21.
- Mod id `mcfloatingbackpack`, package `pnnh.mcfloatingbackpack`.
- Dual-sided mod once storage exists; M1 empty panel MAY be client-visible only
  while still leaving the common entry point in place.
- `thirdpart/` is local reference only and MUST stay gitignored.
- Hotkeys that toggle the panel MUST remain usable while another Screen is
  open, including the host Screen (universal conflict context or equivalent).
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

**Version**: 2.1.1 | **Ratified**: 2026-10-23 | **Last Amended**: 2026-11-05
