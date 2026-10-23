# Contract: Backpack Sync & Click (M2)

## Side requirement

Dedicated servers MUST install this mod. Client-only install MUST NOT grant real item moves on a vanilla server.

## Payloads (logical)

### `BackpackFullSync` (S → C)

| Field | Type | Notes |
| --- | --- | --- |
| slots | 27 × ItemStack | Full mirror |
| cursor | ItemStack | Panel cursor |

Sent on: player login (after attachment load), and when server forces resync.

### `BackpackClick` (C → S)

| Field | Type | Notes |
| --- | --- | --- |
| slotIndex | int | 0–26 |
| button | int | 0 = left |
| shiftDown | bool | Main-hand path when no container |
| screenOpen | bool | Client hint; server verifies |

Server: validate player, panel may be considered always “logically open” for clicks only if client also says panel visible—**require a `panelOpen` bool** so hidden panel cannot click.

| Field | Type | Notes |
| --- | --- | --- |
| panelOpen | bool | Must be true or server ignores |

### `BackpackSlotUpdate` (S → C)

| Field | Type | Notes |
| --- | --- | --- |
| slotIndex | int | 0–26, or -1 if none |
| stack | ItemStack | New slot contents if slotIndex ≥ 0 |
| cursor | ItemStack | Always send current cursor after click |

## Click semantics (server)

1. If `!panelOpen` → ignore.
2. If `shiftDown && player has no container menu open`:
   - Move/swap between `slots[slotIndex]` and main-hand stack.
3. Else:
   - Apply panel cursor ↔ slot left-click logic (pick / place / swap / merge).
4. Persist attachment; reply with `BackpackSlotUpdate` (and touch player inv if main-hand changed).

## Non-goals

- No interaction with `AbstractContainerMenu` carried.
- No chest/inventory screen slot bridging (M3).
- No right-click half-stack unless added later in the same feature tasks (optional; default left-click only for MVP of M2).
